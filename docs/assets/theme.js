(function () {
  "use strict";

  var STORAGE_KEY = "kreyol-theme";
  var media = window.matchMedia("(prefers-color-scheme: dark)");

  function effectiveTheme() {
    var stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "dark" || stored === "light") return stored;
    return media.matches ? "dark" : "light";
  }

  function paintToggles(theme) {
    var toggles = document.querySelectorAll(".theme-toggle");
    for (var i = 0; i < toggles.length; i++) {
      var btn = toggles[i];
      if (theme === "dark") {
        btn.textContent = "☀️";
        btn.setAttribute("aria-label", "Passer en mode clair");
      } else {
        btn.textContent = "🌙";
        btn.setAttribute("aria-label", "Passer en mode sombre");
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

    // Les menus de la barre de navigation sont des <details> : ils s'ouvrent et
    // se ferment tout seuls, mais rien ne les referme quand on clique ailleurs.
    document.addEventListener("click", function (event) {
      var menus = document.querySelectorAll("nav.site details.menu[open]");
      for (var i = 0; i < menus.length; i++) {
        if (!menus[i].contains(event.target)) menus[i].removeAttribute("open");
      }
    });

    document.addEventListener("keydown", function (event) {
      if (event.key !== "Escape") return;
      var menus = document.querySelectorAll("nav.site details.menu[open]");
      for (var i = 0; i < menus.length; i++) menus[i].removeAttribute("open");
    });
  });

  media.addEventListener("change", function () {
    if (localStorage.getItem(STORAGE_KEY)) return; // préférence explicite, ne pas écraser
    applyTheme(effectiveTheme());
  });
})();
