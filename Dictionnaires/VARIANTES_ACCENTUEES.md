# Variantes accentuées du dictionnaire : tableau à arbitrer

`variantes_accentuees.csv` liste les formes du dictionnaire qui deviennent
identiques une fois les accents retirés. **249 groupes, 531 formes**, sur les
5 296 formes du dictionnaire.

Ce tableau ne tranche rien. Il est destiné à être annoté par des professeurs
de créole, parce que la question relève de la norme d'écriture et non de la
technique.

## Pourquoi cet arbitrage est nécessaire

Le dictionnaire est construit à partir de textes créoles d'époques et de
plumes différentes, dont certaines n'accentuent pas. Deux situations se
mélangent donc dans ces groupes :

- **Des paires minimales légitimes**, que le créole distingue précisément par
  l'accent : `sé` et `sè`, `té` et `tè`, `pé` et `pè`, `pyé` et `pyè`. Le
  clavier doit proposer les deux, et c'est même l'un de ses arguments.
- **Des graphies non normées héritées du corpus** : `bel` à côté de `bèl`,
  `zot` à côté de `zòt`, `fe` à côté de `fè`. Le clavier devrait les
  reconnaître sans jamais les proposer.

L'enjeu est concret : en langue vivante régionale, l'orthographe est évaluée.
Un clavier qui propose `bel` valide la faute au moment où l'élève l'écrit.

## Comment remplir le tableau

Le fichier s'ouvre dans un tableur. Une ligne par forme, les groupes les plus
fréquents en premier, parce que ce sont ceux dont l'arbitrage change le plus
de choses à l'usage.

| Colonne | Contenu |
|---|---|
| `groupe` | La forme une fois les accents retirés, qui sert de clé de regroupement |
| `forme` | La graphie telle qu'elle figure au dictionnaire |
| `frequence` | Nombre d'occurrences dans le corpus |
| `part_du_groupe` | Poids de cette graphie parmi celles du groupe |
| `sans_diacritique` | `oui` si la forme ne porte aucun accent. **Indication, pas verdict** |
| `decision` | À remplir |
| `commentaire` | Libre |

**Trois valeurs possibles dans `decision` :**

- `proposer` : forme normée, le clavier la suggère
- `reconnaitre` : forme non normée, le clavier la comprend mais ne la suggère
  jamais
- `retirer` : forme à sortir du dictionnaire, coquille ou mot qui n'a rien à
  y faire

Il n'est pas nécessaire de traiter les 249 groupes d'un coup. Les cinquante
premiers couvrent l'essentiel des occurrences.

## Un piège à connaître

La colonne `sans_diacritique` ne dit pas qu'une forme est fautive. Le
troisième groupe du tableau en donne l'exemple : `kon`, sans accent, totalise
214 occurrences, contre 5 à `kòn`. Les deux sont des mots distincts. Une
lecture mécanique de cette colonne conduirait à écarter la forme la plus
courante du groupe.

## Ce qui se passe ensuite

Le tableau annoté est réinjecté dans le pipeline : les formes marquées
`reconnaitre` restent dans le dictionnaire avec un indicateur qui les exclut
des suggestions, celles marquées `retirer` en sortent, les autres ne bougent
pas. Cette mécanique reste à implémenter, elle attend l'arbitrage.

Un point technique connexe sera traité au même moment : le chemin contextuel
des n-grammes (`SuggestionEngine.kt:422`) compare encore sans normaliser les
accents, alors que le chemin principal a été corrigé. En contexte, taper `fe`
ne fait donc pas remonter `fè` par cette voie.

## Regénérer le tableau

Le dictionnaire est reconstruit à chaque passage du pipeline, et les groupes
peuvent changer. Pour reproduire le fichier :

```bash
python3 Dictionnaires/lister_variantes_accentuees.py
```

Le script relit `creole_dict.json` et réécrit le CSV. **Il écrase les
annotations existantes** : travailler sur une copie du fichier annoté.
