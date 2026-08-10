#!/usr/bin/env python3
"""Recopie la barre de navigation et le pied de page dans toutes les pages.

Les sources sont docs/_includes/nav.html et docs/_includes/footer.html. Les
pages du site ne passent pas toutes par Jekyll (les .html n'ont pas de front
matter et sont publiés tels quels), et un `include` Liquid ne les atteindrait
donc pas. Ce script fait le travail : il remplace, dans chaque page, les blocs
compris entre les marqueurs

    <!-- nav:start --> ... <!-- nav:end -->
    <!-- footer:start --> ... <!-- footer:end -->

et marque d'un aria-current le lien qui pointe vers la page traitée.

Le pied de page est arrivé après la barre : les pages qui n'ont pas encore ses
marqueurs le reçoivent automatiquement, juste avant </body> pour les pages HTML
et à la fin du fichier pour les pages Markdown.

    python3 docs/scripts/sync_nav.py            # synchronise
    python3 docs/scripts/sync_nav.py --check    # vérifie sans écrire
"""

import re
import sys
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
NAV_SOURCE = DOCS / "_includes" / "nav.html"
FOOTER_SOURCE = DOCS / "_includes" / "footer.html"

NAV_START, NAV_END = "<!-- nav:start -->", "<!-- nav:end -->"
FOOTER_START, FOOTER_END = "<!-- footer:start -->", "<!-- footer:end -->"
NAV_RE = re.compile(re.escape(NAV_START) + r".*?" + re.escape(NAV_END), re.S)
FOOTER_RE = re.compile(re.escape(FOOTER_START) + r".*?" + re.escape(FOOTER_END), re.S)
# L'ancienne barre, en un seul bloc <nav class="site">…</nav>, sans marqueurs.
LEGACY_RE = re.compile(r'<nav class="site">.*?</nav>', re.S)

GROUP_RE = re.compile(r"<details\b.*?</details>", re.S)
BODY_END_RE = re.compile(r"\n?</body>", re.I)


def mark_current(html: str, href: str) -> str:
    """Pose aria-current sur le premier lien pointant vers `href`.

    Le lien est cherché par son attribut href et non par une chaîne complète :
    certains portent aussi une classe (la marque, l'appel à l'action), et une
    comparaison littérale les manquerait silencieusement.
    """
    link_re = re.compile(r'<a\b(?![^>]*aria-current)([^>]*\bhref="' + re.escape(href) + r'")')
    return link_re.sub(r'<a\1 aria-current="page"', html, count=1)


def render(page: Path, nav: str, footer: str) -> tuple[str, str]:
    """Rend la barre et le pied pour une page, en signalant la page courante.

    Quand cette page est rangée dans un menu replié, le résumé du menu est
    marqué lui aussi : sinon la barre n'indique rien du tout sur, par exemple,
    la page du tract.
    """
    name = page.name.replace(".md", ".html")
    href = "./" if name == "index.html" else name

    marked_nav = mark_current(nav, href)

    def mark_group(match):
        block = match.group(0)
        if f'href="{href}"' not in block:
            return block
        return block.replace("<summary>", '<summary class="in-section">', 1)

    if name != "index.html":
        marked_nav = GROUP_RE.sub(mark_group, marked_nav)

    return (
        f"{NAV_START}\n{marked_nav.strip()}\n{NAV_END}",
        f"{FOOTER_START}\n{mark_current(footer, href).strip()}\n{FOOTER_END}",
    )


def read_source(path: Path, tag: str) -> str:
    """Lit un include en écartant le commentaire d'en-tête du fichier."""
    text = path.read_text(encoding="utf-8")
    return text[text.index(tag) :]


def insert_footer(text: str, block: str, page: Path) -> str:
    """Ajoute le pied à une page qui n'en a pas encore les marqueurs."""
    if page.suffix == ".html" and BODY_END_RE.search(text):
        return BODY_END_RE.sub("\n" + block + "\n</body>", text, count=1)
    return text.rstrip("\n") + "\n\n" + block + "\n"


def main() -> int:
    check = "--check" in sys.argv
    nav = read_source(NAV_SOURCE, "<nav")
    footer = read_source(FOOTER_SOURCE, "<footer")

    pages = sorted(
        p for p in list(DOCS.glob("*.html")) + list(DOCS.glob("*.md"))
        if NAV_RE.search(p.read_text(encoding="utf-8"))
        or LEGACY_RE.search(p.read_text(encoding="utf-8"))
    )
    if not pages:
        sys.exit("Aucune page ne contient de barre de navigation à synchroniser.")

    stale = []
    for page in pages:
        text = page.read_text(encoding="utf-8")
        nav_block, footer_block = render(page, nav, footer)

        new = NAV_RE.sub(lambda _: nav_block, text) if NAV_RE.search(text) \
            else LEGACY_RE.sub(lambda _: nav_block, text, count=1)
        new = FOOTER_RE.sub(lambda _: footer_block, new) if FOOTER_RE.search(new) \
            else insert_footer(new, footer_block, page)

        if new == text:
            continue
        stale.append(page.name)
        if not check:
            page.write_text(new, encoding="utf-8")

    if check:
        if stale:
            print("Pages à resynchroniser : " + ", ".join(stale))
            return 1
        print(f"{len(pages)} pages à jour.")
        return 0

    print(f"{len(stale)}/{len(pages)} pages mises à jour"
          + (" : " + ", ".join(stale) if stale else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
