# Registre de provenance

Statut : registre de conformité obligatoire. Mis à jour le 2026-10-28.

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
| `crates/gpsx-protocol/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Surface publique de formatage/parsing purs et bornés pour SRCH et l’enregistrement local ; sans E/S ni cryptographie |
| `crates/gpsx-protocol/src/discovery.rs` | `lib/include/chiaki/discovery.h`; `lib/src/discovery.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Port comportemental Rust du format SRCH et des réponses ; parsing strict et borné, affichage expurgé, sans E/S ni WAKEUP |
| `crates/gpsx-protocol/src/http.rs` | `lib/include/chiaki/http.h`; `lib/src/http.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Parseur de réponse HTTP/1.1 borné avec noms ASCII, valeurs UTF-8 contrôlées, erreurs classées et tests de propriété déterministes |
| `crates/gpsx-protocol/src/registration.rs` | `lib/include/chiaki/regist.h`; `lib/src/regist.c`; `lib/src/session.c`; `test/regist.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Port comportemental Rust des constantes/dispositions relues de requête d’enregistrement local et du mapping des champs de réponse ; base64 RFC 4648 et cadrage strict borné écrits par GPSx, identités/secrets empruntés, tampons fixes appelants, sans E/S, transport, crypto, PSN distant, RUDP ou perforation NAT |
| `crates/gpsx-transport/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `AGPL-3.0-only` | GPSx / 2026-10-26 | Manifeste de l’adaptateur de transport de plateforme ; dépend uniquement de gpsx-protocol |
| `crates/gpsx-transport/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Adaptateur IPv4 UDP borné écrit par GPSx autour de l’API SRCH pure relue ; testé en boucle locale, sans implémentation de transport copiée |
| `crates/gpsx-transport/src/bin/gpsx-discover.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Sonde française de découverte Windows à activation explicite ; résumés expurgés uniquement, sans réveil, identifiants ni établissement de session |
| `crates/gpsx-crypto/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `AGPL-3.0-only` | GPSx / 2026-10-28 | Dépendances exactes pour l’enregistrement et la revue d’implémentation de session ; aucune E/S ni dépendance de plateforme et aucun script de build du paquet ; le build C transitif de `secp256k1-sys` est explicite |
| `crates/gpsx-crypto/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-28 | Surface publique expurgée pour la cryptographie d’enregistrement, de session et la frontière LaunchSpec chiffrée typée |
| `crates/gpsx-crypto/src/registration.rs` | `lib/include/chiaki/rpcrypt.h`; `lib/src/rpcrypt.c`; `test/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Port comportemental Rust des dérivations d’enregistrement PS4/PS5, aeropause, IV à compteur fixe et AES-CFB ; offsets typés séparément, propriétaires expurgés et effacés, sans session ni E/S |
| `crates/gpsx-crypto/src/session.rs` | `lib/include/chiaki/ecdh.h`; `lib/src/ecdh.c`; `lib/include/chiaki/gkcrypt.h`; `lib/src/gkcrypt.c`; `lib/include/chiaki/seqnum.h`; `lib/src/streamconnection.c`; `lib/src/takion.c`; `lib/src/launchspec.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-28 | Implémentation de session relue indépendamment plus séquencement LaunchSpec intégré : sortie brute de clé supprimée, sortie LaunchSpec chiffrée réussie exigée avant `finish`, formatage conservé dans `launch_spec.rs` ; aucune E/S, thread, cache ou parseur |
| `crates/gpsx-crypto/src/launch_spec.rs` | `lib/include/chiaki/common.h` ; `lib/include/chiaki/launchspec.h` ; `lib/src/launchspec.c` ; `lib/include/chiaki/rpcrypt.h` ; `lib/src/rpcrypt.c` ; `lib/src/session.c` ; `lib/src/streamconnection.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; contributeurs GPSx | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-28 | Implémentation intégrée définissant le port octet-exact `ECHO_A` : formatage canonique borné, base64 RFC 4648, cibles/codecs/champs numériques typés, propriétaires expurgés et effacés, flot CFB sur zéros compteur zéro à usage unique, XOR JSON-plus-NUL, base64 du seul chiffré et liaison exacte à l’oracle externe ; aucun ctrl, E/S, plateforme, thread, parseur ou code matériel |
| `crates/gpsx-crypto/src/rpcrypt_tables.rs` | `lib/src/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | Contributeurs chiaki-ng ; historique Git épinglé | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-28 | Tables rpcrypt de session `keys_a_ps4`, `keys_a_ps5`, `keys_b_ps4` et `keys_b_ps5` octet-exactes ; réutilise `echo_b` et les clés HMAC déjà portées dans `registration.rs` ; aucune E/S ni comportement de plateforme |

## Entrées de fichiers prévues pour CR3

L’implémentation du codec et ses quatre chemins amont ont été autorisés et
figurent désormais ci-dessus ainsi que dans l’inventaire. R5 a autorisé la
génération externe de l’oracle ; la revue d’intégration a rendu
`GO WITH CHANGES`, puis la remédiation B1-B4 a passé la revue indépendante
exigée le 2026-10-28 et l’intégration est `GO`.

| Artefact local proposé | Chemins amont ou standard proposés | Commit / licence | Périmètre prévu | Exclusions explicites | Gate |
|---|---|---|---|---|---|
| `tests/fixtures/registration_codec_oracle_v1.json` et son entrée dans `tests/fixtures/manifest.json` | Les mêmes quatre chemins épinglés plus `lib/include/chiaki/rpcrypt.h`, `lib/src/rpcrypt.c` et `test/rpcrypt.c` déjà approuvés ; `lib/src/base64.c` uniquement comme entrée de construction de l’oracle | mêmes commit et licence épinglés | Sortie d’oracle C scratch pour quatre profils de cible RP, les deux variantes d’identité PS4_9, cas modernes remplissage/offsets, en-têtes exacts et champs intermédiaires liés séparément ; entrées publiques synthétiques uniquement | Aucune capture de réponse, compte réel, clé réelle, donnée LAN ou émulateur, revendication matérielle ou adaptation de `base64.c` | Génération R5 et intégration `GO` ; B1-B4 vérifiés indépendamment le 2026-10-28. Le harnais, le stub, le patch, les journaux et le manifeste de construction fermé restent externes selon l’option b |
| Revue d’implémentation de `crates/gpsx-crypto/src/session.rs` | même inventaire de session | mêmes commit et licence épinglés | Périmètre implémenté décrit dans le registre principal ci-dessus, avec `secp256k1` 0.33.1 audité après rejet de la résolution `k256` vulnérable | Mêmes exclusions ; le FFI de dépendance reste confiné dans `secp256k1-sys` | Plan `GO`, acceptation du tag court, `GO` de remédiation d’implémentation et `GO` final d’intégration de l’oracle consignés le 2026-10-28 ; intégré |
| `tests/fixtures/session_crypto_oracle_v1.json` intégré | `test/gkcrypt.c`; `test/keystate.c`; chemins de session ci-dessus ; NIST SP 800-38D ; arithmétique secp256k1 pure indépendante | mêmes commit/licence ; standard NIST public ; oracle indépendant écrit par GPSx | Vecteurs synthétiques couvrant les sept cas gkcrypt amont, tous les cas keystate, deux directions, frontières époque/position/report/débordement, égalité tag complet/MSB32 et liaison canonique exacte des octets | Aucun identifiant, clé, capture, LAN, émulateur ou matériel réel ; aucun harnais copié, trace privée, revendication de performance ou d’interopérabilité | Relevé externe fermé, ProvenanceGuard, gates complets, liaison canonique complète de la fixture et contre-revue C1 tous `GO` ; intégré le 2026-10-28 |
| `crates/gpsx-crypto/src/launch_spec.rs`, `rpcrypt_tables.rs` et `tests/fixtures/launch_spec_oracle_v1.json` intégrés | `lib/include/chiaki/common.h` limité aux enums/prédicats cible et codec ; `lib/include/chiaki/launchspec.h` ; `lib/src/launchspec.c` ; `lib/include/chiaki/rpcrypt.h` ; `lib/src/rpcrypt.c` limité à `bright_ambassador_ps4_pre10`, `bright_ambassador`, `chiaki_rpcrypt_bright_ambassador`, `chiaki_rpcrypt_init_auth`, `rpcrypt_hmac_key`, `chiaki_rpcrypt_generate_iv`, `chiaki_rpcrypt_crypt`, `chiaki_rpcrypt_encrypt` et aux tables requises `echo_a`, `keys_a_ps4`, `keys_a_ps5`, `keys_b_ps4`, `keys_b_ps5`, en réutilisant les tables `echo_b` et clés HMAC déjà portées ; initialisation cible/nonce/`morning` de `lib/src/session.c` ; ordre tampon nul/NUL/flot CFB/XOR/base64 de `lib/src/streamconnection.c` ; RFC 4648 | mêmes commit/licence épinglés ; RFC 4648 | Sortie publique de clé brute supprimée ; formatage LaunchSpec canonique borné, flot CFB-sur-zéros compteur zéro à usage unique de rpcrypt de session, XOR du JSON-plus-NUL, puis base64 du seul texte chiffré ; oracle fermé synthétique et liaison exacte | Aucune implémentation ctrl, réseau, protobuf, Takion, log, télémétrie, stockage, thread, média, plateforme, secret/capture réel, revendication console ou code base64/formateur copié | Remédiation du plan et contre-revue de l’implémentation indépendantes `GO` le 2026-10-28 ; relevé fermé, liaison exacte de la fixture et tous les gates locaux vérifiés ; intégré le 2026-10-28 |

Le relevé de génération de l’oracle est un manifeste de construction fermé, pas un résumé narratif. Le checkout de génération, le harnais et les stubs écrits par GPSx restent dans un répertoire scratch temporaire hors dépôt ; seuls leurs hashes sont conservés. Le relevé capture `git rev-parse HEAD` et `git status --porcelain` depuis ce checkout externe. Pour chaque fichier amont modifié, il consigne le diff exact, le SHA-256 et la justification ; toute unité de traduction différente du commit épinglé est marquée `patched` dans la liste d’entrées. `/showIncludes` et la commande de lien exacte ont produit une liste fermée de 76 unités de traduction Mbed TLS et 5 unités oracle, 313 en-têtes observés et 12 bibliothèques explicitement liées, chacun avec libellé stable, rôle, nombre d’octets et SHA-256. `regist.c` et `rpcrypt.c` sont marqués modifiés ; `session_version.c` est marqué comme le stub exactement extrait de `session.c:58-73` au commit épinglé. Le patch octet-exact est sans BOM et passe `git apply --check -R`. Le relevé identifie Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0`, MSVC `19.44.35229.0`, les options complètes, la commande de lien et les hashes du harnais/de la sortie. `lib/src/base64.c` est compilé uniquement pour l’oracle C indépendant ; le base64 de production est écrit par GPSx depuis la RFC 4648. ProvenanceGuard valide la politique des sources et du registre dans le dépôt mais pas ce manifeste de construction externe ; la revue indépendante exigée a vérifié B1-B4 le 2026-10-28.

Les preuves de correspondance firmware/cible de `gui/src/qmlbackend.cpp` et `android/app/src/main/java/com/metallic/chiaki/regist/RegistActivity.kt` restent de simples références. Aucun des deux chemins n’est une source portée ou une entrée proposée de l’inventaire amont.

Pour les lignes de session proposées, `ecdh.c` ne contribue que le HMAC de clé
publique et le contrat ECDH ; `gkcrypt.c`, seulement dérivations, CTR, GMAC et
reconstruction de position ; `streamconnection.c`, seulement directions,
séquence de handshake et `key_pos + 16` ; `takion.c`, seulement allocation et
authentification de position ; `launchspec.c` apporte le contrat du champ de clé
de handshake désormais implémenté derrière la frontière LaunchSpec chiffrée.
Le formatage JSON/base64 et la construction rpcrypt de session sont relus
séparément dans l’inventaire LaunchSpec intégré ci-dessus. `seqnum.h` ne
contribue que le contrat de comparaison
32 bits RFC 1982, dont le comportement à la distance exacte 2^31. Toutes leurs
autres fonctions restent exclues.

La construction de l’oracle de session utilise des checkouts chiaki-ng et
Mbed TLS propres et non modifiés. Six tests `gkcrypt.c` épinglés compatibles et
les deux tests `keystate.c` s’exécutent directement. Le setter de clé locale
Mbed TLS de chiaki épinglé régénère le scalaire fourni et ne peut donc pas
reproduire le vecteur ECDH amont déterministe ; le relevé fermé conserve ce
chemin comme diagnostic d’échec attendu. La fixture reproduit à la place le
même vecteur ECDH amont par une arithmétique affine secp256k1 écrite par GPSx
uniquement dans le scratchpad et le HMAC-SHA-256 de la bibliothèque standard
Python. Aucune sortie ECDH amont réparée n’entre dans la fixture. Le relevé
externe intégré contient 81 unités de traduction
compilées, 313 en-têtes observés, les commandes exactes et leurs hashes ; il
reste ignoré hors dépôt.

## Entrées de fixtures relues

| Fixture locale | Origine | Commit ou résolution | Licence | Génération | Environnement/revue | Redistribution |
|---|---|---|---|---|---|---|
| `tests/fixtures/launch_spec_oracle_v1.json` | Exécution de `lib/src/launchspec.c`, `lib/src/rpcrypt.c` et `lib/src/base64.c` ; ordre du harnais GPSx transcrit de `lib/src/streamconnection.c:1027-1036`, sans exécuter ce fichier ; en-têtes publics pertinents | chiaki-ng `c8bea2642d0e95402f373af55584a3b98cd89417` ; Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Six cas synthétiques publics lient des valeurs distinctes par champ pour chaque cible, des sélecteurs de lignes modernes différents, un cas permuté `u32::MAX`/zéro, les frontières maximales, JSON/NUL, IV, flot CFB sur zéros, chiffré XOR et base64. Sortie : 55 602 octets, SHA-256 `84487e526ac8625dcb555c447916e167cffb39eb4782ee31ca78d467ca740599` ; Rust reconstruit le document canonique et compare chaque cas au chemin public d’écriture. | Windows x64, MSVC 19.44 et Mbed TLS épinglé ; une correction scratch documentée du buffer de sortie SHA-256 ; manifeste externe avec 80 unités de traduction, 311 en-têtes observés, commande exacte de reconstruction propre et sortie brute concordante ; contre-revue indépendante de l’implémentation `GO` le 2026-10-28 | Autorisée par l’option C comme fixture intégrée ; harnais, journaux, checkouts et manifeste fermé restent externes et ignorés |
| `tests/fixtures/registration_codec_oracle_v1.json` | `lib/src/regist.c`; `lib/src/rpcrypt.c`; `lib/src/base64.c`; extraction exacte de `chiaki_rp_version_string` depuis `lib/src/session.c:58-73` | chiaki-ng `c8bea2642d0e95402f373af55584a3b98cd89417` ; Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Cinq cas à remplissage par défaut comparés octet par octet avec le formateur amont épinglé ; quatre cas à remplissage personnalisé assemblés depuis les primitives amont épinglées d’initialisation, d’aeropause et de chiffrement. Sortie : 28 469 octets, SHA-256 `13b3d768d861888d52d4aa9d86cb1ba2a09de701f85288534706734727064dd4`, CRLF préservé par `-text`. | Windows x64, MSVC `19.44.35229.0` ; revue des entrées uniquement synthétiques et scan de secrets le 2026-10-28 ; le manifeste de construction fermé reste externe | Autorisée par l’option C du projet ; fixture uniquement. Le harnais, le stub, le patch, les journaux et le manifeste de construction fermé sont interdits dans le dépôt par l’option b de R5 |
| `tests/fixtures/registration_crypto_oracle_v2.json` | `lib/include/chiaki/rpcrypt.h`; `lib/src/rpcrypt.c`; `test/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Compilation du `rpcrypt.c` épinglé avec Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0`, puis exécution d’un harnais scratch sur des entrées synthétiques publiques pour PS4 avant 10, ainsi que PS4 10+ et PS5 avec les paires indépendantes d’offsets chiffrement/aeropause `(0,31)`, `(31,0)` et `(7,22)`. La branche Mbed TLS épinglée s’est arrêtée car son tableau résultat de 16 octets recevait un HMAC SHA-256 de 32 octets ; la génération a utilisé la correction scratch d’une ligne consignée `hmac[16]` → `hmac[32]`, tout en conservant la troncature amont à 16 octets. | Windows x64, MSVC 19.44, revue manuelle et scan de secrets le 2026-10-26 | Autorisée par l’option C du projet ; aucune capture privée ni valeur réelle |
| `tests/fixtures/registration_crypto_dependency_audit_v1.json` | Résolution des dépendances et nouvel audit du dépôt écrits par GPSx | SHA-256 du lock scratch `d312587e656ced976d4eb20c24178a4101640f548049328b5d8ce063c161d72f` | `AGPL-3.0-only` | Versions/fonctionnalités exactes résolues avec Cargo 1.85 ; contrôlées sous Windows et `aarch64-linux-android` ; lock du dépôt réaudité structurellement par nom, version et somme crates.io avec cargo-audit 0.22.2 et cargo-deny 0.20.2 enraciné dans la crypto. | Base d’avis `a78282a1414d6c28cc228c4eae3570451affb0dc` ; revue manuelle et scan de secrets le 2026-10-26 | Autorisée ; métadonnées factuelles uniquement |
| `tests/fixtures/session_crypto_dependency_audit_v1.json` | Résolution et audit des dépendances de session écrits par GPSx | `Cargo.lock` du dépôt ; fixture de 5 254 octets, SHA-256 `9463dc19261c193c5df324110e92494cc3364cbd74bceb5555b01736ac5f96e6` | `AGPL-3.0-only` | Graphe exact de 30 paquets crates.io pour `gpsx-crypto`, fonctionnalités directes, checksums de registre, NDK 28.2.13676358, compilateur Android API 24 et résultat des audits ; consigne aussi le rejet de `k256`/`crypto-bigint` vulnérable et la licence MIT du code C libsecp256k1 vendu et patché. | Rust/Cargo 1.85.0 sous Windows et cross-compilation `aarch64-linux-android` ; cargo-audit 0.22.2, cargo-deny 0.20.2 ; revue manuelle et scan de secrets le 2026-10-28 | Autorisée ; métadonnées factuelles uniquement, sans secret ni donnée matérielle |
| `tests/fixtures/session_crypto_oracle_v1.json` | `test/gkcrypt.c` ; `test/keystate.c` ; `lib/src/gkcrypt.c` ; `lib/src/ecdh.c` ; NIST SP 800-38D ; arithmétique secp256k1 indépendante écrite par GPSx | chiaki-ng `c8bea2642d0e95402f373af55584a3b98cd89417` ; Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Six tests gkcrypt amont compatibles et les deux tests keystate exécutés directement ; vecteur ECDH épinglé reproduit indépendamment ; deux directions et neuf positions lient dérivation, CTR, tag AES-GCM complet, MSB32 et éléments internes du XOR replié. Sortie : 12 764 octets, SHA-256 `eb67d9123a592b064a7799bf12e36b274f425683e322f83c9f440440cb23f8e9` ; Rust reconstruit le document canonique complet et exige l’égalité des octets. | Windows x64, MSVC 19.44 et Mbed TLS épinglé ; revue manuelle des seules entrées synthétiques et scan de secrets le 2026-10-28 ; manifeste externe avec checkouts propres, 81 unités et 313 en-têtes ; contre-revue indépendante C1 `GO` | Autorisée par l’option C comme fixture intégrée ; harnais, journaux, checkouts et manifeste fermé restent externes et ignorés |
