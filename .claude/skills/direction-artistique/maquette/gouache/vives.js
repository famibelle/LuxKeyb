// Les quatre heures en couleurs vives, d'après la vitrine en papier
// (ciel bleu, rose puis jaune ; verts francs ; eau soutenue), et le mode
// « gouache » : les grandes pièces deviennent des cartons peints.

const VIVES = [
  { rang: 0, cielHaut: 0xFF7EC3F2, cielMilieu: 0xFFF5C3DA, cielBas: 0xFFFFE9A6, astre: 0xFFFFE066, astreX: 22, astreY: 15, astreR: 6,
    loin: 0xFF6F9BEA, milieu: 0xFF5DBB63, pres: 0xFF2F8F5B, sol: 0xFF2A6B45, pierre: 0xFFF4CF94, toit: 0xFFE0674A, fenetre: 0xFF4B6A8A, allume: false, eau: 0xFF3E9DEB, nuit: false },
  { rang: 1, cielHaut: 0xFFFFA94D, cielMilieu: 0xFFFFD27A, cielBas: 0xFFFFF0B8, astre: 0xFFFFF39A, astreX: 78, astreY: 13, astreR: 7,
    loin: 0xFFE38B5C, milieu: 0xFF9CC43F, pres: 0xFF4E8A2E, sol: 0xFF3B5E22, pierre: 0xFFFFD9A0, toit: 0xFFD9503A, fenetre: 0xFF6A4A2E, allume: false, eau: 0xFF2FA8DE, nuit: false },
  { rang: 2, cielHaut: 0xFF4B3BA8, cielMilieu: 0xFFFF6FA8, cielBas: 0xFFFFB25C, astre: 0xFFFFD36A, astreX: 68, astreY: 31, astreR: 10,
    loin: 0xFF9B5FB8, milieu: 0xFF6B3F8F, pres: 0xFF3E2766, sol: 0xFF2A1A48, pierre: 0xFFF0A890, toit: 0xFF8A2F5A, fenetre: 0xFFFFCD48, allume: true, eau: 0xFFE8789A, nuit: false },
  { rang: 3, cielHaut: 0xFF0B1450, cielMilieu: null, cielBas: 0xFF2D4BB0, astre: 0xFFFFF4C8, astreX: 79, astreY: 12, astreR: 4.6,
    loin: 0xFF2F4DA0, milieu: 0xFF22357F, pres: 0xFF16245A, sol: 0xFF0C1638, pierre: 0xFF6A79B8, toit: 0xFF3A3F7A, fenetre: 0xFFFFD45A, allume: true, eau: 0xFF2D4FA8, nuit: true },
].map(o => { for (const k in o) if (typeof o[k] === 'number' && o[k] > 0xFFFFFF) o[k] = hx(o[k]); return o; });

// Le mode gouache : forme() et rect() peignent un carton quand la pièce est
// assez grande pour qu'on y voie les coups de pinceau ; les petits détails
// (fenêtres, joints, lanternes) restent des aplats nets.
const formeNette = Peintre.prototype.forme, rectNet = Peintre.prototype.rect;
Peintre.prototype.forme = function (d, col, o = 1) {
  if (!this.gouache || o < 0.95) return formeNette.call(this, d, col, o);
  const b = typeof d === 'string' ? boiteDe(d) : (this.boite || [0, 0, 100, 66]);
  const aire = (b[2] - b[0]) * (b[3] - b[1]);
  if (aire < 14) return formeNette.call(this, d, col, o);
  const g = this.gouache; this.gouache = false;
  this.peint(d, col, (b[3] - b[1]) > (b[2] - b[0]) * 1.4 ? 1.0 : 0, b);
  this.gouache = g;
};
Peintre.prototype.rect = function (x, y, w, h, col, o = 1) {
  if (!this.gouache || o < 0.95 || w * h < 14) return rectNet.call(this, x, y, w, h, col, o);
  this.forme(`M${x} ${y}L${x + w} ${y}L${x + w} ${y + h}L${x} ${y + h}Z`, col, o);
};

// Les façades du Grund, de toutes les couleurs, réglées pour chaque heure.
const FACADES = [
  [0xFFF7A8B8, 0xFFFFD66B, 0xFF8EC9F0, 0xFF9BDBA8, 0xFFFFB070],
  [0xFFF59A8C, 0xFFFFC94D, 0xFF7FC4E8, 0xFFA7D67A, 0xFFFF9E5A],
  [0xFFD9789A, 0xFFE8A85A, 0xFF8A86C8, 0xFF7FA88C, 0xFFE08A6A],
  [0xFF4F5C9E, 0xFF6A6AA8, 0xFF3E5A9A, 0xFF4A7088, 0xFF5A5A90],
].map(l => l.map(hx));
VIVES.forEach((P, i) => { P.facades = FACADES[i]; });
