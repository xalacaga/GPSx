# Stratégie de test

`tools/powershell/check.ps1` exécute formatage, Clippy avec avertissements
interdits, tests Rust du workspace, contrôle de pureté des dépendances du cœur
(`cargo tree` : ni `jni`, `ndk`, `android*`, `windows*`, `winapi`),
`cargo check --target aarch64-linux-android` si la cible est installée (sinon
avertissement `NOT_TESTED` ; `-RequireAndroid` le rend bloquant), contrôle de
Python 3.12+, tests Python des outils de preuve, scan de secrets de
`tests/fixtures/` et vérification du manifeste (empreintes et provenance complète).

La couverture déterministe porte sur : la table exhaustive 9 × 9 des transitions
de session et l'arrêt idempotent ; l'unicité des bits de boutons internes ; la
dérivation des fronts (appui et relâchement dans un même intervalle, passages de
gâchette, état neutre à la déconnexion) ; la file bornée « tout ou rien » avec
comptage des débordements ; l'isolation multi-manettes ; les percentiles au rang
le plus proche (n = 1, petite série non triée avec doublons, n = 100) ; le
formatage de `Redacted<T>` ; des cas d'expurgation adverses ; un corpus de
classification des clés partagé entre Rust et Python
(`tests/redaction/key_cases.tsv`) ; la vérification du manifeste et la détection
d'un secret planté ; le JSONL diagnostique exact et déterministe, les noms de
métriques et unités fermés, toutes les classes de preuve, les limites de nombre
et de taille, ainsi que le rejet des nombres non finis, révisions invalides et
temps antérieurs à Unix.

Pas encore couvert : la normalisation des axes flottants vers `i16`/`u8` (aucun
code de mapping n'existe), la convention de signe des sticks (UNKNOWN avant
vérification sur PS5) et les modèles de concurrence (`loom`), qui arriveront
avec le premier code multithread.
Compiler ne valide pas le matériel : Windows, émulateur, GP520, DualSense
USB/Bluetooth et PS5 sont rapportés séparément.

Le projet est offline-first vis-à-vis du GP520. Jusqu'à l'ouverture explicite du
gate matériel, toutes ses lignes restent `NOT_TESTED` ; émulateurs, faux codecs,
replays et résultats desktop ne peuvent jamais les passer à `PASS`.

Les tests de panne couvrent données malformées, pertes/réordonnancement futurs,
recréation de Surface, déconnexion manette, transitions réseau et expurgation
adversariale. Tout parseur d’octets non fiables devra être fuzzé.

