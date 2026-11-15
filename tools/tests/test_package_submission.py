from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import package_submission


class SubmissionPackageTest(unittest.TestCase):
    def test_refuses_secret_or_raw_backup_even_if_selection_regresses(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name in ("config/application.properties", "private/.env", "private/backup.sql", "private/mysql.cnf", "private/BACKUP.SQL", "private/.ENV"):
                with self.subTest(name=name), patch.object(package_submission, "submission_files", return_value={name: root / name}):
                    with self.assertRaisesRegex(ValueError, "bí mật"):
                        package_submission.write_package(root, root / "output")


if __name__ == "__main__":
    unittest.main()
