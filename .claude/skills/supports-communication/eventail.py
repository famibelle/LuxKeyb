from PIL import Image, ImageDraw, ImageFilter
# Les quatre cartes de l'étain à l'or, capturées sur l'émulateur le 2026-10-31
# (dessins du domaine public) ; celles de la newsletter d'octobre restent à leur place.
D='docs/assets/cartes/'
k=0.64
cartes=[('bam',-9,175,268),('sonn',-4,400,252),('brell',3,610,250),('kran',9,835,268)]
W,H=1100,640; ox,oy=40,60
fond=Image.new('RGBA',(W,H),(0,0,0,0))
for n,a,cx,cy in cartes:
    im=Image.open(D+f'carte_{n}.webp').convert('RGBA')
    m=Image.new('L',im.size,0); ImageDraw.Draw(m).rounded_rectangle([0,0,im.width-1,im.height-1],30,fill=255)
    im.putalpha(m)
    im=im.resize((round(im.width*k),round(im.height*k)),Image.LANCZOS)
    r=im.rotate(-a,expand=True,resample=Image.BICUBIC)
    x,y=ox+cx-r.width//2, oy+cy-r.height//2
    om=Image.new('RGBA',r.size,(0,0,0,0)); om.putalpha(r.getchannel('A').point(lambda v:v*0.35))
    om=om.filter(ImageFilter.GaussianBlur(8))
    fond.alpha_composite(om,(x+4,y+8)); fond.alpha_composite(r,(x,y))
b=fond.getchannel('A').point(lambda v:255 if v>8 else 0).getbbox()
fond=fond.crop((b[0]-4,b[1]-4,b[2]+4,b[3]+4))
fond.save('docs/assets/cartes-eventail.png',optimize=True); print(fond.size)
