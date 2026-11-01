# Modèle économique : l'objectif de chiffre d'affaires de la saison 1

Note du 1er novembre 2026, tenue par la skill `business-modele`
(`.claude/skills/business-modele/`). À la racine, elle n'est publiée ni sur le
site ni dans l'APK. Les chiffres viennent de
`python3 .claude/skills/business-modele/scripts/modele.py --plan` : ce sont des
**estimations sans devis**, à refaire dès qu'un devis, une charge réelle ou un
tarif d'animation est connu.

---

## 1. La proposition

**Objectif de la saison 1, d'ici la Fierkelsmaart (avril 2027) : 5 000 € de
chiffre d'affaires.**

Pourquoi 5 000 € :

- Ce montant couvre **l'équilibre même dans le cas pessimiste** (4 935 €) : le
  lancement, le premier tirage et un an de charges sont payés.
- Dans le cas favorable, il atteint déjà **le palier de sécurité** (4 185 €) :
  une réserve paie le tirage suivant, et personne n'avance d'argent de sa poche.
- C'est à peu près ce que rapporte **le premier tirage vendu en entier**
  (500 boîtes × 9,90 € = 4 950 €). Ça se mesure facilement : on vend ce tirage.

Exemple de répartition, à ajuster :

| Recette | Volume | Chiffre d'affaires |
|---|---|---|
| Boîtes (départ et extension) à 9,90 € | 300 | 2 970 € |
| Animations payées par les communes | 10 à 200 € | 2 000 € |
| **Total** | | **4 970 €** |

Sur 10 à 15 stands et animations, cela fait **20 à 30 boîtes par événement**.
Le tarif de 200 € par animation est une **hypothèse** : il se fixe en demandant
aux communes ce qu'elles paient d'habitude.

## 2. Les trois paliers

| Palier | Ce qui est payé | Chiffre d'affaires | En boîtes à 9,90 € |
|---|---|---|---|
| **1. Équilibre** | lancement (685–1 185 €), tirage de 500 boîtes (1 500–2 250 €), un an de charges (500–1 500 €) | **2 685–4 935 €** | 271–498 (54–100 % du tirage) |
| **2. Sécurité** | + une réserve qui paie le prochain tirage | **4 185–7 185 €** | 423–726 (85–145 % du tirage) |
| **3. Au-delà** | rémunération, puis réinvestissement dans l'appli et les cartes | — | — |

Ce que ces chiffres montrent :

- **Les boîtes seules ne suffisent pas, dans le cas pessimiste.** Au palier 2,
  même tout le tirage vendu laisse 2 235 € à trouver. Ce sont les **animations
  payées** qui comblent cet écart : 11 animations à 200 €, ou 7 à 300 €.
- **Une animation à 200 € rapporte autant que 20 boîtes**, sans stock ni
  impression. C'est la recette la plus sûre de la saison.
- **Une boîte vendue en librairie rapporte 30 à 40 % de moins.** La saison 1
  compte sur la vente directe : stands, écoles, réservations en ligne.

## 3. L'ordre dans lequel l'argent arrive

Le tirage (1 500–2 250 €) se paie **avant** que les boîtes existent. L'ordre
est donc :

1. **Les animations d'abord.** On joue avec des cartes imprimées depuis
   `cartes-imprimables/` ou des prototypes. Il n'y a ni stock ni essais EN 71
   à faire, et la commune paie.
2. **Les réservations sans paiement**, en ligne depuis le 2026-11-01 sur la
   page Commander, et à chaque événement avec un QR code vers cette page.
   Elles mesurent la demande, avec un objectif de 350 boîtes.
3. **Les précommandes payées**, quand le statut est en place. Le tirage part
   quand les paiements reçus et les recettes d'animation couvrent l'avance
   (skill § 4).
4. **La vente des boîtes** aux stands suivants et en ligne.

**Préalable à toute recette, animation comprise : le statut.** Facturer une
commune ou vendre une boîte demande une autorisation d'établissement ou une
asbl (skill `asbl-administratif`). Vendre une boîte demande en plus les essais
de sécurité des jouets (EN 71).

Démarcher les communes et préparer les animations : skill
`animations-communes`. Chercher des financements : skill `subventions`. Seules
les recettes sûres comptent dans le palier 1 : ventes, animations sous bon de
commande, subventions notifiées par écrit.

## 4. Ce qui reste à chiffrer

| Hypothèse | Valeur utilisée | Statut | Comment la remplacer |
|---|---|---|---|
| Coût offset d'une boîte | 3–4,50 € | estimé (skill imprimeur § 7) | devis de 2–3 imprimeurs de l'UE |
| Essais EN 71 | 300–800 € | hypothèse | devis d'un laboratoire |
| Charges d'un an (comptable, assurance, emplacements) | 500–1 500 € | hypothèse | devis du comptable et de l'assureur, tarifs des stands |
| Tarif d'une animation | 100–300 € | hypothèse | demander aux communes, aux bibliothèques, aux maisons relais |
| Nombre d'événements de la saison | 10–15 | hypothèse | calendrier des stands obtenus |
| Rémunération de l'équipe | non fixée | à décider | décision du propriétaire (skill § 6) |

Pour mettre à jour : changer les valeurs dans `SAISON`, `FRAIS_FIXES` et
`PRODUITS` en tête du script, le relancer avec `--plan`, puis recopier les
paliers ici avec la date.
