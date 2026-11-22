---
name: roseaux-eau-claire
description: Ce qui pousse dans l'eau claire sur les cartes LuxKeyb (roseaux du surcadrage, et tout brin planté dans un lac ou une rivière) — faire voir le pied en transparence sous la ligne de flottaison, de jour comme de nuit, sans bloc jaune ni effet bocal. À utiliser quand le propriétaire dit que des roseaux ou des plantes « sont posés sur l'eau », que l'eau devrait être cristalline ou transparente, quand on ajoute des roseaux (Pied.ROSEAUX) à un lieu, ou quand on touche à `Surcadre`, `Gabarit`, `rendreBrin` ou `immerger` dans `carnet/Paysages.kt`.
---

# Les roseaux dans l'eau claire

Remarque du propriétaire, 2026-11-22, sur Esch-Sauer : « sachant que l'eau
est considérée comme cristalline, il faut que les bas des roseaux, sous la
ligne de flottaison, apparaissent en transparence ». Peints pleins jusqu'au
bas de la carte, les roseaux se lisaient **posés sur** le lac, pas plantés
dedans.

## 1. Où c'est fait

| Pièce | Rôle |
|---|---|
| `Surcadre.eau: Float?` | la ligne de flottaison du rang de devant, en unités de fenêtre (100 × 66). `null` : pas d'eau, rien ne change |
| `gabarits()` | passe `s.eau` au rang de devant, et au second rang (demi-taille) `base - PIED_HORS_CHAMP * 0.5 + eau * 0.5` : sa ligne est un peu plus haut, plus loin |
| `Gabarit.eau` | la ligne dans la fenêtre, pour ce brin |
| `rendreBrin` → `immerger(b, ombre, y, k, heure)` | appelé **en dernier**, après kraft, ombre, grain et `raviver` |

`immerger`, sous la ligne `y` (pixels du brin) :
1. teinte : `heure.cielBas` à alpha 0x55 en `SRC_ATOP` (le pied prend la
   couleur de l'eau, qui reflète le ciel) ;
2. fondu : `DST_OUT` avec un `LinearGradient` de 0x70 à 0xFF sur **7 unités** :
   à moitié visible sous la surface, disparu sept unités plus bas ;
3. ombre portée : `DST_OUT` à 0xE6 sous la ligne ;
4. filet de surface : `SRC_ATOP`, ciel éclairci (0,6 de jour, 0,25 la nuit),
   alpha 0x70, épaisseur 0,3 unité, posé sur les tiges seules.

Lieux réglés : **Esch-Sauer, Ourdall, Haff Réimech**, `eau = 54f`.
Sans eau, et c'est voulu : **Stad** (quai pavé), **Dumongshaff** et
**Stauséi** (grève de galets) : leurs roseaux poussent sur la berge.

## 2. Les pièges, tous rencontrés

- **La transparence faite dans le dessin ne survit pas.** Un `saveLayerAlpha`
  ou un `DST_OUT` dans le `Peintre` rendait le pied translucide, puis
  `krafter` peignait la tranche de kraft **derrière** la silhouette
  (`DST_OVER`, à partir de l'alpha extrait) et rebouchait tout : un bloc
  jaune opaque. Toute transparence d'un brin se pose **après** `krafter`.
- **L'alpha du pinceau multiplie celui du dégradé.** Après la teinte
  (alpha 0x55), le `DST_OUT` en dégradé ne mordait qu'au tiers. Remettre
  `p.color = 0xFF000000` avant de poser le shader.
- **Une transparence uniforme fait un bocal.** Les lames s'élargissent et se
  resserrent en gerbe vers le bas : sous l'eau, à opacité constante, la
  gerbe devient un rectangle translucide, avec un filet clair dessus qui
  fait le bord d'un aquarium. Le pied doit **s'effacer** en descendant.
- **Le bas de la boîte du second rang** (`base + 1.5`) coupe ses tiges net :
  le fondu doit être fini avant (ici 8,5 unités sous sa ligne, fondu de 7).
- **Un filet de surface épais et franc** se lit comme une vitre. Fin et à
  moitié transparent.

## 3. Pour un nouveau lieu

1. Regarder la carte : l'eau descend-elle jusqu'au bas, sous les roseaux ?
   Si le pied est sur un quai, une grève ou une berge, **pas de ligne
   d'eau** : le pied effacé laisserait les roseaux flotter au-dessus des
   galets.
2. Déclarer `eau = 54f` dans `Surcadre.de(lieu)` (ajuster si la surface est
   plus haute ou plus basse dans la photo), avec une ligne de commentaire.
3. Rendre aux quatre heures (`.claude/skills/vegetation-automne/planche_automne.sh`
   ou `PlancheCartesTest.saisons` avec `-PplancheRarete=TOUTES`) et zoomer ×3
   sur le pied : l'eau doit se voir entre les tiges, aucun bord droit.
4. Vérifier qu'un lieu à roseaux non concerné reste identique au pixel près.
5. Tests, puis commit de `Paysages.kt` seul.

## 4. Pour d'autres brins

`immerger` ne sait rien des roseaux : tout `Gabarit` qui reçoit un `eau`
l'aura. Une fougère ou une herbe du pied ne pousse pas dans l'eau ; une
branche de saule qui trempe pourrait, en lui donnant sa propre ligne.

## Liste de contrôle

- [ ] Le pied est dans l'eau sur la photo, pas sur la berge.
- [ ] Transparence posée après le kraft (`immerger`), jamais dans le dessin.
- [ ] Le pied s'efface, sans bloc ni bocal ni coupure de boîte.
- [ ] Quatre heures regardées, nuit comprise.
- [ ] Les autres lieux à roseaux inchangés.
