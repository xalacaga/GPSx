# Plan de sécurité protocolaire CR3

Statut : GO limité accepté le 2026-10-25 pour le seul lot SRCH pur.

Le premier lot proposé est un formateur de découverte et un parseur de réponse
purs, bornés en allocation, dans `gpsx-protocol`. Il ne réalise ni accès réseau,
ni enregistrement, authentification, cryptographie, média ou accès GP520. Les
fichiers chiaki-ng exacts sont gelés dans `PROVENANCE.md` au commit
`c8bea2642d0e95402f373af55584a3b98cd89417`.

## Frontière de confiance et limites des parseurs

Tout octet réseau est non authentifié et hostile jusqu’à preuve contraire par une
future couche de session authentifiée. Les limites font partie de l’API et sont
contrôlées avant allocation ou indexation. L’arithmétique contrôlée est obligatoire
et la récursion interdite.

| Entrée | Limite stricte | Politique d’échec | Statut |
|---|---:|---|---|
| Datagramme UDP de découverte | 1 024 octets | Refus du datagramme entier | ACCEPTÉ CR3 |
| Nombre d’en-têtes de découverte | 32 | Refus du datagramme entier | ACCEPTÉ CR3 |
| Ligne de découverte | 512 octets | Refus du datagramme entier | ACCEPTÉ CR3 |
| Futurs en-têtes HTTP d’enregistrement | 32 Kio / 128 champs | Fermer l’essai ; aucun secret partiel conservé | PRÉVU, hors premier lot |
| Futur corps d’enregistrement | 1 Mio | Fermer l’essai ; effacer les secrets partiels | PRÉVU, hors premier lot |
| Futur datagramme UDP chiffré | 2 048 octets | Abandon et erreur classée comptabilisée | PRÉVU, hors premier lot |
| Futur ensemble de fragments média | 64 fragments / 4 Mio agrégés | Abandon de l’image et demande de récupération si disponible | PRÉVU, hors premier lot |
| Future fenêtre de réordonnancement | 128 paquets / 250 ms | Éviction du plus ancien et perte bornée enregistrée | PRÉVU, hors premier lot |

Le premier lot conserve les lignes de statut et noms d’en-têtes en ASCII,
accepte les valeurs d’en-têtes UTF-8 valides sans caractère de contrôle, refuse
NUL intégré ou répété, champs de sécurité dupliqués, débordement numérique et
octets finaux non analysés, et ne journalise jamais les datagrammes ou
identifiants bruts. Un NUL terminal unique est accepté et retiré par précaution
d’interopérabilité explicite.

## Comportement SRCH autorisé

Seuls `gpsx-protocol` et ses quatre lignes de provenance sont autorisés. La
crate ne possède aucune dépendance ni script de build, interdit le code unsafe et
n’effectue aucune E/S. WAKEUP, identifiants, sockets, enregistrement,
authentification, cryptographie, sessions, Takion, médias, JNI, protocole Kotlin,
captures réelles et travaux GP520 restent interdits.

Le parseur accepte les fins de ligne CRLF, LF et CR ainsi qu’une dernière ligne
complète sans fin de ligne. Il retire exactement un espace facultatif après les
deux-points, refuse les valeurs vides, caractères de contrôle et NUL non
terminal ou répété, ignore les clés inconnues et refuse
les doublons `host-id`, `host-request-port`,
`device-discovery-protocol-version` et `host-type`. Pour les autres champs
connus dupliqués, la première valeur gagne. Les ports sont des décimaux stricts
de 1 à 65535. Ce sont des durcissements volontaires par rapport au C épinglé,
qui tronque au premier NUL, abandonne la dernière ligne sans fin, convertit
partiellement les ports en base 0 et conserve le premier doublon d’entrée par
parcours inversé. GPSx accepte aussi les valeurs UTF-8 et un NUL terminal unique,
compare les noms d’en-têtes sans tenir compte de la casse, accepte une réponse
limitée au statut et les zéros initiaux d’un statut non nul, et refuse un double
séparateur du statut, LF-CR suivi de données et les espaces dans le port.

## Décision cryptographique pour les lots ultérieurs

GPSx n’implémentera pas lui-même AES, le mode CFB, AES-GCM/GHASH, SHA-256, HMAC,
ECDH secp256k1, la dérivation de clé, l’aléatoire, la comparaison en temps
constant ou l’effacement. Le registre candidat couvre `aes`, `cfb-mode`, `hmac`,
`sha2`, `subtle`, `zeroize`, `secrecy`, `getrandom` adossé à la plateforme et
des familles maintenues encore non résolues pour AES-GCM/GHASH et ECDH secp256k1. Les versions candidates,
licences, preuves MSRV et la gate d’avis encore ouverte sont consignées dans
`CRYPTO_DEPENDENCIES.md`. Les sommes exactes devront être épinglées et auditées avant la création de `gpsx-crypto`. Si le mode requis ne
peut pas être représenté sûrement par des bibliothèques maintenues, le travail
s’arrête pour une nouvelle revue ; aucun repli maison n’est permis.

Les secrets utilisent des enveloppes dédiées non `Clone` et expurgées, sont effacés
à la destruction lorsque la dépendance le permet, ne traversent jamais les API de
logs/télémétrie et sont exclus des paniques, fixtures et diagnostics. Les tests de
compatibilité emploient seulement des vecteurs publics ou synthétiques.

## Plan de fuzzing

- `discovery_response` : tranches arbitraires de 0 à 1 025 octets ; absence de
  panique, d’allocation excessive et erreurs classées déterministes.
- `http_response` : entrées fragmentées, champs dupliqués, séparateurs invalides,
  NUL intégré, nombres extrêmes et toutes les frontières entières.
- Cibles ultérieures : enveloppe d’enregistrement, trame de contrôle, framing
  chiffré, assemblage des fragments et fenêtre de réordonnancement.
- Ce lot utilise des tests de propriété déterministes sans dépendance sur des
  vecteurs arbitraires de chaque longueur de 0 à 1 025. L’organisation standard
  `cargo-fuzz` exige une modification du garde de provenance à revoir séparément
  et reste une gate Linux ultérieure.
- Campagnes avec sanitizers lorsque disponibles. Chaque crash devient un test de
  régression déterministe avant avancement du parseur.

## Preuves exigées pour CR3

- Revue indépendante de ce plan, de l’inventaire exact et de la licence.
- Tests à chaque frontière : limite moins un, limite et limite plus un.
- Tests de propriétés pour déterminisme et aller-retour formateur/parseur.
- Parcours déterministe d’entrées arbitraires pour chaque longueur de 0 à 1 025 ;
  la preuve cargo-fuzz est reportée à sa gate Linux relue séparément.
- Registre licence/avis des dépendances avant tout ajout cryptographique.
- Fixtures synthétiques avec provenance complète et scan de secrets propre.

Le GO accepté n’a créé que `gpsx-protocol`. `gpsx-transport` a ensuite reçu un
GO séparé et existe désormais. Le lot `gpsx-crypto` limité à l’enregistrement a
été autorisé le 2026-10-26 et attend sa revue d’implémentation ; `gpsx-session`
reste absente en attente d’une revue explicite ultérieure.
