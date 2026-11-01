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
rendait la main sans libérer le verrou du codec (et le reprenait dans
`kill_decoder`) ; le codec est créé au premier échantillon avec le format lu dans le
flux ; les images décodées en retard sont sautées ; options faible latence du
décodeur désactivables ; en cloud, une demande d'IDR (Takion IDRREQUEST) est
envoyée au plus une fois par seconde quand une image perd sa référence ou que le
FEC échoue ; les échantillons sont copiés dans une file qu'un fil séparé donne au codec, en
attendant jusqu'à 5 s un tampon d'entrée au lieu de les jeter, si bien qu'un décodeur
bloqué ne bloque jamais la réception réseau ; les flux PS4 cloud peuvent utiliser le
décodeur H.264 logiciel d'Android (réglé depuis Kotlin) ; pour le décodeur matériel, le SPS H.264
est réécrit pour annoncer au plus 4 images de référence (le PS4 cloud en annonce 9, plus
de tampons que le décodeur de la GP520 n'en accorde) ; le fil de réception Takion tourne en nice -10 sous Android ; en cloud, une image dont
la référence est perdue est quand même transmise au décodeur au lieu d'être jetée.

Reconstruisez les bibliothèques `RelWithDebInfo` strippées pour toutes les ABI
avec `tools/powershell/build-pylux-native.ps1`. Voir `docs/PROVENANCE.fr.md` et
`docs/STREAM_TUNING.fr.md`.
