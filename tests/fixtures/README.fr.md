# Fixtures relues

## Oracle LaunchSpec v1

`launch_spec_oracle_v1.json` contient six cas synthétiques publics couvrant la
PS4 avant le firmware 10, la PS4 moderne et la PS5 en AVC, HEVC et HDR. Chaque
cible possède un cas avec six champs numériques distincts ; un autre cas permute
`0` et `u32::MAX` ; les cas maximum et `u32::MAX - 1` restent présents. La PS4
moderne et la PS5 utilisent des nonces dont les deux sélecteurs de ligne de table
diffèrent. Le JSON canonique, le NUL terminal, l’IV du compteur zéro, le flot
produit par chiffrement CFB d’un buffer nul, le chiffré après XOR et la base64
externe sont consignés séparément. Le test Rust reconstruit le document entier,
exige l’égalité octet par octet et compare chaque cas au chemin public de
production.

Le harnais externe a compilé directement `launchspec.c`, `rpcrypt.c` et
`base64.c` épinglés au commit chiaki-ng
`c8bea2642d0e95402f373af55584a3b98cd89417`, avec le commit Mbed TLS
`8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` et MSVC 19.44.35229.0. L’unique
correction limitée au scratchpad agrandit de 16 à 32 octets le buffer de sortie
SHA-256 sous-dimensionné connu de `rpcrypt.c`, sans modifier le résultat de
16 octets conservé. L’allocation du buffer nul, l’inclusion du NUL terminal,
l’appel du flot CFB et la boucle XOR sont du code de harnais GPSx transcrit de
`streamconnection.c:1027-1036` ; `streamconnection.c` sert de preuve pour cet
ordre et n’est ni compilé ni exécuté par cet oracle. Le relevé externe fermé de
construction, le harnais, les
produits de compilation et les checkouts restent sous
`target/launchspec-audit/`, ignoré, et ne doivent pas entrer dans le dépôt.

Les libellés neutres `public_input_a_16`, `public_input_b_16` et
`public_input_c_16` désignent le nonce synthétique, le matériau `morning` et la
clé de handshake ; les API de production conservent leurs noms typés porteurs
de secrets.

La fixture mesure 55 602 octets et son SHA-256 est
`84487e526ac8625dcb555c447916e167cffb39eb4782ee31ca78d467ca740599`.
Elle est intégrée après le `GO` de la contre-revue indépendante de
l’implémentation le 2026-10-28 ; elle ne porte aucune revendication console,
performance, émulateur ou matériel.

## Oracle du codec d’enregistrement v1

`registration_codec_oracle_v1.json` contient neuf cas déterministes générés à
partir d’entrées synthétiques publiques. Les cinq cas au remplissage par défaut
sont comparés octet par octet avec le
`chiaki_regist_request_payload_format` amont épinglé ; les quatre cas au
remplissage personnalisé sont assemblés par le harnais externe à partir des
primitives amont épinglées `init_regist`, aeropause et chiffrement, car le
formateur amont impose le remplissage `A`.

Les clés d’entrée `online_name` et `identity_bytes` sont des libellés neutres
de fixture pour l’OnlineId et l’AccountId synthétiques. Elles évitent le
classement volontairement conservateur des clés `online_id` / `account_id` par
le scanner du dépôt, tandis que le champ `identity` de chaque cas continue de
lier exactement les variantes du protocole.

La génération emploie le commit chiaki-ng
`c8bea2642d0e95402f373af55584a3b98cd89417`, le commit Mbed TLS
`8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` et MSVC
`19.44.35229.0`. La sortie mesure 28 469 octets et son SHA-256 est
`13b3d768d861888d52d4aa9d86cb1ba2a09de701f85288534706734727064dd4`.
La sortie texte Windows a produit des fins de ligne CRLF ; `.gitattributes`
marque les fixtures JSON `-text`, ce qui conserve ces octets relus au checkout.

Le harnais de génération, le stub de version extrait, le patch amont, les logs
du compilateur et le manifeste fermé de construction restent externes selon
l’option b de R5 et ne doivent jamais être ajoutés à ce dépôt. La fixture ne
porte aucune revendication matérielle ou d’interopérabilité.

## Oracle cryptographique d’enregistrement v2

`registration_crypto_oracle_v2.json` ne contient que des entrées synthétiques
publiques. Ses cas modernes utilisent volontairement les paires indépendantes
d’offsets chiffrement/aeropause `(0,31)`, `(31,0)` et `(7,22)` afin qu’une
implémentation ne puisse pas permuter ou fusionner silencieusement les deux
champs du protocole. Dans chaque cas, `derived_16` représente le contenu de chiffrement
d’enregistrement de 16 octets, `mix_16` le résultat aeropause,
`initial_vector_16` le résultat HMAC-SHA-256 tronqué pour le compteur zéro et
`encrypted` la sortie AES-128-CFB. Ces libellés neutres empêchent l’outillage de
prendre des vecteurs publics synthétiques pour de vrais identifiants ; les API
de production devront utiliser des noms et types porteurs de secrets.

La branche Mbed TLS épinglée du `rpcrypt.c` amont écrit un résultat SHA-256 de
32 octets dans un tableau de 16 octets. L’oracle scratch a reproduit l’arrêt de
la protection de pile Windows. La génération des vecteurs a donc appliqué une
unique correction, uniquement dans le scratchpad : agrandir ce tableau à
32 octets, tandis que la copie existante conserve les 16 premiers octets. GPSx
ne devra pas porter le buffer sous-dimensionné.

## Audit des dépendances de cryptographie de session v1

`session_crypto_dependency_audit_v1.json` lie le graphe exact de 30 paquets
crates.io résolu pour `gpsx-crypto`, les fonctionnalités des dépendances
directes, les checksums du registre, l’environnement de contrôle Windows et
Android sous Rust 1.85, ainsi que les résultats des audits sécurité/licence. Il
consigne aussi le rejet initial de la famille `k256` et son remplacement par
`secp256k1` 0.33.1, et distingue les métadonnées CC0-1.0 des crates du code C
libsecp256k1 vendu et patché sous MIT. Il ne contient que des métadonnées de dépendances et ne
constituait uniquement l’audit des dépendances, pas l’oracle indépendant de
cryptographie de session décrit ci-dessous.

## Oracle de cryptographie de session v1

`session_crypto_oracle_v1.json` contient des vecteurs synthétiques publics pour
l’exemple ECDH amont épinglé, les deux modes keystate, les deux directions de
trafic, les frontières d’époque et de position, les débordements arithmétiques,
les limites de politique, les tags AES-GCM complets, leurs MSB32 protocolaires
et les éléments internes du XOR replié. Le test Rust reconstruit le document
canonique complet, exige l’égalité des octets et rejette les mutations de chaque
champ de premier niveau ainsi que de tous les champs auparavant non liés.

La génération a exécuté directement les six tests `gkcrypt.c` épinglés
compatibles et les deux tests `keystate.c`. Un calcul affine secp256k1 écrit par
GPSx uniquement dans le scratchpad, avec le HMAC-SHA-256 de la bibliothèque
standard Python, reproduit le vecteur ECDH épinglé. L’implémentation Mbed TLS
épinglée de chiaki régénère le scalaire fourni à son setter déterministe de clé
locale ; ce chemin de test ECDH ne reproduit donc pas le vecteur épinglé. Le
relevé externe conserve ce diagnostic comme sortie 2 attendue et aucune
correction scratch n’est employée pour la fixture.

La sortie mesure 12 764 octets et son SHA-256 est
`eb67d9123a592b064a7799bf12e36b274f425683e322f83c9f440440cb23f8e9`.
Son manifeste externe fermé consigne deux checkouts épinglés propres, 81 unités
de traduction compilées, 313 en-têtes observés, les commandes exactes, les
versions d’outils, les hashes des exécutables et ceux des résultats
déterministes. Le manifeste, le journal compilateur, le harnais et les
checkouts restent sous `target/session-audit/`, ignoré, et ne doivent jamais
entrer dans ce dépôt. La contre-revue indépendante C1 a rendu `GO` le
2026-10-28 ; cette fixture est donc intégrée. Elle ne porte aucune revendication
console, performance, émulateur ou matériel.

Seules des fixtures expurgées et redistribuables vont ici. Chacune documente
origine, licence, environnement, revue d’expurgation et SHA-256 dans
`manifest.json`. Les captures brutes restent dans `docs/evidence/`.

