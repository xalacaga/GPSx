# Canal de mise à jour GPSx

## Première installation

Comportement prévu, encore `NOT_TESTED` : la première installation sur le GP520
utilisera l’application Downloader déjà disponible sur Google TV. Downloader
ouvrira une URL stable de Release GPSx, téléchargera l’APK puis le remettra à
l’installateur Android. L’utilisateur autorisera explicitement cette source et l’installation.

Le premier APK doit déjà utiliser la clé de signature de publication définitive.
Tous les APK suivants conservent le même identifiant d’application et la même
signature, avec un `versionCode` supérieur ; sinon Android refuse la mise à jour.

Une Release GitHub publique est une distribution selon la stratégie AGPL acceptée :
le code source correspondant exact, la licence, la permission OpenSSL, les scripts
de build et les mentions doivent être disponibles simultanément. Un dépôt privé
se prête mal à une première installation par Downloader, car ses assets exigent
normalement des requêtes authentifiées.

## Mise à jour dans l’application

Réglages expose l’action `Rechercher une mise à jour GPSx`. L’updater :

1. consulte le canal de Releases GitHub configuré ;
2. compare les informations version/build du manifeste de Release avec le paquet installé ;
3. télécharge l’APK dans un stockage temporaire privé à l’application ;
4. vérifie schéma, SHA-256, nom de paquet attendu et canal ;
5. lit le certificat de signature de l’APK et refuse toute empreinte SHA-256 différente
   du certificat de publication épinglé dans l’application installée ;
6. demande à l’installateur Android de vérifier la signature et d’installer ;
7. conserve l’application actuelle si téléchargement, validation ou installation échoue.

Une application Android TV ordinaire ne peut pas installer silencieusement une
mise à jour. L’utilisateur confirme chaque installation et peut devoir autoriser
GPSx comme source d’applications inconnues. La politique réelle du GP520 reste
`NOT_TESTED` jusqu’à l’ouverture du gate matériel.

## Règles de sécurité

- Aucune clé de signature ni identifiant de dépôt n’est intégré à l’APK ou au dépôt.
- La clé de publication est créée et sauvegardée hors ligne. Les APK de Release
  utilisent Android APK Signature Scheme v2 et v3. La clé privée n’entre ni dans
  le dépôt ni dans des secrets GitHub Actions ordinaires ; toute signature distante
  future exige une approbation CR6.
- Aucun APK signé avec la clé debug n’est publié. La CI Release échoue si l’empreinte
  du certificat diffère de l’empreinte de production configurée séparément.
- Les Releases publiques ne demandent aucun token. Un canal privé utilise un
  identifiant révocable limité à la lecture d’un dépôt, protégé par Android Keystore.
- Les redirections sont limitées aux hôtes GitHub HTTPS approuvés ; TLS reste validé.
- Une somme publiée à côté d’un APK contrôle son intégrité, pas l’identité du
  producteur. L’authenticité vient du certificat épinglé indépendamment et de la
  règle Android imposant le même signataire pour une mise à jour.
- Le manifeste n’est pas qualifié de signé sans clé hors ligne, format et rotation
  approuvés. Initialement, c’est une métadonnée validée par schéma dans un flux qui
  authentifie l’APK et son certificat.
- Aucun code téléchargé n’est exécuté avant vérification et installation par Android.
- Rétrogradations et changements de canal exigent une procédure de récupération explicite.

Le stockage d’authentification et la chaîne de mise à jour exigent une contre-revue
sécurité Claude Code supplémentaire avant implémentation, selon `CROSS_REVIEW.fr.md`.
