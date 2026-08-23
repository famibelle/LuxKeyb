#!/usr/bin/env python3
"""Exporte une page du site en PDF, prête à être envoyée en pièce jointe.

En business to government, un dossier ne circule pas sous forme de lien : il
passe par des gens qui l'impriment, l'annotent et le joignent à un dossier
d'instruction. Une page web ne fait rien de tout cela.

Le rendu passe par Chrome sans interface, sur un serveur local éphémère : en
file:// les chemins relatifs et la lecture de stats/downloads.json échouent
silencieusement, et le PDF sortirait avec un compteur figé.

    python3 docs/scripts/build_pdf.py                    # partenaires.html
    python3 docs/scripts/build_pdf.py presskit.html      # une autre page

Sortie : docs/presse/<nom>.pdf
"""

import http.server
import shutil
import socket
import socketserver
import subprocess
import sys
import threading
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
SORTIE = DOCS / "presse"

# Le premier binaire trouvé gagne ; l'ordre suit la fréquence d'installation.
CHROMES = ["google-chrome", "google-chrome-stable", "chromium", "chromium-browser"]

# Chrome n'attend pas les requêtes réseau avant d'imprimer : sans budget de
# temps virtuel, le PDF part avant que le compteur d'installations soit lu.
BUDGET_MS = 4000

# Le nom du fichier est ce que voit le destinataire dans sa boîte mail :
# « partenaires.pdf » ne dit rien, « dossier-partenariat.pdf » se classe seul.
NOMS = {
    "partenaires.html": "dossier-partenariat",
    "presskit.html": "dossier-de-presse",
}


def trouver_chrome() -> str | None:
    for nom in CHROMES:
        chemin = shutil.which(nom)
        if chemin:
            return chemin
    return None


def servir(racine: Path) -> tuple[socketserver.TCPServer, int]:
    """Démarre un serveur sur un port libre choisi par le système."""

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(racine), **kwargs)

        def log_message(self, *args):  # silence
            pass

    with socket.socket() as sonde:
        sonde.bind(("127.0.0.1", 0))
        port = sonde.getsockname()[1]

    serveur = socketserver.TCPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    return serveur, port


def main(argv: list[str]) -> int:
    page = argv[1] if len(argv) > 1 else "partenaires.html"
    if not (DOCS / page).exists():
        print(f"page introuvable : {page}")
        return 1

    chrome = trouver_chrome()
    if not chrome:
        print("aucun binaire Chrome ou Chromium trouvé : " + ", ".join(CHROMES))
        return 1

    SORTIE.mkdir(parents=True, exist_ok=True)
    cible = SORTIE / (NOMS.get(page, Path(page).stem) + ".pdf")

    serveur, port = servir(DOCS)
    try:
        resultat = subprocess.run(
            [
                chrome,
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--no-pdf-header-footer",
                f"--virtual-time-budget={BUDGET_MS}",
                f"--print-to-pdf={cible}",
                f"http://127.0.0.1:{port}/{page}",
            ],
            capture_output=True,
            text=True,
            timeout=120,
        )
    finally:
        serveur.shutdown()

    if not cible.exists():
        print("échec du rendu :")
        print(resultat.stderr[-2000:])
        return 1

    poids = cible.stat().st_size / 1024
    print(f"{cible.relative_to(DOCS.parent)} : {poids:.0f} Ko")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
