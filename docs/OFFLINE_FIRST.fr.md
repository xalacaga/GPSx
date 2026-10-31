# Développement offline-first avant accès au GP520

Statut : contrainte propriétaire acceptée le 25 octobre 2026 ; le gate matériel
physique reste fermé jusqu’à confirmation par le propriétaire de la date et du
périmètre, après finalisation du dossier de preuve et de la revue CR4. Aucun
accès physique n’a été exécuté.

GPSx est d'abord développé sans connecter, sonder, installer sur ni dépendre du
BenQ GP520. L'accès physique constitue un gate ultérieur contrôlé explicitement
par le propriétaire. Chaque essai physique exige la procédure bornée, le dossier
de preuve et le `GO` CR4 définis ci-dessous avant que le propriétaire puisse
ouvrir ce périmètre.

## Autorisé avant le gate matériel

- tests Rust unitaires, de propriétés, fuzz, concurrence et replay ;
- validation Windows et interopérabilité PS5 si autorisée séparément ;
- builds émulateur Android TV et tests lifecycle/focus/navigation ;
- faux adaptateurs de décodeur, audio, horloge, réseau et entrées ;
- fixtures H.264/HEVC/Opus synthétiques et redistribuables ;
- replays Android expurgés dont la provenance est approuvée ;
- schémas de capacités et injection de pannes.

## Frontière des preuves

Émulateurs et faux adaptateurs ne prouvent que leur environnement. Ils ne
prouvent jamais codecs, latence, Bluetooth, thermique, key layouts, sideload ou
ADB du GP520. Ces champs restent `UNKNOWN` ou `NOT_TESTED`.

## Conséquence architecturale

Toute dépendance plateforme reste derrière une interface. Le cœur reçoit
horloges, transport, état média, surfaces et événements via des adaptateurs. Les
tests sont déterministes sans matériel Android. Aucun code de production ne
spécialise un comportement GP520 non observé ; les futurs tests appareil
caractérisent d'abord l'écart, puis le plus petit correctif d'adaptateur le ferme.

## Gate matériel physique

Avant de demander au propriétaire d’ouvrir un périmètre borné d’accès physique,
Codex fournit but, commandes exactes, champs collectés, expurgation, durée,
rollback/désinstallation et prompt Claude Code CR4. Le dossier de preuve et un
`GO` CR4 précèdent tout accès physique. Atteindre M3 n’autorise pas à lui seul
l’accès au projecteur.

L’autorisation par le propriétaire de mener la revue CR4 et l’autorisation
d’accès physique sont deux décisions distinctes. La première fige et relit un
diff d’adaptateur limité à l’émulateur ; la seconde ne peut suivre qu’un `GO` et
nomme une date et un périmètre.

## Gate physique ouvert (2026-10-31)

Le propriétaire du projet a ouvert le canal de test GP520 le 2026-10-31.
Périmètre : le propriétaire installe l'APK release signé avec Downloader, lance
des streams sur le GP520 et envoie les rapports par session vers le dépôt public
`xalacaga/GPSx-diagnostics` (voir `INSTALL_GP520.fr.md`). Les agents ne se
connectent toujours jamais eux-mêmes au GP520, ne le sondent pas et n'y
installent rien ; ils analysent les rapports envoyés. Les rapports appareil
portent `evidenceClass: DEVICE` ; ceux de l'émulateur restent `EMULATOR`.
