#!/usr/bin/env python3
"""Banc de la dictée en parole continue — ce que les tranches ne peuvent pas voir.

    python stt/bench/bench_continu.py --work W --out R.json [--fichiers 6]

Tout ce que le banc mesure aujourd'hui passe par `slices/`, dont les tranches
sont découpées **aux silences** (médiane 3,7 s). C'est le régime « phrase par
phrase », c'est-à-dire le seul où une pause coïncide avec une frontière de
phrase — et donc le seul où la segmentation par pauses ne peut pas se tromper.
Un relecteur l'a signalé : les pauses ne sont pas un indicateur fiable de fin de
phrase. Le banc actuel est aveugle à ce reproche par construction.

Ce banc-ci rejoue de la parole enchaînée, dans deux découpes :

    --decoupe enonce    des fenêtres de 8 à 22 s prises entre deux pauses, soit
                        une à trois phrases : la dictée que ce clavier veut
                        servir. Une telle dictée contient au moins une pause
                        **interne**, entre deux phrases, et c'est celle-là que
                        le détecteur ne sait pas distinguer d'une fin.
    --decoupe fichier   les fichiers de 60 s entiers : le pire cas, la queue de
                        la distribution des usages, pas l'usage courant.

et dans deux régimes :

    continu    une seule session, le micro ne se referme jamais tout seul
    hangover   l'application telle qu'elle est : SILENCE_HANGOVER_MS termine
               l'énoncé, l'utilisateur rouvre une session, les textes se
               recollent bout à bout

L'écart entre les deux est le coût de la segmentation par pauses. Le nombre de
sessions du second régime est, lui, le nombre de fois où l'utilisateur aurait dû
rappuyer sur le micro pour dicter une minute.

**Le régime `hangover` est optimiste**, et il faut le lire ainsi : la reprise y
est instantanée (`--reprise 0`), alors qu'en vrai l'utilisateur doit constater
que le micro s'est fermé avant de le rouvrir, et tout ce qu'il dit pendant ce
temps est perdu.

**Coupure intra-phrase** : le service ponctue et capitalise. Une coupure est
comptée intra-phrase quand le segment qu'elle termine ne finit pas sur une
ponctuation forte, ou quand le suivant démarre en minuscule. C'est le jugement
du service sur sa propre sortie, pas le nôtre.
"""

import argparse
import json
import statistics as st
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import vad
from luxasr_client import RATE, CONFIG_APP, decouper, stream
from wer import wer, repetition_ratio
from bench_luxasr import wer_infixe

FORTE = ".!?…"


def note(reference, hyp, decoupe):
    """WER contre la référence du fichier parent.

    Sur un fichier entier, l'alignement est direct — bords rognés, les deux mots
    des extrémités étant tronqués par le découpage du corpus à 60 s. Sur une
    fenêtre, l'hypothèse ne couvre qu'un fragment de la référence : on l'aligne
    alors en infixe, comme le fait déjà `bench_luxasr.py`.
    """
    if not hyp:
        return 1.0
    if decoupe == "fichier":
        return wer(reference, hyp, trim_edges=True)[0]
    return wer_infixe(reference, hyp)[0]


def fenetres(a, lo, hi, mini_pause=0.5):
    """Fenêtres de `lo` à `hi` secondes, bornées par des pauses du locuteur.

    Découper ailleurs qu'à une pause fabriquerait une dictée qui commence ou
    s'arrête en plein mot, ce que personne ne fait. Les bornes sont donc les
    centres des silences, comme dans `prepare_dataset.py` — à ceci près qu'on
    vise ici une longueur d'énoncé réel, pas une tranche de banc.
    """
    bornes = [0.0] + [(x + y) / 2 for x, y in vad.pauses(a, mini_s=mini_pause)]
    bornes.append(len(a) / RATE)
    out, debut = [], 0.0
    for b in bornes[1:]:
        if b - debut < lo:
            continue
        if b - debut > hi:
            debut = b            # trou trop long pour tenir : on repart de là
            continue
        out.append((debut, b))
        debut = b
    return out


def joue(a, config):
    """Une session sur tout l'audio fourni."""
    return stream([(True, b) for b in decouper(a)], config=config)


def intra_phrase(segments):
    """Coupures tombant au milieu d'une phrase, du point de vue du service."""
    n = 0
    for i in range(len(segments) - 1):
        avant, apres = segments[i].strip(), segments[i + 1].strip()
        if not avant:
            continue
        fin_franche = avant[-1] in FORTE
        debut_franc = (not apres) or apres[0].isupper() or not apres[0].isalpha()
        if not (fin_franche and debut_franc):
            n += 1
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--fichiers", type=int, default=6)
    ap.add_argument("--decoupe", choices=["fichier", "enonce"], default="fichier")
    ap.add_argument("--enonce-min", type=float, default=8.0)
    ap.add_argument("--enonce-max", type=float, default=22.0)
    ap.add_argument("--reprise", type=float, default=0.0,
                    help="secondes perdues entre la fermeture du micro et sa réouverture")
    ap.add_argument("--hangover-ms", type=float, default=vad.SILENCE_HANGOVER_MS)
    ap.add_argument("--defaut-serveur", action="store_true")
    args = ap.parse_args()

    config = None if args.defaut_serveur else CONFIG_APP
    manifest = json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))

    # Volet hors ligne : il ne demande aucune session et vaut pour tout le lot.
    offline = []
    for e in manifest:
        a = np.fromfile(e["f32"], dtype="<f4")
        _, coup = vad.trace(a, hangover_ms=args.hangover_ms)
        p = vad.pauses(a, mini_s=0.3)
        offline.append({"id": e["id"], "duree": len(a) / RATE,
                        "coupures": len(coup), "t_coupures": [round(t, 1) for t in coup],
                        "pauses_03": len(p),
                        "pauses_ge_hangover": sum(1 for x, y in p
                                                  if y - x >= args.hangover_ms / 1000)})
    tot_h = sum(o["duree"] for o in offline) / 3600
    print(f"\n🔇 Détecteur seul, sur {len(offline)} fichiers ({tot_h*60:.0f} min) : "
          f"{sum(o['coupures'] for o in offline)} coupures, soit "
          f"{sum(o['coupures'] for o in offline)/max(1e-9, tot_h*60):.1f} par minute "
          f"de parole continue.", flush=True)
    for o in offline:
        print(f"   {o['id']}  {o['coupures']} coupure(s) à {o['t_coupures']}", flush=True)

    # Chaque « énoncé » mesuré : (identifiant, audio, référence du fichier).
    enonces = []
    for e in manifest[:args.fichiers]:
        a = np.fromfile(e["f32"], dtype="<f4")
        if args.decoupe == "fichier":
            enonces.append((e["id"], a, e["reference"]))
        else:
            for k, (x, y) in enumerate(fenetres(a, args.enonce_min, args.enonce_max)):
                enonces.append((f"{e['id']}_{k:02d}",
                                a[int(x * RATE):int(y * RATE)], e["reference"]))
    if args.decoupe == "enonce":
        d = [len(x[1]) / RATE for x in enonces]
        print(f"\n✂️  {len(enonces)} énoncés de {min(d):.0f} à {max(d):.0f} s "
              f"(médiane {st.median(d):.0f} s)", flush=True)

    lignes = []
    for ident, a, reference in enonces:
        duree = len(a) / RATE
        _, coup = vad.trace(a, hangover_ms=args.hangover_ms)
        print(f"\n📄 {ident}  {duree:.0f} s  ·  {len(coup)} coupure(s)", flush=True)

        # --- régime continu ---
        r = joue(a, config)
        w = note(reference, r["texte"], args.decoupe)
        lignes.append({"fichier": ident, "regime": "continu", "wer": w,
                       "mots": len(r["texte"].split()), "sessions": 1, "coupures": 0,
                       "coupures_intra_phrase": 0, "passes": len(r["passes"]),
                       "t_premier_texte": r["t_first"],
                       "repetition": repetition_ratio(r["texte"]),
                       "audio_emis_s": r["audio_emis_s"], "hyp": r["texte"]})
        print(f"   continu    WER {w*100:5.1f} %  {lignes[-1]['mots']} mots  "
              f"{len(r['passes'])} passes", flush=True)
        time.sleep(1.0)

        # --- régime hangover : une session par segment ---
        bornes = [0.0] + list(coup) + [duree]
        segments, passes, emis, t_first = [], 0, 0.0, None
        for i in range(len(bornes) - 1):
            debut = bornes[i] + (args.reprise if i else 0.0)
            fin = bornes[i + 1]
            if fin - debut < 0.2:
                continue
            s = a[int(debut * RATE):int(fin * RATE)]
            rr = joue(s, config)
            segments.append(rr["texte"])
            passes += len(rr["passes"])
            emis += rr["audio_emis_s"]
            if t_first is None and rr["t_first"] is not None:
                t_first = debut + rr["t_first"]
            time.sleep(1.0)
        texte = " ".join(s for s in segments if s).strip()
        w = note(reference, texte, args.decoupe)
        lignes.append({"fichier": ident, "regime": "hangover", "wer": w,
                       "mots": len(texte.split()), "sessions": len(segments),
                       "coupures": len(coup),
                       "coupures_intra_phrase": intra_phrase(segments),
                       "passes": passes, "t_premier_texte": t_first,
                       "repetition": repetition_ratio(texte),
                       "audio_emis_s": emis, "hyp": texte, "segments": segments})
        print(f"   hangover   WER {w*100:5.1f} %  {lignes[-1]['mots']} mots  "
              f"{len(segments)} session(s)  "
              f"{lignes[-1]['coupures_intra_phrase']} coupure(s) intra-phrase", flush=True)

    res = {"date": time.strftime("%Y-%m-%d"),
           "decoupe": args.decoupe,
           "corpus": "Akabi/Luxemburgish_Press_Conferences_Gov",
           "corpus_licence": "non déclarée — audio et transcriptions non redistribués",
           "service": "LuxASR (Uni.lu) v2.3.0",
           "chunk_params": "défaut serveur" if args.defaut_serveur else "2,0 / 0,5 (réglage de l'app)",
           "reprise_s": args.reprise, "hangover_ms": args.hangover_ms,
           "detecteur_hors_ligne": offline, "mesures": lignes}
    args.out.write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    resume(lignes)
    print(f"\n💾 {args.out}")


def resume(lignes):
    if not lignes:
        return
    print(f"\n{'régime':12} {'WER pondéré':>12} {'médiane':>9} {'sessions':>9} "
          f"{'intra-phrase':>13} {'répétition':>11}")
    ag = {}
    for regime in ("continu", "hangover"):
        g = [l for l in lignes if l["regime"] == regime]
        if not g:
            continue
        pond = sum(l["wer"] * l["mots"] for l in g) / max(1, sum(l["mots"] for l in g))
        ag[regime] = pond
        print(f"  {regime:10} {pond*100:11.1f} % {st.median(l['wer'] for l in g)*100:8.1f} % "
              f"{sum(l['sessions'] for l in g):9d} "
              f"{sum(l['coupures_intra_phrase'] for l in g):13d} "
              f"{st.median(l['repetition'] for l in g)*100:10.1f} %")
    if len(ag) == 2:
        d = [(b["wer"] - c["wer"])
             for c in lignes if c["regime"] == "continu"
             for b in lignes if b["regime"] == "hangover" and b["fichier"] == c["fichier"]]
        print(f"\n  écart apparié sur {len(d)} fichiers : médiane "
              f"{st.median(d)*100:+.1f} pt · moyenne {sum(d)/len(d)*100:+.1f} pt "
              f"(hangover moins continu)")


if __name__ == "__main__":
    main()
