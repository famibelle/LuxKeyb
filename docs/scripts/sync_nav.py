#!/usr/bin/env python3
"""Recopie la barre de navigation dans toutes les pages du site.

La source est docs/_includes/nav.html. Les pages du site ne passent pas toutes
par Jekyll (les .html n'ont pas de front matter et sont publiés tels quels), et
un `include` Liquid ne les atteindrait donc pas. Ce script fait le travail :
il remplace, dans chaque page, le bloc compris entre les marqueurs

    <!-- nav:start --> ... <!-- nav:end -->

et marque d'un aria-current le lien qui pointe vers la page traitée.

    python3 docs/scripts/sync_nav.py            # synchronise
    python3 docs/scripts/sync_nav.py --check    # vérifie sans écrire
"""

import re
import sys
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
SOURCE = DOCS / "_includes" / "nav.html"
START, END = "<!-- nav:start -->", "<!-- nav:end -->"
BLOCK_RE = re.compile(re.escape(START) + r".*?" + re.escape(END), re.S)
# L'ancienne barre, en un seul bloc <nav class="site">…</nav>, sans marqueurs.
LEGACY_RE = re.compile(r'<nav class="site">.*?</nav>', re.S)


GROUP_RE = re.compile(r"<details\b.*?</details>", re.S)


def nav_for(page: Path, template: str) -> str:
    """Rend la barre pour une page donnée, en signalant la page courante.

    Quand cette page est rangée dans un groupe replié, le résumé du groupe est
    marqué lui aussi : sinon la barre n'indique rien du tout sur, par exemple,
    la page du tract.
    """
    name = page.name.replace(".md", ".html")
    marked = template
    if name == "index.html":
        marked = marked.replace('<a href="./">', '<a href="./" aria-current="page">', 1)
    else:
        marked = marked.replace(f'<a href="{name}">', f'<a href="{name}" aria-current="page">', 1)

        def mark_group(match):
            block = match.group(0)
            if f'href="{name}"' not in block:
                return block
            return block.replace("<summary>", '<summary class="in-section">', 1)

        marked = GROUP_RE.sub(mark_group, marked)
    return f"{START}\n{marked.strip()}\n{END}"


def main() -> int:
    check = "--check" in sys.argv
    template = SOURCE.read_text(encoding="utf-8")
    # Ne garder que le <nav>, pas le commentaire d'en-tête du fichier source.
    template = template[template.index("<nav") :]

    pages = sorted(
        p for p in list(DOCS.glob("*.html")) + list(DOCS.glob("*.md"))
        if BLOCK_RE.search(p.read_text(encoding="utf-8"))
        or LEGACY_RE.search(p.read_text(encoding="utf-8"))
    )
    if not pages:
        sys.exit("Aucune page ne contient de barre de navigation à synchroniser.")

    stale = []
    for page in pages:
        text = page.read_text(encoding="utf-8")
        block = nav_for(page, template)
        new = BLOCK_RE.sub(lambda _: block, text) if BLOCK_RE.search(text) \
            else LEGACY_RE.sub(lambda _: block, text, count=1)
        if new == text:
            continue
        stale.append(page.name)
        if not check:
            page.write_text(new, encoding="utf-8")

    if check:
        if stale:
            print("Barre de navigation à resynchroniser : " + ", ".join(stale))
            return 1
        print(f"{len(pages)} pages à jour.")
        return 0

    print(f"{len(stale)}/{len(pages)} pages mises à jour"
          + (" : " + ", ".join(stale) if stale else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
