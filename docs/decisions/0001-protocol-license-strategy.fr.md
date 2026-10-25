# ADR-0001 : stratégie de licence et d’implémentation du protocole

- Statut : **ACCEPTÉE — OPTION C**
- Ouvert le : 2026-10-25
- Acceptée par : responsable du projet, 2026-10-25
- Bloque : protocole, transport, enregistrement, authentification et média

Les implémentations utiles de la famille Chiaki sont sous AGPL-3.0 avec une
exception OpenSSL. Traduire, envelopper ou réécrire mécaniquement leur code ne
rend pas le travail indépendant.

## Option A — réutilisation compatible AGPL

Réutiliser une révision immuable et distribuer GPSx conformément à l’AGPL.

## Option B — clean-room séparée

Séparer les rôles : l’implémentation ne reçoit que des spécifications approuvées
et des fixtures redistribuables, jamais les détails du code amont.

## Option C — port Rust dérivé déclaré sous AGPL

Placer GPSx sous AGPL-3.0, déclarer le port Rust dérivé de la révision amont,
préserver notices et disponibilité du source, et documenter l’exception OpenSSL.

## Contexte du responsable et recommandation

- **FACT (responsable, 2026-10-25) :** modèle de distribution : usage personnel sur le matériel du responsable d'abord, puis éventuellement publication publique gratuite sur GitHub s'il fonctionne bien.
- **INFERENCE (ingénierie, pas un avis juridique) :** un usage privé sans transmission de copies ne déclenche aucune obligation de source AGPL ; une publication publique gratuite sous AGPL-3.0-only est compatible avec la famille Chiaki. Le coût de l'option B ne sert qu'à échapper à l'AGPL, ce dont ce modèle n'a pas besoin.
- **Recommandation :** option C ; option A si le calendrier compte plus que la préférence pour Rust. Analyse dans `../LICENSE_AUDIT.fr.md`.
- Tenir le registre de provenance dès le premier commit ; ajouter `LICENSE` et les mentions avant le premier push public.

## Enregistrement de la décision acceptée

- **Stratégie choisie :** option C. GPSx est sous AGPL-3.0-only avec la permission
  additionnelle OpenSSL de la famille Chiaki. Le protocole sera un port Rust
  dérivé déclaré, pas une implémentation en salle blanche.
- **Distribution :** usage personnel d’abord ; une éventuelle publication reste
  gratuite et accompagnée de la totalité du code source correspondant.
- **Approbateur/date :** responsable du projet, 2026-10-25.
- **Base amont principale :** commit `c8bea2642d0e95402f373af55584a3b98cd89417`
  de `chiaki-ng/chiaki-ng`. Changer cette base exige un amendement ADR et une
  revue de provenance.
- **Hypothèse juridique :** choix de conformité d’ingénierie, pas avis juridique.
  Le responsable accepte les obligations AGPL de source, mentions et attribution
  pour chaque APK ou build bureau transmis. Marques, conditions PSN et règles des
  stores restent des gates de release.
- **Périmètre protocolaire :** seuls les crates Rust portables explicitement nommés
  `gpsx-protocol`, `gpsx-transport`, `gpsx-crypto`, `gpsx-session` et leurs
  tests/fixtures peuvent contenir un comportement protocolaire dérivé. Kotlin et
  les adaptateurs de plateforme ne le dupliquent pas.
- **Provenance :** chaque fichier, constante, fixture ou algorithme adapté consigne
  dépôt, commit immuable, chemin/copyright d’origine, identifiant SPDX, responsable,
  date et modifications. Les mentions d’origine sont conservées.
- **Mentions requises :** avant distribution publique, dépôt et application portent
  le texte AGPL-3.0-only, la permission OpenSSL, les attributions amont, l’absence
  de garantie, le lien vers les sources et les mentions de modification.

L’acceptation lève le blocage de licence sur les crates protocole. Elle n’autorise
pas leur implémentation avant le dossier de revue CR3, et n’ouvre pas le gate GP520.

