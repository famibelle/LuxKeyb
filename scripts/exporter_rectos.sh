#!/bin/bash
# exporter_rectos.sh N : exporte N cartes à l'impression depuis le lecteur de cartes ouvert,
# en feuilletant vers la suivante après chacune (scripts/rectos_boite.py pour la suite).
#
# Avant : un build debug sur l'émulateur (run-as), le carnet de `rectos_boite.py carnet`
# dans shared_prefs/carnet_cartes.xml (appli arrêtée), le carnet trié « Récentes » et la
# première carte ouverte. Les coordonnées sont celles de l'AVD pixel9 (1080 × 2424).
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
SORTIE="$(dirname "$0")/../impression/boite/export"
mkdir -p "$SORTIE"

premier_plan() {
  foc=$(adb shell dumpsys window < /dev/null | grep mCurrentFocus)
  echo "$foc" | grep -q "luxkeyboard/com.example.kreyolkeyboard.SettingsActivity" \
    || { echo "premier plan inattendu : $foc"; exit 1; }
}

for n in $(seq 1 "$1"); do
  premier_plan
  # Appui long sur « Partager » : le menu d'impression.
  adb shell input swipe 385 2312 385 2312 1500 < /dev/null; sleep 2
  adb shell uiautomator dump /sdcard/d.xml >/dev/null 2>&1 < /dev/null
  ligne=$(adb shell cat /sdcard/d.xml < /dev/null | grep -o 'text="Cette carte : [^"]*"[^>]*bounds="[^"]*"')
  mot=$(echo "$ligne" | sed 's/text="Cette carte : \([^"]*\)".*/\1/')
  [ -z "$mot" ] && { echo "pas de menu, arrêt"; exit 1; }
  set -- $(echo "$ligne" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  nom=$(python3 -c "import sys;print(''.join(ch for ch in sys.argv[1] if ch.isalnum()))" "$mot")
  adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) < /dev/null
  # Le fichier apparaît dans le cache de l'appli, puis grossit jusqu'à la fin de l'écriture.
  f=""; for i in $(seq 1 60); do
    f=$(adb shell run-as com.potomitan.luxkeyboard ls cache/images < /dev/null 2>/dev/null | tr -d '\r' | grep "^impression_${nom}_")
    [ -n "$f" ] && break; sleep 1
  done
  [ -z "$f" ] && { echo "$mot : pas de fichier, arrêt"; exit 1; }
  t1=0; for i in $(seq 1 15); do
    sleep 2; t2=$(adb shell run-as com.potomitan.luxkeyboard stat -c %s "cache/images/$f" < /dev/null | tr -d '\r')
    [ "$t2" = "$t1" ] && break; t1=$t2
  done
  adb exec-out run-as com.potomitan.luxkeyboard cat "cache/images/$f" < /dev/null > "$SORTIE/$nom.png"
  echo "$mot $(stat -c %s "$SORTIE/$nom.png")"
  # Ferme la feuille de partage, puis carte suivante.
  sleep 1; adb shell input keyevent BACK < /dev/null; sleep 2
  premier_plan
  adb shell input swipe 900 1200 150 1200 250 < /dev/null; sleep 4
done
