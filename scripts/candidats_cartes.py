#!/usr/bin/env python3
"""Tire du LOD les mots des cartes imprimées : la boîte de départ et les
extensions à thème du Wuertduell (docs/regle-du-jeu.md).

Tout vient de données CC0 : catégories, sens (FR, DE, EN, PT), genres et
phrases d'exemple du LOD, traductions des phrases par le ZLS, et le classement
des champs et des dessins du carnet (notre travail). **Rien** ne vient du
corpus RTL (CC BY-NC) : ni phrase de Wuertlück, ni rang de fréquence. Le coût
d'une carte suit la longueur du mot, pas sa fréquence.

Le LOD ne marque ni le registre d'un mot (familier, grossier) ni son thème :

- les mots déplacés pour un enfant sont écartés par une liste de sens à éviter
  ([A_EVITER]), à compléter à la relecture ;
- le thème se lit sur le **premier sens** en français, comparé à des listes
  écrites à la main ([THEMES]).

La sélection est une proposition : elle se relit, idéalement par un
enseignant, avant tout fichier d'impression.

Sorties, dans Dictionnaires/cartes/ :

- selection.md : la boîte (deux paquets de 26) et deux extensions, à relire ;
- selection.csv : les mêmes cartes, pour produire les fichiers ;
- candidats.csv : tous les mots à thème, pour remplacer une carte refusée.

Et, dans les assets de l'appli, cartes_imprimees.json : les ensembles qu'un
code de carte en papier peut nommer (`@B1`, voir carnet/CartesPapier.kt).
Un ensemble déjà imprimé ne change plus : une nouvelle boîte prend un nouvel
identifiant.

À lancer depuis la racine du dépôt : python3 scripts/candidats_cartes.py
"""
import csv
import json
import os
import random
import re
import sys

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
A = os.path.join(RACINE, 'android_keyboard/app/src/main/assets/')
SORTIE = os.path.join(RACINE, 'Dictionnaires/cartes')


def charger(nom):
    with open(os.path.join(A, nom), encoding='utf-8') as f:
        return json.load(f)


CAT = charger('luxemburgish_categories.json')['categories']
SENS = {'fr': charger('luxemburgish_translations.json')['translations']}
for l in ('de', 'en', 'pt'):
    SENS[l] = charger(f'luxemburgish_translations_{l}.json')['translations']
EX = charger('luxemburgish_exemples.json')
BLASONS = charger('luxemburgish_blasons.json')['blasons']

# Les lieux du carnet, dans l'ordre de `Lieu` (Paysages.kt), par champ.
LIEUX = {'mouvement': 'Rout Bréck', 'temps': 'Veianen', 'territoire': 'Esch-Sauer',
         'economie': 'Musel', 'savoir': 'Neimënster', 'public': 'Gëlle Fra',
         'societe': 'Schueberfouer', 'vie': 'Mëllerdall', None: 'Stad'}
ORDRE_LIEUX = list(LIEUX.values())

THEMES = {
    'Déieren': """chat chien cheval vache cochon porcelet mouton chèvre poule coq
        canard oie lapin souris rat oiseau poisson abeille papillon fourmi araignée
        escargot grenouille serpent hérisson écureuil renard loup ours cerf chevreuil
        sanglier hibou chouette corbeau pie moineau cigogne hirondelle lion tigre
        éléphant girafe singe zèbre crocodile baleine dauphin requin tortue mouche
        moustique coccinelle âne taureau veau poussin pigeon aigle chauve-souris taupe
        castor phoque pingouin kangourou perroquet cygne truite crabe chenille guêpe
        chaton chiot agneau""",
    'Iessen': """pain beurre fromage lait œuf pomme poire cerise fraise prune raisin
        banane orange citron carotte tomate salade chou oignon haricot soupe viande
        saucisse jambon poulet riz pâtes nouilles gâteau tarte biscuit chocolat bonbon
        miel confiture sucre sel poivre jus thé café farine crêpe gaufre frites noix
        noisette concombre épinard croissant yaourt crème petit-pain myrtille
        framboise abricot pêche ananas citrouille radis petits-pois""",
    'Schoul': """école livre cahier crayon stylo gomme règle cartable tableau craie
        professeur instituteur institutrice élève classe leçon devoir lettre page
        papier ciseaux colle récréation pupitre chaise table globe ordinateur calcul
        dictée examen bibliothèque pinceau dessin chanson musique alphabet trousse
        taille-crayon calculatrice horloge""",
    'Natur': """arbre fleur herbe feuille forêt montagne colline rivière ruisseau lac
        mer plage pierre rocher sable ciel nuage soleil lune étoile pluie neige vent
        orage arc-en-ciel brouillard champ prairie jardin rose tulipe sapin chêne hêtre
        bouleau buisson mousse champignon terre feu île grotte cascade source vallée
        printemps été automne hiver arc-en-ciel éclair tonnerre""",
}
THEMES = {t: set(v.split()) for t, v in THEMES.items()}

# Les verbes et adjectifs, choisis à la main, les plus usuels d'abord : c'est
# l'ordre de choix. Les tirer du LOD par le sens donnait des pièges qu'aucun
# filtre ne voit : « klauen » pour voler (dérober), « saufen » pour boire,
# « nospillen » (rejouer) pour jouer. Le sens imprimé reste celui du LOD, et le
# script s'arrête si un mot n'y est pas.
VERBES = """iessen drénken schlofen lafen sprangen schwammen fléien spillen sangen
    danzen liesen schreiwen molen laachen kräischen sichen fannen klammen falen
    kachen""".split()
ADJECTIFS = """grouss kleng staark séier schéin léif witzeg daper rout blo gréng
    giel schwaarz wäiss waarm kal al jonk frou midd""".split()

# Sens à écarter, en mots entiers. La carte n'imprime que le **premier** sens :
# c'est lui qu'on filtre, avec la phrase d'exemple. Filtrer tout l'article
# écartait les mots de base, qui ont le plus de sens : « Kou » (vache, conne),
# « Schwäin » (cochon, malotru).
A_EVITER = re.compile(r"\b(" + "|".join("""merde con conne cul pute putain salaud salope
    sexe sexuel sexuelle baiser bite couille couilles chier pisser nichon nichons
    connard bordel cocu prostituée prostitué ivre ivrogne drogue alcool cadavre
    tuer meurtre meurtrier fusil viol violer suicide nazi enculé pénis vagin sein
    seins pédé nègre gouine foutre emmerder crétin idiot imbécile débile abruti
    garce voyou bâtard""".split()) + r")\b", re.IGNORECASE)


def cout(mot):
    n = len(mot)
    return 1 if n <= 4 else 2 if n <= 7 else 3 if n <= 10 else 4


SYMBOLES = {1: '●', 2: '◆◆', 3: '★★★', 4: '✦✦✦✦'}


def premier_sens(gloss):
    return re.sub(r'\([^)]*\)', '', gloss.split(',')[0]).strip().lower().replace(' ', '-')


def exemple(mot):
    """La phrase la plus courte qui a sa traduction ; sinon la plus courte."""
    phrases = EX['exemples'].get(mot, [])
    trads = EX.get('traductions', {}).get(mot, [])
    paires = [(p, trads[i] if i < len(trads) else '') for i, p in enumerate(phrases)]
    paires = [(p, t) for p, t in paires if len(p) <= 70 and not A_EVITER.search(p + ' ' + t)]
    if not paires:
        return '', ''
    paires.sort(key=lambda pt: (pt[1] == '', len(pt[0])))
    return paires[0]


# Le genre écrit sur la pastille. Le LOD en donne parfois deux : « Krokodil »
# est masculin ou neutre, « Hammbier » féminin ou neutre. La carte les écrit
# tous les deux, et le nom compte dans les deux familles (règle du jeu).
GENRES = {'M': 'm.', 'F': 'f.', 'N': 'n.', 'MN': 'm./n.', 'FN': 'f./n.', 'MF': 'm./f.'}

# Le sens imprimé, quand le premier sens du LOD ne se lit pas seul : « jouer
# à » attend un complément que la carte n'a pas.
SENS_IMPRIME = {'spillen': 'jouer'}


def fiche(mot):
    c = CAT.get(mot, '')
    gloss = SENS['fr'].get(mot, '')
    if not gloss or not re.fullmatch(r"[A-Za-zÀ-ÿë'-]+", mot) or len(mot) < 2 or len(mot) > 14:
        return None
    if mot.isupper():  # sigles : « GBP » est une livre, pas un livre
        return None
    nature = {'SUBST': 'nom', 'VRB': 'verbe', 'ADJ': 'adjectif'}.get(c.split(' ')[0])
    if nature is None:
        return None
    sens1 = premier_sens(gloss)
    if A_EVITER.search(sens1.replace('-', ' ')):
        return None
    theme = next((t for t, mots in THEMES.items() if sens1 in mots), '') if nature == 'nom' else ''
    if nature == 'verbe' and mot not in VERBES:
        return None
    if nature == 'adjectif' and mot not in ADJECTIFS:
        return None
    if nature == 'nom' and not theme:
        return None
    b = BLASONS.get(mot, {})
    ph, tr = exemple(mot)
    return {
        'mot': mot, 'nature': nature, 'theme': theme, 'sens': SENS_IMPRIME.get(mot, sens1.replace('-', ' ')),
        'genre': GENRES.get(c.split(' ')[1] if ' ' in c else '', ''),
        'force': len(mot), 'cout': cout(mot), 'lieu': LIEUX[b.get('c')],
        'dessin': b.get('m', ''), 'sens_de': SENS['de'].get(mot, ''), 'sens_en': SENS['en'].get(mot, ''),
        'sens_pt': SENS['pt'].get(mot, ''), 'exemple': ph, 'traduction': tr,
        'nb_sens': gloss.count(',') + 1,
    }


def preference(f):
    """Dessinée d'abord, puis une phrase traduite, puis le sens le moins ambigu."""
    return (f['dessin'] == '', f['traduction'] == '', f['exemple'] == '', f['nb_sens'], f['mot'])


def un_par_sens(fiches):
    """Plusieurs mots pour un même sens : on garde le plus court, qui est le
    mot de base (« Blumm » et non « Blummemotiv », « sprangen » et non
    « iwwersprangen »), puis le préféré."""
    garde = {}
    for f in sorted(fiches, key=lambda f: (len(f['mot']),) + preference(f)):
        garde.setdefault(f['sens'], f)
    return list(garde.values())


# Une courbe de coût par paquet de 14 noms : ce qui fait des parties à 7 tours.
COURBE_NOMS = {1: 4, 2: 6, 3: 3, 4: 1}


def choisir_noms(fiches, courbe=COURBE_NOMS):
    choix = []
    par_cout = {c: sorted([f for f in fiches if f['cout'] == c], key=preference) for c in courbe}
    manque = 0
    for c, n in courbe.items():
        pris = par_cout[c][:n]
        choix += pris
        manque += n - len(pris)
    reste = sorted([f for f in fiches if f not in choix], key=preference)
    return choix + reste[:manque]


# Les longueurs des 14 noms d'un paquet de la boîte : la courbe de coût
# (4 ●, 6 ◆◆, 3 ★★★, 1 ✦✦✦✦) qui fait des parties à 6 ou 7 tours.
LONGUEURS_BOITE = [3, 4, 4, 4, 5, 5, 5, 6, 6, 7, 8, 8, 9, 11]


def jumeler(vivier_a, vivier_b, longueurs):
    """Les noms des deux paquets de la boîte, **par paires de même longueur**.

    La Force est la longueur du mot, et le combat la compare lettre à lettre :
    un paquet dont les mots ont une lettre de moins à coût égal perdait 75 %
    des parties. Échanger des cartes après coup pour rapprocher les totaux
    allait chercher des mots rares (« Spatzen », « Kürbis ») à la place de
    « Fësch » ou « Mëllech ». Ici chaque carte d'un paquet a sa jumelle de même
    Force dans l'autre : la boîte est équilibrée par construction.

    Une longueur que l'un des thèmes n'a pas est remplacée par la plus proche
    qui existe dans les deux, sans changer de coût si possible.
    """
    pris_a, pris_b = [], []
    for n in longueurs:
        essais = sorted({len(f['mot']) for f in vivier_a} & {len(f['mot']) for f in vivier_b},
                        key=lambda m: (cout('x' * m) != cout('x' * n), abs(m - n)))
        for m in essais:
            ca = [f for f in vivier_a if len(f['mot']) == m and f not in pris_a]
            cb = [f for f in vivier_b if len(f['mot']) == m and f not in pris_b]
            if ca and cb:
                pris_a.append(min(ca, key=preference))
                pris_b.append(min(cb, key=preference))
                break
    return pris_a, pris_b


NOMBRES = ['eent', 'zwee', 'dräi', 'véier', 'fënnef', 'sechs']


def carte_nombre(mot, valeur):
    return {'mot': mot, 'nature': 'nombre', 'theme': '', 'sens': str(valeur), 'genre': '',
            'force': 0, 'cout': 0, 'lieu': '', 'dessin': '', 'sens_de': str(valeur),
            'sens_en': str(valeur), 'sens_pt': str(valeur), 'exemple': '', 'traduction': '', 'nb_sens': 1}


def main():
    toutes = [f for f in (fiche(m) for m in CAT) if f]
    noms = {t: un_par_sens([f for f in toutes if f['theme'] == t]) for t in THEMES}
    manquants = [m for m in VERBES + ADJECTIFS if not any(f['mot'] == m for f in toutes)]
    if manquants:
        sys.exit(f"absents du LOD ou écartés : {', '.join(manquants)}")
    verbes = sorted([f for f in toutes if f['nature'] == 'verbe'], key=lambda f: VERBES.index(f['mot']))
    adjectifs = sorted([f for f in toutes if f['nature'] == 'adjectif'], key=lambda f: ADJECTIFS.index(f['mot']))

    # Les verbes et adjectifs se partagent entre les deux paquets, sans doublon,
    # en alternant pour que chacun ait des coûts variés. Quatre adjectifs par
    # paquet : 14 + 5 + 4 + 3 = 26, et les deux paquets font les 52 cartes d'un
    # jeu classique (propriétaire, 2026-10-26).
    vb = sorted(verbes[:10], key=lambda f: f['cout'])
    ad = sorted(adjectifs[:8], key=lambda f: f['cout'])
    na, nb = jumeler(noms['Déieren'], noms['Iessen'], LONGUEURS_BOITE)
    paquets = {
        'Déieren': na + vb[0::2] + ad[0::2]
        + [carte_nombre(m, i + 1) for i, m in enumerate(NOMBRES[:3])],
        'Iessen': nb + vb[1::2] + ad[1::2]
        + [carte_nombre(m, i + 4) for i, m in enumerate(NOMBRES[3:])],
    }
    extensions = {
        'Schoul': choisir_noms(noms['Schoul'], {1: 4, 2: 8, 3: 5, 4: 3}) + verbes[10:14],
        'Natur': choisir_noms(noms['Natur'], {1: 4, 2: 8, 3: 5, 4: 3}) + adjectifs[8:12],
    }

    os.makedirs(SORTIE, exist_ok=True)
    champs = ['ensemble', 'mot', 'nature', 'genre', 'force', 'cout', 'sens', 'sens_de', 'sens_en',
              'sens_pt', 'lieu', 'dessin', 'exemple', 'traduction']
    with open(os.path.join(SORTIE, 'selection.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, champs, extrasaction='ignore')
        w.writeheader()
        for nom, cartes in list(paquets.items()) + list(extensions.items()):
            for c in cartes:
                w.writerow({'ensemble': nom, **c})
    with open(os.path.join(SORTIE, 'candidats.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, ['theme'] + champs[1:], extrasaction='ignore')
        w.writeheader()
        for c in sorted(toutes, key=lambda c: (c['theme'] or c['nature'], c['cout'], c['mot'])):
            w.writerow(c)

    # Les ensembles des codes de cartes en papier. B1 : la boîte de départ,
    # première édition.
    def element(c):
        return f"{c['mot']}#{c['sens']}" if c['nature'] == 'nombre' else c['mot']
    # « paquets » découpe chaque ensemble, dans l'ordre de ses cartes : le
    # médaillon de la carte imprimée porte l'emblème de son paquet, qui dit à
    # quel paquet la rendre après une partie (ImpressionCarte.paquet).
    actifs = {
        'B1': {'nom': 'Boîte de départ (Déieren, Iessen)',
               'cartes': [element(c) for nom in ('Déieren', 'Iessen') for c in paquets[nom]],
               'paquets': [[nom, len(paquets[nom])] for nom in ('Déieren', 'Iessen')]},
        'SCHOUL1': {'nom': 'Extension Schoul', 'cartes': [element(c) for c in extensions['Schoul']],
                    'paquets': [['Schoul', len(extensions['Schoul'])]]},
        'NATUR1': {'nom': 'Extension Natur', 'cartes': [element(c) for c in extensions['Natur']],
                   'paquets': [['Natur', len(extensions['Natur'])]]},
        'FK27': {'nom': 'Fierkelsmaart 2027', 'cartes': ['Fierkelsmaart'], 'paquets': [['Fierkelsmaart', 1]]},
    }
    with open(os.path.join(A, 'cartes_imprimees.json'), 'w', encoding='utf-8') as f:
        json.dump({'ensembles': actifs}, f, ensure_ascii=False, indent=1)
        f.write('\n')

    equilibre = simuler(paquets)
    with open(os.path.join(SORTIE, 'selection.md'), 'w', encoding='utf-8') as f:
        f.write(rapport(paquets, extensions, toutes, equilibre))
    print(equilibre)
    for nom, cartes in list(paquets.items()) + list(extensions.items()):
        print(nom, len(cartes), ' '.join(c['mot'] for c in cartes))


def simuler(paquets, parties=4000):
    """Équilibre de la boîte : chaque paquet commence une partie sur deux."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import simulation_wuertduell as sim
    random.seed(7)

    def cartes(p):
        t = {'nom': 'N', 'verbe': 'V', 'adjectif': 'A', 'nombre': 'Z'}
        # Le bonus de famille est celui du genre (règle du jeu) : deux noms du
        # même genre. Un nom à deux genres est des deux familles ; un nom sans
        # genre au LOD n'en a pas.
        ids = {'m.': 1, 'f.': 2, 'n.': 3}
        def famille(i, genre):
            return frozenset(ids[g] for g in genre.split('/') if g in ids) or frozenset([100 + i])
        return [(t[c['nature']], c['force'], c['cout'], famille(i, c['genre']))
                for i, c in enumerate(p)]
    a, b = cartes(paquets['Déieren']), cartes(paquets['Iessen'])
    victoires, tours = 0, []
    for i in range(parties):
        if i % 2 == 0:
            t, ja, jb = sim.partie(26, a, b)
            victoires += sim.gagnant(t, ja, jb) == 'a'
        else:
            t, ja, jb = sim.partie(26, b, a)
            victoires += sim.gagnant(t, ja, jb) == 'b'
        tours.append(t)
    tours.sort()
    return (f"Déieren gagne {victoires / parties:.0%} des parties contre Iessen ; "
            f"{tours[len(tours) // 2]} tours en médiane, {tours[int(len(tours) * .9)]} pour 90 % des parties")


def rapport(paquets, extensions, toutes, equilibre):
    l = ['# Cartes imprimées : la boîte et les extensions',
         '',
         'Proposition produite par `scripts/candidats_cartes.py` à partir du LOD (CC0).',
         '**À relire avant impression**, idéalement par un enseignant : le LOD ne dit',
         "pas si un mot convient à un enfant, et le thème est lu sur le premier sens.",
         'Pour remplacer une carte, prendre un mot de même coût dans `candidats.csv`.',
         '',
         f'Vivier : {len(toutes)} mots à thème, verbes et adjectifs d\'enfant, dont '
         f"{sum(1 for c in toutes if c['dessin'])} déjà dessinés dans l'application.",
         '',
         f'**Équilibre de la boîte** (simulation, `scripts/simulation_wuertduell.py`) : {equilibre}.',
         '']

    def table(titre, cartes):
        l.append(f'## {titre} ({len(cartes)} cartes)')
        l.append('')
        l.append('| Mot | Nature | Coût | Force | Sens | Paysage | Dessin | Phrase d\'exemple |')
        l.append('|---|---|---|---|---|---|---|---|')
        for c in cartes:
            nat = c['nature'] + (f" {c['genre']}" if c['genre'] else '')
            ex = f"*{c['exemple']}*" + (f" — {c['traduction']}" if c['traduction'] else '') if c['exemple'] else ''
            l.append(f"| **{c['mot']}** | {nat} | {SYMBOLES.get(c['cout'], 'gratuit')} | {c['force'] or '—'} "
                     f"| {c['sens']} | {c['lieu'] or '—'} | {c['dessin'] or '—'} | {ex} |")
        l.append('')
    l.append('# La boîte de départ : 2 paquets de 26 (52 cartes) + 2 cartes de règle')
    l.append('')
    for nom, cartes in paquets.items():
        table(f'Paquet {nom}', cartes)
    l.append('# Les extensions')
    l.append('')
    for nom, cartes in extensions.items():
        table(f'Extension {nom}', cartes)
    return '\n'.join(l) + '\n'


if __name__ == '__main__':
    main()
