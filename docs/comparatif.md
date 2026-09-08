---
title: "Lëtzebuergesch Clavier, Gboard ou clavier Apple : lequel pour écrire en luxembourgeois ?"
description: "Comparatif détaillé du Lëtzebuergesch Clavier face à Gboard (Android) et au clavier intégré d'Apple : disposition, dictionnaire, prédiction, vie privée, et ce que les autres font mieux."
lang: fr
---

<nav class="site">
  <a href="index.html">🏠 Accueil</a> ·
  <a href="guide.html">📘 Guide</a> ·
  <a href="faq.html">❓ FAQ</a> ·
  <a href="simulateur.html">⌨️ Essayer en ligne</a> ·
  <a href="corpus.html">📚 Les corpus</a> ·
  <a href="dossier.html">📄 Dossier</a> ·
  <a href="nouveautes.html">🎁 Nouveautés</a> ·
  <a href="labs.html">🔬 Labs</a> ·
  <strong>⚖️ Comparatif</strong> ·
  <a href="ambassadeurs.html">📣 Ambassadeurs</a> ·
  <a href="privacy/privacy-policy.html">🔒 Confidentialité</a> ·
  <a href="feedbacks_form.html">💬 Retours</a> ·
  <a href="index.html#devenir-testeur">🧪 Testeur</a> ·
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
| Dictionnaire | **123 265 formes**, corpus public et dictionnaire officiel, l'un et l'autre vérifiables | Modèle propriétaire, non consultable | Modèle propriétaire, non consultable |
| Prédiction du mot suivant | **Oui**, 26 172 contextes (bigrammes et trigrammes) | Oui, réseaux de neurones et apprentissage fédéré | Oui, modèle embarqué |
| Pardonne les fautes de frappe | **Oui**, distance de Levenshtein | Oui | Oui |
| Écriture sans diacritiques | **Oui**, `Letzebuergesch` retrouve `Lëtzebuergesch` | — | — |
| Majuscules des noms (*Groussschreiwung*) | **Oui**, rétablies d'après le contexte, désactivables | — | — |
| Deux langues sans rien régler | **Oui**, deux rangées `LB` et `FR` en même temps, sans réglage | Jusqu'à 3 langues, à activer | Saisie multilingue limitée (≈31 langues), à activer |
| Correcteur système en luxembourgeois | **Oui** (et en français) | Intégré au clavier | Intégré, mais pas en lb avant iOS 27 |
| Aucun accès à Internet | **Oui**, hors ligne intégral | Non | Frappe embarquée |
| Données de frappe | **Seuls les mots du dictionnaire sont comptés, en local** | Embarqué + apprentissage fédéré, compte Google | Embarqué, confidentialité différentielle |
| Code ouvert | **Oui**, MIT | Non | Non |
| Jeux et progression | **Oui**, 5 jeux et 8 niveaux | Non | Non |
| Saisie glissée | Non | Oui | Oui |
| Dictée vocale | Non (celle du système reste accessible) | Oui | Oui |
| Traduction, presse-papiers, écriture manuscrite | Non | Oui | Partiellement |
| Thèmes et personnalisation | Palette luxembourgeoise | Très étendus | Très limités |
| Plateformes | Android 5.0 et plus | Android et iOS | iOS et iPadOS |
| Prix | Gratuit, sans publicité | Gratuit | Inclus |

</div>

<p style="font-size:0.9em;opacity:0.8;">« — » : non vérifié. Colonnes Gboard et
Apple relevées en août 2026. Chiffres du dictionnaire : les fichiers réellement
livrés dans la version 10.19.0.</p>

## Trois choses que les autres ne font pas

**Une disposition dessinée pour la langue, pas héritée d'une autre.** Les trois
touches de diacritiques sont là parce que le corpus le dit : `é` y apparaît
269 749 fois, `ë` 142 374, `ä` 111 780 — puis la fréquence chute de 15,6 fois
jusqu'au `ü`, qui reste donc en appui long. L'apostrophe de l'élision —
*d'Land*, *s'Kanner* — a sa propre touche pour 99 349 occurrences, deux fois
plus que le trait d'union et six fois plus que l'apostrophe courbe `’`.

**Un dictionnaire que vous pouvez ouvrir.** Le corpus est public, le script qui
en tire le dictionnaire est dans le dépôt, le fichier embarqué dans
l'application est lisible. Chez les deux autres, la qualité du luxembourgeois
est une boîte noire : impossible de savoir quels mots sont connus, ni d'où ils
viennent. Ouvrable veut aussi dire mesurable : sur *ParaLux*, un jeu de phrases
qu'aucun corpus d'entraînement ne contient, le mot réellement tapé figure dans
les trois suggestions affichées **18,8 %** du temps, et **94,1 %** des mots de
ces phrases sont connus du dictionnaire. Ni Gboard ni Apple ne publient
d'équivalent pour le lëtzebuergesch.

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

La dictée est le seul de ces manques sur lequel quelque chose est en cours : une
reconnaissance vocale luxembourgeoise s'essaie dans le canal
[Labs](labs.html), sans date de sortie ni promesse de qualité.

Dernier point, et c'est une bonne nouvelle pour la langue : **iOS 27 ajoutera le
luxembourgeois**, annoncé en juin 2026. Cela laisse toutefois Android sans
clavier pensé pour le lëtzebuergesch, et ne répond ni à la question du
dictionnaire vérifiable, ni à celle de l'apprentissage.

## Et le clavier Samsung ?

La remarque revient souvent : « le luxembourgeois est pris en charge par les
téléphones Samsung ». **Elle est exacte**, et il vaut mieux le dire clairement
que de laisser croire le contraire.

Vérifié le 8 septembre 2026, sans posséder l'appareil : nous avons ouvert le
Clavier Samsung livré avec le **Galaxy S24 Ultra** (`SM-S928B`, One UI 7 sous
Android 15, build `S928BXXS4BYEC`), récupéré dans un dépôt public de
micrologiciels. Son catalogue de langues compte **691 entrées**, et le
luxembourgeois y figure : code `lb`, affiché « Lëtzebuergesch », avec un nom
traduit dans toutes les langues d'interface de Samsung. Il apparaît donc bien
dans *Paramètres › Gestion globale › Clavier Samsung › Langues et types ›
Gérer les langues de saisie*.

Ce que le fichier déclare pour cette entrée, mot pour mot : disposition
**QWERTZ** par défaut (qwerty et azerty au choix), prédiction, texte prédictif,
correction automatique, vérification orthographique, saisie glissée, espacement
automatique. C'est une prise en charge réelle, et sur plusieurs de ces points le
clavier de Samsung fait ce que le nôtre ne fait pas.

Trois nuances, toutes lisibles dans ce même fichier :

- **Le luxembourgeois est dans la longue traîne, pas dans le noyau.** Sur les
  691 langues du catalogue, 67 seulement reçoivent le jeu complet d'options
  (celles qui incluent la mise en majuscule automatique et la recherche).
  `lb` n'en fait pas partie, en compagnie de l'alsacien, du frison, du corse ou
  du français de Belgique.
- **Aucune disposition dessinée pour la langue.** `lb` hérite du QWERTZ
  générique, partagé avec des dizaines d'autres langues ; l'allemand, lui, a
  droit à son `german_qwertz`. Les `é`, `ä` et `ë` restent donc en appui long,
  alors qu'ils pèsent 269 749, 111 780 et 142 374 occurrences dans notre corpus.
  C'est exactement la différence que ce clavier-ci existe pour corriger.
- **Rien n'est publié sur le contenu.** Ni la taille du dictionnaire
  luxembourgeois, ni sa source, ni son taux de réussite. C'est vrai de Samsung
  comme de Gboard et de SwiftKey, et pour cause : le moteur de prédiction de
  Samsung est celui de SwiftKey (la bibliothèque `libfluency-java.so` est dans
  l'application). Impossible de savoir quels mots sont connus, ni d'où ils
  viennent.

### Les deux claviers, mesurés côte à côte sur un Galaxy

Puisque le Clavier Samsung propose la langue, la question suivante est de savoir
ce qu'il en fait. Mesure du 8 septembre 2026 sur un **Galaxy A21s** (Android 12),
avec son clavier d'origine 5.4.85.4, luxembourgeois déjà activé, contre notre
version 20.3.0 : 20 phrases du corpus ZLS jamais entraîné, **166 frontières de
mots**, et à chaque fois la même question, *le mot suivant est-il dans les trois
suggestions affichées, sans qu'une seule de ses lettres ait été tapée ?*

<div class="table-scroll" markdown="1">

| | barre vide | mot juste en 1ʳᵉ position | mot juste dans les 3 |
|---|---|---|---|
| **Lëtzebuergesch Clavier 20.3.0** | 9,0 % | **10,8 %** | **21,1 %** |
| Clavier Samsung 5.4.85.4 | 0,0 % | 9,0 % | 16,9 % |

</div>

Le détail apparié dit mieux que les totaux ce qui sépare les deux claviers. Les
deux trouvent le mot 21 fois, **nous seuls 14 fois**, Samsung seul 7 fois. Et
quand notre barre propose quelque chose, elle a raison **23,2 %** du temps, contre
**15,2 %** au Clavier Samsung sur ces mêmes positions. La différence tient à ce
qu'il fait de son silence : n'ayant pas de contexte, il affiche *de · an · der*,
les trois mots les plus fréquents de la langue, ce qui lui vaut quelques
victoires et beaucoup de bruit. Nous préférons la barre vide, 9 fois sur 100.

Deux vérifications qui donnent sa valeur au chiffre : notre résultat est
**exactement celui que promet le fichier de n-grammes embarqué** (21,1 % lui
aussi, calculé hors ligne sur ces positions), et les phrases retenues ne
contiennent aucun diacritique, faute de pouvoir les taper en automatique sans
avantager l'un des deux claviers.

Sur la mécanique, même appareil, clavier ouvert dans un champ neutre :

<div class="table-scroll" markdown="1">

| | mémoire vive | processeur au repos | taille installée |
|---|---|---|---|
| **Lëtzebuergesch Clavier 20.3.0** | **105 à 137 Mo** | **0 ms / 30 s** | **8,4 Mo** |
| Clavier Samsung 5.4.85.4 | 158 à 171 Mo | 70 ms / 30 s | 110,1 Mo |

</div>

Le détail de la méthode, les scripts et les relevés bruts sont dans le dépôt
(`BANC-CLAVIERS.md`), y compris les défauts que ce banc nous a fait trouver chez
nous.

Une précision utile au passage : aucun téléphone Android n'affiche son
*interface* en luxembourgeois. La langue est bien reconnue comme locale
(`lb-LU`) par le système, mais le socle d'Android (API 36, vérifié dans ses
ressources) ne contient aucune traduction luxembourgeoise. Ce qui se joue sur
ces appareils est donc la saisie, jamais l'affichage.

Reste la question de fond : « prendre en charge une langue » ne veut pas dire la
même chose d'un clavier à l'autre. Cela peut être une disposition héritée d'une
autre langue, une liste de mots fermée, ou un dictionnaire dont on publie la
taille, la source et le taux de réussite. Nous sommes le seul à donner les
trois, et le seul à traiter la *Groussschreiwung*, l'écriture sans diacritiques,
le correcteur système et le bilinguisme luxembourgeois-français comme la
situation normale plutôt que comme une case à cocher parmi 691.

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
