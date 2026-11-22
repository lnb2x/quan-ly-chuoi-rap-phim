from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import verify_erd


def _load_fixture_inputs():
    schema_sql = (ROOT / "database/schema/01_schema.sql").read_text(encoding="utf-8")
    fk_sql = (ROOT / "database/schema/01b_foreign_keys.sql").read_text(encoding="utf-8")
    er_xml = (ROOT / "docs/erd/ER.drawio").read_text(encoding="utf-8")
    rel_xml = (ROOT / "docs/erd/luoc-do-quan-he.drawio").read_text(encoding="utf-8")
    erd_md = (ROOT / "docs/erd/erd-explanation.md").read_text(encoding="utf-8")
    return schema_sql, fk_sql, er_xml, rel_xml, erd_md


def test_verify_erd_cli_passes() -> None:
    proc = subprocess.run(
        [sys.executable, "-X", "utf8", "-B", str(ROOT / "tools/verify_erd.py")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=False,
    )
    assert proc.returncode == 0, proc.stderr + proc.stdout
    assert "OK: ER.drawio" in proc.stdout
    assert "OK: luoc-do-quan-he.drawio" in proc.stdout


def test_rejects_stray_or_missing_dashed_underline_on_mave() -> None:
    schema_sql, fk_sql, _, rel_xml, _ = _load_fixture_inputs()
    vn_cols, vn_pks, expected_fks, _ = verify_erd.parse_ddl_and_fks(schema_sql, fk_sql)

    tree = ET.fromstring(rel_xml)
    dash_cell = tree.find(
        ".//mxCell[@id='-4jmYRh8cwk822ICuq2i-171']/mxGeometry/mxPoint[@as='sourcePoint']"
    )
    assert dash_cell is not None
    dash_cell.set("x", "1905")
    broken_xml = ET.tostring(tree, encoding="unicode")

    with pytest.raises(ValueError):
        verify_erd.verify_relational_drawio(broken_xml, vn_cols, vn_pks, expected_fks)


def test_rejects_reversed_fk_arrow() -> None:
    schema_sql, fk_sql, _, rel_xml, _ = _load_fixture_inputs()
    vn_cols, vn_pks, expected_fks, _ = verify_erd.parse_ddl_and_fks(schema_sql, fk_sql)

    tree = ET.fromstring(rel_xml)
    arrow = tree.find(".//mxCell[@id='-4jmYRh8cwk822ICuq2i-172']")
    assert arrow is not None
    src, dst = arrow.get("source", ""), arrow.get("target", "")
    arrow.set("source", dst)
    arrow.set("target", src)
    broken_xml = ET.tostring(tree, encoding="unicode")

    with pytest.raises(ValueError):
        verify_erd.verify_relational_drawio(broken_xml, vn_cols, vn_pks, expected_fks)


def test_rejects_unbound_edge_or_fk_on_er_drawio() -> None:
    schema_sql, fk_sql, er_xml, _, _ = _load_fixture_inputs()
    vn_cols, vn_pks, expected_fks, _ = verify_erd.parse_ddl_and_fks(schema_sql, fk_sql)

    tree = ET.fromstring(er_xml)
    edge = tree.find(".//mxCell[@id='1uulpVPfRRImfR5GbRfo-14']")
    assert edge is not None
    edge.attrib.pop("source", None)
    broken_xml = ET.tostring(tree, encoding="unicode")

    with pytest.raises(ValueError):
        verify_erd.verify_er_drawio(broken_xml, vn_cols, vn_pks, expected_fks)


def test_lossless_detects_lossy_three_way_split() -> None:
    assert not verify_erd.algorithm_lossless(
        ["A", "B", "C"],
        [({"A"}, {"C"}), ({"B"}, {"C"})],
        [{"A"}, {"B"}, {"A", "B"}],
    )
