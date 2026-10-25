> Traduction française de `AGENTS.md`. La version anglaise fait foi pour les agents ; les deux fichiers sont mis à jour ensemble.

## graphify

Ce projet dispose d'un graphe de connaissances dans graphify-out/ avec des nœuds centraux (god nodes), une structure en communautés et des relations entre fichiers.

Quand l'utilisateur tape `/graphify`, utiliser la skill graphify installée ou ses instructions avant toute autre chose.

Règles :
- Quand graphify-out/graph.json existe, exécuter `graphify query "<question>"` avant le premier grep, la première recherche ou la première lecture de fichier source pour une question sur le code, et inclure cette règle dans chaque prompt de sous-agent qui explore le code. Ne lire les fichiers bruts que pour les lignes déjà localisées par le graphe ou par une lecture antérieure. Utiliser `graphify path "<A>" "<B>"` pour les relations et `graphify explain "<concept>"` pour les concepts ciblés. Ces commandes renvoient un sous-graphe restreint, généralement bien plus petit que GRAPH_REPORT.md ou qu'une sortie grep brute.
- Des fichiers graphify-out/ modifiés sont normaux après des hooks ou des mises à jour incrémentales ; ce n'est pas une raison d'ignorer graphify. Ne l'ignorer que si la tâche porte sur une sortie de graphe obsolète ou incorrecte, ou si l'utilisateur le demande explicitement.
- Si graphify-out/wiki/index.md existe, l'utiliser pour la navigation générale plutôt que de parcourir les sources brutes.
- Ne lire graphify-out/GRAPH_REPORT.md que pour une revue d'architecture globale ou quand query/path/explain ne donnent pas assez de contexte.
- Après modification du code, exécuter `graphify update .` pour garder le graphe à jour (AST uniquement, sans coût d'API).
- Un graphify-out/graph.json versionné est réécrit entièrement à chaque construction ; un gros diff dans une pull request est donc attendu. Ne pas le signaler comme du bruit et ne pas suggérer de sauter la reconstruction du graphe ou ses hooks.

## Contrat d'ingénierie GPSx

- Garder le moteur Remote Play portable ; Android, Windows, Compose, JNI, MediaCodec, AudioTrack et le comportement du GP520 restent derrière des adaptateurs de plateforme.
- Utiliser des machines à états explicites, des files bornées dont la profondeur et la politique de débordement sont documentées (voir `docs/CONCURRENCY.md`), une propriété (ownership) documentée et des appels JNI grossiers. Ne jamais traverser JNI par paquet, échantillon, pixel ou image quand la propriété peut rester native.
- Représenter l'entrée manette une seule fois, sous forme d'état multi-manettes indépendant de la plateforme, indexé par un identifiant interne.
- Étiqueter les conclusions importantes FACT, INFERENCE, HYPOTHESIS ou UNKNOWN. La compilation n'est pas une validation ; garder séparées les preuves issues du cœur, de Windows, d'Android, de l'émulateur et du GP520 physique.
- Les barrières Rust sont `cargo fmt --check`, Clippy avec les avertissements traités comme des erreurs, et `cargo test --workspace`. Ajouter des tests déterministes de parsing, d'état, de files, d'entrée, de télémétrie et d'expurgation ; fuzzer les parseurs de données non fiables.
- Ne jamais journaliser d'identifiants, cookies, jetons, URL de redirection contenant des secrets, clés d'enregistrement, clés de session ou secrets Remote Play. Ne pas désactiver la validation TLS ni implémenter de primitives cryptographiques maison.
- Ne pas revendiquer d'amélioration de performance sans mesures comparables. Privilégier les preuves P50/P95/P99 et de profondeur de file.
- Ne pas copier, traduire ou adapter de code amont tant que sa licence et sa provenance ne sont pas enregistrées et acceptées. Considérer une réécriture d'un source AGPL comme une œuvre dérivée, sauf si une revue juridique établit le contraire.
- Rust possède la logique portable de protocole, de session, d'entrée et de télémétrie. Kotlin possède le cycle de vie Android, l'UI, la remise de la Surface, la capture des événements d'entrée et l'intégration système. Un adaptateur Android natif (hors du cœur portable) possède les E/S média : `AMediaCodec` du NDK, `ANativeWindow`, AAudio/Oboe et leurs threads. La vidéo compressée et le PCM ne transitent jamais par la JVM. Python sert uniquement à l'outillage.
- Les crates du cœur portable ne doivent dépendre ni de `jni`, ni de `ndk`, ni d'Android, ni de Windows, ni d'aucune autre crate de plateforme. La CI l'impose par une vérification du graphe de dépendances et compile le cœur pour `aarch64-linux-android` en plus de Windows.
- Les crates du cœur portable utilisent `#![forbid(unsafe_code)]`. `unsafe` n'est autorisé que dans les crates FFI/adaptateurs ; chaque bloc `unsafe` porte un commentaire `// SAFETY:` qui énonce ses invariants (imposé par `clippy::undocumented_unsafe_blocks`). Exécuter Miri quand c'est possible et ASan/HWASan sur le code natif Android.
- Les fronts de boutons et les passages relâchée/enfoncée des gâchettes (0 ↔ non-zéro) ne sont jamais fusionnés ni perdus entre la capture et l'envoi réseau. Les sticks, le mouvement et les valeurs intermédiaires des gâchettes sont de l'état continu et peuvent être fusionnés (la plus récente l'emporte).
- Chaque document Markdown rédigé existe en anglais (`NOM.md`, version de référence) et en français (`NOM.fr.md`). Mettre à jour les deux dans la même modification. L'outillage tiers sous `.codex/` et les fichiers générés sous `graphify-out/` sont exclus.
- Demander une contre-revue indépendante par Claude Code aux gates de `docs/CROSS_REVIEW.md`. À chaque gate, s'arrêter, prévenir l'utilisateur et fournir le périmètre et le prompt exacts avant de poursuivre.
- Suivre `docs/OFFLINE_FIRST.md` : ne pas connecter, sonder, sideloader ni exiger le GP520 avant l'ouverture explicite du gate matériel par l'utilisateur. L'émulateur et les replays ne prouvent jamais le comportement GP520.
- Une modification n'est terminée que lorsque les tests concernés passent, que le comportement en cas d'échec est documenté et que les modifications sensibles aux performances sont accompagnées de mesures avant/après.
