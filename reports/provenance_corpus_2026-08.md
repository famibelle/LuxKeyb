# Provenance du corpus et droits associés

**Document de travail interne.** Il établit d'où viennent les données
linguistiques du projet, ce qui est effectivement distribué dans
l'application, et où se situent les points à sécuriser. Il est destiné à être
présenté à un service juridique de collectivité ou à une diligence
d'investisseur, une fois validé.

*Établi le 23 août 2026, à partir de l'état du dépôt à cette date. Ce
document n'est pas un avis juridique : les positions qu'il décrit doivent
être confirmées par un conseil.*

## Pourquoi ce document

Le dictionnaire et le modèle de prédiction sont construits à partir de textes
créoles d'auteurs identifiés, dont plusieurs sont vivants ou récemment
publiés. La question des droits sera posée par tout acheteur public dont le
service juridique instruit une convention, et par tout investisseur dont la
diligence porte sur la propriété intellectuelle. Y répondre par avance, avec
des faits, coûte une page. Y répondre dans l'urgence, en pleine négociation,
coûte la négociation.

## Deux objets distincts, deux situations distinctes

C'est la distinction centrale, et elle est souvent perdue dans les discussions.

### 1. Ce qui est distribué dans l'application

Les fichiers embarqués dans l'APK ne contiennent **aucun texte suivi**.

| Fichier | Contenu réel | Volume |
|---|---|---|
| `creole_dict.json` | Une liste de couples `[mot, fréquence]` | 5 296 entrées |
| `creole_ngrams.json` | Par clé, la liste des mots suivants observés avec leur probabilité | 8 852 clés |
| `french_simple_dict.json` | Dictionnaire français de repli | 662 mots |

Les clés du modèle n-grammes font un ou deux mots, et chaque successeur en
fait un. **La plus longue séquence reconstructible depuis les données
distribuées est donc de trois mots.** Aucune œuvre n'est reproduite, aucun
extrait identifiable ne l'est, et rien ne permet de remonter d'une suggestion
à sa source.

Sur cet objet, la position est solide : il s'agit d'un modèle statistique
dérivé, relevant de l'exception de fouille de textes et de données prévue par
le code de la propriété intellectuelle, et l'artefact distribué ne comporte
aucune reproduction substantielle.

### 2. Ce qui est publié comme corpus brut

Le corpus source est publié à **deux endroits publics** : le fichier
`PawolKreyol/Textes_kreyol.json` du dépôt GitHub, et le jeu de données
Hugging Face `POTOMITAN/PawolKreyol-gfc`, dont l'export parquet est lisible
sans jeton d'accès.

C'est une reproduction, et l'exception de fouille ne couvre pas la
republication des textes eux-mêmes. **C'est là, et seulement là, que se
situe l'exposition réelle du projet.**

## Ce que contient le corpus brut, en chiffres

2 522 entrées, chacune portant un champ `Source`, pour 86 sources distinctes.
La traçabilité est donc native, ce qui est un atout considérable : la plupart
des corpus comparables en sont dépourvus.

**La très grande majorité des entrées sont courtes :**

| Longueur de l'entrée | Nombre d'entrées | Part |
|---|---|---|
| Moins de 50 caractères | 2 072 | 82 % |
| Moins de 200 caractères | 2 438 | 97 % |
| Moins de 1 000 caractères | 2 490 | 99 % |
| 1 000 caractères et plus | 32 | 1 % |

Autrement dit, l'essentiel du corpus est constitué de mots isolés, de
locutions et de phrases courtes, dont la reprise ne pose pas de difficulté
sérieuse. Le sujet se concentre sur une trentaine d'entrées.

**La source la plus représentée est interne** : 1 605 entrées proviennent de
`POTOMITAN/potomitan-gcf-fr-translation`, matériel de traduction produit dans
le cadre du projet. C'est plus de 60 % du corpus, et cette part est libre de
toute revendication extérieure. Une réserve toutefois : une partie de ces
entrées est annotée comme dérivée de cours Assimil, matériel tiers protégé,
dont le statut est à vérifier.

## Les trois points à sécuriser, par ordre d'urgence

### 1. Une entrée longue, isolée et très identifiable

Une seule entrée fait **42 906 caractères** : une pièce de théâtre de Sonny
Rupaire, publiée en 1971 chez Édisyon Parabole. À elle seule, elle représente
davantage de texte que toutes les autres entrées longues réunies, et sa
reprise dans un corpus public est difficile à rattacher à une pratique de
citation.

C'est le point à traiter en premier, et il se traite simplement : soit une
autorisation écrite des ayants droit, soit le retrait de l'entrée du corpus
**publié**. Le retrait ne dégrade pratiquement pas le clavier, puisque les
statistiques déjà calculées restent valables et que le vocabulaire de cette
pièce est largement couvert par le reste du corpus. À vérifier par un
recalcul avant décision.

### 2. Une trentaine d'entrées de plusieurs milliers de caractères

Viennent ensuite des extraits de 2 800 à 3 000 caractères, provenant
notamment de Max Rippon (*Pègmèl*, 2013), Gerty Dambury (Les Éditions du
Manguier, 2015) et Jean Juraver (*Contes créoles*, 1985). Ces volumes se
discutent : selon les cas, ils relèvent de la citation ou la dépassent. Un
conseil doit trancher, entrée par entrée. Le champ `Source` rend cet examen
rapide.

### 3. Des auteurs vivants, à solliciter

Le corpus mobilise des autrices et auteurs vivants ou récemment publiés :
Gisèle Pineau (Stock, 1996, dans une traduction de Sylviane Telchid), Robert
Fontes (traduction du *Petit Prince*, Tintenfass, 2023), Alain Vérin (Nèg
Mawon, 2020), André Alidor (2024), Marga Anzala, Brigitte Démocrite.

C'est un risque, mais c'est surtout une occasion. Le projet les met déjà en
avant publiquement, dans l'application comme dans le dossier de presse, et
la démarche d'autorisation est reçue comme une reconnaissance plutôt que
comme une formalité. Une autorisation écrite obtenue devient un actif :
opposable en diligence, et citable en communication.

## Ce qui est recommandé

1. **Faire valider par un conseil** la position décrite plus haut sur les
   données distribuées, et lui soumettre la liste des entrées longues.
2. **Traiter l'entrée de 42 906 caractères** avant toute nouvelle publication
   du corpus.
3. **Engager les demandes d'autorisation** auprès des auteurs vivants et des
   ayants droit, en commençant par ceux dont le nom figure déjà dans les
   supports publics du projet.
4. **Ajouter un champ de statut** à côté du champ `Source` existant, portant
   pour chaque entrée l'origine du droit invoqué : production interne,
   autorisation écrite, domaine public, citation courte, à examiner. Le champ
   `Source` rend ce travail réalisable entrée par entrée.
5. **Publier une version courte de cette note** sur le site, une fois validée.
   La transparence sur la provenance est un argument en achat public, pas une
   confession.
6. **Vérifier le statut du matériel Assimil** figurant dans la source interne.

## Ce que ce document permet de dire, dès maintenant

Sans attendre le reste, ces trois affirmations sont exactes et vérifiables
par un tiers dans le dépôt :

- L'application distribuée ne contient aucun texte d'auteur, uniquement des
  fréquences et des probabilités de succession.
- La plus longue séquence de mots reconstructible depuis les données
  embarquées est de trois mots.
- Chaque entrée du corpus porte sa source, ce qui rend l'examen des droits
  possible entrée par entrée plutôt que globalement.

C'est déjà, en l'état, plus que ce que la plupart des projets comparables
peuvent démontrer.
