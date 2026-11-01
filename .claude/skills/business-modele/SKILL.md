---
name: business-modele
description: Modèle économique de LuxKeyb et de ses cartes — équilibre dépenses/recettes (seuil de rentabilité), marge de sécurité de l'équipe, prix des paquets de cartes accessibles aux familles, et réinvestissement des gains dans l'appli et les cartes. À utiliser quand on parle de prix, de tarifs, de coût, de rentabilité, de seuil d'équilibre, de budget, de réserve, de rémunération de l'équipe, de ce qu'on gagne ou perd, de savoir si une boîte ou un paquet est trop cher, ou d'où vient l'argent pour développer l'appli et les cartes.
---

# Business modèle — LuxKeyb et ses cartes

## 0. Le but, en une phrase

**Les recettes couvrent les dépenses, l'équipe vit correctement avec une marge
de sécurité, et tout ce qui reste sert à développer l'appli et les cartes.**
Un prix qui ferme le modèle mais que les parents ne peuvent pas payer échoue
aussi. Les trois contraintes se vérifient ensemble, dans cet ordre :

1. **Équilibre** : dépenses ≤ recettes, chaque mois, réserve comprise.
2. **Marge de sécurité** : l'équipe est à l'aise, et une mauvaise période ne
   met personne en difficulté.
3. **Accessibilité** : le prix d'un paquet de cartes reste dans ce qu'un
   parent accepte de payer pour un enfant.
4. **Réinvestissement** : le surplus va à l'appli et aux cartes, pas à
   l'accumulation sans objet.

Quand deux contraintes se heurtent, la skill le dit explicitement et propose
un arbitrage. Elle ne cache jamais le conflit.

---

## 1. Ce qu'on sait déjà (à vérifier avant chaque décision)

Les chiffres viennent des notes du dépôt. Ce sont des **estimations**, à
refaire avec de vrais devis et à faire confirmer par un comptable.

| Poste | Source | Valeur connue |
|---|---|---|
| Coût livré d'une boîte à l'impression à la demande | `BOUTIQUE-CARTES.md` § 5 | ~18–30 € (impression 12–20 €, port 5–9 €, Stripe ~0,70 €) |
| Marge à ajouter | `BOUTIQUE-CARTES.md` § 5 | ~20 % (colis perdus, rétractations) |
| Boîte en tirage offset (500 ex.) | `skill imprimeur` § 7 | 12–15 € de prix, coût 3–4,5 € à l'unité de tirage — **non tenable à l'unité** |
| Frais de paiement Stripe | `BOUTIQUE-CARTES.md` § 5 | ~1,5 % + 0,25 € par carte UE (à vérifier) |
| Droit de douane forfaitaire (import hors UE) | `BOUTIQUE-CARTES.md` § 4 | 3 € par article, depuis le 1er juillet 2026 |
| Essais de sécurité jouets EN 71 | `BOUTIQUE-CARTES.md` § 8 | quelques centaines d'euros, à confirmer |
| Produits vendus | `BOUTIQUE-CARTES.md` § 3 | boîte de départ (54 cartes) ; extension *Schoul et Natur* (54 cartes) ; coffret plus tard ; pas les paquets de 6 pour l'instant |
| Application | `README.md` | libre d'accès ; aucun prix d'appli noté à ce jour |

Ce qui manque et doit être fourni par le propriétaire avant de chiffrer :

- les dépenses fixes réelles (domaine, compte développeur, comptable, assurance,
  logiciels, stand, déplacements) ;
- le niveau de **rémunération** visé pour l'équipe et la **réserve** voulue ;
- le **prix maximum** qu'un parent accepte pour un paquet, si le propriétaire
  l'a déjà en tête.

Sans ces trois éléments, la skill calcule avec des valeurs marquées
« hypothèse » et ne présente aucun résultat comme acquis.

---

## 2. Le seuil d'équilibre

Formule de base, mensuelle, à maintenir à jour :

```
Dépenses fixes mensuelles  =  charges récurrentes + amortissement des frais
                              de lancement (répartis sur 12 à 24 mois)
Coût variable par boîte    =  coût livré (impression, port, paiement)
                              + 20 % d'aléas
Marge par boîte            =  prix de vente TTC − coût variable − TVA et taxes
Seuil (boîtes / mois)      =  Dépenses fixes mensuelles ÷ Marge par boîte
```

Exemple d'illustration, **avec des chiffres inventés pour la méthode** :
si les dépenses fixes sont de 60 € par mois, que le coût variable est de 25 €
et que le prix est de 35 € TTC, la marge est de 10 € et le seuil de 6 boîtes
par mois. À ne pas reprendre tel quel.

Règles :

- Le seuil se calcule **par produit** et pour l'ensemble. Une boîte à 12 €
  sans marge ne « rapporte » rien même si elle se vend bien.
- Les **frais de lancement** (étude, prototypes, tests EN 71, premiers devis)
  se comptent à part, avec un plan de remboursement par les premières ventes.
- Le modèle à **impression à la demande** ne demande aucun stock : il n'y a
  pas d'avance de tirage à financer. C'est la condition pour rester à
  l'équilibre dès la première vente.

---

## 3. La marge de sécurité de l'équipe

Trois niveaux, à fixer ensemble et à écrire dans cette skill :

1. **Réserve** : un montant égal à **N mois de dépenses fixes** (N à fixer
   par le propriétaire ; une valeur usuelle est 3 à 6 mois). Elle n'est pas
   réinvestie. Elle absorbe une mauvaise saison.
2. **Rémunération de l'équipe** : un niveau défini à l'avance, versé avant
   tout réinvestissement. Tant qu'il n'est pas tenu, le réinvestissement est
   nul.
3. **Surplus** : ce qui reste après 1 et 2.

Tant que la réserve est sous son niveau cible, **le surplus va d'abord à la
réserve**. Si une mauvaise période l'entame, les dépenses de développement
sont suspendues avant de toucher à la rémunération.

Le modèle ne dit jamais à quelqu'un de travailler sans être payé pour que le
projet tienne. Si l'équilibre n'est pas possible sans cela, la skill le
signale comme un problème, pas comme une hypothèse de travail.

---

## 4. Le prix des paquets : accessibles aux familles

**Principe** : un paquet doit coûter assez peu pour qu'un parent l'achète pour
un enfant, sans réflexion budgétaire. La skill ne fixe pas le plafond
elle-même : elle le fait valider par le propriétaire, puis le tient.

Méthode :

1. Fixer un **plafond** par produit (prix TTC pour un parent luxembourgeois ou
   belge). Un point de départ à valider : **la boîte de départ au plus à
   15 €**, l'extension au même prix ou moins. Ce plafond vient de la
   contrainte de la skill imprimeur (boîte à 12–15 €) ; il n'est pas une
   étude de marché.
2. Vérifier que le plafond laisse une **marge positive** avec le coût livré
   réel. Si ce n'est pas le cas, deux leviers seulement :
   - baisser le coût (tirage offset dès 200 boîtes vendues, `BOUTIQUE-CARTES.md`
     § 5 ; port groupé quand la boîte et l'extension partent ensemble) ;
   - **regrouper** les produits plutôt que rehausser le prix de la boîte seule
     (le lot boîte + extension est vendu à un prix unitaire plus bas).
3. Ne jamais compenser un prix trop haut en rognant la qualité du carton ou
   du pelliculage : la skill imprimeur interdit de choisir l'option la moins
   chère.
4. Vérifier le prix **une fois la TVA et les taxes de livraison connues**.
   Un prix affiché sans frais de port, avec des frais cachés à la livraison,
   fait fuir les familles et ouvre un risque de rétractation.

Le prix doit rester **le même pour tous les pays** de vente
(`BOUTIQUE-CARTES.md` § 6), sauf décision contraire écrite ici.

---

## 5. Réinvestissement : appli et cartes

Quand la réserve est pleine et la rémunération tenue, le surplus est affecté
par ordre de priorité, à écrire en pourcentages une fois les chiffres réels
connus :

1. **Développement de l'appli** : corrections, accessibilité, performance du
   clavier, nouvelles fonctions du carnet et des jeux.
2. **Nouvelles cartes** : extensions, coffret complet, recherche et dessin des
   cartes (`skill designer-cartes`, `skill direction-artistique`), tests et
   prototypes d'impression.
3. **Frais de conformité** : essais EN 71, conseil juridique et comptable,
   assurance, déclarations TVA.

Un investissement se justifie par ce qu'il rapporte : une carte ou une fonction
dont personne ne se sert n'est pas un gain. La skill demande, pour chaque
dépense de développement, ce qu'elle doit améliorer et comment on le mesure
(ventes, usage, retours).

---

## 6. Ce que cette skill ne fait pas

- Elle ne donne pas de conseil fiscal ni juridique. La TVA (OSS, seuil de
  10 000 € en vente à distance), les règles de sécurité des jouets et le statut
  de l'entreprise se vérifient avec l'AED, guichet.lu ou un comptable
  (`BOUTIQUE-CARTES.md` § 8).
- Elle ne publie rien : pas de prix sur le site, dans l'appli ou sur Stripe
  sans décision écrite du propriétaire.
- Elle ne compte pas comme acquis un chiffre estimé. Chaque prix ou coût cité
  porte sa source et son statut (estimé, devis reçu, confirmé).

---

## 7. Comment l'utiliser

Quand on demande un calcul ou une décision :

1. Relire § 1 et lister les **hypothèses** utilisées, chacune marquée.
2. Calculer le seuil d'équilibre (§ 2) pour le produit concerné.
3. Vérifier la marge de sécurité (§ 3) et le plafond d'accessibilité (§ 4).
4. Si une contrainte échoue, proposer **un** arbitrage chiffré, pas une liste.
5. Écrire le résultat dans la note de modèle économique (à créer si elle
   n'existe pas : `MODELE-ECONOMIQUE.md` à la racine, sans publication), avec la
   date et la liste des hypothèses.

Format de réponse : le seuil, la marge, le prix retenu, la réserve, le surplus
affecté, puis les hypothèses encore non vérifiées. Pas de tableau de bord
décoratif.
