# Adaptateur Android natif — première tranche CR4

Statut : **CANDIDAT D’IMPLÉMENTATION — CONTRE-REVUE CR4 INDÉPENDANTE CIBLÉE REQUISE**.

## Périmètre

Cette tranche limitée à l’émulateur établit la frontière d’ownership et de cycle
de vie Android. Elle ne se connecte ni à une console ni au GP520, ne décode aucun
trafic Remote Play et ne revendique aucune performance appareil.

Kotlin crée un `NativeEngine` à la demande pour le futur propriétaire du stream,
possède le callback du `SurfaceView` et attribue des générations Surface
strictement croissantes. `MainActivity` ne charge pas l’adaptateur et ne démarre
aucun thread natif. JNI reçoit un identifiant de registre opaque, jamais une
adresse native. Le registre prend un propriétaire natif partagé pour chaque
appel : une destruction concurrente n’invalide donc pas une opération en cours.
Il n’existe aucune référence JNI globale ni callback natif vers Kotlin.

L’adaptateur C++ limité à Android possède l’`ANativeWindow` retenue, le
`AMediaCodec` H.264, le flux AAudio chargé dynamiquement, l’anneau stéréo s16 fixe
de 8 192 octets (environ 85,3 ms à 48 kHz) et deux workers natifs de cycle de vie.
Une génération de remplacement tente d’abord `AMediaCodec_setOutputSurface` ; un
échec recrée le décodeur. Si cette recréation échoue, l’ancienne fenêtre et la
nouvelle sont libérées et la génération tentée est enregistrée afin que son clear
correspondant reste valide. Un attach ou clear obsolète ne modifie pas l’ownership.

Le callback AAudio effectue uniquement des lectures d’anneau de capacité fixe,
des opérations atomiques lock-free et un remplissage par zéro. Il n’alloue pas,
ne prend pas de verrou, ne journalise pas et n’appelle pas JNI. Les symboles AAudio
sont chargés par `dlopen` ; la sortie n’est activée qu’à partir d’API 27. L’arrêt
exécute `requestStop`, une attente d’état bornée, puis close. Le callback d’erreur
est lock-free et effectue un seul stockage atomique. INFÉRENCE : à partir d’API
27, `AAudioStream_close` ne retourne qu’après la fin des callbacks ; API 27/28
restent NOT_TESTED. Une déconnexion est exposée comme état de santé ; la
réouverture automatique du flux n’est pas implémentée. PCM et vidéo compressée
ne traversent jamais la JVM.

L’arrêt retire l’entrée du registre, réveille et joint les deux workers, supprime
le codec, libère la fenêtre native, puis exécute l’arrêt audio ordonné. Un échec
du constructeur joint tout worker déjà démarré. `NativeEngine.close()` et l’arrêt
natif sont idempotents.

## Preuves

FAIT (2026-10-28) : NDK 28.2.13676358 a compilé l’adaptateur avec warnings
interdits pour arm64-v8a, armeabi-v7a, x86 et x86_64 dans `assembleDebug`.

FAIT (2026-10-28) : le wrapper limité à l’émulateur a exécuté les 13 tests
d’instrumentation sur l’AVD Google TV API 36 x86_64. Les dix tests natifs couvrent
create/destroy ×1000, générations Surface obsolètes et en échec, remplacement
Surface-vers-Surface réel, start/stop AAudio et signalement de déconnexion sur
1 000 cycles, une table AAudio substituée qui appelle puis joint le callback
d’erreur enregistré depuis un second thread pendant stop et close sur 1 000
cycles, attach/clear/start/stop concurrents face à close en modes lifecycle-test
et codec/AAudio réels, arrêt idempotent et remplacement réel du contrôleur
`SurfaceView`. Chaque worker observe `OK` avant close. Les compteurs engine,
worker et fenêtre native sont revenus à zéro.

FAIT (2026-10-28) : les mêmes dix tests natifs ont réussi sous le profil
AddressSanitizer du NDK sur API 36. LeakSanitizer n’est pas disponible sur ce
runtime Android. `new_delete_type_mismatch` est désactivé car le
`libsfplugin_ccodec.so` système d’API 36 le déclenche en interne ; les autres
erreurs ASan restent fatales.

FAIT (2026-10-28) : les exports JNI de test ne sont compilés qu’avec
`NDK_DEBUG=1` ; un build x86_64 isolé avec `NDK_DEBUG=0` ne contient aucun symbole
dynamique `ForTest`, de comptage de ressources ou de fenêtres. Le wrapper
d’instrumentation efface les résultats Gradle courants avant chaque run et garde
des copies XML normales et ASan séparées.

FAIT : le test existant de découverte et ctrl en espace utilisateur Android sur
LAN virtuel a réussi une fois dans la même session émulateur. Cette preuve
protocolaire reste séparée.

## Limites explicites

- NOT_TESTED : exécution API 27/28, car aucune image système API 27/28 n’est
  installée ;
- NOT_TESTED : livraison d’un callback réel de déconnexion AAudio, récupération
  AAudio automatique après déconnexion, alimentation
  en access units compressées, pacing des frames, récupération IDR, production
  PCM réelle, overflow de l’anneau, dérive audio, restauration après mort du
  processus, HWASan et soak audio de 60 minutes ;
- FAIT : la création/recréation codec est actuellement synchrone sous le mutex de
  contrôle de l’adaptateur et peut s’exécuter sur le thread appelant Kotlin ; son
  déplacement vers un worker de commandes codec dédié est différé avant le
  streaming de production ;
- UNKNOWN : codecs, routes audio, thermique et performances du GP520 ;
- cette tranche ne prouve ni performance ni interopérabilité ;
- le gate matériel physique reste fermé.
