# Développement offline-first avant accès au GP520

Statut : contrainte propriétaire acceptée le 25 octobre 2026.

GPSx est d'abord développé sans connecter, sonder, installer sur ni dépendre du
BenQ GP520. L'accès physique constitue un gate ultérieur contrôlé explicitement
par le propriétaire. Les outils ADB existants restent inactifs jusque-là.

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

Il ne s'ouvre qu'après accord explicite. Avant de le proposer, Codex fournit but,
commandes exactes, champs collectés, expurgation, durée, rollback/désinstallation
et prompt Claude Code CR4. Atteindre M3 n'autorise pas l'accès au projecteur.

