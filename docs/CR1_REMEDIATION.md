# CR1 remediation record

Date: 2026-10-25

This record maps the independent Claude Code CR1 findings to the corrective evidence prepared for re-review. It does not declare CR1 passed; only the reviewer may do that.

## Closed in this remediation

- **Two controllers:** GPSx now promises two isolated physical DualSense inputs merged into one proven Remote Play player state. Buttons use OR, triggers use the maximum value, and each stick axis uses the greatest absolute magnitude with the lower `ControllerId` winning ties. A third active controller is rejected. Rust tests cover the Cross A/B release sequence and analog policy.
- **Licensing and provenance:** workspace crates declare `AGPL-3.0-only`; the three current Rust crate roots carry SPDX identifiers; `NOTICE` and `docs/PROVENANCE.md` define the pinned chiaki-ng baseline and future ledger. One centralized guard checks hidden files, every Cargo manifest/workspace member, complete non-orphan ledger rows, pinned upstream paths, ISO dates and the derived identifier in every non-generated text file.
- **Legal UI:** the TV application has a navigable About destination with copyright, license, warranty, redistribution, source repository, and build revision notices in French and English. Debug builds say `development`; a release requires the exact lowercase Git `HEAD` and a clean worktree. Publication must additionally make that commit available in the public repository.
- **Evidence language:** the parity matrix separates implementation status from evidence status. Unsupported Bluetooth haptics and adaptive triggers are not claimed. Accessibility and all hardware behavior remain `NOT_TESTED` until their gates run.
- **Diagnostic bundle security:** upload-token expiry/revocation and least privilege are specified. Strict ZIP extraction and adversarial tests reject traversal, NTFS alternate streams, classic reserved names (`CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9`, `LPT1`–`LPT9`), trailing dots/spaces, path conflicts, links, encryption, special files, false sizes, bad CRCs, excessive entries/sizes, and non-empty destinations. Extraction is staged and atomically published; failures leave no partial destination files.
- **Update chain:** the design requires an offline release key, Android v2/v3 signing, pinned signer-certificate SHA-256, and CI verification. Checksums are documented as integrity evidence, not authenticity.
- **Build-chain integrity:** the Gradle distribution and wrapper JAR hashes are pinned and checked locally. Every external CI action is pinned to a full commit SHA. CI itself remains `NOT_TESTED` until its first GitHub run.
- **Prototype truthfulness:** the Kotlin reducer is explicitly demo-only, stop from `READY` is a no-op, UI copy no longer promises two remote players, and stale/unused Android resources were removed.
- **Android backup:** application backup and device-transfer extraction are explicitly disabled.

## Verification commands

```powershell
.\tools\powershell\check.ps1 -RequireAndroid
Set-Location .\apps\android-tv
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The five remaining Android lint notices are intentional compatibility/toolchain notices: target SDK 34 for the Android 11 GP520 rollout, compile SDK 36, pinned Gradle 9.6, fixed landscape for a TV-only activity, and a false-positive English spellcheck on the French word “correspondant”. There are no lint errors, unused-resource findings, or hardcoded-text findings.

## Gates still closed

- CR1 remains pending until Claude Code re-reviews this remediation.
- CR3 remains mandatory before the first protocol-derived implementation lot.
- GP520, physical DualSense, Bluetooth-audio coexistence, accessibility, performance, release signing, update installation, and diagnostics upload remain `NOT_TESTED` until their dedicated gates.
- No release APK may be published until the signing and update-chain gate passes.
