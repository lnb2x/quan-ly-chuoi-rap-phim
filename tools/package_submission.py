"""Đóng gói bản nộp theo whitelist, không đưa credentials, backup dữ liệu hoặc QA trung gian vào ZIP."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
NAME = "BTL_CSDL_Nhom08_QuanLyChuoiRapPhim"


def submission_files(root: Path) -> dict[str, Path]:
    selected = {}
    allowed = {".java", ".ttf", ".sql", ".ps1", ".py", ".md", ".puml", ".drawio", ".svg", ".png", ".pdf", ".docx", ".json", ".xml", ".log"}
    for folder in ("src", "database", "docs", "tools", "verification/test-results", "verification/submission-2026-11-15"):
        for path in (root / folder).rglob("*"):
            relative = path.relative_to(root)
            if not path.is_file() or path.suffix.lower() not in allowed:
                continue
            if any(part in {"__pycache__", "pdf-render", "pdf-final", "docx-render", "docx-final", "app-pdf-render"} for part in relative.parts):
                continue
            if path.name.startswith("visual-before-"):
                continue
            selected[relative.as_posix()] = path
    for name in (".gitattributes", "README_NOP_BAI.md", "README.md", "DEMO_GUIDE.md", "ARCHITECTURE.md", "pom.xml", "mvnw.cmd", "run_demo.cmd", "config/application.properties.example", "verification/last-run.json",
                 "verification/logs/mvn_clean_verify.log", "verification/logs/gui_verification_test_db.log",
                 "verification/logs/persistence_restart_verify.log", "verification/logs/functional_sql_examples_test_db.log"):
        path = root / name
        if not path.is_file():
            raise FileNotFoundError(f"Thiếu tệp bản nộp: {name}")
        selected[name] = path
    for path in (root / "verification/screenshots").glob("*.png"):
        if not path.name.startswith("demo_cinema_chain_"):
            selected[path.relative_to(root).as_posix()] = path
    for path in (root / "reports/exports").glob("*_Mau.pdf"):
        selected[path.relative_to(root).as_posix()] = path
    jar = root / "target/cinema-chain-management-1.0.0.jar"
    if not jar.is_file():
        raise FileNotFoundError("Chưa có JAR; chạy Maven verify trước khi đóng gói.")
    selected["runtime/" + jar.name] = jar
    libraries = sorted((root / "target/submission-runtime/lib").glob("*.jar"))
    if len(libraries) != 7:
        raise ValueError("Thiếu bộ 7 JAR runtime đã resolve từ Maven.")
    selected.update({"runtime/lib/" + path.name: path for path in libraries})
    return selected


def write_package(root: Path, output: Path) -> tuple[Path, int]:
    selected = submission_files(root)
    forbidden = {"application.properties", ".env", "backup.sql", "agents.md", "tasks.md", "requirements_traceability.md", ".graphifyignore"}
    if any(Path(name).name.lower() in forbidden or Path(name).suffix.lower() == ".cnf" for name in selected):
        raise ValueError("Bản nộp có tệp nội bộ, cấu hình bí mật hoặc backup dữ liệu.")
    run = json.loads((root / "verification/last-run.json").read_text(encoding="utf-8-sig"))
    if any(run.get(key) is not True for key in ("gui_verified", "two_jvm_persistence_verified", "functional_sql_verified", "demo_fingerprint_preserved")):
        raise ValueError("Chưa có bằng chứng hoàn tất các gate.")
    for name, digest in run["tested_source_hashes"].items():
        if hashlib.sha256((root / name).read_bytes()).hexdigest() != digest:
            raise ValueError(f"Source đổi sau kiểm chứng: {name}")
    output.mkdir(parents=True, exist_ok=True)
    archive = output / (NAME + ".zip")
    temporary = output / (NAME + ".zip.part")
    manifest = {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in sorted(selected.items())}
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zipped:
        for name, path in sorted(selected.items()):
            zipped.write(path, NAME + "/" + name)
        zipped.writestr(NAME + "/MANIFEST_SHA256.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    with zipfile.ZipFile(temporary) as zipped:
        if zipped.testzip() is not None:
            raise ValueError("ZIP bị lỗi CRC.")
    temporary.replace(archive)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (output / (archive.name + ".sha256")).write_text(digest + "  " + archive.name + "\n", encoding="ascii")
    return archive, len(selected) + 1


if __name__ == "__main__":
    archive, count = write_package(ROOT, ROOT / "reports/submission")
    print(f"[SUBMISSION-PACKAGED] {archive} | Files={count} | Bytes={archive.stat().st_size}")
