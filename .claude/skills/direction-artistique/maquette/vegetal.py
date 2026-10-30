import json,sys
# Les palettes de moteur.js, et le test de Habillage.fond : distance < 30 à l'un des trois segments.
B=[dict(loin=0xB3C3C4,milieu=0x8AA392,pres=0x5D7964),dict(loin=0xD6B387,milieu=0xAA895A,pres=0x735C39),
   dict(loin=0x8D6A8A,milieu=0x5D4766,pres=0x392C46),dict(loin=0x27355B,milieu=0x1A2445,pres=0x111930)]
rgb=lambda c:((c>>16)&255,(c>>8)&255,c&255)
def mix(a,b,t):return tuple(round(x+(y-x)*t) for x,y in zip(a,b))
def dist(p,a,b):
    u=[b[i]-a[i] for i in range(3)];uu=sum(x*x for x in u) or 1
    t=max(0,min(1,sum((p[i]-a[i])*u[i] for i in range(3))/uu))
    return sum((a[i]+u[i]*t-p[i])**2 for i in range(3))**.5
coul=json.load(open('couleurs.json'))
for h,P in enumerate(B):
    m,pr,lo=rgb(P['milieu']),rgb(P['pres']),rgb(P['loin'])
    segs=[(m,pr),(lo,m),(mix(m,rgb(0x6F8F3E),.35),mix(m,rgb(0x4F7D3A),.35))]
    vus=set()
    for plan,c in coul[h]:
        c=c&0xFFFFFF
        if (plan,c) in vus:continue
        vus.add((plan,c))
        d=min(dist(rgb(c),a,b) for a,b in segs)
        if d<30:print(f'heure {h} plan {plan} #{c:06X} végétal (d={d:.0f})')
print('fin')
