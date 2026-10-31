# Spécification de l’interface Android TV

La première interface GPSx est un prototype hors ligne pensé d’abord pour la
télécommande. Elle valide navigation, hiérarchie de l’information et états de
session explicites avant toute autorisation de connexion à une console ou au GP520.

## Direction visuelle

- Direction « salle d’arcade » : fond quasi noir, texte blanc cassé chaud et un
  seul orange signal pour le focus et l’action principale. Ni dégradés, ni verre
  teinté, ni cartes arrondies.
- Titres très grands, serrés et très gras, lisibles à trois mètres d’un projecteur.
- Barre d’onglets horizontale en haut (un onglet par destination) avec le nombre
  de manettes connectées ; elle disparaît pendant le stream, et remonter depuis
  un écran ramène sur l’onglet actif.
- Focus : tuiles et lignes prennent un cadre orange de 3 dp, boutons et onglets
  se remplissent d’orange ; la tuile « Jouer » de l’Accueil devient orange.
- Uniquement des données réelles : ni mesures simulées ni fonctions fictives.
- Le G du logo est orange ; l’écran À propos s’ouvre sur la phrase du
  propriétaire « Gaby's game », conservée telle quelle.

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
Manettes affiche deux emplacements DualSense physiques dont l’identité et l’état
de reconnexion restent indépendants localement avant fusion en un seul joueur Remote Play.
Diagnostics réserve une vue de coexistence Bluetooth pour deux manettes, la
télécommande TV et la route audio, avec corrélation de l’impact sur le stream.

## Acceptation de cette tranche

- L’application Android TV compile avec compatibilité API 30 et se lance par Leanback.
- Navigation et actions fonctionnent par événements D-pad clavier dans un émulateur.
- La démo suit `READY → CONNECTING → STREAMING → STOPPED`.
- Les transitions invalides sont ignorées et les tests du reducer passent.
- Aucune connexion ADB, découverte d’appareil, protocole réseau ou accès GP520 n’a lieu.

