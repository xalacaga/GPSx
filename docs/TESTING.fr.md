# Stratégie de test

`tools/powershell/check.ps1` exécute formatage, Clippy avec avertissements
interdits, tests Rust du workspace, contrôle de pureté des dépendances du cœur
(`cargo tree` : ni `jni`, `ndk`, `android*`, `windows*`, `winapi`),
`cargo check --locked --target aarch64-linux-android` si la cible est installée (sinon
avertissement `NOT_TESTED` ; `-RequireAndroid` le rend bloquant), contrôle de
Python 3.12+, tests Python des outils de preuve, scan de secrets de
`tests/fixtures/` et vérification du manifeste (empreintes et provenance complète).

Le gate Android épingle le NDK `28.2.13676358` et l’API 24. Il découvre ce NDK
exact via `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, un SDK nommé par
`ANDROID_SDK_ROOT`/`ANDROID_HOME`, ou `.tools/android-sdk` dans le dépôt, puis
configure `aarch64-linux-android24-clang` et `llvm-ar` pour Cargo. La version,
l’API et le chemin retenus sont journalisés. `-RequireAndroid` échoue avec un
message dédié si la cible Rust ou le NDK épinglé manque.

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

## Simulation locale de connexion

La suite desktop Windows utilise de vraies sockets en boucle locale sans joindre
une console ni le LAN. La découverte UDP envoie le datagramme de recherche exact
à un répondeur local borné. Le smoke test ctrl ouvre un listener TCP sur
`127.0.0.1`, envoie par l'API publique la requête PS4 10 chiffrée, reçoit une
réponse HTTP bornée suivie d'octets de charge utile et termine la transition
d'état ctrl. Toutes les opérations socket ont un délai maximal de deux secondes.

Il s'agit uniquement d'une preuve de simulation desktop. Elle ne simule ni
l'inscription, ni Takion/RUDP, ni la vidéo, ni l'audio, ni les paquets manette,
ni la reconnexion, ni le comportement complet d'une console. Ce n'est jamais
une preuve d'interopérabilité PS4/PS5.

## Simulation Android sur LAN virtuel

Android est le gate de plateforme principal. Depuis Windows, la commande
suivante cross-compile un test d’intégration Rust natif pour
`x86_64-linux-android`, démarre si nécessaire l’AVD Google TV API 36 du dépôt,
lance une fausse console bornée sur l’hôte et exécute le test dans Android :

```text
powershell -NoProfile -ExecutionPolicy Bypass -File tools/powershell/test-android-simulation.ps1
```

Le test utilise le NAT QEMU en mode utilisateur pour joindre la passerelle de
boucle locale de l’hôte `10.0.2.2`. La découverte est unicast UDP sur le port de
test `19878` ; ctrl se connecte au port de test annoncé `19295`, même si l’en-tête
protocolaire canonique `Host` conserve le port `9295`. Il envoie la requête de
découverte PS4 exacte, parse la réponse READY fixe, envoie une requête ctrl PS4
10 chiffrée relue et termine la transition d’état de la réponse ctrl. La
découverte broadcast et les ports standard de découverte/ctrl sont `NOT_TESTED`
dans cette simulation. Le script n’accepte qu’un numéro ADB nommé `emulator-*` ;
il ne sélectionne jamais un appareil Android physique. Toutes les E/S sont
bornées et le binaire de test distant est supprimé au nettoyage.

La console synthétique envoie le marqueur ctrl final sans octet de garde et
garde la connexion ctrl persistante ouverte jusqu’à la fermeture du client
Android. Le client cadre la réponse comme l’en-tête HTTP borné suivi du premier
message ctrl connu ; il n’utilise pas EOF TCP comme frontière de message.
**RAPPORT_OPÉRATEUR :** un diagnostic émulateur `-tcpdump` montre un payload
réussi de 80 octets suivi d’un FIN séparé, alors que les exécutions cadrées par
EOF ne livraient parfois que 79 octets au guest. L’assertion Android exige
l’égalité exacte des octets finaux ; un FIN TCP n’est ni un cadrage protocolaire
ni une autorisation de perdre le dernier octet utile.

FAIT (2026-10-28) : l’AVD Google TV API 36 x86_64 a réussi cette simulation de
découverte + ctrl. C’est une preuve d’espace utilisateur Android natif et de
réseau virtuel. Ce n’est pas une preuve d’intégration APK/JNI, d’inscription, de
Takion/RUDP, de média, de manette, de LAN physique, d’interopérabilité avec une
console réelle, de GP520, de latence ou de performance.
**FAIT (2026-10-28) :** `-Iterations 20` a réussi 20 exécutions consécutives avec
égalité exacte des octets finaux sur le même AVD sans tcpdump. Le script a
conservé les journaux stdout/stderr de la fausse console pour chaque passage et
leurs SHA-256 dans `target/android-simulation/run-summary.json` ; ce résumé
indique 20/20 et porte
le SHA-256 `6c9274920982a34ab7079dcc0ea603c56bbea5708cb8cc09fc3596278fb901fd`.
La sortie libtest Android elle-même n’a pas été conservée ; chaque PASS consigné
combine donc le code de sortie adb vérifié avec la terminaison propre de la
fausse console dans les cinq secondes.
Ces artefacts scratch ignorés sont une preuve émulateur disponible pour revue
indépendante. Le diagnostic tcpdump antérieur reste un rapport opérateur, car sa
capture n’a pas été conservée.

## Fuzzing de la réponse ctrl

La cible libFuzzer `fuzz/control_response` pilote avec des octets arbitraires
le chemin public préparé de réponse ctrl. En cas de succès, elle vérifie que la
longueur d’en-tête annoncée est bornée, se termine au premier `\r\n\r\n` et
laisse les octets suivants hors de l’en-tête. Sur un hôte de type Unix pris en
charge, exécuter :

```text
cargo +nightly fuzz run control_response -- -max_total_time=60 -max_len=1024
```

`cargo +nightly fuzz build control_response` sert aussi de gate de compilation.
L'outil amont `cargo-fuzz` ne prend pas en charge l'exécution sous Windows. Sous
Windows, le test unitaire d'entrées arbitraires déterministes et le test borné
en boucle locale passent dans `check.ps1` ; une compilation Windows réussie de
la cible n'est qu'une preuve de compilation, et une vraie campagne reste
`NOT_TESTED` jusqu'à son exécution sur un gate de type Unix.

Compiler ne valide pas le matériel : Windows, émulateur, GP520, DualSense
USB/Bluetooth et PS5 sont rapportés séparément.

### Cycle de vie de l’adaptateur Android natif

Exécuter uniquement le wrapper du dépôt depuis sa racine :

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/powershell/test-android-instrumentation.ps1
```

Il refuse toute installation sauf si une seule cible ADB prête existe et si son
numéro correspond à `emulator-*` ; une cible physique ou plusieurs cibles
échouent en mode fermé. Il rejette aussi un faux succès Gradle annonçant zéro test
exécuté. La suite couvre la libération des handles/workers/fenêtres après
create/destroy ×1000, les générations Surface obsolètes et en échec, le
remplacement Surface réel, l’arrêt idempotent, start/stop AAudio et le signalement
de déconnexion, les appels concurrents face à close et la recréation du contrôleur
`SurfaceView`. Elle n’alimente ni média compressé ni PCM réel.

Exécuter les dix tests natifs sous AddressSanitizer avec :

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/powershell/test-android-instrumentation.ps1 -Asan
```

FAIT (2026-10-28) : les 13 tests d’instrumentation normaux et les dix tests
natifs ASan ciblés ont réussi sur l’AVD Google TV API 36 x86_64. Le wrapper efface
les résultats Gradle obsolètes avant chaque run et conserve les deux rapports XML
sous `app/build/reports/gpsx-instrumentation-evidence/{normal,asan}`. C’est
uniquement une preuve de cycle de vie sur émulateur. L’exécution API 27/28 reste
`NOT_TESTED` car ces images système ne sont pas installées.

Le gate matériel physique reste fermé dans l’attente du dossier de preuve, du
`GO` CR4 et de la confirmation directe par le propriétaire de la date et du
périmètre. Les résultats d’émulateur, faux codec, replay et desktop ne peuvent
pas promouvoir à `PASS` les lignes GP520 ou console.

Les tests de panne couvrent données malformées, pertes/réordonnancement futurs,
recréation de Surface, déconnexion manette, transitions réseau et expurgation
adversariale. Tout parseur d’octets non fiables devra être fuzzé.

