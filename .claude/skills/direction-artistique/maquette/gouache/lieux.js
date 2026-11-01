// Le Mëllerdall de Paysages.kt, porté tel quel, puis sa variante « papier ».

function hsl(teinte, s, l) {
  const a = s * Math.min(l, 1 - l);
  const f = n => { const k = (n + teinte / 30) % 12; const v = l - a * Math.max(-1, Math.min(k - 3, Math.min(9 - k, 1))); return Math.min(255, Math.max(0, Math.round(v * 255))); };
  return hx((255 << 24) | (f(0) << 16) | (f(8) << 8) | f(4));
}
function heure(rang, teinte) {
  const b = Object.assign({}, BASE[rang]);
  if (teinte == null) return b;
  const t = hsl(teinte, 0.55, 0.62);
  b.cielHaut = melange(b.cielHaut, t, 0.14);
  if (b.cielMilieu != null) b.cielMilieu = melange(b.cielMilieu, t, 0.12);
  b.cielBas = melange(b.cielBas, t, 0.10);
  return b;
}

Peintre.prototype.crete = function (y0, amp, graine, col, x0 = 0, x1 = 100, f1 = 0.07, f2 = 0.19) {
  const r = Alea(graine), p1 = r() * 6, p2 = r() * 6;
  const t = new Path2D(); t.moveTo(x0, H);
  for (let x = x0; x <= x1; x += 2) t.lineTo(x, y0 + Math.sin(x * f1 + p1) * amp + Math.sin(x * f2 + p2) * amp * 0.45);
  t.lineTo(x1, H); t.closePath();
  this.forme(t, col);
};
Peintre.prototype.bosquet = function (x0, x1, y, taille, graine, col) {
  const r = Alea(graine);
  for (let x = x0; x <= x1; x += taille * 1.05) {
    const rr = taille * (0.65 + 0.5 * r()), cx = x + r() * taille * 0.4, cy = y - rr * 0.35 + r() * 1.2;
    this.rond(cx, cy, rr, col);
  }
};
Peintre.prototype.eau = function (y, graine, ...lumieres) {
  const P = this.P, r = Alea(graine);
  this.rect(0, y, 100, H - y, P.eau);
  const clair = melange(P.eau, 0xFFFFFFFF, P.nuit ? 0.18 : 0.45);
  for (let k = 0; k < 14; k++) {
    const yy = y + 1 + r() * (H - y - 1.5), xx = r() * 92, w = 3 + r() * 9;
    this.rect(xx, yy, w, 0.35, clair, 0.7);
  }
  if (P.allume) for (const x of lumieres) this.rect(x - 0.3, y + 0.6, 0.6, (H - y) * 0.7, P.fenetre, 0.45);
};

// Copie fidèle de Peintre.mellerdall().
Peintre.prototype.mellerdall = function () {
  const P = this.P, mix = melange, r = Alea(10);
  this.crete(30, 2.2, 33, P.loin);
  this.bosquet(0, 100, 34, 2.2, 34, mix(P.loin, P.milieu, 0.5));
  this.plan(1);
  const roc = mix(P.pierre, 0xFFB48A55, P.nuit ? 0.15 : 0.32);
  const roco = mix(roc, P.pres, 0.4);
  const mousse = mix(P.milieu, 0xFF4F7D3A, P.nuit ? 0.1 : 0.35);
  this.forme('M0 66L0 13C4 9 12 9 16 13L18 30C17 40 20 52 23 66Z', roc);
  this.forme('M11 12C14 12 16 13 16 13L18 30C17 40 20 52 23 66L15 66C14 50 13 30 11 12Z', roco, 0.6);
  this.forme('M100 66L100 17C96 11 86 11 82 17L80 33C82 44 78 55 79 66Z', roc);
  this.forme('M82 17L80 33C82 44 78 55 79 66L86 66C85 50 86 30 84 15Z', roco, 0.6);
  for (let y = 18; y < 64; y += 4.2) {
    this.trait(`M0 ${y}L${16 + y * 0.08} ${y + 0.5}`, roco, 0.4);
    this.trait(`M${81 - y * 0.02} ${y}L100 ${y + 0.5}`, roco, 0.4);
  }
  this.bosquet(0, 16, 12, 2.2, 35, mousse);
  this.bosquet(83, 100, 15, 2.2, 36, mousse);
  this.plan(2);
  this.forme('M36 33L64 33L64 38.5L56 38.5C56 35.6 44 35.6 44 38.5L36 38.5Z', mix(P.pierre, P.pres, 0.2));
  const blanc = P.nuit ? 0xFF9FB3D8 : 0xFFFFFFFF;
  this.rect(44.6, 37.5, 10.8, 7, blanc, 0.85);
  this.forme('M38 44.5L62 44.5L64 46.5L36 46.5Z', roco);
  this.rect(40, 46.5, 20, 6, blanc, 0.8);
  this.forme('M32 52.5L68 52.5L70 54.5L30 54.5Z', roco);
  this.rect(34, 54.5, 32, 4.5, blanc, 0.75);
  const ecume = mix(P.eau, 0xFFFFFFFF, 0.5);
  for (let k = 0; k < 14; k++) {
    const x = 40 + r() * 20;
    this.rect(x, 38 + r() * 18, 0.3, 2 + r() * 3, ecume, 0.8);
  }
  this.eau(59, 37);
  this.forme('M0 66C4 58 10 56 16 60C14 62 12 64 12 66Z', mousse);
  this.forme('M100 66C96 58 90 56 84 60C86 62 88 64 88 66Z', mousse);
};
