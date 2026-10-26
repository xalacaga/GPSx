# Plan cryptographique de l’enregistrement

Statut : implémentation autorisée le 2026-10-26 ; en attente d’une revue
indépendante avant commit.

Ce lot prépare la plus petite frontière cryptographique portable nécessaire à
un futur enregistrement local PS4/PS5. Il ne formate aucune requête, n’analyse
aucune réponse, n’ouvre aucune socket, ne contacte aucune console, n’accepte
aucun vrai PIN ni identifiant de compte, ne persiste aucun identifiant, ne
réveille aucune console et n’établit aucune session.

## Preuves et périmètre

- **FACT :** le chemin d’enregistrement chiaki-ng épinglé dérive une clé AES de
  16 octets, dérive un IV de 16 octets avec HMAC-SHA-256 et chiffre/déchiffre le
  contenu d’enregistrement avec AES-128-CFB.
- **FACT :** l’enregistrement local exige aussi la transformation `aeropause`.
  PS4 10+ et PS5 sélectionnent les colonnes des deux tables avec des offsets
  dans `0..=31` ; PS4 avant 10 utilise sa transformation fixe séparée, sans
  offset.
- **FACT :** l’IV du chiffrement d’enregistrement vaut
  `HMAC-SHA-256(clé_cible, ambassadeur || compteur_be64)[0..16]`. Trois
  constantes HMAC distinguent PS5, PS4 10+ et PS4 avant 10. L’enregistrement
  utilise uniquement le compteur zéro.
- **FACT :** le comportement amont exact et les vecteurs de compatibilité de ce
  lot se limitent à `lib/include/chiaki/rpcrypt.h`, `lib/src/rpcrypt.c` et
  `test/rpcrypt.c` au commit
  `c8bea2642d0e95402f373af55584a3b98cd89417`.
- **FACT :** ECDH, AES-GCM/GHASH et GKCrypt appartiennent au futur travail de
  session authentifiée et sont exclus de ce lot.
- **INFERENCE :** isoler les primitives d’enregistrement avant le codec et
  l’adaptateur réseau permet de relire séparément la propriété des secrets,
  leur effacement et les échecs de compatibilité.
- **UNKNOWN :** l’interopérabilité avec la PS5 découverte restera non testée
  jusqu’à l’ouverture explicite par l’utilisateur de la porte d’enregistrement
  réel.
- **FACT :** la branche Mbed TLS épinglée réserve 16 octets pour un résultat
  HMAC de 32 octets et déclenche la protection de pile Windows. La fixture
  oracle relue consigne une correction scratch portant le buffer à 32 octets
  avant la troncature existante à 16 octets. GPSx devra employer la taille sûre
  de sortie HMAC Rust et ne jamais porter le buffer sous-dimensionné.

## Fichiers d’implémentation

Seuls ces nouveaux fichiers d’implémentation sont présents :

| Chemin local | Rôle |
|---|---|
| `crates/gpsx-crypto/Cargo.toml` | Déclarations exactes des dépendances et fonctionnalités |
| `crates/gpsx-crypto/src/lib.rs` | Surface publique minimale et types d’erreur |
| `crates/gpsx-crypto/src/registration.rs` | Dérivation d’enregistrement, dérivation d’IV et opération AES-CFB |

Le manifeste du workspace, le lockfile, les registres de provenance, la notice,
les contrôles CI et la politique de dépendances n’ont changé que comme
fichiers d’accompagnement de ces trois fichiers. Aucun codec ni transport
d’enregistrement n’est inclus.

## Sous-ensemble de dépendances adopté

Les dépendances directes auditées sont `aes 0.8.4` avec `zeroize`, `cfb-mode
0.8.2` avec `zeroize`, `hmac 0.12.1`, `sha2 0.10.9` et `zeroize 1.9.1`, toutes
sans leurs fonctionnalités par défaut. `subtle 2.6.1` est une dépendance
transitive attendue via `digest/mac` ; elle n’est pas une dépendance GPSx
directe. Cette crate ne contient aucune dépendance d’aléa.

Le 2026-10-26, le graphe scratch exact a réussi les contrôles Cargo 1.85 sous
Windows et `aarch64-linux-android`, cargo-audit 0.22.2, ainsi que les contrôles
avis, licences, interdictions et sources de cargo-deny 0.20.2. Le commit de la
base d’avis, la liste complète des paquets/sommes, les fonctionnalités, les
résultats des commandes et le SHA-256 du lock scratch sont figés dans
`tests/fixtures/registration_crypto_dependency_audit_v1.json`.
L’implémentation devra produire les mêmes noms, versions, sources, sommes et
fonctionnalités dans le lockfile du dépôt ; tout écart rouvrira la porte d’audit.

## API et invariants des secrets

- La crate est portable, ne fait aucune E/S, interdit `unsafe` et n’a aucune
  dépendance de plateforme.
- Les secrets ont des tailles fixes : ambassadeur, clé et IV de 16 octets. Les
  payloads sont des tranches d’octets empruntées ; cette primitive n’impose pas
  de taille maximale, donc le futur codec devra appliquer sa borne relue.
- Une énumération fermée contient exactement `Ps4Pre10`, `Ps4Modern` et `Ps5`.
  Toute cible inconnue ou toute valeur numérique amont brute est rejetée avant
  le travail cryptographique.
- Le PIN entre dans une enveloppe expurgée, non `Copy` et non `Clone`, construite
  depuis exactement huit chiffres ASCII. Ses octets possédés sont effacés et la
  représentation big-endian temporaire de quatre octets reste dans une portée
  à effacement automatique ; aucune API publique de PIN en `u32` n’est permise.
- L’ambassadeur est un propriétaire expurgé de 16 octets, non `Copy` et non
  `Clone`. Le futur adaptateur de plateforme devra le remplir avec le CSPRNG du
  système, abandonner en cas d’échec d’entropie, ne jamais l’accepter de
  l’utilisateur et ne jamais le réutiliser entre deux tentatives.
- L’API limitée à l’enregistrement fixe le compteur d’IV à zéro en interne et
  n’expose aucun compteur contrôlé par l’appelant. Le chiffrement d’une requête
  consomme le contexte et renvoie un déchiffreur de réponse à usage unique,
  lui-même consommé par le déchiffrement : un contexte chiffre une requête et
  son déchiffreur déchiffre une réponse. Le système de types n’impose pas la
  fraîcheur entre des contextes construits indépendamment. Les clés et IV ne
  sont uniques entre les tentatives que si l’adaptateur de plateforme fournit
  l’ambassadeur CSPRNG frais exigé ci-dessus.
- Les deux offsets des tables modernes sont validés avant indexation : 0 à 31
  sont acceptés et 32 ou plus produit une erreur statique. PS4 avant 10 n’expose
  aucun offset.
- Les propriétaires de secrets ne sont pas `Clone`, expurgent `Debug` et
  `Display`, et effacent leurs buffers possédés à la destruction.
- **HYPOTHESIS / risque résiduel :** déplacer une valeur Rust possédée peut
  laisser des copies dépendantes du compilateur de la clé ou d’octets dérivés
  du PIN dans des emplacements de pile libérés ou des registres, car `Drop` ne
  s’exécute que pour le propriétaire final. L’API réduit les copies explicites
  et efface chaque propriétaire vivant, sans prétendre effacer les copies non
  observables introduites par les déplacements ou l’optimisation.
- Les erreurs sont des énumérations fermées à messages statiques ; elles ne
  contiennent jamais les entrées, clés dérivées, offsets, compteurs ou textes
  chiffrés.
- L’API ne journalise rien et n’expose aucune sérialisation des types secrets.
- `aes/zeroize` et `cfb-mode/zeroize` sont obligatoires afin que les
  planifications de clés AES et l’état IV CFB s’effacent à la destruction ; les
  contextes de chiffrement sont limités à une seule opération.
- Les offsets des tables de clés sont validés avant indexation ; les dimensions
  fixes des tables et la plage validée `0..=31` prouvent que chaque index calculé
  reste dans les bornes. Les additions avec rebouclage d’`aeropause` sont
  l’arithmétique octet définie par le protocole.
- Chiffrement et déchiffrement peuvent traiter toute longueur car CFB est un
  mode flot ; l’appelant possède et doit appliquer la limite maximale du payload.

Les fonctions amont de session et d’enregistrement distant `init_auth`,
`bright_ambassador`, `init_regist_psn`, `aeropause_psn` et
`ambassador_from_aeropause` sont explicitement exclues. ECDH, GKCrypt,
AES-GCM/GHASH, sockets, codecs, stockage, JNI, API Android et accès GP520 le
sont également.

## Tests exigés avant revue

1. Vecteurs AES-128-CFB128 F.3.13 et F.3.14 de NIST SP 800-38A.
2. Cas HMAC-SHA-256 1 à 4, 6 et 7 de RFC 4231, avec troncature explicite du
   résultat de 32 octets aux 16 premiers pour la construction de l’IV.
3. Tous les vecteurs d’enregistrement applicables du `test/rpcrypt.c` épinglé,
   précisément `test_iv_regist_ps4`, `test_iv_regist_ps5`,
   `test_bright_regist_ps4` et `test_bright_regist_ps5`, plus chaque cas de
   `tests/fixtures/registration_crypto_oracle_v2.json` : PS4
   avant 10, puis PS4 10+ et PS5 avec les paires indépendantes d’offsets
   chiffrement/aeropause `(0,31)`, `(31,0)` et `(7,22)`. Chaque valeur
   dérivée, aeropause, IV, texte chiffré et aller-retour de déchiffrement doit
   correspondre bit à bit.
   Les autres vecteurs épinglés de `test/rpcrypt.c` exercent des fonctions
   d’authentification/session explicitement exclues et ne s’appliquent pas à
   cette crate.
4. Tests acceptant chaque offset de 0 à 31 et rejetant 32 avant indexation avec
   une erreur statique.
5. Allers-retours requête/réponse à usage unique pour des tailles nulle,
   inférieure à un bloc, d’un bloc et de plusieurs blocs.
6. Déterminisme entre contextes distincts aux entrées synthétiques identiques,
   formatage expurgé et messages d’erreur statiques.
7. Tests structurels ou de compilation prouvant l’absence de `Clone`,
   l’impossibilité de chiffrer deux requêtes avec le même contexte et l’absence
   de formatage révélant les secrets.
8. `cargo fmt --all --check`, Clippy du workspace avec avertissements interdits,
   tests du workspace, contrôles Rust 1.85, build Windows et cross-build Android
   du cœur.

Les vecteurs sont des données publiques et synthétiques de compatibilité. Ils
ne doivent contenir aucun vrai PIN, identifiant de compte, identifiant de
console, clé d’enregistrement, clé de session, capture privée ni valeur issue de
l’utilisateur.

Pendant l’implémentation, `NOTICE` devra être mis à jour pour les trois sources
chiaki-ng épinglées et chaque dépendance tierce adoptée avant le commit du lot.

## Portes ultérieures

Après un GO indépendant et l’implémentation de ce lot :

1. un codec d’enregistrement pur, relu séparément, pourra formater des requêtes
   bornées et analyser des réponses bornées ;
2. les coffres de secrets de plateforme devront recevoir leur propre revue de
   stockage d’authentification (stockage protégé Windows et adaptateur Android
   Keystore) ;
3. un adaptateur TCP Windows borné pourra être ajouté sans intégrer le stockage
   au cœur portable ;
4. alors seulement l’utilisateur pourra autoriser explicitement une tentative
   réelle, avec le PIN généré par la console saisi interactivement et jamais
   journalisé.

Cette tentative ne rapportera que des résultats classifiés et des durées
agrégées. Elle ne devra jamais afficher ni conserver le PIN, l’identifiant de
compte PSN, la clé d’enregistrement, la clé de session, le contenu déchiffré, la
réponse brute ou une URL contenant un secret.
