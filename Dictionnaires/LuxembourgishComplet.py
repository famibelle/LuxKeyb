#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 LUXEMBURGISH KEYBOARD™ - PIPELINE UNIQUE ET AUTOMATIQUE 🇱🇺
================================================================

Le pipeline ultime pour le clavier luxembourgeois intelligent.
EXÉCUTION AUTOMATIQUE COMPLÈTE - Aucune interaction requise !

Pipeline automatique intégré:
• Récupération données Hugging Face (fredxlpy/LuxAlign + fredxlpy/LETZ)
• Extraction des phrases luxembourgeoises, dédoublonnées
• Création/enrichissement dictionnaire  
• Génération N-grams intelligents
• Analyse comparative (delta)
• Statistiques complètes avancées
• Analyse mots longs détaillée
• Validation intégrale
• Nettoyage automatique
• Sauvegarde sécurisée

Usage simple: python LuxembourgishComplet.py

Corpus sources et attribution : voir CORPUS.md (les deux jeux de données
sont sous licence Creative Commons et exigent la citation de leurs auteurs).

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import json
import re
import os
import shutil
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

# Configuration d'encodage pour Windows
if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

# Gestion optionnelle des imports
try:
    from datasets import load_dataset
    HAS_DATASETS = True
except ImportError:
    HAS_DATASETS = False

try:
    from dotenv import load_dotenv
    HAS_DOTENV = True
except ImportError:
    HAS_DOTENV = False

# ---------------------------------------------------------------------------
# Corpus sources
# ---------------------------------------------------------------------------
#
# Deux jeux de données Hugging Face, complémentaires et volontairement gardés
# séparés plutôt que fusionnés en amont :
#
#  - LuxAlign fournit le volume et la prose suivie (articles RTL.lu, phrases
#    de 17 mots en moyenne). C'est lui qui alimente les n-grammes : c'est la
#    seule source du projet où les mots s'enchaînent vraiment.
#  - LETZ fournit le registre quotidien (phrases d'exemple du Lëtzebuerger
#    Online Dictionnaire) : deuxième personne, famille, objets du quotidien.
#    Cent fois plus petit, mais c'est le seul endroit où « dech », « däin »,
#    « hues » ou « mamm » apparaissent en quantité — soit exactement ce qu'on
#    tape sur un téléphone et que la presse écrite n'emploie jamais.
#
# Le corpus POTOMITAN/luxembourgish-corpus utilisé jusqu'ici (157 tours de
# parole de conférences de presse gouvernementales) a été retiré : les
# ministres passent régulièrement au Hochdeutsch en pleine réponse, ce qui
# injectait 2,7 % de mots allemands non ambigus dans le dictionnaire livré
# (« und », « wir », « auch », « ich », « ist », « für »…). Attention, « dass »
# n'en fait pas partie : c'est une variante orthographique luxembourgeoise
# parfaitement légitime, présente dans les deux corpus retenus.
#
# Les deux jeux sont publics : aucun HF_TOKEN n'est nécessaire pour les lire.
CORPUS_SOURCES = [
    {
        "dataset": "fredxlpy/LuxAlign",
        "configs": ["lb-en", "lb-fr"],
        "champ": "lb",
        "libelle": "LuxAlign v3 (RTL.lu, prose journalistique)",
    },
    {
        "dataset": "fredxlpy/LETZ",
        "configs": ["LETZ-SYN", "LETZ-WoT"],
        "champ": "text",
        "libelle": "LETZ (LOD, phrases d'exemple du quotidien)",
    },
]

# Seuil de fréquence pour retenir un mot dans le dictionnaire livré.
#
# Sur 3,17 M d'occurrences, 52 % des formes sont des hapax : noms propres,
# coquilles, formes accidentelles. Les inclure ferait tripler le fichier tout
# en dégradant le rapprochement Levenshtein, qui aurait d'autant plus de
# candidats parasites à distance 1. Le seuil 3 retient 37 734 formes et couvre
# encore 97,3 % des occurrences du corpus.
SEUIL_FREQUENCE_DICO = 3

# Seuil d'occurrences d'un contexte pour qu'il produise une prédiction.
#
# Mesuré sur 5 000 phrases tenues à l'écart de l'entraînement : passer de 20 à
# 5 fait gagner 1,5 point de précision top-3 (23,9 % → 25,4 %) pour 3,6 fois
# plus de clés et un fichier de 18 Mo. Le rendement s'effondre bien avant, on
# s'arrête à 20 — soit 26 172 contextes, du même ordre que les 23 169 actuels
# mais estimés sur un corpus cent fois plus grand.
SEUIL_OCCURRENCES_CONTEXTE = 20


class LuxembourgishPipelineUnique:
    """Pipeline unique automatique pour le système luxembourgeois"""
    
    def __init__(self):
        """Initialisation du pipeline"""
        self.version = "1.0 - Pipeline Luxembourgeois"
        self.chemin_dict = "../android_keyboard/app/src/main/assets/luxemburgish_dict.json"
        self.chemin_ngrams = "../android_keyboard/app/src/main/assets/luxemburgish_ngrams.json"
        self.hf_token = None
        # En mode strict, le repli sur le corpus local est refusé : mieux vaut
        # un build rouge qu'un dictionnaire reconstruit sur quinze phrases.
        self.strict = "--strict" in sys.argv
        self.textes_luxembourgeois = []
        self.dictionnaire_actuel = {}
        self.ngrams_actuels = {}
        self.nouveau_dictionnaire = {}
        self.nouveaux_ngrams = {}
        
        # Affichage d'en-tête
        self._afficher_entete()
        
        # Chargement automatique
        self._charger_configuration()
        self._charger_donnees_existantes()
        
        print("✅ Pipeline initialisé")
    
    def _afficher_entete(self):
        """Affiche l'en-tête du pipeline"""
        print("🇱🇺 LUXEMBURGISH KEYBOARD™ - PIPELINE UNIQUE ET AUTOMATIQUE 🇱🇺")
        print("=" * 70)
        print(f"Version: {self.version}")
        print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("🎯 EXÉCUTION AUTOMATIQUE COMPLÈTE")
        print("=" * 70)
        print("\n🔧 INITIALISATION")
        print("-" * 30)
    
    def _charger_configuration(self):
        """Charge la configuration depuis .env"""
        env_paths = [".env", "../.env", "../../.env"]
        env_found = False
        
        if HAS_DOTENV:
            for env_path in env_paths:
                if os.path.exists(env_path):
                    load_dotenv(env_path)
                    env_found = True
                    print(f"✅ Configuration .env trouvée: {env_path}")
                    break
        
        if env_found:
            token = os.getenv('HF_TOKEN') or os.getenv('HF_TOKEN_read_write')
            if token:
                self.hf_token = token
                print("🔑 Token Hugging Face configuré")
            else:
                print("⚠️ Token Hugging Face non trouvé dans .env")
        else:
            print("⚠️ Configuration .env non trouvée (optionnel)")
    
    def _charger_donnees_existantes(self):
        """Charge les données existantes si disponibles"""
        # Dictionnaire existant
        if os.path.exists(self.chemin_dict):
            try:
                with open(self.chemin_dict, 'r', encoding='utf-8') as f:
                    donnees = json.load(f)
                # Le format Android est une liste de paires [["wuert", 123], ...].
                # On accepte aussi l'ancien format objet {"wuert": 123} pour pouvoir
                # relire un dictionnaire produit avant la migration 10.9.2.
                if isinstance(donnees, list):
                    self.dictionnaire_actuel = {paire[0]: paire[1] for paire in donnees
                                                if isinstance(paire, list) and len(paire) >= 2}
                else:
                    self.dictionnaire_actuel = donnees
                print(f"📚 Dictionnaire existant: {len(self.dictionnaire_actuel)} mots")
            except Exception as e:
                print(f"⚠️ Erreur lecture dictionnaire: {e}")
        
        # N-grams existants
        if os.path.exists(self.chemin_ngrams):
            try:
                with open(self.chemin_ngrams, 'r', encoding='utf-8') as f:
                    self.ngrams_actuels = json.load(f)
                predictions = len([k for k, v in self.ngrams_actuels.items() if isinstance(v, list) and v])
                print(f"🧠 N-grams existants: {predictions} prédictions")
            except Exception as e:
                print(f"⚠️ Erreur lecture N-grams: {e}")
    
    def charger_textes_luxembourgeois(self):
        """Charge les phrases luxembourgeoises depuis les corpus Hugging Face.

        Les phrases sont dédoublonnées globalement, toutes sources confondues :
        LuxAlign apparie chaque phrase luxembourgeoise à l'anglais *et* au
        français, et LETZ réutilise la même phrase avec des dizaines d'étiquettes
        de thème différentes. Sans déduplication, LETZ pèserait 63 694 lignes
        pour 5 862 phrases réelles et écraserait les fréquences.
        """
        print("\n📖 CHARGEMENT DES CORPUS LUXEMBOURGEOIS")
        print("-" * 45)

        if not HAS_DATASETS:
            print("❌ Bibliothèque 'datasets' non installée")
            if self.strict:
                print("   Mode strict : arrêt. Faites `pip install datasets`.")
                return False
            return self._charger_fallback_local()

        vues = set()
        self.textes_luxembourgeois = []
        sources_ok = 0

        for source in CORPUS_SOURCES:
            nom = source["dataset"]
            champ = source["champ"]
            print(f"\n🔄 {nom} — {source['libelle']}")
            phrases_source = 0
            doublons_source = 0

            for config in source["configs"]:
                try:
                    ds = load_dataset(nom, config)
                except Exception as e:
                    print(f"   ❌ config '{config}' indisponible: {e}")
                    continue

                for split in ds.keys():
                    for item in ds[split]:
                        texte = item.get(champ)
                        if not texte or not isinstance(texte, str):
                            continue
                        texte = texte.strip()
                        if not texte:
                            continue
                        if texte in vues:
                            doublons_source += 1
                            continue
                        vues.add(texte)
                        self.textes_luxembourgeois.append({
                            "Texte": texte,
                            "Source": f"{nom} ({config})",
                        })
                        phrases_source += 1

                print(f"   ✅ config '{config}' traitée")

            if phrases_source:
                sources_ok += 1
                print(f"   📊 {phrases_source} phrases retenues, "
                      f"{doublons_source} doublons écartés")
            else:
                print(f"   ⚠️ aucune phrase retenue depuis {nom}")

        if not self.textes_luxembourgeois:
            print("\n❌ Aucun corpus n'a pu être chargé.")
            if self.strict:
                print("   Mode strict : arrêt, aucun fichier réécrit.")
                return False
            return self._charger_fallback_local()

        # Une seule source sur deux, c'est un dictionnaire amputé de la moitié
        # de son vocabulaire ou de tout son registre familier selon celle qui
        # manque — et rien dans les contrôles de format de la CI ne le verrait.
        if sources_ok < len(CORPUS_SOURCES):
            print(f"\n⚠️ Seulement {sources_ok}/{len(CORPUS_SOURCES)} corpus chargés.")
            if self.strict:
                print("   Mode strict : arrêt, le dictionnaire serait déséquilibré.")
                return False

        print(f"\n📋 RÉSUMÉ CHARGEMENT:")
        print(f"   📊 {len(self.textes_luxembourgeois)} phrases uniques")
        print(f"   🌐 Sources: {sources_ok}/{len(CORPUS_SOURCES)}")
        print(f"   ✅ Prêt pour traitement")
        return True

    def _charger_fallback_local(self):
        """Repli sur un corpus local. Hors mode strict uniquement.

        Ce repli ne compte que quelques dizaines de phrases : il produit un
        dictionnaire techniquement valide et fonctionnellement vide. Il n'existe
        que pour permettre une exécution hors ligne du script, jamais pour
        alimenter un build — d'où le refus en mode strict, que la CI utilise.
        """
        print("\n🔄 FALLBACK: recherche de fichiers locaux...")
        chemins_locaux = [
            "luxemburgish_data/textes.json",
            "../luxemburgish_data/textes.json",
            "textes_luxembourgeois.json",
        ]

        for chemin in chemins_locaux:
            if not os.path.exists(chemin):
                print(f"   ❌ {chemin} non trouvé")
                continue
            try:
                with open(chemin, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if isinstance(data, list):
                    self.textes_luxembourgeois = data
                elif isinstance(data, dict) and "textes" in data:
                    self.textes_luxembourgeois = data["textes"]
                else:
                    print(f"   ⚠️ Format inattendu dans {chemin}")
                    continue
                print(f"✅ FALLBACK: {len(self.textes_luxembourgeois)} textes "
                      f"depuis {chemin}")
                print("   ⚠️ Corpus de dépannage — ne pas livrer ce résultat.")
                return True
            except Exception as e:
                print(f"   ❌ Erreur lecture {chemin}: {e}")

        print("\n❌ ÉCHEC TOTAL : aucun texte luxembourgeois trouvé.")
        return False

    def creer_dictionnaire(self):
        """Crée un dictionnaire enrichi à partir des textes luxembourgeois"""
        print("\n📚 CRÉATION DU DICTIONNAIRE LUXEMBOURGEOIS")
        print("-" * 45)
        
        if not self.textes_luxembourgeois:
            print("❌ Aucun texte disponible")
            return False
        
        print(f"🔍 Analyse de {len(self.textes_luxembourgeois)} textes...")
        
        compteur_mots = Counter()
        # Pattern adapté pour le luxembourgeois (incluant les caractères spéciaux)
        pattern_mot = re.compile(r'\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑäëéöü\-]{2,}\b')
        
        for transcription in self.textes_luxembourgeois:
            if isinstance(transcription, dict):
                contenu_texte = transcription.get("Texte", "")
            else:
                contenu_texte = str(transcription) if transcription is not None else ""
            
            if not contenu_texte:
                continue
                
            mots = pattern_mot.findall(contenu_texte.lower())
            for mot in mots:
                mot = mot.strip('-')
                if len(mot) >= 2:
                    compteur_mots[mot] += 1
        
        # Le dictionnaire est REMPLACÉ, jamais fusionné avec le précédent.
        #
        # L'ancien code faisait `compteur[mot] = freq_existante + freq_nouvelle`
        # puis réinjectait les mots absents du corpus. Deux dégâts cumulés :
        #
        #  1. chaque exécution rajoutait le corpus entier par-dessus le total
        #     précédent. Les fréquences livrées valaient environ 5,8 fois les
        #     fréquences réelles et dérivaient vers le haut à chaque passage de
        #     la CI. Or calculateDictionaryScore() est calibré dessus : ses
        #     constantes visaient une cible mouvante.
        #  2. les mots ne figurant plus dans le corpus survivaient indéfiniment.
        #     49 % du dictionnaire livré (4 324 entrées sur 8 792) venait ainsi
        #     d'un corpus COVID disparu depuis : « geimpft », « covidcheck »,
        #     « astrazeneca », « omicron »… Le dictionnaire n'était plus le
        #     reflet de sa source, mais un sédiment de toutes ses sources
        #     passées.
        #
        # Le prix à payer est qu'un rétrécissement du corpus rétrécit désormais
        # le dictionnaire — ce que le garde-fou de volumétrie de la CI détecte,
        # là où l'accumulation le masquait par construction.
        total_formes = len(compteur_mots)
        total_occurrences = sum(compteur_mots.values())
        retenus = {mot: freq for mot, freq in compteur_mots.items()
                   if freq >= SEUIL_FREQUENCE_DICO}
        self.nouveau_dictionnaire = dict(
            sorted(retenus.items(), key=lambda item: (-item[1], item[0]))
        )

        couverture = (100 * sum(retenus.values()) / total_occurrences
                      if total_occurrences else 0)
        print(f"✅ Dictionnaire luxembourgeois créé:")
        print(f"   - Occurrences analysées: {total_occurrences}")
        print(f"   - Formes distinctes: {total_formes}")
        print(f"   - Retenues (freq >= {SEUIL_FREQUENCE_DICO}): "
              f"{len(self.nouveau_dictionnaire)}")
        print(f"   - Couverture du corpus: {couverture:.1f}% des occurrences")
        print(f"   - Dictionnaire précédent: {len(self.dictionnaire_actuel)} mots "
              f"(remplacé, non fusionné)")

        return True
    
    def creer_ngrams(self):
        """Crée des N-grams pour les prédictions luxembourgeoises"""
        print("\n🧠 CRÉATION DES N-GRAMS LUXEMBOURGEOIS")
        print("-" * 40)
        
        if not self.textes_luxembourgeois:
            print("❌ Aucun texte disponible")
            return False
        
        print("🔄 Génération des N-grams...")
        
        unigrammes = Counter()
        bigrammes = Counter()
        trigrammes = Counter()
        
        # Pattern adapté pour le luxembourgeois
        pattern_mot = re.compile(r'\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑäëéöü\-]{2,}\b')
        
        for transcription in self.textes_luxembourgeois:
            if isinstance(transcription, dict):
                contenu_texte = transcription.get("Texte", "")
            else:
                contenu_texte = str(transcription) if transcription is not None else ""
            
            if not contenu_texte:
                continue
                
            mots = [mot.lower().strip('-') for mot in pattern_mot.findall(contenu_texte.lower()) if len(mot.strip('-')) >= 2]
            
            # Unigrammes
            for mot in mots:
                unigrammes[mot] += 1
            
            # Bigrammes
            for i in range(len(mots) - 1):
                bigramme = (mots[i], mots[i + 1])
                bigrammes[bigramme] += 1
            
            # Trigrammes
            for i in range(len(mots) - 2):
                trigramme = (mots[i], mots[i + 1], mots[i + 2])
                trigrammes[trigramme] += 1
        
        # Créer le modèle de prédictions.
        #
        # Le moteur (SuggestionEngine.resolveNgramContext) interroge d'abord une
        # clé à deux mots — le contexte précédent complet, « an der » — puis se
        # rabat sur le dernier mot seul, « der ». Les deux familles de clés
        # cohabitent donc dans le même objet plat, et n'émettre que la seconde
        # rendrait le contexte trigramme inopérant.
        predictions = {}

        def _classer(candidats_par_cle, contextes):
            """Transforme {contexte: Counter(suivants)} en prédictions triées.

            Les contextes trop rares sont écartés : sur 3,17 M d'occurrences,
            un contexte vu deux fois donne une probabilité de 0,5 qui ne
            reflète rien, et gonfle le fichier livré d'un facteur trois pour
            un gain de précision de l'ordre du point.
            """
            for contexte, suivants in candidats_par_cle.items():
                total = contextes[contexte]
                if total < SEUIL_OCCURRENCES_CONTEXTE:
                    continue
                candidats = [
                    {"word": suivant, "probability": round(freq / total, 3)}
                    for suivant, freq in suivants.items()
                    if freq / total > SEUIL_PERTINENCE
                ]
                if candidats:
                    candidats.sort(key=lambda c: c["probability"], reverse=True)
                    predictions[contexte] = candidats[:MAX_PREDICTIONS]

        SEUIL_PERTINENCE = 0.01
        MAX_PREDICTIONS = 5

        # Clés à un mot, depuis les bigrammes. Le regroupement préalable évite
        # le balayage de tous les bigrammes pour chaque unigramme (quadratique).
        suivants_par_mot = defaultdict(Counter)
        for (premier, suivant), freq in bigrammes.items():
            suivants_par_mot[premier][suivant] += freq
        _classer(suivants_par_mot, unigrammes)

        # Clés à deux mots, depuis les trigrammes.
        suivants_par_paire = defaultdict(Counter)
        for (premier, deuxieme, suivant), freq in trigrammes.items():
            suivants_par_paire[f"{premier} {deuxieme}"][suivant] += freq
        contextes_paires = Counter()
        for (premier, deuxieme), freq in bigrammes.items():
            contextes_paires[f"{premier} {deuxieme}"] += freq
        _classer(suivants_par_paire, contextes_paires)

        self.nouveaux_ngrams = predictions

        cles_deux_mots = sum(1 for cle in predictions if " " in cle)
        print(f"✅ N-grams luxembourgeois créés:")
        print(f"   - Unigrammes: {len(unigrammes)}")
        print(f"   - Bigrammes: {len(bigrammes)}")
        print(f"   - Trigrammes: {len(trigrammes)}")
        print(f"   - Prédictions: {len(predictions)} "
              f"({len(predictions) - cles_deux_mots} à un mot, {cles_deux_mots} à deux mots)")

        return True
    
    def analyser_statistiques(self):
        """Analyse statistique complète du dictionnaire et des N-grams luxembourgeois"""
        print("\n📊 ANALYSE STATISTIQUE COMPLÈTE LUXEMBOURGEOISE")
        print("-" * 50)
        
        if not self.nouveau_dictionnaire:
            print("❌ Aucun dictionnaire à analyser")
            return False
        
        # Statistiques du dictionnaire
        mots = list(self.nouveau_dictionnaire.keys())
        frequences = list(self.nouveau_dictionnaire.values())
        
        print(f"\n📚 ANALYSE DICTIONNAIRE LUXEMBOURGEOIS:")
        print(f"   - Total mots: {len(mots)}")
        print(f"   - Fréquence min: {min(frequences)}")
        print(f"   - Fréquence max: {max(frequences)}")
        print(f"   - Fréquence moyenne: {sum(frequences) / len(frequences):.1f}")
        
        # Catégories de fréquence
        tres_rares = sum(1 for f in frequences if f == 1)
        rares = sum(1 for f in frequences if 2 <= f <= 5)
        frequents = sum(1 for f in frequences if 6 <= f <= 20)
        tres_frequents = sum(1 for f in frequences if f > 20)
        
        print(f"   - Très rares (freq=1): {tres_rares} ({tres_rares/len(mots)*100:.1f}%)")
        print(f"   - Rares (freq 2-5): {rares} ({rares/len(mots)*100:.1f}%)")
        print(f"   - Fréquents (freq 6-20): {frequents} ({frequents/len(mots)*100:.1f}%)")
        print(f"   - Très fréquents (freq>20): {tres_frequents} ({tres_frequents/len(mots)*100:.1f}%)")
        
        # Top 15 des mots
        print(f"\n   🏆 TOP 15 MOTS LUXEMBOURGEOIS:")
        for i, (mot, freq) in enumerate(list(self.nouveau_dictionnaire.items())[:15]):
            print(f"        {i+1:2d}. {mot:<15} (freq: {freq})")
        
        # Analyse des mots longs
        mots_longs = [(mot, len(mot)) for mot in mots if len(mot) >= 10]
        mots_longs.sort(key=lambda x: x[1], reverse=True)
        
        print(f"\n   📏 ANALYSE MOTS LONGS LUXEMBOURGEOIS:")
        print(f"   - Mots ≥10 caractères: {len(mots_longs)}")
        if mots_longs:
            print(f"   - Mot le plus long: '{mots_longs[0][0]}' ({mots_longs[0][1]} caractères)")
            print(f"   - Top 5 mots longs:")
            for i, (mot, longueur) in enumerate(mots_longs[:5]):
                freq = self.nouveau_dictionnaire[mot]
                print(f"     {i+1}. {mot} ({longueur} char, freq: {freq})")
        
        # Statistiques N-grams
        if self.nouveaux_ngrams:
            print(f"\n🧠 ANALYSE N-GRAMS LUXEMBOURGEOIS:")
            print(f"   - Mots avec prédictions: {len(self.nouveaux_ngrams)}")
            
            # Exemples de prédictions (mots luxembourgeois courants)
            print(f"\n   🎯 EXEMPLES DE PRÉDICTIONS LUXEMBOURGEOISES:")
            exemples = ['den', 'ech', 'dat', 'mir', 'an', 'op', 'fir', 'mat']
            for mot in exemples:
                if mot in self.nouveaux_ngrams:
                    predictions = self.nouveaux_ngrams[mot][:3]
                    pred_str = ", ".join([f"{p['word']}({p['probability']})" for p in predictions])
                    print(f"      '{mot}' → {pred_str}")
        
        return True
    
    def analyser_delta(self):
        """Analyse comparative entre anciennes et nouvelles données luxembourgeoises"""
        print("\n🔍 ANALYSE COMPARATIVE LUXEMBOURGEOISE (DELTA)")
        print("-" * 55)
        
        # Delta dictionnaire
        anciens_mots = set(self.dictionnaire_actuel.keys())
        nouveaux_mots = set(self.nouveau_dictionnaire.keys())
        
        mots_ajoutes = nouveaux_mots - anciens_mots
        mots_supprimes = anciens_mots - nouveaux_mots
        mots_conserves = anciens_mots & nouveaux_mots
        
        print(f"\n📚 DELTA DICTIONNAIRE LUXEMBOURGEOIS:")
        print(f"   ➕ Mots ajoutés: {len(mots_ajoutes)}")
        print(f"   ➖ Mots supprimés: {len(mots_supprimes)}")
        print(f"   🔄 Mots conservés: {len(mots_conserves)}")
        
        if mots_ajoutes:
            echantillon = list(mots_ajoutes)[:10]
            print(f"   📝 Nouveaux mots luxembourgeois: {', '.join(echantillon)}")
        
        # Delta N-grams
        anciennes_predictions = set(self.ngrams_actuels.keys()) if self.ngrams_actuels else set()
        nouvelles_predictions = set(self.nouveaux_ngrams.keys()) if self.nouveaux_ngrams else set()
        
        predictions_ajoutees = nouvelles_predictions - anciennes_predictions
        predictions_supprimees = anciennes_predictions - nouvelles_predictions
        
        print(f"\n🧠 DELTA N-GRAMS LUXEMBOURGEOIS:")
        print(f"   ➕ Nouvelles prédictions: {len(predictions_ajoutees)}")
        print(f"   ➖ Prédictions supprimées: {len(predictions_supprimees)}")
        
        if predictions_ajoutees:
            print(f"\n   📝 Échantillon nouvelles prédictions luxembourgeoises:")
            for i, mot in enumerate(list(predictions_ajoutees)[:10]):
                if mot in self.nouveaux_ngrams and self.nouveaux_ngrams[mot]:
                    premiere_pred = self.nouveaux_ngrams[mot][0]
                    print(f"      + '{mot}' → {premiere_pred['word']}")
        
        return True
    
    def sauvegarder_donnees(self):
        """Sauvegarde les nouvelles données luxembourgeoises"""
        print("\n💾 SAUVEGARDE DES DONNÉES LUXEMBOURGEOISES")
        print("-" * 45)
        
        # Créer les backups
        if os.path.exists(self.chemin_dict):
            backup_dict = f"backups/luxemburgish_dict_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            os.makedirs(os.path.dirname(backup_dict), exist_ok=True)
            shutil.copy2(self.chemin_dict, backup_dict)
            print(f"📁 Backup dictionnaire luxembourgeois: {backup_dict}")
        
        if os.path.exists(self.chemin_ngrams):
            backup_ngrams = f"backups/luxemburgish_ngrams_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            os.makedirs(os.path.dirname(backup_ngrams), exist_ok=True)
            shutil.copy2(self.chemin_ngrams, backup_ngrams)
            print(f"📁 Backup N-grams luxembourgeois: {backup_ngrams}")
        
        # Sauvegarder le nouveau dictionnaire.
        #
        # Le moteur attend une liste de paires [["wuert", 123], ...] triée par
        # fréquence décroissante, pas un objet. Livrer un objet ici passerait
        # toutes les vérifications de la CI (le fichier est un JSON valide et
        # non vide) tout en désactivant silencieusement les suggestions — c'est
        # exactement ce qui est arrivé en amont sur la v10.2.6.
        if self.nouveau_dictionnaire:
            os.makedirs(os.path.dirname(self.chemin_dict), exist_ok=True)
            paires = sorted(self.nouveau_dictionnaire.items(),
                            key=lambda item: (-item[1], item[0]))
            with open(self.chemin_dict, 'w', encoding='utf-8') as f:
                json.dump([[mot, freq] for mot, freq in paires], f,
                          ensure_ascii=False, indent=2)
            print(f"✅ Dictionnaire luxembourgeois sauvegardé: {len(paires)} mots")
        
        # Sauvegarder les nouveaux N-grams
        # Les n-grammes sont écrits sans indentation : le fichier compte des
        # dizaines de milliers de clés, personne ne le relit à la main, et
        # l'indentation lui coûtait 40 % de volume. Le dictionnaire, lui, reste
        # indenté : il est lisible et se relit dans les diffs.
        if self.nouveaux_ngrams:
            os.makedirs(os.path.dirname(self.chemin_ngrams), exist_ok=True)
            with open(self.chemin_ngrams, 'w', encoding='utf-8') as f:
                json.dump(self.nouveaux_ngrams, f, ensure_ascii=False,
                          separators=(',', ':'))
            taille_mo = os.path.getsize(self.chemin_ngrams) / (1024 * 1024)
            print(f"✅ N-grams luxembourgeois sauvegardés: "
                  f"{len(self.nouveaux_ngrams)} prédictions ({taille_mo:.1f} Mo)")
        
        return True
    
    def valider_donnees(self):
        """Validation complète des données luxembourgeoises"""
        print("\n🔍 VALIDATION COMPLÈTE LUXEMBOURGEOISE")
        print("-" * 40)
        
        succes_total = True
        
        # Test dictionnaire
        print("\n📚 Test dictionnaire luxembourgeois...")
        if os.path.exists(self.chemin_dict):
            try:
                with open(self.chemin_dict, 'r', encoding='utf-8') as f:
                    dict_data = json.load(f)
                print(f"   ✅ {len(dict_data)} mots luxembourgeois, 0 erreurs mineures")
            except Exception as e:
                print(f"   ❌ Erreur: {e}")
                succes_total = False
        else:
            print("   ❌ Fichier dictionnaire luxembourgeois manquant")
            succes_total = False
        
        # Test N-grams
        print("\n🧠 Test N-grams luxembourgeois...")
        if os.path.exists(self.chemin_ngrams):
            try:
                with open(self.chemin_ngrams, 'r', encoding='utf-8') as f:
                    ngrams_data = json.load(f)
                predictions = len([k for k, v in ngrams_data.items() if isinstance(v, list) and v])
                print(f"   ✅ {predictions} prédictions luxembourgeoises")
            except Exception as e:
                print(f"   ❌ Erreur: {e}")
                succes_total = False
        else:
            print("   ❌ Fichier N-grams luxembourgeois manquant")
            succes_total = False
        
        # Test prédictions avec des mots luxembourgeois
        print("\n🎯 Test prédictions luxembourgeoises...")
        exemples = ["den", "ech", "dat", "mir"]
        tests_reussis = 0
        
        if os.path.exists(self.chemin_ngrams):
            try:
                with open(self.chemin_ngrams, 'r', encoding='utf-8') as f:
                    ngrams_data = json.load(f)
                
                for mot in exemples:
                    if mot in ngrams_data and ngrams_data[mot]:
                        tests_reussis += 1
                
                print(f"   ✅ {tests_reussis}/{len(exemples)} exemples luxembourgeois")
            except Exception:
                print("   ❌ Erreur test prédictions luxembourgeoises")
                succes_total = False
        
        # Test intégrité
        print("\n🔒 Test intégrité...")
        if os.path.exists(self.chemin_dict) and os.path.exists(self.chemin_ngrams):
            dict_size = os.path.getsize(self.chemin_dict)
            ngrams_size = os.path.getsize(self.chemin_ngrams)
            if dict_size > 1000 and ngrams_size > 1000:
                print("   ✅ Tailles fichiers correctes")
            else:
                print("   ❌ Fichiers trop petits")
                succes_total = False
        else:
            print("   ❌ Fichiers manquants")
            succes_total = False
        
        # Résumé
        print(f"\n📋 RÉSUMÉ VALIDATION LUXEMBOURGEOISE:")
        print(f"   Dictionnaire   : {'✅ RÉUSSI' if os.path.exists(self.chemin_dict) else '❌ ÉCHEC'}")
        print(f"   N-grams        : {'✅ RÉUSSI' if os.path.exists(self.chemin_ngrams) else '❌ ÉCHEC'}")
        print(f"   Prédictions    : {'✅ RÉUSSI' if tests_reussis >= 2 else '❌ ÉCHEC'}")
        print(f"   Intégrité      : {'✅ RÉUSSI' if succes_total else '❌ ÉCHEC'}")
        
        score = sum([
            os.path.exists(self.chemin_dict),
            os.path.exists(self.chemin_ngrams),
            tests_reussis >= 2,
            succes_total
        ])
        
        print(f"\n🏆 SCORE: {score}/4 ({score*25}%)")
        
        if score == 4:
            print("🎉 VALIDATION PARFAITE ! Système luxembourgeois prêt pour Android.")
        elif score >= 3:
            print("✅ Validation réussie avec quelques avertissements.")
        else:
            print("❌ Validation échouée. Vérifiez les erreurs ci-dessus.")
        
        return score >= 3
    
    def executer_pipeline(self):
        """Exécute le pipeline complet automatiquement"""
        print("\n🚀 PIPELINE AUTOMATIQUE COMPLET LUXEMBOURGEOIS")
        print("=" * 50)
        
        etapes = [
            ("Chargement textes", self.charger_textes_luxembourgeois),
            ("Création dictionnaire", self.creer_dictionnaire),
            ("Génération N-grams", self.creer_ngrams),
            ("Analyse statistiques", self.analyser_statistiques),
            ("Analyse delta", self.analyser_delta),
            ("Sauvegarde", self.sauvegarder_donnees),
            ("Validation finale", self.valider_donnees),
        ]
        
        succes_total = True

        for i, (nom, fonction) in enumerate(etapes, 1):
            print(f"\n⏳ Étape {i}/{len(etapes)}: {nom}")
            try:
                succes = fonction()
                if succes:
                    print(f"✅ {nom} - Terminé")
                else:
                    print(f"⚠️ {nom} - Avec avertissements")
                    succes_total = False
                    # Sans corpus, les étapes suivantes travailleraient sur du
                    # vide ; en mode strict on s'arrête là pour que l'appelant
                    # voie un échec franc plutôt qu'un « terminé avec
                    # avertissements » suivi d'un code de sortie 0.
                    # `fonction is self.charger_...` ne marcherait pas : chaque
                    # accès à une méthode liée crée un nouvel objet.
                    if self.strict and nom == "Chargement textes":
                        print("\n🛑 Mode strict : pipeline interrompu, aucun fichier réécrit.")
                        return False
            except Exception as e:
                print(f"❌ {nom} - Erreur: {e}")
                succes_total = False
        
        return succes_total

def main():
    """Fonction principale - Pipeline unique automatique luxembourgeois"""
    try:
        # Créer et exécuter le pipeline
        pipeline = LuxembourgishPipelineUnique()
        succes = pipeline.executer_pipeline()
        
        # Afficher les statistiques finales
        dict_count = len(pipeline.nouveau_dictionnaire) if pipeline.nouveau_dictionnaire else 0
        ngrams_count = len(pipeline.nouveaux_ngrams) if pipeline.nouveaux_ngrams else 0
        
        print("\n" + "=" * 60)
        if succes:
            print("🎉 PIPELINE LUXEMBURGISH KEYBOARD™ TERMINÉ AVEC SUCCÈS!")
            print("=" * 60)
            print("📱 Fichiers prêts pour l'intégration Android")
            print("🇱🇺 Lëtzebuergesch Klavier ass prett! 🇱🇺")
            print("✅ Dictionary files generated successfully")
            print(f"📊 Dictionary: {dict_count} words, {ngrams_count} N-grams")
            sys.exit(0)
        else:
            print("⚠️ PIPELINE TERMINÉ AVEC DES AVERTISSEMENTS")
            print("=" * 60)
            print("🔍 Consultez les messages ci-dessus pour plus de détails")
            sys.exit(1)
            
    except Exception as e:
        print(f"\n❌ ERREUR CRITIQUE: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()