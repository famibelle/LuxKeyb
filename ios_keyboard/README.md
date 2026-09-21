# Lëtzebuergesch Clavier pour iOS (squelette)

Extension clavier Swift + app conteneur SwiftUI. **Squelette de validation** : il
tape du QWERTZ luxembourgeois (lettres, chiffres, majuscule, effacement avec
répétition, globe) et sert à vérifier la chaîne complète *code → signature →
TestFlight → iPhone*. Ni suggestions, ni appui long, ni emojis, ni jeux : ils
viennent après, quand la chaîne est prouvée.

Rien n'a été compilé ni lancé : le développement se fait sous Linux, sans Xcode.
La première compilation réelle est celle de Codemagic, attendez-vous à corriger
quelques erreurs de Swift à ce moment-là.

## Structure

```
project.yml                     XcodeGen : décrit les deux cibles, remplace le .xcodeproj
App/                            app conteneur (explique l'activation, champ d'essai)
Keyboard/
  KeyboardViewController.swift  l'extension : construction, frappe, majuscules
  Disposition.swift             les rangées, recopiées de KeyboardLayoutManager.kt
../codemagic.yaml               le workflow de compilation (à la racine du dépôt)
```

Le `.xcodeproj` et les deux `Info.plist` sont **générés** et ignorés par git.

## Identifiants

| Cible | Bundle id |
|---|---|
| App | `com.potomitan.luxkeyboard` |
| Extension | `com.potomitan.luxkeyboard.keyboard` |

L'extension doit être préfixée par l'id de l'app. Pour changer la base, éditer
`project.yml` **et** les `vars` de `codemagic.yaml`.

## Mise en route, une seule fois

1. **Compte Apple Developer** (99 $/an).
2. **App Store Connect > Apps > +** : créer la fiche avec le bundle id de l'app.
   TestFlight refuse un envoi sans fiche.
3. **App Store Connect > Utilisateurs et accès > Intégrations > Clés d'API** :
   créer une clé (rôle *App Manager*), télécharger le `.p8` (une seule fois).
4. **Codemagic** : ajouter le dépôt, puis *Teams > Integrations > App Store
   Connect* et y enregistrer la clé sous le nom `LuxKeyb` (celui de
   `integrations.app_store_connect` dans `codemagic.yaml`).
5. Lancer le workflow `iOS clavier > TestFlight` à la main.
6. Sur l'iPhone : installer TestFlight, accepter l'invitation, installer l'app,
   puis *Réglages > Général > Clavier > Claviers > Ajouter un clavier*.

## Ce qui diffère d'Android, à ne pas oublier

- `RequestsOpenAccess = false` : clavier hors ligne, cohérent avec la politique de
  confidentialité. Passer à `true` (App Group pour partager la progression avec
  l'app, réseau) en change la portée.
- Pas de correcteur orthographique système sur iOS : `KreyolSpellCheckerService`
  n'a pas d'équivalent.
- Plafond mémoire d'une extension (de l'ordre de 50 à 70 Mo) : le moteur ne
  pourra pas parser `luxemburgish_ngrams.json` (5 Mo) comme le fait Android.
  Prévoir des assets binaires compacts.
- La touche EMOJI d'Android devient le globe (iOS ne laisse pas une extension
  appeler le sélecteur d'emojis système).
- `PrimaryLanguage = lb-LU` est un choix non vérifié sur appareil.

## Suite prévue

Appui long pour les accents (ü ö à è ê ç), puis moteur de suggestion (Levenshtein,
n-grammes, filtre de Bloom : les spécifications et les tests Kotlin servent de
référence), puis app conteneur (réglages, jeux, carnet).
