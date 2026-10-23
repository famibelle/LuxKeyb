---
name: imprimeur
description: Préparer l'impression physique des cartes du carnet LuxKeyb (qualité carte à jouer) — choix du jeu de cartes, spécifications techniques, fichiers prêts à imprimer, estimation de budget et choix d'imprimeur. À utiliser quand on parle d'imprimer les cartes, d'un jeu physique, d'un devis, d'un BAT ou du stand (Fierkelsmaart).
---

# Imprimeur — cartes LuxKeyb sur papier

## 1. Quelles cartes existent

Dans l'application, chaque mot tapé peut devenir une carte : le dictionnaire
(`android_keyboard/app/src/main/assets/luxemburgish_dict.json`) en compte
près de 38 000. On n'imprime donc jamais « tout » : on imprime un **ensemble
fini**, en priorité les séries du carnet
(`android_keyboard/app/src/main/java/com/example/kreyolkeyboard/carnet/Series.kt`) :

| Série | Source | Cartes |
|---|---|---|
| Nombres 0–100 | `Series.kt` (NOMBRES), orthographe via `zuelen/ZuelenSpeller` | 101 |
| Enluminures | `assets/luxemburgish_series.json` → `enluminures` | 104 |
| Jeux | `JeuCarte` dans `carnet/CarnetModels.kt` | 7 |
| Paysages | `carnet/Paysages.kt` (9 lieux × 4 heures) | 36 |

Total séries : **248 cartes**. Recompter à chaque fois depuis les sources
(les chiffres ci-dessus datent d'octobre 2026) :

```bash
python3 -c "import json;print(len(json.load(open('android_keyboard/app/src/main/assets/luxemburgish_series.json'))['enluminures']))"
```

Alternative courte : un jeu de 54 cartes (sélection d'enluminures, paysages,
nombres) — beaucoup moins cher, idéal pour un stand.

La rareté d'une carte vient du rang de fréquence (`Rarete.pourRang`,
`Rarete.pourNombre` dans `CarnetModels.kt`) : la reprendre telle quelle sur
le papier (cadre, couleur) pour rester fidèle à l'application.

## 2. Spécifications « qualité carte à jouer »

- Format poker 63 × 88 mm (ou bridge 57 × 89 mm), coins arrondis rayon 3 mm.
- Fond perdu 3 mm de chaque côté → fichier 69 × 94 mm.
- Zone de sécurité : texte et QR à au moins 3 mm du bord de coupe.
- 300 dpi minimum → **815 × 1110 px** par face avec fond perdu (poker).
- Carton 300–330 g/m² à âme noire ou bleue (opacité), pelliculage toilé (linen).
- Couleurs : fournir en RVB sauf demande CMJN de l'imprimeur ; prévoir que
  les couleurs vives de l'écran ternissent en CMJN → toujours un BAT physique.
- Polices : celles du carnet (`carnet/PolicesCarnet.kt`, `assets/fonts`) —
  vérifier leur licence d'impression ; les vectoriser/incorporer dans le PDF.
- QR codes (échange de cartes) : au moins 15 × 15 mm, tester le scan sur
  l'épreuve imprimée.
- Dos commun à toutes les cartes (identique pour le jeu), recto propre à chaque carte.

## 3. Produire les fichiers

Les cartes ne sont pas des images dans le dépôt : elles sont dessinées par le
code (`CarteCarnet.kt`, `CadreOrne.kt`, `Paysages.kt`, `Blason.kt`).
Deux voies :

1. **Export depuis l'app** (fidèle) : un test instrumenté ou un écran debug
   qui rend chaque carte dans un `Bitmap` de 815 × 1110 px et l'écrit en PNG.
2. **Redessin** (script Python/HTML + Playwright, `NODE_PATH=$(npm root -g)`) :
   reproduire la mise en page à partir des JSON d'assets.

Sortie attendue, hors dépôt git si lourde :
`impression/<jeu>/recto/NNN_<mot>.png`, `impression/<jeu>/dos.png`, plus un
`planche.pdf` (une carte par page, fond perdu inclus) et un `liste.csv`
(numéro, mot, série, rareté) pour vérifier l'ensemble avant envoi.

Contrôles avant envoi : nombre de fichiers = nombre de cartes prévu,
orthographe luxembourgeoise relue, aucun texte dans la marge de 3 mm, QR scannés.

## 4. Budget indicatif (hors TVA 17 % et livraison)

Ordres de grandeur, à confirmer par devis :

| Quantité | Imprimeur | Jeu de 54 | Coffret 248 cartes |
|---|---|---|---|
| 1–3 prototypes | Impression à la demande (MakePlayingCards, Printerstudio) | 12–20 € | 50–80 € |
| ~100 | Idem, tarif dégressif | 6–10 € | 25–40 € |
| 500+ | Offset européen (Ludo Fact, Delano, Spielkartenfabrik Altenburg, Cartamundi) | 1,5–5 € | 8–15 € |

À ajouter : livraison vers le Luxembourg (30–150 €), BAT physique (30–80 €),
finitions spéciales (dorure, boîte rigide) en sus.

## 5. Calendrier

Prototype à la demande : 1–2 semaines. Offset : 3–6 semaines de production
après validation du BAT. Pour un événement (ex. Fierkelsmaart, avril 2027),
commander le prototype ~3 mois avant et lancer la série ~2 mois avant.

## 6. Comment répondre

- Toujours demander ou supposer explicitement : quel ensemble de cartes,
  combien d'exemplaires, boîte ou non.
- Donner les prix comme fourchettes, jamais comme devis.
- Proposer d'abord un prototype avant toute série.
- Répondre en français par défaut.
