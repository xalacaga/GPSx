"""Create and verify deterministic SHA-256 manifests for reviewed fixtures.

Each fixture's provenance comes from a reviewer-written `provenance.json` in the
fixture root: `{"<relative path>": {"origin": ..., "license": ..., "environment": ...,
"redaction_review": ...}}`. The manifest records it next to the digest, and
verification fails on any missing, extra, changed, or unprovenanced fixture.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

MANIFEST_NAME = "manifest.json"
PROVENANCE_NAME = "provenance.json"
PROVENANCE_FIELDS = ("origin", "license", "environment", "redaction_review")


def _is_fixture(root: Path, path: Path) -> bool:
    if not path.is_file() or path.name in {MANIFEST_NAME, PROVENANCE_NAME, ".gitkeep"}:
        return False
    # Documentation files at the fixture root are not fixtures.
    return not (path.parent == root and path.name.startswith("README") and path.suffix == ".md")


def fixture_paths(root: Path) -> list[Path]:
    root = root.resolve(strict=True)
    return sorted(path for path in root.rglob("*") if _is_fixture(root, path))


def _load_provenance(root: Path) -> dict[str, dict[str, str]]:
    path = root / PROVENANCE_NAME
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8-sig"))


def build_manifest(root: Path) -> dict[str, object]:
    root = root.resolve(strict=True)
    provenance = _load_provenance(root)
    files: list[dict[str, object]] = []
    for path in fixture_paths(root):
        relative = path.relative_to(root).as_posix()
        files.append(
            {
                "path": relative,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "bytes": path.stat().st_size,
                "provenance": provenance.get(relative),
            }
        )
    return {"schema_version": 2, "files": files}


def write_manifest(root: Path, output: Path) -> None:
    output.write_text(
        json.dumps(build_manifest(root), indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def verify_manifest(root: Path, manifest_path: Path) -> list[str]:
    """Return human-readable problems; an empty list means the fixtures are valid."""
    root = root.resolve(strict=True)
    current = {entry["path"]: entry for entry in build_manifest(root)["files"]}  # type: ignore[index]
    if not manifest_path.exists():
        return [f"{manifest_path.name} is missing but {len(current)} fixture(s) exist"] if current else []
    recorded = {entry["path"]: entry for entry in json.loads(manifest_path.read_text(encoding="utf-8-sig"))["files"]}
    problems = [f"missing fixture: {path}" for path in sorted(recorded.keys() - current.keys())]
    problems += [f"unlisted fixture: {path}" for path in sorted(current.keys() - recorded.keys())]
    for path in sorted(recorded.keys() & current.keys()):
        if recorded[path]["sha256"] != current[path]["sha256"]:
            problems.append(f"checksum mismatch: {path}")
        provenance = recorded[path].get("provenance") or {}
        missing = [field for field in PROVENANCE_FIELDS if not str(provenance.get(field, "")).strip()]
        if missing:
            problems.append(f"incomplete provenance for {path}: {', '.join(missing)}")
    return problems
