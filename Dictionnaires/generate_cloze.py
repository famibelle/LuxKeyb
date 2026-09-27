#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 WUERTLÜCK — génération du jeu de phrases à trous
====================================================

Produit `android_keyboard/app/src/main/assets/luxemburgish_cloze.json`, l'actif
du quatrième jeu de l'application : une phrase luxembourgeoise authentique dont
un mot a été retiré, et quatre propositions dont une seule est celle qu'a écrite
l'auteur.

    cd Dictionnaires
    python generate_cloze.py --strict

Le script est volontairement SÉPARÉ de `LuxembourgishComplet.py` : il ne
reconstruit rien, il **consomme** le dictionnaire et les n-grammes déjà livrés
dans les assets. Il faut donc l'exécuter APRÈS le pipeline, jamais avant — sans
quoi les leurres seraient tirés d'un modèle qui ne correspond plus au corpus.

Trois choix qui décident de la qualité du jeu :

1. **La casse fait office d'étiquetage grammatical.** Le luxembourgeois
   capitalise ses substantifs, et le pipeline élit déjà une casse canonique par
   forme. On s'en sert pour deux choses : ne masquer que des mots porteurs de
   sens (substantifs, ou verbes/adjectifs d'au moins 5 lettres), jamais un
   article ni une préposition — sur « an der ___ » quatre prépositions
   conviendraient aussi bien, la question n'aurait pas de réponse ; et exiger
   que les leurres partagent la classe de casse de la réponse, sinon la
   majuscule désigne la bonne case à elle seule.

2. **Les leurres viennent du contexte n-gramme.** Au moins un des trois doit
   être un mot que le corpus atteste réellement après les mêmes mots : c'est ce
   qui force à lire la phrase entière au lieu de reconnaître la seule
   collocation possible. Les autres complètent depuis la même bande de
   fréquence, pour que la bonne réponse ne soit pas identifiable comme « le
   seul mot courant de la liste ».

3. **Aucun chiffre, aucune parenthèse, aucun guillemet, et tous les mots au
   dictionnaire.** LuxAlign est de la presse : ses phrases sont truffées de
   nombres, d'incises et de citations tronquées qui ne se lisent pas hors de
   leur article. Le filtre est brutal (46 000 phrases retenues sur 186 204) et
   c'est très largement suffisant.

Attribution : les phrases livrées ici sont des extraits de corpus sous licence
Creative Commons — LuxAlign en CC BY-NC 4.0, LETZ en CC BY 4.0. Voir CORPUS.md.
Le jeu affiche la source de chaque phrase, l'écran « À propos » les crédits
complets ; ne pas retirer l'un ni l'autre.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import json
import os
import random
import sys
from bisect import bisect_left
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from LuxembourgishComplet import CORPUS_SOURCES, PATTERN_MOT, _classe_de_casse

try:
    from datasets import load_dataset
    HAS_DATASETS = True
except ImportError:
    HAS_DATASETS = False

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

RACINE_ASSETS = Path(__file__).resolve().parent.parent / \
    "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "luxemburgish_dict.json"
CHEMIN_NGRAMS = RACINE_ASSETS / "luxemburgish_ngrams.json"
CHEMIN_CLOZE = RACINE_ASSETS / "luxemburgish_cloze.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Graine fixe : deux exécutions sur le même corpus doivent produire le même
# fichier, sinon chaque régénération réécrit 300 Ko d'actif pour rien et le
# diff devient illisible.
GRAINE = 20260901

MARQUEUR = "___"
NB_PROPOSITIONS = 4

# Longueur de phrase, en mots. En deçà de 6 le contexte ne suffit pas à
# désigner une réponse ; au-delà de 16 la phrase ne tient plus à l'écran d'un
# téléphone sans que le joueur perde le fil.
MOTS_MIN, MOTS_MAX = 6, 16

# Caractères qui disqualifient une phrase entière. Les chiffres écartent les
# dépêches de résultats et de budgets ; les parenthèses, guillemets et
# deux-points écartent les incises et les citations coupées de leur contexte.
CARACTERES_INTERDITS = set('0123456789()[]{}«»“”„"/\\|<>=+*#@_;:%§€$')

# Bandes de fréquence de la réponse. Un mot vu moins de 20 fois dans 3,1 M
# d'occurrences n'est pas devinable, même en contexte : la borne basse n'est
# pas un réglage de difficulté mais un refus de poser une question sans réponse.
FREQ_MIN = 20
SEUIL_FACILE = 1000
SEUIL_NORMAL = 200

DIFFICULTE_FACILE, DIFFICULTE_NORMALE, DIFFICULTE_DIFFICILE = 1, 2, 3

# Un substantif de 3 lettres se devine mal ; en dessous de 5 lettres, un mot
# minuscule est presque toujours grammatical (« ass », « ginn », « och »).
LONGUEUR_MIN_MAJUSCULE = 3
LONGUEUR_MIN_MINUSCULE = 5

# Au-delà, un mot minuscule très fréquent est un mot-outil déguisé : « kann »,
# « gëtt », « ginn », « soll ». On les laisse au corpus, pas au jeu.
FREQ_MAX_MINUSCULE = 5000

# Un mot majuscule est un nom propre s'il apparaît le plus souvent collé à un
# autre mot majuscule : « Marc Spautz », « Josée Lorsché », « Nuit du Sport ».
# Le signal est fiable en luxembourgeois parce que les mots composés s'écrivent
# soudés (« Justizministère »), donc deux majuscules voisines ne sont
# pratiquement jamais deux substantifs communs. Sans ce filtre, un quart des
# questions difficiles demandait le patronyme d'un député : c'est du trivia, pas
# de la langue.
SEUIL_NOM_PROPRE = 0.50
OCCURRENCES_MIN_NOM_PROPRE = 3

# Écart de longueur toléré entre un leurre et la réponse. Trop large, la
# longueur trahit ; trop étroit, il n'y a plus assez de leurres.
ECART_LONGUEUR = 4

# Combien de questions au maximum partagent la même réponse. Sans plafond,
# « Lëtzebuerg » serait la réponse d'une question sur douze. Trois et non deux :
# les réponses faciles (fréquence ≥ 1 000) ne sont que quelques centaines de
# formes distinctes, et un plafond de deux laissait leur quota inrempli.
MAX_PAR_REPONSE = 3

# Taille de la fenêtre de rang dans laquelle on pioche un leurre de secours,
# quand le contexte n-gramme n'en fournit pas assez.
FENETRE_RANG = 200

# Cible de livraison. 1 600 questions représentent environ 300 Ko et plus de
# quarante parties de 10 questions sans jamais revoir la même phrase.
CIBLE_TOTAL = 1600
REPARTITION = {
    DIFFICULTE_FACILE: 0.35,
    DIFFICULTE_NORMALE: 0.40,
    DIFFICULTE_DIFFICILE: 0.25,
}

# Part de LETZ visée dans le fichier livré, et plancher en deçà duquel on
# prévient. LETZ ne pèse que 6 % des phrases éligibles, mais c'est le seul
# corpus au registre quotidien : sans quota le jeu ne parlerait que de conseils
# communaux et de matchs de football, et en le servant d'abord il étoufferait à
# l'inverse tout le vocabulaire de la vie publique.
PART_LETZ_CIBLE = 0.35
PART_LETZ_MIN = 0.20

LIBELLES_SOURCE = {
    "fredxlpy/LuxAlign": "LuxAlign",
    "fredxlpy/LETZ": "LETZ",
}


def charger_corpus(strict):
    """Charge et dédoublonne les phrases des corpus, comme le pipeline."""
    print("\n📖 CHARGEMENT DES CORPUS")
    print("-" * 45)

    if not HAS_DATASETS:
        print("❌ Bibliothèque 'datasets' non installée (pip install datasets)")
        return None

    vues = set()
    phrases = []
    sources_ok = 0

    for source in CORPUS_SOURCES:
        nom = source["dataset"]
        libelle = LIBELLES_SOURCE.get(nom, nom)
        retenues = 0
        for config in source["configs"]:
            try:
                ds = load_dataset(nom, config)
            except Exception as e:
                print(f"   ❌ {nom} / '{config}' indisponible: {e}")
                continue
            for split in ds.keys():
                for item in ds[split]:
                    texte = item.get(source["champ"])
                    if not texte or not isinstance(texte, str):
                        continue
                    texte = texte.strip()
                    if not texte or texte in vues:
                        continue
                    vues.add(texte)
                    phrases.append((texte, libelle))
                    retenues += 1
        if retenues:
            sources_ok += 1
            print(f"   ✅ {libelle} : {retenues} phrases uniques")
        else:
            print(f"   ⚠️ {libelle} : aucune phrase")

    if sources_ok < len(CORPUS_SOURCES):
        print(f"\n⚠️ Seulement {sources_ok}/{len(CORPUS_SOURCES)} corpus chargés.")
        if strict:
            print("   Mode strict : arrêt. Sans LETZ le jeu perd tout son "
                  "registre quotidien, sans LuxAlign il n'a plus de volume.")
            return None

    print(f"\n📊 {len(phrases)} phrases uniques, {sources_ok}/{len(CORPUS_SOURCES)} sources")
    return phrases


def charger_modele():
    """Lit le dictionnaire et les n-grammes livrés dans les assets."""
    print("\n📚 LECTURE DU MODÈLE LIVRÉ")
    print("-" * 45)

    with open(CHEMIN_DICT, "r", encoding="utf-8") as f:
        brut = json.load(f)
    if not isinstance(brut, list):
        raise ValueError(f"{CHEMIN_DICT.name} doit être un tableau de paires")
    dico = {paire[0]: paire[1] for paire in brut}

    with open(CHEMIN_NGRAMS, "r", encoding="utf-8") as f:
        ngrams = json.load(f)

    print(f"   ✅ {len(dico)} entrées de dictionnaire")
    print(f"   ✅ {len(ngrams)} contextes n-grammes")
    return dico, ngrams


def indexer_par_classe(dico):
    """Range le dictionnaire par classe de casse, trié par fréquence.

    Sert à trouver des leurres « du même rang » que la réponse : même classe de
    casse, fréquence comparable. Retourne, par classe, la liste des mots triés
    par fréquence décroissante et la liste parallèle des fréquences négatives
    (pour une recherche dichotomique croissante).
    """
    par_classe = defaultdict(list)
    for mot, freq in dico.items():
        par_classe[_classe_de_casse(mot)].append((freq, mot))
    index = {}
    for classe, entrees in par_classe.items():
        entrees.sort(key=lambda e: (-e[0], e[1]))
        index[classe] = (
            [mot for _, mot in entrees],
            [-freq for freq, _ in entrees],
        )
    return index


def detecter_noms_propres(phrases):
    """Repère les mots majuscules qui vivent accolés à un autre mot majuscule.

    Le comptage ignore les positions de début de phrase des deux côtés : la
    majuscule y est celle de la phrase et ne dit rien du mot.
    """
    print("\n🏷️  DÉTECTION DES NOMS PROPRES")
    print("-" * 45)

    total = Counter()
    accoles = Counter()
    for texte, _ in phrases:
        mots = [m.group(0) for m in PATTERN_MOT.finditer(texte)]
        majuscules = [
            i > 0 and _classe_de_casse(mot) in ('MAJUSCULE', 'ACRONYME')
            for i, mot in enumerate(mots)
        ]
        for i, mot in enumerate(mots):
            if not majuscules[i]:
                continue
            total[mot] += 1
            voisin = (i > 0 and majuscules[i - 1]) or \
                     (i + 1 < len(mots) and majuscules[i + 1])
            if voisin:
                accoles[mot] += 1

    noms = {
        mot for mot, compte in total.items()
        if compte >= OCCURRENCES_MIN_NOM_PROPRE
        and accoles[mot] / compte >= SEUIL_NOM_PROPRE
    }
    exemples = sorted(noms, key=lambda m: -total[m])[:12]
    print(f"   ✅ {len(noms)} formes écartées, p. ex. {', '.join(exemples)}")
    return noms


def _terminaison_compatible(candidat, reponse):
    """Accord morphologique grossier entre un leurre et une réponse minuscule.

    Il n'y a pas d'étiquetage grammatical dans ce projet, et sans lui la bande
    de fréquence propose volontiers un adjectif (« positiv ») face à trois
    verbes conjugués (« kritt », « wënnt », « verléiert »). La finale suffit à
    trancher l'essentiel : les verbes luxembourgeois se terminent en -t ou -en,
    les adjectifs et adverbes rarement. La règle ne s'applique qu'aux mots
    minuscules — pour les substantifs, exiger la même finale reviendrait à
    exiger le même genre et le même nombre, et il ne resterait plus de leurres.
    """
    return candidat[-1].lower() == reponse[-1].lower()


def mot_masquable(mot, freq):
    """Ce mot fait-il une réponse honnête ?

    Le critère est grammatical avant d'être statistique : la majuscule
    luxembourgeoise désigne un substantif, donc un mot porteur de sens qu'on
    peut demander de retrouver. Un mot minuscule n'est retenu que s'il est assez
    long et pas trop fréquent — soit un verbe ou un adjectif, jamais un article.
    """
    if freq < FREQ_MIN:
        return False
    classe = _classe_de_casse(mot)
    if classe in ('MAJUSCULE', 'ACRONYME'):
        return len(mot) >= LONGUEUR_MIN_MAJUSCULE
    return len(mot) >= LONGUEUR_MIN_MINUSCULE and freq <= FREQ_MAX_MINUSCULE


def difficulte_de(freq):
    if freq >= SEUIL_FACILE:
        return DIFFICULTE_FACILE
    if freq >= SEUIL_NORMAL:
        return DIFFICULTE_NORMALE
    return DIFFICULTE_DIFFICILE


def _compatible(candidat, reponse, interdits, dico, classe_reponse, noms_propres):
    """Un leurre doit être plausible sans être une variante de la réponse."""
    if candidat not in dico:
        return False
    # Un leurre est soumis aux mêmes exigences qu'une réponse : sans cela, le
    # modèle n-grammes propose « de », « um » ou « an » face à un verbe de six
    # lettres, et la bonne case se désigne toute seule.
    if not mot_masquable(candidat, dico[candidat]):
        return False
    if candidat in noms_propres:
        return False
    if classe_reponse == 'minuscule' and not _terminaison_compatible(candidat, reponse):
        return False
    if candidat.lower() in interdits:
        return False
    if _classe_de_casse(candidat) != classe_reponse:
        return False
    if abs(len(candidat) - len(reponse)) > ECART_LONGUEUR:
        return False
    # « Joer » / « Joren », « Land » / « Länner » : la même racine proposée deux
    # fois n'est pas un choix, c'est un piège d'orthographe.
    court = min(len(candidat), len(reponse), 4)
    if court >= 4 and candidat[:court].lower() == reponse[:court].lower():
        return False
    return True


def choisir_leurres(reponse, contextes, mots_phrase, dico, ngrams, index, rng, noms_propres):
    """Trois leurres, dont au moins un attesté dans le même contexte.

    Le premier vivier est le modèle n-grammes : ces mots-là suivent réellement
    les mêmes mots dans le corpus, ils sont donc grammaticalement plausibles à
    l'emplacement du trou. S'il n'en fournit aucun, la question est écartée —
    quatre mots pris au hasard dans la bonne bande de fréquence se départagent
    à l'œil, sans lire la phrase.
    """
    classe = _classe_de_casse(reponse)
    interdits = {m.lower() for m in mots_phrase} | {reponse.lower()}
    leurres = []

    vivier_contexte = []
    for cle in contextes:
        for candidat in ngrams.get(cle, []):
            mot = candidat.get("word", "")
            if _compatible(mot, reponse, interdits, dico, classe, noms_propres):
                vivier_contexte.append(mot)

    for mot in vivier_contexte:
        if mot not in leurres:
            leurres.append(mot)
        if len(leurres) == NB_PROPOSITIONS - 1:
            break

    if not leurres:
        return None

    # Complément par voisinage de rang : le jeu ne doit pas se gagner en
    # repérant « le seul mot que je connais » ou « le seul mot rare ».
    mots_classe, freqs_classe = index.get(classe, ([], []))
    if mots_classe:
        rang = bisect_left(freqs_classe, -dico[reponse])
        debut = max(0, rang - FENETRE_RANG)
        fin = min(len(mots_classe), rang + FENETRE_RANG)
        voisins = mots_classe[debut:fin]
        rng.shuffle(voisins)
        for mot in voisins:
            if len(leurres) == NB_PROPOSITIONS - 1:
                break
            if mot in leurres:
                continue
            if _compatible(mot, reponse, interdits, dico, classe, noms_propres):
                leurres.append(mot)

    if len(leurres) < NB_PROPOSITIONS - 1:
        return None
    return leurres


def construire_questions(phrases, dico, ngrams, index, rng, noms_propres):
    """Parcourt le corpus et fabrique une question par phrase éligible."""
    print("\n✂️  DÉCOUPAGE DES PHRASES À TROUS")
    print("-" * 45)

    formes_connues = {mot.lower() for mot in dico}
    questions = []
    rejets = Counter()

    for texte, libelle in phrases:
        if texte[-1] not in '.!?':
            rejets["ponctuation finale"] += 1
            continue
        if any(ch in CARACTERES_INTERDITS for ch in texte):
            rejets["chiffres ou incises"] += 1
            continue

        tokens = [(m.group(0), m.start(), m.end())
                  for m in PATTERN_MOT.finditer(texte)]
        tokens = [t for t in tokens if len(t[0]) >= 2]
        if not (MOTS_MIN <= len(tokens) <= MOTS_MAX):
            rejets["longueur"] += 1
            continue
        if any(mot.lower() not in formes_connues for mot, _, _ in tokens):
            rejets["mot hors dictionnaire"] += 1
            continue

        mots = [t[0] for t in tokens]

        # Le premier mot est écarté : sa majuscule est celle de la phrase et ne
        # dit rien du mot. Le dernier l'est aussi — un trou final se devine sur
        # la ponctuation plutôt que sur le sens.
        majuscules = [
            j > 0 and _classe_de_casse(mots[j]) in ('MAJUSCULE', 'ACRONYME')
            for j in range(len(mots))
        ]

        emplacements = []
        for i in range(1, len(tokens) - 1):
            mot, debut, fin = tokens[i]
            freq = dico.get(mot)
            if freq is None:      # forme de surface ≠ casse canonique élue
                continue
            if not mot_masquable(mot, freq):
                continue
            if mot in noms_propres:
                continue
            # Voisin majuscule : on est au milieu d'un nom composé (« Marc
            # Spautz », « Nuit du Sport ») même si la forme prise isolément
            # n'est pas assez fréquente pour figurer dans noms_propres.
            if majuscules[i - 1] or (i + 1 < len(mots) and majuscules[i + 1]):
                continue
            emplacements.append((i, mot, debut, fin, freq))

        if not emplacements:
            rejets["aucun mot masquable"] += 1
            continue

        # Une phrase ne donne qu'une question : dix trous dans la même phrase
        # feraient dix fois la même lecture.
        i, mot, debut, fin, freq = rng.choice(emplacements)

        contextes = []
        if i >= 2:
            contextes.append(f"{mots[i - 2].lower()} {mots[i - 1].lower()}")
        contextes.append(mots[i - 1].lower())

        leurres = choisir_leurres(
            mot, contextes, mots, dico, ngrams, index, rng, noms_propres)
        if leurres is None:
            rejets["leurres insuffisants"] += 1
            continue

        questions.append({
            "s": texte[:debut] + MARQUEUR + texte[fin:],
            "a": mot,
            "d": leurres,
            "l": difficulte_de(freq),
            "src": libelle,
        })

    print(f"   ✅ {len(questions)} questions candidates")
    for motif, nombre in rejets.most_common():
        print(f"   ↩️  {nombre:>6} phrases écartées — {motif}")
    return questions


def selectionner(questions, rng):
    """Échantillonne la livraison : difficulté répartie, registres mélangés.

    Trois passes. La première applique un plafond par (difficulté, source) pour
    garantir la part de LETZ ; la deuxième comble ce que la première n'a pas pu
    remplir, sans regarder la source ; la troisième complète jusqu'à la cible
    sans regarder non plus la difficulté. Sans la première, LETZ ne pèse que 6 %
    du vivier et disparaîtrait ; sans la deuxième, une bande de difficulté que
    LETZ ne peut pas alimenter resterait à moitié vide ; sans la troisième, la
    livraison plafonnerait sous sa cible, parce que les réponses faciles
    (fréquence ≥ 1 000) ne comptent que quelques centaines de formes distinctes
    et se heurtent au plafond par réponse bien avant leur quota.
    """
    print("\n⚖️  SÉLECTION DE LA LIVRAISON")
    print("-" * 45)

    rng.shuffle(questions)

    quotas = {
        niveau: round(CIBLE_TOTAL * part)
        for niveau, part in REPARTITION.items()
    }
    plafonds = {}
    for niveau, quota in quotas.items():
        plafonds[(niveau, "LETZ")] = round(quota * PART_LETZ_CIBLE)
        plafonds[(niveau, "LuxAlign")] = quota - plafonds[(niveau, "LETZ")]

    par_reponse = Counter()
    comptes = Counter()
    comptes_source = Counter()
    retenues = []

    def tenter(question, avec_plafond, avec_quota=True):
        niveau = question["l"]
        if avec_quota and comptes[niveau] >= quotas[niveau]:
            return False
        if len(retenues) >= CIBLE_TOTAL:
            return False
        if par_reponse[question["a"]] >= MAX_PAR_REPONSE:
            return False
        cle = (niveau, question["src"])
        if avec_plafond and comptes_source[cle] >= plafonds.get(cle, 0):
            return False
        par_reponse[question["a"]] += 1
        comptes[niveau] += 1
        comptes_source[cle] += 1
        retenues.append(question)
        return True

    restantes = [q for q in questions if not tenter(q, avec_plafond=True)]
    restantes = [q for q in restantes if not tenter(q, avec_plafond=False)]
    for question in restantes:
        if len(retenues) >= CIBLE_TOTAL:
            break
        tenter(question, avec_plafond=False, avec_quota=False)

    rng.shuffle(retenues)
    # Ordre stable et lisible dans le fichier : par difficulté croissante.
    retenues.sort(key=lambda q: q["l"])

    sources = Counter(q["src"] for q in retenues)
    part_letz = sources["LETZ"] / len(retenues) if retenues else 0
    libelles = {1: "facile", 2: "normal", 3: "difficile"}
    for niveau in sorted(quotas):
        detail = ", ".join(
            f"{nom} {comptes_source[(niveau, nom)]}"
            for nom in ("LETZ", "LuxAlign")
        )
        print(f"   📗 {libelles[niveau]:<9} {comptes[niveau]:>5} / {quotas[niveau]}   ({detail})")
    print(f"   🗣️  part LETZ : {part_letz:.1%}")
    if part_letz < PART_LETZ_MIN:
        print(f"   ⚠️ sous le quota de registre quotidien ({PART_LETZ_MIN:.0%})")
    return retenues


def valider(questions, dico):
    """Contrôles qui doivent tenir avant d'écrire quoi que ce soit."""
    print("\n🔎 VALIDATION")
    print("-" * 45)

    erreurs = []
    for index, question in enumerate(questions):
        # Comparaison par mot entier, pas par sous-chaîne : « de » est contenu
        # dans « der » sans y être présent comme mot.
        mots_phrase = {
            m.group(0).lower() for m in PATTERN_MOT.finditer(question["s"])
        }
        if question["s"].count(MARQUEUR) != 1:
            erreurs.append(f"#{index} : {MARQUEUR} absent ou répété")
        if len(question["d"]) != NB_PROPOSITIONS - 1:
            erreurs.append(f"#{index} : {len(question['d'])} leurres")
        if len(set(question["d"]) | {question["a"]}) != NB_PROPOSITIONS:
            erreurs.append(f"#{index} : propositions en doublon")
        if question["a"] not in dico:
            erreurs.append(f"#{index} : réponse « {question['a']} » hors dictionnaire")
        for leurre in question["d"]:
            if leurre not in dico:
                erreurs.append(f"#{index} : leurre « {leurre} » hors dictionnaire")
            if leurre.lower() in mots_phrase:
                erreurs.append(f"#{index} : leurre « {leurre} » déjà dans la phrase")

    if erreurs:
        for erreur in erreurs[:20]:
            print(f"   ❌ {erreur}")
        print(f"   ❌ {len(erreurs)} erreurs au total")
        return False

    print(f"   ✅ {len(questions)} questions valides")
    return True


def sauvegarder(questions):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_CLOZE.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"luxemburgish_cloze_{horodatage}.json"
        copie.write_bytes(CHEMIN_CLOZE.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        # Recopiée dans l'actif pour que l'attribution voyage avec les phrases,
        # y compris si le fichier est lu hors du dépôt.
        "sources": [
            "fredxlpy/LuxAlign — CC BY-NC 4.0 — Philippy, Guo, Klein, Bissyandé (COLING 2025)",
            "fredxlpy/LETZ — CC BY 4.0 — Philippy, Haddadan, Guo (SIGUL 2024)",
        ],
        "items": questions,
    }
    with open(CHEMIN_CLOZE, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False, separators=(",", ":"))

    taille = CHEMIN_CLOZE.stat().st_size
    print(f"   ✅ {CHEMIN_CLOZE.name} — {len(questions)} questions, "
          f"{taille / 1024:.0f} Ko")


def main():
    strict = "--strict" in sys.argv

    print("🇱🇺 WUERTLÜCK — GÉNÉRATION DU JEU DE PHRASES À TROUS 🇱🇺")
    print("=" * 70)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    rng = random.Random(GRAINE)

    try:
        dico, ngrams = charger_modele()
    except Exception as e:
        print(f"\n❌ Modèle illisible : {e}")
        print("   Lancez d'abord `python LuxembourgishComplet.py --strict`.")
        return 1

    phrases = charger_corpus(strict)
    if phrases is None:
        return 1

    noms_propres = detecter_noms_propres(phrases)
    index = indexer_par_classe(dico)
    questions = construire_questions(
        phrases, dico, ngrams, index, rng, noms_propres)
    if not questions:
        print("\n❌ Aucune question produite, rien n'est écrit.")
        return 1

    livraison = selectionner(questions, rng)
    if not valider(livraison, dico):
        print("\n❌ Validation échouée, rien n'est écrit.")
        return 1

    if strict and len(livraison) < CIBLE_TOTAL * 0.9:
        print(f"\n❌ Mode strict : {len(livraison)} questions seulement, "
              f"attendu ~{CIBLE_TOTAL}.")
        return 1

    sauvegarder(livraison)
    print("\n🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
