"""Read-only, fail-closed pre/post checks for the existing 13 -> 14 table migration."""
from __future__ import annotations

import argparse
from decimal import Decimal, InvalidOperation
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from db_fingerprint import load_local_app_credentials, mysql_defaults_file, resolve_mysql_exe, run_mysql_readonly, validate_database_name

TABLES = set("cinema_branch screening_room seat_type seat movie genre movie_genre screening customer employee booking ticket payment screening_seat_lock".split())
ZERO_COLUMNS = {
    "pre": {"ticket_booking_screening_mismatch", "duplicate_booking_seat_pairs", "duplicate_valid_screening_seats", "wrong_room_ticket_count"},
    "post": {"forbidden_non_3nf_ticket_columns", "overlapping_open_screenings", "wrong_room_tickets", "duplicate_valid_seats", "lock_sync_mismatch_count", "multi_success_payment_bookings", "revenue_difference"},
}


def assert_zero_results(output: str, phase: str) -> dict[str, str]:
    """Check named result columns, rejecting missing, malformed and duplicate results."""
    lines = output.splitlines()
    found: dict[str, str] = {}
    for index, line in enumerate(lines):
        headers = line.split("\t")
        wanted = ZERO_COLUMNS[phase].intersection(headers)
        if not wanted:
            continue
        if index + 1 >= len(lines):
            raise RuntimeError("Thiếu dòng kết quả kiểm tra toàn vẹn.")
        values = lines[index + 1].split("\t")
        if len(headers) != len(values):
            raise RuntimeError("Kết quả kiểm tra toàn vẹn sai định dạng.")
        for name in wanted:
            if name in found:
                raise RuntimeError(f"Chỉ số bị lặp: {name}")
            value = values[headers.index(name)]
            try:
                number = Decimal(value)
            except InvalidOperation as error:
                raise RuntimeError(f"Chỉ số không phải số: {name}") from error
            if not number.is_finite() or number != 0:
                raise RuntimeError(f"CHẶN MIGRATION: {name}={value}, yêu cầu bằng 0.")
            found[name] = value
    if found.keys() != ZERO_COLUMNS[phase]:
        raise RuntimeError(f"Thiếu chỉ số bắt buộc: {sorted(ZERO_COLUMNS[phase] - found.keys())}")
    return found


def assert_environment(mysql: Path, cfg: Path, db: str, phase: str, maintenance: bool = False) -> None:
    validate_database_name(db)
    if db not in {"cinema_chain", "cinema_chain_test", "cinema_chain_migration_clone"}:
        raise RuntimeError("Chỉ chấp nhận CSDL chính, test hoặc migration clone của dự án.")
    version, comment, datadir = run_mysql_readonly(mysql, cfg, "SELECT VERSION(), @@version_comment, REPLACE(@@datadir, CHAR(92), '/');").strip().split("\t")
    expected_datadir = Path(os.environ.get("USERPROFILE", "")) / ".mysql" / "data-8.4"
    if not version.startswith("8.4.") or "mariadb" in comment.lower() or os.path.normcase(os.path.normpath(datadir)) != os.path.normcase(str(expected_datadir)):
        raise RuntimeError("Sai instance: yêu cầu MySQL 8.4 tại %USERPROFILE%/.mysql/data-8.4.")
    actual = set(run_mysql_readonly(mysql, cfg, f"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='{db}' AND TABLE_TYPE='BASE TABLE';").splitlines())
    expected = TABLES if phase == "post" else TABLES - {"screening_seat_lock"}
    if actual != expected:
        raise RuntimeError(f"Sai lược đồ {'14' if phase == 'post' else '13'} bảng; thiếu={sorted(expected-actual)}, thừa={sorted(actual-expected)}")
    columns = set(run_mysql_readonly(mysql, cfg, f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{db}' AND TABLE_NAME='ticket';").splitlines())
    legacy = {"screening_id", "active_seat_key"}
    if (phase == "pre" and not legacy <= columns) or (phase == "post" and legacy & columns):
        raise RuntimeError("Cấu trúc ticket không khớp giai đoạn migration.")
    if phase == "pre":
        fks = set(run_mysql_readonly(mysql, cfg, f"SELECT CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='{db}' AND TABLE_NAME='ticket';").splitlines())
        indexes = set(run_mysql_readonly(mysql, cfg, f"SELECT DISTINCT CONCAT(TABLE_NAME,'.',INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='{db}';").splitlines())
        if not {"fk_ticket_booking_scr", "fk_ticket_screening", "fk_ticket_booking", "fk_ticket_seat"} <= fks or not {"ticket.uq_ticket_active_seat", "ticket.idx_ticket_screening_status", "ticket.idx_ticket_booking_scr", "booking.uq_booking_id_screening"} <= indexes:
            raise RuntimeError("Lược đồ cũ thiếu FK/index mà migration cần; dừng trước DDL.")
    else:
        counts = run_mysql_readonly(mysql, cfg, f"SELECT (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='{db}'), (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='{db}' AND CONSTRAINT_TYPE='PRIMARY KEY');").strip()
        if counts != "17\t14":
            raise RuntimeError("Yêu cầu lược đồ hiện hành có đủ 17 FK và 14 PK.")
    if maintenance:
        app_user, _ = load_local_app_credentials(ROOT)
        escaped_user = app_user.replace("'", "''")
        sessions = int(run_mysql_readonly(mysql, cfg, f"SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND (DB='{db}' OR USER='{escaped_user}');").strip())
        if sessions:
            raise RuntimeError("Còn session ứng dụng/CSDL đích; hãy đóng mọi ứng dụng, callback server và client ghi trước khi tiếp tục.")


def check_database(db: str, phase: str, maintenance: bool = False) -> dict[str, str]:
    mysql = resolve_mysql_exe()
    with mysql_defaults_file("root", os.environ.get("MYSQL_ROOT_PASSWORD", "")) as cfg:
        assert_environment(mysql, cfg, db, phase, maintenance)
        sql_file = ROOT / ("database/migrations/V2_3nf_ticket_precheck.sql" if phase == "pre" else "database/verification/verify_constraints.sql")
        result = subprocess.run([str(mysql), f"--defaults-extra-file={cfg}", "-B", db], input=sql_file.read_text(encoding="utf-8"), text=True, encoding="utf-8", capture_output=True)
        if result.returncode:
            raise RuntimeError(f"SQL kiểm tra thất bại: {result.stderr.strip()}")
        return assert_zero_results(result.stdout, phase)


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True)
    parser.add_argument("--phase", choices=("pre", "post"), required=True)
    parser.add_argument("--maintenance", action="store_true")
    args = parser.parse_args()
    try:
        checks = check_database(args.db, args.phase, args.maintenance)
        print(f"[MIGRATION-CHECK] {args.db}: {len(checks)} chỉ số bằng 0; phase={args.phase}.")
    except (RuntimeError, ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
