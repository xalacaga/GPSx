# Analyse DualSense

Cible : DualSense en Bluetooth vers BenQ GP520 / Android TV 11. Révision après
contre-revue : modèle entier exact, préservation des transitions, profils par
scan code, fallback `Generic.kl`, gestion BACK et accord de sortie.

## Preuves et hypothèses

Android fournit boutons via `KeyEvent` et axes via `MotionEvent`, mais cette
documentation ne garantit pas le mapping DualSense sur API 30. AOSP Android 11
n’a pas de layout Sony pour le produit 0ce6 ; `Generic.kl` mappe les scan codes
304–318 vers des boutons génériques, les axes 0–5 vers X/Y/Z/RX/RY/RZ et
0x10/0x11 vers HAT_X/HAT_Y.

Sans layout vendeur ou pilote `hid-playstation`, le mapping physique attendu est
une HYPOTHESIS : Square/Cross/Circle/Triangle vers A/B/C/X, L1/R1 vers Y/Z,
gâchettes numériques vers L1/R1 génériques, Create/Options vers L2/R2,
L3/R3 vers Select/Start, PS vers Mode, clic pavé vers ThumbL, sticks X/Y et
Z/RZ, gâchettes analogiques RX/RY et D-pad HAT. Seul l’inspecteur GP520 tranche.

Le pilote Linux Sony documente rapports USB/Bluetooth, boutons, deux contacts,
mouvement calibré, batterie, sorties et CRC. Sa licence GPL interdit d’en faire
une source de copie sans revue. Le succès desktop chiaki-ng ne prouve aucune
capacité Android TV.

La référence filaire chiaki-ng emploie un masque de 16 boutons sans bouton Mic,
des gâchettes `u8` et des sticks `i16`. Boutons et gâchettes forment un historique
distinct de l’état continu, avec renvois de queue afin d’éviter un bouton bloqué.

## Modèle normalisé

Le descripteur contient identité interne, identifiants Android, VID/PID/version,
sources, clés, axes/plages, capacités et profil versionné. L’état contient temps,
séquence, masque de boutons, quatre sticks `i16`, deux gâchettes `u8` et seulement
les capacités tactiles/mouvement réellement observées.

La quantification, le signe Y et les zones mortes sont définis une seule fois et
validés contre la PS5. Un axe de gâchette reposant au minimum est recalé à zéro.
Mic reste une action locale éventuelle et n’entre jamais dans le masque réseau.

## Profils, transitions et sortie

Un profil est une donnée `(VID, PID, version, empreinte layout) → scan
code/axe brut → commande physique`. Il est testable par replay sous Windows.
L’axe gagne sur le bouton numérique de gâchette et les sources HAT/keys du D-pad
sont dédupliquées. Le nom « Wireless Controller » ne suffit jamais.

Kotlin transmet chaque événement et historique de mouvement en un appel JNI par
événement. Le cœur dérive et conserve chaque transition ; seuls sticks,
mouvement continu et valeurs intermédiaires de gâchette sont fusionnables. Le
passage 0 ↔ non-zéro d’une gâchette est une transition, jamais perdue (révisé
après CR1 : traiter chaque valeur analogique comme une transition saturerait
toute file bornée). Déconnexion ou sortie envoie un état neutre.

Pendant le stream, tous les boutons manette sont consommés, y compris un éventuel
fallback BACK. Sortie primaire : Back/Menu de la télécommande. Sortie secondaire
à valider : maintenir Create+Options deux secondes. L’ouverture de l’overlay
neutralise l’état envoyé à la console.

## Inspecteur et validation

L’inspecteur relève identité/profil, keycode, scan code, action, répétition,
source, temps, tous les axes/plages/rest values, valeurs brutes et normalisées,
état fusionné, mapping filaire, fallbacks système, histogramme d’intervalles et
connexions. Aucune ligne de la matrice n’est `PASS` sans test physique.

Les tests rejouent les événements Android en Rust et couvrent press/release dans
un même intervalle, renvoi après perte, état neutre, déduplication gâchette et
D-pad, absence d’effet BACK/HOME, accord temporisé, reconnexion, veille,
background/foreground et isolation de deux manettes. Mesurer les étapes OS→JNI→
normalisation→historique→paquet→socket en P50/P95/P99 ; mesurer séparément le
bouton-à-photon.

