import cairosvg
OUT_SVG="/sessions/happy-optimistic-pasteur/mnt/outputs/presets/svg"
OUT_PNG="/sessions/happy-optimistic-pasteur/mnt/outputs/presets/png"
INK="#241E17"; L="#3A2F1E"; PAPER="#E8DCC0"; VELLUM="#ECE0C4"; HATCH="#C9B68C"; SEP="#9C8460"; OCH="#C8893A"; OX="#8E3B2E"
W,Hh=1280,720
def shell(floor_y=470):
    s=f'<rect width="{W}" height="{Hh}" fill="{VELLUM}"/>'
    for y in range(70,floor_y,26): s+=f'<line x1="40" y1="{y}" x2="{W-40}" y2="{y}" stroke="{HATCH}" stroke-width="1"/>'
    s+=f'<rect x="0" y="{floor_y}" width="{W}" height="{Hh-floor_y}" fill="{PAPER}"/>'
    s+=f'<line x1="0" y1="{floor_y}" x2="{W}" y2="{floor_y}" stroke="{INK}" stroke-width="3"/>'
    for i in range(1,7): s+=f'<line x1="{i*W/7:.0f}" y1="{floor_y}" x2="{i*W/7*1.25-W*0.18:.0f}" y2="{Hh}" stroke="{SEP}" stroke-width="1"/>'
    for y in range(floor_y+30,Hh,40): s+=f'<line x1="0" y1="{y}" x2="{W}" y2="{y}" stroke="{SEP}" stroke-width="0.8"/>'
    # corner vignette hatch
    s+='<g stroke="#7C6A4A" stroke-width="1" opacity="0.4">'
    for i in range(20): s+=f'<line x1="40" y1="{70+i*9}" x2="{40+90-i*4}" y2="70"/>'
    for i in range(20): s+=f'<line x1="{W-40}" y1="{70+i*9}" x2="{W-40-90+i*4}" y2="70"/>'
    s+='</g>'
    return s
def window(x,y,w,h):
    s=f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{VELLUM}" stroke="{INK}" stroke-width="6"/>'
    s+=f'<line x1="{x+w/2}" y1="{y}" x2="{x+w/2}" y2="{y+h}" stroke="{INK}" stroke-width="4"/>'
    s+=f'<line x1="{x}" y1="{y+h/2}" x2="{x+w}" y2="{y+h/2}" stroke="{INK}" stroke-width="4"/>'
    s+=f'<path d="M{x-6} {y-4} L{x-26} {y-4} L{x-18} {y+h+4} L{x-6} {y+h} Z" fill="none" stroke="{INK}" stroke-width="4"/>'
    s+=f'<path d="M{x+w+6} {y-4} L{x+w+26} {y-4} L{x+w+18} {y+h+4} L{x+w+6} {y+h} Z" fill="none" stroke="{INK}" stroke-width="4"/>'
    s+=f'<g stroke="{SEP}" stroke-width="1">'
    for i in range(6): s+=f'<line x1="{x+8+i*8}" y1="{y+8}" x2="{x+4+i*8}" y2="{y+h/2-6}"/>'
    s+='</g>'
    return s
def wrap(name,inner):
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{Hh}" viewBox="0 0 {W} {Hh}">{inner}</svg>'
    open(f"{OUT_SVG}/{name}.svg","w").write(svg)
    cairosvg.svg2png(url=f"{OUT_SVG}/{name}.svg",write_to=f"{OUT_PNG}/{name}.png",output_width=W,output_height=Hh)
    print("ok",name)

# ROOM 1: parlour (fireplace center, armchair, window)
r=shell(470)
r+=window(150,150,180,210)
# fireplace
r+=f'<rect x="540" y="250" width="200" height="220" fill="none" stroke="{INK}" stroke-width="6"/><rect x="572" y="320" width="136" height="150" fill="{PAPER}" stroke="{INK}" stroke-width="4"/><rect x="520" y="230" width="240" height="26" fill="none" stroke="{INK}" stroke-width="5"/>'
r+=f'<g stroke="{SEP}" stroke-width="1.4">'+"".join(f'<line x1="{590+i*16}" y1="460" x2="{596+i*16}" y2="400"/>' for i in range(8))+'</g>'
r+=f'<path d="M600 470 Q640 430 680 470" fill="none" stroke="{INK}" stroke-width="3"/>'
# mantel clock + picture
r+=f'<rect x="600" y="206" width="80" height="24" fill="none" stroke="{INK}" stroke-width="3"/><circle cx="640" cy="200" r="14" fill="none" stroke="{INK}" stroke-width="3"/>'
r+=f'<rect x="900" y="170" width="150" height="120" fill="none" stroke="{INK}" stroke-width="6"/><ellipse cx="975" cy="230" rx="40" ry="48" fill="none" stroke="{INK}" stroke-width="3"/>'
# armchair foreground right
r+=f'<path d="M900 700 L900 540 Q900 500 940 500 L1080 500 Q1120 500 1120 540 L1120 700 Z" fill="none" stroke="{INK}" stroke-width="6"/><path d="M930 520 L1090 520 L1090 600 L930 600 Z" fill="none" stroke="{INK}" stroke-width="4"/>'
r+=f'<g stroke="{SEP}" stroke-width="1.2">'+"".join(f'<line x1="940" y1="{530+i*14}" x2="1080" y2="{530+i*14}"/>' for i in range(5))+'</g>' 
wrap("room_parlour",r)

# ROOM 2: study (desk + bookshelf + window) reuse
r=shell(470)
r+=window(140,150,170,200)
r+=f'<rect x="430" y="150" width="120" height="100" fill="none" stroke="{INK}" stroke-width="6"/><ellipse cx="490" cy="200" rx="34" ry="40" fill="none" stroke="{INK}" stroke-width="3"/>'
# bookshelf
r+=f'<rect x="940" y="150" width="260" height="320" fill="none" stroke="{INK}" stroke-width="6"/>'
r+=f'<g stroke="{INK}" stroke-width="4">'+"".join(f'<line x1="940" y1="{230+j*80}" x2="1200" y2="{230+j*80}"/>' for j in range(3))+'</g>'
import random; random.seed(3)
bs=""
for j in range(4):
  x=952
  while x<1190:
    w=random.choice([16,20,24]); hh=random.choice([54,60,66])
    bs+=f'<rect x="{x}" y="{226+j*80-hh}" width="{w}" height="{hh}" fill="none" stroke="{INK}" stroke-width="2.5"/>'
    x+=w+4
r+=bs
# desk foreground
r+=f'<rect x="470" y="520" width="360" height="20" fill="none" stroke="{INK}" stroke-width="5"/><rect x="486" y="540" width="328" height="50" fill="none" stroke="{INK}" stroke-width="5"/><rect x="500" y="590" width="20" height="90" fill="none" stroke="{INK}" stroke-width="5"/><rect x="780" y="590" width="20" height="90" fill="none" stroke="{INK}" stroke-width="5"/>'
r+=f'<g stroke="{SEP}" stroke-width="1.2">'+"".join(f'<line x1="{496+i*8}" y1="544" x2="{496+i*8}" y2="586"/>' for i in range(40))+'</g>'
# lamp + papers + magnifier on desk
r+=f'<ellipse cx="720" cy="520" rx="40" ry="10" fill="none" stroke="{INK}" stroke-width="3"/><rect x="714" y="470" width="12" height="50" fill="none" stroke="{INK}" stroke-width="3"/><path d="M680 470 L760 470 L744 436 L696 436 Z" fill="{OCH}" stroke="{INK}" stroke-width="4"/>'
r+=f'<rect x="520" y="500" width="80" height="20" fill="{PAPER}" stroke="{INK}" stroke-width="3" transform="rotate(-5 560 510)"/>'
r+=f'<circle cx="560" cy="516" r="22" fill="none" stroke="{INK}" stroke-width="5"/><line x1="576" y1="532" x2="600" y2="556" stroke="{INK}" stroke-width="6"/>'
wrap("room_study",r)

# ROOM 3: hallway (door at end, runner, sconces, stairs)
r=shell(500)
# perspective walls
r+=f'<path d="M0 90 L360 220 L360 500 L0 560 Z" fill="none" stroke="{INK}" stroke-width="4"/>'
r+=f'<path d="M{W} 90 L{W-360} 220 L{W-360} 500 L{W} 560 Z" fill="none" stroke="{INK}" stroke-width="4"/>'
# end wall door
r+=f'<rect x="560" y="200" width="160" height="270" fill="{VELLUM}" stroke="{INK}" stroke-width="6"/><rect x="576" y="216" width="128" height="110" fill="none" stroke="{INK}" stroke-width="3"/><rect x="576" y="340" width="128" height="116" fill="none" stroke="{INK}" stroke-width="3"/><circle cx="694" cy="340" r="6" fill="{OCH}" stroke="{INK}" stroke-width="2"/>'
r+=f'<rect x="548" y="186" width="184" height="18" fill="none" stroke="{INK}" stroke-width="4"/>'
# runner rug down center
r+=f'<path d="M520 500 L760 500 L900 720 L380 720 Z" fill="none" stroke="{OX}" stroke-width="4"/><path d="M548 520 L732 520 L840 700 L440 700 Z" fill="none" stroke="{SEP}" stroke-width="2"/>'
# sconces
for sx in (300,980):
  r+=f'<circle cx="{sx}" cy="240" r="16" fill="none" stroke="{INK}" stroke-width="4"/><path d="M{sx} 224 Q{sx-8} 206 {sx} 192 Q{sx+8} 206 {sx} 224 Z" fill="{OCH}" stroke="{INK}" stroke-width="2"/><g stroke="{SEP}" stroke-width="1">'+"".join(f'<line x1="{sx-14}" y1="{250+i*10}" x2="{sx+14}" y2="{250+i*10}"/>' for i in range(4))+'</g>'
# framed portraits along wall
for px,py in [(160,250),(1080,250)]:
  r+=f'<rect x="{px}" y="{py}" width="90" height="110" fill="none" stroke="{INK}" stroke-width="5"/><ellipse cx="{px+45}" cy="{py+55}" rx="26" ry="32" fill="none" stroke="{INK}" stroke-width="3"/>'
wrap("room_hallway",r)
print("DONE rooms")
