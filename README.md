# 🇬🇵 Klavié Kreyòl Karukera (Android) 🇬🇵

Clavier système Android natif (IME) optimisé pour l'écriture en **Kreyòl Guadeloupéen** :

![Langue](https://img.shields.io/badge/Langue-Kreyòl%20Guadeloupéen-blue?style=for-the-badge&logo=android)
![Android](https://img.shields.io/badge/Android-7.0+-green?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

Ce dépôt a été simplifié: seuls les fichiers du module `android_keyboard/` sont conservés (suppression ancienne app Flutter & plugin). L'historique complet reste disponible via la branche `legacy-full` et le tag `before-slim`.

## 📱 Aperçu

<img src="Screenshots/Screenshot_1757202571.png" alt="Clavier Kreyòl Karukera en Action" style="width: 25%; height: auto;">

<img src="Screenshots/Screenshot_1757242027.png" alt="Clavier Kreyòl Karukera en home" style="width: 25%; height: auto;">

*Clavier créole avec suggestions intelligentes et design Guadeloupéen*

## 🌟 Fonctionnalités principales

### 🎯 Prédiction & Suggestions
- **~1 800+ mots** dans le dictionnaire embarqué
- Suggestions par préfixe + modèle **N-grams** (bigrammes) pour le contexte
- Ajout automatique des mots créoles tapés au dictionnaire utilisateur (limite le soulignement rouge)
- Accentuation rapide (popup accents via appui long)

#### 📚 Source linguistique
Les N-grams proviennent d'un corpus textuel créole consolidé (œuvres & corpus Potomitan). Le modèle est généré via le script `GenererNgrams.py`.


### 🎨 Design
- Palette inspirée (Bleu Caraïbe / Jaune Soleil / Vert Canne / Rouge Hibiscus)
- Touches arrondies, animations 100–120 ms, haptique légère
- Watermark discret Potomitan™

## 📦 Téléchargements

### 🚀 **Dernière Version Stable**

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/famibelle/KreyolKeyb?style=for-the-badge&logo=github)](https://github.com/famibelle/KreyolKeyb/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/famibelle/KreyolKeyb/total?style=for-the-badge&logo=github)](https://github.com/famibelle/KreyolKeyb/releases)

### 📱 **Installation Rapide**

1. **Téléchargez l'APK** depuis la [dernière release](https://github.com/famibelle/KreyolKeyb/releases/latest)
2. **Autorisez les sources inconnues** dans les paramètres Android
3. **Installez l'APK** en touchant le fichier
4. **Activez le clavier** dans Paramètres → Système → Langues et saisie

### 📦 **Types d'APK Disponibles**

| Type | Description | Taille | Usage |
|------|-------------|--------|-------|
| **Release APK** | Optimisée production | ~2–3 MB | ✅ Recommandé |
| **Debug APK** | Avec logs verbeux | + ~1 MB | 🔧 Dev |

### 🔄 **Mises à Jour Automatiques**

Les nouvelles versions sont automatiquement publiées sur [GitHub Releases](https://github.com/famibelle/KreyolKeyb/releases) à chaque tag `v*.*.*`.

### 🔧 Compilation (sources)
```bash
git clone https://github.com/famibelle/KreyolKeyb.git
cd KreyolKeyb/android_keyboard
./gradlew assembleRelease
```

## �️ Installation depuis les sources

### Prérequis
- **Android 7.0** (API 24) ou supérieur
- **10 MB** d'espace libre

### Installation depuis les sources

1. **Cloner le repository** :
```bash
git clone https://github.com/famibelle/KreyolKeyb.git
cd KreyolKeyb/android_keyboard
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
   - Activer **Clavier Créole Guadeloupéen**
   - Définir comme clavier par défaut

## 🚀 Utilisation

### Activation
1. Ouvrir n'importe quelle application de saisie
2. Appuyer longuement sur l'icône clavier (barre de navigation)
3. Sélectionner **Clavier Créole Guadeloupéen**

### Accents
Appui long sur une lettre: affiche un popup (ex: a → à á â ä ã …). Relâcher après sélection.

### Suggestions de Mots
- Commencer à taper un mot créole
- Les suggestions apparaissent automatiquement
- Toucher une suggestion pour l'insérer

## 🏗️ Architecture

### Structure du Projet
```
android_keyboard/
├── app/src/main/
│   ├── java/com/example/kreyolkeyboard/
│   │   ├── KreyolInputMethodService.kt   ← IME principal (gestion touches / suggestions / accents)
│   │   ├── SettingsActivity.kt           ← UI d’activation & onboarding
│   │   └── KreyolSpellCheckerService.kt  ← (placeholder – à implémenter ou supprimer)
│   ├── assets/
│   │   ├── creole_dict.json              ← Liste [mot, fréquence]
│   │   └── creole_ngrams.json            ← Modèle N-grams (predictions)
│   └── res/…                             ← Ressources UI (couleurs, dimens, drawables, styles)
└── gradle/                               ← Wrapper Gradle
```

### Technologies Utilisées
- **Kotlin** - Langage principal
- **Android InputMethodService** - Framework IME
- **JSON** - Format du dictionnaire
- **Gradle** - Build system
- **Material Design** - Guidelines UI/UX

## 📚 Dictionnaire & Prédiction

### Sources du Dictionnaire
Généré via:
1. Extraction fréquence (script `Dictionnaire.py`) – top N mots.
2. Génération N-grams (`GenererNgrams.py`) → `creole_ngrams.json` (structure: `{ predictions: { mot: [{word, prob}, …] } }`).

### Mots les Plus Fréquents
```
an (424), ka (324), la (219), on (208), té (188)
pou (154), nou (133), i (102), sé (100), yo (94)
```

### Mise à jour dictionnaire
```bash
python Dictionnaire.py            # (Hugging Face, nécessite connexion internet)
python GenererNgrams.py           # produit assets N-grams
```

## 🎨 Design & UX

### Palette de Couleurs
- **Bleu Caribbean** : `#1E88E5` (touches principales)
- **Jaune Soleil** : `#FFC107` (accents)
- **Rouge Hibiscus** : `#E53935` (actions)
- **Vert Tropical** : `#43A047` (confirmations)

### Logo Potomitan™
- Logo officiel intégré dans l'interface
- Représentation de l'héritage culturel guadeloupéen
- Design moderne et respectueux

## 🧪 Validation

### Tests Effectués
- ✅ Saisie de texte en créole
- ✅ Suggestions de mots fonctionnelles
- ✅ Accents et caractères spéciaux
- ✅ Basculement modes alphabétique/numérique
- ✅ Compatibilité applications courantes
- ✅ Performance et fluidité

Tests informels sur: SMS, messageries, réseaux sociaux, champs web, saisie mail.

## 🤝 Contribution

### Comment Contribuer
1. **Fork** le projet
2. Créer une **branch feature** (`git checkout -b feature/AmeliorationClavier`)
3. **Commit** les changements (`git commit -m 'Ajout nouvelle fonctionnalité'`)
4. **Push** vers la branch (`git push origin feature/AmeliorationClavier`)
5. Ouvrir une **Pull Request**

### Développement Local
```bash
# Cloner le repo
git clone https://github.com/famibelle/KreyolKeyb.git

# Setup environnement
cd KreyolKeyb/android_keyboard
./gradlew build

# Tests
./gradlew test
```

## 📖 Détails techniques clés

### API IME Android
- `InputMethodService` - Service principal
- `InputConnection` - Interface application
- `KeyboardView` - Affichage clavier personnalisé

### Suggestion
- Combinaison: préfixe dictionnaire + N-grams contextuels + fallback mots fréquents.
- Nettoyage historique limité (FIFO 5 derniers mots).
- Ajout dynamique au UserDictionary (si disponible) pour réduire soulignement rouge.

## 🌍 Langue & Culture

### Kreyòl Guadeloupéen
Le **Kreyòl Guadeloupéen** est une langue créole parlée en Guadeloupe, qui prend sa source dans une culture africaines, caribéennes et amérindiennes.

### Respect Culturel �
Ce projet est développé dans le respect de :
- La richesse linguistique caribéenne
- L'héritage culturel guadeloupéen
- Les normes d'écriture créole établies
- La communauté créolophone

## 📄 License

Distribué sous licence **MIT**. Voir `LICENSE` pour plus d'informations.

## 👥 Auteurs

### Développement
- **Medhi** - Développeur principal
- **Potomitan™** - [potomitan.io](https://potomitan.io/)

### Remerciements
- Communauté créolophone guadeloupéenne
- Contributeurs du dataset Potomitan
- Auteurs des textes littéraires créoles
- Beta-testeurs et utilisateurs

## 📞 Support

### Contact
- **Email** : support@potomitan.io
- **GitHub Issues** : [Ouvrir un ticket](https://github.com/famibelle/KreyolKeyb/issues)

### FAQ
**Q: Comment changer la langue du clavier ?**
R: Aller dans Paramètres → Langues et saisie → Claviers virtuels

**Q: Les suggestions ne fonctionnent pas ?**
R: Vérifier que le clavier est bien activé et défini par défaut

**Q: Comment régénérer les données linguistiques ?**
R: Exécuter `Dictionnaire.py` puis `GenererNgrams.py`.

---

<div align="center">

**🇬🇵 Fierté Guadeloupéenne – Technologie au service de la langue 🇬🇵**

*Développé avec ❤️ pour la communauté créolophone*

</div>
