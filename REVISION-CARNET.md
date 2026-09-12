# Réviser les cartes du carnet : la décision, les mesures, le plan

Note du 12 septembre 2026, écrite **avant** toute ligne de code, comme
[`SWIPE-ESPACE.md`](SWIPE-ESPACE.md), et à la racine pour la même raison : le
filtre de chemins de `build-apk.yml` couvre `android_keyboard/**`, donc déposer
une note de conception là-dedans déclencherait un build APK + AAB complet,
pipeline Python compris, à chaque correction de typo.

---

## 1. Ce qui manque exactement

Le carnet (version 21.0.0, `carnet/`) garde les mots gagnés dans les sept jeux :
une carte par forme, avec sa glose, une phrase du LOD, sa famille, sa rareté et
le jeu qui l'a donnée. Il a réparé la récompense qui disparaissait avec la
grille.

Il ne répare pas l'oubli. Une carte gagnée lundi est lue une fois, à l'ouverture
de la pochette, puis rangée dans une grille de vignettes que rien n'oblige à
rouvrir. Le carnet est une étagère à trophées, et ce dont on a besoin est un
paquet de cartes qui revient vous voir.

C'est très exactement ce que la répétition espacée fait, et le carnet en a déjà
les trois quarts : une collection persistante, sauvegardée, avec pour chaque mot
un recto (la forme rencontrée), un verso (la glose), une phrase en situation et
un rendu de carte déjà dessiné (`CarteCarnet.complete`). Il manque deux entiers
par carte et une file d'attente.

**Nom proposé : Widderhuelen.** Attesté au LOD (`suggest="true"`), glosé
« répéter, rediffuser, se répéter ». `opfrëschen` (« rafraîchir ») et
`Widderhuelung` (« répétition ») sont attestés aussi. Comme `Wuertplaz`, le choix
n'a pas été vérifié par un locuteur natif.

## 2. Le vivier : le carnet, et lui seul

Le mot « découvert » a déjà deux sens dans l'application, et le paquet de
révision ne peut pas les mélanger :

- **Les cartes du carnet** sont des mots *rencontrés* : le joueur a croisé la
  forme dans un jeu et on lui en a révélé le sens. C'est ce qui s'oublie.
- **Les mots découverts au sens de `LuxLevels`** sont les mots *tapés* au moins
  une fois, comptés dans `luxemburgish_dict_with_usage.json`. Un mot qu'on a
  écrit soi-même dans un vrai message n'a pas besoin d'être révisé : il est la
  preuve qu'on le connaît. Voir la section 5, qui en fait un usage bien plus
  intéressant que d'en remplir un paquet.

Donc : **le paquet est le carnet**, sans ajout manuel depuis le Wierderbuch ni
depuis le mot du jour. L'invariant du carnet est qu'une carte se gagne, et une
carte fabriquée depuis une fiche de dictionnaire le casserait, en plus de fausser
la numérotation, qui est un ordre de capture. Si un jour on veut réviser un mot
simplement consulté, la bonne forme est un second paquet, pas une fausse carte.

### Ce que les actifs livrés permettent, mesuré

Réunion des viviers qui alimentent le carnet (mots des 300 grilles de Kräizwuert
et des 300 de Wuertplaz, réponses de Wuertlück, réserve de tirage des trois jeux
qui choisissent un mot), contre `luxemburgish_translations.json`,
`luxemburgish_familles.json` et `luxemburgish_exemples.json` :

| Vivier | Formes | Glosées | Glose instructive | Phrase d'exemple |
|---|---:|---:|---:|---:|
| Kräizwuert | 1 489 | 100,0 % | 100,0 % | 99,9 % |
| Wuertplaz | 1 963 | 100,0 % | 90,4 % | 99,9 % |
| Wuertlück | 1 004 | 91,2 % | 79,0 % | 91,1 % |
| Tirage (Wuertsich, Wuertmix, Wuertriet) | 18 599 | 100,0 % | 100,0 % | 99,8 % |
| **Réunion** | **18 979** | **99,5 %** | **98,1 %** | **99,3 %** |

Trois lectures :

- **Une carte peut presque toujours devenir une carte de révision**, et la
  question « avec quoi ? » a une réponse dans 99,3 % des cas : la phrase du LOD.
- **Wuertlück est le seul point faible, et c'est structurel** : ses réponses
  viennent du corpus, pas du filtre `estProposable`, donc 8,8 % n'ont aucune
  glose et 21 % se glosent par elles-mêmes (`Budget` → budget). Règle : une carte
  sans glose instructive **reste dans la collection et n'entre jamais dans le
  paquet**. Elle n'a rien à faire réviser.
- Zuelwuert est hors tableau et n'a pas besoin du dictionnaire : un numéral porte
  sa glose (« le nombre 56 ») et sa décomposition (`ZuelenSpeller.decomposition`),
  ce qui en fait la meilleure carte de production du lot.

## 3. L'algorithme : des boîtes de Leitner, ni SM-2 ni FSRS

Six boîtes, intervalles fixes **1, 3, 7, 16, 35, 90 jours**, puis *acquis*. Bonne
réponse : la carte monte d'une boîte. Mauvaise réponse : retour en boîte 0, **et
la carte repasse en fin de session** (sans quoi on échoue sur un mot et on ne le
revoit que le lendemain, ce qui n'enseigne rien).

Pourquoi pas mieux :

- **SM-2 demande au joueur de noter sa propre réponse de 1 à 4**, et cette note
  alimente un exposant. C'est une question à laquelle personne ne répond
  honnêtement, et ici elle décide de quand le mot revient. « Su » ou « pas su »
  est la seule chose que le joueur sait, et c'est aussi la seule que le jeu
  observe quand la réponse est tapée.
- **FSRS s'ajuste sur un journal de milliers de révisions.** Rien ne sort de
  l'appareil (c'est la politique de confidentialité publiée), donc nous
  livrerions des paramètres ajustés sur les révisions de quelqu'un d'autre, dans
  une autre langue. C'est le même refus que celui déjà écrit pour la rareté des
  cartes : poser une statistique inventée sur une vraie langue apprend quelque
  chose de faux. Un intervalle fixe ne prétend rien.
- **L'état tient en deux champs par carte**, un entier de boîte et un jour
  d'échéance, qui entrent dans le blob JSON existant. SM-2 en demanderait quatre
  et un historique.

## 4. La question : la boîte décide, pas le joueur

Trois formes, dans l'ordre où la mémoire les supporte (reconnaître avant
produire) :

- **Boîtes 0 et 1, reconnaissance.** Recto : la forme rencontrée, seule. On
  retourne, et le verso est **la carte du carnet elle-même**
  (`CarteCarnet.complete`), qui porte déjà la glose, la phrase, la famille, la
  rareté et la provenance. Autonotation en deux boutons. Aucun rendu nouveau à
  écrire.
- **Boîtes 2 et au-delà, production.** La phrase du LOD avec le mot remplacé par
  des cases, et on le **tape** sur le pavé QWERTZ de Kräizwuert
  (`CrosswordData.RANGEES`, 31 touches, `ÄËÉÖÜ` comprises). C'est la seule forme
  qui fait produire l'orthographe, accents et majuscule de substantif comprises,
  et c'est la leçon que Kräizwuert a déjà identifiée comme la seule vraie.
  Disponible pour 99,3 % des formes.
- **Repli, quand la forme n'a pas de phrase :** la glose en français, et le mot à
  taper.

### Pourquoi la phrase plutôt que le français seul

Sur la réunion des viviers, **36,2 % des familles glosées partagent leur premier
sens** avec une autre : neuf formes se glosent « présenter », neuf « passer »,
sept « accord ». Demander « présenter » et attendre `virbréngen` est une question
à neuf réponses justes. La phrase, elle, désigne son mot.

Ce n'est pourtant pas rédhibitoire à l'échelle d'un vrai carnet, et c'est ce qui
autorise le repli. Tirages de 200 paquets dans la réunion :

| Taille du paquet | Sens ambigus dans le paquet | Pire des 200 tirages |
|---:|---:|---:|
| 30 cartes | 0,3 % | 16,7 % |
| 60 cartes | 0,6 % | 6,8 % |
| 120 cartes | 1,1 % | 5,1 % |
| 250 cartes | 2,4 % | 6,1 % |
| 500 cartes | 4,2 % | 8,1 % |

Et pour un joueur qui ne jouerait qu'à un seul jeu, donc dont le carnet est
concentré sur un vivier étroit : 1,0 % à 60 cartes et 1,9 % à 120, pour chacun
des trois viviers mesurés. La dispersion suffit.

**Le reste se règle à la notation, pas au tirage** : une réponse tapée est
acceptée si elle est **n'importe quelle carte du paquet dont la glose est celle
affichée**, et non par comparaison à une seule chaîne. Le joueur qui répond
`Akkord` à « accord » quand la carte attendue était `Accord` a raison, et le dire
coûte une boucle sur quelques dizaines de cartes.

### La notation d'une réponse tapée

Forme exacte attendue. Juste **à un accent ou à la majuscule près** : compté
comme réussi, la différence est montrée, et la carte **ne monte pas de boîte**.
C'est le seul endroit de l'application où la majuscule de substantif et l'accent
sont l'objet de la question plutôt qu'un détail de rendu, et laisser passer
`greng` pour `gréng` enseignerait la faute que le jeu existe pour corriger.

## 5. Le clavier est l'examen

C'est ce qu'aucune application de cartes ne peut faire et que celle-ci peut.

`CreoleDictionaryWithUsage` compte déjà, par mot, combien de fois il a été tapé,
pour les seuls mots du dictionnaire et derrière `isSensitiveInput()`. Donc :
**une carte dont le compteur d'usage a monté depuis la dernière révision monte
d'une boîte sans poser de question**, et la session le dit (« vous l'avez écrit
vous-même »). Écrire `Forschett` dans un vrai message est une preuve de mémoire
plus forte que n'importe quelle carte retournée.

Rien de nouveau n'est collecté. Deux contraintes, et la seconde est un piège :

- **La preuve ne doit pas quitter `filesDir`.** Le fichier d'usage vit dans
  `filesDir`, que `backup_rules.xml` et `data_extraction_rules.xml` excluent tous
  les deux, et c'est ce qui rend vraie la confidentialité publiée. Le carnet, lui,
  est un `SharedPreferences`, donc sauvegardé dans le nuage. Le paquet de
  révision n'écrit donc dans les préférences **que la date d'échéance** : une
  échéance repoussée par le clavier est indiscernable d'une échéance repoussée par
  une révision réussie. Aucun compteur de frappe ne franchit la frontière.
- **Savoir que le compteur a monté demande de savoir ce qu'il valait.** Cette
  référence est une donnée de frappe, elle va donc dans un petit fichier de
  `filesDir` à côté du fichier d'usage (`carnet_vu.json`, forme → compteur vu à la
  dernière révision), jamais dans les préférences. Même domaine, même exclusion de
  sauvegarde, invariant tenu.

## 6. Où cela vit

**Pas un huitième jeu.** Réviser est ce qu'on fait d'une collection, pas une
partie de plus : le hub défile déjà depuis la septième carte, la barre d'onglets
en porte quatre et `REAL_COUNT` pilote le modulo du pager cyclique. Le carnet est
le bon lieu, et c'est lui qui devient la destination.

- **Bannière du hub** : une ligne de plus, « 3 cartes à revoir aujourd'hui », et
  le bouton passe de « Ouvrir › » à « Réviser › » quand quelque chose est dû. La
  règle existante de la bannière est respectée : elle n'affiche que ce qui se lit
  **sans toucher aux actifs**, et un décompte d'échéances se lit dans les
  préférences, comme le total.
- **Dans le carnet** : un bouton d'en-tête « 🔁 Widderhuelen · N », et sous le mot
  de chaque vignette une **barre de six segments** qui dit la boîte. Pas une
  couleur : la couleur du cadre appartient à la rareté, et deux échelles de
  couleur sur la même vignette ne se lisent plus.
- **La session** est une vue posée sur le `FrameLayout` racine de
  `CarnetFragment`, comme le voile de `montrerCarte`, et non un second
  `DialogFragment`.

**Plafond : 12 cartes par session**, soit une minute et demie. Une file non
plafonnée est la façon dont ce genre de fonction meurt : une semaine d'absence
fabrique un mur de 200 cartes, et le mur ne s'ouvre jamais. Les plus anciennement
dues d'abord, puis les boîtes les plus basses. Une carte n'est jamais vue deux
fois dans la même session, sauf le repassage d'un échec.

## 7. Stockage, migration, et le calendrier

Deux champs dans le blob existant (`carnet_cartes`) : `b` la boîte, `j` le jour
d'échéance. Absents : carte jamais révisée.

- **Le piège de la migration.** « Absente donc due aujourd'hui » déverse d'un
  coup le carnet entier d'un joueur existant, c'est-à-dire le mur décrit
  ci-dessus, dès la première ouverture. À la première ouverture, les cartes
  existantes sont donc **étalées** sur les `N / 12` jours suivants, dans leur
  ordre de capture : la collection revient au rythme où elle a été faite.
- **Des jours, pas des millisecondes.** Une carte due « demain » doit l'être
  demain matin, pas dans 24 heures à la seconde près. On stocke un numéro de jour
  local avec une coupure à 4 h, comme le font les applications de cartes.
- **Une horloge reculée ne doit pas verrouiller le paquet.** Changement de fuseau,
  réglage manuel : si le jour stocké dépasse aujourd'hui de plus que le plus long
  intervalle (90 jours), la carte est traitée comme due.

## 8. Ce qu'on ne fait pas

- **Pas de série (« streak »), pas de culpabilité quotidienne.** La progression a
  déjà son échelle (`LuxLevels`), et l'utilisatrice de référence de ce projet
  n'a pas besoin d'un compteur qui la gronde.
- **Pas de notification dans un premier temps.** Si elle vient, ce sera le motif
  de `LevelUpNotifier` : canal `IMPORTANCE_LOW`, pastille sur l'icône, **jamais**
  de bandeau déroulant. L'application est un clavier : un bandeau tomberait sur
  la conversation en train d'être écrite.
- **Pas de suppression de carte, pas de mise en sommeil.** Le carnet ne fait que
  grandir, c'est son invariant ; une carte qu'on n'arrive pas à retenir est
  l'affaire des boîtes, pas d'une porte de sortie.
- **Pas de synthèse vocale.** Rien dans le projet ne prononce le luxembourgeois :
  les deux branches de dictée ne sont pas sur `main` et ne font que de la
  reconnaissance.
- **Pas de mots tapés dans le paquet** (section 2). Le clavier renseigne le
  calendrier, il ne remplit pas le paquet.

**Neutralité** : rien de nouveau. Les cartes viennent de viviers déjà filtrés par
`MotsEcartes` au chargement, et les phrases passent par
`TranslationDictionary.exemples`, qui applique `estEcarte` en épargnant le mot
cherché. On n'applique **pas** `SUJETS` ici, pour la raison déjà écrite à propos
de la fiche : ce sont des phrases de dictionnaire, pas des dépêches, et les
écarter priverait `Police` ou `Accident` de l'exemple qui montre leur emploi.

## 9. Plan, fichier par fichier

| Fichier | Ce qu'il reçoit |
|---|---|
| `carnet/Widderhuelen.kt` (nouveau) | Le calendrier seul : boîtes, intervalles, jour local et coupure, construction de la file, plafond, étalement de migration. **Sans `Context`**, donc testable sur la JVM, comme `LuxLevels.kt`. |
| `carnet/CarnetModels.kt` | Deux champs dans `CarteMot`, leur lecture et leur écriture, `Carnet.aRevoir(context)` et `Carnet.noter(context, forme, reussi)`. |
| `carnet/SessionWidderhuelen.kt` (nouveau) | La session : question, révélation, pavé, repassage des échecs, bilan. Réutilise `CarteCarnet.complete` et `CrosswordData.RANGEES`. |
| `carnet/CarnetFragment.kt` | Le bouton d'en-tête, la barre de six segments sur la vignette. |
| `SettingsActivity.kt` | Une ligne dans `majBanniereCarnet()`, et le libellé du bouton. |
| `gamification/CreoleDictionaryWithUsage.kt` | La lecture du compteur d'un mot, et `carnet_vu.json` dans `filesDir` (section 5). |

Tests (`app/src/test/`, il n'y a pas d'`androidTest/`) :

- `WidderhuelenPlanTest` : boîtes et intervalles, la coupure de 4 h, l'horloge
  reculée, l'étalement de migration, le plafond, le repassage d'un échec.
- `CarnetRevisionAssetTest` : sur les actifs livrés, la part des formes des
  viviers qui portent une glose instructive et une phrase d'exemple reste
  au-dessus de 95 % (mesurée 98,1 % et 99,3 %). Une régénération qui perdrait
  `luxemburgish_exemples.json` ferait basculer toutes les cartes de production
  sur le repli français **sans rien casser de visible**, ce qui est exactement le
  genre de panne que ce dépôt garde en CI.
- La règle de notation gelée : tolérance d'accent et de majuscule, et
  l'acceptation de toute carte du paquet dont la glose est celle affichée.

## 10. Post-scriptum d'implémentation (12 septembre 2026, version 22.0.0)

Le plan a été suivi, à trois écarts près, tous décidés en écrivant le code :

- **Le pavé reçoit une touche `⇧`**, dans l'emplacement que Kräizwuert laisse
  volontairement vide. Sans elle, la majuscule du substantif ne peut pas être
  produite, donc pas être demandée, et la section 4 la donne pour l'objet même
  de la question. La majuscule ne vaut que pour la lettre suivante, comme sur le
  clavier, et elle n'est jamais préarmée.
- **La phrase troue toutes les occurrences du mot, pas seulement la première.**
  Le plan disait la première ; le test d'actif l'a contredit en trouvant les
  phrases qui répètent leur mot, et une question dont la réponse est écrite
  dedans est pire qu'une phrase un peu nue.
- **Une quatrième forme de question n'a pas été nécessaire**, mais le repli sur
  le sens français sert plus souvent que prévu : 93,3 % des formes des trois
  viviers de contenu ont une phrase **trouable**, contre 97,2 % qui ont une
  phrase. L'écart vient des flexions que la famille ne liste pas (`bestuete`
  pour `bestuet`, `gezunn` pour `zéi`), et il est mesuré par
  `CarnetRevisionAssetTest`.

La vue a été séparée en deux fichiers plutôt qu'un (`SessionWidderhuelen.kt`
pour les règles, `VueWidderhuelen.kt` pour l'écran), pour la raison qui vaut
déjà pour `ChasseCroiseSession` : ce sont les règles qui cassent en silence, pas
les pixels, et seules les règles se testent.

## 11. Ce qui reste à trancher par le propriétaire

1. **Le nom.** `Widderhuelen`, `Opfrëschen`, ou simplement « Réviser ». Aucun
   n'est vérifié par un locuteur natif. La 22.0.0 livre les deux : le bouton dit
   « Réviser N cartes », l'en-tête de la session dit « Widderhuelen », comme la
   barre d'onglets qui nomme en luxembourgeois ce que les écrans expliquent en
   français. Si un locuteur natif tranche pour un seul, c'est le bouton qui
   changera.
2. **Le plafond (12) et les intervalles (1, 3, 7, 16, 35, 90).** Ce sont mes
   valeurs par défaut ; aucune mesure possible ici ne les départagera, seul
   l'usage le fera.
3. **Le clavier comme examen : silencieux ou annoncé ?** Dire « vous l'avez écrit
   vous-même » est flatteur et explique pourquoi la carte n'a pas été posée, mais
   cela révèle à l'écran que l'application sait ce qui a été tapé. C'est déjà le
   cas de l'onglet de statistiques, qui affiche les mots et leur compteur.
4. **Un rappel quotidien, oui ou non** (section 8).
