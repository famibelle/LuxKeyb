# Campagne de financement participatif du Wuertduell : notes

Sujet **mis de côté le 28 novembre 2026** à la demande du propriétaire, pour
être repris plus tard. Ces notes rassemblent ce qui a été dit et décidé pour ne
rien perdre. À la racine, elles ne sont publiées ni sur le site ni dans l'APK.

Les plateformes, commissions et règles citées ont été relevées le 27 novembre
2026. Il faut les revérifier avant de choisir (conditions des plateformes,
House of Entrepreneurship, un comptable).

---

## 1. Ce qui est décidé

- **Le propriétaire veut faire la campagne sur une plateforme de financement
  participatif.** On ne construit pas de site maison : sinon il faudrait gérer
  le paiement, le remboursement si l'objectif n'est pas atteint, les
  conditions de vente et la rétractation de 14 jours. La plateforme fait tout
  cela, en « tout ou rien ».
- **La maquette de la page de campagne est validée** (« très bien », le
  2026-11-28) :
  [`reports/campagne-financement/maquette-campagne.html`](reports/campagne-financement/maquette-campagne.html)
  et sa capture
  [`maquette-campagne.png`](reports/campagne-financement/maquette-campagne.png).
  Les chiffres de collecte y sont fictifs.

## 2. Les préalables, avant tout lancement

1. **Créer l'asbl et ouvrir son compte bancaire** (skill
   `asbl-administratif`). La campagne se lance au nom de l'association. Lancée
   au nom du propriétaire, l'argent arriverait sur un compte personnel, ce qui
   contredit le texte publié « Où va l'argent ? » (`docs/precommande.html`) :
   « Personne ne touche de part des bénéfices » et « deviendra une asbl avant
   la première vente ».
2. **Choisir la plateforme** après avoir vérifié qu'elle accepte une asbl
   luxembourgeoise :
   - **Kickstarter** accepte les créateurs installés au Luxembourg depuis 2015
     ([qui peut l'utiliser](https://help.kickstarter.com/en-us/articles/16236650-who-can-use-kickstarter)).
     Très connu, surtout anglophone.
   - **Ulule** est plus européen et francophone, et sans doute mieux adapté au
     public. Non vérifié : accepte-t-il une asbl luxembourgeoise, et quelle
     est sa commission exacte ?
   - Comparer aussi les frais de paiement, l'envoi des contreparties et les
     langues de la page.
3. **Avoir le devis de l'imprimeur** (skill `imprimeur`) et le coût d'envoi
   par boîte : ils fixent l'objectif et les prix des contreparties.

## 3. L'enchaînement prévu

1. **Maintenant :** réservations sans paiement sur le site (objectif du
   week-end du 28 novembre : 400 boîtes ; seuil affiché : 350).
2. **En parallèle :** l'asbl et son compte.
3. **Le jour du lancement :** un e-mail à toutes les personnes qui ont réservé,
   avec « La campagne est ouverte, confirmez votre réservation ici ». Une
   campagne qui atteint vite une part de son objectif est mise en avant par la
   plateforme. Les 48 premières heures comptent le plus.
   Cet e-mail reste dans ce qui a été promis aux personnes qui ont réservé :
   leur adresse sert « à vous écrire quand l'impression se prépare, pour
   confirmer et payer votre réservation ».
4. **Sur le site :** la page de précommande et `commander.md` renvoient vers la
   campagne pendant qu'elle est ouverte.

## 4. La page de campagne : ce que contient la maquette

- **En haut :** le titre, une phrase d'accroche, une vidéo d'environ une
  minute (le propriétaire qui présente le projet, puis une partie jouée à
  deux), le compteur (montant, objectif, pourcentage, contributeurs, jours
  restants), le bouton « Soutenir le projet », la mention « Tout ou rien » et
  l'association nommée comme porteuse.
- **Le récit :**
  1. « Le luxembourgeois, ça se joue » ;
  2. ce qu'il y a dans une boîte ;
  3. à quoi servira l'argent, en barres (impression, envoi, commission, réserve) ;
  4. les paliers bonus ;
  5. le calendrier ;
  6. qui sommes-nous.
- **Les contreparties :**

  | Contrepartie | Prix | Statut |
  |---|---|---|
  | Merci, nom dans la liste des soutiens | 5 € | proposé |
  | Une boîte | 9,90 € | décidé (2026-11-01) |
  | La collection complète, « Le meilleur prix » | 34,90 € | décidé (2026-11-27) |
  | Le présentoir de comptoir | à fixer | sur demande pour l'instant |
  | Une animation dans une école ou une commune | à fixer | proposé (skill `animations-communes`) |

## 5. Ce qui reste à décider

- **L'objectif.** La maquette met 3 500 €, à peu près 350 boîtes à 9,90 €.
  Il faut le recalculer avec le devis, la commission de la plateforme et
  l'envoi. Un objectif bas, atteint vite, attire davantage de soutiens.
- **Les contreparties proposées :** le « Merci » à 5 €, l'animation, et le
  prix du présentoir.
- **Les paliers bonus de la maquette :** 500 boîtes pour une carte rare offerte,
  800 pour la règle en allemand et en anglais, 1 200 pour une cinquième boîte.
  Chacun coûte de l'argent : le chiffrer avant de le promettre.
- **Le calendrier :** la maquette prend en exemple janvier pour la campagne de
  30 jours, février pour le bon à tirer, mars pour l'impression et avril pour
  l'envoi. Il dépend de l'asbl et du délai de l'imprimeur.
- **Les deux phrases laissées en attente pour « Où va l'argent ? » :**
  - une rémunération éventuelle d'un travail, « décidée à l'avance et rendue
    publique, jamais une part des bénéfices » ;
  - la publication des comptes chaque année sur le site.

## 6. À préparer quand on reprend le sujet

- [ ] Comparatif des plateformes, vérifié sur leurs conditions
- [ ] Budget de la campagne avec le devis réel
- [ ] Texte définitif de la page, en français, puis en allemand, en anglais
      et en portugais (traductions à faire relire)
- [ ] Script de la vidéo d'une minute
- [ ] E-mail de lancement aux personnes qui ont réservé
- [ ] Messages pour les réseaux et la presse (RTL, Wort, Tageblatt,
      Luxembourg Times)
- [ ] Mise à jour des pages du site pendant la campagne
- [ ] Reporter les décisions dans `MODELE-ECONOMIQUE.md` et le skill
      `business-modele`
