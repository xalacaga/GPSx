# Local evidence

This directory holds local baseline captures. Capture files are intentionally
ignored by Git. Never place credentials, tokens, cookies, registration keys,
session keys, account identifiers, device serials, or secret-bearing URLs here.

Sanitize a JSONL capture into a **new** destination file:

```powershell
$env:PYTHONPATH = 'tools/python'
python -m gpsx_evidence.cli redact-jsonl RAW.jsonl SANITIZED.jsonl
```

The tool refuses to overwrite the destination. Review sanitized output manually
before moving any subset into `tests/fixtures/`.

