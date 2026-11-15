import os
from pathlib import Path
import sys
import tempfile
import time
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_test_results import audit_reports, required_suites


class FreshResultsTest(unittest.TestCase):
    def test_detects_missing_new_suite_without_hardcoded_list(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "BookingTest.java").write_text("package com.cinema.unit;\nclass BookingTest {}", encoding="utf-8")
            (root / "RecoveryIT.java").write_text("package com.cinema.integration;\nclass RecoveryIT {}", encoding="utf-8")
            (root / "CinemaDatasetCheck.java").write_text("package com.cinema.integration;", encoding="utf-8")
            expected = required_suites(root)
            xml = root / "TEST-BookingTest.xml"
            xml.write_text('<testsuite name="com.cinema.unit.BookingTest" tests="1" failures="0" errors="0" skipped="0"/>')
            with self.assertRaisesRegex(ValueError, "RecoveryIT"):
                audit_reports([xml], expected, time.time() - 2)

    def test_rejects_stale_failed_skipped_empty_and_duplicate_results(self):
        with tempfile.TemporaryDirectory() as folder:
            xml = Path(folder) / "TEST-test.xml"
            expected = {"sample.Test"}
            for attributes in ('tests="0"', 'tests="1" failures="1"', 'tests="1" errors="1"', 'tests="1" skipped="1"'):
                xml.write_text(f'<testsuite name="sample.Test" {attributes}/>')
                with self.assertRaises(ValueError):
                    audit_reports([xml], expected, time.time() - 2)
            xml.write_text('<testsuite name="sample.Test" tests="2"/>')
            start = time.time() - 2
            with self.assertRaisesRegex(ValueError, "trùng"):
                audit_reports([xml, xml], expected, start)
            summary = audit_reports([xml], expected, start)
            self.assertEqual(2, summary["tests"])
            os.utime(xml, (start - 10, start - 10))
            with self.assertRaisesRegex(ValueError, "XML cũ"):
                audit_reports([xml], expected, start)


if __name__ == "__main__":
    unittest.main()
