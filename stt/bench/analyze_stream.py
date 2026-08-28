#!/usr/bin/env python3
"""Ce que l'utilisateur voit, et quand — analyse du rejeu de SttSession.

L'étape 1 mesure le modèle. Celle-ci mesure le *produit* : au bout de combien de
temps un mot apparaît, combien de fois le texte se réécrit sous les yeux, et
combien de temps s'écoule entre le relâchement du micro et le texte définitif.
Un modèle correct peut donner une dictée pénible ; c'est ce qui se joue ici.
"""

import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer, cer, normalize, repetition_ratio


def q(v, p):
    v = sorted(v)
    return v[max(0, min(len(v) - 1, int(round(p * (len(v) - 1)))))]


def churn(partials, final):
    """Instabilité du texte en composition.

    Deux mesures distinctes : la réécriture (distance d'édition entre deux
    hypothèses successives, rapportée à la longueur) et la *régression*, cas où
    l'hypothèse précédente n'est plus un préfixe de la suivante. La première est
    inévitable — le texte s'allonge ; la seconde est ce qui gêne réellement à la
    lecture, parce qu'un mot déjà lu change après coup.
    """
    texts = [p["text"] for p in partials] + ([final["text"]] if final else [])
    texts = [t for t in texts if t]
    rew, reg = [], 0
    for a, b in zip(texts, texts[1:]):
        wa, wb = normalize(a).split(), normalize(b).split()
        if not wa:
            continue
        rew.append(wer(a, b)[0])
        if wb[:len(wa)] != wa:
            reg += 1
    return (statistics.mean(rew) if rew else 0.0), reg, max(0, len(texts) - 1)


def main(stream_path, work, out_path):
    res = json.loads(Path(stream_path).read_text(encoding="utf-8"))
    slices = res["slices"]
    load_ms = res["load_ms"]
    manifest = {e["id"]: e for e in
                json.loads((Path(work) / "manifest.json").read_text(encoding="utf-8"))}

    rows = []
    for u in res["utterances"]:
        s = slices[u["f32"]]
        p, f = u["partials"], u["final"]
        dur_ms = s["dur"] * 1000.0
        # Instant où le premier mot s'affiche, compté depuis l'appui sur le micro
        # (chargement du modèle compris : c'est ce que l'utilisateur attend).
        first = p[0]["t_shown_ms"] if p else None
        # Hypothèses réellement vues *pendant* qu'on parle : celles affichées
        # après la fin de l'énoncé n'ont plus rien de « temps réel ».
        during = [x for x in p if x["t_shown_ms"] <= load_ms + dur_ms]
        rew, reg, steps = churn(p, f)
        rows.append({
            "id": s["id"], "parent": s["parent"], "dur": s["dur"],
            "n_partials": len(p), "n_during": len(during),
            "first_ms": first,
            "first_rel": (first - load_ms) if first else None,
            "final_ms": f["ms"] if f else None,
            "pass_ms": [x["ms"] for x in p] + ([f["ms"]] if f else []),
            # Instants d'affichage réels, pas reconstruits : le rapport les
            # dessine tels quels.
            "shown_ms": [x["t_shown_ms"] for x in p],
            "pass_audio": [x["audio_s"] for x in p] + ([f["audio_s"]] if f else []),
            "rewrite": rew, "regressions": reg, "steps": steps,
            "rep": repetition_ratio(f["text"]) if f else 0.0,
            "text": f["text"] if f else "",
        })

    def bucket(d):
        return "2–4 s" if d < 4 else "4–6 s" if d < 6 else "6–10 s" if d < 10 else "10–15 s"

    print(f"\n╔══ Étape 2 — {len(rows)} énoncés, {sum(r['dur'] for r in rows)/60:.1f} min")
    print(f"║ chargement du modèle : {load_ms:.0f} ms (payé une fois, au 1er appui)\n║")
    print(f"║ {'durée':>8} {'n':>4} {'1re hyp.':>9} {'hyp.':>5} {'pendant':>8} "
          f"{'après stop':>11} {'réécriture':>11} {'régressions':>12}")
    for b in ["2–4 s", "4–6 s", "6–10 s", "10–15 s"]:
        g = [r for r in rows if bucket(r["dur"]) == b]
        if not g:
            continue
        firsts = [r["first_ms"] for r in g if r["first_ms"]]
        print(f"║ {b:>8} {len(g):>4} {statistics.median(firsts)/1000:>8.2f}s "
              f"{statistics.median([r['n_partials'] for r in g]):>5.0f} "
              f"{statistics.median([r['n_during'] for r in g]):>8.0f} "
              f"{statistics.median([r['final_ms'] for r in g]):>10.0f}ms "
              f"{statistics.mean([r['rewrite'] for r in g])*100:>10.0f}% "
              f"{sum(r['regressions'] for r in g)/max(1,sum(r['steps'] for r in g))*100:>11.0f}%")

    allf = [r["first_ms"] for r in rows if r["first_ms"]]
    print(f"║\n║ 1re hypothèse : médiane {statistics.median(allf)/1000:.2f}s "
          f"[p10 {q(allf,.1)/1000:.2f}s · p90 {q(allf,.9)/1000:.2f}s]")
    muets = [r for r in rows if not r["first_ms"]]
    print(f"║ énoncés sans aucune hypothèse avant la fin : "
          f"{sum(1 for r in rows if r['n_during'] == 0)}/{len(rows)} "
          f"(dont {len(muets)} sans hypothèse du tout)")

    # Coût d'une passe en fonction de la longueur d'audio — le README affirme
    # qu'il croît avec l'énoncé. L'encodeur de whisper travaille toujours sur
    # 30 s : seul le décodage grandit. Vérification.
    pts = [(a, m) for r in rows for a, m in zip(r["pass_audio"], r["pass_ms"])]
    print("║\n║ coût d'une passe selon l'audio soumis :")
    for lo, hi in [(0, 2), (2, 4), (4, 6), (6, 10), (10, 16)]:
        g = [m for a, m in pts if lo <= a < hi]
        if g:
            print(f"║   {lo:>2}–{hi:<2}s : {statistics.median(g):>6.0f} ms "
                  f"(n={len(g)})")

    # WER du pipeline : les transcriptions finales des tranches d'un fichier,
    # recollées, contre la référence du fichier entier. L'écart avec l'étape 1
    # isole ce que coûtent le découpage et single_segment.
    par = {}
    for r in rows:
        par.setdefault(r["parent"], []).append(r)
    pipe_err = pipe_words = 0
    for pid, group in par.items():
        ref = manifest[pid]["reference"]
        hyp = " ".join(r["text"] for r in sorted(group, key=lambda x: x["id"]))
        w, n, s, i, d = wer(ref, hyp)
        pipe_err += s + i + d
        pipe_words += n
    print(f"║\n║ WER du pipeline (tranches recollées, {len(par)} fichiers) : "
          f"{pipe_err/pipe_words*100:.1f} %")
    boucles = [r for r in rows if r["rep"] > 0.15]
    print(f"║ boucles de décodage : {len(boucles)}/{len(rows)} énoncés")

    Path(out_path).write_text(json.dumps(
        {"load_ms": load_ms, "rows": rows,
         "pipeline_wer": pipe_err / pipe_words,
         "pass_cost": [{"lo": lo, "hi": hi,
                        "median_ms": statistics.median([m for a, m in pts if lo <= a < hi])}
                       for lo, hi in [(0,2),(2,4),(4,6),(6,10),(10,16)]
                       if any(lo <= a < hi for a, _ in pts)]},
        ensure_ascii=False), encoding="utf-8")
    print(f"║\n╚══ 💾 {out_path}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
