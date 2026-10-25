# Audit d'architecture

> Traduction française de `ARCHITECTURE_AUDIT.md`.

Statut : Phase 0, 2026-10-25. Aucune implémentation de production n'a commencé.

## Architecture actuelle

**FACT :** Au début de l'audit, `D:\GPSx` ne contenait ni dépôt Git, ni code source, ni fichiers de build, ni documentation, ni tests, ni architecture antérieure. Les artefacts locaux actuels sont les documents de Phase 0 et la configuration des agents et de l'outillage. Il n'y a rien à préserver, mesurer ou migrer ; la structure du cahier des charges est une proposition.

## Architecture de référence

chiaki-ng documente le code C partagé dans `lib/include/chiaki`, le travail protocole/réseau/média dans `lib/src`, et l'intégration bureau Qt/QML dans `gui`. Son chemin vidéo est réception → reconstruction d'image/FEC → FFmpeg → rendu ; l'audio utilise la réception partagée/Opus avec la gestion des périphériques côté frontend. [Guide des contributeurs chiaki-ng](https://github.com/chiaki-ng/chiaki-ng/blob/main/CONTRIBUTOR_GUIDE.md)

Pylux conserve ce modèle et ajoute Android, iOS, le jeu en cloud et le packaging. Sa bibliothèque partagée sépare découverte, enregistrement, session, transport, réordonnancement, contrôle de congestion, FEC, récepteurs, retour manette et cryptographie. [Bibliothèque Pylux](https://github.com/ForWard-Technologies-LLC/Pylux/tree/master/lib/src)

```text
PS5
 ├─ découverte / enregistrement / réveil
 └─ contrôle de session
      └─ transport chiffré
          ├─ vidéo → réordonnancement/FEC/assemblage d'image → unités compressées
          ├─ audio → réordonnancement/FEC → Opus
          ├─ état manette → émetteur de retour (feedback)
          └─ rapports de qualité → adaptation + télémétrie

Cœur portable
 ├─ adaptateur de validation Windows
 └─ adaptateur Android
      ├─ cycle de vie Kotlin + UI TV + persistance sécurisée
      ├─ Input Framework → état manette normalisé
      ├─ vidéo compressée → MediaCodec/Surface
      └─ PCM → audio Android
```

## Réutiliser, envelopper ou remplacer

| Domaine | Preuve | Recommandation |
|---|---|---|
| Protocole/session/crypto | Implémentation C mature de Chiaki | Choisir réutilisation AGPL ou Rust en salle blanche avant tout code |
| Réordonnancement/FEC | Files, FEC et processeur d'images explicites en amont | Oracle comportemental ; copie uniquement sous une stratégie AGPL acceptée |
| Média bureau | FFmpeg + libplacebo/Qt | Oracle de validation, pas le moteur de rendu Android de production |
| Vidéo Android | L'amont désactive le décodeur FFmpeg partagé sur Android | Adaptateur MediaCodec-vers-Surface étroit, mesuré sur l'appareil |
| UI Android | Amont Kotlin à base de View | Nouveau shell Compose pensé pour la TV |
| Entrée | État amont normalisé ; SDL sur bureau, framework sur Android | Nouveau modèle documenté avec validation physique |
| Télémétrie | Quelques métriques seulement | Nouveau sous-système JSONL à faible surcoût |

## Architecture Rust proposée

La préférence à terme est Rust, mais cela n'autorise pas le portage de code AGPL.

Révisé le 2026-10-25 après revue indépendante. Les crates côté protocole dépendent de l'option de licence choisie dans `LICENSE_AUDIT.md` :

```text
crates/                          (cœur portable : sans dépendance jni/ndk/windows, forbid(unsafe_code))
  model/       identifiants, erreurs, diagnostics             indépendant de l'option
  input/       état exact au format réseau, profils de mapping (données)   indépendant de l'option
  telemetry/   compteurs, histogrammes, anneaux par thread, JSONL, expurgation   indépendant de l'option
  protocol/    parseurs/sérialiseurs                          Option B/C : Rust ; Option A : enveloppe C
  transport/   sockets, files de réordonnancement bornées, congestion   Option B/C : Rust ; Option A : enveloppe C
  session/     cycle de vie et annulation                     Option B/C : Rust ; Option A : enveloppe C
  core/        orchestration et ports de plateforme
adapters/                        (spécifiques à la plateforme ; unsafe autorisé avec invariants SAFETY)
  android-native/  AMediaCodec du NDK, ANativeWindow, AAudio/Oboe, points d'entrée JNI
  windows/         décodeur/audio/entrée Windows pour la validation
apps/
  desktop/     application de validation Windows
  android/     shell TV Kotlin (UI, cycle de vie, remise de la Surface, capture des entrées)
```

Seules les crates indépendantes de l'option peuvent démarrer avant la décision de licence.

Les interfaces de plateforme échangent des opérations grossières et des lots porteurs de propriété : démarrage/arrêt, mise à jour de Surface, lots d'événements d'entrée bruts, unités d'accès compressées, blocs audio, événements et métriques. La vidéo compressée et le PCM n'entrent jamais dans la JVM ; ils restent dans l'adaptateur Android natif. Les threads, capacités de file, politiques de débordement et l'ordre d'arrêt sont spécifiés dans `CONCURRENCY.md`.

## Principaux problèmes d'ingénierie

### 1. Provenance du protocole et licences

- Classification : **FACT**
- Preuve : Chiaki, chiaki-ng et Pylux sont sous AGPL-3.0 avec une exception OpenSSL.
- Gravité : Critique
- Composants concernés : protocole, crypto, transport, session, packaging
- Cause racine : aucune stratégie de licence ou de distribution n'a été choisie pour GPSx.
- Instrumentation requise : pas d'instrumentation à l'exécution ; un registre de provenance et une décision juridique/produit.
- Approche proposée : Option A (réutilisation/enveloppe AGPL), B (implémentation en salle blanche écrite indépendamment) ou C (GPSx sous AGPL-3.0 avec un portage Rust déclaré comme dérivé) ; voir `LICENSE_AUDIT.md`.
- Tests : analyse SPDX/licences, revue de provenance et des mentions dans les binaires.
- Conséquences sur les performances : la FFI C réduit le risque de calendrier ; Rust en salle blanche augmente le risque de correction et de performance.
- Risques de régression : non-conformité, incompatibilité, retard.

### 2. Comportement inconnu du protocole PS5

- Classification : **UNKNOWN**
- Preuve : l'amont fonctionne pour d'autres, mais GPSx n'a ni spécification, ni fixtures, ni captures, ni résultats PS5.
- Gravité : Critique
- Composants concernés : enregistrement, authentification, session, crypto, reconnexion
- Cause racine : non démontrée.
- Instrumentation requise : traces d'état/de handshake expurgées, erreurs, corpus de paquets sans secrets.
- Approche proposée : établir un oracle amont fonctionnel contre la PS5 de l'utilisateur.
- Tests : parseurs de référence (golden), entrées malformées, scénarios matériels connexion/déconnexion/reconnexion.
- Conséquences sur les performances : le traçage du chemin de contrôle coûte peu s'il est borné.
- Risques de régression : des tests unitaires peuvent faire croire à tort à l'interopérabilité.

### 3. Cycle de vie et latence du décodeur Android

- Classification : **INFERENCE**
- Preuve : l'API 30 permet MediaCodec vers Surface ; l'Android amont gère explicitement le cycle de vie de la Surface. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
- Gravité : Élevée
- Composants concernés : adaptateur Android, Surface, vidéo, cycle de vie
- Cause racine : non démontrée sur le GP520.
- Instrumentation requise : identité du codec, profondeur de file, horodatages, pertes, erreurs, générations de Surface.
- Approche proposée : décodage direct des unités compressées vers la Surface ; sonder et vérifier les composants réels.
- Tests : recréation de Surface, arrière-plan/premier plan, réinitialisation, 720p60/1080p60, récupération par IDR.
- Conséquences sur les performances : éviter les copies JNI d'images brutes ; valider un buffering minimal.
- Risques de régression : blocages, repli logiciel, latence cumulée.

### 4. Sémantique DualSense à travers Android

- Classification : **FACT** pour la variabilité d'Android ; **UNKNOWN** pour le GP520
- Preuve : Android documente des variantes de croix directionnelle par touches/HAT et des gâchettes dupliquées axe/touche. [Entrée manette](https://developer.android.com/games/sdk/game-controller/controller-input)
- Gravité : Bloquante
- Composants concernés : entrée Android, état normalisé, focus UI, retour manette
- Cause racine : le comportement du GP520 n'a pas été observé.
- Instrumentation requise : descripteur, VID/PID, sources, touches, plages, événements bruts/normalisés, horodatages.
- Approche proposée : descripteurs de capacités, état par appareil, déduplication, inspecteur en direct, fixtures.
- Tests : matrice complète, reconnexion, duplication des gâchettes, variantes de croix directionnelle.
- Conséquences sur les performances : instantanés sans allocation et sans verrou global.
- Risques de régression : inversion des boutons de façade, entrées bloquées, capture de la télécommande TV.

### 5. Coexistence Bluetooth et latence audio

- Classification : **FACT** pour l'avertissement BenQ d'environ 300 ms sur l'audio Bluetooth externe ; **UNKNOWN** pour la coexistence
- Preuve : [Spécifications GP520](https://www.benq.com/en-sg/projector/cinema/gp520/spec.html), [FAQ Bluetooth BenQ](https://www.benq.com/en-us/support/downloads-faq/faq/product/application/projector-faq-kn-00208.html)
- Gravité : Élevée
- Composants concernés : audio, Bluetooth, entrée, UX, télémétrie
- Cause racine : aucune cause applicative démontrée.
- Instrumentation requise : routes, sous-alimentations (underruns), profondeur, déconnexions manette, qualité Wi-Fi, continuité.
- Approche proposée : isoler les changements de route de la session et séparer les latences application/OS/appareil.
- Tests : haut-parleurs internes ; barre de son ; DualSense + barre de son + télécommande ; changements de route ; endurance.
- Conséquences sur les performances : la latence de l'appareil peut dominer et ne peut pas être éliminée par le code applicatif.
- Risques de régression : redémarrage de session, sortie audio morte, interruption de la manette.

## Inconnues

- Firmware PS5 exact et interopérabilité.
- Acceptabilité d'une distribution sous AGPL.
- Noms des codecs du GP520, classification matérielle, latence et thermique.
- Transmission par le GP520 du bouton PS, du pavé tactile, du mouvement, des vibrations et des rapports de sortie.
- Accès ADB/installation et méthode de débogage.
- Périmètre du Remote Play par Internet.
- Comportement réel de l'audio Android (format, burst, route).
