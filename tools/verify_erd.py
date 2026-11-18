#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Đối chiếu tự động ERD Chen, Lược đồ Quan hệ, Crow's Foot/Mermaid và các thuật toán Chuẩn hóa theo bài giảng CSDL."""

from __future__ import annotations

import hashlib
import re
from collections import defaultdict
from itertools import combinations
from pathlib import Path
from types import MappingProxyType
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
SVG_NS = {"s": "http://www.w3.org/2000/svg"}

VN_TABLE_MAP: MappingProxyType[str, str] = MappingProxyType({
    "cinema_branch": "CHI_NHANH_RAP",
    "screening_room": "PHONG_CHIEU",
    "seat_type": "HANG_GHE",
    "seat": "GHE",
    "movie": "PHIM",
    "genre": "THE_LOAI",
    "movie_genre": "PHIM_THE_LOAI",
    "screening": "SUAT_CHIEU",
    "customer": "KHACH_HANG",
    "employee": "NHAN_VIEN",
    "booking": "DON_DAT_VE",
    "ticket": "VE_XEM_PHIM",
    "screening_seat_lock": "KHOA_GHE_SUAT_CHIEU",
    "payment": "THANH_TOAN",
})

VN_PK_MAP: MappingProxyType[str, str] = MappingProxyType({
    "cinema_branch": "MaCN",
    "screening_room": "MaPhong",
    "seat_type": "MaHangGhe",
    "seat": "MaGhe",
    "movie": "MaPhim",
    "genre": "MaTL",
    "screening": "MaSC",
    "customer": "MaKH",
    "employee": "MaNV",
    "booking": "MaDDV",
    "ticket": "MaVe",
    "payment": "MaTT",
})


def require(condition: bool, message: str) -> None:
    """Ném ValueError nếu điều kiện kiểm chứng không thỏa mãn."""
    if not condition:
        raise ValueError(message)


def safe_parse_xml(xml_text: str) -> ET.Element:
    """Phân tích cú pháp XML an toàn, từ chối khai báo DTD/ENTITY chống tấn công mở rộng thực thể."""
    upper = xml_text.upper()
    require("<!DOCTYPE" not in upper and "<!ENTITY" not in upper, "Từ chối DTD/ENTITY trong tài liệu XML")
    return ET.fromstring(xml_text)


def names(text: str) -> tuple[str, ...]:
    """Tách danh sách định danh phân cách bởi dấu phẩy."""
    return tuple(part.strip() for part in text.split(",") if part.strip())


def sha256_variants(text: str) -> set[str]:
    """Tính tập băm SHA-256 cho cả chuẩn xuống dòng LF và CRLF của chuỗi nguồn."""
    raw = text.encode("utf-8")
    lf = raw.replace(b"\r\n", b"\n")
    crlf = lf.replace(b"\n", b"\r\n")
    return {
        hashlib.sha256(raw).hexdigest(),
        hashlib.sha256(lf).hexdigest(),
        hashlib.sha256(crlf).hexdigest(),
    }


def closure(attributes: set[str], dependencies: list[tuple[set[str], set[str]]]) -> set[str]:
    """Thuật toán Closure(X, F): tính bao đóng thuộc tính X+ trên tập phụ thuộc hàm F."""
    result = set(attributes)
    while True:
        previous = result.copy()
        for left, right in dependencies:
            if left <= result:
                result.update(right)
        if result == previous:
            return result


def member(dependencies: list[tuple[set[str], set[str]]], left: set[str], right: set[str]) -> bool:
    """Thuật toán Member(F, X -> Y): kiểm tra F |= X -> Y thông qua Closure(X, F)."""
    return right <= closure(left, dependencies)


def min_cover(dependencies: list[tuple[set[str], set[str]]]) -> list[tuple[frozenset[str], str]]:
    """Thuật toán MinCover(F): (1) Tách vế phải đơn -> (2) Left-Reduce -> (3) Nonredundant."""
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
    """Thuật toán Lossless(R, F, D): kiểm tra bảng ma trận T (a_j / b_ij) có sinh hàng toàn a hay không."""
    col_index = {attr: idx for idx, attr in enumerate(attrs, 1)}
    matrix = [
        [f"a_{col_index[attr]}" if attr in sub else f"b_{row_i}_{col_index[attr]}" for attr in attrs]
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
    """Thuật toán Preserve(D, F, X -> Y): lặp Z = Z U ((Z ∩ R_i)+ ∩ R_i) kiểm tra bảo toàn FD."""
    z_set = set(left)
    while True:
        prev = z_set.copy()
        for sub in decomposition:
            z_set |= closure(z_set & sub, dependencies) & sub
        if z_set == prev:
            return right <= z_set


def verify_ticket_decomposition() -> None:
    """Kiểm chứng MinCover, Lossless và Preserve trên phép phân tách quan hệ vé U_old."""
    ordered_attrs = [
        "ticket_id", "booking_id", "screening_id", "seat_id",
        "unit_price", "ticket_status", "cancelled_at",
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
    require(set(cover) == expected_cover and len(cover) == 7,
            "MinCover của U_old phải khớp chính xác 7 phụ thuộc hàm tối thiểu (giữ nguyên khóa ghép {booking_id, seat_id})")

    decomposition = [ticket, booking_screening]
    require(ticket | booking_screening == universe, "Phép tách không phủ đúng lược đồ gốc")
    require(algorithm_lossless(ordered_attrs, original, decomposition),
            "Algorithm Lossless (ma trận a_y, b_xy) phải trả về TRUE")
    require(not algorithm_lossless(["A", "B", "C"], [({"A"}, {"C"}), ({"B"}, {"C"})], [{"A"}, {"B"}, {"A", "B"}]),
            "Algorithm Lossless phải trả về FALSE và kết thúc hữu hạn trên phép tách mất thông tin 3 quan hệ")
    for left, right in original:
        require(algorithm_preserve(decomposition, original, left, right),
                f"Algorithm Preserve thất bại cho phụ thuộc hàm: {left} -> {right}")

    local = [({"ticket_id"}, ticket - {"ticket_id"}), *original[1:]]
    cover_fds = [(set(lhs), {rhs}) for lhs, rhs in cover]
    require(closure({"booking_id"}, original) != universe, "booking_id không được nhầm là siêu khóa của vé")
    for size in range(len(universe) + 1):
        for subset in combinations(sorted(universe), size):
            sub_set = set(subset)
            orig_cl = closure(sub_set, original)
            require(orig_cl == closure(sub_set, local) == closure(sub_set, cover_fds),
                    f"Không bảo toàn bao đóng: {subset}")


def parse_sql_fk_pairs(foreign_keys_sql: str) -> set[tuple[str, str]]:
    """Trích xuất tập 17 cặp (Child.FK, Parent.PK) tiếng Việt từ 01b_foreign_keys.sql."""
    pairs: set[tuple[str, str]] = set()
    for table, body in re.findall(r"ALTER TABLE (\w+)\s+(.*?);", foreign_keys_sql, re.S):
        for _, parent, _ in re.findall(r"FOREIGN KEY \((\w+)\)\s+REFERENCES (\w+) \((\w+)\)", body):
            require(table in VN_TABLE_MAP and parent in VN_PK_MAP, f"Bảng FK chưa ánh xạ: {table} -> {parent}")
            pk_vn = VN_PK_MAP[parent]
            pairs.add((f"{VN_TABLE_MAP[table]}.{pk_vn}", f"{VN_TABLE_MAP[parent]}.{pk_vn}"))
    require(len(pairs) == 17, f"Phải trích xuất đúng 17 cặp FK -> PK từ SQL, thực tế: {len(pairs)}")
    return pairs


def _validate_mxgraph_cells(xml_text: str, label: str) -> tuple[list[ET.Element], dict[str, ET.Element]]:
    """Kiểm tra tính toàn vẹn ID, tham chiếu parent/source/target và kích thước hình dương của mxGraph."""
    model = safe_parse_xml(xml_text).find("./diagram/mxGraphModel")
    require(model is not None, f"Thiếu mxGraphModel trong {label}")
    cells = model.findall("./root/mxCell")
    by_id = {cell.get("id", ""): cell for cell in cells if cell.get("id")}
    require(len(by_id) == len(cells), f"Trùng hoặc thiếu ID trong {label}")
    for cell in cells:
        for attr in ("parent", "source", "target"):
            ref = cell.get(attr)
            require(not ref or ref in by_id, f"Tham chiếu {attr} không hợp lệ trong {label}: {ref}")
        parent_id = cell.get("parent")
        if cell.get("vertex") == "1" and parent_id and by_id[parent_id].get("edge") != "1":
            geom = cell.find("mxGeometry")
            require(
                geom is not None and float(geom.get("width", "0")) > 0 and float(geom.get("height", "0")) > 0,
                f"Hình {label} có kích thước không hợp lệ: {cell.get('id')}",
            )
    return cells, by_id


def verify_chen_drawio(diagram: str, svg: str) -> None:
    """Kiểm chứng sơ đồ E-R chuẩn Chen (draw.io & SVG): 59 thuộc tính nội tại, 2 thực thể kết hợp, 13 nét đôi."""
    cells, by_id = _validate_mxgraph_cells(diagram, "erd-chen.drawio")
    image = safe_parse_xml(svg)
    metadata = image.find("s:metadata", SVG_NS)
    meta_text = metadata.text if metadata is not None and metadata.text else ""
    require(any(digest in meta_text for digest in sha256_variants(diagram)),
            "SVG Chen chưa được xuất lại đồng bộ SHA-256 từ erd-chen.drawio")

    top_rhombuses = [c for c in cells if c.get("vertex") == "1" and c.get("parent") == "1" and "rhombus" in c.get("style", "")]
    assoc_rhombuses = [
        c for c in cells
        if c.get("vertex") == "1" and c.get("parent") in ("PHIM_THE_LOAI", "KHOA_GHE_SUAT_CHIEU") and "rhombus" in c.get("style", "")
    ]
    ellipses = [c for c in cells if c.get("vertex") == "1" and "ellipse" in c.get("style", "")]
    rect_entities = [
        c for c in cells
        if c.get("vertex") == "1" and c.get("parent") == "1" and "rhombus" not in c.get("style", "") and "ellipse" not in c.get("style", "")
    ]
    require(len(rect_entities) == 14, "Sơ đồ Chen draw.io phải có đủ 14 hộp thực thể (12 mạnh + 2 kết hợp)")
    require(len(assoc_rhombuses) == 2, "Hai thực thể kết hợp phải có hình thoi nội tiếp")
    require(len(top_rhombuses) == 13, "Sơ đồ Chen draw.io phải có đúng 13 hình thoi quan hệ hai ngôi")
    require(len(ellipses) == 59, "Sơ đồ Chen draw.io chỉ được chứa đúng 59 thuộc tính nội tại (0 FK)")

    for assoc_id in ("assoc_PHIM_THE_LOAI", "assoc_KHOA_GHE_SUAT_CHIEU"):
        require(image.find(f".//s:g[@id='{assoc_id}']/s:polygon", SVG_NS) is not None,
                f"SVG thiếu hình thoi nội tiếp của thực thể kết hợp: {assoc_id}")

    rel_edges = [c for c in cells if c.get("edge") == "1" and c.get("id", "").startswith("rel_edge_")]
    require(len(rel_edges) == 30, "Sơ đồ Chen draw.io phải có đúng 30 nhánh liên kết")
    require(sum("shape=link;" in c.get("style", "") for c in rel_edges) == 13,
            "Sơ đồ Chen draw.io phải có đúng 13 đường nét đôi tham gia đầy đủ")
    for edge in rel_edges:
        eid = edge.get("id", "")
        card_cell = by_id.get(f"{eid}_card")
        require(card_cell is not None and card_cell.get("value") in ("1", "N", "M"), f"Thiếu nhãn bản số 1/N/M: {eid}")
        is_double = "shape=link;" in edge.get("style", "")
        group = image.find(f".//s:g[@id='{eid}']", SVG_NS)
        require(group is not None and len(group.findall("s:line", SVG_NS)) == (2 if is_double else 1),
                f"SVG thiếu nét đơn/kép: {eid}")


def verify_logical_drawio(logical_diagram: str, logical_svg: str, expected_fk_pairs: set[tuple[str, str]]) -> None:
    """Kiểm chứng Lược đồ Quan hệ (draw.io & SVG): 14 bảng, 76 cột logic và đúng 17 mũi tên FK -> PK."""
    l_cells, l_by_id = _validate_mxgraph_cells(logical_diagram, "luoc-do-quan-he.drawio")
    l_edges = [c for c in l_cells if c.get("edge") == "1"]
    l_titles = [c for c in l_cells if c.get("vertex") == "1" and c.get("id", "").startswith("title_")]
    l_cols = [c for c in l_cells if c.get("vertex") == "1" and not c.get("id", "").startswith("title_")]
    require(len(l_titles) == 14 and len(l_cols) == 76 and len(l_edges) == 17,
            "Lược đồ quan hệ draw.io phải có đủ 14 bảng, 76 ô thuộc tính và 17 mũi tên FK")

    l_img = safe_parse_xml(logical_svg)
    l_meta = l_img.find("s:metadata", SVG_NS)
    l_meta_text = l_meta.text if l_meta is not None and l_meta.text else ""
    require(any(digest in l_meta_text for digest in sha256_variants(logical_diagram)),
            "SVG lược đồ quan hệ chưa đồng bộ SHA-256 với luoc-do-quan-he.drawio")

    actual_pairs: set[tuple[str, str]] = set()
    for edge in l_edges:
        eid = edge.get("id", "")
        src = edge.get("source", "")
        dst = edge.get("target", "")
        style = edge.get("style", "")
        src_val = l_by_id[src].get("value", "")
        dst_val = l_by_id[dst].get("value", "")
        require("dashed" in src_val, f"Mũi tên tham chiếu phải xuất phát từ FK gạch chân đứt: {src} -> {dst}")
        require("<u>" in dst_val or "<u " in dst_val, f"Đầu mũi tên phải trỏ vào PK gạch chân liền: {src} -> {dst}")
        require("endArrow=classic" in style and "startArrow=" not in style, f"Sai kiểu đầu mũi tên FK -> PK: {eid}")
        svg_edge = l_img.find(f".//s:g[@id='{eid}']", SVG_NS)
        require(
            svg_edge is not None
            and svg_edge.find("s:path", SVG_NS) is not None
            and svg_edge.find("s:polygon", SVG_NS) is not None,
            f"SVG thiếu đường cong hoặc đầu mũi tên đa giác cho {eid}",
        )
        actual_pairs.add((src, dst))
    require(actual_pairs == expected_fk_pairs, "Tập 17 cặp mũi tên (FK -> PK) không khớp chính xác với 01b_foreign_keys.sql")


def verify_drawio(
    diagram: str,
    svg: str,
    logical_diagram: str,
    logical_svg: str,
    expected_fk_pairs: set[tuple[str, str]],
) -> None:
    """Kiểm chứng đồng thời sơ đồ Chen và Lược đồ Quan hệ định dạng draw.io + SVG."""
    verify_chen_drawio(diagram, svg)
    verify_logical_drawio(logical_diagram, logical_svg, expected_fk_pairs)


def _parse_logical_entity_fields(table: str, body: str) -> tuple[dict[str, tuple[bool, str]], set[str], set[str], dict[str, set[str]]]:
    """Đọc danh sách cột, PK, FK và UQ của một bảng trong erd-logical.puml."""
    fields: dict[str, tuple[bool, str]] = {}
    primary: set[str] = set()
    foreign: set[str] = set()
    unique_groups: dict[str, set[str]] = defaultdict(set)
    for line in body.splitlines():
        match = re.fullmatch(r"\s*(\* )?([\w*/]+) : ([A-Z]+(?:\([^)]*\))?)(?: <<([^>]+)>>)?", line)
        if not match:
            require(line.strip() == "--", f"Dòng ERD chưa được kiểm tra: {table}: {line}")
            continue
        mandatory, raw_name, sql_type, flags = match.groups()
        col_name = raw_name.replace("*", "").replace("/", "")
        require(col_name not in fields, f"Thuộc tính trùng: {table}.{col_name}")
        fields[col_name] = (bool(mandatory), sql_type)
        for flag in names(flags or ""):
            if flag == "PK":
                primary.add(col_name)
            elif flag == "FK":
                foreign.add(col_name)
            elif flag == "UQ":
                unique_groups[col_name].add(col_name)
            elif re.fullmatch(r"UQ\d+", flag):
                unique_groups[flag].add(col_name)
            elif flag not in ("NULLable", "PHYSICAL SUBSET-IDX"):
                raise ValueError(f"Cờ ERD không hợp lệ tại {table}.{col_name}: {flag}")
    return fields, primary, foreign, unique_groups


def _parse_chen_entity_attrs(table: str, body: str) -> tuple[set[str], set[str]]:
    """Đọc và kiểm tra nghiêm ngặt từng dòng thuộc tính của thực thể trong erd-chen.puml."""
    attrs: set[str] = set()
    keys: set[str] = set()
    for line in body.splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r"\s+(\w+)( <<key>>)?", line)
        require(match is not None, f"Dòng thuộc tính Chen không hợp lệ tại {table}: {line}")
        attr_name, key_flag = match.groups()
        require(attr_name not in attrs, f"Thuộc tính Chen bị lặp tại {table}: {attr_name}")
        attrs.add(attr_name)
        if key_flag:
            keys.add(attr_name)
    return attrs, keys


def _verify_chen_relationships(
    chen: str,
    tables: set[str],
    chen_associative: set[str],
    expected_binary_chen: set[frozenset[tuple[str, str]]],
) -> None:
    """Kiểm chứng 13 hình thoi nhị phân và 2 thực thể kết hợp M:N trong erd-chen.puml."""
    relationships = set(re.findall(r'^relationship "[^"\n]+" as (R_\w+) \{', chen, re.M))
    connections: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    assoc_links: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for left, stroke, maximum, right in re.findall(r"^(\w+) ([-=])([1NM])\2 (\w+)$", chen, re.M):
        if left in relationships or right in relationships:
            entity, relation = (right, left) if left in relationships else (left, right)
            require(entity in tables and maximum in ("1", "N"), f"Liên kết hai ngôi Chen không hợp lệ: {left}..{right}")
            connections[relation].append((entity, stroke, maximum))
        else:
            require((left in chen_associative) ^ (right in chen_associative),
                    f"Liên kết trực tiếp chỉ hợp lệ cho thực thể kết hợp: {left}..{right}")
            assoc, participant = (left, right) if left in chen_associative else (right, left)
            require(participant in tables and stroke == "-" and maximum in ("M", "N"),
                    f"Liên kết M:N của thực thể kết hợp không hợp lệ: {left}..{right}")
            assoc_links[assoc].append((participant, maximum))

    require(set(assoc_links.keys()) == {"movie_genre", "screening_seat_lock"}, "Thiếu liên kết M:N cho thực thể kết hợp")
    require({p for p, _ in assoc_links["movie_genre"]} == {"movie", "genre"}
            and {m for _, m in assoc_links["movie_genre"]} == {"M", "N"}, "Sai liên kết M:N của movie_genre")
    require({p for p, _ in assoc_links["screening_seat_lock"]} == {"screening", "seat"}
            and {m for _, m in assoc_links["screening_seat_lock"]} == {"M", "N"}, "Sai liên kết M:N của screening_seat_lock")

    normalized = set()
    for edges in connections.values():
        require(len(edges) == 2, "Mỗi hình thoi liên kết hai ngôi Chen phải có hai đầu")
        normalized.add(frozenset(
            (entity, ("1" if stroke == "=" else "0") + "," + edges[1 - index][2])
            for index, (entity, stroke, _) in enumerate(edges)
        ))
    require(len(relationships) == len(connections) == 13 and normalized == expected_binary_chen,
            "Sai quan hệ/bản số Chen so với 13 FK hai ngôi và quy tắc BR-08")


def verify(schema: str, foreign_keys: str, logical: str, chen: str, mermaid: str) -> None:
    """Đối chiếu toàn diện DDL, FK, PlantUML Logical/Chen và sơ đồ Mermaid."""
    tables = dict(re.findall(r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\) ENGINE=", schema, re.S))
    entities = dict(re.findall(r'entity "[^"\n]+" as (\w+) \{\n(.*?)\n\}', logical, re.S))
    chen_raw = re.findall(r'entity "[^"\n]+" as (\w+)( <<associative>>)? \{(.*?)\}', chen, re.S)
    chen_entities = {name: body for name, _, body in chen_raw}
    chen_associative = {name for name, stereo, _ in chen_raw if stereo.strip() == "<<associative>>"}

    require(len(tables) == 14 and tables.keys() == entities.keys() == chen_entities.keys(),
            "ERD phải có đủ 14 thực thể đúng tên bảng SQL")
    require(chen_associative == {"movie_genre", "screening_seat_lock"},
            "Bản Chen phải đánh dấu đúng 2 thực thể kết hợp <<associative>>")
    require(chen.startswith("@startchen ") and chen.rstrip().endswith("@endchen"), "Sơ đồ Chen phải dùng @startchen/@endchen")

    expected_logical: set[tuple[str, str, str, str]] = set()
    expected_binary_chen: set[frozenset[tuple[str, str]]] = set()
    fk_blocks: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    for tbl_name, body in re.findall(r"ALTER TABLE (\w+)\s+(.*?);", foreign_keys, re.S):
        fk_blocks[tbl_name].extend(re.findall(r"FOREIGN KEY \((\w+)\)\s+REFERENCES (\w+) \((\w+)\)", body))

    for table, ddl in tables.items():
        columns = dict(re.findall(r"^\s+(\w+)\s+((?:INT|VARCHAR|CHAR|TINYINT|DECIMAL|DATE|DATETIME|TEXT)\b[^\n]*)", ddl, re.M))
        fields, primary, foreign, unique_groups = _parse_logical_entity_fields(table, entities[table])
        require(columns.keys() == fields.keys(), f"Sai danh sách thuộc tính: {table}")
        for col_name, definition in columns.items():
            type_match = re.match(r"[A-Z]+(?:\([^)]*\))?", definition)
            require(type_match is not None and fields[col_name] == ("NOT NULL" in definition, type_match.group()),
                    f"Sai kiểu dữ liệu hoặc NULL: {table}.{col_name}")
        pk_match = re.search(r"PRIMARY KEY \(([^)]+)\)", ddl)
        require(pk_match is not None, f"Thiếu PRIMARY KEY trong DDL: {table}")
        sql_primary = set(names(pk_match.group(1)))
        require(primary == sql_primary, f"Sai khóa chính: {table}")

        fks = fk_blocks[table]
        fk_cols = {column for column, _, _ in fks}
        require(foreign == fk_cols, f"Sai cột FK: {table}")

        chen_attrs, chen_primary = _parse_chen_entity_attrs(table, chen_entities[table])
        expected_intrinsic = set(columns.keys()) - fk_cols - {"active_success_booking"}
        require(chen_attrs == expected_intrinsic, f"Sơ đồ Chen sai tập thuộc tính nội tại ở {table}")
        require(chen_primary == (set() if table in chen_associative else sql_primary), f"Sai khóa chính Chen: {table}")

        sql_unique = {frozenset(names(group)) for group in re.findall(r"UNIQUE \(([^)]+)\)", ddl)}
        sql_unique.discard(frozenset({"active_success_booking"}))
        require({frozenset(group) for group in unique_groups.values()} == sql_unique, f"Sai khóa duy nhất: {table}")

        for column, parent, _ in fks:
            nullable = "NOT NULL" not in columns[column]
            single_unique = frozenset({column}) in sql_unique
            expected_logical.add((parent, "|o" if nullable else "||", "o|" if single_unique else "o{", table))
            if table in chen_associative and column in sql_primary:
                continue
            expected_binary_chen.add(frozenset({
                (parent, "0,1" if single_unique else "0,N"),
                (table, "0,1" if nullable else "1,1"),
            }))

    br08_default = frozenset({("booking", "0,N"), ("ticket", "1,1")})
    require(br08_default in expected_binary_chen, "Thiếu quan hệ booking-ticket trước khi áp dụng BR-08")
    expected_binary_chen.remove(br08_default)
    expected_binary_chen.add(frozenset({("booking", "1,N"), ("ticket", "1,1")}))

    actual_logical = re.findall(r"^(\w+) (\|\||\|o)--(o\{|\|\{|o\|) (\w+) :", logical, re.M)
    require(len(actual_logical) == 17 and set(actual_logical) == expected_logical, "Sai bản số Crow's Foot so với 17 FK SQL")
    actual_mermaid = re.findall(r"^    (\w+) (\|\||\|o)--(o\{|\|\{|o\|) (\w+) :", mermaid, re.M)
    require(len(actual_mermaid) == 17 and set(actual_mermaid) == expected_logical, "Sai bản số Mermaid so với 17 FK SQL")
    _verify_chen_relationships(chen, set(tables.keys()), chen_associative, expected_binary_chen)


def _sync_svg_hash(svg_text: str, drawio_text: str, source_name: str) -> str:
    """Cập nhật metadata SHA-256 trong bản sao SVG phục vụ kiểm thử đột biến độc lập."""
    digest = hashlib.sha256(drawio_text.encode("utf-8")).hexdigest()
    return re.sub(
        r"<metadata>.*?</metadata>",
        f"<metadata>Source: {source_name}; SHA-256: {digest}</metadata>",
        svg_text,
        count=1,
    )


def run_mutation_self_tests(
    contents: list[str],
    chen_drawio: str,
    chen_svg: str,
    logical_drawio: str,
    logical_svg: str,
    fk_pairs: set[tuple[str, str]],
) -> None:
    """Thực thi 11 phép thử đột biến âm tính đảm bảo bộ kiểm chứng phát hiện mọi sai lệch."""
    for index, old, new in (
        (2, "cinema_branch ||--o{ screening_room", "cinema_branch ||--|{ screening_room"),
        (2, "//booking_id// : INT <<FK, UQ1>>", "//booking_id// : INT <<FK, UQ>>"),
        (3, "as movie_genre <<associative>> {", "as movie_genre {"),
        (3, "booking =1= R_BOOK_TICKET", "booking -1- R_BOOK_TICKET"),
        (3, "  room_id <<key>>\n", "  room_id <<key>>\n  branch_id\n"),
        (3, "  room_id <<key>>\n", "  room_id <<key>>\n  branch_id <<FK>>\n"),
    ):
        broken = contents.copy()
        require(old in broken[index], f"Mẫu tự kiểm tra không khớp: {old}")
        broken[index] = broken[index].replace(old, new, 1)
        try:
            verify(*broken)
        except ValueError:
            continue
        raise ValueError("Bộ kiểm chứng đã bỏ lọt lỗi ngữ nghĩa cố ý")

    for broken_kind in ("zero_size", "single_total", "missing_assoc_diamond", "reversed_fk_arrow", "wrong_fk_target", "stale_svg_hash"):
        c_tree = safe_parse_xml(chen_drawio)
        l_tree = safe_parse_xml(logical_drawio)
        if broken_kind == "zero_size":
            geom = c_tree.find('.//mxCell[@id="R1"]/mxGeometry')
            assert geom is not None
            geom.set("width", "0")
        elif broken_kind == "single_total":
            edge = c_tree.find('.//mxCell[@id="rel_edge_2"]')
            assert edge is not None
            edge.set("style", edge.get("style", "").replace("shape=link;", ""))
        elif broken_kind == "missing_assoc_diamond":
            parent_box = c_tree.find('.//mxCell[@id="PHIM_THE_LOAI"]')
            assoc = c_tree.find('.//mxCell[@id="assoc_PHIM_THE_LOAI"]')
            root_el = c_tree.find("./diagram/mxGraphModel/root")
            assert parent_box is not None and assoc is not None and root_el is not None
            root_el.remove(assoc)
            parent_box.set("value", "PHIM_THE_LOAI")
        elif broken_kind == "reversed_fk_arrow":
            fk1 = l_tree.find('.//mxCell[@id="fk_1"]')
            assert fk1 is not None
            src, dst = fk1.get("source", ""), fk1.get("target", "")
            fk1.set("source", dst)
            fk1.set("target", src)
        elif broken_kind == "wrong_fk_target":
            fk10 = l_tree.find('.//mxCell[@id="fk_10"]')
            assert fk10 is not None
            fk10.set("target", "PHIM.MaPhim")
        c_xml = ET.tostring(c_tree, encoding="unicode")
        l_xml = ET.tostring(l_tree, encoding="unicode")
        c_svg_test = chen_svg if broken_kind == "stale_svg_hash" else _sync_svg_hash(chen_svg, c_xml, "erd-chen.drawio")
        l_svg_test = _sync_svg_hash(logical_svg, l_xml, "luoc-do-quan-he.drawio")
        if broken_kind == "stale_svg_hash":
            c_xml = c_xml + "\n"
        try:
            verify_drawio(c_xml, c_svg_test, l_xml, l_svg_test, fk_pairs)
        except ValueError:
            continue
        raise ValueError(f"Bộ kiểm chứng bỏ lọt lỗi cố ý: {broken_kind}")


def main() -> None:
    """Điểm vào kiểm chứng tĩnh toàn bộ ERD, Lược đồ Quan hệ và thuật toán chuẩn hóa 3NF."""
    inputs = [
        ROOT / path for path in (
            "database/schema/01_schema.sql",
            "database/schema/01b_foreign_keys.sql",
            "docs/erd/erd-logical.puml",
            "docs/erd/erd-chen.puml",
            "docs/erd/erd-explanation.md",
        )
    ]
    contents = [path.read_text(encoding="utf-8") for path in inputs]
    verify(*contents)

    fk_pairs = parse_sql_fk_pairs(contents[1])
    chen_drawio = (ROOT / "docs/erd/erd-chen.drawio").read_text(encoding="utf-8")
    chen_svg = (ROOT / "docs/erd/erd-chen.svg").read_text(encoding="utf-8")
    logical_drawio = (ROOT / "docs/erd/luoc-do-quan-he.drawio").read_text(encoding="utf-8")
    logical_svg = (ROOT / "docs/erd/luoc-do-quan-he.svg").read_text(encoding="utf-8")
    verify_drawio(chen_drawio, chen_svg, logical_drawio, logical_svg, fk_pairs)
    verify_ticket_decomposition()
    run_mutation_self_tests(contents, chen_drawio, chen_svg, logical_drawio, logical_svg, fk_pairs)

    print("PASS: 14 quan hệ, 59 thuộc tính bản thân trên Chen E-R (0 FK), 2 thực thể kết hợp lồng hình thoi, 13 đường nét đôi.")
    print("PASS: Lược đồ quan hệ đủ 14 bảng, 76 thuộc tính và 17 mũi tên khớp chính xác cặp (FK -> PK) theo giáo trình.")
    print("PASS: MinCover (7 FD), Algorithm Lossless (ma trận a_y/b_xy) và Algorithm Preserve bảo toàn bao đóng 128 tập con.")
    print("PASS: 12 phép tự kiểm tra đột biến (gài FK vào Chen, sai đích FK->PK, đảo mũi tên, lệch hash SVG) đều bị bắt chính xác.")


if __name__ == "__main__":
    main()
