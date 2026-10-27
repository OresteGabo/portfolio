#!/usr/bin/env python3
"""Rebuild the hand-drawn SVG asset family and 1x/2x/3x PNG exports.
Requires Python 3 and librsvg's rsvg-convert. No image-generation API required.
"""
from pathlib import Path
import html
import json
import subprocess
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOGO = ET.parse(ROOT / 'scripts/struct-capital-symbol.svg').getroot()
TEXT = json.loads((ROOT / 'scripts/text-outlines.json').read_text())
PALETTES = {
    'dark': dict(line='#E3D19A', muted='#B6A66F', accent='#B89A28', band='#6D5E0F', surface='#272B1B', card='#FAF8ED', ink='#272B1B', badge='#6D5E0F'),
    'light': dict(line='#6D5E0F', muted='#9A8B52', accent='#B79B31', band='#EEE5BE', surface='#FAF8F2', card='#FFFFFF', ink='#413A20', badge='#6D5E0F'),
    'mono-olive': dict(line='#6D5E0F', muted='#6D5E0F', accent='#6D5E0F', band='none', surface='#FAF8F2', card='#FAF8F2', ink='#6D5E0F', badge='#6D5E0F'),
    'mono-cream': dict(line='#F2E6A7', muted='#F2E6A7', accent='#F2E6A7', band='none', surface='#272B1B', card='#272B1B', ink='#F2E6A7', badge='#F2E6A7'),
}

def path(d, color, width=2.4, fill='none', extra=''):
    return f'<path d="{d}" fill="{fill}" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round" {extra}/>'

def rect(x, y, w, h, radius, fill, stroke='none', sw=2):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'

def circle(x,y,r,fill,stroke='none',sw=2):
    return f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'

def label(s,x,y,size,color,center=False):
    t=TEXT[s]; scale=size/1000
    if center: x-=t['width']*scale/2
    return f'<path fill="{color}" transform="translate({x:.3f} {y}) scale({scale})" d="{t["path"]}"/>'

def bell(x,y,size,color,fill='none'):
    return f'<g transform="translate({x} {y}) scale({size/24})">'+path('M4 18h16l-2.5-3V9a5.5 5.5 0 0 0-11 0v6L4 18Z M10 21h4 M12 1v2',color,1.8,fill)+'</g>'

def badge(x,y,n,p):
    # Cream monos use an inverted numeral so the counter remains legible.
    fg = p['surface'] if p['badge']=='#F2E6A7' else '#FAF8F2'
    return circle(x,y,11,p['badge'])+label(str(n),x,y+4,12,fg,True)

def bars(x,y,lengths,color,spacing=9,sw=2.5):
    return ''.join(path(f'M{x} {y+i*spacing}h{length}',color,sw) for i,length in enumerate(lengths))

FRENCH = {
    'Bond Subscription': 'Souscription obligataire', 'Opens': 'Ouverture',
    'Subscription': 'Souscription', 'Window Closes': 'Clôture',
    'Dividend': 'Dividende', 'Payment': 'versé', 'Notification': 'Notification',
    'Dividend Payment': 'Dividende versé', 'Coupon Paid': 'Coupon versé',
    'Mar 27,': '27 mars', 'Mar 19,': '19 mars',
}


def calendar(p, detail=False, locale='en'):
    def localized(s, *args):
        return label(FRENCH.get(s, s) if locale == 'fr' else s, *args)
    c=p['line']; m=p['muted']; parts=['<g id="desk-calendar" transform="translate(89 66) matrix(1 -.025 .105 1 0 0)">']
    # Tent stand, angled spine, base lip and inner page.
    parts += [path('M0 0 -75 397Q-77 404-69 405L3 405 M-68 401 -23 373H0 M-23 373 0 405 M0 0 -66 397',c,2.8),
        path('M0 0H430Q437 0 437 8V413Q437 420 430 420H5Q0 420 0 413Z',c,3.1),
        path('M10 5V401Q10 408 17 408H428',m,1.5),
        rect(22,66,393,65,2,p['band'],c,2.4),
        path('M242 66V131',c,2),
        path('M22 131V389H415V131',c,2.3)]
    # A ring is a continuous loop passing through a punched hole.
    for i in range(12):
        x=34+i*32.4
        parts += [circle(x+7,15,6,p['surface'],c,2),path(f'M{x+7} 15C{x+19} 17 {x+19} -20 {x+8} -20C{x-1} -20 {x-1} -8 {x+1} -4 M{x+8} -17C{x+14} -13 {x+15} 8 {x+7} 12',c,2.7)]
    for i in range(7):
        x=41+i*54
        parts += [path(f'M{x} 51h19',c,2.5)]
    # Seven columns, four weeks. The highlighted subscription window spans two days.
    cell=393/7
    if not p['band']=='none':
        parts += [rect(22+cell,132,cell*2,64,0,p['band'])]
    for i in range(1,7):
        x=22+i*cell; parts += [path(f'M{x:.3f} 131V389',m,1.65)]
    for y in [195.5,260,324.5]: parts += [path(f'M22 {y}H415',m,1.65)]
    if detail:
        parts += [localized('Bond Subscription',131,92,15,c,True),localized('Opens',131,112,15,c,True),localized('Subscription',328,92,15,c,True),localized('Window Closes',328,112,15,c,True)]
        for row in range(4):
            for col in range(7):
                day=7+row*7+col
                if day<=31: parts += [label(str(day),30+col*cell,151+row*64.5,16,c)]
        parts += [localized('Mar 27,',86,168,10,c),localized('2023',86,181,10,c),localized('Mar 19,',142,168,10,c),localized('2023',142,181,10,c)]
    else:
        parts += [bars(91,91,[111,74],c,16,3),bars(275,91,[108,84],c,16,3)]
        for row in range(4):
            for col in range(7):
                if 7+row*7+col<=31:
                    parts += [path(f'M{34+col*cell:.3f} {149+row*64.5}h10',c,2)]
        parts += [path('M91 171h25 M147 171h25',c,2)]
    # Payment cards sit over the grid, with restrained, flat vector layering.
    def card(x,y,w,h,number,kind):
        out=rect(x,y+3,w,h,6,p['surface'])+rect(x,y,w,h,6,p['card'],m,1.4)
        if kind=='dividend':
            out+=bell(x+8,y+11,20,p['ink'])
            if detail:
                out+=localized('Dividend',x+35,y+15,12,p['ink'])+localized('Payment',x+35,y+28,12,p['ink'])+localized('Notification',x+35,y+41,12,p['ink'])
            else: out+=bars(x+36,y+15,[w-48,w-55,w-68],p['ink'],10,2)
        elif detail:
            if kind=='payment': out+=localized('Dividend Payment',x+w/2,y+17,12,p['ink'],True)+localized('Notification',x+w/2,y+32,12,p['ink'],True)
            else: out+=localized('Coupon Paid',x+w/2,y+22,12,p['ink'],True)
        else:
            # Coin / payment mark gives each text-free card a meaningful visual cue.
            out+=circle(x+18,y+h/2,7,'none',p['ink'],1.5)+path(f'M{x+16} {y+h/2}h4 M{x+18} {y+h/2-3}v6',p['ink'],1.4)
            out+=bars(x+34,y+h/2-4,[w-48,w-63],p['ink'],9,2)
        return out+badge(x+w-1,y+1,number,p)
    parts += [card(205,137,110,51,1,'dividend'),card(29,271,143,42,2,'payment'),card(225,285,116,33,3,'coupon')]
    parts += ['</g>']
    return ''.join(parts)

def phone_logo(p):
    shapes=[]
    for element in LOGO.iter('{http://www.w3.org/2000/svg}path'):
        d=element.attrib['d']
        shapes.append(path(d,p['line'],8) if element.attrib.get('fill')=='none' else f'<path d="{d}" fill="{p["line"]}"/>')
    return '<g id="phone-struct-capital-symbol" transform="translate(553 250) scale(.05)"><g transform="translate(-248 -183)">'+''.join(shapes)+'</g></g>'


def phone(p):
    c=p['line']; parts=['<g id="phone-notifications">']
    # Opaque phone fill makes overlapping outlines read correctly on any host surface.
    parts += [rect(497,231,137,265,22,p['surface'],c,3),rect(504,238,123,251,16,p['surface'],c,1.6),
        path('M522 238h8q2 0 2 4t6 5h54q6-1 6-5t3-4h8',c,2),path('M558 240h16',c,2),circle(582,240,1.8,c),path('M546 480h38',c,3)]
    parts += [path('M526 279H710Q718 279 718 287V318Q718 326 710 326H602L589 337V326H526Q518 326 518 318V287Q518 279 526 279Z',c,2.7,p['surface']),
        bell(529,288,24,c),badge(551,289,3,p),bars(568,291,[135,125,46],c,10,2.4),
        path('M569 367H710Q718 367 718 375V405Q718 413 710 413H598L585 424V413H569Q561 413 561 405V375Q561 367 569 367Z',c,2.7,p['surface']),
        bell(572,377,24,c),bars(606,379,[96,87,40],c,10,2.4)]
    parts+=[phone_logo(p),'</g>']; return ''.join(parts)

VIEWS={'calendar-notifications':('0 0 760 560',760,560), 'calendar-detail':('0 0 760 560',760,560), 'calendar-detail-fr':('0 0 760 560',760,560), 'calendar':('0 0 590 530',590,530), 'notifications':('469 204 278 318',278,318)}

def svg(kind,theme,background=False):
    p=PALETTES[theme]; view,w,h=VIEWS[kind]
    locale = 'fr' if kind == 'calendar-detail-fr' else 'en'
    detail = kind in ('calendar-detail', 'calendar-detail-fr')
    title = ('Calendrier des placements et notifications' if locale == 'fr' else kind.replace('-',' ').title())+' — '+theme
    out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{view}" fill="none" role="img" xml:lang="{locale}" aria-labelledby="title desc">',f'<title id="title">{html.escape(title)}</title>', '<desc id="desc">Vector illustration of an investment desk calendar with subscription windows and dividend and coupon reminders.'+(' A phone displays payment notifications.' if kind!='calendar' else '')+'</desc>']
    if locale == 'fr':
        out[-1] = '<desc id="desc">Illustration vectorielle d’un calendrier de souscription obligataire, avec notifications de versement de dividendes et de coupons sur un téléphone.</desc>'
    if background: out += [rect(0,0,w,h,0,p['surface'])]
    if kind!='notifications': out += [calendar(p,detail=detail,locale=locale)]
    if kind in ('calendar-notifications','calendar-detail','calendar-detail-fr','notifications'): out += [phone(p)]
    return ''.join(out)+'</svg>\n'


def main():
    for d in ('svg','png/1x','png/2x','png/3x'): (ROOT/d).mkdir(parents=True,exist_ok=True)
    assets=[]
    for kind in VIEWS:
        for theme in PALETTES:
            assets.append((kind,theme,False))
    assets += [('calendar-notifications',t,True) for t in ('dark','light')]
    manifest=[]
    for kind,theme,bg in assets:
        name=f'{kind}-{theme}'+('-background' if bg else '')
        source=ROOT/'svg'/f'{name}.svg'; source.write_text(svg(kind,theme,bg))
        _,w,h=VIEWS[kind]
        exports=[]
        for scale in (1,2,3):
            dest=ROOT/'png'/f'{scale}x'/f'{name}.png'
            subprocess.run(['rsvg-convert','-w',str(w*scale),'-h',str(h*scale),'-o',str(dest),str(source)],check=True)
            exports.append(dict(path=str(dest.relative_to(ROOT)),width=w*scale,height=h*scale))
        manifest.append(dict(name=name,svg=str(source.relative_to(ROOT)),transparent=not bg,width=w,height=h,locale='fr' if kind=='calendar-detail-fr' else ('en' if kind=='calendar-detail' else None),text='outlined French labels' if kind=='calendar-detail-fr' else ('outlined English labels' if kind=='calendar-detail' else 'no words; counter digits outlined'),png=exports))
    (ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    # Self-contained contact sheet: dark themes on dark, light themes on off-white.
    height = len(VIEWS)*358
    sheet=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1520" height="{height}" viewBox="0 0 1520 {height}">',rect(0,0,1520,height,0,'#E9E7DD')]
    for row,kind in enumerate(VIEWS):
        for col,theme in enumerate(PALETTES):
            x=col*380; y=row*358; p=PALETTES[theme]
            sheet += [rect(x+8,y+8,364,342,14,p['surface'])]
            source=svg(kind,theme)
            view,w,h=VIEWS[kind]
            content=source[source.index('</desc>')+7:source.rindex('</svg>')]
            sheet += [f'<svg x="{x+18}" y="{y+24}" width="344" height="274" viewBox="{view}">{content}</svg>', f'<text x="{x+24}" y="{y+326}" font-family="sans-serif" font-size="13" fill="{p["line"]}">{kind} / {theme}</text>']
    sheet+=['</svg>']; (ROOT/'preview.svg').write_text(''.join(sheet))
    subprocess.run(['rsvg-convert','-o',str(ROOT/'preview.png'),str(ROOT/'preview.svg')],check=True)
    french=['<svg xmlns="http://www.w3.org/2000/svg" width="1520" height="1120" viewBox="0 0 1520 1120">']
    for i, theme in enumerate(PALETTES):
        x=(i%2)*760; y=(i//2)*560
        source=svg('calendar-detail-fr',theme,True)
        content=source[source.index('</desc>')+7:source.rindex('</svg>')]
        french += [f'<g transform="translate({x} {y})">{content}</g>']
    french+=['</svg>']; (ROOT/'preview-fr.svg').write_text(''.join(french))
    subprocess.run(['rsvg-convert','-o',str(ROOT/'preview-fr.png'),str(ROOT/'preview-fr.svg')],check=True)
    with zipfile.ZipFile(ROOT/'calendar-assets.zip','w',zipfile.ZIP_DEFLATED) as z:
        files=[ROOT/'README.md',ROOT/'manifest.json']
        files+=list((ROOT/'scripts').glob('*.py'))+list((ROOT/'scripts').glob('*.swift'))+list((ROOT/'scripts').glob('*.json'))+list((ROOT/'scripts').glob('*.svg'))
        files+=list((ROOT/'svg').glob('*.svg'))+list((ROOT/'png').glob('*/*.png'))+list(ROOT.glob('preview*.svg'))+list(ROOT.glob('preview*.png'))
        mirror=ROOT/'calendar-assets'
        for file in sorted(files):
            if file.exists():
                z.write(file,Path('calendar-assets')/file.relative_to(ROOT))
                if mirror.is_dir():
                    destination=mirror/file.relative_to(ROOT)
                    destination.parent.mkdir(parents=True,exist_ok=True)
                    shutil.copy2(file,destination)
    print(f'Generated {len(assets)} SVGs, {len(assets)*3} PNGs, preview, manifest and ZIP.')

if __name__=='__main__': main()
