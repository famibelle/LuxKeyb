---
name: imprimeur
description: Préparer l'impression physique des cartes du carnet LuxKeyb (qualité carte à jouer) — choix du jeu de cartes, spécifications techniques, fichiers prêts à imprimer, estimation de budget et choix d'imprimeur. À utiliser quand on parle d'imprimer les cartes, d'un jeu physique, d'un devis, d'un BAT ou du stand (Fierkelsmaart).
---

# Imprimeur — cartes LuxKeyb sur papier

## 1. Quelles cartes existent

Dans l'application, chaque mot tapé peut devenir une carte : le dictionnaire
(`android_keyboard/app/src/main/assets/luxemburgish_dict.json`) en compte
près de 38 000. On n'imprime donc jamais « tout » : on imprime un **ensemble
fini**, en priorité les séries du carnet
(`android_keyboard/app/src/main/java/com/example/kreyolkeyboard/carnet/Series.kt`) :

| Série | Source | Cartes |
|---|---|---|
| Nombres 0–100 | `Series.kt` (NOMBRES), orthographe via `zuelen/ZuelenSpeller` | 101 |
| Enluminures | `assets/luxemburgish_series.json` → `enluminures` | 104 |
| Jeux | `JeuCarte` dans `carnet/CarnetModels.kt` | 7 |
| Paysages | `carnet/Paysages.kt` (9 lieux × 4 heures) | 36 |

Total séries : **248 cartes**. Recompter à chaque fois depuis les sources
(les chiffres ci-dessus datent d'octobre 2026) :

```bash
python3 -c "import json;print(len(json.load(open('android_keyboard/app/src/main/assets/luxemburgish_series.json'))['enluminures']))"
```

Alternative courte : un jeu de 54 cartes (sélection d'enluminures, paysages,
nombres) — beaucoup moins cher, idéal pour un stand.

La rareté d'une carte vient du rang de fréquence (`Rarete.pourRang`,
`Rarete.pourNombre` dans `CarnetModels.kt`) : la reprendre telle quelle sur
le papier (cadre, couleur) pour rester fidèle à l'application.

## 2. Spécifications « qualité carte à jouer »

- Format poker 63 × 88 mm (ou bridge 57 × 89 mm), coins arrondis rayon 3 mm.
- Fond perdu 3 mm de chaque côté → fichier 69 × 94 mm.
- Zone de sécurité : texte et QR à au moins 3 mm du bord de coupe.
- 300 dpi minimum → **815 × 1110 px** par face avec fond perdu (poker).
- Carton 300–330 g/m² à âme noire ou bleue (opacité), pelliculage toilé (linen).
- Couleurs : fournir en RVB sauf demande CMJN de l'imprimeur ; prévoir que
  les couleurs vives de l'écran ternissent en CMJN → toujours un BAT physique.
- Polices : celles du carnet (`carnet/PolicesCarnet.kt`, `assets/fonts`) —
  vérifier leur licence d'impression ; les vectoriser/incorporer dans le PDF.
- QR codes (échange de cartes) : au moins 15 × 15 mm, tester le scan sur
  l'épreuve imprimée.
- Dos commun à toutes les cartes (identique pour le jeu), recto propre à chaque carte.

**Exigence : qualité professionnelle, non négociable.** Ne jamais proposer
l'option la moins chère d'un imprimeur.

- Carton carte à jouer ~310 g/m² à âme noire ou bleue (pas un simple papier
  350 g) : MakePlayingCards « M31 » ou « S33 », jamais le carton standard.
- Pelliculage toilé (linen) obligatoire.
- Découpe à l'emporte-pièce, bords nets et réguliers, coins arrondis 3 mm.
- Quadrichromie offset ou numérique haut de gamme, noirs profonds.
- Fichiers convertis en CMJN avec le profil ICC de l'imprimeur ; export
  vectoriel quand c'est possible (le code dessine les cartes), sinon ≥ 300 dpi.
- BAT physique obligatoire avant toute série.
- Option premium : dorure à chaud ou vernis sélectif sur rares et très rares
  (beau et difficile à contrefaire).
- Sélection de l'imprimeur **sur pièce** : 2–3 prototypes chez deux
  imprimeurs différents, comparés en main (rigidité, glisse, couleurs, coupe).
- Coût de cette qualité : +20 à 40 % par rapport à l'entrée de gamme.

## 3. Produire les fichiers

Les cartes ne sont pas des images dans le dépôt : elles sont dessinées par le
code (`CarteCarnet.kt`, `CadreOrne.kt`, `Paysages.kt`, `Blason.kt`).
Deux voies :

1. **Export depuis l'app — la voie retenue** (`carnet/ImpressionCarte.kt`,
   2026-10-26) : appui long sur « Partager » dans le lecteur de cartes, puis
   « Cette carte » ou « La carte Fierkelsmaart ». L'appli dessine le recto
   avec son propre code (`CarteCarnet.complete(…, impression = true)` : écus
   COÛT et TYPE, pouvoir des cartes spéciales, sans médaillon, saison, sceau
   ni plume ; depuis le 2026-10-29, le paysage en cartons du théâtre de
   papier, figés, par `CarteOrnee.enTheatre()` : coulisses des rares, nuage
   des très rares, voir la skill `direction-artistique`), à 600 dpi,
   69 × 94 mm fond perdu compris (1630 × 2220 px), et
   l'envoie par le partage Android. La carte de l'appli (300 × 440) est plus
   étroite que le poker : posée sur 84 mm de haut (à 86, la ligne du bas
   touchait la limite de sécurité), le reste est le métal du palier.
   **Pour la boîte entière** (2026-10-29) : `python3 scripts/rectos_boite.py
   carnet` (le carnet des 52 cartes B1, à poser dans
   `shared_prefs/carnet_cartes.xml` d'un build debug sur l'émulateur), puis
   `scripts/exporter_rectos.sh 52` (lecteur ouvert sur la première carte, tri
   « Récentes »), puis `scripts/rectos_boite.py assembler` (img2pdf requis).
   Il écrit le 600 dpi dans `impression/boite/` (privé) et la version 300 dpi
   publique dans `cartes-imprimables/boite/` (`rectos_52.pdf`, `liste.csv`),
   comme Medhi l'a choisi.
2. **Redessin** (script Python/HTML + Playwright, `NODE_PATH=$(npm root -g)`) :
   reproduire la mise en page à partir des JSON d'assets. Écarté pour les
   rectos après le premier BAT de la Fierkelsmaart, jugé « moins beau que
   sur le téléphone » par le propriétaire : il reste bon pour la planche de
   BAT (traits de coupe, spécifications, signature) autour du PNG exporté, et
   pour le verso commun.

Sortie attendue, **jamais dans le dépôt public** (dossier `impression/`
ignoré par `.gitignore`, ou dépôt privé) :
`impression/<jeu>/recto/NNN_<mot>.png`, `impression/<jeu>/dos.png`, plus un
`planche.pdf` (une carte par page, fond perdu inclus) et un `liste.csv`
(numéro, mot, série, rareté) pour vérifier l'ensemble avant envoi.

Contrôles avant envoi : nombre de fichiers = nombre de cartes prévu,
orthographe luxembourgeoise relue, aucun texte dans la marge de 3 mm, QR scannés.

## 4. Budget indicatif (hors TVA 17 % et livraison)

Ordres de grandeur, à confirmer par devis :

| Quantité | Imprimeur | Jeu de 54 | Coffret 248 cartes |
|---|---|---|---|
| 1–3 prototypes | Impression à la demande (MakePlayingCards, Printerstudio) | 12–20 € | 50–80 € |
| ~100 | Idem, tarif dégressif | 6–10 € | 25–40 € |
| 500+ | Offset européen (Ludo Fact, Delano, Spielkartenfabrik Altenburg, Cartamundi) | 1,5–5 € | 8–15 € |

À ajouter : livraison vers le Luxembourg (30–150 €), BAT physique (30–80 €),
finitions spéciales (dorure, boîte rigide) en sus.

## 5. Calendrier

Prototype à la demande : 1–2 semaines. Offset : 3–6 semaines de production
après validation du BAT. Pour un événement (ex. Fierkelsmaart, avril 2027),
commander le prototype ~3 mois avant et lancer la série ~2 mois avant.

## 6. Protéger les cartes contre la copie

Le dépôt est public : le code qui dessine les cartes et les données sont
lisibles par tous. On ne peut pas empêcher la copie, seulement la rendre
illégale à la vente, sans valeur, et reconnaissable.

1. **Juridique (le plus efficace)**
   - Licence des visuels : `LICENSE-VISUELS.md` (en place). Code MIT,
     cartes et visuels tous droits réservés **sauf usage scolaire et
     éducatif gratuit**. Toute vente ou édition demande un accord écrit.
     Les dictionnaires gardent leurs licences (`NOTICE.md`).
   - Sur chaque carte imprimée et sur la boîte : « © Lëtzebuergesch Clavier
     — usage scolaire autorisé, vente interdite ».
   - Marque « Lëtzebuergesch Clavier » + logo déposés au BOIP (Benelux),
     ~250–300 € pour 10 ans : c'est ce qui permet d'agir contre un jeu vendu.
2. **Ce qui est public, ce qui ne l'est pas** (propriétaire, 2026-10-26)
   - **Public**, dans `cartes-imprimables/` (README, licence des visuels) :
     planche de la boîte, dos commun, scripts de dessin (`carte_appli.py`,
     `verso_luxe.py`). La copie scolaire est voulue ; chaque dos porte
     « © Lëtzebuergesch Clavier · Usage scolaire libre · vente interdite ».
   - **Jamais public** : la clé privée et les codes signés (un code publié
     donne ses cartes à tous), et la Fierkelsmaart tant que l'organisateur
     n'a pas donné son accord écrit (BAT et `generer_bat.py`, `bat_luxe.py`).
     Ils vivent dans `impression/` (ignoré) et dans l'archive que garde le
     propriétaire ; à rapporter dans une nouvelle session.
   - Ce qui distingue la boîte officielle d'une copie : la **carte code**
     (cartes ajoutées au carnet), le carton à âme noire, le toilé, la dorure.
     Vérifier `git status` avant tout commit : rien de `impression/`.
3. **Authenticité : la carte code** (en place depuis le 2026-10-26, voir
   `CESSION-CARTES.md` § 8)
   - Pas un QR par carte (15 mm au moins, trop de place prise au dessin, et
     le dos doit rester identique) : **une carte code** par boîte (sur une des
     2 cartes de règle), par paquet, ou au stand. Un scan verse ses cartes
     dans le carnet, provenance « carte en papier ».
   - Codes signés par `scripts/codes_cartes.py` (`@B1 -n 500 -o
     impression/codes/B1`) avec `impression/cle_cartes/privee.pem` (hors
     dépôt ; le propriétaire en garde une copie hors du conteneur). Chaque
     code a sa série : **impression à données variables** (un QR différent
     par boîte), simple en numérique, à demander au devis en offset.
   - La photo de la carte a été écartée : ML Kit envoie des statistiques à
     Google, et un moteur hors ligne pèse plusieurs mégaoctets.
   - Finitions difficiles à reproduire : dorure, vernis sélectif, âme bleue.
4. **Politique décidée** : copie scolaire et éducative tolérée, usage
   commercial interdit. Ne pas proposer de mesure qui gênerait les classes.

## 7. Vente en paquets (décisions d'octobre 2026)

À revoir avec les vrais devis ; refaire les calculs plutôt que recopier.

### La gamme (révisée le 2026-10-23, après la règle du jeu)

Les cartes sont d'abord un **jeu** : la règle est publiée sur
`docs/regle-du-jeu.md` (Bataille des mots dès 6 ans, Wuertduell dès 8 ans,
rien d'autre que les cartes). Le produit principal est donc ce qui permet de
jouer tout de suite, et les paquets de 6 deviennent le complément.

**Combien de cartes pour une partie** (simulation de 4 000 parties,
`scripts/simulation_wuertduell.py`, vrais mots du dictionnaire, joueurs automatiques
gloutons) :

| Paquet par joueur | Tours (médiane, 90 %) | Cartes utilisées par joueur | Pioche épuisée |
|---|---|---|---|
| 20 | 7, 9 | 17 | 4 % |
| 25 | 7, 9 | 17 | 0 % |
| 26 | 7, 9 | 17 | 0 % |
| 30 | 7, 9 | 17 | 0 % |

Une partie consomme ~17 cartes par joueur (5 trésor + 5 main + ~7 pioches),
soit ~34 à deux. La règle fixe **26 cartes par joueur** : jamais de pioche
vide, de la marge pour des enfants qui jouent plus lentement que des
automates, et les deux paquets font **les 52 cartes d'un jeu classique**
(propriétaire, 2026-10-26 ; 25 auparavant). Composition d'un paquet de 26
(`scripts/candidats_cartes.py`) : 14 noms, 5 verbes, 4 adjectifs, 3 nombres.

| Produit | Contenu | Rôle | Prix public indicatif |
|---|---|---|---|
| **Boîte de départ** (produit principal) | 54 cartes : 52 à jouer (2 paquets de 26 prêts à jouer) + 2 cartes de règle à la place des jokers | Jouer à deux dès l'ouverture ; sert aussi à la Bataille des mots | 12–15 € |
| Paquet de 6 (complément) | 3 C / 2 PC / 1 R ou mieux, voir plus bas | Faire évoluer son paquet, collectionner, échanger | 4 € |
| Coffret complet | toutes les cartes | Écoles, collectionneurs | 35–45 € |

**Pourquoi 54** : c'est le format d'un jeu de cartes standard, 52 cartes et
2 jokers ; le jeu garde ce principe, 52 cartes à jouer et 2 cartes de règle
(une par jeu, avec un QR vers `docs/regle-du-jeu.md`). Étui, découpe
et tarifs existent chez tous les imprimeurs (voir la ligne « Jeu de 54 » de la
section 4) : c'est le format le moins cher à produire en qualité.

**Économie indicative de la boîte** (à refaire avec les devis) :

- Coût en offset européen, 500 boîtes, qualité exigée (section 2) :
  ~3–4,5 € la boîte avec étui (fourchette 1,5–5 € de la section 4, +20–40 %
  pour la qualité). En impression à la demande (~100) : 6–10 €, trop cher pour
  vendre à 12 € → réservé aux prototypes.
- Mise de départ : ~1 500–2 250 € de tirage + ~385 € de frais fixes
  (marque, prototypes, maquette).
- À 12 € : seuil de rentabilité ~160–220 boîtes ; 400 vendues → bénéfice
  ~+2 000–2 700 €. En librairie (commission 30–40 %), la boîte rapporte
  ~7–8,5 € : garder le prix public, ne pas le baisser.

**Les mots viennent du LOD** (2026-10-23) : `scripts/candidats_cartes.py`
compose la boîte (Déieren contre Iessen, noms jumelés par longueur : 50 % de
victoires de chaque côté en simulation) et deux extensions (Schoul, Natur),
dans `Dictionnaires/cartes/` : `selection.md` à relire, `selection.csv` pour
les fichiers, `candidats.csv` pour remplacer un mot. Verbes et adjectifs
choisis à la main (listes du script), sens et phrases pris au LOD. La famille
du jeu est le **genre** du nom (pastille), pas le paysage : la plupart de ces
mots n'ont pas de champ au carnet et tomberaient tous dans « Stad ».

**Conséquences pour les cartes :**

- La boîte demande des **verbes et des adjectifs**, que les 248 cartes de
  l'appli n'ont presque pas (enluminures = noms). Les choisir dans le
  dictionnaire, sens tirés du LOD (CC0), jamais de phrase RTL.
- Pour l'équilibre du jeu, le **coût (symboles de rareté) suit la longueur du
  mot** : 1–4 lettres ●, 5–7 ◆◆, 8–10 ★★★, 11 et plus ✦✦✦✦. Le tableau des
  raretés ci-dessous est à refaire selon cette règle avant tout fichier.
- Les deux paquets de la boîte ont une **composition fixe** (pas de hasard) ;
  le hasard et les très rares restent dans les paquets de 6.
- Gabarit papier : écu gauche = coût en gros, écu droit = pictogramme du
  type (fait le 2026-10-27, symboles Montessori, `carnet/PictoType.kt`) à la
  place de VUES et NIVEAU, qui n'ont pas de sens hors de l'appli. Numéro de
  collection en bas à gauche, « Wuertduell · n° 012 » (`ImpressionCarte.numero`) :
  une fois une édition imprimée, ne plus réordonner `cartes_imprimees.json`.

**Raretés imprimées** : fixées à la main pour le papier (pas dérivées de
`luxemburgish_dict.json`, qui est en partie CC BY-NC → interdit à la vente).
Rare = beau (paysage de nuit, enluminure), pas « mot peu fréquent ».

| Rareté | Cartes |
|---|---|
| Commune | 140 |
| Peu commune | 70 |
| Rare | 30 |
| Très rare | 7 (les 7 cartes Jeux) |
| Ultra rare | 1 (la carte Fierkelsmaart) |

**La carte Fierkelsmaart** existe dans l'appli depuis le 2026-10-23
(`carnet/CarteFierkelsmaart.kt`, dessin `Porcelet` dans `CadreOrne.kt`) : un
porcelet devant un médaillon vert couronné de laurier, ruban doré « 2027 ».
Le mot a été ajouté au dictionnaire à la main (`MOTS_HORS_CORPUS`). Sa phrase
(« Fierkelsmaart 2027: mir sinn dobäi! ») est écrite pour la carte : elle peut
s'imprimer. Le dessin **s'inspire** du logo de l'organisateur sans le copier ;
avant de vendre une carte qui porte le nom de l'événement, demander l'accord
écrit de l'organisateur. Sur papier : à décider entre un tirage très rare
(ex. 1 paquet sur 50 à 100) et une carte offerte au stand, ou les deux.

Les phrases d'exemple **du LOD** s'impriment (`luxemburgish_exemples.json`,
CC0, avec leur traduction officielle du ZLS) : ce sont celles des cartes de
l'appli. Ne jamais imprimer celles de `luxemburgish_cloze.json` (Wuertlück,
corpus RTL, CC BY-NC), ni choisir les mots par le rang de fréquence de
`luxemburgish_dict.json` (dérivé du même corpus).
Crédit sur l'emballage : « Mots : LOD / ZLS (CC0) ».

**Paquet de 6 (complément de la boîte)** : 6 cartes = 3 communes, 2 peu communes, 1 rare ou mieux
(très rare ~1 paquet sur 6). La première idée (4/1/1) était trop dure.

**Compléter la collection (simulation, sans échange)** :

| Composition | Paquets (médiane) | 90 % des joueurs |
|---|---|---|
| 4 C / 1 PC / 1 R | ~330 | ~455 |
| 3 C / 2 PC / 1 R (retenu) | ~250 | ~345 |
| 6 C / 3 PC / 1 R (10 cartes) | ~165 | ~235 |

Le goulot est les peu communes puis rares/très rares. Compenser par :
l'échange (QR de l'application, coin « Je cherche / Je donne » au stand) et
un **coffret complet** de 248 cartes à 35–45 € (écoles, impatients).

**Prix et volume** :

- 4 € le paquet, 3 pour 10 € ; lot école 20 paquets pour 60 €.
- Tirage 1 : 500 paquets (3 000 cartes, ~12 coffrets), en impression à la
  demande qualité, mis en paquets à la main.
- Coût par paquet qualité ~1,25–1,40 € (1,50–1,70 € avec dorure).
- Frais fixes ~385 € (marque BOIP ~275, prototype ~60, maquette pochette ~50),
  hors emplacement de stand.
- Seuil de rentabilité ~150 paquets ; ~400 vendus (80 %) → bénéfice ~+600 €.
- Librairies : 30–40 % de commission → rester à 4 €, pas en dessous.
- Édition suivante en offset (≥ 1 000) → ~0,60–0,80 € le paquet.
- TVA : franchise probable sous ~50 000 €/an au Luxembourg — à vérifier.
  Statut (indépendant, asbl) à valider par la Chambre de commerce ou un comptable.

**Emballage** : pochette scellée ou kraft étiquetée ; mentions fabricant et
adresse, avertissement « ne convient pas aux moins de 3 ans », © et licence
(voir section 6), crédit LOD/ZLS.

Repris des paquets de cartes du commerce (Panini Adrenalyn XL, examinés le
2026-10-27), à demander au devis :

- **Les chances imprimées sur la pochette** : « 6 cartes : 3 communes,
  2 peu communes, 1 rare ou mieux · très rare : environ 1 paquet sur 6 ».
  Honnête pour des enfants, rassurant pour les parents.
- **Pochette opaque et scellée** (métallisée ou kraft épais thermoscellé) : on
  ne doit pas repérer une rare à la lumière ni rouvrir sans trace.
- **« 6 Kaarten » dans un coin du recto**, l'année de l'édition, et au dos le
  fabricant et son adresse, FSC si le carton l'est, le logo de tri, un
  **code-barres EAN** (indispensable en librairie, adhésion GS1 Luxembourg),
  les mentions en lb, fr, de, en, pt.
- **Variantes numérotées** d'une même carte (métal ou dorure différents,
  « 07/50 ») : la rareté par la beauté, jamais par la Force. Demande
  l'impression à données variables, déjà nécessaire pour les codes.
- **Carte code** : le QR signé, avec dessous « Scanne avec Lëtzebuergesch
  Clavier » et l'adresse du site. Pas de date d'expiration (les codes ne se
  révoquent pas, et elle pousse à l'achat) ; pas de code court à taper
  (sans serveur, il se devinerait).
- À ne pas reprendre : les cartes plus fortes parce que plus rares, un dos
  qui change d'une carte à l'autre.

**Calendrier Fierkelsmaart (avril 2027)** : marque et statut en novembre,
fichiers et prototypes décembre–janvier, BAT et série en février.

## 8. Contexte : porteur du projet basé au Luxembourg

- **Choisir un imprimeur dans l'UE** (Cartamundi à Turnhout en Belgique,
  Ludo Fact et Altenburg en Allemagne, Delano en Pologne) : pas de douane.
  MakePlayingCards/Printerstudio expédient de Hong Kong → TVA d'import 17 %
  + frais de dédouanement du transporteur à la livraison ; acceptable pour
  un prototype, à éviter pour la série. Cartamundi : ~3 h de route,
  visite/BAT sur place possible.
- **TVA** : 17 % au Luxembourg. Régime de franchise des petites entreprises
  (seuil de l'ordre de 50 000 €/an, à vérifier sur guichet.lu) → pas de TVA
  facturée, mais pas de récupération non plus.
- **Statut** : une activité commerciale demande en principe une
  autorisation d'établissement (guichet.lu, Ministère de l'Économie). Une
  asbl peut vendre si c'est accessoire à son objet (promotion de la langue).
  Conseil gratuit : House of Entrepreneurship (Chambre de commerce).
- **Marque** : le BOIP couvre le Benelux, donc le Luxembourg.
- **Stand** : se renseigner auprès de l'organisateur ou de la commune pour
  l'emplacement et les éventuelles autorisations de vente.
- **Débouchés locaux** : librairies (Ernster, Librairie Alinéa…), écoles via
  enseignants de luxembourgeois, Institut national des langues (INLL),
  boutiques souvenirs.
- Ces informations administratives sont générales : faire confirmer par
  guichet.lu ou la Chambre de commerce, ne jamais les présenter comme
  certaines.

## 9. Comment répondre

- Toujours demander ou supposer explicitement : quel ensemble de cartes,
  combien d'exemplaires, boîte ou non.
- Donner les prix comme fourchettes, jamais comme devis.
- Proposer d'abord un prototype avant toute série.
- Rappeler la section 6 dès qu'on produit des fichiers d'impression.
- Rappeler l'exigence de qualité (section 2) et les décisions de vente
  (section 7) ; les présenter comme des estimations, pas des devis.
- Répondre en français par défaut.
