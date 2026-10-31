# Moteur natif Pylux

GPSx embarque le moteur natif Remote Play et cloud PlayStation Plus construit
depuis [Pylux](https://github.com/ForWard-Technologies-LLC/Pylux), commit
`ca87a49a12bab071948f565ca53ffdad790775d3`, sous licence AGPL-3.0-only avec
l'exception OpenSSL (`LICENSE-AGPL-3.0-only-OpenSSL.txt`).

`gpsx-pylux-ca87a49.patch` contient les modifications GPSx : surcharge Python
protobuf pour hôte Windows, tests de nullité avant `display_sink.cantdisplay_cb`,
aucune journalisation de session DEBUG par paquet, des indications basse latence
du décodeur avec nouvel essai simple, et une sortie audio Oboe partagée qui se
rouvre après un changement de sortie (et à la demande de GPSx quand une sortie
Bluetooth apparaît), peut cibler un appareil de sortie donné, et signale son état
et son appareil ; des compteurs du décodeur vidéo (échantillons reçus avant le
décodeur, mis en file, affichés) et une correction de `set_surface(NULL)` qui
rendait la main sans libérer le verrou du codec.

Reconstruisez les bibliothèques `RelWithDebInfo` strippées pour toutes les ABI
avec `tools/powershell/build-pylux-native.ps1`. Voir `docs/PROVENANCE.fr.md` et
`docs/STREAM_TUNING.fr.md`.
