#!/usr/bin/env python3
"""Petits utilitaires adb : dump de l'arbre UI, recherche de noeud, tap."""
import subprocess, re, sys, xml.etree.ElementTree as ET, time

def sh(*a, timeout=60):
    return subprocess.run(["adb"]+list(a), capture_output=True, text=True, timeout=timeout).stdout

def dump():
    for _ in range(3):
        out = sh("exec-out", "uiautomator", "dump", "/dev/tty")
        i = out.find("<?xml")
        if i >= 0:
            xml = out[i:]
            j = xml.rfind("</hierarchy>")
            if j > 0:
                return xml[:j+len("</hierarchy>")]
        time.sleep(0.6)
    return ""

def nodes(xml):
    out = []
    if not xml: return out
    try: root = ET.fromstring(xml)
    except ET.ParseError: return out
    for n in root.iter("node"):
        a = n.attrib
        b = re.findall(r"\d+", a.get("bounds",""))
        if len(b) != 4: continue
        x1,y1,x2,y2 = map(int,b)
        out.append({"text": a.get("text",""), "desc": a.get("content-desc",""),
                    "cls": a.get("class",""), "pkg": a.get("package",""),
                    "res": a.get("resource-id",""), "cx": (x1+x2)//2, "cy": (y1+y2)//2,
                    "box": (x1,y1,x2,y2)})
    return out

def tap(x,y):
    sh("shell","input","tap",str(x),str(y)); time.sleep(0.5)

if __name__ == "__main__":
    ns = nodes(dump())
    pat = sys.argv[1] if len(sys.argv)>1 else ""
    for n in ns:
        if not pat or pat.lower() in (n["text"]+" "+n["desc"]+" "+n["res"]).lower():
            print(f'{n["box"]} pkg={n["pkg"]} txt={n["text"]!r} desc={n["desc"]!r} res={n["res"].split("/")[-1]}')
