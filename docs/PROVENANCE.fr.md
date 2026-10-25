# Registre de provenance

Statut : registre de conformité obligatoire. Mis à jour le 2026-10-25.

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
| Usage actuel | Port comportemental Rust du lot de découverte SRCH borné listé ci-dessous |
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

## Entrées de fichiers prévues pour CR3 (non implémentées)

Le GO CR3 du 2026-10-25 a autorisé exactement les quatre lignes désormais
consignées ci-dessus. Aucun autre fichier protocolaire n’est prévu ni autorisé
dans ce lot.

| Chemin local prévu | Chemin(s) amont exact(s) | Commit | Copyright d’origine | SPDX prévu | Responsable/date | Travail prévu |
|---|---|---|---|---|---|---|
