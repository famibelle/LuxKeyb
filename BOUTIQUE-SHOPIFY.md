# Boutique en ligne : l'option Shopify

Note du 28 novembre 2026, tenue par la skill `boutique-en-ligne`
(`.claude/skills/boutique-en-ligne/`). À la racine, elle n'est publiée ni sur
le site ni dans l'APK. **Rien n'est décidé** : le plan écrit reste celui de
[`BOUTIQUE-CARTES.md`](BOUTIQUE-CARTES.md) (Stripe Payment Links, page
`docs/commander.md` en `mode: "paiement"`). Cette note dit ce qu'il faudrait
faire si le propriétaire choisit Shopify à la place. Les prix de Shopify cités
viennent d'une seule recherche du 2026-11-28 et ne sont pas confirmés.

Voir aussi : [`CONFORMITE-JOUET.md`](CONFORMITE-JOUET.md) (ce qui bloque la
vente d'une boîte), [`MERCHANDISING.md`](MERCHANDISING.md) (les produits
dérivés), [`MODELE-ECONOMIQUE.md`](MODELE-ECONOMIQUE.md) (prix décidés).

---

## 1. Ce qui bloque, avec ou sans Shopify

Shopify n'ouvre aucune porte que Stripe n'ouvre pas. Avant toute vente :

1. **Le statut** : l'asbl créée, un compte bancaire à son nom (skill
   `asbl-administratif`). Le compte Shopify se crée à son nom.
2. **La sécurité des jouets** : essais EN 71, dossier, marquage CE
   ([`CONFORMITE-JOUET.md`](CONFORMITE-JOUET.md)).
3. **Les pages légales** : conditions de vente, rétractation de 14 jours,
   mentions légales, notice des données (`BOUTIQUE-CARTES.md` § 8 et
   l'annexe). Les modèles de Shopify ne sont pas écrits pour le Luxembourg :
   à faire relire.
4. **La TVA** : franchise ou non, envoi vers la France ou l'Allemagne (OSS),
   à régler avant de paramétrer les taxes de Shopify (AED ou comptable).
5. **Shopify Payments au Luxembourg** : **non vérifié**. S'il n'y est pas
   proposé, il faut un autre moyen de paiement (Stripe, PayPal) et Shopify
   prend alors des frais en plus sur chaque vente.

## 2. Shopify ou Stripe Payment Links

| | Stripe Payment Links (plan actuel) | Shopify (plan Starter ou Basic) |
|---|---|---|
| Abonnement | aucun | environ 5 $ par mois pour Starter (non confirmé), plus pour Basic |
| Frais par vente | ceux de Stripe | ceux du paiement, plus des frais Shopify sur Starter ou sans Shopify Payments (à vérifier) |
| Site | reste statique, un bouton par lien | reste statique avec le Buy Button, ou boutique hébergée à part |
| Panier à plusieurs produits | un lien avec articles optionnels | un vrai panier |
| Stock, expéditions, étiquettes | à la main, `scripts/commandes.py` | intégrés |
| Codes de l'appli par commande | `scripts/commandes.py` les envoie | à refaire : appli Shopify, webhook ou export des commandes |
| Impression à la demande (produits dérivés) | non | applis Gelato, Printful… |

**Lecture :** pour deux à quatre boîtes et une précommande, les liens Stripe
suffisent et ne coûtent rien tant qu'on ne vend pas. Shopify devient
intéressant avec un catalogue (boîtes, collection, produits dérivés), du stock
à suivre et beaucoup d'expéditions.

## 3. Comment brancher Shopify sur le site

Le site est en HTML statique sur GitHub Pages : **on ne le déménage pas.**

- **Le Buy Button** (canal de vente de Shopify) : un bout de code par produit
  ou un panier, collé dans `docs/precommande.html`. Le paiement se fait sur la
  page de paiement de Shopify.
- **Les quatre langues** : `docs/precommande-en.html`, `-de.html`, `-pt.html`
  sont produits par `python3 docs/scripts/generer_precommande.py` à partir du
  français. Le code du bouton doit passer dans le générateur, pas être collé à
  la main dans chaque page. Le paiement de Shopify se règle aussi dans ces
  langues.
- **Masqué tant que le § 1 n'est pas rempli** : prévoir un interrupteur dans
  la page (comme `mode:` dans `docs/commander.md`), le formulaire de
  réservation restant affiché tant que la vente n'est pas ouverte.
- **Les produits** à créer, aux prix décidés le 2026-11-27 :

  | Produit | Prix |
  |---|---|
  | Boîte de départ, Schoul et Natur, Niveau A1, Niveau A2 (une fiche par boîte) | 9,90 € |
  | La collection complète, les quatre boîtes | 34,90 € |
  | Présentoir de comptoir | **pas en ligne** : sur demande |

- **Les données** : le code de Shopify pose des cookies et envoie des données
  hors du site. La notice des données de la boutique le dit, et le bandeau de
  consentement si Shopify en impose un.

## 4. La bascule depuis les réservations

Aujourd'hui, les réservations sans paiement passent par Formspree
(`docs/precommande.html`, formulaire `formspree.io/f/mvgwnlky`).

1. Écrire aux personnes qui ont réservé pour leur proposer de payer leur
   réservation, **si le formulaire les prévenait qu'on les recontacterait**.
2. **Ne pas importer leurs adresses dans Shopify** sans leur accord.
3. Garder la règle de `MODELE-ECONOMIQUE.md` § 3 : le tirage part quand les
   paiements reçus couvrent l'avance, pas sur le compteur des réservations.
4. Effacer la liste d'attente après l'e-mail d'ouverture
   (`BOUTIQUE-CARTES.md` § 8.6).

## 5. Les produits dérivés

Les applis d'impression à la demande de Shopify (Gelato, Printful…) vendraient
sans stock le mug ardoise ou le carnet de `MERCHANDISING.md`. Le poster à
gratter et l'enveloppe du mois demandent une fabrication à part. Rien ne se
vend avant l'asbl, et les prix ne sont pas fixés.

## 6. Ce qui reste à décider ou vérifier

| Point | Qui |
|---|---|
| Stripe Payment Links ou Shopify | le propriétaire |
| Shopify Payments disponible au Luxembourg | page des pays de Shopify |
| Prix actuel des plans et frais par vente | page des prix de Shopify, au moment de s'inscrire |
| Comment envoyer les codes de l'appli par commande | à concevoir si Shopify est choisi |
| Précommandes : délai entre paiement et livraison accepté | règles de Shopify et du moyen de paiement |
| Textes de la page en en/de/pt | écrits par Claude : à faire relire |

## 7. Si Shopify est choisi : l'ordre

1. Le § 1 rempli.
2. Compte Shopify au nom de l'asbl, moyen de paiement vérifié, mode test.
3. Produits, prix, livraison (LU, BE, FR, DE), taxes, pages légales.
4. Buy Button dans `docs/precommande.html` via le générateur, masqué.
5. Une commande de bout en bout en test, puis une vraie, remboursée.
6. Ouverture : l'interrupteur de la page, e-mail aux réservations (§ 4).
7. Mettre à jour `BOUTIQUE-CARTES.md` (§ 1, § 2, § 6, § 9) pour dire que
   Shopify remplace Stripe Payment Links.
