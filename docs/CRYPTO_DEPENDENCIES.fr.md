# Registre prévu des dépendances cryptographiques

Statut : candidates uniquement ; aucune dépendance ci-dessous n’est présente dans `Cargo.lock` ni autorisée pour l’implémentation. Revue le 2026-10-25 pour CR3.

| Crate | Candidate | Licence | Preuve MSRV | Usage prévu | État des avis |
|---|---:|---|---:|---|---|
| [`aes`](https://crates.io/crates/aes/0.8.4) | 0.8.4 | MIT OR Apache-2.0 | 1.56 | Primitive bloc AES | NOT_TESTED avant verrouillage |
| Famille AES-GCM/GHASH | Non résolue | Non résolue | Non résolue | Futurs chiffrement authentifié et GHASH exigés par les preuves protocolaires ultérieures | BLOQUÉ en attente de revue de l’algorithme et de la crate |
| [`cfb-mode`](https://crates.io/crates/cfb-mode/0.8.2) | 0.8.2 | MIT OR Apache-2.0 | 1.56 | Composition CFB | NOT_TESTED avant verrouillage |
| [`hmac`](https://crates.io/crates/hmac/0.12.1) | 0.12.1 | MIT OR Apache-2.0 | Non déclarée | Construction HMAC | NOT_TESTED avant verrouillage |
| [`sha2`](https://crates.io/crates/sha2/0.10.9) | 0.10.9 | MIT OR Apache-2.0 | Non déclarée | SHA-256 | NOT_TESTED avant verrouillage |
| [`subtle`](https://crates.io/crates/subtle/2.6.1) | 2.6.1 | BSD-3-Clause | Non déclarée | Comparaison en temps constant | NOT_TESTED avant verrouillage |
| [`zeroize`](https://crates.io/crates/zeroize/1.9.1) | 1.9.1 | Apache-2.0 OR MIT | 1.85 | Effacement des secrets | NOT_TESTED avant verrouillage |
| [`secrecy`](https://crates.io/crates/secrecy/0.10.3) | 0.10.3 | Apache-2.0 OR MIT | 1.60 | Enveloppe de secret | NOT_TESTED avant verrouillage |
| [`getrandom`](https://crates.io/crates/getrandom/0.4.3) | 0.4.3 | MIT OR Apache-2.0 | 1.85 | Entropie de la plateforme | NOT_TESTED avant verrouillage |
| Famille ECDH secp256k1 | Non résolue | Non résolue | Non résolue | Futur accord de clé d’enregistrement exigé par les preuves protocolaires ultérieures | BLOQUÉ en attente de revue de l’algorithme et de la crate |

Les versions actuelles `aes` 0.9.3 et `sha2` 0.11.1 exigent respectivement Rust 1.89 et 1.88 ; elles ne sont donc pas candidates tant que GPSx déclare Rust 1.85. « MSRV non déclarée » est un contrôle de compatibilité non résolu, pas une hypothèse de compatibilité.

Les lignes AES-GCM/GHASH et ECDH secp256k1 ferment le manque d’inventaire
signalé par CR3, sans choisir d’implémentation. Elles devront être résolues avant
toute proposition de `gpsx-crypto` ou d’enregistrement.

## Gate de sécurité et d’acceptation

La [base RustSec](https://rustsec.org/advisories/) a été consultée le 2026-10-25, mais une recherche ne remplace pas l’audit du graphe réellement résolu. L’état des avis reste donc `NOT_TESTED`.

Avant toute création de `gpsx-crypto` :

1. CR3 doit accepter les algorithmes et familles de crates.
2. Versions, fonctionnalités et sommes exactes doivent être résolues dans `Cargo.lock`.
3. `cargo audit` ne doit signaler aucun avis non accepté de vulnérabilité, unsoundness ou paquet non maintenu sur le graphe transitif complet.
4. `cargo deny check advisories licenses bans sources` doit réussir avec une politique relue.
5. Les builds croisés Android/Windows et vecteurs publics connus doivent réussir.
6. Toute exception exige responsable, justification, expiration et nouvelle revue indépendante.

Aucune crate de ce tableau n’autorise une primitive cryptographique maison ni l’implémentation du protocole.
