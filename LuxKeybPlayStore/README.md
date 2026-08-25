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
  securite-keystore.md    audit de l'historique git, clé de signature
graphics/
  build_graphics.py       fabrique tout ce qui suit
  app-icon/               icône 512 × 512
  feature-graphic/        image mise en avant 1024 × 500 + source HTML
  screenshots-phone/      6 captures 1080 × 1920, légende incrustée
  flyer-triptyque/        flyer A4 3 volets (HTML autonome + PDF)
```

## Refabriquer les images

```bash
cd graphics
python3 build_graphics.py              # icône + image mise en avant + captures
python3 build_graphics.py shots        # les captures seules
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

L'application **n'est pas publiée**. Les textes sont prêts à coller, les images
prêtes à envoyer, mais rien n'a encore été mesuré. Ce qui reste à faire est
listé en fin de `texts/fichePlayStore.md` (avant le premier envoi) et de
`texts/aso_pack_10.14.0.md` (après).

Une réserve connue : les brèves descriptions luxembourgeoise et allemande
doivent être relues par un locuteur natif avant publication.

Les captures sources (`docs/Screenshots/lux_*.png`) ont été reprises le
2026-08-25 sur un émulateur 1080 × 2340 sous la 10.14.0 : elles sont natives,
rien n'est agrandi. Après un changement d'interface, les recapturer au même
endroit puis relancer `python3 build_graphics.py shots`.
