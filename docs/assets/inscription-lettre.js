/* Inscription à la newsletter, posée dans chaque <div class="inscription-lettre">.
 *
 * L'adresse part directement au formulaire Brevo du projet (« confirmation
 * simple » : le contact entre dans la liste dès l'envoi, et reçoit l'édition
 * suivante avec les autres). Le serveur de Brevo accepte les envois venant de
 * famibelle.github.io et répond en JSON ({"success":true}), ce qui permet
 * d'afficher la réponse sur place, aux couleurs du site et dans la langue de
 * la page, au lieu du cadre gris de Brevo, qui n'existe qu'en français.
 *
 * La langue est celle de l'attribut lang le plus proche (fr par défaut).
 * Sans JavaScript, le <noscript> de la page mène au formulaire de Brevo. */
(function () {
  "use strict";

  var FORMULAIRE = "https://59665626.sibforms.com/v2/serve/MUIFALrEewAgsgtfo-dwb_9JQPAch_GA_70rnty6I5FXP1G8XZkCKnFCXg2jgbkS3kMxU71nj9W0CD8pnV7LMwFctUPH42j0f48L7aRdz58oUqOC19FIoqG05SL6y2RPcSddU_pNkSYPkjZOEjbUowpBz8TWutOi3x84W_0KjrMg3YZPWlc-_HEFW8y9bUJagnxca5aTIfl63dqXAw==";

  var NB = " ";
  var TEXTES = {
    fr: {
      titre: "Recevez la prochaine lettre",
      chapo: "Les nouveautés du clavier, directement dans votre boîte mail.",
      champ: "Votre adresse e-mail",
      bouton: "Je m'inscris",
      mention: "Gratuit. Chaque lettre a un lien pour vous désabonner.",
      merci: "Merci, c'est fait" + NB + "! Vous recevrez la prochaine lettre dans votre boîte mail.",
      erreur: "L'inscription n'a pas abouti. Vérifiez votre adresse et réessayez."
    },
    en: {
      titre: "Get the next newsletter",
      chapo: "What's new in the keyboard, straight to your inbox.",
      champ: "Your email address",
      bouton: "Sign me up",
      mention: "Free. Every newsletter has a link to unsubscribe.",
      merci: "Thank you, you're signed up! The next newsletter will arrive in your inbox.",
      erreur: "The sign-up didn't go through. Check your address and try again."
    },
    de: {
      titre: "Den nächsten Newsletter erhalten",
      chapo: "Die Neuigkeiten der Tastatur direkt in Ihrem Postfach.",
      champ: "Ihre E-Mail-Adresse",
      bouton: "Anmelden",
      mention: "Kostenlos. Jeder Newsletter enthält einen Link zum Abmelden.",
      merci: "Danke, Sie sind angemeldet! Der nächste Newsletter kommt in Ihr Postfach.",
      erreur: "Die Anmeldung hat nicht geklappt. Prüfen Sie Ihre Adresse und versuchen Sie es erneut."
    },
    pt: {
      titre: "Receba a próxima newsletter",
      chapo: "As novidades do teclado, diretamente na sua caixa de correio.",
      champ: "O seu endereço de e-mail",
      bouton: "Inscrever-me",
      mention: "Gratuito. Cada newsletter tem uma ligação para cancelar a inscrição.",
      merci: "Obrigado! A próxima newsletter vai chegar à sua caixa de correio.",
      erreur: "A inscrição não foi concluída. Verifique o seu endereço e tente novamente."
    }
  };

  /* Les couleurs suivent celles de la page quand elle en déclare (les
     newsletters), sinon des valeurs proches ; le mode sombre est celui du
     système ou du bouton du site (data-theme). */
  var STYLE =
    ".inscription-lettre{--il-fond:var(--papier,#fff);--il-encre:var(--encre,#17212b);--il-doux:var(--doux,#56636f);--il-filet:var(--filet,#dbe3ea);--il-rouge:#ed2939;--il-vert:#1f7a3f;" +
    "font-family:var(--display,inherit);background:var(--il-fond);color:var(--il-encre);border:1px solid var(--il-filet);border-top:4px solid var(--il-rouge);border-radius:10px;padding:20px 22px;margin:24px 0}" +
    "@media (prefers-color-scheme:dark){:root:not([data-theme=light]) .inscription-lettre{--il-fond:var(--papier,#151d25);--il-encre:var(--encre,#e8eef3);--il-doux:var(--doux,#9aa8b4);--il-filet:var(--filet,#2a3641);--il-vert:#5fd38a}}" +
    ":root[data-theme=dark] .inscription-lettre{--il-fond:var(--papier,#151d25);--il-encre:var(--encre,#e8eef3);--il-doux:var(--doux,#9aa8b4);--il-filet:var(--filet,#2a3641);--il-vert:#5fd38a}" +
    ".inscription-lettre h2{font-family:var(--display,inherit);font-size:22px;font-weight:800;line-height:1.2;margin:0 0 4px;border:0;padding:0;color:inherit}" +
    ".inscription-lettre p{margin:0;color:var(--il-doux);font-size:16px;line-height:1.5}" +
    ".inscription-lettre form{display:flex;flex-wrap:wrap;gap:10px;margin:14px 0 10px}" +
    ".inscription-lettre form[hidden]{display:none}" +
    ".inscription-lettre input[type=email]{flex:1 1 220px;min-width:0;font:inherit;font-size:16px;line-height:1.2;padding:12px 14px;border:1px solid var(--il-filet);border-radius:8px;background:var(--fond,#fff);color:var(--il-encre)}" +
    ".inscription-lettre input[type=email]:focus{outline:3px solid #00a1de;outline-offset:1px}" +
    ".inscription-lettre button{flex:0 0 auto;font:inherit;font-size:16px;font-weight:700;line-height:1;padding:12px 20px;border:0;border-radius:8px;background:var(--il-rouge);color:#fff;cursor:pointer}" +
    ".inscription-lettre button[disabled]{opacity:.6;cursor:wait}" +
    ".inscription-lettre .il-piege{position:absolute;left:-9999px;width:1px;height:1px;overflow:hidden}" +
    ".inscription-lettre .il-mention{font-size:14px}" +
    ".inscription-lettre .il-reponse{font-weight:700;margin-top:8px}" +
    ".inscription-lettre .il-reponse.ok{color:var(--il-vert)}" +
    ".inscription-lettre .il-reponse.ko{color:var(--il-rouge)}";

  function langue(el) {
    var porteur = el.closest("[lang]");
    var l = ((porteur && porteur.getAttribute("lang")) || "fr").slice(0, 2).toLowerCase();
    return TEXTES[l] ? l : "fr";
  }

  function monter(boite) {
    var l = langue(boite), t = TEXTES[l];
    boite.innerHTML =
      "<h2></h2><p class=\"il-chapo\"></p>" +
      "<form novalidate><input type=\"email\" name=\"EMAIL\" autocomplete=\"email\" required>" +
      "<span class=\"il-piege\" aria-hidden=\"true\"><input type=\"text\" name=\"email_address_check\" tabindex=\"-1\" autocomplete=\"off\"></span>" +
      "<button type=\"submit\"></button></form>" +
      "<p class=\"il-mention\"></p><p class=\"il-reponse\" role=\"status\" aria-live=\"polite\"></p>";
    boite.querySelector("h2").textContent = t.titre;
    boite.querySelector(".il-chapo").textContent = t.chapo;
    var champ = boite.querySelector("input[type=email]");
    champ.placeholder = t.champ;
    champ.setAttribute("aria-label", t.champ);
    var bouton = boite.querySelector("button");
    bouton.textContent = t.bouton;
    boite.querySelector(".il-mention").textContent = t.mention;
    var reponse = boite.querySelector(".il-reponse");
    var form = boite.querySelector("form");

    function dire(texte, ok) {
      reponse.textContent = texte;
      reponse.className = "il-reponse " + (ok ? "ok" : "ko");
    }

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var adresse = champ.value.trim();
      if (!champ.checkValidity() || !adresse) { dire(t.erreur, false); champ.focus(); return; }
      var donnees = new URLSearchParams();
      donnees.append("EMAIL", adresse);
      donnees.append("email_address_check", form.elements.email_address_check.value);
      donnees.append("locale", l);
      bouton.disabled = true;
      fetch(FORMULAIRE + "?isAjax=1", { method: "POST", body: donnees })
        .then(function (r) { return r.ok ? r.json() : { success: false }; })
        .then(function (j) {
          if (j && j.success) { form.hidden = true; dire(t.merci, true); }
          else { dire(t.erreur, false); }
        })
        .catch(function () { dire(t.erreur, false); })
        .then(function () { bouton.disabled = false; });
    });
  }

  function demarrer() {
    var boites = document.querySelectorAll(".inscription-lettre");
    if (!boites.length) return;
    var s = document.createElement("style");
    s.textContent = STYLE;
    document.head.appendChild(s);
    for (var i = 0; i < boites.length; i++) monter(boites[i]);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", demarrer);
  else demarrer();
})();
