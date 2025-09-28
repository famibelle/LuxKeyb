# 📦 **GUIDE AAB - ANDROID APP BUNDLE**

## 🎯 **Qu'est-ce qu'un AAB ?**

**Android App Bundle (AAB)** est le **format recommandé par Google** pour publier des applications sur le **Google Play Store**. 

### **🔄 APK vs AAB** :

| Format | **APK** | **AAB** |
|--------|---------|---------|
| **Usage** | Installation directe | Google Play Store uniquement |
| **Taille** | Plus lourd (contient tout) | Plus léger (optimisé par Google) |
| **Distribution** | Manuelle | Automatique via Play Store |
| **Optimisation** | Aucune | Optimisée par device |

## 🚀 **AAB du Kreyòl Keyboard**

### **📱 Fichiers générés automatiquement** :
- 🔧 **Debug APK** : `Potomitan_Kreyol_Keyboard_v4.0.4_debug_YYYY-MM-DD.apk`
- 📱 **Release APK** : `Potomitan_Kreyol_Keyboard_v4.0.4_release_YYYY-MM-DD.apk`  
- 📦 **Release AAB** : `app-release.aab` (Google Play Store)

### **🎯 Utilisation recommandée** :

| Cas d'usage | Format recommandé |
|-------------|-------------------|
| 🏪 **Publication Play Store** | **AAB** (obligatoire) |
| 📱 **Installation directe** | APK Release |
| 🔧 **Tests/Debug** | APK Debug |
| 📦 **Distribution interne** | APK Release |

## 🏪 **PUBLIER SUR GOOGLE PLAY STORE**

### **Étape 1 : Récupérer l'AAB**
1. Aller sur : **GitHub Releases** 
2. Télécharger : `app-release.aab` (dernière version)

### **Étape 2 : Play Console**
1. **Google Play Console** : https://play.google.com/console/
2. **Create Application** → "Kreyòl Keyboard"
3. **Release** → **Production** 
4. **Upload** → Sélectionner `app-release.aab`

### **Étape 3 : Configuration Play Store**
```
Application Details:
  Title: Kreyòl Keyboard - Potomitan™
  Short Description: Clavier créole guadeloupéen avec dictionnaire authentique
  Category: Tools
  Content Rating: Everyone
  
Store Listing:
  Description: [Voir ci-dessous]
  Screenshots: [Prendre screenshots de l'app]
  Icon: assets/logoPotomitan.png
```

### **📝 Description Play Store recommandée** :
```
🇸🇷 KLAVIÉ KREYÒL KARUKERA - POTOMITAN™

Le seul clavier créole authentique pour la Guadeloupe !

✨ FONCTIONNALITÉS :
• 📚 Dictionnaire de 1800+ mots créoles authentiques
• 🎯 Suggestions intelligentes basées sur la littérature créole
• 🔤 Accents créoles (à, è, ò) via appui long
• 🎨 Design moderne aux couleurs caribéennes
• 🚫 Zéro tracking - Respecte votre vie privée

📖 SOURCES LITTÉRAIRES :
• Textes de Sonny Rupaire, Max Rippon, Ernest Pépin
• Corpus POTOMITAN authentique et validé
• Fréquences basées sur la littérature créole réelle

🔒 CONFIDENTIALITÉ :
• Code source ouvert sur GitHub
• Aucune collecte de données personnelles
• Fonctionne entièrement hors ligne

🇸🇷 Alé douvan épi klavié kreyòl-la !
```

## 🔧 **AVANTAGES TECHNIQUES AAB**

### **📊 Optimisations automatiques** :
- **APK Splits** : Android génère des APK optimisés par device
- **Architecture ciblée** : ARM64, ARM32 selon le téléphone
- **Ressources optimisées** : Densités d'écran adaptées
- **Taille réduite** : ~30% plus petit que l'APK équivalent

### **🎯 Distribution intelligente** :
```
Exemple pour Samsung Galaxy S24:
- Télécharge uniquement: ARM64 + XXHDPI resources
- Ignore: ARM32 + autres densités
- Résultat: 3MB au lieu de 5MB
```

## 🛠️ **TESTS LOCAUX AAB**

### **Installer l'AAB localement** :
```powershell
# 1. Télécharger bundletool
# Déjà présent: android_keyboard/bundletool.jar

# 2. Convertir AAB en APKs
java -jar bundletool.jar build-apks --bundle=app-release.aab --output=my-app.apks

# 3. Installer sur device connecté
java -jar bundletool.jar install-apks --apks=my-app.apks
```

### **Analyser l'AAB** :
```powershell
# Taille et contenu
java -jar bundletool.jar get-size total --apks=my-app.apks

# APKs générés par device
java -jar bundletool.jar extract-apks --apks=my-app.apks --output-dir=extracted/
```

## 📋 **CHECKLIST PUBLICATION**

### **Avant publication** :
- [ ] ✅ **AAB généré** via GitHub Actions
- [ ] 🧪 **Testé localement** avec bundletool
- [ ] 📱 **Screenshots** pris sur vraix devices
- [ ] 📝 **Description** Play Store rédigée
- [ ] 🔒 **Politique confidentialité** créée
- [ ] 🎨 **Icône** haute résolution (512x512px)

### **Publication** :
- [ ] 📦 **AAB uploadé** sur Play Console
- [ ] ⚙️ **Paramètres app** configurés
- [ ] 🎯 **Audience ciblée** définie
- [ ] 💰 **Prix** défini (gratuit)
- [ ] 🚀 **Release** publié

---

## 🎉 **RÉSULTAT**

Avec l'AAB, ton clavier créole sera :
- 🏪 **Disponible sur Play Store** officiellement
- 📱 **Optimisé automatiquement** pour chaque device
- 🔄 **Mis à jour facilement** via releases automatiques
- 🇸🇷 **Accessible à tous** les Guadeloupéens !

**L'AAB est automatiquement généré à chaque release GitHub !** 🚀