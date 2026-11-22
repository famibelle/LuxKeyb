---
name: guideUtilisateur
description: Créer ou mettre à jour l'aide de l'application Lëtzebuergesch Clavier, c'est-à-dire la visite guidée (bulles, sujets du sommaire, bouton « Essayer »), le bouton « ? » et le sommaire « Aide », la visite des cartes du carnet, et le guide écrit (« Le guide complet », ses sections et ses planches annotées de la carte), puis l'annoncer sur la page nouveautés. À utiliser dès que Medhi parle de visite guidée, d'aide, de guide utilisateur, de tutoriel, d'onboarding des fonctions, de « comment l'utilisateur découvre… », ou quand une fonction nouvelle ou modifiée doit être expliquée aux utilisateurs.
---

# L'aide de l'application

L'aide a une seule porte, **« Aide »** : le bouton « ? » à gauche du titre (visible sur tous les onglets) et la ligne « Aide » en bas de l'onglet Haut. Elle ouvre un sommaire qui donne accès à trois choses :

| Partie | Où dans le code | Pour quoi |
|---|---|---|
| **Visite guidée de l'application** | `SettingsActivity.lancerVisite()` | 8 étapes et un rappel, sur la vraie application |
| **Visite des cartes** | `carnet/CarnetFragment.proposerVisite()` | 6 étapes sur une vraie carte ; aussi par le « ? » du carnet (`aideCartes()`) |
| **Guide écrit** | `SettingsActivity.createGuideContent()` | À lire, avec les planches annotées de la carte |

Le moteur commun (voile, bulle, sujets, placement) est `VisiteGuidee.kt`. Il vient de KreyolKeyb 23.3.0, et LuxKeyb l'a beaucoup prolongé depuis. On travaille ici sur LuxKeyb seulement : ne pas proposer de reporter quoi que ce soit dans Klavyé Kréyòl.

## 1. Le fond : expliquer à quelqu'un qui n'est pas technophile

Les règles communes à tous les textes publics (ton, interdits, typographie, cinq langues) sont dans la skill **textesPublics** ; ce qui suit est propre à l'aide.

L'utilisatrice de référence est la maman de Medhi. C'est pour elle que la visite a été demandée : elle ne comprenait pas toutes les fonctions.

- **Dire quoi faire, pas décrire l'écran.** Les sujets du sommaire sont des actions : « Dicter un message », « Chercher un mot », « Partager une carte ». Pas « Spiller, les jeux ».
- **Une idée par bulle, deux ou trois phrases.** Une bulle qui ne tient pas sur l'écran est trop longue. Exception tolérée : l'étape des trois gestes, numérotée.
- **Faire essayer plutôt qu'expliquer** dès que c'est possible. Le bouton « Essayer » des bulles du clavier ouvre un champ où le clavier apparaît.
- **Toujours dire où retrouver l'aide** : la dernière bulle de la visite complète éclaire le « ? », et « Passer » y mène avant de fermer.
- **Interdit** :
  - le jargon de fabrication : corpus, n-grammes, LOD, Bloom, IME ;
  - le tiret cadratin ;
  - les promesses invérifiables.
- **Vérifier chaque fait dans le code avant de l'écrire.** Le guide a affirmé pendant des semaines que « rien n'est envoyé », alors que la dictée envoie la voix. Les sources :
  - **dictée et confidentialité** : `stt_info_texte` ;
  - **seuils de rareté** : `Rarete.SEUIL_*` dans `CarnetModels.kt` ;
  - **libellés des réglages** : `ks_*` ;
  - **contenu de la carte** : `CadreOrne.kt` (`Ornement`) et une capture.
- **Les noms de l'application restent en luxembourgeois** (Haut, Spiller, Wierderbuch, Mäi Lëtzebuergesch, Mäi Carnet), suivis de leur sens dans la langue de l'interface.

## 2. Montrer le texte avant de l'appliquer

Tout texte vu par les utilisateurs se montre **en entier, en français, à Medhi** avant d'être appliqué, sauf s'il a dit de faire. Ensuite, il faut :

- **Cinq langues** :
  - `values/` (anglais, langue par défaut) ;
  - `values-fr`, `values-lb`, `values-de`, `values-pt`.
- **Les traductions lb, de et pt sont écrites par Claude** : le dire, et proposer la relecture par la professeure de luxembourgeois.
- **`values-lb` glose en français** : l'application luxembourgeoise n'a pas de dictionnaire luxembourgeois-luxembourgeois.
- **En français, une espace insécable (U+00A0)** à l'intérieur des guillemets et avant « ? », sinon « Moien » se retrouve séparé de son guillemet en fin de ligne.
- **Dans `strings.xml`, échapper les apostrophes** (`\'`) et écrire les retours à la ligne `\n`.
- **Les libellés cités doivent être ceux de l'écran.** Les vérifier par `grep` dans les `strings.xml` de chaque langue : « Propositions en français » = `ks_propositions_francais`, « Ouvrir » = `sa_ouvrir`.

## 3. La visite guidée

### Ordre et sujets

L'ordre a été choisi par Medhi le 2026-10-21 : **le clavier, les cartes, le dictionnaire, les jeux, le reste, les réglages du clavier tout en bas**. Il vaut pour le sommaire comme pour la visite complète.

Visite de l'application (`lancerVisite`, rangs 0 à 7, puis le rappel) :

| Rang | Étape | Cible (tag de vue) | Sujet du sommaire |
|---|---|---|---|
| 0 | Le clavier en trois gestes, avec « Essayer » et l'animation `AnimationGestes` (37.7.8) | aucune | GESTES |
| 1 | La dictée, icône micro, avec « Essayer » et l'animation `AnimationDictee` (37.7.8) | aucune | DICTEE |
| 2 | Mäi Carnet (bandeau dans Spiller) | `visite_carnet` | (couvert par les sujets des cartes) |
| 3 | Wierderbuch, avec l'animation `AnimationDictionnaire` (37.7.9) | `visite_onglet_2` | DICTIONNAIRE |
| 4 | Spiller | `visite_onglet_1` | JEUX |
| 5 | Haut | `visite_onglet_0` | ACCUEIL |
| 6 | Mäi Lëtzebuergesch | `visite_onglet_3` | PROGRES |
| 7 | Réglages du clavier (roue dentée) | `visite_reglages` | REGLAGES |
| 8 | « Pour la revoir », visite complète seulement | `visite_aide` (le « ? ») | |

Visite des cartes (`proposerVisite`) :
- **0** : la carte entière ;
- **1** : Partager ;
- **2** : Céder ;
- **3** : Donner, étape par étape : quatre temps numérotés, sans découpe, avec l'animation `carnet/AnimationEchange.kt` posée entre titre et texte (paramètre `illustration` d'`Etape`, 37.7.7, idée de KreyolKeyb 23.3.3 réécrite à nos couleurs) ;
- **4** : Recevoir, carte refermée ;
- **5** : L'appareil photo (éclaire aussi « Recevoir »), qui porte le rappel dans la visite complète.

Sujets correspondants : LIRE_UNE_CARTE (0), PARTAGER (1), DONNER_OU_RECEVOIR (2 à 5). La visite complète ajoute au dernier texte où la retrouver (`visite_rappel_carnet`).

### Les animations des bulles

Trois bulles portent une illustration animée (paramètre `illustration` d'`Etape`) : l'échange d'une carte (`carnet/AnimationEchange.kt`, 37.7.7), les gestes et la dictée (`AnimationGestes.kt`, `AnimationDictee.kt`, 37.7.8, sur le clavier miniature `MiniClavier.kt` : disposition par défaut, couleurs du thème clair, doigt, pastilles). Règles communes :
- **N'animer que ce que le texte fait mal imaginer** : un geste, un enchaînement. Une bulle qui montre un endroit garde sa découpe, sans animation.
- **L'appui long n'est pas mimé** (Medhi, 2026-10-21) : tout le monde le connaît. Les pastilles des gestes montrent donc 1 et 3.
- **Le Wierderbuch lit le vrai dictionnaire** (`AnimationDictionnaire`, 37.7.9) : sens de « Kaz » dans la langue de l'interface (chat, cat, Katze, gato ; « Kaz » parce que « Haus » se glose « Haus » en allemand), lignes, fiche et exemple tirés de `TranslationDictionary` sur un fil, avec `applicatifDansLaLangue()`. Bulle vide tant qu'il charge : le mot du jour le charge au démarrage, mais juste après une installation en debug il faut ~15 s.
- Contenu vrai : les suggestions de « Moi » sont celles du moteur (Moien, Moie, Moies), le bandeau de la dictée est la chaîne `stt_online_listening`.
- Une vue dessinée par code, boucle de 9 à 10 s par `ValueAnimator`, image figée parlante quand les animations du système sont coupées, muette pour l'accessibilité.
- Vérifier la hauteur de la bulle sur le A21s (720 px), filmer avec `adb shell screenrecord` et faire le GIF des nouveautés avec ffmpeg (bulle recadrée, 498 px, 10 i/s, palette).

### Ce qui casse en silence

- **Les sujets désignent les étapes par leur rang** (`VisiteGuidee.Sujet.etapes`). Réordonner, ajouter ou retirer une étape impose de mettre à jour :
  - les rangs dans `Sujet` ;
  - `ETAPES_APPLICATION` et `ETAPES_CARNET` ;
  - `VisiteGuideeTest`.

  Un `check()` en build debug attrape un nombre d'étapes faux, pas un rang décalé.
- **Les cibles sont trouvées par tag de vue.** Renommer ou retirer un tag transforme l'étape en bulle sans découpe, sans erreur.
- **L'ordre des paramètres de `Etape`** : `icone` et `essai` sont placés *avant* `autres`, pour que les appels hérités de l'amont avec un lambda final se lient encore à `autres`.
- **La visite ne se lance que si l'écran a le focus** (`hasWindowFocus()`, reproposée par `onWindowFocusChanged`). Sans ça, le partage d'activation (un `AlertDialog`, pas un `DialogFragment`) s'ouvrait par-dessus la première bulle sur le A21s.
- **Placement de la bulle** : `placement()` prend le côté naturel, sinon l'autre, sinon le bas de l'écran par-dessus la cible. Une cible très grande (la carte entière) ne laissait pas la place au bouton « Suivant ».
- **Le champ « Essayer »** (`ouvrirEssai`) rabaisse le clavier 200 ms après sa fermeture. Le clavier se redemande quand l'activité reprend le focus, et une fermeture immédiate ne suffisait pas.
- **Préférences** dans `lux_onboarding_prefs` :
  - `visite_application_faite` ;
  - `visite_carnet_faite`, remise à `false` par « Toute la visite ».
- **Défaut connu, non corrigé** : avec un clavier physique, la touche Entrée active la vue focalisée sous le voile (la roue dentée) au lieu d'avancer.

## 4. Le guide écrit

`createGuideContent()` empile des sections (`addGuideSection(parent, couleur, titre, texte)`) et des images (`addGuideImage`). Ordre actuel :

1. installation (4 captures système) ;
2. écrire, avec le glissement du curseur ;
3. accents ;
4. suggestions, avec le français en option ;
5. **dictée** ;
6. correcteur ;
7. chiffres ;
8. jeux ;
9. cartes : 3 planches et leurs textes, puis la vignette ;
10. **partager, donner, recevoir** ;
11. progression ;
12. foire aux questions, qui dit aussi comment revoir la visite.

Alterner les couleurs de fond, ne pas en enchaîner deux identiques.

### Les planches annotées de la carte

`docs/scripts/guide_carte_annotee.py` produit `guide_carte_{haut,texte,bas,vignette}.png` dans `drawable-nodpi` (anglais) et `drawable-{fr,lb,de,pt}-nodpi`. Les numéros des légendes renvoient aux textes `sa_le_mot_tel_que_vous` (1 à 4), `sa_la_ligne_de_nature_ce` (5 à 8), `sa_ecu_vues_combien_de_fois` (9 à 13) et `sa_la_petite_carte_ne_garde` (A à C). Changer l'un oblige à changer l'autre, dans toutes les langues.

Recette, sur l'émulateur (AVD `pixel9`, 1080 × 2424, `ANDROID_SERIAL=emulator-5554`) :

1. Installer le build debug.
2. Pour chaque langue :
   - lancer `adb shell cmd locale set-app-locales com.potomitan.luxkeyboard --locales <l>` ;
   - la carte suit la langue de l'application grâce à `applicatifDansLaLangue()`, inutile de changer celle du système.
3. Ouvrir Spiller › « Ouvrir » (`sa_ouvrir`) › trier « A → Z » › défiler jusqu'en bas, et capturer la grille.
4. Toucher la vignette « Waasser », attendre 9 s (retournement et paysage), et capturer la carte.
5. Lancer `python docs/scripts/guide_carte_annotee.py --langue <l> --carte <carte.png> --grille <grille.png>`. Pour ne refaire que certaines planches : `--planches bas` (sans `--grille`, la vignette n'est pas refaite). Une cible de la ligne du bas, à gauche, reçoit une flèche coudée pour ne pas cacher les étoiles.
6. Remettre `--locales ""` à la fin.

Les cibles (`CIBLES`, `VIGNETTE`) ont été mesurées le 2026-10-21 sur un carnet de 11 cartes. Si la carte, l'écran ou le carnet changent, il faut les remesurer sur la capture française et vérifier chaque planche à l'œil. Cas particulier du portugais : le ZLS n'y traduit pas les phrases, la planche « texte » n'a donc que 3 repères, plus bas.

Pour mettre une carte précise dans le carnet de l'émulateur, ajouter une entrée au JSON de `shared_prefs/carnet_cartes.xml`, application arrêtée, via `run-as` :

```json
{"m":"Waasser","d":<ms>,"n":3,"g":"ws,kw","k":<numéro>,"b":2}
```

## 5. Vérifier sur un appareil

Le détail des règles et des pièges du A21s et de l'émulateur est dans la skill **testerSurAppareil** ; ici, ce qui concerne la visite.

**Préférer le A21s quand il est libre.** Il garde ses règles :
- vérifier `dumpsys window | grep mCurrentFocus` avant toute frappe injectée ;
- la délégation ne couvre que `com.potomitan.luxkeyboard` ;
- ne jamais toucher au wifi ;
- remettre notre clavier par défaut après un arrêt forcé ;
- le port ADB change à chaque connexion, le lire dans `adb devices`.

Pièges vus en faisant ce travail :
- **Le clavier par défaut retombe sur Gboard** après `adb install -r` ou `am force-stop`. Relancer `adb shell ime set com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored` : sans notre clavier, l'accueil n'est pas en mode « Aujourd'hui » et la visite ne se lance pas.
- **Rejouer la visite automatique** : retirer les lignes `visite_*` de `lux_onboarding_prefs.xml` (application arrêtée).
- **uiautomator ne rend rien pendant qu'une carte s'anime.** Utiliser `bleu.py` (ce dossier) pour trouver le bouton bleu, ou des coordonnées lues sur la capture. Sur une étape qui éclaire le bandeau bleu, `bleu.py` se trompe : utiliser uiautomator.
- **Les boutons d'`AlertDialog` sont en majuscules** (« FERMER ») : chercher le texte tel qu'affiché. Un emoji apparaît en entité (`&#128228;`) dans le dump.
- **Une ANR (« ne répond pas ») juste après une installation** sur l'émulateur vient de la préparation des cartes, une fois par installation. Ce n'est pas la visite. Attendre avant de faire défiler.
- **Lancer `./gradlew --stop` avant d'utiliser l'émulateur** : l'hôte manque de mémoire.
- **Partager les captures au fil de l'eau** (`SendUserFile`) : Medhi l'a demandé.

## 6. Publier

Le déroulé complet (commits, un tag à la fois, notes, Nouveautés, AAB) est dans la skill **publierVersion**.

1. **Tests** : `./gradlew testDebugUnitTest`, dont `VisiteGuideeTest`, qui couvre les sujets, le rappel, « Passer » et le placement.
2. **CHANGELOG** : une entrée en tête, en mots de tous les jours, puis la version dans `app/build.gradle` (`versionCode` = majeur × 10000 + mineur × 100 + patch).
3. **Un commit de fonction, puis un commit `chore(release): version X.Y.Z`, puis le tag.** Vérifier qu'on est sur `main` (une autre session peut changer de branche). Ne jamais committer `SuggestionEngine.kt` ni `BancEfficaciteTest.kt` s'ils traînent : ils appartiennent à une autre session.
4. **Un tag à la fois.** La création de la release lit le CHANGELOG de `main`, pas celui du tag. Pousser une version avant que la précédente soit publiée fait échouer la précédente (c'est arrivé aux 37.7.2 à 37.7.4). Attendre la fin du run, ou regrouper en une seule version.
5. **Corriger les notes d'une release** : ce `gh` n'a pas de `release edit`, passer par l'API, `gh api -X PATCH repos/famibelle/LuxKeyb/releases/<id> -f body="$(cat notes.md)"`.
6. **Page nouveautés** :
   - ajouter l'image de la version dans `docs/stats/nouveautes-medias.json`, avec un `alt` qui décrit chaque écran et une `legende` d'une phrase ;
   - ajouter les traductions dans `docs/stats/traductions/nouveautes-{en,de,pt}.json`, mêmes sections et même nombre de points que le français ;
   - lancer `python docs/scripts/generate_nouveautes.py` ;
   - vérifier le rendu avec Chrome sans interface ;
   - pousser directement sur `main`.
7. **Images** : un GIF de captures, fait avec `gif_aide.py` (ce dossier). Toute carte visible porte le **filigrane « LuxKeyb »**, en boîtes données à la main puisque le voile sombre empêche la détection automatique.
8. **Rappeler à Medhi** d'envoyer l'AAB sur la Play Console.

## 7. Liste de contrôle

- [ ] Chaque fait vérifié dans le code, en particulier ce qui touche aux données personnelles.
- [ ] Texte français montré à Medhi, sauf instruction contraire.
- [ ] Cinq langues ; lb, de et pt signalées comme à relire.
- [ ] Rangs des sujets, `ETAPES_*` et `VisiteGuideeTest` cohérents avec les listes d'étapes.
- [ ] Tags de vue présents sur chaque cible.
- [ ] Vérifié sur appareil : visite complète, un sujet de chaque groupe, « Essayer », « ? » du carnet.
- [ ] Guide écrit relu en entier si une fonction a changé ; planches refaites si la carte a changé.
- [ ] Un seul tag à la fois ; nouveautés à jour, traduites, cartes filigranées.
- [ ] `CLAUDE.md` (local, jamais committé) mis à jour si le fonctionnement change.
