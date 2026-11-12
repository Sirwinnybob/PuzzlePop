"""Generates the "Pop!" launcher icon layers (foreground + monochrome) and an SVG preview.
Run from anywhere: python3 tools/launcher-icon/popicon.py
Writes app/src/main/res/drawable/ic_launcher_{foreground,monochrome}.xml and tools/launcher-icon/preview*.svg.
The background layer (ic_launcher_background.xml) is hand-written."""
def edge(p0, p1, kind, r):
    # straight edge from p0 to p1 (clockwise walk); kind +1 knob out, -1 hole in, 0 flat
    if kind == 0: return f"L{p1[0]:.2f},{p1[1]:.2f} "
    mx,my=(p0[0]+p1[0])/2,(p0[1]+p1[1])/2
    L=math.hypot(p1[0]-p0[0],p1[1]-p0[1]); dx,dy=(p1[0]-p0[0])/L,(p1[1]-p0[1])/L
    h=r*0.72
    a=(mx-dx*h,my-dy*h); b=(mx+dx*h,my+dy*h)
    sweep=1 if kind>0 else 0
    return f"L{a[0]:.2f},{a[1]:.2f} A{r},{r} 0 1,{sweep} {b[0]:.2f},{b[1]:.2f} L{p1[0]:.2f},{p1[1]:.2f} "
def piece(x,y,s,e,r=None,cr=(0,0,0,0)):
    # e = (top,right,bottom,left); cr = corner radii tl,tr,br,bl
    r=r or s*0.17
    tl,tr,br,bl=cr
    d=f"M{x+tl},{y} "
    d+=edge((x+tl,y),(x+s-tr,y),e[0],r)
    if tr: d+=f"A{tr},{tr} 0 0,1 {x+s},{y+tr} "
    d+=edge((x+s,y+tr),(x+s,y+s-br),e[1],r)
    if br: d+=f"A{br},{br} 0 0,1 {x+s-br},{y+s} "
    d+=edge((x+s-br,y+s),(x+bl,y+s),e[2],r)
    if bl: d+=f"A{bl},{bl} 0 0,1 {x},{y+s-bl} "
    d+=edge((x,y+s-bl),(x,y+tl),e[3],r)
    if tl: d+=f"A{tl},{tl} 0 0,1 {x+tl},{y} "
    return d+"Z"
import sys, math, re, os

# ---- affine helpers: m = (a,b,c,d,e,f) maps (x,y) -> (a x + c y + e, b x + d y + f)
def mul(m,n):  # m after n
    a,b,c,d,e,f=m; A,B,C,D,E,F=n
    return (a*A+c*B, b*A+d*B, a*C+c*D, b*C+d*D, a*E+c*F+e, b*E+d*F+f)
I=(1,0,0,1,0,0)
def T(x,y): return (1,0,0,1,x,y)
def S(k): return (k,0,0,k,0,0)
def R(deg,cx=0,cy=0):
    t=math.radians(deg); c,s=math.cos(t),math.sin(t)
    return mul(T(cx,cy),mul((c,s,-s,c,0,0),T(-cx,-cy)))
def ap(m,x,y): a,b,c,d,e,f=m; return a*x+c*y+e, b*x+d*y+f
def scale_of(m): return math.hypot(m[0],m[1])
def angle_of(m): return math.degrees(math.atan2(m[1],m[0]))
PTS=[]
def tpath(d,m):
    toks=re.findall(r'[MLAQZHV]|-?\d*\.?\d+',d); out=[]; i=0; cmd=None; cur=(0,0)
    k=scale_of(m); ang=angle_of(m)
    def num():
        nonlocal i; v=float(toks[i]); i+=1; return v
    while i<len(toks):
        if toks[i] in 'MLAQZHV': cmd=toks[i]; i+=1
        if cmd in 'ML':
            x,y=num(),num(); cur=(x,y); X,Y=ap(m,x,y); PTS.append((X,Y)); out.append(f"{cmd}{X:.2f},{Y:.2f}")
        elif cmd=='Q':
            x1,y1,x,y=num(),num(),num(),num(); cur=(x,y)
            a=ap(m,x1,y1); b=ap(m,x,y); PTS.append(b); out.append(f"Q{a[0]:.2f},{a[1]:.2f} {b[0]:.2f},{b[1]:.2f}")
        elif cmd=='A':
            rx,ry,rot,la,sw,x,y=[num() for _ in range(7)]; cur=(x,y)
            X,Y=ap(m,x,y); PTS.append((X,Y)); PTS.append(((X+PTS[-1][0])/2+0,(Y+PTS[-1][1])/2)); out.append(f"A{rx*k:.2f},{ry*k:.2f} {(rot+ang)%360:.1f} {int(la)},{int(sw)} {X:.2f},{Y:.2f}")
        elif cmd=='Z': out.append("Z")
    return " ".join(out)
def circ(cx,cy,r): return f"M{cx-r},{cy} A{r},{r} 0 1,0 {cx+r},{cy} A{r},{r} 0 1,0 {cx-r},{cy} Z"
def sparkle(x,y,k):
    return f"M{x},{y-k} Q{x+k*.18},{y-k*.18} {x+k},{y} Q{x+k*.18},{y+k*.18} {x},{y+k} Q{x-k*.18},{y+k*.18} {x-k},{y} Q{x-k*.18},{y-k*.18} {x},{y-k} Z"

C=dict(coral=("#FF6F61","#D2403A"),sun=("#FFD23F","#E38F00"),leaf=("#5BD16A","#2C9544"),grape=("#A472F2","#6B3FC2"))
INK="#4A3366"; BLUSH="#FF8FA3"
s=27; x0=26; y0=26; Rr=7
tl=piece(x0,y0,s,(0,1,-1,0),cr=(Rr,0,0,0)); tr=piece(x0+s,y0,s,(0,0,1,-1),cr=(0,Rr,0,0))
bl=piece(x0,y0+s,s,(1,-1,0,0),cr=(0,0,0,Rr)); br=piece(x0+s,y0+s,s,(-1,0,0,1),cr=(0,0,Rr,0))
cx,cy=x0+s*1.5,y0+s*1.52
# How far the loose piece sits out of its slot: shift (dx, dy) and tilt in degrees.
POP_DX,POP_DY,POP_ROT=[float(v) for v in os.environ.get('POP','7,6,14').split(',')]
POP=mul(T(POP_DX,POP_DY),R(POP_ROT,x0+s*1.5,y0+s*1.5))
# shapes: (d, local matrix, fill, fillAlpha, stroke, strokeWidth, strokeAlpha, cap)
shapes=[]
def add(d,m=I,fill=None,fa=1,stroke=None,sw=0,sa=1,cap=None): shapes.append([d,m,fill,fa,stroke,sw,sa,cap])
add(br,I,"#2A6FA8",.28)                                   # empty slot
for d,col in ((tl,'coral'),(tr,'sun'),(bl,'leaf')): add(d,I,C[col][0],1,C[col][1],2.2)
add(br,mul(POP,T(1.5,2.5)),"#1B5E96",.25)                  # pop shadow
add(br,POP,C['grape'][0],1,C['grape'][1],2.4)
add(circ(cx-s*.16,cy-s*.03,s*.05),POP,INK); add(circ(cx+s*.16,cy-s*.03,s*.05),POP,INK)
add(f"M{cx-s*.12},{cy+s*.1} Q{cx},{cy+s*.26} {cx+s*.12},{cy+s*.1}",POP,None,1,INK,s*.06,1,'round')
add(circ(cx-s*.29,cy+s*.13,s*.075),POP,BLUSH,.85); add(circ(cx+s*.29,cy+s*.13,s*.075),POP,BLUSH,.85)
xs,ys=x0+s,y0+s
add(f"M{xs+s*.62},{ys+s*.16} Q{xs+s*.78},{ys+s*.16} {xs+s*.84},{ys+s*.3}",POP,None,1,"#FFFFFF",s*.06,.8,'round')
add(sparkle(86,46,5),I,"#FFFFFF"); add(sparkle(80,36,2.8),I,"#FFFFFF"); add(sparkle(46,86,3.2),I,"#FFFFFF")

# ---- fit everything into the 66dp safe-zone circle (radius 33 around 54,54)
for d,m,*_ in shapes: tpath(d,m)
pts=list(PTS)
bx,by=x0+s,y0+s   # centre the 2x2 grid itself; the loose piece hangs off it
rad=max(math.hypot(x-bx,y-by) for x,y in pts)+2   # +2 for stroke/arc bulge
k=34.5/rad   # stays inside the 72dp visible circle every launcher mask contains
FIT=mul(T(54,54),mul(S(k),T(-bx,-by)))
print(f"fit: centre=({bx:.1f},{by:.1f}) radius={rad:.1f} scale={k:.3f}",file=sys.stderr)

def vd_path(sh,mono=False):
    d,m,fill,fa,stroke,sw,sa,cap=sh; M=mul(FIT,m); a=[f'android:pathData="{tpath(d,M)}"']
    if fill: a.insert(0,f'android:fillColor="{"#000000" if mono else fill}"'+(f' android:fillAlpha="{fa}"' if fa!=1 else ''))
    if stroke: a.insert(0,f'android:strokeColor="{"#000000" if mono else stroke}" android:strokeWidth="{sw*k:.2f}" android:strokeLineJoin="round"'+(f' android:strokeAlpha="{sa}"' if sa!=1 else '')+(f' android:strokeLineCap="{cap}"' if cap else ''))
    return '    <path '+' '.join(a)+' />'
HEAD='<?xml version="1.0" encoding="utf-8"?>\n{c}\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
ROOT=os.path.dirname(os.path.abspath(__file__))
res=os.path.join(ROOT,'../../app/src/main/res/drawable/')
fg=HEAD.format(c='<!-- Launcher icon foreground ("Pop!"): a 2x2 puzzle with the purple mascot piece popping out of its slot. Generated; kept inside the 66dp safe zone. -->')+"\n".join(vd_path(x) for x in shapes)+"\n</vector>\n"
open(res+'ic_launcher_foreground.xml','w').write(fg)
# monochrome: pieces as soft fills + outlines, face solid; skip shadow, blush, shine, sparkles' extra
mono=[]
for i,sh in enumerate(shapes):
    d,m,fill,fa,stroke,sw,sa,cap=sh
    if i in (1,2,3,5): mono.append([d,m,"#000",.35,"#000",sw,1,None])
    elif i in (6,7,8): mono.append(sh)
    elif i>=12: mono.append(sh)
fgm=HEAD.format(c='<!-- Themed (monochrome) launcher icon: the "Pop!" puzzle as outlines plus the mascot face; Android tints it. Generated. -->')+"\n".join(vd_path(x,True) for x in mono)+"\n</vector>\n"
open(res+'ic_launcher_monochrome.xml','w').write(fgm)

# ---- SVG preview (fg over bg) for checking
def svg_path(sh):
    d,m,fill,fa,stroke,sw,sa,cap=sh; M=mul(FIT,m)
    return f'<path d="{tpath(d,M)}" fill="{fill or "none"}" fill-opacity="{fa}" stroke="{stroke or "none"}" stroke-width="{sw*k:.2f}" stroke-opacity="{sa}" stroke-linejoin="round" stroke-linecap="{cap or "butt"}"/>'
bg=('<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#CBEAFF"/><stop offset="1" stop-color="#7CC8FF"/></linearGradient></defs><rect width="108" height="108" fill="url(#g)"/>'
    '<circle cx="28" cy="80" r="9" fill="#fff" opacity=".35"/><circle cx="80" cy="27" r="6" fill="#fff" opacity=".4"/><circle cx="84" cy="84" r="4" fill="#fff" opacity=".3"/>')
open(os.path.join(ROOT,'preview.svg'),'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">{bg}'+"".join(svg_path(x) for x in shapes)+'</svg>')
open(os.path.join(ROOT,'preview_mono.svg'),'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432"><rect width="108" height="108" fill="#D7E3F4"/><g>'+"".join(svg_path([*x[:2],"#1f3550" if x[2] else None,x[3],"#1f3550" if x[4] else None,*x[5:]]) for x in mono)+'</g></svg>')
