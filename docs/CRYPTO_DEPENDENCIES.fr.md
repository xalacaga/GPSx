# Registre des dépendances cryptographiques

Statut : le sous-ensemble limité à l’enregistrement a été autorisé et ajouté à
`Cargo.lock` le 2026-10-26 ; toutes les candidates de cryptographie de session
restent bloquées.

Le sous-ensemble direct réservé à l’enregistrement proposé le 2026-10-26 est
`aes`, `cfb-mode`, `hmac`, `sha2` et `zeroize`. Il a été autorisé par le GO de
la contre-revue de `REGISTRATION_CRYPTO_PLAN.fr.md`. `subtle` est présent
transitivement via `digest/mac` ; `secrecy`, `getrandom`, AES-GCM/GHASH et ECDH
secp256k1 ne font pas partie de ce lot étroit.

| Crate | Candidate | Licence | Preuve MSRV | Usage prévu | État des avis |
|---|---:|---|---:|---|---|
| [`aes`](https://crates.io/crates/aes/0.8.4) | 0.8.4 | MIT OR Apache-2.0 | 1.56 ; Rust 1.85 Windows/Android PASS | Primitive bloc AES, fonctionnalité `zeroize` | PASS dans le graphe scratch audité |
| Famille AES-GCM/GHASH | Non résolue | Non résolue | Non résolue | Futurs chiffrement authentifié et GHASH exigés par les preuves protocolaires ultérieures | BLOQUÉ en attente de revue de l’algorithme et de la crate |
| [`cfb-mode`](https://crates.io/crates/cfb-mode/0.8.2) | 0.8.2 | MIT OR Apache-2.0 | 1.56 ; Rust 1.85 Windows/Android PASS | Composition CFB, fonctionnalité `zeroize` | PASS dans le graphe scratch audité |
| [`hmac`](https://crates.io/crates/hmac/0.12.1) | 0.12.1 | MIT OR Apache-2.0 | Non déclarée ; Rust 1.85 Windows/Android PASS | Construction HMAC | PASS dans le graphe scratch audité |
| [`sha2`](https://crates.io/crates/sha2/0.10.9) | 0.10.9 | MIT OR Apache-2.0 | Non déclarée ; Rust 1.85 Windows/Android PASS | SHA-256 | PASS dans le graphe scratch audité |
| [`subtle`](https://crates.io/crates/subtle/2.6.1) | 2.6.1 | BSD-3-Clause | Non déclarée ; Rust 1.85 Windows/Android PASS | Support temps constant transitif de `digest/mac` | PASS dans le graphe scratch audité |
| [`zeroize`](https://crates.io/crates/zeroize/1.9.1) | 1.9.1 | Apache-2.0 OR MIT | 1.85 ; Rust 1.85 Windows/Android PASS | Effacement des secrets | PASS dans le graphe scratch audité |
| [`secrecy`](https://crates.io/crates/secrecy/0.10.3) | 0.10.3 | Apache-2.0 OR MIT | 1.60 | Enveloppe de secret | NOT_TESTED avant verrouillage |
| [`getrandom`](https://crates.io/crates/getrandom/0.4.3) | 0.4.3 | MIT OR Apache-2.0 | 1.85 | Entropie de la plateforme | NOT_TESTED avant verrouillage |
| Famille ECDH secp256k1 | Non résolue | Non résolue | Non résolue | Futur accord de clé d’enregistrement exigé par les preuves protocolaires ultérieures | BLOQUÉ en attente de revue de l’algorithme et de la crate |

Les versions actuelles `aes` 0.9.3 et `sha2` 0.11.1 exigent respectivement Rust 1.89 et 1.88 ; elles ne sont donc pas candidates tant que GPSx déclare Rust 1.85. « MSRV non déclarée » est un contrôle de compatibilité non résolu, pas une hypothèse de compatibilité.

Les lignes AES-GCM/GHASH et ECDH secp256k1 ferment le manque d’inventaire
signalé par CR3, sans choisir d’implémentation. Elles devront être résolues avant
toute proposition de cryptographie de session authentifiée ; elles sont hors du
lot isolé limité à l’enregistrement.

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

La gate de revue d’implémentation exige :

1. CR3 doit accepter les algorithmes et familles de crates.
2. Les versions, fonctionnalités et sommes exactes auditées doivent être reproduites dans le `Cargo.lock` du dépôt.
3. `cargo audit` ne doit toujours signaler aucun avis non accepté de vulnérabilité, unsoundness ou paquet non maintenu sur le graphe du dépôt.
4. `cargo deny check advisories licenses bans sources` doit toujours réussir avec la politique relue.
5. Les builds croisés Android/Windows et vecteurs publics connus doivent réussir.
6. Toute exception exige responsable, justification, expiration et nouvelle revue indépendante.

Aucune crate de ce tableau n’autorise une primitive cryptographique maison ni l’implémentation du protocole.
