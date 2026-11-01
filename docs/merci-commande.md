---
title: "Merci pour votre précommande"
description: "Votre précommande du Wuertduell est reçue : ce qui se passe maintenant."
lang: fr
# Page de retour après le paiement Stripe (BOUTIQUE-CARTES.md § 6) : rien à
# chercher ici depuis un moteur de recherche.
noindex: true
---

<nav class="site">
  <a href="index.html">🏠 Accueil</a> ·
  <a href="regle-du-jeu.html">🃏 Règle du jeu</a> ·
  <a href="commander.html">🛒 Précommander</a> ·
  <button type="button" class="theme-toggle" aria-label="Passer en mode sombre">🌙</button>
</nav>

# Merci ! 🎉

{% assign c = site.pages | where: "name", "commander.md" | first %}

<p class="chapo">Votre précommande est reçue et payée. Voici ce qui se passe
maintenant.</p>

1. **Tout de suite**, Stripe vous envoie le reçu de votre paiement par e-mail.
2. **Dès que {{ c.objectif }} boîtes sont précommandées**, nous lançons
   l'impression et vous écrivons. Si l'objectif n'est pas atteint au
   {{ c.date_limite }}, vous êtes **remboursé en entier**, sans rien avoir à
   demander.
3. **Vous recevez vos boîtes** {{ c.delai }}. Chaque boîte contient le
   **code** de ses cartes : scanné dans le carnet de l'application
   (« 🎁 Recevoir », puis « 🃏 Le code d'une carte en papier »), il y ajoute
   ses 52 cartes.

Vous pouvez renoncer à votre achat dans les 14 jours qui suivent la
réception : répondez simplement à notre e-mail.

En attendant le colis, lisez [la règle du jeu](regle-du-jeu.html) : vous
pourrez jouer dès l'ouverture.
