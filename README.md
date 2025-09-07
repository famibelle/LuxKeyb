# 🇬🇵 Clavier Créole Guadeloupéen 🇬🇵

Clavier Android intelligent pour la saisie en **Kreyòl Guadeloupéen** avec suggestions de mots.

![Clavier Créole](https://img.shields.io/badge/Langue-Kreyòl%20Guadeloupéen-blue?style=for-the-badge&logo=android)
![Version Android](https://img.shields.io/badge/Android-7.0+-green?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

## 📱 Aperçu de l'Application

<img src="Screenshots/Screenshot_1757202571.png" alt="Clavier Kreyòl Karukera en Action" style="width: 25%; height: auto;">

<img src="Screenshots/Screenshot_1757242027.png" alt="Clavier Kreyòl Karukera en home" style="width: 25%; height: auto;">

*Interface du clavier créole avec suggestions intelligentes et design Guadeloupéen*

## 🌟 Fonctionnalités

### 🎯 **Suggestions Intelligentes**
- **1 867 mots** créoles dans le dictionnaire
- Suggestions contextuelles en temps réel
- Prédiction de texte adaptée au kreyòl
- **N-grams linguistiques** construits à partir de textes authentiques créoles

#### 📚 **Corpus Littéraire des Suggestions**
Les suggestions de mots sont générées grâce à des **N-grams** (séquences de mots) extraits des œuvres d'éminents auteurs et contributeurs de la littérature créole guadeloupéenne :

**Auteurs et Contributeurs** :
- **Robert Fontes** - Linguiste et lexicographe créole
- **Silvyane Telchid** - Romancière et dramaturge
- **Sonny Rupaire** - Poète et militant culturel
- **Max Rippon** - Écrivain et chroniqueur
- **Alain Rutil** - Auteur et chercheur créolophone
- **Germain William** - Conteur et écrivain traditionnel
- **Alain Verin** - Linguiste spécialiste du créole
- **Katel** - Artiste et poète contemporain
- **Esnard Boisdur** - Écrivain et journaliste
- **Pierre Edouard Decimus** - Auteur et intellectuel
- **Jomimi** - Conteur et Joueur de Ka 🪘



Cette approche garantit des suggestions **authentiques** et **culturellement appropriées**, respectant les nuances et la richesse du **Kreyòl Guadeloupéen** contemporain.


### 🎨 **Design Guadeloupéen**
- **Palette de couleurs** inspirée du drapeau guadeloupéen
- Logo **Potomitan™** intégré
- Interface moderne et élégante
- Thème sombre/clair adaptatif

## 📱 Installation

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

### Saisie des Accents
- **Appui court** : lettre normale (`a`, `e`, `o`, etc.)
- **Appui long** : menu des accents (`à`, `è`, `ò`, etc.)
- Sélectionner l'accent désiré

### Suggestions de Mots
- Commencer à taper un mot créole
- Les suggestions apparaissent automatiquement
- Toucher une suggestion pour l'insérer

## 🏗️ Architecture Technique

### Structure du Projet
```
android_keyboard/
├── app/src/main/
│   ├── java/com/potomitan/kreyolkeyboard/
│   │   ├── KreyolInputMethodService.kt  # Service principal IME
│   │   └── SettingsActivity.kt          # Activité de configuration
│   ├── res/
│   │   ├── layout/                      # Layouts XML
│   │   ├── values/                      # Strings, colors, dimens
│   │   └── drawable/                    # Assets graphiques
│   └── assets/
│       └── creole_dict.json            # Dictionnaire créole (1867 mots)
```

### Technologies Utilisées
- **Kotlin** - Langage principal
- **Android InputMethodService** - Framework IME
- **JSON** - Format du dictionnaire
- **Gradle** - Build system
- **Material Design** - Guidelines UI/UX

## 📚 Dictionnaire

### Sources du Dictionnaire
Le dictionnaire contient **1 867 mots créoles** extraits de :

1. **Dataset Potomitan** (Hugging Face)
   - Corpus de traductions français-créole
   - Validation linguistique professionnelle

2. **Textes Littéraires Créoles** 🐚
   - Œuvres de Gisèle Pineau
   - Poésie de Sonny Rupaire
   - Chansons traditionnelles guadeloupéennes �
   - Littérature créole contemporaine

### Mots les Plus Fréquents
```
an (424), ka (324), la (219), on (208), té (188)
pou (154), nou (133), i (102), sé (100), yo (94)
```

### Enrichissement du Dictionnaire
Un script Python permet d'enrichir le dictionnaire :
```bash
python EnrichirDictionnaire.py
```

## 🎨 Design et Branding

### Palette de Couleurs
- **Bleu Caribbean** : `#1E88E5` (touches principales)
- **Jaune Soleil** : `#FFC107` (accents)
- **Rouge Hibiscus** : `#E53935` (actions)
- **Vert Tropical** : `#43A047` (confirmations)

### Logo Potomitan™
- Logo officiel intégré dans l'interface
- Représentation de l'héritage culturel guadeloupéen
- Design moderne et respectueux

## 🧪 Tests et Validation

### Tests Effectués
- ✅ Saisie de texte en créole
- ✅ Suggestions de mots fonctionnelles
- ✅ Accents et caractères spéciaux
- ✅ Basculement modes alphabétique/numérique
- ✅ Compatibilité applications courantes
- ✅ Performance et fluidité

### Applications Testées
- WhatsApp, Telegram, SMS
- Gmail, Outlook
- Facebook, Twitter
- Notes, Documents

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

## 📖 Documentation Technique

### API IME Android
- `InputMethodService` - Service principal
- `InputConnection` - Interface application
- `KeyboardView` - Affichage clavier personnalisé

### Gestion du Dictionnaire
- Format JSON optimisé
- Chargement asynchrone en mémoire
- Algorithme de suggestion par préfixe
- Cache intelligent pour performance

## 🌍 Langue et Culture 🐚

### Kreyòl Guadeloupéen
Le **Kreyòl Guadeloupéen** est une langue créole parlée en Guadeloupe, développée à partir du français avec des influences africaines, caribéennes et amérindiennes.

### Respect Culturel �
Ce projet est développé dans le respect de :
- La richesse linguistique caribéenne
- L'héritage culturel guadeloupéen
- Les normes d'écriture créole établies
- La communauté créolophone

## 📄 License

Distribué sous licence **MIT**. Voir `LICENSE` pour plus d'informations.

## 👥 Équipe

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

**Q: Comment ajouter des mots au dictionnaire ?**
R: Utiliser le script `EnrichirDictionnaire.py` pour enrichir le corpus

---

<div align="center">

**🇬🇵 Fierté Guadeloupéenne - Technologie Moderne 🇬🇵**

*Développé avec ❤️ pour la communauté créolophone* 🐚�

</div>
