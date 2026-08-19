#!/bin/bash
# Passe tous les AVD au banc d'affichage du clavier.
# Usage : tous.sh [dossier_de_sortie] [apk]
set -u
BANC="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-/tmp/banc-clavier}"
APK="${2:-$(ls -t "$BANC"/../../android_keyboard/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)}"

[ -n "$APK" ] || { echo "Aucun APK debug trouvé : lancer ./gradlew assembleDebug d'abord." >&2; exit 1; }
mkdir -p "$OUT"
echo "APK : $APK"
echo "Sortie : $OUT"

for avd in $(~/Android/Sdk/emulator/emulator -list-avds); do
  echo "=== $avd $(date +%H:%M:%S)"
  timeout 900 "$BANC/passe_avd.sh" "$avd" "$APK" "$OUT" 2>&1 | tail -3
  adb emu kill >/dev/null 2>&1
  sleep 10
done
echo "=== banc terminé $(date +%H:%M:%S)"
python3 "$BANC/synthese.py" "$OUT"
