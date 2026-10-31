# Gates de contre-revue indépendante par Claude Code

> **Supprimé le 2026-10-31.** Le propriétaire du projet a arrêté les gates de
> contre-revue indépendante. Cette page reste un historique de CR0 à CR4. Plus
> aucun gate ne bloque le travail ; les tests, la licence, la sécurité et le gate
> matériel physique restent applicables.


Le but est une validation contradictoire, pas une seconde implémentation. Codex
porte la modification ; Claude Code examine le diff figé, les preuves, tests et
hypothèses. Compiler ne suffit pas à franchir un gate.

| Gate | Moment d’arrêt | Objet de la revue |
|---|---|---|
| CR0 | Architecture phase 0 | Terminé dans `PHASE0_REVIEW.md` |
| CR1 | Avant d’accepter l’entrée en M1 | Licence A/B/C, concurrence, DualSense, sortie et report rigoureux des preuves GP520 |
| CR2 | Avant fusion de M1 | Pureté du cœur, invariants, files bornées, transitions et CI |
| CR3 | Avant le premier lot protocolaire | Provenance, licence, limites des parseurs, crypto, secrets, fixtures |
| CR4 | Avant le premier streaming GP520, après autorisation par le propriétaire du dossier de revue | Ownership JNI, cycle Surface/codec, sûreté du callback audio, arrêt, invariants de sûreté natifs et écarts émulateur/appareil |
| CR5 | Avant une optimisation | Baselines comparables, P50/P95/P99, files, thermique et Bluetooth |
| CR6 | Avant release | Menaces, dépendances/licences, fuzz/soak, récupération et notices |

Une revue supplémentaire est obligatoire pour toute crypto maison, nouveau bloc
`unsafe`, parseur non fiable, chemin chaud non borné ou bloquant, stockage
d’authentification, implémentation dérivée du protocole ou modification de la
frontière JNI/média.

L’autorisation de préparer ou relire CR4 n’autorise pas l’accès au matériel
physique. Après un `GO` CR4, le propriétaire confirme séparément la date, les
commandes et le périmètre physique borné avant toute connexion ou installation
sur le GP520.

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

