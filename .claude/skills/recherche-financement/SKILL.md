---
name: recherche-financement
description: Chargé de chercher des fonds pour LuxKeyb et ses cartes — chiffrer ce qu'il faut financer, cartographier toutes les sources (fondations, banques et entreprises, financement participatif, prix, programmes européens, dons, en plus des subventions publiques de la skill subventions), qualifier chaque piste, tenir le suivi hors du dépôt public, faire la veille mensuelle, préparer les approches (lettre à une fondation, proposition de partenariat, campagne participative) et dire quoi faire ensuite. À utiliser quand on demande « où trouver de l'argent », « qui peut nous financer », « fais la veille des financements », « prépare une demande à une fondation ou à une entreprise », « faut-il lancer un financement participatif », ou pour faire le point sur les pistes en cours.
---

# Recherche de financement — LuxKeyb

## 0. Le rôle

**Trouver, qualifier et suivre les sources d'argent, du plus sûr au moins sûr,
et dire chaque fois quelle est la prochaine action.** Cette skill ne remplace
pas les autres :

| Besoin | Skill |
|---|---|
| combien il faut, ce qui compte comme recette | `business-modele` |
| subventions publiques : dossier, budget, rapport | `subventions` |
| animations facturées aux communes | `animations-communes` |
| asbl, factures, TVA, assurance, données personnelles | `asbl-administratif` |

Celle-ci couvre **tout le reste** et le **suivi de toutes les pistes**,
publiques comprises : le tableau de bord, la veille, les approches vers les
fondations, les entreprises et le public.

**Ce qu'elle ne fait jamais :**

1. **Envoyer, déposer ou signer quoi que ce soit.** Elle prépare, le
   propriétaire envoie.
2. **Compter une piste comme une recette.** Seuls comptent les montants
   notifiés par écrit (`business-modele` § 6).
3. **Inventer un montant, une échéance ou un contact.** Tout chiffre porte sa
   source et sa date, ou « non relevé ».
4. **Écrire un contact personnel dans le dépôt public** (§ 4).

---

## 1. Ce qu'il faut financer

Se baser sur `MODELE-ECONOMIQUE.md` et `scripts/modele.py --plan`, jamais sur
des chiffres recopiés ici. Au 2026-11-02 (estimations, aucun devis) :

- **Palier 1, l'équilibre de la saison 1** : 2 685 à 4 935 € de chiffre
  d'affaires (lancement, tirage de 500 boîtes, un an de charges).
- **Le manque à financer** = palier 1 − recettes sûres déjà acquises. Au
  départ, les recettes sûres sont nulles : le manque est le palier entier.
- **Le poste qui pèse le plus** : l'avance du tirage, 1 500 à 2 250 €, payée
  avant que les boîtes existent. Les précommandes payées la couvrent
  (`business-modele` § 4). Un financement externe sert à **ne pas dépendre du
  seuil** de précommandes ou à avancer l'impression.

**Ce qu'un financement peut payer** : un tirage, des jeux offerts aux écoles et
aux bibliothèques, un cycle d'animations, les essais de sécurité des jouets, le
développement d'une fonction de l'appli. **Ce qu'il ne doit pas payer** :
la rémunération de l'équipe, durablement (`business-modele` § 6).

**Avant de chercher, écrire en une phrase** à quoi sert chaque euro demandé et
quel résultat en sort. Un dossier sans cela se refuse.

---

## 2. La carte des sources

Relevé du 2026-11-02, recherches en ligne. Les pages officielles de plusieurs
sites (`fdlux.lu`, `guichet.public.lu`, `mfsva.gouvernement.lu`) sont
inaccessibles depuis les sessions cloud de ce dépôt : **tout se vérifie sur la
page officielle avant d'agir**. La colonne « Adéquation » est mon appréciation.

### Fondations

| Source | Ce qu'elle finance | Montants, conditions relevés | Adéquation |
|---|---|---|---|
| **Fondation de Luxembourg** (plateforme de fondations abritées) | cinq grands thèmes dont l'éducation universelle ; page « soumettre un projet » | non relevés | à explorer : **premier contact, une question** : quelle fondation abritée correspond ? |
| **Fondation Enovos** (abritée) | appel pour des projets sociaux | association au siège au Luxembourg ; cohésion sociale ou lutte contre la pauvreté ; **2 000 à 5 000 €** | moyenne : le projet touche la cohésion (apprendre la langue du pays), pas la pauvreté. À présenter par l'angle de l'accueil des nouveaux habitants. |
| **Spuerkeess Foundation** (abritée, 2025–2029, banque Spuerkeess) | jeunesse, éducation financière, patrimoine artistique du Luxembourg ; deux appels vus, tous deux artistiques (15 000 € de soutien curatorial ; 30 000 € par an de production photographique) | éligibilité des associations non confirmée ; spuerkeessfoundation@fdlux.lu | faible à moyenne : demander s'il existe une ligne jeunesse |
| **André Losch Fondation** | enfants et jeunes de 0 à 30 ans : inclusion sociale, éducation, santé | « pas de projet pour le moment » | moyenne : **guetter** l'ouverture d'un appel |
| **Fondation Sommer** | appel à projets pédagogiques pour enfants, familles | dernier appel clos le 27 novembre 2023 | bonne : **guetter** le suivant |
| **Œuvre Nationale de Secours Grande-Duchesse Charlotte** | projets d'intérêt général ; appels thématiques | 33 M€ versés en 2025 ; ancien appel à 5 000 € par projet | voir la skill `subventions` |

### Banques et entreprises

| Source | Relevé | Adéquation |
|---|---|---|
| **Spuerkeess** | passe par la Spuerkeess Foundation (ci-dessus) | voir plus haut |
| **BGL BNP Paribas** | engagement de mécénat, programme « Coup de Pouce » (2011, 14 associations, ~39 720 € au total) : sources anciennes | à vérifier |
| **POST, BIL, Raiffeisen, entreprises du secteur de l'éducation, de l'édition, des télécoms** | rien de relevé | à explorer par prospection directe |

### Public, en ligne

| Source | Relevé | Adéquation |
|---|---|---|
| **Financement participatif** : **Nubs** (plateforme luxembourgeoise, contreparties non financières, projets vérifiés avant mise en ligne), **Eticrowd** (projets à engagements sociaux, écologiques ou culturels ; article ancien, statut à vérifier) | frais de Nubs non relevés ; fourchette générale de 3 à 10 % des fonds levés | **voir § 6 : à ne lancer que si les précommandes ne suffisent pas** |
| **Dons de particuliers** | cadre fiscal (déductibilité) non relevé | à demander au comptable avant de recevoir un don (`asbl-administratif`) |

### Prix et concours

Trois concours trouvés sont clos ou hors sujet (innovation industrielle de la
FEDIL, excellence en ingénierie de la Fondation Enovos). **Pas de piste
immédiate.** Garder dans la veille : les prix d'économie sociale et d'éducation.

### Programmes européens

**Erasmus+** (partenariats de coopération à petite échelle) fonctionne avec des
montants forfaitaires (30 000 ou 60 000 € pour les petits partenariats, selon
des sites tiers non officiels) et **exige un cofinancement et des partenaires
d'autres pays**. **CERV** : rien de vérifié. Trop lourd pour la saison 1 : à
reconsidérer pour la saison 2, avec un partenaire étranger.

### Publiques

Communes, ministères, LEADER, ZLS : voir la skill `subventions` § 1.

---

## 3. Qualifier une piste

**Fiche d'opportunité**, avant d'y consacrer du temps :

| Question | Pourquoi |
|---|---|
| Qui finance, quel objet, quel public ? | éviter les dossiers hors sujet |
| L'asbl est-elle éligible aujourd'hui ? | sinon : `asbl-administratif` d'abord |
| Montant, ou fourchette ? Cofinancement exigé ? | la piste vaut-elle le dossier ? |
| Date limite, délai de réponse, date du versement ? | un financement tardif ne paie pas un tirage |
| Que doit-on rendre (rapport, mention, logo, exclusivité) ? | le coût caché |
| Est-ce compatible avec nos règles (§ 5) ? | pas de publicité dans l'appli, vie privée, licences |
| Adéquation de 1 à 5 | le tri |

**Aller ou ne pas aller.** Valeur attendue = montant × chance estimée.
Hypothèses de départ, à corriger avec l'expérience : 0,8 pour une subvention
communale d'association, 0,3 pour un appel avec bon encaissement, 0,1 pour un
concours. **Compter au moins 50 € de valeur attendue par heure de dossier.** En
dessous, on passe, ou on réutilise un dossier déjà écrit.

**Les cofinancements** : tout financeur veut savoir qui d'autre paie. Il est
normal de les empiler, à condition de les déclarer et de ne jamais financer
deux fois la même dépense.

---

## 4. Le suivi

Le tableau de bord est `administratif/financement/pipeline.csv`, **ignoré par
git** (skill `asbl-administratif` § 8). Il contient des contacts : ce sont des
données personnelles. Pour le créer :

```bash
mkdir -p administratif/financement
cp .claude/skills/recherche-financement/modeles/pipeline.csv administratif/financement/
```

Les statuts, dans l'ordre : `a-qualifier`, `qualifie`, `contacte`,
`dossier-en-cours`, `depose`, `accepte`, `notifie`, `recu`, `refuse`,
`ferme-guetter`.

- **`notifie` et `recu`** sont les seuls statuts qui entrent dans
  `scripts/modele.py` (`business-modele` § 6). Les autres sont de l'espoir.
- **`ferme-guetter`** : l'appel est clos, mais il revient (Fondation Sommer,
  Fondation Enovos, Losch). Noter la date de la prochaine vérification.
- Le propriétaire garde une copie hors du conteneur : une session cloud est
  effacée après usage.

**Avant un commit**, `git status` ne doit montrer aucun fichier de
`administratif/`.

---

## 5. Les règles d'indépendance

À tenir pour **tout** financeur privé, et à écrire dans chaque convention :

- **Pas de publicité dans l'appli.** Elle n'en affiche aucune à ce jour
  (`fichePlayStore.md` : classification sans publicité ; aucun module
  publicitaire dans le build). **La mention « sans publicité » a été retirée
  des captures le 2026-10-20** : ne pas la promettre dans un dossier sans
  l'accord du propriétaire. Exposer un sponsor dans l'appli est sa décision,
  jamais un argument de négociation.
- **Aucun logo de sponsor sur les faces des cartes.** Le jeu reste un jeu.
  Les contreparties vont ailleurs (§ 7).
- **Aucune influence sur le contenu** : les mots, les sens et les phrases
  viennent du LOD (CC0) et du ZLS, pas d'un sponsor.
- **Aucun accès aux données** : rien de ce que les gens tapent ne quitte leur
  téléphone, et les réservations ne se partagent pas (`asbl-administratif`
  § 7).
- **Pas d'exclusivité** sans décision écrite du propriétaire.
- **Licences** : l'appli embarque des données CC BY-NC (`NOTICE.md`). Un
  financement qui supposerait une appli payante ou commerciale est exclu tant
  que ces données n'ont pas l'accord de leurs ayants droit.
- **Cartes, visuels et marque** : un financeur n'en devient jamais
  propriétaire (`LICENSE-VISUELS.md`).

---

## 6. Faut-il lancer un financement participatif ?

**Non, pas avant d'avoir essayé la précommande sur la page propre.** La page
Commander prend déjà des réservations, puis des précommandes payées avec
remboursement si l'objectif n'est pas atteint (`BOUTIQUE-CARTES.md` § 9).
C'est le même mécanisme qu'un financement participatif, sans commission de
plateforme, avec les seuls frais de Stripe (~1,5 % + 0,25 €, à vérifier).
Une plateforme prend en général 3 à 10 % des fonds levés (fourchette
générale, frais de Nubs non relevés).

**Une campagne a du sens si** la page de précommande n'atteint pas son objectif
et que l'on veut **de l'audience** : une plateforme locale apporte un public et
une crédibilité (projets vérifiés), pas seulement un paiement.

Si on la lance, le plan minimum :

1. **Les prix, depuis la skill `business-modele`** : la boîte à 9,90 € comme
   palier de base, un palier « boîte offerte à une école » (le prix d'une
   boîte, remise à une classe de la commune du donateur), un palier « jeu
   offert à une bibliothèque ».
2. **Un objectif réaliste et un plancher** : sur la plateforme, en général
   « tout ou rien ». L'objectif ne dépasse pas le palier 1 de la saison.
3. **Une vidéo ou un diaporama de 60 secondes** et la page de règle du jeu.
4. **Une communauté prête avant le lancement** : les participants des
   animations, les communes contactées, les inscrits à la réservation.
5. **Une campagne de 30 jours**, avec un point chaque semaine et un
   remerciement public.

Le statut de l'asbl, le compte bancaire et les mentions légales sont requis
avant la mise en ligne (`asbl-administratif`).

---

## 7. Les approches

Les modèles sont dans `modeles/`. **Un message par destinataire, jamais un
envoi en masse.**

**Fondation, mécène : `modeles/lettre-fondation-fr.md`.** Une page : qui nous
sommes, le projet, le public, ce que l'argent paie, ce qu'on mesure, le budget
résumé, le lien vers la règle du jeu et le simulateur. Joindre : statuts, RIB,
bilan des premières animations. **Une demande de rendez-vous de 20 minutes avant
tout dossier** : beaucoup de fondations préfèrent parler du projet d'abord.

**Entreprise, banque : `modeles/proposition-partenariat-fr.md`.** Une page, avec
des **contreparties** qui respectent les règles du § 5 :

| Contrepartie possible | Remarque |
|---|---|
| Mention dans la page de remerciements du site et dans la règle du jeu | simple, gratuite |
| Logo sur le flyer et l'affiche de stand des animations qu'elle finance | jamais sur les cartes |
| Remerciement oral et sur l'affiche lors des animations financées | |
| Boîtes offertes à des classes ou à des bibliothèques « au nom de » l'entreprise | l'argent devient des jeux : mesurable |
| Compte rendu chiffré de l'usage des fonds | utile pour son rapport RSE |

**Subvention publique** : voir `subventions` § 3.

**Après un accord** : lire la convention, noter ce qui est dû et quand, remercier
publiquement si c'est prévu, tenir les pièces dans
`administratif/financement/[financeur]/`.

---

## 8. La veille mensuelle

Une fois par mois, et dès qu'un événement l'impose (publication d'un appel,
signature de convention) :

1. **Relancer les recherches** : « appel à projets fondation Luxembourg
   éducation enfants », « Fondation Sommer appel à projets pédagogiques »,
   « Fondation Enovos association projet », « Spuerkeess Foundation appel »,
   « André Losch Fondation appel », « appel à projets vivre-ensemble MFSVA »,
   « ministère de la Culture appel à projets médiation culturelle »,
   « financement participatif Luxembourg culture ».
2. **Parcourir les pages officielles** quand elles sont accessibles. Sinon, le
   dire et demander au propriétaire d'autoriser les domaines dans les réglages
   réseau de l'environnement.
3. **Mettre à jour** `pipeline.csv` : nouvelles pistes (`a-qualifier`), dates
   limites approchantes, pistes closes (`ferme-guetter` avec la date de
   vérification).
4. **Rendre compte** en cinq lignes : ce qui est nouveau, ce qui s'approche,
   les trois prochaines actions, les pistes mortes, l'état du manque à financer.

La skill est **lue par une session ouverte** : elle ne tourne pas toute seule.
Pour une veille automatique, le propriétaire peut planifier une tâche mensuelle
qui relance cette skill.

---

## 9. Le plan des 90 jours (novembre 2026 à janvier 2027)

Dans l'ordre d'efficacité : **recette sûre d'abord, argent externe ensuite.**

| Quand | Quoi |
|---|---|
| Novembre | **L'asbl** (`asbl-administratif`) : sans elle, presque rien n'est possible. Premières demandes d'animation aux communes (`animations-communes`). |
| Novembre | **Un message au ZLS** pour une lettre de soutien (`subventions` § 3). |
| Novembre | **Question à la Fondation de Luxembourg** : quelle fondation abritée finance l'éducation et la langue ? Ouvrir une piste Enovos et une piste Spuerkeess Foundation. |
| Décembre | **Les premières animations facturées.** Un compte rendu chiffré en main, on peut parler aux fondations et aux entreprises. |
| Décembre | **Subvention annuelle** de la commune du siège (`subventions` § 1). |
| Janvier | **Les précommandes payées** si le statut est prêt (`BOUTIQUE-CARTES.md` § 9) : la plus grande source de financement du tirage. |
| Janvier | **Décision sur un financement participatif** selon le compteur de réservations (§ 6). |

---

## 10. Comment répondre

1. **Lire le suivi** (`administratif/financement/pipeline.csv`) s'il existe, et
   `MODELE-ECONOMIQUE.md` pour le manque à financer.
2. **Dire l'état** en quelques lignes : manque à financer, pistes en cours par
   statut, échéances des 30 prochains jours.
3. **Proposer trois actions au plus**, la plus sûre d'abord, avec la raison et
   ce que le propriétaire doit faire (envoyer, appeler, signer).
4. **Toujours indiquer la certitude** de chaque source et ce qui reste à
   vérifier sur la page officielle.
5. **Répondre en français** par défaut. Les messages à un financeur sont en
   français, en allemand ou en luxembourgeois relu par un locuteur natif.

Sources relevées le 2026-11-02 : fdlux.lu (Fondation de Luxembourg, appel
sociaux de la Fondation Enovos, Spuerkeess Foundation), loschfondation.lu,
culture.lu (appel de la Fondation Sommer, 2023), spuerkeess.lu, paperjam.lu
(Nubs, Spuerkeess, BGL BNP Paribas), thecrowdspace.com (plateformes),
fedil.lu et chronicle.lu (concours), erasmus-plus.ec.europa.eu et sites tiers
(partenariats à petite échelle).
