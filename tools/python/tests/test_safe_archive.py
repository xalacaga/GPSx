import stat
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile, ZipInfo

from gpsx_evidence.safe_archive import (
    MAX_ENTRIES,
    MAX_ENTRY_BYTES,
    UnsafeArchiveError,
    extract_diagnostic_zip,
)


class SafeArchiveTests(unittest.TestCase):
    def _archive(self, entries: list[tuple[str | ZipInfo, bytes]]) -> tuple[tempfile.TemporaryDirectory, Path]:
        temporary = tempfile.TemporaryDirectory()
        archive = Path(temporary.name) / "bundle.zip"
        with ZipFile(archive, "w") as bundle:
            for name, contents in entries:
                bundle.writestr(name, contents)
        return temporary, archive

    def test_extracts_regular_files(self) -> None:
        temporary, archive = self._archive([("logs/session.jsonl", b"{}\n")])
        with temporary:
            destination = Path(temporary.name) / "out"
            self.assertEqual([path.relative_to(destination).as_posix() for path in extract_diagnostic_zip(archive, destination)], ["logs/session.jsonl"])

    def test_rejects_parent_and_absolute_paths(self) -> None:
        for name in ("../escape.txt", "/absolute.txt", "C:/drive.txt", "safe/../../escape.txt"):
            with self.subTest(name=name):
                temporary, archive = self._archive([(name, b"bad")])
                with temporary, self.assertRaises(UnsafeArchiveError):
                    extract_diagnostic_zip(archive, Path(temporary.name) / "out")

    def test_rejects_windows_unsafe_names(self) -> None:
        for name in ("logs/a.txt:hidden", "CON", "logs/aux.txt", "a.txt.", "trail "):
            with self.subTest(name=name):
                temporary, archive = self._archive([(name, b"bad")])
                with temporary:
                    destination = Path(temporary.name) / "out"
                    with self.assertRaises(UnsafeArchiveError):
                        extract_diagnostic_zip(archive, destination)
                    self.assertFalse(destination.exists())

    def test_rejects_duplicate_paths_case_insensitively(self) -> None:
        temporary, archive = self._archive([("log.txt", b"one"), ("LOG.TXT", b"two")])
        with temporary, self.assertRaises(UnsafeArchiveError):
            extract_diagnostic_zip(archive, Path(temporary.name) / "out")

    def test_rejects_file_child_conflict_without_partial_output(self) -> None:
        temporary, archive = self._archive([("a", b"file"), ("a/b", b"child")])
        with temporary:
            destination = Path(temporary.name) / "out"
            with self.assertRaises(UnsafeArchiveError):
                extract_diagnostic_zip(archive, destination)
            self.assertFalse(destination.exists())

    def test_rejects_symlinks(self) -> None:
        link = ZipInfo("link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        temporary, archive = self._archive([(link, b"target")])
        with temporary, self.assertRaises(UnsafeArchiveError):
            extract_diagnostic_zip(archive, Path(temporary.name) / "out")

    def test_rejects_encrypted_flag(self) -> None:
        temporary, archive = self._archive([("secret.txt", b"ciphertext")])
        with temporary:
            data = bytearray(archive.read_bytes())
            for marker, offset in ((b"PK\x03\x04", 6), (b"PK\x01\x02", 8)):
                position = data.find(marker)
                flags = int.from_bytes(data[position + offset : position + offset + 2], "little") | 1
                data[position + offset : position + offset + 2] = flags.to_bytes(2, "little")
            archive.write_bytes(data)
            destination = Path(temporary.name) / "out"
            with self.assertRaises(UnsafeArchiveError):
                extract_diagnostic_zip(archive, destination)
            self.assertFalse(destination.exists())

    def test_bad_crc_is_wrapped_and_leaves_empty_destination(self) -> None:
        temporary, archive = self._archive([("corrupt.txt", b"known-payload")])
        with temporary:
            data = bytearray(archive.read_bytes())
            position = data.find(b"known-payload")
            data[position] ^= 0xFF
            archive.write_bytes(data)
            destination = Path(temporary.name) / "out"
            with self.assertRaises(UnsafeArchiveError):
                extract_diagnostic_zip(archive, destination)
            self.assertFalse(destination.exists())

    def test_rejects_too_many_entries(self) -> None:
        temporary, archive = self._archive([(f"{index}.txt", b"") for index in range(MAX_ENTRIES + 1)])
        with temporary, self.assertRaises(UnsafeArchiveError):
            extract_diagnostic_zip(archive, Path(temporary.name) / "out")

    def test_rejects_excessive_total_size(self) -> None:
        temporary, archive = self._archive([("one.bin", b"12"), ("two.bin", b"34")])
        with temporary, patch("gpsx_evidence.safe_archive.MAX_TOTAL_BYTES", 3), self.assertRaises(UnsafeArchiveError):
            extract_diagnostic_zip(archive, Path(temporary.name) / "out")

    def test_rejects_oversized_declared_entry(self) -> None:
        info = ZipInfo("huge.bin")
        info.file_size = MAX_ENTRY_BYTES + 1
        temporary, archive = self._archive([(info, b"small")])
        with temporary:
            # zipfile rewrites the declaration, so patch the central-directory size.
            data = bytearray(archive.read_bytes())
            marker = data.rfind(b"PK\x01\x02")
            data[marker + 24 : marker + 28] = (MAX_ENTRY_BYTES + 1).to_bytes(4, "little")
            archive.write_bytes(data)
            with self.assertRaises(UnsafeArchiveError):
                extract_diagnostic_zip(archive, Path(temporary.name) / "out")

    def test_rejects_nonempty_destination(self) -> None:
        temporary, archive = self._archive([("safe.txt", b"ok")])
        with temporary:
            destination = Path(temporary.name) / "out"
            destination.mkdir()
            (destination / "existing.txt").write_text("keep", encoding="utf-8")
            with self.assertRaises(UnsafeArchiveError):
                extract_diagnostic_zip(archive, destination)
