import os
import re
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from db_fingerprint import restrict_file_access

POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class CleanAndAclRegressionTest(unittest.TestCase):
    def test_pom_excludes_demo_backup_from_maven_clean(self):
        tree = ET.parse(ROOT / "pom.xml")
        root = tree.getroot()
        plugins = root.findall("./m:build/m:plugins/m:plugin", POM_NS)
        clean_plugin = None
        for plugin in plugins:
            aid = plugin.findtext("m:artifactId", default="", namespaces=POM_NS).strip()
            if aid == "maven-clean-plugin":
                clean_plugin = plugin
                break
        self.assertIsNotNone(
            clean_plugin,
            "pom.xml phải cấu hình maven-clean-plugin để bảo vệ target/demo-backup",
        )

        config = clean_plugin.find("m:configuration", POM_NS)
        self.assertIsNotNone(config)
        self.assertEqual(
            "true",
            config.findtext(
                "m:excludeDefaultDirectories", default="", namespaces=POM_NS
            )
            .strip()
            .lower(),
            "Phải bật excludeDefaultDirectories=true để Maven không xóa mù toàn bộ target/",
        )

        filesets = config.findall("./m:filesets/m:fileset", POM_NS)
        self.assertEqual(
            1, len(filesets), "Phải có đúng 1 fileset dọn ${project.build.directory}"
        )
        fs = filesets[0]
        self.assertEqual(
            "${project.build.directory}",
            fs.findtext("m:directory", default="", namespaces=POM_NS).strip(),
        )
        includes = {
            el.text.strip()
            for el in fs.findall("./m:includes/m:include", POM_NS)
            if el.text
        }
        excludes = {
            el.text.strip()
            for el in fs.findall("./m:excludes/m:exclude", POM_NS)
            if el.text
        }
        self.assertIn("**", includes)
        self.assertIn("demo-backup", excludes)
        self.assertIn("demo-backup/**", excludes)
        self.assertEqual(
            "false",
            fs.findtext("m:useDefaultExcludes", default="", namespaces=POM_NS)
            .strip()
            .lower(),
        )

    @unittest.skipUnless(os.name == "nt", "Kiểm thử ACL NTFS chỉ áp dụng trên Windows")
    def test_restrict_file_access_allows_owner_deletion_without_parent_dc(self):
        user = os.environ["USERNAME"]
        with tempfile.TemporaryDirectory(prefix="test_acl_rwd_") as folder:
            parent = Path(folder) / "demo-backup"
            parent.mkdir()
            # Parent lacks DC (Delete Child), so deleting child relies on child's own DELETE (D) right.
            subprocess.run(
                [
                    "icacls",
                    str(parent),
                    "/inheritance:r",
                    "/grant:r",
                    f"{user}:(RX,WD,AD)",
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            target = parent / "backup.sql"
            target.write_text("SELECT 1;\n", encoding="utf-8")
            try:
                restrict_file_access(target)
                acl_out = subprocess.run(
                    ["icacls", str(target)],
                    check=True,
                    capture_output=True,
                    text=True,
                ).stdout
                self.assertIn("(R,W,D)", acl_out)
                target.unlink()
                self.assertFalse(target.exists())
            finally:
                subprocess.run(
                    ["icacls", str(parent), "/grant:r", f"{user}:(OI)(CI)F"],
                    check=False,
                    capture_output=True,
                    text=True,
                )
                if target.exists():
                    subprocess.run(
                        ["icacls", str(target), "/grant:r", f"{user}:F"],
                        check=False,
                        capture_output=True,
                        text=True,
                    )

    def test_database_scripts_grant_delete_on_temp_defaults_files(self):
        scripts = [
            ROOT / "database" / "verify_all.ps1",
            ROOT / "database" / "setup.ps1",
            ROOT / "database" / "prepare_demo_db.ps1",
        ]
        for script in scripts:
            content = script.read_text(encoding="utf-8")
            self.assertNotRegex(
                content,
                re.compile(
                    r"icacls\s+\$\w+\s+/inheritance:r\s+/grant:r\s+[^\r\n]*:\(R,W\)"
                ),
                f"{script.name} vẫn dùng icacls (R,W) thiếu quyền D (Delete)",
            )
            self.assertRegex(
                content,
                re.compile(
                    r"icacls\s+\$\w+\s+/inheritance:r\s+/grant:r\s+[^\r\n]*:\(R,W,D\)"
                ),
                f"{script.name} phải cấp (R,W,D) cho tệp cấu hình tạm của user hiện tại",
            )


if __name__ == "__main__":
    unittest.main()
