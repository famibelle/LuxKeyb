#!/bin/bash
# Rejoue les appareils dont un test n'a pas abouti, apres correctif du harnais.
SP="$(cd "$(dirname "$0")" && pwd)"
cd "$SP" || exit 1
for avd in "$@"; do
  label=$(grep "^$avd|" devices.txt | cut -d'|' -f2)
  echo "=== REJEU $avd ($label) $(date +%H:%M:%S)"
  rm -rf "$SP/results/$avd"
  timeout 2400 python3 -u run_device.py "$avd" "$label" \
    > "$SP/results/$avd.log" 2>&1 < /dev/null
  echo "=== FIN $avd code=$? $(date +%H:%M:%S)"
  sleep 10
done
echo "=== REJEUX TERMINES $(date +%H:%M:%S)"
