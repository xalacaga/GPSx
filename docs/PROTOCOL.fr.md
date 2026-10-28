# Registre de preuves du protocole

Statut : parseur de découverte et adaptateur UDP testés en boucle locale ; codec
d’enregistrement testé unitairement ; l’interopérabilité avec une console reste
`NOT_TESTED`. Aucun code Chiaki n’est copié ou traduit ici.

| Domaine | Statut | Preuve requise |
|---|---|---|
| Découverte | LOOPBACK_TESTED / console NOT_TESTED | Métadonnées expurgées et horodatages |
| Enregistrement | CODEC_UNIT_TESTED / console NOT_TESTED | Résultat et erreur, sans secret |
| Session | NOT_TESTED | Chronologie et métadonnées média |
| Vidéo | NOT_TESTED | Codec, dimensions, FPS, pertes, récupération |
| Audio | NOT_TESTED | Codec, route, underruns, dérive |
| Entrées | NOT_TESTED | Trace normalisée sans identifiant |
| Reconnexion | NOT_TESTED | Injection de panne et transitions |
| Distant | NOT_TESTED | Topologie et résultat classé |

Épingler la révision amont et l’environnement, préférer compteurs et durées aux
contenus, ne garder aucun secret et séparer FACT, INFERENCE et HYPOTHESIS.

## Codec d’enregistrement local

**FAIT :** `gpsx-protocol` contient désormais un codec borné et sans dépendance
pour les quatre cibles du protocole RP relues. Il formate l’identité et
l’enveloppe de requête, lie chaque profil à sa cible crypto, émet la requête HTTP
de compatibilité et analyse strictement les réponses HTTP et d’enregistrement
déchiffrées bornées sans posséder les valeurs porteuses de secrets. Les tests
unitaires et compile-fail couvrent les octets exacts, les incohérences de profil,
les offsets, les limites de taille et de nombre, les doublons, les mauvaises
casses et les entrées arbitraires. Le formateur de compatibilité Online-ID PS4_9
est absent de la production parce qu’il est privé et gardé par `#[cfg(test)]` ;
son doctest compile-fail confirme en plus qu’aucun export public n’existe.

Le codec n’effectue ni E/S ni cryptographie. Sa fixture d’oracle C relue lie
désormais neuf cas complets dans les tests du codec, tandis que `gpsx-crypto`
recalcule indépendamment chaque aeropause et bloc interne chiffré. Le harnais,
le stub, le patch, les logs et le manifeste fermé de construction externes
restent hors du dépôt selon l’option b de R5. **INCONNU :** l’interopérabilité
avec une PS4 ou PS5 réelle, y compris les cibles RP PS4_8 et PS4_9.

## Adaptateur de découverte UDP

`gpsx-transport` est un adaptateur de plateforme écrit pour GPSx autour de l’API
SRCH pure de `gpsx-protocol`. Il envoie exactement une requête par génération de
console et collecte les réponses pendant une fenêtre strictement positive et
limitée à cinq secondes. Le tampon fixe de 65 535 octets couvre toute charge UDP
IPv4 ordinaire : une charge dépassant la limite protocolaire de 1 024 octets atteint le
parseur et est refusée, au lieu d’être tronquée ou transformée en erreur de
socket Windows.

Au plus 16 adresses sources uniques sont conservées. Les 16 premières gagnent ;
les réponses uniques suivantes, datagrammes invalides et doublons sont comptés
séparément. Les erreurs contiennent une opération et un `ErrorKind`, jamais les
octets ou adresses reçus. Le formatage de débogage masque les adresses. La sonde
compte sans arrêt les resets/refus de connexion ICMP. Une réponse issue d’un port
autre que 9302 pour PS5 ou 987 pour PS4 reste acceptée mais est comptée séparément
tant que le comportement des consoles reste inconnu. La sonde affiche l’état, le
statut, le port de requête, le port source local éphémère et des compteurs agrégés,
mais aucune adresse homologue.

Chaque point de terminaison découvert est un indice non authentifié et non
fiable. La découverte seule ne devra jamais autoriser un enregistrement ou une
session : ces futures couches exigeront une authentification indépendante. GPSx
utilise actuellement un port source choisi par le système, tandis que l’amont
épinglé essaie d’abord 9303 à 9319 avant un port aléatoire. Le premier essai
console autorisé consignera ce port comme preuve.

Les tests en boucle locale constituent un FACT uniquement pour l’hôte Windows et
sa pile UDP locale. Aucun paquet n’a été envoyé vers une PS4, une PS5 ou le
GP520 : la découverte de console reste NOT_TESTED. La sonde exige l’argument
explicite `--live` avant toute émission en diffusion limitée.

