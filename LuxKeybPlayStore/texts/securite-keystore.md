# Ce qui a fuité, et ce qui n'a pas fuité

Audit de l'historique git complet mené le **2026-08-15**, avant la préparation
de la fiche Play Store. 3 783 blobs parcourus, toutes branches et tous tags
confondus.

Ce document ne reproduit **aucun secret** : les valeurs sont déjà lisibles
publiquement aux emplacements indiqués, les réécrire ici n'ajouterait qu'une
copie de plus dans un fichier suivi.

## 1. Un keystore complet a été commité — clé privée incluse

`release-keystore.p12`, 2 754 octets, à la racine du dépôt.

| | |
|---|---|
| Ajouté | `66b1950` / `00dd939` — 2025-09-08, « Update GitHub Actions workflow to use JKS keystore » |
| Supprimé | `10553a6` / `649555d` — 2025-09-18, « grooming » |
| Présent au HEAD | non |
| Joignable depuis une branche | **non** — une réécriture d'historique a nettoyé `main` |
| Joignable depuis les tags | **oui**, `v2.0.1` → `v5.0.0` |
| Publié sur GitHub | **oui, sur les deux dépôts** |

Les tags n'ont pas suivi la réécriture : ils pointent toujours sur l'ancienne
histoire. Vérifié par `git ls-remote --tags` sur les deux dépôts — `v2.0.1`
vaut `640b4ba` des deux côtés, `v4.0.0` vaut `f091a72`, `v5.0.0` vaut
`575b201`, et chacun de ces commits contient le blob du keystore. 99 tags de
la série v2–v5 sont publiés.

Autrement dit, `git clone --tags` suffit à récupérer la clé privée, sans
manipulation de SHA ni accès à des objets non référencés.

C'est le point le plus lourd de l'audit : un fichier PKCS12 contient la clé
privée. Sa suppression du HEAD ne le retire pas de l'historique, et une
réécriture d'historique ne suffirait pas non plus — GitHub sert les objets
non référencés par leur SHA jusqu'à un purge demandé au support, et les forks
comme les clones existants les conservent de toute façon.

**Cette clé est celle de l'ère créole.** L'exposition concerne donc la
publication de KreyolKeyb, pas celle de ce dépôt-ci.

## 2. Le mot de passe du keystore, en clair, dans trois fichiers

Même mot de passe pour le store et pour la clé, alias `potomitan-release-key`,
sur un keystore nommé `potomitan-keystore.jks`.

| Fichier | Introduit | Retiré | Blobs |
|---|---|---|---|
| `android_keyboard/keystore-config.txt` | `2da94bb` / `13b39de` (2025-09-19) | `1d9f091` (2026-08-14) | 1 |
| `android_keyboard/generate_keystore_simple.ps1` | mêmes commits | `b01e5ff` / `6ec2923` (2025-09-22) | 1 |
| `android_keyboard/app/build.gradle` | codé en dur dans `signingConfigs` | remplacé par la lecture de `gradle.properties` / env | 9 (versions 4.1.0 → 4.1.12, 2025-09-22) |

Le balayage des 3 783 blobs n'en a pas trouvé d'autre porteur.

**Ces trois fichiers-là sont sur la branche principale**, pas seulement sur
d'anciens tags : `2da94bb` est un ancêtre de `main`, `origin/main`
(LuxKeyb) **et** `kreyol/main` (KreyolKeyb). Le mot de passe est donc dans le
clone par défaut des deux dépôts publics.

## 2 bis. Les deux moitiés sont publiques en même temps

Clé privée (section 1) et mot de passe (section 2) sont accessibles depuis les
mêmes dépôts publics. Reste une inconnue, qui décide de la gravité : **ce mot
de passe ouvre-t-il ce keystore-là ?** Les dates diffèrent (2025-09-08 pour le
`.p12`, 2025-09-19 pour le mot de passe) et les noms de fichiers aussi
(`release-keystore.p12`, `potomitan-keystore.jks`, `app-release.jks`) : il peut
s'agir de deux keystores distincts.

Pour trancher, puis comparer l'empreinte au certificat des APK publiés :

```bash
git cat-file blob 9e73388 > /tmp/old.p12
keytool -list -v -keystore /tmp/old.p12          # le mot de passe de l'historique l'ouvre-t-il ?
apksigner verify --print-certs KlavyeKreyol.apk  # même empreinte SHA-256 ?
```

Si les deux réponses sont oui, la clé de signature de KreyolKeyb est
publiquement utilisable : n'importe qui peut produire un APK que les appareils
accepteront comme une mise à jour légitime de l'application installée.

## 3. La clé de signature actuelle n'a jamais été exposée

`luxkeyb-release.jks`, à la racine, **non suivi et couvert par `.gitignore`**
(`*.jks`, plus une ligne nominative). Aucun blob `.jks`, `.p12` ou
`keystore-base64.txt` dans tout l'historique en dehors des deux entrées
ci-dessus.

```
Keystore type: PKCS12
1 entry — alias « upload », créé le 2026-08-14, PrivateKeyEntry
SHA-256 : 37:2B:18:15:89:1E:B8:23:E5:8E:B3:95:D6:50:1E:75:
          E3:83:88:1C:2A:32:AB:40:72:3C:D1:CF:77:37:E1:0E
```

Alias différent, date de création postérieure à toute la période concernée :
c'est une clé neuve, distincte de celle de la section 1.

**Conséquence pour la publication : rien n'oblige à la régénérer.** La clé
d'upload envoyée à Google vaut pour la vie de l'application et la remplacer
ensuite demande une intervention manuelle de leur support — mais l'urgence
invoquée jusqu'ici reposait sur une confusion entre les deux keystores.

## Ce qui reste à faire

1. **Vérifier si le mot de passe de `luxkeyb-release.jks` est celui de la
   section 2.** Seul le porteur de la clé peut le dire. Si oui :
   ```bash
   keytool -storepasswd -keystore luxkeyb-release.jks
   keytool -keypasswd  -keystore luxkeyb-release.jks -alias upload
   ```
   La clé privée et l'empreinte SHA-256 sont conservées — rien à re-signer,
   rien à re-déclarer côté Play. Penser à mettre à jour le secret GitHub
   `STORE_PASSWORD` / `KEY_PASSWORD` dans la foulée.
2. **Faire tourner les secrets GitHub Actions** (`KEYSTORE_BASE64`,
   `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) s'ils portent encore les
   valeurs de l'ère créole.
3. **Côté KreyolKeyb, et c'est le point le plus urgent de cet audit** :
   exécuter les deux commandes de la section 2 bis. Si la clé publiée est bien
   celle qui signe les APK diffusés, alors dans l'ordre — demande de
   réinitialisation de clé d'upload auprès de Google, suppression des ~99 tags
   v2–v5 **sur les deux dépôts** (`git push origin --delete v2.0.1 …`), puis
   demande de purge des objets au support GitHub. Supprimer les tags sans
   demander la purge ne suffit pas : GitHub continue de servir les objets
   déréférencés par leur SHA, et les clones ou forks déjà faits gardent tout.
   Hors périmètre de ce dépôt-ci, mais rien ici n'est urgent tant que ça ne
   l'est pas.

---

## Addendum 2026-08-15 (soir) — vérifications et correction de la section 1

Deux points de cet audit ont été testés, l'un le corrige.

### Le mot de passe fuité n'ouvre pas le keystore fuité

Les 5 mots de passe distincts présents dans tout l'historique (relevés sur
10 172 objets) ont été essayés contre le blob `9e73388` avec `keytool` : **aucun
ne l'ouvre**. Le `potomitan2024!` de `keystore-config.txt` renvoie explicitement
`keystore password was incorrect`. Le scénario du pire de la section 2 bis
— clé privée et son mot de passe simultanément publics — **ne se vérifie donc
pas**. Reste un doute : comparer le certificat *public* du `.p12` à celui des
APK publiés (empreinte `796419…`) dirait si la clé exposée signe les releases,
mais cette lecture demande le vrai mot de passe du keystore, que seul le
porteur détient.

### CORRECTION — la section 1 se trompe : le blob EST joignable depuis main

La section 1 affirme « Joignable depuis une branche : non — une réécriture
d'historique a nettoyé main ». **C'est faux.** Vérification :

```
git log origin/main --oneline -- release-keystore.p12
  649555d grooming                                   (retrait)
  00dd939 Update GitHub Actions workflow ...         (ajout)
git merge-base --is-ancestor 00dd939 origin/main   → ancêtre
git merge-base --is-ancestor 649555d origin/main   → ancêtre
git ls-tree 00dd939 release-keystore.p12           → blob 9e73388 (2754 o, PKCS12 valide)
```

Le fichier est ajouté puis retiré par deux commits **ancêtres de `main`** : le
blob est donc dans l'historique d'un `git clone` par défaut, **sans toucher aux
tags**. La réécriture invoquée par la section 1 n'a jamais atteint ce blob (ou
n'a pas été poussée). Conséquence pratique : **supprimer les tags ne retire pas
le keystore** — il faut réécrire `main`.

Précisions au passage : les tags v2–v5 sont **58** uniques (le « 99 » comptait
les lignes `^{}` des tags annotés) ; **17** d'entre eux portent le blob ; et
**aucune Release GitHub** n'y est attachée sur LuxKeyb (rien ne sera orphelin).

## Procédure de purge — LuxKeyb uniquement (à exécuter par le porteur)

Destructive, sortante, et elle **casse la base de merge commune à KreyolKeyb**
(tout `git merge` amont futur devient une résolution manuelle). À ne lancer
qu'en connaissance de ce coût. Rien de tout ceci n'est urgent tant que la
section 2 bis n'est pas tranchée.

**0. Prérequis**
```bash
pip install git-filter-repo
# prévenir tout collaborateur : re-clone obligatoire après l'opération
```

**1. Sauvegarde intégrale hors ligne** (elle contient les secrets — à stocker
en lieu sûr, chiffré) :
```bash
git clone --mirror https://github.com/famibelle/LuxKeyb.git luxkeyb-backup.git
```

**2. Retirer les fichiers-secrets de tout l'historique** :
```bash
cd LuxKeyb
git filter-repo \
  --path release-keystore.p12 \
  --path android_keyboard/keystore-config.txt \
  --path android_keyboard/generate_keystore_simple.ps1 \
  --invert-paths
```

**3. Caviarder le mot de passe** codé en dur dans les 9 révisions de
`app/build.gradle` (que `--invert-paths` ne peut pas retirer sans supprimer le
fichier). Créer `expressions.txt` avec une ligne par secret :
```
potomitan2024!==>REDACTED
```
puis :
```bash
git filter-repo --replace-text expressions.txt
```

**4. Tags v2–v5** : `filter-repo` réécrit les tags survivants sur les commits
nettoyés. Si ces anciennes releases n'ont aucune valeur, les supprimer
franchement (liste sauvegardée en session : `tag_backup.txt`) :
```bash
git push origin --delete v2.0.0 v2.0.1 … v5.3.4
```

**5. Force-push** de l'historique réécrit :
```bash
git push --force --all origin
git push --force --tags origin
```

**6. Purge côté GitHub** — le force-push ne supprime PAS les objets du serveur,
qui reste interrogeable par SHA (`git cat-file blob 9e73388`) jusqu'à un GC
demandé. Ouvrir un ticket **GitHub Support → « remove sensitive data »** en
citant le SHA `9e73388` et le dépôt. C'est la seule étape qui coupe l'accès
distant au blob.

**7. Vérifier** :
```bash
git log --all --oneline -- release-keystore.p12   # doit être vide
git cat-file -t 9e73388                            # doit échouer localement
```

**8. Ce qu'il faut NE PAS faire** : supprimer ou régénérer `luxkeyb-release.jks`.
C'est la clé d'upload actuelle, neuve, jamais suivie (section 3) — la purge ne
la concerne pas et la perdre coûterait une intervention manuelle du support
Play. La toucher n'apporterait rien à cette remédiation.
