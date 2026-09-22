# LuxKeybPlayStore

Tout ce qui part vers la Play Console pour **Lëtzebuergesch Clavier**
(`com.potomitan.luxkeyboard`), plus le flyer imprimable. Rien ici n'entre dans
l'APK : ce dossier ne déclenche aucun build.

L'organisation est reprise du dossier Play Store créole de KreyolKeyb, depuis
retiré de ce dépôt. Ses contenus — presse guadeloupéenne, auteurs du corpus
kréyòl, bannières, captures — n'ont servi que de gabarit et **rien n'a été
repris tel quel** : tout est refait à partir des sources de ce dépôt-ci.

```
texts/
  fichePlayStore.md       les textes à coller dans la Play Console
  aso_pack_10.14.0.md     titre, canaux, liens UTM, avis
graphics/
  build_graphics.py       fabrique tout ce qui suit
  feature-graphic/        les 10 fichiers à envoyer, chacun nommé d'après
                          l'emplacement de la Console où il va, + la source
                          HTML de l'image de présentation
  flyer-triptyque/        flyer A4 3 volets (HTML autonome + PDF)
```

## Refabriquer les images

```bash
cd graphics
python3 build_graphics.py              # les 8 fichiers, puis leur vérification
python3 build_graphics.py shots        # les captures seules
python3 build_graphics.py check        # vérifie sans rien refabriquer
```

Demande `google-chrome` et ImageMagick (`convert`). Les sources sont
`Logos/luxembourg-logo-hd.png` et les captures réelles de `docs/Screenshots/`.

Le flyer n'est pas géré par ce script : c'est un HTML autonome qui s'édite à la
main, images comprises (embarquées en base64). Pour le PDF :

```bash
cd graphics/flyer-triptyque
google-chrome --headless --disable-gpu --no-sandbox --no-pdf-header-footer \
  --print-to-pdf=Letzebuergesch_Clavier_flyer.pdf flyer_luxkeyb_source.html
```

## Deux pièges rencontrés en fabriquant tout ça

- **Chrome headless peint 87 px de moins que le `--window-size` demandé** (la
  hauteur de la barre de fenêtre) et laisse le bas de la page vide. D'où le
  rendu volontairement trop haut, puis recadré, dans `build_graphics.py`.
- **`@page{ size:297mm 210mm landscape }` est invalide** — c'est soit des
  dimensions, soit un mot-clé de format suivi de l'orientation, pas les deux.
  Chrome l'ignore silencieusement et sort du Letter portrait. Le flyer créole
  porte encore cette faute ; celui-ci déclare `size:297mm 210mm`.

## État

L'application est **en test fermé**, le palier des douze testeurs est atteint
depuis le 2026-09-09, et la sortie publique est programmée au 2026-09-23
(`docs/stats/testeurs.json`). Les textes sont prêts à coller, les images
prêtes à envoyer. Ce qui reste à faire est listé en fin de
`texts/fichePlayStore.md` (avant le passage en production) et de
`texts/aso_pack_10.14.0.md` (après) ; le point encore ouvert dans le premier
est l'e-mail à RTL.lu sur la redistribution des phrases de Wuertlück.

Une réserve connue : les brèves descriptions luxembourgeoise et allemande
doivent être relues par un locuteur natif avant publication.

Les captures sources (`docs/Screenshots/lux_*.png`) ont été reprises le
2026-09-22 sur l'émulateur `kreyol_test` (1080 × 2340) sous la 26.3.0 : elles
sont natives, rien n'est agrandi. Après un changement d'interface, les
recapturer au même endroit puis relancer `python3 build_graphics.py shots`.
