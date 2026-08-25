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

• 8 792 mots luxembourgeois, tirés d'un corpus contemporain
• 23 169 contextes de prédiction : après un espace, le clavier propose la suite probable d'après les deux derniers mots, pas seulement le dernier
• Les mots que vous employez souvent remontent d'eux-mêmes

🇱🇺 🇫🇷 DEUX LANGUES, AUCUN RÉGLAGE

Les suggestions luxembourgeoises passent en premier, le français prend le relais pour les emprunts. « ech hunn eng réunion muer » s'écrit sans changer de clavier.

✍️ IL PARDONNE LES FAUTES DE FRAPPE

Une lettre oubliée, une lettre en trop, une touche voisine : la suggestion arrive quand même. Et vous pouvez taper sans diacritiques — écrivez « letzebuergesch », le clavier vous propose « lëtzebuergesch ».

✅ IL CORRIGE PARTOUT, PAS SEULEMENT DANS LE CLAVIER

Un correcteur orthographique système est fourni : une fois activé, vos mots luxembourgeois cessent d'être soulignés en rouge dans Messages, Notes ou votre messagerie.

🎮 IL VOUS FAIT PROGRESSER

Chaque mot employé fait avancer votre niveau, d'Ufänker 🌍 à Sproochenmeeschter 🧙, selon la part du dictionnaire que vous avez déjà utilisée. Trois jeux de vocabulaire complètent le parcours :

• Wuertsich — les mots mêlés
• Wuertmix — reconstituer un mot dont les lettres sont mélangées
• Wuertriet — deviner un mot de cinq lettres en six essais

🔒 IL NE SAIT RIEN DE VOUS

• Aucun accès à Internet : rien de ce que vous tapez ne quitte votre téléphone
• Aucune collecte, aucun compte, aucune publicité
• Seuls les mots déjà présents dans le dictionnaire sont comptés pour la progression : un mot de passe, un nom propre ou un numéro n'y figurent pas et ne sont donc jamais enregistrés
• Le clavier se désactive de lui-même dans les champs de mot de passe
• Code source ouvert et auditable, licence MIT

📱 COMPATIBILITÉ

• Android 5.0 et plus récent, environ 3 Mo
• Thème clair ou sombre, au choix ou d'après le réglage du téléphone
• Fonctionne dans toutes les applications : WhatsApp, SMS, e-mail, réseaux sociaux
• Installation guidée en trois étapes, avec un clavier d'essai dans l'application
• Android affiche au passage l'avertissement générique montré pour tout clavier tiers : il est normal, et l'onglet Guide explique pourquoi ce clavier-ci ne peut rien envoyer nulle part

🙋 CE QU'IL NE FAIT PAS ENCORE

Pas de saisie glissée, pas de dictée vocale. Ces manques sont connus et en tête de la liste des choses à faire.

🎯 POUR QUI ?

• Celles et ceux qui écrivent en lëtzebuergesch tous les jours
• Les résidents qui apprennent la langue
• Les familles qui la transmettent aux enfants
• Les élèves, et les candidats aux cours de langue
• Tous ceux qui en ont assez de corriger leur langue à la main

📈 UN DICTIONNAIRE QUI BOUGE

Le dictionnaire est régénéré à chaque version à partir d'un corpus ouvert de luxembourgeois contemporain : les suggestions suivent l'usage réel de la langue, pas une liste de mots figée.

Un mot manque, une suggestion tombe à côté ? Signalez-le, c'est ainsi que le dictionnaire progresse : github.com/famibelle/LuxKeyb

🗣️ Potomitan™
« Mir wëlle bleiwe wat mir sinn »
```

3 754 caractères sur 4 000 (3 771 si la Play Console compte en unités
UTF-16, ce que fait son compteur : les drapeaux et quelques émojis valent
2 chacun). Il reste donc moins de 250 caractères de marge — tout ajout
suppose d'en retirer autant.

Trois choses sont **délibérément absentes** de ce texte, contrairement à la
fiche du Klavyé Kréyòl dont elle reprend la structure :

- **aucune citation de presse** — les mentions Canal 10 / Guadeloupe la 1ère
  concernent l'autre application et seraient trompeuses ici ;
- **aucune liste d'auteurs**, le corpus luxembourgeois étant un jeu de données
  agrégé et non une anthologie d'auteurs identifiés ;
- **aucune promesse de saisie glissée ni de dictée vocale** : la section
  « ce qu'il ne fait pas encore » évite les avis 1 étoile de déception, qui
  pèsent lourd sur une fiche à faible volume. Le thème sombre y figurait
  jusqu'à la 10.13.0 ; il est livré depuis la 10.14.0 et est passé du côté
  des arguments.

## Nouveautés de cette version

*500 caractères maximum. Champ « Nouveautés » de la version 10.14.0.*

```
🌗 Le clavier se met en clair ou en sombre : « comme le téléphone », « toujours clair » ou « toujours sombre », dans les réglages du clavier.

Le drapeau ne change pas au passage : le rouge et le bleu ciel restent les mêmes dans les deux thèmes, seul le blanc des lettres passe en anthracite.

📐 Le clavier tient enfin dans l'écran en mode paysage, suggestions comprises.
```

370 caractères. Pour une **première** publication, ce champ n'est pas affiché
aux nouveaux visiteurs : le garder quand même, il devient visible dès la mise
à jour suivante.

Le texte de la 10.9.5 — palette du drapeau, tons de peau des emojis — a servi
de brouillon à celui-ci ; il est conservé dans l'historique git de ce fichier
si la fiche doit être remplie pour une version antérieure.

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

Tout est prêt dans [`../graphics/`](../graphics/), fabriqué par
[`../graphics/build_graphics.py`](../graphics/build_graphics.py) à partir des
sources du dépôt (le logo de `Logos/`, les captures réelles de
`docs/Screenshots/`). Les fichiers de `KreyolKeybPlayStore/graphics/` sont
créoles et n'ont servi que de gabarit.

| Élément | Format exigé | Fichier |
|---|---|---|
| Icône | 512 × 512 PNG 32 bits, sans transparence | `graphics/app-icon/luxkeyb-icon-512.png` — le lion de `Logos/luxembourg-logo-hd.png` aplati sur blanc |
| Image mise en avant | 1024 × 500 JPG ou PNG, sans transparence | `graphics/feature-graphic/luxkeyb-feature-1024x500.png`, source HTML à côté |
| Captures téléphone | 2 à 8, 16:9 ou 9:16, côté min. 320 px, max. 3840 px | `graphics/screenshots-phone/01…06_*.png`, 1080 × 1920, légende incrustée |
| Captures tablette | facultatif | Non prévu |
| Vidéo YouTube | facultatif | Aucune. `docs/Screenshots/lux_clavier_demo.gif` n'est pas utilisable : le Store ne prend **pas** les GIF |

L'ordre des captures est celui des numéros de fichiers ; le premier écran est
le seul que voit la plupart des visiteurs :

1. `01_suggestions.png` — la barre de suggestions bilingue en cours de frappe
2. `02_accents.png` — l'appui long sur `e`
3. `03_niveaux.png` — la progression et le mot du jour
4. `04_wuertsich.png` — les mots mêlés (première image du GIF)
5. `05_onboarding.png` — l'installation guidée
6. `06_numerique.png` — chiffres et symboles

La légende est incrustée dans l'image parce que la Play Console n'en fournit
pas, et qu'elle aide beaucoup sur ce type d'application, où la valeur n'est
pas lisible d'un coup d'œil.

**Réserve sur la définition.** Les captures d'origine font 440 à 540 px de
large : agrandies à 1080, elles restent un peu molles. C'est acceptable pour
un premier envoi, mais le vrai correctif est de les recapturer sur un
émulateur en 1080 × 2340 puis de relancer `build_graphics.py shots`.

Le flyer triptyque A4 (`graphics/flyer-triptyque/`) ne sert pas à la Play
Console : il est là pour l'impression, adapté du flyer créole.

---

## À régler avant le premier envoi

1. **La clé de signature actuelle n'est pas celle qui a fuité** — vérifié le
   2026-08-15, contrairement à ce que laisse entendre la section « Known gaps »
   de `CLAUDE.md`. Détail dans [`securite-keystore.md`](securite-keystore.md).
   En résumé : `luxkeyb-release.jks` (alias `upload`, créé le 2026-08-14) n'a
   jamais été suivi par git, donc **rien n'oblige à la régénérer avant le
   premier envoi**. Deux réserves :
   - si son mot de passe est celui qui traîne dans l'historique public, le
     changer — `keytool -storepasswd`, la clé et l'empreinte sont conservées,
     l'opération est indolore ;
   - l'ancien keystore créole, lui, a bel et bien été commité **en binaire**.
     Cela concerne la publication de KreyolKeyb, pas celle-ci.
2. **Les 9 captures du guide intégré (`res/drawable-nodpi/guide_screenshot_*.png`)
   montrent encore le clavier créole.** Elles ne partent pas au Store, mais un
   testeur qui ouvre l'onglet Guide les verra, et elles peuvent se retrouver
   dans une capture d'écran de la fiche par inadvertance.

Vérifier aussi que l'AAB envoyé est bien signé avec la clé de release : le
build tombe en signature debug avec un simple `println` si un secret manque
(voir le journal du job CI, pas seulement son statut vert).
