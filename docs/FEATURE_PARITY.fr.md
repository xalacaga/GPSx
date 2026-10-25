# Socle de parité fonctionnelle

Statut : contrat produit, 2026-10-25. Ce document ne prouve pas qu’une fonction est implémentée.

GPSx vise au minimum les capacités visibles par l’utilisateur recensées dans les
références Chiaki, chiaki-ng et Pylux, adaptées à une expérience Android TV à
distance. Une capacité ne devient `DONE` qu’après implémentation et tests adaptés.
Les capacités matérielles exigent en plus une preuve GP520.

| Domaine | Capacité minimale GPSx | Statut UI | Statut de preuve | Gate de livraison |
|---|---|---|---|---|
| Consoles | Découverte PS4/PS5, hôte manuel, enregistrement, réveil, masquer/supprimer | Squelette | NOT_TESTED | Protocole + matériel |
| Connectivité | Réseau local et connexion distante assistée par PSN | Squelette | NOT_TESTED | Protocole + sécurité |
| Profils | Plusieurs utilisateurs et réglages locaux/distants par console | Squelette | NOT_TESTED | Stockage sécurisé |
| Stream | H.264 720p60/1080p60, qualité auto, débit, reconnexion, arrêt propre | Squelette simulé | NOT_TESTED | Média + matériel |
| Vidéo avancée | HEVC, HDR si supporté, cadrage/zoom/étirement | Squelette | UNKNOWN sur GP520 | Détection de capacités |
| Audio | Choix de sortie, modes audio seul/vidéo seule, réglage des buffers bornés | Squelette | NOT_TESTED | Média + tests de route |
| Microphone | Choix d’entrée, muet, réduction bruit/écho lorsque possible | Squelette | NOT_TESTED | Permissions + média |
| Manettes | Deux DualSense physiques isolées localement, puis fusionnées dans le seul état joueur Remote Play prouvé | Squelette | Fusion cœur UNIT_TESTED ; Android/GP520 NOT_TESTED | Entrée + tests physiques à deux manettes |
| Entrée DualSense | Boutons standard, gâchettes/sticks analogiques, clic pavé tactile et inspection des capacités | Squelette | Modèle UNIT_TESTED ; mapping physique NOT_TESTED | Capacités + tests physiques |
| Sortie DualSense avancée | Mouvement si exposé ; haptique et gâchettes adaptatives exclues du MVP Bluetooth Android | Squelette | OS_LIMITATION attendue sur Android 11 Bluetooth ; NOT_TESTED | Décision matérielle ultérieure explicite |
| Accessibilité | Focus D-pad déterministe, usage télécommande seule, échelle TV lisible | Squelette | NOT_TESTED | Émulateur + appareil |
| Diagnostics | Inspecteur manette, logs, overlay performance, panneaux décodeur/audio/réseau | Squelette | NOT_TESTED | Intégration sous-systèmes |
| Télémétrie | FPS, pertes, RTT, jitter, débit, percentiles décodage/audio/entrée | Squelette | Cœur percentiles UNIT_TESTED ; sources NOT_TESTED | Sources de mesures réelles |
| Portabilité | Import/export des réglages non secrets et métriques JSONL | Squelette | Cœur expurgation UNIT_TESTED ; export Android NOT_TESTED | Stockage + expurgation |
| Récupération | Perte réseau, reconnexion manette, Surface, changement de route audio | Squelette | NOT_TESTED | Intégration + endurance |
| Coexistence Bluetooth | Deux DualSense + télécommande TV + audio Bluetooth simultanés, avec corrélation indépendante des événements | Squelette | NOT_TESTED | Matrice matérielle GP520 |
| Laboratoire diagnostic | Captures guidées, paquet expurgé, transfert GitHub privé et comparaison baseline | Squelette | Conception seulement, NOT_TESTED | Sécurité + preuves GP520 |
| Mises à jour | Première installation Downloader, puis Releases GitHub vérifiées dans GPSx | Squelette | Conception seulement, NOT_TESTED | Signature + revue sécurité |

`Présente` signifie que la navigation et l’emplacement produit sont réservés dans
la première UI. Cela ne signifie pas que la fonction opère. Le build Android
actuel est un prototype hors ligne et synthétique ; toutes ses données sont
étiquetées comme telles.

## Règles produit

- Les actions courantes restent à deux déplacements de focus maximum de l’accueil.
- Les réglages simples restent courts ; les contrôles avancés sont regroupés par sous-système.
- Une capacité non supportée est affichée indisponible, jamais simulée silencieusement.
- Deux emplacements physiques sont obligatoires. Entrées, capacités et reconnexion
  sont isolées localement par `ControllerId`, puis boutons, gâchettes et axes sont
  fusionnés dans un seul état joueur Remote Play. Plusieurs joueurs distants restent
  `UNKNOWN` jusqu’à preuve protocolaire.
- Haptique Bluetooth Android et gâchettes adaptatives sont explicitement hors MVP ;
  l’entrée standard ne doit jamais en dépendre.
- Android possède l’appairage Bluetooth. GPSx observe les appareils et routes audio sans reconnecter ni réinitialiser les périphériques non concernés.
- GPSx peut dépasser l’amont grâce aux diagnostics, à la séparation des preuves,
  aux réglages sûrs et à l’ergonomie TV, mais ne retire aucune capacité de base
  sans décision produit explicite.
- La distribution store, les marques et les conditions PSN restent des gates de release distincts.

## Sources

Le socle vient des preuves amont consignées dans `UPSTREAM_ANALYSIS.fr.md` et
`LICENSE_AUDIT.fr.md`. Aucun code source amont n’a été copié pour cet inventaire.

