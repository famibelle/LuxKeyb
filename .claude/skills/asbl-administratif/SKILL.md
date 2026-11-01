---
name: asbl-administratif
description: Structure juridique et administratif de LuxKeyb — créer l'asbl (loi du 7 août 2023), statuts, dépôt au RCS, compte bancaire, droits sur les visuels et la marque, facturer une commune, TVA et franchise, comptabilité et comptes annuels, assurance, données personnelles (liste d'attente, contacts, photos d'enfants), rangement des pièces hors du dépôt public. À utiliser quand on parle de créer l'association, de statut, de statuts, de facture, de devis, de bon de commande, de TVA, de comptabilité, d'assurance, de RGPD, d'autorisation d'établissement, ou avant toute première recette (animation, vente, subvention).
---

# ASBL et administratif — LuxKeyb

## 0. Pourquoi d'abord

**Sans structure, il n'y a ni facture, ni subvention, ni vente.** Une commune
ne paie une animation que sur facture d'une structure déclarée. Les
subventions communales et la plupart des appels à projets sont réservés aux
associations. La vente des cartes demande un vendeur identifié (Stripe, page
de vente, étui). Cette skill passe donc avant `animations-communes`,
`subventions` et l'ouverture de la boutique (`BOUTIQUE-CARTES.md`).

**Elle ne donne pas de conseil juridique.** Ce qui suit vient de sources
publiques relevées le 2026-11-01, sans lecture des textes officiels, car
`guichet.public.lu` est inaccessible depuis les sessions cloud de ce dépôt. Tout
se fait confirmer avant d'agir. Les guichets à consulter : guichet.lu, le
portail myASBL (myasbl.public.lu), Luxembourg Business Registers (LBR), l'AED
pour la TVA, la House of Entrepreneurship (conseil gratuit) et un comptable.

---

## 1. Choisir la forme

| | ASBL | Indépendant |
|---|---|---|
| Objet | non lucratif : la promotion de la langue luxembourgeoise | commercial |
| Subventions communales, appels à projets | **oui**, en général réservés aux associations | rarement |
| Vente des cartes | possible si elle reste **accessoire** à l'objet (à confirmer) | oui, avec une autorisation d'établissement |
| Les gains | restent dans l'association et servent son objet | reviennent à l'indépendant |
| Payer l'équipe | possible (salaire, prestation), avec des précautions (§ 5) | revenu de l'indépendant |

**Recommandation : l'asbl.** Elle correspond au modèle voulu : l'équilibre,
puis tous les gains réinvestis dans l'appli et les cartes (skill
`business-modele`). Elle donne aussi accès aux financements de la skill
`subventions`. **La décision revient au propriétaire.**

À faire confirmer avant de vendre : une asbl qui vend des boîtes de façon
habituelle a-t-elle besoin d'une **autorisation d'établissement** ? Une
activité commerciale exercée de façon habituelle en demande une en principe
(guichet.lu, ministère de l'Économie). C'est bloquant pour Stripe.

---

## 2. Créer l'asbl

Loi du 7 août 2023 sur les asbl et les fondations, en vigueur depuis le
23 septembre 2023.

1. **Au moins deux membres fondateurs** (trois avant la loi de 2023).
2. **Les statuts**, avec au minimum : la dénomination, **le siège** (la commune
   du siège est celle qui verse la subvention annuelle d'association), l'objet,
   les conditions d'adhésion et de sortie des membres, les organes (assemblée
   générale, conseil d'administration) et l'exercice comptable. Le portail
   myASBL explique les démarches.
3. **L'objet**, proposition à faire relire. Il doit être assez large pour
   couvrir tout ce que fait le projet :
   > Promouvoir l'apprentissage et l'usage de la langue luxembourgeoise,
   > notamment par des outils numériques gratuits, des jeux, des animations et
   > des ateliers pour enfants et adultes. À titre accessoire, éditer et vendre
   > des supports pédagogiques et ludiques, dont les recettes sont entièrement
   > affectées à cet objet.
4. **Le dépôt au RCS** auprès de LBR, avec publication au RESA. Les frais sont
   à vérifier sur lbr.lu.
5. **Le registre des bénéficiaires effectifs (RBE)** : vérifier si l'asbl doit
   s'y inscrire et dans quel délai.
6. **Un compte bancaire au nom de l'asbl.** Il sert à Stripe, aux factures et
   aux subventions. Jamais de recette sur un compte personnel.
7. **Le registre des membres** est tenu au siège, à jour. Il n'est plus déposé
   chaque année au RCS.

### Les droits que l'asbl doit recevoir

Aujourd'hui, les visuels et les cartes appartiennent au propriétaire du projet
(`LICENSE-VISUELS.md`, « tous droits réservés »). Pour que l'asbl vende les
cartes, il faut :

- une **licence écrite** du propriétaire à l'asbl : ce qu'elle peut imprimer et
  vendre, pour combien de temps, gratuitement ou non ;
- une décision sur **qui dépose la marque** au BOIP (skill imprimeur § 6) : le
  propriétaire, qui donne une licence à l'asbl, ou l'asbl elle-même ;
- le nom de **l'asbl comme vendeur et fabricant** sur l'étui, sur la page de
  vente (`fabricant:` dans `docs/commander.md`) et dans Stripe.

---

## 3. Facturer une commune

**Avant l'animation**, obtenir un **bon de commande** ou une confirmation
écrite. Il doit dire qui commande, ce qui est commandé, à quelle date et à
quel prix. Dans une commune, c'est souvent le collège des bourgmestre et
échevins qui engage la dépense : demander qui signe et dans quel délai. Sans
bon de commande, l'animation n'est pas une recette sûre (`business-modele`
§ 6).

**La facture**, après l'animation :

```
[Nom de l'asbl] asbl · [adresse du siège] · RCS Luxembourg F[numéro]
Facture n° [AAAA]-[NNN]                     Date : [JJ/MM/AAAA]

Client : Administration communale de [commune] · [service] · [adresse]
Référence : bon de commande n° [...] du [date]

Animation « Apprendre le luxembourgeois en jouant »
  [date], [lieu], [durée], [public, nombre de participants]      [montant] €

Total : [montant] €
[Mention de TVA : voir § 4, à faire valider par le comptable]

Paiement à [nombre] jours sur le compte [IBAN] ([banque]),
en rappelant le numéro de facture.
```

- Les numéros se suivent sans trou, par année : 2026-001, 2026-002…
- Joindre le **bilan d'une page** (skill `animations-communes` § 8) : il sert
  à la commune pour justifier la dépense.
- Une commune peut mettre **plusieurs semaines** à payer. Il faut en tenir
  compte dans la trésorerie et ne pas engager de tirage sur une facture
  impayée.

---

## 4. La TVA

- Le taux normal au Luxembourg est de 17 %.
- **Régime de franchise des petites entreprises** : sous un seuil de chiffre
  d'affaires annuel (de l'ordre de 50 000 €, **à vérifier auprès de l'AED**),
  on ne facture pas de TVA et on n'en récupère pas. La saison 1 vise 5 000 €
  (`MODELE-ECONOMIQUE.md`), bien en dessous.
- **À demander à l'AED ou au comptable** : faut-il une immatriculation à la TVA
  même en franchise ? Quelle mention porter sur les factures ? Comment traiter
  les ventes en ligne envoyées depuis un autre pays (`BOUTIQUE-CARTES.md` § 8) ?
- Si l'asbl sort de la franchise, le prix public ne change pas et la marge
  baisse (`business-modele` § 3).

---

## 5. Comptabilité, comptes et paiement de l'équipe

- **Au jour le jour**, un registre des recettes et des dépenses, chaque ligne
  avec sa pièce justificative. Les pièces se gardent 10 ans.
- **Les comptes annuels** : une petite asbl établit un état des recettes et des
  dépenses avec une annexe. Le conseil d'administration les approuve dans les
  six mois qui suivent la clôture, et ils sont déposés au RCS dans les sept mois.
  Les seuils des catégories (petite, moyenne, grande) sont à vérifier sur
  myASBL.
- **Les chiffres du modèle** viennent de la skill `business-modele` et de
  `scripts/modele.py`. Le registre réel les remplace au fur et à mesure.
- **Payer l'équipe** : une asbl peut employer des personnes ou payer des
  prestations. Payer un administrateur ou un membre fondateur demande des
  précautions (conflit d'intérêts, contrat écrit, décision du conseil). À
  organiser avec un comptable **avant** le premier paiement. Le plafond de
  risque personnel et la réserve restent ceux de `business-modele` § 6.

---

## 6. L'assurance

- **Responsabilité civile de l'asbl** pour les animations publiques avec des
  enfants : à souscrire avant la première animation.
- **Le matériel** (tablette, téléphone de démonstration, cartes) : à vérifier
  dans le même contrat.
- **L'assurance de l'organisateur** : à chaque événement, demander si
  l'assurance de la commune ou de l'organisateur couvre les intervenants, et en
  demander l'attestation.
- Les primes d'assurance entrent dans les charges annuelles de
  `scripts/modele.py` (`charges_annuelles`).

---

## 7. Les données personnelles

| Données | Règle |
|---|---|
| **Liste d'attente** (e-mail, pays, intérêt) | consentement au moment de l'inscription, un seul e-mail à l'ouverture, puis effacement (`BOUTIQUE-CARTES.md` § 8). Sur papier, la feuille porte la même phrase. |
| **Contacts dans les communes** (nom, fonction, e-mail, téléphone) | dans `prospection/` seulement (§ 8), jamais dans le dépôt public |
| **Photos d'enfants** | jamais sans **autorisation écrite des parents**. Par défaut, photographier les mains et les cartes, pas les visages. |
| **Listes de présence** (pour un bilan ou une subvention) | compter les participants plutôt que les nommer. Si un financeur exige des noms, les garder dans `administratif/` et les effacer après le contrôle. |
| **Clients de la boutique** | voir `BOUTIQUE-CARTES.md` § 8 |

Tenir une page simple qui dit ce que l'asbl garde, pourquoi et combien de
temps. L'autorité de contrôle est la CNPD.

---

## 8. Où ranger les pièces

**Jamais dans le dépôt : il est public.** Deux dossiers sont ignorés par git
(`.gitignore`), comme `impression/` :

```
administratif/   statuts signés, extraits RCS, factures, contrats, attestations
                 d'assurance, comptes, dossiers de subvention, autorisations photo
prospection/     suivi des contacts et des animations (skill animations-communes)
```

Avant tout commit, vérifier avec `git status` que rien de ces dossiers n'y
figure. Le propriétaire en garde une copie hors du conteneur : une session
cloud est effacée après usage.

---

## 9. Le calendrier

| Quand | Quoi |
|---|---|
| Novembre 2026 | Choisir la forme. Trouver le deuxième membre fondateur. Écrire les statuts et les faire relire. |
| Novembre–décembre | Dépôt au RCS, compte bancaire, RBE si nécessaire, assurance RC. Licence des visuels du propriétaire à l'asbl. Questions de TVA et d'autorisation d'établissement posées à l'AED et à la House of Entrepreneurship. |
| Dès l'asbl créée | Premières factures d'animation (skill `animations-communes`). Demande de subvention annuelle à la commune du siège (skill `subventions`). |
| Avant d'ouvrir la boutique | Autorisation d'établissement si elle est nécessaire, essais EN 71, conditions de vente (`BOUTIQUE-CARTES.md` § 8 et § 11). |

---

## 10. Comment répondre

- Toujours dire ce qui est **vérifié**, et par quelle source, et ce qui est
  **à faire confirmer**, et auprès de qui.
- Ne jamais remplir un document officiel ni une page de vente avec des données
  réelles (nom, adresse, IBAN, RCS) dans le dépôt public. Les modèles gardent
  leurs `[crochets]`.
- Renvoyer aux montants de la skill `business-modele` plutôt que de recalculer.
- Répondre en français par défaut.

Sources relevées le 2026-11-01 : myasbl.public.lu (FAQ, nouveautés de la loi
de 2023), mj.gouvernement.lu (associations sans but lucratif), pwc.lu (nouvelles
obligations comptables), guichet.public.lu (autorisation d'établissement, non lu
dans le détail).
