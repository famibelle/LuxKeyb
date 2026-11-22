#!/bin/bash
# planche_automne.sh <dossier> <LIEU> [<dossier de référence>]
#
# Rend les quatre saisons d'un lieu aux quatre heures (la rareté donne
# l'heure), puis écrit dans <dossier> :
#   automne_heures.png  l'automne en 2 × 2 : matin, après-midi / soir, nuit ;
#   zoom_x2.png         l'automne agrandi deux fois, une heure par rangée.
# Avec un dossier de référence (un rendu d'avant la retouche), compte les
# pixels changés au printemps, en été et en hiver : ils doivent rester à 0.
set -e
D=$1; L=$2; REF=$3
cd /home/medhi/SourceCode/LuxKeyb/android_keyboard
rm -rf "$D"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest \
  --tests "*PlancheCartesTest.saisons" -Pplanche="$D" -PplancheSaisons \
  -PplancheLieux="$L" -PplancheRarete=TOUTES --rerun -q 2>&1 \
  | grep -iv "keystore\|password\|alias\|signing" | tail -15
python3 - "$D" "$REF" <<'EOF'
import sys
from PIL import Image
d, ref = sys.argv[1], sys.argv[2]
im = Image.open(f"{d}/planche_saisons.png").convert("RGB")
w, h = im.size[0] // 4, 490
automne = im.crop((2 * w, 0, 3 * w, 4 * h))
m = Image.new("RGB", (2 * w, 2 * h), "white")
for k in range(4):
    m.paste(automne.crop((0, k * h, w, (k + 1) * h)), ((k % 2) * w, (k // 2) * h))
m.save(f"{d}/automne_heures.png")
automne.resize((w * 2, 8 * h), Image.LANCZOS).save(f"{d}/zoom_x2.png")
if ref:
    a = Image.open(f"{ref}/planche_saisons.png").convert("RGB")
    for col, nom in ((0, "printemps"), (1, "été"), (3, "hiver")):
        x = a.crop((col * w, 0, (col + 1) * w, 4 * h)).tobytes()
        y = im.crop((col * w, 0, (col + 1) * w, 4 * h)).tobytes()
        print(f"{nom} : {sum(p != q for p, q in zip(x, y))} octets changés")
EOF
