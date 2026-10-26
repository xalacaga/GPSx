# Export des diagnostics

Statut : encodeur JSONL portable implémenté ; la conservation et le partage par
les adaptateurs de plateforme ne sont pas encore implémentés.

## Objectif et frontière

Le cœur portable de télémétrie produit des rapports diagnostiques déterministes
que les futurs adaptateurs Windows ou Android pourront conserver, puis que les
outils hors ligne pourront comparer. Il n'effectue aucun accès fichier, réseau,
Android, JNI, PS5 ou GP520.

Un export constitue une preuve uniquement pour l'environnement indiqué dans son
enregistrement et pour la procédure qui l'a produit. Les données de l'émulateur
ne prouvent jamais le comportement du GP520 physique. L'encodeur refuse les
exécutions synthétiques étiquetées `android_generic` ou `gp520`, et une exécution
synthétique ne peut contenir aucune preuve `fact`. Les adaptateurs restent
responsables d'établir la provenance des captures sur matériel réel. Un rapport
ne constitue pas une affirmation de performance sans l'environnement et la
procédure comparables de `PERFORMANCE.fr.md`.

## JSONL v1

La première ligne est un enregistrement d'exécution. Chaque ligne suivante est
une mesure :

```jsonl
{"schema":"gpsx.diagnostics.v1","record":"run","source_revision":"development","environment":"android_emulator","run_kind":"synthetic","sample_count":1,"started_unix_ms":1000}
{"schema":"gpsx.diagnostics.v1","record":"metric","metric":"network.rtt","value":4.8e0,"unit":"ms","monotonic_ns":42,"wall_unix_ms":1234,"evidence":"hypothesis"}
```

Les noms de métriques et leurs unités forment une énumération Rust fermée. La
classe de preuve vaut exactement `fact`, `inference`, `hypothesis`, `unknown` ou
`not_tested`. La révision source vaut `development` ou une empreinte Git de 40
caractères hexadécimaux minuscules. Les valeurs sont des nombres JSON finis,
écrits dans la notation scientifique la plus courte qui restitue exactement les
mêmes bits `f64`, y compris le signe de zéro. Les consommateurs ne doivent pas
déduire un type entier distinct de leur représentation textuelle.

`monotonic_ns` ne décroît pas dans un rapport et constitue le seul champ utilisé
pour ordonner les mesures et calculer les intervalles. Des valeurs égales sont
permises. `started_unix_ms` et `wall_unix_ms` sont des métadonnées facultatives ;
le temps civil peut sauter et ne sert pas à l'ordre. Un temps civil est refusé
s'il précède l'époque Unix ou si son nombre de millisecondes ne tient pas dans un
`u64`. Le maximum représentable de `SystemTime` dépend de la plateforme.

## Sémantique des métriques

Sauf indication contraire, un `count` est cumulatif depuis le début de
l'exécution et une jauge est la valeur observée à `monotonic_ns`.

| Métrique | Unité | Signification v1 |
| --- | --- | --- |
| `network.rtt` | ms | Dernier temps d'aller-retour réseau mesuré. |
| `network.jitter` | ms | Différence absolue entre les échantillons RTT courant et précédent. |
| `network.packets_lost` | count | Nombre cumulatif de paquets détectés perdus. |
| `network.packets_reordered` | count | Nombre cumulatif de paquets reçus dans le désordre. |
| `network.bitrate` | bit/s | Bits de charge utile reçus pendant la dernière fenêtre complète d'une seconde. |
| `video.frames_per_second` | fps | Images présentées pendant la dernière fenêtre complète d'une seconde. |
| `video.receive_to_submit` | ms | Durée entre la réception de la dernière unité d'accès compressée et sa soumission au décodeur. |
| `video.submit_to_present` | ms | Durée entre la soumission au décodeur et la présentation de la dernière image. |
| `video.dropped_frames` | count | Nombre cumulatif d'images abandonnées après réception. |
| `video.queue_depth` | frames | Unités d'accès compressées complètes en attente de soumission au décodeur. |
| `audio.buffer_depth` | ms | Durée PCM lisible actuellement en file d'attente de sortie. |
| `audio.underruns` | count | Nombre cumulatif de sous-alimentations de la sortie audio. |
| `audio.drift` | ms | Temps de présentation audio moins l'horloge monotone de session ; positif signifie que l'audio est en avance. |
| `audio.route_changes` | count | Nombre cumulatif de changements de route de sortie. |
| `input.event_interval` | ms | Temps depuis l'événement de manette capturé précédent. |
| `input.to_response` | ms | Dernière durée mesurée entre capture de la manette et réponse corrélée. |
| `runtime.cpu_percent` | percent | Utilisation CPU du processus normalisée sur la capacité totale de l'appareil, de 0 à 100. |
| `runtime.memory_bytes` | bytes | Mémoire résidente actuelle du processus. |
| `runtime.jni_calls` | count | Nombre cumulatif d'appels ayant franchi la frontière JNI pendant l'exécution. |
| `runtime.thermal_celsius` | celsius | Température pertinente la plus élevée fournie par l'adaptateur ; la procédure de test doit consigner sa source de capteur stable. |

## Bornes et comportement en cas d'échec

- Un rapport contient au plus 4 096 mesures.
- Un enregistrement d'exécution contient au plus 223 octets UTF-8 et un
  enregistrement de mesure au plus 233 octets, hors sauts de ligne. Ces limites
  sont calculées à partir de la valeur la plus longue de chaque champ fermé. La
  limite dérivée du rapport est de 958 688 octets : une ligne d'exécution et
  4 096 lignes de mesure, chacune avec son saut de ligne. Tout rapport v1 valide
  de taille maximale tient donc dans cette limite.
- Les nombres non finis, révisions invalides, preuves synthétiques interdites,
  horodatages monotones décroissants, temps antérieurs à Unix, dépassements de
  temps, excès de mesures et violations des invariants de taille sont rejetés.
- Les entrées sémantiques sont vérifiées avant la construction. Les bornes de
  taille sont aussi contrôlées pendant le formatage. Aucun échec ne renvoie de
  rapport partiel.
- Les erreurs sont statiques et ne recopient jamais les données reçues.
- Le cœur renvoie les octets en mémoire. La création atomique du fichier, la
  conservation, le partage et le consentement relèvent des adaptateurs et de
  tests séparés.

Le format exclut volontairement les clés libres, identifiants d'appareil, URL,
identifiants secrets, données d'enregistrement et secrets de session. Ajouter un
tel champ exige une nouvelle revue de sécurité et des tests d'expurgation adverses.

## Compatibilité

Le schéma v1 est figé : noms de champs, types d'enregistrement, valeurs
d'énumération, unités et significations des métriques ne doivent être ni modifiés
ni étendus sous l'identifiant v1. Un tel changement exige un nouvel identifiant
de schéma et des tests de migration. Les lecteurs doivent refuser les identifiants
de schéma et types d'enregistrement inconnus. Ils peuvent ignorer des membres
d'objet inconnus uniquement après avoir reconnu un schéma et un type pris en
charge. L'ordre reste une ligne d'exécution suivie exactement de `sample_count`
lignes de mesure.
