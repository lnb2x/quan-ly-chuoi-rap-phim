"""Xuất hình SVG của bản Chen draw.io trong repo; chỉ hỗ trợ các hình đang dùng."""

import hashlib
import html
import math
import re
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
SVG = 'http://www.w3.org/2000/svg'
ET.register_namespace('', SVG)


def add(parent, tag, **attrs):
    return ET.SubElement(parent, f'{{{SVG}}}{tag}', {k.replace('_', '-'): str(v) for k, v in attrs.items()})


def export(source: Path, destination: Path) -> None:
    data = source.read_bytes()
    model = ET.fromstring(data).find('./diagram/mxGraphModel')
    cells = {cell.get('id'): cell for cell in model.findall('./root/mxCell')}
    boxes = {}
    styles = {}
    for ident, cell in cells.items():
        styles[ident] = dict(part.split('=', 1) for part in cell.get('style', '').split(';') if '=' in part)
        if cell.get('vertex') == '1' and cell.get('parent') == '1':
            g = cell.find('mxGeometry')
            boxes[ident] = tuple(float(g.get(key, '0')) for key in ('x', 'y', 'width', 'height'))
            if boxes[ident][2] <= 0 or boxes[ident][3] <= 0:
                raise ValueError(f'Hình không có kích thước dương: {ident}')
    width = math.ceil(max(x + w for x, y, w, h in boxes.values()) + 30)
    height = math.ceil(max(y + h for x, y, w, h in boxes.values()) + 30)
    svg = ET.Element(f'{{{SVG}}}svg', width=str(width), height=str(height), viewBox=f'0 0 {width} {height}')
    add(svg, 'title').text = 'Sơ đồ E–R theo bài giảng: lực lượng 1/N và tham gia đầy đủ/một phần'
    add(svg, 'metadata').text = 'Source: erd-chen.drawio; SHA-256: ' + hashlib.sha256(data).hexdigest()
    add(svg, 'rect', width=width, height=height, fill='white')

    def boundary(ident, toward):
        x, y, w, h = boxes[ident]
        cx, cy = x + w / 2, y + h / 2
        dx, dy = toward[0] - cx, toward[1] - cy
        if dx == dy == 0:
            raise ValueError(f'Hai hình có cùng tâm: {ident}')
        style = cells[ident].get('style', '')
        if style.startswith('ellipse;'):
            ratio = 1 / math.hypot(dx / (w / 2), dy / (h / 2))
        elif style.startswith('rhombus;'):
            ratio = 1 / (abs(dx) / (w / 2) + abs(dy) / (h / 2))
        else:
            ratio = min(w / (2 * abs(dx)) if dx else math.inf,
                        h / (2 * abs(dy)) if dy else math.inf)
        return cx + ratio * dx, cy + ratio * dy

    segments = {}
    for ident, cell in cells.items():
        if cell.get('edge') != '1':
            continue
        source_id, target_id = cell.get('source'), cell.get('target')
        a, b = boxes[source_id], boxes[target_id]
        start = boundary(source_id, (b[0] + b[2] / 2, b[1] + b[3] / 2))
        end = boundary(target_id, (a[0] + a[2] / 2, a[1] + a[3] / 2))
        segments[ident] = start, end
        dx, dy = end[0] - start[0], end[1] - start[1]
        length = math.hypot(dx, dy)
        if not length:
            raise ValueError(f'Đường nối có độ dài 0: {ident}')
        group = add(svg, 'g', id=ident)
        for offset in (-2.5, 2.5) if styles[ident].get('shape') == 'link' else (0,):
            ox, oy = -dy / length * offset, dx / length * offset
            add(group, 'line', x1=start[0] + ox, y1=start[1] + oy, x2=end[0] + ox, y2=end[1] + oy,
                stroke=styles[ident].get('strokeColor', '#526579'), stroke_width=1.5)

    for ident, cell in cells.items():
        if cell.get('vertex') != '1':
            continue
        style = cell.get('style', '')
        props = styles[ident]
        parent = cell.get('parent')
        if parent in segments:
            a, b = segments[parent]
            ratio = (float(cell.find('mxGeometry').get('x', '0')) + 1) / 2
            x, y = a[0] + (b[0] - a[0]) * ratio, a[1] + (b[1] - a[1]) * ratio - 14
            group = add(svg, 'g', id=ident)
            add(group, 'rect', x=x - 10, y=y - 12, width=20, height=23, fill='white')
            add(group, 'text', x=x, y=y + 5, text_anchor='middle', font_family='Arial', font_size=16,
                fill='#17324D').text = cell.get('value')
            continue
        if parent == '1':
            x, y, w, h = boxes[ident]
        elif parent in boxes:
            px, py, _, _ = boxes[parent]
            g = cell.find('mxGeometry')
            x, y = px + float(g.get('x', '0')), py + float(g.get('y', '0'))
            w, h = float(g.get('width')), float(g.get('height'))
        else:
            raise ValueError(f'Hình chưa được hỗ trợ: {ident}')
        group = add(svg, 'g', id=ident)
        color = props.get('fillColor', '#EDF4FC')
        outline = props.get('strokeColor', '#6B8AAE')
        if style.startswith('ellipse;'):
            add(group, 'ellipse', cx=x + w / 2, cy=y + h / 2, rx=w / 2, ry=h / 2, fill=color, stroke=outline)
        elif style.startswith('rhombus;'):
            points = f'{x+w/2},{y} {x+w},{y+h/2} {x+w/2},{y+h} {x},{y+h/2}'
            add(group, 'polygon', points=points, fill=color, stroke=outline)
        elif not style.startswith('text;'):
            add(group, 'rect', x=x, y=y, width=w, height=h, fill=color, stroke=outline,
                rx=8 if 'rounded=1;' in style else 0)
        value = cell.get('value', '')
        lines = [html.unescape(re.sub(r'<[^>]+>', '', part)) for part in re.split(r'<br\s*/?>', value)]
        size = float(props.get('fontSize', '16'))
        font_style = int(props.get('fontStyle', '0'))
        for index, line in enumerate(lines):
            left = props.get('align') == 'left'
            tx = x + 16 if left else x + w / 2
            ty = y + 30 + index * (size + 8) if left else y + h / 2 + (index - (len(lines)-1)/2) * (size+6) + size / 3
            add(group, 'text', x=tx, y=ty, text_anchor='start' if left else 'middle', font_family='Arial',
                font_size=size, font_weight='bold' if font_style & 1 else 'normal',
                text_decoration='underline' if font_style & 4 else 'none', fill=props.get('fontColor', '#17324D')).text = line
    ET.ElementTree(svg).write(destination, encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    export(ROOT / 'docs/erd/erd-chen.drawio', ROOT / 'docs/erd/erd-chen.svg')
    print('Exported erd-chen.svg from editable draw.io source')
