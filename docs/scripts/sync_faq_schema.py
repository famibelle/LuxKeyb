#!/usr/bin/env python3
"""Régénère le balisage FAQPage de docs/faq.html à partir de son contenu.

Google n'affiche les questions dans ses résultats que si la page porte un
schema.org/FAQPage, et un balisage qui ne dit pas la même chose que la page
visible est une raison de disqualification. Plutôt que de tenir deux copies à
la main, ce script lit les questions dans le HTML et réécrit le bloc JSON-LD
compris entre les marqueurs

    <!-- faq-schema:start --> ... <!-- faq-schema:end -->

Les questions sont les <summary> des <details class="qa">, les réponses le
texte des <div class="a"> correspondants. Le bloc .headline en tête de page
n'y figure pas : il reformule la première question de la section « Avant
d'installer », et un FAQPage qui pose deux fois la même question est écarté.

    python3 docs/scripts/sync_faq_schema.py            # régénère
    python3 docs/scripts/sync_faq_schema.py --check    # vérifie sans écrire
"""

import html
import json
import re
import sys
from pathlib import Path

PAGE = Path(__file__).resolve().parents[1] / "faq.html"
START, END = "<!-- faq-schema:start -->", "<!-- faq-schema:end -->"
BLOCK_RE = re.compile(re.escape(START) + r".*?" + re.escape(END), re.S)
QA_RE = re.compile(
    r'<details class="qa">\s*<summary>(.*?)</summary>\s*<div class="a">(.*?)</div>\s*</details>',
    re.S,
)


def texte(fragment: str) -> str:
    """Réduit un fragment HTML au texte que lira un moteur de recherche.

    Le remplacement des balises par une espace évite de coller deux mots que
    seule une balise séparait ; il laisse en revanche des espaces parasites
    devant une virgule ou derrière une parenthèse ouvrante, que la seconde
    passe rattrape. Les autres signes doubles (: ; ! ?) gardent la leur, qui
    est correcte en français.
    """
    fragment = re.sub(r"<[^>]+>", " ", fragment)
    plat = re.sub(r"\s+", " ", html.unescape(fragment)).strip()
    plat = re.sub(r"\s+([,.)\]])", r"\1", plat)
    return re.sub(r"([(\[])\s+", r"\1", plat)


def questions(page: str) -> list[tuple[str, str]]:
    return [(texte(q), texte(a)) for q, a in QA_RE.findall(page)]


def rendu(paires: list[tuple[str, str]]) -> str:
    donnees = {
        "@context": "https://schema.org",
        "@type": "FAQPage",
        "mainEntity": [
            {
                "@type": "Question",
                "name": q,
                "acceptedAnswer": {"@type": "Answer", "text": a},
            }
            for q, a in paires
        ],
    }
    corps = json.dumps(donnees, ensure_ascii=False, indent=2)
    return f'{START}\n<script type="application/ld+json">\n{corps}\n</script>\n{END}'


def main() -> int:
    check = "--check" in sys.argv
    page = PAGE.read_text(encoding="utf-8")
    if not BLOCK_RE.search(page):
        sys.exit(f"{PAGE.name} ne contient pas les marqueurs {START} … {END}.")

    paires = questions(page)
    if not paires:
        sys.exit("Aucune question trouvée : le balisage serait vide.")

    nouveau = BLOCK_RE.sub(lambda _: rendu(paires), page)
    if nouveau == page:
        print(f"Balisage à jour ({len(paires)} questions).")
        return 0
    if check:
        print(f"{PAGE.name} : balisage FAQPage à régénérer.")
        return 1
    PAGE.write_text(nouveau, encoding="utf-8")
    print(f"{PAGE.name} : balisage FAQPage régénéré ({len(paires)} questions).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
