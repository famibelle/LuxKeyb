# 🇱🇺 Clavier Lëtzebuergesch : Clavier intelligent pour la saisie en **luxembourgeois** avec suggestions de mots.

**Clavier Lëtzebuergesch** est un clavier Android intelligent conçu pour répondre à un besoin fondamental : permettre aux Luxembourgeois d'écrire facilement en **luxembourgeois** sur leur smartphone, avec fluidité, authenticité et fierté.


- 🛠️ Si ton luxembourgeois est très rouillé...
- 😤 Que tu galères à écrire en lëtzebuergesch parce que ton téléphone refuse tous les mots
- 🤔 Que tu doutes de l'orthographe à chaque message ...
- ➡️ Clavier Lëtzebuergesch est fait pour toi !


🧠 Le dictionnaire est en **évolution permanente**, enrichi régulièrement grâce aux **suggestions de la communauté luxembourgeoise**. Chaque contribution aide à affiner les prédictions et à refléter les usages réels du luxembourgeois contemporain.

⚡ Grâce à ces suggestions basées sur les plus grands textes en luxembourgeois, les utilisateurs peuvent **écrire très rapidement dans un luxembourgeois fluide, riche et parfaitement maîtrisé**, sans effort ni approximation.

📱 Compatible Android 7.0+, il s'installe facilement et fonctionne avec toutes les applications de messagerie, réseaux sociaux ou saisie web.

---

**Clavier Lëtzebuergesch**, c'est plus qu'un outil :  
C'est un acte de transmission, un hommage à la langue, une technologie au service de l'identité luxembourgeoise.


![Langue](https://img.shields.io/badge/Langue-lëtzebuergesch-blue?style=for-the-badge&logo=android)
![Android](https://img.shields.io/badge/Android-7.0+-green?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)


## 📱 Aperçu

<div align="center">
   <img src="Screenshots/ClavierLux_x2.gif" alt="Clavier luxembourgeois en Action" width="25%">
</div>

*Clavier luxembourgeois avec suggestions basées sur les textes littéraires fondateurs du lëtzebuergesch*

## 🌟 Fonctionnalités

### 🎯 **Suggestions Intelligentes**
- Suggestions contextuelles en temps réel
- Prédiction de texte adaptée au luxembourgeois
- **N-grams linguistiques** construits à partir de textes authentiques luxembourgeois

#### 📚 **Corpus Littéraire des Suggestions**
Les suggestions de mots sont générées grâce à des **N-grams** (séquences de mots) extraits des œuvres d'éminents auteurs et contributeurs de la littérature luxembourgeoise :


Cette approche garantit des suggestions **authentiques** et **culturellement appropriées**, respectant les nuances et la richesse du **luxembourgeois** contemporain.


## 📦 Téléchargements

### 🚀 **Dernière Version Stable**

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/famibelle/LuxKeyb?style=for-the-badge&logo=github)](https://github.com/famibelle/LuxKeyb/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/famibelle/LuxKeyb/total?style=for-the-badge&logo=github)](https://github.com/famibelle/LuxKeyb/releases)

### 📱 **Installation Rapide**

1. **Téléchargez l'APK** depuis la [dernière release](https://github.com/famibelle/LuxKeyb/releases/latest)
2. **Autorisez les sources inconnues** dans les paramètres Android
3. **Installez l'APK** en touchant le fichier
4. **Activez le clavier** dans Paramètres → Système → Langues et saisie

### 📦 **Types d'APK Disponibles**

| Type | Description | Taille | Usage |
|------|-------------|--------|-------|
| **Release APK** | Optimisée production | ~2–3 MB | ✅ Recommandé |
| **Debug APK** | Avec logs verbeux | + ~1 MB | 🔧 Dev |

### 🔄 **Mises à Jour Automatiques**

Les nouvelles versions sont automatiquement construites et publiées grâce à **GitHub Actions** :
- ✅ **Build automatique** à chaque push sur `main`
- ✅ **APK signés** prêts pour l'installation
- ✅ **Releases automatiques** sur [GitHub Releases](https://github.com/famibelle/LuxKeyb/releases) à chaque tag `v*.*.*`

### 🔧 Compilation (sources)
```bash
git clone https://github.com/famibelle/LuxKeyb.git
cd LuxKeyb/android_keyboard
./gradlew assembleRelease
```

## ⚙️ Installation depuis les sources

### Prérequis
- **Android 7.0** (API 24) ou supérieur
- **10 MB** d'espace libre

### Installation depuis les sources

1. **Cloner le repository** :
```bash
git clone https://github.com/famibelle/LuxKeyb.git
cd LuxKeyb/android_keyboard
```

2. **Compiler l'APK** :
```bash
./gradlew assembleDebug
```

3. **Installer sur device** :
```bash
./gradlew installDebug
```

4. **Activer le clavier** :
   - Aller dans **Paramètres** → **Système** → **Langues et saisie**
   - Sélectionner **Claviers virtuels**
   - Activer **Clavier Luxembourgeois**
   - Définir comme clavier par défaut

## 🚀 Utilisation

### Activation
1. Ouvrir n'importe quelle application de saisie
2. Appuyer longuement sur l'icône clavier (barre de navigation)
3. Sélectionner **Clavier Luxembourgeois**

### Accents
Appui long sur une lettre: affiche un popup (ex: a → à á â ä ã …). Relâcher après sélection.

### Suggestions de Mots
- Commencer à taper un mot luxembourgeois
- Les suggestions apparaissent automatiquement
- Toucher une suggestion pour l'insérer

## 🏗️ Architecture



### Technologies Utilisées
- **Kotlin** - Langage principal
- **Android InputMethodService** - Framework IME
- **JSON** - Format du dictionnaire et N-grams
- **Gradle** - Build system
- **Material Design** - Guidelines UI/UX
- **GitHub Actions** - CI/CD automatisé
