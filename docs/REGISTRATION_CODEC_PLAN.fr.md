# Plan du codec d'enregistrement

Statut : la remédiation A1/A2 du codec a reçu `GO`. L’intégration de l’oracle a
reçu `GO WITH CHANGES` ; B1-B4 sont implémentés dans l’arbre de revue non
commité et doivent recevoir le contrôle indépendant requis avant le commit de
la fixture.

Ce document délimite un codec pur et borné pour l'enregistrement local d'une console. Il exclut sockets, nouvelles tentatives, persistance, API de plateforme, PSN distant, RUDP, perforation NAT, identifiants réels et accès au matériel.

## Preuves et provenance (C5)

- **FAIT :** `gpsx-protocol` est la couche de protocole portable et doit rester indépendante de `gpsx-crypto`, du transport et des crates de plateforme.
- **FAIT :** la référence chiaki-ng épinglée est le commit `c8bea2642d0e95402f373af55584a3b98cd89417`, sous `LicenseRef-AGPL-3.0-only-OpenSSL`.
- **FAIT :** l'inventaire amont approuvé contient `lib/include/chiaki/regist.h`, `lib/src/regist.c`, `lib/src/session.c` et `test/regist.c`.
- **FAIT :** base64 sera écrit par GPSx à partir de l'alphabet/l'algorithme de la section 4 de la RFC 4648 et des vecteurs de la section 10, tout comme les parseurs hexadécimal et décimal stricts. `lib/src/base64.c` et `lib/src/utils.h` ne sont donc pas des sources portées de l'implémentation.
- **FAIT :** aucun code des chemins amont proposés ne peut être copié, traduit ou adapté avant un `GO` indépendant sur le plan révisé et les entrées de provenance proposées.
- **INCONNU :** l'interopérabilité avec une vraie PS4 ou PS5, notamment avec les cibles RP PS4_8 et PS4_9.

La ligne de registre dérivé proposée couvre `request_head_fmt`, `request_path`, `request_header_format`, la branche sans perforation NAT de `chiaki_regist_request_payload_format`, `regist_parse_response_payload` et uniquement les constantes de `chiaki_rp_version_string` nécessaires aux quatre profils. Elle exclut explicitement `regist_search` UDP, sockets, threads, stop pipes, délais, journalisation, enregistrement PSN, perforation NAT, RUDP, persistance et tout autre code de session.

Les observations de correspondance profil/firmware dans `gui/src/qmlbackend.cpp` et `android/app/src/main/java/com/metallic/chiaki/regist/RegistActivity.kt` sont des preuves de référence uniquement au commit épinglé. Ces fichiers ne sont ni des sources portées ni des entrées proposées de l'inventaire.

## Frontière entre couches et politique d'allocation

Le module implémenté est `crates/gpsx-protocol/src/registration.rs`.

Il n'effectue aucune E/S et expose seulement des opérations déterministes sur des tampons fixes fournis par l'appelant :

1. formater la requête interne en clair et renvoyer sa longueur ;
2. préparer le préfixe de requête `0x1e0` et renvoyer sa disposition dérivée ;
3. placer les octets opaques d'aeropause et un bloc interne déjà chiffré ;
4. formater l'en-tête HTTP externe autour de la charge opaque terminée ;
5. cadrer et valider une réponse HTTP externe en empruntant son corps chiffré ;
6. analyser le bloc de champs déchiffré sous forme de vue empruntée.

Le formateur ne crée ni ne fait croître `Vec`, `String`, `Box`, `Cow`, map ou autre collection sur le tas, et le module n'utilise ni `vec!`, ni `format!`, ni `collect`. Le parseur ne copie aucune valeur porteuse de secret. Le parseur de découverte existant dans `http.rs` reste inchangé ; l'enregistrement utilise une fonction de cadrage CRLF stricte distincte, car la découverte accepte volontairement d'autres fins de ligne et aucun corps binaire.

Le codec ne chiffre ni ne déchiffre. Une couche d'orchestration ultérieure le combine à la typestate d'enregistrement à usage unique de `gpsx-crypto` et à un transport relu séparément.

## Propriété et traitement des secrets (C6)

- `OnlineId<'a>(&'a str)` et `AccountId<'a>(&'a [u8; 8])` sont des enveloppes validées qui empruntent le stockage de l'appelant et ne contiennent que des références. Elles ne copient aucun octet d'identité et n'implémentent ni `Debug`, `Display`, `Clone`, sérialisation ou conversion en chaîne possédée.
- Le formateur en clair écrit directement dans un tampon fixe fourni par l'appelant et renvoie sa longueur. Une capacité insuffisante produit une erreur statique expurgée et aucun résultat partiellement fiable.
- Le parseur externe renvoie une vue qui emprunte le tampon de réponse. Le parseur déchiffré renvoie des tranches secrètes encodées empruntées, du texte validé emprunté et des scalaires.
- Les types de résultat porteurs de secrets n'implémentent ni `Debug`, `Display`, `Clone` ou sérialisation.
- Les erreurs sont des catégories statiques expurgées sans octets d'entrée, valeurs d'en-tête, clés, identifiants, pseudonyme, texte applicatif ou redirection.
- L'appelant possède et remet à zéro les stockages en clair et déchiffrés. Les propriétaires de secrets appartiennent à la frontière crypto/session.

L'identifiant de compte, l'identifiant en ligne, le bloc en clair, l'aeropause et le corps chiffré ne traversent jamais journaux, paniques, télémétrie, fixtures avec données réelles ou archives de diagnostic. Des tests structurels/de non-compilation couvrent les traits interdits des enveloppes d'identité empruntées et de chaque type de résultat public porteur de secrets. Un scan de symboles interdits dans `registration.rs` pour `Vec`, `String`, `ToOwned`, `to_owned`, `to_string`, `vec!`, `Box`, `format!`, `collect`, `Cow`, `HashMap` et `BTreeMap`, suivi d'une revue de chaque site d'allocation des formateurs/parseurs, impose l'absence d'allocation extensible.

**INFÉRENCE :** les vues empruntées et tampons fixes minimisent les duplications évitables de secrets tout en préservant l'absence de dépendance de la crate. Les copies générées par le compilateur dans des registres ou sur la pile ne peuvent être exclues et ne sont pas déclarées remises à zéro.

## API de requête et invariants de disposition (C1)

La requête est construite sans canal latéral d'offset :

1. `prepare_request_prefix(profile, filler, plaintext_len, output)` reçoit exactement `0x1e0` octets de remplissage fournis par l'appelant, les copie dans un tampon de sortie fixe et renvoie un jeton opaque contenant la longueur en clair. Pour la cible protocolaire PS4_10 et PS5, ce jeton expose `cipher_offset = filler[0x18d] & 0x1f` et `aeropause_offset = filler[0] >> 3` comme simples valeurs `u8`. Pour les cibles PS4_8 et PS4_9, il expose la disposition fixe antérieure à 10 sans offset de table.
2. `finish_request_payload(prepared, aeropause, encrypted_inner)` consomme ce jeton et rejette un bloc chiffré dont la longueur diffère de la longueur en clair conservée. Pour les cibles PS4_8/PS4_9, il écrit les 16 octets opaques d'aeropause à `0x11c`. Pour la cible PS4_10/PS5, il écrit `aeropause[8..16]` à `0xc7` et `aeropause[0..8]` à `0x191`, puis ajoute le bloc interne opaque chiffré à `0x1e0`.

Le jeton opaque conserve l'emprunt exclusif de la sortie préparée ; le Rust sûr ne peut donc modifier le remplissage entre la dérivation des offsets et le placement final. Les régions modernes `[0xc7, 0xcf)` et `[0x191, 0x199)` ne chevauchent pas les octets sources `0` et `0x18d` ; la région antérieure à 10 `[0x11c, 0x12c)` ne les chevauche pas non plus. Ces invariants sont des constantes vérifiées par assertions et tests.

Le codec accepte des remplissages arbitraires de fixture pour tester la dérivation. La première politique d'intégration passe `[0x41; 0x1e0]`, seul remplissage couvert par le vecteur amont épinglé. Un remplissage aléatoire n'est pas activé au seul motif que le commentaire amont indique « can be random » ; son acceptation par les consoles est **INCONNUE** et exige une preuve matérielle ultérieure.

Seuls les octets après `0x1e0` sont chiffrés. Le préfixe reste en clair. Le `Content-Length` externe vaut exactement `0x1e0 + encrypted_inner.len()`, avec arithmétique vérifiée, et égale la longueur finale de la charge.

## Profils et constantes fixes de requête (C2)

Le codec utilise quatre profils de cible du protocole RP. Leurs noms ne désignent pas la version du firmware de la console. PS4_8 n'est pas cachée dans un profil générique antérieur à 10.

| Profil codec | Chemin POST | `RP-Version` | Identité interne | `Client-Type` | `gpsx_crypto::RegistrationTarget` |
| --- | --- | --- | --- | --- | --- |
| `Ps4_8` | `/sce/rp/regist` | absent | `Np-Online-Id` | `Windows` | `Ps4Pre10` |
| `Ps4_9` | `/sce/rp/regist` | `9.0` | `Np-AccountId` en base64 RFC 4648 ; `Np-Online-Id` uniquement en test pour le vecteur épinglé | `Windows` | `Ps4Pre10` |
| `Ps4_10Target` | `/sie/ps4/rp/sess/rgst` | `10.0` | `Np-AccountId` en base64 RFC 4648 | constante de 64 caractères ci-dessous | `Ps4Modern` |
| `Ps5` | `/sie/ps5/rp/sess/rgst` | `1.0` | `Np-AccountId` en base64 RFC 4648 | constante de 64 caractères ci-dessous | `Ps5` |

L'orchestrateur doit effectuer une correspondance exhaustive sur ces quatre valeurs et rejeter toute paire profil codec/cible crypto incohérente avant formatage ou chiffrement. Les appels du formateur interne et de préparation du préfixe ne sont pas liés par un même jeton typé ; jusqu'à une modification d'API relue séparément, l'orchestrateur doit propager un seul profil sélectionné dans les deux appels et tester qu'aucune séquence à profils mélangés n'est possible.

| Élément | Valeur exacte | Preuve |
| --- | --- | --- |
| Ligne de requête | `POST <chemin-du-profil> HTTP/1.1\r\n` | `request_head_fmt` épinglé |
| Anomalie de compatibilité | ` HTTP/1.1\r\n`, espace initial inclus | `request_head_fmt` épinglé |
| Hôte | `HOST: 10.0.2.15\r\n` | valeur GPSx fixe correspondant à l'appel amont ; aucune adresse LAN en entrée |
| Agent utilisateur | `User-Agent: remoteplay Windows\r\n` | `request_head_fmt` épinglé |
| Connexion | `Connection: close\r\n` | `request_head_fmt` épinglé |
| Longueur | `Content-Length: <décimal canonique>\r\n` | longueur de la charge terminée |
| Version facultative | `RP-Version: <valeur-du-profil>\r\n` | absente uniquement pour `Ps4_8` |
| Fin d'en-tête | `\r\n` | fin de requête épinglée |
| Type client moderne | `dabfa2ec873de5839bee8d3f4c0239c4282c07c25c6077a2931afcf0adc0d34f` | `client_type` épinglé |
| Bloc interne PS4_8 / compatibilité PS4_9 | `Client-Type: Windows\r\nNp-Online-Id: <identifiant-validé>\r\n` | `request_inner_online_id_fmt` épinglé |
| Bloc interne PS4_9 normal | `Client-Type: Windows\r\nNp-AccountId: <12 caractères base64>\r\n` | formateur d'identifiant de compte épinglé avec type client antérieur à 10 |
| Bloc interne moderne | `Client-Type: <type-client-moderne>\r\nNp-AccountId: <12 caractères base64>\r\n` | `request_inner_account_id_fmt` épinglé |
| Taille du préfixe en clair | `0x1e0` octets | offset interne épinglé |
| Placement aeropause avant 10 | 16 octets à `0x11c` | formateur sans perforation NAT épinglé |
| Placement aeropause moderne | seconde moitié `[8..16]` à `0xc7`, première moitié `[0..8]` à `0x191` | formateur sans perforation NAT épinglé |
| Compteur crypto interne | `0` | appels de chiffrement/déchiffrement épinglés |

GPSx reproduit octet pour octet la ligne de compatibilité apparemment mal formée. La tolérance des consoles à son remplacement par du HTTP valide est **INCONNUE** ; elle ne changera pas sans nouveau vecteur d'oracle et preuve matérielle ultérieure.

`Ps4_8` accepte uniquement l'identifiant en ligne. En production, `Ps4_9` utilise l'identifiant de compte de huit octets avec `Client-Type: Windows` ; un constructeur privé `#[cfg(test)]` accepte son vecteur OnlineId épinglé et est absent des builds de production. `Ps4_10Target` et `Ps5` acceptent uniquement l'identifiant de compte. La grammaire de l'identifiant en ligne compte 1 à 16 octets ASCII de `[A-Za-z0-9_-]` ; c'est un durcissement local et l'ensemble complet accepté par les consoles réelles est **INCONNU**. Les identifiants de compte sont encodés en exactement 12 caractères base64 RFC 4648 avec bourrage. PS4_8 avec AccountId, PS4_9 de production avec OnlineId et cible moderne avec OnlineId sont rejetées.

Les appelants de référence associent un firmware inférieur à 7.0 à la cible PS4_8, de 7.0 inclus à 8.0 exclu à PS4_9, et 8.0 ou ultérieur à la cible PS4_10. Le GUI utilise aussi l'identifiant en ligne seulement pour PS4_8 et l'identifiant de compte pour toute autre cible. Ce mapping sert de preuve de sélection de cible, pas de code à porter.

L'ancienne ligne « authentificateur de session » est supprimée : elle appartient à un autre flux et n'a pas de source dans l'enregistrement local.

## Cadrage et limites de l'enregistrement (C4)

Toutes les limites sont vérifiées avant allocation, indexation, arithmétique ou conversion. L'arithmétique est vérifiée.

| Élément | Limite ou règle stricte | Comportement en cas d'échec |
| --- | --- | --- |
| En-tête de requête externe | 512 octets, `\r\n\r\n` inclus | rejet avant dépassement du tampon appelant |
| Bloc d'en-têtes de réponse externe | 1 024 octets, premier `\r\n\r\n` inclus | fermeture de la tentative |
| Corps de réponse externe | 1 024 octets | fermeture ; l'appelant remet le stockage secret à zéro |
| Réponse externe complète | 1 500 octets pour en-tête plus corps | fermeture de la tentative |
| Bloc de champs déchiffré | exactement la longueur du corps, 1 024 octets au plus | rejet du bloc complet |
| Nombre de champs | 128 au plus | rejet avant le 129e champ |
| Ligne de champ | 512 octets au plus, CRLF inclus | rejet du bloc complet |
| Préfixe de requête | exactement `0x1e0` octets | rejet de capacité/disposition incompatible |

La limite agrégée de 1 500 octets correspond au tampon de réception amont épinglé, avec des sous-limites explicites. Ces valeurs remplacent la proposition contradictoire 32 Kio/1 Mio de `CR3_SECURITY_PLAN.fr.md`.

Le cadreur d'enregistrement coupe au premier `\r\n\r\n` entièrement contenu dans les 1 024 octets d'en-tête. LF seul, CR seul, repli obsolète, NUL et données précédant un séparateur complet sont rejetés. Toute présence de `Transfer-Encoding`, sans tenir compte de la casse du nom, est rejetée.

Pour HTTP 200, exactement un `Content-Length`, comparé sans tenir compte de la casse, est obligatoire. Sa valeur contient seulement des chiffres ASCII, sans signe, espace ou zéro initial, et se situe entre 1 et 1 024. Le corps disponible doit lui être exactement égal ; troncature et octets finaux sont rejetés. `+5`, `05`, `0`, espaces, doublons et débordement `u64` sont invalides.

Une réponse non-200 devient une erreur de statut expurgée. Elle peut omettre `Content-Length` uniquement si l'entrée se termine exactement après les en-têtes. S'il existe, il respecte la même grammaire 1..1 024 et le corps doit être exact, sans être exposé. Une réponse non-200 sans longueur suivie d'un octet est rejetée. Un `RP-Application-Reason` borné peut être classé numériquement, mais aucun texte serveur n'entre dans les erreurs.

## Champs de réponse déchiffrés (C3)

Le bloc utilise des lignes `Nom: Valeur` strictement terminées par CRLF. Un nom compte 1..64 octets ASCII de l'ensemble `tchar` de la RFC 7230 : lettres, chiffres ou ``!#$%&'*+-.^_`|~``. Cette règle vaut aussi pour les champs inconnus. Le séparateur est exactement deux-points puis un espace ASCII. `Nom:Valeur`, `Nom:  Valeur`, les noms vides et les caractères invalides sont rejetés. Les valeurs sont non vides, jamais élaguées et conservent chaque octet après l'unique espace obligatoire. La première implémentation exige un CRLF après la dernière ligne et rejette NUL et bourrage. L'emploi réel d'une dernière ligne sans CRLF, de NUL ou de bourrage par les consoles est **INCONNU** et exige une fixture ou une preuve matérielle avant d'élargir la grammaire.

« Texte borné » signifie UTF-8 valide sans caractère de contrôle ASCII, mesuré en octets. C'est un durcissement GPSx ; l'amont ne fournit que les bornes de tableaux C.

| Nom de champ exact | Profils | Obligatoire | Grammaire et longueur | Traitement / preuve |
| --- | --- | --- | --- | --- |
| `AP-Ssid` | tous | non | texte borné, 1..47 octets | reconnu mais non exposé ; borne amont **FAIT** |
| `AP-Bssid` | tous | non | texte borné, 1..31 octets | reconnu mais non exposé ; borne amont **FAIT** |
| `AP-Key` | tous | non | texte borné, 1..79 octets | secret reconnu mais non exposé ; borne amont **FAIT** |
| `AP-Name` | tous | non | texte borné, 1..31 octets | reconnu mais non exposé ; borne amont **FAIT** |
| `PS4-Nickname` | PS4 | non | texte borné, 1..31 octets | vue empruntée ; borne amont **FAIT** |
| `PS5-Nickname` | PS5 | non | texte borné, 1..31 octets | vue empruntée ; borne amont **FAIT** |
| `PS4-RegistKey` | PS4 | oui | 2..32 caractères hexadécimaux ASCII, longueur paire ; 1..16 octets décodés | secret encodé emprunté ; plage amont **FAIT**, longueur réelle **INCONNUE** |
| `PS5-RegistKey` | PS5 | oui | 2..32 caractères hexadécimaux ASCII, longueur paire ; 1..16 octets décodés | secret encodé emprunté ; plage amont **FAIT**, longueur réelle **INCONNUE** |
| `RP-KeyType` | tous | non | `u32` canonique : `0`, décimal non nul sans zéro initial, ou `0x` puis 1..8 chiffres hexadécimaux | scalaire ; `a-f` et `A-F` acceptés après `0x` minuscule ; caractère facultatif amont **FAIT**, base réellement envoyée **INCONNUE** |
| `RP-Key` | tous | oui | exactement 32 caractères hexadécimaux ASCII / 16 octets décodés | secret encodé emprunté ; taille amont exacte **FAIT** |
| `PS4-Mac` | PS4 | oui | exactement 12 caractères hexadécimaux ASCII / 6 octets décodés | valeur encodée empruntée ; taille amont exacte **FAIT** |
| `PS5-Mac` | PS5 | oui | exactement 12 caractères hexadécimaux ASCII / 6 octets décodés | valeur encodée empruntée ; taille amont exacte **FAIT** |
| `RP-SupportCmd` | tous | non | même grammaire `u32` canonique que `RP-KeyType` | scalaire ; base réellement envoyée **INCONNUE** |

Tous les champs d'octets hexadécimaux acceptent `a-f` et `A-F`, rejettent toute autre donnée et restent empruntés. Le futur propriétaire secret réalise la copie/décodage acceptée et complète une RegistKey courte par des zéros à la frontière de session.

Chaque champ connu, même facultatif, apparaît au plus une fois. Les noms ne sont acceptés qu'avec leur casse exacte. Une variante de casse d'un nom connu est une erreur, tout comme un nom spécifique PS4/PS5 dans le mauvais profil. Les autres champs inconnus bornés sont ignorés mais comptent dans les limites de ligne, nombre, agrégat et comparaisons de doublons.

Aucun champ AP n'est renvoyé. `AP-Key` reste notamment dans le seul stockage déchiffré de l'appelant et demeure secret jusqu'à sa remise à zéro.

## Plan de preuves déterministes (C7)

Après approbation de R5, un harnais C scratch construit depuis les sources épinglées génère `tests/fixtures/registration_codec_oracle_v1.json`. Le checkout de génération, le harnais et chaque stub écrit par GPSx restent dans un répertoire scratch temporaire hors du dépôt ; aucun checkout, stub ni en-tête amont vendored n'est commité. Seuls leurs hashes sont consignés. Le relevé de génération inclut `git rev-parse HEAD` et `git status --porcelain` depuis ce checkout externe. Pour chaque fichier amont modifié, il consigne le diff exact, le SHA-256 et la justification, et marque `patched` toute unité de traduction différente du commit épinglé dans la liste d'entrées. L'entrée de manifeste et la ligne de provenance consignent commit, compilateur, backend cryptographique et version ou commit, dépendances liées, commande de génération, entrées uniquement synthétiques, redistribution, nombre d'octets, SHA-256 et scan de secrets. La liste fermée des entrées est générée depuis la sortie de dépendances du compilateur (`/showIncludes` ou `-MD` selon l'outil) et la commande de lien exacte, jamais à la main. Elle liste chaque unité de traduction amont compilée, en-tête amont consommé, archive/bibliothèque liée, backend cryptographique et stub scratch GPSx, chacun avec chemin ou libellé scratch stable, rôle et SHA-256. `lib/src/base64.c` est uniquement une entrée d'oracle, jamais une source portée par GPSx. Un relecteur, non ProvenanceGuard, vérifie ce manifeste contre l'état et les diffs du checkout externe, la sortie du compilateur et la commande de lien.

L'oracle contient octet pour octet le payload PS4_9 avec OnlineId, réservé à la compatibilité, de `test/regist.c` ; une requête distincte PS4_9 avec AccountId et `Client-Type: Windows` issue du formateur C ; des vecteurs exacts d'en-tête externe et de payload pour les quatre profils ; ainsi que des cas cible PS4_10 et PS5 avec au moins trois remplissages synthétiques donnant des paires distinctes, dont `(0, 31)` et `(31, 0)`. Les octets internes en clair, offsets, aeropause opaque, octets internes chiffrés, payload final et requête externe sont liés séparément par cas.

Les tests couvrent :

- échange des moitiés d'aeropause, échange des offsets typés ou modification d'un octet de remplissage faisant échouer la correspondance d'oracle ;
- mapping exhaustif des quatre profils vers `RegistrationTarget` et rejet de chaque paire incohérente ;
- rejet de PS4_8 avec AccountId, de PS4_9 de production avec OnlineId et de toute cible moderne avec OnlineId ; un doctest de non-compilation appelle depuis l'extérieur le constructeur privé de compatibilité PS4_9 et prouve son absence des builds de production ;
- un test positif et négatif pour chaque ligne du tableau des champs ;
- `Nom: Valeur` accepté ; `Nom:Valeur`, `Nom:  Valeur`, `Nom: `, noms vides/trop longs, `tchar` invalide et valeurs vides rejetés ; valeur textuelle d'un octet acceptée ;
- nom de l'autre génération, variante de casse connue, doublon de chaque champ facultatif connu et longueurs décodées RegistKey 0, 1, 16 et 17 ;
- limite moins un, limite exacte et limite plus un pour en-tête de requête, en-tête de réponse, corps, réponse totale, bloc déchiffré, nombre de champs et longueur de ligne ;
- `Transfer-Encoding` ; valeurs `Content-Length` `+5`, `5`, `05`, `0`, espaces, doublons et débordement `u64` ;
- LF seul rejeté pour l'enregistrement tandis que les tests de découverte existants restent inchangés ;
- vecteurs de la section 10 de la RFC 4648 et forme exacte de l'encodage d'un identifiant de compte de huit octets ;
- contrôles structurels/de non-compilation prouvant que les enveloppes d'identité ne contiennent que des références et n'ont pas les traits interdits ; scan de symboles documenté plus revue de code prouvant l'absence d'allocation extensible ;
- tests d'expurgation de chaque résultat et erreur publics ;
- `cargo tree -p gpsx-protocol` sans dépendance ;
- `cargo fmt --check`, Clippy avec avertissements refusés et `cargo test --workspace`.

Le fuzzing désigne ici des tests de propriété déterministes sans dépendance dans `gpsx-protocol`, pas `cargo-fuzz`. Ils énumèrent mutations structurées et vecteurs arbitraires de chaque longueur de zéro à chaque limite. « Travail borné » signifie un balayage linéaire de l'entrée bornée plus au plus `128 × 128` comparaisons de noms pour les doublons ; aucune récursion, rétroanalyse, nouvelle analyse non bornée ou allocation proportionnelle à l'entrée. Une future crate `cargo-fuzz` exige sa propre revue de dépendances et provenance.

Compilation, oracle C et vecteurs synthétiques ne sont pas des preuves matérielles. Émulateur, appareil Android, LAN et GP520 restent séparés.

Les tests de réponse réussie sont des cas synthétiques écrits par GPSx et ne fournissent qu'une preuve d'interopérabilité de niveau **HYPOTHÈSE** jusqu'à l'existence d'une fixture relue ou d'un résultat matériel.

## Contrat d'échec et recherche UDP reportée

Chaque analyse est tout-ou-erreur : elle ne renvoie aucune structure partiellement fiable. Trame mal formée, dépassement, longueur incohérente, champ connu invalide, doublon, champ de la mauvaise génération ou donnée obligatoire absente ferment la tentative. Aucune reprise ne balaie au-delà du message borné.

**FAIT :** l'implémentation épinglée envoie une recherche UDP `SRC2`/`SRC3` sur le port 9295 avant l'enregistrement TCP. Cette étape est exclue du codec et reportée à une tranche orchestrateur/transport revue séparément.

**INCONNU :** une vraie PS4 ou PS5 peut exiger cette étape UDP avant d'accepter l'enregistrement.

Le futur orchestrateur possède cette séquence :

1. obtenir un ambassadeur CSPRNG neuf depuis un adaptateur de plateforme ;
2. sélectionner l'un des quatre profils codec et sa cible crypto appariée exhaustivement ;
3. formater l'identité dans un stockage fixe en clair détenu par l'appelant ;
4. préparer le préfixe et initialiser la crypto avec les offsets typés renvoyés ;
5. chiffrer une fois, obtenir l'aeropause opaque, terminer le payload et formater son en-tête externe ;
6. envoyer et recevoir via un transport borné revu séparément, y compris toute pré-étape UDP autorisée séparément ;
7. cadrer la réponse, déchiffrer une fois et analyser la vue de champs empruntée ;
8. copier uniquement les secrets acceptés dans des propriétaires remis à zéro et détruire le texte clair temporaire.

Le système de types impose un chiffrement de requête et un déchiffrement de réponse par contexte. L'unicité de la paire clé/IV entre tentatives dépend toujours de la fourniture d'une entrée CSPRNG neuve par l'adaptateur.

## Gate de revue

R5 a autorisé la génération externe et la revue d’intégration a rendu `GO WITH
CHANGES`. B1 consigne désormais 76 unités du backend et 5 unités de l’oracle,
tous les en-têtes observés et chaque bibliothèque explicitement liée avec rôle
et SHA-256 ; B2 recalcule les intermédiaires cryptographiques ; B3 consigne la
provenance bilingue ; B4 lie chaque champ des neuf cas avec tests de mutation.

La fixture et ses tests restent non commités jusqu’au contrôle indépendant de
B1-B4. Selon l’option b de R5, le harnais, le stub extrait, le patch amont, les
logs de build et le manifeste fermé de construction ne doivent jamais entrer
dans ce dépôt.
