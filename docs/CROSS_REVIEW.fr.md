# Gates de contre-revue indépendante par Claude Code

Le but est une validation contradictoire, pas une seconde implémentation. Codex
porte la modification ; Claude Code examine le diff figé, les preuves, tests et
hypothèses. Compiler ne suffit pas à franchir un gate.

| Gate | Moment d’arrêt | Objet de la revue |
|---|---|---|
| CR0 | Architecture phase 0 | Terminé dans `PHASE0_REVIEW.md` |
| CR1 | Avant d’accepter l’entrée en M1 | Licence A/B/C, concurrence, DualSense, sortie et report rigoureux des preuves GP520 |
| CR2 | Avant fusion de M1 | Pureté du cœur, invariants, files bornées, transitions et CI |
| CR3 | Avant le premier lot protocolaire | Provenance, licence, limites des parseurs, crypto, secrets, fixtures |
| CR4 | Avant le premier streaming GP520, après accord utilisateur | JNI, Surface/codec, callback audio, arrêt, invariants `unsafe` et écarts émulateur/appareil |
| CR5 | Avant une optimisation | Baselines comparables, P50/P95/P99, files, thermique et Bluetooth |
| CR6 | Avant release | Menaces, dépendances/licences, fuzz/soak, récupération et notices |

Une revue supplémentaire est obligatoire pour toute crypto maison, nouveau bloc
`unsafe`, parseur non fiable, chemin chaud non borné ou bloquant, stockage
d’authentification, implémentation dérivée du protocole ou modification de la
frontière JNI/média.

Le dossier de revue contient le jalon, le diff pertinent, ADR et provenance,
commandes et résultats, preuves matérielles séparées des simulations, inconnues
et alternatives rejetées, plus les chemins Graphify touchés. Aucun secret ni
capture brute n’y entre.

## Prompt à fournir

```text
Agis comme reviewer contradictoire indépendant pour le gate GPSx <GATE>.
N’implémente rien. Examine les fichiers indiqués et le diff Git courant.
Conteste correction, licence/provenance, sécurité, concurrence/ownership,
récupération, tests, hypothèses plateforme et performances. Sépare FACT,
INFERENCE, HYPOTHESIS, UNKNOWN et NOT_TESTED. Pour chaque constat, donne
sévérité, preuve, fichiers, correction exigée et test de clôture. Termine par
GO, GO WITH CHANGES ou NO-GO avec les blocages exacts.

Périmètre : <FICHIERS ET MODULES>
Commandes exécutées : <COMMANDES ET RÉSULTATS>
Preuves matérielles : <PREUVES OU AUCUNE>
Décisions ouvertes : <DÉCISIONS>
```

Codex classe chaque constat comme accepté, rejeté avec preuve ou différé avec
responsable et gate. Les corrections importantes modifient architecture et
tests, puis le reviewer contrôle le diff de remédiation. Tout désaccord est
présenté à l’utilisateur.

