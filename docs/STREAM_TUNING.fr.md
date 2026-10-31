# Réglage du stream pour le GP520 en Wi-Fi

Statut : implémenté le 2026-10-31 ; mesuré sur émulateur, GP520 NOT_TESTED.

## Cible

- BenQ GP520 : DLP 3840×2160 natif (XPR), 60 Hz, HDR10/HDR10+/HLG, Wi-Fi 6,
  Google TV sous Android 11 (API 30). BenQ annonce 17,9 ms d'input lag en
  1080p60 et 4K60, obtenus avec le préréglage d'image **Jeu** et **Game Boost**.
- Réseau : le stream passera surtout par le Wi-Fi.
- FAIT : le Remote Play PS5 plafonne à 1080p60 ; le projecteur met à l'échelle en 4K.

## Ce que fait GPSx

| Domaine | Changement | Pourquoi |
|---|---|---|
| Moteur natif | Pylux construit en `RelWithDebInfo` (`-O2`) au lieu du build debug `-O0`, puis strippé | Déchiffrement, FEC et Opus tournent en logiciel sur le chemin de réception |
| Journalisation | Lignes `DEBUG` par paquet supprimées sauf journalisation verbeuse | Journaliser chaque paquet coûte du CPU au thread de réception |
| Décodeur | `low-latency`, `priority=0` et clés basse latence Qualcomm/MediaTek/Exynos, avec nouvel essai sans elles en cas de refus | Demander au décodeur de sortir chaque image immédiatement |
| Wi-Fi | Verrou `WIFI_MODE_FULL_LOW_LATENCY` (API 29+, high-perf avant) tenu seulement pendant une session | L'économie d'énergie Wi-Fi ajoute des dizaines de ms de gigue |
| Affichage | `preferMinimalPostProcessing` (API 30) et écran maintenu allumé pendant le stream | Demander le mode jeu de l'écran |
| Fréquence | `Surface.setFrameRate(60, FIXED_SOURCE)` (API 30) | Garder l'écran à 60 Hz sans conversion saccadée |
| Codec | AUTO choisit HEVC 1080p60 si un décodeur HEVC matériel le supporte, sinon H.264 ; l'émulateur reste en H.264 | Meilleure image au même débit de 15 Mbit/s |
| Mesures | FPS, débit, RTT, pertes, images perdues et état du son réels affichés pendant le stream | Diagnostiquer le Wi-Fi à l'écran |
| Audio | Flux Oboe partagé (basse latence, puis défaut), usage Jeu, rouvert après un changement de sortie comme une barre de son Bluetooth qui se (re)connecte ; compteurs underflow/overflow/réouverture dans les rapports | Le moteur d'origine ne rouvrait jamais un flux déconnecté et demandait le mode exclusif, absent en Bluetooth A2DP |

## Réglages du propriétaire sur le GP520

- Mode d'image **Jeu** et **Game Boost** activés. On ignore (UNKNOWN) si
  l'indication Android de post-traitement minimal bascule seule le Google TV
  intégré en mode Jeu ; réglez-le donc à la main.
- Préférer le Wi-Fi 5 GHz ou 6 GHz, la PS5 en Ethernet sur la box si possible,
  et le projecteur en vue directe du point d'accès.

## Avant/après (2026-10-31, émulateur Google TV API 36 x86_64, écran d'accueil réel de la PS5)

| Mesure | Moteur debug | Moteur optimisé |
|---|---|---|
| Lignes de journal moteur, stream de 60 s | 1 225 | 287 (−77 %) |
| CPU des threads décodeur (3 × 10 s) | 47,1–48,2 % | 46,3–47,1 % |
| CPU moteur + app (3 × 10 s) | 17,9–18,5 % | 17,1–18,5 % |
| FPS / pertes / images perdues | 60 / 0 % / 0 | 60 / 0 % / 0 |
| Indications basse latence du décodeur | non demandées | acceptées (`AMediaCodec configured with low-latency hints`) |

- FAIT : l'écran d'accueil ne streame qu'à 0,1–0,3 Mbit/s ; le moteur avait peu
  de travail et les écarts CPU restent dans le bruit d'une mesure à l'autre.
  Aucune amélioration CPU n'est revendiquée. Le build `-O2` compte en jeu, vers
  15 Mbit/s, ce qui n'a pas été mesuré.
- FAIT : sur l'émulateur, les threads « décodeur » font un décodage émulé par
  l'hôte ; sur le GP520, ce travail revient au décodeur matériel.
- FAIT : le RTT de l'émulateur (26–155 ms) reflète son NAT, pas le Wi-Fi.
- UNKNOWN : latence, support basse latence du décodeur, bascule en mode Jeu et
  thermique du GP520 tant que le gate physique reste fermé.
