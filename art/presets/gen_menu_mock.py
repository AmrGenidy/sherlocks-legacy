import cairosvg
INK="#241E17"; PAPER="#E8DCC0"; VELLUM="#F2E9D2"; HATCH="#CBBA94"; SEP="#6B5A43"; OCH="#C8893A"; PET="#1C5D6E"; OX="#8E3B2E"; WOOD="#5A4A35"
W,H=1280,800
s=f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
s+='<defs><radialGradient id="lamp" cx="40%" cy="55%" r="58%"><stop offset="0%" stop-color="#F7EFD6"/><stop offset="62%" stop-color="#ECE0C4"/><stop offset="100%" stop-color="#E1D1AB"/></radialGradient>'
plate="M80 712 L80 280 Q80 206 312 206 Q545 206 545 280 L545 712 Z"
s+=f'<clipPath id="fp"><path d="{plate}"/></clipPath>'
s+='<radialGradient id="glow" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#F6E2A6" stop-opacity="0.85"/><stop offset="100%" stop-color="#F6E2A6" stop-opacity="0"/></radialGradient></defs>'
s+=f'<rect width="{W}" height="{H}" fill="url(#lamp)"/>'
s+='<g stroke="'+HATCH+'" stroke-width="0.8" opacity="0.5">'
for y in range(60,750,22): s+=f'<line x1="40" y1="{y}" x2="{W-40}" y2="{y}"/>'
s+='</g>'
# frame
s+=f'<rect x="28" y="28" width="{W-56}" height="{H-56}" fill="none" stroke="{INK}" stroke-width="4"/>'
s+=f'<rect x="40" y="40" width="{W-80}" height="{H-80}" fill="none" stroke="{INK}" stroke-width="1.5"/>'
for cx,cy,sx,sy in [(40,40,1,1),(W-40,40,-1,1),(40,H-40,1,-1),(W-40,H-40,-1,-1)]:
    s+=f'<path d="M{cx} {cy+sy*54} Q{cx+sx*6} {cy+sy*18} {cx+sx*54} {cy+sy*6} Q{cx+sx*18} {cy+sy*22} {cx+sx*22} {cy+sy*54} Z" fill="{OCH}" stroke="{INK}" stroke-width="1.5"/><circle cx="{cx+sx*30}" cy="{cy+sy*30}" r="3.5" fill="{OX}"/>'
# title
s+=f'<text x="640" y="130" text-anchor="middle" font-family="Georgia, serif" font-size="64" font-weight="bold" fill="{INK}" letter-spacing="2">Sherlock’s Legacy</text>'
s+=f'<line x1="440" y1="156" x2="840" y2="156" stroke="{OCH}" stroke-width="2.5"/><circle cx="640" cy="156" r="5" fill="{OX}"/>'
s+=f'<text x="640" y="186" text-anchor="middle" font-family="Georgia, serif" font-size="18" fill="{SEP}" letter-spacing="6">A DETECTIVE INVESTIGATION</text>'
# ---- FRONTISPIECE SCENE ----
s+='<g clip-path="url(#fp)">'
s+=f'<rect x="80" y="180" width="465" height="540" fill="#ECE0C4"/>'
s+='<g stroke="'+HATCH+'" stroke-width="0.8">'
for y in range(240,560,18): s+=f'<line x1="90" y1="{y}" x2="540" y2="{y}"/>'
s+='</g>'
s+=f'<rect x="80" y="560" width="465" height="160" fill="#E2D2AC"/>'
s+=f'<line x1="80" y1="560" x2="545" y2="560" stroke="{INK}" stroke-width="2"/>'
# window with moon (left)
s+=f'<rect x="120" y="250" width="120" height="150" fill="#ECE0C4" stroke="{INK}" stroke-width="5"/>'
s+=f'<g clip-path="url(#fp)" stroke="{INK}" stroke-width="0.7">'
for i in range(8): s+=f'<line x1="{125+i*14}" y1="255" x2="{125+i*14-30}" y2="395"/>'
s+='</g>'
s+=f'<circle cx="205" cy="290" r="18" fill="#F2E9D2" stroke="{INK}" stroke-width="1.5"/>'
s+=f'<line x1="180" y1="250" x2="180" y2="400" stroke="{INK}" stroke-width="3"/><line x1="120" y1="325" x2="240" y2="325" stroke="{INK}" stroke-width="3"/>'
# bookshelf (right)
s+=f'<rect x="420" y="250" width="105" height="230" fill="none" stroke="{INK}" stroke-width="5"/>'
for j in range(2): s+=f'<line x1="420" y1="{326+j*78}" x2="525" y2="{326+j*78}" stroke="{INK}" stroke-width="3"/>'
import random; random.seed(7); bx2=428
for row in range(3):
  x=428
  while x<518:
    w=random.choice([13,16,19]); hh=random.choice([46,52,58])
    s+=f'<rect x="{x}" y="{322+row*78-hh}" width="{w}" height="{hh}" fill="none" stroke="{INK}" stroke-width="2"/>'; x+=w+3
# seated detective behind desk
cx=322
s+=f'<path d="M{cx-50} 600 L{cx-40} 540 Q{cx} 500 {cx+40} 540 L{cx+50} 600 Z" fill="{WOOD}" stroke="{INK}" stroke-width="3"/>'  # coat/shoulders
s+=f'<ellipse cx="{cx}" cy="512" rx="26" ry="30" fill="#E2CBA8" stroke="{INK}" stroke-width="3"/>'  # head
s+=f'<path d="M{cx-30} 496 L{cx-24} 470 L{cx+24} 470 L{cx+30} 496 Z" fill="{WOOD}" stroke="{INK}" stroke-width="3"/>'  # hat crown
s+=f'<ellipse cx="{cx}" cy="496" rx="40" ry="8" fill="{WOOD}" stroke="{INK}" stroke-width="3"/>'  # hat brim
s+=f'<g stroke="#2A2218" stroke-width="0.8" opacity="0.5">'
for x in range(cx-44,cx+44,8): s+=f'<line x1="{x}" y1="548" x2="{x-26}" y2="600"/>'
s+='</g>'
# desk
s+=f'<rect x="120" y="592" width="404" height="18" fill="{WOOD}" stroke="{INK}" stroke-width="3"/>'
s+=f'<rect x="140" y="610" width="364" height="40" fill="#4A3D2A" stroke="{INK}" stroke-width="3"/>'
# lamp + glow on desk
s+=f'<ellipse cx="200" cy="595" rx="70" ry="22" fill="url(#glow)"/>'
s+=f'<rect x="194" y="556" width="10" height="36" fill="{INK}"/><path d="M172 556 L228 556 L216 528 L184 528 Z" fill="{OCH}" stroke="{INK}" stroke-width="3"/><ellipse cx="200" cy="592" rx="22" ry="6" fill="{INK}"/>'
# papers + magnifier
s+=f'<rect x="360" y="578" width="70" height="18" fill="#F2E9D2" stroke="{INK}" stroke-width="2" transform="rotate(-4 395 587)"/>'
s+=f'<circle cx="430" cy="586" r="16" fill="#E8DCC0" fill-opacity="0.4" stroke="{INK}" stroke-width="4"/><line x1="442" y1="598" x2="458" y2="614" stroke="{INK}" stroke-width="5"/>'
s+='</g>'
# frontispiece frame (arched)
s+=f'<path d="{plate}" fill="none" stroke="{INK}" stroke-width="6"/>'
s+=f'<path d="M92 706 L92 282 Q92 218 312 218 Q532 218 532 282 L532 706 Z" fill="none" stroke="{OCH}" stroke-width="2.5"/>'
# caption ribbon
s+=f'<rect x="180" y="690" width="264" height="34" fill="{VELLUM}" stroke="{INK}" stroke-width="2"/><text x="312" y="713" text-anchor="middle" font-family="Georgia, serif" font-size="16" font-style="italic" fill="{SEP}">“The game is afoot.”</text>'
# ---- RIGHT MENU ----
items=[("Continue","primary"),("Single player","plate"),("Multiplayer","plate"),("Create a case","plate"),("Tutorials","plate")]
bx,bw,by,bh,gap=708,484,286,60,16
for i,(label,kind) in enumerate(items):
    y=by+i*(bh+gap)
    fill=PET if kind=="primary" else VELLUM
    tcol=VELLUM if kind=="primary" else INK
    s+=f'<rect x="{bx}" y="{y}" width="{bw}" height="{bh}" rx="4" fill="{fill}" stroke="{INK}" stroke-width="2.5"/>'
    s+=f'<rect x="{bx+4}" y="{y+4}" width="{bw-8}" height="{bh-8}" rx="3" fill="none" stroke="{tcol}" stroke-width="0.8" opacity="0.4"/>'
    s+=f'<text x="{bx+bw/2}" y="{y+39}" text-anchor="middle" font-family="Georgia, serif" font-size="25" fill="{tcol}" letter-spacing="1">{label}</text>'
# corner utility icons (settings gear + quit) bottom-right
gx,gy=1110,742
lx,ly=1054,742
s+=f'<circle cx="{lx}" cy="{ly}" r="20" fill="{VELLUM}" stroke="{INK}" stroke-width="2"/>'
s+=f'<circle cx="{lx}" cy="{ly}" r="12" fill="none" stroke="{INK}" stroke-width="2"/>'
s+=f'<ellipse cx="{lx}" cy="{ly}" rx="5" ry="12" fill="none" stroke="{INK}" stroke-width="1.6"/>'
s+=f'<line x1="{lx-12}" y1="{ly}" x2="{lx+12}" y2="{ly}" stroke="{INK}" stroke-width="1.6"/>'
s+=f'<line x1="{lx-10}" y1="{ly-6}" x2="{lx+10}" y2="{ly-6}" stroke="{INK}" stroke-width="1.2"/>'
s+=f'<line x1="{lx-10}" y1="{ly+6}" x2="{lx+10}" y2="{ly+6}" stroke="{INK}" stroke-width="1.2"/>'
s+=f'<circle cx="{gx}" cy="{gy}" r="20" fill="{VELLUM}" stroke="{INK}" stroke-width="2"/>'
for a in range(8):
    import math; ang=a*math.pi/4
    s+=f'<line x1="{gx+11*math.cos(ang):.0f}" y1="{gy+11*math.sin(ang):.0f}" x2="{gx+17*math.cos(ang):.0f}" y2="{gy+17*math.sin(ang):.0f}" stroke="{INK}" stroke-width="2.5"/>'
s+=f'<circle cx="{gx}" cy="{gy}" r="6" fill="none" stroke="{INK}" stroke-width="2"/>'
qx,qy=1166,742
s+=f'<circle cx="{qx}" cy="{qy}" r="20" fill="{VELLUM}" stroke="{INK}" stroke-width="2"/><path d="M{qx} {qy-10} A10 10 0 1 1 {qx-6} {qy-8}" fill="none" stroke="{INK}" stroke-width="2.5"/><line x1="{qx}" y1="{qy-13}" x2="{qx}" y2="{qy}" stroke="{INK}" stroke-width="2.5"/>'
s+=f'<text x="1140" y="700" text-anchor="middle" font-family="Georgia, serif" font-size="13" fill="{SEP}">language   ·   settings   ·   quit</text>'
# corner motifs top
s+=f'<g stroke="{SEP}" stroke-width="2" fill="none"><circle cx="92" cy="92" r="13"/><line x1="101" y1="101" x2="112" y2="112"/></g>'
s+=f'<g stroke="{SEP}" stroke-width="2" fill="none"><circle cx="{W-92}" cy="92" r="15"/><line x1="{W-92}" y1="79" x2="{W-92}" y2="84"/><line x1="{W-92}" y1="92" x2="{W-84}" y2="92"/></g>'
s+=f'<text x="92" y="752" font-family="Georgia, serif" font-size="15" fill="{SEP}">v1.0</text>'
s+='</svg>'
open("/sessions/happy-optimistic-pasteur/mnt/outputs/presets/menu_mock2.svg","w").write(s)
cairosvg.svg2png(url="/sessions/happy-optimistic-pasteur/mnt/outputs/presets/menu_mock2.svg",write_to="/sessions/happy-optimistic-pasteur/mnt/outputs/presets/menu_mock2.png",output_width=1280,output_height=800)
print("done")
