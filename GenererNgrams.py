#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Générateur de N-grams pour le clavier créole
Crée des bigrammes et trigrammes à partir des textes créoles
"""

import json
import re
from collections import Counter, defaultdict
import itertools

def charger_textes_kreyol():
    """Charge tous les textes créoles disponibles"""
    textes = []
    
    # 1. Charger Textes_kreyol.json
    try:
        with open("PawolKreyol/Textes_kreyol.json", 'r', encoding='utf-8') as f:
            data = json.load(f)
            for item in data:
                if item.get('Texte') and item['Texte'].strip():
                    textes.append(item['Texte'].strip())
        print(f"✅ Chargé {len([t for t in textes])} textes depuis Textes_kreyol.json")
    except Exception as e:
        print(f"❌ Erreur lors du chargement de Textes_kreyol.json: {e}")
    
    return textes

def nettoyer_et_tokeniser(texte):
    """Nettoie le texte et le tokenise en mots"""
    # Nettoyer le texte
    texte = texte.lower()
    
    # Garder uniquement les mots créoles (avec accents et apostrophes)
    pattern = r"[a-zA-ZòéèùàâêîôûçÀÉÈÙÒ]+(?:['-][a-zA-ZòéèùàâêîôûçÀÉÈÙÒ]+)*"
    mots = re.findall(pattern, texte)
    
    # Filtrer les mots trop courts
    mots_valides = [mot for mot in mots if len(mot) >= 2]
    
    return mots_valides

def generer_bigrammes(mots):
    """Génère des bigrammes (séquences de 2 mots)"""
    bigrammes = []
    for i in range(len(mots) - 1):
        bigramme = (mots[i], mots[i + 1])
        bigrammes.append(bigramme)
    return bigrammes

def generer_trigrammes(mots):
    """Génère des trigrammes (séquences de 3 mots)"""
    trigrammes = []
    for i in range(len(mots) - 2):
        trigramme = (mots[i], mots[i + 1], mots[i + 2])
        trigrammes.append(trigramme)
    return trigrammes

def creer_modele_ngrams(textes):
    """Crée le modèle de N-grams depuis les textes"""
    print("🔄 Création du modèle de N-grams...")
    
    # Compteurs pour les n-grams
    bigrammes_count = Counter()
    trigrammes_count = Counter()
    mots_suivants = defaultdict(Counter)  # mot -> {mot_suivant: freq}
    
    total_bigrammes = 0
    total_trigrammes = 0
    
    for texte in textes:
        if not texte:
            continue
            
        mots = nettoyer_et_tokeniser(texte)
        if len(mots) < 2:
            continue
            
        # Générer bigrammes
        bigrammes = generer_bigrammes(mots)
        bigrammes_count.update(bigrammes)
        total_bigrammes += len(bigrammes)
        
        # Créer le mapping mot -> mots suivants
        for mot1, mot2 in bigrammes:
            mots_suivants[mot1][mot2] += 1
        
        # Générer trigrammes si assez de mots
        if len(mots) >= 3:
            trigrammes = generer_trigrammes(mots)
            trigrammes_count.update(trigrammes)
            total_trigrammes += len(trigrammes)
    
    print(f"📊 Statistiques:")
    print(f"   - Bigrammes uniques: {len(bigrammes_count)}")
    print(f"   - Trigrammes uniques: {len(trigrammes_count)}")
    print(f"   - Total bigrammes: {total_bigrammes}")
    print(f"   - Total trigrammes: {total_trigrammes}")
    
    return bigrammes_count, trigrammes_count, mots_suivants

def convertir_en_probabilites(mots_suivants):
    """Convertit les compteurs en probabilités"""
    modele_probabilites = {}
    
    for mot_precedent, compteur_suivants in mots_suivants.items():
        total = sum(compteur_suivants.values())
        if total > 0:
            probabilites = {}
            for mot_suivant, count in compteur_suivants.items():
                probabilites[mot_suivant] = count / total
            modele_probabilites[mot_precedent] = probabilites
    
    return modele_probabilites

def sauvegarder_modele_ngrams(bigrammes, trigrammes, mots_suivants):
    """Sauvegarde le modèle de N-grams pour Android"""
    
    # 1. Modèle de prédiction simple : mot -> mots suivants avec probabilités
    modele_predictions = convertir_en_probabilites(mots_suivants)
    
    # 2. Top bigrammes pour validation (convertir tuples en strings)
    top_bigrammes = {}
    for (mot1, mot2), count in bigrammes.most_common(1000):
        key = f"{mot1} {mot2}"  # Convertir tuple en string
        top_bigrammes[key] = count
    
    # 3. Format pour Android
    modele_android = {
        "version": "1.0",
        "type": "ngram_model",
        "predictions": {},
        "top_bigrammes": top_bigrammes,
        "stats": {
            "total_bigrammes": len(bigrammes),
            "total_trigrammes": len(trigrammes),
            "mots_avec_predictions": len(modele_predictions)
        }
    }
    
    # 4. Convertir le modèle de prédictions en format compact
    for mot, probabilites in modele_predictions.items():
        # Garder seulement les 5 mots suivants les plus probables
        top_predictions = sorted(probabilites.items(), key=lambda x: x[1], reverse=True)[:5]
        if top_predictions:
            modele_android["predictions"][mot] = [
                {"word": mot_suivant, "prob": round(prob, 3)} 
                for mot_suivant, prob in top_predictions
            ]
    
    # 5. Sauvegarder pour Android
    chemin_ngrams = "android_keyboard/app/src/main/assets/creole_ngrams.json"
    with open(chemin_ngrams, 'w', encoding='utf-8') as f:
        json.dump(modele_android, f, ensure_ascii=False, indent=2)
    
    print(f"✅ Modèle N-grams sauvegardé: {chemin_ngrams}")
    return len(modele_android["predictions"])

def afficher_exemples_predictions(mots_suivants):
    """Affiche des exemples de prédictions"""
    print("\n🎯 Exemples de prédictions N-grams:")
    
    # Mots créoles courants pour exemples
    mots_exemples = ["an", "ka", "té", "nou", "yo", "pou", "sé", "i"]
    
    for mot in mots_exemples:
        if mot in mots_suivants:
            top_suivants = mots_suivants[mot].most_common(3)
            predictions = [f"{suivant}({count})" for suivant, count in top_suivants]
            print(f"   '{mot}' → {', '.join(predictions)}")

def main():
    print("=== Génération des N-grams pour le clavier créole ===")
    
    # 1. Charger les textes
    print("📚 Chargement des textes créoles...")
    textes = charger_textes_kreyol()
    print(f"Textes chargés: {len(textes)}")
    
    if not textes:
        print("❌ Aucun texte trouvé ! Vérifiez les chemins de fichiers.")
        return
    
    # 2. Créer le modèle N-grams
    bigrammes, trigrammes, mots_suivants = creer_modele_ngrams(textes)
    
    # 3. Afficher des exemples
    afficher_exemples_predictions(mots_suivants)
    
    # 4. Sauvegarder le modèle
    nb_predictions = sauvegarder_modele_ngrams(bigrammes, trigrammes, mots_suivants)
    
    print(f"\n🎉 Modèle N-grams créé avec succès !")
    print(f"📈 {nb_predictions} mots avec prédictions disponibles")
    print(f"🎯 Prêt pour l'intégration Android !")

if __name__ == "__main__":
    main()
