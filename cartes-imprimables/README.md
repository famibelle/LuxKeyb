# Cartes imprimables

Les fichiers pour imprimer les cartes du **Wuertduell**, le jeu de cartes du
Lëtzebuergesch Clavier (règle : [docs/regle-du-jeu.md](../docs/regle-du-jeu.md)).

**Licence : [LICENSE-VISUELS.md](../LICENSE-VISUELS.md).** Usage scolaire et
éducatif libre et gratuit : imprimez pour une classe, un atelier, pour
apprendre. **Vente interdite** sans accord écrit, et la mention
« © Lëtzebuergesch Clavier » reste sur les cartes. Une copie n'est pas une
carte officielle du jeu imprimé.

| Fichier | Contenu |
|---|---|
| `boite/planche_boite.pdf` | La boîte de départ : 52 cartes à jouer (paquets Déieren et Iessen), 2 cartes de règle, le dos. Dessin schématique, pour relire ou jouer tout de suite (`scripts/planche_boite.py`). |
| `boite/rectos_52.pdf` | Les 52 cartes à jouer de la boîte, dessinées par l'application : une carte par page, 69 × 94 mm fond perdu compris, 300 dpi. Le dos est dans `verso/` (`scripts/rectos_boite.py`). |
| `boite/liste.csv` | Les 52 cartes dans l'ordre du PDF : mot, paquet, nature, Force, coût, rareté, sens. |
| `verso/verso.pdf`, `verso/verso_300dpi.png` | Le dos commun, 69 × 94 mm fond perdu compris (poker 63 × 88 mm + 3 mm). |
| `dessin/carte_appli.py` | La carte très rare de l'application, portée en SVG pour l'impression. |
| `dessin/verso_luxe.py` | Le dessin du dos. |
| `dessin/polices/` | Roboto (Apache 2.0). |

Les mots, leurs sens et leurs phrases viennent du
[Lëtzebuerger Online Dictionnaire](https://lod.lu) (CC0). La composition des
paquets : `Dictionnaires/cartes/selection.md` (`scripts/candidats_cartes.py`).

## Ce qui n'est pas ici, et pourquoi

- **Les codes des cartes codes** et la clé qui les signe : un code publié
  donnerait ses cartes dans l'application à tout le monde, pour toujours
  (`CESSION-CARTES.md` § 8). C'est ce qui distingue la boîte officielle d'une
  copie, avec le carton à âme noire, le pelliculage toilé et la dorure.
- **La carte Fierkelsmaart** : elle porte le nom d'un événement et s'inspire
  de son logo ; elle sera publiée avec l'accord écrit de l'organisateur.
