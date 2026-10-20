"""Generates the original pixel art for the classic (Flappy Bird style) theme.

Run with: python3 tools/classic_art.py  (needs Pillow). Writes classic_*.png into
app/src/main/res/drawable-nodpi. Shapes are drawn procedurally and outlined automatically.
"""
import json, math, random, os
from PIL import Image
PAL = {'k':(84,56,71),'y':(250,200,48),'Y':(253,236,130),'o':(236,150,40),'w':(255,255,255),
       'r':(250,96,40),'R':(206,58,30),'W':(253,247,226),'G':(205,205,205)}
def grid(w,h): return [['.']*w for _ in range(h)]
def ellipse(g,cx,cy,rx,ry,ch,cond=None):
    for y in range(len(g)):
        for x in range(len(g[0])):
            if ((x+0.5-cx)/rx)**2+((y+0.5-cy)/ry)**2<=1 and (cond is None or cond(x,y)): g[y][x]=ch
def rect(g,x0,y0,x1,y1,ch):
    for y in range(y0,y1+1):
        for x in range(x0,x1+1): g[y][x]=ch
def outline(g):
    h,w=len(g),len(g[0]); out=[r[:] for r in g]
    for y in range(h):
        for x in range(w):
            if g[y][x]=='.' and any(0<=y+dy<h and 0<=x+dx<w and g[y+dy][x+dx] not in '.k' for dx,dy in((1,0),(-1,0),(0,1),(0,-1))):
                out[y][x]='k'
    return out
def bird(wing_y):
    g=grid(21,16)
    ellipse(g,9.5,8,7.6,6.0,'y')
    ellipse(g,9.5,8,7.6,6.0,'Y',lambda x,y:y<=4)
    ellipse(g,9.5,8,7.6,6.0,'o',lambda x,y:y>=11)
    g=outline(g)
    ellipse(g,14,5.2,3.4,3.4,'k'); ellipse(g,14,5.2,2.5,2.5,'w')
    rect(g,15,4,15,5,'k')                 # pupil
    rect(g,11,9,18,9,'r'); rect(g,11,10,17,10,'k'); rect(g,11,11,17,11,'r'); rect(g,12,12,16,12,'R')
    ellipse(g,5.2,wing_y,3.9,2.7,'k'); ellipse(g,5.2,wing_y,3.0,1.8,'W')
    return outline(g)
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'drawable-nodpi'); os.makedirs(OUT,exist_ok=True)
C=dict(PAL)
C.update({'s':(78,192,202),'c':(234,252,240),'C':(214,244,232),'b':(200,232,206),'B':(178,214,188),'n':(231,247,233),
          'u':(98,214,110),'U':(74,180,90),'H':(228,253,139),'L':(156,230,89),'g':(115,191,46),'d':(85,128,34),
          'S':(222,216,149),'T':(208,196,120),'p':(222,216,149),'P':(252,248,214),
          'z':(205,127,50),'Z':(245,190,120),'x':(190,192,200),'X':(240,242,248),'q':(232,180,20),'Q':(255,236,120),
          'O':(252,160,72),'v':(110,211,74)})
def save(g,name):
    h,w=len(g),len(g[0]); im=Image.new('RGBA',(w,h),(0,0,0,0))
    for y in range(h):
        for x in range(w):
            if g[y][x]!='.': im.putpixel((x,y),C[g[y][x]]+(255,))
    im.save(f'{OUT}/{name}.png'); return im
# bird
for i,f in enumerate([bird(6.5),bird(8.5),bird(10.5)]): save(f,f'classic_bird_{i+1}')
# pipe body (1 row) and cap
inner='LHHLLggggggggggggggggdgddd'[:24]
save([list('k'+inner+'k')],'classic_pipe_body')
capinner='LHHLLgggggggggggggggggggdgddd'[:28]
cap=[list('k'*30)]+[list('k'+capinner+'k') for _ in range(11)]+[list('k'+'d'*28+'k'),list('k'*30)]
save(cap,'classic_pipe_cap')
# background tile
W,H=120,220; g=[['s']*W for _ in range(H)]
def wrapcircle(cx,cy,r,ch,cond=lambda x,y:True):
    for y in range(H):
        for x in range(W):
            for ox in (-W,0,W):
                if (x+0.5-cx-ox)**2+(y+0.5-cy)**2<=r*r and cond(x,y): g[y][x]=ch
random.seed(7)
for cx in range(0,W,14): wrapcircle(cx+random.uniform(-3,3),150+random.uniform(-6,4),12+random.uniform(-2,4),'C')
for cx in range(0,W,14): wrapcircle(cx+random.uniform(-3,3),153+random.uniform(-6,4),11+random.uniform(-2,3),'c')
for y in range(158,H):
    for x in range(W): g[y][x]='c'
# buildings
x=0
while x<W:
    bw=random.randint(9,16); bh=random.randint(18,40); top=196-bh
    for yy in range(top,H):
        for xx in range(x,min(W,x+bw)): g[yy][xx]='B' if (xx==x or yy==top) else 'b'
    for yy in range(top+3,196,4):
        for xx in range(x+2,min(W,x+bw)-1,3): g[yy][xx]='n'; 
    x+=bw+random.randint(0,2)
for cx in range(0,W,10): wrapcircle(cx+random.uniform(-2,2),207+random.uniform(-2,3),9+random.uniform(-1,3),'U')
for cx in range(0,W,10): wrapcircle(cx+random.uniform(-2,2),210+random.uniform(-2,3),8+random.uniform(-1,2),'u')
for y in range(214,H):
    for x in range(W): g[y][x]='u'
save(g,'classic_background')
# ground tile
W2,H2=14,30; gr=[]
for y in range(H2):
    row=[]
    for x in range(W2):
        if y==0: ch='k'
        elif y<=5: ch='L' if ((x+y)//7)%2==0 else 'g'
        elif y==6: ch='d'
        elif y==7: ch='T'
        else: ch='S'
        row.append(ch)
    gr.append(row)
save(gr,'classic_ground')
# pixel font 5x7
FONT={
'0':["01110","10001","10011","10101","11001","10001","01110"],
'1':["00100","01100","00100","00100","00100","00100","01110"],
'2':["01110","10001","00001","00110","01000","10000","11111"],
'3':["11110","00001","00001","01110","00001","00001","11110"],
'4':["00010","00110","01010","10010","11111","00010","00010"],
'5':["11111","10000","11110","00001","00001","10001","01110"],
'6':["00110","01000","10000","11110","10001","10001","01110"],
'7':["11111","00001","00010","00100","01000","01000","01000"],
'8':["01110","10001","10001","01110","10001","10001","01110"],
'9':["01110","10001","10001","01111","00001","00010","01100"],
'G':["01110","10001","10000","10111","10001","10001","01111"],
'E':["11111","10000","10000","11110","10000","10000","11111"],
'T':["11111","00100","00100","00100","00100","00100","00100"],
'R':["11110","10001","10001","11110","10100","10010","10001"],
'A':["01110","10001","10001","11111","10001","10001","10001"],
'D':["11110","10001","10001","10001","10001","10001","11110"],
'Y':["10001","10001","01010","00100","00100","00100","00100"],
'M':["10001","11011","10101","10101","10001","10001","10001"],
'O':["01110","10001","10001","10001","10001","10001","01110"],
'V':["10001","10001","10001","10001","10001","01010","00100"],
' ':["000","000","000","000","000","000","000"],
}
def text(s,fill,thick=True):
    # glyphs are 2x scaled so the outline stays thin relative to the stroke
    sc=2; w=sum(len(FONT[c][0])*sc+sc for c in s)-sc+4; h=7*sc+4
    g=grid(w,h); x=2
    for c in s:
        for yy,row in enumerate(FONT[c]):
            for xx,v in enumerate(row):
                if v=='1': rect(g,x+xx*sc,2+yy*sc,x+xx*sc+sc-1,2+yy*sc+sc-1,fill)
        x+=len(FONT[c][0])*sc+sc
    g=outline(g)
    # second white-ish ring for the title look
    return g
for d in '0123456789':
    g=grid(7,9)
    for yy,row in enumerate(FONT[d]):
        for xx,v in enumerate(row):
            if v=='1': g[yy+1][xx+1]='w'
    save(outline(g),f'classic_digit_{d}')
save(text('GET READY','v'),'classic_text_get_ready')
save(text('GAME OVER','O'),'classic_text_game_over')
# panel 60x40 with rounded corners
PW,PH=60,40; g=grid(PW,PH)
for y in range(PH):
    for x in range(PW):
        corner=min(x,PW-1-x)+min(y,PH-1-y)
        if corner<2: continue
        edge=min(x,PW-1-x,y,PH-1-y)
        g[y][x]='k' if edge==0 or corner==2 else ('P' if edge==1 else 'p')
save(g,'classic_panel')
# medals 22x22
for name,(base,shine) in {'bronze':('z','Z'),'silver':('x','X'),'gold':('q','Q')}.items():
    g=grid(22,22); ellipse(g,11,11,10,10,'k'); ellipse(g,11,11,9,9,base)
    ellipse(g,11,11,9,9,shine,lambda x,y:(x+y)<15 and (x+y)>11)
    ellipse(g,11,11,5,5,shine); ellipse(g,11,11,4,4,base)
    save(g,f'classic_medal_{name}')
print(sorted(os.listdir(OUT)))
