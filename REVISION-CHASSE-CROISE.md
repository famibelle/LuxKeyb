# Améliorer chassé-croisé : idées et retours

Note de conception, écrite **avant** toute implémentation, à la racine pour éviter de déclencher les builds du pipeline CI (voir [`REVISION-CARNET.md`](REVISION-CARNET.md) pour la même raison).

---

## Idées en attente

### 1. Glisser-déposer pour placer les mots

**Proposé par :** Utilisatrice de référence (maman de l'auteur)

**Concept :** Plutôt que la mécanique actuelle de chassé-croisé, permettre aux joueurs de **glisser les mots et de les déposer directement dans leur localisation** sur la grille. 

**Avantages :**
- Interaction plus intuitive et naturelle
- Réduit les clics nécessaires
- Interface plus tactile et engageante
- Possibilité de feedback visuel pendant le glisser (surbrillance de la zone de dépôt valide)

**Points à explorer :**
- Afficher les zones de dépôt valides pendant le glissement
- Comportement sur mobile vs desktop
- Accessibilité (assurer une alternative au clavier)
- Gestion des erreurs et des dépôts invalides
- Animation de retour à la position initiale si le dépôt est invalide

**Statut :** À évaluer
