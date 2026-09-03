# Licences des données

Le **code** de l'application est sous licence MIT (voir [`LICENSE`](LICENSE)).
Les **données** qu'elle embarque ne le sont pas : ce sont des œuvres dérivées de
corpus tiers, et chacune garde la licence de sa source. Distribuer
l'application, c'est distribuer les deux.

| Fichier livré | Source | Licence | Ce qu'elle impose |
|---|---|---|---|
| `luxemburgish_dict.json`, `luxemburgish_ngrams.json` | LuxAlign (Philippy et coll., phrases de RTL.lu) et LETZ | **CC BY-NC 4.0** et CC BY 4.0 | Attribution, et **pas d'usage commercial** |
| `luxemburgish_cloze.json` | idem | **CC BY-NC 4.0** et CC BY 4.0 | Idem, et l'attribution doit rester **visible à l'écran** : ce fichier reprend 1 600 phrases entières |
| `luxemburgish_translations.json`, `luxemburgish_familles.json`, `luxemburgish_exemples.json`, `luxemburgish_lod_ids.json`, `luxemburgish_lod_forms.json` | Lëtzebuerger Online Dictionnaire, Zenter fir d'Lëtzebuerger Sprooch | **CC0 1.0** | Rien. Le ZLS est crédité quand même |
| `french_simple_dict.json` | Lexique 3.83, New & Pallier | **CC BY-SA 4.0** | Attribution, et **partage à l'identique** de cet actif dérivé |
| `emoji_data.json` | Unicode CLDR | Unicode License | Attribution |

Trois conséquences pratiques :

- **« Open source » ne lève pas la clause NC.** Les licences approuvées par
  l'OSI autorisent explicitement l'usage commercial, ce que CC BY-NC interdit.
  Une distribution payante de l'application, ou une réutilisation commerciale
  des fichiers de dictionnaire, demande l'accord préalable des ayants droit —
  le contact indiqué par les auteurs pour les données RTL est <ai@rtl.lu>.
- **L'attribution est affichée dans l'application**, carte « Sources » de
  l'onglet À Propos, et sous chaque phrase du jeu Wuertlück. Ces affichages ne
  sont pas décoratifs : ils remplissent l'obligation « BY ». Ne pas les retirer.
- **Le partage à l'identique de Lexique** porte sur `french_simple_dict.json`
  seul, pas sur le reste du dépôt.

Le détail des corpus, de leurs citations bibliographiques et de ce qui a été
écarté est dans [`Dictionnaires/CORPUS.md`](Dictionnaires/CORPUS.md).
