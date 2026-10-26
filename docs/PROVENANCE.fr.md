# Registre de provenance

Statut : registre de conformité obligatoire. Mis à jour le 2026-10-26.

Ce registre est la source de vérité pour chaque artefact externe copié, adapté,
traduit, généré ou utilisé comme fixture protocolaire. `NOTICE` contient les
mentions distribuées avec le produit. La stratégie acceptée est l’option C de l’ADR-0001.

## Règles

- Chaque fichier Rust dérivé commence par
  `SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL`.
- Avant l’ajout d’un fichier dérivé, ce registre consigne chemin local, dépôt
  amont, commit immuable, chemin et copyright d’origine, identifiant SPDX,
  responsable/date d’adaptation et résumé des modifications.
- Un fichier protocolaire écrit par GPSx utilise exactement le champ amont
  `N/A - GPSx-authored` ; un fichier adapté ne cite que des chemins présents
  dans l’inventaire amont épinglé sous `tools/provenance/`.
- Les fixtures indiquent aussi leur droit de redistribution et ne contiennent
  aucun identifiant, compte, secret de session ni capture privée brute.
- Un changement de base amende l’ADR-0001. Une référence amont n’autorise pas la copie.
- `tools/powershell/check.ps1` analyse structurellement ce tableau. Il exige une ligne
  complète de sept champs au commit épinglé pour chaque fichier d’une crate protocole
  autorisée, refuse les crates non déclarées et exige l’en-tête SPDX dérivé sur les fichiers Rust.

## Base dérivée principale

| Champ | Valeur |
|---|---|
| Projet | chiaki-ng |
| Dépôt | `https://github.com/chiaki-ng/chiaki-ng` |
| Commit immuable | `c8bea2642d0e95402f373af55584a3b98cd89417` |
| Licence | `LicenseRef-AGPL-3.0-only-OpenSSL` |
| Source de licence | `COPYING` au commit épinglé |
| Usage actuel | Ports comportementaux Rust des lots bornés de découverte SRCH et de cryptographie limitée à l’enregistrement listés ci-dessous |
| Approbateur/date | Responsable du projet, 2026-10-25 |

## Projets de référence uniquement

| Projet | Usage | Statut de copie |
|---|---|---|
| Chiaki | Architecture historique et recherche de régressions | Aucun source copié |
| Pylux | Cycle de vie Android et comportement produit | Aucun source copié |
| `hid-playstation.c` Linux | Référence DualSense sous GPL-2.0-or-later | Aucun source copié |
| Documentation Android/AOSP | Comportement des API de plateforme | Aucun source copié |

## Entrées de fichiers dérivés

| Chemin local | Chemin amont | Commit | Copyright d’origine | SPDX | Responsable/date | Modifications |
|---|---|---|---|---|---|---|
| `crates/gpsx-protocol/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `AGPL-3.0-only` | GPSx / 2026-10-25 | Manifeste sans dépendance ; aucun script de build |
| `crates/gpsx-protocol/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Surface publique limitée au formatage et au parsing SRCH purs |
| `crates/gpsx-protocol/src/discovery.rs` | `lib/include/chiaki/discovery.h`; `lib/src/discovery.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Port comportemental Rust du format SRCH et des réponses ; parsing strict et borné, affichage expurgé, sans E/S ni WAKEUP |
| `crates/gpsx-protocol/src/http.rs` | `lib/include/chiaki/http.h`; `lib/src/http.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Parseur de réponse HTTP/1.1 borné avec noms ASCII, valeurs UTF-8 contrôlées, erreurs classées et tests de propriété déterministes |
| `crates/gpsx-transport/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `AGPL-3.0-only` | GPSx / 2026-10-26 | Manifeste de l’adaptateur de transport de plateforme ; dépend uniquement de gpsx-protocol |
| `crates/gpsx-transport/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Adaptateur IPv4 UDP borné écrit par GPSx autour de l’API SRCH pure relue ; testé en boucle locale, sans implémentation de transport copiée |
| `crates/gpsx-transport/src/bin/gpsx-discover.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Sonde française de découverte Windows à activation explicite ; résumés expurgés uniquement, sans réveil, identifiants ni établissement de session |
| `crates/gpsx-crypto/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `AGPL-3.0-only` | GPSx / 2026-10-26 | Dépendances primitives exactes et relues pour le seul enregistrement ; aucune E/S, dépendance de plateforme ni script de build |
| `crates/gpsx-crypto/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Surface publique minimale et expurgée pour la seule cryptographie d’enregistrement |
| `crates/gpsx-crypto/src/registration.rs` | `lib/include/chiaki/rpcrypt.h`; `lib/src/rpcrypt.c`; `test/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Port comportemental Rust des dérivations d’enregistrement PS4/PS5, aeropause, IV à compteur fixe et AES-CFB ; offsets typés séparément, propriétaires expurgés et effacés, sans session ni E/S |

## Entrées de fichiers prévues pour CR3

Les entrées cryptographiques réservées à l’enregistrement et autorisées le
2026-10-26 figurent désormais dans le tableau des fichiers dérivés. Aucun autre
fichier CR3 n’est prévu dans ce lot.

## Entrées de fixtures relues

| Fixture locale | Origine | Commit ou résolution | Licence | Génération | Environnement/revue | Redistribution |
|---|---|---|---|---|---|---|
| `tests/fixtures/registration_crypto_oracle_v2.json` | `lib/include/chiaki/rpcrypt.h`; `lib/src/rpcrypt.c`; `test/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Compilation du `rpcrypt.c` épinglé avec Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0`, puis exécution d’un harnais scratch sur des entrées synthétiques publiques pour PS4 avant 10, ainsi que PS4 10+ et PS5 avec les paires indépendantes d’offsets chiffrement/aeropause `(0,31)`, `(31,0)` et `(7,22)`. La branche Mbed TLS épinglée s’est arrêtée car son tableau résultat de 16 octets recevait un HMAC SHA-256 de 32 octets ; la génération a utilisé la correction scratch d’une ligne consignée `hmac[16]` → `hmac[32]`, tout en conservant la troncature amont à 16 octets. | Windows x64, MSVC 19.44, revue manuelle et scan de secrets le 2026-10-26 | Autorisée par l’option C du projet ; aucune capture privée ni valeur réelle |
| `tests/fixtures/registration_crypto_dependency_audit_v1.json` | Résolution des dépendances et nouvel audit du dépôt écrits par GPSx | SHA-256 du lock scratch `d312587e656ced976d4eb20c24178a4101640f548049328b5d8ce063c161d72f` | `AGPL-3.0-only` | Versions/fonctionnalités exactes résolues avec Cargo 1.85 ; contrôlées sous Windows et `aarch64-linux-android` ; lock du dépôt réaudité structurellement par nom, version et somme crates.io avec cargo-audit 0.22.2 et cargo-deny 0.20.2 enraciné dans la crypto. | Base d’avis `a78282a1414d6c28cc228c4eae3570451affb0dc` ; revue manuelle et scan de secrets le 2026-10-26 | Autorisée ; métadonnées factuelles uniquement |
