# Moteur natif Pylux

GPSx embarque le moteur natif Remote Play et cloud PlayStation Plus construit
depuis [Pylux](https://github.com/ForWard-Technologies-LLC/Pylux), commit
`ca87a49a12bab071948f565ca53ffdad790775d3`, sous licence AGPL-3.0-only avec
l'exception OpenSSL (`LICENSE-AGPL-3.0-only-OpenSSL.txt`).

`gpsx-pylux-ca87a49.patch` contient les modifications GPSx :

- Construction : surcharge Python protobuf pour hôte Windows ; aucune
  journalisation de session DEBUG par paquet.
- Robustesse : tests de nullité avant `display_sink.cantdisplay_cb` ;
  `set_surface(NULL)` ne rend plus la main avec le verrou du codec pris (et ne le
  reprend plus dans `kill_decoder`).
- Audio : une sortie Oboe partagée qui se rouvre après un changement de sortie (et à
  la demande quand une sortie Bluetooth apparaît), peut cibler un appareil et signale
  son état ; un tampon anti-gigue de 40 ms (reconstitué après un trou, délai plafonné
  à 120 ms).
- Décodeur vidéo : compteurs (échantillons avant le décodeur, mis en file, affichés) ;
  indications basse latence facultatives avec nouvel essai simple ; le codec suit celui
  lu dans le flux ; seule l'image décodée la plus récente est affichée ; les
  échantillons sont copiés dans une file qu'un fil séparé donne au codec en attendant
  jusqu'à 5 s un tampon d'entrée, si bien qu'un décodeur bloqué ne fait ni perdre
  d'images ni bloquer la réception réseau ; les changements de format de sortie sont
  journalisés.
- H.264 (PS4 cloud sur le décodeur MStar de la GP520) : horodatages espacés de 1/60 s
  (avec le pas d'origine de 1 µs, l'affichage MStar gardait la première image), SPS
  réécrit pour autoriser les trous de frame_num et annoncer 1 image de référence,
  jeux de paramètres envoyés en codec config.
- Cloud : une image dont la référence est perdue est quand même décodée au lieu
  d'être jetée (les serveurs PS4 cloud n'envoient jamais de nouvelle IDR) ; le fil de
  réception Takion tourne en nice -10 sous Android.
- `lib/src/cloudsession_gaikai.c` : journalise les champs d'erreur d'un verrou de session refusé, jamais la réponse entière.

Reconstruisez les bibliothèques `RelWithDebInfo` strippées pour toutes les ABI
avec `tools/powershell/build-pylux-native.ps1`. Voir `docs/PROVENANCE.fr.md` et
`docs/STREAM_TUNING.fr.md`.
