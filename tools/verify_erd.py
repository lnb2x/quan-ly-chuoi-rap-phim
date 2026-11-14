"""Đối chiếu ERD với DDL cục bộ; không kết nối hay thay đổi CSDL."""

import re
import hashlib
from itertools import combinations
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def names(text: str) -> tuple[str, ...]:
    return tuple(part.strip() for part in text.split(','))


def verify_ticket_decomposition() -> None:
    universe = {'ticket_id', 'booking_id', 'screening_id', 'seat_id',
                'unit_price', 'ticket_status', 'cancelled_at'}
    ticket = universe - {'screening_id'}
    booking_screening = {'booking_id', 'screening_id'}
    original = [({'ticket_id'}, universe - {'ticket_id'}),
                ({'booking_id', 'seat_id'}, {'ticket_id'}),
                ({'booking_id'}, {'screening_id'})]
    local = [({'ticket_id'}, ticket - {'ticket_id'}), *original[1:]]

    def closure(attributes, dependencies):
        result = set(attributes)
        while True:
            previous = result.copy()
            for left, right in dependencies:
                if left <= result:
                    result.update(right)
            if result == previous:
                return result

    require(ticket | booking_screening == universe, 'Phép tách không phủ đúng lược đồ gốc')
    require(booking_screening <= closure(ticket & booking_screening, original),
            'Phần giao không xác định một thành phần của phép tách')
    require(closure({'booking_id'}, original) != universe,
            'booking_id không được nhầm là siêu khóa của vé')
    for size in range(len(universe) + 1):
        for subset in combinations(sorted(universe), size):
            require(closure(subset, original) == closure(subset, local),
                    f'Không bảo toàn bao đóng: {subset}')


def verify_drawio(chen: str, diagram: str, svg: str) -> None:
    model = ET.fromstring(diagram).find('./diagram/mxGraphModel')
    cells = model.findall('./root/mxCell')
    by_id = {cell.get('id'): cell for cell in cells}
    require(len(by_id) == len(cells), 'Trùng ID draw.io')
    for cell in cells:
        for attr in ('parent', 'source', 'target'):
            require(not cell.get(attr) or cell.get(attr) in by_id, f'Tham chiếu draw.io không hợp lệ: {attr}')
        if cell.get('vertex') == '1' and by_id[cell.get('parent')].get('edge') != '1':
            geometry = cell.find('mxGeometry')
            require(float(geometry.get('width', '0')) > 0 and float(geometry.get('height', '0')) > 0,
                    f'Hình draw.io có kích thước không hợp lệ: {cell.get("id")}')
    image = ET.fromstring(svg)
    ns = {'s': 'http://www.w3.org/2000/svg'}
    metadata = image.find('s:metadata', ns)
    # read_text chuẩn hóa CRLF; dùng đúng bytes nguồn khi kiểm tra fingerprint.
    expected_hash = hashlib.sha256((ROOT / 'docs/erd/erd-chen.drawio').read_bytes()).hexdigest()
    require(metadata is not None and expected_hash in metadata.text, 'SVG chưa được xuất lại từ draw.io hiện tại')
    for ident in ('movie_genre', 'screening_seat_lock'):
        glyph = by_id.get(ident + '_association')
        require(glyph is not None and glyph.get('parent') == ident and 'rhombus;' in glyph.get('style', ''),
                f'Thiếu ký hiệu thực thể kết hợp: {ident}')
        require(image.find(f'.//s:g[@id="{ident}_association"]/s:polygon', ns) is not None,
                f'SVG thiếu hình thoi của thực thể kết hợp: {ident}')
    links = re.findall(r'^(\w+) ([-=])([1N])\2 (\w+)$', chen, re.M)
    for i, (left, stroke, maximum, right) in enumerate(links):
        ident = 'rel_' + str(i)
        edge = by_id[ident]
        require((edge.get('source'), edge.get('target')) == (left, right), f'Sai đầu nối draw.io: {ident}')
        require(by_id[ident + '_card'].get('value') == maximum, f'Sai bản số draw.io: {ident}')
        require(('shape=link;' in edge.get('style', '')) == (stroke == '='), f'Sai tham gia đầy đủ/một phần: {ident}')
        group = image.find(f'.//s:g[@id="{ident}"]', ns)
        require(group is not None and len(group.findall('s:line', ns)) == (2 if stroke == '=' else 1),
                f'SVG thiếu nét đơn/kép: {ident}')


def verify(schema: str, foreign_keys: str, logical: str, chen: str, mermaid: str) -> None:
    # Parser giới hạn ở cú pháp DDL/PlantUML đang dùng trong repo; fail khi thiếu mục.
    tables = dict(re.findall(r'CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\) ENGINE=', schema, re.S))
    entities = dict(re.findall(r'entity "[^"\n]+" as (\w+) \{\n(.*?)\n\}', logical, re.S))
    chen_entities = dict(re.findall(r'entity "[^"\n]+" as (\w+) \{\n(.*?)\n\}', chen, re.S))
    require(len(tables) == 14 and tables.keys() == entities.keys() == chen_entities.keys(),
            'ERD phải có đủ 14 thực thể đúng tên bảng SQL')
    require(chen.startswith('@startchen ') and chen.rstrip().endswith('@endchen'),
            'Sơ đồ Chen phải dùng ký hiệu Chen gốc')

    expected_logical = set()
    expected_chen = set()
    for table, ddl in tables.items():
        columns = dict(re.findall(r'^    (\w+)\s+((?:INT|VARCHAR|CHAR|TINYINT|DECIMAL|DATE|DATETIME|TEXT)\b[^\n]*)', ddl, re.M))
        fields = {}
        primary = set()
        foreign = set()
        unique_groups = defaultdict(set)
        for line in entities[table].splitlines():
            match = re.fullmatch(r'\s*(\* )?([\w*/]+) : ([A-Z]+(?:\([^)]*\))?)(?: <<([^>]+)>>)?', line)
            if not match:
                require(line.strip() == '--', f'Dòng ERD chưa được kiểm tra: {table}: {line}')
                continue
            mandatory, name, sql_type, flags = match.groups()
            name = name.replace('*', '').replace('/', '')
            require(name not in fields, f'Thuộc tính trùng: {table}.{name}')
            fields[name] = (bool(mandatory), sql_type)
            for flag in names(flags or ''):
                if flag == 'PK':
                    primary.add(name)
                elif flag == 'FK':
                    foreign.add(name)
                elif flag == 'UQ':
                    unique_groups[name].add(name)
                elif re.fullmatch(r'UQ\d+', flag):
                    unique_groups[flag].add(name)
        require(columns.keys() == fields.keys(), f'Sai danh sách thuộc tính: {table}')
        for name, definition in columns.items():
            sql_type = re.match(r'[A-Z]+(?:\([^)]*\))?', definition).group()
            require(fields[name] == ('NOT NULL' in definition, sql_type),
                    f'Sai kiểu dữ liệu hoặc NULL: {table}.{name}')
        sql_primary = set(names(re.search(r'PRIMARY KEY \(([^)]+)\)', ddl).group(1)))
        require(primary == sql_primary, f'Sai khóa chính: {table}')
        chen_primary = set(re.findall(r'^\s+(\w+) <<key>>$', chen_entities[table], re.M))
        require(chen_primary == sql_primary, f'Sai khóa chính Chen: {table}')
        sql_unique = {frozenset(names(group)) for group in re.findall(r'UNIQUE \(([^)]+)\)', ddl)}
        # Cột sinh payment được chú thích riêng, không phải khóa dự tuyển logic.
        sql_unique.discard(frozenset({'active_success_booking'}))
        require({frozenset(group) for group in unique_groups.values()} == sql_unique,
                f'Sai khóa duy nhất/khóa ghép: {table}')
        block = re.search(r'ALTER TABLE ' + table + r'\s+(.*?);', foreign_keys, re.S)
        fks = re.findall(r'FOREIGN KEY \((\w+)\)\s+REFERENCES (\w+) \((\w+)\)', block.group(1)) if block else []
        require(foreign == {column for column, _, _ in fks}, f'Sai cột FK: {table}')
        for column, parent, _ in fks:
            nullable = 'NOT NULL' not in columns[column]
            single_unique = frozenset({column}) in sql_unique
            expected_logical.add((parent, '|o' if nullable else '||', 'o|' if single_unique else 'o{', table))
            expected_chen.add(frozenset({(parent, '0,1' if single_unique else '0,N'),
                                          (table, '0,1' if nullable else '1,1')}))

    # BR-08 yêu cầu đơn mua có vé; DDL không tự bảo đảm số dòng con tối thiểu.
    expected_chen.remove(frozenset({('booking', '0,N'), ('ticket', '1,1')}))
    expected_chen.add(frozenset({('booking', '1,N'), ('ticket', '1,1')}))

    actual_logical = re.findall(r'^(\w+) (\|\||\|o)--(o\{|\|\{|o\|) (\w+) :', logical, re.M)
    require(len(actual_logical) == 17 and set(actual_logical) == expected_logical,
            'Sai quan hệ/bản số Crow\'s Foot so với 17 FK SQL')
    actual_mermaid = re.findall(r'^    (\w+) (\|\||\|o)--(o\{|\|\{|o\|) (\w+) :', mermaid, re.M)
    require(len(actual_mermaid) == 17 and set(actual_mermaid) == expected_logical,
            'Sai quan hệ/bản số Mermaid so với 17 FK SQL')
    relationships = re.findall(r'^relationship "[^"\n]+" as (R_\w+) \{', chen, re.M)
    connections = defaultdict(list)
    for left, stroke, maximum, right in re.findall(r'^(\w+) ([-=])([1N])\2 (\w+)$', chen, re.M):
        entity, relation = (right, left) if left in relationships else (left, right)
        require(entity in tables and relation in relationships, 'Liên kết Chen không hợp lệ')
        connections[relation].append((entity, stroke, maximum))
    # Theo slide: số ghi cạnh E là số thực thể E trên một thực thể đối diện.
    normalized = set()
    for edges in connections.values():
        require(len(edges) == 2, 'Mỗi liên kết Chen phải có hai đầu')
        normalized.add(frozenset((entity, ('1' if stroke == '=' else '0') + ',' + other[2])
                                for index, (entity, stroke, _) in enumerate(edges)
                                for other in [edges[1 - index]]))
    require(len(relationships) == len(connections) == 17
            and normalized == expected_chen,
            'Sai quan hệ/bản số Chen so với 17 FK SQL và quy tắc BR-08')
    require("' associative: movie_genre, screening_seat_lock" in chen,
            'Phải khai báo hai thực thể kết hợp để đối chiếu với bản draw.io')


if __name__ == '__main__':
    inputs = [ROOT / path for path in (
        'database/schema/01_schema.sql', 'database/schema/01b_foreign_keys.sql',
        'docs/erd/erd-logical.puml', 'docs/erd/erd-chen.puml', 'docs/erd/erd-explanation.md')]
    contents = [path.read_text(encoding='utf-8') for path in inputs]
    verify(*contents)
    diagram = (ROOT / 'docs/erd/erd-chen.drawio').read_text(encoding='utf-8')
    svg = (ROOT / 'docs/erd/erd-chen.svg').read_text(encoding='utf-8')
    verify_drawio(contents[3], diagram, svg)
    verify_ticket_decomposition()
    # Tự kiểm tra bộ kiểm chứng bắt được bốn lỗi trước khi chấp nhận PASS.
    for index, old, new in (
        (2, 'cinema_branch ||--o{ screening_room', 'cinema_branch ||--|{ screening_room'),
        (2, '//booking_id// : INT <<FK, UQ1>>', '//booking_id// : INT <<FK, UQ>>'),
        (3, 'as movie_genre {', 'as missing_movie_genre {'),
        (3, 'booking =1= R_BOOK_TICKET', 'booking -1- R_BOOK_TICKET'),
    ):
        broken = contents.copy()
        require(old in broken[index], 'Mẫu tự kiểm tra không còn khớp')
        broken[index] = broken[index].replace(old, new, 1)
        try:
            verify(*broken)
        except ValueError:
            continue
        raise ValueError('Bộ kiểm chứng đã bỏ lọt lỗi cố ý')
    print('PASS: 14 thực thể, thuộc tính/kiểu/NULL/PK/FK/UQ; 17 quan hệ và bản số Chen/Crow\'s Foot/Mermaid.')
    print('PASS: 4 tự kiểm tra phát hiện bản số SQL sai, khóa ghép sai, thiếu thực thể, bản số BR-08 sai.')
    for broken_kind in ('zero_size', 'single_total'):
        tree = ET.fromstring(diagram)
        if broken_kind == 'zero_size':
            tree.find('.//mxCell[@id="R_BRANCH_ROOM"]/mxGeometry').set('width', '0')
        else:
            edge = tree.find('.//mxCell[@id="rel_1"]')
            edge.set('style', edge.get('style').replace('shape=link;', ''))
        try:
            verify_drawio(contents[3], ET.tostring(tree, encoding='unicode'), svg)
        except ValueError:
            continue
        raise ValueError('Bỏ lọt lỗi hình học/ký hiệu draw.io')
    print('PASS: draw.io/SVG dùng đúng 1/N, hai thực thể kết hợp và 17 đường kép; phát hiện 2 lỗi hình học/ký hiệu cố ý.')
    print('PASS: phân rã vé trên đúng U_old; phần giao xác định R2 và bảo toàn bao đóng của 128 tập con theo F đã phát biểu.')
