#!/usr/bin/env python3
"""Réanalyse toutes les captures du banc et imprime un tableau de synthèse."""
import glob, os, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from analyse_clavier import analyse


def deduire_y0(chemin):
    """Haut du bloc de touches, déduit du fond du clavier."""
    im = Image.open(chemin).convert("RGB")
    px, (w, h) = im.load(), im.size
    # En paysage, une bande noire borde l'écran (encoche) : la colonne témoin
    # doit être prise après elle, sinon tout paraît uniforme et rien n'est trouvé.
    xr = 3
    while xr < w // 3 and sum(px[xr, h // 2]) / 3 < 60:
        xr += 1
    xr += 2
    y = h - 1
    while y > 0 and sum(px[xr, y]) / 3 < 90:      # barre de navigation
        y -= 1
    fond = px[xr, y - 5]
    while y > 0 and sum(abs(a - b) for a, b in zip(px[xr, y], fond)) < 14:
        y -= 1
    return y + 1


def main(dossier):
    lignes = []
    for chemin in sorted(glob.glob(f"{dossier}/*_portrait.png")) + \
                  sorted(glob.glob(f"{dossier}/*_paysage.png")):
        nom = os.path.basename(chemin)[:-4]
        try:
            res = analyse(chemin, deduire_y0(chemin))
        except Exception as e:
            lignes.append((nom, [("analyse", "ECHEC", str(e))]))
            continue
        lignes.append((nom, res))

    largeur = max(len(n) for n, _ in lignes) + 2
    echecs_total = 0
    for nom, res in lignes:
        echecs = [(n, d) for n, v, d in res if v == "ECHEC"]
        echecs_total += len(echecs)
        etat = "OK" if not echecs else f"{len(echecs)} ECHEC"
        print(f"{nom:<{largeur}} {etat}")
        for n, d in echecs:
            print(f"{'':<{largeur}}   → {n} : {d}")
    # Une capture absente sortait du tableau sans un mot : l'appareil paraissait
    # simplement ne pas exister, et « 0 échec » couvrait une orientation jamais
    # vue. Les fichiers d'information, écrits pour tout AVD ayant démarré, disent
    # ce qui aurait dû être là.
    attendus = {os.path.basename(f)[:-len("_info.txt")]
                for f in glob.glob(f"{dossier}/*_info.txt")}
    presents = {n for n, _ in lignes}
    manquants = sorted(f"{avd}_{o}" for avd in attendus for o in ("portrait", "paysage")
                       if f"{avd}_{o}" not in presents)
    for nom in manquants:
        print(f"{nom:<{largeur}} MANQUANTE")

    print()
    resume = f"{len(lignes)} captures analysées, {echecs_total} contrôle(s) en échec"
    if manquants:
        resume += f", {len(manquants)} capture(s) manquante(s)"
    print(resume)


if __name__ == "__main__":
    main(sys.argv[1])
