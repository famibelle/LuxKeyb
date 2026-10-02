#!/usr/bin/env python3
"""Régénère docs/privacy/privacy-policy.html depuis PRIVACY_POLICY_FR.md.

    python docs/scripts/build_privacy_html.py

Les deux fichiers étaient tenus à la main en parallèle, ce qui garantit qu'ils
divergent au premier oubli ; pour une politique de confidentialité, c'est un
risque juridique. Le Markdown fait foi : ce script en refait le corps, et
garde du HTML existant tout ce qui n'est pas du texte (en-tête, style, bouton
de thème, pied de page, dont il met à jour la date).

Il reproduit les conventions du HTML d'origine : retour à la ligne pour les
fins de ligne à deux espaces, liste permise juste sous un paragraphe, adresses
web et courriel rendues cliquables.
"""

import re
from pathlib import Path

import markdown

DOSSIER = Path(__file__).resolve().parent.parent / "privacy"
MD = DOSSIER / "PRIVACY_POLICY_FR.md"
HTML = DOSSIER / "privacy-policy.html"


def preparer(md: str) -> str:
    """Une ligne vide avant toute liste collée à un paragraphe."""
    sortie, precedente = [], ""
    for ligne in md.split("\n"):
        est_liste = re.match(r"\s*([-*]|\d+\.)\s", ligne)
        prec_liste = re.match(r"\s*([-*]|\d+\.)\s", precedente)
        if est_liste and precedente.strip() and not prec_liste \
                and not precedente.startswith(("|", ">")) and not ligne.startswith(" "):
            sortie.append("")
        sortie.append(ligne)
        precedente = ligne
    return "\n".join(sortie)


def lier(html: str) -> str:
    """Rend cliquables les URL et adresses courriel hors des balises et liens."""
    morceaux = re.split(r"(<a\b.*?</a>|<[^>]+>|<code>.*?</code>)", html, flags=re.S)
    for i, m in enumerate(morceaux):
        if i % 2:
            continue
        m = re.sub(r"(https?://[^\s<)]+[^\s<).,;:])", r'<a href="\1">\1</a>', m)
        m = re.sub(r"(?<![\w/.@])([\w.+-]+@[\w-]+\.[\w.-]*\w)",
                   r'<a href="mailto:\1">\1</a>', m)
        morceaux[i] = m
    return "".join(morceaux)


def main():
    md = MD.read_text(encoding="utf-8")
    corps = markdown.markdown(preparer(md), extensions=["tables", "sane_lists"])
    corps = lier(corps).replace("<hr />", "<hr>").replace("<br />", "<br>")

    actuel = HTML.read_text(encoding="utf-8")
    debut = actuel.index("<h1>")
    fin = actuel.index('<div class="footer">')
    date = re.search(r"\*\*Dernière mise à jour :\*\*\s*([^\n]+?)\s*$", md, re.M).group(1)
    pied = re.sub(r"Dernière mise à jour : [^<]+", f"Dernière mise à jour : {date}",
                  actuel[fin:])
    HTML.write_text(actuel[:debut] + corps + "\n\n\n" + pied, encoding="utf-8")
    print(f"{HTML} régénéré ({len(corps)} caractères de corps)")


if __name__ == "__main__":
    main()
