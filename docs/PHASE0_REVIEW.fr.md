# Contre-revue indépendante de la phase 0

Date : 25 octobre 2026. Reviewer : Claude Code, en revue contradictoire de
l’analyse Codex. Base : documents GPSx, contrôleur/feedback chiaki-ng, média
Android Pylux et key layouts AOSP Android 11. Rien n’a été construit ni testé
sur matériel pendant cette revue.

## Décision

**GO WITH CHANGES.** M0.5 peut avancer ; M1 reste bloqué par `ROADMAP.md`.

## Constats intégrés

- Média Android déplacé vers un adaptateur natif pour éviter JNI par frame/bloc.
- Mapping DualSense API 30 fondé sur scan codes/profils, jamais sur les seuls
  noms de keycodes ; gestion du fallback BACK et d’un accord de sortie.
- Modèle filaire exact : masque boutons, sticks `i16`, gâchettes `u8` ; aucune
  perte des transitions et renvois de queue à confirmer avec le protocole.
- Matrice étendue aux sorties, interception système et limites Android.
- Ajout de l’option C : port Rust explicitement dérivé sous AGPL. Résolu : option C acceptée par le responsable le 2026-10-25 (ADR-0001).
- Architecture des crates conditionnée à la décision de licence.
- Contrat threads/files/ownership, drop-to-IDR et dérive audio documentés.
- `SurfaceView`, politique `unsafe`, pureté du cœur et tests replay exigés.

Risques encore ouverts : faible latence GP520, `WSAECONNRESET`, accès
ADB/sideload, interception PS, marques/conditions PSN, épuisement de ressources
UDP, test réseau pré-stream et stockage Keystore.

## Ouvert avant M1 au moment de la revue

- décision humaine licence A, B ou C (résolue : option C, ADR-0001) et analyse marques/PSN ;
- approbation humaine de `CONCURRENCY.md` et du design DualSense ;
- preuves GP520 : ADB/sideload, noyau/key layouts, scan codes/axes et codecs. Ce point est remplacé par la contrainte propriétaire ci-dessous.

## Contrainte propriétaire ajoutée après la revue

Le 25 octobre 2026, le propriétaire a imposé un développement offline-first
sans connexion au GP520. Les preuves physiques ne conditionnent donc plus M1 :
M1 exige des interfaces d'adaptateur solides et des statuts UNKNOWN/NOT_TESTED.
Les preuves sont reportées à M4 avec accord explicite ; voir `OFFLINE_FIRST.md`.

