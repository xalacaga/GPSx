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
| Core rétro (un jeu) | échantillonner les manettes, `retro_run`, copier vers `ANativeWindow`, rééchantillonner l'audio dans le ring PCM rétro ; dort 1 ms tant que le ring contient ≥ 2048 images |

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
| Événements d’entrée | 128 transitions, FAIT pour l’adaptateur Android | un événement Android brut produit au plus 18 transitions et est normalisé synchroniquement par `gpsx-input` ; le lot complet entre dans la file ou aucun élément n’y entre ; l’adaptateur la draine avant de retourner et refuse toute sortie si `overflows()` est non nul ; les fronts de boutons et passages 0 ↔ non-zéro sont soumis immédiatement, tandis que les sticks et valeurs intermédiaires sont latest-wins |
| Historique protocolaire | défini par protocole | conserver les événements requis et renvoyer la queue |
| AU vidéo compressés | 2, HYPOTHESIS | jeter non-IDR, demander IDR, ne pas bloquer le réseau |
| Pool vidéo | N buffers fixes | pool vide = débordement vidéo |
| Ring PCM | cible T=40 ms, max 2T, HYPOTHESIS | jeter le plus ancien ; silence et compteur en underrun |
| Ring PCM rétro (core rétro → callback AAudio) | 4096 images stéréo à 48 kHz (~85 ms) ; le core ne tourne qu'en dessous de 2048 images | jeter les images entrantes et les compter ; sous-alimentation : silence compté ; les deux sont journalisés à la fermeture du jeu |
| État manette rétro (Main → core rétro) | un masque tenu + un masque mémorisé par port (4 ports), sticks au plus récent | un appui est mémorisé jusqu'à l'échantillonnage avant l'image suivante : un appui plus court qu'une image n'est jamais perdu |
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

