# Feuille de route

Audit de phase 0 achevé le 25 octobre 2026. Une revue humaine reste obligatoire.

## Premier jalon recommandé — M0.5 Référence factuelle et verrou de licence

### Objectif

Choisir une stratégie protocolaire juridiquement et techniquement défendable,
puis établir un comportement reproductible sur PS5, Windows, DualSense et GP520.

### Périmètre

- préparer les collecteurs GP520 sans les exécuter ; ADB et sideload restent UNKNOWN ;
- développer d'abord sans connecter le GP520, selon `OFFLINE_FIRST.md` ;
- choisir A (réutilisation AGPL), B (clean-room) ou C (port Rust dérivé AGPL) ;
- relever noyau, pilote, key layouts, scan codes et axes GP520 ;
- épingler les révisions amont et la provenance/SBOM ;
- exécuter un client Chiaki connu sur Windows et, si possible, sur GP520 ;
- capturer états, média, erreurs, codecs, audio et contrôleur après expurgation ;
- compléter la matrice DualSense et les baselines réutilisables.

### Hors périmètre

Pas de port protocolaire Rust, d’UI Android de production, de promesse de
performance, ni d’haptique avancée, mouvement ou multitouch.

### Tests et critères

Tester expurgation, manifeste, découverte/enregistrement/connexion Windows,
codecs et Surface GP520, matrice contrôleur et reconnexion. Commencer par dix
minutes, puis trente et soixante. L’acceptation exige une licence approuvée, une
révision reproductible, une session PS5 réussie ou bloquée avec preuve, zéro
secret, les vrais codecs/entrées GP520 et une matrice entièrement qualifiée.

### Entrée en M1

M1 exige : décision A/B/C datée, approbation de `CONCURRENCY.md`, approbation du
modèle DualSense et de la sortie de stream, CI de pureté/cross-compilation/unsafe,
et architecture offline-first approuvée. Le statut ADB/sideload reste volontairement
UNKNOWN jusqu'au gate matériel. Avant cela, seules les crates `model`, `input` et
`telemetry` indépendantes du protocole peuvent avancer.

### Jalons suivants

M1 fondations portables ; M2 validation protocole Windows ; M3 shell Android TV
et JNI/média sur émulateur ; M4 validation GP520 optionnelle après accord explicite ; M5 Bluetooth ; M6 optimisation mesurée ; M7 soak,
fuzzing, injection de pannes, sécurité et préparation de version.

## Gates de contre-revue indépendante

Une contre-revue Claude Code est obligatoire avant les décisions d’entrée en
M1, avant la fusion de chaque jalon, après toute nouvelle frontière
protocole/crypto/JNI/média/concurrence, avant toute affirmation de performance
et avant release. L’agent s’arrête à chaque gate et fournit le prompt et les
fichiers exacts définis dans `CROSS_REVIEW.md`.

