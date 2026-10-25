# GPSx

GPSx est un projet de client Remote Play fondé sur les preuves, destiné à
Windows et Android TV, avec le BenQ GP520 comme première cible physique.

## État actuel

L’option C est acceptée : GPSx est sous AGPL-3.0-only et le futur port Rust du
protocole est un dérivé déclaré avec provenance fichier par fichier. Les
fondations portables d’état, d’entrée et de télémétrie sont testées. Une interface
Android TV hors ligne fournit maintenant navigation D-pad, cycle de session
simulée explicite, deux emplacements DualSense, diagnostics et réglages. Aucune
connexion console ou GP520 n’est encore implémentée.

## Prérequis

- Rust stable avec `rustfmt` et `clippy` ;
- Python 3.12+ ;
- JDK 17+ et SDK Android 36 pour le build Android TV ;
- Android Platform Tools (`adb`) seulement après ouverture du gate matériel ;
- une PS4/PS5, deux DualSense et un GP520 pour les validations matérielles futures.

## Validation

```powershell
./tools/powershell/check.ps1
```

Cette commande exécute formatage, Clippy avec avertissements interdits, tests
Rust et tests Python. Les captures locales vont dans `docs/evidence/`, ignoré
par Git. Seules des fixtures relues et expurgées vont dans `tests/fixtures/`.

Construire et tester le prototype Android TV hors ligne :

```powershell
./apps/android-tv/gradlew.bat -p ./apps/android-tv testDebugUnitTest assembleDebug
```

L’APK debug est produit dans
`apps/android-tv/app/build/outputs/apk/debug/app-debug.apk`. Sa compilation ne
constitue pas une validation émulateur, appareil Android ou GP520.

