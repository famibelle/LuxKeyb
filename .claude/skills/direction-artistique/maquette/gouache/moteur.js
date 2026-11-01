// Portage des outils de tracé de Paysages.kt, pour mettre un lieu au point
// hors appareil. Mêmes noms, mêmes palettes, même hasard (mulberry32).
const L = 100, H = 66;
const hx = s => s >>> 0;
const BASE = [
  { rang: 0, cielHaut: 0xFFA6C6DC, cielMilieu: null, cielBas: 0xFFF3EBDC, astre: 0xFFFFF7DF, astreX: 22, astreY: 15, astreR: 6,
    loin: 0xFFB3C3C4, milieu: 0xFF8AA392, pres: 0xFF5D7964, sol: 0xFF43594A, pierre: 0xFFE4D8C2, toit: 0xFF8E5C4B, fenetre: 0xFF5B6670, allume: false, eau: 0xFFB7D0D9, nuit: false },
  { rang: 1, cielHaut: 0xFFE2A865, cielMilieu: null, cielBas: 0xFFFCE6B6, astre: 0xFFFFF3C6, astreX: 78, astreY: 13, astreR: 7,
    loin: 0xFFD6B387, milieu: 0xFFAA895A, pres: 0xFF735C39, sol: 0xFF504028, pierre: 0xFFF1D5A1, toit: 0xFFA3533A, fenetre: 0xFF6E4E33, allume: false, eau: 0xFFECCB8E, nuit: false },
  { rang: 2, cielHaut: 0xFF463A76, cielMilieu: 0xFFC66F8A, cielBas: 0xFFF7B179, astre: 0xFFFFD690, astreX: 68, astreY: 31, astreR: 10,
    loin: 0xFF8D6A8A, milieu: 0xFF5D4766, pres: 0xFF392C46, sol: 0xFF241C30, pierre: 0xFFC49A8F, toit: 0xFF5A3141, fenetre: 0xFFFFCD78, allume: true, eau: 0xFFCF8689, nuit: false },
  { rang: 3, cielHaut: 0xFF091030, cielMilieu: null, cielBas: 0xFF2A3B70, astre: 0xFFF6F1D8, astreX: 79, astreY: 12, astreR: 4.6,
    loin: 0xFF27355B, milieu: 0xFF1A2445, pres: 0xFF111930, sol: 0xFF0A1022, pierre: 0xFF4B5474, toit: 0xFF2A2F4B, fenetre: 0xFFFFD98A, allume: true, eau: 0xFF2A3A68, nuit: true },
].map(o => { for (const k in o) if (typeof o[k] === 'number' && o[k] > 0xFFFFFF) o[k] = hx(o[k]); return o; });

function melange(a, b, t) {
  const c = d => { const x = (a >>> d) & 255, y = (b >>> d) & 255; return Math.min(255, Math.max(0, Math.round(x + (y - x) * t))); };
  return hx((255 << 24) | (c(16) << 16) | (c(8) << 8) | c(0));
}
function css(col, o = 1) { return `rgba(${(col >>> 16) & 255},${(col >>> 8) & 255},${col & 255},${o})`; }

function Alea(graine) {
  let s = graine | 0;
  return () => {
    s = (s + 0x6D2B79F5) | 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), 1 | t);
    t = ((t + Math.imul(t ^ (t >>> 7), 61 | t)) | 0) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Les couleurs posées, par plan, pour vérifier qu'aucune pierre ne se lit
// comme de la végétation dans Habillage.
const COULEURS = [];

class Peintre {
  constructor(c, P, seul = -1) { this.c = c; this.P = P; this.seul = seul; this.plan_ = 0; this.BAS = H; this.dash = null; }
  get actif() { return this.seul < 0 || this.plan_ === this.seul; }
  plan(n) { this.plan_ = n; }
  mix(a, b, t) { return melange(a, b, t); }
  note(col) { COULEURS.push([this.plan_, col]); }
  rect(x, y, w, h, col, o = 1) { if (!this.actif) return; this.note(col); this.c.fillStyle = css(col, o); this.c.fillRect(x, y, w, h); }
  rond(cx, cy, r, col, o = 1) { if (!this.actif) return; this.note(col); this.c.fillStyle = css(col, o); this.c.beginPath(); this.c.arc(cx, cy, r, 0, Math.PI * 2); this.c.fill(); }
  forme(d, col, o = 1) { if (!this.actif) return; this.note(col); this.c.fillStyle = css(col, o); this.c.fill(typeof d === 'string' ? new Path2D(d) : d); }
  trait(d, col, larg, o = 1) {
    if (!this.actif) return; this.note(col);
    const c = this.c; c.strokeStyle = css(col, o); c.lineWidth = larg; c.lineCap = 'butt';
    c.setLineDash(this.dash || []); c.stroke(typeof d === 'string' ? new Path2D(d) : d); c.setLineDash([]);
  }
  ligne(x1, y1, x2, y2, col, larg, o = 1) { this.trait(`M${x1} ${y1}L${x2} ${y2}`, col, larg, o); }
  triangle(x1, y1, x2, y2, x3, y3, col, o = 1) { this.forme(`M${x1} ${y1}L${x2} ${y2}L${x3} ${y3}Z`, col, o); }

  ciel() {
    const P = this.P, c = this.c;
    const g = c.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, css(P.cielHaut));
    if (P.cielMilieu != null) g.addColorStop(0.58, css(P.cielMilieu));
    g.addColorStop(1, css(P.cielBas));
    c.fillStyle = g; c.fillRect(0, 0, L, H);
    const halo = (r, o) => {
      const rg = c.createRadialGradient(P.astreX, P.astreY, 0, P.astreX, P.astreY, r);
      rg.addColorStop(0, css(P.astre, 0.85 * o)); rg.addColorStop(1, css(P.astre, 0));
      c.fillStyle = rg; c.beginPath(); c.arc(P.astreX, P.astreY, r, 0, 7); c.fill();
    };
    if (P.nuit) {
      const r = Alea(41);
      for (let k = 0; k < 46; k++) {
        const x = r() * 100, y = r() * 38, t = 0.18 + r() * 0.38, o = k % 5 === 0 ? 1 : 0.35 + r() * 0.6;
        this.rond(x, y, t, 0xFFFFF8E0, o);
      }
      halo(P.astreR * 3.2, 0.55);
      this.rond(P.astreX, P.astreY, P.astreR, P.astre);
      this.rond(P.astreX + 1.9, P.astreY - 1.1, P.astreR * 0.92, melange(P.cielHaut, P.cielBas, 0.25));
    } else {
      halo(P.astreR * 3.4, 1);
      this.rond(P.astreX, P.astreY, P.astreR, P.astre, 0.95);
    }
    if (P.rang === 0) this.rect(0, 26, 100, 16, 0xFFFFFFFF, 0.16);
    COULEURS.length = 0;
  }

  fenetre(x, y, w, h, rnd) {
    const P = this.P;
    const allumee = P.allume && (rnd == null || rnd() < 0.7);
    if (allumee) this.rect(x - w * 0.6, y - h * 0.4, w * 2.2, h * 1.8, P.fenetre, 0.22);
    this.rect(x, y, w, h, allumee ? P.fenetre : melange(P.pierre, P.pres, 0.55));
  }
}
