(function () {
  "use strict";

  var STORAGE_KEY = "kreyol-theme";
  var media = window.matchMedia("(prefers-color-scheme: dark)");

  function effectiveTheme() {
    var stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "dark" || stored === "light") return stored;
    return media.matches ? "dark" : "light";
  }

  // Les libellés suivent la langue de la page (en.md, de.md, pt.md et les
  // pages traduites portent la leur) ; le français reste le défaut.
  var LIBELLES = {
    fr: { clair: "Fond clair", sombre: "Fond sombre",
          versClair: "Passer en mode clair", versSombre: "Passer en mode sombre" },
    en: { clair: "Light", sombre: "Dark",
          versClair: "Switch to light mode", versSombre: "Switch to dark mode" },
    de: { clair: "Hell", sombre: "Dunkel",
          versClair: "Zum hellen Modus wechseln", versSombre: "Zum dunklen Modus wechseln" },
    pt: { clair: "Claro", sombre: "Escuro",
          versClair: "Mudar para o modo claro", versSombre: "Mudar para o modo escuro" }
  };

  function libelles() {
    var lang = (document.documentElement.lang || "fr").slice(0, 2).toLowerCase();
    return LIBELLES[lang] || LIBELLES.fr;
  }

  function paintToggles(theme) {
    var t = libelles();
    var toggles = document.querySelectorAll(".theme-toggle");
    for (var i = 0; i < toggles.length; i++) {
      var btn = toggles[i];
      // Épinglé en coin de page, le bouton reste une pastille ; dans la
      // barre de navigation il porte son nom, une icône seule ne se
      // devinant pas.
      var pastille = btn.classList.contains("fixed");
      if (theme === "dark") {
        btn.textContent = pastille ? "☀️" : "☀️ " + t.clair;
        btn.setAttribute("aria-label", t.versClair);
      } else {
        btn.textContent = pastille ? "🌙" : "🌙 " + t.sombre;
        btn.setAttribute("aria-label", t.versSombre);
      }
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    paintToggles(theme);
  }

  document.addEventListener("DOMContentLoaded", function () {
    applyTheme(effectiveTheme());

    document.addEventListener("click", function (event) {
      var btn = event.target.closest(".theme-toggle");
      if (!btn) return;
      var next = effectiveTheme() === "dark" ? "light" : "dark";
      localStorage.setItem(STORAGE_KEY, next);
      applyTheme(next);
    });
  });

  media.addEventListener("change", function () {
    if (localStorage.getItem(STORAGE_KEY)) return; // préférence explicite, ne pas écraser
    applyTheme(effectiveTheme());
  });
})();
