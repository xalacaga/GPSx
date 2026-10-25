# Preuves locales

Ce dossier contient les captures locales, ignorées par Git. Ne jamais y placer
identifiants, jetons, cookies, clés, numéros de série ou URL secrètes.

```powershell
$env:PYTHONPATH = 'tools/python'
python -m gpsx_evidence.cli redact-jsonl RAW.jsonl SANITIZED.jsonl
```

L’outil refuse d’écraser la destination. Relire manuellement avant toute
promotion dans `tests/fixtures/`.

