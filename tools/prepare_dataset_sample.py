"""Convert the downloaded 1K cinema CSV archive into an opt-in JDBC test fixture.

No database connection, SQL execution, or extraction of archive paths is performed.
"""
from __future__ import annotations

import base64
import csv
import hashlib
import io
import json
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "verification" / "dataset-sample"
OFFSET = 1_000_000


def prepare() -> None:
    archive = OUT / "source-csv.zip"
    with zipfile.ZipFile(archive) as z:
        def read(name):
            return list(csv.DictReader(io.StringIO(z.read(name + ".csv").decode("utf-8-sig"), newline="")))

        tables = {n: read(n) for n in (
            "theaters", "screens", "seats", "movies", "shows", "users", "orders", "tickets", "transactions")}
    indexed = {n: {int(r["id"]): r for r in rows} for n, rows in tables.items()}
    tickets = tables["tickets"]
    if len(tickets) != 1000 or any(r["order_status"] != "completed" for r in tables["orders"]):
        raise ValueError("Chỉ hỗ trợ bản sạch 1K với đơn completed.")
    if any(r["status"] != "succeeded" for r in tables["transactions"]):
        raise ValueError("Chỉ hỗ trợ giao dịch succeeded của bản 1K.")
    order_show, totals, seen = {}, defaultdict(Decimal), set()
    branch_totals, branch_tickets, branch_orders = defaultdict(Decimal), Counter(), Counter()
    for t in tickets:
        order, show, seat = int(t["order_id"]), int(t["show_id"]), int(t["seat_id"])
        if (show, seat) in seen or order_show.setdefault(order, show) != show:
            raise ValueError("Trùng ghế hoặc đơn chứa nhiều suất.")
        seen.add((show, seat))
        s = indexed["shows"][show]
        if indexed["seats"][seat]["screen_id"] != s["screen_id"]:
            raise ValueError("Ghế sai phòng.")
        price = Decimal(t["price"]) * 1000
        if price <= 0:
            raise ValueError("Giá vé không dương.")
        totals[order] += price
        branch = int(indexed["screens"][int(s["screen_id"])]["theater_id"])
        branch_totals[branch] += price
        branch_tickets[branch] += 1
    rows = []
    def emit(table, *values):
        # Base64 keeps delimiters and Unicode data out of the fixture grammar.
        rows.append("\t".join([table] + [base64.b64encode(str(v).encode()).decode() for v in values]))
    def key(value):
        return OFFSET + int(value)
    for r in tables["theaters"]:
        i = int(r["id"])
        emit("cinema_branch", key(i), f"DS1K-{i}", f"DS1K {r['name']}", "Địa chỉ kiểm thử", "Thành phố kiểm thử", f"DS1K-B{i}", 1)
        emit("employee", key(i), key(i), f"DS1K-E{i}", "Thu ngân kiểm thử", f"DS1K-E{i}", "CASHIER", "2020-01-01")
    for r in tables["screens"]:
        room_type = "IMAX" if r["screen_type"] == "IMAX" else "2D"
        emit("screening_room", key(r["id"]), key(r["theater_id"]), r["name"], room_type, 1)
    for r in tables["seats"]:
        emit("seat", key(r["id"]), key(r["screen_id"]), r["row_label"], r["number_label"], "VIP" if r["seat_type_id"] in ("2", "3") else "STD")
    for r in tables["movies"]:
        # Synthetic age classification; this fixture does not test rating equivalence.
        emit("movie", key(r["id"]), f"DS1K-M{r['id']}", "DS1K " + r["title"], r["duration_minutes"], r["release_date"], "P", "Quốc gia kiểm thử")
    for r in tables["shows"]:
        emit("screening", key(r["id"]), key(r["movie_id"]), key(r["screen_id"]), r["start_time"], r["end_time"], Decimal(r["base_ticket_price"]) * 1000, "OPEN")
    for r in tables["users"]:
        emit("customer", key(r["id"]), r["first_name"] + " " + r["last_name"], f"DS1K-C{r['id']}", f"ds1k-{r['id']}@example.invalid", r["created_at"])
    payments = {int(r["order_id"]): r for r in tables["transactions"]}
    for order, show in order_show.items():
        r = indexed["orders"][order]
        branch = int(indexed["screens"][int(indexed["shows"][show]["screen_id"])]["theater_id"])
        branch_orders[branch] += 1
        emit("booking", key(order), f"DS1K-BK{order}", key(show), key(r["user_id"]), key(branch), r["created_at"], "CONFIRMED")
    for r in tickets:
        emit("ticket", key(r["id"]), key(r["order_id"]), key(r["seat_id"]), Decimal(r["price"]) * 1000, "VALID")
    for order, total in totals.items():
        p = payments[order]
        emit("payment", key(p["id"]), key(order), total, "CARD", "SUCCESS", p["processed_at"], f"DS1K-TX{p['id']}")
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "fixture.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    capacity = Counter(r["screen_id"] for r in tables["seats"])
    expected = {
        "tickets": len(tickets), "bookings": len(totals), "screenings": len(tables["shows"]),
        "capacity": sum(capacity[r["screen_id"]] for r in tables["shows"]),
        "revenue": str(sum(totals.values())),
    }
    for branch in branch_totals:
        expected[f"branch.{key(branch)}.revenue"] = str(branch_totals[branch])
        expected[f"branch.{key(branch)}.tickets"] = branch_tickets[branch]
        expected[f"branch.{key(branch)}.bookings"] = branch_orders[branch]
    (OUT / "expected.properties").write_text("\n".join(f"{k}={v}" for k, v in expected.items()), encoding="utf-8")
    manifest = {"source": "https://databasesample.com/datasets/cinema-sample-database", "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
                "source_counts": {n: len(v) for n, v in tables.items()}, "expected": expected,
                "adaptations": ["Giữ ngày lịch sử, giá vé nhân 1000 là đơn vị tiền kiểm thử, không phải tỷ giá.",
                                "Chỉ chiếu sổ vé: payment bằng tổng vé; bỏ bắp nước, giảm giá và hoàn tiền của nguồn.",
                                "Premium/Recliner -> VIP; Standard/Accessible -> STD; phân loại tuổi P giả lập.",
                                "Tạo nhân viên, địa chỉ, điện thoại/email riêng; tiền tố DS1K, ID nguồn + 1000000.",
                                "Không nhập chains, genres hoặc các phân hệ ngoài bán vé."]}
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"fixture_rows": len(rows), "expected": expected, "sha256": manifest["sha256"]}, ensure_ascii=False))


if __name__ == "__main__":
    prepare()
