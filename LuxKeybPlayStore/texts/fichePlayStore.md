# Fiche Play Store — Lëtzebuergesch Clavier

Textes à coller tels quels dans la Play Console (Développer la présence →
Fiche Play Store principale). Version de référence : **10.14.0**
(`versionCode` 101400), `applicationId` `com.potomitan.luxkeyboard`.

La Play Console **n'interprète pas le markdown** : pas de `**gras**`, pas de
`#` — les astérisques s'afficheraient tels quels. Tous les blocs ci-dessous
sont déjà en texte brut, émojis compris.

---

## Nom de l'application

*30 caractères maximum.*

```
Lëtzebuergesch Clavier
```

22 caractères. Identique à `app_name` dans `strings.xml`, donc le nom sous
l'icône du téléphone et le nom sur le Store coïncident.

Les 8 caractères restants ne sont pas utilisés volontairement : le mot-clé
recherché — « clavier luxembourgeois » — est couvert par la brève description
et la description complète, que l'algorithme du Store indexe aussi, alors
qu'un titre à rallonge est tronqué dans les listes de résultats.

## Brève description

*80 caractères maximum. C'est la seule ligne visible avant « Plus ».*

```
Clavier luxembourgeois : suggestions de mots, touches ë ä é, 100 % hors ligne
```

77 caractères. « Clavier luxembourgeois » est en tête, c'est la partie qui
survit à la troncature sur petits écrans ; « hors ligne » sert à la fois
d'argument de confidentialité et de mot-clé.

## Description complète

*4000 caractères maximum.*

```
Schreift Lëtzebuergesch op Ärem Telefon — endlech ouni Kampf.

Lëtzebuergesch Clavier est un clavier Android gratuit, sans publicité et entièrement hors ligne, conçu pour une seule langue : le lëtzebuergesch.

🛠️ Votre luxembourgeois est un peu rouillé ?
😤 Votre téléphone souligne en rouge tous vos mots ?
🤔 Vous hésitez sur l'orthographe à chaque message ?
➡️ Ce clavier est fait pour vous.

⌨️ LES ACCENTS SOUS LE POUCE

Les trois diacritiques qui portent la langue — é, ä et ë — ont chacune leur touche, directement sur le clavier. Plus besoin d'aller chercher un ë dans un sous-menu. Les autres accents (ü, è, à, ô, ê, ö) restent disponibles par appui long.

🧠 IL VOUS SOUFFLE LES MOTS

• 123 265 formes luxembourgeoises reconnues à la frappe, du corpus contemporain et du dictionnaire officiel LOD
• 26 172 contextes de prédiction : après un espace, le clavier propose la suite probable d'après les deux derniers mots, pas seulement le dernier
• Les mots que vous employez souvent remontent d'eux-mêmes

🇱🇺 🇫🇷 DEUX LANGUES, AUCUN RÉGLAGE

Le luxembourgeois passe en premier, le français prend le relais sur une seconde rangée. « ech hunn eng réunion muer » s'écrit sans changer de clavier.

📖 UN DICTIONNAIRE DANS L'APPLICATION

L'onglet Wierderbuch traduit 88 852 mots dans les deux sens, luxembourgeois et français, avec des phrases d'exemple du dictionnaire officiel. Hors ligne, comme le reste.

✍️ IL PARDONNE LES FAUTES DE FRAPPE

Une lettre oubliée, une lettre en trop, une touche voisine : la suggestion arrive quand même. Et vous pouvez taper sans diacritiques — écrivez « letzebuergesch », le clavier vous propose « lëtzebuergesch ».

✅ IL CORRIGE PARTOUT, PAS SEULEMENT DANS LE CLAVIER

Un correcteur orthographique système est fourni : une fois activé, vos mots luxembourgeois cessent d'être soulignés en rouge dans Messages, Notes ou votre messagerie.

🎮 IL VOUS FAIT PROGRESSER

Chaque mot employé fait monter votre niveau, d'Ufänker 🌍 à Sproochenmeeschter 🧙. Cinq jeux complètent le parcours :

• Wuertsich — les mots mêlés
• Wuertmix — remettre les lettres d'un mot dans l'ordre
• Wuertriet — deviner un mot de cinq lettres en six essais
• Wuertlück — retrouver le mot qui manque dans une phrase
• Zuelwuert — écrire les nombres en toutes lettres

🔒 IL NE SAIT RIEN DE VOUS

• Aucun accès à Internet : rien de ce que vous tapez ne quitte votre téléphone
• Aucune collecte, aucun compte, aucune publicité
• Seuls les mots déjà présents dans le dictionnaire sont comptés pour la progression : un mot de passe, un nom propre ou un numéro n'y figurent pas et ne sont donc jamais enregistrés
• Le clavier se désactive de lui-même dans les champs de mot de passe
• Code source ouvert et auditable sous licence MIT ; les dictionnaires gardent la licence de leurs corpus

📱 COMPATIBILITÉ

• Android 5.0 et plus récent, moins de 8 Mo
• Thème clair ou sombre, au choix ou d'après le réglage du téléphone
• Fonctionne dans toutes les applications : WhatsApp, SMS, e-mail, réseaux sociaux
• Installation guidée en trois étapes, avec un clavier d'essai dans l'application
• Android affiche l'avertissement générique de tout clavier tiers : c'est normal, l'onglet Guide explique pourquoi celui-ci ne peut rien envoyer nulle part

🙋 CE QU'IL NE FAIT PAS ENCORE

Pas de saisie glissée, pas de dictée vocale. Ces manques sont connus et prioritaires.

🎯 POUR QUI ?

• Celles et ceux qui écrivent en lëtzebuergesch tous les jours
• Les résidents qui apprennent la langue et les familles qui la transmettent
• Tous ceux qui en ont assez de corriger leur langue à la main

📈 UN DICTIONNAIRE QUI BOUGE

Le dictionnaire est régénéré à chaque version à partir d'un corpus ouvert de luxembourgeois contemporain : les suggestions suivent l'usage réel de la langue, pas une liste figée.

Un mot manque, une suggestion tombe à côté ? Signalez-le : github.com/famibelle/LuxKeyb

🗣️ Potomitan™
« Mir wëlle bleiwe wat mir sinn »
```

3937 unités UTF-16 sur 4 000 — c'est ainsi que compte la Play Console,
les drapeaux et quelques émojis valant 2 chacun. Il reste **63 caractères
de marge** : tout ajout suppose d'en retirer autant.

Les chiffres cités sont ceux de la version livrée et sont vérifiables dans les
actifs : `luxemburgish_dict.json` + `luxemburgish_lod_forms.json` pour les
123 265 formes, `luxemburgish_ngrams.json` pour les 26 172 contextes,
`luxemburgish_translations.json` pour les 88 852 mots du Wierderbuch,
`french_simple_dict.json` pour le français. **Les relire à chaque envoi** : ils
ont été faux pendant longtemps, la fiche annonçant encore 8 792 mots et 3 Mo
quand l'application en livrait cinq fois plus.

Quatre choses sont **délibérément absentes** de ce texte, contrairement à la
fiche du Klavyé Kréyòl dont elle reprend la structure :

- **aucune citation de presse** — les mentions Canal 10 / Guadeloupe la 1ère
  concernent l'autre application et seraient trompeuses ici ;
- **aucune liste d'auteurs**, le corpus luxembourgeois étant un jeu de données
  agrégé et non une anthologie d'auteurs identifiés ;
- **aucune promesse de saisie glissée ni de dictée vocale** : la section
  « ce qu'il ne fait pas encore » évite les avis 1 étoile de déception, qui
  pèsent lourd sur une fiche à faible volume ;
- **aucun « MIT » sans qualificatif.** Le code l'est, les dictionnaires non :
  ils dérivent de corpus en CC BY-NC et CC BY-SA. Voir `NOTICE.md`.

## Nouveautés de cette version

*500 caractères maximum. Champ « Nouveautés » de la version 11.3.0.*

```
📖 Le français du clavier passe de 662 à 125 348 mots : la seconde rangée de suggestions devient enfin utile, et le correcteur cesse de souligner en rouge des mots français corrects.

⌨️ Un bouton ramène au clavier luxembourgeois quand un autre clavier a pris la main.

🔗 « Voir sur le dictionnaire officiel » ouvre la fiche du mot sur lod.lu.
```

344 unités sur 500. Ce champ n'est pas affiché aux nouveaux visiteurs
d'une première publication, mais il l'est à chaque mise à jour — et
l'application étant déjà en test fermé, il est lu par les testeurs dès
maintenant. Les textes des versions antérieures sont dans l'historique git de
ce fichier.

---

## Le reste du formulaire

### Paramètres de la fiche

| Champ | Valeur |
|---|---|
| Catégorie d'application | Outils *(c'est la catégorie des claviers : Gboard, SwiftKey et HeliBoard y sont)* |
| Tags | Clavier · Productivité · Éducation |
| E-mail du développeur | medhi.famibelle@gmail.com |
| Site Web | https://famibelle.github.io/LuxKeyb/ |
| Politique de confidentialité | https://famibelle.github.io/LuxKeyb/privacy/privacy-policy.html |
| Langue par défaut de la fiche | Français (France) — l'interface de l'application est en français |
| Pays de diffusion | Luxembourg, Belgique, France, Allemagne, et diaspora (aucune raison de restreindre) |
| Contenu | Tout public — le questionnaire IARC ne déclenche rien : pas d'achat, pas de pub, pas de contenu généré par l'utilisateur, pas de partage de localisation |

### Sécurité des données

Le formulaire attend une réponse par catégorie. Ici tout est **non** :

- **Aucune donnée collectée**, aucune donnée partagée avec des tiers.
- L'application ne demande **aucune permission réseau** — vérifiable dans le
  manifeste, ce qui rend la déclaration défendable en cas de contrôle.
- Le compteur de progression écrit dans `filesDir`, sur l'appareil, et ne
  compte que des mots déjà présents dans le dictionnaire livré : ni les mots
  de passe, ni les noms propres, ni les numéros n'y entrent. C'est ce point
  que la politique de confidentialité détaille, et il faut qu'elle continue de
  le refléter si `CreoleDictionaryWithUsage` change.
- Le questionnaire pose une question spécifique aux claviers sur la saisie de
  texte : répondre que la saisie n'est ni collectée ni transmise.

### Traductions de la fiche

Le Store permet une fiche par langue. Le marché visé est trilingue ; par ordre
de rendement :

| Langue | Nom | Brève description |
|---|---|---|
| Luxembourgeois (lb) | `Lëtzebuergesch Clavier` | `Lëtzebuergesch Tastatur: Wuertvirschléi, ë ä é Tasten, 100 % offline` |
| Allemand (de) | `Lëtzebuergesch Clavier` | `Luxemburgische Tastatur: Wortvorschläge, ë ä é Tasten, 100 % offline` |
| Anglais (en) | `Lëtzebuergesch Clavier` | `Luxembourgish keyboard: word suggestions, ë ä é keys, 100 % offline` |

Le nom ne change pas d'une langue à l'autre : c'est le nom de l'application.

Les brèves descriptions luxembourgeoise et allemande **doivent être relues par
un locuteur natif** avant publication — une faute dans la vitrine d'un clavier
luxembourgeois coûte plus cher qu'ailleurs. Les descriptions complètes dans
ces langues restent à écrire ; tant qu'elles manquent, la fiche française
s'affiche par défaut, ce qui n'est pas bloquant.

---

## Éléments graphiques à fournir

Les huit fichiers à envoyer sont dans
[`../graphics/feature-graphic/`](../graphics/feature-graphic/). Chacun porte le
nom de l'emplacement du formulaire de la Console où il va, il n'y a donc rien à
retrouver au moment de l'envoi. Tous sont fabriqués par
[`../graphics/build_graphics.py`](../graphics/build_graphics.py) à partir des
sources du dépôt (le logo de `Logos/`, les captures réelles de
`docs/Screenshots/`) ; `python3 build_graphics.py check` les reconfronte aux
contraintes ci-dessous sans rien refabriquer. Les graphiques créoles dont ils
reprennent le gabarit ne sont plus dans le dépôt.

| Élément | Format exigé | Fichier |
|---|---|---|
| Icône de l'application | 512 × 512 PNG ou JPEG, moins de 1 Mo, sans transparence | `Icône de l'application.png` (243 Ko) — le lion de `Logos/luxembourg-logo-hd.png` aplati sur blanc |
| Image de présentation | 1024 × 500 PNG ou JPEG, moins de 15 Mo, sans transparence | `Image de présentation.png` (114 Ko), source HTML à côté |
| Captures d'écran pour téléphone | 2 à 8, 16:9 ou 9:16, côté entre 320 et 3840 px, moins de 8 Mo pièce | `Captures d'écran pour téléphone 1-6.png`, 1080 × 1920, de 196 à 261 Ko, légende incrustée |
| Captures tablette | facultatif | Non prévu |
| Vidéo YouTube | facultatif | Aucune. `docs/Screenshots/lux_clavier_demo.gif` n'est pas utilisable : le Store ne prend **pas** les GIF |

Les six captures dépassent toutes 1080 × 1080, et il y en a plus de quatre :
les deux conditions que la Console pose pour que l'application soit
promouvable. Leur numéro est leur ordre d'envoi ; le premier écran est le seul
que voit la plupart des visiteurs :

1. la barre de suggestions bilingue en cours de frappe
2. l'appui long sur `e`
3. la progression et le mot du jour
4. les mots mêlés
5. l'installation guidée
6. chiffres et symboles

Les noms ne disent plus ce que montre chaque capture, c'est le prix des noms
d'emplacement ; la liste ci-dessus et le tableau `SPECS` de
[`../graphics/build_graphics.py`](../graphics/build_graphics.py) le disent.

La légende est incrustée dans l'image parce que la Play Console n'en fournit
pas, et qu'elle aide beaucoup sur ce type d'application, où la valeur n'est
pas lisible d'un coup d'œil.

**Définition.** Les captures sources sont natives : elles ont été reprises le
2026-08-25 sur un émulateur 1080 × 2340 sous la 10.14.0, donc rien n'est
agrandi. Les refaire après un changement d'interface : recapturer dans
`docs/Screenshots/lux_*.png`, puis `python3 build_graphics.py shots`.

Le flyer triptyque A4 (`graphics/flyer-triptyque/`) ne sert pas à la Play
Console : il est là pour l'impression, adapté du flyer créole.

---

## À régler avant le passage en production

L'application est **déjà publiée en test fermé**. Ce qui suit conditionne le
passage en production, pas un premier envoi.

1. **Douze testeurs pendant quatorze jours consécutifs.** C'est une exigence de
   Google, pas un choix : sans elle la Console refuse la promotion. État au
   2026-09-03 : **9 sur 12** (`docs/stats/testeurs.json`, tenu à la main, il
   n'existe pas d'API publique pour ce chiffre). C'est le seul point dont le
   délai ne dépend pas de nous.

2. **Écrire à <ai@rtl.lu> avant la publication ouverte.** `luxemburgish_cloze.json`
   redistribue 1 600 phrases entières de RTL.lu, mot pour mot : ce n'est plus une
   statistique agrégée mais de la republication de contenu. CC BY-NC l'autorise
   pour un usage non commercial avec attribution visible — les deux conditions
   sont remplies — mais c'est le seul point où un tiers peut dire non, et les
   auteurs indiquent eux-mêmes ce contact. Voir `NOTICE.md`.

3. **Les captures du guide intégré** (`res/drawable-nodpi/guide_screenshot_*.png`)
   doivent montrer le clavier luxembourgeois. Elles ne partent pas au Store, mais
   un testeur qui ouvre l'onglet Guide les voit.

4. **Les six captures du Store** sont refaites à chaque changement d'interface.
   Elles doivent montrer la version envoyée : barre à quatre onglets, Wierderbuch,
   cinq jeux.

5. **Relire les chiffres de la description** contre les actifs livrés, voir plus
   haut.

Vérifier enfin que l'AAB envoyé est bien signé avec la clé de release : le
build tombe en signature debug avec un simple `println` si un secret manque
(voir le journal du job CI, pas seulement son statut vert).
