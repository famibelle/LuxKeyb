# Comparatif : Clavier Lëtzebuergesch, Gboard et le clavier Apple

Pour écrire en **luxembourgeois** sur un téléphone, il existe aujourd'hui trois voies : le
clavier système d'Apple, Gboard (le clavier de Google, par défaut sur la plupart des
Android), et **Clavier Lëtzebuergesch** — ce projet. Voici ce qui les sépare, sans
enjoliver : Gboard reste le clavier le plus complet du marché, mais il ne traite pas le
lëtzebuergesch comme une langue à part entière.

*Comparatif à jour du 24 août 2026.*

## Vue d'ensemble

| Critère | 🇱🇺 Clavier Lëtzebuergesch | Gboard (Google) | Clavier Apple (iOS) |
|---|---|---|---|
| **Luxembourgeois pris en charge** | Oui — c'est la seule langue du projet | Oui, parmi plus de 900 variétés de langues | **Non** jusqu'à iOS 26 ; annoncé dans iOS 27 (juin 2026, pas encore diffusé) |
| **Disposition** | QWERTZ luxembourgeois, calquée sur le clavier physique suisse-français | QWERTZ générique de la locale choisie | QWERTZ/AZERTY allemand ou français ; disposition lb avec iOS 27 |
| **Touches diacritiques dédiées** | `é` `ä` `ë` et l'apostrophe ont leur **propre touche** (choix fondé sur le comptage du corpus) | Appui long sur la voyelle | Appui long sur la voyelle |
| **Dictionnaire** | 9 016 mots issus d'un corpus luxembourgeois public ([POTOMITAN/luxembourgish-corpus](https://huggingface.co/datasets/POTOMITAN/luxembourgish-corpus)), **auditable et régénérable** | Modèle propriétaire, non consultable | Modèle propriétaire, non consultable |
| **Prédiction contextuelle** | 25 194 contextes n-grammes, très majoritairement à deux mots : bigrammes + trigrammes | Réseaux de neurones, apprentissage fédéré | Modèle Transformer embarqué, Apple Intelligence sur les appareils récents |
| **Tolérance aux fautes** | Distance de Levenshtein + correspondance insensible aux accents | Correction automatique mature | Correction automatique mature |
| **Bilingue lb + fr** | **Par défaut, sans réglage** : deux rangées de suggestions simultanées, étiquetées `LB` et `FR` et distinguées par couleur ; le luxembourgeois passe devant (score ×1,5 contre ×0,8), le français s'ajoute dès 3 lettres | Multilingue simultané (jusqu'à 3 langues), à activer | Saisie multilingue limitée (≈31 langues), à activer |
| **Correcteur orthographique système** | Oui, service dédié déclaré en `lb` **et** `fr` | Intégré au clavier | Intégré au système |
| **Saisie gestuelle (glissé)** | Non | Oui | Oui (QuickPath) |
| **Dictée vocale** | Non (celle du système reste accessible) | Oui, selon la langue | Oui, selon la langue |
| **Emoji** | Panneau complet, tons de peau par appui long | Panneau + recherche, GIF, autocollants, Emoji Kitchen | Panneau + recherche, autocollants |
| **Presse-papiers, traduction, écriture manuscrite** | Non | Oui | Partiellement |
| **Thèmes et personnalisation** | Palette aux couleurs luxembourgeoises, réglages dédiés | Très étendus | Très limités |
| **Progression et jeux** | **8 niveaux** (Ufänker → Sproochenmeeschter) et 3 jeux de vocabulaire (Wuertsich, Wuertmix, Wuertriet) | Aucun | Aucun |
| **Fonctionne hors ligne** | Intégralement — aucune requête réseau à la frappe | Oui pour la frappe ; certaines fonctions demandent le réseau | Oui pour la frappe |
| **Données de frappe** | **Seuls les mots déjà présents au dictionnaire sont comptés**, en local ; les champs sensibles sont exclus ; rien ne quitte l'appareil | Traitement embarqué + apprentissage fédéré, compte Google | Traitement embarqué, confidentialité différentielle |
| **Code source** | Public, MIT — vérifiable ligne à ligne | Fermé | Fermé |
| **Plateformes** | Android 5.0 et plus | Android et iOS | iOS et iPadOS uniquement |
| **Prix** | Gratuit | Gratuit | Inclus |

## Ce que Clavier Lëtzebuergesch apporte de plus

- **Une disposition dessinée pour la langue, pas héritée d'une autre.** Les trois touches
  diacritiques sont là parce que le corpus le dit : `é` 2 596 occurrences, `ë` 1 251,
  `ä` 1 004 — puis une chute de 6,5× jusqu'au `ü`, qui reste donc en appui long.
  L'apostrophe a sa propre touche pour 649 occurrences, plus que le `ü`.
- **Un dictionnaire vérifiable.** Le corpus est public, le script de génération est dans le
  dépôt, le fichier d'assets est lisible. Chez les deux autres, la qualité du
  luxembourgeois est une boîte noire.
- **Le bilinguisme est câblé dans le moteur**, pas ajouté en repli. Le mode bilingue est
  activé dès l'initialisation du clavier ; chaque suggestion porte sa langue
  (`BilingualSuggestion`), et l'affichage la montre : rangée `LB` en rouge du drapeau,
  rangée `FR` en bleu ciel, avec des encres choisies sur le contraste mesuré (4,2:1 et
  5,9:1). Le classement privilégie le luxembourgeois — +50 % sur son score, −20 % sur
  celui du français, 3 suggestions lb pour 2 fr au maximum — et le français n'apparaît
  qu'à partir de 3 lettres, sur 662 mots courants. Le correcteur système, lui, est
  déclaré en `lb` **et** en `fr`.
- **Apprendre en écrivant.** Niveaux et jeux de vocabulaire n'ont aucun équivalent chez
  Gboard ou Apple — c'est un clavier qui sert aussi la transmission de la langue.
- **Aucune télémétrie de frappe.** Les mots hors dictionnaire — mots de passe, noms
  propres, identifiants — ne sont jamais enregistrés, et le comptage reste sur l'appareil.

## Ce que les autres font mieux

Autant le dire clairement : Gboard gagne sur la surface fonctionnelle — saisie gestuelle,
dictée vocale, traduction intégrée, presse-papiers, écriture manuscrite, thèmes, GIF et
autocollants, et un correcteur affiné par des milliards de frappes. Le clavier Apple gagne
sur l'intégration système. Clavier Lëtzebuergesch ne cherche pas à les rattraper sur ce
terrain : il fait une chose qu'aucun des deux ne fait, écrire du lëtzebuergesch comme
langue première, hors ligne et sans boîte noire.

À noter enfin : l'arrivée du luxembourgeois dans iOS 27 est une bonne nouvelle pour la
langue. Elle laisse toutefois Android sans clavier pensé pour le lëtzebuergesch, et ne
répond ni à l'auditabilité du dictionnaire, ni à l'apprentissage.

La version en ligne de ce comparatif : <https://famibelle.github.io/LuxKeyb/comparatif.html>

## Sources

- [How Gboard is helping European languages in the digital age](https://blog.google/company-news/inside-google/around-the-globe/google-europe/how-gboard-helping-european-languages-digital-age/) — Google
- [Set up Gboard — langues prises en charge](https://support.google.com/gboard/answer/6380730#languages) — Aide Gboard
- [iOS 27 brings new keyboards and typing improvements across multiple languages](https://9to5mac.com/2026/06/12/ios-27-brings-new-keyboards-and-typing-improvements-across-multiple-languages/) — 9to5Mac, 12 juin 2026
- [Luxembourgish Keyboard/Language](https://discussions.apple.com/thread/253000290) — Apple Support Communities
- [Add or change keyboards on iPhone](https://support.apple.com/en-ge/guide/iphone/iph73b71eb/ios) — Apple
- Chiffres du dictionnaire et des n-grammes : génération du corpus lors du build 10.13.0 (24 août 2026)
