# Commander les cartes en ligne : la chaîne de la boutique

Note du 28 octobre 2026, écrite avant d'ouvrir la vente, comme
[`CESSION-CARTES.md`](CESSION-CARTES.md). À la racine, elle n'est publiée ni
sur le site ni dans l'APK. Les prix, tarifs et règles cités sont des
estimations du 28 octobre 2026, à refaire avec les vrais devis et à faire
confirmer (guichet.lu, House of Entrepreneurship, un comptable).

---

## 1. Ce que veut le propriétaire

Qu'une personne intéressée **commande les cartes physiques sur le site et les
reçoive chez elle**. Choix du propriétaire, le 2026-10-28 :

- **Impression à la demande** : l'imprimeur imprime chaque boîte commandée et
  l'expédie lui-même au client. Pas de stock, pas d'avance de tirage.
- **Paiement par Stripe Payment Links** : un lien de paiement par produit, sans
  serveur. Le site reste statique (GitHub Pages).
- **Livraison au Luxembourg, en Belgique, en France et en Allemagne.**
- **Deux produits de 54 cartes** : la boîte de départ et l'extension
  *Schoul* et *Natur* (même jour ; deux extensions de 24 cartes d'abord).

## 2. La chaîne, de la commande à la boîte

```
docs/commander.md ──► paiement Stripe ──► docs/merci-commande.md
                       (adresse, CGV acceptées, reçu par e-mail)
                              │
                              ▼
        scripts/commandes.py preparer   (chez le propriétaire, chaque jour ouvré)
          ├─► fiche de la commande ──► commande passée chez l'imprimeur,
          │                            livrée à l'adresse du client
          └─► e-mail du code  ──► scripts/commandes.py envoyer
                              │
                              ▼
               l'imprimeur imprime et expédie ──► la boîte chez le client
```

1. **Le client** lit la page Commander (contenu, prix, délai, âge, vendeur) et
   touche « Commander ».
2. **Stripe** demande la quantité de chaque jeu (1 à 5 ; les deux autres
   produits s'ajoutent en articles optionnels), l'e-mail, le téléphone,
   l'adresse de livraison (LU, BE, FR, DE seulement), l'acceptation des
   conditions de vente, et encaisse. Il envoie le reçu et ramène le client sur
   `merci-commande.html`.
3. **Le propriétaire** lance `scripts/commandes.py preparer` (§ 10) : chaque
   nouvelle commande payée reçoit un numéro (`WD-0001`), un code signé par
   jeu (boîte ou extension), une fiche avec l'adresse à recopier chez l'imprimeur et un e-mail
   prêt, avec le code QR en pièce jointe.
4. Il **passe la commande chez l'imprimeur** avec l'adresse du client comme
   adresse de livraison, sans prix ni facture dans le colis, et note le
   numéro de commande de l'imprimeur dans `registre.csv` (colonne
   `imprimeur`).
5. Il relit les e-mails puis lance `scripts/commandes.py envoyer`.
6. **L'imprimeur** imprime et expédie. Le numéro de suivi, s'il y en a un, se
   transmet au client en répondant à son e-mail.
7. **Après la vente** : un colis perdu ou abîmé est réimprimé à nos frais (et
   réclamé à l'imprimeur) ; une rétractation se rembourse depuis le tableau de
   bord Stripe. Le code déjà envoyé ne se reprend pas : il ne donne que des
   cartes dans l'application, ce qui est accepté.

## 3. Ce qu'on vend en ligne

- **La boîte de départ** (54 cartes, `docs/regle-du-jeu.md`) : 2 paquets de
  26 et 2 cartes de règle. Ensemble `@B1`.
- **L'extension *Schoul* et *Natur*** (2026-10-28) : 54 cartes comme la
  boîte, 2 paquets de 26 (14 noms, 5 verbes, 4 adjectifs, 3 nombres chacun)
  et les 2 cartes de règle. Ensemble `@E1`. Elle se joue seule (Schoul contre
  Natur, 49 % / 51 % en simulation) ou avec la boîte (chacun de ses paquets à
  49–51 % contre Déieren et Iessen), et aucun mot n'est dans les deux boîtes.
  Même format que la boîte, donc même étui et même grille de prix chez
  l'imprimeur. Cartes : `Dictionnaires/cartes/selection.md`.
  - **L'appli doit être publiée avant la vente** : `@E1` n'est connu que des
    versions qui suivent ce changement, et une plus ancienne répond « mettez à
    jour » au scan du code.
  - Les extensions de 24 cartes (`@SCHOUL1`, `@NATUR1`) sont retirées de
    l'appli : jamais imprimées, aucun code signé connu. Si un code a été
    signé pour elles, les remettre dans `cartes_imprimees.json`.
  - 15 de ses 28 noms n'ont pas encore de dessin (la boîte en a un pour chaque
    nom depuis le 2026-10-26) : à dessiner avant le BAT.
- **Plus tard**, le coffret complet : un jeu fixe lui aussi, que l'impression
  à la demande sait faire.
- **Pas les paquets de 6.** Leur hasard (3 communes, 2 peu communes, 1 rare ou
  mieux) et leur pochette scellée demandent un tirage assemblé ; l'impression
  à la demande imprime toujours le même jeu. Ils restent pour le stand et une
  édition en série.

## 4. Le choix de l'imprimeur : imprimer dans l'UE

**C'est la décision qui conditionne toute la chaîne.** Un imprimeur à la
demande qui expédie de Hong Kong (MakePlayingCards ; Printerstudio, sa société
sœur, sans doute aussi, à vérifier) fait de chaque boîte une **importation** :
à la livraison, le client paie la TVA de son pays (17 à 21 %), le droit de
douane forfaitaire de **3 € par article** que l'UE applique aux colis de moins
de 150 € depuis le 1er juillet 2026 (jusqu'en 2028), et les frais de
dédouanement du transporteur. Pour un cadeau d'enfant déjà payé, c'est la pire
surprise : **écarté pour l'envoi direct**.

Candidats dans l'UE, trouvés le 2026-10-28, **rien de vérifié** (prix, envoi au
client final, qualité) :

| Imprimeur | Pays | Ce qu'on en sait | À vérifier |
|---|---|---|---|
| Kartenspiele Design (kartenspieledesign.com) | DE | Dès 1 exemplaire, carton carte à jouer 310 g | Toilé, âme noire, envoi au client ; étui plastique sous 50 jeux, étui carton imprimé au-delà |
| MeinSpiel | DE | Dès 1 exemplaire, carton 330 g | Format 59 × 91 mm (refaire les fichiers poker) ; toilé seulement en offset dès 1 000 |
| Spiele gestalten (spiele-gestalten.de) | DE | Dès 1 exemplaire | Tout |
| Dialogistiker (dialogistiker.de) | DE | Impression à l'unité **branchée sur une boutique en ligne**, pour les professionnels : le plus proche de ce modèle | Conditions, prix, intégration avec Stripe |

**Critères**, l'exigence de qualité de la skill imprimeur (§ 2) ne change
pas : carton carte à jouer 300–330 g à âme noire ou bleue, pelliculage toilé,
coins arrondis 3 mm, format poker 63 × 88 mm (sinon refaire les fichiers),
étui carton imprimé (il porte les mentions du § 8), envoi direct au client
sans prix, numéro de suivi, certificats EN 71 des matériaux, délai.

**Méthode** : les 2–3 prototypes prévus par la skill se commandent **comme un
client**, une boîte envoyée chez soi et une chez un proche en France ou en
Belgique, pour juger le colis, le délai et l'absence de frais à la livraison.

**Si aucun imprimeur de l'UE ne tient la qualité**, mieux vaut l'impression à
la demande **par lots** que l'envoi direct depuis Hong Kong : 20 à 50 boîtes
livrées chez le propriétaire (une seule importation, payée par lui), puis
envoyées par POST Luxembourg. Stripe, le script et l'e-mail ne changent pas ;
seule l'étape 4 du § 2 devient « emballer et poster ». C'est aussi la voie la
plus simple pour la TVA (§ 8).

## 5. Le prix : l'impression à la demande coûte deux fois plus

| Par boîte livrée | Estimation |
|---|---|
| Impression d'une boîte à l'unité (skill imprimeur § 4) | 12–20 € |
| Envoi par l'imprimeur, dans l'UE | 5–9 €, à confirmer |
| Stripe (~1,5 % + 0,25 € par carte de l'UE, à vérifier sur stripe.com) | ~0,70 € |
| **Coût livré** | **~18–30 €** |

**L'extension** a le format de la boîte (54 cartes, même étui) : même coût,
même prix. Les deux ensemble partent en une commande et un seul port (articles
optionnels, § 6), ce qui allège le prix par boîte.

La boîte à **12–15 €** de la skill imprimeur (§ 7) repose sur un tirage offset
de 500 boîtes à 3–4,5 € : elle **n'est pas tenable** en impression à la
demande. Le prix se fixe après le devis : coût livré réel, plus ~20 % pour
les colis perdus et les rétractations. Il s'écrit dans `docs/commander.md`
(`prix:`) et dans Stripe.

L'impression à la demande sert à **vendre sans rien avancer** et à mesurer la
demande (la liste d'attente aussi). Passé ~200 boîtes vendues, le tirage
offset et le stock (skill § 7) ramènent la boîte vers 12–15 €.

## 6. Stripe : les réglages

- **Compte** au nom de la structure (§ 8), avec son IBAN. Faire d'abord tout le
  parcours en **mode test** (cartes de test, clé `rk_test_…` : le script écrit
  alors dans `impression/commandes-test/`).
- **Deux produits**, prix unique en euros, photo ; pas de TVA tant que la
  franchise s'applique (§ 8) :

  | Produit | Métadonnée `codes` |
  |---|---|
  | Wuertduell — boîte de départ | `B1-boutique` |
  | Wuertduell — extension Schoul et Natur | `E1-boutique` |

  La métadonnée nomme le dossier de `impression/codes/` où le script prend
  les codes. Sans elle, le script ignore le produit : c'est ce qui sépare les
  cartes d'un éventuel autre paiement sur le même compte.
- **Deux liens de paiement**, un par produit (les boutons de la page), mais
  chacun propose **l'autre en article optionnel** : la boîte et l'extension
  font une seule commande, un seul port et un seul colis. Si le
  tableau de bord n'offre pas les articles optionnels, la vente croisée du
  produit fait presque la même chose. Pour chaque lien :
  - quantité modifiable par le client, de 1 à 5 ;
  - adresse de livraison demandée, pays limités à LU, BE, FR, DE ;
  - téléphone demandé (utile au transporteur) ;
  - un tarif de livraison unique pour les quatre pays (un lien de paiement ne
    le fait pas varier selon le pays), ou la livraison comprise dans le prix ;
  - acceptation des conditions de vente obligatoire (Stripe demande l'adresse
    de `conditions-de-vente.html` dans les informations publiques du compte) ;
  - après le paiement, redirection vers
    `https://famibelle.github.io/LuxKeyb/merci-commande.html`.
- **E-mails clients** : reçu des paiements réussis activé.
- **Moyens de paiement** : carte, Apple Pay, Google Pay, Bancontact pour la
  Belgique. Un prélèvement SEPA reste « non payé » quelques jours : le script
  attend qu'il le soit.
- **Clé restreinte** pour le script, en lecture seule : sessions Checkout,
  produits, PaymentIntents et charges. Elle vit dans la variable
  `STRIPE_CLE`, jamais dans un fichier du dépôt.
- **Les liens** se collent enfin dans `docs/commander.md` (`stripe_lien`,
  `stripe_lien_extension`, avec `prix` et `prix_extension`) : celui de la
  boîte remplace la liste d'attente par les boutons ; le lien vide de
  l'extension cache seulement son bouton.

## 7. La carte code part par e-mail

L'impression à la demande imprime le même jeu pour tout le monde : pas de QR
différent par boîte. **Le code part donc par e-mail**, un par jeu commandé,
pris dans une réserve signée pour la boutique seule, une par produit (nommée
`<ENSEMBLE>-boutique`) :

```bash
python3 scripts/codes_cartes.py @B1 -n 200 -o impression/codes/B1-boutique
python3 scripts/codes_cartes.py @E1 -n 200 -o impression/codes/E1-boutique
```

Une commande de plusieurs jeux reçoit un seul e-mail, avec un code par jeu et
la liste de ce que chacun ajoute.

- Une réserve **à part** de celles du stand et des boîtes imprimées en série :
  un code n'est jamais donné deux fois. Le script s'arrête, sans rien
  préparer, si la réserve ne suffit pas.
- **La carte de règle de la boîte imprimée à la demande ne porte aucun code
  signé** : identique dans toutes les boîtes, il donnerait la boîte à
  quiconque le photographie. Elle peut dire « Votre code arrive par e-mail ».
- **Limite** : l'appli ne lit un code qu'à l'appareil photo
  (`CarnetFragment`, `Scan.REMISE`). Un e-mail lu sur le téléphone même ne se
  scanne pas : l'e-mail dit de l'afficher sur un ordinateur ou de l'imprimer.
  Évolution possible de l'appli, **pas faite** : accepter le code par un lien
  ou par « Coller un code ».
- **Option à tester au prototype** : si l'imprimeur accepte un fichier
  différent par commande, le QR (en PNG dans le dossier de la commande) peut
  devenir la carte code de la boîte, comme dans la boîte officielle.

## 8. Avant d'ouvrir : les obligations

Informations générales, **à faire confirmer**, jamais présentées comme
certaines.

1. **Statut** : autorisation d'établissement, ou asbl dont la vente est
   accessoire à l'objet (guichet.lu, House of Entrepreneurship). Bloquant :
   Stripe demande aussi la structure qui vend.
2. **TVA** :
   - Boîtes **expédiées du Luxembourg** : régime de franchise (seuil de l'ordre
     de 50 000 €/an, à vérifier) et ventes à distance dans l'UE sous
     10 000 €/an : règles luxembourgeoises, donc pas de TVA facturée en
     franchise.
   - Boîtes **expédiées par un imprimeur depuis un autre pays** (l'Allemagne…) :
     le seuil de 10 000 € ne vaut que pour les biens qui partent du pays où le
     vendeur est établi. Une boîte qui part d'Allemagne vers la France pourrait
     être taxable en France dès le premier euro (guichet unique OSS), et l'achat
     à l'imprimeur devient une opération en chaîne. **À faire trancher par
     l'AED ou un comptable avant d'ouvrir** : c'est le coût caché de l'envoi
     direct, et un argument de plus pour les lots du § 4.
3. **Mentions légales** sur le site (loi du 14 août 2000 sur le commerce
   électronique) : nom, adresse géographique, e-mail, numéro d'autorisation
   d'établissement, RCS, TVA le cas échéant.
4. **Conditions de vente** et informations avant la commande (Code de la
   consommation) : prix total, frais de livraison, délai, **rétractation de
   14 jours** à compter de la réception avec le formulaire type, garantie
   légale de conformité de deux ans, traitement des réclamations, médiateur
   (Service national du Médiateur de la consommation). Trame en annexe ; page
   `docs/conditions-de-vente.md` à écrire avec elle, relue par un juriste.
5. **Sécurité des jouets** : un jeu de cartes pour enfants dès 6 ans est un
   jouet (directive 2009/48/CE). Celui qui le vend sous son nom en est le
   **fabricant**, même si un imprimeur le fabrique : évaluation de la sécurité,
   essais EN 71-1, -2 et -3 en laboratoire (quelques centaines d'euros, à
   chiffrer), dossier technique, déclaration UE de conformité, **marquage CE**,
   nom, adresse et numéro de lot sur l'étui, avertissements **visibles aussi
   sur la page de vente**. Demander à l'imprimeur ses certificats EN 71. Le
   règlement sur la sécurité générale des produits (GPSR) demande en plus,
   sur la page de vente, le fabricant avec son adresse postale et
   électronique (`fabricant:` dans `docs/commander.md`). Un règlement européen
   adopté fin 2025 remplacera la directive dans quelques années.
6. **Données personnelles** : la boutique traite noms, adresses, e-mails et
   téléphones. La politique de confidentialité de l'appli reste vraie (l'appli
   n'envoie rien) ; la boutique a besoin de sa propre notice
   (`conditions-de-vente.html#donnees`) : qui reçoit quoi (Stripe, l'imprimeur,
   la messagerie, Formspree pour la liste d'attente), pourquoi, combien de
   temps. Pièces comptables : 10 ans. Adresses du registre : effacées une fois
   le délai de rétractation et le service après-vente passés (~3 mois).
   Liste d'attente : effacée après l'e-mail d'ouverture.
7. **Facture ou reçu** : Stripe envoie un reçu. En franchise, la mention
   d'exonération de TVA est à faire valider par le comptable ; Stripe sait
   aussi émettre une facture après le paiement (option payante).
8. **Marque BOIP** (déjà prévue, skill imprimeur § 6) : elle protège la boîte
   vendue en ligne. La carte Fierkelsmaart ne se vend pas en ligne sans
   l'accord écrit de l'organisateur.

## 9. Le site

- **`docs/commander.md`** : la page de la boutique. Tant que `stripe_lien` est
  vide, elle propose la **liste d'attente** (le formulaire Formspree des avis,
  avec le sujet « Liste d'attente : boutique Wuertduell » et des cases pour
  la boîte et l'extension, qui mesurent la demande de chacune ; un
  formulaire à part sera plus propre si les inscriptions dépassent le quota
  gratuit). Une fois `stripe_lien`, `prix` et `fabricant` remplis, elle montre
  le bouton de la boîte, celui de l'extension si son lien est rempli, les prix
  et les informations du produit.
- **`docs/merci-commande.md`** : le retour après le paiement, hors des moteurs
  de recherche (`noindex: true`, lu par `_includes/head-custom.html`).
- Liée depuis la règle du jeu. En français seulement pour l'instant : des
  clients au Luxembourg et en Allemagne demanderont l'allemand.

## 10. Le script de préparation

`scripts/commandes.py`, lancé par le propriétaire sur son ordinateur :

```bash
export STRIPE_CLE=rk_live_…
python3 scripts/commandes.py preparer            # --delai "deux à trois semaines"
# relire impression/commandes/WD-xxxx/mail.eml, passer les commandes chez l'imprimeur
export SMTP_HOTE=… SMTP_UTILISATEUR=… SMTP_MOT_DE_PASSE=…   # SMTP_PORT=465, SMTP_EXPEDITEUR
python3 scripts/commandes.py envoyer
```

Il écrit dans `impression/commandes/` (ignoré par git) :

- `registre.csv` : une ligne par commande préparée (numéro, session Stripe,
  date, nom, e-mail, pays, quantité, montant, codes donnés, date d'envoi de
  l'e-mail, numéro chez l'imprimeur). Les colonnes ajoutées à la main sont
  gardées.
- `WD-xxxx/` : `fiche.txt` (l'adresse à recopier chez l'imprimeur),
  `mail.eml` (l'e-mail du code) et un PNG par code.
- `a_commander_<date>.txt` : les fiches de la préparation du jour.

**Ce qu'il garantit** : une commande ne se prépare qu'une fois et un code ne se
donne qu'une fois (le registre, écrit après chaque commande, fait foi) ; s'il
manque des codes, rien n'est préparé ; les paiements remboursés ou pas encore
payés sont laissés de côté. **Ce qu'il ne fait pas** : passer la commande chez
l'imprimeur (à la main tant que le choix du § 4 n'est pas fait ; une API, si
l'imprimeur en a une, pourra s'ajouter), ni défaire une commande remboursée
après sa préparation.

## 11. Calendrier

- **Novembre 2026** : statut et marque (déjà prévus), compte Stripe, devis et
  prototypes chez 2–3 imprimeurs de l'UE, envoyés comme à un client (§ 4).
  Question TVA posée à l'AED ou au comptable.
- **Décembre** : choix de l'imprimeur, essais EN 71, étui avec les mentions,
  conditions de vente et mentions légales relues, prix fixé.
- **Janvier 2027** : parcours complet en mode test, puis une vraie commande de
  bout en bout, remboursée ; ouverture et e-mail à la liste d'attente.
- **Avril 2027** : la Fierkelsmaart, avec la boutique en ligne déjà rodée.

---

## Annexe : trame des conditions de vente

À compléter et à faire relire par un juriste avant toute publication.

1. **Vendeur** : nom, forme, adresse, e-mail, n° d'autorisation, RCS, TVA.
2. **Produits** : la boîte de départ du Wuertduell et l'extension Schoul
   et Natur, leur contenu, l'âge conseillé, le code envoyé par e-mail pour
   chaque jeu, qui ajoute ses cartes dans l'application gratuite
   Lëtzebuergesch Clavier.
3. **Prix** : en euros, toutes taxes comprises (mention de franchise le cas
   échéant), frais de livraison indiqués avant le paiement.
4. **Commande et paiement** : par la page Commander, paiement par Stripe ; le
   contrat est conclu au paiement ; confirmation par e-mail.
5. **Livraison** : Luxembourg, Belgique, France, Allemagne ; impression à la
   demande puis envoi direct par l'imprimeur ; délai indicatif ; que faire
   d'un colis abîmé ou perdu.
6. **Droit de rétractation** : 14 jours à compter de la réception, sans motif ;
   comment l'exercer (e-mail ou formulaire type) ; retour des jeux aux
   frais du client ; remboursement sous 14 jours par le même moyen de paiement.
   Le code déjà envoyé reste valable.
7. **Garantie légale de conformité** : deux ans.
8. **Données personnelles** (`#donnees`) : finalités, destinataires (Stripe,
   imprimeur, messagerie), durées, droits, contact.
9. **Réclamations et médiation** : contact, puis Service national du Médiateur
   de la consommation.
10. **Droit applicable** : luxembourgeois, sans priver le consommateur de la
    protection de la loi de son pays de résidence.
