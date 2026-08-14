# 🏷️ Branding Potomitan™ - Clavier Kreyòl Karukera

## Vue d'ensemble

Le branding **Potomitan™** est intégré de façon subtile et professionnelle dans tous les aspects du clavier créole pour renforcer l'identité culturelle guadeloupéenne.

## 🎯 Points de Branding dans l'Interface

### 1. Titre Principal du Clavier
```
📱 "Klavyé Kréyòl Karukera  • Potomitan™"
```
- Affiché dans la barre de titre du clavier
- Style: Fond bleu caraïbe avec texte blanc corail
- Police: Gras pour la visibilité

### 2. Barre de Suggestions Intelligente
```
💭 Indicateur "Potomitan™" discret
```
- Apparaît quand aucune suggestion n'est disponible
- Style: Texte italique transparent (60% opacité)
- Couleur: Bleu caraïbe avec une taille réduite (10sp)

### 3. Watermark Permanent
```
🏞️ "Potomitan™" en bas à droite
```
- Watermark semi-transparent (40% opacité)
- Position: Coin inférieur droit du clavier
- Style: Police italique, couleur blanc corail
- Taille: 8sp pour rester discret

### 4. Métadonnées de l'Application
```
📋 Information système
```
- Nom de l'app: "Klavyé Kréyòl Karukera • Potomitan™"
- Description: "Clavier créole pour la Guadeloupe avec suggestions de mots et accents • Potomitan™"
- Version: "1.0-Potomitan™"

## 📱 Emplacements de Branding

### Interface Utilisateur
| Composant | Texte | Style |
|-----------|--------|-------|
| Titre du clavier | "Klavyé Kréyòl Karukera  • Potomitan™" | Gras, bleu caraïbe |
| Suggestions vides | "Potomitan™" | Italique, transparent |
| Watermark | "Potomitan™" | Italique, coin droit |

### Métadonnées Android
| Fichier | Valeur | Utilisation |
|---------|---------|-------------|
| `strings.xml` | Toutes les chaînes incluent "• Potomitan™" | Système Android |
| `AndroidManifest.xml` | Libellé de l'activité | Paramètres système |
| `build.gradle` | Version "1.0-Potomitan™" | Informations de build |

### Code Source
| Fichier | Élément | Description |
|---------|---------|-------------|
| `KreyolInputMethodServiceRefactored.kt` | TAG = "KreyolIME-Potomitan™" | Logs de débogage |
| `creole_ngrams.json` | "branding": "Potomitan™" | Modèle N-grams |
| `GenererNgrams.py` | Header et métadonnées | Script de génération |
| `EnrichirDictionnaire.py` | Header et logs | Script d'enrichissement |

## 🎨 Palette de Couleurs Potomitan™

### Couleurs Officielles
```scss
$potomitan-bleu: #0080FF;      // Bleu caraïbe principal
$potomitan-coral: #FFE4E1;     // Blanc corail pour contraste
$potomitan-transparent: 0.4;   // Niveau de transparence watermark
$potomitan-discret: 0.6;       // Niveau de transparence suggestions
```

### Utilisations
- **Bleu caraïbe (#0080FF)**: Éléments de branding visibles
- **Blanc corail (#FFE4E1)**: Contraste sur fond sombre
- **Transparence 40%**: Watermark non-intrusif
- **Transparence 60%**: Suggestions discrètes

## 📝 Directives d'Usage

### ✅ Bonnes Pratiques
- Maintenir la subtilité du branding
- Préserver la lisibilité du texte principal
- Respecter les couleurs de la palette Guadeloupe
- Garder les éléments de branding non-intrusifs

### ❌ À Éviter
- Surcharger l'interface avec trop de mentions
- Utiliser des couleurs non-conformes à la palette
- Rendre le branding plus visible que le contenu
- Modifier les proportions des éléments de branding

## 🔄 Mise à Jour du Branding

Pour modifier ou étendre le branding Potomitan™ :

1. **Interface**: Modifier `KeyboardLayoutManager.kt`
2. **Métadonnées**: Mettre à jour `strings.xml`
3. **Version**: Changer `build.gradle`
4. **Documentation**: Mettre à jour ce fichier

## 📈 Impact du Branding

### Visibilité
- **Constante**: Watermark toujours présent
- **Contextuelle**: Suggestions quand approprié  
- **Informationnelle**: Métadonnées système

### Reconnaissance
- Identité culturelle guadeloupéenne renforcée
- Association directe avec le patrimoine Potomitan™
- Différenciation des autres claviers créoles

---

*Documentation mise à jour le 7 septembre 2025*  
*Clavier Kreyòl Karukera • Potomitan™*
