/* Rendu de la page des nouveautés, commun à nouveautes.html et à ses
   traductions. Chaque page déclare avant ce script, dans window.NV, le fichier
   à lire et ses quelques mots d'interface ; le contenu des versions vient du
   JSON écrit par docs/scripts/generate_nouveautes.py. */
(function () {
  var NV = window.NV || {};
  var LOCALE = NV.locale || 'fr-FR';
  var T = NV.textes || {};
  var $ = function (id) { return document.getElementById(id); };

  function el(tag, cls, html) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
  }

  function dateLisible(iso) {
    return new Date(iso + 'T00:00:00').toLocaleDateString(LOCALE,
      { day: 'numeric', month: 'long', year: 'numeric' });
  }

  function rendreTableau(t) {
    var wrap = el('div', 'table-scroll');
    var table = el('table');
    var thead = el('thead');
    var tr = el('tr');
    t.entetes.forEach(function (h) { tr.appendChild(el('th', null, h)); });
    thead.appendChild(tr);
    var tbody = el('tbody');
    t.lignes.forEach(function (l) {
      var r = el('tr');
      l.forEach(function (c) { r.appendChild(el('td', null, c)); });
      tbody.appendChild(r);
    });
    table.appendChild(thead);
    table.appendChild(tbody);
    wrap.appendChild(table);
    return wrap;
  }

  function rendreVersion(v, premiere) {
    var d = el('details', 'version');
    if (premiere) d.open = true;

    var nb = v.sections.reduce(function (n, s) { return n + s.points.length; }, 0);
    var s = el('summary');
    s.appendChild(el('span', 'chev', '▸'));
    s.appendChild(el('span', 'num', 'v' + v.version));
    s.appendChild(el('span', 'date', dateLisible(v.date)));
    if (premiere) s.appendChild(el('span', 'badge-new', T.plusRecente));
    s.appendChild(el('span', 'count',
      nb + ' ' + (nb > 1 ? T.changements : T.changement)));
    d.appendChild(s);

    var body = el('div', 'body');
    // Version pas encore traduite : le générateur l'a laissée en français
    // plutôt que de la sauter, et la page le dit.
    if (v.langue && v.langue !== document.documentElement.lang) {
      var n = el('p', 'intro', T.nonTraduite);
      n.lang = document.documentElement.lang;
      body.appendChild(n);
      body.lang = v.langue;
    }
    if (v.intro) body.appendChild(el('p', 'intro', v.intro));

    // Une version peut porter plusieurs captures. « images » est la liste ;
    // les champs « image » restent lus pour les fichiers produits avant elle.
    var vues = v.images;
    if (!vues && v.image) {
      vues = [{ image: v.image, alt: v.image_alt, legende: v.image_legende }];
    }
    (vues || []).forEach(function (vue) {
      var fig = el('figure', 'shot');
      var img = el('img');
      img.src = vue.image;
      img.alt = vue.alt || '';
      img.loading = 'lazy';
      fig.appendChild(img);
      if (vue.legende) fig.appendChild(el('figcaption', null, vue.legende));
      body.appendChild(fig);
    });

    v.sections.forEach(function (sec) {
      var g = el('div', 'grp');
      var h = el('h3');
      if (sec.emoji) h.appendChild(el('span', 'e', sec.emoji));
      h.appendChild(el('span', null, sec.titre));
      g.appendChild(h);
      if (sec.tableau) g.appendChild(rendreTableau(sec.tableau));
      if (sec.texte) g.appendChild(el('p', null, sec.texte));
      sec.points.forEach(function (p) {
        var pt = el('div', 'pt');
        if (p.titre) pt.appendChild(el('h4', null, p.titre));
        if (p.texte) pt.appendChild(el('p', null, p.texte));
        g.appendChild(pt);
      });
      body.appendChild(g);
    });

    d.appendChild(body);
    return d;
  }

  fetch(NV.source || 'stats/nouveautes.json')
    .then(function (r) {
      if (!r.ok) throw new Error(r.status);
      return r.json();
    })
    .then(function (d) {
      $('nv-version').textContent = 'v' + d.derniere_version;
      $('nv-date').textContent = T.publiee + ' ' + dateLisible(d.derniere_date);
      $('nv-maj').textContent = T.aJour + ' ' + dateLisible(d.genere_le);

      var liste = $('nv-liste');
      liste.innerHTML = '';
      d.versions.forEach(function (v, i) {
        liste.appendChild(rendreVersion(v, i === 0));
      });

      liste.appendChild(el('p', 'muted', T.plusAnciennes));
    })
    .catch(function () {
      $('nv-liste').innerHTML =
        '<div class="card"><p class="loading">' + T.indisponible + '</p></div>';
    });
})();
