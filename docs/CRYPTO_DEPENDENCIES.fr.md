# Registre des dépendances cryptographiques

Statut : le sous-ensemble d’enregistrement a été autorisé le 2026-10-26. Le plan
de session a reçu `GO` et l’acceptation du risque de tag court le 2026-10-28. Le
graphe d’implémentation passe les gates verrouillées Windows/Android, RustSec et
licences. Son remplacement de `k256` par `rust-secp256k1`, motivé par la
sécurité, a reçu le `GO` indépendant d’implémentation ; l’oracle externe fermé
et sa liaison canonique ont aussi reçu le `GO` indépendant. Le graphe des
dépendances de session est intégré.

Le sous-ensemble direct réservé à l’enregistrement proposé le 2026-10-26 est
`aes`, `cfb-mode`, `hmac`, `sha2` et `zeroize`. Il a été autorisé par le GO de
la contre-revue du plan de cryptographie d’enregistrement. `subtle` est présent
transitivement via `digest/mac` ; `secrecy`, `getrandom`, AES-GCM/GHASH et ECDH
secp256k1 ne font pas partie de ce lot étroit.

| Crate | Résolution | Licence | Preuve MSRV | Usage prévu | État des avis |
|---|---:|---|---:|---|---|
| [`aes`](https://crates.io/crates/aes/0.8.4) | 0.8.4 | MIT OR Apache-2.0 | 1.56 ; Rust 1.85 Windows/Android PASS | Primitive bloc AES, fonctionnalité `zeroize` | PASS dans le graphe scratch audité |
| [`aes-gcm`](https://crates.io/crates/aes-gcm/0.10.3) | 0.10.3 avec `aes`, `zeroize` ; défauts désactivés ; tag complet 16 octets | MIT OR Apache-2.0 | Métadonnée 1.56 ; Rust 1.85 Windows/Android PASS | GMAC avec IV 16 octets ; vérification constante de `full_tag[..4]` ; sans `hazmat` | PASS dans le graphe intégré et relu indépendamment |
| [`cfb-mode`](https://crates.io/crates/cfb-mode/0.8.2) | 0.8.2 | MIT OR Apache-2.0 | 1.56 ; Rust 1.85 Windows/Android PASS | Composition CFB, fonctionnalité `zeroize` | PASS dans le graphe scratch audité |
| [`hmac`](https://crates.io/crates/hmac/0.12.1) | 0.12.1 | MIT OR Apache-2.0 | Non déclarée ; Rust 1.85 Windows/Android PASS | Construction HMAC | PASS dans le graphe scratch audité |
| [`sha2`](https://crates.io/crates/sha2/0.10.9) | 0.10.9 | MIT OR Apache-2.0 | Non déclarée ; Rust 1.85 Windows/Android PASS | SHA-256 | PASS dans le graphe scratch audité |
| [`subtle`](https://crates.io/crates/subtle/2.6.1) | 2.6.1, défauts désactivés | BSD-3-Clause | Non déclarée ; Rust 1.85 Windows/Android PASS | Comparaison directe constante du tag de 4 octets | PASS dans le graphe d’implémentation |
| [`zeroize`](https://crates.io/crates/zeroize/1.9.1) | 1.9.1 | Apache-2.0 OR MIT | 1.85 ; Rust 1.85 Windows/Android PASS | Effacement des secrets | PASS dans le graphe scratch audité |
| [`secrecy`](https://crates.io/crates/secrecy/0.10.3) | 0.10.3 | Apache-2.0 OR MIT | 1.60 | Enveloppe de secret | NOT_TESTED avant verrouillage |
| [`getrandom`](https://crates.io/crates/getrandom/0.4.3) | 0.4.3 | MIT OR Apache-2.0 | 1.85 | Entropie de la plateforme | NOT_TESTED avant verrouillage |
| [`ctr`](https://crates.io/crates/ctr/0.9.2) | 0.9.2 avec `zeroize` ; défauts désactivés ; `Ctr128LE<Aes128>` | MIT OR Apache-2.0 | Métadonnée 1.56 ; Rust 1.85 Windows/Android PASS | AES-CTR petit-boutiste maintenu sur `aes` 0.8.4 existant | PASS dans le graphe intégré et relu indépendamment |
| [`secp256k1`](https://crates.io/crates/secp256k1/0.33.1) | 0.33.1 avec `alloc` ; défauts désactivés ; `secp256k1-sys` 0.14.1 | Métadonnées des crates CC0-1.0 ; libsecp256k1 C vendue et patchée sous MIT | Métadonnée 1.63 ; Rust 1.85 Windows/Android PASS | Validation SEC1 et ECDH secp256k1 sur point brut | PASS dans l’audit et la revue CR3 indépendante d’implémentation |
| [`k256`](https://crates.io/crates/k256/0.13.4) | rejetée | MIT OR Apache-2.0 | 1.65 | Candidate secp256k1 initiale | REJETÉE : `RUSTSEC-2026-0387` ; chaîne 0.14 corrigée sous Rust 1.87 |

Les versions actuelles `aes` 0.9.3 et `sha2` 0.11.1 exigent Rust 1.89 et 1.88.
`k256` 0.14.0 déclare Rust 1.85, mais sa chaîne `crypto-bigint` corrigée et non
retirée exige Rust 1.87. GPSx conserve donc les générations AES/digest et le MSRV
du workspace, avec la famille secp256k1 séparément relue ci-dessus. « MSRV non
déclarée » reste un contrôle non résolu, pas une hypothèse de compatibilité.

Le graphe de session verrouillé constitue la décision intégrée finale. Sa
résolution exacte figure dans
`tests/fixtures/session_crypto_dependency_audit_v1.json`. Les gates sécurité,
provenance, build, implémentation et intégration de l’oracle fermé réussissent.

## Gate de sécurité et d’acceptation

### Graphe résolu limité à l’enregistrement — 2026-10-26

**FACT :** Cargo 1.85 a résolu 17 paquets de registre. Les fonctionnalités
directes sont `aes/zeroize` et `cfb-mode/zeroize` ; les fonctionnalités par
défaut des cinq dépendances directes sont désactivées. La liste complète des
paquets, versions et sommes se trouve dans
`tests/fixtures/registration_crypto_dependency_audit_v1.json` ; le SHA-256 du
lockfile scratch vaut
`d312587e656ced976d4eb20c24178a4101640f548049328b5d8ce063c161d72f`.

| Commande | Résultat |
|---|---|
| `cargo +1.85.0 check --locked --all-targets` | PASS, Windows x64 |
| `cargo +1.85.0 check --locked --target aarch64-linux-android --all-targets` | PASS |
| `cargo-audit 0.22.2 audit --file Cargo.lock` | PASS, base d’avis `a78282a1414d6c28cc228c4eae3570451affb0dc` |
| `cargo-deny 0.20.2 check advisories licenses bans sources` | PASS avec la politique MIT, Apache-2.0, BSD-3-Clause et AGPL-3.0-only du paquet scratch |

L’implémentation du dépôt devra résoudre le même sous-ensemble et les mêmes
fonctionnalités. Tout écart de somme, source, version ou fonctionnalité annule
cette preuve et impose un nouvel audit.

**FACT (2026-10-26) :** le `Cargo.lock` du dépôt résout ce sous-ensemble
identique de 17 paquets et la gate GPSx automatisée compare chaque nom, version,
source et somme avec la fixture. `cargo-audit` réussit sur tout le lockfile du
dépôt, et cargo-deny réussit les contrôles advisories, licences, bans et sources
avec `gpsx-crypto` comme racine du graphe. La dépendance locale préexistante par
chemin dans `gpsx-transport`, sans rapport avec ce lot, reste hors de ce graphe
deny enraciné dans la crypto.

### Graphe d’implémentation de session — 2026-10-28

**FAIT :** Cargo 1.85 résout 30 paquets de registre pour `gpsx-crypto`. Les
noms, versions, sommes crates.io et fonctionnalités directes exacts sont liés
par `tests/fixtures/session_crypto_dependency_audit_v1.json` et contrôlés dans
`tools/powershell/check.ps1`. Il n’existe aucun doublon de génération `aes`,
`cipher` ou `digest`.

| Commande | Résultat |
|---|---|
| `cargo +1.85.0 check -p gpsx-crypto --locked --all-targets` | PASS, Windows x64 |
| `cargo +1.85.0 check -p gpsx-crypto --locked --target aarch64-linux-android --all-targets` | PASS, NDK 28.2.13676358 |
| `cargo +1.85.0 clippy -p gpsx-crypto --all-targets --locked -- -D warnings` | PASS |
| `cargo-audit 0.22.2 audit --file Cargo.lock` | PASS, base d’avis `40f5be6fb12ed93fc591bf4ab23b7d793b3f2239` |
| `cargo-deny 0.20.2 check advisories licenses bans sources` | PASS ; exceptions CC0 de métadonnées limitées à `secp256k1` et `secp256k1-sys` ; MIT de libsecp256k1 vendue suivi séparément |
| `cargo tree -p gpsx-crypto -d` | PASS, aucun doublon |

**FAIT :** la première résolution avec `k256` 0.13.4 a échoué cargo-audit sur
`RUSTSEC-2026-0387`. Aucune exception d’avis n’a été ajoutée. Le remplacement
passe les gates automatisées, mais sa propriété C/FFI, sa portabilité de build,
les mentions des crates CC0 et du code C vendu MIT, ainsi que son effacement de
secret documenté comme best effort, sont des sujets explicites de la revue CR3.

La gate de revue d’implémentation exige :

1. CR3 doit accepter les algorithmes et familles de crates.
2. Les versions, fonctionnalités et sommes exactes auditées doivent être reproduites dans le `Cargo.lock` du dépôt.
3. `cargo audit` ne doit toujours signaler aucun avis non accepté de vulnérabilité, unsoundness ou paquet non maintenu sur le graphe du dépôt.
4. `cargo deny check advisories licenses bans sources` doit toujours réussir avec la politique relue.
5. Les builds croisés Android/Windows et vecteurs publics connus doivent réussir.
6. Toute exception exige responsable, justification, expiration et nouvelle revue indépendante.

Aucune crate de ce tableau n’autorise une primitive cryptographique maison ni l’implémentation du protocole.
