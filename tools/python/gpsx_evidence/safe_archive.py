"""Strict ZIP extraction for diagnostic bundles.

Archives are data only. Extraction rejects traversal, links, duplicate names,
encrypted entries, unsupported Unix file types, and declared or streamed size
limits before any later manifest and secret checks run.
"""

from __future__ import annotations

import os
import stat
import tempfile
from pathlib import Path, PurePosixPath
from zipfile import BadZipFile, ZipFile, ZipInfo

MAX_ENTRIES = 64
MAX_ENTRY_BYTES = 16 * 1024 * 1024
MAX_TOTAL_BYTES = 64 * 1024 * 1024
WINDOWS_RESERVED_NAMES = frozenset(
    {"CON", "PRN", "AUX", "NUL"}
    | {f"COM{index}" for index in range(1, 10)}
    | {f"LPT{index}" for index in range(1, 10)}
)


class UnsafeArchiveError(ValueError):
    """The bundle is unsafe or outside GPSx diagnostic limits."""


def _safe_relative_path(info: ZipInfo) -> Path:
    normalized = info.filename.replace("\\", "/")
    path = PurePosixPath(normalized)
    if not normalized or path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise UnsafeArchiveError(f"unsafe archive path: {info.filename!r}")
    for part in path.parts:
        if ":" in part:
            raise UnsafeArchiveError(f"colon or alternate data stream in archive path: {info.filename!r}")
        if part.endswith((".", " ")):
            raise UnsafeArchiveError(f"Windows-trimmed archive path: {info.filename!r}")
        device_name = part.split(".", 1)[0].upper()
        if device_name in WINDOWS_RESERVED_NAMES:
            raise UnsafeArchiveError(f"Windows device name in archive path: {info.filename!r}")
    return Path(*path.parts)


def _validate_type(info: ZipInfo) -> None:
    if info.flag_bits & 0x1:
        raise UnsafeArchiveError(f"encrypted entry is unsupported: {info.filename!r}")
    unix_mode = info.external_attr >> 16
    file_type = stat.S_IFMT(unix_mode)
    if file_type not in (0, stat.S_IFREG, stat.S_IFDIR):
        raise UnsafeArchiveError(f"link or special file is forbidden: {info.filename!r}")


def _extract_to_staging(archive: Path, staging: Path) -> list[Path]:
    root = staging.resolve()
    extracted: list[Path] = []
    with ZipFile(archive) as bundle:
        entries = bundle.infolist()
        if len(entries) > MAX_ENTRIES:
            raise UnsafeArchiveError(f"archive has more than {MAX_ENTRIES} entries")
        seen: set[str] = set()
        declared_total = 0
        checked: list[tuple[ZipInfo, Path, str]] = []
        kinds: dict[str, bool] = {}
        for info in entries:
            relative = _safe_relative_path(info)
            _validate_type(info)
            canonical = relative.as_posix().casefold().rstrip("/")
            if canonical in seen:
                raise UnsafeArchiveError(f"duplicate archive path: {info.filename!r}")
            seen.add(canonical)
            kinds[canonical] = info.is_dir()
            if info.file_size > MAX_ENTRY_BYTES:
                raise UnsafeArchiveError(f"entry exceeds {MAX_ENTRY_BYTES} bytes: {info.filename!r}")
            declared_total += info.file_size
            if declared_total > MAX_TOTAL_BYTES:
                raise UnsafeArchiveError(f"archive exceeds {MAX_TOTAL_BYTES} bytes")
            target = (root / relative).resolve()
            if root != target and root not in target.parents:
                raise UnsafeArchiveError(f"archive path escapes destination: {info.filename!r}")
            checked.append((info, target, canonical))

        for path, is_directory in kinds.items():
            if not is_directory and any(other.startswith(path + "/") for other in kinds if other != path):
                raise UnsafeArchiveError(f"file conflicts with a child path: {path!r}")

        streamed_total = 0
        for info, target, _ in checked:
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            written = 0
            with bundle.open(info) as source, target.open("xb") as output:
                while chunk := source.read(64 * 1024):
                    written += len(chunk)
                    streamed_total += len(chunk)
                    if written > MAX_ENTRY_BYTES or streamed_total > MAX_TOTAL_BYTES:
                        raise UnsafeArchiveError("streamed archive data exceeds declared safety limits")
                    output.write(chunk)
            extracted.append(target)
    return extracted


def extract_diagnostic_zip(archive: Path, destination: Path) -> list[Path]:
    """Atomically extract an untrusted bundle into a new, empty directory."""

    destination_existed = destination.exists()
    try:
        if destination_existed and (not destination.is_dir() or any(destination.iterdir())):
            raise UnsafeArchiveError("destination must be new or empty")
        destination.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=".gpsx-extract-", dir=destination.parent) as temporary:
            staging = Path(temporary)
            staged_files = _extract_to_staging(archive, staging)
            relative_files = [path.relative_to(staging) for path in staged_files]
            if destination_existed:
                destination.rmdir()
            os.replace(staging, destination)
            return [destination / path for path in relative_files]
    except UnsafeArchiveError:
        raise
    except (BadZipFile, OSError, RuntimeError) as error:
        if destination_existed and not destination.exists():
            destination.mkdir(parents=True, exist_ok=True)
        raise UnsafeArchiveError(f"archive extraction failed: {error}") from error
