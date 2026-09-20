#!/usr/bin/env python3
"""Mots modifiés par le clavier, en tapant les touches, sur l'émulateur (AVD
`kreyol_playstore`, Gboard 18.2.4 avec le luxembourgeois seul, correction automatique
par défaut). Même protocole que `bench_tel_correction_touches.py` : les 20 phrases sans
diacritique, une espace après chaque mot, on compare le texte du champ à ce qui a été
tapé.

    python3 bench_emu_correction.py [lux gboard18]
"""
import json, re, sys, time
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
import bench_emu_touches as e  # noqa: E402
import bench_tel_correction as c  # noqa: E402


def texte_du_champ():
    xml = e.adb("exec-out", "uiautomator", "dump", "/dev/tty").stdout.decode("utf-8", "replace")
    m = re.search(r'text="([^"]*)"[^>]*resource-id="com.google.android.apps.messaging:id/compose_message_text"', xml) \
        or re.search(r'resource-id="com.google.android.apps.messaging:id/compose_message_text"[^>]*text="([^"]*)"', xml)
    return m.group(1).replace("&apos;", "'").replace("&quot;", '"').replace("&amp;", "&") if m else None


def main():
    ordre = sys.argv[1:] or ["lux", "gboard18"]
    ph = c.phrases()
    sortie = {}
    for nom in ordre:
        cfg, carte = e.CLAVIERS[nom], e.CARTES[nom]
        e.adb("shell", "ime", "set", cfg["ime"])
        time.sleep(1.5)
        if not e.ouvrir_champ():
            sys.exit("clavier absent")
        sx, sy = cfg["espace"]
        mots = modifies = accents = casse = 0
        finals, exemples = [], []
        for phrase in ph:
            if not e.clavier_visible() and not e.ouvrir_champ():
                sys.exit("clavier perdu")
            e.shell("input keyevent 123; sleep 0.2; input keyevent " + " ".join(["67"] * 200) + "; sleep 0.6")
            for w in phrase.split():
                e.shell(e.frappe(w, carte) + f"; input tap {sx} {sy}; sleep 0.7", timeout=120)
            final = (texte_du_champ() or "").strip()
            finals.append({"tape": phrase, "final": final})
            if not final:
                print(f"  ! {nom} : champ illisible", flush=True)
                continue
            r, a, ca, ex = c.comparer(phrase, final)
            mots += len(phrase.split())
            modifies += r
            accents += a
            casse += ca
            exemples += ex
        sortie[nom] = {"mots": mots, "modifies": modifies, "accents_seuls": accents,
                       "casse_seule": casse, "exemples": exemples, "phrases": finals}
        print(f"{nom:9s} {mots} mots : {modifies} modifiés, {accents} accents seuls, {casse} casse seule",
              flush=True)
    (SP / "correction_emu_touches.json").write_text(json.dumps(sortie, ensure_ascii=False, indent=1),
                                                    encoding="utf-8")


if __name__ == "__main__":
    main()
