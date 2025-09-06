# Clavier Créole - POC

Ce projet est une preuve de concept (POC) pour un clavier virtuel en créole, développé avec Flutter. Il permet de saisir du texte en créole avec des caractères spéciaux et des suggestions basées sur un dictionnaire.

## Fonctionnalités

- **Clavier virtuel** :
  - Inclut les caractères spéciaux créoles : `ô`, `é`, `è`, `ò`, `'`, etc.
  - Rangées de touches QWERTY standard.
  - Boutons fonctionnels : espace, backspace (⌫).
- **Suggestions automatiques** :
  - Basées sur un dictionnaire des mots créoles les plus fréquents.
  - Affichées sous la zone de saisie.
- **Interface utilisateur** :
  - Zone de saisie avec un bouton pour masquer/afficher le clavier.
  - Clavier responsive avec un design moderne.

## Structure du Projet

- **`main.dart`** : Contient l'interface utilisateur et la logique du clavier.
  - **Clavier virtuel** : Construit dynamiquement avec des rangées de touches.
  - **Suggestions** : Générées en fonction du texte saisi.
  - **Caractères spéciaux créoles** : Ajoutés dans une rangée dédiée.
- **`assets/creole_dict.json`** : Dictionnaire des mots créoles les plus fréquents.
- **`Dictionnaire.py`** : Script Python pour générer le dictionnaire à partir d'un dataset.

## Installation

1. **Cloner le projet** :
   ```bash
   git clone -b poc_keyboard <URL_DU_REPO>
   cd KreyolKeyb
   ```

2. **Installer les dépendances Flutter** :
   ```bash
   flutter pub get
   ```

3. **Ajouter le fichier de dictionnaire** :
   - Assurez-vous que le fichier `creole_dict.json` est présent dans le dossier `assets/`.

4. **Lancer l'application** :
   ```bash
   flutter run
   ```

5. **Tester l'APK sur un émulateur Android** :
   - Lancer un émulateur Android.
   - Installer l'APK généré :
     ```bash
     adb install clavier_creole/build/app/outputs/flutter-apk/app-release.apk
     ```

## Génération du Dictionnaire

Le fichier `creole_dict.json` est généré à partir d'un dataset de traductions créole-français. Pour le régénérer :

1. Installez les dépendances Python :
   ```bash
   pip install datasets
   ```

2. Exécutez le script :
   ```bash
   python Dictionnaire.py
   ```

Le fichier sera sauvegardé dans le répertoire courant.

## Prochaines Étapes

- Ajouter des fonctionnalités avancées comme la correction automatique.
- Créer une version système du clavier pour Android (IME).
- Optimiser l'interface pour les petits écrans.

## Contributions

Les contributions sont les bienvenues ! N'hésitez pas à ouvrir une issue ou une pull request.

## Licence

Ce projet est sous licence MIT.