#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script d'enrichissement du dictionnaire créole - Potomitan™
Ajoute les mots du dataset Hugging Face au dictionnaire existant

Usage:
    1. Créer un fichier .env avec: HF_TOKEN=hf_xxxxxxxxxxxxxxxxx
    2. Exécuter: python EnrichirDictionnaire.py
    
    Le script chargera automatiquement le token depuis .env
"""

import json
import re
from collections import Counter
from datasets import load_dataset
from dotenv import load_dotenv
import os

def charger_dictionnaire_existant(chemin_dict):
    """Charge le dictionnaire créole existant"""
    try:
        with open(chemin_dict, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"Dictionnaire existant non trouvé: {chemin_dict}")
        return {}

def charger_textes_kreyol(hf_token=None):
    """
    Charge tous les textes créoles disponibles
    
    Args:
        hf_token (str, optional): Token Hugging Face pour l'authentification
    """
    textes = []
    
    # 1. Essayer de charger le dataset depuis Hugging Face
    try:
        print("🔄 Téléchargement du dataset depuis Hugging Face...")
        if hf_token:
            print("🔑 Utilisation du token Hugging Face fourni")
            ds = load_dataset("POTOMITAN/PawolKreyol-gfc", token=hf_token)
        else:
            print("📂 Accès public au dataset")
            ds = load_dataset("POTOMITAN/PawolKreyol-gfc")
        
        for item in ds['train']:
            if item.get('Texte') and item['Texte'].strip():
                textes.append(item['Texte'].strip())
        
        print(f"✅ Chargé {len(textes)} textes depuis Hugging Face Dataset")
        return textes
        
    except Exception as e:
        print(f"⚠️ Erreur lors du téléchargement depuis Hugging Face: {e}")
        if not hf_token:
            print("💡 Conseil: Essayez avec un token HF si le dataset est privé")
        print("🔄 Tentative de chargement depuis le fichier local...")
    
    # 2. Fallback : charger depuis le fichier local
    try:
        with open("PawolKreyol/Textes_kreyol.json", 'r', encoding='utf-8') as f:
            data = json.load(f)
            for item in data:
                if item.get('Texte') and item['Texte'].strip():
                    textes.append(item['Texte'].strip())
        
        print(f"✅ Chargé {len(textes)} textes depuis Textes_kreyol.json (fallback)")
        return textes
        
    except Exception as e:
        print(f"❌ Erreur lors du chargement du fichier local: {e}")
        print("❌ Aucune source de données disponible!")
        return []

def extraire_textes_kreyol(chemin_json):
    """Fonction dépréciée - utiliser charger_textes_kreyol() à la place"""
    print("⚠️ Fonction extraire_textes_kreyol() dépréciée - utilisation de charger_textes_kreyol()")
    return charger_textes_kreyol()

def tokeniser_et_compter(textes):
    """Tokenise les textes et compte les fréquences des mots"""
    print(f"Traitement de {len(textes)} textes créoles...")
    
    # Pattern pour extraire les mots créoles (avec accents)
    pattern = r"[a-zA-ZòéèùàâêîôûçÀÉÈÙÒ]+(?:['-][a-zA-ZòéèùàâêîôûçÀÉÈÙÒ]+)*"
    
    compteur_mots = Counter()
    
    for texte in textes:
        if texte:
            # Extraction des mots
            mots = re.findall(pattern, texte.lower())
            
            # Filtrage des mots trop courts
            mots_valides = [mot for mot in mots if len(mot) >= 2]
            
            compteur_mots.update(mots_valides)
    
    return compteur_mots

def fusionner_dictionnaires(dict_existant, nouveaux_mots):
    """Fusionne le dictionnaire existant avec les nouveaux mots"""
    print("Fusion des dictionnaires...")
    
    # Convertir le dict existant (liste de listes) en Counter pour faciliter la fusion
    compteur_existant = Counter()
    
    # Si dict_existant est une liste de listes [mot, freq]
    if isinstance(dict_existant, list):
        for item in dict_existant:
            if len(item) == 2:
                mot, freq = item
                compteur_existant[mot] = freq
    # Si dict_existant est un dictionnaire
    elif isinstance(dict_existant, dict):
        for mot, freq in dict_existant.items():
            compteur_existant[mot] = freq
    
    # Fusionner les compteurs
    compteur_fusionne = compteur_existant + nouveaux_mots
    
    return compteur_fusionne

def sauvegarder_dictionnaire_enrichi(compteur, chemin_sortie, nb_mots=3000):
    """Sauvegarde le dictionnaire enrichi"""
    print(f"Sauvegarde des {nb_mots} mots les plus fréquents...")
    
    # Prendre les mots les plus fréquents
    mots_frequents = compteur.most_common(nb_mots)
    
    # Créer le dictionnaire final au format liste de listes [mot, fréquence]
    dictionnaire_enrichi = [[mot, freq] for mot, freq in mots_frequents]
    
    # Sauvegarder
    with open(chemin_sortie, 'w', encoding='utf-8') as f:
        json.dump(dictionnaire_enrichi, f, ensure_ascii=False, indent=2)
    
    return len(dictionnaire_enrichi)

def main():
    print("=== Enrichissement du dictionnaire créole - Potomitan™ ===")
    
    # 1. Charger les variables d'environnement depuis .env
    load_dotenv()
    
    # 2. Récupérer le token Hugging Face
    hf_token = os.getenv('HF_TOKEN')
    
    if hf_token:
        print(f"🔑 Token Hugging Face trouvé dans .env")
    else:
        print("ℹ️ Aucun token trouvé dans .env - tentative d'accès public")
        print("💡 Pour utiliser un token: ajoutez HF_TOKEN=<votre_token> dans le fichier .env")
    
    # Chemins des fichiers (mis à jour pour la structure simplifiée)
    chemin_dict_existant = "android_keyboard/app/src/main/assets/creole_dict.json" 
    chemin_dict_enrichi = "android_keyboard/app/src/main/assets/creole_dict_enrichi.json"
    
    # 3. Charger le dictionnaire existant
    print("📚 Chargement du dictionnaire existant...")
    dict_existant = charger_dictionnaire_existant(chemin_dict_existant)
    print(f"Dictionnaire existant: {len(dict_existant)} mots")
    
    # 4. Charger les textes créoles
    print("📖 Chargement des textes créoles...")
    textes = charger_textes_kreyol(hf_token)
    print(f"Textes chargés: {len(textes)}")
    
    if not textes:
        print("❌ Aucun texte trouvé ! Vérifiez la configuration.")
        return
    
    # 5. Tokeniser et compter les nouveaux mots
    print("🔍 Analyse des textes...")
    nouveaux_mots = tokeniser_et_compter(textes)
    print(f"Nouveaux mots uniques trouvés: {len(nouveaux_mots)}")
    
    # 6. Afficher quelques statistiques
    print("\n=== Nouveaux mots les plus fréquents ===")
    for mot, freq in nouveaux_mots.most_common(20):
        print(f"{mot}: {freq}")
    
    # 7. Fusionner avec le dictionnaire existant
    print("\n🔄 Fusion des dictionnaires...")
    compteur_fusionne = fusionner_dictionnaires(dict_existant, nouveaux_mots)
    print(f"Dictionnaire fusionné: {len(compteur_fusionne)} mots uniques")
    
    # 8. Sauvegarder le dictionnaire enrichi
    nb_mots_sauves = sauvegarder_dictionnaire_enrichi(
        compteur_fusionne, 
        chemin_dict_enrichi, 
        nb_mots=3000
    )
    
    print(f"\n✅ Dictionnaire enrichi sauvegardé: {nb_mots_sauves} mots")
    print(f"📁 Fichier: {chemin_dict_enrichi}")
    
    # 9. Statistiques finales
    mots_ajoutes = len(compteur_fusionne) - len(dict_existant) if dict_existant else len(compteur_fusionne)
    print(f"📈 Nouveaux mots ajoutés: {mots_ajoutes}")
    print(f"🎯 Prêt pour l'intégration Android !")

if __name__ == "__main__":
    main()
