"""Offline failure/boundary checks; no MySQL process or database is used."""
from contextlib import nullcontext
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "database/migrations"))
import db_fingerprint as fingerprint
import check_migration
from check_migration import assert_environment, assert_zero_results, TABLES, ZERO_COLUMNS
from refresh_triggers import RECOVERY_DELIMITER, assert_preserved, source_triggers, unescape_batch


class MigrationSafetyTest(unittest.TestCase):
    def test_checks_require_every_named_zero_and_reject_errors(self):
        for phase, names in ZERO_COLUMNS.items():
            output = "\n".join(f"{name}\n0.00" for name in sorted(names))
            self.assertEqual(set(assert_zero_results(output, phase)), names)
            for bad in ("1", "-1", "NULL", "NaN", "Infinity", "error"):
                with self.subTest(phase=phase, value=bad), self.assertRaises(RuntimeError):
                    assert_zero_results(output.replace("0.00", bad, 1), phase)
            with self.assertRaises(RuntimeError):
                assert_zero_results("", phase)
            name = sorted(names)[0]
            with self.assertRaises(RuntimeError):
                assert_zero_results(output + f"\n{name}\n0", phase)

    def test_backup_uses_sibling_executable_and_never_overwrites_or_keeps_partial_dump(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mysql = root / "mysql.exe"
            mysql.touch()
            mysql.with_name("mysqldump.exe").touch()
            destination = root / "backup.sql"

            def dump(command, *, stdout, stderr):
                self.assertEqual(Path(command[0]).name, "mysqldump.exe")
                self.assertEqual(command[-1], "cinema_chain")
                self.assertFalse(any(arg.startswith("--password") for arg in command))
                stdout.write("-- dữ liệu tiếng Việt\n".encode("utf-8"))
                return subprocess.CompletedProcess(command, 0, stderr=b"")

            with patch.object(fingerprint, "resolve_mysql_exe", return_value=mysql), patch.object(fingerprint, "mysql_defaults_file", return_value=nullcontext(root / "credentials.cnf")), patch.object(fingerprint, "restrict_file_access"), patch.object(fingerprint.subprocess, "run", side_effect=dump):
                fingerprint.dump_database(root, "cinema_chain", destination, use_root=True)
                original = destination.read_bytes()
                with self.assertRaises(FileExistsError):
                    fingerprint.dump_database(root, "cinema_chain", destination, use_root=True)
                self.assertEqual(destination.read_bytes(), original)
            destination.unlink()
            with patch.object(fingerprint, "resolve_mysql_exe", return_value=mysql), patch.object(fingerprint, "mysql_defaults_file", return_value=nullcontext(root / "credentials.cnf")), patch.object(fingerprint, "restrict_file_access"), patch.object(fingerprint.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, stderr=b"simulated failure")):
                with self.assertRaisesRegex(RuntimeError, "mysqldump"):
                    fingerprint.dump_database(root, "cinema_chain", destination, use_root=True)
                self.assertFalse(destination.exists())

    def test_database_identifier_validation(self):
        for invalid in ("cinema_chain; DROP DATABASE x", "../cinema_chain", "a'", "", "a" * 65):
            with self.subTest(name=invalid), self.assertRaises(ValueError):
                fingerprint.validate_database_name(invalid)

    def test_instance_schema_and_maintenance_guards_fail_closed(self):
        data_dir = str(Path(os.environ.get("USERPROFILE", "")) / ".mysql/data-8.4").replace("\\", "/")
        version = f"8.4.0\tMySQL Community Server\t{data_dir}\n"
        responses = [version, "\n".join(sorted(TABLES)), "ticket_id\nbooking_id\nseat_id\n", "17\t14\n", "0\n"]
        with patch.object(check_migration, "run_mysql_readonly", side_effect=responses), patch.object(check_migration, "load_local_app_credentials", return_value=("cinema_app", "unused")):
            assert_environment(Path("mysql.exe"), Path("config.cnf"), "cinema_chain", "post", maintenance=True)
        for bad in ([version.replace("8.4.0", "8.0.0")], [version, "ticket\n"], responses[:-1] + ["1\n"]):
            with self.subTest(response=bad[-1]), patch.object(check_migration, "run_mysql_readonly", side_effect=bad), patch.object(check_migration, "load_local_app_credentials", return_value=("cinema_app", "unused")), self.assertRaises(RuntimeError):
                assert_environment(Path("mysql.exe"), Path("config.cnf"), "cinema_chain", "post", maintenance=True)

    def test_credentials_reject_option_file_injection_before_writing(self):
        with self.assertRaises(ValueError):
            with fingerprint.mysql_defaults_file("cinema_app", "password\nuser=root"):
                self.fail("Invalid credentials must not create a usable config")

    def test_trigger_refresh_requires_exact_source_and_preserves_data_ddl_views(self):
        self.assertLessEqual(len(RECOVERY_DELIMITER), 16)
        sql = (ROOT / "database/schema/02_triggers.sql").read_text(encoding="utf-8")
        self.assertEqual(len(source_triggers(sql)), 12)
        with self.assertRaises(RuntimeError):
            source_triggers(sql.replace("DROP TRIGGER IF EXISTS trg_booking_validate_upd;", ""))
        before = {"tables": {"ticket": {"data_sha256": "one", "ddl_sha256": "ddl"}}, "schema_objects": {"views_sha256": "views", "triggers_sha256": "old"}}
        after = {"tables": before["tables"], "schema_objects": {"views_sha256": "views", "triggers_sha256": "new"}}
        assert_preserved(before, after)
        for changed in ({"tables": {}, "schema_objects": after["schema_objects"]}, {"tables": before["tables"], "schema_objects": {"views_sha256": "changed"}}):
            with self.assertRaises(RuntimeError):
                assert_preserved(before, changed)
        self.assertEqual(unescape_batch(r"tiếng Việt\nSELECT '\\n';"), "tiếng Việt\nSELECT '\\n';")


if __name__ == "__main__":
    unittest.main()
