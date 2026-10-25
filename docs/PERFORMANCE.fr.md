# Référence de performance

Aucune affirmation n’est valide sans environnement documenté et mesures
avant/après comparables. Noter révision, firmwares, alimentation, réseau/RSSI,
connexions contrôleur et audio, résolution, fréquence, codec, débit et durée.

| Couche | Métriques |
|---|---|
| Réseau | RTT P50/P95/P99, jitter, pertes, réordonnancement, débit |
| Vidéo | réception-soumission, soumission-affichage, drops, file |
| Audio | profondeur, underruns, dérive, changements de route |
| Entrée | intervalle et entrée-réponse P50/P95/P99 |
| Runtime | CPU, mémoire, GC/JNI, thermique |

Le premier gate dure dix minutes, puis trente et soixante. Windows, émulateur,
Android générique et GP520 physique restent séparés.

