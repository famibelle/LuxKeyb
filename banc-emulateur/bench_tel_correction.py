#!/usr/bin/env python3
"""Mots remplacés par la correction automatique, sur le Galaxy A21s.

Les 20 phrases sans diacritique du banc de prédiction sont tapées mot par mot, une
espace après chacun (c'est l'espace qui déclenche la correction), avec les réglages
par défaut de chaque clavier. On lit ensuite le texte du champ et on le compare à ce
qui a été tapé :

- **mot remplacé** : le mot final est un autre mot, une fois casse et accents repliés ;
- **accents seuls** : même mot, seuls les diacritiques changent ;
- **casse seule** : même mot, seule la majuscule change.

Seul le premier type est une correction indue au sens où le mot tapé était juste :
tous les mots du corpus sont du luxembourgeois correct. Les deux autres sont ce que
la langue demande (`Groussschreiwung`, accents) et ne sont pas comptés contre le clavier.

    TEL_SERIE=<ip>:<port> python3 bench_tel_correction.py
"""
import difflib, json, os, re, sys, time, unicodedata
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
os.environ.setdefault("TEL_SERIE", "")
import bench_tel_vitesse as v  # noqa: E402


def plie(m):
    return "".join(c for c in unicodedata.normalize("NFD", m.lower()) if not unicodedata.combining(c))


def sans_accent(m):
    return "".join(c for c in unicodedata.normalize("NFD", m) if not unicodedata.combining(c))


def phrases():
    journal = json.loads((SP / "journal_tel_lux.json").read_text(encoding="utf-8"))
    dernier = {}
    for e in journal:
        dernier[e["phrase"]] = e
    return [f'{dernier[ip]["contexte"]} {dernier[ip]["attendu"]}'.lower() for ip in sorted(dernier)]


def texte_du_champ():
    xml = v.adb("exec-out", "uiautomator", "dump", "/dev/tty").stdout.decode("utf-8", "replace")
    m = re.search(r'text="([^"]*)"[^>]*resource-id="com.samsung.android.messaging:id/message_edit_text"', xml) \
        or re.search(r'resource-id="com.samsung.android.messaging:id/message_edit_text"[^>]*text="([^"]*)"', xml)
    return m.group(1).replace("&apos;", "'").replace("&quot;", '"').replace("&amp;", "&") if m else None


def comparer(tape, final):
    a, b = tape.split(), final.split()
    sm = difflib.SequenceMatcher(a=[plie(x) for x in a], b=[plie(x) for x in b], autojunk=False)
    remplaces, exemples = 0, []
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op != "equal":
            remplaces += max(i2 - i1, j2 - j1)
            exemples.append((" ".join(a[i1:i2]), " ".join(b[j1:j2])))
    accents = casse = 0
    for x, y in zip(a, b):
        if plie(x) == plie(y):
            if sans_accent(x) != sans_accent(y) and sans_accent(x.lower()) == sans_accent(y.lower()) and x.lower() != y.lower():
                accents += 1
            elif x != y and x.lower() == y.lower():
                casse += 1
    return remplaces, accents, casse, exemples


def main():
    ordre = sys.argv[1:] or ["lux", "samsung", "gboard"]
    sortie = {}
    ph = phrases()
    for nom in ordre:
        v.preparer(nom)
        ex, ey = v.GEOMETRIE[nom]["espace"]
        mots = remplaces = accents = casse = 0
        exemples, finals = [], []
        for phrase in ph:
            v.shell("input keyevent 123; sleep 0.2; input keyevent " + " ".join(["67"] * 140) + "; sleep 0.5")
            for w in phrase.split():
                v.shell(f"input text {w}; sleep 0.25; input tap {ex} {ey}; sleep 0.6")
            final = (texte_du_champ() or "").strip()
            if not final:
                print(f"  ! {nom} : champ illisible pour « {phrase[:30]}… »", flush=True)
                continue
            finals.append({"tape": phrase, "final": final})
            r, a, c, e = comparer(phrase, final)
            mots += len(phrase.split())
            remplaces += r
            accents += a
            casse += c
            exemples += e
        sortie[nom] = {"mots": mots, "remplaces": remplaces, "accents_seuls": accents,
                       "casse_seule": casse, "exemples": exemples[:12], "phrases": finals}
        print(f"{nom:8s} {mots} mots : {remplaces} remplacés, {accents} accents seuls, "
              f"{casse} casse seule ; ex. {exemples[:6]}", flush=True)
    (SP / "correction_tel2.json").write_text(json.dumps(sortie, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
