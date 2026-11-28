---
name: conformite-jouet
description: Sécurité des jouets pour les cartes du Wuertduell, leurs étuis et le présentoir — savoir si le produit est un jouet (directive 2009/48/CE, moins de 14 ans), l'âge annoncé, les essais EN 71-1, -2 et -3, les certificats des matériaux à demander à l'imprimeur (encres, pelliculage, dorure), l'évaluation de la sécurité, le dossier technique, la déclaration UE de conformité, le marquage CE, le numéro de lot, les avertissements et leurs langues, les mentions du fabricant (GPSR) sur l'étui et la page de vente, le présentoir et les produits dérivés. À utiliser quand on parle de EN 71, de CE, de norme, de sécurité des jouets, de laboratoire, d'âge sur la boîte, d'avertissement, de fabricant, de GPSR, quand on prépare un devis d'imprimeur ou un BAT d'étui, ou avant de vendre ou de donner une seule boîte.
---

# Conformité jouet — cartes, étuis, présentoir

L'état du dossier, les points ouverts et la liste à cocher sont dans
**`CONFORMITE-JOUET.md`** à la racine. Cette skill dit comment s'en servir et
quelles règles tenir. Lire la note avant de répondre.

## 0. Ce que cette skill n'est pas

**Pas un avis juridique.** La note du 2026-11-28 a été écrite sans lire les
textes officiels. Toute réponse le rappelle quand elle touche à une
obligation, et renvoie vers ceux qui confirment : le laboratoire d'essais,
l'autorité de surveillance du marché (sans doute l'ILNAS, non vérifié),
guichet.lu, un juriste. Une vérification en ligne faite pendant la session se
date et s'ajoute à la note (§ 11, tableau « ce qui reste à confirmer »).

## 1. Les règles

1. **Le Wuertduell est traité comme un jouet** tant que le propriétaire n'a
   pas décidé autre chose (note § 1). Un âge « dès 16 ans » ne l'en sort pas
   si la présentation vise des enfants.
2. **L'âge annoncé est une décision du propriétaire.** Ne pas en écrire un
   nouveau sans lui ; signaler les endroits incohérents (tableau de la note
   § 1) quand on en touche un.
3. **Le CE ne s'imprime jamais avant** les rapports d'essais et la
   déclaration signée : `MARQUAGE_CE = False` dans `scripts/emballage.py`
   tant que la liste du § 10 n'est pas remplie jusqu'au point 8. C'est la
   règle « rien de provisoire » de `creer-presentoir`.
4. **Aucune pièce du dossier dans le dépôt public** : rapports d'essais,
   déclaration, adresse postale, factures du laboratoire. Rangement : skill
   `asbl-administratif`. Le dépôt ne garde que la note et ses cases cochées.
5. **Une finition sans certificat EN 71-3 ne passe pas** : on l'enlève ou on
   la fait tester. À dire dès qu'une maquette ajoute dorure, vernis ou encre
   spéciale (skills `designer-cartes`, `imprimeur`).
6. **Changer d'imprimeur, de carton ou de finition relance les essais.** Le
   rappeler à chaque nouveau devis.

## 2. Comment répondre

- **« Est-ce qu'on peut vendre / donner une boîte ? »** Non tant que la note
  § 10 n'est pas remplie jusqu'au point 10. Les animations, où les cartes
  sont reprises, et les réservations sans paiement restent possibles (note
  § 8).
- **Un devis d'imprimeur** : joindre le paragraphe de la note § 9, et noter
  dans la réponse les certificats reçus ou manquants.
- **Un BAT d'étui** (`scripts/emballage.py`) : vérifier `EDITEUR`,
  `PAYS_FABRICATION`, le numéro de lot (paramètre **encore absent**, à
  ajouter le jour où l'imprimeur donne son format), `MARQUAGE_CE`, les
  avertissements et leur langue (note § 5).
- **La page de vente** (`docs/commander.md`, `docs/precommande*.html`, ou
  Shopify : `BOUTIQUE-SHOPIFY.md`) : CE, avertissements et fabricant
  visibles avant l'achat (note § 6). Les textes suivent `textesPublics`
  (montrer avant d'appliquer, relecture des langues écrites par Claude).
- **Le présentoir** : pas un jouet ; stabilité et sécurité générale
  seulement, avec le fabricant de PLV (note § 7, skill `creer-presentoir`).
- **Un produit dérivé** (`MERCHANDISING.md`) : demander s'il vise aussi des
  enfants de moins de 14 ans ; si oui, il suit cette skill comme une boîte.
- **Le coût** : l'hypothèse de `MODELE-ECONOMIQUE.md` § 4 (300 à 800 €) se
  remplace par un devis, daté, dans la note et dans le script de la skill
  `business-modele`.

## 3. Tenir la note à jour

- Cocher les cases du § 10 avec la date, sans y mettre de donnée
  administrative (pas de numéro de rapport ni de nom de laboratoire si le
  propriétaire ne le veut pas public).
- Une décision du propriétaire (âge, imprimeur, laboratoire) s'écrit dans la
  note avec la date, puis se reporte dans les endroits du tableau du § 1.
- Après un changement, relire les renvois : `BOUTIQUE-CARTES.md` § 8.5,
  `MODELE-ECONOMIQUE.md` § 3 et § 4, skills `imprimeur` § 9,
  `asbl-administratif` (tableau « avant d'ouvrir la boutique »),
  `creer-presentoir` § 4.
