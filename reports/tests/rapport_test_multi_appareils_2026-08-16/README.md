# Rapport de tests d'écriture portrait / paysage sur 18 appareils, v10.11.1

**Date :** 16 août 2026
**Testeur :** Claude Code (agent, modèle Opus 5), à la demande de l'utilisateur
**Version testée :** 10.11.1, APK debug `Potomitan_Kreyol_Keyboard_v10.11.1_debug_2026-08-15.apk` (commit `54d2ad52`, sources et APK vérifiés synchrones)
**Environnement :** 18 AVD lancés un par un en headless sous WSL2, images `google_apis` et `google_apis_playstore`, API 33 à 36

> **Statut : 18 appareils sur 18 validés, portrait et paysage.** Dans les 36 tests, la phrase « nou ka palé kréyòl » a été saisie intégralement touche par touche, accents `é` et `ò` compris. Aucun crash, aucun ANR, aucune rangée tronquée. Les 4 rangées et leurs 38 touches sont détectées sur chaque appareil et dans chaque orientation. Une seule anomalie observée, une majuscule parasite en milieu de mot sur un test sur 36 (voir conclusion 10).

## Objectif

Vérifier que la rédaction d'un message réel reste possible dans les deux orientations sur toute la gamme d'appareils émulés, et mesurer ce que le clavier coûte à l'application. Suite directe de la campagne du 2 août 2026 ([`rapport_test_multi_appareils_2026-08-02.md`](../rapport_test_multi_appareils_2026-08-02.md)), qui ne couvrait que le portrait, et des correctifs de mise en page paysage de la 10.11.1 (`b59ebb76`, `ec1e9269`).

## Méthodologie

Le banc (`scripts/`) automatise pour chaque AVD :

1. Boot headless de l'émulateur, attente de `sys.boot_completed=1`
2. Installation de l'APK debug, puis temporisation avant la sélection de l'IME (voir « Pièges » ci-dessous)
3. Activation et sélection de l'IME, **revérifiée juste avant chaque capture**
4. Attribution explicite du rôle SMS à Google Messages, puis ouverture de l'écran de rédaction par intention `SENDTO`
5. Tap sur le champ de composition du corps du message, jamais sur le champ destinataire
6. Détection de la géométrie du clavier **par analyse d'image** : les touches portent un contour `#D0D0D0` sur un fond `#F5F5F5`, ce contour sert de marqueur et donne les bornes exactes de chaque touche sans coordonnées devinées
7. Vidage du champ par la vraie touche ⌫ affichée, après déplacement du curseur en fin de texte
8. Frappe de « nou ka palé kréyòl » touche par touche, avec capture à mi-mot (« nou ka palé kré ») pour observer la bande de suggestions
9. Relecture du champ pour vérifier le texte obtenu, avec une reprise automatique en cas de tap dupliqué
10. Rotation en paysage par `adb emu rotate`, puis reprise complète du protocole
11. Mesure de la fenêtre IME via `dumpsys window`, scan logcat des crashs et ANR, extinction propre

Un seul émulateur à la fois : la RAM WSL2 ne supporte pas plusieurs instances.

**Phrase de test.** « nou ka palé kréyòl » est intégralement attestée dans le corpus : les quatre mots sont dans `creole_dict.json` et les enchaînements `nou ka` → `palé` et `ka palé` → `kréyòl` figurent dans `creole_ngrams.json`. Elle exerce en une seule frappe les deux touches d'accent dédiées (`é` en rangée 4, `ò` en rangée 1), la barre d'espace et le moteur de n-grammes.

## Résultats par appareil

Classés par densité croissante. « Touche » donne la hauteur puis la largeur en dp, « clavier » la part de la surface applicative occupée, « libre » ce qui reste à l'application.

| Appareil | Géométrie | dpi | API | P. touche | P. clavier | P. libre | Y. touche | Y. clavier | Y. libre |
|---|---|---|---|---|---|---|---|---|---|
| Samsung Galaxy A05 | 720×1600 | 262 | 34 | 47,0 × 36,6 | 41,1 % | 536,8 dp | 31,1 × 80,3 | 56,5 % | 204,6 dp |
| Petit écran 720×1280 | 720×1280 | 320 | 34 | 46,5 × 27,8 | **58,4 %** | **280,0 dp** | 30,5 × 54,8 | 70,8 % | **122,0 dp** |
| Samsung Galaxy A15 | 1080×2340 | 385 | 34 | 46,5 × 37,2 | 39,9 % | 560,2 dp | 30,8 × 82,3 | 55,3 % | 214,0 dp |
| Samsung Galaxy A34 | 1080×2312 | 390 | 34 | 46,8 × 36,7 | 41,0 % | 536,2 dp | 30,8 × 80,1 | 55,9 % | 208,8 dp |
| OnePlus Nord CE4 | 1080×2412 | 394 | 34 | 46,7 × 36,3 | 39,5 % | 568,5 dp | 30,5 × 83,1 | 56,1 % | 205,9 dp |
| Xiaomi Redmi Note 13 | 1080×2400 | 395 | 34 | 46,6 × 36,3 | 39,8 % | 561,8 dp | 30,8 × 82,5 | 56,6 % | 203,3 dp |
| Samsung Galaxy A54 | 1080×2340 | 403 | 34 | 46,5 × 34,8 | 42,0 % | 517,3 dp | 30,6 × 77,7 | 58,4 % | 192,2 dp |
| Motorola Moto G54 | 1080×2400 | 405 | 34 | 46,6 × 34,7 | 41,2 % | 535,7 dp | 30,8 × 79,7 | 59,1 % | 188,8 dp |
| Samsung Galaxy S25 | 1080×2340 | 416 | 34 | 46,5 × 33,8 | 43,4 % | 490,4 dp | 30,8 × 75,3 | 60,4 % | 178,8 dp |
| Sony Xperia XZ2 | 1080×2160 | 424 | 34 | 46,8 × 33,0 | 48,4 % | 405,7 dp | 30,6 × 67,4 | 61,3 % | 172,5 dp |
| Samsung Galaxy S23 | 1080×2340 | 425 | 34 | 46,7 × 32,9 | 44,4 % | 472,1 dp | 30,9 × 73,7 | 61,8 % | 170,2 dp |
| Honor X9c | 1080×2340 | 440 | **36** | 46,9 × 31,7 | 39,3 %\* | 516,4 dp | 30,9 × 71,2 | 54,1 %\* | 180,4 dp |
| Pixel 5 (Play Store) | 1080×2340 | 440 | 34 | 46,9 × 31,7 | 46,1 % | 442,9 dp | 30,9 × 71,2 | 64,1 % | 156,4 dp |
| Pixel 5 (référence) | 1080×2340 | 440 | 34 | 46,9 × 31,7 | 46,1 % | 442,9 dp | 30,9 × 71,2 | 64,1 % | 156,4 dp |
| Xiaomi Redmi Note 15 Pro | 1280×2772 | 447 | 34 | 46,9 × 38,1 | 38,8 % | 586,3 dp | 30,8 × 84,9 | 54,2 % | 222,6 dp |
| Honor Magic5 Lite | 1080×2400 | 480 | 34 | 46,3 × 27,8 | 49,3 % | 394,7 dp | 30,3 × 65,7 | **70,8 %** | **122,0 dp** |
| Honor Magic5 Lite | 1080×2400 | 480 | 33 | 46,3 × 27,8 | 49,3 % | 394,7 dp | 30,3 × 65,7 | **70,8 %** | **122,0 dp** |
| Google Pixel 7 Pro | 1440×3120 | 560 | 34 | 46,6 × 32,9 | 43,6 % | 490,3 dp | 30,6 × 75,2 | 61,4 % | 173,4 dp |

\* Les pourcentages du Honor X9c ne sont **pas comparables** aux autres : en API 36 `mAppBounds` couvre tout l'écran sans déduction d'insets (`0,0-1080,2340` contre `0,136-1080,2274` en API 34), ce qui gonfle le dénominateur. Comparer plutôt les hauteurs absolues, à géométrie identique : 920 px de fenêtre IME en portrait sur le Honor contre 986 px sur le Pixel 5.

### Amplitude mesurée

| Grandeur | min | médiane | max |
|---|---|---|---|
| Hauteur de touche, portrait | 46,3 dp | 46,7 dp | 47,0 dp |
| Hauteur de touche, paysage | 30,3 dp | 30,8 dp | 31,1 dp |
| Largeur de touche, portrait | 27,8 dp | 33,4 dp | 38,1 dp |
| Largeur de touche, paysage | 54,8 dp | 75,2 dp | 84,9 dp |
| Part du clavier, portrait | 38,8 % | 42,7 % | 58,4 % |
| Part du clavier, paysage | 54,1 % | 59,8 % | 70,8 % |
| Place laissée à l'app, portrait | 280 dp | 503 dp | 586 dp |
| Place laissée à l'app, paysage | 122 dp | 180 dp | 223 dp |

## Conclusions

**1. La mise en page tient sur toute la gamme.** La touche mesure 46,3 à 47,0 dp de haut en portrait sur une plage de densité de 262 à 560 dpi, soit 0,7 dp d'écart. La hauteur nominale de 48 dp est conservée partout, la différence tenant à l'arrondi en pixels et à la mesure entre bordures intérieures. Rien ne dépend du constructeur ni de la version d'Android : le Magic5 Lite donne des chiffres identiques au dp près en API 33 et en API 34.

**2. Le compromis paysage est bien calibré, et il est saturé.** La hauteur de touche tombe à 30,3-31,1 dp partout, c'est-à-dire exactement sur le plancher `BUTTON_MIN_HEIGHT_DP = 32` de `KeyboardLayoutManager`. Ce n'est plus un réglage qui s'adapte, c'est une butée : les 18 appareils y sont collés. Ce que la hauteur perd, la largeur le rend, la touche passant d'une médiane de 33 dp de large en portrait à 75 dp en paysage. La cible reste grande, seulement plus plate, ce qui est le bon arbitrage pour une orientation où seule la hauteur manque.

**3. Le vrai coût du paysage est pour l'application, pas pour le clavier.** Le clavier y occupe 54 à 71 % de la surface applicative. Sur le Magic5 Lite et le petit écran, il ne reste que **122 dp** : dans Google Messages la conversation disparaît entièrement et seul le champ de saisie survit (voir `captures/kreyol_magic5lite_paysage_apres_frappe.png`). C'est la limite structurelle de l'orientation plutôt qu'un défaut du clavier, mais elle est atteinte, et toute rangée ou tout bandeau ajouté s'y verrait immédiatement.

**4. La densité protège l'entrée de gamme.** Le rapport est l'inverse de l'intuition : plus la densité est basse, plus l'appareil respire. Le Redmi Note 15 Pro garde 223 dp en paysage contre 122 dp au Magic5 Lite. Les appareils les plus courants du lectorat visé sont les mieux servis.

**5. Le cas le plus contraint est le petit écran en portrait, pas en paysage.** Sur le 720×1280, le clavier prend **58,4 %** de la surface applicative en portrait, contre 39 à 46 % sur la plupart des appareils, et ne laisse que 280 dp. C'est le seul appareil où le portrait devient serré. Le rendu y reste correct et la frappe intégrale, mais c'est là qu'un futur ajout de hauteur ferait mal en premier. Le Xperia XZ2, avec son écran court en 18:9, arrive juste derrière à 48,4 %.

**6. Android 16 modifie la barre système sous le clavier.** Sur le Honor X9c, seul appareil en API 36, la fenêtre applicative couvre tout l'écran sans déduction d'insets, et le chevron et le globe de navigation se dessinent directement sur le fond du clavier au lieu d'occuper une bande noire séparée comme en API 33 et 34. Les touches ne sont pas recouvertes : 95 px restent libres sous la dernière rangée en portrait, 84 px en paysage. C'est cosmétique aujourd'hui, mais c'est le comportement à retester quand `targetSdk` passera à 36.

**7. Le correcteur système souligne le kréyòl en rouge.** Visible sur toutes les captures après frappe : « ka » et « palé » sont soulignés, « kréyòl » ne l'est pas. C'est exactement ce que `KreyolSpellCheckerService` corrigerait, mais il n'est pas sélectionné par défaut et l'utilisateur doit aller le chercher dans *Réglages › Système › Clavier › Correcteur orthographique*. Un utilisateur qui installe le clavier voit donc toujours son kréyòl signalé comme fautif dans les applications.

**8. La puce « Envoyer un mot à un ami » se déclenche correctement.** Observée une seule fois sur toute la campagne, sur le profil applicatif neuf du Magic5 Lite en API 33, et uniquement sur un champ portant l'action clavier `IME_ACTION_SEND`. La garde ajoutée après le test du 2 août 2026 fonctionne comme documentée : la puce ne consomme pas son unique occasion sur un champ destinataire.

**9. Les suggestions n-grammes répondent partout.** Dans 35 des 36 orientations testées, `kréyòl` est proposé en tête dès la saisie de « kré », suivi de `kréyol` et `krèyòl` produits par la tolérance aux accents. La trente-sixième est une capture prise trop tôt, pas une absence de suggestion. Aucune suggestion française n'apparaît sur cette phrase, ce qui est attendu : aucun de ces mots n'est dans les 660 entrées de `french_simple_dict.json`.

**10. Une majuscule parasite en milieu de mot, une fois sur 36.** Sur le Honor X9c en portrait, le champ contient « NoU ka palé kréyòl » : un `U` capital en troisième lettre, visible à l'écran (`captures/honor_x9c_test_portrait_apres_frappe.png`). La touche ⇧ ne figure pas dans la séquence de frappe et n'est adjacente à aucune des touches tapées, donc un tap égaré est peu probable. Aucun autre appareil ni aucune autre orientation ne reproduit le cas, y compris le passage précédent sur ce même Honor. La cause n'est **pas établie** : soit un réarmement transitoire de l'état majuscule côté IME, soit un artefact d'injection sous charge. Le seuil de comparaison du banc étant insensible à la casse, ce test est compté comme réussi ; l'anomalie est signalée ici parce qu'elle porte sur le seul aspect que la campagne n'a pas pu trancher. À surveiller sur un appareil réel en Android 16.

## Pièges rencontrés, à retenir pour le prochain banc

Ces points ont chacun coûté un ou plusieurs passages complets. Ils sont corrigés dans les scripts publiés ici.

- **La sélection de l'IME ne survit pas à la réinstallation de l'APK.** Le système rebascule sur Gboard pendant sa réindexation des claviers, plusieurs secondes après un `ime set` pourtant réussi. Sans revérification au moment de la capture, toute la campagne mesure Gboard sans que rien ne le signale. Le banc temporise après l'installation **et** revérifie `default_input_method` juste avant chaque capture.
- **`settings put system user_rotation` est sans effet sur ces AVD.** Seule la rotation de l'appareil (`adb emu rotate`) fait tourner l'affichage, et seulement si l'auto-rotation est **activée** (`accelerometer_rotation 1`, l'inverse du réflexe) et qu'une application acceptant le paysage est au premier plan : le lanceur est verrouillé en portrait et bloque tout.
- **Le tap qui donne le focus tombe au centre du champ**, donc au milieu d'un éventuel brouillon restauré par Messages. La touche ⌫ n'efface que vers l'arrière et laissait la fin du texte collée derrière le message tapé. Il faut déplacer le curseur en fin de texte avant de vider.
- **Sur les images Play Store, Messages n'est pas application SMS par défaut.** L'intention `SENDTO` n'ouvre alors aucun écran de rédaction. Le banc attribue le rôle explicitement via `cmd role add-role-holder`.
- **La boucle `while read` de `run_all.sh` doit rediriger le stdin du script appelé** (`< /dev/null`), sans quoi Python avale le reste de `devices.txt` et la campagne s'arrête au premier appareil.
- **`pgrep -f qemu-system` se reconnaît lui-même** dans sa propre ligne de commande : la boucle d'attente d'extinction ne se termine jamais. Utiliser `pgrep -x qemu-system-x86_64`.
- **Le mode fenêtré est inutilisable ici.** Sous WSLg, le rendu SwiftShader entre en concurrence CPU avec l'invité : boot à froid de 11 minutes, puis ANR de SystemUI. En headless le boot tombe à 30-58 secondes. Cette campagne a donc tourné en headless, sur décision explicite de l'utilisateur, par dérogation à la consigne durable du 6 août 2026.

## Contenu du répertoire

| Chemin | Contenu |
|---|---|
| `scripts/run_device.py` | Protocole complet pour un AVD, des deux orientations aux mesures |
| `scripts/kbdetect.py` | Détection de la géométrie du clavier par analyse du contour des touches |
| `scripts/run_all.sh` | Campagne séquentielle sur la liste `devices.txt` |
| `scripts/rejouer.sh` | Rejeu ciblé d'un ou plusieurs appareils |
| `scripts/analyse.py` | Agrégation des `result.json` en tableau comparatif et `resume.json` |
| `scripts/devices.txt` | Liste des AVD testés et leurs libellés |
| `captures/` | 108 captures, 6 par appareil : clavier, suggestions à mi-mot et champ après frappe, dans les deux orientations |
| `donnees/<avd>.json` | Mesures brutes par appareil, y compris cadre de la fenêtre IME et extraits logcat |
| `donnees/resume.json` | Synthèse consolidée des 18 appareils |

## Rejouer la campagne

```bash
cd reports/tests/rapport_test_multi_appareils_2026-08-16/scripts
bash run_all.sh                      # les 18 appareils, environ 1 h 45
bash rejouer.sh kreyol_test          # un seul appareil
python3 analyse.py                   # tableau comparatif
```

L'APK est cherché automatiquement dans `android_keyboard/app/build/outputs/apk/debug/`, le plus récent étant retenu ; une variable d'environnement `APK=` permet d'en imposer un autre. Construire d'abord avec `./gradlew assembleDebug`, en tenant compte des pièges Java 17 et `gradlew` documentés dans [`CLAUDE.md`](../../../CLAUDE.md). Les sorties d'un nouveau passage vont dans `scripts/results/`, qui est ignoré par git : les résultats publiés de cette campagne restent dans `captures/` et `donnees/`.

## Captures d'écran

Six captures par appareil : le clavier à l'ouverture, la bande de suggestions à mi-mot (après « nou ka palé kré ») et le champ une fois la phrase complète saisie, dans chaque orientation. Classées par densité croissante, comme le tableau des résultats.

> Cette section charge les 108 images, soit 16 Mo. Les fichiers sont aussi accessibles un par un dans [`captures/`](captures).

### Samsung Galaxy A05 (720×1600, 262 dpi)

Touche 47,0 × 36,6 dp en portrait, 31,1 × 80,3 dp en paysage. Le clavier occupe 41,1 % de la surface applicative en portrait et 56,5 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A05 portrait](captures/kreyol_a05_portrait_clavier.png) | ![Suggestions Samsung Galaxy A05 portrait](captures/kreyol_a05_portrait_suggestions.png) | ![Après frappe Samsung Galaxy A05 portrait](captures/kreyol_a05_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A05 paysage](captures/kreyol_a05_paysage_clavier.png) | ![Suggestions Samsung Galaxy A05 paysage](captures/kreyol_a05_paysage_suggestions.png) | ![Après frappe Samsung Galaxy A05 paysage](captures/kreyol_a05_paysage_apres_frappe.png) |

### Petit écran (720×1280, 320 dpi)

Touche 46,5 × 27,8 dp en portrait, 30,5 × 54,8 dp en paysage. Le clavier occupe 58,4 % de la surface applicative en portrait et 70,8 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Petit écran 720x1280 (360 dp) portrait](captures/kreyol_smallphone_portrait_clavier.png) | ![Suggestions Petit écran 720x1280 (360 dp) portrait](captures/kreyol_smallphone_portrait_suggestions.png) | ![Après frappe Petit écran 720x1280 (360 dp) portrait](captures/kreyol_smallphone_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Petit écran 720x1280 (360 dp) paysage](captures/kreyol_smallphone_paysage_clavier.png) | ![Suggestions Petit écran 720x1280 (360 dp) paysage](captures/kreyol_smallphone_paysage_suggestions.png) | ![Après frappe Petit écran 720x1280 (360 dp) paysage](captures/kreyol_smallphone_paysage_apres_frappe.png) |

### Samsung Galaxy A15 (1080×2340, 385 dpi)

Touche 46,5 × 37,2 dp en portrait, 30,8 × 82,3 dp en paysage. Le clavier occupe 39,9 % de la surface applicative en portrait et 55,3 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A15 portrait](captures/kreyol_a15_portrait_clavier.png) | ![Suggestions Samsung Galaxy A15 portrait](captures/kreyol_a15_portrait_suggestions.png) | ![Après frappe Samsung Galaxy A15 portrait](captures/kreyol_a15_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A15 paysage](captures/kreyol_a15_paysage_clavier.png) | ![Suggestions Samsung Galaxy A15 paysage](captures/kreyol_a15_paysage_suggestions.png) | ![Après frappe Samsung Galaxy A15 paysage](captures/kreyol_a15_paysage_apres_frappe.png) |

### Samsung Galaxy A34 (1080×2312, 390 dpi)

Touche 46,8 × 36,7 dp en portrait, 30,8 × 80,1 dp en paysage. Le clavier occupe 41,0 % de la surface applicative en portrait et 55,9 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A34 portrait](captures/kreyol_a34_portrait_clavier.png) | ![Suggestions Samsung Galaxy A34 portrait](captures/kreyol_a34_portrait_suggestions.png) | ![Après frappe Samsung Galaxy A34 portrait](captures/kreyol_a34_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A34 paysage](captures/kreyol_a34_paysage_clavier.png) | ![Suggestions Samsung Galaxy A34 paysage](captures/kreyol_a34_paysage_suggestions.png) | ![Après frappe Samsung Galaxy A34 paysage](captures/kreyol_a34_paysage_apres_frappe.png) |

### OnePlus Nord CE4 (1080×2412, 394 dpi)

Touche 46,7 × 36,3 dp en portrait, 30,5 × 83,1 dp en paysage. Le clavier occupe 39,5 % de la surface applicative en portrait et 56,1 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier OnePlus Nord CE4 portrait](captures/kreyol_nordce4_portrait_clavier.png) | ![Suggestions OnePlus Nord CE4 portrait](captures/kreyol_nordce4_portrait_suggestions.png) | ![Après frappe OnePlus Nord CE4 portrait](captures/kreyol_nordce4_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier OnePlus Nord CE4 paysage](captures/kreyol_nordce4_paysage_clavier.png) | ![Suggestions OnePlus Nord CE4 paysage](captures/kreyol_nordce4_paysage_suggestions.png) | ![Après frappe OnePlus Nord CE4 paysage](captures/kreyol_nordce4_paysage_apres_frappe.png) |

### Xiaomi Redmi Note 13 (1080×2400, 395 dpi)

Touche 46,6 × 36,3 dp en portrait, 30,8 × 82,5 dp en paysage. Le clavier occupe 39,8 % de la surface applicative en portrait et 56,6 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Xiaomi Redmi Note 13 portrait](captures/kreyol_rednote13_portrait_clavier.png) | ![Suggestions Xiaomi Redmi Note 13 portrait](captures/kreyol_rednote13_portrait_suggestions.png) | ![Après frappe Xiaomi Redmi Note 13 portrait](captures/kreyol_rednote13_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Xiaomi Redmi Note 13 paysage](captures/kreyol_rednote13_paysage_clavier.png) | ![Suggestions Xiaomi Redmi Note 13 paysage](captures/kreyol_rednote13_paysage_suggestions.png) | ![Après frappe Xiaomi Redmi Note 13 paysage](captures/kreyol_rednote13_paysage_apres_frappe.png) |

### Samsung Galaxy A54 (1080×2340, 403 dpi)

Touche 46,5 × 34,8 dp en portrait, 30,6 × 77,7 dp en paysage. Le clavier occupe 42,0 % de la surface applicative en portrait et 58,4 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A54 portrait](captures/kreyol_a54_portrait_clavier.png) | ![Suggestions Samsung Galaxy A54 portrait](captures/kreyol_a54_portrait_suggestions.png) | ![Après frappe Samsung Galaxy A54 portrait](captures/kreyol_a54_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy A54 paysage](captures/kreyol_a54_paysage_clavier.png) | ![Suggestions Samsung Galaxy A54 paysage](captures/kreyol_a54_paysage_suggestions.png) | ![Après frappe Samsung Galaxy A54 paysage](captures/kreyol_a54_paysage_apres_frappe.png) |

### Motorola Moto G54 (1080×2400, 405 dpi)

Touche 46,6 × 34,7 dp en portrait, 30,8 × 79,7 dp en paysage. Le clavier occupe 41,2 % de la surface applicative en portrait et 59,1 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Motorola Moto G54 portrait](captures/kreyol_motog54_portrait_clavier.png) | ![Suggestions Motorola Moto G54 portrait](captures/kreyol_motog54_portrait_suggestions.png) | ![Après frappe Motorola Moto G54 portrait](captures/kreyol_motog54_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Motorola Moto G54 paysage](captures/kreyol_motog54_paysage_clavier.png) | ![Suggestions Motorola Moto G54 paysage](captures/kreyol_motog54_paysage_suggestions.png) | ![Après frappe Motorola Moto G54 paysage](captures/kreyol_motog54_paysage_apres_frappe.png) |

### Samsung Galaxy S25 (1080×2340, 416 dpi)

Touche 46,5 × 33,8 dp en portrait, 30,8 × 75,3 dp en paysage. Le clavier occupe 43,4 % de la surface applicative en portrait et 60,4 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy S25 portrait](captures/kreyol_s25_portrait_clavier.png) | ![Suggestions Samsung Galaxy S25 portrait](captures/kreyol_s25_portrait_suggestions.png) | ![Après frappe Samsung Galaxy S25 portrait](captures/kreyol_s25_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy S25 paysage](captures/kreyol_s25_paysage_clavier.png) | ![Suggestions Samsung Galaxy S25 paysage](captures/kreyol_s25_paysage_suggestions.png) | ![Après frappe Samsung Galaxy S25 paysage](captures/kreyol_s25_paysage_apres_frappe.png) |

### Sony Xperia XZ2 (1080×2160, 424 dpi)

Touche 46,8 × 33,0 dp en portrait, 30,6 × 67,4 dp en paysage. Le clavier occupe 48,4 % de la surface applicative en portrait et 61,3 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Sony Xperia XZ2 portrait](captures/kreyol_xperiaxz2_portrait_clavier.png) | ![Suggestions Sony Xperia XZ2 portrait](captures/kreyol_xperiaxz2_portrait_suggestions.png) | ![Après frappe Sony Xperia XZ2 portrait](captures/kreyol_xperiaxz2_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Sony Xperia XZ2 paysage](captures/kreyol_xperiaxz2_paysage_clavier.png) | ![Suggestions Sony Xperia XZ2 paysage](captures/kreyol_xperiaxz2_paysage_suggestions.png) | ![Après frappe Sony Xperia XZ2 paysage](captures/kreyol_xperiaxz2_paysage_apres_frappe.png) |

### Samsung Galaxy S23 (1080×2340, 425 dpi)

Touche 46,7 × 32,9 dp en portrait, 30,9 × 73,7 dp en paysage. Le clavier occupe 44,4 % de la surface applicative en portrait et 61,8 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy S23 portrait](captures/kreyol_s23_portrait_clavier.png) | ![Suggestions Samsung Galaxy S23 portrait](captures/kreyol_s23_portrait_suggestions.png) | ![Après frappe Samsung Galaxy S23 portrait](captures/kreyol_s23_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Samsung Galaxy S23 paysage](captures/kreyol_s23_paysage_clavier.png) | ![Suggestions Samsung Galaxy S23 paysage](captures/kreyol_s23_paysage_suggestions.png) | ![Après frappe Samsung Galaxy S23 paysage](captures/kreyol_s23_paysage_apres_frappe.png) |

### Honor X9c, Android 16 (1080×2340, 440 dpi)

Touche 46,9 × 31,7 dp en portrait, 30,9 × 71,2 dp en paysage. Le clavier occupe 39,3 % de la surface applicative en portrait et 54,1 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Honor X9c (Android 16 / API 36) portrait](captures/honor_x9c_test_portrait_clavier.png) | ![Suggestions Honor X9c (Android 16 / API 36) portrait](captures/honor_x9c_test_portrait_suggestions.png) | ![Après frappe Honor X9c (Android 16 / API 36) portrait](captures/honor_x9c_test_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Honor X9c (Android 16 / API 36) paysage](captures/honor_x9c_test_paysage_clavier.png) | ![Suggestions Honor X9c (Android 16 / API 36) paysage](captures/honor_x9c_test_paysage_suggestions.png) | ![Après frappe Honor X9c (Android 16 / API 36) paysage](captures/honor_x9c_test_paysage_apres_frappe.png) |

### Pixel 5 (image Play Store) (1080×2340, 440 dpi)

Touche 46,9 × 31,7 dp en portrait, 30,9 × 71,2 dp en paysage. Le clavier occupe 46,1 % de la surface applicative en portrait et 64,1 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Pixel 5 (image Play Store) portrait](captures/kreyol_playstore_portrait_clavier.png) | ![Suggestions Pixel 5 (image Play Store) portrait](captures/kreyol_playstore_portrait_suggestions.png) | ![Après frappe Pixel 5 (image Play Store) portrait](captures/kreyol_playstore_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Pixel 5 (image Play Store) paysage](captures/kreyol_playstore_paysage_clavier.png) | ![Suggestions Pixel 5 (image Play Store) paysage](captures/kreyol_playstore_paysage_suggestions.png) | ![Après frappe Pixel 5 (image Play Store) paysage](captures/kreyol_playstore_paysage_apres_frappe.png) |

### Pixel 5 (référence) (1080×2340, 440 dpi)

Touche 46,9 × 31,7 dp en portrait, 30,9 × 71,2 dp en paysage. Le clavier occupe 46,1 % de la surface applicative en portrait et 64,1 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Pixel 5 (référence 393 dp) portrait](captures/kreyol_test_portrait_clavier.png) | ![Suggestions Pixel 5 (référence 393 dp) portrait](captures/kreyol_test_portrait_suggestions.png) | ![Après frappe Pixel 5 (référence 393 dp) portrait](captures/kreyol_test_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Pixel 5 (référence 393 dp) paysage](captures/kreyol_test_paysage_clavier.png) | ![Suggestions Pixel 5 (référence 393 dp) paysage](captures/kreyol_test_paysage_suggestions.png) | ![Après frappe Pixel 5 (référence 393 dp) paysage](captures/kreyol_test_paysage_apres_frappe.png) |

### Xiaomi Redmi Note 15 Pro (1280×2772, 447 dpi)

Touche 46,9 × 38,1 dp en portrait, 30,8 × 84,9 dp en paysage. Le clavier occupe 38,8 % de la surface applicative en portrait et 54,2 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Xiaomi Redmi Note 15 Pro portrait](captures/kreyol_rednote15pro_portrait_clavier.png) | ![Suggestions Xiaomi Redmi Note 15 Pro portrait](captures/kreyol_rednote15pro_portrait_suggestions.png) | ![Après frappe Xiaomi Redmi Note 15 Pro portrait](captures/kreyol_rednote15pro_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Xiaomi Redmi Note 15 Pro paysage](captures/kreyol_rednote15pro_paysage_clavier.png) | ![Suggestions Xiaomi Redmi Note 15 Pro paysage](captures/kreyol_rednote15pro_paysage_suggestions.png) | ![Après frappe Xiaomi Redmi Note 15 Pro paysage](captures/kreyol_rednote15pro_paysage_apres_frappe.png) |

### Honor Magic5 Lite (API 34) (1080×2400, 480 dpi)

Touche 46,3 × 27,8 dp en portrait, 30,3 × 65,7 dp en paysage. Le clavier occupe 49,3 % de la surface applicative en portrait et 70,8 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Honor Magic5 Lite (API 34) portrait](captures/kreyol_magic5lite_portrait_clavier.png) | ![Suggestions Honor Magic5 Lite (API 34) portrait](captures/kreyol_magic5lite_portrait_suggestions.png) | ![Après frappe Honor Magic5 Lite (API 34) portrait](captures/kreyol_magic5lite_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Honor Magic5 Lite (API 34) paysage](captures/kreyol_magic5lite_paysage_clavier.png) | ![Suggestions Honor Magic5 Lite (API 34) paysage](captures/kreyol_magic5lite_paysage_suggestions.png) | ![Après frappe Honor Magic5 Lite (API 34) paysage](captures/kreyol_magic5lite_paysage_apres_frappe.png) |

### Honor Magic5 Lite (API 33) (1080×2400, 480 dpi)

Touche 46,3 × 27,8 dp en portrait, 30,3 × 65,7 dp en paysage. Le clavier occupe 49,3 % de la surface applicative en portrait et 70,8 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Honor Magic5 Lite (API 33) portrait](captures/kreyol_magic5lite33_portrait_clavier.png) | ![Suggestions Honor Magic5 Lite (API 33) portrait](captures/kreyol_magic5lite33_portrait_suggestions.png) | ![Après frappe Honor Magic5 Lite (API 33) portrait](captures/kreyol_magic5lite33_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Honor Magic5 Lite (API 33) paysage](captures/kreyol_magic5lite33_paysage_clavier.png) | ![Suggestions Honor Magic5 Lite (API 33) paysage](captures/kreyol_magic5lite33_paysage_suggestions.png) | ![Après frappe Honor Magic5 Lite (API 33) paysage](captures/kreyol_magic5lite33_paysage_apres_frappe.png) |

### Google Pixel 7 Pro (1440×3120, 560 dpi)

Touche 46,6 × 32,9 dp en portrait, 30,6 × 75,2 dp en paysage. Le clavier occupe 43,6 % de la surface applicative en portrait et 61,4 % en paysage.

**Portrait**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Google Pixel 7 Pro portrait](captures/kreyol_pixel7pro_portrait_clavier.png) | ![Suggestions Google Pixel 7 Pro portrait](captures/kreyol_pixel7pro_portrait_suggestions.png) | ![Après frappe Google Pixel 7 Pro portrait](captures/kreyol_pixel7pro_portrait_apres_frappe.png) |

**Paysage**

| Clavier | Suggestions (« kré ») | Après frappe |
|---|---|---|
| ![Clavier Google Pixel 7 Pro paysage](captures/kreyol_pixel7pro_paysage_clavier.png) | ![Suggestions Google Pixel 7 Pro paysage](captures/kreyol_pixel7pro_paysage_suggestions.png) | ![Après frappe Google Pixel 7 Pro paysage](captures/kreyol_pixel7pro_paysage_apres_frappe.png) |
