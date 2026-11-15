#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Trình tạo Báo cáo Học thuật Môn Cơ sở Dữ liệu (Đáp ứng Tiêu chí R8 - 1.0 điểm).
Kết xuất đồng thời:
  1. docs/academic-report/BaoCao_QuanLyChuoiRap.docx (Microsoft Word)
  2. docs/academic-report/BaoCao_QuanLyChuoiRap.pdf  (PDF chuẩn tiếng Việt nhúng font Unicode)
Tích hợp trực tiếp nội dung thiết kế R1-R4 (14 bảng chuẩn 3NF nghiêm ngặt), sơ đồ ERD Chen & Crow's Foot,
ảnh chụp màn hình thực tế từ ứng dụng kết nối MySQL Community Server 8.4.0 LTS (R5-R7),
đặc tả SQL chức năng R5-R7 và kết quả kiểm chứng lấy từ manifest thực thi mới nhất
và Phụ lục SQL cài đặt + thực thi đầy đủ.
"""

import html
import hashlib
import json
import re
from pathlib import Path

from docx import Document
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls, qn
from docx.shared import Cm, Inches, Pt, RGBColor
from PIL import Image as PilImage

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    Image as RLImage,
    PageBreak,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parent.parent
OUT_DOCX = ROOT / "docs" / "academic-report" / "BaoCao_QuanLyChuoiRap.docx"
OUT_PDF = ROOT / "docs" / "academic-report" / "BaoCao_QuanLyChuoiRap.pdf"


def clean_markdown_text(text: str) -> str:
    s = text
    s = s.replace("<br>", "\n")
    s = re.sub(r"<u>(.*?)</u>", r"\1", s)
    replacements = [
        (r"\not\to", "↛"),
        (r"\to", "→"),
        (r"\rightarrow", "→"),
        (r"\Rightarrow", "⇒"),
        (r"\bowtie", "⋈"),
        (r"\subseteq", "⊆"),
        (r"\subset", "⊂"),
        (r"\in", "∈"),
        (r"\notin", "∉"),
        (r"\varnothing", "∅"),
        (r"\neq", "≠"),
        (r"\setminus", " \\ "),
        (r"\ge", "≥"),
        (r"\le", "≤"),
        (r"\times", "×"),
        (r"\cup", "∪"),
        (r"\cap", "∩"),
        (r"\pi", "π"),
        (r"\sigma", "σ"),
        (r"\_", "_"),
        (r"\{", "{"),
        (r"\}", "}"),
    ]
    for k, v in replacements:
        s = s.replace(k, v)
    s = re.sub(r"\\text\{([^}]*)\}", r"\1", s)
    s = re.sub(r"\\underline\{([^}]*)\}", r"\1", s)
    s = s.replace("$$", "")
    s = s.replace("$", "")
    return s


# =============================================================================
# PHẦN 1: XUẤT BÁO CÁO WORD (.DOCX)
# =============================================================================

def set_cell_background(cell, hex_color: str) -> None:
    shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    cell._tc.get_or_add_tcPr().append(shading)


def set_table_borders(table) -> None:
    tbl_pr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        '<w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        '<w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        '<w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        '<w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>'
        '<w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>'
        '<w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>'
        "</w:tblBorders>"
    )
    tbl_pr.append(borders)


def add_formatted_runs(paragraph, text: str, base_size: float = 11.0) -> None:
    cleaned = clean_markdown_text(text)
    tokens = re.split(r"(\*\*.*?\*\*|`.*?`)", cleaned)
    for tok in tokens:
        if not tok:
            continue
        if tok.startswith("**") and tok.endswith("**") and len(tok) > 4:
            run = paragraph.add_run(tok[2:-2].replace("*", "").replace("`", ""))
            run.bold = True
            run.font.name = "Times New Roman"
            run.font.size = Pt(base_size)
        elif tok.startswith("`") and tok.endswith("`") and len(tok) > 2:
            run = paragraph.add_run(tok[1:-1])
            run.font.name = "Consolas"
            run.font.size = Pt(max(base_size - 1.0, 9.0))
            run.font.color.rgb = RGBColor(0, 0, 0)
        else:
            run = paragraph.add_run(tok.replace("*", ""))
            run.font.name = "Times New Roman"
            run.font.size = Pt(base_size)


def configure_docx_styles(doc: Document) -> None:
    for section in doc.sections:
        section.page_width = Cm(21.0)
        section.page_height = Cm(29.7)
        section.top_margin = Cm(2.2)
        section.bottom_margin = Cm(2.2)
        section.left_margin = Cm(2.8)
        section.right_margin = Cm(2.0)

    normal = doc.styles["Normal"]
    normal.font.name = "Times New Roman"
    normal.font.size = Pt(11.5)
    normal.paragraph_format.line_spacing = 1.25
    normal.paragraph_format.space_after = Pt(4)

    for level, (size, color) in {
        1: (15, RGBColor(0, 0, 0)),
        2: (13, RGBColor(0, 0, 0)),
        3: (11.5, RGBColor(0, 0, 0)),
    }.items():
        st = doc.styles[f"Heading {level}"]
        st.font.name = "Times New Roman"
        st.font.size = Pt(size)
        st.font.bold = True
        st.font.color.rgb = color
        st.paragraph_format.space_before = Pt(10 if level == 1 else 7)
        st.paragraph_format.space_after = Pt(4)


def add_docx_cover_page(doc: Document) -> None:
    doc.core_properties.title = GROUP["topic"]
    doc.core_properties.author = "Nhóm " + GROUP["group"]
    for text, style, size in (
        (GROUP["school"], "Normal", 14),
        ("BÁO CÁO BÀI TẬP LỚN", "Normal", 16),
        ("HỌC PHẦN CƠ SỞ DỮ LIỆU", "Normal", 15),
        (GROUP["topic"].upper(), "Title", 19),
    ):
        paragraph = doc.add_paragraph(style=style)
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        paragraph.paragraph_format.space_after = Pt(20)
        run = paragraph.add_run(text)
        run.bold = True
        run.font.name = "Times New Roman"
        run.font.size = Pt(size)
        run.font.color.rgb = RGBColor(0, 0, 0)
    metadata = ["| Thông tin | Nội dung |", "|---|---|"]
    metadata += [f"| {label} | {value} |" for label, value in cover_rows()]
    render_docx_table(doc, metadata)
    paragraph = doc.add_paragraph("Hà Nội, tháng 11 năm 2026")
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_page_break()


def parse_md_table(table_lines: list[str]) -> list[list[str]]:
    parsed_rows = []
    for line in table_lines:
        stripped = line.strip()
        if not stripped.startswith("|"):
            continue
        cells = [c.strip() for c in stripped.strip("|").split("|")]
        if all(re.match(r"^:?-{2,}:?$", c) for c in cells):
            continue
        parsed_rows.append(cells)
    return parsed_rows


def render_docx_table(doc: Document, table_lines: list[str]) -> None:
    parsed_rows = parse_md_table(table_lines)
    if not parsed_rows:
        return
    num_cols = max(len(r) for r in parsed_rows)
    tbl = doc.add_table(rows=len(parsed_rows), cols=num_cols)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(tbl)
    widths = table_widths(parsed_rows, 16.2)
    tbl.autofit = False
    for index, width in enumerate(widths):
        tbl.columns[index].width = Cm(width)
    header = parse_xml(f'<w:tblHeader {nsdecls("w")}/>')
    tbl.rows[0]._tr.get_or_add_trPr().append(header)

    for row in tbl.rows:
        row._tr.get_or_add_trPr().append(parse_xml(f'<w:cantSplit {nsdecls("w")}/>'))

    for r_idx, row_vals in enumerate(parsed_rows):
        for c_idx in range(num_cols):
            val = row_vals[c_idx] if c_idx < len(row_vals) else ""
            cell = tbl.rows[r_idx].cells[c_idx]
            cell.width = Cm(widths[c_idx])
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.line_spacing = 1.1
            if r_idx == 0:
                set_cell_background(cell, "E0F2FE")
                run = p.add_run(clean_markdown_text(val).replace("*", ""))
                run.bold = True
                run.font.name = "Times New Roman"
                run.font.size = Pt(9.5)
            else:
                add_formatted_runs(p, val, base_size=9.5)
    doc.add_paragraph()


def append_docx_code_block(doc: Document, code_text: str, font_size: float = 8.0) -> None:
    import textwrap
    for line in code_text.rstrip().splitlines():
        for wrapped in textwrap.wrap(line, width=99, replace_whitespace=False, drop_whitespace=False) or [""]:
            paragraph = doc.add_paragraph()
            paragraph.paragraph_format.space_after = Pt(0)
            paragraph.paragraph_format.line_spacing = 1.05
            run = paragraph.add_run(wrapped)
            run.font.name = "Consolas"
            run.font.size = Pt(font_size)


def append_docx_markdown(doc: Document, md_path: Path, skip_top_h1: bool = True) -> None:
    lines = md_path.read_text(encoding="utf-8").splitlines()
    i = 0
    in_code = False
    code_lang = ""
    code_buf = []

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith("```"):
            if not in_code:
                in_code = True
                code_lang = stripped[3:].strip().lower()
                code_buf = []
            else:
                in_code = False
                if code_lang != "mermaid":
                    append_docx_code_block(doc, "\n".join(code_buf))
                code_buf = []
            i += 1
            continue
        if in_code:
            code_buf.append(line)
            i += 1
            continue
        if stripped == "---" or not stripped:
            i += 1
            continue
        if stripped.startswith("|"):
            tbl_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                tbl_lines.append(lines[i])
                i += 1
            render_docx_table(doc, tbl_lines)
            continue
        if stripped.startswith("# "):
            if not skip_top_h1:
                doc.add_heading(clean_markdown_text(stripped[2:]).replace("*", ""), level=1)
            i += 1
            continue
        if stripped.startswith("## "):
            doc.add_heading(clean_markdown_text(stripped[3:]).replace("*", ""), level=2)
            i += 1
            continue
        if stripped.startswith("### "):
            doc.add_heading(clean_markdown_text(stripped[4:]).replace("*", ""), level=3)
            i += 1
            continue
        if stripped.startswith("#### "):
            doc.add_heading(clean_markdown_text(stripped[5:]).replace("*", ""), level=3)
            i += 1
            continue
        if re.match(r"^[-*]\s+", stripped):
            p = doc.add_paragraph(style="List Bullet")
            add_formatted_runs(p, re.sub(r"^[-*]\s+", "", stripped), base_size=11.0)
            i += 1
            continue
        if re.match(r"^\d+\.\s+", stripped):
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Cm(0.5)
            p.paragraph_format.keep_with_next = bool(re.match(r"^\d+\.\s+\*\*", stripped))
            add_formatted_runs(p, stripped, base_size=11.0)
            i += 1
            continue
        p = doc.add_paragraph()
        add_formatted_runs(p, stripped, base_size=11.0)
        i += 1


def add_docx_figure(doc: Document, image_path: Path, caption: str, width_inches: float = 6.0) -> None:
    if not image_path.exists():
        p_missing = doc.add_paragraph(f"[Không tìm thấy tệp hình ảnh: {image_path}]")
        p_missing.runs[0].italic = True
        return
    p_img = doc.add_paragraph()
    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_img.paragraph_format.keep_with_next = True
    p_img.add_run().add_picture(str(image_path), width=Inches(width_inches))

    p_cap = doc.add_paragraph()
    p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_cap.paragraph_format.keep_with_next = False
    rc = p_cap.add_run(caption)
    rc.italic = True
    rc.bold = True
    rc.font.size = Pt(10.0)
    rc.font.color.rgb = RGBColor(71, 85, 105)


# =============================================================================
# PHẦN 2: XUẤT BÁO CÁO PDF CHUẨN TIẾNG VIỆT (REPORTLAB PLATYPUS)
# =============================================================================

def register_pdf_fonts() -> None:
    fonts_dir = Path(r"C:\Windows\Fonts")
    pdfmetrics.registerFont(TTFont("VNSerif", str(fonts_dir / "times.ttf")))
    pdfmetrics.registerFont(TTFont("VNSerif-Bold", str(fonts_dir / "timesbd.ttf")))
    pdfmetrics.registerFont(TTFont("VNSerif-Italic", str(fonts_dir / "timesi.ttf")))
    pdfmetrics.registerFont(TTFont("VNSerif-BoldItalic", str(fonts_dir / "timesbi.ttf")))
    pdfmetrics.registerFont(TTFont("VNMono", str(fonts_dir / "consola.ttf")))
    pdfmetrics.registerFont(TTFont("VNSym", str(fonts_dir / "seguisym.ttf")))
    pdfmetrics.registerFontFamily(
        "VNSerif",
        normal="VNSerif",
        bold="VNSerif-Bold",
        italic="VNSerif-Italic",
        boldItalic="VNSerif-BoldItalic",
    )


def _wrap_math_symbols(escaped_text: str) -> str:
    return re.sub(r"([⇒⊆⊂∈∉∅⋈∪∩↛])", r'<font name="VNSym">\1</font>', escaped_text)


def md_to_rl_xml(text: str) -> str:
    cleaned = clean_markdown_text(text)
    tokens = re.split(r"(\*\*.*?\*\*|`.*?`)", cleaned)
    out = []
    for tok in tokens:
        if not tok:
            continue
        if tok.startswith("**") and tok.endswith("**") and len(tok) > 4:
            inner = _wrap_math_symbols(html.escape(tok[2:-2].replace("*", "").replace("`", "")))
            out.append(f"<b>{inner}</b>")
        elif tok.startswith("`") and tok.endswith("`") and len(tok) > 2:
            inner = _wrap_math_symbols(html.escape(tok[1:-1]))
            out.append(f'<font name="VNMono" size="8.5" color="#0F766E">{inner}</font>')
        else:
            out.append(_wrap_math_symbols(html.escape(tok.replace("*", "").replace("`", ""))))
    return "".join(out).replace("\n", "<br/>")


def make_pdf_styles():
    styles = getSampleStyleSheet()
    return {
        "body": ParagraphStyle(
            "VNBody",
            parent=styles["Normal"],
            fontName="VNSerif",
            fontSize=10.5,
            leading=14.5,
            alignment=TA_JUSTIFY,
            spaceAfter=5,
        ),
        "bullet": ParagraphStyle(
            "VNBullet",
            parent=styles["Normal"],
            fontName="VNSerif",
            fontSize=10.5,
            leading=14.5,
            leftIndent=14,
            spaceAfter=4,
        ),
        "h1": ParagraphStyle(
            "VNH1",
            parent=styles["Heading1"],
            fontName="VNSerif-Bold",
            fontSize=14,
            leading=18,
            textColor=colors.black,
            spaceBefore=10,
            spaceAfter=6,
        ),
        "h2": ParagraphStyle(
            "VNH2",
            parent=styles["Heading2"],
            fontName="VNSerif-Bold",
            fontSize=12,
            leading=16,
            textColor=colors.black,
            spaceBefore=8,
            spaceAfter=4,
        ),
        "h3": ParagraphStyle(
            "VNH3",
            parent=styles["Heading3"],
            fontName="VNSerif-Bold",
            fontSize=10.8,
            leading=14.5,
            textColor=colors.black,
            spaceBefore=6,
            spaceAfter=3,
        ),
        "caption": ParagraphStyle(
            "VNCaption",
            parent=styles["Normal"],
            fontName="VNSerif-BoldItalic",
            fontSize=9.5,
            leading=13,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#475569"),
            spaceBefore=3,
            spaceAfter=8,
        ),
        "cell": ParagraphStyle(
            "VNCell",
            parent=styles["Normal"],
            fontName="VNSerif",
            fontSize=8.2,
            leading=10.8,
        ),
        "cell_hdr": ParagraphStyle(
            "VNCellHdr",
            parent=styles["Normal"],
            fontName="VNSerif-Bold",
            fontSize=8.2,
            leading=10.8,
        ),
        "code": ParagraphStyle(
            "VNCode",
            parent=styles["Code"],
            fontName="VNMono",
            fontSize=7.2,
            leading=9.4,
            leftIndent=0,
            rightIndent=0,
            textColor=colors.HexColor("#1E293B"),
            backColor=colors.HexColor("#F8FAFC"),
            borderColor=colors.HexColor("#CBD5E1"),
            borderWidth=0.5,
            borderPadding=5,
            spaceAfter=6,
        ),
        "cover_center": ParagraphStyle(
            "VNCoverCenter",
            parent=styles["Normal"],
            fontName="VNSerif-Bold",
            fontSize=12.5,
            leading=17,
            alignment=TA_CENTER,
            spaceAfter=6,
        ),
        "cover_title": ParagraphStyle(
            "VNCoverTitle",
            parent=styles["Normal"],
            fontName="VNSerif-Bold",
            fontSize=17.5,
            leading=23,
            alignment=TA_CENTER,
            textColor=colors.black,
            spaceAfter=10,
        ),
    }


def render_pdf_table(story: list, table_lines: list[str], st: dict, avail_width: float) -> None:
    parsed = parse_md_table(table_lines)
    if not parsed:
        return
    num_cols = max(len(r) for r in parsed)
    data = []
    for r_idx, row_vals in enumerate(parsed):
        row_cells = []
        for c_idx in range(num_cols):
            val = row_vals[c_idx] if c_idx < len(row_vals) else ""
            style = st["cell_hdr"] if r_idx == 0 else st["cell"]
            row_cells.append(Paragraph(md_to_rl_xml(val), style))
        data.append(row_cells)

    tbl = Table(data, colWidths=table_widths(parsed, avail_width), repeatRows=1)
    tbl.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#E0F2FE")),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("TOPPADDING", (0, 0), (-1, -1), 3),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
                ("LEFTPADDING", (0, 0), (-1, -1), 4),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    story.append(tbl)
    story.append(Spacer(1, 4))


def append_pdf_code(story: list, code_text: str, st: dict, max_chars: int = 104) -> None:
    wrapped_lines = []
    for raw_line in code_text.rstrip().splitlines():
        if len(raw_line) <= max_chars:
            wrapped_lines.append(raw_line)
        else:
            for pos in range(0, len(raw_line), max_chars):
                wrapped_lines.append(raw_line[pos : pos + max_chars])
    chunk_size = 55
    for idx in range(0, len(wrapped_lines), chunk_size):
        chunk = "\n".join(wrapped_lines[idx : idx + chunk_size])
        story.append(Preformatted(chunk, st["code"]))


def append_pdf_figure(story: list, image_path: Path, caption: str, st: dict, max_w: float = 15.6 * cm, max_h: float = 9.5 * cm) -> None:
    if not image_path.exists():
        story.append(Paragraph(f"[Không tìm thấy tệp hình ảnh: {html.escape(str(image_path))}]", st["caption"]))
        return
    with PilImage.open(image_path) as im:
        iw, ih = im.size
    scale = min(max_w / iw, max_h / ih)
    img = RLImage(str(image_path), width=iw * scale, height=ih * scale)
    img.hAlign = "CENTER"
    img.keepWithNext = True
    story.append(img)
    story.append(Paragraph(html.escape(caption), st["caption"]))


def append_pdf_markdown(story: list, md_path: Path, st: dict, avail_width: float, skip_top_h1: bool = True) -> None:
    lines = md_path.read_text(encoding="utf-8").splitlines()
    i = 0
    in_code = False
    code_lang = ""
    code_buf = []

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith("```"):
            if not in_code:
                in_code = True
                code_lang = stripped[3:].strip().lower()
                code_buf = []
            else:
                in_code = False
                if code_lang != "mermaid":
                    append_pdf_code(story, "\n".join(code_buf), st)
                code_buf = []
            i += 1
            continue
        if in_code:
            code_buf.append(line)
            i += 1
            continue
        if stripped == "---" or not stripped:
            i += 1
            continue
        if stripped.startswith("|"):
            tbl_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                tbl_lines.append(lines[i])
                i += 1
            render_pdf_table(story, tbl_lines, st, avail_width)
            continue
        if stripped.startswith("# "):
            if not skip_top_h1:
                story.append(Paragraph(md_to_rl_xml(stripped[2:]), st["h1"]))
            i += 1
            continue
        if stripped.startswith("## "):
            story.append(Paragraph(md_to_rl_xml(stripped[3:]), st["h2"]))
            i += 1
            continue
        if stripped.startswith("### "):
            story.append(Paragraph(md_to_rl_xml(stripped[4:]), st["h3"]))
            i += 1
            continue
        if stripped.startswith("#### "):
            story.append(Paragraph(md_to_rl_xml(stripped[5:]), st["h3"]))
            i += 1
            continue
        if re.match(r"^[-*]\s+", stripped):
            story.append(Paragraph("• " + md_to_rl_xml(re.sub(r"^[-*]\s+", "", stripped)), st["bullet"]))
            i += 1
            continue
        if re.match(r"^\d+\.\s+", stripped):
            paragraph = Paragraph(md_to_rl_xml(stripped), st["bullet"])
            paragraph.keepWithNext = bool(re.match(r"^\d+\.\s+\*\*", stripped))
            story.append(paragraph)
            i += 1
            continue
        story.append(Paragraph(md_to_rl_xml(stripped), st["body"]))
        i += 1


# =============================================================================
# NỘI DUNG DÙNG CHUNG CHO CẢ DOCX VÀ PDF
# =============================================================================

GROUP = json.loads((ROOT / "docs/academic-report/thong-tin-nhom.json").read_text(encoding="utf-8"))


def cover_rows():
    return [
        ("Giảng viên", GROUP["lecturer"]),
        ("Lớp học phần", GROUP["class_code"]),
        ("Nhóm thực hiện", GROUP["group"]),
        *[(f"Thành viên {number}", f"{member['name']} - {member['student_id']}")
          for number, member in enumerate(GROUP["members"], 1)],
    ]


def table_widths(rows, width):
    count = max(len(row) for row in rows)
    header = " ".join(rows[0]).lower()
    if count == 6 and "null" in header:
        weights = [1.4, 1.35, .65, .7, 2, 2.1]
    elif count == 3 and "kiểm thử" in header:
        weights = [3, .8, 1]
    elif count == 3:
        weights = [1.3, 1.3, 2.4]
    elif count == 2:
        weights = [1.2, 3.2]
    elif count == 5 and "điểm" in header:
        weights = [.5, 2.6, .5, 1.1, 1.6]
    else:
        weights = [min(32, max(10, max(len(row[index]) if index < len(row) else 0 for row in rows))) for index in range(count)]
    return [width * weight / sum(weights) for weight in weights]


def verified_run():
    path = ROOT / "verification/last-run.json"
    run = json.loads(path.read_text(encoding="utf-8-sig"))
    required = ("gui_verified", "two_jvm_persistence_verified", "functional_sql_verified", "demo_fingerprint_preserved")
    if any(run.get(key) is not True for key in required) or run["tests"] <= 0 or any(run[key] for key in ("failures", "errors", "skipped")):
        raise ValueError("Chưa có bộ bằng chứng đầy đủ; chạy database/verify_all.ps1 trước khi tạo bản nộp.")
    for name, expected in run["tested_source_hashes"].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
            raise ValueError(f"Source đã đổi sau kiểm chứng: {name}; cần chạy lại các gate.")
    return run


INTRO_TEXT = (
    "Báo cáo trình bày yêu cầu dữ liệu, mô hình thực thể liên kết, mô hình quan hệ, kiểm tra chuẩn hóa 3NF "
    "và cài đặt Hệ thống quản lý chuỗi rạp chiếu phim. Cơ sở dữ liệu gồm 14 bảng trên MySQL 8.4, "
    "ứng dụng Java 21 sử dụng Swing để lập lịch, đặt vé, tìm kiếm và xuất báo cáo PDF tiếng Việt. "
    "Bảy chương chuyên môn tương ứng với bảy mục nội dung của đề bài; phụ lục cung cấp SQL cài đặt và SQL chức năng. "
    "Phần thực nghiệm ghi rõ môi trường, thời điểm chạy và phạm vi kiểm chứng. Điểm số trong bảng dưới là trọng số chấm của học phần."
)
RUBRIC_TABLE = [
    "| Mục | Nội dung | Điểm | Vị trí | Bằng chứng |",
    "|---|---|---|---|---|",
    "| 1 | Yêu cầu dữ liệu và nghiệp vụ | 1,0 | Chương 1 | Yêu cầu và quy tắc nghiệp vụ |",
    "| 2 | Vẽ và mô tả ERD | 2,5 | Chương 2 | Chen và Crow's Foot chỉnh sửa được |",
    "| 3 | Mô hình quan hệ và mô tả bảng | 2,0 | Chương 3 | 14 bảng, 17 FK và từ điển dữ liệu |",
    "| 4 | Kiểm tra và chuẩn hóa 3NF | 1,0 | Chương 4 | Khóa, FD, phép tách lossless |",
    "| 5 | Hai form nhập liệu có kiểm tra đúng sai | 1,0 | Chương 5 | Ảnh và log GUI thực nghiệm |",
    "| 6 | Một màn hình tìm kiếm | 0,5 | Chương 6 | Bộ lọc, kết quả và phân trang |",
    "| 7 | Hai báo cáo xem và xuất file | 1,0 | Chương 7 | Preview, PDF và SQL đối soát |",
    "| 8 | Báo cáo đủ bảy mục và SQL tương ứng | 1,0 | Toàn báo cáo và phụ lục | DOCX, PDF, source và SQL đi kèm |",
]

CHAPTERS = [
    ("CHƯƠNG 1 YÊU CẦU DỮ LIỆU VÀ NGHIỆP VỤ", ["docs/requirements/requirements.md", "docs/requirements/business-rules.md"]),
    ("CHƯƠNG 2 MÔ HÌNH THỰC THỂ LIÊN KẾT", ["docs/erd/erd-explanation.md"]),
    ("CHƯƠNG 3 MÔ HÌNH QUAN HỆ VÀ TỪ ĐIỂN DỮ LIỆU", ["docs/relational-model/schema-design.md"]),
    ("CHƯƠNG 4 KIỂM TRA VÀ CHUẨN HÓA 3NF", ["docs/normalization/normalization.md"]),
]
SCREENSHOT_ITEMS = [
    ("5.1 Quản lý suất chiếu", "Quản lý chi nhánh lập và cập nhật lịch chiếu. Form kiểm tra phim, phòng, giá dương, thời lượng và khoảng giao lịch. Suất đã có vé không được đổi phòng; dữ liệu được phân trang 50 dòng. Các câu SQL tương ứng được mô tả tại Phụ lục SQL chức năng, mục 1.", [
        ("01_screening_management_valid.png", "Hình 5.1 Tạo suất chiếu hợp lệ"),
        ("02_screening_management_overlap_rejected.png", "Hình 5.2 Từ chối lịch chiếu chồng lấn"),
    ]),
    ("5.2 Đặt vé và chọn ghế", "Thu ngân chọn suất, khách hàng hoặc khách vãng lai và các ghế trống. Giá bán được chốt theo từng vé. Booking, ticket và payment ghi trong cùng transaction; khóa ghế ngăn bán trùng, hủy đơn giải phóng ghế để bán lại. Các câu SQL tương ứng tại Phụ lục SQL chức năng, mục 2.", [
        ("03_ticket_booking_seat_selection.png", "Hình 5.3 Sơ đồ ghế và thao tác chọn chỗ"),
        ("04_ticket_booking_success.png", "Hình 5.4 Đơn đặt vé được ghi thành công"),
        ("04b_ticket_booking_occupied_rejected.png", "Hình 5.5 Từ chối ghế đã được quầy khác đặt"),
    ]),
    ("6.1 Tra cứu suất chiếu", "Nhân viên tìm theo tên hoặc mã phim, chi nhánh, ngày và khung giờ. Kết quả hiển thị thông tin phim, phòng, giá, ghế đang giữ và ghế trống, có tổng số dòng và phân trang 50 dòng. PreparedStatement truyền tham số tìm kiếm. SQL tại Phụ lục SQL chức năng, mục 3.", [
        ("05_screening_search.png", "Hình 6.1 Kết quả tra cứu và phân trang"),
    ]),
    ("7.1 Báo cáo doanh thu chuỗi rạp", "Người lập là Kế toán chuỗi rạp; người nhận là Giám đốc Kinh doanh. Báo cáo theo ngày thanh toán và chi nhánh, phục vụ đối soát tiền thu. Chỉ cộng vé VALID thuộc đơn CONFIRMED có payment SUCCESS; vẫn giữ doanh thu lịch sử của chi nhánh ngừng hoạt động. Cùng một bản báo cáo dùng snapshot đọc nhất quán. SQL tại Phụ lục SQL chức năng, mục 4.", [
        ("06_revenue_report_preview.png", "Hình 7.1 Xem trước báo cáo doanh thu"),
        ("08_exported_revenue_pdf_page1.png", "Hình 7.2 File PDF báo cáo doanh thu"),
    ]),
    ("7.2 Báo cáo bán vé và tỷ lệ lấp đầy", "Người lập là Quản lý chi nhánh; người nhận là Ban Điều hành và Quản lý Kinh doanh. Báo cáo theo ngày chiếu và suất, gồm sức chứa, vé đã thanh toán, ghế trống, tỷ lệ lấp đầy và doanh thu. Ghế của đơn PENDING vẫn giữ chỗ nhưng chưa được tính là vé bán có doanh thu. SQL tại Phụ lục SQL chức năng, mục 5.", [
        ("07_ticket_sales_report_preview.png", "Hình 7.3 Xem trước báo cáo bán vé"),
        ("09_exported_sales_pdf_page1.png", "Hình 7.4 File PDF báo cáo bán vé"),
    ]),
]
SQL_APPENDICES = [
    ("PHỤ LỤC A SQL TẠO 14 BẢNG", "database/schema/01_schema.sql"),
    ("PHỤ LỤC B SQL TẠO 17 KHÓA NGOẠI", "database/schema/01b_foreign_keys.sql"),
    ("PHỤ LỤC C SQL TẠO 12 TRIGGER", "database/schema/02_triggers.sql"),
    ("PHỤ LỤC D SQL TẠO 4 VIEW", "database/schema/03_views.sql"),
    ("PHỤ LỤC E SQL CHẠY DEMO HAI FORM TÌM KIẾM VÀ HAI BÁO CÁO", "database/queries/functional_sql_verification.sql"),
]


def test_summary(run):
    rows = ["| Bộ kiểm thử | Số test | Kết quả |", "|---|---|---|"]
    for name, result in sorted(run["suites"].items()):
        rows.append(f"| {name.rsplit('.', 1)[-1]} | {result['tests']} | Không lỗi, không bỏ qua |")
    rows.append(f"| Tổng cộng | {run['tests']} | Failures 0, Errors 0, Skipped 0 |")
    return rows


def verification_text(run):
    return (
        f"Lần kiểm chứng bắt đầu lúc {run['run_started_at_utc']} (UTC), dùng JDK 21 và MySQL Community Server 8.4.0 LTS. "
        f"Bộ mặc định có {run['tests']} kiểm thử thuộc {run['suite_count']} suite; không có lỗi hay test bỏ qua. "
        "XML cũ được xóa trước khi chạy. Đã thực thi năm màn hình với dữ liệu hợp lệ và bị từ chối, xuất hai PDF, "
        "kiểm tra dữ liệu tồn tại qua hai tiến trình JVM và chạy SQL chức năng trên cinema_chain_test. "
        "Fingerprint của cinema_chain được đọc trước và sau kiểm chứng, kết quả bằng nhau. "
        "Manifest, log và ảnh đi kèm cho phép đối chiếu từng kết quả. Kiểm tra ERD là kiểm tra tĩnh đối chiếu source; "
        "chứng minh 3NF ở Chương 4 dựa trên phụ thuộc hàm và khóa nghiệp vụ, không suy ra chỉ từ số bảng.\n"
        "Phạm vi bản nộp là ứng dụng quản lý tại quầy bằng Swing. Mở rộng VNPay chỉ chạy sandbox ở database test, "
        "IPN provider trong bộ tự động được ký mô phỏng; chưa có chứng từ thanh toán merchant hoặc hoàn tiền thật. "
        "Chưa kiểm thử mất điện, crash MySQL và tải 100K/1M. Quyền truy cập Internet không thuộc phạm vi triển khai này."
    )


def generate_docx(run) -> None:
    OUT_DOCX.parent.mkdir(parents=True, exist_ok=True)
    doc = Document()
    configure_docx_styles(doc)
    add_docx_cover_page(doc)
    doc.add_heading("LỜI MỞ ĐẦU VÀ ĐỐI CHIẾU TIÊU CHÍ", level=1)
    add_formatted_runs(doc.add_paragraph(), INTRO_TEXT)
    render_docx_table(doc, RUBRIC_TABLE)
    doc.add_heading("MỤC LỤC NỘI DUNG", level=1)
    for title in [*[chapter[0] for chapter in CHAPTERS], "CHƯƠNG 5 HAI MÀN HÌNH NHẬP LIỆU", "CHƯƠNG 6 TÌM KIẾM VÀ HIỂN THỊ KẾT QUẢ", "CHƯƠNG 7 BÁO CÁO VÀ XUẤT FILE", "KẾT QUẢ KIỂM CHỨNG", "PHỤ LỤC SQL CHỨC NĂNG VÀ CÀI ĐẶT"]:
        doc.add_paragraph(title)
    for number, (title, files) in enumerate(CHAPTERS, 1):
        doc.add_page_break()
        doc.add_heading(title, level=1)
        if number == 2:
            for stem, caption in (("erd-chen", "Hình 2.1 ERD Chen tổng thể có bản phóng lớn ERD_Chen_A1.pdf đi kèm"), ("erd-logical", "Hình 2.2 Sơ đồ dữ liệu theo Crow's Foot")):
                add_docx_figure(doc, ROOT / f"docs/erd/{stem}.png", caption)
        for name in files:
            append_docx_markdown(doc, ROOT / name)
    for title, sections in (("CHƯƠNG 5 HAI MÀN HÌNH NHẬP LIỆU", SCREENSHOT_ITEMS[:2]), ("CHƯƠNG 6 TÌM KIẾM VÀ HIỂN THỊ KẾT QUẢ", SCREENSHOT_ITEMS[2:3]), ("CHƯƠNG 7 BÁO CÁO VÀ XUẤT FILE", SCREENSHOT_ITEMS[3:])):
        doc.add_page_break()
        doc.add_heading(title, level=1)
        for heading, description, figures in sections:
            doc.add_heading(heading, level=2)
            add_formatted_runs(doc.add_paragraph(), description)
            for image, caption in figures:
                add_docx_figure(doc, ROOT / "verification/screenshots" / image, caption)
    doc.add_page_break()
    doc.add_heading("KẾT QUẢ KIỂM CHỨNG VÀ PHẠM VI", level=1)
    add_formatted_runs(doc.add_paragraph(), verification_text(run))
    render_docx_table(doc, test_summary(run))
    doc.add_page_break()
    doc.add_heading("PHỤ LỤC SQL CHỨC NĂNG", level=1)
    append_docx_markdown(doc, ROOT / "docs/sql-features/functional-sql-mapping.md")
    for title, name in SQL_APPENDICES:
        doc.add_page_break()
        doc.add_heading(title, level=1)
        append_docx_code_block(doc, (ROOT / name).read_text(encoding="utf-8"))
    doc.save(OUT_DOCX)
    print(f"Generated DOCX: {OUT_DOCX}")


def add_pdf_page_number(canvas, doc) -> None:
    if doc.page > 1:
        canvas.saveState()
        canvas.setFont("VNSerif", 9)
        canvas.setFillColor(colors.black)
        canvas.drawRightString(A4[0] - 2 * cm, 1.3 * cm, f"Trang {doc.page}")
        canvas.drawString(2.5 * cm, 1.3 * cm, "Nhóm 8 | INT1313-20261-01 | Quản lý chuỗi rạp chiếu phim")
        canvas.restoreState()


def generate_pdf(run) -> None:
    register_pdf_fonts()
    styles = make_pdf_styles()
    width = A4[0] - 4.5 * cm
    doc = SimpleDocTemplate(str(OUT_PDF), pagesize=A4, leftMargin=2.5 * cm, rightMargin=2 * cm,
                            topMargin=2.2 * cm, bottomMargin=2.2 * cm, title=GROUP["topic"], author="Nhóm " + GROUP["group"])
    story = []
    for text, style in ((GROUP["school"], "cover_center"), ("BÁO CÁO BÀI TẬP LỚN", "cover_center"), ("HỌC PHẦN CƠ SỞ DỮ LIỆU", "cover_center"), (GROUP["topic"].upper(), "cover_title")):
        story.append(Paragraph(html.escape(text), styles[style]))
        story.append(Spacer(1, .5 * cm))
    metadata = ["| Thông tin | Nội dung |", "|---|---|"] + [f"| {key} | {value} |" for key, value in cover_rows()]
    render_pdf_table(story, metadata, styles, width)
    story += [Spacer(1, cm), Paragraph("Hà Nội, tháng 11 năm 2026", styles["caption"]), PageBreak()]
    story.append(Paragraph("LỜI MỞ ĐẦU VÀ ĐỐI CHIẾU TIÊU CHÍ", styles["h1"]))
    story.append(Paragraph(md_to_rl_xml(INTRO_TEXT), styles["body"]))
    render_pdf_table(story, RUBRIC_TABLE, styles, width)
    story.append(Paragraph("MỤC LỤC NỘI DUNG", styles["h1"]))
    for title in [*[chapter[0] for chapter in CHAPTERS], "CHƯƠNG 5 HAI MÀN HÌNH NHẬP LIỆU", "CHƯƠNG 6 TÌM KIẾM VÀ HIỂN THỊ KẾT QUẢ", "CHƯƠNG 7 BÁO CÁO VÀ XUẤT FILE", "KẾT QUẢ KIỂM CHỨNG", "PHỤ LỤC SQL CHỨC NĂNG VÀ CÀI ĐẶT"]:
        story.append(Paragraph(html.escape(title), styles["body"]))
    for number, (title, files) in enumerate(CHAPTERS, 1):
        story += [PageBreak(), Paragraph(html.escape(title), styles["h1"])]
        if number == 2:
            for stem, caption in (("erd-chen", "Hình 2.1 ERD Chen tổng thể có bản phóng lớn ERD_Chen_A1.pdf đi kèm"), ("erd-logical", "Hình 2.2 Sơ đồ dữ liệu theo Crow's Foot")):
                append_pdf_figure(story, ROOT / f"docs/erd/{stem}.png", caption, styles, max_w=width, max_h=20 * cm)
                story.append(PageBreak())
        for name in files:
            append_pdf_markdown(story, ROOT / name, styles, width)
    for title, sections in (("CHƯƠNG 5 HAI MÀN HÌNH NHẬP LIỆU", SCREENSHOT_ITEMS[:2]), ("CHƯƠNG 6 TÌM KIẾM VÀ HIỂN THỊ KẾT QUẢ", SCREENSHOT_ITEMS[2:3]), ("CHƯƠNG 7 BÁO CÁO VÀ XUẤT FILE", SCREENSHOT_ITEMS[3:])):
        story += [PageBreak(), Paragraph(title, styles["h1"])]
        for heading, description, figures in sections:
            story += [Paragraph(heading, styles["h2"]), Paragraph(md_to_rl_xml(description), styles["body"])]
            for image, caption in figures:
                append_pdf_figure(story, ROOT / "verification/screenshots" / image, caption, styles)
    story += [PageBreak(), Paragraph("KẾT QUẢ KIỂM CHỨNG VÀ PHẠM VI", styles["h1"]), Paragraph(md_to_rl_xml(verification_text(run)), styles["body"])]
    render_pdf_table(story, test_summary(run), styles, width)
    story += [PageBreak(), Paragraph("PHỤ LỤC SQL CHỨC NĂNG", styles["h1"])]
    append_pdf_markdown(story, ROOT / "docs/sql-features/functional-sql-mapping.md", styles, width)
    for title, name in SQL_APPENDICES:
        story += [PageBreak(), Paragraph(title, styles["h1"])]
        append_pdf_code(story, (ROOT / name).read_text(encoding="utf-8"), styles)
    doc.build(story, onFirstPage=add_pdf_page_number, onLaterPages=add_pdf_page_number)
    print(f"Generated PDF: {OUT_PDF}")


if __name__ == "__main__":
    run = verified_run()
    generate_docx(run)
    generate_pdf(run)
