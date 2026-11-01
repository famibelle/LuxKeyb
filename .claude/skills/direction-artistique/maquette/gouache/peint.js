// La variante « peinture sur carton » du Mëllerdall : chaque pièce est un
// carton brun découpé, peint à la gouache par coups de pinceau, et la
// peinture s'arrête un peu avant le bord, qui laisse voir le carton.

// La boîte d'un chemin SVG en coordonnées absolues : toutes les paires x y.
function boiteDe(d) {
  const n = (d.match(/-?\d*\.?\d+/g) || []).map(Number);
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
  for (let i = 0; i + 1 < n.length; i += 2) {
    x0 = Math.min(x0, n[i]); x1 = Math.max(x1, n[i]); y0 = Math.min(y0, n[i + 1]); y1 = Math.max(y1, n[i + 1]);
  }
  return [x0, y0, x1, y1];
}

Peintre.prototype.kraft = function () {
  const P = this.P;
  return melange(0xFFB08A5A, P.pres, P.nuit ? 0.55 : 0.18);
};

// Une pièce de carton peint. [dir] : l'angle des coups de pinceau (0 :
// horizontaux, π/2 : verticaux). [boite] : où semer les coups.
// [union] : une forme faite de plusieurs morceaux qui se chevauchent (un
// feuillage de ronds) ; son bord de carton se pose à l'extérieur seulement.
Peintre.prototype.peint = function (d, col, dir = 0, boite = null, ombre = 1, union = false) {
  if (!this.actif) return;
  const c = this.c, k = c.getTransform().a, P = this.P;
  const path = typeof d === 'string' ? new Path2D(d) : d;
  const [bx0, by0, bx1, by1] = boite || boiteDe(d);
  const kraft = this.kraft();
  // 1. Le carton, avec son ombre (la rampe : elle monte).
  c.save();
  c.shadowColor = `rgba(11,16,32,${(P.nuit ? 0.5 : 0.32) * ombre})`;
  c.shadowBlur = 1.1 * k; c.shadowOffsetY = -0.55 * k;
  c.fillStyle = css(kraft); c.fill(path);
  if (union) { c.strokeStyle = css(kraft); c.lineWidth = 0.9; c.stroke(path); }
  c.restore();
  // 2. La gouache, dans la pièce seulement.
  c.save();
  c.clip(path);
  c.fillStyle = css(col, 0.84); c.fill(path);
  // 3. Les coups de pinceau : de courtes touches un peu plus claires ou
  // plus sombres, chacune faite de trois poils.
  const r = Alea(Math.round((bx0 * 131 + by0 * 71 + bx1 * 17 + by1) * 10) | 0);
  const aire = Math.max(1, (bx1 - bx0) * (by1 - by0));
  const n = Math.min(700, Math.round(aire * 0.55));
  c.lineCap = 'round';
  for (let i = 0; i < n; i++) {
    const x = bx0 + r() * (bx1 - bx0), y = by0 + r() * (by1 - by0);
    const a = dir + (r() - 0.5) * (dir === 1.0 ? 1.4 : 0.5), l = (dir === 1.0 ? 1.5 + r() * 3 : 2.5 + r() * 6), w = 0.9 + r() * 1.3;
    const t = (r() - 0.5) * 0.34;
    const v = t > 0 ? melange(col, 0xFFFFFFFF, t * 1.4) : melange(col, 0xFF000000, -t);
    const dx = Math.cos(a) * l / 2, dy = Math.sin(a) * l / 2, px = -Math.sin(a), py = Math.cos(a);
    for (let b = -1; b <= 1; b++) {
      c.strokeStyle = css(v, 0.4 + r() * 0.3);
      c.lineWidth = w * 0.45;
      const ox = px * b * w * 0.35, oy = py * b * w * 0.35;
      c.beginPath();
      c.moveTo(x - dx + ox, y - dy + oy);
      c.quadraticCurveTo(x + ox + px * (r() - 0.5) * 0.6, y + oy + py * (r() - 0.5) * 0.6, x + dx + ox, y + dy + oy);
      c.stroke();
    }
  }
  if (union) { c.restore(); return; }
  // 4. Le bord sec : le pinceau n'a pas couvert jusqu'à la découpe, le
  // carton reparaît par endroits.
  c.setLineDash([0.6 + r() * 1.4, 0.4 + r() * 1.2, 1.5 + r(), 0.8, 0.3, 1.1]);
  c.strokeStyle = css(kraft, 0.85); c.lineWidth = 0.9; c.stroke(path);
  c.setLineDash([]);
  c.strokeStyle = css(kraft, 0.35); c.lineWidth = 1.3; c.stroke(path);
  c.restore();
};

Peintre.prototype.rondPeint = function (cx, cy, rr, col, ombre = 0.8) {
  const p = new Path2D(); p.arc(cx, cy, rr, 0, Math.PI * 2);
  this.peint(p, col, -0.6, [cx - rr, cy - rr, cx + rr, cy + rr], ombre);
};

Peintre.prototype.feuillagePeint = function (x0, x1, y, taille, graine, col) {
  const r = Alea(graine), ronds = [];
  for (let x = x0; x <= x1; x += taille * 1.05) {
    const rr = taille * (0.65 + 0.5 * r()), cx = x + r() * taille * 0.4, cy = y - rr * 0.35 + r() * 1.2;
    ronds.push([cx, cy, rr]);
  }
  for (const [cx, cy, rr] of ronds) this.rondPeint(cx, cy - rr * 0.35, rr, melange(col, 0xFF000000, 0.12));
  for (const [cx, cy, rr] of ronds) this.rondPeint(cx + rr * 0.25, cy + rr * 0.25, rr * 0.82, melange(col, 0xFFFFFFFF, 0.1));
};

// Le ciel peint : le dégradé, puis de larges touches horizontales.
Peintre.prototype.cielPeint = function () {
  this.ciel();
  const c = this.c, P = this.P, r = Alea(5);
  c.save(); c.lineCap = 'round';
  for (let i = 0; i < 260; i++) {
    const x = r() * 110 - 5, y = r() * 46, l = 6 + r() * 12;
    const base = y < 23 ? melange(P.cielHaut, P.cielBas, y / 46) : melange(P.cielHaut, P.cielBas, y / 40);
    const v = r() < 0.5 ? melange(base, 0xFFFFFFFF, 0.12) : melange(base, 0xFF000000, 0.05);
    c.strokeStyle = css(v, 0.3 + r() * 0.2); c.lineWidth = 1.4 + r() * 2;
    c.beginPath(); c.moveTo(x, y); c.quadraticCurveTo(x + l / 2, y + (r() - 0.5) * 0.8, x + l, y + (r() - 0.5) * 0.6); c.stroke();
  }
  c.restore();
  // L'astre se peint après le ciel, d'un seul aplat.
  if (!P.nuit) this.rond(P.astreX, P.astreY, P.astreR, P.astre, 0.95);
};

Peintre.prototype.nuagePeint = function (x, y, l) {
  const P = this.P, s = l / 20;
  const col = melange(P.cielHaut, 0xFFFFFFFF, P.nuit ? 0.3 : 0.85);
  const t = new Path2D();
  t.moveTo(x, y + 4 * s); t.lineTo(x + 20 * s, y + 4 * s);
  t.arc(x + 17 * s, y + 3 * s, 3 * s, 0.3, -Math.PI * 0.7, true);
  t.arc(x + 11.5 * s, y + 1.2 * s, 4.4 * s, -0.2, -Math.PI * 0.95, true);
  t.arc(x + 5 * s, y + 2.6 * s, 3.2 * s, -0.4, -Math.PI * 1.05, true);
  t.closePath();
  this.peint(t, col, 0, [x, y - 4 * s, x + 20 * s, y + 4 * s], 1.2);
};

Peintre.prototype.mellerdallPeint = function () {
  const P = this.P, mix = melange, r = Alea(10);
  // La crête lointaine, découpée dans un seul carton.
  {
    const a = Alea(33), p1 = a() * 6, p2 = a() * 6;
    let d = 'M0 66';
    for (let x = 0; x <= 100; x += 2) d += `L${x} ${(30 + Math.sin(x * 0.07 + p1) * 2.2 + Math.sin(x * 0.19 + p2) * 1).toFixed(2)}`;
    d += 'L100 66Z';
    this.peint(d, P.loin, 0, [0, 26, 100, 66], 0.6);
  }
  this.nuagePeint(60, 6, 17);
  this.nuagePeint(30, 19.5, 10);
  this.feuillagePeint(0, 100, 34, 2.2, 34, mix(P.loin, P.milieu, 0.5));
  this.plan(1);
  const roc = mix(P.pierre, 0xFFB48A55, P.nuit ? 0.15 : 0.32);
  // La face à l'ombre tirée vers la tuile plutôt que vers le vert : du grès, pas du kaki.
  const roco = mix(roc, P.toit, 0.38);
  const clair = mix(roc, 0xFFFFFFFF, 0.22);
  const mousse = mix(P.milieu, 0xFF4F7D3A, P.nuit ? 0.1 : 0.35);
  // Les rochers : des touches obliques et courtes, du grès tamponné, pas des planches.
  const V = 1.0;
  this.peint('M0 66L0 13C4 9 12 9 16 13L18 30C17 40 20 52 23 66Z', roc, V);
  this.peint('M11 12C14 12 16 13 16 13L18 30C17 40 20 52 23 66L15 66C14 50 13 30 11 12Z', mix(roc, roco, 0.6), V, null, 0.7);
  this.peint('M1 14C4 11 8 10.5 10 11.5L9 28C8 38 9 50 10 66L3 66C3 50 2 30 1 14Z', clair, V, null, 0.5);
  this.peint('M100 66L100 17C96 11 86 11 82 17L80 33C82 44 78 55 79 66Z', roc, V);
  this.peint('M82 17L80 33C82 44 78 55 79 66L86 66C85 50 86 30 84 15Z', mix(roc, roco, 0.6), V, null, 0.7);
  this.peint('M99 18C97 14 93 13 91 13.5L92 30C93 42 92 54 92 66L98 66C98 50 99 32 99 18Z', clair, V, null, 0.5);
  for (const y of [24, 37, 50]) {
    this.peint(`M0 ${y}L${16 + y * 0.08} ${y + 0.6}L${16.4 + y * 0.08} ${y + 2.4}L0 ${y + 2}Z`, mix(roc, roco, 0.35), 0, null, 0.6);
    this.peint(`M${81 - y * 0.02} ${y + 1}L100 ${y + 1.6}L100 ${y + 3.4}L${80.6 - y * 0.02} ${y + 3}Z`, mix(roc, roco, 0.35), 0, null, 0.6);
  }
  this.feuillagePeint(0, 16, 12, 2.2, 35, mousse);
  this.feuillagePeint(83, 100, 15, 2.2, 36, mousse);
  this.plan(2);
  // La cascade derrière le mot : peinte, mais sans rien y ajouter.
  this.peint('M36 33L64 33L64 38.5L56 38.5C56 35.6 44 35.6 44 38.5L36 38.5Z', mix(P.pierre, P.pres, 0.2));
  const blanc = P.nuit ? 0xFF9FB3D8 : 0xFFFFFFFF;
  this.rect(44.6, 37.5, 10.8, 7, blanc, 0.85);
  this.peint('M38 44.5L62 44.5L64 46.5L36 46.5Z', roco);
  this.rect(40, 46.5, 20, 6, blanc, 0.8);
  this.peint('M32 52.5L68 52.5L70 54.5L30 54.5Z', roco);
  this.rect(34, 54.5, 32, 4.5, blanc, 0.75);
  const ecume = mix(P.eau, 0xFFFFFFFF, 0.5);
  for (let k = 0; k < 14; k++) {
    const x = 40 + r() * 20;
    this.rect(x, 38 + r() * 18, 0.3, 2 + r() * 3, ecume, 0.8);
  }
  // L'eau : un carton peint à l'horizontale, puis deux bandes de vagues.
  this.peint('M-1 59L101 59L101 67L-1 67Z', P.eau, 0, null, 0.5);
  const w = Alea(37);
  for (let k = 0; k < 2; k++) {
    const yy = 60.8 + k * 2.6, col = melange(P.eau, 0xFFFFFFFF, 0.28 - k * 0.08);
    let x = -1 - w() * 4, d = `M-1 67L${x} ${yy}`;
    while (x < 101) { const l = 4 + w() * 3; d += `Q${x + l / 2} ${yy - 0.9} ${x + l} ${yy}`; x += l; }
    d += 'L101 67Z';
    this.peint(d, col, 0, [-1, yy - 1, 101, 67], 0.6);
  }
  this.peint('M0 66C4 58 10 56 16 60C14 62 12 64 12 66Z', mix(mousse, 0xFF000000, 0.12), -0.6);
  this.peint('M0 66C3 61 8 59.5 12 62C11 63.5 10 65 10 66Z', mix(mousse, 0xFFFFFFFF, 0.1), -0.6, null, 0.7);
  this.peint('M100 66C96 58 90 56 84 60C86 62 88 64 88 66Z', mix(mousse, 0xFF000000, 0.12), 0.6);
  this.peint('M100 66C97 61 92 59.5 88 62C89 63.5 90 65 90 66Z', mix(mousse, 0xFFFFFFFF, 0.1), 0.6, null, 0.7);
};
