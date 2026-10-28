# Plan de cryptographie de session

Statut : intégré le 2026-10-28. La troisième remédiation du plan,
l’implémentation et ses suivis R1/R2, l’oracle externe fermé et la liaison C1 de
la fixture canonique ont tous reçu un `GO` indépendant. Le responsable du
projet a accepté le risque résiduel imposé par le tag de 32 bits. N1, le paquet
complet des licences tierces, reste obligatoire avant toute distribution d’un
binaire.

## Périmètre et preuves

**FAIT :** la base chiaki-ng épinglée au commit
`c8bea2642d0e95402f373af55584a3b98cd89417` emploie ECDH secp256k1, une clé de
handshake de 16 octets, un secret partagé de 32 octets, une dérivation
HMAC-SHA-256, un flot AES-128 en mode compteur et AES-128-GCM comme GMAC avec un
IV de 16 octets et un tag de 4 octets. Les sources observées sont
`lib/include/chiaki/ecdh.h`, `lib/src/ecdh.c`,
`lib/include/chiaki/gkcrypt.h`, `lib/src/gkcrypt.c`,
`lib/include/chiaki/seqnum.h`, `lib/src/streamconnection.c` et
`test/gkcrypt.c`. Elles restent de simples références tant que la revue de
provenance n’autorise pas de fichiers dérivés.

**FAIT :** la fonction épinglée `chiaki_ecdh_derive_secret` accepte une clé de
handshake et une signature distante de 32 octets sans lire aucune des deux. Son
appelant exige une signature distante non vide puis la lui transmet. GPSx ne
doit pas reproduire silencieusement ce comportement non authentifié.

**FAIT :** le vecteur amont prouve que la signature distante vaut
`HMAC-SHA-256(handshake_key, clé SEC1 non compressée complète de 65 octets)`,
préfixe `0x04` inclus. ECDH produit la coordonnée x grand-boutiste de 32 octets.
La dérivation vaut `key || iv = HMAC-SHA-256(ecdh_secret,
01 || direction || 00 || handshake_key || 01 || 00)`, avec direction locale `2`
et distante `3`.

**FAIT :** le flot est AES-128 sur
`little_endian_u128(iv) + floor(key_pos / 16)`. GMAC utilise un IV de 16 octets
avancé de `floor(key_pos / 16)`, le paquet comme AAD, un texte clair vide et les
quatre premiers octets du tag complet. L’époque vaut
`(key_pos > 0 ? key_pos - 1 : 0) / 45000`. Les clés suivantes utilisent
SHA-256, pas HMAC, sur `key_gmac_base` et l’IV avancé de `epoch * 44910`, puis
font le XOR des deux moitiés du digest.

**INCONNU :** GPSx n’a testé aucun échange de session avec une console, un
émulateur, le LAN, une PS4, une PS5 ou le GP520. L’interopérabilité, la réponse
d’échec, la correction de position sortante et les différences par cible restent
non vérifiées.

## Décision intégrée sur les dépendances

Les ajouts directs intégrés sont des versions exactes sans fonctionnalités par
défaut :

| Crate | Fonctionnalités candidates | Usage | Preuve actuelle |
|---|---|---|---|
| `secp256k1` 0.33.1 | `alloc` ; défauts désactivés ; `secp256k1-sys` 0.14.1 | validation secp256k1 et ECDH sur le point brut | Métadonnées des crates CC0-1.0 ; libsecp256k1 C vendue et patchée sous MIT ; MSRV déclarée 1.63 ; C/FFI reste dans la dépendance relue |
| `ctr` 0.9.2 | `zeroize` ; type concret `Ctr128LE<aes::Aes128>` | composition AES-CTR petit-boutiste maintenue | MIT OR Apache-2.0 ; MSRV déclarée 1.56 |
| `aes-gcm` 0.10.3 | `aes`, `zeroize` ; `AesGcm<Aes128, U16>` | GMAC complet de 16 octets avec nonce de 16 octets | MIT OR Apache-2.0 ; MSRV déclarée 1.56 |
| `subtle` 2.6.1 | défauts désactivés ; dépendance directe | comparaison constante du tag reçu avec `full_tag[..4]` | BSD-3-Clause ; déjà présente transitivement |

Il n’y a aucune fonctionnalité `hazmat`. GPSx calcule le tag complet, prend ses
quatre octets de poids fort comme l’exige GCM et les vérifie en temps constant.
Les dépendances exactes existantes `hmac`, `sha2`, `aes` et `zeroize` restent
dans le périmètre. `getrandom` n’est pas une dépendance directe du cœur portable
dans cette proposition : l’entropie de production est injectée par un adaptateur
de plateforme implémentant les traits de RNG cryptographique requis. Les RNG
déterministes sont réservés aux tests.

**FAIT (2026-10-28) :** la résolution `k256` 0.13.4 initialement approuvée
amenait `crypto-bigint` 0.5.5, désormais touché par `RUSTSEC-2026-0387`.
`k256` 0.14.0 déclare Rust 1.85, mais sa chaîne transitive corrigée exige Rust
1.87. GPSx a rejeté les deux résolutions sans ignorer l’avis et a résolu
`secp256k1` 0.33.1/`secp256k1-sys` 0.14.1. Les contrôles Rust 1.85 Windows et
Android, Clippy, cargo-audit 0.22.2, cargo-deny 0.20.2 et `cargo tree -d`
réussissent ; le graphe exact figure dans
`session_crypto_dependency_audit_v1.json`. Cette dépendance C/FFI maintenue,
ses métadonnées de crates CC0-1.0 et son code C libsecp256k1 vendu et patché
sous MIT remplacent néanmoins la famille Rust pure approuvée : ils ont donc
exigé puis reçu l’acceptation CR3 indépendante le 2026-10-28.

## API et invariants d’état requis

- La crate portable `gpsx-crypto` reste `#![forbid(unsafe_code)]` et ne dépend
  d’aucune plateforme, JNI, réseau, journalisation ou télémétrie.
- `HandshakeKey` possède exactement 16 octets. Elle est non `Clone`, non `Copy`,
  expurgée, effacée à la destruction et produite une fois par tentative par un
  CSPRNG frais. Elle n’est transmise que dans le LaunchSpec chiffré par rpcrypt.
  Le `HandshakeSession` consommant signe la clé locale, vérifie le pair, effectue
  ECDH et dérive les deux directions. Sa seule sortie de clé est la méthode
  LaunchSpec étroite ci-dessous ; aucun accesseur général n’existe.
- Une clé privée éphémère est non `Clone`, expurgée et effacée à la destruction.
  Une tentative de session la consomme exactement une fois lors de la dérivation.
- La clé publique est exactement un point SEC1 non compressé et validé de
  65 octets. Longueur, préfixe, point hors courbe, point à l’infini ou longueur
  de signature invalides échouent avant ECDH.
- Avant parsing du point ou ECDH, GPSx vérifie le HMAC-SHA-256 distant de
  32 octets sur les 65 octets exacts avec `Mac::verify_slice`. Un échec est
  terminal, sans repli, et ne produit aucun secret.
- La coordonnée x ECDH brute ne devient jamais directement une clé de chiffrement.
  La dérivation HMAC-SHA-256 observée n’est reproduite qu’après liaison par oracle
  de ses octets exacts et de la séparation clé/IV en deux blocs de 16 octets.
- Les contextes de chiffrement et GMAC possèdent leurs secrets, sont non `Clone`,
  ont des sorties `Debug`/`Display` expurgées et effacent toutes leurs clés à la
  destruction.
- Position, addition vérifiée, époque GMAC et nombre de vérifications entrantes sont des états
  typés. Le déchiffrement entrant accepte toute position d’octet ; seul le début
  CTR interne est aligné et les premiers octets sont sautés. Débordement, position
  hors fenêtre ou limite épuisée renvoient une erreur sans sortie entrante
  partielle. Pour la protection sortante, une erreur interne après le début du
  CTR peut laisser la plage choisie transformée ; l’appelant jette le paquet
  entier et la position monotone n’est volontairement pas annulée, ce qui
  empêche la réutilisation d’un nonce.
- Le typage sépare entrée non authentifiée, clé de pair authentifiée, secret
  dérivé et clés de trafic prêtes. Aucun appelant ne peut initialiser le trafic
  depuis des octets non vérifiés.

Le typestate public concret est :

```text
HandshakeSession<AwaitingPeer>::new(entropy) -> Result<HandshakeSession<AwaitingPeer>>
HandshakeSession<AwaitingPeer>::local_public_key_and_tag(&self) -> LocalHandshakeMaterial
HandshakeSession<AwaitingPeer>::write_encrypted_launch_spec(
  &mut self, &mut SessionRpcrypt, LaunchSpec, &mut [u8])
  -> Result<EncryptedLaunchSpecLength, LaunchSpecError>
HandshakeSession<AwaitingPeer>::finish(self, UnverifiedPeerKey)
  -> Result<TrafficKeyPair { local: LocalTrafficKeys, remote: RemoteTrafficKeys }>
LocalTrafficKeys::protect_in_place(&mut packet, encrypted_range)
  -> Result<(OutboundPacketPositions, [u8; 4])>
RemoteTrafficKeys::verify_and_decrypt_in_place(
  key_pos_low, &mut packet, encrypted_range, &[u8; 4]) -> Result<ReceiveOutcome>
```

`finish` vérifie le HMAC du pair avec la `HandshakeKey` possédée par la session,
parse le point, consomme le scalaire éphémère de la session pour ECDH et dérive
les deux directions avec cette même clé. Aucun `AuthenticatedPeerKey`, secret
partagé, emprunt de clé de handshake, `From`/`TryFrom`, désérialiseur ou chemin
non vérifié ne sort de cette chaîne. Une primitive privée utilise
`Direction::{Local, Remote}`, mappée uniquement vers `2` et `3`. Les tests
`compile_fail` refusent dérivation hors de `finish`, réutilisation de la session,
constructeur de scalaire fixe en production, échange des directions et clonage.
Un test structurel prouve qu’aucune API publique ne peut vérifier avec une
`HandshakeKey` puis dériver avec une autre.

La candidate LaunchSpec relue ultérieurement supprime la sortie temporaire de
clé brute. `write_encrypted_launch_spec` est désormais l’unique consommateur
public des 16 octets ; il ne renvoie que la longueur de base64 chiffrée écrite
dans le stockage de l’appelant, et `finish` rejette la session tant que cette
écriture n’a pas réussi. Aucun tableau possédé ni référence de clé ne sort. En
production, aucun constructeur de `HandshakeKey` ou `HandshakeSession` depuis
des octets n’existe ; la clé fixe est sous `#[cfg(test)]`.

**HYPOTHÈSE :** la confidentialité de cette frontière dépend du lot relu
rpcrypt/LaunchSpec. Jusqu’au `GO` de la contre-revue indépendante de
l’implémentation, cette frontière reste candidate et non une garantie intégrée ;
le matériau de clé en clair ne doit jamais rejoindre E/S, logs, erreurs ou
télémétrie.

Le vecteur `test/gkcrypt.c::test_ecdh` est obligatoire. Toute mutation d’un octet
de la clé du pair, de la signature ou de la clé de handshake échoue avant ECDH.
Une contradiction matérielle impose une nouvelle revue.

L’API de trafic authentifie le paquet complet et ne chiffre que la
`encrypted_range` bornée contenue dans ce paquet. À l’émission, CTR est appliqué
d’abord à cette plage, puis GMAC au paquet résultant complet. À la réception,
GMAC est vérifié sur le paquet complet avant tout déchiffrement. L’appelant
possède la disposition Takion et doit mettre à zéro le champ MAC et, pour
CONTROL/CONGESTION, le champ position avant d’appeler cette primitive portable.
La limite de 2 048 octets porte sur le paquet authentifié complet, non sur la
seule plage chiffrée.

## Politique de position et d’allocation sortante

`KeyPositionReconstructor` met dans le périmètre la reconstruction 32 vers 64 bits
de `chiaki_key_state_request_pos` et reproduit tous les vecteurs de
`test/keystate.c`. La position est reconstruite et bornée avant SHA-256, AES ou GHASH.

Politique de réception fixe :

- paquet authentifié maximal : 2 048 octets ;
- plafond de resynchronisation avant : 4 194 304 octets (4 Mio) ;
- tolérance arrière : 262 144 octets (fenêtre prévue de 128 paquets) ;
- avant le premier succès, position maximale 4 194 304 ;
- seul un succès avance la plus haute position authentifiée ;
- une candidate avant dans le plafond de 4 Mio provoque au plus une dérivation
  directe de l’époque cible et une tentative GMAC ; un succès resynchronise la
  marque haute, un échec consomme les budgets global et de l’époque ;
- une position initiale ou avant au-delà du plafond ne calcule ni SHA-256, ni AES,
  ni GHASH et consomme un échec global ; 32 de ces échecs terminent la session ;
- une position plus ancienne que la fenêtre arrière ne calcule rien et est
  abandonnée silencieusement comme périmée, sans consommer le budget d’échec
  d’authentification ;
- une seule époque/clé GMAC courante est retenue. Une transition avant dérive la
  cible une fois ; une époque antérieure dans la fenêtre utilise une clé
  temporaire effaçable. Aucun parcours itératif d’époques.

**HYPOTHÈSE :** le plafond de 4 Mio tolère avec marge les pertes simulées exigées
de 64 Kio, 256 Kio et 1 Mio tout en bornant les époques choisies par l’attaquant ;
à 15 Mbit/s, il représente environ 2,2 secondes de flux chiffré. La fenêtre
arrière de 256 Kio ne borne que péremption et réordonnancement. Les preuves
replay/oracle doivent confirmer les deux ; tout changement impose une revue. Une
perte supérieure à 4 Mio ne peut pas se resynchroniser : les positions suivantes
restent trop en avant et le 32e rejet termine la session, imposant une reconnexion.

`epoch * 44910`, `key_pos + 16`, reconstruction, conversion en blocs et addition
IV 128 bits sont vérifiés. Un report au-delà de 128 bits est une erreur.

`OutboundKeyPositionAllocator` est l’unique source de positions émises. Pour une
plage chiffrée de longueur `n`, GMAC est à `p`, le chiffrement à `p + 16`, puis la position devient
`round_up_16(p + 16 + n)`. Cette correction volontaire du défaut amont garantit
que deux tags ne partagent jamais `floor(p / 16)` sous une clé. L’acceptation
console est **INCONNUE** ; une contradiction arrête la tranche.

## Gate de sûreté du tag court et du compteur

Le tag GMAC de 32 bits est une contrainte protocolaire, pas un choix GPSx.
L’annexe C, tableau 1, de la NIST SP 800-38D donne les limites suivantes
([publication officielle](https://doi.org/10.6028/NIST.SP.800-38D)) :

| Maximum Combined Length of the Ciphertext and AAD In a Single Packet (bytes) | Maximum Invocations of the Authenticated Decryption Function |
|---:|---:|
| 2^5 | 2^22 |
| 2^6 | 2^20 |
| 2^7 | 2^18 |
| 2^8 | 2^15 |
| 2^9 | 2^13 |
| 2^10 | 2^11 |

Il n’existe aucune ligne au-delà de 1 024 octets. GPSx ne revendique aucune
conformité SP 800-38D pour les paquets de 1 025 à 2 048 octets. C’est un risque
résiduel imposé par le protocole. NIST a annoncé que la future révision retirera
les tags inférieurs à 96 bits. L’acceptation de ce risque par le responsable du
projet est **ACCEPTÉE le 2026-10-28**. Cette acceptation ne revendique ni
conformité SP 800-38D ni interopérabilité console.

Politique fixe de réception, par direction distante. La génération
d’authentification dans la direction locale/émission monotone n’est pas imputée
à ces limites d’invocations de déchiffrement authentifié ; l’allocateur sortant
continue de garantir l’unicité des IV :

- toute vérification GMAC compte, succès ou échec ;
- limite de 2 048 invocations pour chaque clé GMAC d’époque, indexée par époque ;
  une vérification avec une clé temporaire antérieure compte pour cette époque ;
- jusqu’à 1 024 octets, cette limite respecte la ligne applicable ; jusqu’à
  2 048 octets, le risque reste non conforme ;
- les échecs d’authentification, longueur, position initiale ou trop en avant
  comptent dans une limite globale de 32 par direction/session ; les échecs GMAC
  comptent aussi dans une limite de 8 par époque ; aucun compteur d’échec ne reset ;
- atteindre une limite termine la session avant une nouvelle tentative, sans
  reset, réponse, retry ni re-clé silencieuse ;
- les paquets en échec sont abandonnés silencieusement. Seuls des compteurs
  numériques bornés sont consignables ; jamais paquet, adresse, clé, position ou tag.

L’état d’invocation est une table bornée et non secrète associant époque et
compteur. L’intervalle fixe de réception intersecte au plus 101 époques ; la
capacité vaut donc 102 entrées. Une entrée ne peut disparaître qu’une fois son
époque définitivement sous la fenêtre arrière de la marque haute monotone ; toute
époque encore acceptable ou susceptible de devenir courante conserve son compte.
Changer d’époque ne reset donc aucun compte, tandis qu’une session peut traiter
au moins 1 000 000 de paquets valides sur de nombreuses clés d’époque sans
atteindre de limite globale de succès.

La borne utilise des époques de 45 000 positions définies plus haut par
`(key_pos > 0 ? key_pos - 1 : 0) / 45000` : la portée vaut
`262 144 + 4 194 304 = 4 456 448` octets, donc elle intersecte au plus
`ceil(4 456 448 / 45 000) + 1 = 101` époques. La constante distincte `44 910`
n’est que le multiplicateur vérifié d’offset IV, pas la longueur d’une époque.

**HYPOTHÈSE :** 2 048 invocations tiennent dans une époque de 45 000 positions
seulement si l’avance moyenne ne dépasse pas environ 21,97 octets, ou si des
retransmissions s’accumulent. L’oracle replay doit établir si un trafic valide
peut atteindre la limite ; il ne doit ni l’augmenter ni la remettre à zéro.

Le compteur et les constantes GMAC exigent un oracle octet-exact. `Ctr128LE`
n’est accepté que si largeur, boutisme, positionnement et débordement concordent.
Sinon le travail s’arrête ; aucun repli cryptographique maison n’existe.

## Entropie, cache, performance et provenance

La production injecte un CSPRNG de plateforme implémentant `CryptoRng + RngCore`
de `rand_core` 0.6. GPSx appelle `try_fill_bytes` en amont dans une candidate
effaçable de 32 octets et effectue au plus huit essais pour construire une
`secp256k1::SecretKey` validée par rejet sans biais ; échec d’entropie ou huit
candidates rejetées renvoie une `Err` classée sans panique. L’enveloppe GPSx
consommante, non `Clone`, passe la clé par référence à
`secp256k1::ecdh::shared_secret_point`, ne conserve que la coordonnée x
grand-boutiste, efface le point temporaire et appelle `non_secure_erase` dans
`Drop`. La dépendance précise que les copies créées par le compilateur ne peuvent
être garanties effacées ; ce risque résiduel de copie mémoire fait explicitement
partie de la nouvelle revue CR3 d’implémentation. Seul `#[cfg(test)]` expose un constructeur
de scalaire fixe pour le vecteur épinglé ; il est absent des builds de production.

Le cœur portable n’a ni thread, ni worker, ni cache de flot. Il conserve au plus
la clé GMAC courante de 16 octets ; clés temporaires et contextes sont effacés.
Tout cache ou pré-calcul supplémentaire exige une revue séparée.

La performance CTR/GMAC est **NON_TESTÉE** sur GP520/ARMv8. Aucune amélioration
n’est revendiquée. Après ouverture explicite du gate matériel, le premier build
dépendant de ce chemin exige des mesures comparables P50/P95/P99 CPU et profondeur
de file avant d’envisager cache ou thread.

`PROVENANCE.fr.md` consigne l’inventaire et les fonctions proposés. Seuls ECDH,
HMAC, dérivations clé/IV/GMAC, positions CTR, directions, allocation sortante et
reconstruction 32 vers 64 bits, ainsi que le contrat de sortie unique de la clé
LaunchSpec, sont portables. Réseau, protobuf, parsing Takion, threads, cache
circulaire, formatage/chiffrement rpcrypt réel du LaunchSpec, média et logs sont
exclus et relèvent d’un futur lot relu.

## Gate de tests et de preuves

L’implémentation ne peut commencer qu’après un GO CR3 indépendant sur ce plan.
Sa revue devra ensuite exiger :

1. la résolution exacte des dépendances/fonctionnalités dans une nouvelle fixture d’audit ;
2. `cargo +1.85.0 check --locked --all-targets` sous Windows et pour
   `aarch64-linux-android`, ainsi que les gates audit et deny ;
3. `cargo tree -d` sans doublon de génération `aes`, `cipher` ou `digest`, sauf
   exception signée séparément ;
4. des KAT publics secp256k1 ECDH et NIST AES-GCM/CTR ;
5. un oracle fermé avec les sept tests `test/gkcrypt.c`, tous les vecteurs
   `test/keystate.c`, les deux directions, positions non alignées, et `0`, `1`,
   `44999` (époque 0), `45000` (époque 0), `45001` (époque 1), `90000`, `90001`,
   `2^32 + 0x420`, `0x6b1de0`, plus
   le cas RFC 1982 à une distance exacte de `2^31` ;
6. retour à une époque antérieure, saut multi-époques, report IV `ff..ff`, erreur
   à 2^128, `epoch * 44910`, `key_gmac_base` contre `key_base`, XOR des moitiés,
   `key_pos + 16` et égalité tag complet/MSB32 ;
7. toute mutation d’un octet, points SEC1 invalides, mauvais HMAC, débordements
   et expurgation ; un RNG en échec renvoie une `Err` classée sans panique ; le
   rejet de huit candidates renvoie l’erreur d’épuisement ; le constructeur de
   test reproduit `test_ecdh` ; les tests `compile_fail` prouvent l’absence en
   production de constructeur fixe, `HandshakeKey` depuis octets ou
   `HandshakeSession` depuis octets ;
8. au moins 1 000 000 de paquets complets via la véritable API
   protection/vérification/déchiffrement sur au moins 1 000 époques et plusieurs
   tailles de plage chiffrée sans arrêt,
   arrêt à la 2 049e vérification d’une même époque, facturation des clés
   temporaires à leur époque et absence de reset de tout compteur d’échec ; la
   preuve replay couvre l’avance moyenne sous 22 octets ou les retransmissions
   pouvant atteindre légitimement la limite d’époque ;
9. pertes simulées de 64 Kio, 256 Kio et 1 Mio suivies de trafic valide ; rejet
   au-delà de 4 Mio avant SHA-256/GHASH ; arrêt après 32 positions forgées trop en
   avant ; trafic ancien sans consommation du budget ; écart supérieur à 4 Mio
   imposant une reconnexion propre ; toutes frontières ;
10. tests structurels/API prouvant une unique sortie de clé de handshake, aucune
    vérification/dérivation avec deux clés différentes et l’égalité entre octets
    exportés et clé de dérivation sur vecteur déterministe ; propriété d’unicité
    des IV GMAC et test structurel sans thread ni cache de flot dans `gpsx-crypto` ;
11. un oracle indépendant avec relevé fermé équivalent à R5, sans identifiant,
    secret, capture, LAN, émulateur ou matériel réels.

La tranche de cryptographie de session est intégrée après les `GO` indépendants
sur l’implémentation, le relevé de construction externe, la liaison canonique
exacte de la fixture, les entrées de provenance et les résultats de tous les
gates. Le risque de tag court est accepté depuis le 2026-10-28. N1 bloque encore
la distribution binaire. Aucune revendication console ou performance n’est
permise sans sa gate séparée.
