#!/usr/bin/env python3
"""Les séries « Niveau A1 » et « Niveau A2 » du carnet (propriétaire, 2026-10-31).

Aucun examen de luxembourgeois ne publie sa liste de mots : l'INLL fait passer
le diplôme LAF (A2 à C1) et le Sproochentest (A2 à l'oral, B1 à l'écoute),
sans vocabulaire officiel. La source la plus proche est celle du LOD, qui
marque les mots des deux manuels de cours de l'INLL : catégories `GWS A1`
(« Schwätzt Dir Lëtzebuergesch? – Niveau A1 (Wuertschatz) », 867 articles)
et `GWS A2` (1 278 articles). Le LOD les dit purement didactiques, faits pour
travailler avec les manuels : l'appli parle donc du vocabulaire des niveaux
A1 et A2, jamais des « mots de l'examen ». Les données sont celles du LOD
(data.public.lu, CC0), déjà en cache pour `generate_translations.py`.

La liste A2 complète la liste A1 au lieu de la répéter (107 mots en commun).
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
"""
import argparse
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
                    lb='Uebst a Geméis', pt='Fruta e legumes'), {'UEBST', 'GEMEIS', 'NOSS', 'FRECHHEET'}),
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

# Les sens écrits ici, là où la table mêle un homographe ou un nom propre
# (fr, de, en, pt). « Enkel » est en plus isolé : sa carte ne prend ni la
# famille ni les phrases de la cheville.
MANUELS = {
    'Enkel': ('petit-fils', 'Enkel', 'grandson', 'neto'),               # LOD ENKEL1
    'Fra': ('femme, épouse', 'Frau, Ehefrau', 'woman, wife', 'mulher, esposa'),  # LOD FRA1
}

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


def articles(niveau):
    """Les articles du LOD marqués pour le niveau, dans l'ordre du fichier.

    Un article sans traduction renvoie souvent à sa variante (« genau » →
    « genee ») : il en prend les sens. Une contraction (« am » = an + dem)
    n'en a pas, et reste sans sens.
    """
    marque = f'GWS {niveau}'
    tous, retenus = {}, []
    for _, el in ET.iterparse(LOD):
        if el.tag != 'entry':
            continue
        sens = _sens(el, False) or _sens(el, True)
        variante = next((l.get('idRef') for l in el.iter('internalLink') if l.get('relation') == 'Variant'), None)
        tous[el.get('id')] = sens
        categories = [c.text for c in el.iter('category')]
        if marque in categories:
            pos_el = el.find('.//partOfSpeech')
            pos = pos_el.text if pos_el is not None else None
            retenus.append({
                'id': el.get('id'), 'lemme': el.findtext('lemma'), 'pos': pos,
                'genre': pos_el.get('gen') if pos_el is not None and pos == 'SUBST' else None,
                'categories': set(categories), 'sens': sens, 'variante': variante,
            })
        el.clear()
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--niveau', choices=('A1', 'A2'), required=True)
    niveau = parser.parse_args().niveau
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
        n'est pas « Sauer » (la Sûre)."""
        lemme = article['lemme']
        if lemme in dico or lemme in essentiels:
            return lemme
        autres = connues.get(lemme.lower(), set())
        if len(autres) == 1:
            autre = next(iter(autres))
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
            if forme == 'Enkel':
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
