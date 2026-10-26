# Fixtures relues

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

