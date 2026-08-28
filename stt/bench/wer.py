#!/usr/bin/env python3
"""Taux d'erreur mot (WER) et caractère (CER), avec la normalisation qui va avec.

Aucune dépendance : `jiwer` ferait la même programmation dynamique en trente
lignes de plus dans l'environnement.

La normalisation est volontairement conservatrice. Elle ne développe **rien** :
`z.B.`, `d.h.`, `resp.` restent tels quels et comptent comme erreurs si le
modèle écrit « zum Beispill ». C'est pessimiste et c'est assumé — développer les
abréviations demanderait un lexique luxembourgeois de plus, et l'écart mesuré
serait alors le nôtre, pas celui du modèle.
"""

import re
import unicodedata

# Apostrophes typographiques → ASCII. En luxembourgeois l'apostrophe est
# grammaticale (d'Kanner, d'Land) : la retirer fusionnerait deux mots.
_APOS = {"’": "'", "‘": "'", "´": "'", "ʼ": "'"}
_QUOTES = "„“”«»\"()[]{}"
_EDGE = ".,;:!?……-–—*/\\"


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFC", text or "")
    for k, v in _APOS.items():
        text = text.replace(k, v)
    for c in _QUOTES:
        text = text.replace(c, " ")
    text = text.lower()
    out = []
    for tok in text.split():
        tok = tok.strip(_EDGE)
        # Apostrophe ou trait d'union en bord de jeton : décoratif, pas
        # grammatical. À l'intérieur du mot, on garde.
        tok = tok.strip("'-")
        if tok:
            out.append(tok)
    return " ".join(out)


def _distance(a, b):
    """Levenshtein classique, avec le détail des trois types d'erreur."""
    n, m = len(a), len(b)
    if n == 0:
        return m, 0, m, 0
    prev = [(j, 0, j, 0) for j in range(m + 1)]
    for i in range(1, n + 1):
        cur = [(i, 0, 0, i)] + [None] * m
        for j in range(1, m + 1):
            if a[i - 1] == b[j - 1]:
                cur[j] = prev[j - 1]
            else:
                sub = prev[j - 1]
                ins = cur[j - 1]
                dele = prev[j]
                best = min(sub[0], ins[0], dele[0])
                if best == sub[0]:
                    cur[j] = (sub[0] + 1, sub[1] + 1, sub[2], sub[3])
                elif best == ins[0]:
                    cur[j] = (ins[0] + 1, ins[1], ins[2] + 1, ins[3])
                else:
                    cur[j] = (dele[0] + 1, dele[1], dele[2], dele[3] + 1)
        prev = cur
    return prev[m]


def wer(ref: str, hyp: str, trim_edges=False):
    """Retourne (taux, N, subs, ins, dels).

    `trim_edges` retire le premier et le dernier mot de la référence *et* de
    l'hypothèse : le corpus est un transcript roulant coupé toutes les 60 s, ces
    deux mots-là sont tronqués par construction et pénalisent tout le monde.
    """
    r = normalize(ref).split()
    h = normalize(hyp).split()
    if trim_edges:
        r, h = r[1:-1], h[1:-1]
    if not r:
        return (0.0, 0, 0, 0, 0) if not h else (1.0, 0, 0, len(h), 0)
    d, s, i, dl = _distance(r, h)
    return d / len(r), len(r), s, i, dl


def cer(ref: str, hyp: str, trim_edges=False):
    r = normalize(ref)
    h = normalize(hyp)
    if trim_edges:
        r = " ".join(r.split()[1:-1])
        h = " ".join(h.split()[1:-1])
    if not r:
        return 0.0 if not h else 1.0
    d, *_ = _distance(list(r), list(h))
    return d / len(r)


def repetition_ratio(text: str, n=4):
    """Part des n-grammes répétés — signature des boucles de décodage.

    Whisper, privé de son repli en température, se bloque parfois sur une
    séquence qu'il réémet indéfiniment (« Ech soen net. » quarante fois). Le WER
    seul ne distingue pas cette panne d'une transcription simplement médiocre,
    alors qu'elles n'appellent pas du tout le même correctif.
    """
    w = normalize(text).split()
    if len(w) < n * 2:
        return 0.0
    grams = [tuple(w[i:i + n]) for i in range(len(w) - n + 1)]
    return 1.0 - len(set(grams)) / len(grams)


# --- Conventions d'écriture, qui ne sont pas des erreurs de reconnaissance ----
#
# La référence humaine écrit « z. B. », « 2 », « 80 Joer » ; le modèle écrit ce
# qui est prononcé : « zum Beispill », « zwou », « achtzeg ». L'alignement compte
# ces couples comme des substitutions alors que la reconnaissance est juste.
# Mesurer le WER sans eux ne blanchit pas le modèle — il reste tout ce qui suit —
# mais sépare ce qu'un correcteur pourrait normaliser de ce qu'il ne pourrait pas.

_ABBR = {
    "z.b.": "zum beispill", "d.h.": "dat heescht", "resp.": "respektiv",
    "etc.": "et cetera", "z.b": "zum beispill", "d.h": "dat heescht",
    "nr.": "nummer", "art.": "artikel", "bzw.": "bezéiungsweis",
}


def expand(text: str) -> str:
    """Développe les abréviations et retire les jetons chiffrés des deux côtés."""
    toks = []
    for t in normalize(text).split():
        t = _ABBR.get(t, t)
        if any(c.isdigit() for c in t):
            continue          # « 1,2 % », « 15. » : retirés des deux côtés
        toks.append(t)
    return " ".join(toks)


def wer_loose(ref: str, hyp: str):
    """WER après développement des abréviations et retrait des chiffres."""
    r, h = expand(ref).split(), expand(hyp).split()
    if not r:
        return 0.0 if not h else 1.0, 0
    d, *_ = _distance(r, h)
    return d / len(r), len(r)
