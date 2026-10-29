# Contrat d’implémentation LaunchSpec et rpcrypt de session

Statut : **INTÉGRÉ**. La remédiation du plan et la contre-revue de
l’implémentation ont toutes deux reçu un `GO` indépendant le 2026-10-28.
L’implémentation Rust, l’oracle externe fermé, les tests de liaison exacte et
les gates locaux sont intégrés dans le commit
`3b90227491eb924168347e688d8dcdfe036e38ef`.

## But et preuves

Ce lot ferme l’unique frontière restante qui expose la clé brute de handshake.
Il formate le LaunchSpec canonique, inclut la clé par tentative en base64 RFC
4648, ajoute le NUL terminal, produit le flot rpcrypt épinglé au compteur zéro
en chiffrant par CFB un tampon nul de même longueur, XOR ce flot avec le
JSON-plus-NUL, puis ne renvoie que le base64 de ce texte chiffré. Il
n’effectue ni réseau, protobuf, Takion, log, télémétrie, stockage, thread, média,
contrôleur, Android, Windows ou travail GP520.

**FAIT :** au commit chiaki-ng épinglé
`c8bea2642d0e95402f373af55584a3b98cd89417`, `lib/src/launchspec.c` définit
l’ordre des champs et les ajouts selon cible/codec. `lib/src/streamconnection.c`
utilise un tampon JSON de 1 024 octets, inclut le NUL terminal, chiffre par CFB
un tampon nul de même longueur au compteur zéro de rpcrypt de session, XOR ce
flot avec ces octets, puis encode le texte chiffré en base64 avant la
construction protobuf. `lib/src/session.c` initialise cet
état rpcrypt depuis la cible négociée, le nonce de 16 octets et le matériau
d’enregistrement de 16 octets nommé `morning`.

**FAIT :** l’implémentation supprime l’ancienne sortie publique de clé brute.
`HandshakeSession::write_encrypted_launch_spec` est désormais l’unique chemin
public : il écrit la base64 du chiffré dans le stockage de l’appelant, et
`finish` rejette une session qui n’a pas achevé cette étape. Les propriétaires
de secrets restent non `Clone`, expurgés et effacés.

**HYPOTHÈSE :** reproduire le formateur épinglé et rpcrypt de session suffit à
l’interopérabilité console. Aucune connexion console n’a testé ce lot. Un
résultat émulateur ne prouverait ni le comportement console ni celui du GP520.

## Frontière des sources et licences

Les seules sources comportementales proposées sont :

- `lib/include/chiaki/common.h`, limité à `ChiakiTarget`, `ChiakiCodec`,
  `chiaki_target_is_ps5`, `chiaki_codec_is_h265` et `chiaki_codec_is_hdr` ;
- `lib/include/chiaki/launchspec.h` et `lib/src/launchspec.c` pour les champs,
  l’ordre, la ponctuation et les ajouts cible/codec ;
- `lib/include/chiaki/rpcrypt.h` et seulement
  `bright_ambassador_ps4_pre10`, `bright_ambassador`,
  `chiaki_rpcrypt_bright_ambassador`, `chiaki_rpcrypt_init_auth`,
  `rpcrypt_hmac_key`, `chiaki_rpcrypt_generate_iv`, `chiaki_rpcrypt_crypt` et
  `chiaki_rpcrypt_encrypt` dans `lib/src/rpcrypt.c`, avec les tables `echo_a`,
  `keys_a_ps4`, `keys_a_ps5`, `keys_b_ps4` et `keys_b_ps5` nécessaires ;
- `lib/src/session.c` uniquement pour l’ordre cible/nonce/`morning` ;
- `lib/src/streamconnection.c` uniquement pour la borne de 1 024 octets, le NUL
  terminal, le chiffrement et l’ordre du base64 externe ;
- RFC 4648 pour un base64 écrit par GPSx.

Tous les chemins utilisent le commit et la licence
`LicenseRef-AGPL-3.0-only-OpenSSL` déjà acceptés par l’option C. GPSx ne copie ni
formateur, implémentation base64, logs, protobuf ou transport. L’oracle externe
peut compiler les sources épinglées, mais harnais, correctifs, journaux et
manifeste fermé restent hors dépôt.

## Ownership et API proposés

L’implémentation reste dans la crate portable `gpsx-crypto`, dans un module privé
`launch_spec`. Déplacer le formatage dans `gpsx-protocol` obligerait à conserver
une sortie publique de clé brute entre crates. La crate reste
`#![forbid(unsafe_code)]`, sans E/S ni dépendance de plateforme.

La surface publique relue sera équivalente à :

```text
SessionNonce::from_session_response_bytes([u8; 16]) -> SessionNonce
SessionMorning::take_from_registration_buffer(&mut Zeroizing<[u8; 16]>)
  -> SessionMorning
SessionRpcrypt::new(SessionTarget, SessionNonce, SessionMorning)
  -> Result<SessionRpcrypt>
HandshakeSession<AwaitingPeer>::write_encrypted_launch_spec(
  &mut self,
  &mut SessionRpcrypt,
  LaunchSpec,
  &mut [u8],
) -> Result<EncryptedLaunchSpecLength>
HandshakeSession<AwaitingPeer>::finish(self, UnverifiedPeerKey)
  -> Result<TrafficKeyPair>
```

Les noms peuvent évoluer, mais pas ces invariants :

1. `SessionMorning`, `SessionRpcrypt` et tout tampon temporaire de texte clair
   ou clé sont non `Clone`, non `Copy`, expurgés et effacés sur tout retour.
   `SessionNonce` est public dans le protocole mais reste une valeur fixe
   possédée. `SessionTarget` et les paramètres vidéo publics peuvent être `Copy`.
2. La cible est une enum fermée unique : PS4 avant firmware 10, PS4 10+ ou PS5.
   Elle sélectionne à la fois la dérivation rpcrypt et les ajouts LaunchSpec.
   L’appelant ne peut fournir deux cibles indépendantes.
3. L’ancienne méthode publique `write_launch_spec_handshake_key` est absente. Un
   helper privé à la crate écrit une fois dans `Zeroizing<[u8; 16]>` ; aucune
   clé brute, référence, accesseur, callback, sérialiseur ou construction depuis
   les octets de clé ne devient public.
4. `write_encrypted_launch_spec` est l’unique consommateur. Il valide d’abord
   paramètres et capacité, construit le texte clair dans un tampon effaçable de
   1 024 octets, ajoute un NUL, chiffre par CFB le même nombre d’octets nuls au
   compteur zéro pour obtenir le flot de type OFB épinglé, XOR le texte clair
   avec ce flot, puis n’écrit que le base64 du texte chiffré. Il ne chiffre pas
   directement le texte clair par CFB.
5. La sortie appelante reste inchangée sur toute erreur. Un succès engage à la
   fois `launch_spec_written` dans `HandshakeSession` et le slot à usage unique
   `launch_spec_counter_zero_consumed` possédé par `SessionRpcrypt`. Le
   propriétaire rpcrypt mutable refuse tout LaunchSpec ultérieur, même via un
   autre `HandshakeSession`. `finish` échoue avant vérification/ECDH si son
   LaunchSpec n’a pas réussi. Aucun état n’est annulé après succès.
6. La longueur exclut tout terminateur de confort et vaut au plus 1 368 octets
   (`4 * ceil(1024 / 3)`). Aucune collection possédée de texte clair ou chiffré
   n’est renvoyée. Les octets après la plage retournée n’en font pas partie.
7. Les erreurs ne contiennent que des classes publiques : paramètres invalides,
   sortie trop petite, déjà écrit, non écrit, débordement arithmétique ou erreur
   cryptographique interne. Jamais nonce, `morning`, clé, texte clair, chiffré
   ou octets dérivés d’un secret.

`SessionRpcrypt`, et non `HandshakeSession`, possède le slot à succès unique car
il possède l’espace des compteurs. Deux sessions de handshake ne peuvent donc
pas demander le flot LaunchSpec compteur zéro au même propriétaire rpcrypt. Cela
n’empêche pas l’appelant de construire deux propriétaires depuis des entrées
copiées : le typage ne prouve ni fraîcheur ni bon appariement. Pour éviter une
copie évitable du secret `rp_key`, `SessionMorning::take_from_registration_buffer`
emprunte une source `Zeroizing`, la copie une fois puis efface immédiatement la
source au lieu d’accepter un `[u8; 16]` par valeur, qui est `Copy`. Fraîcheur et
appariement restent les obligations de l’orchestrateur de session.

**INFÉRENCE / risque résiduel :** le protocole épinglé commence aussi les deux
directions ctrl futures au compteur rpcrypt zéro. La construction LaunchSpec
CFB-sur-zéros et le CFB direct de ctrl partagent donc `E(IV)` pour leur premier
bloc ; leurs feedbacks et blocs suivants diffèrent. Ce lot n’implémente ni
n’autorise le chiffrement ctrl. L’accès aux deux slots ctrl compteur zéro est
réservé à un futur lot ctrl relu séparément. L’état à succès unique LaunchSpec
ne prétend pas éliminer ce partage imposé par le protocole.

## Contrat de formatage canonique

`LaunchSpec` ne contient que des entrées numériques/enum typées et bornées :
largeur, hauteur, FPS maximal, débit envoyé en kbit/s, MTU, RTT en millisecondes
et codec. Aucune chaîne JSON appelante n’existe, donc aucune voie d’échappement
ou d’injection. Le formatage décimal vérifié est sans allocation.

L’oracle octet-exact lie l’ordre épinglé, la ponctuation, l’écriture des nombres,
les valeurs fixes, tableaux fixes, le base64 de la clé et les ajouts cible/codec.
PS4 n’a aucun ajout PS5. PS5 ajoute le mode de flux adaptatif, sélectionne AVC ou
HEVC et SDR ou HDR exactement comme l’amont. Le formateur épinglé ignore le codec
pour les deux familles PS4. GPSx rejette néanmoins PS4 avec HEVC ou HEVC-HDR par
politique produit explicite afin d’éviter un repli AVC non signalé. Ce rejet est
testé hors de l’oracle amont et n’est pas présenté comme un comportement amont.

Les six entrées numériques suivent la frontière de compatibilité octet-exacte
ci-dessous. Le formateur épinglé accepte `unsigned int` sans validation
sémantique ; zéro est donc accepté ici. Un futur orchestrateur vidéo/session
pourra imposer une politique plus étroite avant construction.

| Champ | Type Rust | Plage acceptée | Politique zéro |
|---|---:|---:|---|
| largeur | `u32` | `0..=u32::MAX` | accepté, comme le formateur |
| hauteur | `u32` | `0..=u32::MAX` | accepté, comme le formateur |
| FPS maximal | `u32` | `0..=u32::MAX` | accepté, comme le formateur |
| débit envoyé (kbit/s) | `u32` | `0..=u32::MAX` | accepté, comme le formateur |
| MTU | `u32` | `0..=u32::MAX` | accepté, comme le formateur |
| RTT (ms) | `u32` | `0..=u32::MAX` | accepté, comme le formateur |

Avec tous les champs à `u32::MAX`, le modèle épinglé mesure 879 octets pour PS4
et 951 pour PS5 avant NUL. La plus grande entrée chiffrée mesure donc 952 octets
et son base64 1 272 octets. Les capacités défensives restent 1 024 octets pour
texte-plus-NUL et 1 368 pour le base64 (`4 * ceil(1024 / 3)`). Les tests couvrent
`0`, `1`, `u32::MAX - 1` et `u32::MAX` pour chaque champ ; négatifs et valeurs
au-delà de `u32` sont non représentables et refusés par des tests `compile_fail`.
Le formatage refuse troncature, arithmétique non vérifiée, dépendance à la locale,
flottants appelants et tout dépassement interne des capacités fixes.

## Contrat cryptographique

Rpcrypt de session possède la clé AES dérivée et l’ambassadeur de 16 octets. Son
IV est constitué des 16 premiers octets de HMAC-SHA-256 sur
`ambassador || counter_be64`, avec la clé HMAC de la famille cible épinglée. Pour
un LaunchSpec de `n` octets NUL inclus, l’implémentation met à zéro un tampon
`Zeroizing` de `n` octets, lui applique l’encryptor AES-128-CFB au compteur zéro,
puis calcule `ciphertext = (JSON || NUL) XOR resulting_keystream`. Cela reproduit
le feedback de type OFB épinglé ; chiffrer directement le texte par CFB devient
faux dès le deuxième bloc. Les dépendances RustCrypto auditées sont réutilisées ;
aucune nouvelle primitive AES, CFB, OFB, HMAC, SHA-256 ou base64 n’est introduite.

Les dérivations PS4 avant 10 et PS4 moderne/PS5 restent des branches fermées
séparées. La production n’accepte le matériau négocié qu’à travers les frontières
possédée `SessionNonce` et consommatrice `SessionMorning` décrites ci-dessus ;
cela n’authentifie pas son origine et n’empêche pas l’appelant de dupliquer les
entrées avant transfert d’ownership. Les constructeurs de confort pour tuples
fixes complets n’existent que sous `#[cfg(test)]`. Debug et Display sont expurgés
pour tous les secrets et l’état du constructeur.

## Oracle fermé requis

Avant intégration, un build scratch externe du comportement C épinglé produit
`tests/fixtures/launch_spec_oracle_v1.json` depuis des nonce, `morning` et clé de
handshake synthétiques publics. Le dépôt ne garde que la fixture canonique, ses
entrées manifeste/provenance et ses hashes. Le relevé externe fermé consigne
checkout propre, commandes exactes, compilateur, unités de traduction et
en-têtes, correctifs éventuels et SHA-256 de chaque entrée/sortie.

La fixture couvre au minimum :

- PS4 avant 10, PS4 10+ et PS5 ;
- AVC, HEVC-SDR et HEVC-HDR lorsqu’ils sont valides ;
- frontières décimales de chaque champ et plus grand JSON accepté ;
- représentation base64 exacte de 24 octets de la clé de handshake ;
- texte clair sans puis avec NUL, IV rpcrypt, flot CFB-sur-zéros, texte chiffré
  par XOR et base64 final dans des champs liés séparément ;
- cas d’au moins 32 octets et bloc final dont la longueur n’est pas multiple de 16 ;
- calculs de longueur exacts et frontière de capacité de sortie.

Le test Rust reconstruit le document entier et compare ses octets exacts. Il
vérifie le nombre de cas et tous les noms de champs/racines. Toute mutation d’une
entrée, cible, codec, valeur numérique, octet JSON, NUL, IV, octet du flot,
octet chiffré, base64, longueur, nombre de cas ou contenu après l’accolade finale
doit échouer. Des mutations dédiées remplacent la construction par CFB direct
du texte, omettent le XOR et utilisent un compteur non nul ; chacune doit
échouer. Les littéraux de fixture sont dérivés des constantes de production
lorsqu’elles existent.

## Gate de tests et d’implémentation

L’implémentation ne commence qu’après un `GO` CR3 indépendant sur ce plan, son
équivalent anglais et les entrées de provenance. La revue d’implémentation exige :

1. tests `compile_fail` prouvant la disparition de la sortie de clé brute et le
   caractère non `Clone` de tous les propriétaires secrets ;
2. vecteurs exacts formateur, RFC 4648, rpcrypt et bout en bout ;
3. tests un succès par `SessionRpcrypt` à travers plusieurs sessions de
   handshake, second appel, absence avant `finish`, sortie inchangée, effacement,
   expurgation, frontières numériques, combinaisons invalides et capacités ;
4. mutations tuées si le CFB direct remplace CFB-sur-zéros-plus-XOR, XOR omis,
   ordre chiffrement/base64 inversé, NUL omis, compteur non nul ou texte clair
   transmis à la sortie ;
5. `cargo +1.85.0 fmt --all --check`, Clippy verrouillé avec warnings refusés,
   tests workspace verrouillés, checks cœur Windows/Android, audit, deny,
   doublons, scan/manifeste des fixtures et ProvenanceGuard ;
6. `graphify update .` puis une contre-revue indépendante de l’implémentation.

Preuve matérielle : **AUCUNE**. Interopérabilité console et performances GP520
restent **INCONNU / NON TESTÉ**. N1, le bundle complet des licences tierces,
reste obligatoire avant toute distribution binaire.
