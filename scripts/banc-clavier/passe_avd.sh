#!/bin/bash
# Passe un AVD au banc : démarre, installe, affiche le clavier, capture et analyse.
# Usage : passe_avd.sh <nom_avd> <apk> <dossier_sortie>
AVD="$1"; APK="$2"; OUT="$3"
EMU=~/Android/Sdk/emulator/emulator
BANC="$(dirname "$0")"
IME=com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored

echo "### $AVD"
nohup $EMU -avd "$AVD" -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 \
  > "$OUT/$AVD.log" 2>&1 &
EMUPID=$!

fin=$((SECONDS+420))
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  [ $SECONDS -gt $fin ] && { echo "$AVD|BOOT|echec de démarrage"; kill $EMUPID 2>/dev/null; adb emu kill >/dev/null 2>&1; sleep 5; exit 1; }
  sleep 5
done
sleep 8

adb install -r "$APK" >/dev/null 2>&1 || { echo "$AVD|INSTALL|echec"; adb emu kill >/dev/null 2>&1; exit 1; }
adb shell ime enable $IME >/dev/null 2>&1
adb shell ime set $IME >/dev/null 2>&1
adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1
adb shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null 2>&1

capture_clavier() {   # $1 = suffixe (portrait/paysage)
  ORIENT="$1"   # set -- plus bas écrase $1
  ROT=0; [ "$ORIENT" = "paysage" ] && ROT=1
  adb shell am force-stop com.google.android.contacts >/dev/null 2>&1
  adb shell am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >/dev/null 2>&1
  # La rotation ne prend que sur une activité déjà au premier plan : posée
  # pendant que le launcher, verrouillé en portrait, tient encore l'écran, elle
  # est bien enregistrée (mUserRotation passe à ROTATION_90) mais le display la
  # refuse, et plus rien ne la réévalue ensuite. Six secondes n'y suffisaient
  # pas toujours : on attend la résumption réelle de l'éditeur de contact.
  for i in $(seq 1 20); do
    case "$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 ResumedActivity | tr -d '\r')" in
      *contacts*) break;;
    esac
    sleep 1
  done
  sleep 2
  # Le verrou d'orientation ne prend pas à l'instant où on l'écrit :
  # accelerometer_rotation passe bien à 0 dans les réglages, mais le gestionnaire
  # de fenêtres reste un temps en USER_ROTATION_FREE, où user_rotation est
  # purement ignoré. Une rotation demandée dans cet intervalle est perdue sans
  # trace, et rien ne la rejoue quand le verrou finit par prendre. On attend donc
  # le verrou effectif plutôt que son écriture, en le réécrivant à chaque essai.
  for i in $(seq 1 10); do
    adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1
    case "$(adb shell dumpsys window 2>/dev/null | grep -m1 -o 'mUserRotationMode=[A-Z_]*')" in
      *LOCKED*) break;;
    esac
    sleep 2
  done
  # Repasser par l'orientation opposée force la réévaluation : réécrire la
  # valeur déjà présente dans les réglages ne déclenche rien du tout.
  adb shell settings put system user_rotation $((1 - ROT)) >/dev/null 2>&1
  sleep 1
  adb shell settings put system user_rotation $ROT >/dev/null 2>&1
  paysage_ok() { [ "$1" = "90" ] || [ "$1" = "270" ]; }
  portrait_ok() { [ "$1" = "0" ] || [ "$1" = "180" ]; }
  attente=0
  while [ $attente -lt 12 ]; do
    r=$(adb shell dumpsys window displays 2>/dev/null | grep -m1 -o "mDisplayRotation=ROTATION_[0-9]*" | grep -o "[0-9]*$")
    # Le paysage est servi en 90 comme en 270, au choix du système : exiger 90
    # faisait abandonner l'attente sur un écran pourtant bel et bien tourné.
    [ "$ROT" = "1" ] && paysage_ok "$r" && break
    [ "$ROT" = "0" ] && portrait_ok "$r" && break
    sleep 2; attente=$((attente+1))
  done
  # Sans ce refus, l'écran resté en portrait était capturé quand même et rangé
  # sous le nom « paysage » : dix appareils sur dix-huit étaient déclarés
  # vérifiés en paysage sans l'avoir jamais été.
  if { [ "$ROT" = "1" ] && ! paysage_ok "$r"; } || { [ "$ROT" = "0" ] && ! portrait_ok "$r"; }; then
    echo "$AVD|$ORIENT|ROTATION|écran resté en ROTATION_$r"
    return 1
  fi
  sleep 3
  for essai in 1 2 3; do
    adb shell uiautomator dump /sdcard/d.xml >/dev/null 2>&1
    dump=$(adb shell cat /sdcard/d.xml 2>/dev/null)
    if echo "$dump" | grep -q "isn't responding\|ne répond pas"; then
      # dialogue « System UI isn't responding » : choisir Attendre
      coords=$(echo "$dump" | tr '>' '\n' | grep -i 'text="Wait"\|text="Attendre"' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 |
               grep -o '[0-9]*' | tr '\n' ' ')
      set -- $coords
      [ -n "$1" ] && adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) >/dev/null 2>&1
      sleep 6
      continue
    fi
    champ=$(echo "$dump" | tr '>' '\n' | grep -i "EditText" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | grep -o '[0-9]*' | tr '\n' ' ')
    set -- $champ
    if [ -n "$1" ]; then
      adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) >/dev/null 2>&1
      sleep 5
    fi
    if [ "$(adb shell dumpsys input_method 2>/dev/null | grep -c 'mInputShown=true')" -ge 1 ]; then
      break
    fi
  done

  frame=$(adb shell dumpsys window displays 2>/dev/null | grep -m1 "type=ime frame" | grep -o 'frame=\[[0-9]*,[0-9]*\]' | grep -o '[0-9]*' | tail -1)
  if [ -z "$frame" ] || [ "$frame" = "0" ]; then
    echo "$AVD|$ORIENT|CLAVIER|le clavier ne s'affiche pas"
    return 1
  fi
  adb exec-out screencap -p > "$OUT/${AVD}_$ORIENT.png" 2>/dev/null
  # Dernier garde-fou, indépendant de ce que raconte dumpsys : c'est la forme de
  # l'image qui dit l'orientation. Une capture qui dément son nom est jetée.
  forme=$(python3 -c "from PIL import Image
import sys
w, h = Image.open(sys.argv[1]).size
print('paysage' if w > h else 'portrait')" "$OUT/${AVD}_$ORIENT.png" 2>/dev/null)
  if [ "$forme" != "$ORIENT" ]; then
    echo "$AVD|$ORIENT|CAPTURE|image $forme alors que $ORIENT est demandé"
    rm -f "$OUT/${AVD}_$ORIENT.png"
    return 1
  fi
  python3 "$BANC/analyse_clavier.py" "$OUT/${AVD}_$ORIENT.png" "$frame" > "$OUT/${AVD}_$ORIENT.json" 2>"$OUT/${AVD}_$ORIENT.err"
}

capture_clavier portrait
capture_clavier paysage
adb shell settings put system user_rotation 0 >/dev/null 2>&1

modele=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
taille=$(adb shell wm size 2>/dev/null | tr -d '\r' | awk '{print $NF}')
densite=$(adb shell wm density 2>/dev/null | head -1 | tr -d '\r' | awk '{print $NF}')
echo "$AVD|INFO|$modele|$taille|$densite" > "$OUT/${AVD}_info.txt"
adb emu kill >/dev/null 2>&1
sleep 8
