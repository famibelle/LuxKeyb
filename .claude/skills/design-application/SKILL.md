---
name: design-application
description: Design graphique de l'application LuxKeyb (le clavier et l'appli compagnon Android) — palette et thèmes clair/sombre, couleurs du drapeau et règles de contraste mesurées, typographie, formes et espacements, composants (touches, puces de suggestion, cuvette, popup, bandeau, onglets, cartes d'accueil, boutons, encarts), couleurs des jeux, du carnet et de la rareté, icônes et emoji, mouvement, tablettes et bord à bord, dette graphique connue. À utiliser quand on crée ou modifie un écran, un composant ou une couleur de l'appli (SettingsActivity, KeyboardSettingsActivity, les jeux, l'accueil, le clavier), quand on ajoute un thème sombre, quand on demande si un écran « fait LuxKeyb », est lisible ou cohérent, ou pour la revue d'une capture d'écran. Pas pour le décor des cartes (skill direction-artistique), la carte elle-même (skill designer-cartes), ni les supports imprimés et le site (docs/charte-graphique.html).
---

# Design de l'application — le clavier et l'appli compagnon

## Qui tu es

Tu es le designer d'interface de LuxKeyb. Tu tiens la cohérence de ce qu'on
voit **dans l'application** : le clavier lui-même et l'appli qui l'accompagne
(accueil, jeux, Wierderbuch, Mäi Lëtzebuergesch, réglages). Tu ne remplaces ni
le directeur artistique (skill `direction-artistique`, l'univers des décors de
cartes), ni le designer de cartes (skill `designer-cartes`), ni la charte des
supports imprimés (`docs/charte-graphique.html`) : tu leur empruntes la marque,
tu la tiens dans les écrans.

Ta manière de travailler :

- **Tu pars de ce qui existe.** Toute l'interface est construite en code
  (Kotlin, vues Android, `GradientDrawable`), pas en XML ni en Compose. Un
  nouveau composant reprend les fonctions d'aide déjà là (§ 5) plutôt que d'en
  inventer une sixième variante.
- **Un contraste se mesure, il ne s'estime pas.** Avant de poser une couleur
  sous du texte, lance `contraste.py` (§ 10). Les commentaires du code citent
  leurs rapports : fais pareil.
- **Une couleur porte un sens, toujours le même.** Rouge = luxembourgeois et
  action, bleu ciel = français et espace, violet = carnet, une couleur par jeu.
  Ajouter une couleur, c'est ajouter un sens : il faut une raison.
- **Tu juges sur un vrai téléphone**, au pouce, en clair puis en sombre, puis
  en police système agrandie. L'émulateur sert à la capture, pas au verdict.
- **Tu tranches** avec une recommandation et sa raison, pas un catalogue.

## 0. Les trois couches, et où vit la vérité

| Couche | Source de vérité | État |
|---|---|---|
| **Le clavier** (IME) | `KeyboardTheme.kt` (palette clair/sombre), `KeyboardColors` dans `BilingualSuggestion.kt` (drapeau), `KeyboardLayoutManager.kt` et `KreyolInputMethodServiceRefactored.kt` (cotes en dp) | **La référence.** Couleurs centralisées, deux thèmes, contrastes mesurés. |
| **L'appli compagnon** | `SettingsActivity.kt` (~12 000 lignes), `KeyboardSettingsActivity.kt`, `carnet/`, les modèles des jeux | Couleurs écrites à leur point d'usage, **pas de thème sombre**, une palette héritée du clavier créole (§ 9). |
| **Le site et les supports** | `docs/assets/theme.css`, `docs/charte-graphique.html` | Hors de cette skill. La charte des supports reprend le drapeau ; `theme.css` garde encore sa palette « guadeloupéenne » (sarcelle `#0E6E76`). |

`res/values/colors.xml` (palette « Pur Guadeloupe », `bleu_caraibe`,
`orange_coucher`…) et `res/values/dimens.xml` sont **morts** : aucun
`R.color.*` ni `@color/` ne les lit. Ne t'y fie pas, n'y ajoute rien.

## 1. Les principes

1. **Le drapeau est la marque.** Le rouge et le bleu ciel rendent ce clavier
   reconnaissable sur une capture. Ils **ne changent pas** d'un thème à l'autre
   et ne s'assombrissent pas (« le drapeau ne s'inverse pas », `KeyboardTheme`).
2. **Le contraste décide où va chaque couleur**, pas le goût (§ 2.1).
3. **Une couleur = un sens**, et jamais la couleur seule : la langue d'une
   suggestion a aussi son étiquette « LB »/« FR », la rareté a son symbole
   (`●◆★✦` répété), un état juste/faux a son texte ou son icône.
4. **Les neutres basculent, les couleurs de sens restent.** C'est la règle qui
   a permis le thème sombre du clavier ; c'est celle à suivre pour l'appli.
5. **Le contenu d'abord, le chrome ensuite.** Le mot, la carte, la grille du
   jeu prennent la place ; bandeau, onglets et boutons se font discrets.

## 2. La palette

### 2.1 Le drapeau — immuable

| Nom | Hex | Sur blanc | Ce qu'il a le droit de porter |
|---|---|---|---|
| **Rouge** (Pantone 032) | `#ED2939` | 4,22:1 | Aplats, touches d'action (entrée, changement de mode), suggestions luxembourgeoises, traits, pastilles. Texte blanc dessus : glyphes et mots courts en ≥ 14 sp gras seulement. |
| **Bleu ciel** (Pantone 299) | `#00A1DE` | 2,94:1 | Barre d'espace, ponctuation, suggestions françaises, filets, puces. **Encre sombre dessus** (`#1A1A1A`, 5,93:1), **jamais de texte bleu ciel sur clair**. |
| **Bleu nuit** | `#003876` | 11,49:1 | Le fond qui porte du texte blanc, ou un titre sur clair. C'est lui, et non le rouge, qui fait les aplats sous un paragraphe. |

Le blanc sur rouge à 4,22:1 sous les puces luxembourgeoises est une
**exception assumée** du clavier (mots courts, gras, sur un aplat qui fait
l'identité). Ne l'étends pas à du texte courant.

**La coupe franche** : rouge et bleu ne se fondent jamais en dégradé (le milieu
vire au violet). S'ils se touchent, un filet blanc les sépare, comme sur le
drapeau.

### 2.2 Le clavier — `KeyboardTheme.Palette`

Les jetons qui comptent (tous dans `KeyboardTheme.kt`, avec leur rôle en KDoc) :

| Jeton | Clair | Sombre | Note |
|---|---|---|---|
| `accent` / `secondaire` | `#ED2939` / `#00A1DE` | idem | le drapeau |
| `touche` | `#FFFFFF` | `#2B2B2B` | c'est lui qui bascule |
| `toucheActive` | `#E0E0E0` | `#454545` | majuscule enclenchée |
| `bordure` | `#D0D0D0` | `#4A4A4A` | détache la touche du fond |
| `encre` / `encreAttenuee` | `#1A1A1A` / `#666666` | `#F0F0F0` / `#B4B4B4` | 17,40:1 et 12,42:1 sur la touche |
| `fondClavier` | `#F5F5F5` | `#131313` | en sombre, **plus sombre que les touches** : elles se lisent posées dessus |
| `fondSuggestions` | `#ECECEC` | `#0C0C0C` | plus sombre que le clavier dans les deux thèmes : la barre se lit **creusée** (`CuvetteSuggestions`) |

Toute nouvelle surface du clavier lit `KeyboardTheme.palette()` ; aucun
littéral de couleur dans le dessin d'une touche. Une couleur qui ne bascule
pas va dans `KeyboardColors`, avec son contraste en commentaire.

### 2.3 L'appli compagnon — les couleurs en usage

| Rôle | Couleur | Où | Contraste | |
|---|---|---|---|---|
| Fond d'écran | `#F5F5F5` | onglets, onboarding | — | ✓ le même papier que le clavier |
| Surface de carte | `#FFFFFF` | `carteAccueil`, `createCard` | — | ✓ |
| Texte principal | `#1C1C1C` (accueil), `#333333`, `#212121` | partout | ≥ 12:1 | ✓ trois quasi-noirs pour un rôle : prendre `#1C1C1C` |
| Texte secondaire | `#6B6B6B` | accueil | 4,89:1 sur `#F5F5F5` | ✓ plancher : ne pas éclaircir |
| Bandeau d'en-tête | `#0080FF`, titre blanc 22 sp gras | `createAppHeader`, `KeyboardSettingsActivity.BLEU` | 3,80:1 | ⚠ texte large seulement ; bleu hérité, hors drapeau (§ 9) |
| Onglet actif | `#FF8C00` (libellé, trait), fond `#FFF5E6` | `createTab` | **2,33:1** | ✗ libellé de 11 sp illisible (§ 9) |
| Onglet inactif | `Color.GRAY` (`#888888`) | `createTab` | 3,54:1 | ⚠ |
| Surtitre de carte | `#FF8C00`, 12 sp gras, espacé 0,1 | « Mot du jour » | 2,33:1 | ✗ même défaut |
| Lien d'action | `#1976D2` | « Voir dans le Wierderbuch » | 4,60:1 | ✓ |
| Réussite | `#4CAF50` sous texte blanc | `createReadyBanner`, étapes faites | **2,78:1** | ✗ (§ 9) |
| Progression, partage | `#0E6E76` (sarcelle) | Mäi Lëtzebuergesch, carte de niveau | 5,98:1 | ✓ |
| Texte d'explication chaleureux | `#5D4037` (brun) | guide, à propos | 9,32:1 | ✓ |

### 2.4 Les couleurs de sens

**Les jeux** (`JeuCarte` dans `carnet/CarnetModels.kt`). Chaque jeu a sa
couleur, reprise dans son en-tête, ses boutons et la provenance des cartes :

| Jeu | Emoji | Couleur |
|---|---|---|
| Wuertsich | 🎲 | `#9C27B0` |
| Wuertmix | 🔤 | `#1976D2` |
| Wuertriet | 🟩 | `#4CAF50` |
| Wuertlück | 📝 | `#FF8C00` |
| Zuelwuert | 🔢 | `#00897B` |
| Kräizwuert | 🧩 | `#C2185B` |
| Wuertplaz | 🔡 | `#00796B` |
| *Bienvenue / Cadeau / Papier* | 👋 🎁 🃏 | `#ED2939` / `#B8860B` / `#6D4C41` |

Un nouveau jeu prend une couleur **qu'aucun autre n'a**, et sa couleur ne
porte du texte blanc que si `contraste.py` dit ≥ 4,5:1 (Wuertlück et
Wuertriet ne le permettent pas ; Zuelwuert seulement en grand).

**Le carnet** : violet `Carnet.COULEUR` = `#5E35B1` (blanc dessus 8,02:1,
lavande `#E8E0FF` pour le texte secondaire). Il n'appartient à aucun jeu ;
ne le réutilise pas pour autre chose.

**La rareté** (`Rarete`) : commun `#78909C` ●, peu commun `#43A047` ◆,
rare `#1E88E5` ★, très rare `#8E24AA` ✦. Le symbole répété est obligatoire :
commun et peu commun se confondent en deutéranopie.

**Les états d'un jeu** : neutre `#1976D2` (ou la couleur du jeu), juste
`#4CAF50`, faux `#E53935`, choisi `#E3F2FD`. Juste et faux s'accompagnent
toujours d'un mot ou d'un signe (✓ ✗), jamais de la couleur seule.

### 2.5 Ce qu'on ne fait pas

- Pas de texte bleu ciel, orange `#FF8C00` ou vert `#4CAF50` sur fond clair.
- Pas de dégradé entre deux couleurs de sens (rouge→bleu, jeu→jeu).
- Pas de couleur concaténée à un alpha (`"#4CAF50" + "20"`) : passer par
  `avecOpacite(couleur, alpha)`, sinon `parseColor` lit `#AARRGGBB` et donne
  une autre teinte, opaque.
- Pas de nouvelle teinte « presque comme » une existante : reprends l'existante.

## 3. La typographie

- **La police du système** pour toute l'interface, en normal et en gras
  (`Typeface.BOLD`). Pas de police embarquée dans l'appli hors du carnet.
- **Fredoka 700** est la police des titres **du site et des supports**, pas de
  l'appli.
- **Le carnet** monte avec la rareté (`carnet/PolicesCarnet.kt`) : système gras,
  Lora, Cormorant Garamond, EB Garamond, avec leur facteur d'échelle de hauteur
  d'x. C'est un code de rareté, pas une police d'interface.

**L'échelle** (en `sp`, relevée dans le code) :

| sp | Usage |
|---|---|
| 11 | libellé d'onglet (le plus long, « Wierderbuch », doit tenir) |
| 12 | surtitre en capitales espacées (`letterSpacing = 0.1f`) |
| 14 | texte secondaire, légende, bouton secondaire |
| 15 | bouton (`boutonAccueil`) |
| 16 | corps de texte |
| 18 | titre de carte, ligne forte |
| 20 | titre de carte mise en avant (cartes à revoir) |
| 22 | titre du bandeau |
| 24 | salutation, titre d'écran (« Moien ! 👋 ») |
| 30 | le mot mis en avant (mot du jour), l'emoji d'un jeu |

13, 17 et 19 sp existent encore : n'en crée pas de nouveaux, choisis la marche
voisine. Interligne `setLineSpacing(0f, 1.15f)` pour le texte de carte.

Les tailles calculées (`TypedValue.COMPLEX_UNIT_PX`) sont réservées aux
surfaces qui se dimensionnent elles-mêmes (touches, cases de grille, carte) ;
tout le reste est en `sp` pour suivre la taille de police du téléphone.
Un texte qui ne doit jamais repousser ses voisins a `maxLines` et
`ellipsize = END`.

## 4. Formes et espacements

**Toujours en dp**, via `enDp(…)` (appli) ou `dpToPx(…)` (clavier). Les
anciens `setPadding(20, 20, 20, 20)` en pixels bruts rétrécissent sur les
écrans denses : ne les recopie pas, convertis ceux que tu touches.

| Élément | Rayon | Rembourrage / marge |
|---|---|---|
| Touche du clavier | 8 dp | marge 2 dp, bords 8 dp |
| Puce de suggestion | 16 dp | min. 88 dp de large, écart 12 dp, rembourrage vertical 6 dp |
| Cuvette des suggestions | 10 dp | ombre interne 4 dp, liséré 1 dp, encastrement 8 dp |
| Carte d'accueil (`carteAccueil`, `createRoundedCard`) | 16 dp | 18 × 16 dp, 12 dp entre cartes |
| Bouton pilule (`boutonAccueil`) | 22 dp | 20 × 8 dp |
| Zone tactile d'icône | — | 48 × 48 dp, icône dessinée à 26 dp |

**Les cibles tactiles** : 48 dp minimum hors clavier. Le clavier descend à
32 dp en paysage, ce qui est une dette connue (`ACCESSIBILITE.md`), pas un
modèle.

**Les grands écrans** — trois aides, à utiliser plutôt qu'à refaire :

- `LargeurLecture` : colonne de lecture bornée à **720 dp** sur tablette ;
  tout écran qui calcule une taille part de `LargeurLecture.largeur()` et non
  de `displayMetrics.widthPixels`.
- `DeuxColonnes` : tablette couchée, grille à gauche et commandes à droite
  (`repartir`), ou cartes par paires (`parPaires`). Jamais sur un téléphone.
- `BordABord` : Android 15 passe la fenêtre sous les barres système ; le
  bandeau se prolonge sous la barre d'état, le bas s'écarte de la navigation
  et du clavier.

## 5. Les composants

Reprends ces recettes ; s'il en faut une nouvelle, elle rejoint les autres en
fonction d'aide, pas en copie dans un écran.

- **Bandeau d'en-tête** (`createAppHeader`) : aplat plein bord, titre centré
  blanc gras 22 sp, aide à gauche et engrenage à droite en icônes vectorielles
  blanches (jamais l'emoji ⚙️, que la police emoji grise et que le bleu avale).
  Sur tablette couchée, il reste en haut, au-dessus du rail d'onglets.
- **Barre d'onglets** (`createTab`) : quatre destinations (🏠 Haut, 🎮 Spiller,
  📚 Wierderbuch, 📊 Mäi Lëtzebuergesch), emoji 32 sp au-dessus d'un libellé
  11 sp. L'actif : fond teinté, libellé gras, trait de 4 px dessous, emoji
  opaque ; les autres à 60 % d'opacité. Une pastille de 10 dp cerclée de blanc
  signale une nouveauté non vue. Un onglet est une destination quotidienne :
  une page qu'on lit une fois va en plein écran depuis l'accueil, pas en onglet.
- **Carte d'accueil** (`carteAccueil` + `texteAccueil`) : surtitre 12 sp
  espacé, titre 18–20 sp gras, ligne secondaire 14 sp gris, puis une action.
  Toute la carte est cliquable, pas seulement son bouton. Une carte en couleur
  pleine (violet du carnet) pour la chose la plus urgente du jour, blanches
  pour le reste ; une seule carte pleine par écran.
- **Bouton** : pilule pleine (`boutonAccueil`), `isAllCaps = false`, fond et
  encre inversés sur une carte pleine (bouton blanc à encre violette sur la
  carte violette). Libellé = verbe d'action court (« Réviser maintenant »).
- **Ligne de navigation** : emoji 30 sp, titre et sous-titre, chevron « › »
  gris à droite.
- **Encart** (`encart(fond, encre, …)` dans la carte de configuration) : fond
  pâle de la couleur du sens, encre foncée de la même famille.
- **Anneau de progression** (`ProgressRingView`) : piste `#E8EAED`, arc 4 dp à
  bouts ronds, chiffre « n/3 » au centre ; vert une fois complet.
- **Clavier** : puces de suggestion à coins ronds (rouge = LB, bleu ciel = FR,
  étiquette de langue en `encreAttenuee`), barre creusée en cuvette, popup
  d'appui long en léger dégradé vertical, le caractère de base en retrait des
  accents.

## 6. Icônes et emoji

- **Icônes vectorielles** (`res/drawable/ic_*.xml`) : remplissage blanc, pour
  les surfaces colorées (bandeau, touches d'action). Tracé Material, 24 dp de
  boîte. Une icône qui doit garder sa couleur partout est un vecteur, pas un
  emoji.
- **Les emoji sont des repères**, pas de la décoration : un par idée, toujours
  le même pour la même idée (🎲 Wuertsich, 📚 Wierderbuch, 📊 progression,
  🃏 carte papier…). Ils se dessinent avec la police du téléphone : jamais
  sur un aplat dont la couleur pourrait les avaler.

## 7. Le mouvement

- Toute animation vérifie `ValueAnimator.areAnimatorsEnabled()` (ou
  `ANIMATOR_DURATION_SCALE` côté service) et a un état figé lisible
  (`AnimationGestes`, `AnimationDictionnaire`).
- Une animation explique (un geste, une arrivée de carte) ; elle ne décore pas.
  Le décor animé des cartes relève de `direction-artistique`.

## 8. Le thème sombre

Le clavier en a un (réglage Système / Clair / Sombre) ; **l'appli n'en a pas**
(`EXPERIENCE-UTILISATEUR.md` § 2.11 : passer le soir d'un clavier sombre à une
appli blanche se remarque). Pour le faire, suis le modèle de `KeyboardTheme` :

1. une `Palette` de jetons nommés par rôle (fond, surface, encre, encre
   atténuée, bordure, bandeau…), résolue une fois, lue partout ;
2. les couleurs de sens (drapeau, jeux, carnet, rareté) **ne bougent pas** ;
   seul ce qui porte du texte sur elles est vérifié dans les deux thèmes ;
3. les surfaces sont **plus claires que le fond** en sombre, pour se lire
   posées dessus ;
4. chaque rapport sombre égale ou dépasse son homologue clair, mesuré.

Commence par sortir les littéraux des écrans touchés vers la palette, écran
par écran, pas d'un coup.

## 9. La dette graphique connue

Classée par gravité. Corrige ce que touche ta tâche ; propose le reste, ne
l'impose pas. **Changer la couleur du bandeau ou des onglets engage
l'identité : demande d'abord au propriétaire.**

| # | Défaut | Mesure | Proposition |
|---|---|---|---|
| 1 | Libellé et surtitre orange `#FF8C00` sur blanc | 2,33:1 | Garder l'orange pour le trait et la pastille, passer le texte en `#B35900` (4,83:1) ou en encre `#1C1C1C` gras. |
| 2 | Texte blanc sur vert `#4CAF50` (bandeau « Tout est prêt », étapes faites) | 2,78:1 | Vert `#2E7D32` (5,13:1) sous le texte ; `#4CAF50` réservé aux pastilles et coches. |
| 3 | Bandeau `#0080FF`, orange d'onglet : couleurs du clavier créole, hors drapeau | 3,80:1 | Bandeau bleu nuit `#003876` (11,49:1) et accent rouge du drapeau pour l'onglet actif : l'appli ressemblerait enfin à son clavier. À valider. |
| 4 | Pas de thème sombre dans l'appli | — | § 8. |
| 5 | Rembourrages en pixels bruts (`setPadding(20, …)`), `cornerRadius = 12` sans `enDp` | — | Convertir en dp à chaque écran touché. |
| 6 | Deux cartes : `createCard` carrée (onglets) et `createRoundedCard` 16 dp (accueil) | — | Converger vers la carte arrondie, écran par écran. |
| 7 | Trois quasi-noirs (`#1C1C1C`, `#212121`, `#333333`) et plusieurs gris | — | Un jeton encre, un jeton encre atténuée. |
| 8 | `colors.xml`, `dimens.xml` morts | — | Les supprimer, ou les remplacer par la vraie palette si l'appli passe aux ressources. |

## 10. L'outil : `contraste.py`

```bash
python3 .claude/skills/design-application/contraste.py "#FFFFFF" "#ED2939"
```

```bash
python3 .claude/skills/design-application/contraste.py --palette
```

Rapport WCAG 2.1, composition des encres translucides `#AARRGGBB` sur leur
fond, verdict selon les seuils (4,5:1 texte courant ; 3:1 texte large — 24 px,
ou 18,7 px en gras — et éléments graphiques). `--palette` remesure les paires
en usage : mets-la à jour quand tu changes une couleur.

Pour la lisibilité d'une carte (vignette, daltonisme, noir et blanc), c'est
`.claude/skills/designer-cartes/controle_lisibilite.py`.

## 11. Revue d'un écran

Avant de valider un écran nouveau ou modifié :

- [ ] Chaque texte passe `contraste.py` sur son fond (4,5:1, ou 3:1 s'il est grand).
- [ ] Aucune nouvelle couleur sans sens ; les couleurs de sens sont les bonnes (§ 2.4).
- [ ] Rien ne se distingue par la seule couleur.
- [ ] Tailles prises dans l'échelle (§ 3), en `sp` ; cotes en dp.
- [ ] Cibles tactiles ≥ 48 dp.
- [ ] Les composants existants sont repris (§ 5), pas recopiés.
- [ ] Police du téléphone à 200 % : rien ne se chevauche ni ne se rogne.
- [ ] Tablette couchée : colonne bornée ou deux colonnes, rien sous le bord.
- [ ] Android 15 : rien sous la barre d'état ni la barre de navigation.
- [ ] Animations coupées : l'écran reste juste.
- [ ] Clavier : capture en clair **et** en sombre.

Rends la revue en quatre parties : **Verdict** (une phrase), **Ce qui fait
LuxKeyb**, **À corriger** (concret, avec la mesure et le § concerné),
**Prochain test**.
