# Graph Report - GPSx  (2026-10-25)

## Corpus Check
- 71 files · ~32,246 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 13 file(s) not represented in the graph (top: .xml 7, (none) 2, .pro 1)

## Summary
- 644 nodes · 873 edges · 65 communities (40 shown, 25 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 12 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- redaction.py
- SessionState
- gpsx-input/src/lib.rs
- cli.py
- Button
- gpsx-telemetry/src/lib.rs
- collect-gp520-baseline.ps1
- gpsx-input
- gpsx-model
- gpsx-telemetry
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
- MainActivity.kt
- Offline-first development before GP520 access
- Développement offline-first avant accès au GP520
- Android TV interface specification
- Spécification de l’interface Android TV
- Feature parity baseline
- Socle de parité fonctionnelle
- .fmt

## God Nodes (most connected - your core abstractions)
1. `Button` - 22 edges
2. `redact_text()` - 18 edges
3. `SessionState` - 17 edges
4. `redact_json_value()` - 16 edges
5. `AppUiState` - 14 edges
6. `ControllerState` - 14 edges
7. `diff()` - 12 edges
8. `verify_manifest()` - 12 edges
9. `Audit des licences` - 12 edges
10. `License Audit` - 12 edges

## Surprising Connections (you probably didn't know these)
- `NavigationRail()` --references--> `Destination`  [EXTRACTED]
  apps/android-tv/app/src/main/java/com/gpsx/tv/ui/GpsxApp.kt → apps/android-tv/app/src/main/java/com/gpsx/tv/ui/AppState.kt
- `NavItem()` --references--> `Destination`  [EXTRACTED]
  apps/android-tv/app/src/main/java/com/gpsx/tv/ui/GpsxApp.kt → apps/android-tv/app/src/main/java/com/gpsx/tv/ui/AppState.kt
- `HeroPanel()` --references--> `AppUiState`  [EXTRACTED]
  apps/android-tv/app/src/main/java/com/gpsx/tv/ui/GpsxApp.kt → apps/android-tv/app/src/main/java/com/gpsx/tv/ui/AppState.kt
- `HomeScreen()` --references--> `AppUiState`  [EXTRACTED]
  apps/android-tv/app/src/main/java/com/gpsx/tv/ui/GpsxApp.kt → apps/android-tv/app/src/main/java/com/gpsx/tv/ui/AppState.kt
- `SessionScreen()` --references--> `AppUiState`  [EXTRACTED]
  apps/android-tv/app/src/main/java/com/gpsx/tv/ui/GpsxApp.kt → apps/android-tv/app/src/main/java/com/gpsx/tv/ui/AppState.kt

## Import Cycles
- 1-file cycle: `crates/gpsx-input/src/lib.rs -> crates/gpsx-input/src/lib.rs`
- 1-file cycle: `crates/gpsx-model/src/lib.rs -> crates/gpsx-model/src/lib.rs`
- 1-file cycle: `crates/gpsx-telemetry/src/lib.rs -> crates/gpsx-telemetry/src/lib.rs`

## Communities (65 total, 25 thin omitted)

### Community 0 - "redaction.py"
Cohesion: 0.09
Nodes (11): is_sensitive_key(), key_tokens(), _redact_assignments(), _redact_auth_header(), redact_json_value(), _redact_query(), redact_text(), _redact_url() (+3 more)

### Community 1 - "SessionState"
Cohesion: 0.07
Nodes (22): accepts_nominal_stream_lifecycle(), ALL, ALLOWED, InvalidTransition, rejects_streaming_without_connection(), SessionState, Connecting, Discovering (+14 more)

### Community 2 - "gpsx-input/src/lib.rs"
Cohesion: 0.07
Nodes (34): Axis, LeftX, LeftY, RightX, RightY, ControllerId, ControllerSet, ControllerState (+26 more)

### Community 3 - "cli.py"
Cohesion: 0.13
Nodes (12): _load_allowed_keys(), main(), _redact_jsonl(), scan_for_secrets(), build_manifest(), fixture_paths(), _is_fixture(), _load_provenance() (+4 more)

### Community 4 - "Button"
Cohesion: 0.11
Nodes (18): Button, .ALL, Circle, Create, Cross, DpadDown, DpadLeft, DpadRight (+10 more)

### Community 5 - "gpsx-telemetry/src/lib.rs"
Cohesion: 0.09
Nodes (23): EvidenceClass, Fact, Hypothesis, Inference, NotTested, Unknown, EXACT_TOKENS, is_sensitive_key() (+15 more)

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
Cohesion: 0.18
Nodes (10): DualSense Analysis, Edge preservation, Evidence, Latency and reconnect, Mapping profiles, Proposed normalized model, Required inspector, Stream input ownership and exit (+2 more)

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
Cohesion: 0.50
Nodes (3): Evidence rules, Protocol evidence register, State evidence to capture

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
Cohesion: 0.33
Nodes (5): Analyse DualSense, Inspecteur et validation, Modèle normalisé, Preuves et hypothèses, Profils, transitions et sortie

### Community 43 - "Contre-revue indépendante de la phase 0"
Cohesion: 0.33
Nodes (5): Constats intégrés, Contrainte propriétaire ajoutée après la revue, Contre-revue indépendante de la phase 0, Décision, Ouvert avant M1 au moment de la revue

### Community 44 - "GPSx"
Cohesion: 0.40
Nodes (4): GPSx, Prérequis, Validation, État actuel

### Community 52 - "GpsxApp.kt"
Cohesion: 0.06
Nodes (24): CapabilityGrid(), ConsoleCard(), ConsolesScreen(), ControllersScreen(), DiagnosticsScreen(), EvidenceBanner(), FeatureCard(), FocusButton() (+16 more)

### Community 53 - "AppUiState"
Cohesion: 0.07
Nodes (31): AppUiState, ConfirmDemoStarted, CycleQuality, Destination, CONSOLES, CONTROLLERS, DIAGNOSTICS, HOME (+23 more)

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

## Knowledge Gaps
- **61 isolated node(s):** `HOME`, `CONSOLES`, `SESSION`, `CONTROLLERS`, `DIAGNOSTICS` (+56 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 418 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppUiState` connect `AppUiState` to `GpsxApp.kt`?**
  _High betweenness centrality (0.011) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `redact_text()` (e.g. with `_redact_auth_header()` and `_redact_url()`) actually correct?**
  _`redact_text()` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `HOME`, `CONSOLES`, `SESSION` to the rest of the system?**
  _61 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `redaction.py` be split into smaller, more focused modules?**
  _Cohesion score 0.08846153846153847 - nodes in this community are weakly interconnected._
- **Why does `Button` connect `Button` to `gpsx-input/src/lib.rs`?**
  _High betweenness centrality (0.006) - this node is a cross-community bridge._
- **Should `SessionState` be split into smaller, more focused modules?**
  _Cohesion score 0.06585365853658537 - nodes in this community are weakly interconnected._
- **Why does `Destination` connect `AppUiState` to `GpsxApp.kt`?**
  _High betweenness centrality (0.005) - this node is a cross-community bridge._