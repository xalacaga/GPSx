# Graph Report - GPSx  (2026-10-28)

## Corpus Check
- 120 files · ~79,942 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 7, (none) 5, .properties 2)

## Summary
- 1463 nodes · 2225 edges · 134 communities (103 shown, 31 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 25 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ed2422e2`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- redaction.py
- SessionState
- gpsx-input/src/lib.rs
- Planned cryptography dependency register
- Button
- gpsx-telemetry/src/lib.rs
- collect-gp520-baseline.ps1
- gpsx-input
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
- Analyse de l'amont
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
- AppUiState
- RemoteNavigationTest.kt
- Offline-first development before GP520 access
- Développement offline-first avant accès au GP520
- Android TV interface specification
- Spécification de l’interface Android TV
- Feature parity baseline
- Socle de parité fonctionnelle
- DiagnosticExportError
- Embedded diagnostic lab
- Laboratoire de diagnostic embarqué
- GPSx update channel
- Canal de mise à jour GPSx
- extract_diagnostic_zip
- steps
- CR3 protocol security plan
- Plan de sécurité protocolaire CR3
- Registre prévu des dépendances cryptographiques
- gpsx-input/src/lib.rs
- http.rs
- Provenance ledger
- Registre de provenance
- CR1 remediation record
- Registre de remédiation CR1
- ControllerId
- InputTransitionQueue
- HttpParseError
- ControllerState
- discovery.rs
- Registration codec plan
- DiscoveryParseError
- DiscoveryHost<'a>
- diff
- MetricName
- gpsx-protocol
- BoundedLines<'a>
- encode_diagnostic_jsonl
- DiagnosticRunKind
- EvidenceClass
- DiagnosticEnvironment
- Diagnostic export
- Export des diagnostics
- registration.rs
- Registration cryptography plan
- Plan cryptographique de l’enregistrement
- gpsx-transport/src/lib.rs
- gpsx-discover.rs
- .record
- DiscoveryError
- ConsoleGeneration
- Windows UDP discovery transport
- Transport de découverte UDP Windows
- gpsx-protocol/src/registration.rs
- manifest.json
- registration_codec_oracle.rs
- RegistrationCodecError
- DiscoveryReport
- format_account_id_inner
- gpsx-crypto
- assert_codec_oracle_crypto
- RegistrationCryptoError
- assert_oracle
- RegistrationTarget
- Plan du codec d'enregistrement
- gpsx-protocol/src/lib.rs
- RegistrationProfile
- Q: Close codec review findings A1 and A2 and prepare the authorized external R5 oracle evidence
- Redacted
- RegistrationResponseView<'a>
- RegistrationCryptoTarget
- Composable
- Q: Implement the reviewed bounded registration codec without generating the R5-blocked C oracle or committing stubs
- Q: Apply the GO WITH CHANGES remediation for codec blockers B1 and B2 and add the missing R5-a provenance requirements without generating the oracle
- GpsxApp
- SessionScreen
- Q: Close codec oracle integration findings B1-B4 without committing R5 external artifacts
- SettingsScreen

## God Nodes (most connected - your core abstractions)
1. `RegistrationCodecError` - 66 edges
2. `MetricName` - 24 edges
3. `Button` - 22 edges
4. `HttpParseError` - 21 edges
5. `RegistrationProfile` - 20 edges
6. `parse_registration_response_fields()` - 20 edges
7. `extract_diagnostic_zip()` - 20 edges
8. `DiagnosticExportError` - 18 edges
9. `discover()` - 18 edges
10. `redact_text()` - 18 edges

## Surprising Connections (you probably didn't know these)
- `discover()` --calls--> `format_search_request()`  [INFERRED]
  crates/gpsx-transport/src/lib.rs → crates/gpsx-protocol/src/discovery.rs
- `parse_search_response()` --calls--> `parse_http_response()`  [INFERRED]
  crates/gpsx-protocol/src/discovery.rs → crates/gpsx-protocol/src/http.rs
- `discover()` --calls--> `parse_search_response()`  [INFERRED]
  crates/gpsx-transport/src/lib.rs → crates/gpsx-protocol/src/discovery.rs
- `run_live()` --calls--> `discover_broadcast()`  [INFERRED]
  crates/gpsx-transport/src/bin/gpsx-discover.rs → crates/gpsx-transport/src/lib.rs
- `NavigationRail()` --references--> `Destination`  [EXTRACTED]
  apps/android-tv/app/src/main/java/com/gpsx/tv/ui/GpsxApp.kt → apps/android-tv/app/src/main/java/com/gpsx/tv/ui/AppState.kt

## Import Cycles
- 1-file cycle: `crates/gpsx-input/src/lib.rs -> crates/gpsx-input/src/lib.rs`
- 1-file cycle: `crates/gpsx-model/src/lib.rs -> crates/gpsx-model/src/lib.rs`
- 1-file cycle: `crates/gpsx-transport/src/lib.rs -> crates/gpsx-transport/src/lib.rs`
- 1-file cycle: `crates/gpsx-transport/src/bin/gpsx-discover.rs -> crates/gpsx-transport/src/bin/gpsx-discover.rs`
- 1-file cycle: `crates/gpsx-telemetry/src/lib.rs -> crates/gpsx-telemetry/src/lib.rs`

## Communities (134 total, 31 thin omitted)

### Community 0 - "redaction.py"
Cohesion: 0.06
Nodes (23): _load_allowed_keys(), main(), _redact_jsonl(), scan_for_secrets(), build_manifest(), fixture_paths(), _is_fixture(), _load_provenance() (+15 more)

### Community 1 - "SessionState"
Cohesion: 0.07
Nodes (22): accepts_nominal_stream_lifecycle(), ALL, ALLOWED, InvalidTransition, rejects_streaming_without_connection(), SessionState, Connecting, Discovering (+14 more)

### Community 2 - "gpsx-input/src/lib.rs"
Cohesion: 0.25
Nodes (7): kinds(), TransitionKind, Button, Trigger, Trigger, L2, R2

### Community 3 - "Planned cryptography dependency register"
Cohesion: 0.50
Nodes (3): Cryptography dependency register, Registration-only resolved graph — 2026-10-26, Security and acceptance gate

### Community 4 - "Button"
Cohesion: 0.11
Nodes (18): Button, .ALL, Circle, Create, Cross, DpadDown, DpadLeft, DpadRight (+10 more)

### Community 5 - "gpsx-telemetry/src/lib.rs"
Cohesion: 0.09
Nodes (18): EXACT_TOKENS, LatencySummary, MAX_DIAGNOSTIC_JSONL_BYTES, MAX_DIAGNOSTIC_SAMPLES, MAX_METRIC_LINE_BYTES, MAX_RUN_LINE_BYTES, MAX_SCIENTIFIC_F64_BYTES, MAX_U64_BYTES (+10 more)

### Community 10 - "Assert-GpsxProvenance"
Cohesion: 0.23
Nodes (4): Assert-GpsxProvenance(), Get-GpsxRelativePath(), Get-GpsxRepositoryFiles(), Test-GpsxGeneratedPath()

### Community 12 - "Architecture Audit"
Cohesion: 0.15
Nodes (12): 1. Protocol provenance and licensing, 2. Unknown PS5 protocol behavior, 3. Android decoder lifecycle and latency, 4. DualSense semantics through Android, 5. Bluetooth coexistence and audio latency, Architecture Audit, Current architecture, Proposed Rust architecture (+4 more)

### Community 13 - "Audit d'architecture"
Cohesion: 0.15
Nodes (12): 1. Provenance du protocole et licences, 2. Comportement inconnu du protocole PS5, 3. Cycle de vie et latence du décodeur Android, 4. Sémantique DualSense à travers Android, 5. Coexistence Bluetooth et latence audio, Architecture actuelle, Architecture de référence, Architecture Rust proposée (+4 more)

### Community 14 - "License Audit"
Cohesion: 0.15
Nodes (12): Choosing between options, Compatible third-party candidates (not yet adopted), Dependency considerations, License Audit, Non-copyright considerations, Option A — reuse or wrap the Chiaki family, Option B — Rust reimplementation, Option C — GPSx licensed AGPL-3.0, Rust port declared as derivative (+4 more)

### Community 15 - "Audit des licences"
Cohesion: 0.15
Nodes (12): Audit des licences, Candidats tiers compatibles (pas encore adoptés), Choisir entre les options, Considérations hors droit d'auteur, Décision de Phase 0, Dépendances, Option A — réutiliser ou envelopper la famille Chiaki, Option B — réimplémentation en Rust (+4 more)

### Community 16 - "Recommended first milestone — M0.5 Evidence Baseline and License Gate"
Cohesion: 0.15
Nodes (12): Acceptance criteria, Files/modules affected, Goal, Independent cross-review gates, Later milestones, M1 entry criteria, Non-goals, Recommended first milestone — M0.5 Evidence Baseline and License Gate (+4 more)

### Community 17 - "DualSense Analysis"
Cohesion: 0.17
Nodes (11): DualSense Analysis, Edge preservation, Evidence, Latency and reconnect, Mapping profiles, Proposed normalized model, Required inspector, Stream input ownership and exit (+3 more)

### Community 18 - "Upstream Analysis"
Cohesion: 0.18
Nodes (10): Android forks, Chiaki, chiaki-ng, Evidence limit, Media and input mapping, Protocol architecture mapping, Pylux, Replace or redesign (+2 more)

### Community 19 - "Analyse de l'amont"
Cohesion: 0.18
Nodes (10): Analyse de l'amont, Chiaki, chiaki-ng, Correspondance de l'architecture du protocole, Correspondance des médias et des entrées, Forks Android, Limite des preuves, Pylux (+2 more)

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
Cohesion: 0.40
Nodes (4): Automated gates, Failure and security testing, Hardware gates, Testing strategy

### Community 27 - "Performance baseline"
Cohesion: 0.50
Nodes (3): Performance baseline, Required metrics, Required run metadata

### Community 28 - "Protocol evidence register"
Cohesion: 0.33
Nodes (5): Evidence rules, Local-registration codec, Protocol evidence register, State evidence to capture, UDP discovery adapter

### Community 35 - "fixtures/README.md"
Cohesion: 0.50
Nodes (3): Registration codec oracle v1, Registration cryptography oracle v2, Reviewed fixtures

### Community 36 - "Premier jalon recommandé — M0.5 Référence factuelle et verrou de licence"
Cohesion: 0.20
Nodes (9): Entrée en M1, Feuille de route, Gates de contre-revue indépendante, Hors périmètre, Jalons suivants, Objectif, Premier jalon recommandé — M0.5 Référence factuelle et verrou de licence, Périmètre (+1 more)

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

### Community 51 - "fixtures/README.fr.md"
Cohesion: 0.50
Nodes (3): Fixtures relues, Oracle cryptographique d’enregistrement v2, Oracle du codec d’enregistrement v1

### Community 53 - "AppUiState"
Cohesion: 0.08
Nodes (31): AppUiState, ConfirmDemoStarted, CycleQuality, Destination, ABOUT, CONSOLES, CONTROLLERS, DIAGNOSTICS (+23 more)

### Community 55 - "Offline-first development before GP520 access"
Cohesion: 0.33
Nodes (5): Allowed before the hardware gate, Architecture consequence, Evidence boundaries, Offline-first development before GP520 access, Physical-hardware gate

### Community 56 - "Développement offline-first avant accès au GP520"
Cohesion: 0.33
Nodes (5): Autorisé avant le gate matériel, Conséquence architecturale, Développement offline-first avant accès au GP520, Frontière des preuves, Gate matériel physique

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

### Community 65 - "Embedded diagnostic lab"
Cohesion: 0.29
Nodes (6): Captured signals, Comparison rules, Embedded diagnostic lab, Export bundle, Guided scenarios, Optional private GitHub path

### Community 66 - "Laboratoire de diagnostic embarqué"
Cohesion: 0.29
Nodes (6): Chemin GitHub privé facultatif, Laboratoire de diagnostic embarqué, Paquet d’export, Règles de comparaison, Scénarios guidés, Signaux capturés

### Community 67 - "GPSx update channel"
Cohesion: 0.40
Nodes (4): Bootstrap installation, GPSx update channel, In-application update, Security rules

### Community 68 - "Canal de mise à jour GPSx"
Cohesion: 0.40
Nodes (4): Canal de mise à jour GPSx, Mise à jour dans l’application, Première installation, Règles de sécurité

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
Cohesion: 0.50
Nodes (3): Gate de sécurité et d’acceptation, Graphe résolu limité à l’enregistrement — 2026-10-26, Registre des dépendances cryptographiques

### Community 75 - "gpsx-input/src/lib.rs"
Cohesion: 0.18
Nodes (8): internal_button_masks_are_distinct_low_16_bits(), MAX_LOCAL_CONTROLLERS, MAX_TRANSITIONS_PER_CHANGE, merged_buttons_release_only_after_every_controller_releases(), neutral_state_releases_everything_held(), overflow_rejects_whole_batch_and_is_counted(), queue(), transitions_keep_their_controller()

### Community 76 - "http.rs"
Cohesion: 0.11
Nodes (15): accepts_all_line_endings_and_final_line_without_eol(), accepts_utf8_values_and_rejects_invalid_or_control_values(), applies_strict_header_grammar(), BoundedLines, enforces_datagram_boundaries(), Header, HttpResponse, is_header_name_byte() (+7 more)

### Community 77 - "Provenance ledger"
Cohesion: 0.25
Nodes (7): CR3 planned-file entries, Derived-file entries, Primary derivative baseline, Provenance ledger, Reference-only projects, Reviewed fixture entries, Rules

### Community 78 - "Registre de provenance"
Cohesion: 0.25
Nodes (7): Base dérivée principale, Entrées de fichiers dérivés, Entrées de fichiers prévues pour CR3, Entrées de fixtures relues, Projets de référence uniquement, Registre de provenance, Règles

### Community 79 - "CR1 remediation record"
Cohesion: 0.40
Nodes (4): Closed in this remediation, CR1 remediation record, Gates still closed, Verification commands

### Community 80 - "Registre de remédiation CR1"
Cohesion: 0.40
Nodes (4): Commandes de vérification, Fermé par cette remédiation, Gates encore fermées, Registre de remédiation CR1

### Community 81 - "ControllerId"
Cohesion: 0.24
Nodes (11): ControllerId, ControllerSet, ControllerUpdateError, Capacity, ReservedId, keeps_controllers_separate(), MergedControllerUpdate, PAD (+3 more)

### Community 83 - "HttpParseError"
Cohesion: 0.12
Nodes (14): HttpParseError, ControlCharacter, DatagramTooLarge, DuplicateSensitiveHeader, EmbeddedNul, EmptyHeaderValue, HeaderAfterTerminator, InvalidHeaderName (+6 more)

### Community 84 - "ControllerState"
Cohesion: 0.21
Nodes (10): Axis, LeftX, LeftY, RightX, RightY, ControllerState, merged_analog_policy_is_max_trigger_and_greatest_axis_magnitude(), preserves_wire_exact_stick_extremes() (+2 more)

### Community 85 - "discovery.rs"
Cohesion: 0.17
Nodes (12): debug_and_display_redact_host_identity(), first_non_sensitive_duplicate_wins(), maps_status_codes(), parse_errors_do_not_echo_identifiers(), parse_search_response(), parses_known_headers_and_ignores_unknown_ones(), PS4_DISCOVERY_PORT, PS4_PROTOCOL_VERSION (+4 more)

### Community 86 - "Registration codec plan"
Cohesion: 0.17
Nodes (11): Decrypted response fields (C3), Deterministic evidence plan (C7), Evidence and provenance (C5), Failure contract and deferred UDP search, Layer boundary and allocation policy, Ownership and secret handling (C6), Profiles and fixed request constants (C2), Registration codec plan (+3 more)

### Community 87 - "DiscoveryParseError"
Cohesion: 0.27
Nodes (6): DiscoveryHost, DiscoveryParseError, Http, InvalidHostRequestPort, parse_port(), set_first_known_header()

### Community 89 - "diff"
Cohesion: 0.22
Nodes (7): diff(), full_trigger_sweep_never_overflows_and_keeps_both_crossings(), InputTransition, press_and_release_in_one_interval_both_reach_the_queue(), QueueOverflow, TransitionBatch, .EMPTY

### Community 90 - "MetricName"
Cohesion: 0.09
Nodes (21): MetricName, AudioBufferDepth, AudioDrift, AudioRouteChanges, AudioUnderruns, InputEventInterval, InputToResponse, NetworkBitrate (+13 more)

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

### Community 99 - "registration.rs"
Cohesion: 0.06
Nodes (18): AMBASSADOR, decode(), decode_table(), ECHO_B, hex_nibble(), HMAC_KEY_PS4_MODERN, HMAC_KEY_PS4_PRE10, HMAC_KEY_PS5 (+10 more)

### Community 100 - "Registration cryptography plan"
Cohesion: 0.25
Nodes (7): Adopted dependency subset, API and secret invariants, Evidence and scope, Implementation files, Later gates, Registration cryptography plan, Tests required before review

### Community 101 - "Plan cryptographique de l’enregistrement"
Cohesion: 0.25
Nodes (7): API et invariants des secrets, Fichiers d’implémentation, Plan cryptographique de l’enregistrement, Portes ultérieures, Preuves et périmètre, Sous-ensemble de dépendances adopté, Tests exigés avant revue

### Community 102 - "gpsx-transport/src/lib.rs"
Cohesion: 0.13
Nodes (14): closed_loopback_port_returns_report_and_counts_windows_icmp_reset(), DEFAULT_RESPONSE_WINDOW, discover(), duplicate_responses_are_coalesced_by_generation_and_source_ip(), loopback_discovery_sends_exact_request_and_collects_response(), malformed_response_is_counted_without_stopping_collection(), MAX_DISCOVERED_CONSOLES, MAX_RESPONSE_WINDOW (+6 more)

### Community 103 - "gpsx-discover.rs"
Cohesion: 0.16
Nodes (10): arguments(), live_network_mode_requires_one_exact_argument(), main(), Mode, Help, Live, parse_mode(), print_help() (+2 more)

### Community 104 - ".record"
Cohesion: 0.14
Nodes (11): DiscoveryState, Ready, Standby, Unknown, state_label(), capacity_keeps_first_sixteen_unique_addresses_and_counts_drops(), connection_refused_is_non_fatal_for_portability(), debug_output_redacts_peer_address() (+3 more)

### Community 105 - "DiscoveryError"
Cohesion: 0.11
Nodes (16): error_label(), DiscoveryError, InvalidResponseWindow, Io, DiscoveryOperation, Bind, ConfigureBroadcast, ConfigureTimeout (+8 more)

### Community 106 - "ConsoleGeneration"
Cohesion: 0.33
Nodes (7): ConsoleGeneration, Ps4, Ps5, format_search_request(), generation_label(), discover_broadcast(), discovery_port()

### Community 107 - "Windows UDP discovery transport"
Cohesion: 0.33
Nodes (5): Bounds and failure behavior, Evidence boundary, Local validation, Scope, Windows UDP discovery transport

### Community 108 - "Transport de découverte UDP Windows"
Cohesion: 0.33
Nodes (5): Bornes et comportement en cas d’échec, Frontière des preuves, Périmètre, Transport de découverte UDP Windows, Validation locale

### Community 109 - "gpsx-protocol/src/registration.rs"
Cohesion: 0.07
Nodes (42): ACCOUNT, ACCOUNT_ID_BASE64_SIZE, ACCOUNT_ID_SIZE, AEROPAUSE_SIZE, arbitrary_inputs_are_deterministic_and_never_panic(), content_length_grammar_and_exact_body_are_enforced(), decrypted_field_line_grammar_is_exact(), decrypted_fields_are_strict_borrowed_and_complete() (+34 more)

### Community 111 - "registration_codec_oracle.rs"
Cohesion: 0.24
Nodes (10): Case, exact(), FIXTURE, Inputs, number_value(), offset_value(), Oracle, parse() (+2 more)

### Community 112 - "RegistrationCodecError"
Cohesion: 0.05
Nodes (36): error_of(), RegistrationCodecError, ArithmeticOverflow, BodyLengthMismatch, BodyTooLarge, ControlCharacter, CryptoTargetMismatch, DuplicateContentLength (+28 more)

### Community 114 - "format_account_id_inner"
Cohesion: 0.16
Nodes (17): AccountId, AccountId<'a>, assert_codec_oracle(), checked_sum(), codec_oracle_binds_all_nine_complete_cases(), format_account_id_inner(), format_online_id_inner(), format_ps4_9_online_id_compatibility() (+9 more)

### Community 116 - "assert_codec_oracle_crypto"
Cohesion: 0.28
Nodes (8): assert_codec_oracle_crypto(), codec_oracle_crypto_fields_are_bound_for_all_nine_cases(), invalid_inputs_fail_with_static_redacted_errors(), modern(), pin(), RegistrationAmbassador, RegistrationPin, upstream_registration_vectors_are_reproduced()

### Community 117 - "RegistrationCryptoError"
Cohesion: 0.19
Nodes (8): payload_lengths_round_trip_with_one_shot_states(), RegistrationContext, RegistrationCryptoError, CryptoInitialization, InvalidPin, InvalidTableOffset, InvalidTarget, RegistrationResponseDecryptor

### Community 118 - "assert_oracle"
Cohesion: 0.24
Nodes (5): AeropauseTableOffset, assert_oracle(), CipherTableOffset, oracle_v2_has_independent_offsets_for_every_modern_target(), RegistrationMaterial

### Community 119 - "RegistrationTarget"
Cohesion: 0.33
Nodes (7): fixture_case_line(), fixture_has_case(), OracleCase, RegistrationTarget, Ps4Modern, Ps4Pre10, Ps5

### Community 120 - "Plan du codec d'enregistrement"
Cohesion: 0.17
Nodes (11): API de requête et invariants de disposition (C1), Cadrage et limites de l'enregistrement (C4), Champs de réponse déchiffrés (C3), Contrat d'échec et recherche UDP reportée, Frontière entre couches et politique d'allocation, Gate de revue, Plan de preuves déterministes (C7), Plan du codec d'enregistrement (+3 more)

### Community 121 - "gpsx-protocol/src/lib.rs"
Cohesion: 0.15
Nodes (15): encrypted_length_mismatch_fails_before_finalization(), ensure_output(), finish_registration_request_payload(), MAX_REGISTRATION_BODY_SIZE, MAX_REGISTRATION_FIELD_COUNT, MAX_REGISTRATION_FIELD_LINE_SIZE, MAX_REGISTRATION_FIELD_NAME_SIZE, MAX_REGISTRATION_REQUEST_HEADER_SIZE (+7 more)

### Community 122 - "RegistrationProfile"
Cohesion: 0.18
Nodes (11): ensure_ps4(), ensure_ps5(), format_decimal(), format_registration_http_request_header(), outer_request_headers_are_exact_for_every_profile(), RegistrationPayload, RegistrationProfile, Ps4_10Target (+3 more)

### Community 123 - "Q: Close codec review findings A1 and A2 and prepare the authorized external R5 oracle evidence"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Close codec review findings A1 and A2 and prepare the authorized external R5 oracle evidence, Source Nodes

### Community 126 - "RegistrationCryptoTarget"
Cohesion: 0.40
Nodes (5): RegistrationCryptoTarget, Ps4Modern, Ps4Pre10, Ps5, validate_registration_crypto_target()

### Community 127 - "Composable"
Cohesion: 0.26
Nodes (16): AboutScreen(), CapabilityGrid(), ConsoleCard(), ConsolesScreen(), ControllerSlot(), ControllersScreen(), DiagnosticsScreen(), EvidenceBanner() (+8 more)

### Community 128 - "Q: Implement the reviewed bounded registration codec without generating the R5-blocked C oracle or committing stubs"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Implement the reviewed bounded registration codec without generating the R5-blocked C oracle or committing stubs, Source Nodes

### Community 129 - "Q: Apply the GO WITH CHANGES remediation for codec blockers B1 and B2 and add the missing R5-a provenance requirements without generating the oracle"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Apply the GO WITH CHANGES remediation for codec blockers B1 and B2 and add the missing R5-a provenance requirements without generating the oracle, Source Nodes

### Community 130 - "GpsxApp"
Cohesion: 0.24
Nodes (3): MainActivity, GpsxApp(), GpsxTheme()

### Community 131 - "SessionScreen"
Cohesion: 0.29
Nodes (8): FeatureCard(), FocusButton(), HeroPanel(), HomeScreen(), MetricCard(), OverlayMetric(), sessionLabel(), SessionScreen()

### Community 132 - "Q: Close codec oracle integration findings B1-B4 without committing R5 external artifacts"
Cohesion: 0.50
Nodes (3): Answer, Outcome, Q: Close codec oracle integration findings B1-B4 without committing R5 external artifacts

### Community 133 - "SettingsScreen"
Cohesion: 0.67
Nodes (3): SettingRow(), SettingsScreen(), yesNo()

## Knowledge Gaps
- **188 isolated node(s):** `HOME`, `CONSOLES`, `SESSION`, `CONTROLLERS`, `DIAGNOSTICS` (+183 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 856 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **31 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Work-memory lessons

**Preferred sources** — corroborated by past sessions; start here.
- `Registration codec plan` (3× useful, score=2.902582894)

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RegistrationCodecError` connect `RegistrationCodecError` to `gpsx-protocol/src/registration.rs`, `format_account_id_inner`, `gpsx-protocol/src/lib.rs`, `RegistrationProfile`, `RegistrationCryptoTarget`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **What connects `HOME`, `CONSOLES`, `SESSION` to the rest of the system?**
  _188 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `redaction.py` be split into smaller, more focused modules?**
  _Cohesion score 0.058018018018018015 - nodes in this community are weakly interconnected._
- **Why does `DiscoveryState` connect `.record` to `gpsx-transport/src/lib.rs`, `gpsx-discover.rs`, `discovery.rs`, `DiscoveryParseError`, `DiscoveryHost<'a>`, `gpsx-protocol/src/lib.rs`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **Should `SessionState` be split into smaller, more focused modules?**
  _Cohesion score 0.06585365853658537 - nodes in this community are weakly interconnected._
- **Why does `MetricName` connect `MetricName` to `encode_diagnostic_jsonl`, `gpsx-telemetry/src/lib.rs`?**
  _High betweenness centrality (0.014) - this node is a cross-community bridge._
- **Should `Button` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._