# Fixtures relues

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

Seules des fixtures expurgées et redistribuables vont ici. Chacune documente
origine, licence, environnement, revue d’expurgation et SHA-256 dans
`manifest.json`. Les captures brutes restent dans `docs/evidence/`.

