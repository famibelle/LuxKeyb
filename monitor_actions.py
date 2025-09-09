#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Surveillance GitHub Actions - Clavier Créole Potomitan
Affiche le statut des workflows en cours

Usage: python monitor_actions.py
"""

import requests
import json
import time
import sys
from datetime import datetime, timedelta

class GitHubActionsMonitor:
    def __init__(self, owner="famibelle", repo="KreyolKeyb"):
        self.owner = owner
        self.repo = repo
        self.api_base = f"https://api.github.com/repos/{owner}/{repo}"
        
    def get_recent_runs(self, limit=5):
        """Récupère les runs récents des GitHub Actions"""
        try:
            url = f"{self.api_base}/actions/runs"
            params = {"per_page": limit, "status": "all"}
            
            response = requests.get(url, params=params, timeout=10)
            response.raise_for_status()
            
            data = response.json()
            return data.get("workflow_runs", [])
            
        except requests.RequestException as e:
            print(f"❌ Erreur API GitHub: {e}")
            return []
    
    def get_workflow_jobs(self, run_id):
        """Récupère les jobs d'un workflow run"""
        try:
            url = f"{self.api_base}/actions/runs/{run_id}/jobs"
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            
            data = response.json()
            return data.get("jobs", [])
            
        except requests.RequestException as e:
            print(f"❌ Erreur lors de la récupération des jobs: {e}")
            return []
    
    def format_status(self, status, conclusion):
        """Formate le statut avec des icônes"""
        if status == "completed":
            if conclusion == "success":
                return "✅ Succès"
            elif conclusion == "failure":
                return "❌ Échec"
            elif conclusion == "cancelled":
                return "🚫 Annulé"
            else:
                return f"⚠️ {conclusion}"
        elif status == "in_progress":
            return "🔄 En cours"
        elif status == "queued":
            return "⏳ En attente"
        else:
            return f"❓ {status}"
    
    def format_duration(self, started_at, completed_at):
        """Calcule et formate la durée"""
        if not started_at:
            return "N/A"
        
        start = datetime.fromisoformat(started_at.replace('Z', '+00:00'))
        
        if completed_at:
            end = datetime.fromisoformat(completed_at.replace('Z', '+00:00'))
            duration = end - start
        else:
            duration = datetime.now(start.tzinfo) - start
        
        total_seconds = int(duration.total_seconds())
        minutes = total_seconds // 60
        seconds = total_seconds % 60
        
        if minutes > 0:
            return f"{minutes}m {seconds}s"
        else:
            return f"{seconds}s"
    
    def display_runs(self, runs):
        """Affiche les runs de manière formatée"""
        print("🚀 GITHUB ACTIONS - RUNS RÉCENTS")
        print("=" * 60)
        
        if not runs:
            print("ℹ️  Aucun run trouvé")
            return
        
        for i, run in enumerate(runs, 1):
            status = self.format_status(run["status"], run.get("conclusion"))
            duration = self.format_duration(run.get("run_started_at"), run.get("updated_at"))
            
            # Formatage de la date
            created = datetime.fromisoformat(run["created_at"].replace('Z', '+00:00'))
            time_ago = datetime.now(created.tzinfo) - created
            
            if time_ago.days > 0:
                time_str = f"il y a {time_ago.days}j"
            elif time_ago.seconds > 3600:
                hours = time_ago.seconds // 3600
                time_str = f"il y a {hours}h"
            elif time_ago.seconds > 60:
                minutes = time_ago.seconds // 60
                time_str = f"il y a {minutes}m"
            else:
                time_str = "à l'instant"
            
            print(f"\n{i}. 📋 {run['name']}")
            print(f"   {status} • Durée: {duration} • {time_str}")
            print(f"   🔀 Branche: {run['head_branch']} • Run #{run['run_number']}")
            
            if run.get("conclusion") == "failure":
                print(f"   🔗 Logs: {run['html_url']}")
        
        print("\n" + "=" * 60)
    
    def display_current_jobs(self, run_id, workflow_name):
        """Affiche les jobs d'un workflow en cours"""
        jobs = self.get_workflow_jobs(run_id)
        
        if not jobs:
            return
        
        print(f"\n📊 JOBS DÉTAILLÉS - {workflow_name}")
        print("-" * 50)
        
        for job in jobs:
            status = self.format_status(job["status"], job.get("conclusion"))
            duration = self.format_duration(job.get("started_at"), job.get("completed_at"))
            
            print(f"  {job['name']}: {status} ({duration})")
            
            # Afficher les steps si le job a échoué
            if job.get("conclusion") == "failure" and "steps" in job:
                failed_steps = [step for step in job["steps"] if step.get("conclusion") == "failure"]
                if failed_steps:
                    print(f"    💥 Étapes échouées:")
                    for step in failed_steps:
                        print(f"      - {step['name']}")
    
    def monitor(self, watch=False, interval=30):
        """Monitoring principal"""
        while True:
            try:
                runs = self.get_recent_runs()
                
                # Effacer l'écran pour le mode watch
                if watch:
                    import os
                    os.system('cls' if os.name == 'nt' else 'clear')
                
                print(f"🇬🇵 MONITORING GITHUB ACTIONS - {datetime.now().strftime('%H:%M:%S')}")
                
                self.display_runs(runs)
                
                # Afficher les détails des runs en cours
                active_runs = [run for run in runs if run["status"] in ["in_progress", "queued"]]
                
                for run in active_runs:
                    self.display_current_jobs(run["id"], run["name"])
                
                if active_runs:
                    print(f"\n🔄 {len(active_runs)} workflow(s) actif(s)")
                else:
                    print("\n✅ Aucun workflow en cours")
                
                if not watch:
                    break
                
                print(f"\n⏱️  Prochaine mise à jour dans {interval}s (Ctrl+C pour arrêter)")
                time.sleep(interval)
                
            except KeyboardInterrupt:
                print("\n\n👋 Monitoring arrêté")
                break
            except Exception as e:
                print(f"\n💥 Erreur inattendue: {e}")
                if not watch:
                    break
                time.sleep(10)

def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description="🇬🇵 Surveillance GitHub Actions - Clavier Créole Potomitan"
    )
    parser.add_argument('--watch', '-w', action='store_true',
                       help='Mode surveillance continue (rafraîchit automatiquement)')
    parser.add_argument('--interval', '-i', type=int, default=30,
                       help='Intervalle de rafraîchissement en secondes (défaut: 30)')
    
    args = parser.parse_args()
    
    monitor = GitHubActionsMonitor()
    monitor.monitor(watch=args.watch, interval=args.interval)

if __name__ == "__main__":
    main()
