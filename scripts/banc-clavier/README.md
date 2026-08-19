# Banc d'affichage du clavier

Vérifie sur tous les AVD installés que les touches s'affichent correctement, en
portrait et en paysage. Écrit après la 10.12.5, où deux défauts d'affichage
(la touche emoji réduite à « … », la touche « 123 » désalignée) étaient passés
inaperçus jusqu'à ce qu'un œil humain les remarque.

```bash
cd android_keyboard && ./gradlew assembleDebug     # l'APK que le banc installera
scripts/banc-clavier/tous.sh                        # ~2 min 30 par appareil
```

La sortie va dans `/tmp/banc-clavier` par défaut (premier argument pour changer,
second pour désigner un autre APK). Le banc y laisse une capture par appareil et
par orientation, et termine par la synthèse. Pour rejouer l'analyse seule sur des
captures déjà prises :

```bash
python3 scripts/banc-clavier/synthese.py /tmp/banc-clavier
```

## Ce qui est vérifié

Chaque capture passe six contrôles, tous déduits de l'image : aucune coordonnée
n'est écrite en dur, ce qui les rend valables quelle que soit la géométrie.

- les sept touches colorées de la rangée du bas sont présentes ;
- elles partagent le même bord supérieur (tolérance 4 px) ;
- la touche emoji contient bien un emoji (pixels jaunes) ;
- le libellé « 123 » occupe une hauteur plausible, et l'encre de « Potomitan™ »
  une largeur plausible : une ellipse « … » tiendrait sur presque rien. Depuis la
  10.12.9 la signature est peinte en blanc très dilué sur le dégradé de la touche,
  trop pâle pour un seuil de blanc franc : elle est mesurée par son écart au fond
  de la rangée, et rapportée à la hauteur de touche plutôt qu'à la largeur de la
  barre d'espace, seul étalon qui vaille autant en portrait qu'en paysage ;
- les rangées de lettres portent 29 caractères environ (10 + 10 + 9, accents
  détachés compris), ce qui attrape le défaut des touches vides.

## Étalonnage

Un contrôle qui ne peut pas échouer ne prouve rien. L'analyseur a été mis au
point contre une capture témoin portant le décalage de « 123 » de la 10.12.2,
qu'il signale (« écart de 11 px entre les bords supérieurs »), et contre les
captures corrigées, qu'il laisse passer. Conserver ce réflexe avant de faire
confiance à un « 0 échec » : ajouter un contrôle, c'est aussi vérifier qu'il
sait dire non.

Le contrôle de la signature a été étalonné de la même façon, sur deux témoins
fabriqués depuis une capture réelle en repeignant la barre d'espace avec la
médiane de chaque ligne : signature effacée donne 0,00 hauteur de touche,
réduite à « … » donne 0,17, intacte donne 1,06 à 1,13 selon l'appareil. Le seuil
est à 0,55, à mi-chemin, et les deux témoins échouent bien.

## La rotation en paysage

Deux pièges y ont fait passer dix appareils sur dix-huit pour vérifiés en paysage
alors que la capture était un portrait, de mêmes dimensions exactes que la capture
portrait du même appareil. Ils tiennent tous les deux au même mécanisme : Android
ne réévalue l'orientation que sur événement, et une demande faite au mauvais
moment est perdue sans laisser de trace.

- La rotation posée pendant que le launcher, verrouillé en portrait, tient encore
  l'écran est enregistrée (`mUserRotation` passe bien à `ROTATION_90`) mais refusée
  par le display. Le banc attend donc la résumption réelle de l'éditeur de contact,
  et repasse par l'orientation opposée : réécrire la valeur déjà présente dans les
  réglages ne déclenche rien.
- Le verrou d'orientation ne prend pas à l'instant où on l'écrit.
  `accelerometer_rotation 0` est bien dans les réglages tandis que le gestionnaire
  de fenêtres reste en `USER_ROTATION_FREE`, où `user_rotation` est ignoré. Le banc
  attend `USER_ROTATION_LOCKED` plutôt que l'écriture du réglage.

Le paysage est par ailleurs servi en `ROTATION_90` comme en `ROTATION_270`, au
choix du système : exiger 90 faisait abandonner l'attente sur un écran pourtant
tourné.

Deux refus explicites empêchent désormais le silence de se reproduire : le banc
renonce à l'appareil si la rotation n'est pas obtenue, et jette la capture si la
forme de l'image dément son nom. `synthese.py` affiche les captures manquantes
au lieu de les omettre du tableau, un « 0 échec » ne pouvant plus reposer sur une
orientation jamais vue.

## Limites connues

- Un seul émulateur à la fois (port 5554), d'où la durée.
- Un AVD dont le System UI se bloque (« System UI isn't responding » en boucle)
  ne peut pas être testé ; le banc l'annonce par `CLAVIER|le clavier ne
  s'affiche pas` et passe au suivant.
- `kreyol_magic5lite33` ne démarre plus du tout : son instantané `default_boot`
  se charge en 4 secondes puis l'appareil reste inerte, `sys.boot_completed` ne
  passe jamais à 1 et l'émulateur signale `Could not take screenshot, error: -1`.
  Le banc l'annonce par `BOOT|echec de démarrage` après 420 s d'attente. C'est un
  instantané corrompu, pas un manque de temps. Le symptôme a changé de nature : cet
  AVD se bloquait auparavant sur son System UI, et un `-wipe-data` n'en était déjà
  pas venu à bout. Un AVD qui ne démarre pas n'apparaît pas non plus dans le tableau de
  synthèse, qui ne connaît que les appareils ayant écrit leur fichier
  d'information : c'est la sortie de `tous.sh` qui porte l'alerte.
- Le clavier n'apparaît pas dans `uiautomator dump` : c'est bien l'image qui est
  analysée, pas l'arbre de vues, et les libellés ne sont donc jamais lus
  littéralement.
