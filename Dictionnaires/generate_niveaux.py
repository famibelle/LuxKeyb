#!/usr/bin/env python3
"""Les séries « Niveau A1 » et « Niveau A2 » du carnet (propriétaire, 2026-10-31).

Aucun examen de luxembourgeois ne publie sa liste de mots : l'INLL fait passer
le diplôme LAF (A2 à C1) et le Sproochentest (A2 à l'oral, B1 à l'écoute),
sans vocabulaire officiel. La source la plus proche est celle du LOD, qui
marque les mots des deux manuels de cours de l'INLL : catégories `GWS A1`
(« Schwätzt Dir Lëtzebuergesch? – Niveau A1 (Wuertschatz) », 867 articles)
et `GWS A2` (1 281 articles). Le LOD les dit purement didactiques, faits pour
travailler avec les manuels : l'appli parle donc du vocabulaire des niveaux
A1 et A2, jamais des « mots de l'examen ».

La liste des articles d'un niveau est celle que la recherche avancée du LOD
exporte (`Dictionnaires/niveaux/gws_a1.csv` et `gws_a2.csv`, listes du
25 novembre 2026, colonnes de l'export) ; les sens et les catégories viennent
du jeu de données ouvert du LOD (data.public.lu, CC0), déjà en cache pour
`generate_translations.py`. Le jeu ouvert n'est publié que de loin en loin :
un article de la liste qu'il n'a pas encore prend ses sens dans [MANUELS].
Pour mettre une liste à jour : réexporter la recherche avancée du LOD
(catégorie « GWS A1 » ou « GWS A2 »), réécrire le CSV, relancer.

La liste A2 complète la liste A1 au lieu de la répéter (une centaine de mots
en commun).
Chaque niveau est une série à lui, rangée en pages comme les essentiels :

- d'abord des **thèmes**, tirés des catégories du LOD (famille, métiers,
  manger, santé, calendrier…), dans l'ordre de [THEMES] : le premier qui
  convient prend le mot ;
- puis le reste par **nature** : les noms, les verbes, les adjectifs, les
  petits mots (adverbes, prépositions, pronoms…) ;
- une page dépasse rarement [PAGE_MAX] cases : au-delà, elle est coupée en
  pages numérotées, dans l'ordre alphabétique.

Le format de l'actif est celui de `luxemburgish_essentiels.json`, lu par le
même code (`carnet/Essentiels.kt`) :

- la **forme de carte** est le lemme du LOD, ramené à la casse du
  dictionnaire ou d'un essentiel quand seule la casse diffère (« moien »
  interjection est la carte « Moien ») ;
- les **sens** sont ceux des actifs de traduction quand ils disent le même
  mot que l'article du manuel ; sinon (homographe, ou forme absente des
  traductions) ceux de l'article lui-même, dans les quatre langues, et le
  mot est **isolé** : sa carte ne reprend ni la famille ni la phrase
  d'exemple de l'autre mot ;
- les mots de `MotsEcartes.kt` restent dehors (les jeux ne les proposent
  pas), et les noms propres aussi, sauf le Nationalfeierdag.

L'actif n'est pas régénéré par la CI, comme les essentiels : le relancer à la
main après une mise à jour du LOD, puis `./gradlew testDebugUnitTest --rerun`
(`NiveauxAssetTest`).

    python Dictionnaires/generate_niveaux.py --niveau A1
    python Dictionnaires/generate_niveaux.py --niveau A2

Un autre classement, à la main, est proposé dans `niveaux/themes_proposes.py`
(une quarantaine de thèmes communs aux deux niveaux) : `--classement propose`
l'applique, et `--apercu` écrit seulement `niveaux/classement_a1.md` (ou a2)
pour le relire, sans toucher à l'actif.
"""
import argparse
import csv
import datetime
import json
import os
import sys
import xml.etree.ElementTree as ET

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ICI)
from generate_series import mots_ecartes, plier  # noqa: E402

ASSETS = os.path.join(ICI, '..', 'android_keyboard', 'app', 'src', 'main', 'assets')
LOD = os.path.join(ICI, 'luxemburgish_data', 'lod', 'new_lod-art.xml')
LANGUES = ('fr', 'de', 'en', 'pt')
PAGE_MAX = 40

# Les thèmes, dans l'ordre où ils prennent les mots. Les titres en
# luxembourgeois, en allemand et en portugais sont écrits par Claude, à
# faire relire.
THEMES = [
    ('famille', dict(fr='Famille', en='Family', de='Familie', lb='Famill', pt='Família'), {'FAMILL'}),
    ('metiers', dict(fr='Métiers', en='Jobs', de='Berufe', lb='Beruffer', pt='Profissões'),
     {'BERUFFSBEZEECHNUNG'}),
    ('personnes', dict(fr='Les gens', en='People', de='Menschen', lb='D\'Leit', pt='Pessoas'), {'PERSOUN'}),
    ('fruits', dict(fr='Fruits et légumes', en='Fruit and vegetables', de='Obst und Gemüse',
                    lb='Uebst a Geméis', pt='Fruta e legumes'), {'UEBST', 'GEMEIS', 'NOSS'}),
    ('repas', dict(fr='Manger et boire', en='Food and drink', de='Essen und Trinken',
                   lb='Iessen an Drénken', pt='Comer e beber'),
     {'IESSEN', 'LWS', 'GEDRENKS', 'GEWIERZ', 'KRAUTGEWIERZ', 'HORECA', 'GASTR', 'FESCH'}),
    ('sante', dict(fr='Corps et santé', en='Body and health', de='Körper und Gesundheit',
                   lb='Kierper a Gesondheet', pt='Corpo e saúde'), {'MED', 'ANAT', 'CORONA'}),
    ('meteo', dict(fr='Météo', en='Weather', de='Wetter', lb='Wieder', pt='Tempo'), {'WIEDER', 'METEO'}),
    ('calendrier', dict(fr='Mois et fêtes', en='Months and holidays', de='Monate und Feste',
                        lb='Méint a Fester', pt='Meses e festas'), {'MOUNT', 'FEIERDEEG', 'FEST'}),
    ('ecole', dict(fr='École et études', en='School and studies', de='Schule und Studium',
                   lb='Schoul a Studium', pt='Escola e estudos'), {'SCHOUL', 'INFO'}),
    ('loisirs', dict(fr='Sport et loisirs', en='Sport and leisure', de='Sport und Freizeit',
                     lb='Sport a Fräizäit', pt='Desporto e lazer'),
     {'SPORT', 'FUSSBALL', 'MUSEK', 'FILM', 'THEATER', 'LITERATUR', 'MUSEKSINSTRUMENT', 'SPILLPLAZ'}),
    ('transports', dict(fr='Transports', en='Transport', de='Verkehr', lb='Transport', pt='Transportes'),
     {'GEFIER', 'FUHRERSCHAIN'}),
    ('couleurs', dict(fr='Couleurs', en='Colours', de='Farben', lb='Faarwen', pt='Cores'), {'FAARF'}),
    ('mesures', dict(fr='Mesures et argent', en='Measures and money', de='Maße und Geld',
                     lb='Moossen a Suen', pt='Medidas e dinheiro'), {'MOOSSEENHEET', 'WARUNG'}),
    ('lieux', dict(fr='Le pays', en='The country', de='Das Land', lb='D\'Land', pt='O país'), {'POL'}),
]

# Puis le reste, par nature.
NATURES = [
    ('noms', dict(fr='Noms', en='Nouns', de='Nomen', lb='Substantiver', pt='Nomes'), {'SUBST'}),
    ('verbes', dict(fr='Verbes', en='Verbs', de='Verben', lb='Verben', pt='Verbos'), {'VRB'}),
    ('adjectifs', dict(fr='Adjectifs', en='Adjectives', de='Adjektive', lb='Adjektiver', pt='Adjetivos'), {'ADJ'}),
    ('petits_mots', dict(fr='Petits mots', en='Little words', de='Kleine Wörter', lb='Kleng Wierder',
                         pt='Palavras pequenas'), None),
]

# Les catégories du LOD sont larges : « MED » range aussi Glas et Regioun,
# « FEST » l'Aarbecht du 1er mai. Ces mots-là vont à leur nature.
HORS_THEME = {
    'Glas', 'Regioun', 'Waasser', 'huelen', 'leien', 'réi', 'Schnapp', 'Aarbecht', 'sangen', 'sauer',
    'séiss', 'schneien', 'ellen', 'Kaart', 'Stonn', 'Buch', 'Menü', 'Dossier', 'goen',
}

# Écartés ici en plus de MotsEcartes : l'autre moitié d'un mot qu'il écarte
# (« Chrëscht » y est, pas le féminin).
# « Do » (am hellen Do, en plein jour) doublerait « do » (là), qui y est.
EXCLUS = {'Chrëschtin', 'Do'}

# Le sens de la table, et non celui de l'article marqué : le LOD marque
# l'adverbe « du » (alors), mais c'est le pronom que le manuel enseigne.
TABLE_GARDEE = {'du'}

# Les sens écrits ici (fr, de, en, pt), là où la table mêle un homographe ou
# un nom propre, où l'article du LOD n'a pas de traduction dans les quatre
# langues, ou n'est pas encore dans le jeu de données ouvert. Écrits par
# Claude d'après l'article du LOD, à faire relire.
MANUELS = {
    'Enkel': ('petit-fils', 'Enkel', 'grandson', 'neto'),               # LOD ENKEL1
    'Fra': ('femme, épouse', 'Frau, Ehefrau', 'woman, wife', 'mulher, esposa'),  # LOD FRA1
    # A1 : articles sans traduction dans une des langues.
    'Aarbechter': ('ouvrier', 'Arbeiter', 'worker', 'operário'),
    'Aarbechterin': ('ouvrière', 'Arbeiterin', '(female) worker', 'operária'),
    'Botzmann': ('agent d\'entretien, homme de ménage', 'Putzmann, Reinigungskraft', 'cleaner',
                 'empregado de limpeza'),
    'Direktesch': ('directrice', 'Direktorin', '(female) director, headmistress', 'diretora'),
    'Disko': ('discothèque', 'Disko, Diskothek', 'disco, nightclub', 'discoteca'),
    'Dëppchen': ('petit pot, gobelet', 'Becher', 'pot, tub', 'pote, copo'),
    'Enkelkand': ('petit-enfant', 'Enkelkind', 'grandchild', 'neto, neta'),
    'Estheticien': ('esthéticien', 'Kosmetiker', 'beautician', 'esteticista'),
    'Groussherzogin': ('grande-duchesse', 'Großherzogin', 'Grand Duchess', 'grã-duquesa'),
    'Receptionnistin': ('réceptionniste', 'Rezeptionistin, Empfangsdame', 'receptionist', 'rececionista'),
    'Tëlee': ('télé, télévision', 'Fernsehen, Fernseher', 'TV, television', 'televisão'),
    'Uni': ('fac, université', 'Uni, Universität', 'university', 'universidade'),
    'ofschmaachen': ('assaisonner (en goûtant)', 'abschmecken', 'to season to taste', 'temperar a gosto'),
    'dono': ('après, ensuite', 'danach', 'afterwards, then', 'depois, a seguir'),
    'duerno': ('après, ensuite', 'danach', 'afterwards, then', 'depois, a seguir'),
    # A2 : les mêmes raisons.
    'Disco': ('discothèque', 'Disko, Diskothek', 'disco, nightclub', 'discoteca'),
    'Dugend': ('qualité, vertu', 'Tugend', 'virtue', 'virtude'),
    'emol': ('un jour, une fois', 'einmal, mal', 'once, some day', 'um dia, uma vez'),
    'Info': ('info, information', 'Info, Information', 'info, information', 'informação'),
    'opspären': ('ouvrir (à clé), déverrouiller', 'aufschließen', 'to unlock', 'destrancar, abrir'),
    'Rand': ('bord', 'Rand', 'edge, rim', 'borda, margem'),            # LOD RAND3, pas Rand (bovin)
    'WG': ('colocation', 'WG, Wohngemeinschaft', 'shared flat', 'casa partilhada'),
    'domat': ('avec ça', 'damit', 'with it, with that', 'com isso'),
    'dovun': ('de cela, en', 'davon', 'of it, from it', 'disso'),
    'drop': ('dessus, là-dessus', 'darauf', 'on it, on that', 'em cima disso'),
    'drun': ('à cela, y', 'daran', 'on it, about it', 'nisso'),
    'heimat': ('avec ceci', 'hiermit', 'with this, herewith', 'com isto'),
    # A2 : participes devenus adjectifs, plus récents que le LOD ouvert ; la
    # table donne le sens du verbe (« besat » : occuper).
    'besat': ('occupé', 'besetzt', 'occupied, taken', 'ocupado'),
    'gepacst': ('pacsé', 'eingetragen verpartnert', 'in a civil partnership', 'em união de facto'),
    'miwweléiert': ('meublé', 'möbliert', 'furnished', 'mobilado'),
}

# Les mots de MANUELS dont la carte ne prend ni la famille ni les phrases de
# l'homographe : la cheville pour « Enkel », le bovin pour « Rand ».
ISOLES_MANUELS = {'Enkel', 'Rand'}

# Les noms propres qui restent : une fête que tout le pays célèbre.
NOMS_PROPRES_GARDES = {'Nationalfeierdag'}


def charger(nom):
    with open(os.path.join(ASSETS, nom), encoding='utf-8') as f:
        return json.load(f)


def _sens(el, avec_locutions):
    """Les traductions du premier sens qui en a, langue par langue."""
    sens = {}
    for m in el.iter('meaning'):
        # Les locutions de l'article ne disent pas le mot lui-même : on ne
        # les prend que si l'article n'a rien d'autre (« (rechten) Cousin »).
        if not avec_locutions and m.find('secondaryHeadword') is not None:
            continue
        for tl in m.findall('targetLanguage'):
            l = tl.get('lang')
            if l in LANGUES and l not in sens:
                t = [x.text.strip() for x in tl.findall('translation') if x.text and x.text.strip()]
                if t:
                    sens[l] = ', '.join(t[:3])
        if len(sens) == len(LANGUES):
            break
    return sens


# Les listes du LOD, exportées de sa recherche avancée (« Schwätzt Dir
# Lëtzebuergesch? – Niveau A1 (Wuertschatz) », liste du 25 novembre 2026) :
# elles font foi, car le jeu de données ouvert (data.public.lu) n'est publié
# que de loin en loin et ignore les articles ajoutés depuis.
LISTES = os.path.join(ICI, 'niveaux')

# La nature d'un article que le jeu de données ouvert n'a pas encore, d'après
# la colonne « Wuertaart » de la liste.
WUERTAART = {'Adjektiv': 'ADJ', 'onverännerbaart Adjektiv': 'ADJ', 'Verb': 'VRB', 'Adverb': 'ADV',
             'Substantiv': 'SUBST', 'Prepositioun': 'PREP', 'Konjunktioun': 'CONJ', 'Interjektioun': 'INTERJ'}


def liste(niveau):
    """Les articles de la liste du LOD : {id: (lemme, nature)}, dans l'ordre."""
    with open(os.path.join(LISTES, f'gws_{niveau.lower()}.csv'), encoding='utf-8') as f:
        return {r['LOD-Artikel']: (r['Grondformen'], r['Wuertaart']) for r in csv.DictReader(f)}


def articles(niveau):
    """Les articles de la liste du niveau, dans l'ordre du fichier du LOD.

    Un article sans traduction renvoie souvent à sa variante (« genau » →
    « genee ») : il en prend les sens. Une contraction (« am » = an + dem)
    n'en a pas, et reste sans sens. Un article plus récent que le jeu de
    données ouvert n'a que son lemme et sa nature : ses sens sont dans
    MANUELS.
    """
    marque, ids = f'GWS {niveau}', liste(niveau)
    tous, retenus, vus = {}, [], set()
    for _, el in ET.iterparse(LOD):
        if el.tag != 'entry':
            continue
        sens = _sens(el, False) or _sens(el, True)
        variante = next((l.get('idRef') for l in el.iter('internalLink') if l.get('relation') == 'Variant'), None)
        tous[el.get('id')] = sens
        categories = [c.text for c in el.iter('category')]
        if el.get('id') in ids:
            vus.add(el.get('id'))
            pos_el = el.find('.//partOfSpeech')
            pos = pos_el.text if pos_el is not None else None
            retenus.append({
                'id': el.get('id'), 'lemme': el.findtext('lemma'), 'pos': pos,
                'genre': pos_el.get('gen') if pos_el is not None and pos == 'SUBST' else None,
                'categories': set(categories), 'sens': sens, 'variante': variante,
            })
        elif marque in categories:
            print(f'  {el.get("id")} est marqué {marque} dans le LOD ouvert, mais pas dans la liste : laissé dehors')
        el.clear()
    for i, (lemme, wuertaart) in ids.items():
        if i not in vus:
            print(f'  {i} ({lemme}) : plus récent que le LOD ouvert, sens écrits à la main')
            retenus.append({'id': i, 'lemme': lemme, 'pos': WUERTAART.get(wuertaart), 'genre': None,
                            'categories': set(), 'sens': {}, 'variante': None})
    for a in retenus:
        if len(a['sens']) < len(LANGUES) and a['variante'] in tous:
            a['sens'] = {**tous[a['variante']], **a['sens']}
    return retenus


def couper(ident, titres, mots):
    """Une page, ou plusieurs pages numérotées si elle dépasse PAGE_MAX."""
    mots = sorted(mots, key=lambda m: (plier(m), m))
    n = -(-len(mots) // PAGE_MAX)
    if n <= 1:
        return [{'id': ident, 'titres': titres, 'mots': mots}]
    taille = -(-len(mots) // n)
    return [{'id': f'{ident}_{k + 1}', 'titres': {l: f'{t} {k + 1}' for l, t in titres.items()},
             'mots': mots[k * taille:(k + 1) * taille]} for k in range(n)]


# Le plus petit thème qui fait une page dans un niveau.
THEME_MIN = 3


def themes_proposes():
    """Le classement de niveaux/themes_proposes.py : [(id, titres, {mots})] et les replis."""
    sys.path.insert(0, LISTES)
    from themes_proposes import THEMES as PROPOSES, REPLI, CHAPITRES_A2
    return [(i, titres, {m.replace('_', ' ') for m in mots.split()}) for i, titres, mots in PROPOSES], REPLI, CHAPITRES_A2


def chapitre(ident, chapitres):
    """Le chapitre du manuel d'un thème (sans son numéro de page), ou None."""
    base = ident.rsplit('_', 1)[0] if ident[-1].isdigit() else ident
    return next(((n, titre) for n, titre, ids in chapitres if base in ids), None)


def apercu(niveau, pages, gloses, traductions, chapitres=()):
    """Les pages d'un niveau, en Markdown, avec le sens français de chaque mot."""
    def sens(m):
        t = traductions['fr']
        g = gloses['fr'].get(m) or t.get(m) or t.get(m.lower()) or ''
        return g.split(',')[0].strip()
    total = sum(len(p['mots']) for p in pages)
    lignes = [f'# Niveau {niveau} : classement proposé', '',
              f'{len(pages)} pages, {total} mots. Écrit par `generate_niveaux.py --niveau {niveau} '
              '--classement propose --apercu` ; les thèmes sont dans `themes_proposes.py`.', '']
    vu = None
    for p in pages:
        c = chapitre(p['id'], chapitres)
        if c and c != vu:
            lignes += [f'# Kapitel {c[0]} : {c[1]}' if c[0] else f'# {c[1]}', '']
        elif not c and vu:
            lignes += ['# Les autres pages', '']
        vu = c
        lignes += [f"## {p['titres']['fr']} ({len(p['mots'])})", '',
                   ' · '.join(f'**{m}** {sens(m)}' for m in p['mots']), '']
    chemin = os.path.join(LISTES, f'classement_{niveau.lower()}.md')
    with open(chemin, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lignes))
    print(f'{chemin} : {len(pages)} pages, {total} mots')
    for p in pages:
        print(f"  {p['titres']['fr']:<32} {len(p['mots']):>3}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--niveau', choices=('A1', 'A2'), required=True)
    parser.add_argument('--classement', choices=('lod', 'propose'), default='lod',
                        help="lod : thèmes tirés des catégories du LOD ; propose : niveaux/themes_proposes.py")
    parser.add_argument('--apercu', action='store_true',
                        help="écrit niveaux/classement_<niveau>.md pour relecture, sans toucher à l'actif")
    args = parser.parse_args()
    niveau = args.niveau
    sortie_nom = f'luxemburgish_niveau_{niveau.lower()}.json'

    dico = {m for m, _ in charger('luxemburgish_dict.json')}
    essentiels = {m for t in charger('luxemburgish_essentiels.json')['themes'] for m in t['mots']}
    traductions = {l: charger('luxemburgish_translations.json' if l == 'fr' else f'luxemburgish_translations_{l}.json')
                   ['translations'] for l in LANGUES}
    categories_lod = charger('luxemburgish_categories.json')['categories']
    ecartes = mots_ecartes()

    # La casse d'une carte qui existe déjà, quand seule la casse diffère.
    connues = {}
    for m in sorted(dico | essentiels):
        connues.setdefault(m.lower(), set()).add(m)

    def table(forme, langue):
        t = traductions[langue]
        return t.get(forme) or t.get(forme.lower())

    def meme_mot(forme, article):
        """La glose de la table dit-elle le mot de l'article ?

        Elle mêle parfois deux homographes (« Enkel » : petit-fils,
        petits-enfants, cheville) ; on la garde quand même dès qu'elle dit
        aussi le mot de l'article, car ses sens servent à toute l'appli
        (Wierderbuch, jeux) : les cas qui se voient sont dans MANUELS.
        """
        g = table(forme, 'fr')
        if not g or 'fr' not in article['sens']:
            return bool(g)
        dits = {plier(s.strip()) for s in g.split(',')}
        return any(plier(s.strip()) in dits for s in article['sens']['fr'].split(','))

    def forme_de_carte(article):
        """Le lemme, ou la carte connue qui ne s'en distingue que par la casse
        et dit le même mot : « moien » est « Moien », mais « sauer » (aigre)
        n'est pas « Sauer » (la Sûre). Un nom ne devient jamais une carte en
        minuscules : « Grouss » (salutation) n'est pas « grouss » (grand),
        « Hunn » (poule) n'est pas « hunn » (avoir)."""
        lemme = article['lemme']
        if lemme in dico or lemme in essentiels:
            return lemme
        autres = connues.get(lemme.lower(), set())
        if len(autres) == 1:
            autre = next(iter(autres))
            if lemme[0].isupper() and autre[0].islower():
                return lemme
            if autre in essentiels or meme_mot(autre, article):
                return autre
        return lemme

    # Un même mot peut avoir plusieurs articles marqués (« du » pronom et
    # adverbe) : on garde celui que la table glose, sinon le premier.
    par_forme = {}
    for a in articles(niveau):
        if a['pos'] == 'NP' and a['lemme'] not in NOMS_PROPRES_GARDES:
            continue
        par_forme.setdefault(forme_de_carte(a), []).append(a)

    retenus, ecartes_ici, sans_sens = [], [], []
    gloses = {l: {} for l in LANGUES}
    categories, isoles = {}, []
    propres_essentiels = set(charger('luxemburgish_essentiels.json')['gloses']['fr'])
    for forme, candidats in par_forme.items():
        a = next((c for c in candidats if meme_mot(forme, c)), candidats[0])
        if plier(forme) in ecartes or forme in EXCLUS:
            ecartes_ici.append(forme)
            continue
        if forme in propres_essentiels or forme in TABLE_GARDEE:
            retenus.append((forme, a))      # les essentiels ont déjà leur sens
            continue
        if forme in MANUELS:
            for i, l in enumerate(LANGUES):
                gloses[l][forme] = MANUELS[forme][i]
            if forme in ISOLES_MANUELS:
                isoles.append(forme)
            code = a['pos'] or 'PREP'
            categories[forme] = code + (' ' + a['genre'].upper() if code == 'SUBST' and a['genre'] else '')
            retenus.append((forme, a))
            continue
        meme = meme_mot(forme, a)
        if not meme or any(not table(forme, l) for l in LANGUES):
            if any(l not in a['sens'] and not (meme and table(forme, l)) for l in LANGUES):
                sans_sens.append(forme)
                continue
            for l in LANGUES:
                gloses[l][forme] = a['sens'].get(l) or table(forme, l)
            if not meme and table(forme, 'fr'):
                isoles.append(forme)     # un homographe : rien de l'autre mot
            if forme not in categories_lod or not meme:
                code = a['pos'] or 'PREP'
                if code == 'SUBST' and a['genre']:
                    code += ' ' + a['genre'].upper()
                categories[forme] = code
        retenus.append((forme, a))

    pages, places = [], set()
    if args.classement == 'propose':
        proposes, repli, chapitres = themes_proposes()
        if niveau == 'A2':
            # L'ordre des chapitres du manuel A2, les autres thèmes après.
            rang = {i: k for k, i in enumerate(i for _, _, ids in chapitres for i in ids)}
            proposes = sorted(proposes, key=lambda t: rang.get(t[0], len(rang)))
        prises = {}
        for ident, _, mots in proposes:
            prises[ident] = [f for f, a in retenus if f in mots and all(f not in v for v in prises.values())]
        # Un thème de deux mots à ce niveau ne fait pas une page : ils vont au
        # thème voisin (le mois de mai, seul en A2, aux jours), sinon à leur
        # nature.
        for ident, cible in repli.items():
            if 0 < len(prises[ident]) < THEME_MIN:
                prises[cible] += prises[ident]
                prises[ident] = []
        for ident, titres, _ in proposes:
            if len(prises[ident]) >= THEME_MIN:
                places.update(prises[ident])
                pages += couper(ident, titres, prises[ident])
    else:
        for ident, titres, cats in THEMES:
            mots = [f for f, a in retenus if f not in places and f not in HORS_THEME and a['categories'] & cats]
            places.update(mots)
            if mots:
                pages += couper(ident, titres, mots)
    for ident, titres, natures in NATURES:
        mots = [f for f, a in retenus if f not in places and (natures is None or a['pos'] in natures)]
        places.update(mots)
        if mots:
            pages += couper(ident, titres, mots)

    sortie = {
        'version': 1,
        'generated': datetime.date.today().isoformat(),
        'niveau': niveau,
        'description': f'La série « Niveau {niveau} » du carnet : le vocabulaire du manuel '
                       f'« Schwätzt Dir Lëtzebuergesch? – Niveau {niveau} » de l\'INLL, tel que le LOD le marque.',
        'source': f'LOD (data.public.lu, CC0), catégorie « GWS {niveau} » ; voir generate_niveaux.py.',
        'themes': pages,
        'gloses': gloses,
        'categories': categories,
        'isoles': sorted(isoles),
    }
    if args.apercu:
        apercu(niveau, pages, gloses, traductions, themes_proposes()[2] if niveau == 'A2' else ())
        return
    with open(os.path.join(ASSETS, sortie_nom), 'w', encoding='utf-8') as f:
        json.dump(sortie, f, ensure_ascii=False, indent=1)

    total = sum(len(p['mots']) for p in pages)
    print(f'{sortie_nom} : {len(pages)} pages, {total} mots')
    for p in pages:
        print(f"  {p['titres']['fr']:<24} {len(p['mots']):>3}  {', '.join(p['mots'][:8])}…")
    print(f'sens de l\'article : {len(gloses["fr"])} mots, dont {len(isoles)} isolés')
    print(f'écartés (MotsEcartes) : {ecartes_ici}')
    print(f'sans sens dans une langue, laissés dehors : {sans_sens}')
    print(f'hors du dictionnaire de fréquences : {sum(f not in dico for f in places)}')


if __name__ == '__main__':
    main()
