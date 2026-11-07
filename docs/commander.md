---
title: "Précommander le jeu de cartes"
description: "Le Wuertduell, le jeu de cartes en luxembourgeois : quatre boîtes de 54 cartes à 9,90 € la boîte. Réservez les vôtres sans payer : l'impression part dès que l'objectif est atteint. Livraison au Luxembourg, en Belgique, en France et en Allemagne."
lang: fr
# Pendant le sondage de novembre 2026 sur le jeu et son prix, la page reste en
# ligne mais aucune page du site ne la cite, et elle sort des moteurs de recherche.
noindex: true
# La précommande (BOUTIQUE-CARTES.md § 1 et § 9, skill business-modele § 4).
#
# mode "reservation" : réservation sans paiement, par le formulaire. C'est le
#   seul mode possible tant que l'asbl, les conditions de vente, les mentions
#   légales et le marquage CE ne sont pas en place (BOUTIQUE-CARTES.md § 8).
# mode "paiement" : précommande payée par Stripe, remboursée en entier si
#   l'objectif n'est pas atteint à la date limite. Remplir stripe_lien,
#   date_limite et fabricant avant de basculer : sans stripe_lien, la page
#   reste en réservation.
mode: "reservation"
# Prix du 2026-11-01 (skill business-modele) : il suppose un tirage offset,
# jamais l'impression à la demande (BOUTIQUE-CARTES.md § 5).
prix: "9,90 €"
prix_nombre: 9.90
# Objectif : l'équilibre d'un tirage de 500 boîtes dans le cas pessimiste
# (skill business-modele § 3 : 221–347 boîtes). Toutes boîtes confondues.
objectif: 350
# À mettre à jour à la main, depuis les réservations reçues, avec la date.
reservees: 0
reservees_au: "1er novembre 2026"
delai: "six à huit semaines après le lancement de l'impression"
date_limite: ""   # mode paiement : sans objectif atteint à cette date, tout est remboursé
stripe_lien: ""   # mode paiement : un lien Stripe avec les quatre boîtes en quantités ajustables
fabricant: ""     # nom, adresse postale et e-mail du vendeur : obligatoires en mode paiement
---

{% assign paiement = false %}
{% if page.mode == "paiement" and page.stripe_lien != "" %}{% assign paiement = true %}{% endif %}
{% assign pct = page.reservees | times: 100 | divided_by: page.objectif %}
{% if pct > 100 %}{% assign pct = 100 %}{% endif %}

<nav class="site">
  <a href="index.html">🏠 Accueil</a> ·
  <a href="regle-du-jeu.html">🃏 Règle du jeu</a> ·
  <strong>🛒 Précommander</strong> ·
  <a href="faq.html">❓ Aide</a> ·
  <button type="button" class="theme-toggle" aria-label="Passer en mode sombre">🌙</button>
</nav>

# Précommander le jeu de cartes

<p class="chapo">Le <strong>Wuertduell</strong>, le jeu de cartes en
luxembourgeois : quatre boîtes de 54 cartes, à <strong>{{ page.prix }} la
boîte</strong>. On joue tout de suite, à deux, dès 6 ans.
{% if paiement %}Précommandez maintenant : l'impression part dès que
{{ page.objectif }} boîtes sont précommandées.{% else %}Réservez les vôtres
<strong>sans rien payer</strong> : l'impression part dès que
{{ page.objectif }} boîtes sont réservées.{% endif %}</p>

<div class="objectif" role="group" aria-label="Avancement de la précommande" style="margin:20px 0;padding:16px 18px;border:1px solid var(--line);border-radius:12px;background:var(--card);">
  <p style="margin:0 0 8px;font-weight:600;">
    {{ page.reservees }} boîte{% if page.reservees > 1 %}s{% endif %} {% if paiement %}précommandée{% else %}réservée{% endif %}{% if page.reservees > 1 %}s{% endif %}
    sur {{ page.objectif }}
  </p>
  <div style="height:14px;border-radius:7px;background:var(--sea-soft);overflow:hidden;" role="progressbar" aria-valuemin="0" aria-valuemax="{{ page.objectif }}" aria-valuenow="{{ page.reservees }}" aria-label="{{ page.reservees }} sur {{ page.objectif }}">
    <div style="height:100%;width:{{ pct }}%;min-width:{% if page.reservees > 0 %}6px{% else %}0{% endif %};background:var(--gradient);"></div>
  </div>
  <p class="note" style="margin:8px 0 0;">Chiffre au {{ page.reservees_au }}, mis à jour à la main.</p>
</div>

<figure style="margin:20px auto;max-width:620px;text-align:center;">
  <img src="assets/cartes-eventail.png" style="width:100%;" alt="Quatre cartes en éventail : Bam (arbre) en étain, Sonn (soleil) en bronze, Brëll (lunettes) en argent, Kran (grue) en or">
  <figcaption>Les cartes du carnet de l'application, sur du vrai carton</figcaption>
</figure>

## Comment ça marche

{% if paiement %}
1. **Vous précommandez** vos boîtes et vous payez, port compris.
2. **Dès que {{ page.objectif }} boîtes sont précommandées**, nous lançons
   l'impression et vous écrivons. Si l'objectif n'est pas atteint au
   **{{ page.date_limite }}**, vous êtes **remboursé en entier**.
3. **Vous recevez vos boîtes** {{ page.delai }}.
{% else %}
1. **Vous réservez** vos boîtes avec le formulaire ci-dessous. Vous ne payez
   rien, et réserver ne vous engage à rien.
2. **Dès que {{ page.objectif }} boîtes sont réservées**, nous vous écrivons
   pour confirmer votre réservation et la payer. L'impression part avec les
   paiements reçus.
3. **Vous recevez vos boîtes** {{ page.delai }}, chez vous ou lors d'une de
   nos animations au Luxembourg.
{% endif %}

**Pourquoi une précommande ?** Imprimer en série coûte deux à trois fois moins
cher qu'imprimer chaque boîte à l'unité : c'est ce qui permet une boîte à
{{ page.prix }}. La précommande nous dit combien de boîtes imprimer, sans
avance et sans stock perdu.

## Les quatre boîtes

Chaque boîte contient **52 cartes à jouer**, deux paquets de 26 prêts à jouer
l'un contre l'autre, et **2 cartes de règle** : la Bataille des mots dès 6 ans,
et le Wuertduell dès 8 ans ([la règle complète](regle-du-jeu.html)). Elle
contient aussi **un code**, qui ajoute ses 52 cartes au carnet de
l'application. Les boîtes se jouent seules ou ensemble, et aucun mot n'est
dans deux boîtes.

**🔴 La boîte de départ**, *Déieren* contre *Iessen* : les animaux contre ce
qu'on mange.

**🟢 L'extension *Schoul* et *Natur*** : l'école (*Buch*, *Heft*,
*Schoulsak*, *liesen*, *schreiwen*…) contre la nature (*Bam*, *Sonn*,
*Mound*, les saisons, *gréng*, *waarm*…).

**🔵 Niveau A1**, *Doheem* contre *Dobaussen* : les premiers mots, à la maison
(*Fra*, *Kand*, *Apel*, *Kaffi*, *Famill*…) et dehors (*Bus*, *Vëlo*, *Büro*,
*Noper*, *Apdikt*…).

**🟣 Niveau A2**, *Doheem* contre *Dobaussen* : la suite, à la maison
(*Haus*, *Gaart*, *Hand*, *Häerz*, *Schong*…) et dehors (*Gare*, *Tram*,
*Duerf*, *Rees*, *Schnéi*…).

Toutes les cartes sont au format poker (63 × 88 mm). Les mots, leurs sens et
leurs phrases viennent du [Lëtzebuerger Online Dictionnaire](https://lod.lu),
le dictionnaire officiel du Zenter fir d'Lëtzebuerger Sprooch.

{% if paiement %}
## Précommander

<p class="chapo-note">{{ page.prix }} la boîte, port en plus.</p>

<p style="margin:20px 0 8px;"><a href="{{ page.stripe_lien }}" class="btn-installer">🛒 Précommander mes boîtes</a></p>

<p class="note">Paiement sécurisé par Stripe, qui vous demande l'adresse de
livraison et le nombre de boîtes de chaque sorte. Si l'objectif n'est pas
atteint au {{ page.date_limite }}, vous êtes remboursé en entier. Vous pouvez
renoncer à votre achat dans les 14 jours qui suivent la réception.
<a href="conditions-de-vente.html">Conditions de vente</a> ·
<a href="conditions-de-vente.html#donnees">Vos données</a></p>

### Sur le produit

- Jeux de cartes pour enfants **dès 6 ans** (Wuertduell dès 8 ans).
- Vendeur et fabricant : {{ page.fabricant }}
{% else %}
## Réserver mes boîtes

<form id="reservation" action="https://formspree.io/f/mvgwnlky" method="POST" style="margin:16px 0;">
  <input type="hidden" name="_subject" value="Réservation : Wuertduell">
  <input type="hidden" name="liste" value="precommande-wuertduell">
  <fieldset style="margin:0 0 14px;padding:0;border:0;">
    <legend style="padding:0;font-weight:600;">Combien de boîtes ?</legend>
    <p class="note" style="margin:4px 0 10px;">{{ page.prix }} la boîte, port en plus. Rien à payer aujourd'hui.</p>
    {% assign boites = "boite_depart|🔴 La boîte de départ,extension|🟢 L'extension Schoul et Natur,niveau_a1|🔵 Niveau A1,niveau_a2|🟣 Niveau A2" | split: "," %}
    {% for b in boites %}{% assign champ = b | split: "|" %}
    <p style="margin:0 0 8px;display:flex;align-items:center;gap:12px;flex-wrap:wrap;">
      <input id="q-{{ champ[0] }}" class="quantite" type="number" name="{{ champ[0] }}" min="0" max="20" step="1" inputmode="numeric" value="{% if forloop.first %}1{% else %}0{% endif %}"
             style="width:72px;padding:8px;font-size:17px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--ink);">
      <label for="q-{{ champ[0] }}">{{ champ[1] }}</label>
    </p>
    {% endfor %}
    <p id="total" role="status" style="margin:6px 0 0;font-weight:600;"></p>
  </fieldset>
  <p style="margin:0 0 10px;">
    <label for="reservation-email">Votre e-mail</label><br>
    <input id="reservation-email" type="email" name="email" required autocomplete="email"
           style="width:100%;max-width:360px;padding:10px;font-size:17px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--ink);">
  </p>
  <p style="margin:0 0 10px;">
    <label for="reservation-pays">Pays de livraison</label><br>
    <select id="reservation-pays" name="pays"
            style="padding:10px;font-size:17px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--ink);">
      <option>Luxembourg</option>
      <option>Belgique</option>
      <option>France</option>
      <option>Allemagne</option>
      <option>Un autre pays</option>
    </select>
  </p>
  <fieldset style="margin:0 0 14px;padding:0;border:0;">
    <legend style="padding:0;">Comment recevoir vos boîtes ?</legend>
    <label><input type="radio" name="reception" value="livraison" checked> Livrées chez moi (port en plus)</label><br>
    <label><input type="radio" name="reception" value="retrait"> Retirées lors d'une animation ou d'un stand au Luxembourg (sans frais)</label>
  </fieldset>
  <button type="submit" class="btn-installer" style="border:0;cursor:pointer;">📦 Réserver, sans payer</button>
  <p id="reservation-message" role="status" style="margin:12px 0 0;font-weight:600;"></p>
</form>

<p class="note">Votre adresse sert seulement à vous écrire quand l'impression
se prépare, pour confirmer et payer votre réservation, puis elle est effacée
après la livraison, ou tout de suite si le projet n'aboutit pas. Réserver ne
vous engage à rien. Le formulaire est hébergé par
<a href="https://formspree.io/legal/privacy-policy">Formspree</a>, comme celui
des avis sur l'application.</p>

<script>
(function () {
  var form = document.getElementById('reservation');
  var message = document.getElementById('reservation-message');
  var total = document.getElementById('total');
  var champs = form.querySelectorAll('.quantite');
  var prix = {{ page.prix_nombre }};

  function nombreDeBoites() {
    var n = 0;
    champs.forEach(function (c) {
      var v = parseInt(c.value, 10);
      if (v > 0) n += v;
    });
    return n;
  }

  function majTotal() {
    var n = nombreDeBoites();
    total.textContent = n === 0
      ? 'Choisissez au moins une boîte.'
      : n + (n > 1 ? ' boîtes, ' : ' boîte, ') +
        (n * prix).toFixed(2).replace('.', ',') + ' € hors port, à payer plus tard.';
  }

  champs.forEach(function (c) { c.addEventListener('input', majTotal); });
  majTotal();

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    if (nombreDeBoites() === 0) {
      message.textContent = 'Choisissez au moins une boîte.';
      return;
    }
    message.textContent = 'Envoi…';
    fetch(form.action, { method: 'POST', body: new FormData(form), headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error(r.status);
        form.reset();
        majTotal();
        message.textContent = 'C\'est réservé ! Nous vous écrirons quand l\'impression se prépare. Merci !';
      })
      .catch(function () {
        message.textContent = 'L\'envoi n\'a pas marché. Réessayez dans un instant.';
      });
  });
})();
</script>
{% endif %}

## Livraison

Au **Luxembourg**, en **Belgique**, en **France** et en **Allemagne**, port
en plus{% unless paiement %}, communiqué avant le paiement{% endunless %}. Au
Luxembourg, vous pouvez aussi **retirer vos boîtes sans frais** lors d'une de
nos animations ou d'un stand.

## Gratuit, pour toujours

- **L'application** Lëtzebuergesch Clavier : le clavier, le Wierderbuch, le
  carnet et les jeux. [L'installer](index.html#installer).
- **Les cartes à imprimer**, pour les écoles, les animations et pour apprendre
  chez soi : [les fichiers](https://github.com/famibelle/LuxKeyb/tree/main/cartes-imprimables).
  Les boîtes ajoutent le vrai carton, la boîte prête à jouer et le code des
  cartes pour le carnet.

## En attendant

Lisez [la règle du jeu](regle-du-jeu.html), et commencez la collection dans le
carnet de [l'application](index.html#installer) : chaque mot que vous tapez en
luxembourgeois peut devenir une carte.
