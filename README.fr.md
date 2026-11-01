# GPSx

GPSx est une application Android TV pour jouer sur grand écran depuis un seul
endroit :

- **Remote Play PS5 / PS4** sur le réseau local ;
- **streaming cloud PlayStation Plus** (abonnement Premium, sans console) ;
- **consoles rétro** — Super Nintendo, Neo Geo, arcade — avec les jeux lus sur
  votre propre NAS.

La première cible est le vidéoprojecteur BenQ GP520 (Android TV 11, ARM 32 bits).
GPSx est un client non officiel, sans lien avec Sony Interactive Entertainment,
Nintendo ou SNK ni approuvé par eux.

## État

Les versions de test sont publiées depuis la branche `gp520-test-channel` et
tournent sur le GP520 (voir [docs/INSTALL_GP520.fr.md](docs/INSTALL_GP520.fr.md)).
Points marquants :

- Le Remote Play et le streaming cloud PlayStation Plus tournent sur le GP520
  grâce au moteur natif Pylux, avec la correspondance des boutons DualSense et
  DualShock 4.
- La section Rétro lit le partage NAS du propriétaire organisé à la façon
  d'EmulationStation (jaquettes et descriptions depuis `gamelist.xml`, jaquettes
  du serveur de vignettes libretro) et fait tourner des cores libretro dans un
  hôte natif : SNES (Snes9x 2010), Neo Geo (FB Alpha 2012) et arcade
  (FinalBurn Neo), avec le son et un joueur par manette. L'Amiga (PUAE) est prévu.
- Ce qui est vérifié, et sur quel appareil, est noté fonction par fonction dans
  les plans de [docs/](docs/), étiqueté FACT, INFERENCE, HYPOTHESIS ou UNKNOWN.

## Installer sur Android TV

1. Installez **Downloader** (AFTVnews) sur la TV et autorisez-le à installer des
   applications.
2. Ouvrez `https://github.com/xalacaga/GPSx/releases/latest/download/gpsx.apk`.
3. Les versions suivantes s'installent depuis GPSx : Paramètres → *Mettre à jour
   GPSx*.

Chaque APK publié est signé avec la même clé GPSx ; la mise à jour intégrée
vérifie l'empreinte SHA-256 de l'APK, le nom du paquet et le certificat de
signature avant qu'Android l'installe.

## Utiliser GPSx

L'accueil sert à choisir la console ; chaque console a son propre environnement :

| Environnement | Onglets |
| --- | --- |
| PS5 | Jouer (Remote Play) · Cloud PS Plus · Réglages (qualité vidéo, faible latence, statistiques du stream) |
| Chaque console rétro | Jeux (liste avec jaquettes) · Commandes (rôle de chaque bouton PlayStation) |

Le menu du haut garde ce qui est commun à toutes les consoles : Manettes,
Diagnostics (journal en direct sur le réseau local, rapports de test), Paramètres
(sortie audio, accès au NAS, place occupée par les jeux rétro, mises à jour) et À
propos. L1/R1 changent d'onglet ; L1 + R1 + Options maintenus une seconde, ou
Retour sur la télécommande, quittent un jeu.

## Jeux rétro : ce que GPSx fournit et ne fournit pas

- GPSx ne fournit, ne télécharge et ne référence jamais de ROM ni de BIOS : les
  jeux viennent de votre partage (`\\serveur\partage\dossier`, en invité ou avec
  un compte du NAS ; le mot de passe est chiffré sur la TV et jamais journalisé).
- Les cores d'émulation sont téléchargés à la demande depuis le serveur de build
  officiel libretro, dans le stockage privé de l'application. Plusieurs
  interdisent l'usage commercial ; ils ne font jamais partie de ce dépôt ni de
  l'APK.
- Les jaquettes du serveur de vignettes libretro appartiennent à leurs éditeurs ;
  la TV ne les télécharge que pour afficher vos propres jeux et les garde en cache.
- Les jeux sont copiés une fois sur la TV et gardés ; Paramètres affiche la place
  occupée et efface séparément les jeux, les jaquettes et émulateurs, ou les
  sauvegardes.

## Organisation du dépôt

| Chemin | Contenu |
| --- | --- |
| `apps/android-tv/` | Application Android TV : interface Kotlin + Jetpack Compose pour TV, adaptateur natif et hôte rétro en C++ (`app/src/main/cpp/`) |
| `crates/` | Cœur Rust portable : protocole, transport, crypto, entrées, modèle, télémétrie (aucune dépendance Android ou Windows, `unsafe` interdit) |
| `platform/android/` | Adaptateur JNI Rust pour les manettes |
| `third_party/pylux/` | Patch local appliqué au moteur Pylux épinglé (AGPL) |
| `tools/` | Scripts PowerShell de build, d'émulateur et de publication ; outils Python de preuves |
| `docs/` | Plans, architecture, notes de protocole, audit des licences — chacun en anglais (`NOM.md`) et en français (`NOM.fr.md`) |

## Compiler

Prérequis : Rust stable avec `rustfmt` et `clippy`, `cargo-ndk`, Python 3.12+,
JDK 17+, SDK Android 36 avec le NDK 28.2.13676358.

```powershell
# Gates Rust et Python : formatage, Clippy (avertissements interdits), tests du workspace et des outils
./tools/powershell/check.ps1

# Moteur natif Pylux pour les quatre ABI Android (une fois, ou après un changement du patch)
./tools/powershell/build-pylux-native.ps1

# Tests unitaires Android et APK debug
./apps/android-tv/gradlew.bat -p ./apps/android-tv testDebugUnitTest assembleDebug

# Émulateur Google TV avec les passerelles GPSx, puis installation de l'APK debug
./start-gpsx-emulator.ps1
```

Les builds de publication demandent les éléments de signature hors du dépôt
(`~/.gpsx/signing.properties`) et un arbre propre et poussé ; le mainteneur publie
avec `./tools/powershell/publish-release.ps1`.

Un build réussi n'est pas une validation sur appareil : les résultats émulateur,
appareil Android et GP520 sont notés séparément.

## Preuves et secrets

Ne versionnez jamais d'identifiants, jetons, cookies, clés d'enregistrement, clés
de session ni d'URL contenant des secrets. Les captures locales vont dans
`docs/evidence/` (les fichiers générés sont ignorés par Git) ; seules des
fixtures relues et nettoyées vont dans `tests/fixtures/`.

## Licence

GPSx est sous licence GNU AGPL-3.0-only, avec l'exception de liaison OpenSSL
indiquée à la fin de [LICENSE](LICENSE). Les attributions amont et les mentions
tierces sont dans [NOTICE](NOTICE) ; l'audit des licences est dans
[docs/LICENSE_AUDIT.fr.md](docs/LICENSE_AUDIT.fr.md). Le code source de chaque
version est le commit étiqueté sur GitHub.
