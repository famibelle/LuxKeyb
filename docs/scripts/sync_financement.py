#!/usr/bin/env python3
"""Met à jour le compteur de la collecte (docs/stats/financement.json).

Le fichier mêle deux sortes de champs :

- ceux qu'on écrit à la main : `objectif`, `fin` (AAAA-MM-JJ ou null),
  `opencollective` (le slug du collectif, vide tant qu'il n'existe pas),
  `liens` (adresse d'un palier Open Collective par contrepartie) et
  `hors_plateforme` (dons reçus ailleurs, sur eticrowd ou par virement, et
  les dépenses payées avec cet argent) ;
- ceux que ce script calcule : `collecte`, `donateurs`, `soutiens`,
  `depenses`, `mis_a_jour`. Les modifier à la main ne sert à rien, ils sont
  réécrits au passage suivant.

Open Collective est la source de vérité quand le slug est renseigné : montant
reçu, nombre de contributeurs, noms des contributeurs publics, dépenses
payées. Son API publique se lit sans jeton. Sans slug, seuls les montants
`hors_plateforme` comptent.

Un collectif hébergé par Open Source Collective tient ses comptes en dollars.
Le total reçu est converti en euros par l'API elle-même ; chaque dépense l'est
au taux du jour où elle a été payée, et garde son montant d'origine
(`montant_origine`, `devise`) pour que le relevé reste vérifiable contre celui
d'Open Collective.

`mis_a_jour` ne change que si un chiffre a changé : sans quoi le workflow
quotidien ferait un commit par jour pour rien.

Usage : python docs/scripts/sync_financement.py
"""

import datetime
import json
import pathlib
import urllib.request

FICHIER = pathlib.Path(__file__).resolve().parent.parent / "stats" / "financement.json"
API = "https://api.opencollective.com/graphql/v2"

REQUETE_COLLECTIF = """
query ($slug: String) {
  account(slug: $slug, throwIfMissing: false) {
    currency
    stats {
      totalAmountReceived(currency: EUR) { value }
      contributorsCount
    }
    ... on Collective {
      contributors(limit: 1000, roles: [BACKER]) {
        nodes { account { name isIncognito } }
      }
    }
  }
}
"""

REQUETE_DEPENSES = """
query ($slug: String) {
  expenses(account: {slug: $slug}, status: [PAID], limit: 100) {
    nodes { description createdAt paidAt amountV2 { value currency } }
  }
}
"""

REQUETE_TAUX = """
query ($demandes: [CurrencyExchangeRateRequest!]!) {
  currencyExchangeRate(requests: $demandes) { value fromCurrency }
}
"""


def graphql(requete, variables):
    corps = json.dumps({"query": requete, "variables": variables}).encode()
    req = urllib.request.Request(
        API, data=corps,
        headers={"Content-Type": "application/json", "User-Agent": "LuxKeyb-financement"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        rep = json.load(r)
    if rep.get("errors"):
        raise RuntimeError("Open Collective : " + "; ".join(e.get("message", "?") for e in rep["errors"]))
    return rep["data"]


def interroger(slug):
    """Ce que dit Open Collective du collectif `slug`, ramené à nos champs."""
    compte = graphql(REQUETE_COLLECTIF, {"slug": slug})["account"]
    if compte is None:
        raise RuntimeError(f"aucun collectif « {slug} » sur Open Collective")

    soutiens = []
    for c in (compte.get("contributors") or {}).get("nodes") or []:
        a = c.get("account")
        if a and a.get("name") and not a.get("isIncognito") and a["name"] not in soutiens:
            soutiens.append(a["name"])

    depenses = [
        {
            "date": (d["paidAt"] or d["createdAt"])[:10],
            "objet": d["description"],
            "montant": d["amountV2"]["value"],
            "devise": d["amountV2"]["currency"],
        }
        for d in graphql(REQUETE_DEPENSES, {"slug": slug})["expenses"]["nodes"]
    ]
    en_euros(depenses)
    return {
        "collecte": compte["stats"]["totalAmountReceived"]["value"] or 0,
        "donateurs": compte["stats"]["contributorsCount"] or 0,
        "soutiens": soutiens,
        "depenses": depenses,
    }


def en_euros(depenses):
    """Convertit sur place les dépenses qui ne sont pas en euros."""
    autres = [d for d in depenses if d["devise"] != "EUR"]
    if autres:
        taux = graphql(REQUETE_TAUX, {"demandes": [
            {"fromCurrency": d["devise"], "toCurrency": "EUR", "date": d["date"] + "T12:00:00Z"}
            for d in autres
        ]})["currencyExchangeRate"]
        if len(taux) != len(autres) or any(t["fromCurrency"] != d["devise"] for t, d in zip(taux, autres)):
            raise RuntimeError("Open Collective : taux de change incomplets")
        for d, t in zip(autres, taux):
            d["montant_origine"] = round(d["montant"], 2)
            d["montant"] = d["montant"] * t["value"]
    for d in depenses:
        d["montant"] = round(d["montant"], 2)
        if d["devise"] == "EUR":
            del d["devise"]


def fusionner(f, oc, aujourd_hui):
    """Recalcule les champs dérivés de `f`. Rend True si un chiffre a changé."""
    hors = f.get("hors_plateforme") or {}
    oc = oc or {"collecte": 0, "donateurs": 0, "soutiens": [], "depenses": []}

    nouveau = {
        "collecte": round(oc["collecte"] + (hors.get("montant") or 0), 2),
        "donateurs": oc["donateurs"] + (hors.get("donateurs") or 0),
        "soutiens": oc["soutiens"],
        "depenses": sorted(
            oc["depenses"] + list(hors.get("depenses") or []),
            key=lambda d: d["date"], reverse=True,
        ),
    }
    change = any(f.get(k) != v for k, v in nouveau.items())
    f.update(nouveau)
    if change:
        f["mis_a_jour"] = aujourd_hui
    return change


def main():
    f = json.loads(FICHIER.read_text(encoding="utf-8"))
    slug = (f.get("opencollective") or "").strip()
    oc = interroger(slug) if slug else None
    if fusionner(f, oc, datetime.date.today().isoformat()):
        FICHIER.write_text(json.dumps(f, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Mis à jour : {f['collecte']} € de {f['donateurs']} donateurs.")
    else:
        print("Rien de nouveau.")


if __name__ == "__main__":
    main()
