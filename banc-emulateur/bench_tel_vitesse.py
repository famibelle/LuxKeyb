#!/usr/bin/env python3
"""Test de vitesse sur le Galaxy A21s : LuxKeyb, Gboard, Clavier Samsung.

Les mesures sont prises dans un champ neutre (Samsung Messages, jamais notre propre
application, dont le processus est partagé avec le clavier).

1. **Temps de traitement d'un appui.** `input tap` rend la main quand l'application
   qui reçoit l'événement l'a traité : enchaîner des appuis sans pause mesure donc
   ce que le clavier met à les absorber. On chronomètre 60 appuis de suite sur les
   touches (cinq lettres, une espace, dix fois), on en retranche le chronomètre de
   60 appuis dans une zone vide de l'écran, qui donne le coût fixe de l'outil (un
   processus Java lancé à chaque appui), et on divise par 60. Ce que fait le clavier
   en arrière-plan après l'appui n'est pas compté ici : c'est le coût processeur.
2. **Coût processeur** : `utime + stime` du processus du clavier pendant ces appuis,
   par appui, et au repos pendant 30 s sans toucher l'écran.
3. **Fluidité** : `dumpsys gfxinfo` du processus, remis à zéro avant chaque série :
   images dessinées, part d'images ratées, 90ᵉ et 99ᵉ centile de la durée d'une image,
   images à latence d'entrée élevée signalées par le système.
4. **Mémoire** : PSS total du processus après la série.

Chaque clavier est mesuré dans deux passes, l'ordre étant inversé à la seconde pour
qu'un échauffement du téléphone ne favorise personne.

    TEL_SERIE=<ip>:<port> python3 bench_tel_vitesse.py [--passes 2]
"""
import argparse, json, os, re, statistics, subprocess, sys, time
from pathlib import Path

SP = Path(__file__).resolve().parent
SERIE = os.environ.get("TEL_SERIE", "")

CLAVIERS = {
    "lux": {"paquet": "com.potomitan.luxkeyboard",
            "ime": "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored"},
    "gboard": {"paquet": "com.google.android.inputmethod.latin",
               "ime": "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"},
    "samsung": {"paquet": "com.samsung.android.honeyboard",
                "ime": "com.samsung.android.honeyboard/.service.HoneyBoardService"},
}
GEOMETRIE = json.loads((SP / "geometrie_vitesse.json").read_text(encoding="utf-8"))
APPUIS = 60          # appuis par série
ZONE_VIDE = (360, 500)   # zone de la conversation, sans effet sur la saisie


def adb(*a, timeout=180):
    if not SERIE:
        sys.exit("TEL_SERIE=<ip>:<port> requis : le port Wi-Fi du téléphone change à chaque connexion")
    return subprocess.run(["adb", "-s", SERIE, *a], capture_output=True, timeout=timeout)


def shell(cmd, timeout=180):
    return adb("shell", cmd, timeout=timeout).stdout.decode("utf-8", "replace")


def pid_de(paquet):
    s = shell(f"pidof {paquet}").split()
    return int(s[0]) if s else None


def jiffies(pid):
    c = shell(f"cat /proc/{pid}/stat").split(") ")[-1].split()
    return (int(c[11]) + int(c[12])) * 10          # ms


def chronometre(commande):
    """Durée en secondes d'une commande shell, chronométrée côté ordinateur : le coût
    de la connexion adb est le même pour la série de touches et pour la série à vide,
    et disparaît dans la soustraction."""
    t0 = time.perf_counter()
    shell(commande)
    return time.perf_counter() - t0


def sequence_touches(geo):
    xs, y = geo["x"], geo["ligne_y"]
    ex, ey = geo["espace"]
    taps = []
    for i in range(APPUIS):
        if i % 6 == 5:
            taps.append(f"input tap {ex} {ey}")
        else:
            taps.append(f"input tap {xs[(i * 4 + i // 6) % len(xs)]} {y}")
    return "; ".join(taps)


def tap_champ():
    xml = adb("exec-out", "uiautomator", "dump", "/dev/tty").stdout.decode("utf-8", "replace")
    m = re.search(r'resource-id="com.samsung.android.messaging:id/message_edit_text"[^>]*'
                  r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    x, y = ((int(m[1]) + int(m[3])) // 2, (int(m[2]) + int(m[4])) // 2) if m else (440, 1500)
    shell(f"input tap {x} {y}; sleep 1.5")


def gfx(paquet):
    t = shell(f"dumpsys gfxinfo {paquet}")

    def nombre(motif):
        m = re.search(motif, t)
        return float(m.group(1)) if m else None
    return {"images": nombre(r"Total frames rendered: (\d+)"),
            "ratees_pct": nombre(r"Janky frames: \d+ \(([\d.]+)%\)"),
            "p50_ms": nombre(r"50th percentile: (\d+)ms"),
            "p90_ms": nombre(r"90th percentile: (\d+)ms"),
            "p99_ms": nombre(r"99th percentile: (\d+)ms"),
            "latence_entree_elevee": nombre(r"Number High input latency: (\d+)")}


def preparer(nom):
    cfg = CLAVIERS[nom]
    shell(f"ime set {cfg['ime']}")
    time.sleep(1.5)
    shell("input keyevent 4; input keyevent 4; input keyevent 3; sleep 0.5")
    shell("am start -a android.intent.action.SENDTO -d sms:0000 -p com.samsung.android.messaging > /dev/null")
    time.sleep(3)
    tap_champ()
    if "mInputShown=true" not in shell("dumpsys input_method"):
        tap_champ()
    return cfg


def mesurer(nom, series=2):
    cfg, geo = preparer(nom), GEOMETRIE[nom]
    pid = pid_de(cfg["paquet"])
    if pid is None:
        sys.exit(f"{nom} : processus introuvable, le clavier n'est pas affiché")
    touches = sequence_touches(geo)
    vide = "; ".join([f"input tap {ZONE_VIDE[0]} {ZONE_VIDE[1]}"] * APPUIS)
    chronometre(touches)                                  # échauffement
    shell("input keyevent 123; input keyevent " + " ".join(["67"] * 120))
    res = {"clavier": nom, "series": []}
    for _ in range(series):
        base = chronometre(vide)
        shell(f"dumpsys gfxinfo {cfg['paquet']} reset")
        j0 = jiffies(pid)
        t = chronometre(touches)
        cpu = jiffies(pid) - j0
        f = gfx(cfg["paquet"])
        shell("input keyevent 123; input keyevent " + " ".join(["67"] * 120))
        res["series"].append({"total_s": t, "base_s": base,
                              "traitement_ms_par_appui": (t - base) * 1000 / APPUIS,
                              "cpu_ms_par_appui": cpu / APPUIS, **f})
    r0 = jiffies(pid)
    time.sleep(30)
    res["cpu_repos_ms_30s"] = jiffies(pid) - r0
    m = re.search(r"TOTAL PSS:\s+(\d+)", shell(f"dumpsys meminfo {cfg['paquet']}"))
    res["pss_mo"] = round(int(m.group(1)) / 1024, 1) if m else None
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--passes", type=int, default=2)
    ap.add_argument("--clavier", choices=list(CLAVIERS), action="append")
    args = ap.parse_args()
    ordre = args.clavier or ["lux", "samsung", "gboard"]
    resultats = []
    for p in range(args.passes):
        for nom in (ordre if p % 2 == 0 else ordre[::-1]):
            r = mesurer(nom)
            r["passe"] = p + 1
            resultats.append(r)
            s = r["series"]
            print(f"passe {p+1} {nom:8s} traitement {statistics.mean(x['traitement_ms_par_appui'] for x in s):6.1f} ms/appui"
                  f"  cpu {statistics.mean(x['cpu_ms_par_appui'] for x in s):5.1f} ms/appui"
                  f"  repos {r['cpu_repos_ms_30s']} ms/30 s  pss {r['pss_mo']} Mo", flush=True)
    (SP / "vitesse_tel.json").write_text(json.dumps(resultats, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
