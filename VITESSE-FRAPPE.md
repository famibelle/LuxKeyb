# Temps par frappe sur le A21s, campagne du 2026-11-23

Compte rendu complet de la campagne de mesure du 2026-11-23, déclenchée par une
demande de tests de vitesse sur la version du dépôt (41.1.5), puis étendue en
comparaison avec la 37.3.0 parce que le chiffre de repère inscrit dans
`CLAUDE.md` (7,5 ms de médiane) s'est révélé irreproductible.

Ce fichier vit à la racine, et pas dans `android_keyboard/`, pour la même raison
que `ACCESSIBILITE.md` et `SWIPE-ESPACE.md` : `build-apk.yml` filtre sur
`android_keyboard/**`, donc une correction de typo ici ne déclenche pas un build
APK + AAB complet avec la chaîne Python. La campagne de 2026-09-05 sur la touche
`123`, elle, est restée dans `android_keyboard/PERF_CLAVIER.md`.

## Résumé

- **Aucune dérive entre la 37.3.0 et la 41.1.5.** Mêmes sonde, téléphone, phrase
  et cadence, les deux versions tiennent dans 1,8 ms de médiane (14,8 contre
  16,6 ms sur 96 frappes chacune), alors que l'écart entre deux séries d'une même
  version va de 14,5 à 16,8 ms. Elles sont indiscernables. Quatre mois, sept
  jeux, le carnet et la dictée en plus n'ont rien coûté à la frappe.
- **Repère utilisable, mots connus : médiane 15 à 17 ms, p90 27, dont 2,3 ms de
  puces.** Tout le reste est le moteur plus le saut de coroutine, donc il n'y a
  rien à gagner dans la couche vue.
- **Mot inconnu ou fautif : médiane 25 ms, p90 40, max 69.** C'est le repli
  Levenshtein, le chemin le plus lent du clavier, et il ne se déclenche que là.
- **Le 7,5 ms de `CLAUDE.md` n'est pas reproductible** : la 37.3.0 elle-même,
  reconstruite depuis son tag avec la sonde identique, lit 14,8 ms. Le chiffre
  publié mesurait quelque chose de plus étroit, très probablement le seul
  classement à l'intérieur de la coroutine, sans l'aller-retour de dispatch ni la
  pose des puces.
- **Le délai mesuré est celui de la barre, pas de la lettre.** Voir plus bas.

## Ce que la sonde mesure, et ce qu'elle ne mesure pas

Trois points de mesure, du même appui :

| Instant | Emplacement | Sens |
|---|---|---|
| `debut` | `InputProcessor.processKeyPress`, première ligne | l'appui entre dans l'application |
| `calcul` | `displaySuggestions` / `displayBilingualSuggestions`, première ligne | le moteur a rendu sa réponse, la vue va être remplie |
| `fin` | même méthode, dernière ligne | les puces sont posées dans l'arbre de vues |

Donc **moteur** = `calcul - debut` (classement, plus le saut de fil vers
`Dispatchers.Default` et le retour sur le fil principal), **puces** =
`fin - calcul`, **total** = `fin - debut`.

Deux limites à garder en tête :

- **Ce n'est pas la latence de la lettre.** `handleCharacterInput` appelle
  `processorListener?.onWordChanged(currentWord)`, qui ne fait que lancer une
  coroutine, puis `inputConnection.commitText(character, 1)` suit immédiatement.
  La lettre s'affiche donc avant la fin de la mesure. Ce qui est chronométré ici
  est le temps que met **la barre de suggestions** à se mettre à jour.
- **Le total s'arrête à la pose des puces dans l'arbre de vues**, pas à leur
  apparition à l'écran. Il manque la frame de rendu (environ 16,7 ms de budget à
  60 Hz sur cet écran). Pour le coût de rendu lui-même, c'est `framestats` qu'il
  faut, méthode de `android_keyboard/PERF_CLAVIER.md`.
- Une touche sans calcul de suggestion (`123`, `⇧`, emoji) est **invisible** pour
  cette sonde : `fin` ne vient jamais. Le coût de `123` est celui de l'autre
  campagne.

## Contexte et méthode

| Paramètre | Valeur |
|---|---|
| Appareil | Samsung Galaxy A21s (SM-A217F), Android 12, 720 x 1600, 60 Hz |
| Liaison | ADB sans fil, `192.168.1.37:<port>` (le port change à chaque reconnexion) |
| Builds | **release** dans les deux cas, signés par une clé jetable |
| Versions | `41.1.5` (arbre de travail) et `37.3.0` (`git archive v37.3.0`) |
| Champ de saisie | champ d'essai de l'accueil (`champTest`, tag `onboarding_test_field`) |
| Phrase, mots connus | `Ech hunn op der Schueberfouer Gromperekichelcher giess` |
| Phrase, mots fautifs | `Schuebrfouer Grompereckichelcher iessn gudden owenn zesummen` |
| Cadence | un `input tap` toutes les 250 ms |
| Sondes retenues par série | 48 (mots connus), 55 (mots fautifs) |
| Journal | `println("CHRONO …")` relu par `logcat -d --pid=<pid>` |

**Pourquoi release.** Un build debug est environ 3 fois plus lent, ses chiffres
ne valent rien. Mais ProGuard retire `android.util.Log` (`-assumenosideeffects`),
donc une sonde en `Log.d` disparaît du dex : la sonde écrit en `println`, qui
sort sous l'étiquette `System.out`.

**Travail sur copie.** Tout s'est fait sur une copie de `android_keyboard` dans
le répertoire de travail temporaire de la session, jamais dans l'arbre partagé :
une autre session y avait du travail non committé. `git status --short` a été
vérifié identique au début et à la fin.

## Résultats

### Mots connus, 41.1.5, sonde simple (lettre vers barre)

| Série | n | médiane | moyenne | p90 | max |
|---|---|---|---|---|---|
| run1 | 48 | 15,6 | 18,3 | 27,4 | 48,7 |
| run2 | 48 | 15,4 | 17,3 | 25,6 | 27,4 |
| run3 | 48 | 15,5 | 17,5 | 26,5 | 29,4 |

Trois séries consécutives, écart de médiane de 0,2 ms : le banc est stable.

### Mots fautifs, 41.1.5

| Série | n | médiane | moyenne | p90 | max |
|---|---|---|---|---|---|
| fautes | 55 | 25,7 | 23,6 | 40,1 | 69,0 |

Soit **+10 ms de médiane** sur un mot que le dictionnaire ne reconnaît pas. Le
coût n'est pas réparti : les premières lettres d'un mot fautif restent à 7 à
10 ms, et la facture tombe dès que le préfixe ne complète plus rien (`u` de
`Schuebrfouer` : 26,2 ms).

### Décomposition moteur / puces, double sonde

| Série | n | moteur médiane | puces médiane | total médiane | total p90 |
|---|---|---|---|---|---|
| 41.1.5, mots connus (d1) | 48 | 14,8 | 2,4 | 16,8 | 27,6 |
| 41.1.5, mots fautifs (d2) | 55 | 22,9 | 2,3 | 24,8 | 39,9 |

**Les puces coûtent 2,3 ms**, p90 3,1, max 4,0, et ne bougent pas d'une
phrase à l'autre. Il n'y a donc rien à optimiser dans la couche vue : la
suppression totale du coût d'affichage gagnerait 14 % du délai mesuré.

### A/B 37.3.0 contre 41.1.5, sonde identique

Deux séries établies de chacune des deux versions, mises bout à bout (96
frappes), installées en alternance sur le même téléphone :

| Régime établi, n = 96 | moteur | puces | **total médiane** | moyenne | p90 | max | dans une frame |
|---|---|---|---|---|---|---|---|
| 37.3.0 | 12,3 | 2,3 | **14,8** | 17,5 | 26,6 | 38,4 | 57 % |
| 41.1.5 | 13,7 | 2,1 | **16,6** | 18,1 | 27,2 | 46,3 | 51 % |

« Dans une frame » = part des frappes dont le total tient sous 16,7 ms.

Série par série, pour montrer l'étalement réel :

| Série | ordre | total médiane |
|---|---|---|
| 37.3.0 a | première après installation | 20,5 |
| 37.3.0 b | | 15,4 |
| 37.3.0 c | | 14,5 |
| 41.1.5 d1 | | 16,8 |
| 41.1.5 b | première après réinstallation | 21,1 |
| 41.1.5 c | | 16,1 |

Les deux médianes établies se tiennent dans 1,8 ms, l'étalement intra-version
couvre 2,3 ms : **les deux versions sont équivalentes**, et toute conclusion plus
fine que cela serait du bruit.

## Le repère de 7,5 ms

`CLAUDE.md` portait « On the A21s (release, 37.3.0) a keystroke costs median
7,5 ms, p90 20, max 25 ». La 37.3.0 reconstruite depuis son tag, mesurée avec la
sonde décrite ici, donne 14,5 à 15,4 ms de médiane en régime établi. Les deux
chiffres ne mesurent donc pas la même chose, et l'écart n'est pas une régression.

Hypothèse la plus probable : l'ancien chiffre chronométrait le classement seul à
l'intérieur de la coroutine, sans l'aller-retour `Dispatchers.Default` ni la pose
des puces. Il n'a pas été retrouvé de sonde d'époque pour le confirmer.

**La ligne de `CLAUDE.md` a été réécrite le 2026-11-23** : elle garde la mention
de l'ancien chiffre en le marquant irreproductible, et donne les repères de cette
campagne avec leur date et la définition de la sonde.

## Pièges rencontrés

- **La première série après une installation lit environ 5 ms de trop** (ART
  compile encore). 37.3.0 : 20,5 puis 15,4 et 14,5. 41.1.5 : 21,1 puis 16,1.
  **Jeter la première série**, toujours.
- **Le délai mesuré est celui de la barre, pas de la lettre** (voir plus haut).
  C'est le piège d'interprétation principal de ce banc.
- **La visite guidée détourne les frappes.** L'étape 9/9 (« Pour la revoir »)
  était ouverte : les retours arrière de nettoyage sont allés dans sa bulle et la
  série a rendu zéro sonde. La fermer par « Terminer » avant de commencer.
- **Un tap au milieu du champ d'essai place le curseur au milieu du texte**, et
  les retours arrière effacent les mauvaises lettres. Toucher la fin de la
  dernière ligne avant de vider. `input keycombination 113 29` (Ctrl+A) ne
  fonctionne pas sur ce Samsung.
- **Le champ d'essai se cache quand les trois étapes de réglage sont faites** :
  la carte se replie. Il faut toucher « Clavier installé › », puis « Voir les
  3 étapes », puis « Essayer le clavier › » pour le faire réapparaître.
- **Installer par-dessus une signature différente échoue**
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) : désinstaller d'abord. Le carnet y
  passe (125 cartes perdues ici, ce qui est permis sur ce téléphone de test).
- **Une installation neuve n'active pas l'IME** : `ime set` répond « Unknown
  input method … cannot be selected ». Il faut `ime enable <id>` avant
  `ime set <id>`.
- `adb: device offline` arrive après une veille : refaire `adb connect` sur le
  port relu dans l'écran de débogage sans fil. **Ne jamais toucher au wifi.**
- Gradle exige JVM 17 : `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.

## Démarrage à froid et mémoire, pour mémoire

Relevés le même jour, build release 41.1.5 propre du dépôt :

- **Lancement à froid de l'activité** (`am start -S -W`) : 524 à 820 ms de
  `TotalTime` selon le lancement, mesuré après le premier (qui compile).
- **Empreinte du processus** : environ 182 MB de PSS avec l'activité d'accueil
  ouverte (`dumpsys meminfo`). Le processus IME seul est bien plus léger : c'est
  l'activité, ses assets et ses cartes qui pèsent.

Ces deux chiffres sont des ordres de grandeur, pris une fois, sans série.

## Ce qui n'a pas été mesuré

À ne pas déduire des chiffres ci-dessus :

- **La fluidité du carnet et des cartes.** Le carnet était vide après la
  réinstallation, et un build release n'est pas `debuggable` donc `run-as` est
  impossible pour y injecter des cartes. Il faudrait un build debug avec la sonde
  Choreographer, méthode de la skill `mesurerQualite`. Les derniers chiffres
  connus sont ceux de la 41.1.4 (tout à 30/60 i/s sauf la première carte
  ouverte, 0,6 s d'attente).
- **La touche `123` / `ABC`.** Elle ne déclenche aucun calcul de suggestion, donc
  elle est invisible pour cette sonde. Dernier chiffre connu : 45 à 55 ms de
  travail en 12.0.1, ramené à environ 32 ms en 17.0.1
  (`android_keyboard/PERF_CLAVIER.md`).
- **Le coût de la frame de rendu** des puces, pour la raison dite plus haut.
- **La dictée**, la recherche du Wierderbuch (qui referme le clavier) et les
  jeux.

## Annexe A : la sonde

Fichier ajouté aux deux copies, `app/src/main/java/com/example/kreyolkeyboard/ChronoFrappe.kt`,
et **retiré du build livré**. À ne jamais committer.

```kotlin
object ChronoFrappe {
    @Volatile private var t0 = 0L
    @Volatile private var tCalc = 0L
    @Volatile private var touche = ""
    fun debut(key: String) { t0 = System.nanoTime(); tCalc = 0L; touche = key }
    fun calcul() { if (t0 != 0L) tCalc = System.nanoTime() }
    fun fin(n: Int) {
        val t = t0; if (t == 0L) return
        t0 = 0L
        val fin = System.nanoTime()
        val calc = if (tCalc != 0L) (tCalc - t) / 1000 else -1
        println("CHRONO touche=$touche calc=$calc total=${(fin - t) / 1000} n=$n")
    }
}
```

Trois insertions, une ligne chacune :

| Fichier | Méthode | Ligne posée |
|---|---|---|
| `InputProcessor.kt` | `processKeyPress`, en tête | `ChronoFrappe.debut(key)` |
| `KreyolInputMethodServiceRefactored.kt` | `displaySuggestions`, en tête | `ChronoFrappe.calcul()` |
| idem | `displaySuggestions`, en queue | `ChronoFrappe.fin(suggestions.size)` |
| idem | `displayBilingualSuggestions`, en tête | `ChronoFrappe.calcul()` |
| idem | `displayBilingualSuggestions`, en queue | `ChronoFrappe.fin(suggestions.size)` |

Les deux méthodes d'affichage sont sondées parce que le clavier passe par l'une
ou l'autre selon que la rangée française est active.

## Annexe B : construire et installer un build de mesure

```bash
# clé jetable, hors du dépôt
keytool -genkeypair -keystore /tmp/.../mesure.jks -storepass mesure2026 \
        -alias mesure -keypass mesure2026 -keyalg RSA -keysize 2048 \
        -validity 3650 -dname "CN=mesure"

cd <copie>/android_keyboard
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
KEYSTORE_FILE=/tmp/.../mesure.jks STORE_PASSWORD=mesure2026 \
KEY_ALIAS=mesure KEY_PASSWORD=mesure2026 \
  ./gradlew assembleRelease
# verifier dans le journal : "Production signing enabled", sinon c'est un build
# signe en debug et il ne s'installera pas par-dessus l'autre.

A21S=192.168.1.37:<port>
IME=com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored
adb -s $A21S uninstall com.potomitan.luxkeyboard
adb -s $A21S install app/build/outputs/apk/release/*.apk
adb -s $A21S shell ime enable $IME && adb -s $A21S shell ime set $IME
adb -s $A21S shell am start -S -W -n com.potomitan.luxkeyboard/com.example.kreyolkeyboard.SettingsActivity
```

Pour la 37.3.0 : `git archive v37.3.0 | tar -x -C <dossier>`, puis y copier la
sonde et les cinq insertions, et construire de la même façon.

## Annexe C : la carte des touches

Les coordonnées ne sont pas codées en dur, elles sont relues sur l'appareil : le
clavier est construit par programme, et la disposition par défaut (Suisse
allemand, 11 touches par rangée) a changé en cours de projet.

```python
"""Carte des touches du clavier LuxKeyb lu sur l'appareil (uiautomator --windows)."""
import re, subprocess, sys, json

SERIAL = sys.argv[1] if len(sys.argv) > 1 else "192.168.1.37:40377"

def adb(*a, **kw):
    return subprocess.run(["adb", "-s", SERIAL, *a], capture_output=True,
                          text=True, stdin=subprocess.DEVNULL, **kw).stdout

def dump():
    adb("shell", "uiautomator", "dump", "--windows", "/sdcard/w.xml")
    return adb("shell", "cat", "/sdcard/w.xml")

def touches(xml):
    carte = {}
    for n in re.findall(r"<node[^>]*/?>", xml):
        t = re.search(r'text="([^"]*)"', n)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        p = re.search(r'package="([^"]*)"', n)
        if not (t and b and p) or p.group(1) != "com.potomitan.luxkeyboard":
            continue
        txt = t.group(1)
        if len(txt) > 2 and txt not in ("123",):
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 - x1 < 20 or y2 - y1 < 20:
            continue
        carte[txt] = ((x1 + x2) // 2, (y1 + y2) // 2)
    return carte

if __name__ == "__main__":
    c = touches(dump())
    print(json.dumps(c, ensure_ascii=False, indent=1))
    print(len(c), "touches", file=sys.stderr)
```

`uiautomator dump` sans `--windows` ne voit pas l'IME. Au JSON produit il faut
ajouter à la main les touches sans texte lisible, relevées dans le même dump par
leur `content-desc` : `"maj"`, `"eff"` (retour arrière) et `" "` (espace).

## Annexe D : le banc

```python
"""Banc de vitesse, double sonde : moteur (calc) et puces posees (total)."""
import json, os, re, statistics as st, subprocess, sys, time
SP = os.path.dirname(os.path.abspath(__file__))
SERIAL = os.environ.get("A21S", "192.168.1.37:40377")
T = json.load(open(os.path.join(SP, "touches.json")))
CADENCE = float(os.environ.get("CADENCE", "0.25"))

def adb(*a):
    return subprocess.run(["adb", "-s", SERIAL, *a], capture_output=True,
                          text=True, stdin=subprocess.DEVNULL).stdout

def tap(n):
    x, y = T[n]
    adb("shell", "input", "tap", str(x), str(y))

def stats(v):
    if not v: return None
    s = sorted(v)
    return {"n": len(s), "mediane": round(st.median(s), 1), "moyenne": round(st.fmean(s), 1),
            "p90": round(s[max(0, int(len(s)*0.9)-1)], 1), "max": round(s[-1], 1)}

def main():
    phrase, etiq = sys.argv[1], sys.argv[2]
    foc = re.search(r"mCurrentFocus=.*", adb("shell", "dumpsys", "window")).group(0)
    if "com.potomitan.luxkeyboard" not in foc:
        sys.exit("premier plan inattendu : " + foc)
    pid = adb("shell", "pidof", "com.potomitan.luxkeyboard").split()[0]
    adb("logcat", "-c")
    for c in phrase:
        if c == " ": tap(" ")
        elif c.isupper(): tap("maj"); time.sleep(0.3); tap(c.lower())
        else: tap(c.lower())
        time.sleep(CADENCE)
    time.sleep(2.5)
    brut = adb("logcat", "-d", "--pid=" + pid)
    m = re.findall(r"CHRONO touche=(\S*) calc=(-?\d+) total=(\d+) n=(\d+)", brut)
    lignes = [(t, int(c)/1000.0, int(tt)/1000.0) for t, c, tt, _ in m if len(t) == 1 and t.isalpha()]
    out = {"etiquette": etiq, "phrase": phrase, "n": len(lignes),
           "moteur_ms": stats([c for _, c, _ in lignes if c >= 0]),
           "total_ms": stats([tt for _, _, tt in lignes]),
           "puces_ms": stats([tt - c for _, c, tt in lignes if c >= 0]),
           "detail": [[t, round(c, 1), round(tt, 1)] for t, c, tt in lignes]}
    print(json.dumps(out, ensure_ascii=False, indent=1))

main()
```

Le contrôle du premier plan en tête n'est pas décoratif : sans lui, une série de
`input tap` part dans l'application qui a le focus, et derrière l'accueil se
trouvent les options de développement.

Déroulé d'une série :

1. vérifier le premier plan, fermer la visite guidée si une bulle est ouverte ;
2. toucher la fin de la dernière ligne du champ d'essai, puis vider par 60
   retours arrière ;
3. lancer le banc, attendre 2,5 s après la dernière frappe pour que le dernier
   calcul soit journalisé ;
4. **jeter la première série après une installation**, en lancer au moins deux
   autres.

## À refaire autrement la prochaine fois

- La sonde pourrait porter un troisième point après la frame de rendu
  (`view.post {}` ou `Choreographer.postFrameCallback`), pour couvrir enfin le
  délai complet jusqu'à l'écran.
- Sonder aussi le chemin sans suggestion (`123`, `⇧`) demanderait un point de
  mesure dans `onModeChanged`, ce qui réunirait les deux campagnes dans un seul
  banc.
- Mesurer la fluidité du carnet en même temps exige un build debug : les deux
  mesures ne peuvent pas se faire sur la même installation.
