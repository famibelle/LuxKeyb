# Corpus sources et attribution

Le dictionnaire et le modèle n-grammes livrés dans l'application
(`android_keyboard/app/src/main/assets/luxemburgish_dict.json` et
`luxemburgish_ngrams.json`) sont dérivés de deux jeux de données publics.
Les deux sont sous licence Creative Commons et **exigent la citation de leurs
auteurs**. Cette page remplit cette obligation ; les crédits sont également
affichés dans l'application, onglet « À propos », carte « Sources ».

## LuxAlign

Phrases d'articles de presse publiés par **RTL.lu**, appariées à leurs
équivalents anglais et français.

- Dépôt : <https://huggingface.co/datasets/fredxlpy/LuxAlign> (version `v3`)
- Auteurs : Fred Philippy, Siwen Guo, Jacques Klein, Tegawendé F. Bissyandé
- Licence : **CC BY-NC 4.0** — attribution obligatoire, usage non commercial
- Apport : 180 342 phrases, ~3,1 M de mots, prose suivie de 17 mots en moyenne.
  C'est la source du volume lexical et de la quasi-totalité des n-grammes.

```bibtex
@inproceedings{philippy-etal-2025-luxembedder,
    title     = "{L}ux{E}mbedder: A Cross-Lingual Approach to Enhanced {L}uxembourgish Sentence Embeddings",
    author    = "Philippy, Fred and Guo, Siwen and Klein, Jacques and Bissyande, Tegawende",
    booktitle = "Proceedings of the 31st International Conference on Computational Linguistics",
    year      = "2025",
    address   = "Abu Dhabi, UAE",
    publisher = "Association for Computational Linguistics",
    url       = "https://aclanthology.org/2025.coling-main.753/",
    pages     = "11369--11379"
}
```

## LETZ

Phrases d'exemple du **Lëtzebuerger Online Dictionnaire** (<https://lod.lu>),
dont les données d'origine sont publiées en CC0 par le Luxembourgish Open Data
Platform.

- Dépôt : <https://huggingface.co/datasets/fredxlpy/LETZ> (configs `LETZ-SYN`, `LETZ-WoT`)
- Auteurs : Fred Philippy, Shohreh Haddadan, Siwen Guo
- Licence : **CC BY 4.0** — attribution obligatoire, usage commercial autorisé
- Apport : 5 862 phrases, ~53 k mots. Cent fois plus petit que LuxAlign, mais
  seul porteur du registre quotidien — deuxième personne, famille, objets
  courants. Ce sont les mots qu'on tape sur un téléphone et que la presse
  écrite n'emploie jamais.

```bibtex
@inproceedings{philippy-etal-2024-forget,
    title     = "Forget {NLI}, Use a Dictionary: Zero-Shot Topic Classification for Low-Resource Languages with Application to {L}uxembourgish",
    author    = "Philippy, Fred and Haddadan, Shohreh and Guo, Siwen",
    booktitle = "Proceedings of the 3rd Annual Meeting of the Special Interest Group on Under-resourced Languages @ LREC-COLING 2024",
    year      = "2024",
    address   = "Torino, Italia",
    publisher = "ELRA and ICCL",
    url       = "https://aclanthology.org/2024.sigul-1.13",
    pages     = "97--104"
}
```

## Conséquence sur la licence du projet

Le code de l'application et les données qu'elle embarque **ne sont pas sous la
même licence**, et c'est délibéré :

- le **code** reste sous la licence du dépôt ;
- les **assets de dictionnaire** (`luxemburgish_dict.json`,
  `luxemburgish_ngrams.json`) sont des œuvres dérivées de LuxAlign et héritent
  donc de sa clause **NonCommercial**.

« Open source » ne lève pas la clause NC : les licences approuvées par l'OSI
autorisent explicitement l'usage commercial, ce que CC BY-NC interdit. Une
distribution payante de l'application, ou une réutilisation commerciale des
fichiers de dictionnaire, demande l'accord préalable des ayants droit — le
contact indiqué par les auteurs pour les données RTL est <ai@rtl.lu>.

## Corpus retiré

`POTOMITAN/luxembourgish-corpus` (157 tours de parole de conférences de presse
gouvernementales) alimentait le dictionnaire jusqu'à cette version. Il a été
retiré pour deux raisons :

1. **Contamination allemande.** Les ministres passent régulièrement au
   Hochdeutsch en pleine réponse. 2,7 % des occurrences étaient des mots
   allemands sans ambiguïté, et 42 d'entre eux étaient livrés dans le
   dictionnaire : `und` 372, `wir` 352, `auch` 324, `ich` 124, `ist` 120,
   `für` 112. Sur un préfixe `au`, le clavier proposait `auch`.
2. **Registre inadapté.** Personne ne tutoie en conférence de presse : le
   corpus ne contenait pratiquement aucune forme de deuxième personne.

Attention en revanche : **`dass` n'est pas un germanisme.** C'est une variante
orthographique luxembourgeoise légitime, employée dans les deux corpus
retenus (`dass` 10 749 contre `datt` 14 189 dans le dictionnaire livré). Ne
pas l'ajouter à une éventuelle liste d'exclusion.
