# 🇱🇺 Lëtzebuergesch Clavier

**Vous le parlez. Maintenant, vous l'écrivez.** Les mots sont proposés pendant
la frappe, les accents et les majuscules se mettent à leur place, le
luxembourgeois cesse d'être souligné en rouge dans les messages, et quand
écrire est trop long, vous pouvez le dicter.

- 🛠️ Si votre luxembourgeois est très rouillé...
- 😤 Que vous galérez à écrire en lëtzebuergesch parce que votre téléphone refuse tous les mots
- 🤔 Que vous doutez de l'orthographe à chaque message...
- ➡️ Lëtzebuergesch Clavier est fait pour vous.

## ⌨️ Essayer maintenant, sans rien installer

**[Ouvrir le clavier dans le navigateur](https://famibelle.github.io/LuxKeyb/simulateur.html)**

Le simulateur est le clavier lui-même, pas une capture animée : disposition
QWERTZ, touches `é` `ä` `ë`, accents par appui long, et le même moteur de
suggestion réécrit en JavaScript (préfixe, n-grammes, distance de Levenshtein,
tolérance aux diacritiques, respect de la casse). Il tourne sur le dictionnaire
et les n-grammes livrés dans l'application, sans la couche issue du LOD.

Pour s'en servir vraiment, au quotidien, dans toutes les applications du
téléphone, il faut installer le clavier : voir [Téléchargements](#-téléchargements).

## 🧩 Un clavier, pas une application Android

L'application Android est la seule livraison disponible à ce jour, mais elle
n'est pas le projet. Ce qui fait ce clavier, ce sont ses **données de langue**
et son **moteur**, et ni l'une ni l'autre ne dépendent d'Android :

| Ce que produit le dépôt | Forme | Dépend d'Android ? |
|---|---|---|
| Dictionnaire, n-grammes, formes du LOD, gloses, exemples, grilles de jeu | JSON, produit par un pipeline Python (`Dictionnaires/`) | Non |
| Moteur de suggestion | Kotlin (`android_keyboard/`) **et** JavaScript (`docs/assets/simulateur-engine.js`) | Non |
| Bancs d'évaluation (ParaLux, corpus de traduction du ZLS) | Python et Node (`docs/scripts/`) | Non |
| Application, correcteur système, jeux, Wierderbuch | Kotlin, `InputMethodService` | Oui |

Une version iOS ou de bureau n'est ni commencée ni exclue : le travail restant
serait l'interface, pas la langue.

![Langue](https://img.shields.io/badge/Langue-lëtzebuergesch-blue?style=for-the-badge)
![Navigateur](https://img.shields.io/badge/Navigateur-essai_en_ligne-orange?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-5.0+-green?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/Code-MIT-yellow?style=for-the-badge)

> Le **code** est sous licence MIT ([`LICENSE`](LICENSE)). Les **dictionnaires**
> livrés avec l'application ne le sont pas : ce sont des œuvres dérivées de
> corpus tiers, en CC BY-NC, CC BY-SA et CC0 selon la source. Voir
> [`NOTICE.md`](NOTICE.md).

## 📱 Aperçu

<div align="center">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/captures/fr-FR/Captures%20d%27%C3%A9cran%20pour%20t%C3%A9l%C3%A9phone%201%20%28Suggestions%29.png" alt="Le clavier propose « Moien », « Moie » et « Moies » en tapant « Moi »" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/captures/fr-FR/Captures%20d%27%C3%A9cran%20pour%20t%C3%A9l%C3%A9phone%205%20%28Wierderbuch%29.png" alt="La fiche du Wierderbuch pour Gromperekichelchen : sens, exemple, autres formes" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/hors-console/fr-FR/Jeu%203%20%28Wuertriet%29.png" alt="Wuertriet, le jeu du mot de cinq lettres en six essais" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/hors-console/fr-FR/Carnet%201%20%28Bo%C3%AEte%20de%20Leitner%29.png" alt="La boîte de Leitner et ses sept casiers" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/hors-console/fr-FR/Carnet%202%20%28%C3%89ventail%20de%20cartes%29.png" alt="Les cartes d'un casier en éventail" width="19%">
</div>

*Suggestions en luxembourgeois et en français, Wierderbuch, jeux et boîte de Leitner (captures des versions 29 à 33, émulateur Pixel 9).*

## 🌟 Fonctionnalités

### 🎯 **Suggestions intelligentes**
- Suggestions contextuelles en temps réel sur **38 442 formes** relevées dans le corpus, complétées par le Lëtzebuerger Online Dictionnaire : **123 297 formes proposables**, et **149 721 reconnues** par le correcteur
- **N-grammes** : après un espace, le clavier propose la suite probable de la phrase d'après les deux mots précédents, sur **27 746 contextes**. Quand le modèle n'a rien à dire, la barre reste vide plutôt que de proposer au hasard
- **Tolérance aux fautes de frappe** : une lettre oubliée, en trop ou tapée à côté n'empêche pas la suggestion d'arriver
- **Tolérance aux diacritiques** : taper « letzebuergesch » propose « lëtzebuergesch »
- **Majuscules des noms** : la Groussschreiwung est portée par le dictionnaire, `Joer` et non `joer`
- **Le français reconnu** : 125 348 formes françaises ne sont pas soulignées, pour les mots qu'on insère en écrivant luxembourgeois. Une deuxième rangée de suggestions en français (71 586 formes) s'active dans les réglages

### ⌨️ **Écrire en lëtzebuergesch**
- Disposition **QWERTZ**, celle des claviers physiques au Luxembourg
- Par défaut, la disposition **Suisse allemand** : ü, ö et ä à droite, comme sur le clavier physique, é et ë près de l'espace
- **é, ä et ë**, les trois diacritiques les plus fréquentes de la langue, ont leur touche dédiée dans les deux dispositions
- Les plus rares (è, à, â...) sont en appui long, avec un aperçu dans le coin de la touche
- Disposition **Luxembourg** au choix dans les réglages : dix touches par rangée au lieu de onze, é à droite du l, ü et ö en appui long
- L'apostrophe de l'élision (*d'Land*, *s'Kanner*) a sa propre touche
- Panneau **emoji** complet, tons de peau compris
- **Correcteur orthographique système** : les mots luxembourgeois ne sont plus soulignés en rouge dans Messages ou Notes

### 📖 **Wierderbuch, le dictionnaire intégré**
- **88 883 formes traduites** depuis le LOD, en français, allemand, anglais et portugais, cherchables dans les deux sens à partir d'un seul champ : « Haus », « maison », « house », « Katze » ou « casa »
- **26 149 mots illustrés** par les phrases d'exemple du LOD, avec leur traduction officielle quand le ZLS l'a publiée, et un lien vers l'article sur lod.lu
- Les résultats sont groupés par famille de formes : chercher `Haiser` mène à `Haus`

### 🗂️ **Carnet et révision espacée**
- Chaque mot gagné dans un jeu devient une **carte** : sens, phrase d'exemple du LOD, traduction officielle quand elle existe, rareté d'après le rang de fréquence du mot
- La **boîte de Leitner** range les cartes dans sept casiers, de « 1 jour » à « acquis » : une bonne réponse fait avancer la carte, elle revient de plus en plus tard
- Une carte se révise sans rien taper : on la retourne, puis on dit si on la savait
- Écrire le mot au clavier, dans n'importe quelle application, compte comme une révision réussie. Ce compteur reste sur l'appareil et n'est jamais sauvegardé

### 🏆 **Progression et jeux**
- Huit niveaux, d'**Ufänker** à **Sproochenmeeschter**, selon la part du dictionnaire employée
- Carte de niveau partageable
- Sept jeux de vocabulaire : **Wuertsich** (mots mêlés), **Wuertmix** (anagrammes), **Wuertriet** (six essais), **Wuertlück** (texte à trou), **Zuelwuert** (les nombres en toutes lettres), **Kräizwuert** (mots croisés) et **Wuertplaz** (mots à placer)

### 🌍 **Dans votre langue**
- L'application s'affiche en **français, allemand, anglais, portugais ou luxembourgeois**, selon la langue du téléphone (en anglais pour les autres langues)
- Depuis Android 13, la langue de l'application se choisit sans changer celle du téléphone : Paramètres › Applications › Lëtzebuergesch Clavier › Langue
- Les traductions des mots, le carnet, le mot du jour et les grilles de Kräizwuert et de Wuertplaz suivent la langue de l'application. Les noms des jeux, des onglets et des niveaux restent en luxembourgeois

### 🔒 **Vie privée**
**Ce que vous tapez ne quitte jamais l'appareil** : la frappe, les suggestions,
le correcteur et les jeux fonctionnent entièrement hors ligne. Seuls les mots
déjà présents dans le dictionnaire sont comptés pour la progression, si bien que
mots de passe et termes personnels ne sont jamais enregistrés. La seule donnée
qui sort est la **voix, pendant une [dictée](#-dictée-vocale)** : elle part,
chiffrée, vers le service LuxASR de l'Université du Luxembourg, qui la transcrit
sans la conserver. Sans appui sur le micro, aucune connexion n'est ouverte.
Détails dans la [politique de confidentialité](https://famibelle.github.io/LuxKeyb/privacy/privacy-policy.html).

### 🏛️ **Construit sur les ressources publiques du Luxembourg**
Ce clavier existe parce que le Luxembourg a ouvert ses ressources de langue.
Il les met dans la poche de ceux qui écrivent :

| Ressource | Publiée par | Ce que le clavier en fait |
|---|---|---|
| **LOD, Linguistesch Daten** (CC0, data.public.lu) | ZLS | Les traductions, les phrases d'exemple, les familles de formes, et 84 855 formes ajoutées aux suggestions |
| **LOD, Index vun der Sich-Funktioun** (CC0) | ZLS | Le pont entre les formes conjuguées ou déclinées et leur article : `Haiser` mène à `Haus` |
| **Méisproochegen Iwwersetzungskorpus** (CC0) | ZLS | Les traductions officielles sous les phrases d'exemple, et le banc d'essai de la prédiction |
| **LuxASR** | Université du Luxembourg | La dictée vocale |
| **LuxAlign** et **LETZ** | Université du Luxembourg | Les fréquences et les contextes qui prédisent le mot suivant |

### 📚 **Corpus des suggestions**
Le dictionnaire et les n-grammes sont produits à partir de deux corpus publics
de luxembourgeois contemporain :

- **[LuxAlign](https://huggingface.co/datasets/fredxlpy/LuxAlign)** (CC BY-NC 4.0), 180 342 phrases de RTL.lu, qui apporte le vocabulaire et presque tous les n-grammes ;
- **[LETZ](https://huggingface.co/datasets/fredxlpy/LETZ)** (CC BY 4.0), 5 862 phrases d'exemple du LOD, soixante fois plus petit, mais le seul endroit où `dech`, `däin`, `hues`, `mamm` apparaissent en nombre, c'est-à-dire exactement ce qu'on tape sur un téléphone.

Le pipeline est relancé à chaque build, ce qui fait évoluer les suggestions avec
l'usage réel de la langue. La qualité est mesurée sur des textes **jamais vus à
l'entraînement** : le corpus de traduction du ZLS (135 343 événements de frappe)
et ParaLux. Citations et détail dans
[`Dictionnaires/CORPUS.md`](Dictionnaires/CORPUS.md).

### ⚖️ **Face à Gboard et au clavier Apple**
Gboard gère le luxembourgeois parmi 900 autres langues, et Apple ne l'ajoutera
qu'avec iOS 27 : voir le **[comparatif détaillé](COMPARATIF.md)**, disposition,
dictionnaire, vie privée, et ce que les autres font mieux.

## 🎙️ Dictée vocale

Un bouton micro s'affiche à droite de la barre de suggestions : un appui
l'allume, le texte s'écrit pendant qu'on parle (souligné tant qu'il n'est pas
confirmé), et la dictée s'arrête d'elle-même quand on se tait, ou au second
appui. Elle reste désactivée dans les champs de mot de passe. Avant la première
dictée, un écran explique où part la voix, et rien n'est envoyé avant
« J'accepte ».

La reconnaissance est assurée par **[LuxASR](https://luxasr.uni.lu)**, le
service de l'Université du Luxembourg, dont le moteur temps réel est publié par
Peter Gilles sous le nom [LuxASRlive](https://github.com/PeterGilles/LuxASRlive)
(Apache 2.0). Le
son part en flux chiffré (`wss://luxasr.uni.lu`) pendant la dictée, et pendant
la dictée seulement ; l'Université en est responsable de traitement, le
transcrit sur le moment et ne le conserve pas.

Ce que le clavier fait du réseau :

- **sans réseau**, le micro apparaît barré et rien n'est tenté ;
- **réseau lent** : au-delà d'une seconde d'audio en attente d'envoi, le bandeau
  le signale ; à huit secondes, le micro se coupe mais ce qui attend finit de
  partir, et le texte arrive en entier ;
- **réseau bloqué** (plus rien ne part depuis 5 s) : la dictée s'arrête et garde
  le texte déjà reçu.

### Pourquoi en ligne, et pas dans le téléphone

Une dictée entièrement embarquée a été essayée et mesurée, avec le même clavier :

| | **Modèle embarqué** | **LuxASR en ligne** |
|---|---|---|
| Reconnaissance | `whisper-tiny` dans le téléphone, dérivé du modèle [`unilux/whisper-tiny-v1-luxembourgish`](https://huggingface.co/unilux/whisper-tiny-v1-luxembourgish) de l'Université du Luxembourg (licence open-mdw) | Service [LuxASR](https://luxasr.uni.lu) de l'Université du Luxembourg |
| Où va la voix | Nulle part | À `luxasr.uni.lu`, pendant la dictée |
| Sans connexion | Oui, avion et tunnel compris | Non |
| Mots erronés | **72 %** sur 161 énoncés de conférences de presse : trop pour être utile | **25,4 %** sur 22 dictées d'une à trois phrases, 11 à 15 % sur un extrait lu proprement |
| Délai | ≈ 6 s par passe sur un téléphone ancien | premier aperçu ≈ 1,1 s, texte engagé ≈ 0,23 s après la dernière syllabe |
| Poids ajouté à l'APK | 31 Mo de modèle | rien |

Au-delà d'environ 25 % de mots erronés, corriger coûte plus cher que taper. Des
modèles plus gros (`base`, `small`) ont aussi été mesurés sur un Galaxy A21s : 9
à 32 s d'attente par phrase, pour une précision encore inférieure au service.

Les deux services publics de la parole n'ont pas le même propriétaire : LuxASR
est celui de l'Université du Luxembourg, le seul appelé ici. La
*Sproochmaschinn* et la *Schreifmaschinn* relèvent du Zenter fir d'Lëtzebuerger
Sprooch (ministère de la Culture) : le clavier ne les appelle pas.

### Comment ça marche

L'application pousse l'audio en PCM 16 bits à 16 kHz sur une connexion
WebSocket vers `luxasr.uni.lu`. Le service redécode l'énoncé en cours toutes les
demi-secondes à une seconde et n'engage un mot qu'après l'avoir vu à la même
place dans trois hypothèses de suite ; la fin encore instable revient à part et
s'affiche en aperçu. Aucun enregistrement n'est conservé sur le téléphone.

## 🔬 Labs : les versions d'essai

**Labs** est le canal des versions expérimentales : un APK construit à partir
d'une branche de travail, pour la faire essayer avant qu'elle n'atteigne la
version stable. La dictée vocale y a été essayée avant d'entrer dans la
version stable avec la 33.0.0 ; la page
[Labs](https://famibelle.github.io/LuxKeyb/labs.html) raconte désormais son
fonctionnement.

### Installer une version Labs

1. Téléchargez l'APK depuis la [préversion `labs`](https://github.com/famibelle/LuxKeyb/releases/tag/labs) 
2. Autorisez l'installation depuis cette source, puis installez l'APK
3. Activez le clavier dans Paramètres → Système → Claviers, choisissez-le comme clavier courant
4. Essayez la nouveauté décrite sur la page de la préversion

L'APK Labs est **signé avec la clé de production** : il remplace l'application
installée, y compris celle reçue depuis Google Play, et se réinstalle par-dessus
la version stable sans rien désinstaller. Réglages et progression sont conservés.
Son icône porte la mention **LABS** pour qu'on sache laquelle des deux est
installée. Ce n'est pas la version stable : elle est reconstruite à chaque
amélioration et peut changer d'un jour à l'autre.

### Comment c'est construit

Le workflow [`.github/workflows/labs.yml`](.github/workflows/labs.yml)
régénère le dictionnaire, lance les tests, construit un APK release signé et le
publie comme **préversion GitHub sous un tag roulant** : une adresse de
téléchargement qui ne change jamais. Quatre choix délibérés :

- la préversion n'est **jamais** marquée « latest », sinon le lien de téléchargement du simulateur servirait un build de laboratoire aux visiteurs ordinaires ;
- la publication automatique est **épinglée à des branches nommées**, chacune avec son tag : deux expériences ne peuvent pas se disputer le même ;
- **pas de repli sur une clé de debug** : un APK Labs signé debug ne s'installerait pas par-dessus une version stable, et on ne le découvrirait qu'à l'installation ;
- l'APK est **inspecté**, pas supposé : dictionnaire présent, signature valide, et plus aucune trace de l'ancienne dictée embarquée (bibliothèque native ou modèle).

La dictée embarquée a quitté le code ; le banc [`stt/`](stt/README.md) reste
capable de mesurer un modèle hors ligne si la question revient.

### Un retour à nous faire

Le plus utile est un cas précis : le modèle de téléphone, ce que vous avez dit,
et ce qui s'est écrit. Passez par le
[formulaire de retours](https://famibelle.github.io/LuxKeyb/feedbacks_form.html)
ou ouvrez une [issue](https://github.com/famibelle/LuxKeyb/issues).

## 📦 Téléchargements

### 🚀 **Dernière version stable**

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/famibelle/LuxKeyb?style=for-the-badge&logo=github)](https://github.com/famibelle/LuxKeyb/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/famibelle/LuxKeyb/total?style=for-the-badge&logo=github)](https://github.com/famibelle/LuxKeyb/releases)

### 🛒 **Sur Google Play**

Depuis le 28 septembre 2026, l'application est **disponible librement sur le
Play Store**. C'est la voie la plus simple : installation en un geste, mises à
jour automatiques, sans autoriser les « sources inconnues ».

**[Ouvrir la fiche Google Play](https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard)**

<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard"><img src="docs/assets/qr-luxkeyb-store.png" alt="QR code ouvrant la fiche de Lëtzebuergesch Clavier sur Google Play" width="200"></a>
  <br><em>Scannez depuis votre téléphone : la fiche s'ouvre</em>
</div>

Des retours ou une question ? Ouvrez une [issue](https://github.com/famibelle/LuxKeyb/issues) ou passez par le
[formulaire de retours](https://famibelle.github.io/LuxKeyb/feedbacks_form.html).

L'APK ci-dessous reste disponible pour qui préfère s'en passer, et contient exactement le même code.

### 📱 **Installation rapide**

1. **Téléchargez l'APK** depuis la [dernière release](https://github.com/famibelle/LuxKeyb/releases/latest)
2. **Autorisez les sources inconnues** dans les paramètres Android
3. **Installez l'APK** en touchant le fichier
4. **Activez le clavier** dans Paramètres → Système → Langues et saisie

### 📦 **Types d'APK disponibles**

| Type | Description | Taille | Usage |
|------|-------------|--------|-------|
| **Release APK** | Optimisée production, sans journaux | ~14 Mo | ✅ Recommandé |
| **Debug APK** | Journaux verbeux, à ne pas installer au quotidien | plus lourde | 🔧 Dev |

### 🔄 **Mises à jour automatiques**

Les nouvelles versions sont construites et publiées par **GitHub Actions** :
- ✅ **Build automatique** à chaque push sur `main`
- ✅ **APK signés** prêts pour l'installation
- ✅ **Releases automatiques** sur [GitHub Releases](https://github.com/famibelle/LuxKeyb/releases) à chaque tag `v*.*.*`

## ⚙️ Compilation depuis les sources

### Prérequis
- **JDK 17** et le SDK Android 36
- **Android 7.0** (API 24) ou supérieur pour installer le résultat
- **20 Mo** d'espace libre

### Construire l'application

```bash
git clone https://github.com/famibelle/LuxKeyb.git
cd LuxKeyb/android_keyboard
./gradlew assembleDebug      # APK de développement
./gradlew installDebug       # installation sur appareil ou émulateur
./gradlew assembleRelease    # APK de production
./gradlew testDebugUnitTest  # la suite de tests, près de 400 tests
```

Puis **activer le clavier** :
- Paramètres → Système → Langues et saisie
- Sélectionner **Claviers virtuels**
- Activer **Klaviatur Lëtzebuergesch**, puis le définir comme clavier par défaut

### Régénérer les données de langue

```bash
cd Dictionnaires
pip install datasets huggingface_hub
python LuxembourgishComplet.py --strict     # dictionnaire et n-grammes
python generate_lod_forms.py --strict       # couverture LOD
python generate_translations.py --strict    # gloses, familles, exemples
```

Les deux corpus sont publics : aucun jeton Hugging Face n'est nécessaire.
L'ordre compte, les scripts suivants lisent les fichiers produits par les
précédents. Le détail du pipeline est dans
[`Dictionnaires/README.md`](Dictionnaires/README.md), les corpus et leurs
citations dans [`Dictionnaires/CORPUS.md`](Dictionnaires/CORPUS.md), et les
chiffres mesurés sur la
[page corpus du site](https://famibelle.github.io/LuxKeyb/corpus.html).

## 🚀 Utilisation

### Accents
Appui long sur une lettre : un popup s'affiche (a → ä à â, e → é ë è ê,
u → ü û ù, o → ô ö). Glissez le doigt vers l'accent voulu, puis relâchez.

Par défaut, la disposition **Suisse allemand** donne en touches directes
**ü**, **ö** et **ä** à droite (ü à droite du p, ö et ä à droite du l), et
**é** et **ë** autour de la barre d'espace : onze touches par rangée.

La disposition **Luxembourg** (Réglages du clavier → Disposition) garde dix
touches par rangée, plus larges : **é** à droite du l, **ä** et **ë** autour de
la barre d'espace, ü et ö en appui long.

### Suggestions
Commencez à taper : les suggestions apparaissent au-dessus des touches, sur
fond rouge. Touchez-en une pour l'insérer. Une rangée de suggestions en
français, sur fond bleu, s'active dans Réglages du clavier → Suggestions.

### Dictée
Touchez le micro à droite de la barre de suggestions et parlez : le texte
s'écrit à mesure. Le micro se referme quand vous vous taisez.

## 🏗️ Architecture

Côté Android, le service `KreyolInputMethodServiceRefactored` coordonne quatre
composants par interfaces de rappel : `KeyboardLayoutManager` (construction des
touches), `SuggestionEngine` (dictionnaire, n-grammes, Levenshtein),
`AccentHandler` (appui long) et `InputProcessor` (traitement des touches).

Le même moteur de suggestion existe en JavaScript dans
[`docs/assets/simulateur-engine.js`](docs/assets/simulateur-engine.js) : c'est
lui qui fait tourner le clavier dans le navigateur, avec les mêmes règles de
score, de casse et de tolérance aux fautes. Les choix techniques sont résumés
sur la [fiche technique](https://famibelle.github.io/LuxKeyb/dossier-technique.html).

> Les noms de classes et le package `com.example.kreyolkeyboard` viennent du
> clavier créole dont ce projet est issu et avec lequel il partage sa base de
> code. Ce sont des alias historiques, pas une trace de créole dans le produit.

### Technologies utilisées
- **Kotlin** pour l'application, compilé nativement par AGP 9
- **JavaScript** pour le portage du moteur dans le navigateur
- **Android InputMethodService** comme cadre du clavier
- **JSON** pour le dictionnaire, les n-grammes et les données de jeu
- **Gradle 9.6** et **AGP 9.3** pour le build
- **JUnit 4**, près de 400 tests unitaires exécutés en CI
- **Python et Hugging Face** pour le pipeline de génération des données
- **GitHub Actions** pour l'intégration continue
