#!/usr/bin/env python3
"""Test d'ecriture d'un message en portrait puis en paysage sur un AVD donne.

Usage: python3 -u run_device.py <avd> [label]

Deroulement : boot -> install APK -> activation IME -> ouverture du champ de
composition SMS -> frappe touche par touche de "nou ka pale kreyol" -> captures
et mesures, dans les deux orientations. Ecrit un JSON de resultats et les
captures dans results/<avd>/.
"""
import glob
import json
import os
import re
import subprocess
import sys
import time

SP = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SP)
import kbdetect  # noqa: E402

SDK = os.path.expanduser("~/Android/Sdk")
ADB = f"{SDK}/platform-tools/adb"
EMU = f"{SDK}/emulator/emulator"
# Racine du depot deduite de l'emplacement du script (reports/tests/<rapport>/scripts)
REPO = os.path.abspath(os.path.join(SP, "..", "..", "..", ".."))
APK_GLOB = os.path.join(
    REPO, "android_keyboard/app/build/outputs/apk/debug/*.apk")
IME = ("com.potomitan.kreyolkeyboard/"
       "com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored")


def trouver_apk():
    """APK debug le plus recent, ou celui passe en APK=... dans l'environnement.

    Construire d'abord avec `./gradlew assembleDebug` (voir CLAUDE.md pour les
    pieges Java 17 / gradlew corrompu)."""
    if os.environ.get("APK"):
        return os.environ["APK"]
    apks = sorted(glob.glob(APK_GLOB), key=os.path.getmtime)
    if not apks:
        sys.exit(f"aucun APK debug trouve dans {APK_GLOB} — lancer "
                 f"./gradlew assembleDebug d'abord")
    return apks[-1]


APK = trouver_apk()
# La campagne du 16 aout tapait "nou ka palé kréyòl". "ò" n'est plus une touche
# dediee depuis la 10.11.3 (5a788156) : il faudrait un appui long sur "o", que
# ce banc ne simule pas. La phrase est donc raccourcie a ses trois premiers
# mots, tous attestes dans creole_dict.json, l'enchainement "nou ka" -> "palé"
# figurant dans creole_ngrams.json. Elle exerce toujours la touche d'accent "é"
# de la rangee 4, la barre d'espace et le moteur de n-grammes.
MESSAGE = "nou ka palé"
# Point de controle a mi-mot pour observer la bande de suggestions
PARTIAL = "nou ka pal"
# Caractere ajoute en 10.12.15, objet de cette campagne
HASH = "#"


def sh(cmd, timeout=120):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True,
                       timeout=timeout)
    return r.stdout.strip()


def adb(args, timeout=120):
    return sh(f"{ADB} {args}", timeout)


def wait_boot(limit=900, log=None):
    """Attend la fin du boot, en abandonnant tout de suite si l'emulateur a
    refuse de demarrer (verrou d'instance laisse par un run precedent)."""
    t0 = time.time()
    while time.time() - t0 < limit:
        if log and os.path.exists(log):
            txt = open(log, errors="ignore").read()
            if "FATAL" in txt:
                return False
        if adb("shell getprop sys.boot_completed", 30).strip() == "1":
            time.sleep(5)
            return True
        time.sleep(10)
    return False


def clear_locks(avd):
    d = os.path.expanduser(f"~/.android/avd/{avd}.avd")
    for name in ("hardware-qemu.ini.lock", "multiinstance.lock",
                 "userdata-qemu.img.lock", "snapshot.lock"):
        p = os.path.join(d, name)
        subprocess.run(f"rm -rf '{p}'", shell=True)


def ime_frame():
    """Cadre de la fenetre IME, lu dans dumpsys window."""
    out = adb("shell dumpsys window windows", 90)
    block = None
    for chunk in out.split("Window #"):
        if re.search(r"Window\{\w+ u0 InputMethod\}", chunk):
            block = chunk
            break
    if not block:
        return None
    m = re.search(r"frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", block)
    if not m:
        return None
    x0, y0, x1, y1 = (int(v) for v in m.groups())
    app = re.search(r"mAppBounds=Rect\((\d+), (\d+) - (\d+), (\d+)\)", block)
    appb = tuple(int(v) for v in app.groups()) if app else None
    return {"frame": [x0, y0, x1, y1], "app_bounds": appb}


FIELD_ID = "compose_message_text"


def ensure_ime():
    """Rend le clavier creole courant, et le verifie.

    `ime set` juste apres l'installation peut echouer silencieusement (paquet
    pas encore pris en compte) : le systeme reste alors sur Gboard et tout le
    test mesure le mauvais clavier."""
    for _ in range(6):
        cur = adb("shell settings get secure default_input_method").strip()
        if cur.startswith(IME.split("/")[0]):
            return cur
        adb(f"shell ime enable {IME}")
        adb(f"shell ime set {IME}")
        time.sleep(4)
    return adb("shell settings get secure default_input_method").strip()


def clear_dialogs():
    """Ecarte une boite systeme (ANR, permission) qui prendrait le focus et
    masquerait le champ vise."""
    xml = adb("shell uiautomator dump /sdcard/d.xml", 90)
    txt = adb("shell cat /sdcard/d.xml", 60)
    for rid in ("android:id/aerr_wait", "android:id/aerr_close",
                "android:id/button1"):
        m = re.search(r'resource-id="' + re.escape(rid) +
                      r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', txt)
        if m:
            x0, y0, x1, y1 = (int(v) for v in m.groups())
            adb(f"shell input tap {(x0 + x1) // 2} {(y0 + y1) // 2}")
            time.sleep(2)
            return True
    return False


def dump_xml():
    adb("shell uiautomator dump /sdcard/d.xml", 90)
    return adb("shell cat /sdcard/d.xml", 60)


def open_compose():
    """Ouvre l'ecran de redaction d'un SMS et renvoie le centre du champ de
    saisie du corps du message (jamais celui du destinataire, qui reinterprete
    le texte comme une recherche de contact)."""
    global FIELD_ID
    clear_dialogs()
    adb("shell am force-stop com.google.android.apps.messaging")
    time.sleep(1)
    adb('shell am start -a android.intent.action.SENDTO -d "sms:0590123456"')
    time.sleep(6)
    for _ in range(4):
        clear_dialogs()
        xml = dump_xml()
        m = re.search(r'resource-id="[^"]*compose_message_text"[^>]*'
                      r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if m:
            FIELD_ID = "compose_message_text"
            x0, y0, x1, y1 = (int(v) for v in m.groups())
            return (x0 + x1) // 2, (y0 + y1) // 2
        time.sleep(3)
    # Repli : fiche contact, dont le champ "notes" est un simple EditText
    adb("shell am start -a android.intent.action.INSERT "
        "-t vnd.android.cursor.dir/contact")
    time.sleep(6)
    xml = dump_xml()
    m = re.search(r'resource-id="([^"]*)"[^>]*class="android.widget.EditText"'
                  r'[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if m:
        FIELD_ID = m.group(1).split("/")[-1]
        x0, y0, x1, y1 = (int(v) for v in m.groups()[1:])
        return (x0 + x1) // 2, (y0 + y1) // 2
    return None


def locate_field():
    """Bornes du champ deja ouvert, sans relancer l'application."""
    xml = dump_xml()
    m = re.search(r'resource-id="[^"]*' + FIELD_ID + r'"[^>]*'
                  r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if not m:
        return None
    x0, y0, x1, y1 = (int(v) for v in m.groups())
    return (x0 + x1) // 2, (y0 + y1) // 2


def current_rotation():
    out = adb("shell dumpsys window displays | grep -m1 -oE "
              "'mRotation=ROTATION_[0-9]+'", 60)
    m = re.search(r"ROTATION_(\d+)", out)
    return int(m.group(1)) if m else 0


def set_orientation(landscape):
    """Fait tourner l'affichage.

    Deux conditions, chacune suffisante a tout bloquer silencieusement :
    `settings put system user_rotation` reste sans effet sur ces AVD, seule la
    rotation de l'appareil (`adb emu rotate`) agit, et comme elle passe par le
    capteur d'orientation elle exige l'auto-rotation ACTIVEE
    (`accelerometer_rotation 1`) ; il faut de plus qu'une application acceptant
    le paysage soit au premier plan, le lanceur etant verrouille en portrait."""
    adb("shell settings put system accelerometer_rotation 1")
    for _ in range(5):
        r = current_rotation()
        if (r in (90, 270)) == landscape:
            return True
        adb("emu rotate", 30)
        time.sleep(6)
    return False


# Texte d'invite du champ, releve alors qu'il est certainement vide : le dump
# uiautomator n'expose pas d'attribut distinct pour l'invite, il la rend dans
# `text` exactement comme un contenu reel. Sans cette reference, clear_field()
# prenait « Text message » pour un brouillon et tapait 112 fois sur ⌫ dans un
# champ vide avant de sortir par son plafond de passes (constate le 23/08).
HINT = None


def field_text():
    """Texte du champ de saisie.

    Repli sur le champ qui a le focus : sur les images Play Store, Messages
    n'est pas configure comme application SMS par defaut et l'on retombe sur
    la fiche contact, dont l'identifiant de ressource differe."""
    xml = dump_xml()
    if FIELD_ID:
        m = re.search(r'text="([^"]*)"[^>]*resource-id="[^"]*' + FIELD_ID + '"',
                      xml)
        if m:
            return m.group(1)
    m = re.search(r'text="([^"]*)"[^>]*class="android\.widget\.EditText"'
                  r'[^>]*focused="true"', xml)
    return m.group(1) if m else None


def screenshot(path):
    with open(path, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f,
                       timeout=120)


def clear_field(info):
    """Vide le champ avec la vraie touche ⌫ affichee.

    Messages restaure le brouillon de la conversation d'un run a l'autre.
    KEYCODE_DEL effacerait bien le texte visible mais court-circuite
    InputProcessor, laissant le mot courant du moteur de suggestions pollue :
    on passe donc par la touche du clavier, comme un utilisateur."""
    # ⌫, derniere touche de la 3e rangee. Index 8 et non 7 depuis la 10.11.4 :
    # l'apostrophe est redevenue une touche visible, la rangee est passee de 8 a
    # 9 touches et l'index 7 tape desormais sur "'".
    x, y = kbdetect.key_center(info, 2, len(kbdetect.ROWS[2]) - 1)
    # Le tap qui a donne le focus est tombe au centre du champ, donc au milieu
    # d'un eventuel brouillon : ⌫ n'efface que vers l'arriere et laissait la
    # fin du texte en place, collee derriere le message a taper.
    adb("shell input keyevent KEYCODE_MOVE_END")
    time.sleep(0.5)
    total = 0
    # On relit le champ entre chaque passe : un simple len(texte)+marge laissait
    # des caracteres, des taps rapproches etant avales par le gestionnaire
    # d'appui long de la touche ⌫.
    for _ in range(8):
        txt = field_text() or ""
        if not txt.strip() or (HINT and txt.strip() == HINT):
            return total
        for _ in range(min(len(txt) + 2, 60)):
            adb(f"shell input tap {x} {y}", 30)
            time.sleep(0.2)
            total += 1
    return total


def type_message(info, text, outdir, tag, pause=0.22):
    seq = kbdetect.tap_seq(info, text)
    partial_shot = None
    for i, (x, y) in enumerate(seq):
        adb(f"shell input tap {x} {y}", 30)
        time.sleep(pause)
        if text[:i + 1] == PARTIAL:
            partial_shot = f"{outdir}/{tag}_suggestions.png"
            time.sleep(0.6)
            screenshot(partial_shot)
    return partial_shot


def test_symboles(info_alpha, outdir, name, o, dens):
    """Bascule en mode 123, verifie la page de symboles et frappe "#".

    Objet de la campagne : la touche "#" ajoutee en 10.12.15 porte la rangee 3
    de 9 a 10 touches. On controle donc que les quatre rangees sont completes,
    que "#" insere bien son caractere, et on mesure la largeur des touches de la
    rangee 3 face aux deux rangees du dessus, l'alignement des trois etant
    l'argument de l'ajout."""
    x, y = kbdetect.key_center(info_alpha, 3, 0)  # touche "123"
    adb(f"shell input tap {x} {y}", 30)
    time.sleep(1.5)
    shot = f"{outdir}/{name}_symboles.png"
    screenshot(shot)
    num = kbdetect.analyse(shot, kbdetect.ROWS_NUM)
    o["sym_layout_ok"] = num["ok"]
    o["sym_rows"] = [{"keys": r["n_keys"], "expected": r["expected"],
                      "h": r["height"]} for r in num["rows"]]
    if not num["ok"]:
        o["sym_error"] = "geometrie de la page de symboles non conforme"
        adb("shell input keyevent KEYCODE_BACK")
        return
    # Largeur moyenne des touches, rangee par rangee : la rangee 3 doit
    # rejoindre les rangees 1 et 2 maintenant qu'elle porte dix touches.
    larg = []
    for ri in range(3):
        r = num["rows"][ri]
        w = [b - a for a, b in r["keys"]]
        larg.append(round(sum(w) / len(w) / dens, 1))
    o["sym_key_width_dp"] = larg
    o["sym_width_spread_dp"] = round(max(larg) - min(larg), 1)
    hx, hy = kbdetect.key_center(num, *kbdetect.HASH_POS)
    o["sym_hash_center"] = [hx, hy]
    # Colonne de reference : "@" de la rangee du dessus, sous lequel "#" est
    # cense tomber.
    ax, _ = kbdetect.key_center(num, 1, 8)
    o["sym_hash_offset_px"] = hx - ax
    avant = field_text() or ""
    adb(f"shell input tap {hx} {hy}", 30)
    time.sleep(1.0)
    apres = field_text() or ""
    o["sym_field_avant"] = avant
    o["sym_field_apres"] = apres
    o["sym_hash_ok"] = apres.strip() == (avant.strip() + HASH)
    screenshot(f"{outdir}/{name}_apres_diese.png")
    # Retour a l'alphabetique : touche "ABC", 1re de la rangee 4
    bx, by = kbdetect.key_center(num, 3, 0)
    adb(f"shell input tap {bx} {by}", 30)
    time.sleep(1.0)


def relever_hint(res):
    """Memorise le texte d'invite du champ, une seule fois.

    Fiable seulement si Messages vient d'etre reinitialise : sinon un brouillon
    restaure serait pris pour l'invite et clear_field() renoncerait a l'effacer.
    On retombe alors sur une liste d'invites connues."""
    global HINT
    if HINT is not None:
        return
    if res.get("messages_efface"):
        HINT = (field_text() or "").strip() or None
    else:
        for connue in ("Text message", "Message texte", "Message SMS",
                       "Envoyer un message", "Message"):
            if (field_text() or "").strip() == connue:
                HINT = connue
                break
    res["hint_champ"] = HINT


def run_orientation(avd, rot, name, outdir, res):
    landscape = rot == 1
    o_ime = ensure_ime()
    field = open_compose()
    o = {"orientation": name, "ime_avant": o_ime}
    o["rotation_ok"] = set_orientation(landscape)
    if field:
        time.sleep(3)
        field = locate_field() or field
    o["rotation"] = current_rotation()
    if not field:
        o["error"] = "champ de composition introuvable"
        screenshot(f"{outdir}/{name}_ECHEC.png")
        res["orientations"].append(o)
        return
    adb(f"shell input tap {field[0]} {field[1]}")
    time.sleep(4)
    relever_hint(res)
    # Verification au moment qui compte : le systeme repasse sur Gboard un
    # court instant apres la reinstallation de l'apk, bien apres le ime set
    # initial. Sans ce controle ici, tout le test mesure Gboard.
    cur = adb("shell settings get secure default_input_method").strip()
    if not cur.startswith(IME.split("/")[0]):
        ensure_ime()
        adb("shell input keyevent KEYCODE_BACK")
        time.sleep(1)
        adb(f"shell input tap {field[0]} {field[1]}")
        time.sleep(4)
        cur = adb("shell settings get secure default_input_method").strip()
    o["ime"] = cur
    shown = adb("shell dumpsys input_method | grep -m1 mInputShown")
    o["input_shown"] = "true" in shown

    shot = f"{outdir}/{name}_clavier.png"
    screenshot(shot)
    info = kbdetect.analyse(shot)
    o["screen"] = [info["w"], info["h"]]
    o["layout_ok"] = info["ok"]
    o["rows"] = [{"top": r["top"], "bot": r["bot"], "h": r["height"],
                  "keys": r["n_keys"], "expected": r["expected"]}
                 for r in info["rows"]]
    fr = ime_frame()
    o["ime_frame"] = fr
    dens = res["density"] / 160.0
    if fr:
        h = fr["frame"][3] - fr["frame"][1]
        o["ime_height_px"] = h
        o["ime_height_dp"] = round(h / dens, 1)
        o["ime_share_screen_pct"] = round(100 * h / info["h"], 1)
        if fr["app_bounds"]:
            ab = fr["app_bounds"]
            apph = ab[3] - ab[1]
            o["app_area_px"] = apph
            o["app_left_px"] = max(0, fr["frame"][1] - ab[1])
            o["app_left_dp"] = round(o["app_left_px"] / dens, 1)
            o["ime_share_app_pct"] = round(100 * h / apph, 1)
    if info["ok"]:
        o["key_height_px"] = info["rows"][0]["height"]
        o["key_height_dp"] = round(info["rows"][0]["height"] / dens, 1)
        widths = [r["keys"][1][1] - r["keys"][1][0] for r in info["rows"]]
        o["key_width_dp"] = round(sum(widths) / len(widths) / dens, 1)
        last = info["rows"][-1]
        o["last_row_bottom_px"] = last["bot"]
        o["px_below_last_row"] = info["h"] - last["bot"]
        # Une frappe pilotee par adb duplique parfois un tap sous charge : on
        # rejoue une fois avant de conclure, pour ne pas imputer au clavier un
        # artefact de l'automatisation.
        attempts = []
        for essai in range(2):
            o["cleared_taps"] = clear_field(info)
            adb("logcat -c")
            o["partial_shot"] = bool(
                type_message(info, MESSAGE, outdir, name))
            time.sleep(2)
            got = field_text()
            attempts.append(got)
            if (got or "").strip().lower() == MESSAGE.lower():
                break
        screenshot(f"{outdir}/{name}_apres_frappe.png")
        o["typed"] = attempts[-1]
        o["typed_attempts"] = attempts
        # comparaison insensible a la casse : le champ SMS demande la majuscule
        # automatique en debut de phrase, que le clavier applique
        o["typed_ok"] = (o["typed"] or "").strip().lower() == MESSAGE.lower()
        test_symboles(info, outdir, name, o, dens)
        log = adb("logcat -d -v brief", 120)
        o["sugg_lines"] = [l.strip() for l in log.splitlines()
                           if "suggestion" in l.lower()][-6:]
        o["height_log"] = [l.strip() for l in log.splitlines()
                           if "Hauteur fen" in l][-2:]
        o["errors"] = [l.strip() for l in log.splitlines()
                       if ("FATAL" in l or "ANR in" in l
                           or "E/AndroidRuntime" in l)][:5]
    else:
        o["error"] = "geometrie du clavier non conforme"
    res["orientations"].append(o)


def main():
    avd = sys.argv[1]
    label = sys.argv[2] if len(sys.argv) > 2 else avd
    outdir = f"{SP}/results/{avd}"
    os.makedirs(outdir, exist_ok=True)
    res = {"avd": avd, "label": label, "orientations": []}

    cfg = os.path.expanduser(f"~/.android/avd/{avd}.avd/config.ini")
    conf = dict(l.split("=", 1) for l in open(cfg) if "=" in l)
    conf = {k.strip(): v.strip() for k, v in conf.items()}
    res["density"] = int(conf.get("hw.lcd.density", 160))
    res["api"] = conf.get("image.sysdir.1", "")
    res["geometry"] = f"{conf.get('hw.lcd.width')}x{conf.get('hw.lcd.height')}"

    reuse = "--use-running" in sys.argv
    if not reuse:
        log = f"{SP}/results/{avd}/emu.log"
        clear_locks(avd)
        t0 = time.time()
        # Repli headless assume pour cette campagne : en fenetre, le rendu
        # SwiftShader entre en concurrence CPU avec l'invite sous WSLg
        # (boot a froid de ~11 min, puis ANR de SystemUI).
        win = "" if "--windowed" in sys.argv else "-no-window "
        subprocess.Popen(
            f"{EMU} -avd {avd} {win}-no-audio -no-boot-anim "
            f"-gpu swiftshader_indirect -memory 2048 > {log} 2>&1",
            shell=True, start_new_session=True)
        if not wait_boot(limit=1500, log=log):
            res["error"] = "boot non termine"
            json.dump(res, open(f"{outdir}/result.json", "w"),
                      ensure_ascii=False, indent=1)
            print(json.dumps(res, ensure_ascii=False))
            return
        res["boot_seconds"] = round(time.time() - t0)

    adb(f"install -r -t {APK}", 300)
    # Le systeme rebascule sur le clavier par defaut pendant sa reindexation
    # des IME qui suit l'installation : laisser passer cette fenetre avant de
    # selectionner le notre, sinon la selection est annulee juste apres.
    time.sleep(12)
    adb("shell settings put secure show_ime_with_hard_keyboard 1")
    res["ime"] = ensure_ime()
    # Messages restaure le brouillon de la conversation d'une campagne a
    # l'autre. La touche ⌫ suffit d'ordinaire a le vider, mais sous charge des
    # taps rapproches se font avaler par le gestionnaire d'appui long et le
    # brouillon survit, la phrase de test venant alors s'ajouter derriere lui
    # (observe sur l'A05 en paysage le 23 aout). --clear-messages efface les
    # donnees de l'application avant le test, ce qui supprime le brouillon a la
    # source. A faire avant l'attribution du role SMS, que pm clear reinitialise.
    if "--clear-messages" in sys.argv:
        adb("shell pm clear com.google.android.apps.messaging", 90)
        time.sleep(3)
        res["messages_efface"] = True
    # Sur les images Play Store, Messages n'est pas application SMS par defaut :
    # l'intention SENDTO n'ouvre alors aucun ecran de redaction et le test
    # retombait sur la fiche contact, ou le clavier ne s'ouvre pas de facon
    # fiable. On lui attribue donc le role explicitement.
    adb("shell cmd role add-role-holder android.app.role.SMS "
        "com.google.android.apps.messaging", 90)
    adb("shell settings put secure sms_default_application "
        "com.google.android.apps.messaging")
    time.sleep(3)
    adb("shell settings put system accelerometer_rotation 1")
    adb("shell settings put global window_animation_scale 0")
    adb("shell settings put global transition_animation_scale 0")

    try:
        run_orientation(avd, 0, "portrait", outdir, res)
        run_orientation(avd, 1, "paysage", outdir, res)
    except Exception as e:  # noqa: BLE001
        res["exception"] = f"{type(e).__name__}: {e}"

    set_orientation(False)
    if not reuse:
        # Arret propre uniquement : un pkill laisse l'instantane de demarrage
        # incoherent, ce qui force un boot a froid de ~10 min au run suivant.
        adb("emu kill", 30)
        for _ in range(30):
            # En headless le binaire s'appelle qemu-system-x86_64-headless :
            # un `pgrep -x qemu-system-x86_64` ne le voit jamais et la boucle
            # sortait aussitot, sans attendre l'extinction (constate le 23/08).
            # -f sur la ligne complete, ancree sur -avd pour ne pas se
            # reconnaitre soi-meme.
            if not sh("pgrep -f 'qemu-system-x86_64[^ ]* -avd' || true"):
                break
            time.sleep(4)

    json.dump(res, open(f"{outdir}/result.json", "w"),
              ensure_ascii=False, indent=1)
    print(json.dumps(res, ensure_ascii=False))


if __name__ == "__main__":
    main()
