#!/usr/bin/env python3
"""Les boîtes papier « Niveau A1 » et « Niveau A2 » (propriétaire, 2026-10-31) :
deux jeux de 54 cartes faits comme la boîte de départ (scripts/candidats_cartes.py),
mais tirés du vocabulaire d'un niveau (Dictionnaires/generate_niveaux.py).

Chaque boîte a deux paquets de 26 cartes, **Doheem** (à la maison : famille,
repas, corps, vêtements, la maison) contre **Dobaussen** (dehors : métiers,
transports, ville, école, loisirs, météo), et deux cartes de règle. Un paquet :
14 noms, 5 verbes, 4 adjectifs, 3 nombres, comme la boîte.

- Les noms viennent des pages du niveau : une page à thème va à son paquet
  ([PAGES]) ; un nom des pages « Noms » y va si son premier sens en français
  est dans les listes de [SENS] ; les autres restent dehors.
- Les noms des deux paquets sont **jumelés par longueur** (`jumeler` de la
  boîte) : chaque carte a sa jumelle de même Force dans l'autre paquet.
- Verbes et adjectifs : ceux du niveau, au coût de la boîte (7 verbes ◆◆ et
  3 ★★★, 1 adjectif ● et 7 ◆◆), les mieux illustrés d'abord (dessin, phrase
  traduite, sens le moins ambigu). Aucun rang de fréquence : le corpus RTL
  (CC BY-NC) ne décide d'aucune carte imprimée.
- Aucun mot déjà dans la boîte de départ ou l'extension : deux exemplaires
  d'un mot dans un paquet composé sont interdits par la règle.

La sélection est une proposition, à relire avant tout fichier d'impression.
Sorties, dans Dictionnaires/cartes/ : niveaux.md (à relire), niveaux.csv ; et
les ensembles NA1 et NA2 de assets/cartes_imprimees.json (numérotation de la
collection, codes de cartes en papier). À relancer après candidats_cartes.py.

À lancer depuis la racine du dépôt : python3 scripts/boites_niveaux.py
"""
import csv
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import candidats_cartes as CC  # noqa: E402

PAGES = {
    'Doheem': ('famille', 'fruits', 'repas'),
    'Dobaussen': ('metiers', 'personnes', 'transports', 'loisirs', 'ecole', 'lieux', 'meteo'),
}
# Les mois, les fêtes et les mesures n'y vont pas : sept mois dans un paquet
# le remplissaient sans rien raconter. La page santé ne donne que le corps,
# à Doheem : l'hôpital et l'accident ne font pas un jeu de famille.
CORPS = set("""tête œil yeux nez bouche oreille dent dents cou gorge épaule bras main doigt
    ventre dos jambe genou pied cheveu cheveux cœur peau visage langue lèvre""".split())

# Mots que la sélection ne prend pas : registre (« dronken », ivre ;
# « chillen », familier), sujet déplacé pour un jeu de famille, mot peu utile
# à ce niveau (« agebilt », vaniteux ; « Genre », « Poubelle », « Linguist »,
# « Kajak »), marque (« Bic ») ou doublon d'un mot pris (« Apdikter » à côté
# d'« Apdikt »). La carte imprime le sens de l'appli en entier : « Stull »
# (chaise, selles) et « lénks » (gauche, gay) y portent un sens malvenu.
# Les mots qui se glosent par eux-mêmes restent : « Bus » ou « Sport »
# s'écrivent comme en français, et c'est bon à savoir.
REFUSES = {'dronken', 'Béier', 'Aperitif', 'Wäin', 'Bier', 'Accident', 'Ambulanz', 'Bluttanalys',
           'Spidol', 'Kliniken', 'Klinick', 'chillen', 'agebilt', 'Apdikter', 'Genre', 'Poubelle', 'Bic',
           'Linguist', 'Kajak', 'Stull', 'lénks'}

# Le premier sens en français d'un nom des pages « Noms » qui le range.
SENS = {
    'Doheem': set("""maison appartement chambre cuisine salle salon lit table chaise armoire
        fenêtre porte escalier cave grenier jardin balcon douche baignoire lavabo toilettes
        tapis lampe canapé fauteuil étagère miroir rideau coussin couverture oreiller
        assiette verre tasse bol cuillère fourchette couteau casserole poêle frigo
        réfrigérateur four cuisinière lave-vaisselle machine aspirateur balai seau
        vêtement pantalon jupe robe chemise pull pullover manteau veste chaussure chaussette
        botte pyjama chapeau bonnet écharpe gant ceinture sac lunettes montre bague
        savon serviette brosse peigne dentifrice chat chien oiseau poisson clé jouet
        poupée ballon livre radio télévision téléphone ordinateur lettre cadeau bougie
        déjeuner dîner souper repas petit-déjeuner anniversaire""".split()),
    'Dobaussen': set("""ville village rue route place pont gare arrêt aéroport port église
        mairie commune poste banque magasin boutique marché supermarché boulangerie
        pharmacie hôpital clinique cabinet école lycée université bureau usine entreprise
        restaurant hôtel cinéma théâtre musée bibliothèque piscine parc forêt
        montagne rivière lac mer plage campagne champ voiture bus train tram vélo moto
        avion bateau taxi camion métro ticket billet carte plan feu parking autoroute
        frontière pays capitale nord sud est ouest voyage vacances excursion fête
        concert match sport football tennis vent pluie neige soleil nuage orage brouillard
        température degré saison printemps été automne hiver police pompier""".split()),
}

NOMBRES = {
    'A1': [('dräizéng', 13), ('véierzéng', 14), ('fofzéng', 15),
           ('siechzéng', 16), ('siwwenzéng', 17), ('uechtzéng', 18)],
    'A2': [('nonzéng', 19), ('zwanzeg', 20), ('drësseg', 30),
           ('véierzeg', 40), ('fofzeg', 50), ('honnert', 100)],
}

A = CC.A


def niveau(code):
    with open(os.path.join(A, f'luxemburgish_niveau_{code.lower()}.json'), encoding='utf-8') as f:
        return json.load(f)


def fiche(mot, gloses):
    """La carte d'un mot du niveau, comme `candidats_cartes.fiche` mais sans
    ses listes de thèmes, de verbes et d'adjectifs d'enfant ; le sens propre
    au niveau passe avant celui de la table."""
    c = CC.CAT.get(mot, '')
    gloss = gloses['fr'].get(mot) or CC.SENS['fr'].get(mot, '')
    if not gloss or not CC.re.fullmatch(r"[A-Za-zÀ-ÿë'-]+", mot) or not 2 <= len(mot) <= 14:
        return None
    if mot.isupper() or mot in CC.FAUX_AMIS:
        return None
    nature = {'SUBST': 'nom', 'VRB': 'verbe', 'ADJ': 'adjectif'}.get(c.split(' ')[0])
    if nature is None:
        return None
    sens1 = CC.premier_sens(gloss)
    if CC.A_EVITER.search(sens1.replace('-', ' ')):
        return None
    b = CC.BLASONS.get(mot, {})
    ph, tr = CC.exemple(mot)

    def sens(l):
        return gloses[l].get(mot) or CC.SENS[l].get(mot, '')
    return {
        'mot': mot, 'nature': nature, 'theme': '', 'sens': sens1.replace('-', ' '),
        'genre': CC.GENRES.get(c.split(' ')[1] if ' ' in c else '', ''),
        'force': len(mot), 'cout': CC.cout(mot), 'lieu': CC.LIEUX[b.get('c')],
        'dessin': b.get('m', ''), 'sens_de': sens('de'), 'sens_en': sens('en'), 'sens_pt': sens('pt'),
        'exemple': ph, 'traduction': tr, 'nb_sens': gloss.count(',') + 1,
    }


def choisir(fiches, couts):
    """Les mieux illustrés, au coût demandé : [(coût, combien), …]."""
    pris = []
    for cout, n in couts:
        pris += sorted([f for f in fiches if f['cout'] == cout and f not in pris], key=CC.preference)[:n]
    return pris


def boite(code, deja):
    d = niveau(code)
    gloses = d['gloses']
    viviers = {p: [] for p in PAGES}
    for page in d['themes']:
        base = page['id'].rsplit('_', 1)[0] if page['id'][-1].isdigit() else page['id']
        for mot in page['mots']:
            if mot in deja or mot in REFUSES:
                continue
            f = fiche(mot, gloses)
            if not f or f['nature'] != 'nom':
                continue
            # Le sens décide d'abord (« Fënster » est à la maison même dans la
            # page de l'école), la page ensuite.
            p = next((p for p in SENS if f['sens'] in SENS[p]), None)
            if p is None and base == 'sante' and f['sens'] in CORPS:
                p = 'Doheem'
            if p is None:
                p = next((p for p, pages in PAGES.items() if base in pages), None)
            if p:
                f['theme'] = p
                viviers[p].append(f)
    viviers = {p: CC.un_par_sens(v) for p, v in viviers.items()}
    mots = [m for t in d['themes'] for m in t['mots'] if m not in deja]
    fiches = [f for f in (fiche(m, gloses) for m in dict.fromkeys(mots)) if f and f['mot'] not in REFUSES]
    verbes = choisir([f for f in fiches if f['nature'] == 'verbe'], [(2, 7), (3, 3)])
    adjectifs = choisir([f for f in fiches if f['nature'] == 'adjectif'], [(1, 1), (2, 7)])
    if len(verbes) < 10 or len(adjectifs) < 8:
        sys.exit(f'{code} : {len(verbes)} verbes, {len(adjectifs)} adjectifs')
    vb = sorted(verbes, key=lambda f: f['cout'])
    ad = sorted(adjectifs, key=lambda f: f['cout'])
    na, nb = CC.jumeler(viviers['Doheem'], viviers['Dobaussen'], CC.LONGUEURS_BOITE)
    nombres = [CC.carte_nombre(m, v) for m, v in NOMBRES[code]]

    # Le partage des verbes et des adjectifs qui équilibre le mieux : les noms
    # sont jumelés, mais les verbes n'ont pas tous la même Force. On essaie
    # des partages à coût égal (mêmes nombres de ◆◆ et de ★★★ de chaque côté)
    # et on garde le plus proche de 50 %.
    rnd = CC.random.Random(11)
    meilleur = None
    for essai in range(40):
        if essai == 0:
            v1, a1 = vb[0::2], ad[0::2]
        else:
            # 7 ◆◆ et 3 ★★★ : 4 + 1 d'un côté et 3 + 2 de l'autre, ou l'inverse.
            v1 = []
            for cout, n in ((2, 4 - essai % 2), (3, 1 + essai % 2)):
                groupe = [f for f in vb if f['cout'] == cout]
                rnd.shuffle(groupe)
                v1 += groupe[:n]
            groupe = ad[1:]
            rnd.shuffle(groupe)
            a1 = [ad[0]] + groupe[:3]
        v2 = [f for f in vb if f not in v1]
        a2 = [f for f in ad if f not in a1]
        if len(v1) != 5 or len(a1) != 4:
            continue
        v1, v2, a1, a2 = (sorted(x, key=lambda f: f['cout']) for x in (v1, v2, a1, a2))
        paquets = {'Doheem': na + v1 + a1 + nombres[:3], 'Dobaussen': nb + v2 + a2 + nombres[3:]}
        taux = victoires(paquets, 1000)
        if meilleur is None or abs(taux - 0.5) < abs(meilleur[0] - 0.5):
            meilleur = (taux, paquets)
    return meilleur[1], {p: len(v) for p, v in viviers.items()}


def victoires(paquets, parties):
    texte = CC.simuler(paquets, 'Doheem', 'Dobaussen', parties)
    return int(texte.split(' gagne ')[1].split('%')[0]) / 100


def main():
    with open(os.path.join(CC.SORTIE, 'selection.csv'), encoding='utf-8') as f:
        deja = {r['mot'] for r in csv.DictReader(f)}
    boites, viviers, equilibre = {}, {}, {}
    for code in ('A1', 'A2'):
        boites[code], viviers[code] = boite(code, deja)
        deja |= {c['mot'] for p in boites[code].values() for c in p}
        equilibre[code] = CC.simuler(boites[code], 'Doheem', 'Dobaussen')

    champs = ['ensemble', 'mot', 'nature', 'genre', 'force', 'cout', 'sens', 'sens_de', 'sens_en',
              'sens_pt', 'lieu', 'dessin', 'exemple', 'traduction']
    with open(os.path.join(CC.SORTIE, 'niveaux.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, champs, extrasaction='ignore')
        w.writeheader()
        for code, paquets in boites.items():
            for nom, cartes in paquets.items():
                for c in cartes:
                    w.writerow({'ensemble': f'{code} {nom}', **c})

    # Les ensembles des codes de cartes en papier et de la numérotation
    # (ImpressionCarte.COLLECTION : NA1 après E1, puis NA2), ajoutés à ceux
    # de candidats_cartes.py sans toucher aux autres. Les paquets portent le
    # niveau : le médaillon dit « Doheem A1 », et une carte se range dans la
    # bonne boîte après une partie.
    chemin = os.path.join(A, 'cartes_imprimees.json')
    with open(chemin, encoding='utf-8') as f:
        actifs = json.load(f)['ensembles']
    for code, paquets in boites.items():
        actifs[f'N{code}'] = {
            'nom': f'Niveau {code} (Doheem, Dobaussen)',
            'cartes': [CC.element(c) for nom in ('Doheem', 'Dobaussen') for c in paquets[nom]],
            'paquets': [[f'{nom} {code}', len(paquets[nom])] for nom in ('Doheem', 'Dobaussen')],
        }
    with open(chemin, 'w', encoding='utf-8') as f:
        json.dump({'ensembles': actifs}, f, ensure_ascii=False, indent=1)
        f.write('\n')

    l = ['# Cartes imprimées : les boîtes Niveau A1 et Niveau A2', '',
         'Proposition produite par `scripts/boites_niveaux.py` à partir du vocabulaire des niveaux',
         '(`Dictionnaires/generate_niveaux.py`, LOD CC0). **À relire avant impression.**', '']
    for code, paquets in boites.items():
        l += [f'# Niveau {code} : 2 paquets de 26 (52 cartes) + 2 cartes de règle', '',
              f"Vivier : {viviers[code]['Doheem']} noms pour Doheem, {viviers[code]['Dobaussen']} pour Dobaussen.",
              '', f'**Équilibre** (simulation) : {equilibre[code]}.', '']
        for nom, cartes in paquets.items():
            l += [f'## Paquet {nom} ({len(cartes)} cartes)', '',
                  "| Mot | Nature | Coût | Force | Sens | Dessin | Phrase d'exemple |", '|---|---|---|---|---|---|---|']
            for c in cartes:
                nat = c['nature'] + (f" {c['genre']}" if c['genre'] else '')
                ex = (f"*{c['exemple']}*" + (f" — {c['traduction']}" if c['traduction'] else '')) if c['exemple'] else ''
                l.append(f"| **{c['mot']}** | {nat} | {CC.SYMBOLES.get(c['cout'], 'gratuit')} | {c['force'] or '—'} "
                         f"| {c['sens']} | {c['dessin'] or '—'} | {ex} |")
            l.append('')
    with open(os.path.join(CC.SORTIE, 'niveaux.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(l) + '\n')
    for code in boites:
        print(code, equilibre[code])
        for nom, cartes in boites[code].items():
            print(' ', nom, len(cartes), ' '.join(c['mot'] for c in cartes))


if __name__ == '__main__':
    main()
