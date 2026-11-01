# Audit des licences

> Traduction française de `LICENSE_AUDIT.md`.

Date de l'audit : 2026-10-25. Il s'agit d'une analyse de conformité d'ingénierie, pas d'un avis juridique.

## Statut du projet

**FAIT :** le responsable a accepté l’option C le 2026-10-25 et la base Android chiaki-ng épinglée le 2026-10-28. GPSx est sous AGPL-3.0-only avec la permission additionnelle OpenSSL de la famille Chiaki ; son port protocolaire Rust et son contrat de compatibilité Kotlin/JNI sont des dérivés déclarés. L’ADR-0001 et `PROVENANCE.fr.md` sont les sources de vérité.

## Résumé de l'amont

| Projet/composant | Licence observée | Statut de réutilisation |
|---|---|---|
| Chiaki | Lignée AGPL-3.0 avec exception OpenSSL | Bloqué en attente de la décision de licence produit |
| chiaki-ng | AGPL-3.0 ; les fichiers du cœur utilisent `LicenseRef-AGPL-3.0-only-OpenSSL` | Accepté au commit épinglé pour la base Android et les ports dérivés déclarés |
| Pylux | AGPL-3.0 avec la même exception OpenSSL | Bloqué en attente de décision |
| `hid-playstation.c` de Linux | GPL-2.0-or-later | Référence comportementale uniquement ; pas de copie sans revue |
| API/artefacts Android AOSP | Licence variable ; souvent Apache-2.0 pour les sources concernées | Utilisation des API autorisée ; tout code copié doit être revu |
| Graphify | Apache-2.0 selon la documentation du projet | Outillage uniquement ; aucune dépendance à l'exécution |
| `libfuzzer-sys` 0.4.13 | `(MIT OR Apache-2.0) AND NCSA` | Crate de fuzzing autonome réservée aux tests ; NCSA n’est autorisée que par une exception `cargo-deny` propre à cette crate |

chiaki-ng et Pylux incluent l'AGPL v3 avec une permission supplémentaire de liaison à OpenSSL et des clauses sur la source correspondante. [COPYING chiaki-ng](https://github.com/chiaki-ng/chiaki-ng/blob/main/COPYING), [COPYING Pylux](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/COPYING)

## Option A — réutiliser ou envelopper la famille Chiaki

Hypothèse : l'application distribuée et la source correspondante peuvent respecter l'AGPL-3.0 ainsi que toutes les obligations de mention et de fourniture des sources.

- Une enveloppe ABI C/FFI n'évite pas en soi les obligations du copyleft.
- La révision amont exacte, les correctifs, les scripts de build, les mentions et la source correspondante doivent être conservés.
- Cette voie présente le risque le plus faible pour la correction du protocole et le calendrier.

## Option B — réimplémentation en Rust

Une traduction mécanique, une réécriture ligne à ligne ou une adaptation d'un source AGPL reste exposée au risque d'œuvre dérivée. Renommer les types ou changer de langage n'établit pas l'indépendance.

Une voie défendable en salle blanche exigerait :

1. une spécification du comportement et du protocole produite sans copier l'expression de l'implémentation ;
2. un processus d'implémentation distinct, fondé sur cette spécification, sur des observations licites et sur des sources sous licence indépendante ;
3. la provenance de chaque constante, champ de paquet, algorithme et fixture ;
4. l'absence des noms, commentaires, structures et choix d'implémentation non évidents propres à l'amont, sauf s'ils sont dérivés indépendamment ;
5. une revue juridique des règles de rétro-ingénierie et d'interopérabilité dans les juridictions visées.

Cette voie est nettement plus lente et plus risquée, et ne peut pas se justifier par la seule préférence pour Rust.

## Option C — GPSx sous AGPL-3.0, portage Rust déclaré comme œuvre dérivée

Ajoutée le 2026-10-25 après revue indépendante ; l'audit initial omettait cette option.

GPSx est lui-même distribué sous AGPL-3.0-only avec la même permission supplémentaire OpenSSL. La logique de protocole amont est portée en Rust en tant qu'œuvre dérivée déclarée ouvertement, avec mentions de copyright, attribution et provenance fichier par fichier.

- **FACT :** les fichiers de chiaki-ng portent `LicenseRef-AGPL-3.0-only-OpenSSL`. GPSx ne peut donc pas utiliser « or-later » pour l'œuvre combinée.
- Correspond à la préférence pour Rust sans le coût d'une salle blanche, et garde le comportement amont comme référence directe.
- Les obligations sont les mêmes que pour l'option A : source correspondante pour chaque APK et build Windows distribué, mentions, scripts de build et offres de source.
- La permission OpenSSL ne compte que si OpenSSL est lié. Une pile Rust utilisant RustCrypto/rustls n'a pas besoin de l'exception, mais la conserver garde la possibilité de lier le C amont.
- La différence avec l'option A est uniquement d'ingénierie. L'option A garde le cœur C derrière une FFI, ce qui implique deux modèles de threads et une FFI `unsafe`. L'option C a un seul modèle de propriété Rust, mais porte un risque de correction de la traduction, atténué par les fixtures amont.

## Choisir entre les options

**FACT (déclaration du responsable, 2026-10-25) :** GPSx servira d'abord à l'usage personnel du responsable, sur son propre matériel. Plus tard, s'il fonctionne bien, il pourra être publié gratuitement sur GitHub.

Conséquences (lecture d'ingénierie, pas un avis juridique) :

- **Phase personnelle :** les obligations de l'AGPL-3.0 se déclenchent quand on transmet des copies à d'autres. La modification et l'usage privés sur ses propres appareils, sans distribution, ne déclenchent aucune obligation de fournir les sources. Le §13 (interaction à distance par réseau) concerne des utilisateurs qui interagissent avec un programme modifié via un réseau, pas un client privé.
- **Phase de publication :** une publication gratuite et publique sur GitHub sous AGPL-3.0-only (avec la permission OpenSSL) est entièrement compatible avec la famille Chiaki, à condition que les mentions, l'attribution, la provenance et la source correspondante soient présentes. Une source publique satisfait l'obligation de source pour les binaires publiés à côté.
- **L'option B (salle blanche) n'est donc pas justifiée :** son coût ne sert qu'à échapper à l'AGPL, ce dont ce modèle de distribution n'a pas besoin.
- **Recommandation :** l'option C (GPSx sous AGPL-3.0-only avec un portage Rust déclaré comme dérivé), qui correspond à la préférence pour Rust. L'option A reste valable si le calendrier compte plus que Rust.
- Tenir le registre de provenance dès le premier commit, pour que la publication future ne demande aucun audit rétroactif. Ajouter le fichier `LICENSE` et les mentions avant le premier push public.
- Les points sur les marques et le PSN ci-dessous deviennent pertinents au moment de la publication.

**Acceptée :** le responsable a choisi l’option C le 2026-10-25. La décision et la base amont épinglée sont consignées uniquement dans `decisions/0001-protocol-license-strategy.fr.md` (ADR-0001), seule source de vérité. Les couches protocole, transport et session seront donc des implémentations Rust avec provenance dérivée explicite.

## Considérations hors droit d'auteur

Signalements d'ingénierie uniquement, pas un avis juridique ; pas encore évalués :

- Marques Sony (PlayStation, PS5, DualSense, Remote Play) dans le nom de l'application, l'icône, la fiche du store et l'UI. Utiliser uniquement une formulation descriptive de compatibilité, en attendant une revue.
- Conditions d'utilisation du PlayStation Network, et flux OAuth utilisé pour obtenir l'identifiant de compte PSN nécessaire à l'enregistrement. Ce flux manipule des URL de redirection et des jetons contenant des secrets, jamais journalisés.
- Politique de distribution des stores pour un client Remote Play non officiel.

## Candidats tiers compatibles (pas encore adoptés)

| Composant | Licence | Compatibilité AGPL-3.0 |
|---|---|---|
| Oboe | Apache-2.0 | Compatible avec GPLv3/AGPLv3 (FACT, liste de compatibilité de la FSF) |
| libopus | BSD-3-Clause | Compatible |
| Crates RustCrypto | MIT/Apache-2.0 | Compatible |
| crates rust-secp256k1 / libsecp256k1 vendue | Métadonnées des crates : CC0-1.0 ; bibliothèque C vendue et patchée : MIT, Copyright (c) 2013 Pieter Wuille | Permissive ; la mention MIT figure maintenant dans `NOTICE`, tandis que le bundle complet de licences tierces/SBOM N1 reste requis avant toute distribution binaire |

Chacun doit encore figurer dans le registre de provenance et le SBOM avant adoption.

## Bibliothèques Android adoptées (section rétro)

| Composant | Licence | Usage |
|---|---|---|
| jcifs-ng 2.1.10 (`eu.agno3.jcifs:jcifs-ng`) | LGPL-2.1 | Lit les jeux du propriétaire sur un partage SMB ; bibliothèque JVM liée dynamiquement, compatible AGPL-3.0 |
| Bouncy Castle `bcprov` (apporté par jcifs-ng) | MIT | Primitives cryptographiques NTLM/SMB internes à jcifs-ng |
| API SLF4J (apportée par jcifs-ng) | MIT | Façade de journalisation, aucune implémentation embarquée |

Les cores d'émulation libretro ne sont **pas** distribués avec GPSx : l'application les télécharge à la demande du propriétaire (voir `RETROGAMING_PLAN.fr.md`).

## Dépendances

Le manifeste bureau de chiaki-ng inclut SDL2, protobuf, OpenSSL, json-c, libevent, miniupnpc, Opus, lcms, SpeexDSP et Vulkan. Chaque dépendance transitive épinglée nécessite son propre enregistrement de licence en cas de réutilisation. [Manifeste vcpkg chiaki-ng](https://github.com/chiaki-ng/chiaki-ng/blob/main/vcpkg.json)

La future pile Rust/Kotlin doit produire un SBOM et un rapport de licences en CI. Des bibliothèques cryptographiques éprouvées sont obligatoires ; la composition propre au protocole exige tout de même une revue de sécurité.

## Registre de provenance

Pour chaque artefact importé ou adapté, consigner :

- le dépôt source et la révision immuable ;
- le chemin d'origine et la mention de copyright ;
- l'identifiant SPDX et le texte de la licence ;
- le statut : copie, correctif, enveloppe, artefact généré ou salle blanche ;
- les modifications et le responsable local ;
- les obligations de mention dans les binaires et de fourniture des sources ;
- le relecteur et la date d'approbation.

## Décision de Phase 0

L’option C est acceptée. Les revues CR3 ont fermé les gates de provenance, parsing, cryptographie et fixtures ; la décision de base Android du 2026-10-28 autorise le contrat de compatibilité épinglé consigné dans `PROVENANCE.fr.md`. Tout nouveau chemin ou nouvelle révision amont exige encore une mise à jour du registre et l’audit final.
