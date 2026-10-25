"""Command-line entry points for evidence sanitization, scanning, and manifests."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .manifest import fixture_paths, verify_manifest, write_manifest
from .redaction import redact_json_value, redact_text


def _load_allowed_keys(path: Path | None) -> frozenset[str] | None:
    if path is None:
        return None
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    return frozenset(line.strip() for line in lines if line.strip() and not line.startswith("#"))


def _redact_jsonl(source: Path, destination: Path, allowed_keys: frozenset[str] | None) -> None:
    # utf-8-sig accepts files written with a BOM by Windows PowerShell 5.1.
    with source.open("r", encoding="utf-8-sig") as input_file, destination.open("x", encoding="utf-8") as output_file:
        for line_number, line in enumerate(input_file, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
                output_file.write(json.dumps(redact_json_value(value, allowed_keys=allowed_keys), sort_keys=True) + "\n")
            except json.JSONDecodeError:
                output_file.write(
                    json.dumps({"line": line_number, "text": redact_text(line.rstrip("\n"))}, sort_keys=True) + "\n"
                )


def scan_for_secrets(paths: list[Path]) -> list[str]:
    """Report every line that redaction would change: such a file still holds a secret."""
    findings: list[str] = []
    files: list[Path] = []
    for path in paths:
        files.extend(fixture_paths(path) if path.is_dir() else [path])
    for file in files:
        try:
            text = file.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            findings.append(f"{file}: binary file cannot be scanned; store a reviewed text form instead")
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            try:
                value = json.loads(line)
                changed = redact_json_value(value) != value
            except json.JSONDecodeError:
                changed = redact_text(line) != line
            if changed:
                findings.append(f"{file}:{number}: possible secret")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(prog="gpsx-evidence")
    commands = parser.add_subparsers(dest="command", required=True)

    redact = commands.add_parser("redact-jsonl", help="redact a capture into a new JSONL file")
    redact.add_argument("source", type=Path)
    redact.add_argument("destination", type=Path)
    redact.add_argument("--allow-keys", type=Path, help="file of keys to keep; every other key is redacted")

    manifest = commands.add_parser("manifest", help="create a fixture checksum and provenance manifest")
    manifest.add_argument("root", type=Path)
    manifest.add_argument("output", type=Path)

    verify = commands.add_parser("manifest-verify", help="fail on missing, changed, or unprovenanced fixtures")
    verify.add_argument("root", type=Path)
    verify.add_argument("manifest", type=Path)

    scan = commands.add_parser("scan", help="fail if any file still contains a redactable secret")
    scan.add_argument("paths", type=Path, nargs="+")

    args = parser.parse_args()
    if args.command == "redact-jsonl":
        _redact_jsonl(args.source, args.destination, _load_allowed_keys(args.allow_keys))
        return 0
    if args.command == "manifest":
        write_manifest(args.root, args.output)
        return 0
    problems = verify_manifest(args.root, args.manifest) if args.command == "manifest-verify" else scan_for_secrets(args.paths)
    for problem in problems:
        print(problem, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
