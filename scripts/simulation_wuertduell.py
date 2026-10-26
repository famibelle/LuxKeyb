#!/usr/bin/env python3
"""Simule des parties de Wuertduell (docs/regle-du-jeu.md) pour régler la taille
des paquets : tours par partie, cartes utilisées, pioches épuisées.

Joueurs automatiques gloutons, vrais mots du dictionnaire (nature lue au LOD).
À lancer depuis la racine du dépôt : python3 scripts/simulation_wuertduell.py
"""
import json, random, statistics as st, sys
import os
A=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','android_keyboard/app/src/main/assets/')
dico=json.load(open(A+'luxemburgish_dict.json'))
cat=json.load(open(A+'luxemburgish_categories.json'))['categories']
# vivier : les 3 000 mots les plus fréquents, par nature LOD
viv={'N':[], 'V':[], 'A':[]}
for m,_ in dico[:6000]:
    c=cat.get(m,'')
    if c.startswith('SUBST'): viv['N'].append(len(m))
    elif c.startswith('VRB'): viv['V'].append(len(m))
    elif c.startswith('ADJ'): viv['A'].append(len(m))
def cout(l): return 1 if l<=4 else 2 if l<=7 else 3 if l<=10 else 4
def carte(t):
    if t=='Z': return ('Z',0,0,0)
    l=random.choice(viv[t]); return (t,l,cout(l),random.randrange(9))
def paquet(n):
    comp=['N']*round(n*.55)+['V']*round(n*.2)+['A']*round(n*.1)
    comp+=['Z']*(n-len(comp))
    p=[carte(t) for t in comp]; random.shuffle(p); return p
class J:
    def __init__(s,n):
        s.pioche=paquet(n); s.tresor=[s.pioche.pop() for _ in range(5)]
        s.main=[s.pioche.pop() for _ in range(5)]; s.reserve=0; s.terrain=[]; s.vide=False; s.pioches=0
def meme_famille(a,b):
    # Une famille est un genre, ou l'ensemble des genres d'un nom qui en a deux.
    return bool(a & b) if isinstance(a, frozenset) else a==b
def force(j,x):  # x = [carte, bonus_adj, pret]
    f=x[0][1]+x[1]
    if sum(1 for y in j.terrain if meme_famille(y[0][3],x[0][3]))>=2: f+=1
    return f
def tour(j,adv):
    if j.pioche: j.main.append(j.pioche.pop()); j.pioches+=1
    else: j.vide=True
    for x in j.terrain: x[2]=True
    # réserve : la carte la moins utile, jusqu'à 5
    if j.reserve<5 and j.main:
        nonz=[c for c in j.main if c[0]!='Z'] or j.main
        c=min(nonz,key=lambda c:(c[0]=='N',c[1])) if j.reserve<4 else min(nonz,key=lambda c:c[1])
        j.main.remove(c); j.reserve+=1
    s=j.reserve
    for c in [c for c in j.main if c[0]=='Z']: j.main.remove(c); s+=1
    for c in sorted([c for c in j.main if c[0]=='N'],key=lambda c:-c[1]):
        if len(j.terrain)<3 and c[2]<=s: j.main.remove(c); s-=c[2]; j.terrain.append([c,0,False])
    for c in [c for c in j.main if c[0]=='A']:
        if j.terrain and c[2]<=s: j.main.remove(c); s-=c[2]; max(j.terrain,key=lambda x:x[0][1]+x[1])[1]+=2
    verbes=[c for c in j.main if c[0]=='V']
    for x in [x for x in j.terrain if x[2]]:
        fa=force(j,x)+1  # +1 : le sens dit en français
        bloq=[y for y in adv.terrain if force(adv,y)>=fa] or ([max(adv.terrain,key=lambda y:force(adv,y))] if adv.terrain and len(adv.tresor)<=1 else [])
        if bloq:
            b=min(bloq,key=lambda y:force(adv,y)); fb=force(adv,b)
            if fb>=fa and verbes and verbes[0][2]<=s and fa+3>fb:
                v=verbes.pop(0); j.main.remove(v); s-=v[2]; fa+=3
            if fa>fb: adv.terrain.remove(b)
            elif fa<fb: j.terrain.remove(x)
            else: adv.terrain.remove(b); j.terrain.remove(x)
        elif adv.tresor: adv.tresor.pop()
        if not adv.tresor: return True
    return False
def partie(n, paquet_a=None, paquet_b=None):
    """Une partie ; [paquet_a] et [paquet_b] : listes de cartes (type, force,
    coût, lieu) imposées au lieu de paquets tirés au hasard."""
    a,b=J(n),J(n)
    for j,p in ((a,paquet_a),(b,paquet_b)):
        if p is not None:
            j.pioche=list(p); random.shuffle(j.pioche)
            j.tresor=[j.pioche.pop() for _ in range(5)]
            j.main=[j.pioche.pop() for _ in range(5)]
    t=0
    while t<200:
        t+=1
        if tour(a,b): return t,a,b
        if tour(b,a): return t,a,b
    return t,a,b
def gagnant(t,a,b):
    """'a', 'b' ou None (partie interrompue)."""
    if not b.tresor: return 'a'
    if not a.tresor: return 'b'
    return None

if __name__ == '__main__':
  random.seed(1)
  for n in (20,25,26,30,40):
    R=[partie(n) for _ in range(4000)]
    tours=[r[0] for r in R]; pi=[max(r[1].pioches,r[2].pioches) for r in R]
    vide=sum(r[1].vide or r[2].vide for r in R)/len(R)
    print(f"paquet {n}: tours médiane {st.median(tours)} (90% {sorted(tours)[int(.9*len(R))]}) · cartes utilisées/joueur médiane {10+st.median(pi)} · pioche épuisée {vide:.0%}")
