#!/bin/bash
# Reprise de l'A05 apres la campagne : meme protocole, mais Messages est
# reinitialise avant le test pour partir d'un champ vide (le run du 23/08 avait
# herite du brouillon de la campagne du 16/08, que la touche ⌫ n'a pas efface).
SP="$(cd "$(dirname "$0")" && pwd)"
cd "$SP" || exit 1
until grep -q "CAMPAGNE TERMINEE" results/campagne.log 2>/dev/null; do sleep 20; done
until ! pgrep -f 'qemu-system-x86_64[^ ]* -avd' > /dev/null; do sleep 10; done
sleep 15
mkdir -p results_precedents
rm -rf results_precedents/kreyol_a05
mv results/kreyol_a05 results_precedents/kreyol_a05
echo "=== REPRISE kreyol_a05 $(date +%H:%M:%S)"
timeout 2400 python3 -u run_device.py kreyol_a05 "Samsung Galaxy A05 (262 dpi)" \
  --clear-messages > results/kreyol_a05_reprise.log 2>&1 < /dev/null
echo "=== FIN REPRISE kreyol_a05 code=$? $(date +%H:%M:%S)"
