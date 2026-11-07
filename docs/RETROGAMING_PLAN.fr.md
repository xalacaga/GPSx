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
- Jusqu'à la 0.2.26, l'hôte plantait sur les cartouches Mega Drive et Master
  System : le premier démarrage d'une version ultérieure oublie donc une fois
  toutes les marques de plantage. Un jeu qui plante vraiment se marque de nouveau
  à son prochain lancement.

### Recherche

L'entrée **Recherche** de la barre du haut trouve un jeu par son nom sur toutes
les consoles du partage. Les accents, la casse et la ponctuation sont ignorés ;
chaque mot tapé doit commencer un mot du titre, dans n'importe quel ordre, et les
espaces peuvent être omises (« megaman »). Elle ne couvre que les jeux rétro ; le
catalogue PS Plus garde sa propre recherche. Les jeux refusés restent listés avec
leur marque, car une recherche est une demande délibérée.

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
| Mega Drive, Master System | Genesis Plus GX | non commerciale | idem |
| NES | FCEUmm | GPL-2.0 | idem |
| Game Boy, Game Boy Color | Gambatte | GPL-2.0 | idem |
| Game Boy Advance | mGBA | MPL-2.0 | idem ; aucun fichier BIOS nécessaire |
| PC Engine | Beetle PCE Fast | GPL-2.0 | idem ; les jeux CD demandent la syscard du propriétaire dans `pcengine/bios` |
| PlayStation | PCSX ReARMed | GPL-2.0 | idem ; BIOS de `psx/bios` s'il existe, sinon le remplacement du core |

Les systèmes ajoutés le 2026-11-07 sont NOT_TESTED : les jeux du propriétaire ne sont pas
encore sur le partage, donc aucun jeu de ces systèmes n'a tourné sur l'émulateur ni sur le
GP520. Leurs dossiers (`megadrive`, `mastersystem`, `nes`, `gb`, `gbc`, `gba`, `pcengine`,
`psx`) existent sur le partage et restent masqués dans GPSx tant qu'ils ne contiennent pas
de jeu. Un `.cue` PlayStation ou PC Engine nomme ses pistes, qui sont lues avec lui.

### Vitesse de l'Amiga sur le GP520

FACT (GP520, 0.2.27, 07/11/2026) : avec les options par défaut du core, quatre parties
PUAE ont atteint 31 à 38 images/s au lieu de 50, avec 112 à 4811 sous-alimentations
audio. Le son et la vitesse ont une seule cause : l'horloge audio attend un core qui
ne suit pas. La même soirée, la Mega Drive tournait à 58-59 images/s et la Neo Geo à
58. GPSx demande maintenant une basse résolution sauf si le jeu en exige plus, une
image sur deux affichée, pas d'interpolation du son, pas de bruit de lecteur, et une
lecture des disquettes quatre fois plus rapide. NOT_TESTED sur le GP520 ; l'émulateur
montre seulement que Speedball 2 démarre toujours. Brutal Football s'arrête sur un
Guru Meditation sur l'émulateur, aux deux vitesses de disquette.

Le propriétaire a choisi le 07/11/2026 de ne garder que les jeux Amiga 500. La lecture
du partage écarte les versions AGA, CD32 et CDTV, celles pour A1200 ou A4000, celles
qui demandent 2 Mo ou plus ou un disque dur, les disquettes Workbench seules, les
titres connus uniquement en mauvais dump et les disquettes qui ne démarrent aucun jeu
seules (données, sauvegardes, missions, décors, extensions, mises à jour, éditeurs,
installateurs, documentation). Un jeu qui existe en AGA et en A500 garde
cette dernière. Ce tri ne lit que les noms de fichiers : il ne dit pas si un jeu
tourne vraiment, et les jeux ne sont jamais testés en masse. La fiche du jeu a donc
un bouton **Ne marche pas**, qui le masque de la liste de sa console jusqu'à
**Remettre dans la liste** ; la recherche l'affiche toujours. Les fichiers du partage
ne sont pas touchés.

### Jeux Mega Drive avec musique CD

Le dossier `megadrive` du propriétaire contient un dossier par jeu : un `.md` patché,
un `.cue` et des pistes audio (`.bin`, `.wav` ou `.ogg`). Un `.md` accompagné d'un
`.cue` du même nom reste sur le partage avec ses pistes, comme un jeu CD, et un `.bin`
à côté d'un `.cue` est une piste, pas un jeu.

Genesis Plus GX trouve seul le `.cue` à partir du chemin du `.md`. Il existe deux
familles de packs (FACT, source du core lue le 07/11/2026) : les packs MD+ ont des
lignes `REM LOOP` et utilisent son mode MegaSD, les packs MSU-MD n'en ont pas et
utilisent son mode Mega CD. Son réglage automatique choisit le bon d'après le `.cue` ;
GPSx n'y touche donc pas, car forcer le Mega CD rendait les packs MD+ muets. GPSx
impose seulement une région NTSC, les patches bloquant les consoles PAL.

Le mode Mega CD exige un BIOS, sans lequel le core abandonne le CD sans rien dire.
GPSx copie `bios_CD_E.bin`, `bios_CD_J.bin` et `bios_CD_U.bin` depuis `megadrive/bios`
ou le lot Recalbox du propriétaire, une seule fois et sous ces noms exacts. S'ils
manquent, le lancement s'arrête avec un message et le jeu n'est pas marqué refusé.
Les BIOS ne sont jamais livrés avec GPSx.

Preuves, sur émulateur uniquement (07/11/2026, vrai partage, core v1.7.4 f27655b) :
Sonic 2 passe l'écran de blocage à 60 images/s, et la lecture audio CD a été mesurée
en trafic réseau sur 20 s : Gunstar Heroes 159 Kio/s (MD+), Comix Zone 119 Kio/s et
Lemmings 159 Kio/s, proche des 172 Kio/s d'un CD. Personne ne l'a encore écoutée.
NOT_TESTED : le GP520, et Moonwalker après ses crédits, qui demandent une manette.
Une lecture sur le NAS peut retarder une image en cas d'absence du cache ; on compte
7 à 51 sous-alimentations audio par partie de 33 s sur l'émulateur, sans comparaison
sur la TV pour l'instant.

UNKNOWN : sur l'émulateur, le processus gagne un descripteur `sync_file` par image
affichée, conservé après la fermeture du jeu. GPSx ne manipule aucun descripteur sur
ce chemin (`ANativeWindow_lock` et `unlockAndPost`), l'origine serait donc la pile
graphique de l'émulateur (INFERENCE). Cela n'a pas été vérifié sur le GP520.

### Les jeux CD restent sur le partage

Décision du propriétaire (2026-11-07) : les jeux lourds sont lus depuis le NAS et jamais
copiés sur la TV. Les images `.cue`, `.chd` et `.pbp` s'ouvrent comme fichiers `RemoteGame` :
`openProxyFileDescriptor` d'Android (API 26) fournit un descripteur système alimenté par
des lectures SMB, et l'hôte natif le présente au core par l'interface de fichiers libretro,
dans un dossier qui n'existe nulle part (`/gpsx-remote/`). Les blocs ne traversent jamais
JNI. `ReadAheadFile` garde 16 blocs de 512 Kio en mémoire et lit le bloc suivant à l'avance.
Un core qui ne demande pas l'interface de fichiers reçoit une erreur plutôt qu'une copie
silencieuse ; PCSX ReARMed et Beetle PCE Fast la demandent dans leurs sources (FACT), ce
qu'aucune exécution n'a encore confirmé. Si le NAS ne répond plus, le jeu se bloque ou
échoue : il n'y a pas de copie locale de secours.

Les disques d'un même jeu (étiquettes « (Disc 2) ») forment une seule entrée et se chargent
par une playlist ; L1 + R1 + Create maintenus 1 s insèrent le disque suivant. Les sauvegardes
prennent le titre du jeu.

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
