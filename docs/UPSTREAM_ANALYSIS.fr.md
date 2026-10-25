# Analyse de l'amont

> Traduction française de `UPSTREAM_ANALYSIS.md`.

Date de l'audit : 2026-10-25. Ce document décrit des preuves ; il n'autorise pas à copier du code.

## Pylux

Pylux est un fork multiplateforme actif, bâti sur Chiaki et chiaki-ng. Son dépôt contient une bibliothèque C partagée, une UI bureau Qt, un frontend Android Kotlin/JNI, des intégrations iOS/macOS, des composants cloud/PSN, des tests et du packaging. Son guide de projet place explicitement la logique de protocole et de streaming dans la bibliothèque partagée, et le comportement propre à chaque plateforme dans des adaptateurs. [Dépôt Pylux](https://github.com/ForWard-Technologies-LLC/Pylux), [Guide des agents Pylux](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/CLAUDE.md)

Modules partagés observés :

- découverte et enregistrement : `discovery.c`, `discoveryservice.c`, `regist.c` ;
- session/contrôle : `session.c`, `ctrl.c`, `launchspec.c` ;
- transport : `takion.c`, `streamconnection.c`, `reorderqueue.c`, `congestioncontrol.c` ;
- récupération : `fec.c`, `frameprocessor.c`, gestion des images corrompues et des IDR ;
- enveloppes cryptographiques : `ecdh.c`, `gkcrypt.c`, `rpcrypt.c` ;
- média : `videoreceiver.c`, `audioreceiver.c`, `ffmpegdecoder.c`, `opusdecoder.c` ;
- entrée/sortie : `controller.c`, `feedback.c`, `feedbacksender.c`, `audiosender.c`.

[Bibliothèque partagée Pylux](https://github.com/ForWard-Technologies-LLC/Pylux/tree/master/lib/src)

Le build Android utilise Kotlin et CMake/NDK, active Opus, mbedTLS et RUDP, et désactive le décodeur FFmpeg partagé. C'est une référence utile pour le cycle de vie, mais son UI est à base de View plutôt qu'en Compose pensé pour la TV. [Build Android Pylux](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/android/app/build.gradle)

Pylux contient des références comportementales utiles : nettoyage de la Surface, capture du pointeur pour le pavé tactile de la manette, overlay de métriques, dépendance de stockage sécurisé et UI de reconnexion. Sa grande `StreamActivity` montre aussi que GPSx doit séparer plus nettement cycle de vie, entrée, overlay et streaming. [StreamActivity Pylux](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/android/app/src/main/java/com/metallic/chiaki/stream/StreamActivity.kt)

Recommandation : utiliser Pylux comme oracle comportemental Android le plus pertinent, mais pas comme source de traduction tant que l'AGPL n'est pas acceptée.

## Chiaki

Le dépôt GitHub d'origine est archivé et renvoie vers SourceHut. Il a établi le cœur C portable avec plusieurs frontends, et implémenté la découverte, l'enregistrement, le réveil et le streaming. [Dépôt Chiaki archivé](https://github.com/thestr4ng3r/chiaki)

Sa valeur principale est la provenance : les forks ultérieurs conservent son architecture et son cœur. L'utiliser pour l'historique et l'archéologie des régressions, pas comme base Android de référence.

## chiaki-ng

chiaki-ng est le fork de nouvelle génération activement maintenu. Son guide des contributeurs associe le travail protocole/session à `ctrl.c` et `takion.c`, la gestion des paquets et des médias aux récepteurs, les codecs aux enveloppes FFmpeg/Opus, et le retour manette aux émetteurs partagés. Le rendu bureau utilise Qt/QML et libplacebo ; la prise en charge des manettes utilise SDL avec des extensions. [Guide des contributeurs chiaki-ng](https://github.com/chiaki-ng/chiaki-ng/blob/main/CONTRIBUTOR_GUIDE.md)

Le récepteur vidéo réalise l'assemblage des images, la récupération FEC, la détection des références manquantes, le signalement des images corrompues, les demandes d'IDR et les callbacks d'échantillons compressés. [Récepteur vidéo chiaki-ng](https://github.com/chiaki-ng/chiaki-ng/blob/main/lib/src/videoreceiver.c)

Les fonctionnalités bureau incluent le remappage, le pavé tactile, le mouvement, le micro, les retours haptiques, les gâchettes adaptatives, le suivi des pertes de paquets, le choix du décodeur et le buffering audio. C'est donc le meilleur oracle Windows, même si Qt/libplacebo n'est pas le frontend final visé. [StreamSession chiaki-ng](https://github.com/chiaki-ng/chiaki-ng/blob/main/gui/src/streamsession.cpp)

Recommandation : utiliser chiaki-ng bureau comme premier client de référence fonctionnel. Si une distribution sous AGPL est acceptable, envelopper son cœur partagé est moins risqué que le traduire.

## Forks Android

Pylux est la branche Android maintenue la plus solide trouvée. Un fork spécialisé `chiaki-ng-android-TV` ajoute des shaders, du remappage, du mouvement et des retours haptiques, mais son propre README avertit qu'il a été généré par IA et n'a pas été correctement revu. C'est une source d'investigation, pas une base de confiance. [chiaki-ng-android-TV](https://github.com/drydenmike/chiaki-ng-android-TV)

Des forks et pull requests Android récents revendiquent la prise en charge des gâchettes adaptatives et des retours haptiques en Bluetooth. Ils sont expérimentaux et ne constituent pas une preuve pour le GP520 ; aucun ne peut servir à marquer une capacité PASS.

## Correspondance de l'architecture du protocole

```text
découverte → sélection → enregistrement/stockage des identifiants → réveil
→ négociation/contrôle → établissement du transport temps réel chiffré
→ réception des médias + envoi du retour manette
→ récupération/reconnexion ou arrêt ordonné
```

La découverte locale utilise des paquets de recherche et des ports propres à la console. La connexion distante dans chiaki-ng utilise une signalisation assistée par le PSN et le perçage de NAT UDP (hole punching), et elle est documentée comme IPv4 uniquement côté PlayStation. [Service de découverte](https://github.com/chiaki-ng/chiaki-ng/blob/main/lib/src/discoveryservice.c), [connexion distante](https://chiaki-ng.github.io/chiaki-ng/setup/remoteconnection/)

Le cœur partagé possède les sockets, les messages de contrôle, le transport Takion, le réordonnancement, la FEC, le retour de congestion, les rapports de corruption et la récupération par IDR. Tout cela reste sous les frontières de l'UI.

## Correspondance des médias et des entrées

```text
paquet vidéo → déchiffrement/validation → réordonnancement/FEC → assemblage d'image
→ unité d'accès H.264/HEVC compressée → décodeur de plateforme → Surface

paquet audio → déchiffrement/validation → réordonnancement/FEC → décodage Opus
→ file PCM bornée → sortie audio de la plateforme

entrée brute de la plateforme → descripteur/capacités → état normalisé
→ transitions boutons/gâchettes → historique de retour (préserve les fronts, renvois de queue)
→ état sticks/mouvement → paquet d'état de retour (le plus récent l'emporte)
```

Correction du 2026-10-25 : une version précédente indiquait « émetteur borné/fusionné ». Le `feedbacksender.c` amont (FACT) conserve un historique de 16 événements et renvoie le dernier paquet d'historique à 20/50/100/200 ms pour qu'un relâchement perdu ne laisse pas un bouton bloqué. Les transitions de boutons ne doivent jamais être fusionnées.

Le bureau utilise FFmpeg/libplacebo. Android doit fournir les unités d'accès compressées à MediaCodec avec une sortie Surface. Android prend en charge AVC/HEVC et recommande de choisir un décodeur nommé à partir des capacités interrogées. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)

Les changements de route audio ne doivent recréer que la sortie audio si nécessaire, pas la session réseau. L'entrée Android doit gérer les croix directionnelles par touches/HAT et les variantes de gâchettes par axe/touche. [Entrée manette Android](https://developer.android.com/games/sdk/game-controller/controller-input)

## À conserver dans l'esprit

- La séparation cœur partagé / adaptateurs de plateforme.
- Des sous-systèmes distincts pour découverte, enregistrement, session, transport, médias et retour manette.
- La frontière de callback sur la vidéo compressée.
- L'état manette normalisé.
- La gestion FEC/réordonnancement et la signalisation explicite de récupération.
- Le client bureau de référence comme oracle du protocole et des manettes.

## À remplacer ou repenser

- Les grandes classes d'intégration activité/session.
- L'UI téléphone pensée pour le tactile.
- Des métriques sans schéma de télémétrie unifié.
- Des hypothèses de plateforme sur les manettes sans descripteurs de capacités.
- Tout buffer non borné ou ordre d'arrêt dont l'absence de course (race) ne peut être prouvée.

## Limite des preuves

Aucun build amont n'a été exécuté contre la PS5 ou le GP520 de l'utilisateur. L'inspection des dépôts établit l'architecture et le comportement déclaré seulement. Fiabilité, mappings, latence, décodage matériel et coexistence Bluetooth restent NOT_TESTED.
