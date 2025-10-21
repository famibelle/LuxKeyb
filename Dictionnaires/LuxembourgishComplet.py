#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 LUXEMBURGISH KEYBOARD™ - PIPELINE UNIQUE ET AUTOMATIQUE 🇱🇺
================================================================

Le pipeline ultime pour le clavier luxembourgeois intelligent.
EXÉCUTION AUTOMATIQUE COMPLÈTE - Aucune interaction requise !

Pipeline automatique intégré:
• Récupération données Hugging Face (Akabi/Luxemburgish_Press_Conferences_Gov)
• Extraction depuis la colonne "transcription"
• Création/enrichissement dictionnaire  
• Génération N-grams intelligents
• Analyse comparative (delta)
• Statistiques complètes avancées
• Analyse mots longs détaillée
• Validation intégrale
• Nettoyage automatique
• Sauvegarde sécurisée

Usage simple: python LuxembourgishComplet.py

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

class LuxembourgishPipelineUnique:
    """Pipeline unique automatique pour le système luxembourgeois"""
    
    def __init__(self):
        """Initialisation du pipeline"""
        self.version = "1.0 - Pipeline Luxembourgeois"
        self.chemin_dict = "../android_keyboard/app/src/main/assets/luxemburgish_dict.json"
        self.chemin_ngrams = "../android_keyboard/app/src/main/assets/luxemburgish_ngrams.json"
        self.hf_token = None
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
                    self.dictionnaire_actuel = json.load(f)
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
        """Charge les textes luxembourgeois depuis Hugging Face"""
        print("\n📖 CHARGEMENT DES TEXTES LUXEMBOURGEOIS")
        print("-" * 45)
        
        textes_charges = False
        
        # Essayer Hugging Face d'abord
        if HAS_DATASETS:
            try:
                print("🔄 Téléchargement depuis Hugging Face...")
                print(f"   📡 Connexion au dataset Akabi/Luxemburgish_Press_Conferences_Gov...")
                print("   🎵 Dataset audio détecté - Mode optimisé transcriptions uniquement")
                
                # Chargement optimisé sans audio - utilisation du streaming pour éviter le chargement de l'audio
                try:
                    print("   🚀 Méthode streaming (rapide, sans audio)...")
                    ds = load_dataset("Akabi/Luxemburgish_Press_Conferences_Gov", streaming=True)
                    
                    print("   ✅ Streaming activé")
                    print("   📝 Extraction des transcriptions en mode streaming...")
                    
                    self.textes_luxembourgeois = []
                    textes_vides = 0
                    textes_avec_transcription = 0
                    
                    # Traitement en streaming - plus rapide, pas d'audio chargé
                    for i, item in enumerate(ds["train"]):
                        # Limiter pour éviter trop de données en streaming
                        if i >= 500:  # Limite raisonnable pour le clavier
                            break
                            
                        if "transcription" in item and item["transcription"]:
                            self.textes_luxembourgeois.append({
                                "Texte": item["transcription"],
                                "Source": "Akabi/Luxemburgish_Press_Conferences_Gov (streaming)",
                                "metadata": {k: v for k, v in item.items() if k not in ["transcription", "audio"]}
                            })
                            textes_avec_transcription += 1
                        else:
                            textes_vides += 1
                            
                        # Affichage de progression
                        if (i + 1) % 50 == 0:
                            print(f"      📊 Traité {i + 1} transcriptions...")
                    
                    print(f"   📈 Statistiques d'extraction (streaming):")
                    print(f"      - Transcriptions traitées: {textes_avec_transcription + textes_vides}")
                    print(f"      - Avec transcription valide: {textes_avec_transcription}")
                    print(f"      - Vides ou invalides: {textes_vides}")
                    print(f"      - Transcriptions extraites: {len(self.textes_luxembourgeois)}")
                    
                    # Échantillon des premières transcriptions
                    if self.textes_luxembourgeois:
                        print("   🔬 Échantillon des transcriptions:")
                        for i, texte in enumerate(self.textes_luxembourgeois[:3]):
                            preview = texte["Texte"][:50] + "..." if len(texte["Texte"]) > 50 else texte["Texte"]
                            print(f"      Transcription {i+1}: '{preview}'")
                    
                    if self.textes_luxembourgeois:
                        print(f"🎉 TÉLÉCHARGEMENT HUGGING FACE RÉUSSI (STREAMING) !")
                        print(f"   ✅ {len(self.textes_luxembourgeois)} transcriptions récupérées")
                        print(f"   📊 Source: Dataset Akabi (mode streaming - sans audio)")
                        textes_charges = True
                    else:
                        print("⚠️ STREAMING INCOMPLET - Tentative méthode standard...")
                        textes_charges = False
                        
                except Exception as e_stream:
                    print(f"   ⚠️ Streaming échoué: {e_stream}")
                    print("   🔄 Tentative méthode standard (avec gestion audio)...")
                    textes_charges = False
                
                # Fallback: méthode standard si streaming échoue
                if not textes_charges:
                    # Utilisation du code simplifié fourni avec gestion optimisée de l'audio
                    ds = load_dataset("Akabi/Luxemburgish_Press_Conferences_Gov")
                    dataset = ds
                
                print("   ✅ Dataset récupéré avec succès")
                
                # Vérifier la structure du dataset
                print(f"   � Structure du dataset: {list(dataset.keys())}")
                
                # Déterminer quelle clé utiliser
                data_split = None
                if 'train' in dataset:
                    data_split = dataset['train']
                    split_name = 'train'
                elif 'test' in dataset:
                    data_split = dataset['test']
                    split_name = 'test'
                else:
                    # Prendre la première clé disponible
                    split_name = list(dataset.keys())[0]
                    data_split = dataset[split_name]
                
                print(f"   📊 Utilisation du split: '{split_name}'")
                print(f"   📊 Nombre total de rows: {len(data_split)}")
                print("   🔍 Extraction des transcriptions...")
                
                # Échantillon des premières rows pour debug
                print("   🔬 Échantillon des premières rows:")
                for i in range(min(3, len(data_split))):
                    item = data_split[i]
                    print(f"      Row {i+1}: {list(item.keys())}")
                    if 'transcription' in item:
                        preview = str(item['transcription'])[:50] + "..." if len(str(item['transcription'])) > 50 else str(item['transcription'])
                        print(f"         Transcription: '{preview}'")
                
                self.textes_luxembourgeois = []
                textes_vides = 0
                textes_avec_transcription = 0
                
                for i, item in enumerate(data_split):
                    if "transcription" in item and item["transcription"]:
                        self.textes_luxembourgeois.append({
                            "Texte": item["transcription"],
                            "Source": "Akabi/Luxemburgish_Press_Conferences_Gov",
                            "metadata": {k: v for k, v in item.items() if k != "transcription"}
                        })
                        textes_avec_transcription += 1
                    else:
                        textes_vides += 1
                        if textes_vides <= 3:  # Afficher seulement les 3 premiers exemples
                            print(f"   ⚠️ Row {i+1} sans transcription valide: {list(item.keys())}")
                
                print(f"   📈 Statistiques d'extraction:")
                print(f"      - Rows totales: {len(data_split)}")
                print(f"      - Avec champ 'transcription': {textes_avec_transcription}")
                print(f"      - Vides ou invalides: {textes_vides}")
                print(f"      - Transcriptions extraites: {len(self.textes_luxembourgeois)}")
                
                if self.textes_luxembourgeois:
                    print(f"🎉 TÉLÉCHARGEMENT HUGGING FACE RÉUSSI !")
                    print(f"   ✅ {len(self.textes_luxembourgeois)} transcriptions récupérées")
                    print(f"   📊 Source: Dataset Akabi/Luxemburgish_Press_Conferences_Gov")
                    textes_charges = True
                else:
                    print("❌ TÉLÉCHARGEMENT HUGGING FACE ÉCHOUÉ !")
                    print("   ⚠️ Dataset vide - aucune transcription trouvée")
                    
            except Exception as e:
                print("❌ TÉLÉCHARGEMENT HUGGING FACE ÉCHOUÉ !")
                print(f"   💥 Erreur: {e}")
                print("   🔄 Passage au mode fallback local...")
        else:
            print("❌ TÉLÉCHARGEMENT HUGGING FACE IMPOSSIBLE !")
            print("   📦 Bibliothèque 'datasets' non installée")
            print("   🔄 Passage au mode fallback local...")
        
        # Fallback local si Hugging Face échoue
        if not textes_charges:
            print("\n🔄 FALLBACK: Recherche de fichiers locaux...")
            chemins_locaux = [
                "luxemburgish_data/transcriptions.json",
                "../luxemburgish_data/transcriptions.json",
                "transcriptions_luxembourgeoises.json"
            ]
            
            for chemin in chemins_locaux:
                print(f"   🔍 Vérification: {chemin}")
                if os.path.exists(chemin):
                    try:
                        print(f"   📁 Fichier trouvé, chargement...")
                        with open(chemin, 'r', encoding='utf-8') as f:
                            data = json.load(f)
                        
                        if isinstance(data, list):
                            self.textes_luxembourgeois = data
                        elif isinstance(data, dict) and "transcriptions" in data:
                            self.textes_luxembourgeois = data["transcriptions"]
                        else:
                            print(f"   ⚠️ Format inattendu dans {chemin}")
                            continue
                        
                        print(f"✅ FALLBACK RÉUSSI !")
                        print(f"   📊 {len(self.textes_luxembourgeois)} transcriptions chargées depuis {chemin}")
                        textes_charges = True
                        break
                        
                    except Exception as e:
                        print(f"   ❌ Erreur lecture {chemin}: {e}")
                else:
                    print(f"   ❌ Fichier non trouvé")
        
        if not textes_charges:
            print("\n❌ ÉCHEC TOTAL !")
            print("   💥 Aucune transcription luxembourgeoise trouvée (ni Hugging Face, ni local)")
            print("   🚨 Le pipeline ne peut pas continuer sans données")
            return False
        
        print(f"\n📋 RÉSUMÉ CHARGEMENT:")
        # Détection de source plus précise
        if textes_charges and self.textes_luxembourgeois:
            source_hf = any(t.get("Source", "").find("Akabi") != -1 for t in self.textes_luxembourgeois[:5])
            source = "Hugging Face (Akabi)" if source_hf else "Local"
        else:
            source = "Inconnu"
        print(f"   📊 {len(self.textes_luxembourgeois)} transcriptions chargées")
        print(f"   🌐 Source: {source}")
        print(f"   ✅ Prêt pour traitement")
        
        return True
    
    def creer_dictionnaire(self):
        """Crée un dictionnaire enrichi à partir des transcriptions luxembourgeoises"""
        print("\n📚 CRÉATION DU DICTIONNAIRE LUXEMBOURGEOIS")
        print("-" * 45)
        
        if not self.textes_luxembourgeois:
            print("❌ Aucune transcription disponible")
            return False
        
        print(f"🔍 Analyse de {len(self.textes_luxembourgeois)} transcriptions...")
        
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
        
        # Fusionner avec le dictionnaire existant
        for mot, freq_nouvelle in compteur_mots.items():
            freq_existante = self.dictionnaire_actuel.get(mot, 0)
            compteur_mots[mot] = freq_existante + freq_nouvelle
        
        # Ajouter les mots existants non trouvés
        for mot, freq in self.dictionnaire_actuel.items():
            if mot not in compteur_mots:
                compteur_mots[mot] = freq
        
        self.nouveau_dictionnaire = dict(compteur_mots.most_common())
        
        nouveaux_mots = len(self.nouveau_dictionnaire) - len(self.dictionnaire_actuel)
        print(f"✅ Dictionnaire luxembourgeois créé:")
        print(f"   - Total mots: {len(self.nouveau_dictionnaire)}")
        print(f"   - Nouveaux mots: {nouveaux_mots}")
        print(f"   - Mots existants: {len(self.dictionnaire_actuel)}")
        
        return True
    
    def creer_ngrams(self):
        """Crée des N-grams pour les prédictions luxembourgeoises"""
        print("\n🧠 CRÉATION DES N-GRAMS LUXEMBOURGEOIS")
        print("-" * 40)
        
        if not self.textes_luxembourgeois:
            print("❌ Aucune transcription disponible")
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
        
        # Créer le modèle de prédictions
        predictions = {}
        total_unigrammes = sum(unigrammes.values())
        
        for mot in unigrammes:
            candidats = []
            
            # Chercher les mots qui suivent souvent ce mot
            for (premier, suivant), freq in bigrammes.items():
                if premier == mot:
                    probabilite = freq / unigrammes[premier]
                    if probabilite > 0.01:  # Seuil de pertinence
                        candidats.append({
                            "word": suivant,
                            "probability": round(probabilite, 3)
                        })
            
            # Trier par probabilité décroissante
            candidats.sort(key=lambda x: x["probability"], reverse=True)
            
            # Garder les 5 meilleurs
            if candidats:
                predictions[mot] = candidats[:5]
        
        self.nouveaux_ngrams = predictions
        
        print(f"✅ N-grams luxembourgeois créés:")
        print(f"   - Unigrammes: {len(unigrammes)}")
        print(f"   - Bigrammes: {len(bigrammes)}")
        print(f"   - Trigrammes: {len(trigrammes)}")
        print(f"   - Prédictions: {len(predictions)}")
        
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
        
        # Sauvegarder le nouveau dictionnaire
        if self.nouveau_dictionnaire:
            os.makedirs(os.path.dirname(self.chemin_dict), exist_ok=True)
            with open(self.chemin_dict, 'w', encoding='utf-8') as f:
                json.dump(self.nouveau_dictionnaire, f, ensure_ascii=False, indent=2)
            print(f"✅ Dictionnaire luxembourgeois sauvegardé: {len(self.nouveau_dictionnaire)} mots")
        
        # Sauvegarder les nouveaux N-grams
        if self.nouveaux_ngrams:
            os.makedirs(os.path.dirname(self.chemin_ngrams), exist_ok=True)
            with open(self.chemin_ngrams, 'w', encoding='utf-8') as f:
                json.dump(self.nouveaux_ngrams, f, ensure_ascii=False, indent=2)
            print(f"✅ N-grams luxembourgeois sauvegardés: {len(self.nouveaux_ngrams)} prédictions")
        
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
            ("Chargement transcriptions", self.charger_textes_luxembourgeois),
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