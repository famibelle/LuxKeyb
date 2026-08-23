#!/bin/bash
# Campagne complete : un AVD apres l'autre, jamais deux en parallele
# (la RAM WSL2 ne le supporte pas).
SP="$(cd "$(dirname "$0")" && pwd)"
cd "$SP" || exit 1
while IFS='|' read -r avd label; do
  [ -z "$avd" ] && continue
  if [ -f "$SP/results/$avd/result.json" ] && \
     grep -q '"sym_hash_ok"' "$SP/results/$avd/result.json" 2>/dev/null; then
    echo "SKIP $avd (deja teste)"
    continue
  fi
  echo "=== DEBUT $avd ($label) $(date +%H:%M:%S)"
  # < /dev/null : sans cela le python heriterait du stdin de la boucle et
  # avalerait le reste de devices.txt, la campagne s'arretant au 1er appareil
  timeout 2400 python3 -u run_device.py "$avd" "$label" \
    > "$SP/results/$avd.log" 2>&1 < /dev/null
  echo "=== FIN $avd code=$? $(date +%H:%M:%S)"
  sleep 10
done < "$SP/devices.txt"
echo "=== CAMPAGNE TERMINEE $(date +%H:%M:%S)"
