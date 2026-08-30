#!/usr/bin/env python3
"""Banc de la dictée **sur le téléphone**, chaîne complète et automatisée.

    python stt/bench/bench_device.py --work W --out R.json --device 192.168.1.236:36331 --clips 12

Ce que ça mesure, et pourquoi ça ne se confond pas avec `bench_luxasr.py` :
celui-ci parle au service depuis le poste, avec de l'audio parfait ; celui-là
fait rejouer l'extrait par le haut-parleur du téléphone devant son propre
micro, laisse l'application capturer, détecter la fin d'énoncé, envoyer et
poser le texte. Tout ce que la chaîne acoustique et le VAD coûtent apparaît
dans l'écart entre les deux.

Le montage. Un serveur local est exposé au téléphone par `adb reverse` ; Chrome
y ouvre une page qui sert à la fois de lecteur (les tranches sortent au
haut-parleur) et de champ de saisie (la dictée s'écrit dans un textarea). Rien
ne change d'application pendant la mesure : basculer vers un lecteur externe
déclencherait `onFinishInput` et couperait la dictée. La page renvoie ses
horodatages au serveur, ce qui donne le délai réel entre la fin de la parole et
le texte — le chiffre que ressent l'utilisateur.

Ce que ça ne mesure pas : une voix humaine dans une pièce réelle. Un
haut-parleur de téléphone devant son propre micro, c'est de la parole déjà
enregistrée, comprimée en MP3 puis rejouée ; le résultat est un ordre de
grandeur, pas une note d'examen.
"""

import argparse
import json
import re
import subprocess
import sys
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from bench_luxasr import wer_infixe

PORT = 8099
PAGE = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<title>Banc dictée</title>
<style>body{font:15px system-ui;margin:0;padding:6px;background:#fff}
textarea{width:100%;height:120px;font-size:17px}
button{font-size:20px;padding:10px 18px;margin-top:6px}
#s{font:13px monospace;color:#555;margin-top:6px}</style>
<textarea id=t placeholder="dictée ici"></textarea>
<button id=go>▶ Démarrer le banc</button>
<div id=s>en attente</div>
<script>
const t=document.getElementById('t'), s=document.getElementById('s');
const a=new Audio(); a.preload='auto'; let seq=-1, dernier='';
function post(ev,d){d=d||{};d.ev=ev;d.t=Date.now();
  fetch('/report',{method:'POST',body:JSON.stringify(d)});}
// Déverrouillage de la lecture : Chrome n'autorise le son que si un geste
// utilisateur a démarré cet élément-là au moins une fois.
document.getElementById('go').onclick=()=>{
  a.src='/silence.wav'; a.play().then(()=>{s.textContent='prêt';post('ready');})
   .catch(e=>{s.textContent='refus lecture: '+e;post('erreur',{e:''+e});});};
a.onended=()=>{post('audio_end');s.textContent='fin audio';};
async function poll(){try{
  const c=await (await fetch('/cmd')).json();
  if(c.seq!==seq){seq=c.seq;
    if(c.cmd==='clear'){t.value='';dernier='';t.focus();post('clear');}
    if(c.cmd==='play'){a.src=c.url;a.currentTime=0;
      a.play().then(()=>{s.textContent='lecture '+c.clip;post('audio_start',{clip:c.clip});})
       .catch(e=>post('erreur',{e:''+e}));}
  }}catch(e){}}
setInterval(poll,150);
// Le texte est relevé au fil de l'eau : la dictée arrive en texte de
// composition, qui ne déclenche pas toujours 'input' sur toutes les versions.
setInterval(()=>{if(t.value!==dernier){dernier=t.value;post('texte',{v:t.value});}},120);
</script>"""


class Etat:
    def __init__(self):
        self.cmd = {"seq": 0, "cmd": "noop"}
        self.evenements = []
        self.lock = threading.Lock()

    def envoyer(self, cmd, **kw):
        with self.lock:
            self.cmd = dict(seq=self.cmd["seq"] + 1, cmd=cmd, **kw)

    def ajouter(self, e):
        with self.lock:
            self.evenements.append(e)

    def marque(self):
        with self.lock:
            return len(self.evenements)

    def depuis(self, i, ev=None):
        """Les événements postés depuis la marque `i`.

        Volontairement indexé et non horodaté : les estampilles viennent de la
        page, donc de l'horloge du téléphone, qui n'est pas celle du poste. Un
        filtre par date mélangerait les deux et vidait silencieusement la
        liste.
        """
        with self.lock:
            return [e for e in self.evenements[i:]
                    if ev is None or e["ev"] == ev]


ETAT = Etat()
DOSSIER_WAV = None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _envoi(self, code, ctype, corps):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(corps)

    def do_GET(self):
        chemin = self.path.split("?")[0]
        if chemin == "/":
            self._envoi(200, "text/html; charset=utf-8", PAGE.encode())
        elif chemin == "/cmd":
            with ETAT.lock:
                c = json.dumps(ETAT.cmd).encode()
            self._envoi(200, "application/json", c)
        elif chemin.endswith(".wav"):
            f = DOSSIER_WAV / Path(chemin).name
            if f.exists():
                self._envoi(200, "audio/wav", f.read_bytes())
            else:
                self._envoi(404, "text/plain", b"absent")
        else:
            self._envoi(404, "text/plain", b"absent")

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        try:
            ETAT.ajouter(json.loads(self.rfile.read(n)))
        except Exception:
            pass
        self._envoi(200, "text/plain", b"ok")


# --- téléphone ---------------------------------------------------------------

def adb(dev, *args, **kw):
    return subprocess.run(["adb", "-s", dev, *args], capture_output=True,
                          text=True, **kw).stdout


def fenetre(dev):
    for l in adb(dev, "shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in l:
            return l.strip()
    return ""


def assurer_chrome(dev):
    """Garde-fou : ne taper que si Chrome est bien au premier plan.

    Sans ça, un enchaînement raté envoie des appuis aveugles dans
    l'application qui se trouve dessous — ici une conversation personnelle,
    à un doigt du bouton « envoyer ». Aucune mesure ne vaut ce risque.
    """
    f = fenetre(dev)
    if "com.android.chrome" not in f:
        raise SystemExit(f"❌ Chrome n'est pas au premier plan ({f}) — arrêt "
                         f"avant d'envoyer un appui dans une autre application")


def taper(dev, x, y):
    assurer_chrome(dev)
    adb(dev, "shell", "input", "tap", str(int(x)), str(int(y)))


def bornes_micro(dev):
    """Coordonnées du bouton micro, lues dans l'arbre d'accessibilité."""
    adb(dev, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb(dev, "shell", "cat", "/sdcard/ui.xml")
    m = re.search(r'content-desc="Dictée vocale"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if not m:
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="Dictée vocale"', xml)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def ecrire_wav(f32_path, dst, gain=1.0):
    a = np.fromfile(f32_path, dtype="<f4") * gain
    pcm = (np.clip(a, -1, 1) * 32767).astype("<i2")
    with wave.open(str(dst), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(pcm.tobytes())


def main():
    global DOSSIER_WAV
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--device", required=True)
    ap.add_argument("--clips", type=int, default=12)
    ap.add_argument("--parents", default="", help="ne prendre que ces fichiers, "
                    "mais TOUTES leurs tranches — nécessaire pour le WER de "
                    "pipeline, qui recolle les tranches d'un fichier entier")
    ap.add_argument("--dur-min", type=float, default=3.0)
    ap.add_argument("--dur-max", type=float, default=9.0)
    ap.add_argument("--attente-max", type=float, default=45.0)
    ap.add_argument("--micro", default="", help="x,y du bouton micro si l'arbre "
                    "d'accessibilité ne l'expose pas")
    args = ap.parse_args()

    dev = args.device
    slices = json.loads((args.work / "slices.json").read_text(encoding="utf-8"))
    refs = {e["id"]: e["reference"] for e in
            json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))}
    if args.parents:
        garde = tuple(args.parents.split(","))
        choix = [s for s in slices if s["parent"].startswith(garde)]
    else:
        choix = [s for s in slices
                 if args.dur_min <= s["dur"] <= args.dur_max][:args.clips]

    DOSSIER_WAV = args.work / "wav"
    DOSSIER_WAV.mkdir(exist_ok=True)
    # 100 ms de silence : sert uniquement à déverrouiller la lecture.
    ecrire_wav_silence = DOSSIER_WAV / "silence.wav"
    with wave.open(str(ecrire_wav_silence), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
        w.writeframes(b"\0" * 3200)
    for s in choix:
        ecrire_wav(s["f32"], DOSSIER_WAV / f"{s['id']}.wav")

    serveur = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    subprocess.run(["adb", "-s", dev, "reverse", f"tcp:{PORT}", f"tcp:{PORT}"],
                   capture_output=True)

    print(f"📱 {dev} — {len(choix)} tranches, "
          f"{sum(s['dur'] for s in choix):.0f} s d'audio")
    adb(dev, "shell", "media", "volume", "--stream", "3", "--set", "15")
    adb(dev, "shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    adb(dev, "shell", "am", "start", "-a", "android.intent.action.VIEW",
        "-d", f"http://localhost:{PORT}/",
        "-n", "com.android.chrome/com.google.android.apps.chrome.Main")
    time.sleep(6)
    assurer_chrome(dev)
    # Relancer l'intent sur un onglet déjà ouvert donne le focus à la barre
    # d'adresse et non à la page. Échap la relâche ; surtout pas « retour »,
    # qui ferme l'onglet et ramène l'application précédente sous les appuis
    # suivants.
    adb(dev, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    if 'id/url_bar' in adb(dev, "shell", "cat", "/sdcard/ui.xml").split('focused="true"')[0][-400:]:
        adb(dev, "shell", "input", "keyevent", "111")
        time.sleep(1.0)

    # Geste utilisateur : le bouton « Démarrer » débloque la lecture audio.
    # Chrome n'autorise le son que si un geste a démarré cet élément-là une
    # fois ; ensuite les lectures programmées passent.
    taille = adb(dev, "shell", "wm", "size")
    larg, haut = map(int, re.search(r"(\d+)x(\d+)", taille).groups())
    taper(dev, larg * 0.28, haut * 0.285)
    time.sleep(2)
    if not ETAT.depuis(0, "ready"):
        print("⚠️  la page n'a pas confirmé le déverrouillage audio "
              "(bouton mal visé, ou lecture refusée)")

    # Focus dans le textarea : ouvre le clavier.
    taper(dev, larg // 2, haut * 0.17)
    time.sleep(2.5)
    # Le bouton micro se trouve d'abord dans l'arbre d'accessibilité ; certaines
    # surcouches (Samsung One UI 4 ici) n'exposent pas la fenêtre du clavier à
    # `uiautomator dump`, d'où le repli géométrique : le micro occupe le coin
    # droit de la rangée de suggestions, dont la hauteur est fixée par le
    # thème. Vérifier sur une capture avant de faire confiance au repli.
    micro = tuple(int(v) for v in args.micro.split(",")) if args.micro \
        else bornes_micro(dev)
    if micro is None:
        micro = (int(larg * 0.945), int(haut * 0.6325))
        print(f"⚠️  micro absent de l'arbre d'accessibilité — repli géométrique {micro}")
    print(f"🎙️  micro en {micro}")

    lignes = []
    for n, s in enumerate(choix, 1):
        ETAT.envoyer("clear")
        time.sleep(0.8)
        marque = ETAT.marque()
        taper(dev, micro[0], micro[1])
        time.sleep(1.6)                       # ouverture WebSocket + micro
        ETAT.envoyer("play", url=f"/{s['id']}.wav", clip=s["id"])

        debut = fin = None          # estampilles page (horloge du téléphone)
        t_fin_hote = None           # même instant, vu du poste
        n_textes, t_dernier_texte = 0, time.time()
        limite = time.time() + args.attente_max
        while time.time() < limite:
            if debut is None:
                e = ETAT.depuis(marque, "audio_start")
                if e:
                    debut = e[0]["t"]
            if fin is None:
                e = ETAT.depuis(marque, "audio_end")
                if e:
                    fin, t_fin_hote = e[0]["t"], time.time()
            textes = ETAT.depuis(marque, "texte")
            if len(textes) != n_textes:
                n_textes, t_dernier_texte = len(textes), time.time()
            # Le VAD coupe à 1,5 s de silence, le service rend ensuite sa passe
            # finale : on laisse passer 5 s après l'audio, puis on conclut dès
            # que le texte s'est tu pendant 3 s.
            if t_fin_hote and time.time() - t_fin_hote > 5.0 \
                    and time.time() - t_dernier_texte > 3.0:
                break
            time.sleep(0.2)

        textes = [e for e in ETAT.depuis(marque, "texte") if e.get("v")]
        hyp = textes[-1]["v"].strip() if textes else ""
        premier = (textes[0]["t"] - debut) / 1000 if textes and debut else None
        w, mots = wer_infixe(refs[s["parent"]], hyp) if hyp else (1.0, 0)
        lignes.append({
            "id": s["id"], "dur": s["dur"], "wer": w, "mots": mots,
            "hyp": hyp, "passes": len(textes),
            "t_premier_texte": premier,
            "t_final_apres_audio": (textes[-1]["t"] - fin) / 1000 if textes and fin else None,
        })
        etat = f"WER {w*100:5.1f} %" if hyp else "AUCUN TEXTE"
        print(f"  [{n:2d}/{len(choix)}] {s['id']} {s['dur']:4.1f}s  {etat}  "
              f"{len(textes)} maj  « {hyp[:60]} »", flush=True)

    args.out.write_text(json.dumps(lignes, ensure_ascii=False, indent=1), encoding="utf-8")
    utiles = [l for l in lignes if l["mots"]]
    if utiles:
        pond = sum(l["wer"] * l["mots"] for l in utiles) / sum(l["mots"] for l in utiles)
        print(f"\nWER pondéré {pond*100:.1f} % sur {len(utiles)}/{len(lignes)} tranches")
    print(f"💾 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
