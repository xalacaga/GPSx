# Transport de découverte UDP Windows

Statut : implémenté et testé en boucle locale ; l’interopérabilité PS4/PS5 reste
NOT_TESTED.

## Périmètre

Cette tranche ajoute le premier adaptateur d’E/S hors du cœur portable. La crate
`gpsx-transport` dépend de `gpsx-protocol` ; aucune crate du cœur portable ne
dépend du transport. Seule la découverte SRCH en IPv4 est prise en charge.

Elle n’implémente ni WAKEUP, ni enregistrement, ni authentification, ni stockage
d’identifiants, ni cryptographie, ni session, ni média, ni manette, ni JNI, ni
accès Android ou GP520.

## Bornes et comportement en cas d’échec

- Un datagramme SRCH est envoyé pour chaque génération de console demandée.
- La fenêtre de réponse est strictement positive et limitée à cinq secondes ; la
  sonde utilise 750 millisecondes pour la PS5, puis 750 pour la PS4.
- Le stockage de réception est une allocation fixe unique de 65 535 octets,
  couvrant toute charge UDP IPv4 ordinaire, afin que les charges hors limite atteignent
  le parseur et soient refusées sous Windows comme sous Unix.
- Au plus 16 consoles uniques sont conservées. La première réponse par génération
  et adresse IP gagne ; les doublons et dépassements suivants sont comptés.
- Les paquets invalides sont comptés puis ignorés. Une erreur de socket contient
  une opération statique et un `std::io::ErrorKind`, sans contenu ni adresse.
- `ConnectionReset` et `ConnectionRefused`, qui peuvent représenter un port ICMP
  inaccessible, ne sont pas fatals et alimentent `icmp_errors` ; la collecte se
  poursuit jusqu’à son échéance. Toute autre erreur de réception renvoie `Err`
  sans restituer le rapport partiel.
- Les réponses valides dont le port source diffère du port de découverte de la
  génération sont conservées, mais comptées dans `unexpected_source_ports`.
- Les adresses homologues sont disponibles pour la future couche de transport,
  mais masquées dans `Debug` ; la sonde ne les affiche pas. Elle affiche le port
  source local éphémère et uniquement des compteurs agrégés de réponses.

Les réponses de découverte sont des indices non authentifiés et non fiables. Un
hôte du réseau local peut usurper une réponse ou occuper une des 16 places. Une
future couche d’enregistrement ou de session ne devra jamais faire confiance à
un pair découvert sans effectuer ses propres contrôles d’authentification et
d’identité.

GPSx utilise actuellement un port source éphémère choisi par le système. Cela
diffère volontairement de l’implémentation amont épinglée, qui essaie d’abord les
ports 9303 à 9319 avant de revenir à un port choisi par le système. On ignore si
les consoles ou des configurations courantes du pare-feu Windows dépendent de
cette plage préférée. Le premier essai réel autorisé devra consigner le port
source local, le nombre d’écarts de port source, la décision du pare-feu et le
résultat classé, sans conserver les adresses homologues ni le contenu des paquets.

## Validation locale

La commande sûre suivante affiche seulement l’aide et n’effectue aucune E/S
réseau :

```powershell
cargo run -p gpsx-transport --bin gpsx-discover -- --help
```

La commande réelle est volontairement exclue des tests automatisés. Elle diffuse
sur le réseau local et ne doit être exécutée qu’après une autorisation explicite :

```powershell
cargo run -p gpsx-transport --bin gpsx-discover -- --live
```

Le pare-feu Windows peut demander si le nouvel exécutable peut recevoir des
réponses UDP locales. Refuser est sans danger ; la découverte peut alors trouver
zéro console. La commande ne réveille et n’enregistre aucune console, et ne
conserve aucune sortie.

## Frontière des preuves

Les tests en boucle locale prouvent les octets de requête, le délai borné, la
poursuite après une réponse invalide, la fusion des doublons, la politique de
capacité et le masquage des adresses sur l’hôte Windows. Ils prouvent aussi que
les resets ICMP Windows sont comptés sans perdre le rapport. Ils ne prouvent ni
la compatibilité console, ni le routage de diffusion, ni les ports sources des
consoles, ni le pare-feu d’une autre machine, ni le moindre comportement du GP520.

La suite de tests du transport a réussi 50 exécutions consécutives sous charge
CPU locale après le passage des fenêtres de réponse à 500 millisecondes. Cette
preuve de stabilité ne vaut que pour cet hôte Windows et ne constitue aucune
affirmation de performance.
