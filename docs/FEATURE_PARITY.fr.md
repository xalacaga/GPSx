# Socle de parité fonctionnelle

Statut : contrat produit, 2026-10-25. Ce document ne prouve pas qu’une fonction est implémentée.

GPSx vise au minimum les capacités visibles par l’utilisateur recensées dans les
références Chiaki, chiaki-ng et Pylux, adaptées à une expérience Android TV à
distance. Une capacité ne devient `DONE` qu’après implémentation et tests adaptés.
Les capacités matérielles exigent en plus une preuve GP520.

| Domaine | Capacité minimale GPSx | UI initiale | Gate de livraison |
|---|---|---:|---|
| Consoles | Découverte PS4/PS5, hôte manuel, enregistrement, réveil, masquer/supprimer | Présente | Protocole + matériel |
| Connectivité | Réseau local et connexion distante assistée par PSN | Présente | Protocole + sécurité |
| Profils | Plusieurs utilisateurs et réglages locaux/distants par console | Présente | Stockage sécurisé |
| Stream | H.264 720p60/1080p60, qualité auto, débit, reconnexion, arrêt propre | Présente | Média + matériel |
| Vidéo avancée | HEVC, HDR si supporté, cadrage/zoom/étirement | Présente | Détection de capacités |
| Audio | Choix de sortie, modes audio seul/vidéo seule, réglage des buffers bornés | Présente | Média + tests de route |
| Microphone | Choix d’entrée, muet, réduction bruit/écho lorsque possible | Présente | Permissions + média |
| Manettes | Mapping DualSense prioritaire, manettes génériques et remappage | Présente | Entrée + tests physiques |
| DualSense | Gâchettes analogiques, pavé tactile, mouvement, vibration/haptique, gâchettes adaptatives | Présente | Capacités + tests physiques |
| Accessibilité | Focus D-pad déterministe, usage télécommande seule, échelle TV lisible | Implémentée | Émulateur + appareil |
| Diagnostics | Inspecteur manette, logs, overlay performance, panneaux décodeur/audio/réseau | Squelette implémenté | Intégration sous-systèmes |
| Télémétrie | FPS, pertes, RTT, jitter, débit, percentiles décodage/audio/entrée | Squelette implémenté | Sources de mesures réelles |
| Portabilité | Import/export des réglages non secrets et métriques JSONL | Présente | Stockage + expurgation |
| Récupération | Perte réseau, reconnexion manette, Surface, changement de route audio | Présente | Intégration + endurance |

`Présente` signifie que la navigation et l’emplacement produit sont réservés dans
la première UI. Cela ne signifie pas que la fonction opère. Le build Android
actuel est un prototype hors ligne et synthétique ; toutes ses données sont
étiquetées comme telles.

## Règles produit

- Les actions courantes restent à deux déplacements de focus maximum de l’accueil.
- Les réglages simples restent courts ; les contrôles avancés sont regroupés par sous-système.
- Une capacité non supportée est affichée indisponible, jamais simulée silencieusement.
- GPSx peut dépasser l’amont grâce aux diagnostics, à la séparation des preuves,
  aux réglages sûrs et à l’ergonomie TV, mais ne retire aucune capacité de base
  sans décision produit explicite.
- La distribution store, les marques et les conditions PSN restent des gates de release distincts.

## Sources

Le socle vient des preuves amont consignées dans `UPSTREAM_ANALYSIS.fr.md` et
`LICENSE_AUDIT.fr.md`. Aucun code source amont n’a été copié pour cet inventaire.

