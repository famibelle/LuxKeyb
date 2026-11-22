---
name: publierVersion
description: Publier une version du Lëtzebuergesch Clavier (LuxKeyb) de bout en bout, c'est-à-dire les tests, l'entrée du CHANGELOG, le versionCode, les commits, le tag, le suivi du build GitHub, les notes de release, la page Nouveautés traduite et le rappel de l'AAB pour la Play Console. À utiliser dès que Medhi dit « commit, tag et pousse », « publie une version », « fais un patch », « sors la X.Y.Z », « release », ou demande de corriger les notes d'une release ou de mettre à jour la page Nouveautés.
---

# Publier une version

On ne committe et on ne pousse **que quand Medhi le demande**. « Commit et pousse » ne veut pas dire « tag » : la version se fait quand il dit « publie », « tag » ou « patch ».

## 1. Avant de toucher à git

- **Être sur `main`.** Une autre session travaille parfois dans le même dossier et change de branche : `git branch --show-current` juste avant chaque commit et avant le tag, pas seulement en début de session.
- **Fichiers qui ne sont pas à nous.** Une autre session laisse souvent `SuggestionEngine.kt` modifié et `BancEfficaciteTest.kt` non suivi. Ne jamais les ajouter : `git add` fichier par fichier, jamais `git add -A` ni `git commit -a`. Vérifier `git diff --cached --stat` avant de committer.
- **`CLAUDE.md` est local**, ignoré par git : ne jamais le forcer dans un commit.
- **Tests** : depuis `android_keyboard/`, `./gradlew testDebugUnitTest`. Ajouter `--rerun` si un asset a été régénéré : les tests d'assets lisent `src/main/assets/` par `File`, Gradle ne le voit pas et rejoue un ancien résultat vert (c'est ce qui a fait échouer deux fois le tag 37.2.0 en CI).
- JDK 17 : `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` si le shell est resté sur le 11.

## 2. La version

**Numéro** : un patch (Z) pour une correction ou une petite amélioration, un mineur (Y) pour une fonction nouvelle. Le majeur ne bouge que sur décision de Medhi.

**`android_keyboard/CHANGELOG.md`**, une entrée en tête, juste sous l'avertissement sur la base commune :

```markdown
## [37.7.6] - 2026-10-21

### ✨ Améliorations

- **Ce que l'utilisateur gagne, en gras** : la suite en une phrase.

### 🐛 Corrections

- **Ce qui marche maintenant** : ce qui n'allait pas, dit simplement.
```

- Écrit pour l'utilisatrice de référence (la maman de Medhi) : **ce qu'elle voit ou gagne**, jamais le nom d'une classe, d'un fichier ou d'un algorithme. Pas de corpus, n-grammes, LOD, Bloom, IME.
- Pas de tiret cadratin ; espaces insécables dans les guillemets français et avant « ? », « ! », « : ».
- Sections habituelles : `✨ Améliorations` ou `✨ Nouveautés`, `🐛 Corrections`, `⚡ Rapidité`, `🔒 Confidentialité`. Sur la page Nouveautés, le gras de tête de chaque puce devient le titre de sa carte : toujours commencer une puce par `**…**`.
- La page traduit un titre de section soit par la traduction de la version (`titre`), soit par le glossaire `_sections` des fichiers de traduction. Les traductions écrites jusqu'ici donnent toujours `titre` : faire pareil.
- Le CHANGELOG est la source de la release GitHub **et** de la page Nouveautés : ce qu'on y écrit est public deux fois.

**`android_keyboard/app/build.gradle`** : `versionName "X.Y.Z"` et `versionCode` = X × 10000 + Y × 100 + Z (37.7.6 → `370706`). Les deux, toujours.

## 3. Commits et tag

1. **Le commit de la fonction**, s'il n'est pas déjà fait : `feat(zone): …`, `fix(zone): …`, `docs(zone): …`, message en français, corps qui dit le pourquoi et comment ça a été vérifié (« Vérifié sur le A21s »).
2. **Le commit de version**, qui ne touche que `CHANGELOG.md` et `build.gradle` : `chore(release): version X.Y.Z`.
3. **Le tag**, léger, comme tous les précédents : `git tag vX.Y.Z`.
4. **Pousser** : `git push origin main` puis `git push origin vX.Y.Z`.

Chaque message se termine par les lignes d'attribution que donne la session (`Co-Authored-By`, `Claude-Session`).

### Un tag à la fois, c'est la règle qui coûte le plus cher

Le job `create-release` de `.github/workflows/build-apk.yml` fait `checkout` de **`ref: main`**, pas du tag, puis exige que la première entrée du CHANGELOG soit celle du tag poussé. Si on pousse la 37.7.3 alors que le build de la 37.7.2 tourne encore, le CHANGELOG de `main` dit déjà 37.7.3 quand la 37.7.2 arrive à l'étape de release : elle échoue, sans release GitHub. C'est arrivé aux 37.7.2, 37.7.3 et 37.7.4.

Donc :
- pousser un tag, **attendre que son run soit terminé et vert**, puis seulement préparer la version suivante ;
- si Medhi veut plusieurs patchs d'affilée, les committer et les tagger quand même, mais pousser les tags un par un, chacun après la fin du run du précédent ; ou regrouper en une seule version.
- La vraie correction (lire le CHANGELOG du tag, `ref: ${{ github.ref }}`) n'est pas faite : c'est une décision de Medhi, ne pas modifier le workflow sans son accord.

### Suivre le build

Lancer en arrière-plan et être prévenu à la fin (le run met une quinzaine de minutes) :

```bash
sleep 15; r=$(gh run list -L 5 | grep "vX.Y.Z" | awk -F'\t' '{print $7}' | head -1); echo "run=$r"
until s=$(gh run view $r --json status,conclusion -q '.status+" "+.conclusion'); [[ $s == completed* ]]; do sleep 60; done
echo "$s"; gh release view vX.Y.Z --json assets -q '.assets[].name'
```

Une release réussie porte `LetzebuergeschClavier-Release-vX.Y.Z.aab` et `.apk`, les mêmes en Debug, et `LetzebuergeschClavier-latest.apk`. En cas d'échec : `gh run view <id> --log-failed | tail -50`, et le dire à Medhi avec la sortie.

Rappel : un commit qui ne touche que `docs/` ne lance aucun build (le workflow filtre sur `android_keyboard/`, `Dictionnaires/` et `.github/`).

## 4. Corriger les notes d'une release

Le `gh` installé n'a pas de `release edit`. Passer par l'API :

```bash
id=$(gh api repos/famibelle/LuxKeyb/releases/tags/vX.Y.Z -q .id)
gh api -X PATCH repos/famibelle/LuxKeyb/releases/$id -f body="$(cat notes.md)"
```

Utile quand Medhi veut que la dernière version résume plusieurs patchs, notamment ceux restés sans release.

## 5. La page Nouveautés

Elle se fait **après** la release, par un commit `docs(nouveautes): …` poussé **directement sur `main`**, sans branche ni PR.

1. **Image** (facultative mais attendue pour une nouveauté visible) dans `docs/stats/nouveautes-medias.json`, clé = la version :
   ```json
   "37.7.5": {"images": [{"image": "Screenshots/lux_xxx.gif", "alt": "…", "legende": "…"}]}
   ```
   - `alt` décrit **chaque écran**, numéroté, pour qui ne voit pas l'image ; `legende` est une phrase.
   - Chemins relatifs à `docs/`. Les GIF vivent dans `docs/Screenshots/`.
   - **Toute carte visible porte le filigrane « LuxKeyb »** (`filigrane()` de `docs/scripts/filigrane_cartes.py`).
2. **Traductions** dans `docs/stats/traductions/nouveautes-{en,de,pt}.json`, une clé par version : `intro`, `sections` (même nombre de sections et de points, dans le même ordre que le français), et le texte des `images` (`alt`, `legende`). Une version absente ou de forme différente s'affiche en français, avec un avertissement : ce n'est pas une erreur, mais ce n'est pas fini.
3. `python docs/scripts/generate_nouveautes.py` : il écrit `docs/stats/nouveautes*.json` et `historique.json`.
4. Vérifier le rendu en local avec Chrome sans interface, en français et dans une langue traduite.
5. Les traductions lb, de et pt sont écrites par Claude : le dire à Medhi.

## 6. Après

- **Rappeler à Medhi d'envoyer l'AAB** (`LetzebuergeschClavier-Release-vX.Y.Z.aab`, sur la release GitHub) sur la Play Console. Claude n'y a pas accès.
- Mettre à jour `CLAUDE.md` (local) si le fonctionnement a changé, et la mémoire si une décision de Medhi a été prise en route.

## Liste de contrôle

- [ ] Sur `main`, juste avant le commit et le tag.
- [ ] Ni `SuggestionEngine.kt` ni `BancEfficaciteTest.kt` dans `git diff --cached --stat` (s'ils appartiennent à une autre session).
- [ ] Tests verts (`--rerun` si un asset a bougé).
- [ ] CHANGELOG en mots de tous les jours ; `versionName` et `versionCode` à jour.
- [ ] Un seul tag en vol ; run suivi jusqu'au bout, release vérifiée.
- [ ] Page Nouveautés : image, `alt`, traductions en/de/pt, cartes filigranées.
- [ ] Rappel de l'AAB.
