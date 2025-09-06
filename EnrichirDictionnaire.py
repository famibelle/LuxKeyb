#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script d'enrichissement du dictionnaire créole
Ajoute les mots du fichier Textes_kreyol.json au dictionnaire existant
"""

import json
import re
from collections import Counter

def charger_dictionnaire_existant(chemin_dict):
    """Charge le dictionnaire créole existant"""
    try:
        with open(chemin_dict, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"Dictionnaire existant non trouvé: {chemin_dict}")
        return {}

def extraire_textes_kreyol(chemin_json):
    """Extrait tous les textes du fichier Textes_kreyol.json"""
    with open(chemin_json, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    textes = []
    for item in data:
        if item.get('Texte') and item['Texte'].strip():
            textes.append(item['Texte'])
    
    return textes

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
    # Chemins des fichiers
    chemin_textes_kreyol = "PawolKreyol/Textes_kreyol.json"
    chemin_dict_existant = "clavier_creole/assets/creole_dict.json" 
    chemin_dict_enrichi = "clavier_creole/assets/creole_dict_enrichi.json"
    
    print("=== Enrichissement du dictionnaire créole ===")
    
    # 1. Charger le dictionnaire existant
    print("Chargement du dictionnaire existant...")
    dict_existant = charger_dictionnaire_existant(chemin_dict_existant)
    print(f"Dictionnaire existant: {len(dict_existant)} mots")
    
    # 2. Extraire les textes du fichier JSON
    print("Extraction des textes créoles...")
    textes = extraire_textes_kreyol(chemin_textes_kreyol)
    print(f"Textes extraits: {len(textes)}")
    
    # 3. Tokeniser et compter les nouveaux mots
    nouveaux_mots = tokeniser_et_compter(textes)
    print(f"Nouveaux mots uniques trouvés: {len(nouveaux_mots)}")
    
    # 4. Afficher quelques statistiques
    print("\n=== Nouveaux mots les plus fréquents ===")
    for mot, freq in nouveaux_mots.most_common(20):
        print(f"{mot}: {freq}")
    
    # 5. Fusionner avec le dictionnaire existant
    compteur_fusionne = fusionner_dictionnaires(dict_existant, nouveaux_mots)
    print(f"Dictionnaire fusionné: {len(compteur_fusionne)} mots uniques")
    
    # 6. Sauvegarder le dictionnaire enrichi
    nb_mots_sauves = sauvegarder_dictionnaire_enrichi(
        compteur_fusionne, 
        chemin_dict_enrichi, 
        nb_mots=3000
    )
    
    print(f"\n✅ Dictionnaire enrichi sauvegardé: {nb_mots_sauves} mots")
    print(f"📁 Fichier: {chemin_dict_enrichi}")
    
    # 7. Statistiques finales
    mots_ajoutes = len(compteur_fusionne) - len(dict_existant)
    print(f"📈 Nouveaux mots ajoutés: {mots_ajoutes}")

if __name__ == "__main__":
    main()
