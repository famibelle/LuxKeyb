# Produits dérivés de l'appli et des cartes : notes

Idées travaillées le **28 novembre 2026** avec le propriétaire. Rien n'est
décidé ni chiffré : ce sont des pistes à reprendre. À la racine, ces notes ne
sont publiées ni sur le site ni dans l'APK.

Les maquettes sont sur une toile de design privée (trois pages, une par
série) : <https://claude.ai/artifact/8NXLdekXDibxDzQLjmcPpm>. Pour la montrer à
quelqu'un, il faut la partager depuis son menu Partager.

---

## 1. Le principe retenu

Le propriétaire a écarté deux premières séries, pour deux raisons à garder en
tête pour tout objet futur :

1. **Un objet qui ne fait que montrer le logo ne sert à rien.** Le lion sur un
   mug ou un t-shirt ne répond pas au besoin de la personne qui apprend.
2. **Les mots génériques n'apprennent rien.** « Moien », « Merci », « Brout » :
   tout le monde les connaît déjà. Les gens viennent d'apprendre des mots ;
   l'objectif est **un mot nouveau chaque jour**.

Donc chaque objet doit :

- **apporter un mot nouveau chaque jour**, ou **garder la trace des mots
  appris** ;
- prendre ses mots dans **les 208 mots des quatre boîtes du Wuertduell**
  (`android_keyboard/app/src/main/assets/cartes_imprimees.json`), du plus
  simple à l'A2, avec l'article, le genre, le pluriel, le sens et une phrase
  d'exemple du LOD (`luxemburgish_exemples.json`,
  `luxemburgish_categories.json`, `luxemburgish_translations.json`) ;
- renvoyer vers **l'une des deux actions** : installer le Clavier (QR code,
  « Mot du jour » de l'onglet « Mäi Lëtzebuergesch ») ou avoir les cartes
  (les mots du jeu donnent envie des boîtes).

## 2. La série retenue : « Un mot nouveau chaque jour »

### Un mot nouveau chaque jour, sous les yeux

| Objet | Ce qu'il fait | Lien |
|---|---|---|
| **Calendrier éphéméride, 365 mots** | Une feuille par jour : le jour et la date en luxembourgeois (« Samschdeg, 28. November »), le mot avec son article, genre, pluriel, sens, une phrase du LOD et sa traduction. Une bande de couleur dit de quelle boîte vient le mot (rouge départ, vert extension, bleu A1, violet A2). Un aperçu de « Muer » (demain). Progression : boîte de départ en début d'année, A2 à la fin. | Cartes et appli (QR) |
| **Bloc « Wuert vum Dag »** | 365 notes repositionnables numérotées (76 × 76 mm). On colle celle du jour sur l'écran, le frigo ou le miroir, puis dans son carnet le soir. | Cartes |
| **Mug ardoise « Haut léieren ech: »** | « Aujourd'hui j'apprends : » et la place pour écrire à la craie le mot du jour de l'appli ou du calendrier. Livré avec un feutre craie. | Appli (mot du jour) |

### Voir les mots appris s'accumuler

| Objet | Ce qu'il fait | Lien |
|---|---|---|
| **Poster à gratter « 208 Wierder »** | Les 208 mots des boîtes sous une pellicule argentée, en quatre blocs de 52 aux couleurs des boîtes. On gratte un mot quand on le connaît ; compteur « … / 208 geléiert ». Les cases grises donnent envie de la boîte qui va avec. 50 × 35 cm. | Cartes |
| **Carnet « Mäi Wierderbuch »** | A5, 52 semaines. Une ligne par jour : le mot, son sens, « Ma phrase ». Une page de révision chaque dimanche (cacher le sens, cocher les mots retenus). | Appli (mot du jour, QR) |
| **Enveloppe du mois « Wierder vum Mount »** | Abonnement : chaque mois, 30 cartes nouvelles autour d'un thème (ex. décembre : l'hiver et les fêtes), une par jour, et un code qui les ajoute au carnet de l'appli. | Cartes et appli |

Mots utilisés dans les maquettes (genres vérifiés dans le LOD) : d'Feierblumm,
de Gletscher, d'Hieselnoss (pl. Hieselnëss), de Kamillentéi, de Bongert,
de Paangech, d'Harespel, d'Schmuewel, den Donner, den Hond.

## 3. Les séries écartées (pour mémoire)

**Série 1, « logo » (écartée) :** mug blanc au lion, t-shirt bleu nuit
« Ech schreiwe Lëtzebuergesch » avec les touches ë é ä, tote bag « Moien! »,
planche de stickers, mug bleu nuit avec la carte Kaz, t-shirt des trois cartes
(Hond, Kaz, Kuch), tapis de jeu en néoprène, porte-clés carte en acrylique.
Raison : met en avant le lion, n'apprend rien.

**Série 2, « au quotidien » (écartée) :** chaque objet partait d'une
difficulté (« on me répond en français », « je ne sais pas quoi dire au
comptoir »…), mais avec des mots trop génériques.

- Badges « Schwätzt Lëtzebuergesch mat mir! Lues, wann ech gelift. » (bleu,
  pour l'apprenant) et « Ech schwätze Lëtzebuergesch mat Iech. » (blanc, pour
  le comptoir).
- T-shirt « Ech léieren nach. » devant, la demande et le QR code dans le dos.
- Mug « Am Café » (quatre phrases pour commander), tote bag « Um Maart »
  (liste de courses), étiquettes de la maison (mot, article, genre en
  couleur), aimant de la règle du n (den Hond / de Kaffi, en Apel / e Kuch,
  si kommen haut / si komme muer), marque-page sinn et hunn.

Pistes qui pourraient revenir sous une autre forme : les **badges** (le besoin
« qu'on me réponde en luxembourgeois » reste vrai), l'**aimant de la règle du
n** et le **marque-page glissé dans chaque boîte** (il relie les cartes à
l'appli).

## 4. Ce qui reste à décider

- **Les 365 mots du calendrier et du bloc.** Les quatre boîtes en donnent 208.
  D'où viennent les 157 autres : liste A1 de l'appli, futures boîtes, autre ?
- **L'enveloppe du mois** suppose de produire des cartes au-delà des quatre
  boîtes et un code par enveloppe : projet à part entière, à chiffrer (skills
  `business-modele` et `imprimeur`).
- **Les prix** : toutes les maquettes disent « Prix à fixer ».
- **La vente** : pas avant l'asbl (voir `CAMPAGNE-FINANCEMENT.md` et le skill
  `asbl-administratif`). Certains objets pourraient devenir des contreparties
  de la campagne de financement participatif.
- **Relecture du luxembourgeois** par une personne qui parle la langue, avant
  toute impression : les phrases d'exemple viennent du LOD, mais leurs
  traductions et les en-têtes (« Haut léieren ech », « Wuert vum Dag »,
  « Mäi Wierderbuch », « Wierder vum Mount », « 208 Wierder », « Muer »,
  « Woch 48 ») ont été écrits par Claude.
