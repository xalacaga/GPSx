# Plan rétro-gaming

État : **R1 TERMINÉ, R2 FONCTIONNE SUR L'ÉMULATEUR** (01/11/2026). Rien n'est encore validé sur le GP520.

## Avancement (01/11/2026)

- FACT (émulateur, x86_64) : la ludothèque liste 3 systèmes et 1207 jeux du NAS
  du propriétaire en 1 s environ ; les jaquettes Neo Geo viennent de
  `gamelist.xml`, les jaquettes SNES du serveur de vignettes libretro (104 sur
  106 trouvées).
- FACT (émulateur) : Snes9x 2010 téléchargé depuis le serveur de build, chargé
  par `dlopen` depuis le stockage privé (`targetSdk 34`), a fait tourner ActRaiser
  (France) à 50,0 images/s (PAL) avec le son : 3904 images calculées, 3894
  affichées, 3 sous-alimentations audio (toutes au démarrage), 0 échantillon
  audio jeté. Un appui sur la croix directionnelle d'une manette a déplacé le
  curseur du menu du jeu. L'image est en 4:3 (1440×1080 sur un écran 1080p).
- FACT (émulateur) : FinalBurn Neo v1.0.0.03 refuse le romset Neo Geo du
  propriétaire : il exige `sp-s3.sp1` dans `neogeo.zip`, absent de ce romset plus
  ancien, même quand son réglage de BIOS en désigne un autre. FB Alpha 2012 Neo Geo
  (v0.2.97.29) lit ce même romset : 2020 Super Baseball a atteint son mode
  démonstration à 59,18 images/s. Le crédit est Select (Create), le départ Start
  (Options).
- FACT (émulateur) : FinalBurn Neo a fait tourner '96 Flag Rally du dossier
  arcade (320x240, 60 images/s) ; crédit (Select) et départ (Start) ont mené au
  choix de la voiture. La part des 951 jeux arcade acceptés par FinalBurn Neo est
  UNKNOWN : beaucoup sont des machines de jeu d'argent ou de quiz que seul MAME
  émule.
- Amiga (code en place, NOT_TESTED) : le dossier `amiga` du partage contient des
  images de disquettes TOSEC. Les disquettes d'une même version forment un seul
  jeu, et une seule version par titre est gardée (jeu complet d'abord, puis le
  moins de modifications, mauvais dumps en dernier). Un jeu sur plusieurs
  disquettes est copié avec une liste M3U et chaque disquette va dans son propre
  lecteur (`puae_floppy_multidrive`). Les Kickstart du propriétaire (fullset BIOS
  Recalbox) ont été copiés dans `amiga/bios` le 01/11/2026.
- NOT_TESTED : deux manettes physiques en même temps (le code donne à chaque
  manette son propre port joueur), le GP520, la SRAM entre deux parties.

## Objectif

Jouer aux jeux de vieilles consoles et d'arcade du propriétaire sur Android TV,
depuis une section **Rétro** de GPSx, avec le même aspect, les mêmes manettes et
la même navigation que les sections PlayStation. GPSx héberge lui-même des cores
d'émulation libretro (la même interface de core que RetroArch) ; il n'embarque
pas RetroArch et ne le lance pas.

## Décisions du propriétaire (01/11/2026)

- Les jeux viennent du partage NAS du propriétaire `\\192.168.1.230\Public\Retrogaming`.
  GPSx ne fournit, ne télécharge et ne référence jamais de ROM ni de BIOS.
- Il n'y aura aucun usage commercial. Les cores dont la licence interdit l'usage
  commercial (Snes9x, FinalBurn Neo, MAME 2003-Plus…) sont donc acceptables.
- Ces cores ne sont **jamais versionnés dans le dépôt ni intégrés à l'APK** :
  GPSx est sous AGPL-3.0-only et ne peut pas ajouter de restriction à ce qu'il
  distribue. L'application télécharge un core à la demande du propriétaire depuis
  le serveur de build officiel libretro, dans son stockage privé, comme RetroArch.

- Les systèmes rétro sont dans le menu **Consoles**, sous la PS5 : une carte par
  système trouvé sur le partage, et une carte ouvre la liste des jeux de ce
  système avec leurs jaquettes. L'accueil présente PS5, Cloud et Rétro côte à
  côte. Les images des consoles sont des dessins originaux de GPSx (ni photo, ni
  logo, ni marque).
- Les jaquettes absentes de `gamelist.xml` viennent du serveur de vignettes
  libretro (comme dans RetroArch). Elles appartiennent à leurs éditeurs et ne sont
  pas libres de droit : GPSx n'en fournit aucune ; la TV télécharge une jaquette
  pour afficher le jeu du propriétaire et la garde en cache.

## Contenu du partage (FACT, inventaire en lecture seule du 01/11/2026)

| Dossier | Contenu | Métadonnées |
|---|---|---|
| `SNES SFC French Best Set` | 106 jeux SNES zippés (noms No-Intro) | aucune ; noms tirés des fichiers |
| `neogeo` | 151 jeux rangés par genre, BIOS `neogeo.zip` présent | `gamelist.xml` (descriptions en français), images |
| `mame` | 951 jeux, surtout obscurs (poker, fléchettes, quiz), quelques disques CHD | `gamelist.xml`, images, vidéos |

L'organisation est celle d'EmulationStation (Recalbox/Batocera) : un dossier
par système, avec un `gamelist.xml` facultatif.

## Ordre des travaux

| Étape | Contenu | Critère de sortie |
|---|---|---|
| R0 | Ce plan | Plan rédigé en anglais et en français |
| R1 | **Ludothèque** : onglet Rétro, réglages NAS, parcours SMB, lecture des `gamelist.xml`, liste des jeux par système | L'émulateur liste les dossiers SNES et Neo Geo d'un partage de test ; tests JVM verts |
| R2 | **Hôte libretro natif** et installateur de cores ; SNES avec Snes9x 2010 | Un homebrew SNES tourne sur l'émulateur avec image, son et manette |
| R3 | Validation GP520 (seulement après ouverture du gate matériel par le propriétaire) | Régularité des images P50/P95/P99 et sous-alimentations audio mesurées sur le GP520 |
| R4 | Neo Geo et la partie jouable du dossier MAME avec FinalBurn Neo | `neogeo.zip` trouvé à côté du jeu ; un jeu Neo Geo tourne |
| R5 | Sauvegardes d'état, synchronisation SRAM, disposition des boutons par système | Sauvegarder et reprendre une partie après redémarrage de l'application |

## Architecture

Le découpage suit les règles d'architecture du projet : Kotlin gère l'interface, le cycle de vie, les
réglages, l'accès réseau aux fichiers et la capture des entrées ; l'adaptateur
natif gère le core, son thread, la vidéo et l'audio. Images et échantillons ne
traversent jamais JNI.

```
Onglet Rétro (Compose)
  └─ RetroLibrary (Kotlin) ── SmbRomSource (jcifs-ng) ── partage NAS
        │  jeu choisi → copie dans le cache de l'appli (décompressé pour les consoles)
        ▼
  RetroSession (Kotlin) ── JNI : load(core, jeu), start, pause, stop, état manette
        ▼
  retro_host (adaptateur natif, C++)
     thread du core : dlopen(core) → retro_run() au rythme du core
     vidéo : retro_video_refresh → ANativeWindow (copie logicielle, RGB565/XRGB8888)
     audio : retro_audio_sample_batch → anneau PCM borné → callback AAudio
     entrées : retro_input_state ← dernier état manette écrit par JNI à chaque changement
```

### Ludothèque (R1)

- Paquet `com.gpsx.tv.retro` : `RetroSystem` (dossier → système, core,
  extensions), `GameList` (lecture de `gamelist.xml`), `RomName` (nettoyage des
  noms No-Intro), `SmbRomSource`, `RetroLibrary` (détenteur d'état comme
  `CloudPlayController`).
- L'accès SMB utilise **jcifs-ng** (LGPL-2.1, compatible AGPL-3.0 en tant que
  bibliothèque séparée). Le partage est ouvert en invité ; si un mot de passe
  devient nécessaire, il est stocké comme le jeton PSN et jamais journalisé.
- Le système est reconnu au nom du dossier (`snes`, `SNES…`, `neogeo`, `mame`,
  `megadrive`, `gba`…), sans tenir compte de la casse ; les dossiers inconnus
  apparaissent comme « pas encore pris en charge ».
- La liste affiche nom, année, genre et joueurs depuis `gamelist.xml` s'il
  existe, sinon le nom de fichier nettoyé. Les images sont chargées à la demande
  par SMB et mises en cache.

### Compatibilité au fil des parties

- Chaque lancement note sur la TV si le core a accepté le jeu. Pour les jeux
  d'arcade, le chargement est strict : une erreur journalisée par le core pendant
  le chargement (une ROM manquante) compte comme un refus, même si le core
  afficherait ensuite son propre écran d'erreur. Un chargement qui fait planter
  l'application est retenu comme un refus au démarrage suivant.
- Les listes de jeux masquent les jeux refusés par défaut, avec un bouton pour
  les réafficher ; les cartes indiquent « Compatible » ou « Incompatible » une
  fois le jeu essayé. Les jeux ne sont jamais testés en masse (décision du
  propriétaire, 01/11/2026).

### Stockage sur la TV et commandes

- Chaque jeu est copié une fois depuis le partage dans le stockage de l'appli et
  gardé : il redémarre sans repasser par le NAS ; une copie est complète quand un
  marqueur nomme son fichier de jeu. Paramètres affiche la place occupée (jeux
  gardés, jaquettes, émulateurs, sauvegardes) et l'espace libre, avec un bouton
  d'effacement pour chacun (décision du propriétaire, 01/11/2026).
- La fiche du jeu et l'écran de chargement affichent un schéma des commandes du
  système : chaque bouton PlayStation, dessiné comme une touche, avec son action
  (SNES B/A/Y/X, Neo Geo A–D, boutons arcade 1–6, crédit et départ) et la façon de
  quitter le jeu.

### Hôte natif (R2)

- Un seul core à la fois, possédé par un thread natif dédié (les cores libretro
  sont mono-thread). États : `IDLE → LOADED → RUNNING ⇄ PAUSED → STOPPED`, plus
  `FAILED` avec une raison.
- Vidéo : l'image du core est copiée dans le tampon de l'`ANativeWindow`
  (`ANativeWindow_setBuffersGeometry` à la taille du core, le compositeur met à
  l'échelle de l'écran). Pas d'OpenGL en R2 ; les cores à rendu matériel (N64,
  PSP) sont hors périmètre.
- Audio : les échantillons du core sont rééchantillonnés linéairement à
  48 kHz dans un anneau borné de 4096 images stéréo (environ 85 ms). Le thread du
  core ne calcule une image que tant que l'anneau contient moins de 2048 images :
  l'horloge audio cadence le jeu et l'anneau ne dérive pas. Un débordement jette
  les images entrantes, une sous-alimentation joue du silence ; les deux sont
  comptés et journalisés à la fermeture du jeu. Sans sortie audio, le core est
  cadencé par sa propre fréquence d'images.
- Entrées : les événements manette Android déjà capturés pour le Remote Play sont
  traduits une fois en état de manette neutre, puis en RetroPad (Croix→B,
  Rond→A, Carré→Y, Triangle→X, L1/R1/L2/R2, Select/Start, croix directionnelle,
  sticks). JNI est appelé aux changements d'état, pas à chaque image. Chaque
  manette physique reçoit le port joueur libre suivant (jusqu'à 4) au premier
  bouton pressé : deux manettes jouent en joueur 1 et joueur 2. Un appui est
  mémorisé jusqu'à la lecture de l'image suivante, donc un appui plus court
  qu'une image n'est jamais perdu. Le stick gauche pilote aussi la croix
  directionnelle.
- Quitter un jeu : Retour, ou le même accord L1+R1+Options que le Remote Play.

### Cores

| Système | Core | Licence | Source |
|---|---|---|---|
| SNES | Snes9x 2010 | non commerciale | buildbot libretro, armeabi-v7a / arm64-v8a / x86_64 |
| Neo Geo | FB Alpha 2012 Neo Geo | non commerciale | idem ; correspond aux anciens romsets fournis par Recalbox |
| Arcade | FinalBurn Neo | non commerciale | idem |
| Amiga | PUAE | GPL-2.0 | idem ; Kickstart copiés depuis le dossier `amiga/bios` du partage |

L'installateur télécharge
`https://buildbot.libretro.com/nightly/android/latest/<abi>/<core>_libretro_android.so.zip`
en HTTPS avec validation normale des certificats, le décompresse dans
`filesDir/retro/cores/`, note son SHA-256 et sa taille, et le charge avec
`dlopen`. L'ABI est celle du processus (`armeabi-v7a` sur le GP520).

## Risques et inconnues

- HYPOTHESIS : le `dlopen` d'un core téléchargé dans le stockage privé
  fonctionne avec `targetSdk 34` (Android bloque l'exécution de fichiers des
  données de l'appli par `execve`, pas le chargement de bibliothèques). À
  vérifier sur l'émulateur en R2.
- HYPOTHESIS : Snes9x 2010 tourne à pleine vitesse sur le processeur ARM 32 bits
  du GP520.
- Sécurité : un core est du code natif exécuté dans GPSx. Il n'est téléchargé
  qu'à la demande du propriétaire, en HTTPS depuis le serveur officiel, et son
  empreinte est affichée et journalisée. Les builds nightly changent, donc
  l'empreinte ne peut pas être fixée à l'avance.
- UNKNOWN : le NAS accepte-t-il un accès SMB2/3 invité depuis Android, ou faut-il
  un utilisateur et un mot de passe ?
- Les jeux d'arcade doivent correspondre à la version de romset du core ; un jeu
  qui ne correspond pas échoue au chargement et est signalé comme tel.

## Hors périmètre

- Fournir, télécharger ou référencer des ROM ou des BIOS.
- Les systèmes à rendu matériel (N64, PSP, Dreamcast) et les jeux d'arcade sur CHD.
- Jeu en réseau, succès, shaders.
