#!/usr/bin/env python3
"""Utilitaires adb pour le téléphone (Galaxy A21s en Wi-Fi)."""
import subprocess, time, io, re, xml.etree.ElementTree as ET
from PIL import Image

SERIE = "192.168.1.37:46271"

def adb(*a, timeout=120):
    return subprocess.run(["adb", "-s", SERIE, *a], capture_output=True, timeout=timeout)

def shell(*a, timeout=120):
    return adb("shell", *a, timeout=timeout)

def dump():
    for _ in range(3):
        out = adb("exec-out", "uiautomator", "dump", "/dev/tty").stdout.decode("utf-8", "replace")
        i, j = out.find("<?xml"), out.rfind("</hierarchy>")
        if i >= 0 and j > 0:
            return out[i:j+12]
        time.sleep(0.8)
    return ""

def noeuds(xml=None):
    xml = xml if xml is not None else dump()
    out = []
    if not xml: return out
    try: root = ET.fromstring(xml)
    except ET.ParseError: return out
    for n in root.iter("node"):
        a = n.attrib
        b = re.findall(r"\d+", a.get("bounds", ""))
        if len(b) != 4: continue
        x1, y1, x2, y2 = map(int, b)
        out.append({"text": a.get("text",""), "desc": a.get("content-desc",""),
                    "cls": a.get("class",""), "res": a.get("resource-id",""),
                    "checked": a.get("checked",""), "pkg": a.get("package",""),
                    "cx": (x1+x2)//2, "cy": (y1+y2)//2, "box": (x1,y1,x2,y2)})
    return out

def tap(x, y, pause=0.6):
    shell("input", "tap", str(x), str(y)); time.sleep(pause)

def capture(bande=None):
    im = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p").stdout))
    return im.crop(bande) if bande else im

def trouver(motif, ns=None):
    for n in (ns or noeuds()):
        if motif.lower() in (n["text"] + " " + n["desc"]).lower():
            return n
    return None
