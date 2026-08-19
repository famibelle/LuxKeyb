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

Chaque capture passe huit contrôles, tous déduits de l'image : aucune coordonnée
n'est écrite en dur, ce qui les rend valables quelle que soit la géométrie.

- les sept touches colorées de la rangée du bas sont présentes ;
- elles partagent le même bord supérieur (tolérance 4 px) ;
- la touche emoji contient bien un emoji (pixels jaunes) ;
- le libellé « 123 » occupe une hauteur plausible, et « Potomitan™ » une largeur
  plausible : une ellipse « … » tiendrait sur presque rien ;
- les rangées de lettres portent 29 caractères environ (10 + 10 + 9, accents
  détachés compris), ce qui attrape le défaut des touches vides.

## Étalonnage

Un contrôle qui ne peut pas échouer ne prouve rien. L'analyseur a été mis au
point contre une capture témoin portant le décalage de « 123 » de la 10.12.2,
qu'il signale (« écart de 11 px entre les bords supérieurs »), et contre les
captures corrigées, qu'il laisse passer. Conserver ce réflexe avant de faire
confiance à un « 0 échec » : ajouter un contrôle, c'est aussi vérifier qu'il
sait dire non.

## Limites connues

- Un seul émulateur à la fois (port 5554), d'où la durée.
- Un AVD dont le System UI se bloque (« System UI isn't responding » en boucle)
  ne peut pas être testé ; le banc l'annonce par `CLAVIER|le clavier ne
  s'affiche pas` et passe au suivant. Constaté sur `kreyol_magic5lite33`, même
  après `-wipe-data`.
- Le clavier n'apparaît pas dans `uiautomator dump` : c'est bien l'image qui est
  analysée, pas l'arbre de vues, et les libellés ne sont donc jamais lus
  littéralement.
