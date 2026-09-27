#!/usr/bin/env python3
"""Latence de prédiction sur le Galaxy A21s : combien de temps après l'apparition d'une
lettre dans le champ la barre de suggestions se met-elle à jour ?

Une vidéo d'écran (`screenrecord`, environ 60 images par seconde autour d'un
changement) est enregistrée pendant qu'on tape, puis analysée sur l'ordinateur :

- dans la **zone du champ**, la première image où le texte change après un appui est
  l'instant où la lettre apparaît ;
- dans la **zone de la barre**, la première image où son contenu change ensuite est
  l'instant où la suggestion suit.

Le délai est l'écart entre les deux, lu dans une seule et même vidéo : aucune
horloge n'est à synchroniser, ni celle de l'appareil ni celle de l'ordinateur. Il ne
comprend donc pas le temps de l'appui jusqu'à la lettre, mais tout ce que le clavier
met à recalculer ses propositions une fois la lettre posée. Résolution : une image,
soit environ 17 ms.

Les appuis sont espacés de 1,6 s, cinq lettres puis une espace, trois fois : dix-huit
appuis par enregistrement.

    TEL_SERIE=<ip>:<port> python3 bench_tel_latence.py [--repetitions 3]
"""
import argparse, json, os, re, statistics, subprocess, sys, time
from pathlib import Path
import numpy as np

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
os.environ.setdefault("TEL_SERIE", "")
import bench_tel_vitesse as v  # noqa: E402

LARGEUR, HAUTEUR = 720, 1600
BARRES = {"gboard": (0, 1010, 720, 1090), "samsung": (0, 945, 720, 1010), "lux": (0, 975, 720, 1130)}
PAS = 1.6
SEUIL_PX = 40            # écart de niveau de gris qui compte comme un pixel changé
SEUIL_CHAMP = 100        # pixels changés dans le champ pour qu'une image compte
SEUIL_BARRE = 150        # pixels changés dans la barre : un mot qui change


def bornes_champ():
    xml = v.adb("exec-out", "uiautomator", "dump", "/dev/tty").stdout.decode("utf-8", "replace")
    m = re.search(r'resource-id="com.samsung.android.messaging:id/message_edit_text"[^>]*'
                  r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    return tuple(int(x) for x in m.groups()) if m else None


def enregistrer(nom, geo, champ):
    xs, y = geo["x"], geo["ligne_y"]
    ex, ey = geo["espace"]
    taps, duree = [], 1.2
    lettres = 0
    for i in range(18):
        if i % 6 == 5:
            taps.append(f"input tap {ex} {ey}; sleep {PAS}")
        else:
            taps.append(f"input tap {xs[(lettres * 2 + i) % len(xs)]} {y}; sleep {PAS}")
            lettres += 1
    v.shell("input keyevent 123; input keyevent " + " ".join(["67"] * 60) + "; sleep 0.8")
    v.shell("rm -f /sdcard/lat.mp4")
    v.shell(f"nohup screenrecord --bit-rate 6000000 --time-limit {int(18 * PAS + 5)} /sdcard/lat.mp4 "
            "> /dev/null 2>&1 &")
    time.sleep(1.2)
    v.shell("; ".join(taps), timeout=120)
    time.sleep(3.5)
    cible = SP / f"lat_{nom}.mp4"
    v.adb("pull", "/sdcard/lat.mp4", str(cible))
    return cible


def images(mp4, zone):
    """Niveaux de gris de la zone, image par image, avec l'horodatage de chaque image."""
    x1, y1, x2, y2 = zone
    w, h = x2 - x1, y2 - y1
    sortie = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(mp4), "-vf", f"crop={w}:{h}:{x1}:{y1},format=gray",
         "-vsync", "passthrough", "-f", "rawvideo", "-"], capture_output=True).stdout
    ts = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries",
         "frame=best_effort_timestamp_time", "-of", "csv=p=0", str(mp4)],
        capture_output=True, text=True).stdout.split()
    n = len(sortie) // (w * h)
    return np.frombuffer(sortie[:n * w * h], np.uint8).reshape(n, h, w).astype(np.int16), \
        [float(t) for t in ts[:n]]


def changements(mp4, zone, seuil):
    """Instants où le nombre de pixels changés d'une image à l'autre dépasse le seuil."""
    im, ts = images(mp4, zone)
    if len(im) < 2:
        return []
    px = (np.abs(im[1:] - im[:-1]) > SEUIL_PX).sum(axis=(1, 2))
    return [(ts[i + 1], int(px[i])) for i in range(len(px)) if px[i] >= seuil]


def grappes(evts, ecart=0.08):
    """Les changements consécutifs à moins de 80 ms forment une grappe : (début, images)."""
    out = []
    for t, n in evts:
        if out and t - out[-1][1] <= ecart:
            out[-1][1] = t
            out[-1][2] += 1
        else:
            out.append([t, t, 1])
    return [(d, k) for d, _, k in out]


SEUIL_BARRE_PAR_CLAVIER = {"gboard": 150, "samsung": 1200, "lux": 1200}   # le bruit de fond de la barre
                                          # de LuxKeyb et de Samsung (~600 px toutes les 0,5 s) est plus bas
FENETRE_BASE = 0.6       # s : le curseur clignote deux fois par seconde, il est éteint au moins une fois
SAUT_PX = 6              # élargissement du texte qui vaut une lettre (le curseur n'en fait que 3)


def lettres_vues(mp4, champ):
    """Instants où une lettre apparaît dans le champ.

    Le curseur clignote de plusieurs milliers de pixels toutes les 0,5 s : compter les
    pixels changés noie une lettre. On suit plutôt l'abscisse du dernier pixel sombre
    de la ligne de texte. Le curseur la fait varier de 3 px ; une lettre la déplace de
    6 px ou plus et l'y laisse. On la compare au minimum des 0,6 s précédentes, où le
    curseur a été éteint au moins une fois."""
    im, ts = images(mp4, champ)
    if len(im) < 2:
        return []
    sombre = im < 130
    col = sombre.any(axis=1)                         # (images, colonnes)
    droite = np.where(col.any(axis=1), col.shape[1] - 1 - np.argmax(col[:, ::-1], axis=1), -1)
    evts, hors = [], False
    for i in range(1, len(ts)):
        base = min([droite[j] for j in range(i) if ts[i] - ts[j] <= FENETRE_BASE] or [droite[i]])
        haut = droite[i] - base >= SAUT_PX
        if haut and not hors and (not evts or ts[i] - evts[-1] > 0.4):
            evts.append(ts[i])
        hors = haut
    return evts


def latences(mp4, nom, champ):
    """Délai entre l'apparition d'une lettre et le premier changement de la barre."""
    lettres = lettres_vues(mp4, champ)
    barres = changements(mp4, BARRES[nom], SEUIL_BARRE_PAR_CLAVIER[nom])
    res = []
    for t in lettres:
        suite = [tb for tb, _ in barres if t - 0.03 <= tb <= t + 1.0]
        res.append((suite[0] - t) * 1000 if suite else None)
    return lettres, res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repetitions", type=int, default=3)
    ap.add_argument("--clavier", choices=list(v.CLAVIERS), action="append")
    args = ap.parse_args()
    ordre = args.clavier or ["lux", "samsung", "gboard"]
    toutes = {k: [] for k in ordre}
    for r in range(args.repetitions):
        for nom in (ordre if r % 2 == 0 else ordre[::-1]):
            v.preparer(nom)
            # Le champ change de taille dès la première lettre (le bouton d'envoi
            # apparaît) : on le relève une lettre posée, puis on l'efface.
            geo = v.GEOMETRIE[nom]
            v.shell(f"input tap {geo['x'][0]} {geo['ligne_y']}; sleep 0.8")
            bornes = bornes_champ()
            v.shell("input keyevent 67; sleep 0.5")
            if bornes is None:
                sys.exit(f"{nom} : champ de saisie introuvable")
            x1, y1, x2, y2 = bornes
            champ = (x1 + 15, y1, x2 - 90, y2)      # le texte seul, sans les icônes
            mp4 = enregistrer(nom, v.GEOMETRIE[nom], champ)
            lettres, lat = latences(mp4, nom, champ)
            mesures = [x for x in lat if x is not None]
            toutes[nom] += mesures
            print(f"répétition {r+1} {nom:8s} lettres vues {len(lettres):2d}, barre suivie {len(mesures):2d}"
                  + (f", médiane {statistics.median(mesures):5.0f} ms" if mesures else ""), flush=True)
    resume = {}
    for nom, m in toutes.items():
        if m:
            m = sorted(m)
            resume[nom] = {"n": len(m), "mediane_ms": statistics.median(m),
                           "p90_ms": m[min(len(m) - 1, int(0.9 * (len(m) - 1) + 0.5))],
                           "moyenne_ms": statistics.mean(m), "brut": m}
    (SP / "latence_tel.json").write_text(json.dumps(resume, indent=1), encoding="utf-8")
    for nom, r in resume.items():
        print(f"{nom:8s} n={r['n']:3d}  médiane {r['mediane_ms']:5.0f} ms  p90 {r['p90_ms']:5.0f} ms")


if __name__ == "__main__":
    main()
