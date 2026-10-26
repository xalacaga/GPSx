# Concurrence, files et ownership

Statut : proposition du 25 octobre 2026 après revue indépendante ; approbation
humaine requise avant M1. Les nombres HYPOTHESIS sont des budgets initiaux à
remplacer par les mesures GP520.

## Principes

- Les chemins chauds ont des threads nommés ; l’async est réservé au contrôle.
- Toute file est bornée, avec propriétaires et politique de débordement.
- Aucun allocation par paquet/image/événement en régime stable et aucun verrou
  partagé avec contrôle/télémétrie sur un thread chaud.
- Le callback audio ne verrouille, n’alloue, ne logue et n’appelle jamais JNI.

## Threads Android

| Thread | Travail principal |
|---|---|
| Main Kotlin | UI, lifecycle, capture d’entrée et soumission JNI non bloquante |
| Contrôle | découverte, enregistrement, session et reconnexion |
| Réception réseau | UDP, validation/déchiffrement, reorder, FEC, assemblage, Opus initialement |
| Feedback | historique d’entrées et renvois temporisés |
| Alimentation vidéo | AU vers buffer d’entrée MediaCodec |
| Sortie vidéo | sortie codec vers rendu et métriques |
| Callback audio | lecture du ring PCM et correction de dérive |
| Télémétrie | drainage des rings vers JSONL, faible priorité |

Windows réutilise les threads du cœur avec ses propres threads média. Ses
résultats ne prouvent rien pour Android.

L’appel de découverte actuel de `gpsx-transport` est une opération synchrone du
chemin de contrôle, pas un thread réseau chaud. L’appelant possède la socket,
l’unique allocation de réception de 65 535 octets et le rapport renvoyé. L’appel
envoie un datagramme, bloque au plus cinq secondes, ne crée aucun thread de fond
et n’utilise aucune file. La sonde Windows l’exécute en série pour PS5 puis PS4,
soit au plus 1,5 seconde avec les réglages par défaut. Une future interface devra
l’appeler depuis son worker de contrôle, jamais depuis le thread d’interface.
Les resets/refus de connexion ICMP sont comptés sans terminer la collecte ;
l’échéance est vérifiée avant chaque réception, même sous un flot continu.

## Files

| File | Capacité initiale | Débordement |
|---|---|---|
| Événements d’entrée | 256, HYPOTHESIS | ne jamais perdre un front de bouton ni un passage 0 ↔ non-zéro de gâchette ; les transitions d’un changement d’état entrent toutes ou aucune ; sticks, mouvement et valeurs intermédiaires de gâchette sont de l’état (le plus récent l’emporte) ; compter tout débordement (`overflows()`), qui fait échouer les tests ; implémenté dans `gpsx-input` (`diff`, `push_batch`) |
| Historique protocolaire | défini par protocole | conserver les événements requis et renvoyer la queue |
| AU vidéo compressés | 2, HYPOTHESIS | jeter non-IDR, demander IDR, ne pas bloquer le réseau |
| Pool vidéo | N buffers fixes | pool vide = débordement vidéo |
| Ring PCM | cible T=40 ms, max 2T, HYPOTHESIS | jeter le plus ancien ; silence et compteur en underrun |
| Télémétrie | ring fixe par thread | jeter le nouveau record et compter |
| Événements Kotlin | petit lot fixe | ne pas perdre lifecycle/erreur ; métriques « latest wins » |

La dérive entre horloge PS5 et sink est corrigée seulement après mesure :
drop/insertion à faible énergie ou ratio de resampling borné. La profondeur du
ring est enregistrée pendant les soaks.

## Ownership et arrêt

Les AU appartiennent au pool du cœur, passent par valeur au feeder, puis
reviennent après l’unique copie dans MediaCodec. Le PCM est écrit en place.
L’adaptateur natif possède `ANativeWindow` avec génération de Surface et les
références JNI globales.

Arrêt idempotent : bloquer les nouvelles entrées, envoyer l’état neutre et
joindre feedback, fermer les sockets et joindre réseau, drainer/join vidéo,
EOS/stop/delete codec puis libérer la window, fermer audio, flusher/join
télémétrie, libérer JNI et invalider le handle. Sur perte de Surface, suspendre
le feeder, remplacer la Surface si possible et demander un IDR à la reprise.

## Tests requis

Modèles `loom` ou équivalents, débordement de chaque file avec press/release,
création/destruction ×1000, churn de Surface, soak audio ≥60 minutes et
P50/P95/P99 de toutes les profondeurs de file.

