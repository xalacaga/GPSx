# Transport de découverte UDP

Statut : implémenté sous Windows et Android, et testé en boucle locale et sur
l’émulateur Android ; l’interopérabilité avec une vraie PS4/PS5 reste NOT_TESTED.

## Périmètre

Cette tranche ajoute le premier adaptateur d’E/S hors du cœur portable. La crate
`gpsx-transport` dépend de `gpsx-protocol` ; aucune crate du cœur portable ne
dépend du transport. Seule la découverte SRCH en IPv4 est prise en charge.

L’adaptateur limité à Android expose un appel JNI grossier pour la découverte
PS5. Il n’implémente ni WAKEUP, ni authentification, ni stockage d’identifiants,
ni cryptographie, ni média, ni accès GP520.

## Intégration dans l’interface Android

L’écran Session lance une recherche PS5 bornée à 1,2 seconde sur son worker
existant. Kotlin détient temporairement un verrou multicast Wi-Fi non
reference-counted et l’adaptateur Rust Android appelle la même implémentation
`gpsx-transport` que le CLI. Le premier résultat IPv4 ne fait que préremplir le
champ d’hôte : c’est un indice non authentifié et l’enregistrement reste chargé
d’authentifier la console.

L’AVD Google TV standard ne transfère pas les broadcasts IPv4 limités entre son
réseau virtuel et le LAN physique. GPSx sélectionne donc un bridge réservé à
l’émulateur lorsque Android signale `ranchu`, `goldfish`, un produit SDK ou un
modèle émulateur. L’AVD envoie une requête fixe vers l’alias hôte
`10.0.2.2:49302` ; le helper hôte écoute uniquement sur `127.0.0.1:49302`,
effectue le broadcast LAN borné et ne renvoie que la première adresse IPv4.
Aucune donnée d’enregistrement, aucun identifiant et aucun trafic de stream ne
traverse ce bridge. Un appareil Android réel utilise toujours le broadcast UDP
direct.

Les deux bridges émulateur se lancent avec :

```powershell
tools/powershell/start-android-emulator-bridges.ps1
```

Le 29 octobre 2026, ce bridge a découvert la vraie PS5 à `192.168.1.50` depuis
l’AVD Google TV API 36. Cette preuve ne vaut que pour cet hôte et ce LAN et ne
constitue pas une preuve GP520.

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
