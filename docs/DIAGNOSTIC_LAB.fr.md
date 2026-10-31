# Laboratoire de diagnostic embarqué

GPSx intègre un laboratoire de mesure sur l’appareil afin que les sessions GP520
produisent des preuves reproductibles sans débogueur. La capture locale fonctionne
sans GitHub ni Internet ; le transfert GitHub est une seconde étape facultative.

Première implémentation (2026-10-31) : un rapport JSON `gpsx-stream-test/1` par
session streamée, envoyé sur demande via l'API contents de GitHub vers le dépôt
de diagnostics privé ; voir `INSTALL_GP520.fr.md`. Les scénarios guidés et
l'archive zip ci-dessous restent prévus.

## Scénarios guidés

- Contrôle rapide : 2 minutes.
- Comparaison standard : 10 minutes, après une chauffe exclue des résultats.
- Stabilité : 30 ou 60 minutes.
- Endurance : durée de plusieurs heures choisie par l’utilisateur.
- Entrées : latence des manettes physiques A/B, isolation de reconnexion et correction de l’état fusionné.
- Coexistence Bluetooth : deux DualSense, télécommande TV et audio Bluetooth.
- Changement de route audio et récupération Surface/arrière-plan.

Chaque exécution consigne identifiant de scénario, temps monotone de départ,
révision/build, build Android, empreinte appareil, configuration non secrète,
classe réseau et classe de preuve explicite. Les identifiants console/compte sont
expurgés ou remplacés par des alias aléatoires propres à l’export.

## Signaux capturés

- États de session, reconnexions et erreurs classées.
- FPS reçues/décodées/rendues, distribution du temps d’image, pertes, nom du codec,
  résolution, débit et profondeur des files bornées.
- RTT, jitter, pertes, réordonnancement, duplication et signaux de congestion.
- P50/P95/P99 de décodage/rendu, état thermique, CPU et mémoire si disponibles.
- Routes audio, mode accordé, profondeur de buffer, sous-alimentations/débordements.
- Horodatages réception OS, normalisation, file et envoi par `ControllerId` ; les
  transitions de boutons sont conservées, les valeurs continues peuvent être échantillonnées/fusionnées.
- Arrivées/départs Bluetooth et routes audio corrélés dans le temps, sans adresse
  MAC ni identifiant personnel stable.

GPSx ne prétend pas mesurer bouton-vers-photon ou la latence réseau complète avec
ses seuls horodatages internes. Ces mesures exigent un dispositif externe séparé.

## Paquet d’export

Un export est un `.gpsx-diagnostics.zip` versionné contenant :

- `manifest.json` : schéma, build, scénario, classe de preuve et inventaire SHA-256 ;
- `metrics.jsonl` : séries temporelles bornées et lisibles par machine ;
- `events.jsonl` : états et événements de cycle de vie ;
- `summary.json` et `summary.md` : percentiles et résultat du test ;
- `redaction-report.json` : catégories supprimées, jamais les valeurs supprimées.

Aucun identifiant, token, cookie, clé d’enregistrement, URL de redirection, clé de
session, identifiant console, descripteur de manette, adresse MAC ou paquet réseau
brut ne peut entrer dans le paquet. L’export applique liste interdite et liste de
champs structurés autorisés, puis affiche taille, durée et catégories pour confirmation.

## Chemin GitHub privé facultatif

Le flux personnel recommandé utilise un dépôt privé dédié aux diagnostics :

1. GPSx enregistre et valide le paquet localement.
2. L’utilisateur relit le manifeste et confirme explicitement l’envoi.
3. Un identifiant à droits minimaux, jamais intégré à l’APK et protégé par Android
   Keystore, téléverse le paquet comme asset d’une release de test privée.
4. Un workflow valide hashes/schéma et exécute le comparateur Python.
5. Le rapport Markdown/JSON devient un artefact du workflow.

L’identifiant est limité à ce seul dépôt, révocable, absent des logs et exports.
Il expire au plus après 30 jours. Réglages affiche son expiration et une action de
suppression ; la procédure de révocation renvoie aux paramètres de tokens GitHub,
et GPSx traite sa révocation comme une erreur récupérable normale. OAuth/GitHub
App est préférable avant publication ; un token personnel avec `Contents: write`
n’est acceptable que pour le dépôt privé de diagnostics et ne doit jamais accéder
au dépôt source public. Un échec d’envoi ne supprime jamais le paquet local.

Le workflow traite toute archive reçue comme hostile. Avant extraction, il rejette
chemins absolus, composants `..`, liens symboliques/durs, noms dupliqués, plus de
64 entrées, une entrée décompressée dépassant 16 Mio ou un total dépassant 64 Mio.
Il extrait en flux dans un répertoire neuf, n’exécute jamais le contenu, contrôle
liste autorisée et hashes du manifeste, puis recherche des secrets avant analyse.
Des tests adversariaux zip-slip et zip-bomb sont obligatoires.

## Règles de comparaison

Baseline et candidate doivent partager appareil, firmware, classe réseau, scénario,
qualité, codec, durée et fenêtre thermique. Les rapports affichent écarts et,
si possible, intervalles de confiance, puis classent amélioration, régression ou
résultat non concluant. Une moyenne seule ne permet jamais de parler d’optimisation.
