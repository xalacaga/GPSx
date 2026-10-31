# Installing and testing GPSx on the GP520

Status: test channel opened by the project owner on 2026-10-31.

## 1. Install with Downloader

1. On the GP520, install **Downloader** (AFTVnews) from the Google Play Store.
2. Settings → Privacy & security → Unknown sources (wording varies): allow
   **Downloader** to install apps.
3. In Downloader, enter:
   `https://github.com/xalacaga/GPSx/releases/latest/download/gpsx.apk`
4. Confirm the Android installation, then open **GPSx**.

The APK is signed with the GPSx release key (certificate SHA-256
`4D:04:2D:1D:08:60:0F:6A:8B:48:98:21:B6:54:7A:C1:3A:71:38:0E:9F:2F:70:B2:27:BA:58:72:F6:2B:E2:90`).
Every later version must carry the same signature.

## 2. Update from inside GPSx

Settings → **Mettre à jour GPSx** → *Rechercher une mise à jour* → *Installer*.
GPSx reads the latest public GitHub release, checks the APK SHA-256, package name
and signing certificate, then Android asks for confirmation. The first time,
Android may ask to allow GPSx as an install source.

## 3. Recommended projector and network settings

- Picture mode **Game** and **Game Boost** on.
- 5 GHz or 6 GHz Wi-Fi, PS5 wired to the router if possible, projector in sight
  of the access point. See `STREAM_TUNING.md`.

## 4. Run a test

1. Pair the PS5 once in **Session** (8-digit code and PSN Account ID).
2. Press **Démarrer le stream** (GPSx wakes the PS5 if needed) and play for at
   least 2 minutes; 10 minutes gives a more useful comparison.
3. Press Back to stop. Every session of 5 s or more writes one report.

## 5. Send results to GitHub

Results go to the private repository `xalacaga/GPSx-diagnostics`, never to the
public source repository.

1. On github.com: Settings → Developer settings → Fine-grained tokens →
   *Generate new token*; repository access **Only select repositories →
   GPSx-diagnostics**; permission **Contents: Read and write**; expiration
   30 days.
2. In GPSx → **Diagnostics** → *Résultats de tests*: keep the repository
   `xalacaga/GPSx-diagnostics`, paste the token (the Google TV phone app can
   paste text) and press **Enregistrer**.
3. Press **Envoyer les résultats sur GitHub**. Each report lands in
   `results/YYYY/MM/<date>-<kind>.json`.

The token is encrypted with Android Keystore, never logged or exported, and can
be removed with **Supprimer le jeton**. Reports contain measurements, device
model, Android version and Wi-Fi band/link/RSSI only: no SSID, BSSID, IP address,
console name, registration key or PSN token.

## Report format

`gpsx-stream-test/1`: app version and source revision, device, requested video
profile and codec, network class, start/end, end reason, p50/p95/p99 summaries
after a 5 s warm-up, and one sample per second
`[t, fps, bitrateMbps, rttMs, packetLoss, framesLost, width, height]`.
