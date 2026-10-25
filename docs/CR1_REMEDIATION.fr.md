# Registre de remédiation CR1

Date : 2026-10-25

Ce registre relie les constats de la revue indépendante Claude Code CR1 aux preuves correctives préparées pour la contre-revue. Il ne déclare pas CR1 validée ; seul le relecteur peut le faire.

## Fermé par cette remédiation

- **Deux manettes :** GPSx promet désormais deux entrées DualSense physiques isolées, fusionnées dans un seul état joueur Remote Play prouvé. Les boutons utilisent un OU logique, les gâchettes la valeur maximale, et chaque axe de stick la plus grande valeur absolue, avec priorité au plus petit `ControllerId` en cas d’égalité. Une troisième manette active est refusée. Des tests Rust couvrent la séquence de relâchement de Croix A/B et la politique analogique.
- **Licence et provenance :** les crates du workspace déclarent `AGPL-3.0-only` ; les racines des trois crates Rust actuelles portent des identifiants SPDX ; `NOTICE` et `docs/PROVENANCE.md` définissent la base chiaki-ng épinglée et le futur registre. Une garde centralisée contrôle les fichiers cachés, chaque manifeste/membre Cargo, les lignes complètes non orphelines, les chemins amont épinglés, les dates ISO et l’identifiant dérivé dans tout fichier texte non généré.
- **Interface légale :** l’application TV possède une destination À propos navigable avec copyright, licence, absence de garantie, redistribution, dépôt source et révision de build, en français et en anglais. Les builds debug indiquent `development` ; un build release exige le `HEAD` Git exact en minuscules et un arbre propre. La publication devra en plus rendre ce commit accessible dans le dépôt public.
- **Vocabulaire des preuves :** la matrice de parité sépare l’état d’implémentation de l’état de preuve. L’haptique Bluetooth et les gâchettes adaptatives non prises en charge ne sont pas revendiquées. L’accessibilité et tout comportement matériel restent `NOT_TESTED` jusqu’à leurs gates.
- **Sécurité des paquets de diagnostic :** l’expiration/révocation du jeton d’envoi et le moindre privilège sont spécifiés. L’extraction ZIP stricte et ses tests hostiles refusent traversée de chemin, flux alternatifs NTFS, noms réservés classiques (`CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9`, `LPT1`–`LPT9`), points/espaces finaux, conflits de chemins, liens, chiffrement, fichiers spéciaux, tailles mensongères, CRC invalides, quantités/tailles excessives et destination non vide. L’extraction passe par une zone temporaire puis une publication atomique ; un échec ne laisse aucun fichier partiel dans la destination.
- **Chaîne de mise à jour :** la conception exige une clé de release hors ligne, la signature Android v2/v3, l’empreinte SHA-256 épinglée du certificat signataire et sa vérification CI. Les sommes de contrôle sont documentées comme preuve d’intégrité, pas d’authenticité.
- **Intégrité de la chaîne de build :** les empreintes de la distribution Gradle et du JAR wrapper sont épinglées et contrôlées localement. Chaque action CI externe est épinglée sur un SHA de commit complet. La CI elle-même reste `NOT_TESTED` jusqu’à sa première exécution GitHub.
- **Sincérité du prototype :** le reducer Kotlin est explicitement limité à la démo, l’arrêt depuis `READY` ne fait rien, l’interface ne promet plus deux joueurs distants et les ressources Android obsolètes/inutilisées ont été supprimées.
- **Sauvegarde Android :** la sauvegarde applicative et l’extraction lors d’un transfert d’appareil sont explicitement désactivées.

## Commandes de vérification

```powershell
.\tools\powershell\check.ps1 -RequireAndroid
Set-Location .\apps\android-tv
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Les cinq remarques lint Android restantes sont des avis intentionnels de compatibilité/chaîne d’outils : SDK cible 34 pour le déploiement Android 11 du GP520, SDK de compilation 36, Gradle 9.6 épinglé, orientation paysage fixe pour une activité exclusivement TV et faux positif du correcteur anglais sur le mot français « correspondant ». Il n’existe aucune erreur lint, ressource inutilisée ou alerte de texte codé en dur.

## Gates encore fermées

- CR1 reste en attente jusqu’à la contre-revue Claude Code de cette remédiation.
- CR3 reste obligatoire avant le premier lot d’implémentation dérivé du protocole.
- GP520, DualSense physiques, coexistence audio Bluetooth, accessibilité, performances, signature release, installation des mises à jour et envoi des diagnostics restent `NOT_TESTED` jusqu’à leurs gates dédiées.
- Aucun APK release ne peut être publié avant validation de la gate de signature et de chaîne de mise à jour.
