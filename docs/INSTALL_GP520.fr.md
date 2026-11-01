# Installer et tester GPSx sur le GP520

Statut : canal de test ouvert par le propriétaire du projet le 2026-10-31.

## 1. Installer avec Downloader

1. Sur le GP520, installez **Downloader** (AFTVnews) depuis le Google Play Store.
2. Paramètres → Confidentialité et sécurité → Sources inconnues (le libellé
   varie) : autorisez **Downloader** à installer des applications.
3. Dans Downloader, saisissez :
   `https://github.com/xalacaga/GPSx/releases/latest/download/gpsx.apk`
4. Confirmez l'installation Android, puis ouvrez **GPSx**.

L'APK est signé avec la clé release de GPSx (certificat SHA-256
`4D:04:2D:1D:08:60:0F:6A:8B:48:98:21:B6:54:7A:C1:3A:71:38:0E:9F:2F:70:B2:27:BA:58:72:F6:2B:E2:90`).
Chaque version suivante doit porter la même signature.

## 2. Mettre à jour depuis GPSx

Paramètres → **Mettre à jour GPSx** → *Rechercher une mise à jour* → *Installer*.
GPSx lit la dernière release GitHub publique, vérifie le SHA-256 de l'APK, le nom
de paquet et le certificat de signature, puis Android demande confirmation. La
première fois, Android peut demander d'autoriser GPSx comme source d'installation.

## 3. Réglages conseillés du projecteur et du réseau

- Pas de son (par exemple avec une barre de son Bluetooth) : GPSx passe
  automatiquement d'AAudio à OpenSL ES si la sortie reste muette ; Paramètres →
  **Sortie audio → Compatibilité** force OpenSL ES dès le départ. L'affichage du
  stream indique l'API audio utilisée et la sortie qu'elle signale (Bluetooth,
  HDMI, ou « système » quand OpenSL ES ne la précise pas).
- Casque, enceinte ou barre de son Bluetooth : GPSx joue via OpenSL ES en usage
  média, le chemin des applis vidéo (un flux AAudio vers une barre de son Bose
  restait inaudible sur le GP520). À chaque connexion ou déconnexion, le flux se
  rouvre en une seconde environ. Le Bluetooth ajoute son propre retard ; une
  liaison HDMI ARC ou optique l'évite. Si
  l'affichage indique toujours HDMI, lancez la session une fois l'appareil
  Bluetooth connecté et envoyez un rapport de test.

- Format vidéo : le décodeur est créé au premier paquet vidéo et suit le codec
  réellement envoyé par la console ou le serveur cloud (H.264 ou H.265), quel que
  soit celui demandé ; l'affichage du stream l'indique à côté de la résolution.
- Image figée avec le son du jeu (vu sur les jeux cloud PS4, en H.264) :
  Paramètres → **Mode faible latence → DÉSACTIVÉ** lance la session suivante sans
  les options faible latence du décodeur. Le compteur *affichées* de l'affichage
  du stream indique si les images arrivent encore à l'écran.
- Mode d'image **Jeu** et **Game Boost** activés.
- Wi-Fi 5 GHz ou 6 GHz, PS5 en Ethernet sur la box si possible, projecteur en
  vue du point d'accès. Voir `STREAM_TUNING.fr.md`.

## 4. Faire un test

1. Associez une fois la PS5 dans **Session** (code à 8 chiffres et PSN Account ID).
2. Appuyez sur **Démarrer le stream** (GPSx réveille la PS5 si besoin) et jouez
   au moins 2 minutes ; 10 minutes donnent une comparaison plus utile.
3. Appuyez sur Retour pour arrêter. Chaque session de 5 s ou plus écrit un rapport.

## 5. Envoyer les résultats sur GitHub (QR code, sans jeton)

Les résultats vont dans le dépôt `xalacaga/GPSx-diagnostics`, séparé du dépôt
source. Il est **public** pour l'instant par choix du propriétaire : tout le
monde peut lire les rapports, qui ne contiennent que les données listées
ci-dessous.

1. GPSx → **Diagnostics** → *Résultats de tests* : choisissez un rapport.
2. Scannez le QR code avec un téléphone déjà connecté à github.com dans son
   navigateur. Il ouvre un nouveau ticket dans `xalacaga/GPSx-diagnostics`,
   prérempli avec le rapport et l'étiquette `report`. L'application GitHub perd
   le formulaire prérempli : sur iPhone, ouvrez le lien dans Safari (appui long,
   ou lien `github.com` en haut à droite une fois l'application ouverte). Une
   connexion en cours de route a déjà renvoyé une erreur HTTP 500.
3. Appuyez sur **Submit new issue**.
4. *Effacer tous les rapports* (deux appuis pour confirmer) supprime tous les
   rapports enregistrés, pour ne garder que les nouveaux tests. La version
   installée est affichée au-dessus de la liste.

Aucun identifiant n'est stocké sur le GP520 ni dans l'APK. Le fichier du QR code
est un résumé texte compact (statistiques, API et compteurs audio, jusqu'à 30 échantillons répartis) ;
le rapport JSON complet reste sur l'appareil. Les rapports contiennent uniquement
des mesures, le modèle de l'appareil, la version d'Android, la bande/le lien/le
RSSI Wi-Fi et l'état du son : ni SSID, ni BSSID, ni adresse IP, ni nom de
console, ni clé d'enregistrement, ni jeton PSN.

## 6. Connexion cloud (QR code, Wi-Fi local)

Le téléphone doit être sur le même réseau que la TV. Cloud → **Se connecter avec
le téléphone (Wi-Fi local)** affiche un QR code qui ouvre une page servie par la
TV elle-même. Sur le téléphone, connectez-vous sur playstation.com, ouvrez
`https://ca.account.sony.com/api/v1/ssocookie`, copiez la valeur `npsso` et
collez-la dans la page de la TV. Aucun serveur tiers : le jeton traverse une fois
le réseau local, en HTTP non chiffré, vers une adresse aléatoire à usage unique.
La page accepte un seul jeton puis s'arrête ; elle s'arrête aussi au bout de
10 minutes. GPSx chiffre le jeton sur l'appareil et ne le journalise jamais.

Secours : **Secours : via xbgamestream.com** (le relais Pylux, qui reçoit le jeton
pour le transmettre à la TV) et la saisie manuelle du NPSSO. Sur l'émulateur
Android, le téléphone ne peut pas joindre la page de la TV (réseau virtuel) :
utilisez-y un secours.

## Format des rapports

`gpsx-stream-test/1` : version de l'app et révision source, appareil, profil
vidéo et codec demandés, classe réseau, début/fin, motif de fin, résumés
p50/p95/p99 après 5 s de chauffe, et un échantillon par seconde
`[t, fps, bitrateMbps, rttMs, packetLoss, framesLost, width, height]`.
