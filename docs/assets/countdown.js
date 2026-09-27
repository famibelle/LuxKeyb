/* Décompte jusqu'à la sortie publique sur Google Play.
   Remplit tout bloc `[data-sortie]` de la page ; l'accueil et la page
   ambassadeurs partagent donc un seul calcul et une seule date.

   La date vient de stats/testeurs.json et non du HTML : elle est écrite à un
   seul endroit, et les deux pages ne peuvent pas se contredire. Le HTML porte
   quand même la date en toutes lettres, qui reste affichée si le fichier ne
   se charge pas : la page dit alors moins de choses, jamais des fausses. */
(function () {
  var script = document.currentScript;

  // Résolue depuis l'URL du script plutôt que depuis celle de la page : le
  // chemin reste juste quelle que soit la profondeur de la page qui l'appelle.
  var source = new URL('../stats/testeurs.json', script.src).href;

  var blocs = [].slice.call(document.querySelectorAll('[data-sortie]'));
  if (!blocs.length) return;

  function deuxChiffres(n) { return n < 10 ? '0' + n : String(n); }

  function peindre(cible) {
    var reste = cible - new Date();

    blocs.forEach(function (bloc) {
      if (reste <= 0) {
        bloc.setAttribute('data-etat', 'sortie');
        return;
      }

      var s = Math.floor(reste / 1000);
      var valeurs = {
        jours: Math.floor(s / 86400),
        heures: deuxChiffres(Math.floor(s % 86400 / 3600)),
        minutes: deuxChiffres(Math.floor(s % 3600 / 60)),
        secondes: deuxChiffres(s % 60)
      };

      Object.keys(valeurs).forEach(function (unite) {
        var cellule = bloc.querySelector('[data-unite="' + unite + '"]');
        if (cellule) cellule.textContent = valeurs[unite];
      });
    });

    return reste > 0;
  }

  fetch(source)
    .then(function (r) { return r.json(); })
    .then(function (data) {
      var cible = new Date(data.sortie_publique);
      if (isNaN(cible)) return;

      if (peindre(cible)) {
        // Une seconde : c'est le pas de la plus petite unité affichée. Le
        // décompte n'est pas annoncé aux lecteurs d'écran (les chiffres sont
        // en aria-hidden, la phrase à côté porte la date), sans quoi il
        // parlerait par-dessus la lecture de la page une fois par seconde.
        setInterval(function () { peindre(cible); }, 1000);
      }
    })
    .catch(function () { /* la date en toutes lettres reste affichée */ });
})();
