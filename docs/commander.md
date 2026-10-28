---
title: "Commander le jeu de cartes"
description: "Le Wuertduell, le jeu de cartes du Lëtzebuergesch Clavier : la boîte de 54 cartes en luxembourgeois et les extensions Schoul et Natur, imprimées à la demande et livrées chez vous au Luxembourg, en Belgique, en France et en Allemagne."
lang: fr
# La boutique (BOUTIQUE-CARTES.md). Tant que stripe_lien (la boîte) est vide,
# la page propose la liste d'attente. Ne le remplir qu'après la liste du § 8
# de la note : statut, conditions de vente, mentions légales, marquage CE.
# Un lien vide d'extension cache seulement son bouton.
stripe_lien: ""
stripe_lien_schoul: ""
stripe_lien_natur: ""
prix: ""            # la boîte, ex. « 29 €, livraison comprise »
prix_extension: ""  # une extension, ex. « 12 € »
delai: "deux à trois semaines"
fabricant: ""   # nom, adresse postale et e-mail du vendeur, obligatoires sur la page de vente
---

<nav class="site">
  <a href="index.html">🏠 Accueil</a> ·
  <a href="regle-du-jeu.html">🃏 Règle du jeu</a> ·
  <strong>🛒 Commander</strong> ·
  <a href="faq.html">❓ Aide</a> ·
  <button type="button" class="theme-toggle" aria-label="Passer en mode sombre">🌙</button>
</nav>

# Commander le jeu de cartes

<p class="chapo">Le <strong>Wuertduell</strong> : 54 cartes en luxembourgeois,
imprimées à la demande et livrées chez vous. On joue tout de suite, à deux,
dès 6 ans, et les extensions <em>Schoul</em> et <em>Natur</em> agrandissent
le jeu.</p>

<figure style="margin:20px auto;max-width:620px;text-align:center;">
  <img src="assets/cartes-eventail.png" style="width:100%;" alt="Quatre cartes en éventail : Bam (arbre) en étain, Sonn (soleil) en bronze, Brëll (lunettes) en argent, Kran (grue) en or">
  <figcaption>Les cartes du carnet de l'application, sur du vrai carton</figcaption>
</figure>

## La boîte de départ

- **52 cartes à jouer** : deux paquets de 26, *Déieren* (les animaux) et
  *Iessen* (ce qu'on mange), prêts à jouer l'un contre l'autre.
- **2 cartes de règle** : la Bataille des mots, dès 6 ans, et le Wuertduell,
  dès 8 ans. La règle complète est [en ligne](regle-du-jeu.html).
- **Un code par e-mail**, un par boîte : scanné dans le carnet de
  l'application, il y ajoute les 52 cartes de la boîte.

## Les extensions

- **Schoul**, l'école : 24 cartes, de *Buch* et *Heft* à *Schoulsak* et
  *Schouljoffer*, avec des verbes pour la classe : *liesen*, *schreiwen*,
  *molen*…
- **Natur** : 24 cartes, *Bam*, *Sonn*, *Mound*, *Reen*, *Donnerwieder*… et
  les couleurs *rout*, *blo*, *gréng*, *giel*.

Elles complètent la boîte : avec elles, chacun compose son propre paquet de
26 cartes ([voir la règle](regle-du-jeu.html#les-paquets)). Chaque extension a
aussi son code par e-mail, qui ajoute ses 24 cartes au carnet.

Toutes les cartes sont au format poker (63 × 88 mm). Les mots, leurs sens et
leurs phrases viennent du [Lëtzebuerger Online Dictionnaire](https://lod.lu).

## Livraison

Au **Luxembourg**, en **Belgique**, en **France** et en **Allemagne**. Chaque
jeu est imprimé pour vous, puis envoyé directement chez vous : comptez
{{ page.delai }}. Au moment de payer, vous pouvez ajouter les extensions à la
boîte, ou l'inverse : tout part dans la même commande.

{% if page.stripe_lien != "" %}
## Commander

<p class="chapo-note">La boîte de départ : {{ page.prix }}</p>

<p style="margin:20px 0 8px;"><a href="{{ page.stripe_lien }}" class="btn-installer">🛒 Commander la boîte</a></p>
{% if page.stripe_lien_schoul != "" or page.stripe_lien_natur != "" %}
<p class="chapo-note" style="margin-top:24px;">Une extension : {{ page.prix_extension }}</p>

<p>{% if page.stripe_lien_schoul != "" %}<a href="{{ page.stripe_lien_schoul }}" class="btn-secondaire">Commander Schoul</a>{% endif %}
{% if page.stripe_lien_natur != "" %}<a href="{{ page.stripe_lien_natur }}" class="btn-secondaire">Commander Natur</a>{% endif %}</p>
{% endif %}
<p class="note">Paiement sécurisé par Stripe, qui vous demande l'adresse de
livraison. Vous pouvez renoncer à votre achat dans les 14 jours qui suivent la
réception. <a href="conditions-de-vente.html">Conditions de vente</a> ·
<a href="conditions-de-vente.html#donnees">Vos données</a></p>

### Sur le produit

- Jeux de cartes pour enfants **dès 6 ans** (Wuertduell dès 8 ans).
- Vendeur et fabricant : {{ page.fabricant }}
{% else %}
## Un e-mail à la sortie

Le jeu imprimé est en préparation. Laissez votre adresse : vous recevrez **un
seul e-mail**, le jour où les commandes ouvrent.

<form id="attente" action="https://formspree.io/f/mvgwnlky" method="POST" style="margin:16px 0;">
  <input type="hidden" name="_subject" value="Liste d'attente : boutique Wuertduell">
  <input type="hidden" name="liste" value="boutique-wuertduell">
  <p style="margin:0 0 10px;">
    <label for="attente-email">Votre e-mail</label><br>
    <input id="attente-email" type="email" name="email" required autocomplete="email"
           style="width:100%;max-width:360px;padding:10px;font-size:17px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--ink);">
  </p>
  <fieldset style="margin:0 0 10px;padding:0;border:0;">
    <legend style="padding:0;">Ce qui vous intéresse</legend>
    <label><input type="checkbox" name="interet_boite" value="oui" checked> La boîte de départ</label><br>
    <label><input type="checkbox" name="interet_schoul" value="oui"> L'extension Schoul</label><br>
    <label><input type="checkbox" name="interet_natur" value="oui"> L'extension Natur</label>
  </fieldset>
  <p style="margin:0 0 14px;">
    <label for="attente-pays">Pays de livraison</label><br>
    <select id="attente-pays" name="pays"
            style="padding:10px;font-size:17px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--ink);">
      <option>Luxembourg</option>
      <option>Belgique</option>
      <option>France</option>
      <option>Allemagne</option>
      <option>Un autre pays</option>
    </select>
  </p>
  <button type="submit" class="btn-installer" style="border:0;cursor:pointer;">🔔 Me prévenir</button>
  <p id="attente-message" role="status" style="margin:12px 0 0;font-weight:600;"></p>
</form>

<p class="note">Votre adresse sert seulement à vous prévenir de l'ouverture
des commandes, puis elle est effacée. Le formulaire est hébergé par
<a href="https://formspree.io/legal/privacy-policy">Formspree</a>, comme celui
des avis sur l'application.</p>

<script>
(function () {
  var form = document.getElementById('attente');
  var message = document.getElementById('attente-message');
  form.addEventListener('submit', function (e) {
    e.preventDefault();
    message.textContent = 'Envoi…';
    fetch(form.action, { method: 'POST', body: new FormData(form), headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error(r.status);
        form.reset();
        message.textContent = 'C\'est noté : nous vous écrirons à l\'ouverture des commandes. Merci !';
      })
      .catch(function () {
        message.textContent = 'L\'envoi n\'a pas marché. Réessayez dans un instant.';
      });
  });
})();
</script>
{% endif %}

## En attendant

Lisez [la règle du jeu](regle-du-jeu.html), et commencez la collection dans
le carnet de [l'application](index.html#installer) : chaque mot que vous tapez
en luxembourgeois peut devenir une carte.
