# 📊 Statistiques Vocabulaire - Gamification

## Fonctionnalité

Un système de gamification qui permet aux utilisateurs de suivre leur progression dans l'apprentissage du vocabulaire créole.

## Accès

### Depuis l'application principale

1. Ouvrez l'application **Klavyé Kréyòl Karukera**
2. Dans l'écran principal, cliquez sur le bouton orange **📊 Statistiques Vocabulaire**
3. L'écran des statistiques s'affiche avec toutes les informations

### Via ADB (développement)

```bash
adb shell am start -n com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.gamification.VocabularyStatsActivity
```

## Affichage

L'écran des statistiques affiche :

### 🏆 Niveau de maîtrise
- **⭐ DÉBUTANT ⭐** : 0-100 mots
- **🌟 EXPLORATEUR 🌟** : 101-300 mots
- **💫 INTERMÉDIAIRE 💫** : 301-600 mots
- **✨ AVANCÉ ✨** : 601-1000 mots
- **🔥 EXPERT 🔥** : 1001-1500 mots
- **👑 MAÎTRE 👑** : 1501-2000 mots
- **🎯 LÉGENDE 🎯** : 2000+ mots

### 📈 Progression
- Barre de progression visuelle
- Pourcentage de mots découverts
- Nombre de mots utilisés / 100 pour le niveau suivant

### 🏅 Top 5 des mots
Liste des 5 mots les plus utilisés avec :
- 🥇 Médaille d'or pour le 1er
- 🥈 Médaille d'argent pour le 2e
- 🥉 Médaille de bronze pour le 3e
- Indicateurs visuels (●) pour le nombre d'utilisations

### 📊 Statistiques globales
- **Couverture du dictionnaire** : Pourcentage des 2833 mots explorés
- **Mots découverts** : Nombre de mots différents utilisés
- **Total d'utilisations** : Nombre total de fois où vous avez tapé des mots du dictionnaire
- **Mots maîtrisés** : Mots utilisés 10 fois ou plus

### 💬 Message de progression
Message d'encouragement personnalisé selon votre niveau

## Respect de la vie privée

✅ **100% privé et local**
- Seuls les mots du dictionnaire créole sont comptabilisés
- Les mots personnels, mots de passe, etc. sont automatiquement ignorés
- Aucune synchronisation cloud
- Toutes les données restent sur votre appareil
- Filtres automatiques :
  - Mots < 3 lettres ignorés
  - Mots avec chiffres ignorés
  - URLs et emails ignorés

## Fichiers concernés

### Backend
- `CreoleDictionaryWithUsage.kt` : Gestion du dictionnaire avec compteurs
- `WordUsageStats.kt` : Modèle de données pour un mot
- `VocabularyStats.kt` : Modèle de données pour les statistiques globales

### Frontend
- `VocabularyStatsActivity.kt` : Activity d'affichage des statistiques
- `activity_vocabulary_stats.xml` : Layout de l'écran (design compact)

### Intégration
- `SettingsActivity.kt` : Bouton d'accès aux statistiques
- `AndroidManifest.xml` : Déclaration de l'Activity

### Stockage
- Fichier : `/data/data/com.potomitan.kreyolkeyboard/files/creole_dict_with_usage.json`
- Format : `{"mot": {"frequency": X, "user_count": Y}}`
- Sauvegarde : Batch de 10 mots + onDestroy()

## Commits

- `118a8ab` : Initial gamification backend
- `1e1a461` : Fix dictionary migration 
- `f22bee3` : Fix ENTER key behavior
- `[CURRENT]` : Add statistics dashboard UI

## Tests

### Manuel
1. Installer l'APK
2. Utiliser le clavier pour taper des mots créoles
3. Ouvrir les statistiques depuis l'app
4. Vérifier que les compteurs augmentent
5. Tester le bouton Rafraîchir
6. Tester le bouton Fermer

### Via ADB
```bash
# Vérifier le fichier de données
adb shell "run-as com.potomitan.kreyolkeyboard cat files/creole_dict_with_usage.json" | head -50

# Lancer l'Activity
adb shell am start -n com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.gamification.VocabularyStatsActivity

# Vérifier les logs
adb logcat -d | grep "VocabStats"
```

## Design

**Thème sombre moderne**
- Fond : #1E1E1E (noir charbonneux)
- Texte : Blanc sur fond sombre
- Couleur accent : Orange soleil (#FF8C00) pour le bouton
- Émojis : Pour une touche ludique et visuelle

**Disposition compacte**
- Niveau en haut
- Barre de progression
- Top 5 des mots avec médailles
- Statistiques condensées
- Message de progression
- Boutons d'action en bas

## Évolutions futures possibles

- [ ] Graphique d'évolution dans le temps
- [ ] Badges de réussite
- [ ] Partage des statistiques (capture d'écran)
- [ ] Filtres par catégorie de mots
- [ ] Historique de progression
- [ ] Mode compétition avec amis
- [ ] Objectifs quotidiens/hebdomadaires
