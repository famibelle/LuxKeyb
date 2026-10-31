#!/usr/bin/env python3
"""Les essentiels : le vocabulaire de base que le carnet doit au moins
contenir, rangé par thème (propriétaire, 2026-10-31).

La liste vient du propriétaire, telle quelle, thème par thème. Elle devient
la série « Les essentiels » du carnet, une page par thème, et les jeux font
passer devant les mots qui y manquent encore (`carnet/Essentiels.kt`).

Ce script écrit `luxemburgish_essentiels.json` à partir de cette liste, et
c'est lui qui la **vérifie** :

- chaque mot est ramené à sa forme de carte : sans article, à la casse du
  dictionnaire (« Jo » est la carte « jo ») ;
- chaque mot doit avoir un sens dans les quatre langues de glose, pris dans
  les actifs de traduction ou, à défaut, dans GLOSES ci-dessous ;
- GLOSES corrige aussi les sens faux pour cette liste : « Mee » est mai, pas
  le « mee » (mais) du corpus ; « Ee » l'œuf, « A » l'œil, « Fest » la fête.
  Ces sens viennent du LOD (articles MEE1, EE1, A1, FEST1, ORANGE2…) quand il
  les donne, et sont écrits à la main pour les expressions qu'il n'a pas ;
- ISOLES marque ces homographes : leur carte ne reprend ni la famille ni la
  phrase d'exemple du mot qu'ils ne sont pas.

L'actif n'est pas régénéré par la CI, comme les blasons et les séries : le
relancer à la main après un changement de la liste ou des traductions, puis
`./gradlew testDebugUnitTest --rerun` (`EssentielsAssetTest`).

    python Dictionnaires/generate_essentiels.py
"""
import datetime
import json
import os
import re
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ICI, '..', 'android_keyboard', 'app', 'src', 'main', 'assets')
SORTIE = os.path.join(ASSETS, 'luxemburgish_essentiels.json')
LANGUES = ('fr', 'de', 'en', 'pt')

# ------------------------------------------------------------- la liste
# Les titres dans les cinq langues de l'interface. Ceux en luxembourgeois,
# en allemand et en portugais sont écrits par Claude, à faire relire.

THEMES = [
    ('salutations', dict(fr='Salutations', en='Greetings', de='Begrüßung', lb='Begréissung', pt='Saudações'),
     "Moien, Gudde Moien, Gudden Owend, Gutt Nuecht, Äddi, Bis geschwënn, Merci, Wann ech gelift, Pardon, Jo, Nee"),
    ('identite', dict(fr='Identité', en='Identity', de='Angaben zur Person', lb='Identitéit', pt='Identidade'),
     "den Numm, de Virnumm, d'Adress, d'Telefonsnummer, d'Nationalitéit, d'Alter, d'Land, d'Sprooch, bestuet, ledeg, gescheet"),
    ('famille', dict(fr='Famille', en='Family', de='Familie', lb='Famill', pt='Família'),
     "d'Famill, d'Elteren, de Papp, d'Mamm, de Brudder, d'Schwëster, de Jong, d'Duechter, d'Kanner, de Bopa, "
     "d'Bomi, de Mann, d'Fra, de Monni, d'Tatta, de Frënd, d'Frëndin"),
    ('nombres', dict(fr='Nombres', en='Numbers', de='Zahlen', lb='Zuelen', pt='Números'),
     "eent, zwee, dräi, véier, fënnef, sechs, siwen, aacht, néng, zéng, eelef, zwielef, zwanzeg, drësseg, honnert, dausend"),
    ('temps', dict(fr='Temps', en='Time', de='Zeit', lb='Zäit', pt='Tempo'),
     "den Dag, d'Woch, de Mount, d'Joer, haut, muer, gëschter, de Moien, de Mëtteg, den Owend, d'Nuecht, d'Auer, "
     "d'Stonn, d'Minutt, fréi, spéit"),
    ('jours', dict(fr='Jours', en='Days', de='Wochentage', lb='Deeg', pt='Dias da semana'),
     "Méindeg, Dënschdeg, Mëttwoch, Donneschdeg, Freideg, Samschdeg, Sonndeg"),
    ('mois', dict(fr='Mois', en='Months', de='Monate', lb='Méint', pt='Meses'),
     "Januar, Februar, Mäerz, Abrëll, Mee, Juni, Juli, August, September, Oktober, November, Dezember"),
    ('meteo', dict(fr='Météo et saisons', en='Weather and seasons', de='Wetter und Jahreszeiten',
                   lb='Wieder a Joreszäiten', pt='Tempo e estações'),
     "d'Wieder, d'Sonn, de Reen, de Schnéi, de Wand, kal, waarm, d'Fréijoer, de Summer, den Hierscht, de Wanter"),
    ('couleurs', dict(fr='Couleurs', en='Colours', de='Farben', lb='Faarwen', pt='Cores'),
     "rout, blo, giel, gréng, schwaarz, wäiss, gro, brong, rosa, orange"),
    ('logement', dict(fr='Logement', en='Home', de='Wohnen', lb='Wunnen', pt='Habitação'),
     "d'Haus, d'Wunneng, d'Zëmmer, d'Kichen, d'Stuff, d'Schlofzëmmer, d'Buedzëmmer, de Gaart, de Balcon, d'Dier, "
     "d'Fënster, den Dësch, de Stull, d'Bett, de Schaf, de Loyer, den Noper"),
    ('travail', dict(fr='Travail', en='Work', de='Arbeit', lb='Aarbecht', pt='Trabalho'),
     "d'Aarbecht, de Beruff, de Chef, de Kolleg, de Büro, d'Firma, d'Paus, d'Pai, d'Vakanz, schaffen"),
    ('transports', dict(fr='Transports', en='Transport', de='Verkehr', lb='Transport', pt='Transportes'),
     "den Auto, de Bus, den Zuch, den Tram, de Vëlo, de Fliger, d'Gare, den Arrêt, den Ticket, zu Fouss"),
    ('nourriture', dict(fr='Nourriture', en='Food', de='Essen und Trinken', lb='Iessen an Drénken', pt='Comida'),
     "d'Brout, d'Botter, de Kéis, d'Fleesch, de Fësch, d'Geméis, d'Uebst, den Apel, d'Gromper, d'Ee, d'Zopp, "
     "de Kuch, de Kaffi, den Téi, d'Waasser, d'Mëllech, de Wäin, de Béier"),
    ('courses', dict(fr='Courses', en='Shopping', de='Einkaufen', lb='Akafen', pt='Compras'),
     "de Buttek, de Supermarché, d'Bäckerei, de Präis, deier, bëlleg, kafen, bezuelen, Wéi vill kascht dat?"),
    ('corps', dict(fr='Corps et santé', en='Body and health', de='Körper und Gesundheit',
                   lb='Kierper a Gesondheet', pt='Corpo e saúde'),
     "de Kapp, d'A, d'Aen, d'Nues, de Mond, d'Ouer, den Aarm, d'Hand, d'Been, de Fouss, de Bauch, de Réck, "
     "den Dokter, d'Apdikt, d'Medikament, krank, gesond"),
    ('vetements', dict(fr='Vêtements', en='Clothes', de='Kleidung', lb='Kleeder', pt='Roupa'),
     "d'Kleeder, d'Box, de Rack, d'Hiem, de Pullover, d'Jackett, de Mantel, d'Schong, d'Kap, de Schal"),
    ('loisirs', dict(fr='Loisirs', en='Leisure', de='Freizeit', lb='Fräizäit', pt='Lazer'),
     "de Sport, d'Musek, de Film, d'Buch, schwammen, danzen, spadséiere goen, reesen, kachen, liesen"),
    ('fetes', dict(fr='Fêtes', en='Celebrations', de='Feste', lb='Fester', pt='Festas'),
     "d'Fest, de Gebuertsdag, d'Hochzäit, de Kaddo, Chrëschtdag, Ouschteren, den Nationalfeierdag, feieren, invitéieren"),
    ('verbes', dict(fr='Verbes courants', en='Common verbs', de='Häufige Verben', lb='Heefeg Verben', pt='Verbos comuns'),
     "sinn, hunn, ginn, kommen, heeschen, wunnen, schwätzen, maachen, iessen, drénken, schlofen, fueren, spillen, "
     "gesinn, héieren, schreiwen, sichen, wëllen, kënnen, mussen"),
    ('adjectifs', dict(fr='Adjectifs', en='Adjectives', de='Adjektive', lb='Adjektiver', pt='Adjetivos'),
     "grouss, kleng, al, jonk, nei, schéin, gutt, schlecht, laang, kuerz, déck, schlank, frëndlech"),
    ('image', dict(fr='Décrire une image', en='Describing a picture', de='Ein Bild beschreiben',
                   lb='E Bild beschreiwen', pt='Descrever uma imagem'),
     "uewen, ënnen, lénks, riets, an der Mëtt, vir, hannen, niewent, tëscht"),
    ('questions', dict(fr='Mots interrogatifs', en='Question words', de='Fragewörter', lb='Froewierder',
                       pt='Palavras interrogativas'),
     "wien, wat, wou, vu wou, wéini, wéi, firwat, wéi vill"),
]

# --------------------------------------------- les sens, là où il en faut
# Un sens par langue : fr, de, en, pt. LOD = traduction reprise du LOD
# (article indiqué) ; les autres sont écrits à la main.

GLOSES = {
    # Les salutations : la glose du corpus ne dit que « matin », « remerciement ».
    'Moien': ('bonjour, matin', 'guten Morgen, Morgen', 'hello, morning', 'bom dia, manhã'),        # LOD MOIEN1
    'Merci': ('merci, remerciement', 'danke, Dank', 'thank you', 'obrigado, agradecimento'),        # LOD MERCI1
    'Äddi': ('au revoir, salut', 'tschüss, auf Wiedersehen', 'goodbye, bye', 'adeus, tchau'),
    # Absents des traductions, ou absents du LOD (« ledeg » : le LOD dit celibataire, single, onbestuet).
    'ledeg': ('célibataire', 'ledig', 'single', 'solteiro'),                                         # LOD CELIBATAIRE1
    'gescheet': ('divorcé', 'geschieden', 'divorced', 'divorciado'),                                 # LOD scheeden
    'tëscht': ('entre', 'zwischen', 'between', 'entre'),
    # Les homographes : le corpus n'a que l'autre mot.
    'Mee': ('mai', 'Mai', 'May', 'maio'),                                                             # LOD MEE1
    'Ee': ('œuf', 'Ei', 'egg', 'ovo'),                                                                # LOD EE1
    'A': ('œil', 'Auge', 'eye', 'olho'),                                                              # LOD A1
    'Fest': ('fête', 'Fest', 'party', 'festa'),                                                       # LOD FEST1
    'orange': ('orange (couleur)', 'orange', 'orange (colour)', 'cor de laranja'),                    # LOD ORANGE2
    # Un sens plus juste pour cette liste.
    'Aen': ('yeux', 'Augen', 'eyes', 'olhos'),
    'Kanner': ('enfants', 'Kinder', 'children', 'crianças'),
    'Sprooch': ('langue, parole', 'Sprache', 'language, speech', 'língua, fala'),                    # LOD SPROOCH1
    'wéi': ('comment, combien', 'wie', 'how', 'como'),                                                # LOD WEI3
    # Les expressions.
    'Gudde Moien': ('bonjour', 'guten Morgen', 'good morning', 'bom dia'),                           # LOD MOIEN1
    'Gudden Owend': ('bonsoir', 'guten Abend', 'good evening', 'boa noite'),                         # LOD OWEND1
    'Gutt Nuecht': ('bonne nuit', 'gute Nacht', 'good night', 'boa noite (ao deitar)'),
    'Bis geschwënn': ('à bientôt', 'bis bald', 'see you soon', 'até breve'),
    'Wann ech gelift': ('s’il vous plaît', 'bitte', 'please', 'por favor'),
    'zu Fouss': ('à pied', 'zu Fuß', 'on foot', 'a pé'),                                              # LOD FOUSS1
    'Wéi vill kascht dat?': ('combien ça coûte ?', 'Wie viel kostet das?', 'How much does it cost?', 'Quanto custa?'),
    'spadséiere goen': ('aller se promener', 'spazieren gehen', 'to go for a walk', 'ir passear'),
    'an der Mëtt': ('au milieu', 'in der Mitte', 'in the middle', 'no meio'),
    'vu wou': ('d’où', 'woher', 'where from', 'de onde'),
    'wéi vill': ('combien (de)', 'wie viel', 'how much, how many', 'quanto(s)'),                     # LOD WEI3
}

# La catégorie de la carte, là où le LOD ne la donne pas ou donne celle de l'autre mot.
CATEGORIES = {
    'Mee': 'SUBST M', 'Ee': 'SUBST N', 'A': 'SUBST N', 'Fest': 'SUBST N', 'orange': 'ADJ',
    'ledeg': 'ADJ', 'gescheet': 'ADJ', 'Äddi': 'INTERJ', 'tëscht': 'PREP',
    # Des pluriels et un nom que le LOD range sans catégorie lisible.
    'Aen': 'SUBST', 'Kanner': 'SUBST', 'Kleeder': 'SUBST', 'Kolleg': 'SUBST M',
}

# Ceux dont la carte ne doit rien prendre au mot qu'ils ne sont pas.
ISOLES = ['Mee', 'Ee', 'A', 'Fest', 'orange']

ARTICLE = re.compile(r"^(den|de)\s+|^d'")


def est_expression(mot):
    return not mot.isalpha()


def charger(nom):
    with open(os.path.join(ASSETS, nom), encoding='utf-8') as f:
        return json.load(f)


def main():
    dico = {m for m, _ in charger('luxemburgish_dict.json')}
    casse = {}
    for m in dico:
        casse.setdefault(m.lower(), m)
    traductions = {l: charger('luxemburgish_translations.json' if l == 'fr' else f'luxemburgish_translations_{l}.json')
                   ['translations'] for l in LANGUES}
    categories_lod = charger('luxemburgish_categories.json')['categories']

    def forme_de_carte(brut):
        m = ARTICLE.sub('', brut.strip())
        if m in GLOSES or est_expression(m):
            return m
        if m in dico:
            return m
        return casse.get(m.lower(), m)

    def glose(forme, langue):
        if forme in GLOSES:
            return GLOSES[forme][LANGUES.index(langue)]
        t = traductions[langue]
        return t.get(forme) or t.get(forme.lower())

    themes, erreurs, tous = [], [], []
    for ident, titres, liste in THEMES:
        mots = [forme_de_carte(b) for b in liste.split(', ')]
        for m in mots:
            for l in LANGUES:
                if not glose(m, l):
                    erreurs.append(f'{ident} : « {m} » n\'a pas de sens en {l}')
        themes.append({'id': ident, 'titres': titres, 'mots': mots})
        tous += mots

    if erreurs:
        sys.exit('\n'.join(erreurs))
    inutiles = sorted(set(GLOSES) - set(tous))
    if inutiles:
        sys.exit(f'GLOSES pour des mots hors de la liste : {inutiles}')

    sortie = {
        'version': 1,
        'generated': datetime.date.today().isoformat(),
        'description': 'Les essentiels du carnet : le vocabulaire de base par thème, et les sens propres à cette liste.',
        'source': 'Liste du propriétaire ; sens du LOD (data.public.lu, CC0) ou écrits à la main (voir generate_essentiels.py).',
        'themes': themes,
        'gloses': {l: {m: g[LANGUES.index(l)] for m, g in GLOSES.items()} for l in LANGUES},
        'categories': {m: ('EXPR' if est_expression(m) else CATEGORIES[m])
                       for m in sorted(set(tous)) if est_expression(m) or m in CATEGORIES},
        'isoles': ISOLES,
    }
    with open(SORTIE, 'w', encoding='utf-8') as f:
        json.dump(sortie, f, ensure_ascii=False, indent=1)

    distincts = len(set(tous))
    print(f'{len(themes)} thèmes, {len(tous)} emplacements, {distincts} mots distincts, '
          f'{sum(est_expression(m) for m in set(tous))} expressions')
    print('hors du dictionnaire de fréquences :',
          sorted(m for m in set(tous) if m not in dico and not est_expression(m)))
    sans_categorie = sorted(m for m in set(tous) if m not in sortie['categories'] and m not in categories_lod)
    print('sans catégorie (la carte lira la majuscule) :', sans_categorie)


if __name__ == '__main__':
    main()
