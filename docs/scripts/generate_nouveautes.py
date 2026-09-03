#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Alimente docs/nouveautes.html depuis android_keyboard/CHANGELOG.md.

La page créole équivalente compare la version du Play Store à celle de GitHub,
pour lister ce qui est déjà livré mais pas encore validé par Google. Ici cette
comparaison n'a pas d'objet : le Lëtzebuergesch Clavier n'est pas publié sur le
Play Store, et sa seule voie de distribution est GitHub Releases. La page
raconte donc simplement ce qui est arrivé dans les dernières versions, et le
CHANGELOG en est la source de vérité — écrire ces textes à la main dans le HTML
les aurait condamnés à diverger du journal des versions dès la release suivante.

    python docs/scripts/generate_nouveautes.py

Écrit docs/stats/nouveautes.json.
"""

import html
import json
import re
import sys
from datetime import date
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
CHANGELOG = RACINE / "android_keyboard" / "CHANGELOG.md"
SORTIE = RACINE / "docs" / "stats" / "nouveautes.json"

VERSIONS_AFFICHEES = 6

EN_TETE_VERSION = re.compile(r'^## \[([0-9]+\.[0-9]+\.[0-9]+)\](?: — .*?)? - (\d{4}-\d{2}-\d{2})\s*$')
EN_TETE_SECTION = re.compile(r'^### (.+)$')
PUCE = re.compile(r'^- (.*)$')
EMOJI_INITIAL = re.compile(
    r'^([\U0001F000-\U0001FAFF←-⇿☀-➿️‍]+)\s*'
)


def en_html(markdown):
    """Convertit le sous-ensemble de Markdown employé par le CHANGELOG.

    L'échappement passe en premier : le texte vient d'un fichier du dépôt, mais
    il contient des chevrons et des esperluettes qui casseraient le rendu.
    """
    texte = html.escape(markdown, quote=False)
    texte = re.sub(r'`([^`]+)`', r'<code>\1</code>', texte)
    texte = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', texte)
    texte = re.sub(r'(?<![\w*])\*([^*\n]+)\*(?![\w*])', r'<em>\1</em>', texte)
    texte = re.sub(r'\[([^\]]+)\]\((https?://[^)]+)\)',
                   r'<a href="\2" target="_blank" rel="noopener">\1</a>', texte)
    return texte


def separer_puce(brut):
    """Sépare « **Titre.** suite » en titre et corps.

    Le CHANGELOG ouvre presque toutes ses puces par un segment en gras qui
    résume le point ; c'est lui qui sert de titre à la carte. Une puce sans
    gras initial n'a pas de titre, et son texte est rendu tel quel.

    Le gras n'est pas toujours une phrase entière : « **L'appui long copie le
    mot**, sans passer par la fiche » laisse un corps qui commence par une
    virgule, et la carte affichait « , sans passer par la fiche ». La
    ponctuation de liaison est donc retirée et la phrase remise sur ses pieds —
    seulement quand elle commence par une lettre, pour ne pas capitaliser un
    guillemet ou un nom de code.
    """
    m = re.match(r'^\*\*(.+?)\*\*[  ]*(.*)$', brut, re.S)
    if not m:
        return None, en_html(brut.strip())
    titre = m.group(1).strip().rstrip(':').rstrip('.')
    corps = m.group(2).strip().lstrip(',;:').strip()
    if corps[:1].isalpha():
        corps = corps[0].upper() + corps[1:]
    return en_html(titre), en_html(corps)


def lire_versions(lignes):
    versions = []
    courante = section = None

    def clore_puce(acc):
        if not acc or not section:
            return
        titre, texte = separer_puce(" ".join(acc).strip())
        if titre or texte:
            section["points"].append({"titre": titre, "texte": texte})

    acc = []
    intro = []
    for ligne in lignes:
        m = EN_TETE_VERSION.match(ligne)
        if m:
            clore_puce(acc); acc = []
            if courante:
                courante["intro"] = en_html(" ".join(intro).strip())
            intro = []
            courante = {"version": m.group(1), "date": m.group(2),
                        "intro": "", "sections": []}
            section = None
            versions.append(courante)
            continue
        if courante is None:
            continue

        m = EN_TETE_SECTION.match(ligne)
        if m:
            clore_puce(acc); acc = []
            titre = m.group(1).strip()
            emoji = ""
            me = EMOJI_INITIAL.match(titre)
            if me:
                emoji = me.group(1)
                titre = titre[me.end():].strip()
            section = {"emoji": emoji, "titre": en_html(titre),
                       "points": [], "texte": [], "tableau": None}
            courante["sections"].append(section)
            continue

        m = PUCE.match(ligne)
        if m:
            clore_puce(acc)
            acc = [m.group(1)]
            continue

        # Continuation d'une puce : le CHANGELOG enroule ses paragraphes.
        if acc and ligne.startswith(("  ", "\t")) and ligne.strip():
            acc.append(ligne.strip())
            continue

        clore_puce(acc); acc = []
        nu = ligne.strip()
        if not nu:
            continue

        # Un tableau Markdown : le CHANGELOG s'en sert pour les avant/après
        # chiffrés, qui sont précisément ce qu'un lecteur vient voir.
        if nu.startswith("|") and section is not None:
            cellules = [c.strip() for c in nu.strip("|").split("|")]
            if all(set(c) <= set("-: ") for c in cellules):
                continue  # ligne de séparation
            if section["tableau"] is None:
                section["tableau"] = {"entetes": [en_html(c) for c in cellules],
                                      "lignes": []}
            else:
                section["tableau"]["lignes"].append([en_html(c) for c in cellules])
            continue

        if nu.startswith(("```", ">", "|")):
            continue

        # Paragraphe libre : avant le premier ### il appartient à l'intro de la
        # version, après il appartient à la section ouverte.
        if section is None:
            intro.append(nu)
        else:
            section["texte"].append(nu)

    clore_puce(acc)
    if courante:
        courante["intro"] = en_html(" ".join(intro).strip())
    return versions


def main():
    if not CHANGELOG.exists():
        sys.exit(f"CHANGELOG introuvable : {CHANGELOG}")

    versions = lire_versions(CHANGELOG.read_text("utf-8").splitlines())
    for v in versions:
        for s in v["sections"]:
            s["texte"] = en_html(" ".join(s["texte"]).strip())
        # Une section sans puce, sans texte et sans tableau n'a rien à afficher.
        v["sections"] = [s for s in v["sections"]
                         if s["points"] or s["texte"] or s["tableau"]]
    versions = [v for v in versions if v["sections"]][:VERSIONS_AFFICHEES]
    if not versions:
        sys.exit("Aucune version exploitable dans le CHANGELOG.")

    resultat = {
        "genere_le": date.today().isoformat(),
        "derniere_version": versions[0]["version"],
        "derniere_date": versions[0]["date"],
        "depot": "https://github.com/famibelle/LuxKeyb",
        "versions": versions,
    }
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(json.dumps(resultat, ensure_ascii=False, indent=1), "utf-8")

    points = sum(len(s["points"]) for v in versions for s in v["sections"])
    print(f"Écrit {SORTIE} — {len(versions)} versions, {points} points",
          file=sys.stderr)


if __name__ == "__main__":
    main()
