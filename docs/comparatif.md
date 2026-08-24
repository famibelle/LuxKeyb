---
title: "Lëtzebuergesch Clavier, Gboard ou clavier Apple : lequel pour écrire en luxembourgeois ?"
description: "Comparatif détaillé du Lëtzebuergesch Clavier face à Gboard (Android) et au clavier intégré d'Apple : disposition, dictionnaire, prédiction, vie privée, et ce que les autres font mieux."
lang: fr
---

<nav class="site">
  <a href="index.html">🏠 Accueil</a> ·
  <a href="guide.html">📘 Guide</a> ·
  <strong>⚖️ Comparatif</strong> ·
  <a href="privacy/privacy-policy.html">🔒 Confidentialité</a> ·
  <a href="feedbacks_form.html">💬 Retours</a> ·
  <a href="https://github.com/famibelle/LuxKeyb/releases/latest">📲 Télécharger</a> ·
  <a href="https://github.com/famibelle/LuxKeyb">💻 GitHub</a> ·
  <button type="button" class="theme-toggle" aria-label="Changer de thème">🌙</button>
</nav>

# Lëtzebuergesch Clavier, Gboard ou clavier Apple ?

*Pour écrire en luxembourgeois sur un téléphone, trois voies : le clavier livré
avec l'iPhone, Gboard — celui de Google, installé par défaut sur presque tous
les Android — et celui-ci. Voici ce qui les sépare, sans enjoliver.*

Autant l'annoncer tout de suite : **Gboard reste le clavier le plus complet du
marché**. Ce qu'il ne fait pas, c'est traiter le lëtzebuergesch comme une langue
à part entière plutôt que comme la 900ᵉ ligne d'une liste.

<div class="table-scroll" markdown="1">

| | **🇱🇺 Lëtzebuergesch Clavier** | Gboard (Google) | Clavier Apple (iOS) |
|---|---|---|---|
| Luxembourgeois pris en charge | **Oui**, c'est sa seule langue | Oui, parmi plus de 900 variétés de langues | **Non** jusqu'à iOS 26 ; annoncé dans iOS 27 |
| Disposition | **QWERTZ luxembourgeois**, celle des claviers physiques du pays | QWERTZ de la locale choisie | Allemand ou français ; disposition lb avec iOS 27 |
| Touches diacritiques | **`é` `ä` `ë` et l'apostrophe ont leur propre touche** | Appui long sur la voyelle | Appui long sur la voyelle |
| Dictionnaire | **9 016 mots**, corpus luxembourgeois public et vérifiable | Modèle propriétaire, non consultable | Modèle propriétaire, non consultable |
| Prédiction du mot suivant | **Oui**, 25 194 contextes (bigrammes et trigrammes) | Oui, réseaux de neurones et apprentissage fédéré | Oui, modèle embarqué |
| Pardonne les fautes de frappe | **Oui**, distance de Levenshtein | Oui | Oui |
| Écriture sans diacritiques | **Oui**, `Letzebuergesch` retrouve `Lëtzebuergesch` | — | — |
| Deux langues sans rien régler | **Oui**, deux rangées `LB` et `FR` en même temps, sans réglage | Jusqu'à 3 langues, à activer | Saisie multilingue limitée (≈31 langues), à activer |
| Correcteur système en luxembourgeois | **Oui** (et en français) | Intégré au clavier | Intégré, mais pas en lb avant iOS 27 |
| Aucun accès à Internet | **Oui**, hors ligne intégral | Non | Frappe embarquée |
| Données de frappe | **Seuls les mots du dictionnaire sont comptés, en local** | Embarqué + apprentissage fédéré, compte Google | Embarqué, confidentialité différentielle |
| Code ouvert | **Oui**, MIT | Non | Non |
| Jeux et progression | **Oui**, 3 jeux et 8 niveaux | Non | Non |
| Saisie glissée | Non | Oui | Oui |
| Dictée vocale | Non (celle du système reste accessible) | Oui | Oui |
| Traduction, presse-papiers, écriture manuscrite | Non | Oui | Partiellement |
| Thèmes et personnalisation | Palette luxembourgeoise | Très étendus | Très limités |
| Plateformes | Android 5.0 et plus | Android et iOS | iOS et iPadOS |
| Prix | Gratuit, sans publicité | Gratuit | Inclus |

</div>

<p style="font-size:0.9em;opacity:0.8;">« — » : non vérifié. Relevé en août 2026.
Chiffres du dictionnaire : génération du corpus lors de la version 10.13.0.</p>

## Trois choses que les autres ne font pas

**Une disposition dessinée pour la langue, pas héritée d'une autre.** Les trois
touches de diacritiques sont là parce que le corpus le dit : `é` y apparaît
2 596 fois, `ë` 1 251, `ä` 1 004 — puis la fréquence chute de 6,5 fois jusqu'au
`ü`, qui reste donc en appui long. L'apostrophe de l'élision — *d'Land*,
*s'Kanner* — a sa propre touche pour 649 occurrences, davantage que le `ü`.

**Un dictionnaire que vous pouvez ouvrir.** Le corpus est public, le script qui
en tire le dictionnaire est dans le dépôt, le fichier embarqué dans
l'application est lisible. Chez les deux autres, la qualité du luxembourgeois
est une boîte noire : impossible de savoir quels mots sont connus, ni d'où ils
viennent.

**Le bilinguisme comme situation normale**, et non comme réglage à activer.
Au Luxembourg on écrit rarement dans une seule langue, et le clavier est
construit là-dessus : il affiche **deux rangées de suggestions à la fois**, une
`LB` au rouge du drapeau et une `FR` au bleu ciel, chaque mot proposé sachant de
quelle langue il vient. Le luxembourgeois garde la priorité — son score est
majoré de moitié, celui du français réduit d'un cinquième, et il ne peut jamais
y avoir plus de deux mots français en face de trois luxembourgeois. Le français
n'apparaît qu'à partir de trois lettres, sur les 662 mots les plus courants :
de quoi dépanner sans jamais prendre le dessus. Le correcteur orthographique,
lui, est déclaré dans les deux langues.

Sans compter les niveaux et les jeux de vocabulaire, qui n'ont aucun équivalent
chez Gboard ni chez Apple — ce clavier sert aussi à réapprendre la langue en
l'écrivant.

## Ce que les autres font mieux

Gboard gagne sur la surface fonctionnelle, et largement : saisie glissée, dictée
vocale, traduction intégrée, presse-papiers, écriture manuscrite, thèmes, GIF et
autocollants, et une correction affinée par des milliards de frappes. Le clavier
Apple gagne sur l'intégration au système. Le Lëtzebuergesch Clavier ne cherche
pas à les rattraper sur ce terrain.

Dernier point, et c'est une bonne nouvelle pour la langue : **iOS 27 ajoutera le
luxembourgeois**, annoncé en juin 2026. Cela laisse toutefois Android sans
clavier pensé pour le lëtzebuergesch, et ne répond ni à la question du
dictionnaire vérifiable, ni à celle de l'apprentissage.

## Et face aux claviers libres ?

SwiftKey, HeliBoard et AnySoftKeyboard acceptent eux aussi le luxembourgeois,
avec d'autres compromis — la page d'accueil en donne
[le tableau complet](index.html#face-aux-autres-claviers).

<div align="center" style="margin: 24px 0;">
  <a href="https://github.com/famibelle/LuxKeyb/releases/latest"
     style="display:inline-block;padding:14px 28px;background:#ED2939;color:#fff;
            border-radius:8px;font-weight:bold;text-decoration:none;font-size:1.1em;">
    📲 Télécharger l'APK
  </a>
</div>
