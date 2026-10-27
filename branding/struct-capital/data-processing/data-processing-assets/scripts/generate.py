#!/usr/bin/env python3
"""Draw language-neutral investment data-processing artwork as portable SVG paths.
Rebuild with Python 3 and rsvg-convert (librsvg). No third-party Python packages.
"""
from pathlib import Path
import json
import subprocess
import shutil
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOGO = ET.parse(ROOT / 'scripts/struct-capital-symbol.svg').getroot()
LOGO_PATHS = list(LOGO.iter('{http://www.w3.org/2000/svg}path'))
THEMES = {
    'dark': dict(ink='#E3D19A', fine='#AFA16A', accent='#C7AC50', surface='#272B1B', face='#343723', inset='#1E2419'),
    'light': dict(ink='#6D5E0F', fine='#A19563', accent='#9D8120', surface='#FAF8F2', face='#EEE8D0', inset='#DDD3A7'),
    'mono-olive': dict(ink='#6D5E0F', fine='#6D5E0F', accent='#6D5E0F', surface='#FAF8F2', face='#FAF8F2', inset='#FAF8F2'),
    'mono-cream': dict(ink='#F2E6A7', fine='#F2E6A7', accent='#F2E6A7', surface='#272B1B', face='#272B1B', inset='#272B1B'),
}

def f(n): return f'{n:.3f}'.rstrip('0').rstrip('.')
def point(p): return ' '.join(f(v) for v in p)
def path(d, color, sw=1.7, fill='none', extra=''):
    return f'<path d="{d}" fill="{fill}" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round" {extra}/>'
def poly(points, color, sw=1.7, fill='none'):
    return path('M'+'L'.join(point(p) for p in points)+'Z',color,sw,fill)
def circle(x,y,r,color,fill='none',sw=1.6):
    return f'<circle cx="{f(x)}" cy="{f(y)}" r="{r}" fill="{fill}" stroke="{color}" stroke-width="{sw}"/>'
def rect(x,y,w,h,r,fill,color,sw=1.7):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{color}" stroke-width="{sw}"/>'
def iso(u,v,depth=0): return (320+(u-v)*120,100+(u+v)*66+depth)
def quad(a,b,depth=0): return [iso(a,a,depth),iso(b,a,depth),iso(b,b,depth),iso(a,b,depth)]


def ribbon(p):
    """A wire mesh ribbon: longitudinal splines plus curved cross sections."""
    out=['<g id="flowing-data-ribbon">']
    def controls(i,segment):
        if segment==0: return [(38,354+i*4),(78,285+i*3.8),(142,327+i*1.8),(187,283+i*1.7)]
        return [(187,283+i*1.7),(211,261+i*1.4),(214,248+i*1.2),(239,236+i*1.0)]
    def bezier(c,t):
        return tuple((1-t)**3*c[0][j]+3*(1-t)**2*t*c[1][j]+3*(1-t)*t*t*c[2][j]+t**3*c[3][j] for j in (0,1))
    for i in range(13):
        a=controls(i,0); b=controls(i,1)
        out += [path('M'+point(a[0])+'C'+' '.join(point(q) for q in a[1:])+'C'+' '.join(point(q) for q in b[1:]),p['accent'],.85)]
    for segment in (0,1):
        for step in range(1,15):
            t=step/15
            pts=[bezier(controls(i,segment),t) for i in (0,4,8,12)]
            out += [path('M'+point(pts[0])+'L'+'L'.join(point(q) for q in pts[1:]),p['fine'],.65)]
    return ''.join(out)+'</g>'


def pin_ends():
    result=[]
    for side in ('left','right'):
        for i in range(10):
            t=.08+i*.09
            a=iso(t,1,21) if side=='left' else iso(1,t,21)
            dx=-8 if side=='left' else 8
            result.append((side,i,a,(a[0]+dx,a[1]+9),(a[0]+dx,a[1]+16)))
    return result


def printed_logo(p,x,y,scale,name):
    paths=[]
    for element in LOGO_PATHS:
        d=element.attrib['d']
        paths.append(path(d,p['ink'],8) if element.attrib.get('fill')=='none' else f'<path d="{d}" fill="{p["ink"]}"/>')
    return f'<g id="{name}-struct-capital-symbol" transform="translate({x} {y}) scale({scale})"><g transform="translate(-248 -183)">{"".join(paths)}</g></g>'


def engraved_logo(p):
    """Project the official symbol onto the same u/v plane as the silicon die.

    The source artwork's 512-square canvas maps to u/v .31..69. Offsets are
    applied after projection so the shallow engraving bevel has uniform depth.
    """
    def layer(color,offset=0):
        paths=[]
        for element in LOGO_PATHS:
            d=element.attrib['d']
            if element.attrib.get('fill')=='none':
                paths.append(path(d,color,8))
            else:
                paths.append(f'<path d="{d}" fill="{color}"/>')
        return f'<g transform="translate(0 {offset})"><g transform="matrix(.0890625 .048984375 -.0890625 .048984375 320 140.92)"><g transform="translate(-248 -183)">{"".join(paths)}</g></g></g>'
    out=['<g id="struct-capital-engraving">']
    if p['ink'] != p['fine']:
        # Tiny opposing rims suggest a laser-etched, recessed metal marking.
        out += [layer(p['surface'],-.6),layer(p['ink'],.55)]
    out += [layer(p['accent'])]
    return ''.join(out)+'</g>'


def core(p):
    c=p['ink']; fine=p['fine']; out=['<g id="isometric-processor-core">']
    # Leadframe: pins are behind the chip body.
    for side,i,a,b,end in pin_ends():
        out += [path('M'+point(a)+'L'+point(b)+'L'+point(end),c,2.1),path(f'M{f(a[0]+3)} {f(a[1])}l{3 if side=="right" else -3} 6v7',fine,.7)]
    out += [poly([iso(0,1),iso(1,1),iso(1,1,22),iso(0,1,22)],c,1.8,p['face']),poly([iso(1,0),iso(1,1),iso(1,1,22),iso(1,0,22)],c,1.8,p['surface'])]
    for i in range(1,33):
        t=i/33
        for a,b in [(iso(t,1,3),iso(t,1,19)),(iso(1,t,3),iso(1,t,19))]: out += [path('M'+point(a)+'L'+point(b),fine,.65)]
    out += [poly(quad(0,1),c,2.2,p['face'])]
    # Crosshatched substrate engraved into the top package.
    for i in range(1,43):
        t=i/43
        out += [path('M'+point(iso(t,.025))+'L'+point(iso(t,.975)),fine,.55),path('M'+point(iso(.025,t))+'L'+point(iso(.975,t)),fine,.55)]
    out += [poly(quad(.065,.935),c,1.15),poly(quad(.255,.745),c,1.8,p['surface']),poly(quad(.295,.705),p['accent'],1.3,p['inset']),path('M'+point(iso(.295,.705))+'L'+point(iso(.295,.295))+'L'+point(iso(.705,.295)),fine,.8)]
    out += [engraved_logo(p),circle(*iso(.13,.84),2,c,p['surface'],1)]
    return ''.join(out)+'</g>'


def traces(p):
    out=['<g id="branching-circuit-traces">']
    for side,i,a,b,start in pin_ends():
        x,y=start
        if side=='right':
            ex=595-i*13; ey=192+i*22
            d=f'M{point(start)}v9Q{f(x)} {f(y+18)} {f(x+13)} {f(y+22)}L{f(ex-15)} {f(ey)}H{f(ex-4)}'
            out += [path(d,p['ink'],1.45),circle(ex,ey,3,p['accent'],p['surface'],1.25)]
        else:
            # Left traces curve toward the incoming mesh, without node markers.
            ex=190-i*6; ey=270+i*10
            d=f'M{point(start)}v5Q{f(x)} {f(y+13)} {f(x-12)} {f(y+17)}L{f(ex+14)} {f(ey)}H{f(ex)}'
            out += [path(d,p['ink'],1.25)]
    # Upper right and top clock/data connections.
    for i in range(5):
        a=iso(.22+i*.135,0)
        ex=471+i*23; ey=92+i*17
        out += [path(f'M{point(a)}l18 -10L{f(ex-18)} {f(ey+9)}Q{f(ex-9)} {ey} {f(ex-4)} {ey}',p['ink'],1.3),circle(ex,ey,3,p['accent'],p['surface'],1.2)]
    out += [path('M320 100V58',p['ink'],1.5),poly([(316,62),(320,55),(324,62)],p['ink'],1.2,p['surface'])]
    return ''.join(out)+'</g>'


def processor(p): return ribbon(p)+traces(p)+core(p)


def document(p,stack=True):
    c=p['ink']; out=['<g id="document-stack">']
    if stack:
        for offset in (24,16,8):
            out += [path(f'M{43-offset} {40+offset}H{158-offset}L{202-offset} {84+offset}V{246+offset}Q{202-offset} {252+offset} {196-offset} {252+offset}H{43-offset}Q{37-offset} {252+offset} {37-offset} {246+offset}V{46+offset}Q{37-offset} {40+offset} {43-offset} {40+offset}Z',p['fine'],1.45,p['surface'])]
    out += [path('M43 40H158L202 84V246Q202 252 196 252H43Q37 252 37 246V46Q37 40 43 40Z',c,2,p['surface']),path('M158 40V78Q158 84 164 84H202',c,1.6,p['face'])]
    for i in range(1,9):
        x=159+i*4
        out += [path(f'M{x} {44+i*4}V81',p['fine'],.6)]
    for y,length in [(68,48),(80,48),(92,33),(115,117),(127,117),(139,117),(151,117),(163,117),(175,117),(187,87),(199,49),(224,43),(234,43)]:
        x=56 if y<220 else 141
        out += [path(f'M{x} {y}h{length}',c,1.4)]
    out += [printed_logo(p,110,54,.075,'document')]
    return ''.join(out)+'</g>'


def bar_chart(p):
    c=p['ink']; out=['<g id="allocation-bar-chart">',path('M29 25V191H222',c,1.9)]
    bars=[(44,49),(78,89),(112,128),(146,101),(180,153)]
    for i,(x,h) in enumerate(bars):
        y=190-h
        out += [rect(x,y,23,h,0,p['face'] if i in (0,4) else p['surface'],c,1.7)]
        if i in (0,4):
            # Diagonals clipped algebraically to each bar rectangle.
            for k in range(-int(h),24,6):
                start=max(0,-k); end=min(h,23-k)
                if start<end: out += [path(f'M{x+k+start} {y+start}L{x+k+end} {y+end}',p['fine'],.65)]
    return ''.join(out)+'</g>'


def line_chart(p):
    out=['<g id="portfolio-value-graph">']
    for y in range(49,190,28): out += [path(f'M30 {y}H220',p['fine'],.65)]
    for x in range(57,222,28): out += [path(f'M{x} 27V191',p['fine'],.65)]
    out += [path('M29 25V191H222',p['ink'],1.9),path('M33 177L54 139L72 157L94 121L114 146L137 94L153 117L173 67L188 98L215 35',p['ink'],2.5),path('M201 40L216 31L217 48',p['accent'],2)]
    return ''.join(out)+'</g>'


def event_calendar(p):
    c=p['ink']; out=['<g id="event-calendar">']
    out += [path('M45 58H175Q185 58 185 68V173L157 201H45Q35 201 35 191V68Q35 58 45 58Z',c,2,p['surface']),path('M35 85H185',c,1.6),path('M157 201V183Q157 173 167 173H185',c,1.6,p['face'])]
    for x in (64,155): out += [rect(x-5,45,10,26,4,p['surface'],c,1.8)]
    for row in range(4):
        for col in range(5):
            if row==3 and col==4: continue
            x=51+col*24; y=100+row*22
            fill=p['face'] if (row,col) in [(0,3),(1,1),(2,3)] else 'none'
            out += [rect(x,y,23,21,0,fill,p['fine'],.85)]
    out += [printed_logo(p,99,61,.042,'calendar'),path('M78 132l4 4 8-9',p['accent'],1.9)]
    return ''.join(out)+'</g>'


def arrow(d,end,direction,p):
    x,y=end
    if direction=='right': head=f'M{x-7} {y-5}L{x} {y}L{x-7} {y+5}'
    elif direction=='up': head=f'M{x-5} {y+7}L{x} {y}L{x+5} {y+7}'
    else: head=f'M{x-5} {y-7}L{x} {y}L{x+5} {y-7}'
    return path(d,p['ink'],1.7)+path(head,p['ink'],1.7)


def connectors(p):
    return '<g id="flow-connectors">'+''.join([
        arrow('M207 177H261Q287 177 287 203V264',(287,264),'down',p),
        arrow('M572 258H607Q632 258 632 233V201Q632 180 654 180',(654,180),'right',p),
        arrow('M626 355H654',(654,355),'right',p),
        arrow('M508 430V529Q508 550 529 550H654',(654,550),'right',p),
    ])+'</g>'


def pipeline(p):
    return ''.join([connectors(p),'<g transform="translate(25 60) scale(.85)">',document(p),'</g>', '<g transform="translate(160 170) scale(.78)">',processor(p),'</g>', '<g transform="translate(660 64) scale(.9)">',bar_chart(p),'</g>', '<g transform="translate(660 282) scale(.9)">',line_chart(p),'</g>', '<g transform="translate(660 476) scale(.9)">',event_calendar(p),'</g>'])

ASSETS={
    'processor-flow': ('0 0 640 460',640,460,processor,'Isometric processor with branching circuit traces and a flowing mesh data ribbon.'),
    'processor-core': ('160 70 320 240',480,360,core,'Isometric microprocessor with engraved substrate, inset die with the Struct Capital symbol engraved into its surface, and exposed pins.'),
    'circuit-traces': ('105 35 520 385',624,462,traces,'Independent branching circuit paths with terminal nodes and a data arrow.'),
    'data-ribbon': ('18 216 246 210',492,420,ribbon,'Flowing wire mesh ribbon, independent of the processor.'),
    'document-stack': ('0 15 240 285',320,380,document,'Stack of folded financial documents with abstract ruled lines.'),
    'document': ('16 15 208 265',312,397,lambda p:document(p,False),'Single folded financial document with abstract ruled lines.'),
    'allocation-chart': ('0 0 250 220',400,352,bar_chart,'Five-bar allocation chart with engraved diagonal accents.'),
    'value-chart': ('0 0 250 220',400,352,line_chart,'Rising portfolio value graph on a fine grid with an arrow tip.'),
    'event-calendar': ('15 25 190 200',342,360,event_calendar,'Small event calendar with binding rings, folded corner, and a check mark.'),
    'flow-connectors': ('175 145 510 435',510,435,connectors,'Separate curved routing arrows for assembling a data-processing diagram.'),
    'data-pipeline': ('0 0 920 700',920,700,pipeline,'Complete language-neutral illustration of documents flowing through a processor into charts and a calendar.'),
}


def svg(kind,theme,background=False):
    view,w,h,draw,desc=ASSETS[kind]; p=THEMES[theme]
    bg=f'<rect x="{view.split()[0]}" y="{view.split()[1]}" width="{view.split()[2]}" height="{view.split()[3]}" fill="{p["surface"]}"/>' if background else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{view}" fill="none" role="img" aria-labelledby="title desc"><title id="title">{kind.replace("-"," ").title()}</title><desc id="desc">{desc}</desc>{bg}{draw(p)}</svg>\n'


def preview(name,kinds):
    # Theme colors are the columns. All illustration canvases themselves stay transparent.
    cellw,cellh=460,370
    rows=len(kinds); out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{cellw*4}" height="{cellh*rows}" viewBox="0 0 {cellw*4} {cellh*rows}">']
    for row,kind in enumerate(kinds):
        for col,theme in enumerate(THEMES):
            x=col*cellw; y=row*cellh; p=THEMES[theme]; view=ASSETS[kind][0]
            out += [rect(x,y,cellw,cellh,0,p['surface'],'none',0),f'<svg x="{x+18}" y="{y+15}" width="{cellw-36}" height="{cellh-58}" viewBox="{view}">{ASSETS[kind][3](p)}</svg>', f'<text x="{x+22}" y="{y+cellh-20}" font-family="sans-serif" font-size="15" fill="{p["ink"]}">{kind} / {theme}</text>']
    out+=['</svg>']; (ROOT/f'{name}.svg').write_text(''.join(out))
    subprocess.run(['rsvg-convert','-o',str(ROOT/f'{name}.png'),str(ROOT/f'{name}.svg')],check=True)


def main():
    for directory in ('svg','png/1x','png/2x','png/3x'): (ROOT/directory).mkdir(parents=True,exist_ok=True)
    manifest=[]
    for kind,(view,w,h,draw,desc) in ASSETS.items():
        for theme in THEMES:
            name=f'{kind}-{theme}'; source=ROOT/'svg'/f'{name}.svg'; source.write_text(svg(kind,theme))
            exports=[]
            for scale in (1,2,3):
                dest=ROOT/'png'/f'{scale}x'/f'{name}.png'
                subprocess.run(['rsvg-convert','-w',str(w*scale),'-h',str(h*scale),'-o',str(dest),str(source)],check=True)
                exports.append(dict(path=str(dest.relative_to(ROOT)),width=w*scale,height=h*scale))
            manifest.append(dict(name=name,component=kind,theme=theme,locale=None,transparent=True,svg=str(source.relative_to(ROOT)),viewBox=view,width=w,height=h,png=exports))
    (ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    preview('preview',list(ASSETS))
    preview('processor-preview',['processor-flow'])
    preview('chip-preview',['processor-core'])
    for theme in ('dark','light'):
        dest=ROOT/f'pipeline-preview-{theme}.svg'; dest.write_text(svg('data-pipeline',theme,True))
        subprocess.run(['rsvg-convert','-o',str(ROOT/f'pipeline-preview-{theme}.png'),str(dest)],check=True)
    with zipfile.ZipFile(ROOT/'data-processing-assets.zip','w',zipfile.ZIP_DEFLATED) as z:
        # Package only our explicit deliverables, never extracted copies of a previous ZIP.
        files=[ROOT/'README.md',ROOT/'manifest.json',ROOT/'scripts/generate.py',ROOT/'scripts/verify.swift',ROOT/'scripts/struct-capital-symbol.svg']
        files+=list((ROOT/'svg').glob('*.svg'))+list((ROOT/'png').glob('*/*.png'))+list(ROOT.glob('*preview*.svg'))+list(ROOT.glob('*preview*.png'))
        mirror=ROOT/'data-processing-assets'
        for file in sorted(files):
            if file.exists():
                z.write(file,Path('data-processing-assets')/file.relative_to(ROOT))
                if mirror.is_dir():
                    destination=mirror/file.relative_to(ROOT)
                    destination.parent.mkdir(parents=True,exist_ok=True)
                    shutil.copy2(file,destination)
    print(f'Generated {len(manifest)} SVGs, {len(manifest)*3} transparent PNGs, previews, manifest, and ZIP.')

if __name__=='__main__': main()
