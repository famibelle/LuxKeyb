#!/usr/bin/env python3
"""Rassemble les traductions de la fiche en un seul fichier pour la Play Console.

    python3 build_import.py

La Console (Fiche Play Store › Traductions › Importer un fichier) accepte un
fichier « dans n'importe quel format » et en détecte les langues. On lui donne
donc un texte simple, une section par langue, chaque champ annoncé par son nom
anglais tel que la Console le nomme. Le français n'y est pas : c'est la langue
par défaut de la fiche, qui se colle directement depuis `fr-FR/`.

Les dossiers de langue restent la source ; ce fichier se refait après chaque
modification d'un texte.
"""

import pathlib

HERE = pathlib.Path(__file__).resolve().parent
SORTIE = HERE / "import-console-traductions.txt"

# (dossier, nom de la langue tel que la Console l'affiche, code)
LANGUES = [
    ("de-DE", "German", "de-DE"),
    ("en-US", "English (United States)", "en-US"),
    ("pt-PT", "Portuguese (Portugal)", "pt-PT"),
    ("lb", "Luxembourgish", "lb"),
]
CHAMPS = [
    ("App name", "title.txt", 30),
    ("Short description", "short_description.txt", 80),
    ("Full description", "full_description.txt", 4000),
]


def longueur(texte: str) -> int:
    # la Console compte en unités UTF-16 : un drapeau ou certains émojis valent 2
    return len(texte.encode("utf-16-le")) // 2


def main() -> None:
    blocs = []
    for dossier, nom, code in LANGUES:
        lignes = [f"===== Language: {nom} ({code}) =====", ""]
        for champ, fichier, limite in CHAMPS:
            texte = (HERE / dossier / fichier).read_text(encoding="utf-8").strip()
            if longueur(texte) > limite:
                raise SystemExit(f"{dossier}/{fichier} : {longueur(texte)} > {limite}")
            lignes += [f"{champ}:", texte, ""]
        blocs.append("\n".join(lignes))
    SORTIE.write_text("\n\n".join(blocs).rstrip() + "\n", encoding="utf-8")
    print(f"{SORTIE.name} : {len(LANGUES)} langues")


if __name__ == "__main__":
    main()
