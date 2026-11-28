# Sécurité des jouets : rendre les cartes, les étuis et le présentoir conformes

Note du 28 novembre 2026, tenue par la skill `conformite-jouet`
(`.claude/skills/conformite-jouet/`). À la racine, elle n'est publiée ni sur
le site ni dans l'APK. **Ce n'est pas un avis juridique** : rien n'a été lu
dans les textes officiels ni vérifié en ligne ce jour-là. Chaque point se fait
confirmer par le laboratoire d'essais, l'autorité de surveillance du marché et
un juriste avant d'imprimer le marquage CE.

Voir aussi : [`BOUTIQUE-CARTES.md`](BOUTIQUE-CARTES.md) § 8.5 (les
obligations avant d'ouvrir la vente), [`MODELE-ECONOMIQUE.md`](MODELE-ECONOMIQUE.md)
§ 4 (le coût des essais), [`BOUTIQUE-SHOPIFY.md`](BOUTIQUE-SHOPIFY.md) (ce que
la page de vente doit afficher).

---

## 1. La question à trancher d'abord : est-ce un jouet ?

La directive 2009/48/CE (sécurité des jouets) s'applique à un produit **conçu
ou destiné à être utilisé par des enfants de moins de 14 ans**, même s'ils
n'en sont pas le seul public. C'est la présentation qui compte, pas seulement
l'âge imprimé : des illustrations d'animaux (Hond, Kaz), un jeu de bataille,
des animations dans les écoles et les maisons relais font du Wuertduell un
jouet. Écrire « dès 16 ans » sur l'étui ne suffirait pas à l'en sortir.

**Recommandation : assumer le statut de jouet.** C'est le public visé.

**Le dépôt n'est pas cohérent sur l'âge** (relevé le 2026-11-28) :

| Endroit | Ce qu'il dit |
|---|---|
| `docs/commander.md` (lignes 54, 102-103, 145) | dès 6 ans, Wuertduell dès 8 ans |
| `docs/regle-du-jeu.html` | plus d'âge (retiré le 2026-11-28) |
| `scripts/emballage.py` | âge retiré de la face et du côté |
| skill `creer-presentoir` § 4 | Bataille dès 6 ans, Wuertduell dès 8 ans |
| skill `imprimeur` § 9 | jouet dès 6 ans |

À décider par le propriétaire : l'âge (ou les âges) annoncé(s). L'évaluation
de la sécurité (§ 4) se fait pour cet âge, et tous ces endroits s'alignent
ensuite.

## 2. Ce que vérifie la norme EN 71

| Partie | Ce qu'elle vérifie | Pour le Wuertduell |
|---|---|---|
| **EN 71-1** (propriétés mécaniques et physiques) | bords coupants, pointes, petites pièces, emballage | Cartes 63 × 88 mm à coins arrondis de 3 mm : pas de risque attendu. À vérifier : la découpe et les rabats de l'étui, le film plastique s'il y en a un. |
| **EN 71-2** (inflammabilité) | vitesse de combustion | Le carton passe en général sans difficulté. |
| **EN 71-3** (migration de certains éléments) | métaux lourds qui passent des encres, vernis, pelliculages et dorures | **Le seul vrai point.** Tout ce qui recouvre le carton : encres CMJN, pelliculage toilé, dorure à chaud ou vernis sélectif des cartes rares. |

Les autres parties (jouets chimiques, aires de jeux, etc.) ne concernent pas
un jeu de cartes.

## 3. Les matériaux : choisir un imprimeur qui fournit les certificats

- **Exiger les certificats EN 71-3** du carton, des encres et de chaque
  finition dans toute demande de devis (paragraphe prêt au § 9).
- **Pas de finition sans certificat.** Une dorure ou un vernis qui n'en a pas
  s'enlève, ou se fait tester en plus.
- **Même carton, mêmes encres, même étui pour les quatre boîtes** : c'est ce
  qui permet, sans doute, un seul dossier d'essais pour les quatre (à faire
  confirmer par le laboratoire).
- Un changement d'imprimeur, de carton ou de finition **relance les essais**.

## 4. Les essais et les papiers (c'est l'asbl, « fabricant », qui signe)

Celui qui met le jouet sur le marché sous son nom en est le **fabricant**,
même si un imprimeur le fabrique. Ce sera l'asbl (skill `asbl-administratif`).

1. **Évaluation de la sécurité** : quelques pages qui passent en revue les
   risques chimiques, physiques et mécaniques, d'inflammabilité et d'hygiène,
   pour l'âge choisi au § 1, et disent pourquoi chacun est maîtrisé.
2. **Essais en laboratoire** sur le produit fini : une boîte complète (cartes
   et étui) sortie de la vraie production de l'imprimeur, pas un prototype
   fait ailleurs. EN 71-1, -2 et -3. Laboratoires à interroger : SGS, TÜV,
   Bureau Veritas, Intertek, ou celui que l'imprimeur a l'habitude
   d'utiliser.
3. **Dossier technique** : description du produit, fichiers d'impression,
   coordonnées de l'imprimeur, certificats des matériaux, rapports d'essais,
   évaluation de la sécurité. **Se garde 10 ans après la mise sur le marché**,
   **hors du dépôt public** (rangement : skill `asbl-administratif`).
4. **Déclaration UE de conformité**, signée par l'asbl, une copie dans le
   dossier technique.

## 5. Ce que porte l'étui

- le **marquage CE**, visible, lisible, indélébile ;
- le **nom, l'adresse postale et une adresse e-mail** du fabricant (directive
  jouets et règlement GPSR) ;
- un **numéro de lot** (ou de type) qui permet de retrouver le tirage ;
- les **avertissements** que l'évaluation de la sécurité juge utiles. Sans
  petites pièces, l'avertissement « ne convient pas aux enfants de moins de
  36 mois » n'est sans doute pas nécessaire ;
- dans **la langue du pays de vente** : français et allemand au Luxembourg,
  allemand en Allemagne, français en France, français et néerlandais en
  Belgique (à confirmer pays par pays).

Dans le dépôt, `scripts/emballage.py` a déjà `EDITEUR`, `PAYS_FABRICATION` et
`MARQUAGE_CE = False` en tête. **Il manque un paramètre pour le numéro de
lot.** Règle « rien de provisoire » (skill `creer-presentoir` § 4) : le CE ne
s'imprime qu'une fois les essais réussis et le dossier signé.

Le film plastique éventuel autour de l'étui suit les règles de l'emballage
d'EN 71-1 : à vérifier avec le laboratoire (épaisseur, ou film qui se détruit
à l'ouverture).

## 6. Ce que montre la page de vente

Avant l'achat, la page affiche : le marquage CE, les avertissements, le nom et
les adresses du fabricant (champ `fabricant:` de `docs/commander.md`, et
`docs/precommande*.html`). Une photo de l'étui lisible suffit souvent pour le
CE, mais les avertissements s'écrivent aussi en texte.

## 7. Le présentoir n'est pas un jouet

C'est un meuble de magasin, pas un objet pour jouer : EN 71 ne s'y applique
pas. Il doit seulement être sûr au sens général (règlement GPSR) : stable sur
un comptoir chargé, sans arête coupante, carton propre au contact. C'est
l'affaire du fabricant de PLV (skill `creer-presentoir`). Ce sont les étuis
qu'il contient qui portent le CE.

Les produits dérivés (`MERCHANDISING.md`) : un mug, un carnet, un calendrier
pour adultes ne sont pas des jouets (sécurité générale seulement, et contact
alimentaire pour le mug). L'**enveloppe du mois** et le **poster à gratter**
pourraient l'être s'ils visent aussi les enfants : à évaluer le jour où ils
sont lancés.

## 8. Ce qui n'en a pas besoin aujourd'hui

- **Les cartes des animations**, utilisées puis reprises, ne sont pas mises sur
  le marché. Les animations peuvent commencer avant les essais.
- **Les réservations sans paiement** ne mettent rien sur le marché.
- En revanche, **distribuer ou vendre une seule boîte**, même offerte à une
  tombola ou laissée à une école, demande que tout le § 4 et le § 5 soient en
  place.

À surveiller : un nouveau **règlement européen sur la sécurité des jouets**,
adopté fin 2025, remplacera la directive dans quelques années, avec un
**passeport numérique du produit**. Il concernera les tirages suivants.

## 9. Paragraphe pour les demandes de devis (brouillon)

> Ce jeu de cartes est un jouet au sens de la directive 2009/48/CE. Merci de
> joindre à votre devis les certificats EN 71-3 du carton, des encres, du
> pelliculage et de chaque finition proposée (dorure, vernis), et de nous dire
> si vous pouvez fournir des exemplaires de production pour des essais
> EN 71-1, -2 et -3, ou les faire réaliser par un laboratoire avec lequel vous
> travaillez, et à quel prix.

Version allemande et anglaise à écrire si l'imprimeur choisi l'exige (texte
écrit par Claude : à faire relire).

## 10. Liste à cocher, dans l'ordre

1. [ ] Âge annoncé décidé par le propriétaire (§ 1), puis aligné partout.
2. [ ] asbl créée : c'est elle le fabricant (skill `asbl-administratif`).
3. [ ] Devis d'imprimeurs avec certificats EN 71-3 (§ 3, § 9).
4. [ ] Imprimeur, carton et finitions choisis et figés.
5. [ ] Devis de 2 laboratoires ; confirmer qu'un dossier couvre les quatre
       boîtes.
6. [ ] Exemplaires de production envoyés au laboratoire ; rapports reçus.
7. [ ] Évaluation de la sécurité écrite ; dossier technique complet, rangé
       hors du dépôt.
8. [ ] Déclaration UE de conformité signée.
9. [ ] `scripts/emballage.py` : `EDITEUR`, `PAYS_FABRICATION`, numéro de lot,
       `MARQUAGE_CE = True` ; BAT relu.
10. [ ] Page de vente : CE, avertissements, fabricant.
11. [ ] Présentoir : stabilité vérifiée avec le fabricant de PLV.

## 11. Ce qui reste à confirmer

| Point | Statut | Qui confirme |
|---|---|---|
| Autorité de surveillance du marché des jouets au Luxembourg | sans doute l'ILNAS, non vérifié | guichet.lu, ILNAS |
| Langues obligatoires des avertissements par pays | supposées (§ 5) | autorité de chaque pays, laboratoire |
| Prix des essais | 300 à 800 €, hypothèse (`MODELE-ECONOMIQUE.md` § 4) | devis de laboratoires |
| Un seul dossier pour les quatre boîtes | probable | laboratoire |
| Film plastique de l'étui | à vérifier | laboratoire |
| Date d'application du nouveau règlement | « dans quelques années » | Journal officiel de l'UE |
