#!/usr/bin/env python3
"""Đối chiếu sơ đồ ER.drawio, luoc-do-quan-he.drawio, Mermaid và DDL MySQL."""

from __future__ import annotations

import re
from collections import Counter, defaultdict
from itertools import combinations
from pathlib import Path
from types import MappingProxyType
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent

VN_SCHEMA_MAP: MappingProxyType[str, tuple[str, tuple[tuple[str, str], ...]]] = (
    MappingProxyType(
        {
            "cinema_branch": (
                "CHI_NHANH_RAP",
                (
                    ("branch_id", "MaCN"),
                    ("branch_code", "MaCodeCN"),
                    ("branch_name", "TenChiNhanh"),
                    ("address", "DiaChi"),
                    ("city", "ThanhPho"),
                    ("phone", "SoDienThoai"),
                    ("active", "DangHoatDong"),
                ),
            ),
            "screening_room": (
                "PHONG_CHIEU",
                (
                    ("room_id", "MaPhong"),
                    ("branch_id", "MaCN"),
                    ("room_name", "TenPhong"),
                    ("room_type", "LoaiPhong"),
                    ("active", "DangHoatDong"),
                ),
            ),
            "seat_type": (
                "HANG_GHE",
                (
                    ("seat_type_code", "MaHangGhe"),
                    ("type_name", "TenHangGhe"),
                    ("surcharge", "PhuThu"),
                ),
            ),
            "seat": (
                "GHE",
                (
                    ("seat_id", "MaGhe"),
                    ("room_id", "MaPhong"),
                    ("seat_row", "HangGhe"),
                    ("seat_number", "SoGhe"),
                    ("seat_type_code", "MaHangGhe"),
                ),
            ),
            "movie": (
                "PHIM",
                (
                    ("movie_id", "MaPhim"),
                    ("movie_code", "MaCodePhim"),
                    ("title", "TuaDe"),
                    ("duration_minutes", "ThoiLuongPhut"),
                    ("release_date", "NgayKhoiChieu"),
                    ("age_rating", "PhanLoaiTuoi"),
                    ("country", "QuocGia"),
                    ("description", "MoTa"),
                ),
            ),
            "genre": (
                "THE_LOAI",
                (
                    ("genre_id", "MaTL"),
                    ("genre_name", "TenTheLoai"),
                    ("description", "MoTa"),
                ),
            ),
            "movie_genre": (
                "PHIM_THE_LOAI",
                (
                    ("movie_id", "MaPhim"),
                    ("genre_id", "MaTL"),
                ),
            ),
            "screening": (
                "SUAT_CHIEU",
                (
                    ("screening_id", "MaSC"),
                    ("movie_id", "MaPhim"),
                    ("room_id", "MaPhong"),
                    ("start_time", "ThoiGianBatDau"),
                    ("end_time", "ThoiGianKetThuc"),
                    ("base_price", "GiaCoSo"),
                    ("status", "TrangThai"),
                ),
            ),
            "customer": (
                "KHACH_HANG",
                (
                    ("customer_id", "MaKH"),
                    ("full_name", "HoTen"),
                    ("phone", "SoDienThoai"),
                    ("email", "Email"),
                    ("birth_date", "NgaySinh"),
                    ("created_at", "NgayTao"),
                ),
            ),
            "employee": (
                "NHAN_VIEN",
                (
                    ("employee_id", "MaNV"),
                    ("branch_id", "MaCN"),
                    ("employee_code", "MaCodeNV"),
                    ("full_name", "HoTen"),
                    ("phone", "SoDienThoai"),
                    ("role", "ChucVu"),
                    ("hired_date", "NgayVaoLam"),
                ),
            ),
            "booking": (
                "DON_DAT_VE",
                (
                    ("booking_id", "MaDDV"),
                    ("booking_code", "MaCodeDDV"),
                    ("screening_id", "MaSC"),
                    ("customer_id", "MaKH"),
                    ("employee_id", "MaNV"),
                    ("booked_at", "ThoiGianDat"),
                    ("booking_status", "TrangThai"),
                ),
            ),
            "ticket": (
                "VE_XEM_PHIM",
                (
                    ("ticket_id", "MaVe"),
                    ("booking_id", "MaDDV"),
                    ("seat_id", "MaGhe"),
                    ("unit_price", "DonGia"),
                    ("ticket_status", "TrangThai"),
                    ("cancelled_at", "ThoiGianHuy"),
                ),
            ),
            "screening_seat_lock": (
                "KHOA_GHE_SUAT_CHIEU",
                (
                    ("screening_id", "MaSC"),
                    ("seat_id", "MaGhe"),
                    ("ticket_id", "MaVe"),
                ),
            ),
            "payment": (
                "THANH_TOAN",
                (
                    ("payment_id", "MaTT"),
                    ("booking_id", "MaDDV"),
                    ("amount", "SoTien"),
                    ("payment_method", "PhuongThuc"),
                    ("payment_status", "TrangThai"),
                    ("paid_at", "NgayThanhToan"),
                    ("transaction_ref", "MaGiaoDich"),
                ),
            ),
        }
    )
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def parse_xml(xml_text: str) -> ET.Element:
    upper = xml_text.upper()
    require(
        "<!DOCTYPE" not in upper and "<!ENTITY" not in upper,
        "Không cho phép DTD/ENTITY trong XML",
    )
    return ET.fromstring(xml_text)


def strip_html(text: str | None) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", text or "")).strip()


def style_map(style_text: str | None) -> dict[str, str]:
    result: dict[str, str] = {}
    for item in (style_text or "").split(";"):
        if "=" in item:
            key, val = item.split("=", 1)
            result[key] = val
    return result


def closure(
    attributes: set[str], dependencies: list[tuple[set[str], set[str]]]
) -> set[str]:
    result = set(attributes)
    while True:
        previous = result.copy()
        for left, right in dependencies:
            if left <= result:
                result.update(right)
        if result == previous:
            return result


def member(
    dependencies: list[tuple[set[str], set[str]]], left: set[str], right: set[str]
) -> bool:
    return right <= closure(left, dependencies)


def min_cover(
    dependencies: list[tuple[set[str], set[str]]],
) -> list[tuple[frozenset[str], str]]:
    single: list[tuple[set[str], str]] = [
        (set(left), attr) for left, right in dependencies for attr in sorted(right)
    ]
    for left, right_attr in single:
        for candidate in sorted(left):
            if len(left) > 1:
                reduced_left = left - {candidate}
                current_fds = [(lhs, {rhs}) for lhs, rhs in single]
                if member(current_fds, reduced_left, {right_attr}):
                    left.remove(candidate)

    kept: list[tuple[set[str], str]] = [(set(lhs), rhs) for lhs, rhs in single]
    idx = 0
    while idx < len(kept):
        left, right_attr = kept[idx]
        without = [(lhs, {rhs}) for pos, (lhs, rhs) in enumerate(kept) if pos != idx]
        if member(without, left, {right_attr}):
            kept.pop(idx)
        else:
            idx += 1
    return [(frozenset(lhs), rhs) for lhs, rhs in kept]


def algorithm_lossless(
    attrs: list[str],
    dependencies: list[tuple[set[str], set[str]]],
    decomposition: list[set[str]],
) -> bool:
    col_index = {attr: idx for idx, attr in enumerate(attrs, 1)}
    matrix = [
        [
            f"a_{col_index[attr]}" if attr in sub else f"b_{row_i}_{col_index[attr]}"
            for attr in attrs
        ]
        for row_i, sub in enumerate(decomposition, 1)
    ]
    changed = True
    while changed:
        changed = False
        for left, right in dependencies:
            left_cols = [col_index[attr] - 1 for attr in sorted(left)]
            groups: dict[tuple[str, ...], list[int]] = defaultdict(list)
            for r_idx, row in enumerate(matrix):
                groups[tuple(row[col] for col in left_cols)].append(r_idx)
            for matching_rows in groups.values():
                if len(matching_rows) < 2:
                    continue
                for r_attr in sorted(right):
                    c_idx = col_index[r_attr] - 1
                    symbols = {matrix[r_idx][c_idx] for r_idx in matching_rows}
                    if len(symbols) <= 1:
                        continue
                    a_syms = sorted(sym for sym in symbols if sym.startswith("a_"))
                    chosen = a_syms[0] if a_syms else min(symbols)
                    superseded = symbols - {chosen}
                    for row in matrix:
                        if row[c_idx] in superseded:
                            row[c_idx] = chosen
                            changed = True
    target_row = [f"a_{idx}" for idx in range(1, len(attrs) + 1)]
    return any(row == target_row for row in matrix)


def algorithm_preserve(
    decomposition: list[set[str]],
    dependencies: list[tuple[set[str], set[str]]],
    left: set[str],
    right: set[str],
) -> bool:
    z_set = set(left)
    while True:
        prev = z_set.copy()
        for sub in decomposition:
            z_set |= closure(z_set & sub, dependencies) & sub
        if z_set == prev:
            return right <= z_set


def verify_ticket_decomposition() -> None:
    ordered_attrs = [
        "ticket_id",
        "booking_id",
        "screening_id",
        "seat_id",
        "unit_price",
        "ticket_status",
        "cancelled_at",
    ]
    universe = set(ordered_attrs)
    ticket = universe - {"screening_id"}
    booking_screening = {"booking_id", "screening_id"}
    original = [
        ({"ticket_id"}, universe - {"ticket_id"}),
        ({"booking_id", "seat_id"}, {"ticket_id"}),
        ({"booking_id"}, {"screening_id"}),
    ]
    cover = min_cover(original)
    expected_cover = {
        (frozenset({"ticket_id"}), "booking_id"),
        (frozenset({"ticket_id"}), "seat_id"),
        (frozenset({"ticket_id"}), "unit_price"),
        (frozenset({"ticket_id"}), "ticket_status"),
        (frozenset({"ticket_id"}), "cancelled_at"),
        (frozenset({"booking_id", "seat_id"}), "ticket_id"),
        (frozenset({"booking_id"}), "screening_id"),
    }
    require(
        set(cover) == expected_cover and len(cover) == 7,
        "Phủ tối thiểu của U_old phải gồm đúng 7 phụ thuộc hàm",
    )

    decomposition = [ticket, booking_screening]
    require(
        algorithm_lossless(ordered_attrs, original, decomposition),
        "Phép tách ticket phải kết nối không mất thông tin",
    )
    for left, right in original:
        require(
            algorithm_preserve(decomposition, original, left, right),
            f"Phép tách không bảo toàn phụ thuộc hàm: {left} -> {right}",
        )

    local = [({"ticket_id"}, ticket - {"ticket_id"}), *original[1:]]
    cover_fds = [(set(lhs), {rhs}) for lhs, rhs in cover]
    for size in range(len(universe) + 1):
        for subset in combinations(sorted(universe), size):
            sub_set = set(subset)
            require(
                closure(sub_set, original)
                == closure(sub_set, local)
                == closure(sub_set, cover_fds),
                f"Lệch bao đóng trên tập con: {subset}",
            )


def parse_ddl_and_fks(
    schema_sql: str, fk_sql: str
) -> tuple[
    dict[str, list[str]],
    dict[str, set[str]],
    set[tuple[str, str]],
    set[tuple[str, str, str, str]],
]:
    tables_raw = dict(
        re.findall(
            r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\) ENGINE=", schema_sql, re.DOTALL
        )
    )
    require(
        set(tables_raw.keys()) == set(VN_SCHEMA_MAP.keys()),
        "Danh sách 14 bảng SQL không khớp bảng ánh xạ",
    )

    fk_blocks: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    for tbl, body in re.findall(r"ALTER TABLE (\w+)\s+(.*?);", fk_sql, re.DOTALL):
        fk_blocks[tbl].extend(
            re.findall(r"FOREIGN KEY \((\w+)\)\s+REFERENCES (\w+) \((\w+)\)", body)
        )

    vn_cols_by_table: dict[str, list[str]] = {}
    vn_pks_by_table: dict[str, set[str]] = {}
    expected_vn_fks: set[tuple[str, str]] = set()
    expected_mermaid: set[tuple[str, str, str, str]] = set()

    for sql_table, ddl in tables_raw.items():
        vn_table, col_pairs = VN_SCHEMA_MAP[sql_table]
        col_map = dict(col_pairs)
        sql_cols_defs = dict(
            re.findall(
                r"^\s+(\w+)\s+((?:INT|VARCHAR|CHAR|TINYINT|DECIMAL|DATE|DATETIME|TEXT)\b[^\n]*)",
                ddl,
                re.MULTILINE,
            )
        )
        logical_sql_cols = [c for c in sql_cols_defs if c != "active_success_booking"]
        require(
            logical_sql_cols == [c for c, _ in col_pairs],
            f"Thứ tự hoặc danh sách cột SQL bảng {sql_table} không khớp: {logical_sql_cols}",
        )
        vn_cols_by_table[vn_table] = [vn for _, vn in col_pairs]

        pk_match = re.search(r"PRIMARY KEY \(([^)]+)\)", ddl)
        require(pk_match is not None, f"Thiếu PRIMARY KEY ở bảng {sql_table}")
        sql_pks = {
            part.strip() for part in pk_match.group(1).split(",") if part.strip()
        }
        vn_pks_by_table[vn_table] = {col_map[col] for col in sql_pks}

        sql_unique = {
            frozenset(part.strip() for part in grp.split(",") if part.strip())
            for grp in re.findall(r"UNIQUE \(([^)]+)\)", ddl)
        }
        sql_unique.discard(frozenset({"active_success_booking"}))

        for fk_col, parent_sql, parent_pk_sql in fk_blocks[sql_table]:
            parent_vn, parent_pairs = VN_SCHEMA_MAP[parent_sql]
            parent_map = dict(parent_pairs)
            expected_vn_fks.add(
                (
                    f"{vn_table}.{col_map[fk_col]}",
                    f"{parent_vn}.{parent_map[parent_pk_sql]}",
                )
            )
            nullable = "NOT NULL" not in sql_cols_defs[fk_col]
            single_unique = frozenset({fk_col}) in sql_unique
            expected_mermaid.add(
                (
                    parent_sql,
                    "|o" if nullable else "||",
                    "o|" if single_unique else "o{",
                    sql_table,
                )
            )

    require(
        len(expected_vn_fks) == 17,
        f"Phải có đúng 17 khóa ngoại, thực tế: {len(expected_vn_fks)}",
    )
    return vn_cols_by_table, vn_pks_by_table, expected_vn_fks, expected_mermaid


def verify_er_drawio(
    er_xml: str,
    vn_cols_by_table: dict[str, list[str]],
    vn_pks_by_table: dict[str, set[str]],
    expected_vn_fks: set[tuple[str, str]],
) -> None:
    root = parse_xml(er_xml)
    cells = root.findall("./diagram/mxGraphModel/root/mxCell")
    by_id = {c.get("id", ""): c for c in cells if c.get("id")}
    require(len(by_id) == len(cells), "Trùng hoặc thiếu ID trong ER.drawio")

    # Mọi cạnh phải gắn source và target đầy đủ
    edges = [c for c in cells if c.get("edge") == "1"]
    for edge in edges:
        src, dst = edge.get("source", ""), edge.get("target", "")
        require(
            src in by_id and dst in by_id,
            f"Cạnh rời chưa gắn nút trong ER.drawio: {edge.get('id')}",
        )

    # Kiểm tra 12 thực thể khỏe, 2 thực thể kết hợp (hộp chữ nhật + hình thoi cùng tọa độ), 13 hình thoi quan hệ 2 ngôi
    rects = []
    rhombuses = []
    ellipses = []
    card_labels = []
    for cell in cells:
        if cell.get("vertex") != "1":
            continue
        st = cell.get("style", "")
        val = strip_html(cell.get("value"))
        geom = cell.find("mxGeometry")
        require(geom is not None, f"Thiếu mxGeometry: {cell.get('id')}")
        pos = (
            round(float(geom.get("x", "0")), 1),
            round(float(geom.get("y", "0")), 1),
            round(float(geom.get("width", "0")), 1),
            round(float(geom.get("height", "0")), 1),
        )
        if "ellipse" in st:
            ellipses.append((cell, val))
        elif "rhombus" in st:
            rhombuses.append((cell, val, pos))
        elif "text;" in st:
            if val in ("1", "N", "M"):
                card_labels.append(val)
        else:
            rects.append((cell, val, pos))

    rect_by_pos = {pos: (cell, val) for cell, val, pos in rects}
    assoc_names = {val for _, val, pos in rhombuses if pos in rect_by_pos}
    strong_names = {val for _, val, _ in rects if val}
    binary_rhombuses = [val for _, val, pos in rhombuses if pos not in rect_by_pos]

    require(
        assoc_names == {"PHIM_THE_LOAI", "KHOA_GHE_SUAT_CHIEU"},
        f"Sai thực thể kết hợp trên ER.drawio: {assoc_names}",
    )
    require(
        strong_names | assoc_names == set(vn_cols_by_table.keys()),
        "Tập 14 thực thể/thực thể kết hợp trên ER.drawio không khớp 14 bảng",
    )
    require(
        len(binary_rhombuses) == 13,
        f"ER.drawio phải có 13 hình thoi quan hệ hai ngôi, thực tế: {len(binary_rhombuses)}",
    )
    require(
        len(ellipses) == 59,
        f"ER.drawio chỉ được chứa đúng 59 thuộc tính bản thân (0 FK), thực tế: {len(ellipses)}",
    )
    require(
        len(card_labels) == 34,
        f"ER.drawio phải có đủ 34 nhãn bản số 1/N/M (26 cho 13 quan hệ hai ngôi + 8 cho 2 thực thể kết hợp), thực tế: {len(card_labels)}",
    )

    # Kiểm tra thuộc tính gắn đúng từng thực thể khỏe và gạch chân khóa chính
    fk_cols_by_table: dict[str, set[str]] = defaultdict(set)
    for child_fk, _ in expected_vn_fks:
        t_name, c_name = child_fk.split(".", 1)
        fk_cols_by_table[t_name].add(c_name)

    entity_id_to_name = {cell.get("id", ""): val for cell, val, _ in rects if val}
    attrs_per_entity: dict[str, set[str]] = defaultdict(set)
    pks_per_entity: dict[str, set[str]] = defaultdict(set)
    ellipse_ids = {cell.get("id", ""): (cell, val) for cell, val in ellipses}

    pair_lines: Counter[tuple[str, str]] = Counter()
    for edge in edges:
        s_id, t_id = edge.get("source", ""), edge.get("target", "")
        if s_id in ellipse_ids or t_id in ellipse_ids:
            e_id, ent_id = (s_id, t_id) if s_id in ellipse_ids else (t_id, s_id)
            require(
                ent_id in entity_id_to_name,
                f"Thuộc tính {e_id} không nối vào thực thể khỏe hợp lệ",
            )
            e_cell, attr_val = ellipse_ids[e_id]
            ent_name = entity_id_to_name[ent_id]
            attrs_per_entity[ent_name].add(attr_val)
            st = e_cell.get("style", "")
            raw = e_cell.get("value", "")
            if "fontStyle=4" in st or "<u>" in raw:
                pks_per_entity[ent_name].add(attr_val)
        else:
            pair = tuple(sorted((s_id, t_id)))
            pair_lines[pair] += 2 if "shape=link" in edge.get("style", "") else 1

    for vn_table, all_cols in vn_cols_by_table.items():
        if vn_table in assoc_names:
            require(
                vn_table not in attrs_per_entity,
                f"Thực thể kết hợp {vn_table} không có thuộc tính riêng",
            )
            continue
        expected_intrinsic = set(all_cols) - fk_cols_by_table[vn_table]
        require(
            attrs_per_entity[vn_table] == expected_intrinsic,
            f"Sai tập thuộc tính bản thân của {vn_table} trên ER.drawio: {attrs_per_entity[vn_table]} != {expected_intrinsic}",
        )
        require(
            pks_per_entity[vn_table] == vn_pks_by_table[vn_table],
            f"Sai gạch chân khóa chính của {vn_table} trên ER.drawio",
        )

    double_line_count = sum(1 for cnt in pair_lines.values() if cnt == 2)
    require(
        double_line_count == 13,
        f"ER.drawio phải có đúng 13 nhánh tham gia đầy đủ (nét đôi), thực tế: {double_line_count}",
    )


def verify_relational_drawio(
    rel_xml: str,
    vn_cols_by_table: dict[str, list[str]],
    vn_pks_by_table: dict[str, set[str]],
    expected_vn_fks: set[tuple[str, str]],
) -> None:
    root = parse_xml(rel_xml)
    cells = root.findall("./diagram/mxGraphModel/root/mxCell")
    by_id = {c.get("id", ""): c for c in cells if c.get("id")}
    require(
        len(by_id) == len(cells), "Trùng hoặc thiếu ID trong luoc-do-quan-he.drawio"
    )

    def abs_rect(cid: str) -> tuple[float, float, float, float]:
        x = y = w = h = 0.0
        cur = by_id.get(cid)
        first = True
        while cur is not None:
            geom = cur.find("mxGeometry")
            if geom is not None:
                x += float(geom.get("x") or 0)
                y += float(geom.get("y") or 0)
                if first:
                    w = float(geom.get("width") or 0)
                    h = float(geom.get("height") or 0)
                    first = False
            pid = cur.get("parent", "")
            cur = by_id.get(pid) if pid not in ("", "0", "1") else None
        return x, y, w, h

    titles: list[tuple[str, float, float]] = []
    col_cells: list[tuple[ET.Element, str, float, float, float, float]] = []
    for cell in cells:
        if cell.get("vertex") != "1":
            continue
        val = strip_html(cell.get("value"))
        if not val:
            continue
        x, y, w, h = abs_rect(cell.get("id", ""))
        if val in vn_cols_by_table:
            titles.append((val, x, y))
        else:
            col_cells.append((cell, val, x, y, w, h))

    require(
        len(titles) == 14,
        f"luoc-do-quan-he.drawio phải có đủ 14 nhãn tên bảng, thực tế: {len(titles)}",
    )
    require(
        len(col_cells) == 76,
        f"luoc-do-quan-he.drawio phải có đủ 76 ô thuộc tính, thực tế: {len(col_cells)}",
    )

    def nearest_table(cx: float, cy: float) -> str:
        candidates = [
            (abs(cy - ty) * 10 + max(0.0, cx - tx) / 100.0, tname)
            for tname, tx, ty in titles
            if 0.0 <= cy - ty <= 70.0 and -20.0 <= cx - tx <= 1700.0
        ]
        require(bool(candidates), f"Không tìm thấy bảng cho ô tại ({cx}, {cy})")
        return min(candidates)[1]

    cols_grouped: dict[
        str, list[tuple[float, str, ET.Element, tuple[float, float, float, float]]]
    ] = defaultdict(list)
    col_boxes: dict[str, tuple[float, float, float, float]] = {}
    for cell, val, x, y, w, h in col_cells:
        tname = nearest_table(x, y)
        cols_grouped[tname].append((x, val, cell, (x, y, w, h)))
        col_boxes[f"{tname}.{val}"] = (x, y, w, h)

    for tname, expected_cols in vn_cols_by_table.items():
        ordered = [
            val
            for _, val, _, _ in sorted(cols_grouped[tname], key=lambda item: item[0])
        ]
        require(
            ordered == expected_cols,
            f"Danh sách/thứ tự thuộc tính bảng {tname} trên luoc-do-quan-he.drawio không khớp DDL: {ordered} != {expected_cols}",
        )
        # Kiểm tra gạch chân liền cho PK (bao gồm cả khóa ghép kiêm FK theo Slide 5 tr. 23, 25, 29, 33, 43)
        for _, val, cell, _ in cols_grouped[tname]:
            st = cell.get("style", "")
            raw = cell.get("value", "")
            has_solid_u = ("fontStyle=4" in st) or ("<u>" in raw)
            is_pk = val in vn_pks_by_table[tname]
            require(
                has_solid_u == is_pk,
                f"Quy ước gạch chân liền PK không đúng tại {tname}.{val} (expected={is_pk}, actual={has_solid_u})",
            )

    def col_containing(
        px: float, py: float, tol_x: float = 4.0, tol_y: float = 8.0
    ) -> str:
        matches = [
            full_name
            for full_name, (bx, by, bw, bh) in col_boxes.items()
            if bx - tol_x <= px <= bx + bw + tol_x
            and by - tol_y <= py <= by + bh + tol_y
        ]
        require(
            len(matches) == 1,
            f"Điểm ({px:.1f}, {py:.1f}) không nằm gọn trong đúng 1 ô thuộc tính: {matches}",
        )
        return matches[0]

    non_pk_fks = {
        src
        for src, _ in expected_vn_fks
        if src.split(".", 1)[1] not in vn_pks_by_table[src.split(".", 1)[0]]
    }
    dashed_cols: set[str] = set()
    arrow_pairs: set[tuple[str, str]] = set()

    for cell in cells:
        if cell.get("edge") != "1":
            continue
        st = style_map(cell.get("style"))
        geom = cell.find("mxGeometry")
        require(geom is not None, f"Thiếu mxGeometry trên cạnh {cell.get('id')}")
        if st.get("dashed") == "1" and st.get("endArrow") == "none":
            sp = geom.find("mxPoint[@as='sourcePoint']")
            tp = geom.find("mxPoint[@as='targetPoint']")
            require(
                sp is not None and tp is not None,
                f"Thiếu tọa độ đoạn gạch đứt: {cell.get('id')}",
            )
            sx, sy = float(sp.get("x", "0")), float(sp.get("y", "0"))
            tx, ty = float(tp.get("x", "0")), float(tp.get("y", "0"))
            col_left = col_containing(sx, sy, tol_x=0.0, tol_y=0.0)
            col_right = col_containing(tx, ty, tol_x=0.0, tol_y=0.0)
            require(
                col_left == col_right,
                f"Đoạn gạch đứt tràn qua ranh giới ô: {col_left} -> {col_right}",
            )
            dashed_cols.add(col_left)
        else:
            require(
                st.get("endArrow") == "classic" and "startArrow" not in st,
                f"Mũi tên khóa ngoại phải có đầu mũi tên một chiều tại đích: {cell.get('id')}",
            )
            src_id, dst_id = cell.get("source", ""), cell.get("target", "")
            require(
                src_id in by_id and dst_id in by_id,
                f"Mũi tên chưa gắn nguồn/đích: {cell.get('id')}",
            )
            sx, sy, sw, sh = abs_rect(src_id)
            dx, dy, dw, dh = abs_rect(dst_id)
            ex_x = sx + float(st.get("exitX", "0.5")) * sw
            ex_y = sy + float(st.get("exitY", "0.5")) * sh
            en_x = dx + float(st.get("entryX", "0.5")) * dw
            en_y = dy + float(st.get("entryY", "0.5")) * dh
            src_col = col_containing(ex_x, ex_y)
            dst_col = col_containing(en_x, en_y)
            arrow_pairs.add((src_col, dst_col))

    require(
        dashed_cols == non_pk_fks,
        f"Tập thuộc tính FK đơn gạch chân nét đứt không khớp Slide 5: thiếu={non_pk_fks - dashed_cols}, thừa={dashed_cols - non_pk_fks}",
    )
    require(
        arrow_pairs == expected_vn_fks,
        f"Tập 17 mũi tên FK -> PK không khớp 01b_foreign_keys.sql: thiếu={expected_vn_fks - arrow_pairs}, thừa={arrow_pairs - expected_vn_fks}",
    )


def verify_mermaid(
    erd_md: str, expected_mermaid: set[tuple[str, str, str, str]]
) -> None:
    actual = set(
        re.findall(
            r"^    (\w+) (\|\||\|o)--(o\{|\|\{|o\|) (\w+) :", erd_md, re.MULTILINE
        )
    )
    require(
        len(actual) == 17 and actual == expected_mermaid,
        "Sơ đồ Mermaid trong erd-explanation.md không khớp 17 khóa ngoại SQL",
    )


def main() -> None:
    schema_sql = (ROOT / "database/schema/01_schema.sql").read_text(encoding="utf-8")
    fk_sql = (ROOT / "database/schema/01b_foreign_keys.sql").read_text(encoding="utf-8")
    er_xml = (ROOT / "docs/erd/ER.drawio").read_text(encoding="utf-8")
    rel_xml = (ROOT / "docs/erd/luoc-do-quan-he.drawio").read_text(encoding="utf-8")
    erd_md = (ROOT / "docs/erd/erd-explanation.md").read_text(encoding="utf-8")

    vn_cols_by_table, vn_pks_by_table, expected_vn_fks, expected_mermaid = (
        parse_ddl_and_fks(schema_sql, fk_sql)
    )
    verify_er_drawio(er_xml, vn_cols_by_table, vn_pks_by_table, expected_vn_fks)
    verify_relational_drawio(
        rel_xml, vn_cols_by_table, vn_pks_by_table, expected_vn_fks
    )
    verify_mermaid(erd_md, expected_mermaid)
    verify_ticket_decomposition()

    print(
        "OK: ER.drawio khớp 14 thực thể (12 khỏe + 2 kết hợp), 59 thuộc tính bản thân, 13 quan hệ hai ngôi và 13 nhánh nét đôi."
    )
    print(
        "OK: luoc-do-quan-he.drawio khớp 14 bảng, 76 thuộc tính, gạch chân PK liền / FK đứt và đủ 17 mũi tên FK -> PK."
    )
    print(
        "OK: Phép tách chuẩn 3NF của bảng ticket bảo toàn phụ thuộc hàm và kết nối không mất thông tin."
    )


if __name__ == "__main__":
    main()
