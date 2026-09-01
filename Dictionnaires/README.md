# �� LUXEMBURGISH KEYBOARD™ - PIPELINE UNIQUE

## Vue d'ensemble

Ce répertoire contient le **pipeline unique automatique** pour le système de clavier luxembourgeois intelligent. Plus besoin de menus ou d'interactions - tout s'exécute automatiquement !

## 🚀 Utilisation Ultra-Simple

### Pipeline Créole (Original)
```bash
python KreyolComplet.py
```

### Pipeline Luxembourgeois (Nouveau)
```bash
python LuxembourgishComplet.py
python generate_cloze.py        # jeu « Wuertlück », APRÈS le pipeline
```

`generate_cloze.py` ne reconstruit rien : il lit le dictionnaire et les
n-grammes que le pipeline vient d'écrire pour en tirer 1 600 phrases à trous
(`luxemburgish_cloze.json`). Le lancer avant produirait un jeu calé sur
l'ancien modèle. Les deux acceptent `--strict`, que la CI utilise.

C'est tout ! Le pipeline fait **TOUT** automatiquement :

- ✅ Récupération des données Hugging Face (transcriptions gouvernementales)
- ✅ Extraction depuis la colonne "transcription"
- ✅ Création/enrichissement du dictionnaire luxembourgeois
- ✅ Génération des N-grams adaptés au luxembourgeois
- ✅ Support des caractères spéciaux (ä, ë, é, ö, ü)
- ✅ Analyses statistiques complètes
- ✅ Sauvegarde sécurisée avec backups
- ✅ Validation intégrale

## 📊 Résultats par Langue

### 🇸🇷 Dictionnaire Créole
- **1,846 mots** total
- **358 occurrences** pour "ka" (mot le plus fréquent)
- **156 mots longs** (≥10 caractères)
- **"sèvis-ladministrasyon"** (21 caractères, mot le plus long)

### 🇱🇺 Dictionnaire Luxembourgeois
- Génération automatique depuis **fredxlpy/LuxAlign** (v3) et **fredxlpy/LETZ**
- Support natif des caractères luxembourgeois
- Mots courants : "an", "de", "der", "vun", "ass", "op"
- Attribution obligatoire — voir [CORPUS.md](CORPUS.md)

### N-grams (Prédictions Intelligentes)
- **Créole** : 1,721 prédictions | "ka" → fè, di, vwè | "nou" → ka, yé, fè
- **Luxembourgeois** : Adapté aux patterns linguistiques spécifiques

### Analyse Avancée
- Catégorisation par fréquence (rares, fréquents, très fréquents)
- Top 15 des mots les plus utilisés par langue
- Analyse comparative (delta) entre versions
- Validation automatique avec scoring

## 📱 Intégration Android

Les fichiers générés sont **directement prêts** pour l'app Android :

### Créole
- `../clavier_creole/assets/creole_dict.json`
- `../clavier_creole/assets/creole_ngrams.json`

### Luxembourgeois
- `../clavier_creole/assets/luxemburgish_dict.json`
- `../clavier_creole/assets/luxemburgish_ngrams.json`

## 🔧 Configuration

### Pipeline Créole
- Token Hugging Face (depuis `.env`)
- Dataset `POTOMITAN/PawolKreyol-gfc`
- Fallback sur fichiers locaux si nécessaire

### Pipeline Luxembourgeois
- Datasets `fredxlpy/LuxAlign` (champ `lb`) et `fredxlpy/LETZ` (champ `text`)
- **Publics : aucun `HF_TOKEN` n'est nécessaire**
- Phrases dédoublonnées toutes sources confondues avant comptage
- `--strict` refuse le repli local et l'absence d'un des deux corpus
- Licences et citations : [CORPUS.md](CORPUS.md)

## 📁 Structure

```
Dictionnaires/
├── KreyolComplet.py          # ⭐ PIPELINE CRÉOLE
├── LuxembourgishComplet.py   # ⭐ PIPELINE LUXEMBOURGEOIS
├── generate_cloze.py         # Jeu de phrases à trous (dérive des assets ci-dessus)
├── README.md                 # Documentation
├── README_Luxemburgish.md    # Documentation luxembourgeoise
├── requirements.txt          # Dépendances créoles
├── requirements_luxemburgish.txt # Dépendances luxembourgeoises
├── .venv/                    # Environnement virtuel
├── backups/                  # Sauvegardes automatiques
└── archives/                 # Anciens fichiers (historique)
    ├── scripts/              # Anciens scripts Python
    └── docs/                 # Ancienne documentation
```

## 🎯 Avantages du Pipeline Unique

1. **Zéro interaction** - Lancement et oubli
2. **Multi-langues** - Créole et Luxembourgeois
3. **Tout intégré** - Plus de scripts séparés
4. **Automatique** - De A à Z sans intervention
5. **Robuste** - Gestion d'erreurs et validation
6. **Complet** - Statistiques avancées incluses
7. **Sécurisé** - Backups automatiques
8. **Adaptatif** - Support caractères spéciaux par langue

## 🌐 Support Linguistique

### 🇸🇷 Créole Guadeloupéen
- Caractères spéciaux créoles
- Patterns linguistiques créoles
- Vocabulaire traditionnel et moderne

### �🇺 Luxembourgeois (Lëtzebuergesch)
- Caractères : ä, ë, é, ö, ü
- Vocabulaire gouvernemental et officiel
- Patterns de conférences de presse

## �🏆 Performance

### Créole
- **100% de validation** (4/4 tests réussis)
- **+78% de prédictions** vs versions précédentes
- **Temps d'exécution** : ~30 secondes

### Luxembourgeois
- Pipeline optimisé pour transcriptions longues
- Validation adaptée aux mots luxembourgeois
- Extraction efficace depuis dataset Akabi

## 🚀 Installation et Usage

### 1. Environnement virtuel
```bash
python -m venv .venv
.venv\Scripts\Activate.ps1  # Windows
source .venv/bin/activate   # Linux/Mac
```

### 2. Installation des dépendances
```bash
# Pour le créole
pip install -r requirements.txt

# Pour le luxembourgeois
pip install -r requirements_luxemburgish.txt
```

### 3. Exécution
```bash
# Pipeline créole
python KreyolComplet.py

# Pipeline luxembourgeois
python LuxembourgishComplet.py
```

---

*Fait avec ❤️ pour préserver les langues régionales*
*🇸🇷 Kreyòl Gwadloup ka viv! 🇸🇷*
*🇱🇺 Lëtzebuergesch Klavier ass prett! 🇱🇺*