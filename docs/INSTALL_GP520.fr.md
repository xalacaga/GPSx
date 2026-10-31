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

- Mode d'image **Jeu** et **Game Boost** activés.
- Wi-Fi 5 GHz ou 6 GHz, PS5 en Ethernet sur la box si possible, projecteur en
  vue du point d'accès. Voir `STREAM_TUNING.fr.md`.

## 4. Faire un test

1. Associez une fois la PS5 dans **Session** (code à 8 chiffres et PSN Account ID).
2. Appuyez sur **Démarrer le stream** (GPSx réveille la PS5 si besoin) et jouez
   au moins 2 minutes ; 10 minutes donnent une comparaison plus utile.
3. Appuyez sur Retour pour arrêter. Chaque session de 5 s ou plus écrit un rapport.

## 5. Envoyer les résultats sur GitHub

Les résultats vont dans le dépôt privé `xalacaga/GPSx-diagnostics`, jamais dans
le dépôt source public.

1. Sur github.com : Settings → Developer settings → Fine-grained tokens →
   *Generate new token* ; accès **Only select repositories → GPSx-diagnostics** ;
   permission **Contents : Read and write** ; expiration 30 jours.
2. Dans GPSx → **Diagnostics** → *Résultats de tests* : gardez le dépôt
   `xalacaga/GPSx-diagnostics`, collez le jeton (l'application Google TV du
   téléphone permet de coller du texte) et appuyez sur **Enregistrer**.
3. Appuyez sur **Envoyer les résultats sur GitHub**. Chaque rapport arrive dans
   `results/AAAA/MM/<date>-<type>.json`.

Le jeton est chiffré par Android Keystore, jamais journalisé ni exporté, et
supprimable avec **Supprimer le jeton**. Les rapports contiennent uniquement des
mesures, le modèle de l'appareil, la version d'Android et la bande/le lien/le
RSSI Wi-Fi : ni SSID, ni BSSID, ni adresse IP, ni nom de console, ni clé
d'enregistrement, ni jeton PSN.

## Format des rapports

`gpsx-stream-test/1` : version de l'app et révision source, appareil, profil
vidéo et codec demandés, classe réseau, début/fin, motif de fin, résumés
p50/p95/p99 après 5 s de chauffe, et un échantillon par seconde
`[t, fps, bitrateMbps, rttMs, packetLoss, framesLost, width, height]`.
