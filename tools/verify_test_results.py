"""Kiểm tra các tệp báo cáo XML JUnit theo danh sách lớp *Test/*IT trong mã nguồn."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
from datetime import UTC, datetime
from pathlib import Path


def required_suites(source_dir: Path) -> set[str]:
    suites = set()
    for path in source_dir.rglob("*.java"):
        if not path.stem.endswith(("Test", "IT")):
            continue
        package = re.search(
            r"^package\s+([\w.]+)\s*;", path.read_text(encoding="utf-8"), re.MULTILINE
        )
        if package is None:
            raise ValueError(f"Lớp kiểm thử thiếu package: {path}")
        suites.add(f"{package.group(1)}.{path.stem}")
    if not suites:
        raise ValueError("Không tìm thấy lớp kiểm thử bắt buộc trong source.")
    return suites


def audit_reports(paths: list[Path], required: set[str], started_at: float) -> dict:
    suites = {}
    for path in sorted(paths):
        if path.stat().st_mtime < started_at:
            raise ValueError(f"XML cũ, không thuộc lần chạy hiện tại: {path}")
        root = ET.parse(path).getroot()
        name = root.get("name", "")
        if root.tag != "testsuite" or not name:
            raise ValueError(f"XML không phải một testsuite có tên: {path}")
        if name in suites:
            raise ValueError(f"XML trùng suite: {name}")
        counts = {
            key: int(root.get(key, "0"))
            for key in ("tests", "failures", "errors", "skipped")
        }
        if counts["tests"] <= 0 or any(
            counts[key] != 0 for key in ("failures", "errors", "skipped")
        ):
            raise ValueError(
                f"Suite không đạt (không chấp nhận bỏ qua): {name}: {counts}"
            )
        suites[name] = {
            **counts,
            "xml": path.name,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        }
    missing = required - suites.keys()
    if missing:
        raise ValueError("Thiếu suite bắt buộc: " + ", ".join(sorted(missing)))
    return {
        "generated_at_utc": datetime.now(UTC).isoformat(),
        "run_started_at_utc": datetime.fromtimestamp(started_at, UTC).isoformat(),
        "suite_count": len(suites),
        **{
            key: sum(row[key] for row in suites.values())
            for key in ("tests", "failures", "errors", "skipped")
        },
        "suites": suites,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=Path("src/test/java"))
    parser.add_argument("--reports-dir", type=Path, action="append", required=True)
    parser.add_argument("--started-at", required=True, help="ISO-8601 có múi giờ")
    parser.add_argument("--copy-to", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args()
    started = datetime.fromisoformat(args.started_at)
    if started.tzinfo is None:
        parser.error("--started-at phải có múi giờ")
    paths = [path for folder in args.reports_dir for path in folder.glob("TEST-*.xml")]
    summary = audit_reports(
        paths, required_suites(args.source_dir), started.timestamp()
    )
    source_files = [
        *Path("src").rglob("*.java"),
        *Path("database/schema").glob("*.sql"),
        Path("pom.xml"),
    ]
    summary["tested_source_hashes"] = {
        path.as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(source_files)
    }
    args.copy_to.mkdir(parents=True, exist_ok=True)
    for path in paths:
        shutil.copy2(path, args.copy_to / path.name)
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(
        f"[JUNIT XML CHECK] Suites={summary['suite_count']} | Tests={summary['tests']} | Failures=0 | Errors=0 | Skipped=0"
    )


if __name__ == "__main__":
    main()
