# Spécification de l’interface Android TV

La première interface GPSx est un prototype hors ligne pensé d’abord pour la
télécommande. Elle valide navigation, hiérarchie de l’information et états de
session explicites avant toute autorisation de connexion à une console ou au GP520.

## Direction visuelle

- Fond cinéma sombre, profondeur bleue discrète, focus menthe et alertes ambre.
- Grande typographie et anneaux de focus contrastés lisibles à distance TV.
- Rail persistant à six entrées : Accueil, Consoles, Lecture, Manettes, Diagnostics et Réglages.
- Panneaux arrondis et mouvements sobres ; aucune animation décorative ne masque le focus ou n’ajoute de latence.
- Toute valeur synthétique porte une mention visible `DONNÉES SIMULÉES`.

## Contrat d’interaction

- Chaque action est focalisable avec seulement D-pad, Validation et Retour.
- Retour ramène à l’Accueil depuis une destination ; quitter un futur stream demandera confirmation.
- Le focus ne dépend pas uniquement de la couleur et reste visible sur chaque panneau.
- Le stream ne capture la manette que lorsque le futur mode d’entrée stream est actif.
- Le tactile reste facultatif et n’est jamais requis.

## Écrans actuels

Accueil présente l’action principale, la santé synthétique et les raccourcis.
Consoles réserve découverte locale, ajout manuel et connexion distante. Lecture
exerce la machine d’états et l’overlay. Manettes expose le modèle de capacités.
Diagnostics sépare les preuves réseau, vidéo, audio et entrée. Réglages propose
des choix sûrs avant les pages avancées par sous-système.

## Acceptation de cette tranche

- L’application Android TV compile avec compatibilité API 30 et se lance par Leanback.
- Navigation et actions fonctionnent par événements D-pad clavier dans un émulateur.
- La démo suit `READY → CONNECTING → STREAMING → STOPPED`.
- Les transitions invalides sont ignorées et les tests du reducer passent.
- Aucune connexion ADB, découverte d’appareil, protocole réseau ou accès GP520 n’a lieu.

