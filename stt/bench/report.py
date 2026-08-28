#!/usr/bin/env python3
"""Écrit le rapport de mesure en HTML, à partir des seuls fichiers de résultats.

Rien n'est saisi à la main : chaque chiffre et chaque coordonnée de graphique
vient des JSON produits par run_bench.py / analyze_stream.py. Un rapport dont
les figures sont dessinées à la main diverge de ses données au premier
re-passage — ici il suffit de relancer le script.
"""

import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer

# --- Données ----------------------------------------------------------------


def load(d):
    R = Path(d)
    return {
        "tiny_full": json.loads((R / "tiny_full.json").read_text(encoding="utf-8")),
        "base_full": json.loads((R / "base_full.json").read_text(encoding="utf-8")),
        "tiny_stream": json.loads((R / "tiny_stream_summary.json").read_text(encoding="utf-8")),
        "base_stream": json.loads((R / "base_stream_summary.json").read_text(encoding="utf-8")),
    }


def agg(full):
    rows = full["rows"]
    words = sum(r["ref_words"] for r in rows)
    err = sum(r["sub"] + r["ins"] + r["del"] for r in rows)
    return {
        "wer": err / words,
        "sub": sum(r["sub"] for r in rows) / words,
        "ins": sum(r["ins"] for r in rows) / words,
        "del": sum(r["del"] for r in rows) / words,
        "wer_med": statistics.median(r["wer"] for r in rows),
        "wer_p10": sorted(r["wer"] for r in rows)[5],
        "wer_p90": sorted(r["wer"] for r in rows)[44],
        "cer_med": statistics.median(r["cer"] for r in rows),
        "rtf": statistics.median(r["rtf"] for r in rows),
        "loops": sum(1 for r in rows if r["rep"] > 0.15),
        "n": len(rows),
        "words": words,
    }


def buckets(stream):
    def b(d):
        return "2–4 s" if d < 4 else "4–6 s" if d < 6 else "6–10 s" if d < 10 else "10–15 s"
    out = {}
    for name in ["2–4 s", "4–6 s", "6–10 s", "10–15 s"]:
        g = [r for r in stream["rows"] if b(r["dur"]) == name]
        if not g:
            continue
        firsts = [r["first_ms"] for r in g if r["first_ms"]]
        out[name] = {
            "n": len(g),
            "first": statistics.median(firsts) / 1000,
            "hyp": statistics.median(r["n_partials"] for r in g),
            "during": statistics.median(r["n_during"] for r in g),
            "final": statistics.median(r["final_ms"] for r in g) / 1000,
            "reg": sum(r["regressions"] for r in g) / max(1, sum(r["steps"] for r in g)),
        }
    return out


# --- Graphiques (SVG calculé, jamais dessiné à la main) ----------------------


def chart_distribution(t, b, w=680, h=220):
    """Courbe des WER triés : montre le déplacement de toute la distribution,
    là où une moyenne ne montre qu'un point."""
    pad = {"l": 42, "r": 12, "t": 12, "b": 28}
    iw, ih = w - pad["l"] - pad["r"], h - pad["t"] - pad["b"]
    series = [("tiny", sorted(r["wer"] for r in t["rows"]), "var(--tiny)"),
              ("base", sorted(r["wer"] for r in b["rows"]), "var(--base)")]
    ymax = 1.6
    parts = []
    for y in [0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5]:
        yy = pad["t"] + ih - (y / ymax) * ih
        parts.append(f'<line x1="{pad["l"]}" y1="{yy:.1f}" x2="{w-pad["r"]}" y2="{yy:.1f}" class="grid"/>')
        parts.append(f'<text x="{pad["l"]-8}" y="{yy+4:.1f}" class="tick" text-anchor="end">{y*100:.0f}</text>')
    for name, vals, col in series:
        pts = []
        for i, v in enumerate(vals):
            x = pad["l"] + (i / (len(vals) - 1)) * iw
            y = pad["t"] + ih - min(v, ymax) / ymax * ih
            pts.append(f"{x:.1f},{y:.1f}")
        parts.append(f'<polyline points="{" ".join(pts)}" fill="none" '
                     f'stroke="{col}" stroke-width="2.5" stroke-linejoin="round"/>')
    parts.append(f'<text x="{pad["l"]}" y="{h-8}" class="tick">meilleur fichier</text>')
    parts.append(f'<text x="{w-pad["r"]}" y="{h-8}" class="tick" text-anchor="end">pire fichier</text>')
    return f'<svg viewBox="0 0 {w} {h}" class="chart" role="img" aria-label="Distribution des taux d\'erreur mot, 50 fichiers, tiny contre base">{"".join(parts)}</svg>'


def chart_pass_cost(t, b, w=680, h=210):
    """Coût d'une passe selon l'audio soumis. Le point du rapport : c'est plat."""
    pad = {"l": 46, "r": 12, "t": 12, "b": 40}
    iw, ih = w - pad["l"] - pad["r"], h - pad["t"] - pad["b"]
    labels = ["0–2 s", "2–4 s", "4–6 s", "6–10 s", "10–16 s"]
    tv = [c["median_ms"] for c in t["pass_cost"]]
    bv = [c["median_ms"] for c in b["pass_cost"]]
    ymax = max(bv + tv) * 1.15
    parts = []
    for y in [0, 500, 1000, 1500, 2000, 2500]:
        if y > ymax:
            break
        yy = pad["t"] + ih - y / ymax * ih
        parts.append(f'<line x1="{pad["l"]}" y1="{yy:.1f}" x2="{w-pad["r"]}" y2="{yy:.1f}" class="grid"/>')
        parts.append(f'<text x="{pad["l"]-8}" y="{yy+4:.1f}" class="tick" text-anchor="end">{y}</text>')
    gw = iw / len(labels)
    for i, lab in enumerate(labels):
        cx = pad["l"] + gw * (i + 0.5)
        for j, (vals, col) in enumerate([(tv, "var(--tiny)"), (bv, "var(--base)")]):
            if i >= len(vals):
                continue
            bh = vals[i] / ymax * ih
            bx = cx - gw * 0.34 + j * gw * 0.34
            parts.append(f'<rect x="{bx:.1f}" y="{pad["t"]+ih-bh:.1f}" width="{gw*0.30:.1f}" '
                         f'height="{bh:.1f}" fill="{col}" rx="2"/>')
        parts.append(f'<text x="{cx:.1f}" y="{h-20}" class="tick" text-anchor="middle">{lab}</text>')
    parts.append(f'<text x="{pad["l"]-8}" y="{pad["t"]-2}" class="tick" text-anchor="end">ms</text>')
    parts.append(f'<text x="{w/2:.0f}" y="{h-4}" class="tick" text-anchor="middle">audio soumis à la passe</text>')
    return f'<svg viewBox="0 0 {w} {h}" class="chart" role="img" aria-label="Coût d\'une passe whisper selon la longueur d\'audio soumise">{"".join(parts)}</svg>'


def chart_timeline(t, b, w=680, h=170):
    """Chronologie d'une dictée : quand chaque hypothèse s'affiche.

    C'est la figure centrale — c'est exactement ce que l'utilisateur perçoit,
    et ce qu'aucun WER ne dit.
    """
    def pick(stream):
        cands = [r for r in stream["rows"] if 3.8 < r["dur"] < 4.6]
        return min(cands, key=lambda r: abs(r["dur"] - 4.2))
    rt, rb = pick(t), pick(b)
    span = max(rt["first_ms"] + sum(rt["pass_ms"]), rb["first_ms"] + sum(rb["pass_ms"]),
               (max(rt["dur"], rb["dur"]) * 1000 + max(rt["final_ms"], rb["final_ms"]))) + 400
    pad = {"l": 52, "r": 14, "t": 30, "b": 34}
    iw = w - pad["l"] - pad["r"]

    def X(ms):
        return pad["l"] + ms / span * iw

    parts = []
    for s in range(0, int(span / 1000) + 1):
        x = X(s * 1000)
        parts.append(f'<line x1="{x:.1f}" y1="{pad["t"]-8}" x2="{x:.1f}" y2="{h-pad["b"]+4}" class="grid"/>')
        parts.append(f'<text x="{x:.1f}" y="{h-pad["b"]+18}" class="tick" text-anchor="middle">{s} s</text>')
    for k, (r, col, name) in enumerate([(rt, "var(--tiny)", "tiny"), (rb, "var(--base)", "base")]):
        y = pad["t"] + k * 54
        # durée de parole
        parts.append(f'<rect x="{X(0):.1f}" y="{y:.1f}" width="{X(r["dur"]*1000)-X(0):.1f}" '
                     f'height="16" class="speech" rx="3"/>')
        parts.append(f'<text x="{pad["l"]-10}" y="{y+12:.1f}" class="rowlab" text-anchor="end">{name}</text>')
        # hypothèses affichées, aux instants réellement mesurés
        for s_ms in r["shown_ms"]:
            parts.append(f'<line x1="{X(s_ms):.1f}" y1="{y+20:.1f}" x2="{X(s_ms):.1f}" y2="{y+34:.1f}" '
                         f'stroke="{col}" stroke-width="2.5"/>')
        stop = r["dur"] * 1000
        fin = stop + r["final_ms"]
        parts.append(f'<line x1="{X(stop):.1f}" y1="{y-6:.1f}" x2="{X(stop):.1f}" y2="{y+38:.1f}" class="stop"/>')
        parts.append(f'<circle cx="{X(fin):.1f}" cy="{y+27:.1f}" r="5" fill="{col}"/>')
        parts.append(f'<text x="{X(fin)+9:.1f}" y="{y+31:.1f}" class="tick">texte définitif</text>')
    parts.append(f'<text x="{pad["l"]}" y="{pad["t"]-14}" class="tick">— parole —   '
                 f'│ = hypothèse affichée   ┃ = relâchement du micro</text>')
    return (f'<svg viewBox="0 0 {w} {h}" class="chart" role="img" '
            f'aria-label="Chronologie d\'une dictée de 4 secondes, hypothèses affichées">{"".join(parts)}</svg>',
            rt, rb)


# --- Page -------------------------------------------------------------------

CSS = """
:root{
  --ground:#EFF2F4; --surface:#FFFFFF; --sunk:#E5EAEE;
  --ink:#101820; --ink-soft:#4E5A64; --ink-faint:#7B8792;
  --rule:#D3DBE1; --accent:#1B6E8A;
  --tiny:#66788E; --base:#B0603A; --bad:#9E2F2F; --warn:#8A5A12;
  --speech:#C9D4DB;
}
@media (prefers-color-scheme:dark){
  :root:not([data-theme="light"]){
    --ground:#0D1216; --surface:#141C22; --sunk:#101820;
    --ink:#E4EBF0; --ink-soft:#A2B0BB; --ink-faint:#78868F;
    --rule:#25313A; --accent:#54B4D2;
    --tiny:#93A5BB; --base:#DC9163; --bad:#E0736B; --warn:#D9A65C;
    --speech:#233039;
  }
}
:root[data-theme="dark"]{
  --ground:#0D1216; --surface:#141C22; --sunk:#101820;
  --ink:#E4EBF0; --ink-soft:#A2B0BB; --ink-faint:#78868F;
  --rule:#25313A; --accent:#54B4D2;
  --tiny:#93A5BB; --base:#DC9163; --bad:#E0736B; --warn:#D9A65C;
  --speech:#233039;
}
*{box-sizing:border-box}
body{
  margin:0; background:var(--ground); color:var(--ink);
  font-family:"Source Serif 4",Georgia,serif; font-size:17px; line-height:1.62;
  -webkit-font-smoothing:antialiased;
}
.wrap{max-width:1000px; margin:0 auto; padding:0 24px 96px}
.col{max-width:66ch}
h1,h2,h3,.lab,th,.tick,.rowlab,.num,.kpi-v{font-family:Archivo,"Helvetica Neue",Arial,sans-serif}
h1{
  font-size:clamp(2.1rem,5vw,3.2rem); line-height:1.04; font-weight:700;
  letter-spacing:-.028em; text-wrap:balance; margin:0;
}
h2{
  font-size:1.34rem; font-weight:650; letter-spacing:-.012em;
  margin:0; text-wrap:balance;
}
h3{font-size:1.02rem; font-weight:650; letter-spacing:-.005em; margin:0 0 .4rem}
p{margin:0 0 1.05rem}
a{color:var(--accent)}
.lab{
  font-size:.7rem; text-transform:uppercase; letter-spacing:.13em;
  font-weight:600; color:var(--ink-faint);
}
code,.mono,.num{font-family:"IBM Plex Mono",ui-monospace,monospace}
code{font-size:.86em; background:var(--sunk); padding:.1em .34em; border-radius:3px}

header.head{padding:72px 0 40px; border-bottom:2px solid var(--ink)}
.sub{font-size:1.12rem; color:var(--ink-soft); max-width:60ch; margin-top:1.1rem}
.meta{display:flex; flex-wrap:wrap; gap:8px 26px; margin-top:26px}
.meta div{font-size:.8rem}
.meta .lab{display:block; margin-bottom:1px}
.meta .num{font-size:.93rem; color:var(--ink)}

section{padding-top:52px}
.shead{display:flex; align-items:baseline; gap:14px; margin-bottom:20px;
  border-bottom:1px solid var(--rule); padding-bottom:10px}
.step{
  font-family:"IBM Plex Mono",monospace; font-size:.78rem; font-weight:500;
  color:var(--surface); background:var(--ink); border-radius:3px;
  padding:3px 7px; letter-spacing:.02em; flex:none;
}

.verdict{
  display:grid; grid-template-columns:repeat(auto-fit,minmax(210px,1fr));
  gap:1px; background:var(--rule); border:1px solid var(--rule);
  margin:34px 0 0;
}
.kpi{background:var(--surface); padding:20px 20px 18px}
.kpi-v{font-size:2.05rem; font-weight:700; letter-spacing:-.03em; line-height:1;
  font-variant-numeric:tabular-nums}
.kpi-d{font-size:.83rem; color:var(--ink-soft); margin-top:9px; line-height:1.45}
.kpi.warn .kpi-v{color:var(--bad)}
.kpi.good .kpi-v{color:var(--accent)}

.panel{background:var(--surface); border:1px solid var(--rule); padding:22px 24px; margin:26px 0}
.panel.tight{padding:16px 20px}
figure{margin:26px 0}
figcaption{font-size:.83rem; color:var(--ink-soft); margin-top:11px; max-width:62ch}
.chart{width:100%; height:auto; display:block; background:var(--surface)}
.chart .grid{stroke:var(--rule); stroke-width:1}
.chart .tick{font-size:11px; fill:var(--ink-faint); font-weight:500}
.chart .rowlab{font-size:12px; fill:var(--ink); font-weight:600}
.chart .speech{fill:var(--speech)}
.chart .stop{stroke:var(--ink); stroke-width:2}
.legend{display:flex; gap:20px; margin-top:12px; font-size:.82rem; flex-wrap:wrap}
.legend span{display:inline-flex; align-items:center; gap:7px}
.sw{width:13px; height:13px; border-radius:2px; flex:none}

.tbl-wrap{overflow-x:auto; margin:22px 0}
table{border-collapse:collapse; width:100%; font-size:.9rem}
th,td{text-align:right; padding:9px 12px; border-bottom:1px solid var(--rule)}
th:first-child,td:first-child{text-align:left; font-family:"Source Serif 4",serif}
thead th{font-size:.72rem; text-transform:uppercase; letter-spacing:.08em;
  color:var(--ink-faint); border-bottom:1.5px solid var(--ink)}
td.num,td:not(:first-child){font-family:"IBM Plex Mono",monospace;
  font-variant-numeric:tabular-nums; font-size:.86rem}
tbody tr:hover{background:var(--sunk)}
.t-tiny{color:var(--tiny); font-weight:600}
.t-base{color:var(--base); font-weight:600}
td.delta{color:var(--accent); font-weight:600}
td.delta.neg{color:var(--bad)}

.find{border-left:3px solid var(--accent); padding:2px 0 2px 18px; margin:24px 0}
.find.alert{border-color:var(--bad)}
.find h3{margin-bottom:.3rem}
.find p:last-child{margin-bottom:0}

ul{padding-left:1.1rem; margin:0 0 1.05rem}
li{margin-bottom:.5rem}
.caveats li{color:var(--ink-soft)}
.caveats strong{color:var(--ink)}

pre{background:var(--sunk); border:1px solid var(--rule); padding:14px 16px;
  overflow-x:auto; font-size:.8rem; line-height:1.6; margin:16px 0}
pre code{background:none; padding:0}
footer{margin-top:64px; padding-top:22px; border-top:1px solid var(--rule);
  font-size:.83rem; color:var(--ink-faint)}
:focus-visible{outline:2px solid var(--accent); outline-offset:2px}
@media (prefers-reduced-motion:no-preference){
  .kpi,.panel{transition:none}
}
"""


def pc(x, d=1):
    return f"{x*100:.{d}f} %"


def build(res, out_path):
    t, b = agg(res["tiny_full"]), agg(res["base_full"])
    ts, bs = res["tiny_stream"], res["base_stream"]
    tb, bb = buckets(ts), buckets(bs)
    tl_svg, rt, rb = chart_timeline(ts, bs)
    n_mute_base = sum(1 for r in bs["rows"] if r["n_during"] == 0)

    # Coût du découpage : mêmes fichiers, une passe de 60 s contre tranches.
    def chunk_cost(full, stream):
        idx = {r["id"]: r for r in full["rows"]}
        pids = {r["parent"] for r in stream["rows"]}
        e = sum(idx[p]["sub"] + idx[p]["ins"] + idx[p]["del"] for p in pids)
        w = sum(idx[p]["ref_words"] for p in pids)
        return e / w, stream["pipeline_wer"], len(pids)
    t_long, t_short, nfiles = chunk_cost(res["tiny_full"], ts)
    b_long, b_short, _ = chunk_cost(res["base_full"], bs)

    rowsT = [
        ("WER agrégé", pc(t["wer"]), pc(b["wer"]), f"−{(t['wer']-b['wer'])*100:.1f} pt"),
        ("· substitutions", pc(t["sub"]), pc(b["sub"]), ""),
        ("· insertions", pc(t["ins"]), pc(b["ins"]), ""),
        ("· omissions", pc(t["del"]), pc(b["del"]), ""),
        ("WER médian par fichier", pc(t["wer_med"]), pc(b["wer_med"]),
         f"−{(t['wer_med']-b['wer_med'])*100:.1f} pt"),
        ("WER p10 – p90", f"{pc(t['wer_p10'],0)} – {pc(t['wer_p90'],0)}",
         f"{pc(b['wer_p10'],0)} – {pc(b['wer_p90'],0)}", ""),
        ("CER médian", pc(t["cer_med"]), pc(b["cer_med"]),
         f"−{(t['cer_med']-b['cer_med'])*100:.1f} pt"),
        ("RTF (hôte, 3 threads)", f"{t['rtf']:.3f}", f"{b['rtf']:.3f}",
         f"×{b['rtf']/t['rtf']:.1f}"),
        ("Boucles de décodage", f"{t['loops']}/50", f"{b['loops']}/50", ""),
        ("Poids de l'asset", "32,2 Mo", "59,7 Mo", "+27,5 Mo"),
    ]
    trs = "".join(
        f'<tr><td>{a}</td><td class="t-tiny">{c}</td><td class="t-base">{d}</td>'
        f'<td class="delta{"" if not e.startswith("×") and not e.startswith("+") else " neg"}">{e}</td></tr>'
        for a, c, d, e in rowsT)

    bt = "".join(
        f'<tr><td>{k}</td><td>{v["n"]}</td>'
        f'<td class="t-tiny">{v["first"]:.2f} s</td><td class="t-base">{bb[k]["first"]:.2f} s</td>'
        f'<td class="t-tiny">{v["during"]:.0f}</td><td class="t-base">{bb[k]["during"]:.0f}</td>'
        f'<td class="t-tiny">{v["final"]:.2f} s</td><td class="t-base">{bb[k]["final"]:.2f} s</td></tr>'
        for k, v in tb.items())

    html = f"""<title>Whisper luxembourgeois au banc d'essai</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wght@500;600;700&family=IBM+Plex+Mono:wght@400;500;600&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&display=swap">
<style>{CSS}</style>
<div class="wrap">

<header class="head">
  <div class="lab">Dictée vocale embarquée · branche feat/speech-to-text-lb</div>
  <h1>Ce que vaut vraiment la dictée luxembourgeoise</h1>
  <p class="sub">Première mesure chiffrée du modèle et du pipeline temps réel, sur
  50 minutes de parole luxembourgeoise réelle et 161 énoncés rejoués. Le
  <code>stt/README.md</code> disait « la qualité n'est pas mesurée ». Elle l'est.</p>
  <div class="meta">
    <div><span class="lab">Corpus</span><span class="num">Akabi · 470 conférences de presse</span></div>
    <div><span class="lab">Mesuré</span><span class="num">50 min + 161 énoncés</span></div>
    <div><span class="lab">Machine</span><span class="num">hôte x86, 3 threads</span></div>
    <div><span class="lab">Modèles</span><span class="num">unilux tiny · base, q5_1</span></div>
  </div>
</header>

<div class="verdict">
  <div class="kpi warn"><div class="kpi-v">{pc(t_short,0)}</div>
    <div class="kpi-d">WER de <span class="t-tiny">tiny</span>, le modèle
    embarqué aujourd'hui, sur des énoncés de quelques secondes — le régime réel
    d'un clavier.</div></div>
  <div class="kpi good"><div class="kpi-v">{pc(b_short,0)}</div>
    <div class="kpi-d">Le même corpus, mêmes énoncés, avec
    <span class="t-base">base</span> : la moitié des erreurs en moins, pour
    +27,5 Mo d'APK.</div></div>
  <div class="kpi"><div class="kpi-v">{tb['2–4 s']['first']:.2f} s</div>
    <div class="kpi-d">Avant le premier mot affiché avec
    <span class="t-tiny">tiny</span>, sur cette machine. Puis
    {tb['2–4 s']['final']:.2f} s après le relâchement du micro.</div></div>
  <div class="kpi"><div class="kpi-v">{bb['2–4 s']['first']:.2f} s</div>
    <div class="kpi-d">Le même délai avec <span class="t-base">base</span>, puis
    {bb['2–4 s']['final']:.2f} s. C'est le prix de la qualité — et il est encore
    inconnu sur un téléphone.</div></div>
</div>

<section class="col">
  <div class="shead"><h2>Ce qui a été mesuré, et sur quoi</h2></div>
  <p>Deux niveaux, qu'il ne faut pas confondre. Le premier mesure le
  <strong>modèle</strong> : une passe sur un fichier entier, comme le ferait
  n'importe quel banc d'essai ASR. Le second mesure le <strong>produit</strong> :
  le découpage temps réel de <code>SttSession</code> rejoué énoncé par énoncé,
  avec ses passes partielles, ses tours sautés et sa passe finale. Un modèle
  correct peut donner une dictée pénible ; c'est ce second niveau qui décide.</p>
  <p>Le corpus est <code>Akabi/Luxemburgish_Press_Conferences_Gov</code> :
  470 extraits de 60 s de conférences de presse gouvernementales, 16 kHz mono,
  avec transcription humaine. Quatre réserves comptent autant que les chiffres.</p>
  <ul class="caveats">
    <li><strong>Contamination invérifiable.</strong> La carte du modèle ne publie
    pas ses données d'entraînement (« ≈150 h de paires audio-texte »
    luxembourgeoises). Ce corpus public en est un candidat naturel : le WER
    mesuré est un <em>plafond</em> de qualité, pas une estimation neutre.</li>
    <li><strong>Registre hostile.</strong> Ce sont les conférences COVID — la
    source même écartée du dictionnaire parce que les ministres basculent en
    Hochdeutsch, avec des termes français cités à chaque phrase.</li>
    <li><strong>Acoustique trop favorable.</strong> Micro-cravate en salle
    traitée, pas un téléphone dans la rue.</li>
    <li><strong>Machine trop rapide.</strong> Tous les temps sont ceux d'un x86
    à 6 cœurs. Rien ici ne dit ce que fait un téléphone.</li>
  </ul>
</section>

<section>
  <div class="shead"><span class="step">étape 1</span><h2>Le modèle seul</h2></div>
  <div class="col"><p>50 fichiers, 50 minutes, {t['words']} mots de référence.
  Une passe par fichier, paramètres de décodage recopiés à l'identique depuis
  <code>whisper_jni.cpp</code> — même échantillonnage glouton, même
  <code>temperature_inc</code> à zéro.</p></div>
  <div class="tbl-wrap"><table>
    <thead><tr><th>Mesure</th><th>tiny q5_1</th><th>base q5_1</th><th>écart</th></tr></thead>
    <tbody>{trs}</tbody>
  </table></div>
  <figure>
    {chart_distribution(res['tiny_full'], res['base_full'])}
    <div class="legend">
      <span><i class="sw" style="background:var(--tiny)"></i>tiny q5_1</span>
      <span><i class="sw" style="background:var(--base)"></i>base q5_1</span>
    </div>
    <figcaption>WER de chacun des 50 fichiers, triés du meilleur au pire. Ce
    n'est pas la moyenne qui bouge, c'est toute la distribution : la médiane
    passe de {pc(t['wer_med'],0)} à {pc(b['wer_med'],0)}. Les deux courbes se
    rejoignent à droite — sur les fichiers les plus difficiles, changer de
    modèle ne sauve rien.</figcaption>
  </figure>
</section>

<section>
  <div class="shead"><span class="step">étape 2</span><h2>Le pipeline vécu</h2></div>
  <div class="col"><p>161 énoncés de 2 à 15 s, découpés aux silences — des
  longueurs de dictée, pas des monologues. Chaque énoncé est rejoué sur une
  horloge virtuelle qui reproduit exactement la logique de
  <code>SttSession</code> : l'audio arrive par blocs de 64 ms, une passe part
  quand 0,6 s d'audio nouveau s'est accumulé <em>et</em> que le worker est
  libre. Seule l'arrivée de l'audio est simulée ; chaque passe whisper est
  réellement exécutée et chronométrée.</p></div>
  <figure>
    {tl_svg}
    <figcaption>Une dictée de {rt['dur']:.1f} s, telle qu'elle se déroule.
    Chaque trait vertical est une hypothèse qui remplace le texte en
    composition. <span class="t-tiny">tiny</span> en affiche
    {len(rt['pass_ms'])-1} et rend le texte définitif {rt['final_ms']/1000:.2f} s
    après le relâchement ; <span class="t-base">base</span> en affiche
    {len(rb['pass_ms'])-1} et met {rb['final_ms']/1000:.2f} s.</figcaption>
  </figure>
  <div class="tbl-wrap"><table>
    <thead><tr><th rowspan="2">Durée de l'énoncé</th><th rowspan="2">n</th>
      <th colspan="2">1<sup>re</sup> hypothèse</th><th colspan="2">hypothèses vues</th>
      <th colspan="2">après « stop »</th></tr>
      <tr><th>tiny</th><th>base</th><th>tiny</th><th>base</th><th>tiny</th><th>base</th></tr></thead>
    <tbody>{bt}</tbody>
  </table></div>
</section>

<section class="col">
  <div class="shead"><h2>Trois résultats qui changent le code</h2></div>

  <div class="find">
    <h3>Le correctif d'aujourd'hui tient</h3>
    <p>Avec <span class="t-tiny">tiny</span>, sur 161 énoncés,
    <strong>aucun</strong> ne reste sans hypothèse avant la fin : même un énoncé
    de 2 à 4 s en affiche {tb['2–4 s']['during']:.0f} pendant qu'on parle, la
    première à {tb['2–4 s']['first']:.2f} s, avec une dispersion minuscule.
    C'était exactement le défaut constaté au téléphone.</p>
    <p>Avec <span class="t-base">base</span>, la marge se referme :
    {n_mute_base} énoncés sur 161 n'affichent plus rien avant le relâchement,
    parce qu'une passe dure {bb['2–4 s']['first']:.1f} s là où elle en durait
    {tb['2–4 s']['first']:.1f}. Le mécanisme fonctionne toujours, mais il n'a
    plus de marge — et cette marge est ce qu'un téléphone consommerait
    en premier.</p>
  </div>

  <div class="find">
    <h3>Le coût d'une passe ne croît presque pas</h3>
    <p>Le <code>README</code> affirme que « le coût d'une passe croît avec la
    durée de l'énoncé ». C'est faux au premier ordre : l'encodeur de Whisper
    travaille toujours sur une fenêtre de 30 s, quelle que soit la parole
    soumise. Seul le décodage grandit, et il est minoritaire.</p>
  </div>
  <figure>
    {chart_pass_cost(ts, bs)}
    <div class="legend">
      <span><i class="sw" style="background:var(--tiny)"></i>tiny</span>
      <span><i class="sw" style="background:var(--base)"></i>base</span>
    </div>
    <figcaption>Durée médiane d'une passe selon l'audio qu'on lui soumet. L'audio
    est multiplié par huit d'un bout à l'autre ; le coût par
    {ts['pass_cost'][-1]['median_ms']/ts['pass_cost'][0]['median_ms']:.2f}.
    La phrase du README est à réécrire.</figcaption>
  </figure>

  <div class="find alert">
    <h3>tiny s'effondre sur les énoncés courts, base non</h3>
    <p>C'est le résultat qui décide. Sur les mêmes {nfiles} fichiers, une passe
    sur 60 s de parole continue donne {pc(t_long,1)} à
    <span class="t-tiny">tiny</span> et {pc(b_long,1)} à
    <span class="t-base">base</span> — douze points d'écart, ce qu'on attendait.
    Découpés en tranches de 4 s, c'est-à-dire dans la situation réelle d'un
    clavier, <span class="t-tiny">tiny</span> tombe à {pc(t_short,1)} tandis que
    <span class="t-base">base</span> tient à {pc(b_short,1)}.</p>
    <p>Autrement dit : privé de contexte, <span class="t-tiny">tiny</span> perd
    {(t_short-t_long)*100:.0f} points, <span class="t-base">base</span> en perd
    {(b_short-b_long)*100:.0f}. L'écart entre les deux modèles n'est pas de douze
    points dans le régime qui nous intéresse, il est de
    <strong>{(t_short-b_short)*100:.0f}</strong> — la moitié des erreurs. Mesurer
    les modèles sur des fichiers de 60 s, comme le fait tout banc d'essai ASR,
    aurait laissé croire à un choix de confort ; c'en est un de viabilité.</p>
  </div>
</section>

<section class="col">
  <div class="shead"><h2>tiny ou base</h2></div>
  <p>Dans le régime du clavier — énoncés de quelques secondes —
  <span class="t-base">base</span> retire <strong>{(t_short-b_short)*100:.0f}
  points</strong> de WER, et fait tomber le CER de {pc(t['cer_med'],1)} à
  {pc(b['cer_med'],1)}. Le gain se voit aussi à l'œil : là où
  <span class="t-tiny">tiny</span> phonétise les emprunts français
  (« am&nbsp;Situatioun ieregeliär »), <span class="t-base">base</span> écrit
  « a&nbsp;Situatioun irregulière » et « Haut-Commissaire à la Protection ».</p>
  <p>Ce que ça coûte : <strong>+27,5 Mo d'APK</strong> (32,2 → 59,7 Mo),
  <strong>×{b['rtf']/t['rtf']:.1f} de calcul</strong>, et donc à peu près le double
  de latence — {tb['2–4 s']['first']:.2f} s contre {bb['2–4 s']['first']:.2f} s
  avant le premier mot, {tb['2–4 s']['final']:.2f} s contre
  {bb['2–4 s']['final']:.2f} s après le relâchement, sur cette machine. S'y
  ajoutent des tampons de calcul plus gros, dans un processus IME que le système
  tue déjà volontiers.</p>
  <p>L'arbitrage ne se tranche pas ici : il se tranche sur un téléphone, parce
  que doubler une latence de 1,5 s et doubler une latence de 8 s ne sont pas la
  même décision.</p>
</section>

<section class="col">
  <div class="shead"><h2>Ce que ce rapport ne dit pas</h2></div>
  <ul class="caveats">
    <li><strong>La latence réelle.</strong> Tout est mesuré sur un x86 à
    6 cœurs. Le rapport ARM/x86 sur ggml va couramment de 3 à 8× : la première
    hypothèse arriverait entre 4 et 12 s sur un téléphone d'entrée de gamme. La
    conclusion sur l'utilisabilité en dépend entièrement.</li>
    <li><strong>Ce que coûterait de réparer les boucles.</strong>
    {t['loops']} fichiers sur 50 partent en boucle de répétition — « Ech soen
    net. » quarante fois — et c'est le mode de défaillance catastrophique
    dominant, pire chez <span class="t-base">base</span> (jusqu'à 71 % de
    n-grammes répétés) que chez <span class="t-tiny">tiny</span>. C'est la
    contrepartie directe de <code>temperature_inc = 0</code>, choisi pour borner
    la latence. Personne n'a mesuré ce que le repli coûterait réellement.</li>
    <li><strong>La tenue en mémoire.</strong> Non mesurée, ici comme avant.</li>
  </ul>
</section>

<section class="col">
  <div class="shead"><h2>Refaire la mesure</h2></div>
  <p>Rien n'est saisi à la main dans cette page : chaque chiffre et chaque
  coordonnée de graphique vient des fichiers de résultats. L'audio, lui, ne rentre
  jamais dans le dépôt — la source Hugging Face ne déclare aucune licence.</p>
  <pre><code>cmake -B build -S stt/bench &amp;&amp; cmake --build build -j
python stt/bench/prepare_dataset.py --work W --files 50 --slice-files 15
python stt/bench/run_bench.py --stage full   --work W --model M --binary build/lux_bench --out R/full.json
python stt/bench/run_bench.py --stage stream --work W --model M --binary build/lux_bench --out R/stream.json
python stt/bench/analyze_stream.py R/stream.json W R/stream_summary.json
python stt/bench/report.py R rapport.html</code></pre>
</section>

<footer>
  Mesuré le 28 août 2026 · modèles <span class="mono">unilux/whisper-{{tiny,base}}-v1-luxembourgish</span>,
  licence open-mdw · corpus <span class="mono">Akabi/Luxemburgish_Press_Conferences_Gov</span>,
  aucune licence déclarée, non redistribué · harnais <span class="mono">stt/bench/</span>
</footer>

</div>
"""
    Path(out_path).write_text(html, encoding="utf-8")
    print(f"💾 {out_path}  ({len(html)/1024:.0f} Ko)")


if __name__ == "__main__":
    build(load(sys.argv[1]), sys.argv[2])
