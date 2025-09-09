#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Déclencheur GitHub Actions - Clavier Créole Potomitan
Facilite le déclenchement des workflows GitHub Actions

Usage:
python actions_trigger.py --help
"""

import argparse
import subprocess
import sys
import json
from datetime import datetime

def run_command(cmd, description=""):
    """Exécute une commande shell et retourne le résultat"""
    print(f"🔄 {description}")
    print(f"   Commande: {cmd}")
    
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if result.returncode == 0:
            print(f"   ✅ Succès")
            if result.stdout.strip():
                print(f"   📤 Sortie: {result.stdout.strip()}")
            return True, result.stdout
        else:
            print(f"   ❌ Erreur (code {result.returncode})")
            if result.stderr.strip():
                print(f"   🚨 Erreur: {result.stderr.strip()}")
            return False, result.stderr
    except Exception as e:
        print(f"   💥 Exception: {e}")
        return False, str(e)

def create_version_tag(version, message=None):
    """Crée un tag de version et le pousse vers GitHub"""
    if not version.startswith('v'):
        version = f'v{version}'
    
    if not message:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M")
        message = f"🚀 Version {version} - Clavier Créole Potomitan\\n\\n✨ Build automatique du {timestamp}\\n🇬🇵 Klavié Kreyòl Karukera - Potomitan™"
    
    # Créer le tag
    success, output = run_command(
        f'git tag -a {version} -m "{message}"',
        f"Création du tag {version}"
    )
    
    if not success:
        return False
    
    # Pousser le tag
    success, output = run_command(
        f'git push origin {version}',
        f"Push du tag {version} vers GitHub"
    )
    
    return success

def check_git_status():
    """Vérifie l'état du repository Git"""
    print("📊 ÉTAT DU REPOSITORY")
    print("-" * 40)
    
    # Vérifier les changements non commités
    success, output = run_command("git status --porcelain", "Vérification des changements")
    if output.strip():
        print("⚠️  Des changements non commités ont été détectés:")
        print(output)
        return False
    else:
        print("✅ Repository propre, aucun changement non commité")
    
    # Afficher les derniers commits
    success, output = run_command("git log --oneline -3", "Derniers commits")
    if success and output:
        print("\n📝 Derniers commits:")
        for line in output.strip().split('\n'):
            print(f"   {line}")
    
    # Afficher les tags récents
    success, output = run_command("git tag --list | tail -5", "Tags récents")
    if success and output:
        print("\n🏷️  Tags récents:")
        for line in output.strip().split('\n'):
            print(f"   {line}")
    
    return True

def main():
    parser = argparse.ArgumentParser(
        description="🇬🇵 Déclencheur GitHub Actions - Clavier Créole Potomitan",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemples d'utilisation:

1. Vérifier l'état:
   python actions_trigger.py --status

2. Créer un tag de version:
   python actions_trigger.py --tag v2.2.1

3. Créer un tag avec message personnalisé:
   python actions_trigger.py --tag v2.2.1 --message "🚀 Correction critique"

4. Pousser les changements actuels:
   python actions_trigger.py --push

5. Workflow complet (push + tag):
   python actions_trigger.py --push --tag v2.2.1

Note: Les GitHub Actions se déclenchent automatiquement lors du push d'un tag.
        """
    )
    
    parser.add_argument('--status', action='store_true',
                       help='Vérifier l\'état du repository')
    
    parser.add_argument('--push', action='store_true',
                       help='Pousser les changements vers GitHub')
    
    parser.add_argument('--tag', type=str,
                       help='Créer et pousser un tag de version (ex: v2.2.1)')
    
    parser.add_argument('--message', type=str,
                       help='Message personnalisé pour le tag')
    
    args = parser.parse_args()
    
    print("🇬🇵 CLAVIER CRÉOLE POTOMITAN - DÉCLENCHEUR GITHUB ACTIONS")
    print("=" * 60)
    
    # Si aucun argument, afficher le statut par défaut
    if not any(vars(args).values()):
        args.status = True
    
    # Vérifier l'état du repository
    if args.status:
        if not check_git_status():
            print("\n⚠️  Repository non propre. Committez vos changements avant de continuer.")
            sys.exit(1)
    
    # Pousser les changements
    if args.push:
        print("\n📤 PUSH VERS GITHUB")
        print("-" * 30)
        success, output = run_command("git push origin main", "Push vers origin/main")
        if not success:
            print("❌ Échec du push")
            sys.exit(1)
    
    # Créer et pousser un tag
    if args.tag:
        print(f"\n🏷️  CRÉATION DU TAG {args.tag}")
        print("-" * 30)
        success = create_version_tag(args.tag, args.message)
        if success:
            print(f"✅ Tag {args.tag} créé et poussé avec succès!")
            print("🚀 Les GitHub Actions vont se déclencher automatiquement.")
            print("📱 Rendez-vous sur https://github.com/famibelle/KreyolKeyb/actions")
        else:
            print(f"❌ Échec de la création du tag {args.tag}")
            sys.exit(1)
    
    print("\n🎉 TERMINÉ!")
    print("📱 Pour surveiller les builds: https://github.com/famibelle/KreyolKeyb/actions")
    print("📦 Pour télécharger les APK: https://github.com/famibelle/KreyolKeyb/releases")

if __name__ == "__main__":
    main()
