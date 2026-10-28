# Contrat d’implémentation de la négociation HTTP du canal de contrôle

Statut : **CANDIDAT D’IMPLÉMENTATION — ORACLE EXTERNE ET REVUE DE
L’IMPLÉMENTATION REQUIS**. Les constats C1–C7 et D1–D4 ont reçu un `GO`
indépendant. L’implémentation Rust portable et les tests locaux déterministes
existent désormais. L’oracle externe fermé, la liaison exacte de la fixture,
les preuves de mutation, les gates complets et une contre-revue indépendante
restent obligatoires avant intégration.

## But et preuves

Cette tranche prépare et valide uniquement la négociation HTTP du canal de
contrôle TCP sur réseau local. Elle s’exécute avant LaunchSpec, comme
l’implémentation épinglée, afin que le type de serveur classé contraigne le
profil vidéo et le codec ensuite écrits dans LaunchSpec. Elle formate une
requête bornée, chiffre ses champs, analyse uniquement l’en-tête HTTP borné et
conserve des compteurs ctrl opaques pour une future tranche revue séparément.

**FAIT :** `lib/src/session.c:522-598` démarre ctrl et attend son identifiant de
session avant que `lib/src/session.c:682` lance la connexion de flux qui
formate LaunchSpec. `lib/src/ctrl.c:1442-1461` utilise le type de serveur pour
ramener la PS4 de base de 1080p à 720p et imposer H.264 à PS4/PS4 Pro. GPSx
ordonne donc les états ainsi : négociation ctrl, classification serveur,
LaunchSpec validé, puis cryptographie du canal ctrl.

**FAIT :** au commit chiaki-ng épinglé
`c8bea2642d0e95402f373af55584a3b98cd89417`, `lib/src/ctrl.c` initialise les
compteurs ctrl local et distant à zéro. Sa requête TCP locale chiffre, dans
l’ordre, la clé d’enregistrement de 16 octets au compteur 0, l’identifiant
d’appareil de 32 octets au compteur 1, puis `Win10.0.0` avec son NUL au
compteur 2 ; PS4 10 et PS5 ajoutent un débit initial nul en petit-boutiste au
compteur 3.
PS5 ajoute le type de flux en petit-boutiste au compteur 4 (`1` AVC, `2` HEVC,
`3` HEVC HDR).

**FAIT :** la requête épinglée utilise `/sce/rp/session/ctrl` pour PS4 8/9,
`/sie/ps4/rp/sess/ctrl` pour PS4 10 et `/sie/ps5/rp/sess/ctrl` pour PS5. Les
versions RP sont respectivement `8.0`, `9.0`, `10.0` et `1.0`. Avec l’adresse
IPv4 numérique de longueur maximale et le port local 9295, les requêtes
canoniques mesurent 331 octets pour PS4 8/9, 360 pour PS4 10 et 387 pour PS5.

**FAIT :** `lib/src/session.c:252-256` crée l’identifiant d’appareil de 32
octets par session : préfixe de dix octets
`00 18 00 00 00 07 00 40 00 80`, seize octets issus de
`chiaki_random_bytes_crypt`, puis six octets nuls. Ce n’est pas une sortie de
l’enregistrement. GPSx le génère dans `gpsx-crypto` avec un `CryptoRng` injecté.

**FAIT :** le helper de réception amont peut renvoyer des octets après le
premier `\r\n\r\n` ; `lib/src/ctrl.c:1469-1472` les conserve comme début du
premier message ctrl. Le parseur renvoie donc la longueur exacte de l’en-tête
et laisse tous les octets suivants non interprétés et inchangés.

**HYPOTHÈSE :** reproduire le comportement épinglé de la requête et de la
réponse TCP locales suffit à cette étape de l’interopérabilité console. Aucune
console ne l’a testé. Un résultat émulateur ne prouverait rien pour une console
ou le GP520.

## Périmètre et exclusions explicites

Le périmètre d’implémentation est volontairement limité à :

- une requête de négociation du contrôle TCP local ;
- un en-tête HTTP borné en conservant les octets suivants déjà lus ;
- un DID par session généré depuis un CSPRNG injecté ;
- la sélection exacte du profil cible et du codec ;
- des compteurs session-rpcrypt local et distant monotones ;
- un propriétaire cryptographique opaque après négociation pour la tranche
  revue suivante.

Sont exclus : sockets, DNS, IPv6, choix du port, nouvelles tentatives,
temporisations, threads, files, RUDP et connexion distante PSN, trames
continues de messages ctrl, identifiant de session, PIN de connexion, heartbeat,
clavier, microphone, messages d’affichage, veille/accueil, Takion, protobuf,
média, retour manette, adaptateurs Android/Windows, logs, télémétrie, stockage et
accès matériel. Une nouvelle tentative de transport doit renvoyer exactement
les octets déjà produits ; elle ne doit pas rappeler le chiffrement ni la
préparation de requête.

## Frontière de sources et de licence

Les seules sources comportementales proposées sont :

- `lib/include/chiaki/common.h`, limité aux quatre profils RP concrets et aux
  prédicats de cible ;
- `lib/include/chiaki/session.h` pour la taille de 32 octets de l’identifiant
  d’appareil ;
- `lib/src/session.c:58-72` pour la correspondance cible/version RP,
  `lib/src/session.c:252-256` pour le DID et
  `lib/src/session.c:522-598,682` pour l’ordre ctrl-avant-flux ;
- `lib/src/ctrl.c:1125-1154,1195-1309,1424-1472` pour la disposition statique,
  les compteurs, la réponse, la classification, la conservation des octets
  suivants et l’ajustement du profil ;
- le comportement session-rpcrypt déjà intégré depuis `lib/src/rpcrypt.c` ;
- RFC 4648 pour le base64 de production écrit par GPSx.

Tous les chemins chiaki-ng utilisent le commit épinglé et
`LicenseRef-AGPL-3.0-only-OpenSSL`, acceptés avec l’option C. GPSx ne copiera
pas le formateur C, le parseur, le base64, le réseau, les logs, les threads, les
files ni la logique de nouvelle tentative. L’oracle externe doit compiler et
exécuter `http.c` et `base64.c` épinglés et consigner séparément le diagnostic
du décodeur seul et le verdict du chemin ctrl amont. Ces fichiers sont exécutés
uniquement par l’oracle, pas portés en production. Le formatage de requête reste
une transcription GPSx marquée `compiled=false, executed=false` ; la
transcription étiquetée de la boucle `ctrl.c:1138-1153` est compilée et exécutée
avec `strlen(value) + 1` et un buffer de sortie de 16 octets.

## Propriété et API proposées

L’implémentation reste dans `gpsx-crypto`, portable, dans un module privé
`control_handshake`. Conserver formatage, analyse et chiffrement derrière une
frontière unique évite que les clés d’enregistrement, identifiants d’appareil,
matériau rpcrypt ou compteurs mutables traversent les crates. La crate conserve
`#![forbid(unsafe_code)]` et n’effectue aucune E/S.

La surface publique revue devra être équivalente à :

```text
ControlProfile::{Ps4_8, Ps4_9, Ps4_10, Ps5}
ControlEndpoint::local_ipv4(Ipv4Addr) -> ControlEndpoint
ControlRegistrationKey::take_from_registration_buffer(
    &mut Zeroizing<[u8; 16]>,
) -> ControlRegistrationKey
SessionRpcrypt::begin_control(
    &mut self,
    ControlProfile,
    &mut (impl CryptoRng + RngCore),
) -> Result<ControlHandshake<'_>>
ControlHandshake::write_request(
    &mut self,
    ControlEndpoint,
    &ControlRegistrationKey,
    SessionCodec,
    &mut [u8],
) -> Result<ControlRequestLength>
ControlHandshake::finish(self, &[u8])
  -> Result<(ControlHandshakeResponse, ControlHeaderLength)>
HandshakeSession<AwaitingPeer>::write_encrypted_launch_spec(
    &mut self,
    &mut SessionRpcrypt,
    LaunchSpec,
    &mut [u8],
) -> Result<EncryptedLaunchSpecLength>
SessionRpcrypt::into_control_channel(self) -> Result<ControlChannelCrypto>
```

Les noms pourront changer, mais pas ces invariants :

1. `SessionRpcrypt` possède deux slots à usage unique indépendants, ctrl et
   LaunchSpec. `begin_control` l’emprunte de façon mutable (`&mut`), ouvre une
   fois l’espace ctrl et n’exige ni ne consomme le slot LaunchSpec. Un échec RNG ne produit ni
   DID ni requête et n’ouvre ou n’avance aucun slot.
2. `finish` inscrit le type de serveur classé dans le propriétaire emprunté puis
   termine l’emprunt ctrl. LaunchSpec exige ensuite une réponse ctrl achevée et
   valide cible, codec, largeur et hauteur contre cette classification.
   `into_control_channel` consomme le propriétaire et exige ctrl et LaunchSpec
   achevés : ctrl → LaunchSpec → cryptographie de canal.
3. **Politique GPSx fail-closed / divergence volontaire au risque de
   compatibilité INCONNU :** le type connu `0` (PS4 de base) autorise seulement
   un profil PS4, AVC, `width <= 1280` et `height <= 720`. Le type `1` (PS4 Pro)
   autorise seulement un profil PS4 et AVC. Le type `2` autorise seulement PS5
   et les choix AVC/HEVC/HEVC-HDR revus. Un premier octet déchiffré `>= 3` est
   une erreur. Un type absent ou court accepté est `Unknown` et conserve la
   cible et le codec compatibles déjà choisis. En amont, seule une hauteur
   exactement 1080 est abaissée lorsque `video_profile_auto_downgrade` est
   actif, H.264 est imposé sans condition aux types 0/1, et le type 2 ne
   déclenche aucune action. Tout rejet de classification est atomique : la
   sortie reste inchangée et le slot LaunchSpec reste disponible pour une
   nouvelle tentative compatible.
4. `ControlProfile` distingue PS4 8 et PS4 9, bien qu’ils correspondent tous
   deux aux tables `SessionTarget::Ps4Pre10`. `begin_control` rejette un profil
   dont la cible cryptographique diffère de celle du propriétaire emprunté.
5. Le DID est créé dans chaque `begin_control` avec le préfixe fixe, exactement
   16 octets CSPRNG et six zéros. Aucun constructeur ou DID brut n’est public.
   La clé d’enregistrement est copiée une fois depuis le stockage `Zeroizing`
   appelant, immédiatement effacé.
6. Le propriétaire de clé, rpcrypt, les états de négociation/canal, le DID et
   les temporaires clair/flot ne sont ni `Clone` ni `Copy`, sont expurgés et
   effacés. Le point de terminaison est une adresse IPv4 numérique avec le port local
   fixe 9295. Aucune chaîne de l’appelant ne peut entrer dans la requête, ce qui
   empêche l’injection de ligne ou d’en-tête.
7. `write_request` emprunte le propriétaire de clé d’enregistrement et valide
   profil, codec, point de terminaison, capacité de sortie et toute
   l’arithmétique avant chiffrement. La sortie et les compteurs restent
   inchangés sur toute erreur, et le même propriétaire de clé reste disponible
   pour une nouvelle tentative sans relire le stockage sécurisé. Un succès
   écrit la requête complète,
   valide le prochain compteur local exact (`3`, `4` ou `5`) et ferme
   définitivement la préparation pour ce propriétaire.
8. `finish` consomme la négociation et analyse seulement jusqu’au premier
   `\r\n\r\n`. L’en-tête lui-même mesure au plus 512 octets, 32 lignes et 256
   octets par ligne. L’entrée peut contenir autant d’octets suivants que
   nécessaire : ils ne comptent pas dans la borne, ne sont ni analysés,
   rejetés, copiés ou modifiés. `ControlHeaderLength` donne leur offset exact.
9. Le parseur rejette LF seul, CR seul, un NUL avant le terminateur d’en-tête,
   obs-fold, l’espace avant deux-points, les noms/valeurs invalides et les
   en-têtes connus mal formés. Les en-têtes
   sensibles sont comparés sans tenir compte de la casse
   pour détecter les doublons, mais doivent porter leur casse canonique. Les
   doublons ou variantes de casse de `RP-Server-Type` et `RP-Prohibit` sont
   rejetés. Les en-têtes inconnus valides sont ignorés dans les mêmes limites.
10. Le statut est exactement `HTTP/1.1 200`, suivi directement de CRLF ou d’une
    espace, d’une raison dont le premier octet est VCHAR et dont les octets
    suivants sont VCHAR ou SP, puis CRLF. Ainsi
    `HTTP/1.1 200\r\n` et `HTTP/1.1 200 OK\r\n` sont valides.
11. `RP-Prohibit` n’accepte que `0` ou `1` canoniques ; toute autre valeur est
    rejetée au lieu d’hériter du comportement de `atoi` en C. C’est un
    durcissement volontaire du parseur, pas une revendication amont.
12. `RP-Server-Type` suit la politique base64 exacte ci-dessous. Une valeur de
    16 octets consomme une fois le compteur distant 0 ; une valeur courte
    acceptée ou absente ne le consomme pas.
13. `ControlChannelCrypto` n’expose ni clé brute, ni IV, ni compteur, ni méthode
    arbitraire de chiffrement/déchiffrement, ni sérialiseur, ni construction à
    partir d’octets, ni `Clone`. Ses prochains compteurs ne seront accessibles
    qu’à une future API de messages ctrl revue.
14. Les erreurs ne contiennent que des classes publiques et positions
    d’octets. Elles ne contiennent jamais identifiants, valeurs d’en-tête,
    chiffré, clair, octets dérivés, adresse hôte ou fragment de réponse.

## Contrat de requête canonique

La requête est ASCII, uniquement CRLF, dans cet ordre :

```text
GET <chemin cible> HTTP/1.1
Host: <IPv4 numérique>:9295
User-Agent: remoteplay Windows
Connection: keep-alive
Content-Length: 0
RP-Auth: <base64 de la clé d’enregistrement chiffrée sur 16 octets>
RP-Version: <version cible>
RP-Did: <base64 du DID par session chiffré sur 32 octets>
RP-ControllerType: 3
RP-ClientType: 11
RP-OSType: <base64 de "Win10.0.0\0" chiffré>
RP-ConPath: 1
[RP-StartBitrate: <base64 du zéro LE32 chiffré>]
[RP-StreamingType: <base64 de la valeur codec LE32 chiffrée>]

```

Le formateur utilise un buffer temporaire fixe effaçable de 512 octets et
renvoie une longueur excluant tout terminateur de commodité. La sortie doit
avoir au moins la longueur exacte ; les octets après la plage retournée ne sont
pas modifiés.

Le décodage de production n’accepte qu’une écriture RFC 4648 canonique. Le
remplissage et ses bits nuls sont obligatoires lorsque la RFC l’exige. Une
longueur décodée de 16 octets est déchiffrée et classée. Les longueurs courtes canoniques avec
remplissage acceptées en amont, `1, 2, 4, 5, 7, 8, 10, 11, 13, 14`, deviennent
`Unknown` sans consommation du compteur. Toute autre longueur, écriture sans
remplissage, nouvelle ligne, espace, octet hors alphabet ou donnée après `=`
est une erreur. L’oracle couvre les longueurs 1 à 19 avec et sans remplissage,
plus des données après `=`.

**FAIT / divergences GPSx volontaires au risque de compatibilité INCONNU :** le
parseur amont compare la casse des noms (donc ignore les variantes), considère
l’espace avant `:` comme appartenant à un autre nom, préfixe les doublons et
inverse donc leur ordre, puis retire une seule espace après `:`. Son `strtol`
accepte notamment `0200`, une espace initiale et une raison absente. Son
parseur accepte LF seul et CR seul comme fins de ligne, et s’arrête
silencieusement à NUL. Son décodeur base64 saute seulement la nouvelle ligne, reçoit
`strlen + 1` NUL inclus, accepte des bits de remplissage non nuls comme `AB==`,
s’arrête à `=` et ignore les données suivantes. GPSx applique les règles
strictes ci-dessus. Il rejette aussi la tabulation horizontale ou l’absence
d’espace après les deux-points, les valeurs d’en-tête vides et les champs
`RP-Server-Type` ou `RP-Prohibit` dupliqués. Ces choix de validation stricte ont
des tests dédiés et restent des risques d’interopérabilité tant qu’aucune
console ne les a validés.

## Contrat des compteurs et risque résiduel

La requête TCP locale consomme exactement :

| Profil | Compteur 0 | Compteur 1 | Compteur 2 | Compteur 3 | Compteur 4 | Prochain local |
|---|---|---|---|---|---|---:|
| PS4 8/9 | auth | identifiant | type OS | — | — | 3 |
| PS4 10 | auth | identifiant | type OS | débit | — | 4 |
| PS5 | auth | identifiant | type OS | débit | type de flux | 5 |

La réponse ne consomme le compteur distant 0 que pour un
`RP-Server-Type` décodé valide de 16 octets. Une expiration du transport ne
modifie aucun compteur, car le transport renvoie les octets déjà préparés.

**FAIT :** les 16 premiers octets du clair LaunchSpec sont toujours
`{"sessionId":"se`. Sa construction CFB-sur-zéros et les deux directions ctrl
réutilisent le premier bloc de flot session-rpcrypt au compteur zéro. Un
observateur voyant le chiffré LaunchSpec peut donc récupérer ce bloc de flot et
révéler le premier bloc clair ctrl : la clé d’enregistrement `RP-Auth` entière
et le bloc du type de serveur.

**INFÉRENCE :** la clé d’enregistrement est déjà envoyée en hexadécimal clair
dans la requête de session antérieure ; cette récupération ne crée donc
probablement pas une nouvelle exposition pour elle. **INCONNU :** le chiffré
LaunchSpec est-il visible par un observateur LAN ? Ce plan n’a pas vérifié la
protection Takion. Le typage empêche les usages supplémentaires dans un
propriétaire, sans pouvoir réparer la réutilisation imposée par le protocole.
Il ne peut pas non plus empêcher une reconstruction depuis des entrées copiées.

## Oracle fermé requis

L’implémentation exige une fixture suivie
`tests/fixtures/control_handshake_oracle_v1.json`, générée hors dépôt avec un
relevé de construction fermé. Elle ne contiendra que des entrées synthétiques
publiques et couvrira :

- PS4 8, PS4 9, PS4 10 et PS5 en AVC, plus PS5 HEVC et HEVC HDR ;
- des clés d’enregistrement, octets CSPRNG injectés du DID, nonces et valeurs
  `morning` distincts discriminant chaque cas et compteur ;
- chaque champ clair, compteur, IV, chiffré, valeur base64, requête complète,
  longueur et prochain compteur local ;
- des réponses pour les types 0, 1 et 2, les deux valeurs d’interdiction, un
  type absent, toutes les longueurs décodées 1 à 19 avec/sans remplissage et
  des données après `=` ;
- le bloc type-de-serveur déchiffré de 16 octets, son premier octet classé et le
  prochain compteur distant exact.

Pour chaque vecteur de réponse, le relevé externe compile et exécute `http.c`
et `base64.c` épinglés ; la fixture sépare le diagnostic du décodeur seul du
succès ctrl amont, de la validité du type serveur et de prohibit. Il marque le
formatage GPSx `compiled=false, executed=false` et la transcription étiquetée
de la boucle `ctrl.c:1138-1153` `compiled=true, executed=true`. Il hache chaque
entrée, unité de traduction,
produit, commande, checkout, patch, harnais, sortie brute et fixture. Harnais,
journaux, checkouts, produits et manifeste restent ignorés sous
`target/control-handshake-audit/`.

Rust doit reconstruire le document JSON canonique complet et exiger l’égalité
exacte des octets. Chaque cas doit aussi appeler séparément le chemin public de
requête et comparer longueur et requête complète. Les mutations couvrent tous
les champs, compteurs, profils, codecs, ordre des en-têtes, CRLF, NUL,
remplissage base64, longueur, classification de réponse, interdiction, prochain
compteur, cas supplémentaire et octets après l’accolade JSON finale.

## Gate de test et d’implémentation

Avant intégration, sont requis :

1. Re-check indépendant ciblé et `GO` sur D1–D4 dans les deux langues, la
   frontière de sources et les trois chemins nouvellement inventoriés.
2. Tests de requête exacte des six cas, capacités exacte et inférieure d’un
   octet, et atomicité de toute erreur.
3. Tests d’état imposant ctrl → LaunchSpec → cryptographie de canal ; frontières
   720/721 et 1280/1281 ; type 2 avec LaunchSpec PS4 ; premiers octets 3 et 255 ;
   réussite compatible sur le même propriétaire après rejet atomique ; mutants
   retirant ou relâchant chaque règle d’ordre/classification.
4. Tests de nouvelle tentative prouvant que les mêmes octets peuvent être
   envoyés deux fois sans nouveau chiffrement ni changement de compteur.
5. Tests du DID pour le préfixe/aléa/suffixe exacts, l’échec RNG sans sortie ni
   changement d’état et un mutant du préfixe tué.
6. Tests du parseur avec en-tête plus 1..N octets arbitraires, longueur exacte,
   chaque borne/divergence, LF seul, CR seul, NUL interne, raisons multi-mots,
   longueurs 1..19 avec/sans remplissage, bits de remplissage non nuls, données
   après `=`, entrées arbitraires déterministes et fuzzing prouvant qu’une
   longueur d’en-tête réussie finit au premier terminateur et exclut la suite.
   Des vecteurs d’oracle correspondants consignent le verdict amont.
7. Inspection et tests d’expurgation/effacement des identifiants, temporaires,
   état de canal, erreurs, `Debug` et `Display`.
8. ProvenanceGuard, scan de secrets, manifeste trié, relevé fermé exécutant
   `http.c` et `base64.c` pour chaque réponse, et recalcul indépendant.
9. `cargo +1.85.0 fmt --all --check`, Clippy sans avertissement,
   `cargo +1.85.0 test --workspace --locked`, compilation croisée Android,
   `cargo audit`, `cargo deny check` et inspection des doublons.
10. Contre-revue indépendante de l’implémentation avec résultats des mutants
    avant de marquer la tranche intégrée.

N1, le bundle complet de licences tierces, reste obligatoire avant toute
distribution binaire. L’interopérabilité console et les performances GP520
restent `INCONNU / NON TESTÉ` ; la preuve matérielle est `AUCUNE`.
