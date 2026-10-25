# Architecture Android TV

> Traduction française de `ANDROID_TV_ARCHITECTURE.md`.

Cible : BenQ GP520, Google TV / Android 11, API 30.

## Faits sur l'appareil

BenQ annonce Android 11, Bluetooth 5.2, Wi-Fi 2,4/5/6 GHz et une chaîne d'affichage à 60 Hz. Son manuel annonce le décodage H.264 et HEVC jusqu'en 4K60, mais la lecture de fichiers ne prouve pas qu'un MediaCodec choisi par l'application soit matériel ou à faible latence. [Spécifications GP520](https://www.benq.com/en-sg/projector/cinema/gp520/spec.html), [Manuel GP520](https://esupportdownload.benq.com/esupport/PROJECTOR%20FOR%20CONSUMER/UserManual/GP520/GP520_UM_EN_260324150604.pdf)

Des données tierces indiquent un MT9629/T31, quatre Cortex-A55, un Mali-G52 et 2 Go de RAM. À traiter comme **INFERENCE** jusqu'à confirmation par des diagnostics sur l'appareil physique. [Android TV Guide](https://www.androidtv-guide.com/streaming-gaming/benq-gp520/)

## Couches

```text
Kotlin (JVM)
  UI Compose for TV : Accueil / Consoles / Enregistrement / Réglages / Diagnostics
  Coordinateur de session : cycle de vie, permissions, stockage sécurisé, routes,
                            cycle de vie de la Surface, capture InputDevice/KeyEvent/MotionEvent
           │ JNI étroit : commandes, remise de la Surface, lots d'entrées, lots d'événements/métriques
Adaptateur Android natif (Rust ndk/jni ou C ; Android uniquement ; NE fait PAS partie du cœur)
  ANativeWindow depuis la Surface, threads d'alimentation et de sortie AMediaCodec,
  flux AAudio/Oboe + anneau PCM, threads de callback JNI
           │ appels Rust/C dans le processus uniquement ; aucune JVM sur ce chemin
Cœur portable (sans dépendance Android)
  découverte, enregistrement, session, transport, crypto, télémétrie, entrée
  émet unités d'accès compressées, PCM et événements via des ports de plateforme
```

**Décision (revue du 2026-10-25) :** la vidéo compressée et le PCM n'entrent jamais dans la JVM. Kotlin remet une `Surface` une fois par génération ; l'adaptateur natif la convertit avec `ANativeWindow_fromSurface` et possède le codec. Cela correspond à la pratique Android amont (FACT : `video-decoder.c` de Pylux utilise `AMediaCodec` du NDK avec un thread de sortie natif ; `audio-output.cpp` utilise Oboe).

La vue du stream est une `SurfaceView` hébergée dans Compose via `AndroidView`, jamais une `TextureView` (TextureView ajoute une composition GPU et une copie). L'impact des overlays sur les plans du compositeur matériel est UNKNOWN sur le GP520 et doit être mesuré.

Compose for TV prend en charge l'API 21+, donc l'API 30 est compatible. Android exige une activité `LEANBACK_LAUNCHER`, et les applications TV doivent fonctionner sans écran tactile. [Compose for TV](https://developer.android.com/training/tv/playback/compose), [Configuration d'une app TV](https://developer.android.com/training/tv/get-started/create)

Utiliser des groupes de focus et des requesters explicites, et tester chaque écran à la croix directionnelle et à la DualSense. L'entrée du stream ne doit pas naviguer dans les overlays sauf si le mode menu est actif. [Focus Compose](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)

## Frontière JNI

Opérations recommandées : créer/détruire le moteur, démarrer/arrêter la découverte, enregistrer, connecter/déconnecter, définir/effacer la génération de Surface, soumettre des lots d'événements d'entrée bruts, mettre à jour la route audio, et récupérer des instantanés groupés de métriques/événements.

- Kotlin possède le cycle de vie Activity/Service/Surface, les permissions et les objets Android.
- Le code natif possède les sockets, l'état du protocole, les files et les threads de transport.
- L'adaptateur Android natif possède `AMediaCodec`, l'`ANativeWindow` d'exactement une génération de Surface courante, et le flux AAudio/Oboe.
- L'entrée traverse JNI une fois par événement d'entrée Android (un appel par `KeyEvent`, un par `MotionEvent` avec ses échantillons historiques). Chaque appel transporte les scan codes bruts, les key codes, les valeurs d'axes et l'heure de l'événement ; le mapping se fait côté natif. Les instantanés ne sont jamais fusionnés à cette frontière (voir `DUALSENSE_ANALYSIS.md`).
- Les threads natifs qui rappellent Kotlin s'attachent une seule fois, utilisent des références globales de classe/méthode mises en cache, et livrent des événements groupés.
- Les callbacks utilisent des exécuteurs documentés et des références globales valides.
- L'arrêt est idempotent et joint les workers avant de libérer les callbacks et les références globales.

## Vidéo

Commencer par H.264 720p60, puis 1080p60. HEVC ne vient qu'une fois le cycle de vie H.264 stable.

Fournir des unités d'accès compressées complètes à MediaCodec et rendre directement vers une Surface. Interroger `MediaCodecList`, choisir selon le format exact, et consigner le nom du composant, le format, le profil/niveau, la classification, la profondeur de file, les horodatages, les images perdues, les exceptions, les réinitialisations et la cadence. La disponibilité d'une API ne prouve pas l'accélération matérielle. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec), [Médias NDK](https://developer.android.com/ndk/reference/group/media)

Les identifiants de génération de Surface empêchent des callbacks tardifs de se lier à une Surface remplacée. Les erreurs du décodeur doivent d'abord recréer le décodeur et récupérer sur un IDR, sans relancer l'authentification. Une Surface de remplacement passe par `AMediaCodec_setOutputSurface` (API 23) avant de se replier sur la recréation du décodeur.

Le cœur n'appelle jamais le codec. L'assemblage d'images remet des unités d'accès complètes à l'adaptateur via la file vidéo bornée décrite dans `CONCURRENCY.md`. Seul le thread d'alimentation de l'adaptateur attend sur `AMediaCodec_dequeueInputBuffer`. (FACT : Pylux attend jusqu'à 3 × 10 ms sur le chemin des échantillons ; GPSx ne doit pas bloquer la réception réseau de cette façon.) Une seule `memcpy` vers le buffer d'entrée du codec est autorisée ; aucun passage par un buffer JVM.

Notes API 30 (FACT) : la clé basse latence de `MediaFormat` et `FEATURE_LowLatency` existent en API 30 ; leur prise en compte par les décodeurs du GP520 est UNKNOWN. Pylux ne définit pas de CSD et s'appuie sur des SPS/PPS dans le flux (FACT) ; l'acceptation de ces paramètres dans le flux par le GP520 est NEEDS_HARDWARE_TEST. Mesurer la profondeur de rétention du décodeur (mise en file → rendu) pour chaque composant.

La latence d'affichage du projecteur (mode d'image, traitement du mouvement) échappe au contrôle de l'application. La mesurer séparément par une méthode « glass-to-glass » : une caméra rapide filme à la fois la sortie de référence de la PS5 et l'image projetée, plus des tests bouton-vers-photon avec un indicateur d'entrée visible.

## Audio

Garder la réception réseau et le décodage Opus indépendants de la sortie. L'adaptateur natif pilote AAudio (API 26+) ou Oboe (Apache-2.0) ; le PCM ne transite jamais par `AudioTrack` ni par la JVM. Utiliser un anneau PCM borné avec une petite profondeur cible mesurée, des métriques de sous-alimentation/débordement et la politique de dérive d'horloge de `CONCURRENCY.md`. Le mode Exclusive/MMAP peut être refusé sur le GP520 (UNKNOWN) ; consigner ce qui est accordé. Les changements de route ne recréent que les ressources audio de la plateforme, si nécessaire.

À titre de référence, l'anneau de Pylux fait 32 × 1024 octets, soit environ 170 ms de stéréo s16 à 48 kHz (calculé à partir de valeurs FACT). C'est une borne supérieure à battre, pas une cible.

BenQ avertit que l'audio Bluetooth externe peut ajouter environ 300 ms. Le signaler séparément de la latence applicative et ne pas le masquer avec de plus gros buffers globaux. Un stream en direct ne peut pas retarder la vidéo pour compenser ; la synchronisation A/V avec un audio Bluetooth est donc une limitation documentée pour l'utilisateur. [FAQ Bluetooth BenQ](https://www.benq.com/en-us/support/downloads-faq/faq/product/application/projector-faq-kn-00208.html)

## Entrée, Bluetooth et sécurité

Utiliser `InputDeviceListener` ; maintenir l'état par identifiant interne et collecter le descripteur, VID/PID, les sources, les touches, les plages de mouvement et la capacité de vibration. La télécommande TV sert à l'UI, ce n'est pas une manette Remote Play.

Les liaisons Bluetooth sont gérées par l'OS. Observer les événements de périphériques et de routes et les corréler aux métriques du stream ; ne pas reconnecter inutilement des appareils non concernés.

Pendant un stream, l'entrée manette est entièrement consommée par le gestionnaire du stream, y compris les touches qu'Android transformerait sinon en actions de repli (un `BUTTON_B` non géré devient `BACK`). Voir `DUALSENSE_ANALYSIS.md` pour la conception de la sortie du stream.

Le regroupement des événements d'entrée par vsync peut ajouter du délai (HYPOTHESIS). Mesurer le temps entre la réception par l'OS et l'appel JNI, et évaluer `View.requestUnbufferedDispatch` (vérifier le niveau d'API) pour les sources joystick.

L'API 30 n'a pas `VibratorManager` (API 31). La vibration n'est possible que via `InputDevice.getVibrator()` si le noyau expose le retour de force, ce qui est UNKNOWN.

Protéger les identifiants persistants avec une clé AES-GCM de l'Android Keystore (adossée au matériel si le GP520 le permet ; UNKNOWN) et un stockage chiffré géré par l'application sur l'API 30. Ne pas adopter `androidx.security:security-crypto` sans avoir d'abord confirmé son état de maintenance (INFERENCE : déprécié en amont). Garder les secrets hors des préférences ordinaires et expurger de façon centralisée les journaux et exports structurés.

## Validation sur le GP520

Cette section est un gate matériel différé. Selon `OFFLINE_FIRST.md`, aucun de
ces contrôles n'est exécuté ni ne bloque M1–M3 avant l'autorisation explicite
d'accès au GP520. Les preuves émulateur restent séparées.

0. Confirmer d'abord l'accès ADB et l'installation d'APK hors store sur le GP520 ; toutes les étapes suivantes en dépendent.
1. Consigner le firmware/build, la version du noyau, la présence du pilote `hid-playstation`, les fichiers de disposition de touches du fabricant (`/vendor/usr/keylayout`, `/system/usr/keylayout`), le SoC, la mémoire, l'état thermique, le réseau, le mode d'affichage, les codecs et les périphériques audio.
2. Vérifier la visibilité dans le lanceur et chaque écran avec la seule télécommande.
3. Recenser le comportement de MediaCodec avec des flux de test 720p60 et 1080p60.
4. Valider la vidéo PS5 en direct, la recréation de Surface, le passage arrière-plan/premier plan et l'arrêt.
5. Exécuter la matrice DualSense en Bluetooth.
6. Tester les haut-parleurs internes et les changements de route vers une barre de son.
7. Tester la coexistence DualSense + barre de son + télécommande en corrélant les données Wi-Fi et du stream.
8. Effectuer des tests d'endurance de 10, 30 et 60 minutes, puis de plusieurs heures.
