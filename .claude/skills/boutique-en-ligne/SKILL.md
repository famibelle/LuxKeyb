---
name: boutique-en-ligne
description: Vendre les boîtes du Wuertduell (et plus tard les produits dérivés) sur le site — choisir entre Stripe Payment Links et Shopify, brancher un paiement sur le site statique GitHub Pages (Buy Button, liens de paiement, interrupteur réservation / paiement), les pages de précommande en quatre langues et leur générateur, les produits et prix décidés, la bascule des réservations Formspree vers la précommande payée, les cookies et la notice des données, l'impression à la demande des produits dérivés. À utiliser quand on parle de Shopify, de Stripe, de boutique en ligne, de panier, de bouton d'achat, de paiement, d'ouvrir la vente, de passer de la réservation à la précommande payée, ou de vendre un mug ou un carnet.
---

# Boutique en ligne — Stripe ou Shopify

Deux notes à la racine, à lire avant de répondre :

- **`BOUTIQUE-CARTES.md`** : le plan écrit, avec Stripe Payment Links, la page
  `docs/commander.md`, le script `scripts/commandes.py` et les obligations
  avant d'ouvrir (§ 8).
- **`BOUTIQUE-SHOPIFY.md`** : l'option Shopify (2026-11-28), ce qu'elle
  change et l'ordre à suivre si elle est choisie.

## 1. Les règles

1. **Rien ne se vend avant le statut, la conformité et les pages légales**
   (`BOUTIQUE-SHOPIFY.md` § 1, `CONFORMITE-JOUET.md`). Préparer le code est
   permis ; l'afficher ou l'activer ne l'est pas.
2. **Le choix entre Stripe et Shopify est celui du propriétaire.** Tant
   qu'il n'a rien dit, le plan reste Stripe (`BOUTIQUE-CARTES.md`). Donner une
   recommandation, pas un état des lieux sans fin.
3. **Pas de prix non décidé.** Seuls 9,90 € la boîte et 34,90 € la
   collection complète sont décidés (2026-11-27, `MODELE-ECONOMIQUE.md`). Le
   présentoir reste « sur demande ». Les produits dérivés n'ont pas de prix.
4. **Les prix et frais de Shopify ou de Stripe se vérifient en ligne** au
   moment d'en parler, et se datent. Ne pas les donner de mémoire.
5. **Le site reste statique** sur GitHub Pages : boutons ou liens de
   paiement, pas de serveur, pas de déménagement du site.
6. **Les pages en/de/pt se régénèrent** avec
   `python3 docs/scripts/generer_precommande.py` ; tout changement de
   `docs/precommande.html` qui touche un texte passe par ses paires
   (français, traduction). Jamais de copie à la main dans les trois pages.
7. **Les adresses des personnes qui ont réservé** ne s'importent pas dans un
   service tiers sans leur accord (`BOUTIQUE-SHOPIFY.md` § 4). Pas d'e-mail,
   de nom ni d'adresse dans le dépôt ni dans une requête externe.
8. **Les textes visibles** suivent `textesPublics` : montrer avant
   d'appliquer, pas de tiret cadratin, espaces insécables, et signaler à
   relire tout texte en/de/pt/lb écrit par Claude.
9. **Pas de modification du workflow GitHub** sans l'accord du propriétaire.

## 2. Comment répondre

- **« Que faire pour intégrer Shopify ? »** Suivre `BOUTIQUE-SHOPIFY.md` § 7
  dans l'ordre ; dire où en est le § 1 avant tout.
- **« Shopify ou Stripe ? »** Le tableau du § 2 de la note : peu de produits
  et une précommande → Stripe ; catalogue, stock, produits dérivés en
  impression à la demande → Shopify.
- **Préparer le branchement** : un emplacement masqué dans
  `docs/precommande.html` (interrupteur comme `mode:` de `docs/commander.md`),
  ajouté au générateur, vérifié en local (Playwright en `file://`, le site en
  ligne est bloqué depuis les sessions cloud) à 360 et 390 px, clair et
  sombre.
- **Ouvrir la vente** : refuser tant que la note de conformité n'est pas
  remplie ; sinon suivre l'ordre (mode test, commande de bout en bout
  remboursée, ouverture, e-mail aux réservations, effacement de la liste).
- **Les codes de l'appli** : avec Stripe, `scripts/commandes.py`. Avec
  Shopify, rien n'existe encore : le dire, et proposer une conception
  (export des commandes, webhook) avant d'écrire du code.

## 3. Tenir les notes à jour

- Une décision du propriétaire s'écrit avec la date dans `BOUTIQUE-SHOPIFY.md`
  (§ 6) et, si elle change le plan, dans `BOUTIQUE-CARTES.md` § 1.
- Un prix ou un frais vérifié en ligne remplace l'hypothèse dans la note,
  avec la date et la source.
- Renvois à relire après un changement : `MODELE-ECONOMIQUE.md` § 3,
  `MERCHANDISING.md` § 4, skills `business-modele`, `asbl-administratif`.
