#!/usr/bin/env python3
"""
Công cụ tính toán và đối chứng dấu vân tay nội dung bất biến (SHA-256) theo Khóa chính
cho toàn bộ bảng, cấu trúc DDL, Trigger và View của một cơ sở dữ liệu MySQL 8.4 (chỉ đọc).

Phạm vi kiểm chứng:
- Toàn bộ nội dung tất cả dòng của mọi BASE TABLE trong schema, sắp xếp tất định theo PRIMARY KEY.
- Định nghĩa DDL (SHOW CREATE TABLE, bao gồm cả AUTO_INCREMENT hiện hành), danh sách TRIGGER và định nghĩa VIEW.
Giới hạn kiểm chứng:
- Công cụ chỉ đọc qua kết nối TCP (SELECT / SHOW CREATE TABLE), không đọc trực tiếp tệp nhị phân .ibd trên đĩa
  và không kiểm tra biến trạng thái phiên tạm thời hoặc bảng mysql.user toàn cục.
Bảo mật:
- Truyền thông tin xác thực qua tệp tạm --defaults-extra-file (quyền truy cập giới hạn cho user hiện tại)
  và truyền câu lệnh SQL qua stdin; tuyệt đối không truyền mật khẩu hay SQL qua tham số dòng lệnh tiến trình.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator


def resolve_mysql_exe() -> Path:
    override = os.environ.get("CINEMA_MYSQL_EXE_OVERRIDE")
    if override and Path(override).exists():
        return Path(override)
    candidate = Path(os.environ.get("USERPROFILE", "")) / ".mysql" / "mysql-8.4" / "bin" / "mysql.exe"
    if candidate.exists():
        return candidate
    raise RuntimeError(f"Không tìm thấy mysql.exe tại {candidate}")


def load_local_app_credentials(root: Path) -> tuple[str, str]:
    props_path = root / "config" / "application.properties"
    props: dict[str, str] = {}
    if props_path.exists():
        for raw in props_path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    user = os.environ.get("CINEMA_DB_USER") or props.get("db.user") or "cinema_app"
    pwd = os.environ.get("CINEMA_DB_PASSWORD") or props.get("db.password") or ""
    return user, pwd


@contextmanager
def mysql_defaults_file(user: str, pwd: str) -> Iterator[Path]:
    escaped_pwd = pwd.replace("\\", "\\\\").replace('"', '\\"')
    content = (
        "[client]\n"
        "host=127.0.0.1\n"
        "port=3306\n"
        "protocol=TCP\n"
        f"user={user}\n"
        f'password="{escaped_pwd}"\n'
        "\n"
        "[mysql]\n"
        "default-character-set=utf8mb4\n"
        "\n"
        "[mysqldump]\n"
        "default-character-set=utf8mb4\n"
    )
    fd, raw_path = tempfile.mkstemp(prefix="cinema_my_", suffix=".cnf")
    os.close(fd)
    cfg_path = Path(raw_path)
    try:
        cfg_path.write_text(content, encoding="utf-8")
        username = os.environ.get("USERNAME", "")
        if os.name == "nt" and username:
            subprocess.run(
                ["icacls", str(cfg_path), "/inheritance:r", "/grant:r", f"{username}:(R,W)"],
                capture_output=True,
                text=True,
                check=False,
            )
        yield cfg_path
    finally:
        try:
            cfg_path.unlink(missing_ok=True)
        except OSError:
            pass


def run_mysql_readonly(mysql_exe: Path, cfg_path: Path, sql: str, db: str = "") -> str:
    cmd = [
        str(mysql_exe),
        f"--defaults-extra-file={cfg_path}",
        "-N",
        "-B",
    ]
    if db:
        cmd.append(db)
    res = subprocess.run(
        cmd,
        input=sql,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if res.returncode != 0:
        raise RuntimeError(f"Truy vấn chỉ đọc thất bại ({res.returncode}): {res.stderr.strip()}")
    return res.stdout


def compute_fingerprint(root: Path, db_name: str, use_root: bool = False) -> dict:
    mysql_exe = resolve_mysql_exe()
    if use_root:
        user = "root"
        pwd = os.environ.get("MYSQL_ROOT_PASSWORD", "")
    else:
        user, pwd = load_local_app_credentials(root)

    with mysql_defaults_file(user, pwd) as cfg_path:
        tables_raw = run_mysql_readonly(
            mysql_exe,
            cfg_path,
            f"SELECT TABLE_NAME FROM information_schema.TABLES "
            f"WHERE TABLE_SCHEMA = '{db_name}' AND TABLE_TYPE = 'BASE TABLE' "
            f"ORDER BY TABLE_NAME ASC;",
        )
        table_names = [t.strip() for t in tables_raw.splitlines() if t.strip()]

        manifest: dict = {
            "database": db_name,
            "table_count": len(table_names),
            "tables": {},
            "schema_objects": {},
        }
        overall = hashlib.sha256()

        for tbl in table_names:
            pk_raw = run_mysql_readonly(
                mysql_exe,
                cfg_path,
                f"SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                f"WHERE TABLE_SCHEMA = '{db_name}' AND TABLE_NAME = '{tbl}' "
                f"AND CONSTRAINT_NAME = 'PRIMARY' ORDER BY ORDINAL_POSITION ASC;",
            )
            pk_cols = [c.strip() for c in pk_raw.splitlines() if c.strip()]
            order_clause = ", ".join(f"`{c}` ASC" for c in pk_cols) if pk_cols else "1"

            ddl_out = run_mysql_readonly(mysql_exe, cfg_path, f"SHOW CREATE TABLE `{tbl}`;", db_name)
            rows_out = run_mysql_readonly(
                mysql_exe, cfg_path, f"SELECT * FROM `{tbl}` ORDER BY {order_clause};", db_name
            )
            row_count_raw = run_mysql_readonly(
                mysql_exe, cfg_path, f"SELECT COUNT(*) FROM `{tbl}`;", db_name
            ).strip()
            row_count = int(row_count_raw) if row_count_raw else 0

            ddl_sha = hashlib.sha256(ddl_out.encode("utf-8")).hexdigest()
            data_sha = hashlib.sha256(rows_out.encode("utf-8")).hexdigest()
            manifest["tables"][tbl] = {
                "primary_key": pk_cols,
                "row_count": row_count,
                "ddl_sha256": ddl_sha,
                "data_sha256": data_sha,
            }
            overall.update(f"{tbl}|{row_count}|{ddl_sha}|{data_sha}\n".encode("utf-8"))

        triggers_out = run_mysql_readonly(
            mysql_exe,
            cfg_path,
            f"SELECT TRIGGER_NAME, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, ACTION_TIMING, ACTION_STATEMENT "
            f"FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '{db_name}' ORDER BY TRIGGER_NAME ASC;",
        )
        views_out = run_mysql_readonly(
            mysql_exe,
            cfg_path,
            f"SELECT TABLE_NAME, VIEW_DEFINITION "
            f"FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '{db_name}' ORDER BY TABLE_NAME ASC;",
        )
        trg_sha = hashlib.sha256(triggers_out.encode("utf-8")).hexdigest()
        vw_sha = hashlib.sha256(views_out.encode("utf-8")).hexdigest()
        manifest["schema_objects"]["triggers_sha256"] = trg_sha
        manifest["schema_objects"]["views_sha256"] = vw_sha
        overall.update(f"TRIGGERS|{trg_sha}\nVIEWS|{vw_sha}\n".encode("utf-8"))

        manifest["overall_sha256"] = overall.hexdigest()
        return manifest


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Capture or verify read-only SHA-256 DB fingerprint.")
    parser.add_argument("--db", default="cinema_chain", help="Target database name")
    parser.add_argument("--file", required=True, help="JSON manifest path")
    parser.add_argument("--mode", choices=["capture", "verify"], required=True)
    parser.add_argument("--use-root", action="store_true", help="Connect as root (read-only queries)")
    parser.add_argument("--dump-backup", default="", help="Optional SQL backup output path on capture")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    out_file = Path(args.file)
    current = compute_fingerprint(root, args.db, use_root=args.use_root)

    if args.mode == "capture":
        out_file.parent.mkdir(parents=True, exist_ok=True)
        out_file.write_text(json.dumps(current, indent=2, ensure_ascii=False), encoding="utf-8")
        if args.dump_backup:
            dump_path = Path(args.dump_backup)
            dump_path.parent.mkdir(parents=True, exist_ok=True)
            mysqldump_exe = resolve_mysql_bin() / "mysqldump.exe"
            root_pwd = os.environ.get("MYSQL_ROOT_PASSWORD", "")
            with mysql_defaults_file("root", root_pwd) as cfg_path:
                res = subprocess.run(
                    [
                        str(mysqldump_exe),
                        f"--defaults-extra-file={cfg_path}",
                        "--single-transaction",
                        "--triggers",
                        "--routines",
                        "--no-tablespaces",
                        args.db,
                    ],
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                    check=True,
                )
                dump_path.write_text(res.stdout, encoding="utf-8")
        print(
            f"[DB-FINGERPRINT-CAPTURE] DB={args.db} | Tables={current['table_count']} | "
            f"OverallSHA256={current['overall_sha256']}"
        )
        return 0

    expected = json.loads(out_file.read_text(encoding="utf-8"))
    if expected["overall_sha256"] != current["overall_sha256"]:
        print(f"[DB-FINGERPRINT-MISMATCH] DB={args.db} bị thay đổi!", file=sys.stderr)
        print(f"  Expected OverallSHA256: {expected['overall_sha256']}", file=sys.stderr)
        print(f"  Actual   OverallSHA256: {current['overall_sha256']}", file=sys.stderr)
        all_tbls = sorted(set(expected["tables"].keys()) | set(current["tables"].keys()))
        for t in all_tbls:
            e_t = expected["tables"].get(t)
            c_t = current["tables"].get(t)
            if e_t != c_t:
                print(f"  -> Lệch bảng `{t}`: before={e_t} vs after={c_t}", file=sys.stderr)
        if expected["schema_objects"] != current["schema_objects"]:
            print(
                f"  -> Lệch Trigger/View: before={expected['schema_objects']} vs after={current['schema_objects']}",
                file=sys.stderr,
            )
        return 2

    print(
        f"[DB-FINGERPRINT-VERIFIED] DB={args.db} | Tables={current['table_count']} | "
        f"OverallSHA256={current['overall_sha256']} (KHỚP TUYỆT ĐỐI 100%)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
