# 🇱🇺 Lëtzebuergesch Clavier

**Écrire en luxembourgeois sans se battre avec son clavier.** Les mots sont
proposés pendant la frappe, les accents et les majuscules se mettent à leur
place, et le luxembourgeois cesse d'être souligné en rouge dans les messages.

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
   <img src="LuxKeybPlayStore/graphics/feature-graphic/Captures%20d%27%C3%A9cran%20pour%20t%C3%A9l%C3%A9phone%201%20%28Suggestions%29.png" alt="Le clavier propose « giess » en tapant « Ech hunn op der Schueberfouer Gromperekichelcher »" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/Captures%20d%27%C3%A9cran%20pour%20t%C3%A9l%C3%A9phone%204%20%28Wierderbuch%29.png" alt="La fiche du Wierderbuch pour Gromperekichelchen : sens, exemple, autres formes" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/Jeu%203%20%28Wuertriet%29.png" alt="Wuertriet, le jeu du mot de cinq lettres en six essais" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/Carnet%201%20%28Bo%C3%AEte%20de%20Leitner%29.png" alt="La boîte de Leitner et ses sept casiers" width="19%">
   <img src="LuxKeybPlayStore/graphics/feature-graphic/Carnet%202%20%28%C3%89ventail%20de%20cartes%29.png" alt="Les cartes d'un casier en éventail" width="19%">
</div>

*Suggestions en luxembourgeois et en français, Wierderbuch, jeux et boîte de Leitner (captures de la version 29, émulateur Pixel 9).*

## 🌟 Fonctionnalités

### 🎯 **Suggestions intelligentes**
- Suggestions contextuelles en temps réel sur **38 442 formes** relevées dans le corpus, complétées par le Lëtzebuerger Online Dictionnaire : **123 297 formes proposables**, et **149 721 reconnues** par le correcteur
- **N-grammes** : après un espace, le clavier propose la suite probable de la phrase d'après les deux mots précédents, sur **27 746 contextes**. Quand le modèle n'a rien à dire, la barre reste vide plutôt que de proposer au hasard
- **Tolérance aux fautes de frappe** : une lettre oubliée, en trop ou tapée à côté n'empêche pas la suggestion d'arriver
- **Tolérance aux diacritiques** : taper « letzebuergesch » propose « lëtzebuergesch »
- **Majuscules des noms** : la Groussschreiwung est portée par le dictionnaire, `Joer` et non `joer`
- **Deuxième rangée en français** : 71 586 formes proposées, 125 348 reconnues, pour les mots français qu'on insère en écrivant luxembourgeois

### ⌨️ **Écrire en lëtzebuergesch**
- Disposition **QWERTZ**, celle des claviers physiques au Luxembourg
- **é, ä et ë** ont chacune leur touche dédiée, les trois diacritiques les plus fréquentes de la langue
- Les plus rares (ü, ö, à, â...) sont en appui long, avec un aperçu dans le coin de la touche
- L'apostrophe de l'élision (*d'Land*, *s'Kanner*) a sa propre touche
- Panneau **emoji** complet, tons de peau compris
- **Correcteur orthographique système** : les mots luxembourgeois ne sont plus soulignés en rouge dans Messages ou Notes

### 📖 **Wierderbuch, le dictionnaire intégré**
- **88 883 formes glossées** en français depuis le LOD, cherchables dans les deux sens à partir d'un seul champ
- **26 149 mots illustrés** par les phrases d'exemple du LOD, et un lien vers l'article officiel sur lod.lu
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

### 🔒 **Vie privée**
La version publiée du clavier fonctionne **entièrement hors ligne** : il n'a aucun accès à Internet.
Seuls les mots déjà présents dans le dictionnaire sont comptés pour la
progression, si bien que mots de passe et termes personnels ne sont jamais
enregistrés, et rien ne quitte l'appareil. La dictée vocale expérimentale de
[Labs](#-labs--la-dictée-vocale-en-avant-première) est à part : sa variante en
ligne envoie le son du micro à un service tiers, et n'est pas distribuée par le
Play Store.

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

## 🔬 Labs : la dictée vocale, en avant-première

**Labs** est le canal des versions expérimentales : un APK construit à partir
d'une branche de travail, pour la faire essayer avant qu'elle n'atteigne la
version stable. La dictée vocale en luxembourgeois y vit depuis août 2026. Elle
**n'est pas sur `main`, ni sur Google Play**, et ce n'est pas un oubli.

Un bouton micro s'affiche à droite de la barre de suggestions : un appui
l'allume, le texte s'écrit pendant qu'on parle (souligné tant qu'il n'est pas
confirmé), un second appui fige le résultat, et la dictée s'arrête d'elle-même
après un silence. Elle reste désactivée dans les champs de mot de passe.

### Deux variantes, deux compromis

Les deux versions sont le même clavier, à un détail près : d'où vient le texte
quand on parle.

| | **Modèle embarqué** | **Démonstration LuxASR en ligne** |
|---|---|---|
| Reconnaissance | `whisper-tiny` dans le téléphone, dérivé du modèle [`unilux/whisper-tiny-v1-luxembourgish`](https://huggingface.co/unilux/whisper-tiny-v1-luxembourgish) de l'Université du Luxembourg (licence open-mdw) | Service [LuxASR](https://luxasr.uni.lu) de l'Université du Luxembourg |
| Où va la voix | **Nulle part** : aucune permission réseau | **À `luxasr.uni.lu`**, en continu pendant la dictée |
| Sans connexion | Oui, avion et tunnel compris | Non, pas de repli hors ligne, volontairement |
| Mots erronés | **72 %** sur 161 énoncés de conférences de presse : trop pour être utile | **25,4 %** sur 22 dictées d'une à trois phrases, 11 à 15 % sur un extrait lu proprement |
| Délai | ≈ 6 s par passe sur un téléphone ancien | premier aperçu ≈ 1,1 s, texte engagé ≈ 0,23 s après la dernière syllabe |
| Taille de l'APK | ≈ 38 Mo, dont 31 pour le modèle | ≈ 6 Mo |
| Branche | `feat/speech-to-text-lb` | `feat/luxasr-online` |

Au-delà d'environ 25 % de mots erronés, corriger coûte plus cher que taper :
c'est pourquoi le modèle embarqué, malgré son avantage de confidentialité, n'est
pas proposé comme une fonction. Les chiffres, leurs conditions de mesure et
leurs limites sont détaillés sur la page
[Labs](https://famibelle.github.io/LuxKeyb/labs.html).

### Pourquoi ce n'est pas dans la version stable

- **La politique de confidentialité publiée décrit une application qui
  n'envoie rien.** La dictée en ligne la contredit : la version stable ne
  l'intégrera pas avant que la politique ait été réécrite.
- **Une dictée en ligne supposerait l'accord formel du service appelé.** Le
  traitement des enregistrements par ce service ne relève pas de ce projet.
- **Les deux services publics de la parole n'ont pas le même propriétaire.**
  LuxASR est celui de l'Université du Luxembourg, le seul appelé ici. La
  *Sproochmaschinn* et la *Schreifmaschinn* relèvent du Zenter fir d'Lëtzebuerger
  Sprooch (ministère de la Culture) : le clavier ne les appelle pas.

### Installer une version Labs

1. Téléchargez l'APK depuis la [préversion `labs`](https://github.com/famibelle/LuxKeyb/releases/tag/labs) (ou depuis la page [Labs](https://famibelle.github.io/LuxKeyb/labs.html) du site, qui affiche le build réellement disponible)
2. Autorisez l'installation depuis cette source, puis installez l'APK
3. Activez le clavier dans Paramètres → Système → Claviers, choisissez-le comme clavier courant
4. Ouvrez un champ de texte, touchez le micro et autorisez l'accès au microphone au premier usage

L'APK Labs est **signé avec la clé de production** : il remplace l'application
installée, y compris celle reçue depuis Google Play, et se réinstalle par-dessus
la version stable sans rien désinstaller. Réglages et progression sont conservés.
Son icône porte la mention **LABS** pour qu'on sache laquelle des deux est
installée. Ce n'est pas la version stable : elle est reconstruite à chaque
amélioration et peut changer d'un jour à l'autre.

### Comment c'est construit

Le workflow [`.github/workflows/labs.yml`](.github/workflows/labs.yml)
régénère le dictionnaire et le modèle, lance les tests, construit un APK release
signé et le publie comme **préversion GitHub sous le tag roulant `labs`** : une
adresse de téléchargement qui ne change jamais. Quatre choix délibérés :

- la préversion n'est **jamais** marquée « latest », sinon le lien de téléchargement du simulateur servirait un build de laboratoire aux visiteurs ordinaires ;
- la publication automatique est **épinglée à une seule branche** : deux expériences ne peuvent pas se disputer le même tag ;
- **pas de repli sur une clé de debug** : un APK Labs signé debug ne s'installerait pas par-dessus une version stable, et on ne le découvrirait qu'à l'installation ;
- l'APK est **inspecté**, pas supposé : bibliothèque native présente pour les deux architectures ARM, modèle présent et non compressé, signature valide.

**Modèle embarqué** (détail dans [`stt/README.md`](stt/README.md)) : moteur
[whisper.cpp](https://github.com/ggml-org/whisper.cpp) en JNI. Whisper n'a pas de
mode flux : le temps réel consiste à retranscrire l'énoncé entier toutes les
900 ms et à remplacer le texte en cours, dans une fenêtre de 30 s au plus. Le
modèle pèse 31 Mo et n'est **pas versionné** (l'historique git est définitif) :
la CI le convertit depuis Hugging Face avec `stt/convert_model.py`. Il n'est
chargé qu'au premier appui sur le micro et libéré à la sortie du champ, le
calcul réclamant environ 165 Mo de mémoire.

**Démonstration en ligne** : l'application pousse l'audio en PCM 16 bits à
16 kHz sur une connexion WebSocket vers `luxasr.uni.lu`. Le service redécode
l'énoncé en cours toutes les demi-secondes à une seconde et n'engage un mot
qu'après l'avoir vu à la même place dans trois hypothèses de suite ; la fin
encore instable revient à part et s'affiche en aperçu. Aucun enregistrement
n'est conservé sur le téléphone.

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
| **Release APK** | Optimisée production, sans journaux | ~7,7 Mo | ✅ Recommandé |
| **Debug APK** | Journaux verbeux, à ne pas installer au quotidien | plus lourde | 🔧 Dev |

### 🔄 **Mises à jour automatiques**

Les nouvelles versions sont construites et publiées par **GitHub Actions** :
- ✅ **Build automatique** à chaque push sur `main`
- ✅ **APK signés** prêts pour l'installation
- ✅ **Releases automatiques** sur [GitHub Releases](https://github.com/famibelle/LuxKeyb/releases) à chaque tag `v*.*.*`

## ⚙️ Compilation depuis les sources

### Prérequis
- **JDK 17** et le SDK Android 36
- **Android 5.0** (API 21) ou supérieur pour installer le résultat
- **20 Mo** d'espace libre

### Construire l'application

```bash
git clone https://github.com/famibelle/LuxKeyb.git
cd LuxKeyb/android_keyboard
./gradlew assembleDebug      # APK de développement
./gradlew installDebug       # installation sur appareil ou émulateur
./gradlew assembleRelease    # APK de production
./gradlew testDebugUnitTest  # la suite de tests, plus de 340 tests
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

Les trois diacritiques les plus fréquentes, **é**, **ä** et **ë**, ont leur
propre touche, sans appui long.

### Suggestions
Commencez à taper : les suggestions apparaissent au-dessus des touches, le
luxembourgeois sur fond rouge, le français sur fond bleu. Touchez-en une pour
l'insérer.

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
- **JUnit 4**, plus de 340 tests unitaires exécutés en CI
- **Python et Hugging Face** pour le pipeline de génération des données
- **GitHub Actions** pour l'intégration continue
