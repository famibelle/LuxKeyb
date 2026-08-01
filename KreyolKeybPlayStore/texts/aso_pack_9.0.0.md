# Pack ASO : mise à jour 2026-08-01

Complète `aso_pack_7.0.7.md` (toujours valable pour le titre proposé et les
liens UTM par canal). Cette note ajoute la couverture TV, confirmée par
l'utilisateur : passage sur **Canal 10 le 24 juillet 2026**, et au JT
*Guadeloupe Soir 19h30* de **Guadeloupe la 1ère le 28 juillet 2026**. C'est la
meilleure preuve sociale disponible actuellement (gratuite, indépendante) et
elle n'était pas encore présente dans la fiche Play Store.

## Description complète : mise à jour appliquée

Une ligne « 📺 Vu sur Canal 10 et au JT de Guadeloupe la 1ère (France
Télévisions) en juillet 2026 ! » a été ajoutée en 2ᵉ ligne de
`description.md`, juste après le titre et avant l'accroche douleur/solution.
Elle reste dans les ~250 premiers caractères visibles avant le « Lire la
suite » sur mobile.

Les deux occurrences de l'emoji 🏝️ (cliché touristique, cf. mémoire projet)
ont été remplacées par 🌱 et 🗣️.

## Notes de version à coller dans « Nouveautés » (Play Console)

La version 9.0.0 publiée est une migration technique (AGP 9) invisible pour
l'utilisateur. Le texte ci-dessous met en avant ce qui compte pour un
lecteur de fiche Play Store : la preuve sociale TV et le dernier changement
utilisateur réel (touche « * », 8.8.5).

```
📺 Vu sur Canal 10 et au JT de Guadeloupe la 1ère (France Télévisions) en juillet 2026 !
⌨️ Le clavier numérique gagne la touche « * » (mots de passe, calculs).
✨ Petites corrections de textes en créole.

Ba kréyòl la lanmou'w : télécharge, note, partage !
```

263 caractères, largement sous la limite de 500.

## À vérifier en Play Console (accès utilisateur uniquement)

- Le titre proposé en juillet (« Klavyé Kréyòl – Clavier Créole ») a-t-il été
  appliqué, ou la fiche affiche-t-elle toujours « Klavyé Kréyòl Guadeloupe » ?
- Nombre d'avis et note actuelle : la demande d'In-App Review (livrée en
  7.0.7) a-t-elle produit des avis depuis juillet ?
- Nombre d'installations actuel, pour recalibrer l'objectif 10k du
  [[project-growth-plan-2026-07]] (départ 100+ installs le 2026-07-12).

## Capture d'écran presse (fait le 2026-08-01)

Asset créé : `graphics/screenshots-phone/Screenshot_presse_vu_a_la_tele.png`
(1080×2340, même format que les autres captures). Bandeau bleu « Vu à la
télé » avec mentions textuelles Canal 10 / Guadeloupe la 1ère (pas de
logos officiels, pour éviter tout problème de droits de marque) au-dessus
d'une capture du clavier en action (suggestions KR/FR) — le même contenu
que l'ex-`Screenshot_v8.8_clavier_suggestions_kreyol.png`, déjà présente
dans la galerie Play Store. Committé en `0cfd751`.

L'ancien fichier a été supprimé du dossier (doublon avec le nouvel asset)
et remplacé, pour l'usage README, par un simple recadrage propre de la
même capture réelle : `Screenshot_clavier_suggestions_kreyol.png` (sans
le message de test qui s'affichait en haut de l'ancienne version).

Reste à faire manuellement (accès Play Console uniquement) : **remplacer**
l'ancienne capture « suggestions kréyòl » par
`Screenshot_presse_vu_a_la_tele.png` à la même position dans la galerie
de la fiche (pas d'ajout, pour éviter le doublon visuel des deux captures
montrant la même démo clavier).
