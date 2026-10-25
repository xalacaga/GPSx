# Matrice de validation DualSense

> Traduction française de `DUALSENSE_MAPPING.md`.

Aucune ligne ne peut devenir `PASS` sans événements bruts relevés sur l'appareil cible.

| Commande/capacité | Windows USB | Windows Bluetooth | GP520 USB | GP520 Bluetooth |
|---|---|---|---|---|
| Boutons de façade | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Croix directionnelle | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| L1/R1 | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Plage analogique L2/R2 | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Axes des sticks | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| L3/R3 | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Create/Options/PS | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Clic du pavé tactile | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Contacts tactiles | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Gyroscope | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Accéléromètre | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vibration simple | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Retours haptiques avancés | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Gâchettes adaptatives | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| LED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Bouton/LED de sourdine | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Repli Retour (Croix en `BUTTON_B` → `BACK`) | n/a | n/a | NOT_TESTED | NOT_TESTED |
| Interception système du bouton PS | n/a | n/a | NOT_TESTED | NOT_TESTED |
| Combinaison de sortie du stream (Create + Options, 2 s) | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Niveau de batterie | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |

Statuts autorisés : `PASS`, `FAIL`, `OS_LIMITATION`, `NOT_SUPPORTED`,
`NOT_TESTED`. Chaque case modifiée doit référencer un artefact de preuve et
indiquer l'OS/build, le mode de connexion, le firmware de la manette, les codes
d'événements/axes, la plage observée, la zone morte et le comportement à la
reconnexion.

Les cases GP520 doivent consigner les **scan codes et identifiants d'axes
bruts**, pas seulement les noms de key codes Android. AOSP Android 11 ne fournit
aucune disposition DualSense (0ce6) ; la base attendue en API 30 est donc le
repli `Generic.kl`, où des libellés comme `BUTTON_A` ne correspondent pas aux
boutons physiques (HYPOTHESIS jusqu'à observation ; voir
`DUALSENSE_ANALYSIS.fr.md`). Consigner aussi la valeur de repos des axes de
gâchette (possiblement -1,0), le pilote noyau utilisé et toute disposition de
touches du fabricant. La sourdine est purement locale : l'ensemble des boutons
Remote Play n'a pas de bit Micro. Les colonnes Windows sont un sur-ensemble et
ne constituent jamais une preuve pour les colonnes GP520.
