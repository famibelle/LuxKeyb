# Guide d'Hébergement - Politique de Confidentialité

## 🎯 Objectif
Google Play Store **EXIGE** une URL publique vers la politique de confidentialité pour publier l'application.

## 📋 Options d'Hébergement

### Option 1: GitHub Pages (GRATUIT - RECOMMANDÉ)
1. **Créer un repository public** sur GitHub
2. **Activer GitHub Pages** dans Settings
3. **Upload le fichier** `privacy-policy.html`
4. **URL finale** : `https://famibelle.github.io/kreyol-keyboard-privacy/privacy-policy.html`

### Option 2: Google Sites (GRATUIT)
1. Aller sur **sites.google.com**
2. Créer un nouveau site
3. Copier-coller le contenu de `PRIVACY_POLICY.md`
4. Publier le site

### Option 3: Netlify Drop (GRATUIT)
1. Aller sur **drop.netlify.com**
2. Glisser-déposer le fichier `privacy-policy.html`
3. Récupérer l'URL générée

### Option 4: Site Web Personnel
Si vous avez un site web, créer une page `/privacy-policy/` 

## 🚀 Instructions GitHub Pages (Recommandé)

### Étape 1: Créer le Repository
```bash
# 1. Aller sur github.com
# 2. Cliquer "New repository"
# 3. Nom: kreyol-keyboard-privacy
# 4. Public ✓
# 5. Create repository
```

### Étape 2: Upload les Fichiers
```bash
# Upload ces fichiers:
- privacy-policy.html (version web)
- PRIVACY_POLICY.md (version markdown)
- PRIVACY_POLICY_EN.md (version anglaise)
```

### Étape 3: Activer GitHub Pages
```bash
# 1. Aller dans Settings du repository
# 2. Défiler jusqu'à "Pages"
# 3. Source: "Deploy from a branch"
# 4. Branch: main
# 5. Folder: / (root)
# 6. Save
```

### Étape 4: URL Finale
```
https://famibelle.github.io/kreyol-keyboard-privacy/privacy-policy.html
```

**OU MIEUX** : Héberger sur le site officiel Potomitan
```
https://potomitan.io/privacy-policy/kreyol-keyboard
```

## ✅ Validation

### Checklist avant soumission Google Play:
- [ ] URL publique accessible
- [ ] Page se charge correctement
- [ ] Contenu en français ET anglais
- [ ] Contact email visible
- [ ] Date de mise à jour présente
- [ ] Conformité RGPD mentionnée

### Test de l'URL:
1. Ouvrir l'URL dans un navigateur
2. Vérifier que la page s'affiche
3. Tester sur mobile
4. Copier l'URL finale pour Google Play Console

## 📝 Configuration Google Play Console

### Dans "App content" > "Privacy Policy":
```
URL: https://potomitan.io/privacy-policy/kreyol-keyboard
```
**OU**
```
URL: https://famibelle.github.io/kreyol-keyboard-privacy/privacy-policy.html
```

### Déclarations obligatoires:
- [ ] "This app does NOT collect user data"
- [ ] "No personal information collected"
- [ ] "No data shared with third parties"

## 🔄 Mises à Jour

### Pour modifier la politique:
1. Modifier les fichiers locaux
2. Push sur GitHub
3. GitHub Pages se met à jour automatiquement
4. Pas besoin de refaire la soumission Google Play

## 📧 Support

Si vous avez besoin d'aide pour l'hébergement:
- Email: medhi@potomitan.io
- Objet: "Aide Hébergement Politique Confidentialité"

## 🎉 Résultat Final

Une fois hébergée, vous aurez:
- ✅ URL publique obligatoire pour Google Play
- ✅ Politique conforme RGPD
- ✅ Version française et anglaise
- ✅ Hosting gratuit et fiable
- ✅ Mises à jour faciles