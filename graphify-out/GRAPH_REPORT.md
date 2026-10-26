# Graph Report - GPSx  (2026-10-26)

## Corpus Check
- 102 files · ~55,989 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: .xml 7, (none) 4, .properties 2)

## Summary
- 1134 nodes · 1642 edges · 107 communities (77 shown, 30 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 25 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `5c34a20d`
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
- GpsxApp.kt
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
- gpsx-protocol/src/lib.rs
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
- DiscoveryReport
- gpsx-transport/src/lib.rs
- gpsx-discover.rs
- DiscoveryReport
- DiscoveryError
- ConsoleGeneration
- Windows UDP discovery transport
- Transport de découverte UDP Windows

## God Nodes (most connected - your core abstractions)
1. `MetricName` - 24 edges
2. `Button` - 22 edges
3. `HttpParseError` - 21 edges
4. `extract_diagnostic_zip()` - 20 edges
5. `DiagnosticExportError` - 18 edges
6. `discover()` - 18 edges
7. `redact_text()` - 18 edges
8. `SessionState` - 17 edges
9. `DiscoveryReport` - 17 edges
10. `AppUiState` - 16 edges

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

## Communities (107 total, 30 thin omitted)

### Community 0 - "redaction.py"
Cohesion: 0.06
Nodes (23): _load_allowed_keys(), main(), _redact_jsonl(), scan_for_secrets(), build_manifest(), fixture_paths(), _is_fixture(), _load_provenance() (+15 more)

### Community 1 - "SessionState"
Cohesion: 0.07
Nodes (22): accepts_nominal_stream_lifecycle(), ALL, ALLOWED, InvalidTransition, rejects_streaming_without_connection(), SessionState, Connecting, Discovering (+14 more)

### Community 2 - "gpsx-input/src/lib.rs"
Cohesion: 0.25
Nodes (7): kinds(), TransitionKind, Button, Trigger, Trigger, L2, R2

### Community 4 - "Button"
Cohesion: 0.11
Nodes (18): Button, .ALL, Circle, Create, Cross, DpadDown, DpadLeft, DpadRight (+10 more)

### Community 5 - "gpsx-telemetry/src/lib.rs"
Cohesion: 0.08
Nodes (20): EXACT_TOKENS, LatencySummary, MAX_DIAGNOSTIC_JSONL_BYTES, MAX_DIAGNOSTIC_SAMPLES, MAX_METRIC_LINE_BYTES, MAX_RUN_LINE_BYTES, MAX_SCIENTIFIC_F64_BYTES, MAX_U64_BYTES (+12 more)

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
Cohesion: 0.40
Nodes (4): Evidence rules, Protocol evidence register, State evidence to capture, UDP discovery adapter

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

### Community 52 - "GpsxApp.kt"
Cohesion: 0.05
Nodes (28): AboutScreen(), CapabilityGrid(), ConsoleCard(), ConsolesScreen(), ControllerSlot(), ControllersScreen(), DiagnosticsScreen(), EvidenceBanner() (+20 more)

### Community 53 - "AppUiState"
Cohesion: 0.08
Nodes (31): AppUiState, ConfirmDemoStarted, CycleQuality, Destination, ABOUT, CONSOLES, CONTROLLERS, DIAGNOSTICS (+23 more)

### Community 54 - "RemoteNavigationTest.kt"
Cohesion: 0.06
Nodes (4): LargeTextLegalTest, RemoteNavigationTest, MainActivity, GpsxTheme()

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
Cohesion: 0.12
Nodes (6): extract_diagnostic_zip(), _extract_to_staging(), _safe_relative_path(), UnsafeArchiveError, _validate_type(), SafeArchiveTests

### Community 71 - "steps"
Cohesion: 0.09
Nodes (23): runs-on, steps, jobs, core-and-android, wrapper-validation, name, permissions, contents (+15 more)

### Community 72 - "CR3 protocol security plan"
Cohesion: 0.29
Nodes (6): Authorized SRCH behavior, CR3 protocol security plan, Cryptography decision for later slices, Fuzzing plan, Required CR3 evidence, Trust boundary and parser limits

### Community 73 - "Plan de sécurité protocolaire CR3"
Cohesion: 0.29
Nodes (6): Comportement SRCH autorisé, Décision cryptographique pour les lots ultérieurs, Frontière de confiance et limites des parseurs, Plan de fuzzing, Plan de sécurité protocolaire CR3, Preuves exigées pour CR3

### Community 75 - "gpsx-input/src/lib.rs"
Cohesion: 0.18
Nodes (8): internal_button_masks_are_distinct_low_16_bits(), MAX_LOCAL_CONTROLLERS, MAX_TRANSITIONS_PER_CHANGE, merged_buttons_release_only_after_every_controller_releases(), neutral_state_releases_everything_held(), overflow_rejects_whole_batch_and_is_counted(), queue(), transitions_keep_their_controller()

### Community 76 - "http.rs"
Cohesion: 0.13
Nodes (12): accepts_all_line_endings_and_final_line_without_eol(), accepts_utf8_values_and_rejects_invalid_or_control_values(), applies_strict_header_grammar(), BoundedLines, enforces_datagram_boundaries(), Header, HttpResponse, is_header_name_byte() (+4 more)

### Community 77 - "Provenance ledger"
Cohesion: 0.29
Nodes (6): CR3 planned-file entries (not yet implemented), Derived-file entries, Primary derivative baseline, Provenance ledger, Reference-only projects, Rules

### Community 78 - "Registre de provenance"
Cohesion: 0.29
Nodes (6): Base dérivée principale, Entrées de fichiers dérivés, Entrées de fichiers prévues pour CR3 (non implémentées), Projets de référence uniquement, Registre de provenance, Règles

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
Cohesion: 0.24
Nodes (8): debug_and_display_redact_host_identity(), first_non_sensitive_duplicate_wins(), maps_status_codes(), parse_errors_do_not_echo_identifiers(), parse_search_response(), parses_known_headers_and_ignores_unknown_ones(), PS4_SEARCH, PS5_SEARCH

### Community 86 - "gpsx-protocol/src/lib.rs"
Cohesion: 0.25
Nodes (7): PS4_DISCOVERY_PORT, PS4_PROTOCOL_VERSION, PS5_DISCOVERY_PORT, PS5_PROTOCOL_VERSION, MAX_DATAGRAM_SIZE, MAX_HEADER_COUNT, MAX_LINE_SIZE

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

### Community 102 - "gpsx-transport/src/lib.rs"
Cohesion: 0.13
Nodes (14): closed_loopback_port_returns_report_and_counts_windows_icmp_reset(), DEFAULT_RESPONSE_WINDOW, discover(), duplicate_responses_are_coalesced_by_generation_and_source_ip(), loopback_discovery_sends_exact_request_and_collects_response(), malformed_response_is_counted_without_stopping_collection(), MAX_DISCOVERED_CONSOLES, MAX_RESPONSE_WINDOW (+6 more)

### Community 103 - "gpsx-discover.rs"
Cohesion: 0.16
Nodes (10): arguments(), live_network_mode_requires_one_exact_argument(), main(), Mode, Help, Live, parse_mode(), print_help() (+2 more)

### Community 104 - "DiscoveryReport"
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

## Knowledge Gaps
- **139 isolated node(s):** `HOME`, `CONSOLES`, `SESSION`, `CONTROLLERS`, `DIAGNOSTICS` (+134 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 678 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HttpParseError` connect `HttpParseError` to `http.rs`, `discovery.rs`, `gpsx-protocol/src/lib.rs`, `DiscoveryParseError`?**
  _High betweenness centrality (0.010) - this node is a cross-community bridge._
- **What connects `HOME`, `CONSOLES`, `SESSION` to the rest of the system?**
  _139 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `redaction.py` be split into smaller, more focused modules?**
  _Cohesion score 0.05886708626434654 - nodes in this community are weakly interconnected._
- **Why does `ConsoleGeneration` connect `ConsoleGeneration` to `gpsx-transport/src/lib.rs`, `gpsx-discover.rs`, `DiscoveryReport`, `discovery.rs`, `gpsx-protocol/src/lib.rs`?**
  _High betweenness centrality (0.007) - this node is a cross-community bridge._
- **Should `SessionState` be split into smaller, more focused modules?**
  _Cohesion score 0.06585365853658537 - nodes in this community are weakly interconnected._
- **Should `Button` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._
- **Should `gpsx-telemetry/src/lib.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.07862903225806452 - nodes in this community are weakly interconnected._