# Expérience utilisateur : ce qui peut s'améliorer

Audit du 2026-09-29, version installée sur l'émulateur `pixel9`. Deux sources :
la lecture du code (service de saisie, `InputProcessor`, `SettingsActivity`,
écran des réglages) et un parcours réel à l'écran (Démarrage, Spiller,
Wierderbuch, Mäi Lëtzebuergesch, frappe dans le champ de recherche).

Chaque point dit s'il a été **constaté** (vu à l'écran ou lu sans ambiguïté
dans le code) ou s'il reste **à vérifier**. La personne de référence est
l'utilisatrice non technicienne : ce qui l'oblige à lire, à comprendre un
réglage Android ou à corriger le clavier passe avant ce qui ferait plaisir à un
utilisateur averti.

---

## 1. Le clavier : ce qui gêne à chaque message

C'est là que se joue l'essentiel. Une friction dans un jeu se subit une fois
par partie ; une friction de frappe se subit cinquante fois par jour.

### 1.1 L'espace automatique reste devant la ponctuation (constaté)

**Fait en 29.3.0.**

Toucher une suggestion insère le mot **suivi d'une espace**, puis un point tapé
juste après donne `Gromper .`. Vérifié à l'écran. `handleCharacterInput()` ne
sait pas que l'espace précédente a été posée par le clavier.

Correction attendue, comme sur Gboard et Samsung : si le caractère suivant est
`. , ? ! : ; ) …` et que l'espace précédente vient d'une suggestion, la
retirer. Un indicateur « espace posée par nous » suffit, sur le modèle de
`lastAutoCapitalization`. Priorité la plus haute de ce document : c'est le
geste le plus fréquent du clavier (toucher une suggestion, finir la phrase).

### 1.2 Le clavier ne s'adapte pas au type de champ (constaté)

**Fait en 29.3.0.**

`onStartInputView()` appelle `forceAlphabeticMode()` sans regarder
`EditorInfo.inputType`. Conséquences :

| Champ | Aujourd'hui | Attendu |
|---|---|---|
| Code PIN, code SMS, montant, numéro | clavier QWERTZ, il faut toucher `123` | pavé numérique directement |
| Téléphone | QWERTZ | pavé téléphone (`+`, `*`, `#`) |
| E-mail | majuscule auto, suggestions, espace après suggestion | pas de majuscule, pas d'espace auto, `@` et `.lu` visibles |
| Adresse web | idem | pas d'espace auto, `/` et `.lu` visibles |
| Recherche | touche ⏎ | icône loupe |
| Envoi de message | touche ⏎ | icône d'envoi |

Le code PIN est le cas le plus pénible : c'est souvent le premier champ où
l'on tombe après l'installation (code de vérification SMS).

La touche Entrée exécute déjà la bonne action (`handleEnter()`), seul son
dessin ne le dit pas.

### 1.3 Des suggestions apparaissent dans les champs de mot de passe (constaté dans le code, à confirmer à l'écran)

**Fait en 29.3.0.**

`isSensitiveField()` bloque le comptage des mots, la majuscule auto et les
emojis récents, mais **pas l'affichage des suggestions** : `onWordChanged()`
lance la recherche sans condition. Taper un mot de passe fait donc défiler ses
préfixes en grosses puces rouges, lisibles par-dessus l'épaule, et nourrit
l'historique n-gramme en mémoire. Vider la barre dans un champ sensible, comme
le font tous les claviers du marché. Petit correctif, et cohérent avec la
politique de confidentialité publiée.

### 1.4 Des caractères courants sont introuvables (constaté)

**Fait en 29.3.0.**

La seule page de symboles n'a ni `%`, ni `_`, ni `$`, `£`, `°`, `§`, `<`, `>`,
`[`, `]`, `{`, `}`, `~`, `|`, `\`, `^`. Or `%` pèse **4 495 occurrences** dans
le corpus (plus que `?`), `_` est dans beaucoup d'identifiants et d'adresses,
`°` sert pour la météo et les degrés. Aujourd'hui il faut changer de clavier.

Deux options : une seconde page `=\<` comme Gboard, ou des appuis longs sur la
rangée de chiffres et de symboles (`%` sous `5`, `_` sous `-`, `°` sous `0`,
`$ £` sous `€`, etc.). La seconde ne coûte aucune touche et reprend un geste que
l'utilisateur connaît déjà sur les lettres.

### 1.5 Pas d'annulation d'une suggestion touchée par erreur (constaté)

Le Retour arrière annule déjà une majuscule automatique
(`revertAutoCapitalization`). Il ne fait rien de tel pour une suggestion :
toucher `Grompereféld` au lieu de `Gromper` oblige à effacer le mot entier.
Même mécanisme à étendre : un Retour arrière immédiat restitue ce qui était
tapé.

### 1.6 Les mots personnels ne sont jamais appris (constaté)

Prénoms, noms de villages, surnoms, mots de la famille : le clavier ne les
propose jamais et le correcteur les souligne toujours. C'est ce qui fait dire
« ce clavier ne me connaît pas » au bout d'une semaine.

Proposition : un dictionnaire personnel **local**, rempli quand un mot inconnu
est tapé deux ou trois fois hors champ sensible, plus « Ajouter au
dictionnaire » dans le menu du correcteur. Même stockage et même frontière de
confidentialité que les compteurs d'usage (`filesDir`, hors sauvegarde). Avec
son pendant : appui long sur une puce, « Ne plus proposer ce mot ». Demande une
phrase dans la politique de confidentialité ; le reste existe déjà.

### 1.7 Coller depuis la barre de suggestions (à concevoir)

Quand la barre est vide (début de champ), elle pourrait offrir une puce
« Coller : … » si le presse-papiers contient un texte récent. Ce ne sont pas des
prédictions, donc la règle « pas de barre remplie sans contexte n-gramme » n'est
pas en cause. À faire proprement : n'afficher que du texte copié depuis moins
d'une minute ou deux, jamais dans un champ sensible, et Android 12+ affiche un
bandeau à chaque lecture du presse-papiers.

### 1.8 La cuvette vide mange la place (constaté)

Sans suggestion, les deux rangées grises restent réservées (environ 190 px sur
un Pixel 9), et quand seule la rangée luxembourgeoise a des puces, la rangée
française reste un bandeau vide. Le choix d'une hauteur fixe évite que le
clavier saute à chaque lettre et doit rester ; mais l'espace vide peut porter
les outils du point 1.7 ou, a minima, être ramené à une rangée quand la rangée
française n'a rien donné depuis plusieurs mots.

### 1.9 Réglages de confort absents (constaté)

L'écran ⚙ ne propose que thème, vibration, son et emojis récents. Manquent, par
ordre d'utilité pour la personne de référence :

1. **Hauteur des touches** (petite, normale, grande). Le premier réglage que
   cherchent les personnes âgées ou malvoyantes.
2. **Délai de l'appui long** (500 ms fixe dans `AccentHandler`). Trop court
   pour une main qui tremble, trop long pour un habitué des accents.
3. **Rangée de chiffres** permanente, ou chiffres en appui long sur
   `q`…`p` avec leur rappel en coin.
4. **Double espace pour un point**, désactivé par défaut.
5. Mode une main, plus tard.

### 1.10 Le correcteur souligne un mot luxembourgeois correct (à vérifier)

Dans le champ de recherche de l'appli, `Gromper` est apparu souligné en rouge.
Hypothèse : la langue du téléphone de l'émulateur n'est ni `lb` ni `fr`, donc
c'est le correcteur du système qui répond, pas le nôtre. Si c'est bien ça,
l'écran d'aide du correcteur devrait le dire (« le correcteur luxembourgeois
s'active quand la langue du téléphone est le français ») et le point mérite un
test sur un téléphone réglé en allemand ou en anglais, cas fréquent au
Luxembourg.

---

## 2. L'application : ce qu'on voit en l'ouvrant

### 2.1 L'accueil reste un écran d'installation pour toujours (constaté)

Une fois les trois étapes faites, l'onglet Démarrage affiche encore, dans
l'ordre : « Tout est prêt ! », la configuration 3/3, deux réglages, un long
texte sur le changement de clavier, l'astuce, un lien vers les statistiques.
Rien de ce qui fait revenir : ni les **12 cartes à revoir aujourd'hui**
(visibles seulement dans Spiller), ni le mot du jour (dans le 4ᵉ onglet), ni la
partie en cours.

Proposition : après installation, l'accueil devient « Haut » (aujourd'hui) :
cartes à revoir avec un bouton, mot du jour, dernier jeu joué, progression vers
le prochain palier. Le contenu d'installation se replie en une ligne
« Configuration terminée › » et le texte sur le changement de clavier passe
dans le Guide.

### 2.2 Aucun rappel de révision (constaté)

La boîte de Leitner repose sur des échéances (1/3/7/16/35/90 jours), mais rien
ne prévient qu'une révision est due : la seule notification de l'appli est la
montée de niveau. Une notification quotidienne **facultative**, silencieuse,
à l'heure choisie (« 12 cartes vous attendent »), calculée localement. Sans
elle, la répétition espacée ne fonctionne que pour ceux qui y pensent seuls,
c'est-à-dire presque personne.

### 2.3 La barre d'onglets saute (constaté)

Sélectionné, « Mäi Lëtzebuergesch » passe sur deux lignes et la barre grandit
d'environ 28 px, ce qui décale tout l'écran à chaque changement d'onglet.
Non sélectionné, le libellé touche les bords. Fixer la hauteur de la barre (deux
lignes réservées pour tous) ou réduire la taille du libellé actif.

### 2.4 « Mäi Lëtzebuergesch » décourage (constaté)

**Fait en 29.3.0.**

- En gros : **0.0%**, et « 7 mots découverts sur les 38442 mots ». Pour
  quelqu'un qui débute, c'est lire qu'il ne sait rien. Mettre en avant la
  progression vers le **prochain palier** (barre 7 / 576 vers Klengen) et
  garder le pourcentage global en petit, ou le retirer.
- La phrase « plus que 569 mots restants à découvrir pour passer au niveau
  suivant » se lit mal ; « Encore 569 mots avant Klengen ».
- Le bouton « Partager ma carte de niveau » arrive **avant** le niveau
  lui-même. Le niveau d'abord, le partage ensuite.
- `38442` sans espace de milliers, `0.0%` avec un point : écrire `38 442` et
  `0 %`.

### 2.5 Les noms perdent leur majuscule dans les statistiques (constaté)

**Fait en 29.3.0.**

Mot du jour **brauereien**, chips **parteipresidenten**, **konservativ**.
L'application apprend la Groussschreiwung dans les jeux et l'écrit ici en
minuscules. Cause probable : les clés du fichier d'usage sont en minuscules
(`loadDictionary()` abaisse la casse). Afficher la forme canonique de
`luxemburgish_dict.json` (`Brauereien`).

Deux raffinements du mot du jour au même endroit :
- préférer le représentant de famille (`Brauerei`) à une forme fléchie ;
- écarter les mots français tels quels (`recommandé` est apparu dans les mots à
  découvrir).

Et vérifier qu'un toucher sur le mot du jour ou sur une chip ouvre la fiche du
Wierderbuch ; c'est le geste que tout le monde tentera.

### 2.6 Beaucoup de texte à lire (constaté)

- Spiller s'ouvre sur un paragraphe de huit lignes avant les cartes de jeu.
- Wierderbuch : quatre lignes de mode d'emploi au-dessus du champ.
- Écran ⚙ : « sur plusieurs surcouches ce mode ne descend pas jusqu'aux
  claviers tiers », « le réglage de vibration au toucher ne gouverne que le
  clavier du constructeur ». Juste, mais c'est du vocabulaire de développeur.

Règle proposée : une ligne d'explication par écran, le reste dans le Guide.
Les justifications techniques ont leur place dans le code et dans ce dépôt,
pas sous un interrupteur.

### 2.7 Les réglages sont dispersés (constaté)

Thème, vibration et son sont derrière ⚙ ; majuscules automatiques et
correcteur sont dans Démarrage ; le changement de clavier aussi. Tout ce qui
règle le clavier devrait être dans ⚙, Démarrage gardant au plus des raccourcis.

### 2.8 Spiller : doublons et reprise (constaté)

- La Boîte de Leitner apparaît deux fois : le bandeau violet « Mäi Carnet »
  (avec « 12 cartes à revoir ») et la première carte de jeu. Et le texte parle
  de « huit façons » pour sept jeux plus la boîte. Garder le bandeau, retirer
  la carte, ou l'inverse.
- Pas de « Reprendre » : quitter une grille de Kräizwuert à moitié remplie la
  perd (à vérifier jeu par jeu). Mémoriser la partie en cours et la difficulté
  choisie.
- Un défi du jour (même grille pour tout le monde, graine = date) donne une
  raison de revenir sans rien collecter : la graine suffit.

### 2.9 Wierderbuch (constaté et à concevoir)

- ~~La recherche ne nettoie pas la ponctuation~~ : fait en 29.3.0.
- Le clavier ajoute une espace après une suggestion touchée dans ce champ
  (voir 1.2 : un champ de recherche ne devrait pas en recevoir).
- État vide : un grand écran gris. Y mettre les recherches récentes (locales)
  et le mot du jour.
- Depuis une fiche : « Ajouter à mon carnet ». Aujourd'hui seuls les jeux
  remplissent le carnet ; chercher un mot est pourtant l'acte d'apprentissage
  le plus volontaire qui soit. Le code l'anticipe déjà (« favori »).
- **Prononciation.** La fiche la prévoit déjà dans un commentaire. Le LOD
  publie des enregistrements audio par article ; à vérifier : licence (le reste
  du LOD est CC0), mode (lien en ligne vers lod.lu plutôt qu'embarqué, pour ne
  pas grossir l'APK). Pour un apprenant, entendre le mot compte plus que lire
  un deuxième exemple.

### 2.10 Langue de l'interface (à concevoir)

Tout est en français. Au Luxembourg, une grande part des apprenants sont
anglophones ou lusophones, et une partie des locuteurs natifs préféreraient
une interface en luxembourgeois ou en allemand. Anglais d'abord (le plus large
public d'apprenants), luxembourgeois ensuite. Travail lourd tant que les
textes sont écrits en dur dans `SettingsActivity.kt` : commencer par les
sortir dans `strings.xml` au fil des écrans touchés, pas d'un coup.

### 2.11 Mode sombre de l'application (à vérifier)

Le clavier a trois thèmes ; l'application, elle, semble toujours blanche. Le
soir, passer d'un clavier sombre à une appli éclatante se remarque.

### 2.12 Accessibilité (à vérifier)

Seules ⌫, ⏎ et ⇧ ont une description explicite ; `123`, l'emoji, la barre
d'espace (`LuxKeyb™`) et les puces de suggestion (langue comprise) sont à
tester avec TalkBack. Vérifier aussi l'appli avec la taille de police système
au maximum : les cartes de jeu et la barre d'onglets en sont les premières
victimes.

---

## 3. Ce qu'il ne faut pas faire, même si c'est tentant

Décisions déjà prises et mesurées (voir `CLAUDE.md`) ; ces propositions
reviendront, elles ont déjà été écartées pour de bonnes raisons :

- **Remplir la barre sans contexte n-gramme** : 2 à 3 % de bonnes réponses
  contre 18 % avec contexte. La barre vide est le bon signal. Les outils du
  point 1.7 ne sont pas des prédictions et n'enfreignent pas cette règle.
- **Correction automatique à l'espace.** Dans un clavier bilingue, remplacer
  un mot français correct par un mot luxembourgeois proche serait bien pire
  que de souligner. Si on y vient un jour : jamais sans l'annulation du
  point 1.5, et jamais sur un mot reconnu en français.
- **Remettre la saisie au clavier dans la révision du carnet** (retirée en
  23.0.0).
- **Fusionner le LOD dans `luxemburgish_dict.json`** pour « simplifier » : les
  paliers et les jeux en dépendent.
- **Dictée sur `main`** avant l'accord écrit et la nouvelle politique de
  confidentialité.

---

## 4. Ordre proposé

Le critère : fréquence de la gêne × nombre de personnes touchées ÷ coût.

| # | Chantier | Coût | Pourquoi maintenant |
|---|---|---|---|
| 1 | Espace avant ponctuation (1.1) | petit | à chaque phrase |
| 2 | Pas de suggestions en champ mot de passe (1.3) | petit | confidentialité |
| 3 | Adapter le clavier au type de champ (1.2) | moyen | PIN et e-mail dès le premier jour |
| 4 | `%`, `_`, `°`… en appui long (1.4) | petit | caractères aujourd'hui impossibles |
| 5 | Casse et mot du jour dans les stats (2.5) | petit | contredit ce que l'appli enseigne |
| 6 | Barre d'onglets fixe (2.3), textes et chiffres des stats (2.4) | petit | première impression |
| 7 | Annuler une suggestion au Retour arrière (1.5) | petit | erreur de doigt fréquente |
| 8 | Accueil « aujourd'hui » + rappel de révision (2.1, 2.2) | moyen | fait revenir, fait marcher le carnet |
| 9 | Hauteur des touches, délai d'appui long (1.9) | moyen | personne de référence |
| 10 | Dictionnaire personnel (1.6) | moyen | « le clavier me connaît » |
| 11 | Wierderbuch : nettoyage, état vide, ajout au carnet (2.9) | petit à moyen | |
| 12 | Textes raccourcis, réglages regroupés (2.6, 2.7) | moyen | |
| 13 | Prononciation, interface anglaise, mode sombre (2.9, 2.10, 2.11) | gros | après vérification licence et volume |

Les points 1 à 7 tiennent chacun en un commit et se testent sur l'émulateur ;
ils pourraient former une version corrective à eux seuls.
