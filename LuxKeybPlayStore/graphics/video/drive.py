import subprocess, json, time, sys, re, pathlib
S = pathlib.Path(__file__).parent
KEYS = {}
rows = {"qwertzuiop": 1864, "asdfghjkl": 2000, "yxcvbnm": 2136}
xs1 = [68,162,256,350,444,538,632,726,821,916]
for i,c in enumerate("qwertzuiop"): KEYS[c]=(xs1[i],1864)
for i,c in enumerate("asdfghjkl"): KEYS[c]=(xs1[i],2000)
for i,c in enumerate("yxcvbnm"): KEYS[c]=(251+96*i,2136)
KEYS[" "]=(450,2271)
def adb(*a): subprocess.run(["adb","-s","emulator-5554",*a],check=True,stdout=subprocess.DEVNULL)
def shot(path): 
    with open(path,"wb") as f: subprocess.run(["adb","-s","emulator-5554","exec-out","screencap","-p"],stdout=f,check=True)
class Rec:
    def __init__(self, name):
        self.dir = S/name; self.dir.mkdir(exist_ok=True); self.n=0; self.log=[]
    def snap(self, tap=None, hold=False):
        p = self.dir/f"{self.n:03d}.png"; shot(p); self.log.append({"img":str(p),"tap":tap,"hold":hold}); self.n+=1
    def tap(self, x, y, wait=0.5):
        adb("shell","input","tap",str(x),str(y)); time.sleep(wait); self.snap(tap=[x,y])
    def type(self, text):
        for c in text: self.tap(*KEYS[c.lower()])
    def save(self): (self.dir/"log.json").write_text(json.dumps(self.log,indent=1))
def chip(regex):
    adb("shell","uiautomator","dump","--windows","/sdcard/w.xml")
    x=subprocess.run(["adb","shell","cat","/sdcard/w.xml"],capture_output=True,text=True).stdout
    for m in re.finditer(r'<node [^>]*>',x):
        n=m.group(0); t=re.search(r' text="([^"]*)"',n).group(1)
        if re.fullmatch(regex,t):
            a,b,c,d=map(int,re.findall(r'\d+',re.search(r'bounds="([^"]*)"',n).group(1)))
            if b>1500: return (a+c)//2,(b+d)//2
    return None
