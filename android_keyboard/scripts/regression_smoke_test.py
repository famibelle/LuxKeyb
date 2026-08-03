#!/usr/bin/env python3
"""Test de non-régression pour un APK Klavyé Kréyòl, sur un émulateur/appareil adb déjà démarré.

Simule un premier utilisateur (désinstallation puis installation propre), active le
clavier, ouvre un vrai champ de texte système, tape un mot créole connu avec le clavier
à l'écran, et vérifie que le dictionnaire s'est chargé (nombre de mots dans les logs)
et que le mot tapé est bien arrivé intact dans le champ. Conçu pour attraper la classe
de bug de la v10.2.6 (dictionnaire au mauvais format, suggestions désactivées
silencieusement) avant publication d'une release.

Usage: python3 regression_smoke_test.py chemin/vers/app-release.apk
"""

import json
import re
import subprocess
import sys
import time
import zipfile
from pathlib import Path

PACKAGE_ID = "com.potomitan.kreyolkeyboard"
# applicationId != package des classes Kotlin — piège documenté, voir memory emulator-testing-setup
IME_ID = f"{PACKAGE_ID}/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored"

# Coordonnées validées le 2026-08-03 sur kreyol_test (Pixel 5, 1080x2340, layout alphabétique)
ROW_Y = {1: 1634, 2: 1777, 3: 1918, 4: 2060}
KEY_XY = {
    "a": (69, ROW_Y[1]), "z": (163, ROW_Y[1]), "e": (257, ROW_Y[1]), "r": (351, ROW_Y[1]),
    "t": (445, ROW_Y[1]), "y": (539, ROW_Y[1]), "u": (633, ROW_Y[1]), "i": (727, ROW_Y[1]),
    "o": (821, ROW_Y[1]), "ò": (915, ROW_Y[1]), "p": (1010, ROW_Y[1]),
    "q": (73, ROW_Y[2]), "s": (177, ROW_Y[2]), "d": (280, ROW_Y[2]), "f": (383, ROW_Y[2]),
    "g": (487, ROW_Y[2]), "h": (590, ROW_Y[2]), "j": (694, ROW_Y[2]), "k": (797, ROW_Y[2]),
    "l": (901, ROW_Y[2]), "m": (1005, ROW_Y[2]),
    "w": (249, ROW_Y[3]), "x": (365, ROW_Y[3]), "c": (481, ROW_Y[3]), "v": (597, ROW_Y[3]),
    "b": (713, ROW_Y[3]), "n": (829, ROW_Y[3]), "⌫": (972, ROW_Y[3]),
    "é": (242, ROW_Y[4]), "è": (747, ROW_Y[4]), " ": (539, ROW_Y[4]),
}

BOOT_TIMEOUT_S = 30
IME_SHOW_TIMEOUT_S = 15


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check)


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.2)


def fail(message):
    print(f"❌ ÉCHEC : {message}")
    sys.exit(1)


def check_dict_format_in_apk(apk_path):
    """Vérifie AVANT toute installation que le dictionnaire embarqué est un tableau
    [[mot, fréquence], ...], pas un objet {mot: fréquence} — le bug exact de la v10.2.6.
    Ne nécessite aucun appareil connecté, donc échoue vite si le build CI a régressé."""
    with zipfile.ZipFile(apk_path) as z:
        raw = z.read("assets/creole_dict.json")
    data = json.loads(raw)
    if not isinstance(data, list):
        fail(
            f"assets/creole_dict.json dans l'APK est un {type(data).__name__}, "
            "pas un tableau. JSONArray(...) va planter silencieusement au chargement "
            "et désactiver toutes les suggestions (bug v10.2.6)."
        )
    print(f"✅ Format du dictionnaire embarqué : tableau de {len(data)} mots")


def ensure_device_ready():
    result = adb("devices")
    lines = [l for l in result.stdout.splitlines()[1:] if l.strip()]
    if not any(l.endswith("device") for l in lines):
        fail("aucun appareil adb connecté à l'état 'device' — démarrer l'émulateur avant ce script")
    boot_start = time.time()
    while time.time() - boot_start < BOOT_TIMEOUT_S:
        if adb("shell", "getprop", "sys.boot_completed").stdout.strip() == "1":
            return
        time.sleep(1)
    fail("sys.boot_completed jamais passé à 1")


def install_fresh(apk_path):
    adb("uninstall", PACKAGE_ID, check=False)
    result = adb("install", "-r", apk_path, check=False)
    if "Success" not in result.stdout:
        fail(f"échec de l'installation : {result.stdout}\n{result.stderr}")
    print("✅ APK installée (premier utilisateur simulé)")


def activate_ime():
    # Passe par adb directement plutôt que par les dialogues UI système, qui exigent
    # deux validations successives sur certaines versions d'Android (piège documenté) —
    # ce script vérifie le comportement du clavier, pas le parcours d'onboarding UI.
    adb("shell", "ime", "enable", IME_ID)
    result = adb("shell", "ime", "set", IME_ID)
    if "selected" not in result.stdout:
        fail(f"impossible de sélectionner l'IME : {result.stdout}")
    current = adb("shell", "settings", "get", "secure", "default_input_method").stdout.strip()
    if current != IME_ID:
        fail(f"default_input_method='{current}', attendu '{IME_ID}'")
    print("✅ Clavier activé et sélectionné par défaut")


def dump_edittext_nodes():
    """Retourne les attributs (text, bounds) de chaque EditText de l'écran au premier
    plan. L'ordre des attributs dans un nœud uiautomator (text avant class avant
    bounds) est fixe mais ne doit pas être présumé entre deux attributs différents
    dans une regex unique — on capture le bloc du nœud puis on cherche dedans."""
    adb("shell", "uiautomator", "dump", "/sdcard/dump.xml")
    dump = adb("shell", "cat", "/sdcard/dump.xml").stdout
    nodes = []
    for node_match in re.finditer(r"<node\b(.*?)(?:/>|>)", dump, re.DOTALL):
        attrs = node_match.group(1)
        if 'class="android.widget.EditText"' not in attrs:
            continue
        text_match = re.search(r'text="([^"]*)"', attrs)
        bounds_match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attrs)
        if bounds_match:
            nodes.append({
                "text": text_match.group(1) if text_match else "",
                "bounds": tuple(int(v) for v in bounds_match.groups()),
            })
    return nodes


def locate_first_edittext():
    # Ne jamais figer une coordonnée de tap devinée visuellement : la position exacte
    # du champ "Prénom" dérive selon l'état de l'app Contacts (piège documenté dans la
    # mémoire du projet, reproduit pendant l'écriture de ce script — un tap figé est
    # tombé sur le champ Téléphone, qui rejette silencieusement les lettres).
    nodes = dump_edittext_nodes()
    if not nodes:
        fail("aucun champ EditText trouvé dans le dump uiautomator de l'écran Contacts")
    x1, y1, x2, y2 = nodes[0]["bounds"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def open_text_field_and_wait_keyboard():
    # force-stop avant de lancer l'écran de création : sans ça, un run précédent peut
    # laisser Contacts en arrière-plan avec le formulaire scrollé plus bas, et
    # locate_first_edittext() retourne alors un champ différent de "Prénom" (vécu
    # pendant l'écriture de ce script).
    adb("shell", "am", "force-stop", "com.google.android.contacts", check=False)
    adb("shell", "am", "start", "-a", "android.intent.action.INSERT",
        "-t", "vnd.android.cursor.dir/contact")
    time.sleep(1.5)
    tap(*locate_first_edittext())

    # mInputShown peut rester à false plusieurs secondes sous charge avant de passer à
    # true — conclure à un échec trop tôt est le piège vécu pendant cette session.
    deadline = time.time() + IME_SHOW_TIMEOUT_S
    while time.time() < deadline:
        dump = adb("shell", "dumpsys", "input_method").stdout
        if "mInputShown=true" in dump:
            time.sleep(0.5)  # laisser l'animation d'apparition se stabiliser avant de taper
            print("✅ Clavier affiché")
            return
        time.sleep(1)
    fail(f"le clavier ne s'est pas affiché dans les {IME_SHOW_TIMEOUT_S}s (mInputShown resté false)")


def type_word(word):
    for char in word.lower():
        xy = KEY_XY.get(char)
        if xy is None:
            fail(f"caractère '{char}' non mappé dans KEY_XY — étendre la table de coordonnées")
        tap(*xy)


def verify_dictionary_loaded():
    logcat = adb("logcat", "-d").stdout

    if "FATAL EXCEPTION" in logcat or "AndroidRuntime: FATAL" in logcat:
        fail("une exception fatale est apparue dans logcat pendant le test — voir capture logcat ci-dessous\n" +
             "\n".join(l for l in logcat.splitlines() if "FATAL" in l))

    if "Dictionnaire chargé:" not in logcat:
        fail("aucune ligne 'Dictionnaire chargé:' dans logcat — le dictionnaire n'a "
             "probablement pas fini de charger, ou a échoué silencieusement")

    dict_line = next(l for l in logcat.splitlines() if "Dictionnaire chargé:" in l)
    print(f"✅ {dict_line.split(':', 1)[-1].strip()}")


def verify_word_reached_field(expected_word, screenshot_path):
    # Vérifie via le champ Contacts réel (visible dans uiautomator, contrairement à la
    # fenêtre IME séparée — mémoire du projet) que le mot est bien arrivé caractère par
    # caractère jusqu'à l'InputConnection. Plus robuste qu'un grep sur un message de log
    # précis : ce dernier peut disparaître d'un build à l'autre sans rapport avec le bug
    # qu'on cherche à détecter (constaté pendant l'écriture de ce script : les logs
    # d'affichage de suggestions de KreyolInputMethodServiceRefactored.kt n'apparaissent
    # pas dans le logcat de l'APK release, alors que les suggestions s'affichent bien
    # à l'écran — capture d'écran à l'appui).
    with open(screenshot_path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)

    texts = [node["text"] for node in dump_edittext_nodes()]
    if not any(t.lower() == expected_word.lower() for t in texts):
        fail(
            f"le champ ne contient pas '{expected_word}' (contenu trouvé: {texts!r}) — "
            f"une touche a probablement été perdue en route. Capture : {screenshot_path}"
        )
    print(f"✅ '{expected_word}' bien reçu par le champ de texte — capture : {screenshot_path}")


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    apk_path = sys.argv[1]
    if not Path(apk_path).is_file():
        fail(f"fichier introuvable : {apk_path}")

    check_dict_format_in_apk(apk_path)
    ensure_device_ready()
    adb("logcat", "-c")  # buffer propre : un long historique peut faire déborder les lignes utiles
    install_fresh(apk_path)
    activate_ime()
    open_text_field_and_wait_keyboard()

    test_word = "byen"
    type_word(test_word)
    time.sleep(1)
    verify_dictionary_loaded()
    verify_word_reached_field(test_word, "/tmp/regression_smoke_test_screenshot.png")

    print("\n✅ TOUS LES TESTS SONT PASSÉS")


if __name__ == "__main__":
    main()
