import tempfile
import unittest
from pathlib import Path

import json

from gpsx_evidence.cli import scan_for_secrets
from gpsx_evidence.manifest import build_manifest, verify_manifest, write_manifest

COMPLETE = {"origin": "synthetic", "license": "CC0-1.0", "environment": "unit test", "redaction_review": "reviewer, 2026-10-25"}


class ManifestVerificationTests(unittest.TestCase):
    """Closing tests for review findings D1 and R4."""

    def _root(self, directory: str) -> Path:
        root = Path(directory)
        (root / "README.md").write_text("docs, not a fixture", encoding="utf-8")
        (root / "trace.jsonl").write_text('{"fps": 60}\n', encoding="utf-8")
        (root / "provenance.json").write_text(json.dumps({"trace.jsonl": COMPLETE}), encoding="utf-8")
        return root

    def test_valid_fixtures_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._root(directory)
            write_manifest(root, root / "manifest.json")
            self.assertEqual(verify_manifest(root, root / "manifest.json"), [])

    def test_modified_fixture_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._root(directory)
            write_manifest(root, root / "manifest.json")
            (root / "trace.jsonl").write_text('{"fps": 30}\n', encoding="utf-8")
            self.assertEqual(verify_manifest(root, root / "manifest.json"), ["checksum mismatch: trace.jsonl"])

    def test_unlisted_and_unprovenanced_fixtures_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._root(directory)
            (root / "provenance.json").write_text(json.dumps({"trace.jsonl": {"origin": "synthetic"}}), encoding="utf-8")
            write_manifest(root, root / "manifest.json")
            (root / "extra.jsonl").write_text("{}\n", encoding="utf-8")
            problems = verify_manifest(root, root / "manifest.json")
            self.assertIn("unlisted fixture: extra.jsonl", problems)
            self.assertTrue(any(p.startswith("incomplete provenance for trace.jsonl") for p in problems))

    def test_missing_manifest_is_fine_only_without_fixtures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "README.md").write_text("docs", encoding="utf-8")
            self.assertEqual(verify_manifest(root, root / "manifest.json"), [])
            (root / "trace.jsonl").write_text("{}\n", encoding="utf-8")
            self.assertEqual(len(verify_manifest(root, root / "manifest.json")), 1)

    def test_scan_flags_planted_secret(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "clean.jsonl").write_text('{"fps": 60}\n', encoding="utf-8")
            self.assertEqual(scan_for_secrets([root]), [])
            (root / "leak.log").write_text("ok\nsessionKey: abc\n", encoding="utf-8")
            self.assertEqual(len(scan_for_secrets([root])), 1)


class ManifestTests(unittest.TestCase):
    def test_manifest_is_sorted_and_excludes_itself(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "z.txt").write_text("z", encoding="utf-8")
            (root / "a.txt").write_text("a", encoding="utf-8")
            (root / "manifest.json").write_text("stale", encoding="utf-8")
            manifest = build_manifest(root)
            self.assertEqual([item["path"] for item in manifest["files"]], ["a.txt", "z.txt"])


if __name__ == "__main__":
    unittest.main()

