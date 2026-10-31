# GPSx update channel

## Bootstrap installation

Implemented 2026-10-31 (`AppUpdater`, `tools/powershell/publish-release.ps1`); GP520 behavior `NOT_TESTED`. The first GP520 installation will use the
Downloader application already available on Google TV. Downloader will open a
stable GPSx release URL, download the release APK, and hand it to Android's package
installer. The user will explicitly authorize the Downloader source and installation.

The first APK must already use the definitive release signing key. Every later
APK keeps the same application ID and signing identity and increments
`versionCode`; otherwise Android rejects it as an update.

A public GitHub release is a distribution under the accepted AGPL strategy, so
the exact corresponding source, license text, OpenSSL permission, build scripts
and notices must be available at the same time. A private repository generally
cannot be bootstrapped conveniently through Downloader because release assets
require authenticated requests.

## In-application update

Settings exposes a `Check for GPSx update` action. The updater:

1. reads the configured GitHub release channel;
2. compares release manifest version/build data with the installed package;
3. downloads the APK to application-private temporary storage;
4. verifies manifest schema, SHA-256, expected package name and release channel;
5. reads the APK signing certificate and rejects it unless its SHA-256 fingerprint
   matches the release certificate pinned in the installed application;
6. asks Android's package installer to verify the APK signature and install it;
7. retains the current application if download, verification or installation fails.

Android TV does not permit an ordinary application to install an update silently.
The user confirms each installation and may need to allow GPSx as an unknown-app
install source. The GP520's actual policy remains `NOT_TESTED` until the physical
hardware gate opens.

## Security rules

- No signing key or repository credential is embedded in the APK or repository.
- The release signing key is generated and backed up offline. Release APKs use
  Android APK Signature Scheme v2 and v3. The private key never enters the source
  repository or ordinary GitHub Actions secrets; any later remote-signing design
  requires CR6 approval.
- Debug-signed APKs are never published. Release CI fails when the signer certificate
  fingerprint differs from the separately configured production fingerprint.
- Public release checks need no token. Private checks use a revocable credential
  scoped to read-only contents of one repository, stored with Android Keystore.
- Redirects are restricted to approved HTTPS GitHub hosts; TLS validation remains enabled.
- A checksum beside an APK is an integrity check, not publisher authentication.
  Publisher authenticity comes from the independently pinned signing certificate
  and Android's same-signer update rule.
- The release manifest is not described as signed unless a separate offline
  manifest-signing key, format and rotation process are approved. Initially it is
  schema-validated metadata covered by the APK/certificate verification workflow.
- Updates never execute downloaded code before Android verifies and installs it.
- Downgrades and cross-channel installs require an explicit recovery procedure.

Authentication storage and the updater supply chain require an extra Claude Code
security review before implementation, as required by `CROSS_REVIEW.md`.
