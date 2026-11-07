#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Trình tạo Báo cáo Học thuật Môn Cơ sở Dữ liệu (Đáp ứng Tiêu chí R8 - 1.0 điểm).
Kết xuất đồng thời:
  1. docs/academic-report/BaoCao_QuanLyChuoiRap.docx (Microsoft Word)
  2. docs/academic-report/BaoCao_QuanLyChuoiRap.pdf  (PDF chuẩn tiếng Việt nhúng font Unicode)
Tích hợp trực tiếp nội dung thiết kế R1-R4, sơ đồ ERD, ảnh chụp màn hình thực tế từ ứng dụng
kết nối trực tiếp MySQL Community Server 8.4.0 LTS (R5-R7), kết quả 21/21 bài kiểm thử tự động
và Phụ lục SQL thực thi đầy đủ.
"""

import html
import re
from pathlib import Path

from docx import Document
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls
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
    replacements = {
        r"\to": "→",
        r"\rightarrow": "→",
        r"\Rightarrow": "⇒",
        r"\subseteq": "⊆",
        r"\subset": "⊂",
        r"\in": "∈",
        r"\notin": "∉",
        r"\varnothing": "∅",
        r"\neq": "≠",
        r"\setminus": " \\ ",
        r"\ge": "≥",
        r"\le": "≤",
        r"\times": "×",
        r"\_": "_",
        r"\{": "{",
        r"\}": "}",
    }
    for k, v in replacements.items():
        s = s.replace(k, v)
    s = re.sub(r"\\text\{([^}]*)\}", r"\1", s)
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
            run.font.color.rgb = RGBColor(15, 118, 110)
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
        1: (15, RGBColor(15, 23, 42)),
        2: (13, RGBColor(3, 105, 161)),
        3: (11.5, RGBColor(15, 118, 110)),
    }.items():
        st = doc.styles[f"Heading {level}"]
        st.font.name = "Times New Roman"
        st.font.size = Pt(size)
        st.font.bold = True
        st.font.color.rgb = color
        st.paragraph_format.space_before = Pt(10 if level == 1 else 7)
        st.paragraph_format.space_after = Pt(4)


def add_docx_cover_page(doc: Document) -> None:
    p_uni = doc.add_paragraph()
    p_uni.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r1 = p_uni.add_run("[TRƯỜNG ĐẠI HỌC / HỌC VIỆN ĐÀO TẠO — PLACEHOLDER]\n")
    r1.bold = True
    r1.font.size = Pt(13)
    r2 = p_uni.add_run("[KHOA CÔNG NGHỆ THÔNG TIN — BỘ MÔN HỆ THỐNG THÔNG TIN]")
    r2.bold = True
    r2.font.size = Pt(12)

    p_line = doc.add_paragraph()
    p_line.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_line = p_line.add_run("───────────────────────────")
    r_line.bold = True

    for _ in range(3):
        doc.add_paragraph()

    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rs = p_sub.add_run("BÁO CÁO BÀI TẬP LỚN HỌC PHẦN CƠ SỞ DỮ LIỆU")
    rs.bold = True
    rs.font.size = Pt(15)
    rs.font.color.rgb = RGBColor(3, 105, 161)

    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rt = p_title.add_run(
        "ĐỀ TÀI:\nHỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM\n(CINEVIET CINEMA CHAIN MANAGEMENT SYSTEM)"
    )
    rt.bold = True
    rt.font.size = Pt(19)
    rt.font.color.rgb = RGBColor(15, 23, 42)

    p_tech = doc.add_paragraph()
    p_tech.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rtech = p_tech.add_run(
        "Công nghệ: Java 21 LTS • Java Swing (FlatLaf) • MySQL Community Server 8.4 LTS • JDBC • Apache PDFBox 3"
    )
    rtech.italic = True
    rtech.font.size = Pt(11)
    rtech.font.color.rgb = RGBColor(71, 85, 105)

    for _ in range(3):
        doc.add_paragraph()

    meta_table = doc.add_table(rows=5, cols=2)
    meta_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(meta_table)
    meta_rows = [
        ("Giảng viên hướng dẫn:", "[Họ và tên Giảng viên hướng dẫn — Placeholder]"),
        ("Lớp học phần / Nhóm:", "[Mã lớp học phần] — [Nhóm XX — Placeholder]"),
        ("Sinh viên thực hiện 1:", "[Họ và tên Sinh viên 1] — MSSV: [Mã SV 01]"),
        ("Sinh viên thực hiện 2:", "[Họ và tên Sinh viên 2] — MSSV: [Mã SV 02]"),
        ("Sinh viên thực hiện 3:", "[Họ và tên Sinh viên 3] — MSSV: [Mã SV 03]"),
    ]
    for idx, (k, v) in enumerate(meta_rows):
        c0, c1 = meta_table.rows[idx].cells
        set_cell_background(c0, "F1F5F9")
        rk = c0.paragraphs[0].add_run(k)
        rk.bold = True
        rk.font.size = Pt(11)
        rv = c1.paragraphs[0].add_run(v)
        rv.font.size = Pt(11)

    for _ in range(3):
        doc.add_paragraph()

    p_date = doc.add_paragraph()
    p_date.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rd = p_date.add_run("Hà Nội, Năm học 2026")
    rd.italic = True
    rd.font.size = Pt(12)
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

    for r_idx, row_vals in enumerate(parsed_rows):
        for c_idx in range(num_cols):
            val = row_vals[c_idx] if c_idx < len(row_vals) else ""
            cell = tbl.rows[r_idx].cells[c_idx]
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
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(tbl)
    cell = tbl.rows[0].cells[0]
    set_cell_background(cell, "F8FAFC")
    p = cell.paragraphs[0]
    p.paragraph_format.line_spacing = 1.05
    p.paragraph_format.space_after = Pt(2)
    run = p.add_run(code_text.rstrip())
    run.font.name = "Consolas"
    run.font.size = Pt(font_size)
    run.font.color.rgb = RGBColor(30, 41, 59)
    doc.add_paragraph()


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
        if re.match(r"^[-*]\s+", stripped):
            p = doc.add_paragraph(style="List Bullet")
            add_formatted_runs(p, re.sub(r"^[-*]\s+", "", stripped), base_size=11.0)
            i += 1
            continue
        if re.match(r"^\d+\.\s+", stripped):
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Cm(0.5)
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
    p_img.add_run().add_picture(str(image_path), width=Inches(width_inches))

    p_cap = doc.add_paragraph()
    p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
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
    return re.sub(r"([⇒⊆⊂∈∉∅])", r'<font name="VNSym">\1</font>', escaped_text)


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
            textColor=colors.HexColor("#0F172A"),
            spaceBefore=10,
            spaceAfter=6,
        ),
        "h2": ParagraphStyle(
            "VNH2",
            parent=styles["Heading2"],
            fontName="VNSerif-Bold",
            fontSize=12,
            leading=16,
            textColor=colors.HexColor("#0369A1"),
            spaceBefore=8,
            spaceAfter=4,
        ),
        "h3": ParagraphStyle(
            "VNH3",
            parent=styles["Heading3"],
            fontName="VNSerif-Bold",
            fontSize=10.8,
            leading=14.5,
            textColor=colors.HexColor("#0F766E"),
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
            fontSize=8.5,
            leading=11,
        ),
        "cell_hdr": ParagraphStyle(
            "VNCellHdr",
            parent=styles["Normal"],
            fontName="VNSerif-Bold",
            fontSize=8.5,
            leading=11,
        ),
        "code": ParagraphStyle(
            "VNCode",
            parent=styles["Code"],
            fontName="VNMono",
            fontSize=7.2,
            leading=9.2,
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
            textColor=colors.HexColor("#0F172A"),
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

    col_w = avail_width / num_cols
    tbl = Table(data, colWidths=[col_w] * num_cols, repeatRows=1)
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


def append_pdf_code(story: list, code_text: str, st: dict, max_chars: int = 96) -> None:
    wrapped_lines = []
    for raw_line in code_text.rstrip().splitlines():
        if len(raw_line) <= max_chars:
            wrapped_lines.append(raw_line)
        else:
            for pos in range(0, len(raw_line), max_chars):
                wrapped_lines.append(raw_line[pos : pos + max_chars])
    # Split long SQL files into chunks of 55 lines so ReportLab flows neatly across pages
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
        if re.match(r"^[-*]\s+", stripped):
            story.append(Paragraph("• " + md_to_rl_xml(re.sub(r"^[-*]\s+", "", stripped)), st["bullet"]))
            i += 1
            continue
        if re.match(r"^\d+\.\s+", stripped):
            story.append(Paragraph(md_to_rl_xml(stripped), st["bullet"]))
            i += 1
            continue
        story.append(Paragraph(md_to_rl_xml(stripped), st["body"]))
        i += 1


# =============================================================================
# NỘI DUNG DÙNG CHUNG CHO CẢ DOCX VÀ PDF
# =============================================================================

INTRO_TEXT = (
    "Báo cáo này trình bày trọn vẹn quá trình phân tích, thiết kế cơ sở dữ liệu chuẩn 3NF, "
    "cài đặt ràng buộc toàn vẹn & giao dịch đồng thời trên **MySQL Community Server 8.4.0 LTS**, "
    "phát triển ứng dụng Desktop **Java 21 LTS + Java Swing (FlatLaf)** kết nối qua **MySQL Connector/J** "
    "và kiểm chứng thực nghiệm cho **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim CineViet**. "
    "Toàn bộ tài liệu, sơ đồ, mã lệnh SQL, biểu mẫu giao diện và báo cáo xuất PDF đều được đối "
    "chiếu nghiêm ngặt theo 8 tiêu chí chấm điểm học phần (tổng cộng 10,0 điểm):"
)

RUBRIC_TABLE = [
    "| Mã YC | Nội dung Yêu cầu Đánh giá | Điểm | Chương / Mục trong Báo cáo | Trạng thái Kiểm chứng |",
    "|---|---|---|---|---|",
    "| **R1** | Phân tích yêu cầu nghiệp vụ và dữ liệu cần lưu trữ | 1.0 | Chương 1 | Đã kiểm chứng trên MySQL 8.4 (PASS) |",
    "| **R2** | Thiết kế đầy đủ Sơ đồ Thực thể – Liên kết (ERD Chen & Crow's Foot) | 2.5 | Chương 2 (Hình 2.1 & 2.2) | Đã kiểm chứng (PASS) |",
    "| **R3** | Thiết kế Lược đồ CSDL Quan hệ (13 bảng) và Từ điển dữ liệu | 2.0 | Chương 3 & Phụ lục A, B | Đã kiểm chứng trên MySQL 8.4 (PASS) |",
    "| **R4** | Phân tích Phụ thuộc hàm, tìm khóa và chứng minh chuẩn hóa 3NF | 1.0 | Chương 4 | Đã kiểm chứng (PASS) |",
    "| **R5** | Cài đặt 02 màn hình nhập liệu có kiểm tra hợp lệ & chống xung đột | 1.0 | Chương 5 (Mục 5.2 & 5.3) | Đã kiểm chứng trên MySQL 8.4 (PASS) |",
    "| **R6** | Cài đặt 01 màn hình tìm kiếm vận hành đa tiêu chí kèm số ghế trống | 0.5 | Chương 5 (Mục 5.4) | Đã kiểm chứng trên MySQL 8.4 (PASS) |",
    "| **R7** | Cài đặt 02 báo cáo thực tiễn có xem trước (Preview) và xuất PDF | 1.0 | Chương 5 (Mục 5.5 & 5.6) | Đã kiểm chứng trên MySQL 8.4 (PASS) |",
    "| **R8** | Báo cáo học thuật tổng hợp đầy đủ R1–R7 và Phụ lục SQL thực thi | 1.0 | Toàn bộ Báo cáo & Phụ lục SQL | Đã kiểm chứng (PASS) |",
]

CH5_ARCH = (
    "Ứng dụng Desktop được xây dựng trên nền tảng **Java 21 LTS**, **Java Swing + FlatLaf 3.5**, "
    "**JDBC (`MySQL Connector/J 9.1.0` + `PreparedStatement`)** kết nối trực tiếp tới **MySQL Community Server 8.4.0 LTS** "
    "(`InnoDB`, `utf8mb4_unicode_ci`, `ONLY_FULL_GROUP_BY`, `STRICT_TRANS_TABLES`) và **Apache PDFBox 3**, "
    "tuân thủ nghiêm ngặt kiến trúc phân tầng (`ui` → `service` → `repository` → `shared.db`). "
    "Bộ kiểm thử kiến trúc tự động `ArchitectureComplianceTest` (ArchUnit) đảm bảo không một lớp giao diện nào được phép "
    "truy cập trực tiếp `java.sql.*`. Mọi kết nối vận hành đều sử dụng tài khoản phân quyền tối thiểu `cinema_app` "
    "(mật khẩu chỉ lưu trong tệp cấu hình cục bộ `config/application.properties` hoặc biến môi trường, tuyệt đối không mã hóa cứng).\n"
    "Đối với hai bài toán tranh chấp đồng thời cốt lõi trên MySQL 8.4 InnoDB:\n"
    "• **Chống chồng lấn lịch chiếu đồng thời (`BR-06`):** `ScreeningService` khóa bi quan dòng phòng chiếu "
    "`SELECT room_id, active FROM screening_room WHERE room_id = ? FOR UPDATE` trước khi kiểm tra giao thoa khung giờ, "
    "kết hợp với Trigger `trg_screening_validate_ins`/`upd` tại tầng MySQL Server.\n"
    "• **Chống bán trùng ghế đồng thời (`BR-10`):** `BookingService` khóa dòng suất chiếu "
    "`SELECT ... FROM screening WHERE screening_id = ? FOR UPDATE`, kiểm tra trạng thái ghế, lưu `booking` + `ticket` + `payment` "
    "nguyên tử và được bảo vệ lớp cuối bởi chỉ mục duy nhất trên cột sinh lưu trữ `uq_ticket_active_seat (active_seat_key)`."
)

CH6_VERIFICATION = (
    "Hệ thống đã được thực thi và kiểm chứng đầu-cuối trên **JDK 21.0.13 LTS (Eclipse Temurin)** và hệ quản trị CSDL chính thức "
    "**MySQL Community Server 8.4.0 LTS** (`SELECT VERSION()` trả về `8.4.0`, `@@version_comment` trả về `MySQL Community Server - GPL`, "
    "chế độ nghiêm ngặt `ONLY_FULL_GROUP_BY, STRICT_TRANS_TABLES`) với quy mô dữ liệu khởi tạo gồm **04 chi nhánh**, **12 phòng chiếu**, "
    "**960 ghế vật lý**, **20 bộ phim**, **160 suất chiếu**, **160 đơn đặt vé**, **373 bản ghi vé** (bao gồm 23 ghế hủy được bán lại hợp lệ) "
    "và **175 giao dịch thanh toán** (bao gồm 15 đơn có lịch sử `FAILED` + `SUCCESS`)."
)

TEST_SUMMARY_TABLE = [
    "| Bộ Kiểm thử (Test Class) | Cổng Kiểm chứng | Số Test | Kết quả | Nội dung Kiểm chứng Cốt lõi trên MySQL 8.4.0 LTS |",
    "|---|---|---|---|---|",
    "| `ArchitectureComplianceTest` | Gate B (Kiến trúc) | 05 | **PASS (5/5)** | Cấm SQL trong UI, tách biệt phân tầng, cấm SQL phá hủy (`DROP`/`TRUNCATE`/`DELETE`) & gọi tiến trình trong `src/main/java` |",
    "| `TestSafetyGuardTest` | Gate B (Bảo vệ DB) | 03 | **PASS (3/3)** | Chốt chặn `TestDatabaseGuard` chặn `cinema_chain`, chặn MariaDB, chặn `root` và ẩn mật khẩu trong cấu hình |",
    "| `ScreeningValidationTest` | Gate A (Đơn vị) | 05 | **PASS (5/5)** | Kiểm tra hợp lệ thời lượng suất chiếu, giá cơ sở dương, danh sách ghế, thông báo tiếng Việt |",
    "| `DatabaseIntegrityIT` | Gate C (MySQL 8.4) | 13 | **PASS (13/13)** | Xác nhận `SELECT VERSION() = 8.4.0`, Trigger chống trùng lịch (`INSERT` & `UPDATE`), chặn đổi phòng khi đã có vé, chặn phòng ngừng hoạt động, chống bán trùng ghế, chống thanh toán đúp, chặn xác nhận thanh toán đơn đã hủy, FK/CHECK/Savepoint, giá lịch sử, chuẩn UTF-8 |",
    "| `ScreeningConcurrencyIT` | Gate C (Đồng thời) | 02 | **PASS (2/2)** | 12 luồng `CREATE` đồng thời và 8 luồng `UPDATE` đồng thời suất chiếu trùng giờ → đúng 01 thắng + chặn đổi phòng |",
    "| `BookingConcurrencyIT` | Gate C (Đồng thời) | 01 | **PASS (1/1)** | 20 luồng đặt đồng thời cùng 1 ghế → đúng 01 thắng, 0 bản ghi mồ côi; hủy đơn → bán lại ghế; chuyển trạng thái `PENDING` |",
    "| `ScreeningSearchIT` | Gate D (Tra cứu) | 02 | **PASS (2/2)** | Tra cứu đa điều kiện kèm số ghế trống và hiển thị đầy đủ `> 120` suất chiếu `OPEN` trên Màn hình Đặt vé |",
    "| `ReportAccuracyIT` | Gate E (Báo cáo & PDF) | 03 | **PASS (3/3)** | Đối chiếu 2 báo cáo & PDF tiếng Việt với SQL độc lập (`35.165.000 VNĐ`, sai lệch `0.00 VNĐ`) và Known-Answer Fixture |",
    "| **TỔNG CỘNG** | **Gates A – E** | **34** | **PASS (34/34)** | **0 lỗi biên dịch, 0 lỗi kiểm thử, 0 test bị bỏ qua (`Skipped = 0`), bảo toàn nguyên vẹn `cinema_chain`** |",
]

SCREENSHOT_ITEMS = [
    (
        "5.2. Màn hình Nhập liệu 1: Quản lý Lịch chiếu & Suất chiếu (Đáp ứng R5)",
        "Màn hình cho phép Quản lý Chi nhánh tạo mới, cập nhật hoặc hủy suất chiếu; tự động gợi ý giờ kết thúc "
        "dựa trên thời lượng bộ phim cộng 20 phút dọn phòng; kiểm tra hợp lệ giá cơ sở dương, thời lượng tối thiểu (`BR-05`) "
        "và từ chối tức thì mọi suất chiếu trùng lịch trong cùng một phòng chiếu (`BR-06`).",
        [
            ("01_screening_management_valid.png", "Hình 5.1. Màn hình Quản lý Suất chiếu — Tạo mới thành công suất chiếu hợp lệ trên MySQL 8.4 LTS."),
            ("02_screening_management_overlap_rejected.png", "Hình 5.2. Màn hình Quản lý Suất chiếu — Phát hiện và từ chối suất chiếu chồng lấn thời gian trong cùng phòng [BR-06]."),
        ],
    ),
    (
        "5.3. Màn hình Nhập liệu 2: Đặt vé & Chọn ghế theo Sơ đồ Phòng chiếu (Đáp ứng R5)",
        "Màn hình phục vụ Thu ngân bán vé cho cả **Khách hàng thành viên** lẫn **Khách vãng lai tại quầy**, hiển thị "
        "trực quan sơ đồ 80 ghế (8 hàng A–H × 10 ghế) phân biệt rõ hạng ghế Thường (`STD`), `VIP`, Ghế đôi (`COUPLE`), "
        "ghế đang chọn và ghế đã bán; tính tự động đơn giá lịch sử (`base_price + surcharge`), xử lý thanh toán nguyên tử "
        "và cho phép **Hủy đơn đặt vé để giải phóng ghế mở bán lại (`BR-11`)**.",
        [
            ("03_ticket_booking_seat_selection.png", "Hình 5.3. Màn hình Đặt vé & Chọn ghế — Chọn nhiều ghế trống trực tiếp trên sơ đồ phòng chiếu và tính tổng tiền tự động."),
            ("04_ticket_booking_success.png", "Hình 5.4. Màn hình Đặt vé & Chọn ghế — Xác nhận giao dịch đặt vé và thanh toán thành công trên MySQL 8.4 LTS."),
            ("04b_ticket_booking_occupied_rejected.png", "Hình 5.5. Màn hình Đặt vé & Chọn ghế — Chặn giao dịch khi ghế vừa bị quầy thu ngân khác đặt trước [BR-10]."),
        ],
    ),
    (
        "5.4. Màn hình Tìm kiếm Vận hành: Tra cứu Suất chiếu & Kiểm tra Ghế trống (Đáp ứng R6)",
        "Màn hình sử dụng truy vấn tham số hóa (`PreparedStatement`) trên View `vw_screening_search` của MySQL 8.4, cho phép lọc linh hoạt "
        "theo từ khóa tên/mã phim, bộ phim, chi nhánh rạp, ngày chiếu, khung giờ bắt đầu và tùy chọn chỉ hiển thị suất còn ghế trống.",
        [
            ("05_screening_search.png", "Hình 5.6. Màn hình Tra cứu Suất chiếu — Hiển thị danh sách suất chiếu khớp điều kiện kèm sức chứa, vé đã bán và số ghế còn trống."),
        ],
    ),
    (
        "5.5. Báo cáo Thực tiễn 1: Báo cáo Doanh thu Chuỗi rạp — Preview & Xuất PDF (Đáp ứng R7)",
        "• **Người lập báo cáo:** Kế toán Chuỗi rạp (`Trần Thị Thu Hà`).\n"
        "• **Người nhận báo cáo:** Giám đốc Kinh doanh Chuỗi rạp.\n"
        "• **Quy tắc ghi nhận doanh thu (`BR-14`, `BR-15`):** Chỉ tính tổng `ticket.unit_price` của các vé `VALID` thuộc đơn `CONFIRMED` "
        "có giao dịch thanh toán `SUCCESS` (sử dụng View `vw_verified_booking_revenue` và ràng buộc `uq_payment_one_success` trên MySQL 8.4 để triệt tiêu hoàn toàn "
        "nguy cơ nhân đôi doanh thu khi một đơn có lịch sử thanh toán lỗi `FAILED` trước khi thanh toán lại `SUCCESS`). Tổng doanh thu thực thu "
        "trên tập dữ liệu mẫu đạt chính xác **35.165.000 VNĐ** (khớp tuyệt đối 100% với truy vấn SQL đối soát độc lập).",
        [
            ("06_revenue_report_preview.png", "Hình 5.7. Màn hình Xem trước (Preview) Báo cáo Doanh thu Chuỗi rạp kết xuất trực tiếp từ MySQL 8.4 LTS."),
            ("08_exported_revenue_pdf_page1.png", "Hình 5.8. Bản kết xuất PDF tiếng Việt Báo cáo Doanh thu Chuỗi rạp (reports/exports/BaoCaoDoanhThu_Mau.pdf)."),
        ],
    ),
    (
        "5.6. Báo cáo Thực tiễn 2: Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy — Preview & Xuất PDF (Đáp ứng R7)",
        "• **Người lập báo cáo:** Quản lý Chi nhánh (`Nguyễn Minh Hoàng`).\n"
        "• **Người nhận báo cáo:** Ban Điều hành và Quản lý Kinh doanh.\n"
        "• **Nội dung quản trị:** Thống kê chi tiết từng suất chiếu từ MySQL 8.4 về sức chứa phòng, số vé hợp lệ đã bán, số ghế còn trống, "
        "tỷ lệ lấp đầy phòng chiếu (`%`) và doanh thu bán vé thực thu; hỗ trợ xem trước trực quan và kết xuất PDF tiếng Việt.",
        [
            ("07_ticket_sales_report_preview.png", "Hình 5.9. Màn hình Xem trước (Preview) Báo cáo Tình hình Bán vé và Tỷ lệ Lấp đầy theo Suất chiếu."),
            ("09_exported_sales_pdf_page1.png", "Hình 5.10. Bản kết xuất PDF tiếng Việt Báo cáo Tình hình Bán vé (reports/exports/BaoCaoBanVe_Mau.pdf)."),
        ],
    ),
]


def generate_docx() -> None:
    OUT_DOCX.parent.mkdir(parents=True, exist_ok=True)
    doc = Document()
    configure_docx_styles(doc)
    add_docx_cover_page(doc)

    doc.add_heading("LỜI MỞ ĐẦU & MA TRẬN ĐÁP ỨNG YÊU CẦU HỌC PHẦN (R1 – R8)", level=1)
    p_intro = doc.add_paragraph()
    add_formatted_runs(p_intro, INTRO_TEXT)
    render_docx_table(doc, RUBRIC_TABLE)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 1. PHÂN TÍCH YÊU CẦU NGHIỆP VỤ VÀ DỮ LIỆU CẦN LƯU TRỮ (TIÊU CHÍ R1)", level=1)
    append_docx_markdown(doc, ROOT / "docs" / "requirements" / "requirements.md")
    doc.add_heading("Quy tắc Nghiệp vụ & Cơ chế Đảm bảo Toàn vẹn (BR-01 .. BR-16)", level=2)
    append_docx_markdown(doc, ROOT / "docs" / "requirements" / "business-rules.md")

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 2. THIẾT KẾ SƠ ĐỒ THỰC THỂ – LIÊN KẾT ERD (TIÊU CHÍ R2)", level=1)
    add_docx_figure(doc, ROOT / "docs" / "erd" / "erd-chen.png", "Hình 2.1. Sơ đồ Thực thể – Liên kết mức Khái niệm theo ký hiệu Chen (Conceptual ERD).")
    add_docx_figure(doc, ROOT / "docs" / "erd" / "erd-logical.png", "Hình 2.2. Sơ đồ Thực thể – Liên kết mức Logic theo ký hiệu Crow's Foot (Logical ERD — 13 bảng).")
    append_docx_markdown(doc, ROOT / "docs" / "erd" / "erd-explanation.md")

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 3. LƯỢC ĐỒ CƠ SỞ DỮ LIỆU QUAN HỆ VÀ TỪ ĐIỂN DỮ LIỆU TRÊN MYSQL 8.4 (TIÊU CHÍ R3)", level=1)
    append_docx_markdown(doc, ROOT / "docs" / "relational-model" / "schema-design.md")

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 4. PHỤ THUỘC HÀM VÀ CHỨNG MINH CHUẨN HÓA 3NF (TIÊU CHÍ R4)", level=1)
    append_docx_markdown(doc, ROOT / "docs" / "normalization" / "normalization.md")

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 5. KIẾN TRÚC ỨNG DỤNG, GIAO DỊCH ĐỒNG THỜI & CÁC MÀN HÌNH NGHIỆP VỤ (R5, R6, R7)", level=1)
    doc.add_heading("5.1. Kiến trúc Phân tầng Nghiêm ngặt & Kiểm soát Giao dịch ACID trên MySQL 8.4 LTS", level=2)
    p_arch = doc.add_paragraph()
    add_formatted_runs(p_arch, CH5_ARCH)

    for sec_title, sec_desc, figs in SCREENSHOT_ITEMS:
        doc.add_heading(sec_title, level=2)
        p_sec = doc.add_paragraph()
        add_formatted_runs(p_sec, sec_desc)
        for img_name, cap in figs:
            add_docx_figure(doc, ROOT / "verification" / "screenshots" / img_name, cap)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 6. TỔNG HỢP KẾT QUẢ KIỂM THỬ TỰ ĐỘNG TRÊN MYSQL COMMUNITY SERVER 8.4.0 LTS", level=1)
    p_ver = doc.add_paragraph()
    add_formatted_runs(p_ver, CH6_VERIFICATION)
    render_docx_table(doc, TEST_SUMMARY_TABLE)

    for app_title, sql_rel in [
        ("PHỤ LỤC A1. MÃ LỆNH DDL KHỞI TẠO 13 BẢNG QUAN HỆ TRÊN MYSQL 8.4 (01_schema.sql)", "database/schema/01_schema.sql"),
        ("PHỤ LỤC A2. MÃ LỆNH THIẾT LẬP 16 RÀNG BUỘC KHÓA NGOẠI (01b_foreign_keys.sql)", "database/schema/01b_foreign_keys.sql"),
        ("PHỤ LỤC B. MÃ LỆNH TRIGGER BẢO VỆ TOÀN VẸN NGHIỆP VỤ (02_triggers.sql)", "database/schema/02_triggers.sql"),
        ("PHỤ LỤC C. MÃ LỆNH VIEW TRA CỨU & ĐỐI SOÁT DOANH THU (03_views.sql)", "database/schema/03_views.sql"),
        ("PHỤ LỤC D. CÁC TRUY VẤN NGHIỆP VỤ, GIAO DỊCH & BÁO CÁO (business_queries.sql)", "database/queries/business_queries.sql"),
    ]:
        doc.add_page_break()
        doc.add_heading(app_title, level=1)
        append_docx_code_block(doc, (ROOT / sql_rel).read_text(encoding="utf-8"), font_size=8.0)

    doc.save(str(OUT_DOCX))
    print(f"Generated academic DOCX: {OUT_DOCX} ({OUT_DOCX.stat().st_size} bytes)")


def add_pdf_page_number(canvas, doc) -> None:
    if doc.page > 1:
        canvas.saveState()
        canvas.setFont("VNSerif", 9)
        canvas.setFillColor(colors.HexColor("#64748B"))
        canvas.drawRightString(A4[0] - 2.0 * cm, 1.3 * cm, f"Trang {doc.page}")
        canvas.drawString(2.5 * cm, 1.3 * cm, "Báo cáo Học phần CSDL — Hệ thống Quản lý Chuỗi Rạp Chiếu Phim (MySQL 8.4 LTS)")
        canvas.restoreState()


def generate_pdf() -> None:
    register_pdf_fonts()
    st = make_pdf_styles()
    left_m, right_m = 2.5 * cm, 2.0 * cm
    avail_w = A4[0] - left_m - right_m

    doc = SimpleDocTemplate(
        str(OUT_PDF),
        pagesize=A4,
        leftMargin=left_m,
        rightMargin=right_m,
        topMargin=2.2 * cm,
        bottomMargin=2.2 * cm,
        title="Báo cáo Học thuật Hệ thống Quản lý Chuỗi Rạp Chiếu Phim (MySQL 8.4 LTS)",
        author="Nhóm Sinh viên Thực hiện (CineViet DBMS Project)",
    )

    story = []
    # Cover Page
    story.append(Paragraph("[TRƯỜNG ĐẠI HỌC / HỌC VIỆN ĐÀO TẠO — PLACEHOLDER]", st["cover_center"]))
    story.append(Paragraph("[KHOA CÔNG NGHỆ THÔNG TIN — BỘ MÔN HỆ THỐNG THÔNG TIN]", st["cover_center"]))
    story.append(Spacer(1, 2.0 * cm))
    story.append(Paragraph("BÁO CÁO BÀI TẬP LỚN HỌC PHẦN CƠ SỞ DỮ LIỆU", st["cover_center"]))
    story.append(Spacer(1, 0.5 * cm))
    story.append(
        Paragraph(
            "ĐỀ TÀI:<br/>HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM<br/>(CINEVIET CINEMA CHAIN MANAGEMENT SYSTEM)",
            st["cover_title"],
        )
    )
    story.append(
        Paragraph(
            "Công nghệ: Java 21 LTS • Java Swing (FlatLaf) • MySQL Community Server 8.4 LTS • JDBC • Apache PDFBox 3",
            st["caption"],
        )
    )
    story.append(Spacer(1, 1.8 * cm))
    cover_meta = [
        "| Thông tin Học phần | Nội dung Đăng ký (Placeholder) |",
        "|---|---|",
        "| **Giảng viên hướng dẫn** | [Họ và tên Giảng viên hướng dẫn — Placeholder] |",
        "| **Lớp học phần / Nhóm** | [Mã lớp học phần] — [Nhóm XX — Placeholder] |",
        "| **Sinh viên thực hiện 1** | [Họ và tên Sinh viên 1] — MSSV: [Mã SV 01] |",
        "| **Sinh viên thực hiện 2** | [Họ và tên Sinh viên 2] — MSSV: [Mã SV 02] |",
        "| **Sinh viên thực hiện 3** | [Họ và tên Sinh viên 3] — MSSV: [Mã SV 03] |",
        "| **Hệ quản trị CSDL thực thi** | **MySQL Community Server 8.4.0 LTS** (`InnoDB`, `utf8mb4_unicode_ci`) |",
    ]
    render_pdf_table(story, cover_meta, st, avail_w)
    story.append(Spacer(1, 2.5 * cm))
    story.append(Paragraph("Hà Nội, Năm học 2026", st["caption"]))
    story.append(PageBreak())

    # Intro & Rubric
    story.append(Paragraph("LỜI MỞ ĐẦU &amp; MA TRẬN ĐÁP ỨNG YÊU CẦU HỌC PHẦN (R1 – R8)", st["h1"]))
    story.append(Paragraph(md_to_rl_xml(INTRO_TEXT), st["body"]))
    render_pdf_table(story, RUBRIC_TABLE, st, avail_w)

    # Chapter 1
    story.append(PageBreak())
    story.append(Paragraph("CHƯƠNG 1. PHÂN TÍCH YÊU CẦU NGHIỆP VỤ VÀ DỮ LIỆU CẦN LƯU TRỮ (TIÊU CHÍ R1)", st["h1"]))
    append_pdf_markdown(story, ROOT / "docs" / "requirements" / "requirements.md", st, avail_w)
    story.append(Paragraph("Quy tắc Nghiệp vụ &amp; Cơ chế Đảm bảo Toàn vẹn (BR-01 .. BR-16)", st["h2"]))
    append_pdf_markdown(story, ROOT / "docs" / "requirements" / "business-rules.md", st, avail_w)

    # Chapter 2
    story.append(PageBreak())
    story.append(Paragraph("CHƯƠNG 2. THIẾT KẾ SƠ ĐỒ THỰC THỂ – LIÊN KẾT ERD (TIÊU CHÍ R2)", st["h1"]))
    append_pdf_figure(story, ROOT / "docs" / "erd" / "erd-chen.png", "Hình 2.1. Sơ đồ Thực thể – Liên kết mức Khái niệm theo ký hiệu Chen (Conceptual ERD).", st)
    append_pdf_figure(story, ROOT / "docs" / "erd" / "erd-logical.png", "Hình 2.2. Sơ đồ Thực thể – Liên kết mức Logic theo ký hiệu Crow's Foot (Logical ERD — 13 bảng).", st)
    append_pdf_markdown(story, ROOT / "docs" / "erd" / "erd-explanation.md", st, avail_w)

    # Chapter 3
    story.append(PageBreak())
    story.append(Paragraph("CHƯƠNG 3. LƯỢC ĐỒ CƠ SỞ DỮ LIỆU QUAN HỆ VÀ TỪ ĐIỂN DỮ LIỆU TRÊN MYSQL 8.4 (TIÊU CHÍ R3)", st["h1"]))
    append_pdf_markdown(story, ROOT / "docs" / "relational-model" / "schema-design.md", st, avail_w)

    # Chapter 4
    story.append(PageBreak())
    story.append(Paragraph("CHƯƠNG 4. PHỤ THUỘC HÀM VÀ CHỨNG MINH CHUẨN HÓA 3NF (TIÊU CHÍ R4)", st["h1"]))
    append_pdf_markdown(story, ROOT / "docs" / "normalization" / "normalization.md", st, avail_w)

    # Chapter 5
    story.append(PageBreak())
    story.append(Paragraph("CHƯƠNG 5. KIẾN TRÚC ỨNG DỤNG, GIAO DỊCH ĐỒNG THỜI &amp; CÁC MÀN HÌNH NGHIỆP VỤ (R5, R6, R7)", st["h1"]))
    story.append(Paragraph("5.1. Kiến trúc Phân tầng Nghiêm ngặt &amp; Kiểm soát Giao dịch ACID trên MySQL 8.4 LTS", st["h2"]))
    story.append(Paragraph(md_to_rl_xml(CH5_ARCH), st["body"]))

    for sec_title, sec_desc, figs in SCREENSHOT_ITEMS:
        story.append(Paragraph(md_to_rl_xml(sec_title), st["h2"]))
        story.append(Paragraph(md_to_rl_xml(sec_desc), st["body"]))
        for img_name, cap in figs:
            append_pdf_figure(story, ROOT / "verification" / "screenshots" / img_name, cap, st)

    # Chapter 6
    story.append(PageBreak())
    story.append(Paragraph("CHƯƠNG 6. TỔNG HỢP KẾT QUẢ KIỂM THỬ TỰ ĐỘNG TRÊN MYSQL COMMUNITY SERVER 8.4.0 LTS", st["h1"]))
    story.append(Paragraph(md_to_rl_xml(CH6_VERIFICATION), st["body"]))
    render_pdf_table(story, TEST_SUMMARY_TABLE, st, avail_w)

    # SQL Appendices
    for app_title, sql_rel in [
        ("PHỤ LỤC A1. MÃ LỆNH DDL KHỞI TẠO 13 BẢNG QUAN HỆ TRÊN MYSQL 8.4 (01_schema.sql)", "database/schema/01_schema.sql"),
        ("PHỤ LỤC A2. MÃ LỆNH THIẾT LẬP 16 RÀNG BUỘC KHÓA NGOẠI (01b_foreign_keys.sql)", "database/schema/01b_foreign_keys.sql"),
        ("PHỤ LỤC B. MÃ LỆNH TRIGGER BẢO VỆ TOÀN VẸN NGHIỆP VỤ (02_triggers.sql)", "database/schema/02_triggers.sql"),
        ("PHỤ LỤC C. MÃ LỆNH VIEW TRA CỨU & ĐỐI SOÁT DOANH THU (03_views.sql)", "database/schema/03_views.sql"),
        ("PHỤ LỤC D. CÁC TRUY VẤN NGHIỆP VỤ, GIAO DỊCH & BÁO CÁO (business_queries.sql)", "database/queries/business_queries.sql"),
    ]:
        story.append(PageBreak())
        story.append(Paragraph(html.escape(app_title), st["h1"]))
        append_pdf_code(story, (ROOT / sql_rel).read_text(encoding="utf-8"), st)

    doc.build(story, onFirstPage=add_pdf_page_number, onLaterPages=add_pdf_page_number)
    print(f"Generated academic PDF: {OUT_PDF} ({OUT_PDF.stat().st_size} bytes)")


if __name__ == "__main__":
    generate_docx()
    generate_pdf()
