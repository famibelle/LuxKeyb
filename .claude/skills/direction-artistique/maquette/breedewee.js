// Le Breedewee : la porte de pierre en cadre de scène, la ruelle derrière,
// la ville haute au loin, la chaussée pavée qui entre sous l'arche.
// Écrit pour se recopier tel quel dans Peintre.breedewee().

// Les cotes de la porte, partagées avec les coulisses.
const PORTE = { cx: 50, naissance: 30, rIn: 13.5, rOut: 19.5, seuil: 55 };

Peintre.prototype.arche = function (cx, y, rIn, rOut, seuil) {
  const t = new Path2D();
  t.moveTo(cx - rOut, seuil); t.lineTo(cx - rOut, y);
  t.arc(cx, y, rOut, Math.PI, 0, false);
  t.lineTo(cx + rOut, seuil); t.lineTo(cx + rIn, seuil); t.lineTo(cx + rIn, y);
  t.arc(cx, y, rIn, 0, Math.PI, true);
  t.lineTo(cx - rIn, seuil); t.closePath();
  return t;
};

Peintre.prototype.lanterne = function (x, y, versDroite) {
  const P = this.P, s = versDroite ? 1 : -1;
  const fer = melange(P.toit, 0xFF000000, 0.45);
  this.ligne(x, y, x + 3.6 * s, y, fer, 0.55);
  this.trait(`M${x} ${y + 2.2}Q${x + 1.6 * s} ${y + 2} ${x + 2.6 * s} ${y}`, fer, 0.4);
  if (P.allume) this.rond(x + 3.6 * s, y + 2.3, 2.6, P.fenetre, 0.25);
  this.ligne(x + 3.6 * s, y, x + 3.6 * s, y + 0.9, fer, 0.4);
  this.forme(`M${x + 2.8 * s} ${y + 0.9}L${x + 4.4 * s} ${y + 0.9}L${x + 4.1 * s} ${y + 3.6}L${x + 3.1 * s} ${y + 3.6}Z`,
    P.allume ? P.fenetre : melange(P.pierre, P.pres, 0.55));
  this.rect(x + 2.6 * s - (s < 0 ? 2 : 0), y + 0.5, 2, 0.5, fer);
};

Peintre.prototype.breedewee = function () {
  const P = this.P, mix = melange, r = Alea(12);
  const pierre = P.pierre;
  const joint = mix(P.pierre, P.pres, 0.42);
  // Les pavés, plus sombres que la pierre de la porte.
  const pave = mix(pierre, P.toit, 0.55);

  // 0. La ville haute, au-dessus de la ruelle : des pignons et un clocher.
  // La pierre et les toits de l'heure : l'estompe du lointain les rapproche du ciel.
  const haute = pierre;
  const toitsHauts = P.toit;
  const pignons = [0, 7, 9, 6, 9, 12, 15, 8, 8, 23, 7, 10, 30, 9, 8, 39, 6, 11, 45, 10, 9, 66, 8, 11, 74, 9, 8, 83, 7, 10, 90, 10, 9];
  for (let i = 0; i < pignons.length; i += 3) {
    const x = pignons[i], w = pignons[i + 1], h = pignons[i + 2];
    this.rect(x, 33 - h, w, h + 33, haute);
    this.triangle(x - 0.3, 33 - h, x + w / 2, 33 - h - w * 0.45, x + w + 0.3, 33 - h, toitsHauts);
    if (P.allume) for (let y = 35 - h; y < 31; y += 3) if (r() < 0.45) this.rect(x + w * 0.4, y, 0.9, 1.3, P.fenetre, 0.8);
  }
  // L'église, dans le creux des pignons : son clocher passe derrière la clé de la porte.
  this.rect(55, 22, 11, 44, haute);
  this.triangle(54.6, 22, 60.5, 17.5, 66.4, 22, toitsHauts);
  this.rect(57.5, 12, 4, 10, haute);
  this.triangle(57.1, 12, 59.5, 4, 61.9, 12, toitsHauts);

  // 1. La ruelle derrière la porte : des façades enduites, plus claires que
  // la pierre de la porte, et une seule porte sombre au fond, dans l'axe.
  this.plan(1);
  const facades = [-1, 12, 16, 11, 11, 14, 22, 12, 17, 34, 12, 15, 46, 10, 18, 56, 12, 14, 68, 11, 17, 79, 11, 15, 90, 11, 16];
  for (let i = 0; i < facades.length; i += 3) {
    const x = facades[i], w = facades[i + 1], h = facades[i + 2];
    const enduit = mix(pierre, P.cielBas, (i / 3) % 2 === 0 ? 0.3 : 0.42);
    this.rect(x, 50 - h, w, h + 16, enduit);
    this.triangle(x - 0.4, 50 - h, x + w / 2, 50 - h - w * 0.3, x + w + 0.4, 50 - h, P.toit);
    // Dans l'axe, derrière le mot : pas de fenêtre, la porte de la ruelle.
    if (x + w > 42 && x < 58) continue;
    for (let fy = 53.5 - h; fy < 45; fy += 5)
      for (let fx = x + 2.4; fx < x + w - 2.6; fx += 4) this.fenetre(fx, fy, 1.5, 2.4, r);
  }
  this.forme('M48.4 50L48.4 45.2Q51 42.6 53.6 45.2L53.6 50Z', P.allume ? mix(P.fenetre, P.toit, 0.35) : mix(pierre, P.pres, 0.55));
  const place = mix(pierre, pave, 0.4);
  this.rect(-1, 49.5, 102, 17, place);
  this.forme('M41 66C43 59 46 53 48.6 50L53.4 50C56 53 58 59 60 66Z', pave);

  // 2. La porte et la place pavée, devant.
  this.plan(2);
  const { cx, naissance: y0, rIn, rOut, seuil } = PORTE;
  this.rect(-1, seuil, 102, 12, place);
  this.forme(`M${cx - rIn} ${seuil}L${cx + rIn} ${seuil}C${cx + rIn + 4} 59 82 62 98 66L4 66C18 62 ${cx - rIn - 2} 59 ${cx - rIn} ${seuil}Z`, pave);
  // Les rangs de pavés : quelques arcs à peine plus clairs, pas une texture.
  const rangs = mix(pave, pierre, 0.18);
  this.dash = [1.1, 0.55];
  for (let k = 0; k < 5; k++) {
    const t = (k + 0.6) / 5, y = seuil + 1 + t * 9.4;
    const x0 = (cx - rIn) + ((4) - (cx - rIn)) * t * t * 0.95 + 1.5, x1 = (cx + rIn) + (98 - (cx + rIn)) * t * t * 0.95 - 1.5;
    this.trait(`M${x0} ${y}Q${cx} ${y + 1.2 + t} ${x1} ${y}`, rangs, 0.45);
  }
  this.dash = null;
  // La porte.
  this.forme(this.arche(cx, y0, rIn, rOut, seuil), pierre);
  // Les tailloirs, qui débordent : ils font la silhouette de la porte.
  this.rect(cx - rOut - 1, y0 - 0.2, rOut - rIn + 2, 1.7, pierre);
  this.rect(cx + rIn - 1, y0 - 0.2, rOut - rIn + 2, 1.7, pierre);
  // Les claveaux, et la clé qui dépasse.
  const n = 9;
  for (let k = 1; k < n; k++) {
    const a = Math.PI + k * Math.PI / n;
    this.ligne(cx + rIn * Math.cos(a), y0 + rIn * Math.sin(a), cx + rOut * Math.cos(a), y0 + rOut * Math.sin(a), joint, 0.4);
  }
  const ac = Math.PI / n / 2, ak = 1.5 * Math.PI;
  const pt = (rr, a) => `${(cx + rr * Math.cos(a)).toFixed(2)} ${(y0 + rr * Math.sin(a)).toFixed(2)}`;
  this.forme(`M${pt(rIn - 0.6, ak - ac * 0.9)}L${pt(rOut + 1.4, ak - ac * 1.15)}L${pt(rOut + 1.4, ak + ac * 1.15)}L${pt(rIn - 0.6, ak + ac * 0.9)}Z`, pierre);
  this.trait(`M${pt(rIn - 0.6, ak - ac * 0.9)}L${pt(rOut + 1.4, ak - ac * 1.15)}M${pt(rIn - 0.6, ak + ac * 0.9)}L${pt(rOut + 1.4, ak + ac * 1.15)}`, joint, 0.4);
  // Les assises des piliers.
  for (const gauche of [true, false]) {
    const xa = gauche ? cx - rOut : cx + rIn, w = rOut - rIn;
    let decale = gauche;
    for (let y = y0 + 1.5 + 3.4; y < seuil; y += 3.4) {
      this.ligne(xa, y, xa + w, y, joint, 0.4);
      const xj = xa + w * (decale ? 0.38 : 0.62);
      this.ligne(xj, y - 3.4, xj, y, joint, 0.4);
      decale = !decale;
    }
  }
  this.lanterne(cx + rOut, y0 + 6, true);
  this.lanterne(cx - rOut, y0 + 6, false);
};

// Les coulisses du Breedewee : deux piliers de pierre à contre-jour.
Peintre.prototype.coulissesBreedewee = function () {
  const P = this.P;
  const pierre = melange(P.pierre, 0xFF000000, P.nuit ? 0.3 : 0.45);
  const joint = melange(pierre, 0xFF000000, 0.3);
  for (const droite of [false, true]) {
    const x = v => droite ? L - v : v;
    this.forme(`M${x(-1)} 66L${x(-1)} 24L${x(7.2)} 24L${x(7.2)} 26.2L${x(6.4)} 26.2L${x(6.4)} 66Z`, pierre);
    this.rect(Math.min(x(-1), x(8)), 22.4, 9, 1.8, pierre);
    for (let y = 29.6; y < 66; y += 3.4) this.ligne(x(-1), y, x(6.4), y, joint, 0.4);
    if (P.allume) this.rond(x(9.8), 33.5, 2.8, P.fenetre, 0.25);
    const fer = melange(pierre, 0xFF000000, 0.35);
    this.ligne(x(6.4), 31, x(10), 31, fer, 0.6);
    this.forme(`M${x(9)} 32L${x(10.6)} 32L${x(10.3)} 35L${x(9.3)} 35Z`, P.allume ? P.fenetre : joint);
  }
};
