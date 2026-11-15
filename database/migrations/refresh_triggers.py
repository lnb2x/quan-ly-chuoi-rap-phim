"""Refresh only the 12 source triggers on the existing 14-table schema during maintenance."""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from uuid import uuid4

from check_migration import ROOT, assert_environment, check_database
from db_fingerprint import compute_fingerprint, dump_database, mysql_defaults_file, resolve_mysql_exe, run_mysql_readonly

SOURCE = ROOT / "database/schema/02_triggers.sql"
RECOVERY_DELIMITER = "__CINEMA_END__"  # mysql client accepts at most 16 delimiter characters.
TRIGGER_PATTERN = re.compile(r"CREATE TRIGGER\s+(\w+)\s+(BEFORE|AFTER)\s+(INSERT|UPDATE|DELETE)\s+ON\s+(\w+)\s+FOR EACH ROW\s+(.*?)\$\$", re.S)


def source_triggers(sql: str) -> dict[str, tuple[str, str, str, str]]:
    definitions = {name: (timing, event, table, re.sub(r"\s+", " ", body).strip()) for name, timing, event, table, body in TRIGGER_PATTERN.findall(sql)}
    drops = re.findall(r"(?m)^DROP TRIGGER IF EXISTS (\w+);", sql)
    if len(definitions) != 12 or len(drops) != 12 or set(drops) != definitions.keys():
        raise RuntimeError("Nguồn phải gồm đúng 12 CREATE/DROP TRIGGER tương ứng; dừng trước DDL.")
    return definitions


def unescape_batch(value: str) -> str:
    escapes = {"0": "\0", "t": "\t", "n": "\n", "r": "\r", "\\": "\\"}
    return re.sub(r"\\([0tnr\\])", lambda match: escapes[match[1]], value)


def capture_triggers(mysql: Path, cfg: Path, db: str, names: set[str]) -> tuple[str, dict]:
    actual = set(run_mysql_readonly(mysql, cfg, f"SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='{db}';").splitlines())
    if actual != names:
        raise RuntimeError("Tên trigger live không khớp 12 trigger nguồn; không tự xóa trigger lạ.")
    recovery = ["-- Khôi phục riêng trigger cũ; không sửa dữ liệu/bảng/view. Chỉ chạy trong cửa sổ bảo trì.", f"DELIMITER {RECOVERY_DELIMITER}"]
    metadata = {}
    for name in sorted(names):
        fields = run_mysql_readonly(mysql, cfg, f"SHOW CREATE TRIGGER `{name}`;", db).strip().split("\t")
        if len(fields) < 6:
            raise RuntimeError(f"Thiếu SHOW CREATE TRIGGER: {name}")
        mode, statement, charset, collation = (unescape_batch(fields[index]) for index in (1, 2, 3, 4))
        if RECOVERY_DELIMITER in statement or not re.fullmatch(r"[A-Za-z0-9_]+", charset + collation):
            raise RuntimeError("Định nghĩa trigger không phù hợp định dạng khôi phục an toàn.")
        metadata[name] = {"sql_mode": mode, "create_statement": statement, "character_set_client": charset, "collation_connection": collation}
        recovery.extend([f"DROP TRIGGER IF EXISTS `{name}`{RECOVERY_DELIMITER}", f"SET NAMES {charset} COLLATE {collation}{RECOVERY_DELIMITER}", f"SET SESSION sql_mode='{mode.replace(chr(39), chr(39)*2)}'{RECOVERY_DELIMITER}", statement + RECOVERY_DELIMITER])
    recovery.append("DELIMITER ;")
    return "\n".join(recovery) + "\n", metadata


def live_triggers(mysql: Path, cfg: Path, db: str) -> dict[str, tuple[str, str, str, str]]:
    rows = run_mysql_readonly(mysql, cfg, f"SELECT TRIGGER_NAME,ACTION_TIMING,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,HEX(ACTION_STATEMENT) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='{db}';")
    result = {}
    for row in rows.splitlines():
        name, timing, event, table, body = row.split("\t")
        result[name] = timing, event, table, re.sub(r"\s+", " ", bytes.fromhex(body).decode("utf-8")).strip()
    return result


def execute_triggers(mysql: Path, cfg: Path, db: str, sql: str) -> None:
    result = subprocess.run([str(mysql), f"--defaults-extra-file={cfg}", "--skip-reconnect", db], input=sql, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Cập nhật trigger thất bại: {result.stderr.strip()}")


def assert_preserved(before: dict, after: dict) -> None:
    if before["tables"] != after["tables"] or before["schema_objects"]["views_sha256"] != after["schema_objects"]["views_sha256"]:
        raise RuntimeError("DỪNG: dấu vân tay dữ liệu/bảng/view thay đổi; giữ ứng dụng đóng để đối soát.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", choices=("cinema_chain", "cinema_chain_test", "cinema_chain_migration_clone"), default="cinema_chain")
    parser.add_argument("--maintenance-confirmed", action="store_true", help="Đã đóng ứng dụng, callback server và mọi client ghi cho tới khi đối soát xong")
    args = parser.parse_args()
    if not args.maintenance_confirmed:
        parser.error("Cần --maintenance-confirmed sau khi đóng mọi tiến trình/client ghi; không tự dừng hoặc kill tiến trình.")
    token = f"REFRESH_TRIGGERS_{args.db}"
    if input(f"Chỉ thay 12 trigger, giữ nguyên dữ liệu. Nhập '{token}' để tiếp tục: ").strip() != token:
        print("ĐÃ HỦY: không thay đổi CSDL.")
        return 0
    source = SOURCE.read_text(encoding="utf-8")
    expected = source_triggers(source)
    check_database(args.db, "post", maintenance=True)
    evidence = ROOT / "target/demo-backup" / f"triggers_{args.db}_{datetime.now():%Y%m%d_%H%M%S}_{uuid4().hex[:8]}"
    evidence.mkdir(parents=True, exist_ok=False)
    mysql = resolve_mysql_exe()
    with mysql_defaults_file("root", os.environ.get("MYSQL_ROOT_PASSWORD", "")) as cfg:
        recovery, metadata = capture_triggers(mysql, cfg, args.db, set(expected))
        (evidence / "restore_triggers.sql").write_text(recovery, encoding="utf-8")
        (evidence / "before-triggers.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
        before = compute_fingerprint(ROOT, args.db, use_root=True)
        (evidence / "before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
        dump_database(ROOT, args.db, evidence / "backup.sql", use_root=True)
        assert_environment(mysql, cfg, args.db, "post", maintenance=True)
        if compute_fingerprint(ROOT, args.db, use_root=True) != before:
            raise RuntimeError("CSDL thay đổi trong lúc sao lưu; dừng trước DDL và kiểm lại cửa sổ bảo trì.")
        try:
            execute_triggers(mysql, cfg, args.db, source)
            if live_triggers(mysql, cfg, args.db) != expected:
                raise RuntimeError("Trigger live sau cập nhật không khớp nguồn.")
            after = compute_fingerprint(ROOT, args.db, use_root=True)
            assert_preserved(before, after)
            checks = check_database(args.db, "post", maintenance=True)
        except Exception:
            try:
                execute_triggers(mysql, cfg, args.db, recovery)
                restored = compute_fingerprint(ROOT, args.db, use_root=True)
                if restored["overall_sha256"] != before["overall_sha256"]:
                    raise RuntimeError("Dấu vân tay sau phục hồi không khớp.")
                print(f"Đã phục hồi trigger cũ; kiểm tra lỗi trước khi thử lại. Bằng chứng: {evidence}", file=sys.stderr)
            except Exception as restore_error:
                print(f"PHỤC HỒI CHƯA HOÀN TẤT: {restore_error}. GIỮ ỨNG DỤNG ĐÓNG. Recovery: {evidence / 'restore_triggers.sql'}", file=sys.stderr)
            raise
        (evidence / "after.json").write_text(json.dumps(after, indent=2), encoding="utf-8")
        (evidence / "result.json").write_text(json.dumps({"database": args.db, "tables_data_views_preserved": True, "source_triggers_match": True, "source_sha256": hashlib.sha256(source.encode("utf-8")).hexdigest(), "backup_sha256": hashlib.sha256((evidence / "backup.sql").read_bytes()).hexdigest(), "checks": checks}, indent=2), encoding="utf-8")
    print(f"[TRIGGERS-REFRESHED] 12 trigger khớp nguồn; dữ liệu, DDL bảng và view giữ nguyên. Bằng chứng: {evidence}")
    return 0


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    try:
        raise SystemExit(main())
    except (RuntimeError, ValueError, OSError, EOFError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
