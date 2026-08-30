import cairosvg, os
OUT_SVG="/sessions/happy-optimistic-pasteur/mnt/outputs/presets/svg"
OUT_PNG="/sessions/happy-optimistic-pasteur/mnt/outputs/presets/png"
INK="#241E17"; LINE="#3A2F1E"; PAPER="#E8DCC0"; VELLUM="#ECE0C4"; OCHRE="#C8893A"
PETROL="#1C5D6E"; OXBLOOD="#8E3B2E"; SEP="#9C8460"; HATCH="#C9B68C"

def head(hcx,hcy,skin,shade):
    s=f'<ellipse cx="{hcx}" cy="{hcy}" rx="62" ry="72" fill="{skin}" stroke="{INK}" stroke-width="3"/>'
    # cheek shade right
    s+=f'<path d="M{hcx+30} {hcy-40} Q{hcx+64} {hcy} {hcx+30} {hcy+50} Q{hcx+50} {hcy} {hcx+30} {hcy-40} Z" fill="{shade}" opacity="0.5"/>'
    s+=f'<ellipse cx="{hcx-60}" cy="{hcy+6}" rx="8" ry="12" fill="{skin}" stroke="{INK}" stroke-width="2"/>'
    s+=f'<ellipse cx="{hcx+60}" cy="{hcy+6}" rx="8" ry="12" fill="{skin}" stroke="{INK}" stroke-width="2"/>'
    return s

def eyes(hcx,hcy,glasses):
    y=hcy-6; lx=hcx-22; rx=hcx+22
    s=f'<path d="M{lx-12} {y-12} q12 -7 24 0" stroke="{INK}" stroke-width="2.4" fill="none"/>'
    s+=f'<path d="M{rx-12} {y-12} q12 -7 24 0" stroke="{INK}" stroke-width="2.4" fill="none"/>'
    s+=f'<circle cx="{lx}" cy="{y}" r="5.5" fill="{INK}"/><circle cx="{rx}" cy="{y}" r="5.5" fill="{INK}"/>'
    s+=f'<circle cx="{lx+2}" cy="{y-2}" r="1.6" fill="#FFFFFF" opacity="0.6"/><circle cx="{rx+2}" cy="{y-2}" r="1.6" fill="#FFFFFF" opacity="0.6"/>'
    if glasses:
        s+=f'<circle cx="{lx}" cy="{y}" r="16" fill="none" stroke="{INK}" stroke-width="2.4"/>'
        s+=f'<circle cx="{rx}" cy="{y}" r="16" fill="none" stroke="{INK}" stroke-width="2.4"/>'
        s+=f'<line x1="{lx+16}" y1="{y}" x2="{rx-16}" y2="{y}" stroke="{INK}" stroke-width="2.4"/>'
    return s

def nose(hcx,hcy):
    return f'<path d="M{hcx} {hcy-4} l-7 22 l12 0" stroke="{INK}" stroke-width="1.8" fill="none"/>'

def mouth(hcx,hcy,kind):
    y=hcy+34
    if kind=="smile": return f'<path d="M{hcx-16} {y} q16 11 32 0" stroke="{INK}" stroke-width="1.8" fill="none"/>'
    if kind=="soft": return f'<path d="M{hcx-12} {y} q12 6 24 0" stroke="{INK}" stroke-width="1.8" fill="none"/>'
    return f'<path d="M{hcx-13} {y} q13 4 26 0" stroke="{INK}" stroke-width="1.8" fill="none"/>'

def hair(hcx,hcy,col,style):
    s=""
    if style=="short":
        s+=f'<path d="M{hcx-58} {hcy-30} Q{hcx-66} {hcy-78} {hcx} {hcy-80} Q{hcx+66} {hcy-78} {hcx+58} {hcy-30} Q{hcx+40} {hcy-58} {hcx} {hcy-58} Q{hcx-40} {hcy-58} {hcx-58} {hcy-30} Z" fill="{col}" stroke="{INK}" stroke-width="2"/>'
    elif style=="bald":
        s+=f'<path d="M{hcx-58} {hcy-6} Q{hcx-62} {hcy-44} {hcx-46} {hcy-54} L{hcx-40} {hcy-30} Q{hcx-52} {hcy-16} {hcx-50} {hcy+8} Z" fill="{col}"/>'
        s+=f'<path d="M{hcx+58} {hcy-6} Q{hcx+62} {hcy-44} {hcx+46} {hcy-54} L{hcx+40} {hcy-30} Q{hcx+52} {hcy-16} {hcx+50} {hcy+8} Z" fill="{col}"/>'
    elif style=="updo":
        s+=f'<path d="M{hcx-60} {hcy-10} Q{hcx-70} {hcy-76} {hcx} {hcy-82} Q{hcx+70} {hcy-76} {hcx+60} {hcy-10} Q{hcx+44} {hcy-44} {hcx} {hcy-44} Q{hcx-44} {hcy-44} {hcx-60} {hcy-10} Z" fill="{col}" stroke="{INK}" stroke-width="2"/>'
        s+=f'<ellipse cx="{hcx}" cy="{hcy-88}" rx="26" ry="20" fill="{col}" stroke="{INK}" stroke-width="2"/>'
    elif style=="curls":
        for dx in range(-56,57,16):
            s+=f'<circle cx="{hcx+dx}" cy="{hcy-58+ (abs(dx)//8)}" r="13" fill="{col}" stroke="{INK}" stroke-width="1.5"/>'
        s+=f'<rect x="{hcx-58}" y="{hcy-58}" width="116" height="26" fill="{col}"/>'
    elif style=="long":
        s+=f'<path d="M{hcx-58} {hcy-30} Q{hcx-66} {hcy-80} {hcx} {hcy-82} Q{hcx+66} {hcy-80} {hcx+58} {hcy-30} L{hcx+62} {hcy+60} L{hcx+44} {hcy+60} Q{hcx+44} {hcy-40} {hcx} {hcy-54} Q{hcx-44} {hcy-40} {hcx-44} {hcy+60} L{hcx-62} {hcy+60} Z" fill="{col}" stroke="{INK}" stroke-width="2"/>'
    return s

def facial(hcx,hcy,col,kind):
    if kind=="mustache":
        y=hcy+24; return f'<path d="M{hcx-24} {y} Q{hcx} {y-6} {hcx+24} {y} Q{hcx+12} {y+12} {hcx} {y+6} Q{hcx-12} {y+12} {hcx-24} {y} Z" fill="{col}"/>'
    if kind=="beard":
        s=f'<path d="M{hcx-46} {hcy+6} Q{hcx-40} {hcy+70} {hcx} {hcy+76} Q{hcx+40} {hcy+70} {hcx+46} {hcy+6} Q{hcx+30} {hcy+40} {hcx} {hcy+42} Q{hcx-30} {hcy+40} {hcx-46} {hcy+6} Z" fill="{col}" stroke="{INK}" stroke-width="1.5"/>'
        s+=f'<path d="M{hcx-24} {hcy+22} Q{hcx} {hcy+16} {hcx+24} {hcy+22} Q{hcx+12} {hcy+32} {hcx} {hcy+27} Q{hcx-12} {hcy+32} {hcx-24} {hcy+22} Z" fill="{col}"/>'
        return s
    if kind=="muttonchops":
        return (f'<path d="M{hcx-58} {hcy-20} L{hcx-50} {hcy+46} L{hcx-30} {hcy+40} Q{hcx-40} {hcy+10} {hcx-40} {hcy-24} Z" fill="{col}" stroke="{INK}" stroke-width="1.2"/>'
                f'<path d="M{hcx+58} {hcy-20} L{hcx+50} {hcy+46} L{hcx+30} {hcy+40} Q{hcx+40} {hcy+10} {hcx+40} {hcy-24} Z" fill="{col}" stroke="{INK}" stroke-width="1.2"/>')
    return ""

def headwear(hcx,hcy,kind,col):
    if kind=="tophat":
        return (f'<rect x="{hcx-52}" y="{hcy-150}" width="104" height="72" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<ellipse cx="{hcx}" cy="{hcy-78}" rx="82" ry="14" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<rect x="{hcx-52}" y="{hcy-98}" width="104" height="12" fill="{OXBLOOD}"/>')
    if kind=="bowler":
        return (f'<path d="M{hcx-50} {hcy-78} Q{hcx-54} {hcy-128} {hcx} {hcy-128} Q{hcx+54} {hcy-128} {hcx+50} {hcy-78} Z" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<ellipse cx="{hcx}" cy="{hcy-78}" rx="72" ry="12" fill="{col}" stroke="{INK}" stroke-width="3"/>')
    if kind=="flatcap":
        return (f'<path d="M{hcx-54} {hcy-72} Q{hcx-58} {hcy-104} {hcx} {hcy-108} Q{hcx+58} {hcy-104} {hcx+54} {hcy-78} Z" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<path d="M{hcx-54} {hcy-76} L{hcx-92} {hcy-66} L{hcx-50} {hcy-66} Z" fill="{col}" stroke="{INK}" stroke-width="2"/>')
    if kind=="deerstalker":
        return (f'<path d="M{hcx-52} {hcy-76} Q{hcx-58} {hcy-122} {hcx} {hcy-122} Q{hcx+58} {hcy-122} {hcx+52} {hcy-76} Z" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<ellipse cx="{hcx}" cy="{hcy-76}" rx="78" ry="12" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<circle cx="{hcx-60}" cy="{hcy-80}" r="13" fill="{col}" stroke="{INK}" stroke-width="2"/>'
                f'<circle cx="{hcx+60}" cy="{hcy-80}" r="13" fill="{col}" stroke="{INK}" stroke-width="2"/>'
                f'<rect x="{hcx-8}" y="{hcy-132}" width="16" height="14" fill="{col}" stroke="{INK}" stroke-width="2"/>')
    if kind=="bonnet":
        return (f'<path d="M{hcx-64} {hcy} Q{hcx-86} {hcy-92} {hcx} {hcy-96} Q{hcx+86} {hcy-92} {hcx+64} {hcy} L{hcx+50} {hcy} Q{hcx+56} {hcy-72} {hcx} {hcy-74} Q{hcx-56} {hcy-72} {hcx-50} {hcy} Z" fill="{col}" stroke="{INK}" stroke-width="3"/>'
                f'<path d="M{hcx-50} {hcy} Q{hcx-30} {hcy+40} {hcx-10} {hcy+34}" fill="none" stroke="{INK}" stroke-width="2"/>')
    if kind=="hood":
        return (f'<path d="M{hcx-70} {hcy+20} Q{hcx-80} {hcy-90} {hcx} {hcy-96} Q{hcx+80} {hcy-90} {hcx+70} {hcy+20} L{hcx+54} {hcy+20} Q{hcx+58} {hcy-66} {hcx} {hcy-70} Q{hcx-58} {hcy-66} {hcx-54} {hcy+20} Z" fill="{col}" stroke="{INK}" stroke-width="3"/>')
    return ""

def neckwear(hcx,hcy,kind):
    y=hcy+92
    if kind=="cravat": return f'<path d="M{hcx-16} {y-16} L{hcx+16} {y-16} L{hcx+10} {y+34} L{hcx} {y+44} L{hcx-10} {y+34} Z" fill="{LINE}" stroke="{INK}" stroke-width="1.5"/>'
    if kind=="scarf": return f'<path d="M{hcx-30} {y-18} Q{hcx} {y+2} {hcx+30} {y-18} L{hcx+30} {y+30} L{hcx-30} {y+30} Z" fill="{PETROL}" stroke="{INK}" stroke-width="2"/>'
    if kind=="bowtie": return f'<path d="M{hcx-22} {y-6} L{hcx-4} {y+4} L{hcx-22} {y+14} Z" fill="{LINE}"/><path d="M{hcx+22} {y-6} L{hcx+4} {y+4} L{hcx+22} {y+14} Z" fill="{LINE}"/><circle cx="{hcx}" cy="{y+4}" r="5" fill="{LINE}"/>'
    if kind=="lace": return f'<path d="M{hcx-40} {y-10} Q{hcx-30} {y+8} {hcx-20} {y-10} Q{hcx-10} {y+8} {hcx} {y-10} Q{hcx+10} {y+8} {hcx+20} {y-10} Q{hcx+30} {y+8} {hcx+40} {y-10}" fill="none" stroke="{INK}" stroke-width="2"/>'
    return ""

def portrait(fname,label,accent=OCHRE,skin="#E2CBA8",shade="#C7AC86",haircol="#3A2A22",
             hstyle="short",hw=None,hwcol="#2C2419",fh=None,glasses=False,collar="shirt",neck="cravat",coat="#5A4A35"):
    hcx,hcy=240,300
    p=f'<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600" viewBox="0 0 480 600">'
    p+=f'<rect width="480" height="600" fill="{PAPER}"/>'
    p+='<defs><clipPath id="m"><ellipse cx="240" cy="270" rx="133" ry="162"/></clipPath>'
    p+=f'<clipPath id="hd"><ellipse cx="{hcx}" cy="{hcy}" rx="62" ry="72"/></clipPath>'
    p+='<clipPath id="ct"><path d="M120 432 Q240 350 360 432 Z"/></clipPath></defs>'
    p+=f'<g clip-path="url(#m)"><rect x="107" y="108" width="266" height="324" fill="{VELLUM}"/>'
    for yy in range(360,432,10):
        p+=f'<line x1="107" y1="{yy}" x2="373" y2="{yy}" stroke="{HATCH}" stroke-width="0.8"/>'
    # coat
    p+=f'<path d="M120 432 Q240 350 360 432 Z" fill="{coat}" stroke="{INK}" stroke-width="3"/>'
    ch='<g clip-path="url(#ct)" stroke="#2A2218" stroke-width="0.8" opacity="0.55">'
    for x in range(150,380,9): ch+=f'<line x1="{x}" y1="356" x2="{x-46}" y2="434"/>'
    for x in range(150,380,16): ch+=f'<line x1="{x}" y1="380" x2="{x+40}" y2="434"/>'
    ch+='</g>'; p+=ch
    if collar=="shirt": p+=f'<path d="M210 392 L240 424 L270 392" fill="{VELLUM}" stroke="{INK}" stroke-width="2"/>'
    if collar=="lace": p+=f'<path d="M200 396 Q240 430 280 396 L280 432 L200 432 Z" fill="#D8C9A6" stroke="{INK}" stroke-width="2"/>'
    if collar=="apron": p+=f'<path d="M208 398 L240 420 L272 398 L272 432 L208 432 Z" fill="#D8C9A6" stroke="{INK}" stroke-width="2"/>'
    p+=neckwear(hcx,hcy,neck)
    p+=f'<rect x="{hcx-18}" y="{hcy+58}" width="36" height="30" fill="{skin}"/>'
    p+=hair(hcx,hcy,haircol,hstyle)
    p+=head(hcx,hcy,skin,shade)
    fh2='<g clip-path="url(#hd)" stroke="#A9865E" stroke-width="0.8" opacity="0.7">'
    for i in range(7): fh2+=f'<line x1="{hcx+16+i*7}" y1="{hcy-34}" x2="{hcx+8+i*7}" y2="{hcy+46}"/>'
    for i in range(6): fh2+=f'<line x1="{hcx-28+i*13}" y1="{hcy+46}" x2="{hcx-32+i*13}" y2="{hcy+66}"/>'
    for i in range(4): fh2+=f'<line x1="{hcx+22+i*9}" y1="{hcy-30}" x2="{hcx+34+i*9}" y2="{hcy+40}"/>'
    fh2+='</g>'; p+=fh2
    p+=facial(hcx,hcy,haircol,fh) if fh in("muttonchops",) else ""
    p+=eyes(hcx,hcy,glasses)
    p+=nose(hcx,hcy)
    p+=mouth(hcx,hcy,"soft" if collar=="lace" else "neutral")
    if fh and fh!="muttonchops": p+=facial(hcx,hcy,haircol,fh)
    if hw: p+=headwear(hcx,hcy,hw,hwcol)
    # engraving hatch shading lower-left of medallion
    vg='<g stroke="#7C6A4A" stroke-width="0.7" opacity="0.5">'
    for i in range(14): vg+=f'<line x1="{112+i*6}" y1="432" x2="{112+i*6-26}" y2="380"/>'
    for i in range(14): vg+=f'<line x1="{368-i*6}" y1="432" x2="{368-i*6+26}" y2="380"/>'
    vg+='</g>'; p+=vg
    p+='</g>'
    p+=f'<ellipse cx="240" cy="270" rx="150" ry="180" fill="none" stroke="{INK}" stroke-width="5"/>'
    p+=f'<ellipse cx="240" cy="270" rx="137" ry="166" fill="none" stroke="{accent}" stroke-width="3"/>'
    p+=f'<rect x="60" y="498" width="360" height="54" fill="#F2E9D2" stroke="{INK}" stroke-width="3"/>'
    tcol=accent if accent!=OCHRE else INK
    p+=f'<text x="240" y="535" text-anchor="middle" font-family="Georgia, serif" font-size="30" font-weight="bold" fill="{tcol}">{label}</text>'
    p+='</svg>'
    open(f"{OUT_SVG}/{fname}.svg","w").write(p)
    cairosvg.svg2png(url=f"{OUT_SVG}/{fname}.svg",write_to=f"{OUT_PNG}/{fname}.png",output_width=480,output_height=600)
    print("ok",fname)

# named anchors
portrait("char_watson","Dr. Watson",accent=OCHRE,hstyle="bald",fh="mustache",neck="bowtie",coat="#5A4A35")
portrait("char_partner","Your partner",accent=PETROL,hw="deerstalker",hwcol="#5A4A35",neck="scarf",coat="#4A3D2A")
# 12 suspects
portrait("char_suspect_01","",accent=OCHRE,hw="tophat",fh="mustache",neck="cravat")
portrait("char_suspect_02","",accent=OCHRE,skin="#D9BE98",hstyle="updo",collar="lace",neck="lace")
portrait("char_suspect_03","",accent=OCHRE,hw="flatcap",hwcol="#5A4A35",collar="apron",neck=None,coat="#4A3D2A")
portrait("char_suspect_04","",accent=OCHRE,hstyle="bald",haircol="#8A7A60",glasses=True,neck="bowtie")
portrait("char_suspect_05","",accent=OCHRE,skin="#C79A6E",shade="#A87E58",hstyle="short",fh="beard",neck="cravat")
portrait("char_suspect_06","",accent=OCHRE,skin="#E0C7A2",hstyle="curls",collar="lace",neck="lace")
portrait("char_suspect_07","",accent=OCHRE,hw="bowler",fh="muttonchops",neck="cravat")
portrait("char_suspect_08","",accent=OCHRE,skin="#B98A5E",shade="#946A40",hstyle="short",fh="mustache",neck="scarf")
portrait("char_suspect_09","",accent=OCHRE,skin="#DCC09A",hstyle="long",collar="lace",neck=None)
portrait("char_suspect_10","",accent=OCHRE,hstyle="slicked" if False else "short",haircol="#2C2419",glasses=True,fh="beard",neck="cravat")
portrait("char_suspect_11","",accent=OCHRE,hw="hood",hwcol="#4A3D2A",skin="#D9BE98",neck=None,coat="#4A3D2A")
portrait("char_suspect_12","",accent=OCHRE,skin="#C79A6E",shade="#A87E58",hw="bonnet",hwcol="#6B4A4E",collar="lace",neck="lace")
print("DONE chars")
