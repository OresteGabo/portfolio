#!/usr/bin/env python3
"""Generate app branding from the approved concept-10 SVG geometry. No service or storage identifiers change."""
from pathlib import Path
import json,re,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
MASTER=ROOT/'branding/inzira/refined-10'
DATA=json.loads((MASTER/'geometry.json').read_text())
PATHS=list(DATA['paths'].values())
SHARED=ROOT/'app/shared/src/commonMain'
BRAND=ROOT/'branding/inzira'
NOTE='<!-- Generated colors: scripts/sync-design-colors.py; edit design/StructColors.kt. -->\n'

def save(relative,text):
 p=ROOT/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
def vector(color,viewport=512,scale=512/440,tx=0,ty=(512-400*512/440)/2,dp=512):
 return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{dp}dp" android:height="{dp}dp" android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n<group android:translateX="{tx:.8f}" android:translateY="{ty:.8f}" android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}">\n'+''.join(f'<path android:fillColor="{color}" android:pathData="{d}"/>\n' for d in PATHS)+'</group>\n</vector>\n'
def svg(body,size=512,fill='#6D5E0F'):
 return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}" fill="{fill}"><title>Inzira</title>{body}</svg>'
def mark(size=512):
 scale=size/440;ty=(size-400*scale)/2
 return f'<g transform="translate(0 {ty}) scale({scale})">'+''.join(f'<path d="{d}"/>' for d in PATHS)+'</g>'
def render(source,dest,size):
 target=ROOT/dest;target.parent.mkdir(parents=True,exist_ok=True)
 subprocess.run(['rsvg-convert','-w',str(size),'-h',str(size),str(ROOT/source),'-o',str(target)],check=True)
# Keep historical resource names so screens and stored references remain compatible.
for theme,color in [('light','#6D5E0F'),('dark','#F2E6A7')]:
 save(f'app/shared/src/commonMain/composeResources/drawable/struct_mark_{theme}.xml', NOTE+vector(color))
 body=f'<rect width="1024" height="1024" fill="{"#FAF8F2" if theme=="light" else "#11150F"}"/>'
 body+='<g transform="translate(150 182) scale(1.6454545455)">'+''.join(f'<path d="{d}"/>' for d in PATHS)+'</g>'
 save(f'branding/inzira/inzira-icon-{theme}.svg',svg(body,1024,color))
 render(f'branding/inzira/inzira-icon-{theme}.svg',f'branding/inzira/inzira-icon-{theme}-1024.png',1024)
 render(f'branding/inzira/inzira-icon-{theme}.svg',f'app/iosApp/ShareImport/Assets.xcassets/ImportBrand.imageset/brand-{theme}.png',192)
# Outlined wordmark resources use the approved vector file without a font dependency.
for theme,color in [('light','#6D5E0F'),('dark','#F2E6A7')]:
 word=ET.parse(MASTER/f'inzira-wordmark-{theme}.svg').getroot()
 node=word.find('{http://www.w3.org/2000/svg}path')
 tx,ty,sx,sy=map(float,re.findall(r'[-+]?(?:\d*\.)?\d+',node.attrib['transform']))
 w,h=map(float,word.attrib['viewBox'].split()[2:])
 xml=f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{w}" android:viewportHeight="{h}"><group android:translateX="{tx}" android:translateY="{ty}" android:scaleX="{sx}" android:scaleY="{sy}"><path android:fillColor="{color}" android:pathData="{node.attrib["d"]}"/></group></vector>\n'
 save(f'app/shared/src/commonMain/composeResources/drawable/inzira_wordmark_{theme}.xml',NOTE+xml)
# Adaptive and status icons are monochrome vector resources, not raster logo substitutions.
for file,color,viewport,scale,tx,ty,dp in [
 ('drawable/ic_launcher_foreground.xml','@color/struct_launcher_foreground',108,72/440,18,(108-400*72/440)/2,108),
 ('drawable/ic_launcher_monochrome.xml','@color/struct_launcher_foreground',108,72/440,18,(108-400*72/440)/2,108),
 ('drawable/ic_widget_brand.xml','@color/struct_fixed_white',512,512/440,0,(512-400*512/440)/2,20),
 ('drawable/ic_price_notification.xml','@color/struct_fixed_white',512,512/440,0,(512-400*512/440)/2,24)]:
 save('app/androidApp/src/main/res/'+file,vector(color,viewport,scale,tx,ty,dp))
save('branding/inzira/inzira-widget-symbol.svg',svg(mark(),512,'#F2E6A7'))
render('branding/inzira/inzira-widget-symbol.svg','app/iosApp/NextWidget/Assets.xcassets/WidgetLogo.imageset/WidgetLogo.png',192)
save('app/iosApp/ShareImport/ImportActionIcon.svg',svg(mark(),512,'#000000'))
render('app/iosApp/ShareImport/ImportActionIcon.svg','app/iosApp/ShareImport/ImportActionIcon@2x.png',120)
render('app/iosApp/ShareImport/ImportActionIcon.svg','app/iosApp/ShareImport/ImportActionIcon@3x.png',180)
# Flatten the legacy illustration coordinate system into reusable Inzira paths.
def flatten(d):
 tokens=re.findall(r'[MLCQHVZ]|[-+]?(?:\d*\.)?\d+',d);i=0;x=y=0;start=(0,0);out=[];pdf=[];cmd=None
 while i<len(tokens):
  if tokens[i].isalpha():cmd=tokens[i];i+=1
  if cmd=='Z':out.append('Z');pdf.append('h');x,y=start;cmd=None;continue
  n={'M':2,'L':2,'C':6,'Q':4,'H':1,'V':1}[cmd]
  v=list(map(float,tokens[i:i+n]));i+=n
  if cmd=='H':v=[v[0],y];kind='L'
  elif cmd=='V':v=[x,v[0]];kind='L'
  else:kind=cmd
  if kind=='Q':
   qx,qy,ex,ey=v
   controls=[x+2*(qx-x)/3,y+2*(qy-y)/3,ex+2*(qx-ex)/3,ey+2*(qy-ey)/3,ex,ey]
   pdf.append(' '.join(f'{z:.6f}' for z in controls)+' c')
  else:pdf.append(' '.join(f'{z:.6f}' for z in v)+' '+{'M':'m','L':'l','C':'c'}[kind])
  world=[z*(512/440)+(248 if j%2==0 else 183+(512-400*512/440)/2) for j,z in enumerate(v)]
  out.append(kind+' '.join(f'{z:.6f}' for z in world))
  x,y=v[-2:]
  if cmd=='M':start=(x,y);cmd='L'
 return ' '.join(out),' '.join(pdf)
flat=[flatten(d) for d in PATHS]
legacy='<g transform="translate(-248 -183)">'+''.join(f'<path d="{d}"/>' for d,_ in flat)+'</g>'
save('branding/inzira/inzira-artwork-symbol.svg',svg(legacy))
kt='package dev.orestegabo.portfolio.investments\n\n// Generated by scripts/generate-inzira-brand.py from branding/inzira/refined-10/geometry.json.\ninternal object InziraBrandGeometry {\n'
for (name,d),(_,pdf) in zip(DATA['paths'].items(),flat):
 key=name.upper().replace('-','_')
 kt+=f'    const val {key} = "{d}"\n    const val PDF_{key} = "{pdf}"\n'
word=ET.parse(MASTER/'inzira-wordmark-light.svg').getroot()
word_w,word_h=map(float,word.attrib['viewBox'].split()[2:])
kt+=f'    const val WORDMARK_ASPECT_RATIO = {word_w/word_h:.8f}f\n'
kt+='}\n'
save('app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InziraBrandGeometry.generated.kt',kt)
print('Generated Inzira native marks, launcher masters, widget and import artwork.')
