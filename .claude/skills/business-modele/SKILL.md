---
name: business-modele
description: Modèle économique de LuxKeyb et de ses cartes — prix cibles des pochettes, boîtes et coffrets accessibles aux familles, équilibre dépenses/recettes (seuil de rentabilité), marge de sécurité de l'équipe, financement des tirages par précommande, coût pour compléter une collection, et réinvestissement des gains dans l'appli et les cartes. À utiliser quand on parle de prix, de tarif, de coût, de marge, de rentabilité, de budget, de réserve, de rémunération, de précommande, de subvention, de librairie ou de revendeur, quand on se demande si un paquet, une boîte ou une collection est trop cher pour un parent, ou d'où vient l'argent pour développer l'appli et les cartes.
---

# Business modèle — LuxKeyb et ses cartes

## 0. Le but

**Un parent achète une pochette sans y réfléchir, l'équipe ne prend aucun
risque personnel, les recettes couvrent les dépenses, et tout ce qui reste
sert à développer l'appli et les cartes.**

Quatre contraintes, vérifiées ensemble :

1. **Accessibilité** : le prix tient dans ce qu'un parent paie pour un enfant,
   et une collection se complète sans dépenser des centaines d'euros.
2. **Équilibre** : dépenses ≤ recettes, frais de lancement compris.
3. **Marge de sécurité** : personne n'avance d'argent qu'il ne peut pas perdre,
   et une mauvaise saison ne met personne en difficulté.
4. **Réinvestissement** : le surplus va à l'appli et aux cartes.

Quand deux contraintes se heurtent, la skill le dit et propose **un**
arbitrage chiffré. Elle ne cache jamais le conflit.

---

## 1. La méthode : le prix d'abord, le coût ensuite

Partir du coût et ajouter une marge donne les prix trop hauts du dépôt :
**4 € la pochette de 6** (skill imprimeur § 7), **12–15 € la boîte**, et
l'exemple de `docs/commander.md`, **« 29 €, livraison comprise »**. C'est le
prix d'un cadeau, pas celui d'un achat de parent.

La méthode est l'inverse : **fixer le prix qu'une famille accepte, puis
chercher la production et le canal qui le tiennent.** Si rien ne le tient,
le produit ne se fait pas encore, ou il se fait autrement (§ 5, § 7). On ne
remonte pas le prix.

Repères à relever **en magasin au Luxembourg** avant de fixer un prix (ordres
de grandeur de mémoire, non vérifiés) : pochette d'autocollants Panini autour
de 1 €, booster de cartes à collectionner du commerce autour de 5–6 €, jeu
de cartes familial (sept familles, Uno, Dobble) entre 5 et 15 €.

---

## 2. La gamme et les prix cibles

Propositions du 2026-11-01, **à valider par le propriétaire**. Une fois
validées, elles remplacent les prix de la skill imprimeur § 7, de
`BOUTIQUE-CARTES.md` § 5 et de `docs/commander.md` : les mettre à jour
ensemble, jamais l'un sans l'autre.

| Produit | Prix cible | Prix noté aujourd'hui | Pour qui |
|---|---|---|---|
| Appli, carnet, jeux | **gratuit** | gratuit | tout le monde |
| PDF à imprimer soi-même (`cartes-imprimables/`) | **gratuit** | gratuit | familles, classes ; usage personnel et scolaire (`LICENSE-VISUELS.md`) |
| Pochette de 6 cartes | **2 €** | 4 €, 3 pour 10 € | l'argent de poche, la surprise, l'échange |
| Boîte de 54 (départ, Schoul et Natur, Niveau A1, Niveau A2) | **10 €** | 12–15 € (offset), ~29 € livrée (à la demande) | jouer tout de suite, à deux |
| Coffret complet | **29 €** | 35–45 € | écoles, collectionneurs, ceux qui veulent tout |
| Lot école | **prix public − 30 %** (la commission qu'une librairie aurait prise) : 20 pochettes pour 28 € | 20 pochettes pour 60 € | les classes |
| Édition luxe (dorure, étui rigide) | **libre** | — | adultes, cadeaux, boutiques souvenirs (§ 7) |

**Le gratuit fait partie du modèle** : aucune famille n'est exclue du jeu.
Ce qui se vend, c'est le vrai carton, la boîte prête à jouer et la carte code
qui verse les cartes dans le carnet.

**Un seul prix public par produit**, le même au stand, en librairie et en
ligne, port à part. Les prix cibles ont été choisis pour rester positifs dans
tous les canaux en offset (§ 3).

---

## 3. Ce que ces prix exigent

Résultat de `scripts/modele.py` du 2026-11-01, hypothèses de la skill
imprimeur § 4 et § 7 (estimations, **aucun devis**). Les frais fixes de
lancement (685–1 185 €, essais EN 71 compris) sont imputés **en entier** à
chaque produit : c'est le pire cas.

| Produit | Coût offset | Marge directe | Marge librairie | Avance du tirage | Équilibre |
|---|---|---|---|---|---|
| Pochette à 2 € | 0,60–0,80 € | 1,20–1,40 € | 0,40–0,80 € | 600–800 € (1 000) | 642–992 vendues |
| Boîte à 10 € | 3–4,50 € | 5,50–7 € | 1,50–4 € | 1 500–2 250 € (500) | 218–344 vendues |
| Coffret à 29 € | 8–15 € | 14–21 € | 2,40–12,30 € | 2 400–4 500 € (300) | 106–196 vendus |

Ce qu'il faut en retenir :

- **L'offset est la condition des prix familiaux.** À la demande, la pochette
  à 2 € perd de l'argent en librairie, la boîte à 10 € n'en gagne nulle part
  et le coffret à 29 € en perd partout. L'impression à la demande reste pour
  les prototypes et pour mesurer la demande.
- **Les planchers testés** : la pochette à 1,50 € perd en librairie et ne
  s'équilibre qu'au-delà du tirage entier ; la boîte à 8 € ne gagne rien en
  librairie. **2 € et 10 € sont les plus bas tenables** avec ces coûts.
  Ils baisseront avec un vrai devis ou un tirage plus grand : relancer le
  calcul.
- **La librairie prend 30–40 %.** C'est le canal le plus mince : il se
  garde pour la visibilité, pas pour la marge.
- **Le port pèse autant que le produit.** Une pochette ne se vend pas seule
  en ligne. En ligne, on vend la boîte et le coffret, port à part. Pour les
  pochettes, ce sont le stand, les écoles et les librairies.
- **La TVA** : en franchise, rien à reverser. Si l'activité en sort (seuil à
  vérifier sur guichet.lu), le prix TTC reste le même et 17 % de TVA sont à
  reverser : une boîte à 10 € ne rapporte plus que 8,55 €, pour un coût
  offset de 3 à 4,50 €. Elle reste bénéficiaire.

---

## 4. Financer le tirage sans risque : la précommande

L'offset demande d'avancer 600 à 4 500 € par produit. L'impression à la demande
évite cette avance mais coûte deux à trois fois plus. Pour concilier les deux,
**c'est la précommande qui paie le tirage** :

1. La page Commander prend des précommandes au **prix cible**, avec un délai
   annoncé. La liste d'attente actuelle en est la première étape : elle mesure
   la demande.
2. Le tirage part quand les précommandes, plus la trésorerie hors réserve,
   couvrent **l'avance du tirage et les frais fixes du produit**. Le seuil de
   lancement s'écrit à l'avance.
3. Si le seuil n'est pas atteint à la date dite, **tout le monde est
   remboursé**. Personne n'a avancé d'argent.
4. Les écoles et les communes commandent par lots : un lot école commandé à
   l'avance compte comme une précommande.

À faire confirmer avant d'ouvrir (Code de la consommation, `BOUTIQUE-CARTES.md`
§ 8) : le délai de livraison annoncé, le remboursement si le tirage n'a pas
lieu, et la rétractation de 14 jours. Une plateforme de financement
participatif en mode « tout ou rien » fait la même chose, contre une
commission à vérifier.

Ce choix modifie la décision du 2026-10-28 (impression à la demande pour la
boutique, `BOUTIQUE-CARTES.md` § 1). **Le propriétaire tranche** : la skill
présente l'écart, elle ne réécrit pas la note seule.

---

## 5. Les pochettes : la surprise, pas le chemin pour compléter

Le prix d'une pochette ne suffit pas : ce qui coûte aux parents, c'est de
**compléter**. Simulation `scripts/modele.py --collection`, pochettes à 2 €,
composition 3 communes, 2 peu communes, 1 rare ou mieux (imprimeur § 7) :

| Édition | Seul | 2 enfants qui échangent | 4 qui échangent |
|---|---|---|---|
| Les 248 cartes | ~257 pochettes, **~514 €** | ~178, ~356 € par enfant | ~132, ~263 € par enfant |
| Une édition de 54 cartes | ~40 pochettes, ~80 € | ~30, ~60 € par enfant | ~23, ~46 € par enfant |

Ce sont des montants médians. Pour un enfant sur dix, il faut compter un tiers à
moitié de plus. Même à 2 €,
**compléter les 248 cartes par pochettes n'est pas acceptable**.

Règles :

- **Le chemin pour tout avoir, c'est la boîte ou le coffret**, jamais les
  pochettes. La pochette le dit : « Toutes les cartes : le coffret ».
- **Des éditions de pochettes courtes**, d'environ 54 cartes, plutôt qu'une
  seule collection de 248. Cela change le plan de la skill imprimeur § 7, où
  les pochettes puisent dans les 248 cartes : à valider par le propriétaire.
- **Les chances sont imprimées** sur la pochette (déjà prévu, imprimeur § 7).
- **L'échange réduit le coût d'un quart à la moitié** : la cession par QR de
  l'appli (`CESSION-CARTES.md`) et le coin « Je cherche / Je donne » du stand
  font partie du modèle, pas seulement du jeu.
- **La rareté ne donne jamais de force** (déjà la règle) : un enfant qui ne
  peut pas acheter de pochettes joue à égalité avec une boîte.

---

## 6. Équilibre et marge de sécurité de l'équipe

Le calcul se fait par produit, puis pour l'ensemble :

```
Frais fixes        = lancement (marque, prototypes, maquettes, EN 71…)
                     + charges récurrentes (comptable, assurance, domaine…)
Marge unitaire     = prix × (1 − commission du canal) − frais de paiement − coût
Équilibre offset   = (frais fixes + avance du tirage) ÷ prix
Équilibre à la demande = frais fixes ÷ marge unitaire
```

`scripts/modele.py` le fait pour chaque produit et chaque canal. Mettre ses
hypothèses à jour (source et statut de chaque valeur) plutôt que recalculer à
la main.

**La marge de sécurité**, trois niveaux à fixer par le propriétaire et à écrire
ici :

1. **Plafond de risque personnel** : le montant maximum qu'un membre de
   l'équipe accepte d'avancer de sa poche. La valeur par défaut est **0 €**. Les
   tirages se financent par précommande (§ 4) ou par la trésorerie du projet.
2. **Réserve** : N mois de charges récurrentes plus le coût d'un retirage de
   secours (colis perdus, défaut d'impression). Valeur usuelle : 3 à 6 mois.
   La réserve ne se réinvestit pas.
3. **Rémunération** : un montant défini à l'avance, compté comme une charge
   fixe dès qu'il est décidé. Tant qu'il n'est pas tenu, le réinvestissement
   est nul.

L'ordre du surplus : réserve, puis rémunération, puis réinvestissement. Si une
mauvaise période entame la réserve, le développement s'arrête avant la
rémunération. Le modèle ne repose jamais sur du travail gratuit présenté comme
une hypothèse : si l'équilibre en dépend, la skill le signale comme un
problème.

---

## 7. Qui paie un peu plus, pour que les enfants paient moins

Les prix bas des pochettes et des boîtes se tiennent mieux si d'autres
recettes portent une part des frais fixes :

- **L'édition luxe**, avec dorure, étui rigide et variantes numérotées
  (imprimeur § 2 et § 7), pour les adultes, les cadeaux et les boutiques
  souvenirs. La qualité de base (carton carte à jouer, toilé) ne baisse pas.
  Seuls les ornements passent dans l'édition luxe.
- **Les adultes qui apprennent le luxembourgeois** (résidents, frontaliers,
  candidats à la nationalité) : le coffret et les boîtes Niveau A1 et A2 leur
  parlent, à un prix qui n'a pas besoin d'être celui de l'argent de poche.
- **Les subventions et le mécénat** pour la promotion de la langue, pistes
  **à vérifier** (éligibilité, montants, calendrier) : ministère de
  l'Éducation nationale, ministère de la Culture, Fonds culturel national,
  Œuvre Nationale de Secours Grande-Duchesse Charlotte, communes (écoles,
  maisons relais). Une subvention baisse le prix du lot école ou finance un
  tirage. Elle ne paie pas la rémunération de façon durable.
- **Les commandes groupées** des écoles et des communes, à l'avance (§ 4).

---

## 8. Ce que les licences interdisent comme recettes

- **L'appli ne peut pas devenir payante telle quelle.** Elle embarque des
  dictionnaires sous **CC BY-NC** (`NOTICE.md`). Une appli payante, des achats
  intégrés ou de la publicité demandent l'accord des ayants droit (contact :
  <ai@rtl.lu>) ou le remplacement de ces données. Jusque-là, l'appli
  reste gratuite et l'argent vient des cartes.
- **Les cartes vendues ne prennent que du contenu LOD (CC0)** : mots, sens,
  phrases et traductions du ZLS. Ni les phrases RTL de Wuertlück, ni les raretés
  dérivées de `luxemburgish_dict.json` (imprimeur § 7).
- Les visuels sont au projet (`LICENSE-VISUELS.md`) : leur vente est libre
  pour le projet et interdite aux autres.

---

## 9. Réinvestissement

Une fois la réserve pleine et la rémunération tenue, le surplus va, dans
l'ordre :

1. **Baisser les prix** quand un tirage plus grand ou un meilleur devis le
   permet. Le premier bénéficiaire du surplus, ce sont les familles.
2. **Développer l'appli** : corrections, accessibilité, performance du clavier,
   carnet et jeux.
3. **De nouvelles cartes** : boîtes, éditions de pochettes, dessins (skills
   `designer-cartes` et `direction-artistique`), prototypes.
4. **La conformité** : essais EN 71, comptable, assurance, juriste.

Chaque dépense de développement dit ce qu'elle doit améliorer et comment on le
mesure (ventes, usage, retours).

---

## 10. Ce que cette skill ne fait pas

- Elle ne donne pas de conseil fiscal ni juridique. TVA, statut, sécurité des
  jouets et précommandes se font confirmer par guichet.lu, l'AED, la House of
  Entrepreneurship ou un comptable (`BOUTIQUE-CARTES.md` § 8).
- Elle ne publie aucun prix (site, Stripe, appli, pochette) sans décision
  écrite du propriétaire.
- Elle ne présente jamais une estimation comme un devis. Chaque chiffre porte
  sa source et son statut.

---

## 11. Comment l'utiliser

1. Mettre à jour les hypothèses en tête de `scripts/modele.py` si un devis ou
   un prix réel est connu (statut `devis` ou `confirmé`).
2. Lancer `python3 .claude/skills/business-modele/scripts/modele.py` (ajouter
   `--collection` pour les pochettes, `--prix pochette=1.5 boite=8` pour tester
   un prix).
3. Vérifier les quatre contraintes du § 0 dans l'ordre. **Commencer par le
   prix** (§ 1) : c'est la production qui s'adapte.
4. Si une contrainte échoue, proposer **un** arbitrage chiffré : changer de
   mode d'impression, de canal, de taille d'édition, financer par
   précommande, ou ne pas faire le produit maintenant.
5. Écrire la décision dans `MODELE-ECONOMIQUE.md`, à la racine. Si la note
   n'existe pas, la créer. Elle ne déclenche ni le build APK ni le site. Y
   mettre la date, les hypothèses et la sortie du script. Signaler ensuite
   les notes à aligner : skill imprimeur § 7, `BOUTIQUE-CARTES.md` § 5,
   `docs/commander.md`.

Format de réponse : le prix retenu, la marge par canal, l'avance et le seuil
de lancement, le coût pour compléter si c'est une pochette, la réserve, puis
les hypothèses encore non vérifiées.
