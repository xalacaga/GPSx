# Plan du jeu cloud PlayStation Plus

Statut : **IMPLÉMENTÉ SUR ÉMULATEUR — streaming cloud NOT_TESTED** (2026-10-31).

## Avancement (2026-10-31)

- FAIT : la bibliothèque native Pylux `ca87a49` est construite pour x86_64 avec
  le NDK 28.2 et les deux correctifs locaux consignés dans `PROVENANCE.fr.md` ;
  elle est autonome (bibliothèques système uniquement) et embarquée dans l'APK
  debug de GPSx.
- FAIT : `ChiakiCompat.kt`, `RemotePlayEngine.startCloud`, `CloudPlayController`,
  `PsnTokenStore`, `CloudCatalog` et l'écran Compose **Cloud** sont en place.
  21 tests unitaires JVM passent, dont l'analyse du catalogue, la validation du
  NPSSO et une vérification par réflexion de chaque champ de `ConnectInfo` lu
  par le code natif.
- FAIT : sur l'émulateur Google TV API 36, l'écran Cloud s'ouvre et le moteur
  Pylux crée une session Remote Play console (décodeur H.264 configuré, requête
  de session envoyée). La PS5 n'a pas répondu dans le délai ; le stream console
  lui-même n'a donc pas été revérifié.
- FAIT : la page de connexion Sony reste blanche dans la WebView intégrée : son
  script anti-bot y échoue. GPSx ne contourne pas cette protection ; la saisie
  manuelle du NPSSO est donc le chemin de connexion supporté, la page intégrée
  restant une tentative qui peut fonctionner sur un vrai appareil.
- NOT_TESTED : récupération du catalogue, réservation de serveur et stream cloud
  réel — il faut le jeton NPSSO PlayStation Plus Premium du propriétaire.
- FAIT : les builds natifs optimisés arm64-v8a, armeabi-v7a, x86 et x86_64 sont
  embarqués (voir `STREAM_TUNING.fr.md`).

## Objectif

Permettre à un abonné PlayStation Plus Premium de streamer les jeux du
catalogue cloud (titres PS5 « pscloud » et titres façon PS Now « psnow ») sur
Android TV, à côté du Remote Play console existant. Aucune console n'est
nécessaire pour une session cloud.

## Base amont

FAIT : Pylux (`ForWard-Technologies-LLC/Pylux`, AGPL-3.0 avec l'exception
OpenSSL, construit sur chiaki-ng) implémente le streaming cloud dans sa
bibliothèque C partagée :

- `lib/src/cloudcatalog_*.c` : récupération, fusion et cache du catalogue ;
- `lib/src/cloudsession*.c` : résolution des droits Kamaji, allocation de
  serveur Gaikai, ping et choix du datacenter ;
- `lib/src/pscloud_audio_reassembler.c` et le `session.c` étendu : la variante
  cloud de la session de stream.

Son JNI Android expose `cloudCatalogFetchUnified`, `cloudProvisionSession` et un
`ConnectInfo` étendu avec les champs cloud (launch spec, clé de handshake, ID de
session, port, type d'enveloppe PSN, MTU, RTT). Révision épinglée :
`ca87a49a12bab071948f565ca53ffdad790775d3`.

DÉCISION : le moteur natif de GPSx passe de chiaki-ng `c8bea264` à Pylux
`ca87a49`. Pylux est un sur-ensemble de chiaki-ng avec le même paquet JNI
`com.metallic.chiaki.lib` ; le Remote Play console continue donc de fonctionner.
`ChiakiCompat.kt` doit être aligné sur le `ConnectInfo` de Pylux dans le même
changement que les bibliothèques natives, car le code natif cherche ces champs
par leur nom.

## Ce que GPSx fait autrement

- **La connexion PSN reste entre l'appareil et Sony.** Sur TV, Pylux renvoie
  l'utilisateur vers `xbgamestream.com`, qui rend le jeton NPSSO, et journalise
  le corps de la réponse. GPSx n'utilise pas ce relais : il se connecte via une
  WebView intégrée sur la page de connexion de Sony et lit le cookie `npsso` de
  `ca.account.sony.com`. La saisie manuelle du NPSSO sert de secours.
- Le jeton NPSSO est chiffré avec une clé Android Keystore, jamais journalisé,
  jamais exporté dans les diagnostics, et effaçable depuis les Réglages.
- Aucun QR code généré par un service tiers.
- L'interface reste le shell TV Compose existant : une nouvelle destination
  **Cloud** avec connexion, grille du catalogue, fiche de jeu et action « Jouer ».

## Tâches

1. Construire les bibliothèques natives Pylux (x86_64 pour l'émulateur,
   arm64-v8a et armeabi-v7a pour les appareils) depuis le commit épinglé ;
   consigner le build dans `PROVENANCE.fr.md`.
2. Pointer `chiakiPrebuiltDir` vers la sortie Pylux et mettre à jour
   `ChiakiCompat.kt` (déclarations JNI cloud, `ConnectInfo` étendu).
3. Relancer les tests unitaires et d'instrumentation existants ; le Remote Play
   console doit toujours s'enregistrer et se connecter.
4. `PsnAccount` : connexion WebView, stockage du NPSSO chiffré par Keystore,
   déconnexion.
5. `CloudCatalog` : récupération via `cloudCatalogFetchUnified`, cache dans le
   répertoire cache de l'application, analyse du contrat JSON avec des tailles
   bornées.
6. `CloudSessionStarter` : `cloudProvisionSession` sur un worker avec
   progression et annulation, puis démarrage d'une `Session` avec les champs
   cloud de `ConnectInfo`, en réutilisant le chemin Surface, audio et manettes.
7. Écrans Compose et réglages (résolution, débit, datacenter, langue).
8. Tests : analyse du JSON du catalogue, stockage du jeton (jamais en clair sur
   disque), conversion du résultat de provisioning, navigation UI.

## Risques et inconnues

- UNKNOWN : Sony peut changer les endpoints Kamaji/Gaikai ; Pylux les suit.
- HYPOTHESIS : un accès cloud non officiel peut enfreindre les conditions du
  PlayStation Network. L'usage se fait aux risques du titulaire du compte ;
  c'est un blocage de release pour toute distribution publique.
- NOT_TESTED : tout ce qui précède tant que le build natif n'existe pas.
- Le gate matériel GP520 de `OFFLINE_FIRST.fr.md` est inchangé ; le travail
  cloud est d'abord validé sur l'émulateur.
