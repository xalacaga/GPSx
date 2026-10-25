# M0.5 baseline runbook

## 1. Record provenance

Record repository URL, immutable commit SHA, build instructions, local patches,
binary hashes, and applicable notices for the selected upstream client. Do not
vendor its source into GPSx while ADR-0001 is pending.

## 2. Capture the Windows environment

```powershell
./tools/powershell/collect-windows-baseline.ps1 -OutFile docs/evidence/windows-environment.json
```

## 3. Exercise a known-good client

Run discovery, registration, connect, ten minutes of streaming, clean stop, and
reconnect. Record state timestamps and aggregate metrics. Never capture secret
payloads.

## 4. Inspect the GP520

**Deferred hardware gate:** do not execute this section until the user explicitly
authorizes connecting to the GP520. Its outputs remain UNKNOWN meanwhile.

Enable developer access and verify the computer fingerprint on the projector,
then run:

```powershell
./tools/powershell/collect-gp520-baseline.ps1 -OutFile docs/evidence/gp520-environment.json
```

`-OutFile` writes UTF-8 without a BOM. Do not pipe into `Set-Content -Encoding utf8`:
Windows PowerShell 5.1 adds a BOM that strict JSON readers reject.

Review the result for identifiers before retention. `dumpsys` formats vary by
firmware, so absence of a field means UNKNOWN rather than unsupported.

## 5. Validate the DualSense matrix

Test one control at a time over USB and Bluetooth. Preserve raw event names,
axis identifiers, ranges, dead zones, connection transitions, OS build, and
controller firmware. Update `DUALSENSE_MAPPING.md` only from reviewed evidence.

## 6. Promote fixtures

Redact candidate JSONL, review it manually, copy only the minimal safe subset to
`tests/fixtures/`, record provenance/license metadata, then create a checksum
manifest:

```powershell
$env:PYTHONPATH = 'tools/python'
python -m gpsx_evidence.cli manifest tests/fixtures tests/fixtures/manifest.json
```

