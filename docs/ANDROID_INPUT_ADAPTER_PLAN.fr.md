# Plan de l’adaptateur d’entrée Android

Statut : **IMPLÉMENTÉ POUR VALIDATION ÉMULATEUR — MANETTES PHYSIQUES NOT_TESTED**.

## Périmètre et frontière de preuve

Cette tranche M3 relie la capture Android `InputDevice`, `KeyEvent` et
`MotionEvent` au modèle portable `gpsx-input` existant. Elle se limite aux
événements synthétiques sur émulateur et aux fixtures de replay redistribuables.
Elle ne se connecte ni à une console ni au GP520 et ne peut pas valider le
mapping DualSense sous Android 11.

FAIT : le cœur portable possède déjà l’identité des manettes, la fusion
déterministe de deux manettes, la dérivation des edges et la file bornée de
transitions. FAIT : `MainActivity` route maintenant les événements Android de
touches et de mouvement dans l’adaptateur natif, puis `gpsx-input` et son
`InputTransitionQueue`.

## Interface des manettes Bluetooth

L’écran Manettes observe l’`InputManager` Android, affiche au plus deux gamepads
connectés, classe pour le diagnostic les appareils Sony (`0x054c`), Microsoft
(`0x045e`) et Android génériques, puis ouvre les réglages Bluetooth Android pour
l’association. L’association et les permissions Bluetooth restent la propriété
du système. Touches gamepad standard, axes HAT, sticks et gâchettes analogiques
empruntent le même chemin portable ; le nom du constructeur n’attribue aucune
sémantique wire.

L’émulateur Google TV ne fournit aucun passthrough Bluetooth de l’hôte. Pour les
tests émulateur, le script hôte optionnel lance scrcpy 5.0 en mode manette UHID
uniquement et expose dans Android la manette associée à Windows comme un gamepad
HID physique. L’archive Windows vérifiée porte le SHA-256
`44c10d9e82f20ea67227d14d37bf9fbe3603117c5736df3f514544a02ba20a73`.
Cet outil et ce chemin ne sont pas embarqués dans l’APK. Sur un appareil Android
réel, la manette est associée dans les réglages Bluetooth système et passe
directement par `InputManager`.

Le 29 octobre 2026, le bridge UHID a exposé la manette Xbox associée à Windows
dans l’AVD Google TV API 36 sous le nom `Microsoft X-Box 360 Pad`, constructeur
1118, produit 654, avec les sources Android `GAMEPAD | JOYSTICK` et le numéro de
contrôleur 1. Cela prouve l’énumération et la classification des sources Android
sur cet hôte. Le mapping complet touches/axes, la latence, la livraison physique
DualSense et le GP520 restent NOT_TESTED.

## Ownership et couches

- Kotlin possède la découverte des appareils Android, le focus stream/UI et les
  objets d’événement Android. Il n’attribue jamais de sémantique physique aux
  boutons.
- Un nouveau crate Rust d’adaptation limité à Android possède le décodage JNI,
  la validation, le choix du profil de mapping et la conversion en valeurs
  `gpsx-input`. Il peut dépendre de `jni` et des API Android ; les crates
  portables ne doivent pas dépendre de lui.
- `gpsx-input` reste l’unique propriétaire de l’état normalisé multi-manette, de
  la fusion et de la dérivation des transitions.
- Le futur feedback sender possède l’historique protocolaire et l’émission.
  Cette tranche n’ajoute pas d’émetteur wire.

L’adaptateur expose des identifiants opaques de registre, jamais des adresses
Rust. La destruction retire l’entrée du registre avant de libérer le
propriétaire. Chaque appel JNI prend un propriétaire partagé temporaire ; un
arrêt concurrent ne peut donc pas invalider un événement en cours.

## Contrat JNI grossier

Un `KeyEvent` produit au plus un appel JNI contenant :

```text
device_id, jeton de descriptor, source, action, key_code, scan_code,
repeat_count, event_time_ns
```

Un `MotionEvent` produit au plus un appel JNI avec l’échantillon courant et tous
les échantillons historiques en ordre chronologique. L’appel transporte un
vecteur fermé d’identifiants d’axes, un vecteur de valeurs row-major, ainsi que :

```text
device_id, jeton de descriptor, source, action, history_size,
axis_count, sample_times_ns
```

Les limites échouent de manière fermée et sont vérifiées avant la lecture des
éléments des tableaux :

- au plus 32 axes ;
- au plus 64 échantillons, échantillon courant inclus ;
- exactement `axis_count × sample_count` valeurs float finies ;
- temps d’événement positifs ou nuls et monotones non décroissants ;
- classifications fermées des actions et sources Android.

Un appel invalide ne change aucun état de manette et renvoie un statut classifié.
Il n’existe aucun appel JNI par axe, échantillon, bouton, paquet ou frame. Kotlin
ne fusionne aucun événement ni échantillon historique.

## Identité et routage des appareils

Kotlin maintient un snapshot `InputDeviceListener` pour le diagnostic, mais
l’adaptateur indexe l’état vivant avec son propre `ControllerId` monotone. Les
device IDs Android peuvent être réutilisés et ne constituent jamais l’identité
stable. Descriptor, VID, PID, version, sources, touches et plages de mouvement
sélectionnent un profil versionné ; le nom de l’appareil et les noms de key codes
Android servent uniquement au diagnostic.

Au plus deux manettes physiques occupent les slots actifs. Une télécommande TV
est réservée à l’UI. Pendant le streaming, les événements de manette sont
consommés avant qu’un fallback Android puisse transformer un bouton en `BACK` ;
Back/Menu de la télécommande restent sur le chemin UI. À la fin de l’ownership
stream, à l’ouverture de l’overlay ou au retrait d’une manette, l’adaptateur
publie un état neutre avant de libérer le slot.

Le profil DualSense Generic.kl d’API 30 reste une HYPOTHÈSE jusqu’à l’existence
d’une preuve physique. La première implémentation utilise des profils
synthétiques avec maps explicites de scan codes et d’axes ; elle ne doit pas
présenter les key codes de l’émulateur comme des faits DualSense.

## État, edges et travail borné

Chaque événement brut accepté est mappé synchroniquement pendant son unique
appel JNI. Aucune file non bornée d’événements bruts n’est ajoutée. L’adaptateur
met à jour `ControllerSet` et pousse atomiquement le batch complet de transitions
dans l’`InputTransitionQueue` Android de 128 entrées.

- Les edges de boutons et les franchissements de gâchette `0 ↔ non-zéro` ne sont
  jamais fusionnés.
- Sticks, mouvement et valeurs intermédiaires de gâchettes sont des états
  latest-wins.
- Les variantes gâchette axe/touche et croix HAT/touches fusionnent avant le diff.
- Un overflow rejette le changement d’état complet, incrémente le compteur
  existant et conserve l’état normalisé précédent afin que l’appelant puisse
  mettre la session en échec plutôt que continuer avec un bouton maintenu inconnu.
- JNI retourne sans attendre le réseau, le feedback, la télémétrie ou l’UI.

La frontière JNI passe des primitives par valeur et ne crée aucun tableau JNI.
Le bridge actuel n’alloue plus après l’entrée du slot manette dans le
`ControllerSet` ; sa latence doit encore être mesurée avant toute revendication
d’optimisation.

## Sûreté native

Les crates portables conservent `#![forbid(unsafe_code)]`. L’unsafe reste limité
au crate d’adaptation Android. Chaque bloc unsafe porte un invariant `// SAFETY:`.
Les longueurs des tableaux JNI sont vérifiées avant accès, aucune référence
locale ne sort de l’appel, aucune mémoire JVM empruntée n’est conservée et les
panics sont capturées avant de traverser JNI.

Miri couvre le mapping et le registre avec un substitut JNI. Android ASan couvre
la vraie bibliothèque JNI. Un audit des symboles release ne doit trouver aucun
hook de test.

## Tests de fermeture

Tests portables/replay :

- chaque pression/relâchement, y compris les deux dans un même intervalle sender ;
- sweep des gâchettes avec exactement un engage et un release ;
- déduplication gâchette axe/touche et fusion croix HAT/touches ;
- isolation de deux manettes et état fusionné déterministe ;
- neutralisation au retrait, overlay et arrêt ;
- atomicité de l’overflow sans changement d’état ;
- dimensions invalides, NaN/infini, régression temporelle et actions inconnues.

Instrumentation sur émulateur Android :

- une soumission sink/JNI par `KeyEvent` et par `MotionEvent` ;
- chaque échantillon historique de mouvement arrive une fois et dans l’ordre ;
- le fallback `BACK` de manette est consommé uniquement avec ownership stream ;
- Back de la télécommande TV reste une entrée UI ;
- ajout/retrait/réutilisation d’un device ID n’hérite jamais d’un ancien état ;
- soumission concurrente et destroy renvoient des statuts classifiés sans fuite
  d’entrée du registre ;
- les profils normal et ASan conservent des preuves XML hashées.

Contrôles release :

- les quatre ABI Android compilent avec warnings interdits ;
- la pureté des dépendances du cœur portable reste inchangée ;
- l’adaptateur ne lie aucune implémentation média ou protocolaire dans Kotlin ;
- les symboles dynamiques ne contiennent que la surface JNI de production revue.

## Résiduels explicites

- NOT_TESTED : livraison DualSense physique, comportement Generic.kl API 30,
  interception PS/Home, vibration, débit Bluetooth réel et GP520 ;
- NOT_IMPLEMENTED : feedback sender, historique/tail resend protocolaire, UI du
  chord de sortie, coordonnées tactiles, capteurs de mouvement et rapports output ;
- UNKNOWN : mapping réel scan/axes, key layouts vendor et driver kernel du GP520 ;
- le gate matériel physique reste fermé.
