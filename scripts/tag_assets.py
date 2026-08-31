#!/usr/bin/env python3
"""Inscrit la marque Potomitan™ dans les métadonnées des visuels du dépôt.

Un visuel finit toujours par circuler seul : téléchargé d'une page, réexporté
par une régie publicitaire, transmis à un journaliste. Ses métadonnées sont
alors la seule chose qui dise encore d'où il vient. Ce script y écrit l'auteur,
le copyright et le nom du produit, dans les trois emplacements que les outils
savent lire :

  * PNG  : chunks iTXt (UTF-8) + paquet XMP
  * JPEG : segment APP1 Exif + APP1 XMP + commentaire COM
  * SVG  : <title>, <desc> et un bloc <metadata> RDF

Rien n'est réencodé. Les chunks PNG et les segments JPEG sont réécrits au
niveau de l'octet, autour des données d'image laissées intactes : repasser le
script sur un JPEG ne le dégrade pas, contrairement à un simple
`Image.open().save()`. Il est aussi idempotent : ses propres chunks et
segments sont retirés avant d'être réécrits.

    python3 scripts/tag_assets.py            # marque tout ce qui doit l'être
    python3 scripts/tag_assets.py --check    # signale ce qui manque, n'écrit rien
    python3 scripts/tag_assets.py docs/assets/ads/export   # restreint aux chemins donnés

À relancer après chaque export de visuels (`docs/assets/ads/generate.py`), qui
produit des PNG neufs et donc sans métadonnées.
"""

import struct
import sys
import zlib
from datetime import date
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]

# Les dossiers qui contiennent des visuels de marque. Volontairement étroit :
# `docs/assets` et `docs/Screenshots` sont un mélange, où subsistent des
# visuels hérités du projet jumeau en créole guadeloupéen. Y apposer « Visuel
# de Lëtzebuergesch Clavier » les attribuerait au mauvais produit. Les chemins
# passés en argument restent traités quoi qu'il arrive, ce dont se sert
# docs/assets/ads/generate.py après chaque export.
DOSSIERS = [
    "docs/assets/ads/export",
    "docs/assets/logo",
]

# Visuels qui ne nous appartiennent pas : y apposer notre copyright serait faux.
# Les badges Google Play sont des marques de Google.
TIERS = (
    "GetItOnGooglePlay",
    "PreRegisterOnGooglePlay",
)

EXTENSIONS = {".png", ".jpg", ".jpeg", ".svg", ".pdf"}

MARQUE = "Potomitan™"
PRODUIT = "Lëtzebuergesch Clavier"
ANNEE = date.today().year
AUTEUR = MARQUE
COPYRIGHT = f"© {ANNEE} {MARQUE} · {PRODUIT}"
DESCRIPTION = f"Visuel de {PRODUIT}, le clavier luxembourgeois publié par {MARQUE}."
SITE = "https://potomitan.io/"
OUTIL = "scripts/tag_assets.py · Potomitan™"

# Écrit dans chaque paquet XMP : c'est à cette chaîne que le script reconnaît
# son propre travail quand il repasse sur un fichier.
SIGNATURE = "potomitan:tag_assets"


def titre(chemin: Path) -> str:
    """Titre lisible : le nom du produit, puis celui du fichier."""
    return f"{PRODUIT} · {chemin.stem}"


def xmp(chemin: Path) -> bytes:
    """Paquet XMP. C'est le seul des trois emplacements dont l'encodage UTF-8
    est garanti par la spécification, donc celui qui porte le « ™ » sans
    dépendre de la bonne volonté du lecteur."""
    return f"""<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="{SIGNATURE}">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
    xmlns:xmpRights="http://ns.adobe.com/xap/1.0/rights/"
    xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/">
   <dc:title><rdf:Alt><rdf:li xml:lang="x-default">{titre(chemin)}</rdf:li></rdf:Alt></dc:title>
   <dc:creator><rdf:Seq><rdf:li>{AUTEUR}</rdf:li></rdf:Seq></dc:creator>
   <dc:rights><rdf:Alt><rdf:li xml:lang="x-default">{COPYRIGHT}</rdf:li></rdf:Alt></dc:rights>
   <dc:description><rdf:Alt><rdf:li xml:lang="x-default">{DESCRIPTION}</rdf:li></rdf:Alt></dc:description>
   <dc:publisher><rdf:Bag><rdf:li>{MARQUE}</rdf:li></rdf:Bag></dc:publisher>
   <photoshop:Credit>{MARQUE}</photoshop:Credit>
   <xmpRights:Marked>True</xmpRights:Marked>
   <xmpRights:WebStatement>{SITE}</xmpRights:WebStatement>
   <xmp:CreatorTool>{OUTIL}</xmp:CreatorTool>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>""".encode("utf-8")


# ---------------------------------------------------------------------------
# PNG : chunks
# ---------------------------------------------------------------------------

SIGNATURE_PNG = b"\x89PNG\r\n\x1a\n"
CLES_PNG = ["Title", "Author", "Copyright", "Description", "Source", "Software"]
CLE_XMP_PNG = b"XML:com.adobe.xmp"


def chunks_png(data: bytes):
    """Découpe un PNG en (type, contenu). Le CRC est recalculé à l'écriture."""
    pos = len(SIGNATURE_PNG)
    while pos < len(data):
        (taille,) = struct.unpack(">I", data[pos:pos + 4])
        type_ = data[pos + 4:pos + 8]
        yield type_, data[pos + 8:pos + 8 + taille]
        pos += 12 + taille


def chunk(type_: bytes, contenu: bytes) -> bytes:
    return (struct.pack(">I", len(contenu)) + type_ + contenu
            + struct.pack(">I", zlib.crc32(type_ + contenu) & 0xFFFFFFFF))


def itxt(cle: bytes, texte: str) -> bytes:
    """Chunk iTXt : l'unique forme de texte PNG dont le contenu est en UTF-8.
    tEXt et zTXt sont en Latin-1, qui ne contient pas le « ™ »."""
    return chunk(b"iTXt", cle + b"\x00\x00\x00" + b"\x00" + b"\x00" + texte.encode("utf-8"))


def marquer_png(data: bytes, chemin: Path) -> bytes:
    nos_cles = {c.encode("ascii") for c in CLES_PNG} | {CLE_XMP_PNG}
    entete, corps = [], []
    for type_, contenu in chunks_png(data):
        if type_ in (b"tEXt", b"zTXt", b"iTXt") and contenu.split(b"\x00", 1)[0] in nos_cles:
            continue  # une exécution précédente : on la remplace
        (entete if type_ == b"IHDR" else corps).append(chunk(type_, contenu))

    valeurs = {
        "Title": titre(chemin), "Author": AUTEUR, "Copyright": COPYRIGHT,
        "Description": DESCRIPTION, "Source": SITE, "Software": OUTIL,
    }
    textes = [itxt(c.encode("ascii"), valeurs[c]) for c in CLES_PNG]
    textes.append(itxt(CLE_XMP_PNG, xmp(chemin).decode("utf-8")))
    return SIGNATURE_PNG + b"".join(entete) + b"".join(textes) + b"".join(corps)


# ---------------------------------------------------------------------------
# JPEG : segments
# ---------------------------------------------------------------------------

PREFIXE_EXIF = b"Exif\x00\x00"
PREFIXE_XMP = b"http://ns.adobe.com/xap/1.0/\x00"


def exif() -> bytes:
    """Bloc TIFF des tags Exif, construit par Pillow mais écrit par nous.

    Les champs ASCII reçoivent des octets UTF-8 : Pillow, à qui on passerait
    une chaîne, remplacerait le « ™ » par un point d'interrogation. Les tags
    XP* de Windows, eux, sont en UTF-16 par définition.
    """
    from PIL import Image

    tags = Image.Exif()
    tags[0x010E] = DESCRIPTION.encode("utf-8")   # ImageDescription
    tags[0x013B] = AUTEUR.encode("utf-8")        # Artist
    tags[0x8298] = COPYRIGHT.encode("utf-8")     # Copyright
    tags[0x0131] = OUTIL.encode("utf-8")         # Software
    tags[0x9C9B] = PRODUIT.encode("utf-16-le") + b"\x00\x00"   # XPTitle
    tags[0x9C9D] = AUTEUR.encode("utf-16-le") + b"\x00\x00"    # XPAuthor
    tags[0x9C9C] = COPYRIGHT.encode("utf-16-le") + b"\x00\x00"  # XPComment
    return tags.tobytes()


def segments_jpeg(data: bytes):
    """Parcourt les segments jusqu'au début des données compressées.

    Rend (marqueur, segment complet). Le dernier élément rendu est le bloc
    entropique à partir de SOS : il n'est jamais touché, c'est l'image.
    """
    pos = 2  # après SOI
    while pos < len(data):
        if data[pos] != 0xFF:
            break
        marqueur = data[pos + 1]
        if marqueur == 0xDA:  # SOS : tout ce qui suit est l'image
            yield None, data[pos:]
            return
        (taille,) = struct.unpack(">H", data[pos + 2:pos + 4])
        yield marqueur, data[pos:pos + 2 + taille]
        pos += 2 + taille
    yield None, data[pos:]


def segment(marqueur: int, contenu: bytes) -> bytes:
    return bytes([0xFF, marqueur]) + struct.pack(">H", len(contenu) + 2) + contenu


def marquer_jpeg(data: bytes, chemin: Path) -> bytes:
    tete, reste = [], []
    for marqueur, brut in segments_jpeg(data):
        if marqueur == 0xE1 and (brut[4:].startswith(PREFIXE_EXIF)
                                 or brut[4:].startswith(PREFIXE_XMP)):
            continue  # Exif ou XMP d'une exécution précédente
        if marqueur == 0xFE:  # COM
            continue
        # APP0 (JFIF) doit rester en tête : nos segments se glissent juste après.
        (tete if marqueur == 0xE0 else reste).append(brut)

    nos_segments = [
        segment(0xE1, PREFIXE_EXIF + exif()),
        segment(0xE1, PREFIXE_XMP + xmp(chemin)),
        segment(0xFE, f"{COPYRIGHT} · {SITE}".encode("utf-8")),
    ]
    return b"\xff\xd8" + b"".join(tete) + b"".join(nos_segments) + b"".join(reste)


# ---------------------------------------------------------------------------
# SVG : balises
# ---------------------------------------------------------------------------

def marquer_svg(texte: str, chemin: Path) -> str:
    # Un <metadata> déjà posé par le script est retiré, avec l'indentation qui
    # le précède : sinon chaque passage empilerait un bloc de plus, ou au moins
    # une ligne vide de plus, et le fichier ne se stabiliserait jamais.
    debut = texte.find("<metadata>")
    if debut != -1 and SIGNATURE in texte:
        fin = texte.find("</metadata>", debut)
        if fin != -1:
            fin += len("</metadata>")
            while debut and texte[debut - 1] in " \t":
                debut -= 1
            if debut and texte[debut - 1] == "\n":
                debut -= 1
            texte = texte[:debut] + texte[fin:]

    ouverture = texte.find(">", texte.find("<svg"))
    if ouverture == -1:
        raise ValueError(f"{chemin} : pas de balise <svg>")
    apres = ouverture + 1

    bloc = f"""
  <metadata>
    <!-- {SIGNATURE} -->
    <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
             xmlns:dc="http://purl.org/dc/elements/1.1/"
             xmlns:cc="http://creativecommons.org/ns#">
      <cc:Work rdf:about="">
        <dc:title>{titre(chemin)}</dc:title>
        <dc:creator><cc:Agent><dc:title>{AUTEUR}</dc:title></cc:Agent></dc:creator>
        <dc:publisher><cc:Agent><dc:title>{MARQUE}</dc:title></cc:Agent></dc:publisher>
        <dc:rights><cc:Agent><dc:title>{COPYRIGHT}</dc:title></cc:Agent></dc:rights>
        <dc:description>{DESCRIPTION}</dc:description>
        <dc:source>{SITE}</dc:source>
      </cc:Work>
    </rdf:RDF>
  </metadata>"""

    # <title> et <desc> existants : on n'y touche pas. Sur les cartes, le
    # <title> est le nom accessible de l'image, le remplacer changerait ce que
    # lit un lecteur d'écran. L'ordre title puis desc est celui qu'attendent
    # ces lecteurs.
    ajouts = ""
    if "<title>" not in texte:
        ajouts += f"\n  <title>{titre(chemin)}</title>"
    if "<desc>" not in texte:
        ajouts += f"\n  <desc>{DESCRIPTION}</desc>"
    texte = texte[:apres] + ajouts + texte[apres:]

    # Le bloc se pose après le titre et la description, jamais avant : c'est ce
    # qui rend le résultat identique au premier passage comme au dixième.
    ancre = apres + len(ajouts)
    for balise in ("</title>", "</desc>"):
        trouve = texte.find(balise)
        if trouve != -1:
            ancre = max(ancre, trouve + len(balise))
    return texte[:ancre] + bloc + texte[ancre:]


# ---------------------------------------------------------------------------
# PDF : dictionnaire /Info et paquet XMP
# ---------------------------------------------------------------------------

def marquer_pdf(chemin: Path) -> bool:
    """Écrit l'auteur et le copyright dans les tracts et affiches à imprimer.

    Rend False si pikepdf manque : le reste des visuels se marque quand même,
    et le message final le signale. `pip install pikepdf` pour l'activer.
    """
    try:
        import pikepdf
    except ImportError:
        return False

    with pikepdf.open(chemin, allow_overwriting_input=True) as pdf:
        # Le titre posé par Chrome à l'export vient du <title> de la page :
        # « Tract à imprimer · Klavyé Kréyòl Karukera » dit mieux ce qu'est le
        # document que le nom de fichier. On le garde.
        titre_pdf = str(pdf.docinfo.get("/Title", "")) or titre(chemin)
        with pdf.open_metadata(set_pikepdf_as_editor=False) as meta:
            meta["dc:title"] = titre_pdf
            meta["dc:creator"] = [AUTEUR]
            meta["dc:rights"] = COPYRIGHT
            meta["dc:description"] = DESCRIPTION
            meta["dc:publisher"] = [MARQUE]
            meta["xmp:CreatorTool"] = OUTIL
            meta["pdf:Keywords"] = f"{PRODUIT}, {MARQUE}, kréyòl, Guadeloupe, clavier"
        # Le dictionnaire /Info reste ce que lisent les visionneuses et les
        # imprimeurs, bien après que XMP soit devenu la référence.
        pdf.docinfo["/Title"] = titre_pdf
        pdf.docinfo["/Author"] = AUTEUR
        pdf.docinfo["/Subject"] = DESCRIPTION
        pdf.docinfo["/Creator"] = MARQUE
        pdf.docinfo["/Keywords"] = f"{PRODUIT}, {MARQUE}, {COPYRIGHT}"
        pdf.save(chemin)
    return True


# ---------------------------------------------------------------------------

def a_marquer(chemin: Path) -> bool:
    return (chemin.suffix.lower() in EXTENSIONS
            and not any(t in chemin.name for t in TIERS))


def deja_marque(chemin: Path) -> bool:
    if chemin.suffix.lower() == ".pdf":
        # Les métadonnées d'un PDF vivent dans des objets compressés : les
        # chercher dans les octets bruts ne donnerait rien.
        try:
            import pikepdf
        except ImportError:
            return True  # rien à reprocher au fichier si l'outil manque
        with pikepdf.open(chemin) as pdf:
            return str(pdf.docinfo.get("/Author", "")) == AUTEUR
    return SIGNATURE.encode("utf-8") in chemin.read_bytes()


def main() -> int:
    args = sys.argv[1:]
    verifier = "--check" in args
    cibles = [a for a in args if not a.startswith("-")] or DOSSIERS

    fichiers, ecartes = [], []
    for cible in cibles:
        chemin = RACINE / cible
        candidats = sorted(chemin.rglob("*")) if chemin.is_dir() else [chemin]
        for f in candidats:
            if not f.is_file() or f.suffix.lower() not in EXTENSIONS:
                continue
            (fichiers if a_marquer(f) else ecartes).append(f)

    if not fichiers:
        sys.exit(f"Aucun visuel à marquer dans {cibles}.")

    manquants, marques, sans_pikepdf = [], 0, 0
    for f in fichiers:
        if verifier:
            if not deja_marque(f):
                manquants.append(f)
            continue
        suffixe = f.suffix.lower()
        if suffixe == ".svg":
            texte = f.read_text(encoding="utf-8")
            f.write_text(marquer_svg(texte, f), encoding="utf-8")
        elif suffixe == ".pdf":
            if not marquer_pdf(f):
                sans_pikepdf += 1
                continue
        else:
            data = f.read_bytes()
            sortie = (marquer_png(data, f) if data.startswith(SIGNATURE_PNG)
                      else marquer_jpeg(data, f))
            f.write_bytes(sortie)
        marques += 1

    if verifier:
        for f in manquants:
            print(f"  sans métadonnées : {f.relative_to(RACINE)}")
        print(f"\n{len(fichiers) - len(manquants)}/{len(fichiers)} visuels portent {MARQUE}.")
        return 1 if manquants else 0

    print(f"{marques} visuels marqués {MARQUE} ({ANNEE}).")
    if sans_pikepdf:
        print(f"{sans_pikepdf} PDF laissés de côté : pip install pikepdf")
    if ecartes:
        print("Écartés, visuels de tiers :")
        for f in ecartes:
            print(f"  {f.relative_to(RACINE)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
