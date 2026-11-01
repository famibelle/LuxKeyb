---
name: animations-communes
description: Animations et stands LuxKeyb dans les communes, bibliothèques, maisons relais et écoles du Luxembourg — qui solliciter (communes du Gemengepakt en priorité), comment les contacter (lettres en français et en allemand, relances), suivi des démarches hors du dépôt public, offre et tarifs, préparation et matériel, déroulé d'une animation par âge (Bataille des mots, Wuertduell, découverte de l'appli), règles sur place (pas de vente sans statut, réservations, photos), facture et bilan, calendrier des événements. À utiliser quand on démarche une commune, prépare ou anime un atelier ou un stand, écrit à une institution, fait le point sur les animations obtenues, ou demande « qui contacter » ou « combien demander ».
---

# Animations dans les communes — LuxKeyb

## 0. Le but

**Faire jouer, montrer l'appli, recueillir des réservations, et se faire payer
pour ça.** Une animation facturée est la recette la plus sûre du début. Elle
ne demande ni tirage ni stock, et une animation à 200 € rapporte autant que
20 boîtes (skill `business-modele` § 6, `MODELE-ECONOMIQUE.md`).

Trois règles valent pour toute la skill :

1. **Pas de facture ni de vente avant l'asbl** (skill `asbl-administratif`).
   D'ici là, on peut démarcher et caler des dates, mais pas encaisser.
2. **Pas de vente de boîtes** avant le statut, l'autorisation d'établissement
   si elle est nécessaire, et les essais EN 71. Sur place, on fait jouer et on
   prend des réservations, sans paiement, sur la page Commander.
3. **Les contacts ne vont jamais dans le dépôt public** : ils restent dans
   `prospection/`, qui est ignoré par git (§ 4).

---

## 1. Qui solliciter

**En priorité, les communes signataires du « Gemengepakt vum interkulturellen
Zesummeliewen »** (loi du 23 août 2023 sur le vivre-ensemble interculturel).
Elles s'engagent à mener des actions d'intégration et reçoivent pour cela des
aides de l'État, dont jusqu'à 30 000 € par an pour un coordinateur.
Apprendre le luxembourgeois en jouant entre dans leur plan d'action.

Il y avait 55 signataires en mai 2026, et 67 selon un article de septembre 2026
(chiffre non vérifié). Les noms relevés au 2026-11-01 :

- **Premières signataires (2024)** : Bettembourg, Differdange, Hesperange,
  Junglinster, Kehlen, Leudelange, Niederanven, Roeser, Sanem, Schuttrange,
  Steinfort, Strassen, Wiltz.
- **Ensuite** : Kayl, Kiischpelt, Mersch, Vianden, Betzdorf (février 2026),
  Ville de Luxembourg (mai 2026), Dippach (mai 2026).
- **En groupe** : les communes du Naturpark Uewersauer (Lac de la Haute-Sûre,
  Boulaide, Esch-sur-Sûre, Goesdorf, Winseler) et du Natur- & Geopark
  Mëllerdall.

La liste complète et à jour est à demander au ministère :
gemengen.zesummeliewen@fm.etat.lu.

**Les interlocuteurs**, par ordre d'efficacité :

| Structure | Qui demander | Ce qu'on propose |
|---|---|---|
| Commune signataire du pacte | le coordinateur du pacte communal, ou le service intégration ou vivre-ensemble | un cycle d'animations pour enfants et nouveaux habitants |
| Bibliothèque communale | le ou la responsable des animations | un atelier enfants, régulier si possible |
| Maison relais | la direction | une animation pendant les vacances scolaires |
| École fondamentale | l'enseignant de luxembourgeois, avec l'accord de la direction | une séance en classe. Les cartes imprimables restent gratuites pour l'école (`LICENSE-VISUELS.md`). |
| Service culturel ou commission culturelle | le service ou le président de la commission | un stand pendant une fête ou un marché |
| Commune du siège de l'asbl | l'administration communale | la subvention annuelle d'association (skill `subventions`) |

---

## 2. L'offre et les tarifs

| Format | Public | Durée | Tarif de départ |
|---|---|---|---|
| **Atelier Bataille des mots** | 6–8 ans, 8 à 16 enfants | 45–60 min | 200 € |
| **Atelier Wuertduell** | 8–12 ans, 8 à 16 enfants | 60–90 min | 200 € |
| **Atelier découverte** (cartes et appli) | adultes, nouveaux habitants | 60–90 min | 200 € |
| **Cycle de 4 ateliers** | un groupe suivi | 4 séances | 800–1 000 € |
| **Stand** (fête, marché, accueil des nouveaux habitants) | tout public | demi-journée | emplacement gratuit demandé ; pas de tarif |

**Les tarifs sont une proposition de départ (2026-11-01)**, à confirmer après
les premières réponses. Ils comptent environ 2 heures de présence, plus la
préparation et le trajet, soit environ 50 € de l'heure. Aucun tarif d'animation
publié au Luxembourg n'a été trouvé. Les règles :

- **Demander d'abord le budget** : « Quel budget prévoyez-vous pour une
  animation de ce type ? » Puis proposer le tarif.
- **Ne pas descendre sous 150 € par atelier** sans le dire au propriétaire :
  en dessous, le temps de préparation n'est plus payé.
- Chaque tarif obtenu se note dans le suivi (§ 4). Il remplace l'hypothèse
  `animation` de `scripts/modele.py` de la skill `business-modele`.

---

## 3. Le premier contact

1. **Un e-mail** à partir des modèles de `modeles/` :
   `demande-animation-fr.md` ou `demande-animation-de.md`. Une version en
   luxembourgeois est un vrai plus pour ce projet. Elle s'écrit à partir de la
   version allemande et **se fait relire par un locuteur natif** avant tout
   envoi.
2. **Les pièces jointes** : le flyer
   (`LuxKeybPlayStore/graphics/flyer-triptyque/Letzebuergesch_Clavier_flyer.pdf`),
   et les liens vers la règle du jeu
   (https://famibelle.github.io/LuxKeyb/regle-du-jeu.html, en français seulement
   pour l'instant) et le simulateur
   (https://famibelle.github.io/LuxKeyb/simulateur.html).
3. **Un appel** après 7 à 10 jours sans réponse. Ensuite, une seule relance
   par e-mail, puis on passe à la commune suivante et on note « à recontacter »
   avec une date.
4. **Ne jamais promettre** une date de sortie des boîtes, un prix de vente ou
   une subvention. Ce qui est sûr : l'appli gratuite, les cartes imprimables
   gratuites, l'animation.

---

## 4. Le suivi

Le suivi vit dans `prospection/suivi.csv`, **ignoré par git**. Il contient des
noms et des contacts : ce sont des données personnelles (skill
`asbl-administratif` § 7). Pour le créer, copier l'en-tête de
`modeles/suivi.csv` :

```bash
mkdir -p prospection && cp .claude/skills/animations-communes/modeles/suivi.csv prospection/
```

Les statuts, dans l'ordre : `a-contacter`, `contacte`, `relance`, `interesse`,
`bon-de-commande`, `animee`, `facturee`, `payee`, `refus`, `a-recontacter`.

**Avant un commit, vérifier avec `git status`** que rien de `prospection/`
n'apparaît. Le propriétaire garde une copie hors du conteneur, car une session
cloud est effacée après usage.

Pour faire le point, compter les lignes par statut et par mois. Le chiffre qui
compte pour le modèle, ce sont les animations **sous bon de commande** : ce
sont les seules recettes sûres.

---

## 5. Préparer une animation

**À confirmer avec l'organisateur, par écrit :**

- la date, l'heure, le lieu, le contact sur place et son téléphone ;
- le public : l'âge, le nombre de participants, les langues parlées ;
- la salle : des tables pour jouer à deux, des chaises, une prise électrique ;
- le bon de commande signé (`asbl-administratif` § 3) ;
- l'assurance : celle de l'organisateur couvre-t-elle l'intervenant ?

**Le matériel :**

| Quoi | Combien | D'où |
|---|---|---|
| Jeux de cartes | 1 jeu de 52 cartes pour 2 joueurs, plus 2 de réserve (10 jeux pour 16 enfants) | jeux de démonstration (skill imprimeur § 7) |
| Règle du jeu imprimée | 1 par table | `docs/regle-du-jeu.md` |
| Téléphone ou tablette avec l'appli | 1 ou 2 | l'appli marche hors ligne |
| Affiche avec un QR code vers la page de réservation et un QR code pour installer l'appli | 1 | skill imprimeur § 7 |
| Flyers | 1 par famille | le flyer triptyque |
| Feuille de réservation papier (e-mail, nombre de boîtes de chaque sorte) | 1 | avec la phrase de consentement (§ 7) |
| Autorisations photo pour les parents | quelques-unes | `administratif/` |

---

## 6. Le déroulé

La règle d'or vient de `docs/regle-du-jeu.md` : **pour jouer une carte, on lit
son mot à voix haute.**

**Atelier Bataille des mots, 6–8 ans, 60 minutes**

| Minutes | Quoi |
|---|---|
| 0–5 | « Moien ! » Chacun dit son prénom et un mot luxembourgeois qu'il connaît. |
| 5–15 | Les cartes : la Force (le nombre de lettres), le dessin, le mot. On lit ensemble trois cartes à voix haute. |
| 15–45 | Parties de Bataille des mots par deux, environ 5 minutes chacune. On change d'adversaire à chaque partie. |
| 45–55 | Jeu collectif : l'animateur lit un sens en français, le premier qui trouve la carte la lit en luxembourgeois. |
| 55–60 | Un mot appris par enfant. Pour les parents : le flyer, l'affiche avec les QR codes, la réservation. |

**Atelier Wuertduell, 8–12 ans, 90 minutes**. Même ouverture. On explique la
règle sur une partie jouée devant tout le monde, puis on joue deux ou trois
parties d'environ 15 minutes. On termine en montrant le carnet de l'appli,
avec l'accord des parents pour l'installer.

**Atelier découverte, adultes, 90 minutes**

| Minutes | Quoi |
|---|---|
| 0–10 | Tour de table : depuis quand au Luxembourg, pourquoi le luxembourgeois |
| 10–25 | Le clavier sur leur téléphone : installation par QR code, écrire « Moien, wéi geet et? », voir les suggestions et les accents |
| 25–55 | Wuertduell par deux |
| 55–75 | Le Wierderbuch et un jeu de l'appli (Wuertriet ou Wuertsich) |
| 75–90 | Questions, réservations, ressources (cours de l'INLL, Sproochentest) |

**Stand** : une table de jeu en continu, des parties de 5 minutes avec
l'animateur, l'appli en démonstration et les réservations. Compter les
parties jouées.

---

## 7. Sur place

- **Réservations** : QR code vers la page Commander (`docs/commander.md`), ou
  feuille papier avec cette phrase : « Votre e-mail sert seulement à vous
  écrire quand l'impression se prépare, pour confirmer et payer votre
  réservation. Il est effacé après la livraison. Réserver n'engage à rien. »
  Recopier la feuille dans le formulaire le soir même, puis la détruire.
- **Photos** : jamais d'enfant identifiable sans l'autorisation écrite d'un
  parent. Par défaut, photographier les mains et les cartes.
- **Pas de vente de boîtes** tant que les conditions du § 0 ne sont pas
  remplies. Si on demande « où l'acheter ? », la réponse est la
  réservation sur la page Commander, ou sur la feuille papier.
- **Compter** : les participants, leur tranche d'âge, les parties jouées, les
  boîtes réservées, les installations de l'appli. Ces chiffres
  font le bilan et nourrissent les dossiers de la skill `subventions`.

---

## 8. Après

1. **Le bilan d'une page** à l'organisateur, sous 3 jours, à partir de
   `modeles/bilan.md`. Il ne contient aucun nom de participant.
2. **La facture**, jointe au bilan (`asbl-administratif` § 3).
3. **Le suivi** : statut `animee`, puis `facturee`, puis `payee`.
4. **Le modèle** : mettre le tarif obtenu et le nombre d'animations dans
   `scripts/modele.py` (`SAISON`), relancer `--plan`, et reporter les paliers
   dans `MODELE-ECONOMIQUE.md`.
5. **Proposer la suite** : un cycle, la même animation dans une autre structure
   de la commune, ou un stand au prochain événement.

---

## 9. Le calendrier, de décembre 2026 à juin 2027

| Quand | Événement | Pourquoi |
|---|---|---|
| Décembre 2026 | Marchés de Noël des communes | Beaucoup de familles, période des cadeaux. À demander **dès novembre**. |
| Toute l'année | Accueil des nouveaux habitants (à vérifier commune par commune) | Le public adulte le plus motivé |
| Toute l'année | Bibliothèques (par exemple « Bib fir Kids » à Differdange) | Public d'enfants, rendez-vous réguliers |
| Vacances scolaires | Maisons relais | Animations payées, pour des groupes d'enfants |
| 29 mars 2027 | Emaischen (lundi de Pâques), à Luxembourg-Ville et à Nospelt, commune de Kehlen | Kehlen a signé le pacte |
| Avril 2027 | Fierkelsmaart | Déjà prévue (skill imprimeur § 7). Date et accord de l'organisateur à confirmer. |
| 22–23 juin 2027 | Fête nationale | Fête de la langue et du pays |
| 26 septembre 2027 | Journée européenne des langues | Pour la saison 2 |

---

## 10. Comment répondre

- Partir du suivi (`prospection/suivi.csv`) s'il existe : qui est en attente,
  qui relancer, quelles animations sont sous bon de commande.
- Pour une nouvelle commune : vérifier si elle a signé le pacte, choisir
  l'interlocuteur (§ 1) et le format (§ 2), puis adapter le modèle de lettre.
- Les tarifs viennent du § 2 et du suivi, jamais inventés. Une subvention n'est
  pas un tarif : elle relève de la skill `subventions`.
- Ne jamais écrire de nom, d'e-mail ou de téléphone de contact dans un fichier
  suivi par git.
- Répondre en français par défaut. Les lettres sont en français, en allemand ou
  en luxembourgeois relu.

Sources relevées le 2026-11-01 : gouvernement.lu (signatures du Gemengepakt,
2024–2026), lequotidien.lu, chronicle.lu, lac-haute-sure.lu, visitluxembourg.com
(Bib fir Kids), tarifs d'ateliers (aucun tarif luxembourgeois publié).
