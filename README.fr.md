# GPSx

GPSx est un projet de client Remote Play fondé sur les preuves, destiné à
Windows et Android TV, avec le BenQ GP520 comme première cible physique.

## État actuel

Le projet est au jalon **M0.5 — Référence factuelle et verrou de licence**.
Les fondations portables d’état, d’entrée et de télémétrie peuvent évoluer,
mais aucun code protocolaire ne doit être copié, traduit ou implémenté avant
l’approbation de `docs/decisions/0001-protocol-license-strategy.md`.

## Prérequis

- Rust stable avec `rustfmt` et `clippy` ;
- Python 3.12+ ;
- Android Platform Tools (`adb`), installable dans `.tools/platform-tools/` ;
- une PS5, une DualSense et un GP520 pour les validations matérielles.

## Validation

```powershell
./tools/powershell/check.ps1
```

Cette commande exécute formatage, Clippy avec avertissements interdits, tests
Rust et tests Python. Les captures locales vont dans `docs/evidence/`, ignoré
par Git. Seules des fixtures relues et expurgées vont dans `tests/fixtures/`.

