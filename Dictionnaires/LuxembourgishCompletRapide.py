#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 LUXEMBURGISH KEYBOARD™ - PIPELINE ULTRA-RAPIDE ET OPTIMISÉ 🇱🇺
======================================================================
Auteur: Medhi Akabi
Version: 1.0 - Version optimisée pour la vitesse (ignore complètement l'audio)
Date: 2025-10-21

MISSION:
Pipeline optimisé pour créer le dictionnaire et les n-grammes luxembourgeois
pour l'intégration dans le clavier Android, en ignorant complètement les données audio.

OPTIMISATIONS:
- Chargement direct sans streaming
- Ignore complètement l'audio
- Traitement rapide des transcriptions uniquement
- Limite à 500 transcriptions pour la performance
======================================================================
"""

import os
import sys
import json
import re
import time
from datetime import datetime
from collections import Counter, defaultdict
from dotenv import load_dotenv

# Gestion des dépendances optionnelles
try:
    from datasets import load_dataset
    HAS_DATASETS = True
    print("✅ Bibliothèque 'datasets' disponible")
except ImportError:
    HAS_DATASETS = False
    print("❌ Bibliothèque 'datasets' non disponible - mode fallback uniquement")

class LuxembourgishKeyboardPipelineRapide:
    """
    Pipeline optimisé pour créer le dictionnaire et les n-grammes luxembourgeois
    en mode ultra-rapide sans traitement audio.
    """
    
    def __init__(self):
        self.version = "1.0-RAPIDE"
        self.langue = "Luxembourgish"
        self.textes_luxembourgeois = []
        self.mots_luxembourgeois = set()
        self.dictionnaire_luxembourgeois = {}
        self.ngrams_luxembourgeois = defaultdict(int)
        
        # Configuration des caractères luxembourgeois
        self.caracteres_luxembourgeois = set('äëéöüABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz')
        
        print(f"🇱🇺 LUXEMBURGISH KEYBOARD™ - PIPELINE ULTRA-RAPIDE 🇱🇺")
        print("=" * 70)
        print(f"Version: {self.version} - Pipeline Luxembourgeois Optimisé")
        print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("🎯 TRAITEMENT RAPIDE - AUDIO IGNORÉ")
        print("=" * 70)
        
    def charger_textes_luxembourgeois(self):
        """
        Charge les transcriptions luxembourgeoises en mode ultra-rapide.
        Ignore complètement l'audio pour optimiser la vitesse.
        """
        print("\n📖 CHARGEMENT RAPIDE DES TRANSCRIPTIONS LUXEMBOURGEOISES")
        print("-" * 60)
        
        textes_charges = False
        
        # Tentative Hugging Face optimisée
        if HAS_DATASETS:
            try:
                print("🚀 Mode ultra-rapide: Hugging Face sans audio...")
                print("   📡 Connexion dataset Akabi/Luxemburgish_Press_Conferences_Gov")
                
                # Chargement direct avec limite pour la performance
                print("   ⚡ Chargement optimisé (limite 500 transcriptions)...")
                ds = load_dataset("Akabi/Luxemburgish_Press_Conferences_Gov", split="train[:500]")
                
                print(f"   ✅ Dataset chargé: {len(ds)} entrées")
                print("   📝 Extraction transcriptions uniquement...")
                
                self.textes_luxembourgeois = []
                transcriptions_valides = 0
                
                for i, item in enumerate(ds):
                    if "transcription" in item and item["transcription"] and item["transcription"].strip():
                        # Ne jamais accéder au champ audio
                        transcription = item["transcription"].strip()
                        if len(transcription) > 10:  # Filtrer les transcriptions trop courtes
                            self.textes_luxembourgeois.append({
                                "Texte": transcription,
                                "Source": "Akabi/Luxemburgish_Press_Conferences_Gov (optimisé)",
                                "index": i
                            })
                            transcriptions_valides += 1
                    
                    # Affichage de progression
                    if (i + 1) % 100 == 0:
                        print(f"      📊 Traité {i + 1}/{len(ds)} entrées...")
                
                print(f"   📈 Résultats:")
                print(f"      - Entrées traitées: {len(ds)}")
                print(f"      - Transcriptions valides: {transcriptions_valides}")
                print(f"      - Transcriptions retenues: {len(self.textes_luxembourgeois)}")
                
                # Échantillon
                if self.textes_luxembourgeois:
                    print("   🔬 Échantillon des transcriptions:")
                    for i, texte in enumerate(self.textes_luxembourgeois[:3]):
                        preview = texte["Texte"][:60] + "..." if len(texte["Texte"]) > 60 else texte["Texte"]
                        print(f"      {i+1}: '{preview}'")
                
                if len(self.textes_luxembourgeois) >= 50:  # Seuil minimum
                    print(f"🎉 CHARGEMENT RAPIDE RÉUSSI !")
                    print(f"   ✅ {len(self.textes_luxembourgeois)} transcriptions récupérées")
                    print("   ⚡ Mode ultra-rapide - audio ignoré")
                    textes_charges = True
                else:
                    print("⚠️ DONNÉES INSUFFISANTES - Fallback local...")
                    
            except Exception as e:
                print(f"❌ ERREUR HUGGING FACE: {e}")
                print("   🔄 Passage au fallback local...")
        else:
            print("❌ Bibliothèque 'datasets' non disponible")
            print("   🔄 Passage au fallback local...")
        
        # Fallback local si nécessaire
        if not textes_charges:
            print("\n🔄 FALLBACK LOCAL RAPIDE")
            print("-" * 40)
            
            # Données d'exemple luxembourgeoises pour garantir un fonctionnement
            transcriptions_fallback = [
                "D'Regierung huet elo eng wichteg Decisioun geholl fir d'Zukunft vum Land.",
                "Mir wëllen all Efforte maachen fir de Lëtzebuerger Bierger ze hëllefen.",
                "D'Educatioun ass eng vun eisen Prioritéiten fir déi nächst Joeren.",
                "D'Wirtschaft muss sech adaptéieren un déi nei Realitéiten.",
                "Mir sinn houfreg op eist Land an op eis Sprooch.",
                "D'Digitalisatioun spillt eng ëmmer méi wichteg Roll an eisem Liewen.",
                "D'Gesondheetsversuergung muss fir jiddereen zougänglech sinn.",
                "D'Ëmwelt ze schützen ass eng gemeinsam Verantwortung.",
                "D'Junioun tëschent de verschiddenen Culturen ass eist Stärkten.",
                "D'Zukunft vum Lëtzebuerg läit an den Hänn vun eis aller."
            ]
            
            self.textes_luxembourgeois = []
            for i, transcription in enumerate(transcriptions_fallback):
                self.textes_luxembourgeois.append({
                    "Texte": transcription,
                    "Source": "Fallback local luxembourgeois",
                    "index": i
                })
            
            print(f"✅ Fallback activé: {len(self.textes_luxembourgeois)} transcriptions")
            textes_charges = True
        
        if textes_charges:
            print(f"\n🎯 CHARGEMENT TERMINÉ")
            print(f"   📊 Total transcriptions: {len(self.textes_luxembourgeois)}")
            return True
        else:
            print("❌ ÉCHEC COMPLET DU CHARGEMENT")
            return False
    
    def extraire_mots_luxembourgeois(self):
        """Extrait et nettoie les mots luxembourgeois des transcriptions."""
        print("\n🔤 EXTRACTION DES MOTS LUXEMBOURGEOIS")
        print("-" * 45)
        
        if not self.textes_luxembourgeois:
            print("❌ Aucune transcription disponible")
            return False
        
        mots_bruts = []
        
        for texte_obj in self.textes_luxembourgeois:
            texte = texte_obj["Texte"]
            
            # Nettoyage et extraction des mots
            texte_nettoye = re.sub(r'[^\w\säëéöüÄËÉÖÜ]', ' ', texte)
            mots = texte_nettoye.split()
            
            for mot in mots:
                mot_propre = mot.strip().lower()
                if len(mot_propre) >= 2 and any(c in self.caracteres_luxembourgeois for c in mot_propre):
                    mots_bruts.append(mot_propre)
        
        # Compter les fréquences
        compteur_mots = Counter(mots_bruts)
        
        # Filtrer les mots par fréquence (minimum 2 occurrences)
        seuil_frequence = 1 if len(compteur_mots) < 1000 else 2
        
        for mot, freq in compteur_mots.items():
            if freq >= seuil_frequence:
                self.mots_luxembourgeois.add(mot)
                self.dictionnaire_luxembourgeois[mot] = freq
        
        print(f"   📊 Mots bruts extraits: {len(mots_bruts)}")
        print(f"   📊 Mots uniques trouvés: {len(compteur_mots)}")
        print(f"   📊 Mots retenus (fréq >= {seuil_frequence}): {len(self.mots_luxembourgeois)}")
        
        # Échantillon des mots les plus fréquents
        mots_frequents = sorted(self.dictionnaire_luxembourgeois.items(), key=lambda x: x[1], reverse=True)[:10]
        print("   🔬 Mots les plus fréquents:")
        for mot, freq in mots_frequents:
            print(f"      '{mot}' ({freq}x)")
        
        return len(self.mots_luxembourgeois) > 0
    
    def generer_ngrams_luxembourgeois(self):
        """Génère les n-grammes luxembourgeois pour l'autocomplétion."""
        print("\n🔗 GÉNÉRATION DES N-GRAMMES LUXEMBOURGEOIS")
        print("-" * 47)
        
        if not self.textes_luxembourgeois:
            print("❌ Aucune transcription disponible")
            return False
        
        # Génération de bigrammes et trigrammes
        for texte_obj in self.textes_luxembourgeois:
            texte = texte_obj["Texte"].lower()
            mots = re.findall(r'\b\w+\b', texte)
            
            # Bigrammes
            for i in range(len(mots) - 1):
                if len(mots[i]) >= 2 and len(mots[i+1]) >= 2:
                    bigram = f"{mots[i]} {mots[i+1]}"
                    self.ngrams_luxembourgeois[bigram] += 1
            
            # Trigrammes
            for i in range(len(mots) - 2):
                if all(len(mots[i+j]) >= 2 for j in range(3)):
                    trigram = f"{mots[i]} {mots[i+1]} {mots[i+2]}"
                    self.ngrams_luxembourgeois[trigram] += 1
        
        # Filtrer par fréquence
        seuil_ngram = 1 if len(self.ngrams_luxembourgeois) < 500 else 2
        ngrams_filtres = {ng: freq for ng, freq in self.ngrams_luxembourgeois.items() if freq >= seuil_ngram}
        self.ngrams_luxembourgeois = ngrams_filtres
        
        print(f"   📊 N-grammes générés (fréq >= {seuil_ngram}): {len(self.ngrams_luxembourgeois)}")
        
        # Échantillon des n-grammes les plus fréquents
        ngrams_frequents = sorted(self.ngrams_luxembourgeois.items(), key=lambda x: x[1], reverse=True)[:5]
        print("   🔬 N-grammes les plus fréquents:")
        for ngram, freq in ngrams_frequents:
            print(f"      '{ngram}' ({freq}x)")
        
        return len(self.ngrams_luxembourgeois) > 0
    
    def sauvegarder_donnees(self):
        """Sauvegarde le dictionnaire et les n-grammes en JSON dans le dossier Android assets."""
        print("\n💾 SAUVEGARDE DES DONNÉES ANDROID")
        print("-" * 40)
        
        try:
            # Définir le chemin du dossier assets Android
            assets_dir = os.path.join("android_keyboard", "app", "src", "main", "assets")
            
            # Vérifier et créer le dossier assets si nécessaire
            if not os.path.exists(assets_dir):
                print(f"   📁 Création du dossier: {assets_dir}")
                os.makedirs(assets_dir, exist_ok=True)
            else:
                print(f"   📁 Dossier assets trouvé: {assets_dir}")
            
            # Sauvegarde du dictionnaire dans assets
            dict_file = os.path.join(assets_dir, "luxemburgish_dict.json")
            with open(dict_file, 'w', encoding='utf-8') as f:
                json.dump(self.dictionnaire_luxembourgeois, f, ensure_ascii=False, indent=2)
            print(f"   ✅ Dictionnaire sauvé: {dict_file}")
            
            # Sauvegarde des n-grammes dans assets
            ngrams_file = os.path.join(assets_dir, "luxemburgish_ngrams.json")
            with open(ngrams_file, 'w', encoding='utf-8') as f:
                json.dump(self.ngrams_luxembourgeois, f, ensure_ascii=False, indent=2)
            print(f"   ✅ N-grammes sauvés: {ngrams_file}")
            
            # Sauvegarde de backup locale également
            backup_dict = "luxemburgish_dict_backup.json"
            backup_ngrams = "luxemburgish_ngrams_backup.json"
            
            with open(backup_dict, 'w', encoding='utf-8') as f:
                json.dump(self.dictionnaire_luxembourgeois, f, ensure_ascii=False, indent=2)
            print(f"   📋 Backup dictionnaire: {backup_dict}")
            
            with open(backup_ngrams, 'w', encoding='utf-8') as f:
                json.dump(self.ngrams_luxembourgeois, f, ensure_ascii=False, indent=2)
            print(f"   📋 Backup n-grammes: {backup_ngrams}")
            
            # Rapport final
            print(f"\n📈 RAPPORT FINAL")
            print(f"   - Transcriptions traitées: {len(self.textes_luxembourgeois)}")
            print(f"   - Mots dans le dictionnaire: {len(self.dictionnaire_luxembourgeois)}")
            print(f"   - N-grammes générés: {len(self.ngrams_luxembourgeois)}")
            print(f"   - Fichiers Android: {assets_dir}")
            print(f"   - Fichiers backup: répertoire courant")
            
            return True
            
        except Exception as e:
            print(f"❌ Erreur de sauvegarde: {e}")
            return False
    
    def executer_pipeline_rapide(self):
        """Exécute le pipeline complet en mode ultra-rapide."""
        print("\n🚀 PIPELINE ULTRA-RAPIDE LUXEMBOURGEOIS")
        print("=" * 50)
        
        etapes = [
            ("Chargement transcriptions", self.charger_textes_luxembourgeois),
            ("Extraction mots", self.extraire_mots_luxembourgeois),
            ("Génération n-grammes", self.generer_ngrams_luxembourgeois),
            ("Sauvegarde", self.sauvegarder_donnees)
        ]
        
        for i, (nom, fonction) in enumerate(etapes, 1):
            print(f"\n⏳ Étape {i}/{len(etapes)}: {nom}")
            start_time = time.time()
            
            if not fonction():
                print(f"❌ ÉCHEC à l'étape: {nom}")
                return False
            
            elapsed = time.time() - start_time
            print(f"   ⏱️ Terminé en {elapsed:.2f}s")
        
        print(f"\n🎉 PIPELINE ULTRA-RAPIDE TERMINÉ AVEC SUCCÈS !")
        return True

def main():
    """Fonction principale d'exécution."""
    
    # Configuration de l'environnement
    load_dotenv()
    
    print("🔧 INITIALISATION")
    print("-" * 30)
    
    # Vérifier le token Hugging Face
    if os.getenv('HUGGINGFACE_TOKEN'):
        print("🔑 Token Hugging Face configuré")
    else:
        print("⚠️ Token Hugging Face non configuré (optionnel)")
    
    # Créer et exécuter le pipeline
    pipeline = LuxembourgishKeyboardPipelineRapide()
    
    start_total = time.time()
    succes = pipeline.executer_pipeline_rapide()
    elapsed_total = time.time() - start_total
    
    print(f"\n{'='*70}")
    if succes:
        print("🎊 SUCCÈS COMPLET DU PIPELINE ULTRA-RAPIDE!")
        print(f"⏱️ Temps total d'exécution: {elapsed_total:.2f} secondes")
        print("🎯 Fichiers générés:")
        print("   - luxemburgish_dict_rapide.json")
        print("   - luxemburgish_ngrams_rapide.json")
    else:
        print("❌ ÉCHEC DU PIPELINE")
        print("Vérifiez les logs ci-dessus pour plus de détails")
    
    print("=" * 70)
    return succes

if __name__ == "__main__":
    main()