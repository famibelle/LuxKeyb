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
   <img src="docs/Screenshots/Screenshot_1761490896.png" alt="Le clavier luxembourgeois en action" width="25%">
</div>

*Suggestions luxembourgeoises en cours de frappe, ici les premiers vers de « Ons Heemecht »*

<!-- Capture antérieure à la 10.9.2 : la rangée du bas n'y montre pas encore la
     touche « ä » ni la touche emoji. À refaire sur émulateur. -->

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

### 🏆 **Progression et jeux**
- Huit niveaux, d'**Ufänker** à **Sproochenmeeschter**, selon la part du dictionnaire employée
- Carte de niveau partageable
- Sept jeux de vocabulaire : **Wuertsich** (mots mêlés), **Wuertmix** (anagrammes), **Wuertriet** (six essais), **Wuertlück** (texte à trou), **Zuelwuert** (les nombres en toutes lettres), **Kräizwuert** (mots croisés) et **Wuertplaz** (mots à placer)

### 🔒 **Vie privée**
Le clavier fonctionne **entièrement hors ligne** : il n'a aucun accès à Internet.
Seuls les mots déjà présents dans le dictionnaire sont comptés pour la
progression, si bien que mots de passe et termes personnels ne sont jamais
enregistrés, et rien ne quitte l'appareil.

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

## 📦 Téléchargements

### 🚀 **Dernière version stable**

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/famibelle/LuxKeyb?style=for-the-badge&logo=github)](https://github.com/famibelle/LuxKeyb/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/famibelle/LuxKeyb/total?style=for-the-badge&logo=github)](https://github.com/famibelle/LuxKeyb/releases)

### 🧪 **Test fermé sur Google Play**

L'application est en test fermé sur le Play Store. Y participer, c'est la
recevoir comme n'importe quelle autre application : installation en un geste,
mises à jour automatiques, sans autoriser les « sources inconnues ».

1. Depuis votre téléphone, ouvrez la [page d'inscription au test](https://play.google.com/apps/testing/com.potomitan.luxkeyboard) avec le compte Google que vous utilisez sur le Play Store
2. Appuyez sur **Devenir testeur**, puis suivez le lien vers Google Play
3. Le test est réservé à une liste de comptes : si le vôtre n'y est pas encore, ouvrez une [issue](https://github.com/famibelle/LuxKeyb/issues) ou passez par le [formulaire de retours](https://famibelle.github.io/LuxKeyb/feedbacks_form.html)

<div align="center">
  <a href="https://play.google.com/apps/testing/com.potomitan.luxkeyboard"><img src="docs/assets/qr-luxkeyb-test-ferme.png" alt="QR code ouvrant la page d'inscription au test fermé sur Google Play" width="200"></a>
  <br><em>Scannez : la page d'inscription s'ouvre</em>
</div>

Google demande au moins douze testeurs pendant quatorze jours avant d'autoriser
une publication ouverte : chaque inscription rapproche le clavier du Play Store
public. Détails sur la [page d'accueil du site](https://famibelle.github.io/LuxKeyb/#devenir-testeur).

L'APK ci-dessous reste disponible et contient exactement le même code.

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
./gradlew testDebugUnitTest  # la suite de tests, plus de 260 tests
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
- **JUnit 4**, plus de 260 tests unitaires exécutés en CI
- **Python et Hugging Face** pour le pipeline de génération des données
- **GitHub Actions** pour l'intégration continue
