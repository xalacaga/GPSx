# Graph Report - GPSx  (2026-11-01)

## Corpus Check
- 246 files · ~195,186 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 23 file(s) not represented in the graph (top: .xml 7, (none) 5, .mk 2)

## Summary
- 3626 nodes · 6632 edges · 252 communities (188 shown, 64 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 189 edges (avg confidence: 0.88)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `32c55adf`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- redact_text
- SessionState
- CloudPlayController
- Planned cryptography dependency register
- Button
- gpsx-telemetry/src/lib.rs
- collect-gp520-baseline.ps1
- gpsx-android-input
- gpsx-model
- gpsx-telemetry
- Assert-GpsxProvenance
- Architecture Audit
- Audit d'architecture
- License Audit
- Audit des licences
- Recommended first milestone — M0.5 Evidence Baseline and License Gate
- DualSense Analysis
- Upstream Analysis
- launch_spec.rs
- Android TV Architecture
- Concurrency, Queues, and Ownership
- M0.5 baseline runbook
- ADR-0001: Protocol implementation and license strategy
- GPSx
- Phase 0 Independent Review
- Testing strategy
- Performance baseline
- Protocol evidence register
- AGENTS.md
- AGENTS.fr.md
- CLAUDE.md
- CLAUDE.fr.md
- DUALSENSE_MAPPING.md
- evidence/README.md
- fixtures/README.md
- Premier jalon recommandé — M0.5 Référence factuelle et verrou de licence
- Architecture Android TV
- Procédure de référence M0.5
- Concurrence, files et ownership
- ADR-0001 : stratégie de licence et d’implémentation du protocole
- Independent Claude Code cross-review gates
- Analyse DualSense
- Contre-revue indépendante de la phase 0
- GPSx
- Gates de contre-revue indépendante par Claude Code
- DUALSENSE_MAPPING.fr.md
- evidence/README.fr.md
- PERFORMANCE.fr.md
- PROTOCOL.fr.md
- TESTING.fr.md
- fixtures/README.fr.md
- GpsxApp.kt
- StreamTestRecorder.kt
- RemoteNavigationTest.kt
- Offline-first development before GP520 access
- Développement offline-first avant accès au GP520
- Android TV interface specification
- Spécification de l’interface Android TV
- Feature parity baseline
- Socle de parité fonctionnelle
- DiagnosticExportError
- SmbRomSource.kt
- Embedded diagnostic lab
- Laboratoire de diagnostic embarqué
- GPSx update channel
- LargeTextLegalTest.kt
- extract_diagnostic_zip
- steps
- CR3 protocol security plan
- Plan de sécurité protocolaire CR3
- Registre prévu des dépendances cryptographiques
- http.rs
- gpsx-crypto/src/registration.rs
- androidKeyToPs5Button
- CR1 remediation record
- Registre de remédiation CR1
- ControllerId
- gpsx-input/src/lib.rs
- android_virtual_lan.rs
- ControllerState
- RetroLibrary
- Registration codec plan
- RetroConsoleArt.kt
- DiscoveryHost<'a>
- wakeupCredential
- MetricName
- ControllerState
- HttpParseError
- encode_diagnostic_jsonl
- DiagnosticRunKind
- EvidenceClass
- DiagnosticEnvironment
- Diagnostic export
- Export des diagnostics
- AppUpdater.kt
- Registration cryptography plan
- Plan cryptographique de l’enregistrement
- gpsx-transport/src/lib.rs
- gpsx-discover.rs
- NativeAdapterLifecycleTest
- DiscoveryError
- control_handshake_oracle_tests.rs
- .wakeAndWait
- .protect_in_place
- gpsx-protocol/src/registration.rs
- manifest.json
- registration_codec_oracle.rs
- RegistrationCodecError
- Composable
- format_account_id_inner
- gpsx-crypto
- ControlProfile
- AAudioStream
- LaunchSpec and session-rpcrypt plan (pre-implementation)
- Q: Implement the approved CR3 session cryptography plan after owner acceptance of the 32-bit tag residual risk
- Plan du codec d'enregistrement
- PadInput
- RegistrationProfile
- Q: Close codec review findings A1 and A2 and prepare the authorized external R5 oracle evidence
- Q: Remediate the independent CR3 follow-up findings B1-B5 for session cryptography
- RegistrationResponseView<'a>
- Q: Remediate the CR3 session-crypto implementation review: make check.ps1 discover pinned Android NDK 28.2.13676358 in a clean environment, add the 2049-byte receive rejection test, non-Clone API checks, and EN/FR documentation.
- Session cryptography plan
- Q: Implement the reviewed bounded registration codec without generating the R5-blocked C oracle or committing stubs
- Q: Apply the GO WITH CHANGES remediation for codec blockers B1 and B2 and add the missing R5-a provenance requirements without generating the oracle
- Plan de cryptographie de session
- Q: Apply the independent GO verdict for codec oracle B1-B4 and include the three LOW documentation fixes
- Q: Close codec oracle integration findings B1-B4 without committing R5 external artifacts
- Q: Où en est le projet et le test réel avec la simulation sous Windows ?
- Q: Remediate CR3 follow-up blocker C1 for HandshakeKey LaunchSpec egress
- Q: What is the next safe project slice after the reviewed registration codec, and what must block session-cryptography implementation?
- session.rs
- .finish
- SessionCryptoError
- Plan LaunchSpec et rpcrypt de session (avant implémentation)
- Q: Is the GPSx session-cryptography slice integrated after the independent C1 GO, and what conditions remain?
- .verify_and_decrypt_in_place
- Session
- FailingRng
- Q: What is the current session cryptography oracle integration status, evidence, and remaining gate?
- Host
- Q: Fix CR3 D1 epoch boundary inconsistency
- control_handshake.rs
- RemotePlayEngine
- ControlHandshakeError
- StreamTestRecorder.kt
- Q: Remediate the independent CR3 implementation review findings: outbound epoch exhaustion, full-packet GMAC versus payload encryption, SEC1 hybrid prefixes, missing tests, vendored libsecp256k1 MIT licensing, and weakened cargo-deny policy
- retro_api.h
- Q: Did the C1 remediation make the session oracle test bind every fixture field?
- gpsx-protocol/src/lib.rs
- Assert-SingleAndroidEmulator
- ControllerMapper.kt
- ControllerMapper
- DiscoveryReport
- TrafficCore
- AAudioApi
- rpcrypt_tables.rs
- wrap.sh
- RetroStorage
- FailingRng
- MainActivity
- Engine
- Control-channel HTTP handshake implementation contract
- Contrat d’implémentation de la négociation HTTP du canal de contrôle
- RetroSession
- native_adapter.cpp
- .close
- AppUpdater
- gpsx-android-input/src/lib.rs
- RetroGamesScreen
- LocalTokenServerTest
- retro_host.cpp
- Analyse de l'amont
- control_response.rs
- .attach_surface
- NativeEngine
- PcmRing
- StreamQuality
- NativeAudioHealth
- GameControllerMonitor
- Java_com_gpsx_tv_retro_RetroNative_nativeClose
- QrCode.kt
- RemotePlayPhase
- CloudPlayController.kt
- RegisteredConsoleStoreTest.kt
- Registre de provenance
- Android input adapter plan
- ConnectInfo
- AppUiState
- Plan de l’adaptateur d’entrée Android
- RemotePlaySurfaceController
- NativeAudioHealth
- gpsx-protocol/src/lib.rs
- ChiakiCompat.kt
- Retro-gaming plan
- RemotePlayEngine.kt
- Plan rétro-gaming
- .check
- format_account_id_inner
- RetroPadMapper.kt
- ChiakiCompatAbiTest
- RegistrationCryptoTarget
- LiveLogServerTest
- Stream tuning for the GP520 over Wi-Fi
- CloudCatalog.kt
- Réglage du stream pour le GP520 en Wi-Fi
- EncryptedSecretStore
- RegisteredConsoleStore
- asserttrue
- RetroPhase
- LocalTokenServer
- LogDigestEntry
- QuitChord
- Installing and testing GPSx on the GP520
- Installer et tester GPSx sur le GP520
- ShareAddress
- .load
- TransitionKind
- GpsxInputBridgeTest.kt
- CoreInstaller
- ControllerState
- InputTransitionQueue
- pylux/README.md
- pylux/README.fr.md
- StreamMetrics
- Canal de mise à jour GPSx
- parseLatestRelease
- Provenance ledger
- RetroLibraryTest
- RetroSystem
- .parse
- ControllerMappingTest.kt
- .derive
- TransitionBatch
- ChiakiPackagingTest.kt
- LogLevel

## God Nodes (most connected - your core abstractions)
1. `Host` - 68 edges
2. `RegistrationCodecError` - 66 edges
3. `RemotePlayEngine` - 57 edges
4. `SessionCryptoError` - 46 edges
5. `Engine` - 42 edges
6. `RetroLibrary` - 40 edges
7. `ControlHandshakeError` - 37 edges
8. `AAudioApi` - 35 edges
9. `NativeEngine` - 33 edges
10. `LaunchSpecError` - 29 edges

## Surprising Connections (you probably didn't know these)
- `Android virtual-LAN simulation` --references--> `Host`  [INFERRED]
  docs/TESTING.md → apps/android-tv/app/src/main/cpp/retro_host.cpp
- `Simulation Android sur LAN virtuel` --references--> `Host`  [INFERRED]
  docs/TESTING.fr.md → apps/android-tv/app/src/main/cpp/retro_host.cpp
- `Périmètre et frontière de preuve` --references--> `MainActivity`  [INFERRED]
  docs/ANDROID_INPUT_ADAPTER_PLAN.fr.md → apps/android-tv/app/src/main/java/com/gpsx/tv/MainActivity.kt
- `Scope and evidence boundary` --references--> `MainActivity`  [INFERRED]
  docs/ANDROID_INPUT_ADAPTER_PLAN.md → apps/android-tv/app/src/main/java/com/gpsx/tv/MainActivity.kt
- `Décision d'intégration Android épinglée (2026-10-28)` --references--> `RemotePlayEngine`  [INFERRED]
  docs/UPSTREAM_ANALYSIS.fr.md → apps/android-tv/app/src/main/java/com/gpsx/tv/remoteplay/RemotePlayEngine.kt

## Import Cycles
- 1-file cycle: `crates/gpsx-input/src/lib.rs -> crates/gpsx-input/src/lib.rs`
- 1-file cycle: `crates/gpsx-model/src/lib.rs -> crates/gpsx-model/src/lib.rs`
- 1-file cycle: `crates/gpsx-transport/src/lib.rs -> crates/gpsx-transport/src/lib.rs`
- 1-file cycle: `crates/gpsx-transport/src/bin/gpsx-discover.rs -> crates/gpsx-transport/src/bin/gpsx-discover.rs`
- 1-file cycle: `crates/gpsx-telemetry/src/lib.rs -> crates/gpsx-telemetry/src/lib.rs`
- 2-file cycle: `crates/gpsx-crypto/src/launch_spec.rs -> crates/gpsx-crypto/src/session.rs -> crates/gpsx-crypto/src/launch_spec.rs`
- 2-file cycle: `crates/gpsx-crypto/src/control_handshake.rs -> crates/gpsx-crypto/src/launch_spec.rs -> crates/gpsx-crypto/src/control_handshake.rs`

## Communities (252 total, 64 thin omitted)

### Community 0 - "redact_text"
Cohesion: 0.05
Nodes (27): drain_request(), main(), receive_control_request(), run(), _load_allowed_keys(), main(), _redact_jsonl(), scan_for_secrets() (+19 more)

### Community 1 - "SessionState"
Cohesion: 0.07
Nodes (22): accepts_nominal_stream_lifecycle(), ALL, ALLOWED, InvalidTransition, rejects_streaming_without_connection(), SessionState, Connecting, Discovering (+14 more)

### Community 2 - "CloudPlayController"
Cohesion: 0.17
Nodes (8): CloudCategory, OWNED, PURCHASEABLE, STREAMABLE, CloudGame, CloudPlayController, CloudSnapshot, cloudCatalogInvalidateCache()

### Community 3 - "Planned cryptography dependency register"
Cohesion: 0.40
Nodes (4): Cryptography dependency register, Registration-only resolved graph — 2026-10-26, Security and acceptance gate, Session implementation graph — 2026-10-28

### Community 4 - "Button"
Cohesion: 0.11
Nodes (18): Button, .ALL, Circle, Create, Cross, DpadDown, DpadLeft, DpadRight (+10 more)

### Community 5 - "gpsx-telemetry/src/lib.rs"
Cohesion: 0.08
Nodes (20): EXACT_TOKENS, LatencySummary, MAX_DIAGNOSTIC_JSONL_BYTES, MAX_DIAGNOSTIC_SAMPLES, MAX_METRIC_LINE_BYTES, MAX_RUN_LINE_BYTES, MAX_SCIENTIFIC_F64_BYTES, MAX_U64_BYTES (+12 more)

### Community 7 - "gpsx-android-input"
Cohesion: 0.67
Nodes (4): gpsx-android-input, gpsx-input, gpsx-protocol, gpsx-transport

### Community 10 - "Assert-GpsxProvenance"
Cohesion: 0.20
Nodes (6): Get-PinnedAndroidNdk(), Resolve-PinnedAndroidNdk(), Assert-GpsxProvenance(), Get-GpsxRelativePath(), Get-GpsxRepositoryFiles(), Test-GpsxGeneratedPath()

### Community 12 - "Architecture Audit"
Cohesion: 0.15
Nodes (12): 1. Protocol provenance and licensing, 2. Unknown PS5 protocol behavior, 3. Android decoder lifecycle and latency, 4. DualSense semantics through Android, 5. Bluetooth coexistence and audio latency, Architecture Audit, Current architecture, Proposed Rust architecture (+4 more)

### Community 13 - "Audit d'architecture"
Cohesion: 0.15
Nodes (12): 1. Provenance du protocole et licences, 2. Comportement inconnu du protocole PS5, 3. Cycle de vie et latence du décodeur Android, 4. Sémantique DualSense à travers Android, 5. Coexistence Bluetooth et latence audio, Architecture actuelle, Architecture de référence, Architecture Rust proposée (+4 more)

### Community 14 - "License Audit"
Cohesion: 0.14
Nodes (13): Adopted Android runtime libraries (retro section), Choosing between options, Compatible third-party candidates (not yet adopted), Dependency considerations, License Audit, Non-copyright considerations, Option A — reuse or wrap the Chiaki family, Option B — Rust reimplementation (+5 more)

### Community 15 - "Audit des licences"
Cohesion: 0.14
Nodes (13): Audit des licences, Bibliothèques Android adoptées (section rétro), Candidats tiers compatibles (pas encore adoptés), Choisir entre les options, Considérations hors droit d'auteur, Décision de Phase 0, Dépendances, Option A — réutiliser ou envelopper la famille Chiaki (+5 more)

### Community 16 - "Recommended first milestone — M0.5 Evidence Baseline and License Gate"
Cohesion: 0.14
Nodes (13): Acceptance criteria, Current direction (2026-10-31), Files/modules affected, Goal, Independent cross-review gates, Later milestones, M1 entry criteria, Non-goals (+5 more)

### Community 17 - "DualSense Analysis"
Cohesion: 0.17
Nodes (11): DualSense Analysis, Edge preservation, Evidence, Latency and reconnect, Mapping profiles, Proposed normalized model, Required inspector, Stream input ownership and exit (+3 more)

### Community 18 - "Upstream Analysis"
Cohesion: 0.17
Nodes (11): Android forks, Chiaki, chiaki-ng, Evidence limit, Media and input mapping, Pinned Android integration decision (2026-10-28), Protocol architecture mapping, Pylux (+3 more)

### Community 19 - "launch_spec.rs"
Cohesion: 0.04
Nodes (66): AUTH_TABLE_ROW_SIZE, authenticated_test_peer(), base64_encoded_len(), build_launch_spec_oracle(), canonical_maximum_lengths_match_the_reviewed_bounds(), closed_launch_spec_oracle_binds_every_field_and_rejects_mutations(), control_complete(), decode_hex() (+58 more)

### Community 20 - "Android TV Architecture"
Cohesion: 0.22
Nodes (8): Android TV Architecture, Audio, Device facts, GP520 validation, Input, Bluetooth, and security, JNI boundary, Layers, Video

### Community 21 - "Concurrency, Queues, and Ownership"
Cohesion: 0.22
Nodes (8): Audio clock drift, Concurrency, Queues, and Ownership, Lifecycle and shutdown order, Ownership, Principles, Queues, Required tests, Threads (Android)

### Community 22 - "M0.5 baseline runbook"
Cohesion: 0.25
Nodes (7): 1. Record provenance, 2. Capture the Windows environment, 3. Exercise a known-good client, 4. Inspect the GP520, 5. Validate the DualSense matrix, 6. Promote fixtures, M0.5 baseline runbook

### Community 23 - "ADR-0001: Protocol implementation and license strategy"
Cohesion: 0.25
Nodes (7): Accepted decision record, ADR-0001: Protocol implementation and license strategy, Context, Option A — AGPL-compatible reuse, Option B — separated clean-room implementation, Option C — declared-derivative AGPL Rust port, Owner input and recommendation

### Community 24 - "GPSx"
Cohesion: 0.33
Nodes (5): Current status, Evidence handling, GPSx, Prerequisites, Validation

### Community 25 - "Phase 0 Independent Review"
Cohesion: 0.33
Nodes (5): Decision, Findings, Owner constraint added after review, Phase 0 Independent Review, Still open before M1 at review time

### Community 26 - "Testing strategy"
Cohesion: 0.22
Nodes (8): Android native-adapter lifecycle, Android virtual-LAN simulation, Automated gates, Ctrl-response fuzzing, Failure and security testing, Hardware gates, Local connection simulation, Testing strategy

### Community 27 - "Performance baseline"
Cohesion: 0.50
Nodes (3): Performance baseline, Required metrics, Required run metadata

### Community 28 - "Protocol evidence register"
Cohesion: 0.33
Nodes (5): Evidence rules, Local-registration codec, Protocol evidence register, State evidence to capture, UDP discovery adapter

### Community 35 - "fixtures/README.md"
Cohesion: 0.29
Nodes (6): LaunchSpec oracle v1, Registration codec oracle v1, Registration cryptography oracle v2, Reviewed fixtures, Session cryptography dependency audit v1, Session cryptography oracle v1

### Community 36 - "Premier jalon recommandé — M0.5 Référence factuelle et verrou de licence"
Cohesion: 0.18
Nodes (10): Entrée en M1, Feuille de route, Gates de contre-revue indépendante, Hors périmètre, Jalons suivants, Objectif, Orientation actuelle (2026-10-31), Premier jalon recommandé — M0.5 Référence factuelle et verrou de licence (+2 more)

### Community 37 - "Architecture Android TV"
Cohesion: 0.22
Nodes (8): Architecture Android TV, Audio, Couches, Entrée, Bluetooth et sécurité, Faits sur l'appareil, Frontière JNI, Validation sur le GP520, Vidéo

### Community 38 - "Procédure de référence M0.5"
Cohesion: 0.25
Nodes (7): 1. Enregistrer la provenance, 2. Capturer Windows, 3. Tester un client connu fonctionnel, 4. Inspecter le GP520, 5. Valider la DualSense, 6. Promouvoir des fixtures, Procédure de référence M0.5

### Community 39 - "Concurrence, files et ownership"
Cohesion: 0.29
Nodes (6): Concurrence, files et ownership, Files, Ownership et arrêt, Principes, Tests requis, Threads Android

### Community 40 - "ADR-0001 : stratégie de licence et d’implémentation du protocole"
Cohesion: 0.29
Nodes (6): ADR-0001 : stratégie de licence et d’implémentation du protocole, Contexte du responsable et recommandation, Enregistrement de la décision acceptée, Option A — réutilisation compatible AGPL, Option B — clean-room séparée, Option C — port Rust dérivé déclaré sous AGPL

### Community 41 - "Independent Claude Code cross-review gates"
Cohesion: 0.33
Nodes (5): Gates, Handling findings, Independent Claude Code cross-review gates, Prompt template to give the user, Review package

### Community 42 - "Analyse DualSense"
Cohesion: 0.29
Nodes (6): Analyse DualSense, Deux manettes physiques, un seul joueur Remote Play prouvé, Inspecteur et validation, Modèle normalisé, Preuves et hypothèses, Profils, transitions et sortie

### Community 43 - "Contre-revue indépendante de la phase 0"
Cohesion: 0.33
Nodes (5): Constats intégrés, Contrainte propriétaire ajoutée après la revue, Contre-revue indépendante de la phase 0, Décision, Ouvert avant M1 au moment de la revue

### Community 44 - "GPSx"
Cohesion: 0.40
Nodes (4): GPSx, Prérequis, Validation, État actuel

### Community 49 - "PROTOCOL.fr.md"
Cohesion: 0.50
Nodes (3): Adaptateur de découverte UDP, Codec d’enregistrement local, Registre de preuves du protocole

### Community 50 - "TESTING.fr.md"
Cohesion: 0.33
Nodes (5): Cycle de vie de l’adaptateur Android natif, Fuzzing de la réponse ctrl, Simulation Android sur LAN virtuel, Simulation locale de connexion, Stratégie de test

### Community 51 - "fixtures/README.fr.md"
Cohesion: 0.29
Nodes (6): Audit des dépendances de cryptographie de session v1, Fixtures relues, Oracle cryptographique d’enregistrement v2, Oracle de cryptographie de session v1, Oracle du codec d’enregistrement v1, Oracle LaunchSpec v1

### Community 54 - "RemoteNavigationTest.kt"
Cohesion: 0.09
Nodes (3): RemoteNavigationTest, greetingFor(), GreetingTest

### Community 55 - "Offline-first development before GP520 access"
Cohesion: 0.29
Nodes (6): Allowed before the hardware gate, Architecture consequence, Evidence boundaries, Offline-first development before GP520 access, Physical gate opened (2026-10-31), Physical-hardware gate

### Community 56 - "Développement offline-first avant accès au GP520"
Cohesion: 0.29
Nodes (6): Autorisé avant le gate matériel, Conséquence architecturale, Développement offline-first avant accès au GP520, Frontière des preuves, Gate matériel physique, Gate physique ouvert (2026-10-31)

### Community 57 - "Android TV interface specification"
Cohesion: 0.33
Nodes (5): Acceptance for this slice, Android TV interface specification, Current screens, Interaction contract, Visual direction

### Community 58 - "Spécification de l’interface Android TV"
Cohesion: 0.33
Nodes (5): Acceptation de cette tranche, Contrat d’interaction, Direction visuelle, Spécification de l’interface Android TV, Écrans actuels

### Community 59 - "Feature parity baseline"
Cohesion: 0.50
Nodes (3): Feature parity baseline, Product rules, Sources

### Community 60 - "Socle de parité fonctionnelle"
Cohesion: 0.50
Nodes (3): Règles produit, Socle de parité fonctionnelle, Sources

### Community 61 - "DiagnosticExportError"
Cohesion: 0.12
Nodes (13): append_bounded_line(), DiagnosticExportError, Formatting, InvalidSourceRevision, NonFiniteValue, NonMonotonicSamples, OutputTooLarge, SyntheticFactEvidence (+5 more)

### Community 62 - "SmbRomSource.kt"
Cohesion: 0.15
Nodes (3): SmbCredentials, SmbRomSource, FilterInputStream

### Community 65 - "Embedded diagnostic lab"
Cohesion: 0.29
Nodes (6): Captured signals, Comparison rules, Embedded diagnostic lab, Export bundle, Guided scenarios, Optional private GitHub path

### Community 66 - "Laboratoire de diagnostic embarqué"
Cohesion: 0.29
Nodes (6): Chemin GitHub privé facultatif, Laboratoire de diagnostic embarqué, Paquet d’export, Règles de comparaison, Scénarios guidés, Signaux capturés

### Community 67 - "GPSx update channel"
Cohesion: 0.40
Nodes (4): Bootstrap installation, GPSx update channel, In-application update, Security rules

### Community 68 - "LargeTextLegalTest.kt"
Cohesion: 0.10
Nodes (3): CloudLaunchScreenTest, LargeTextLegalTest, GpsxTheme()

### Community 70 - "extract_diagnostic_zip"
Cohesion: 0.13
Nodes (6): extract_diagnostic_zip(), _extract_to_staging(), _safe_relative_path(), UnsafeArchiveError, _validate_type(), SafeArchiveTests

### Community 71 - "steps"
Cohesion: 0.09
Nodes (23): runs-on, steps, jobs, core-and-android, wrapper-validation, name, permissions, contents (+15 more)

### Community 72 - "CR3 protocol security plan"
Cohesion: 0.29
Nodes (6): CR3 protocol security plan, Cryptography decision for later slices, Fuzzing plan, Historical authorization — first SRCH slice, Required CR3 evidence, Trust boundary and parser limits

### Community 73 - "Plan de sécurité protocolaire CR3"
Cohesion: 0.29
Nodes (6): Autorisation historique — premier lot SRCH, Décision cryptographique pour les lots ultérieurs, Frontière de confiance et limites des parseurs, Plan de fuzzing, Plan de sécurité protocolaire CR3, Preuves exigées pour CR3

### Community 74 - "Registre prévu des dépendances cryptographiques"
Cohesion: 0.40
Nodes (4): Gate de sécurité et d’acceptation, Graphe d’implémentation de session — 2026-10-28, Graphe résolu limité à l’enregistrement — 2026-10-26, Registre des dépendances cryptographiques

### Community 76 - "http.rs"
Cohesion: 0.09
Nodes (16): accepts_all_line_endings_and_final_line_without_eol(), accepts_utf8_values_and_rejects_invalid_or_control_values(), applies_strict_header_grammar(), BoundedLines, BoundedLines<'a>, enforces_datagram_boundaries(), Header, HttpResponse (+8 more)

### Community 77 - "gpsx-crypto/src/registration.rs"
Cohesion: 0.06
Nodes (42): AeropauseTableOffset, AMBASSADOR, assert_codec_oracle_crypto(), assert_oracle(), CipherTableOffset, codec_oracle_crypto_fields_are_bound_for_all_nine_cases(), decode(), decode_table() (+34 more)

### Community 79 - "CR1 remediation record"
Cohesion: 0.40
Nodes (4): Closed in this remediation, CR1 remediation record, Gates still closed, Verification commands

### Community 80 - "Registre de remédiation CR1"
Cohesion: 0.40
Nodes (4): Commandes de vérification, Fermé par cette remédiation, Gates encore fermées, Registre de remédiation CR1

### Community 81 - "ControllerId"
Cohesion: 0.18
Nodes (12): ControllerId, ControllerSet, ControllerState, ControllerUpdateError, Capacity, ReservedId, keeps_controllers_separate(), MergedControllerUpdate (+4 more)

### Community 82 - "gpsx-input/src/lib.rs"
Cohesion: 0.19
Nodes (12): diff(), full_trigger_sweep_never_overflows_and_keeps_both_crossings(), internal_button_masks_are_distinct_low_16_bits(), MAX_LOCAL_CONTROLLERS, MAX_TRANSITIONS_PER_CHANGE, merged_buttons_release_only_after_every_controller_releases(), neutral_state_releases_everything_held(), overflow_rejects_whole_batch_and_is_counted() (+4 more)

### Community 83 - "android_virtual_lan.rs"
Cohesion: 0.07
Nodes (18): android_virtual_lan_discovers_and_opens_ctrl_to_fake_console(), CONTROL_PORT, DISCOVERY_PORT, IO_TIMEOUT, MORNING, NONCE, PatternRng, read_bounded_response() (+10 more)

### Community 84 - "ControllerState"
Cohesion: 0.25
Nodes (9): Axis, LeftX, LeftY, RightX, RightY, merged_analog_policy_is_max_trigger_and_greatest_axis_magnitude(), preserves_wire_exact_stick_extremes(), sticks_and_intermediate_trigger_values_are_state_not_transitions() (+1 more)

### Community 85 - "RetroLibrary"
Cohesion: 0.14
Nodes (5): compatible(), incompatible(), RetroLibrary, RetroSnapshot, RetroGame

### Community 86 - "Registration codec plan"
Cohesion: 0.17
Nodes (11): Decrypted response fields (C3), Deterministic evidence plan (C7), Evidence and provenance (C5), Failure contract and deferred UDP search, Layer boundary and allocation policy, Ownership and secret handling (C6), Profiles and fixed request constants (C2), Registration codec plan (+3 more)

### Community 87 - "RetroConsoleArt.kt"
Cohesion: 0.18
Nodes (7): drawShell(), StandingConsoleArt(), drawArcadeCabinet(), drawBlackCartridgeConsole(), drawGreyCartridgeConsole(), floorShadow(), RetroConsoleArt()

### Community 90 - "MetricName"
Cohesion: 0.09
Nodes (21): MetricName, AudioBufferDepth, AudioDrift, AudioRouteChanges, AudioUnderruns, InputEventInterval, InputToResponse, NetworkBitrate (+13 more)

### Community 92 - "HttpParseError"
Cohesion: 0.12
Nodes (14): HttpParseError, ControlCharacter, DatagramTooLarge, DuplicateSensitiveHeader, EmbeddedNul, EmptyHeaderValue, HeaderAfterTerminator, InvalidHeaderName (+6 more)

### Community 93 - "encode_diagnostic_jsonl"
Cohesion: 0.26
Nodes (16): diagnostic_jsonl_enforces_evidence_environment_combinations(), diagnostic_jsonl_enforces_sample_and_byte_bounds(), diagnostic_jsonl_is_exact_and_deterministic(), diagnostic_jsonl_rejects_invalid_revision_without_echoing_it(), diagnostic_jsonl_requires_non_decreasing_monotonic_times(), diagnostic_jsonl_serializes_every_evidence_class(), diagnostic_jsonl_uses_closed_metric_names_and_units(), DiagnosticRunMetadata (+8 more)

### Community 94 - "DiagnosticRunKind"
Cohesion: 0.29
Nodes (6): DiagnosticRunKind, Baseline, Candidate, Synthetic, is_sensitive_key(), key_tokens()

### Community 95 - "EvidenceClass"
Cohesion: 0.29
Nodes (6): EvidenceClass, Fact, Hypothesis, Inference, NotTested, Unknown

### Community 96 - "DiagnosticEnvironment"
Cohesion: 0.33
Nodes (5): DiagnosticEnvironment, AndroidEmulator, AndroidGeneric, Gp520, Windows

### Community 97 - "Diagnostic export"
Cohesion: 0.29
Nodes (6): Bounds and failure behavior, Compatibility, Diagnostic export, JSONL v1, Metric semantics, Purpose and boundary

### Community 98 - "Export des diagnostics"
Cohesion: 0.29
Nodes (6): Bornes et comportement en cas d'échec, Compatibilité, Export des diagnostics, JSONL v1, Objectif et frontière, Sémantique des métriques

### Community 100 - "Registration cryptography plan"
Cohesion: 0.25
Nodes (7): Adopted dependency subset, API and secret invariants, Evidence and scope, Implementation files, Later gates, Registration cryptography plan, Tests required before review

### Community 101 - "Plan cryptographique de l’enregistrement"
Cohesion: 0.25
Nodes (7): API et invariants des secrets, Fichiers d’implémentation, Plan cryptographique de l’enregistrement, Portes ultérieures, Preuves et périmètre, Sous-ensemble de dépendances adopté, Tests exigés avant revue

### Community 102 - "gpsx-transport/src/lib.rs"
Cohesion: 0.12
Nodes (22): ConsoleGeneration, Ps4, Ps5, format_search_request(), generation_label(), closed_loopback_port_returns_report_and_counts_windows_icmp_reset(), DEFAULT_RESPONSE_WINDOW, discover() (+14 more)

### Community 103 - "gpsx-discover.rs"
Cohesion: 0.12
Nodes (15): ANDROID_RELAY_ADDRESS, ANDROID_RELAY_REQUEST, arguments(), handle_android_relay_request(), live_network_mode_requires_one_exact_argument(), main(), Mode, AndroidRelay (+7 more)

### Community 105 - "DiscoveryError"
Cohesion: 0.11
Nodes (16): error_label(), DiscoveryError, InvalidResponseWindow, Io, DiscoveryOperation, Bind, ConfigureBroadcast, ConfigureTimeout (+8 more)

### Community 106 - "control_handshake_oracle_tests.rs"
Cohesion: 0.12
Nodes (30): assert_finish_rejection_is_atomic(), assert_public_finish(), build_control_oracle(), build_requests(), build_responses(), closed_control_oracle_binds_every_field_and_rejects_mutations(), encode_b64(), encode_hex() (+22 more)

### Community 107 - ".wakeAndWait"
Cohesion: 0.11
Nodes (15): isSessionPortOpen(), Android UI integration, Bounds and failure behavior, Evidence boundary, Bornes et comportement en cas d’échec, Frontière des preuves, Intégration dans l’interface Android, Périmètre (+7 more)

### Community 108 - ".protect_in_place"
Cohesion: 0.27
Nodes (6): LocalTrafficKeys, outbound_allocator_never_repeats_tag_blocks(), OutboundKeyPositionAllocator, OutboundPacketPositions, TrafficKeyPair, validate_encrypted_range()

### Community 109 - "gpsx-protocol/src/registration.rs"
Cohesion: 0.08
Nodes (39): ACCOUNT, ACCOUNT_ID_BASE64_SIZE, ACCOUNT_ID_SIZE, AEROPAUSE_SIZE, arbitrary_inputs_are_deterministic_and_never_panic(), content_length_grammar_and_exact_body_are_enforced(), decrypted_field_line_grammar_is_exact(), decrypted_fields_are_strict_borrowed_and_complete() (+31 more)

### Community 111 - "registration_codec_oracle.rs"
Cohesion: 0.24
Nodes (10): Case, exact(), FIXTURE, Inputs, number_value(), offset_value(), Oracle, parse() (+2 more)

### Community 112 - "RegistrationCodecError"
Cohesion: 0.05
Nodes (36): error_of(), RegistrationCodecError, ArithmeticOverflow, BodyLengthMismatch, BodyTooLarge, ControlCharacter, CryptoTargetMismatch, DuplicateContentLength (+28 more)

### Community 113 - "Composable"
Cohesion: 0.13
Nodes (40): AboutScreen(), accent(), ArcadeTile(), BlurredArtBackground(), CapabilityGrid(), CloudCatalogPanel(), CloudGameCard(), CloudLaunchScreen() (+32 more)

### Community 114 - "format_account_id_inner"
Cohesion: 0.19
Nodes (13): AccountId<'a>, assert_codec_oracle(), codec_oracle_binds_all_nine_complete_cases(), format_online_id_inner(), format_ps4_9_online_id_compatibility(), format_registration_inner_request(), identity_profiles_and_exact_inner_headers_are_bound(), OnlineId (+5 more)

### Community 116 - "ControlProfile"
Cohesion: 0.10
Nodes (15): ControlEndpoint, ControlProfile, Ps4_10, Ps4_8, Ps4_9, Ps5, ControlRequestLength, FixedWriter<'a> (+7 more)

### Community 117 - "AAudioStream"
Cohesion: 0.27
Nodes (13): test_close_stream(), test_get_state(), test_invoke_error_callback(), test_request_start(), test_request_stop(), test_stream(), test_wait_for_state_change(), TestAAudioStream (+5 more)

### Community 118 - "LaunchSpec and session-rpcrypt plan (pre-implementation)"
Cohesion: 0.22
Nodes (8): Canonical formatting contract, Cryptographic contract, LaunchSpec and session-rpcrypt implementation contract, Proposed ownership and API, Purpose and evidence, Required closed oracle, Source and license boundary, Test and implementation gate

### Community 119 - "Q: Implement the approved CR3 session cryptography plan after owner acceptance of the 32-bit tag residual risk"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Implement the approved CR3 session cryptography plan after owner acceptance of the 32-bit tag residual risk, Source Nodes

### Community 120 - "Plan du codec d'enregistrement"
Cohesion: 0.17
Nodes (11): API de requête et invariants de disposition (C1), Cadrage et limites de l'enregistrement (C4), Champs de réponse déchiffrés (C3), Contrat d'échec et recherche UDP reportée, Frontière entre couches et politique d'allocation, Gate de revue, Plan de preuves déterministes (C7), Plan du codec d'enregistrement (+3 more)

### Community 121 - "PadInput"
Cohesion: 0.14
Nodes (13): PadInput, CIRCLE, CREATE, CROSS, DPAD, L1, L2, OPTIONS (+5 more)

### Community 122 - "RegistrationProfile"
Cohesion: 0.17
Nodes (12): checked_sum(), ensure_ps4(), ensure_ps5(), format_decimal(), format_registration_http_request_header(), outer_request_headers_are_exact_for_every_profile(), RegistrationPayload, RegistrationProfile (+4 more)

### Community 123 - "Q: Close codec review findings A1 and A2 and prepare the authorized external R5 oracle evidence"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Close codec review findings A1 and A2 and prepare the authorized external R5 oracle evidence, Source Nodes

### Community 124 - "Q: Remediate the independent CR3 follow-up findings B1-B5 for session cryptography"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Remediate the independent CR3 follow-up findings B1-B5 for session cryptography, Source Nodes

### Community 126 - "Q: Remediate the CR3 session-crypto implementation review: make check.ps1 discover pinned Android NDK 28.2.13676358 in a clean environment, add the 2049-byte receive rejection test, non-Clone API checks, and EN/FR documentation."
Cohesion: 0.50
Nodes (3): Answer, Outcome, Q: Remediate the CR3 session-crypto implementation review: make check.ps1 discover pinned Android NDK 28.2.13676358 in a clean environment, add the 2049-byte receive rejection test, non-Clone API checks, and EN/FR documentation.

### Community 127 - "Session cryptography plan"
Cohesion: 0.22
Nodes (8): Entropy, caching, performance, and provenance, Integrated dependency decision, Key-position and outbound-allocation policy, Required API and state invariants, Scope and evidence, Session cryptography plan, Short-tag and counter safety gate, Test and evidence gate

### Community 128 - "Q: Implement the reviewed bounded registration codec without generating the R5-blocked C oracle or committing stubs"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Implement the reviewed bounded registration codec without generating the R5-blocked C oracle or committing stubs, Source Nodes

### Community 129 - "Q: Apply the GO WITH CHANGES remediation for codec blockers B1 and B2 and add the missing R5-a provenance requirements without generating the oracle"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Apply the GO WITH CHANGES remediation for codec blockers B1 and B2 and add the missing R5-a provenance requirements without generating the oracle, Source Nodes

### Community 130 - "Plan de cryptographie de session"
Cohesion: 0.22
Nodes (8): API et invariants d’état requis, Décision intégrée sur les dépendances, Entropie, cache, performance et provenance, Gate de sûreté du tag court et du compteur, Gate de tests et de preuves, Plan de cryptographie de session, Politique de position et d’allocation sortante, Périmètre et preuves

### Community 131 - "Q: Apply the independent GO verdict for codec oracle B1-B4 and include the three LOW documentation fixes"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Apply the independent GO verdict for codec oracle B1-B4 and include the three LOW documentation fixes, Source Nodes

### Community 132 - "Q: Close codec oracle integration findings B1-B4 without committing R5 external artifacts"
Cohesion: 0.50
Nodes (3): Answer, Outcome, Q: Close codec oracle integration findings B1-B4 without committing R5 external artifacts

### Community 133 - "Q: Où en est le projet et le test réel avec la simulation sous Windows ?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Où en est le projet et le test réel avec la simulation sous Windows ?, Source Nodes

### Community 134 - "Q: Remediate CR3 follow-up blocker C1 for HandshakeKey LaunchSpec egress"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Remediate CR3 follow-up blocker C1 for HandshakeKey LaunchSpec egress, Source Nodes

### Community 135 - "Q: What is the next safe project slice after the reviewed registration codec, and what must block session-cryptography implementation?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: What is the next safe project slice after the reviewed registration codec, and what must block session-cryptography implementation?, Source Nodes

### Community 136 - "session.rs"
Cohesion: 0.05
Nodes (22): Aes128CtrLe, Aes128Gcm16, AwaitingPeer, BLOCK_SIZE, GMAC_EPOCH_IV_OFFSET, GMAC_EPOCH_POSITIONS, GMAC_TAG_SIZE, HANDSHAKE_KEY_SIZE (+14 more)

### Community 137 - ".finish"
Cohesion: 0.12
Nodes (9): all_secret_owners_are_redacted(), EphemeralScalar, HandshakeKey, HandshakeSession, HandshakeSession<AwaitingPeer>, LocalHandshakeMaterial, peer_mutations_fail_before_key_derivation(), pinned_ecdh_vector_authenticates_and_derives() (+1 more)

### Community 138 - "SessionCryptoError"
Cohesion: 0.12
Nodes (17): SessionCryptoError, AuthenticationFailed, CryptoInitialization, EntropyUnavailable, FailureLimitReached, InvalidEncryptedRange, InvalidPeerMaterialLength, InvalidPeerPublicKey (+9 more)

### Community 139 - "Plan LaunchSpec et rpcrypt de session (avant implémentation)"
Cohesion: 0.22
Nodes (8): But et preuves, Contrat cryptographique, Contrat d’implémentation LaunchSpec et rpcrypt de session, Contrat de formatage canonique, Frontière des sources et licences, Gate de tests et d’implémentation, Oracle fermé requis, Ownership et API proposés

### Community 140 - "Q: Is the GPSx session-cryptography slice integrated after the independent C1 GO, and what conditions remain?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Is the GPSx session-cryptography slice integrated after the independent C1 GO, and what conditions remain?, Source Nodes

### Community 141 - ".verify_and_decrypt_in_place"
Cohesion: 0.15
Nodes (11): EpochUsage, InvocationState, key_state_vectors_and_half_range_edge_match(), KeyPositionReconstructor, oversized_receive_packets_fail_before_gmac_and_exhaust_global_budget(), ReceiveOutcome, Authenticated, StaleDiscarded (+3 more)

### Community 142 - "Session"
Cohesion: 0.11
Nodes (3): ChiakiNative, CreateResult, Regist

### Community 144 - "Q: What is the current session cryptography oracle integration status, evidence, and remaining gate?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: What is the current session cryptography oracle integration status, evidence, and remaining gate?, Source Nodes

### Community 145 - "Host"
Cohesion: 0.04
Nodes (46): close_audio(), close_sles(), Host, aaudio, audio_open, av, buttons, core (+38 more)

### Community 146 - "Q: Fix CR3 D1 epoch boundary inconsistency"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Fix CR3 D1 epoch boundary inconsistency, Source Nodes

### Community 147 - "control_handshake.rs"
Cohesion: 0.06
Nodes (42): begin_control_is_atomic_and_profile_bound(), canonical_requests_have_exact_lengths_counters_and_did_layout(), codec_and_classification_mismatches_are_rejected_before_state_changes(), CONTROL_PORT, ControlChannelCrypto, ControlHandshake, ControlHandshakeResponse, ControlHeaderLength (+34 more)

### Community 148 - "RemotePlayEngine"
Cohesion: 0.13
Nodes (3): audioOutputTypeLabel(), RemotePlayEngine, RemotePlaySnapshot

### Community 149 - "ControlHandshakeError"
Cohesion: 0.08
Nodes (22): ControlHandshakeError, AlreadyStarted, ControlNotFinished, CryptoInitialization, DuplicateHeader, EntropyUnavailable, FormattingFailed, HeaderLineTooLong (+14 more)

### Community 150 - "StreamTestRecorder.kt"
Cohesion: 0.20
Nodes (11): compactReport(), reportPath(), reportUploadUrl(), buildStreamTestReport(), decodedCodecLabel(), NetworkInfo, percentile(), probeStats() (+3 more)

### Community 151 - "Q: Remediate the independent CR3 implementation review findings: outbound epoch exhaustion, full-packet GMAC versus payload encryption, SEC1 hybrid prefixes, missing tests, vendored libsecp256k1 MIT licensing, and weakened cargo-deny policy"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Remediate the independent CR3 implementation review findings: outbound epoch exhaustion, full-packet GMAC versus payload encryption, SEC1 hybrid prefixes, missing tests, vendored libsecp256k1 MIT licensing, and weakened cargo-deny policy, Source Nodes

### Community 152 - "retro_api.h"
Cohesion: 0.06
Nodes (35): AudioSampleBatchFn, AudioSampleFn, EnvironmentFn, GameGeometry, aspect_ratio, base_height, base_width, max_height (+27 more)

### Community 153 - "Q: Did the C1 remediation make the session oracle test bind every fixture field?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Did the C1 remediation make the session oracle test bind every fixture field?, Source Nodes

### Community 154 - "gpsx-protocol/src/lib.rs"
Cohesion: 0.12
Nodes (18): debug_and_display_redact_host_identity(), DiscoveryHost, DiscoveryParseError, Http, InvalidHostRequestPort, first_non_sensitive_duplicate_wins(), maps_status_codes(), parse_errors_do_not_echo_identifiers() (+10 more)

### Community 155 - "Assert-SingleAndroidEmulator"
Cohesion: 0.43
Nodes (3): Assert-SingleAndroidEmulator(), Get-AndroidDeviceEntries(), Assert-Rejected()

### Community 156 - "ControllerMapper.kt"
Cohesion: 0.22
Nodes (4): buttonNames(), sonyHidScanCodeToPs5Button(), usesSonyRawHidLayout(), RetroPlayerInput

### Community 157 - "ControllerMapper"
Cohesion: 0.20
Nodes (3): ControllerMapper, PlatformControllerState, QuitChord

### Community 158 - "DiscoveryReport"
Cohesion: 0.10
Nodes (12): DiscoveryState, Ready, Standby, Unknown, state_label(), capacity_keeps_first_sixteen_unique_addresses_and_counts_drops(), connection_refused_is_non_fatal_for_portability(), debug_output_redacts_peer_address() (+4 more)

### Community 159 - "TrafficCore"
Cohesion: 0.16
Nodes (12): build_session_oracle(), counter_at(), decode(), decode_vec(), derive_gmac_key(), pinned_epoch_key_vector_matches(), pinned_traffic_derivation_and_ctr_vectors_match(), pinned_unaligned_ctr_vector_round_trips() (+4 more)

### Community 160 - "AAudioApi"
Cohesion: 0.08
Nodes (19): AAudioApi, builder_delete, close_stream, create_stream_builder, get_state, library, open_stream, request_start (+11 more)

### Community 161 - "rpcrypt_tables.rs"
Cohesion: 0.29
Nodes (7): AUTH_TABLE_SIZE, decode_table(), hex_nibble(), KEYS_A_PS4, KEYS_A_PS5, KEYS_B_PS4, KEYS_B_PS5

### Community 162 - "wrap.sh"
Cohesion: 0.50
Nodes (3): ASAN_OPTIONS, LD_PRELOAD, wrap.sh script

### Community 163 - "RetroStorage"
Cohesion: 0.18
Nodes (6): RetroStorage, RetroStorageUse, SettingRow(), SettingsScreen(), yesNo(), RetroStorageRows()

### Community 166 - "Engine"
Cohesion: 0.07
Nodes (26): acquire_window(), Engine, aaudio_, audio_error_, audio_stream_, codec_, control_mutex_, force_codec_failure_ (+18 more)

### Community 167 - "Control-channel HTTP handshake implementation contract"
Cohesion: 0.20
Nodes (9): Canonical request contract, Control-channel HTTP handshake implementation contract, Counter and residual-risk contract, Proposed ownership and API, Purpose and evidence, Required closed oracle, Scope and explicit exclusions, Source and license boundary (+1 more)

### Community 168 - "Contrat d’implémentation de la négociation HTTP du canal de contrôle"
Cohesion: 0.20
Nodes (9): But et preuves, Contrat d’implémentation de la négociation HTTP du canal de contrôle, Contrat de requête canonique, Contrat des compteurs et risque résiduel, Frontière de sources et de licence, Gate de test et d’implémentation, Oracle fermé requis, Propriété et API proposées (+1 more)

### Community 169 - "RetroSession"
Cohesion: 0.12
Nodes (4): RetroNative, RetroSession, RetroSessionSnapshot, RetroPlayScreen()

### Community 170 - "native_adapter.cpp"
Cohesion: 0.08
Nodes (21): force_audio_disconnect_for_test(), set_force_codec_failure_for_test(), set_lifecycle_test_mode_for_test(), test_builder(), test_builder_delete(), test_create_stream_builder(), test_open_stream(), test_set_channel_count() (+13 more)

### Community 173 - ".close"
Cohesion: 0.17
Nodes (8): Android native adapter — first CR4 slice, Evidence, Explicit limits, Adaptateur Android natif — première tranche CR4, Limites explicites, Preuves, Périmètre, Scope

### Community 175 - "gpsx-android-input/src/lib.rs"
Cohesion: 0.17
Nodes (12): ANDROID_RELAY_PORT, ANDROID_RELAY_REQUEST, Bridge, bridge(), BRIDGE, clamp_axis(), EDGE_QUEUE_DEPTH, Java_com_gpsx_tv_remoteplay_GpsxDiscoveryBridge_nativeDiscoverPs5ViaEmulatorHost() (+4 more)

### Community 176 - "RetroGamesScreen"
Cohesion: 0.33
Nodes (10): RetroShelf, facts(), rememberRetroArt(), RetroConsoleCard(), RetroConsolesPanel(), RetroControlsPanel(), RetroGameCard(), RetroGameSheet() (+2 more)

### Community 178 - "retro_host.cpp"
Cohesion: 0.07
Nodes (12): CoreApi, AAudioDataCallback, thread, Java_com_gpsx_tv_retro_RetroNative_nativeOpen(), load_sram(), read_file(), resolve_core(), save_sram() (+4 more)

### Community 179 - "Analyse de l'amont"
Cohesion: 0.17
Nodes (11): Analyse de l'amont, Chiaki, chiaki-ng, Correspondance de l'architecture du protocole, Correspondance des médias et des entrées, Décision d'intégration Android épinglée (2026-10-28), Forks Android, Limite des preuves (+3 more)

### Community 180 - "control_response.rs"
Cohesion: 0.18
Nodes (3): MAX_CONTROL_HEADER_SIZE, MAX_CONTROL_REQUEST_SIZE, FixedRng

### Community 181 - ".attach_surface"
Cohesion: 0.33
Nodes (14): find_engine(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeAttachSurface(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeAudioHealth(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeClearSurface(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeCreate(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeDestroy(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeForceAudioDisconnectForTest(), Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeInstallCallbackDuringStopForTest() (+6 more)

### Community 183 - "PcmRing"
Cohesion: 0.18
Nodes (4): PcmRing, read_index_, samples_, write_index_

### Community 184 - "StreamQuality"
Cohesion: 0.13
Nodes (12): ChiakiSessionAbiTest, videoProfileFor(), StreamQuality, AUTO, FULL_HD_1080, HD_720, Codec, H264 (+4 more)

### Community 185 - "NativeAudioHealth"
Cohesion: 0.18
Nodes (8): NativeResourceCounts, NativeStatus, AUDIO_ERROR, CLOSED, CODEC_ERROR, INVALID_SURFACE, OK, STALE_GENERATION

### Community 186 - "GameControllerMonitor"
Cohesion: 0.18
Nodes (6): ConnectedGameController, GameControllerKind, DUALSENSE, GENERIC, XBOX, GameControllerMonitor

### Community 187 - "Java_com_gpsx_tv_retro_RetroNative_nativeClose"
Cohesion: 0.35
Nodes (7): Java_com_gpsx_tv_retro_RetroNative_nativeClose(), Java_com_gpsx_tv_retro_RetroNative_nativeGeometry(), Java_com_gpsx_tv_retro_RetroNative_nativeLastError(), Java_com_gpsx_tv_retro_RetroNative_nativeSetInput(), Java_com_gpsx_tv_retro_RetroNative_nativeSetSurface(), Java_com_gpsx_tv_retro_RetroNative_nativeStart(), Java_com_gpsx_tv_retro_RetroNative_nativeStats()

### Community 189 - "RemotePlayPhase"
Cohesion: 0.18
Nodes (11): RemotePlayPhase, CONNECTING, FAILED, LOGIN_PIN_REQUIRED, NEEDS_REGISTRATION, READY, REGISTERING, STOPPED (+3 more)

### Community 190 - "CloudPlayController.kt"
Cohesion: 0.16
Nodes (12): CloudResolution, FULL_HD_1080, HD_720, cloudCatalogFetchUnified(), CloudProvisionCallbacks, CloudProvisionRequest, CloudProvisionResult, cloudProvisionSession() (+4 more)

### Community 192 - "Registre de provenance"
Cohesion: 0.22
Nodes (8): Base dérivée principale, Entrées de fichiers dérivés, Entrées de fichiers prévues pour CR3, Entrées de fixtures relues, Fichiers plateforme écrits par GPSx, Projets de référence uniquement, Registre de provenance, Règles

### Community 193 - "Android input adapter plan"
Cohesion: 0.18
Nodes (10): Android input adapter plan, Bluetooth controller UI, Closing tests, Coarse JNI contract, Device identity and routing, Explicit residuals, Native safety, Ownership and layers (+2 more)

### Community 194 - "ConnectInfo"
Cohesion: 0.16
Nodes (16): CloudCatalog, ConnectInfo, Avancement (2026-10-31), Base amont, Ce que GPSx fait autrement, Objectif, Plan du jeu cloud PlayStation Plus, Risques et inconnues (+8 more)

### Community 195 - "AppUiState"
Cohesion: 0.08
Nodes (29): AppUiState, ConfirmDemoStarted, CycleQuality, Destination, ABOUT, CLOUD, CONSOLES, CONTROLLERS (+21 more)

### Community 196 - "Plan de l’adaptateur d’entrée Android"
Cohesion: 0.18
Nodes (10): Contrat JNI grossier, Identité et routage des appareils, Interface des manettes Bluetooth, Ownership et couches, Plan de l’adaptateur d’entrée Android, Périmètre et frontière de preuve, Résiduels explicites, Sûreté native (+2 more)

### Community 197 - "RemotePlaySurfaceController"
Cohesion: 0.06
Nodes (15): StreamSurfaceController, readSystemProperty(), startLocalSignIn(), DiscoveryResult, Failed, Found, GpsxDiscoveryBridge, HostBridgeUnavailable (+7 more)

### Community 198 - "NativeAudioHealth"
Cohesion: 0.33
Nodes (5): NativeAudioHealth, CLOSED, DISCONNECTED, READY, UNAVAILABLE

### Community 201 - "gpsx-protocol/src/lib.rs"
Cohesion: 0.14
Nodes (16): encrypted_length_mismatch_fails_before_finalization(), finish_registration_request_payload(), MAX_REGISTRATION_BODY_SIZE, MAX_REGISTRATION_FIELD_COUNT, MAX_REGISTRATION_FIELD_LINE_SIZE, MAX_REGISTRATION_FIELD_NAME_SIZE, MAX_REGISTRATION_REQUEST_HEADER_SIZE, MAX_REGISTRATION_RESPONSE_HEADER_SIZE (+8 more)

### Community 202 - "ChiakiCompat.kt"
Cohesion: 0.13
Nodes (8): ChiakiLog, ConnectedEvent, LoginPinRequestEvent, QuitEvent, QuitReason, RumbleEvent, Session, SessionEvent

### Community 205 - "Retro-gaming plan"
Cohesion: 0.13
Nodes (14): Architecture, Compatibility as you play, Cores, Goal, Library (R1), Native host (R2), Non-goals, Order of work (+6 more)

### Community 206 - "RemotePlayEngine.kt"
Cohesion: 0.10
Nodes (10): bluetoothOutputActive(), preferredAudioOutput(), GameRefused, RegistEvent, RegistEventCanceled, RegistEventFailed, RegistEventSuccess, setAudioCompatibilityMode() (+2 more)

### Community 207 - "Plan rétro-gaming"
Cohesion: 0.13
Nodes (14): Architecture, Avancement (01/11/2026), Compatibilité au fil des parties, Contenu du partage (FACT, inventaire en lecture seule du 01/11/2026), Cores, Décisions du propriétaire (01/11/2026), Hors périmètre, Hôte natif (R2) (+6 more)

### Community 208 - ".check"
Cohesion: 0.47
Nodes (5): Available, Failed, ReleaseManifest, UpdateCheck, UpToDate

### Community 209 - "format_account_id_inner"
Cohesion: 0.40
Nodes (6): encode_exact(), AccountId, encode_base64(), ensure_output(), format_account_id_inner(), rfc4648_base64_vectors_are_reproduced()

### Community 212 - "RegistrationCryptoTarget"
Cohesion: 0.40
Nodes (5): RegistrationCryptoTarget, Ps4Modern, Ps4Pre10, Ps5, validate_registration_crypto_target()

### Community 215 - "Stream tuning for the GP520 over Wi-Fi"
Cohesion: 0.33
Nodes (5): Before/after (2026-10-31, Google TV API 36 x86_64 emulator, real PS5 home screen), Owner settings on the GP520, Stream tuning for the GP520 over Wi-Fi, Target, What GPSx does

### Community 216 - "CloudCatalog.kt"
Cohesion: 0.20
Nodes (8): boundedString(), CloudStoreRegion, parseCloudCatalog(), provisioningFailureLabel(), storeRegionFor(), toCloudGame(), normalizeNpsso(), CloudCatalogTest

### Community 217 - "Réglage du stream pour le GP520 en Wi-Fi"
Cohesion: 0.33
Nodes (5): Avant/après (2026-10-31, émulateur Google TV API 36 x86_64, écran d'accueil réel de la PS5), Ce que fait GPSx, Cible, Réglage du stream pour le GP520 en Wi-Fi, Réglages du propriétaire sur le GP520

### Community 221 - "RetroPhase"
Cohesion: 0.40
Nodes (5): RetroPhase, FAILED, IDLE, PLAYING, PREPARING

### Community 222 - "LocalTokenServer"
Cohesion: 0.06
Nodes (10): Current, LiveLogServer, CloudArtCache, loadArt(), smoothBlur(), Ignored, LocalTokenServer, Outcome (+2 more)

### Community 223 - "LogDigestEntry"
Cohesion: 0.46
Nodes (3): digestSessionLog(), LogDigestEntry, SessionLogDigestTest

### Community 225 - "Installing and testing GPSx on the GP520"
Cohesion: 0.20
Nodes (9): 1. Install with Downloader, 2. Update from inside GPSx, 3. Recommended projector and network settings, 4. Run a test, 5. Send results to GitHub (QR code, no token), 6. Cloud sign-in (QR code, local Wi-Fi), 7. Live log (remote debugging), Installing and testing GPSx on the GP520 (+1 more)

### Community 226 - "Installer et tester GPSx sur le GP520"
Cohesion: 0.20
Nodes (9): 1. Installer avec Downloader, 2. Mettre à jour depuis GPSx, 3. Réglages conseillés du projecteur et du réseau, 4. Faire un test, 5. Envoyer les résultats sur GitHub (QR code, sans jeton), 6. Connexion cloud (QR code, Wi-Fi local), 7. Journal en direct (dépannage à distance), Format des rapports (+1 more)

### Community 228 - ".load"
Cohesion: 0.12
Nodes (15): AAudioApi, audio_batch(), audio_callback(), audio_sample(), input_state(), open_audio(), resolve(), run_loop() (+7 more)

### Community 229 - "TransitionKind"
Cohesion: 0.25
Nodes (7): kinds(), TransitionKind, Button, Trigger, Trigger, L2, R2

### Community 231 - "CoreInstaller"
Cohesion: 0.21
Nodes (4): Ps5ButtonMask, CoreInstaller, RetroPad, RetroPlayTest

### Community 232 - "ControllerState"
Cohesion: 0.12
Nodes (11): ControllerState, ControllerTouch, RegistHost, RegistInfo, Target, PS4_10, PS4_8, PS4_9 (+3 more)

### Community 239 - "Canal de mise à jour GPSx"
Cohesion: 0.40
Nodes (4): Canal de mise à jour GPSx, Mise à jour dans l’application, Première installation, Règles de sécurité

### Community 240 - "parseLatestRelease"
Cohesion: 0.39
Nodes (3): isAllowedUrl(), parseLatestRelease(), AppUpdaterTest

### Community 243 - "Provenance ledger"
Cohesion: 0.22
Nodes (8): CR3 planned-file entries, Derived-file entries, GPSx-authored platform files, Primary derivative baseline, Provenance ledger, Reference-only projects, Reviewed fixture entries, Rules

### Community 247 - "RetroLibraryTest"
Cohesion: 0.13
Nodes (7): RomFile, RomSource, ShareScanner, RomName, RomNames, FakeShare, RetroLibraryTest

### Community 248 - "RetroSystem"
Cohesion: 0.15
Nodes (10): RetroSystem, ARCADE, GB, GBA, GBC, MEGADRIVE, NEOGEO, NES (+2 more)

### Community 255 - ".derive"
Cohesion: 0.26
Nodes (10): Direction, Local, Remote, gmac_epoch(), invocation_and_failure_limits_are_terminal(), local_and_remote_round_trip_and_reject_forgery(), one_million_real_packets_cross_one_thousand_epochs_without_termination(), packet_ranges_and_source_structure_are_bounded() (+2 more)

### Community 256 - "TransitionBatch"
Cohesion: 0.32
Nodes (3): InputTransition, TransitionBatch, .EMPTY

### Community 262 - "LogLevel"
Cohesion: 0.33
Nodes (6): LogLevel, kLogDebug, kLogError, kLogInfo, kLogWarn, log_core()

## Knowledge Gaps
- **426 isolated node(s):** `kOk`, `kClosed`, `kStaleGeneration`, `kInvalidSurface`, `kCodecError` (+421 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1589 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **64 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Work-memory lessons

**Preferred sources** — corroborated by past sessions; start here.
- `Session cryptography plan` (5× useful, score=4.998094049) _(code changed — re-verify)_
- `Short-tag and counter safety gate` (3× useful, score=2.998527364) _(code changed — re-verify)_
- `Registration codec plan` (3× useful, score=2.898967607)
- `Key-position and outbound-allocation policy` (2× useful, score=1.999552133) _(code changed — re-verify)_
- `CR3 protocol security plan` (2× useful, score=1.999484509) _(code changed — re-verify)_
- `Required API and state invariants` (2× useful, score=1.999484509) _(code changed — re-verify)_

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RemotePlayEngine` connect `RemotePlayEngine` to `CloudPlayController`, `RetroStorage`, `MainActivity`, `RemotePlaySurfaceController`, `ChiakiCompat.kt`, `.wakeAndWait`, `RemotePlayEngine.kt`, `Session`, `Composable`, `Upstream Analysis`, `Analyse de l'amont`, `GpsxApp.kt`, `StreamTestRecorder.kt`, `RegisteredConsoleStore`, `CloudPlayController.kt`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Host` (e.g. with `Android virtual-LAN simulation` and `Simulation Android sur LAN virtuel`) actually correct?**
  _`Host` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `kOk`, `kClosed`, `kStaleGeneration` to the rest of the system?**
  _426 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `redact_text` be split into smaller, more focused modules?**
  _Cohesion score 0.052393857271906055 - nodes in this community are weakly interconnected._
- **Why does `Host` connect `Host` to `.load`, `retro_host.cpp`, `TESTING.fr.md`, `AAudioStream`, `retro_api.h`, `Testing strategy`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `RemotePlayEngine` (e.g. with `GpsxDiscoveryBridge` and `hasHardwareHevc1080p60()`) actually correct?**
  _`RemotePlayEngine` has 4 INFERRED edges - model-reasoned connections that need verification._
- **Should `SessionState` be split into smaller, more focused modules?**
  _Cohesion score 0.06585365853658537 - nodes in this community are weakly interconnected._