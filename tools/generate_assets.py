#!/usr/bin/env python3
"""Original texel-aligned artwork and cuboid models. Run with Python + Pillow.
No console logos, photographs, or borrowed Minecraft textures are used.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json, random

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/retrocraft'
FAMILIES = {
    'nes': ('#c9c5b8', '#555861', '#b44848', '8 BIT'),
    'snes': ('#c7c4ce', '#595569', '#8272af', '16 BIT'),
    'gb': ('#d0cbb5', '#777b6d', '#9c4d68', 'POCKET'),
    'gbc': ('#438d91', '#31535e', '#cf8054', 'COLOR'),
    'gba': ('#7973b5', '#49466f', '#cbb878', 'ADVANCE'),
    'megadrive': ('#414650', '#262d35', '#6098a4', '16 BIT'),
    'genesis': ('#414650', '#262d35', '#b16058', '16 BIT'),
    'n64': ('#4f5c53', '#303b35', '#b66a52', '64 BIT'),
    'mame': ('#4c4852', '#2a3237', '#bd985d', 'ARCADE'),
}
GLYPHS = {
'R':['110','101','110','101','101'], 'E':['111','100','110','100','111'],
'T':['111','010','010','010','010'], 'O':['111','101','101','101','111'],
'N':['101','111','111','111','101'], 'S':['111','100','111','001','111'],
'G':['111','100','101','101','111'], 'B':['110','101','110','101','110'],
'C':['111','100','100','100','111'], 'A':['010','101','111','101','101'],
'6':['011','100','111','101','111'], '4':['101','101','111','001','001'],
'M':['101','111','111','101','101'], 'D':['110','101','101','101','110'],
'F':['111','100','110','100','100'], '8':['111','101','111','101','111'],
'1':['010','110','010','010','111'], '2':['110','001','010','100','111'],
'P':['110','101','110','100','100'], 'X':['101','101','010','101','101'],
'Y':['101','101','010','010','010'], '0':['111','101','101','101','111'],
' ':['000']*5,
}
def rgb(c): return tuple(int(c[i:i+2],16) for i in (1,3,5))
def tint(c,n): return tuple(max(0,min(255,v+n)) for v in (rgb(c) if isinstance(c,str) else c))
def word(draw,text,x,y,color):
    for letter in text:
        for dy,row in enumerate(GLYPHS.get(letter,GLYPHS[' '])):
            for dx,bit in enumerate(row):
                if bit=='1': draw.point((x+dx,y+dy),fill=color)
        x+=4

def atlas(family):
    body,edge,accent,caption = FAMILIES[family]
    im=Image.new('RGB',(64,64)); d=ImageDraw.Draw(im)
    colors=[body,edge,'#252d34','#e6dcbf','#243a38','#343a3d','#343a3d',edge,
            '#b79450',accent,'#242a30',edge,'#31564b','#4c815c','#513e3e','#315746']
    rand=random.Random(family)
    for index,color in enumerate(colors):
        x=index%4*16; y=index//4*16
        for yy in range(16):
            for xx in range(16):
                delta=rand.choice([-3,-1,0,0,0,1,2])
                if yy==0 or xx==0: delta+=7
                if yy==15 or xx==15: delta-=7
                d.point((x+xx,y+yy),fill=tint(color,delta))
    # Original printed cartridge label: colored banner, small mountain scene, gold registration bar.
    d.rectangle((49,1,62,3),fill=accent);d.line((49,4,62,4),fill='#fff0ce')
    code={'nes':'8','snes':'16','gb':'GB','gbc':'GC','gba':'GA','megadrive':'MD','genesis':'MD','n64':'64','mame':'RX'}[family]
    word(d,code,51 if len(code)==2 else 55,6,'#3b4650')
    d.rectangle((49,12,62,13),fill=accent);d.point((59,5),fill='#bfaa6b')
    # LCD glass with restrained scanlines and reflected corners.
    for yy in range(18,30,2):d.line((2,yy,13,yy),fill='#2b4540')
    d.line((2,19,2,27),fill='#587369');d.line((3,18,9,18),fill='#587369');d.line((11,29,13,29),fill='#1c2c2c')
    # Vents, parallel louvers cut into dark plastic.
    for xx in range(18,30,3):
        d.rectangle((xx,18,xx+1,29),fill='#20282d');d.line((xx+2,18,xx+2,29),fill=tint(edge,8))
    # Cartridge port with recessed contact pins.
    d.rectangle((33,21,46,26),fill='#151e24');d.line((34,20,45,20),fill='#78827c')
    for xx in range(35,46,2):d.point((xx,25),fill='#af995d')
    # Controller ports on front fascia.
    for xx in (49,57):
        d.rectangle((xx,21,xx+5,26),fill='#1c242a');d.line((xx+1,21,xx+4,21),fill='#92948e')
        for xp in (xx+1,xx+3):d.point((xp,24),fill='#a99f7b')
    # Gold contacts and gentle bevels on button caps.
    for xx in range(1,16,2):
        d.rectangle((xx,33,xx+1,46),fill='#ddbd70');d.line((xx,34,xx,45),fill='#f1d38a')
    d.rectangle((19,35,28,44),fill=tint(accent,7));d.line((20,34,27,34),fill=tint(accent,30))
    d.line((20,45,28,45),fill=tint(accent,-30));d.line((29,36,29,44),fill=tint(accent,-23))
    # Rubber D-pad's inset center and directional notches.
    d.rectangle((38,34,41,45),fill='#383e42');d.rectangle((34,38,45,41),fill='#383e42')
    d.rectangle((38,38,41,41),fill='#1a232a');d.line((35,38,37,38),fill='#52595a')
    # Bezel edge and screw holes.
    d.rectangle((49,33,62,46),outline=tint(edge,12));d.rectangle((50,34,61,45),outline=tint(edge,-18))
    for xx,yy in [(50,34),(61,34),(50,45),(61,45)]:d.point((xx,yy),fill='#2f353b')
    # Arcade PCB traces.
    for xx in range(2,16,4):
        d.line((xx,49,xx,61),fill='#6b976d');d.line((xx,61,xx+2,61),fill='#6b976d');d.point((xx+2,61),fill='#c9ac63')
    # Indicator tile: faces map to its central few texels.
    d.rectangle((20,52,27,59),fill='#b1c78e');d.rectangle((21,53,26,58),fill='#d3e4ad')
    d.rectangle((36,52,43,59),fill='#7c4b49');d.rectangle((37,53,42,58),fill='#965d57')
    # Powered handheld's boot glass, rendered on the item only.
    for yy in range(50,62,2):d.line((50,yy,61,yy),fill='#3e6852')
    word(d,'R',55,53,'#c0d39a');d.point((51,51),fill='#7fab78');d.line((57,60,61,60),fill='#719c73')
    out=ASSETS/f'textures/block/console/{family}.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
    return im

def uv(tile):
    x=tile%4*4;y=tile//4*4
    return [x,y,x+4,y+4]
FACES=['north','south','east','west','up','down']
def box(a,b,tile=0,override=None,shade=True):
    faces={f:{'texture':'#atlas','uv':uv(tile)} for f in FACES}
    for face,value in (override or {}).items():
        faces[face]={'texture':value if isinstance(value,str) else '#atlas','uv':[0,0,16,16] if isinstance(value,str) else uv(value)}
    return {'from':list(a),'to':list(b),'faces':faces,'shade':shade}
def lamp(a,b): return box(a,b,13,{f:'#indicator' for f in FACES},False)
def model(family,elements,kind='item'):
    result={'credit':'Original Retrocraft pixel artwork and models','textures':{'atlas':f'retrocraft:block/console/{family}','particle':f'retrocraft:block/console/{family}','indicator':'retrocraft:block/console/power_off'},'elements':elements}
    if kind=='item':
        result['gui_light']='front'
        result['display']={
            'gui':{'rotation':[10,-25,0],'translation':[0,0,0],'scale':[.95,.95,.95]},
            'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]},
            'fixed':{'rotation':[0,180,0],'scale':[.8,.8,.8]},
            'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,2,1],'scale':[.6,.6,.6]},
            'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,2,1],'scale':[.6,.6,.6]},
            'firstperson_righthand':{'rotation':[0,-20,0],'translation':[1,2,0],'scale':[.8,.8,.8]},
            'firstperson_lefthand':{'rotation':[0,20,0],'translation':[1,2,0],'scale':[.8,.8,.8]},
        }
    else:
        result['parent']='minecraft:block/block'
        result['display']={'gui':{'rotation':[25,225,0],'translation':[0,1,0],'scale':[.95,.95,.95]}}
    return result

def cartridge(f):
    if f=='mame':
        e=[box((2,3,7.5),(14,13,8.5),12),box((3,2,7.7),(13,3,8.3),8)]
        for x,y,w,h in [(3,5,3,5),(7,6,3,3),(11,5,2,6)]: e.append(box((x,y,8.5),(x+w,y+h,9),2))
        e += [box((3,11,8.51),(10,12.6,8.6),3)]
        return model(f,e)
    if f=='nes': bounds=(3,1,13,15)
    elif f in ('snes','megadrive','genesis','n64'): bounds=(1,3,15,12)
    elif f=='gba': bounds=(1,4,15,11)
    else: bounds=(3,2,13,14)
    x,y,X,Y=bounds
    e=[box((x,y+1,7),(X,Y-1,9),0),box((x+1,Y-1,7),(X-1,Y,9),1),box((x+1,y,7),(X-1,y+1,9),1)]
    e += [box((x+1.5,y+.4,9.02),(X-1.5,y+1.5,9.1),8)]
    e += [box((x+1.5,y+2,9.02),(X-1.5,Y-2,9.13),3,{'south':3,'north':1,'up':1,'down':1,'east':1,'west':1})]
    for side in (x+.1,X-.6):
        for yy in range(int(y+2),int(Y-1),2):e.append(box((side,yy,9),(side+.5,yy+.4,9.22),1))
    if f=='n64':e.append(box((x+3,Y,7.1),(X-3,Y+.7,8.9),0))
    # Back screw heads; stamped cartridge notch on the top edge.
    for side in (x+1,X-1.5):e.append(box((side,y+2,6.97),(side+.4,y+2.4,7),2))
    e += [box((x+2,Y-.7,7.7),(X-2,Y-.3,8.3),2)]
    return model(f,e)

def handheld(f):
    wide=f in ('gba','nes')
    if wide:
        e=[box((1,3,7),(15,12,9),0),box((0,4,7.2),(1,11,8.8),1),box((15,4,7.2),(16,11,8.8),1)]
        e += [box((4,4.5,9),(12,11,9.4),11),box((4.7,5.2,9.41),(11.3,10.3,9.45),4,{'south':'#screen'})]
        dx,dy,bx,by=2.4,7,13,6.4
        e += [box((1.5,11.8,7),(4,12.4,8.8),1),box((12,11.8,7),(14.5,12.4,8.8),1)]
        e += [box((5.2,3.4,9),(6.6,3.8,9.2),10),box((8.6,3.4,9),(10,3.8,9.2),10)]
        led=(3.8,10.5,9.1)
    else:
        e=[box((3,1,7),(13,15,9.2),0),box((3.5,0,7.2),(12,1,9),1),box((3.5,15,7.2),(12.5,16,9),0)]
        e += [box((3.8,8,9.2),(12.2,14.7,9.5),11),box((4.8,9,9.51),(11.4,13.8,9.56),4,{'south':'#screen'})]
        dx,dy,bx,by=5,5,9.4,4.6
        e += [box((6,2.5,9.2),(7.5,2.9,9.4),10),box((8,2.2,9.2),(9.5,2.6,9.4),10)]
        for yy in [1.1,1.7,2.3,2.9]:e.append(box((10,yy,9.22),(11.9,yy+.2,9.32),2))
        led=(4.05,11.6,9.5)
    e += [box((dx-.5,dy-1.6,9.4),(dx+.5,dy+1.6,9.8),10),box((dx-1.6,dy-.5,9.4),(dx+1.6,dy+.5,9.8),10)]
    for ox,oy in [(0,0),(1.7,1)]: e.append(box((bx+ox-.55,by+oy-.55,9.3),(bx+ox+.55,by+oy+.55,9.8),9))
    e += [lamp(led,(led[0]+.3,led[1]+.3,led[2]+.05))]
    # Back battery cover and cartridge socket.
    e += [box((5,2,6.92),(11,6,7),1),box((5.5,10,6.9),(10.5,14,7),6)]
    m=model(f,e);m['textures']['screen']='retrocraft:block/console/lcd_off'
    return m

def console(f):
    e=[]
    if f=='mame':
        e=[box((2,0,3),(14,6,14),1),box((2,6,8),(14,15,14),0),box((2,14,3),(14,16,14),1)]
        e += [box((1.5,0,3),(2.5,16,14),9),box((13.5,0,3),(14.5,16,14),9)]
        e += [box((3,7,7.7),(13,13.7,8),11),box((3.8,7.8,7.6),(12.2,13,7.7),15)]
        e += [box((2,5.8,2),(14,7,9),0,{'up':7}),box((4,7,4),(4.8,8.5,4.8),10),box((4.2,8.4,4.2),(4.6,9,4.6),9)]
        for x,z in [(9,4),(11,5),(9,6)]:e.append(box((x,7,z),(x+.8,7.3,z+.8),9))
        e += [box((4,1,2.9),(12,4.5,3),2,{'north':7}),box((3,14.5,2.8),(13,15.7,3),3)]
        e += [lamp((10.7,3.1,2.8),(11.2,3.6,2.9))]
        return model(f,e,'block')
    for x,z in [(2,3),(12,3),(2,11),(12,11)]:e.append(box((x,0,z),(x+2,.8,z+2),10))
    h=6 if f=='nes' else 4.5
    e += [box((1,.8,2),(15,1.6,14),1),box((1,1.6,2),(15,h,14),0)]
    # Recessed front controller fascia, separate power and reset keys.
    e += [box((7,1.8,1.85),(14,3.5,2),7),box((2,2,1.6),(3.3,2.7,1.9),9),box((4,2,1.6),(5.3,2.7,1.9),1)]
    e += [lamp((2,3.1,1.7),(2.5,3.6,1.9))]
    if f=='nes':
        e += [box((2,6,3),(11,6.45,12),1),box((3,4.5,1.8),(10,5.8,2.02),0)]
        e += [box((11.5,6.01,3),(14,6.08,12),5),box((3,6.46,7),(9,6.49,11),3)]
    elif f=='snes':
        e += [box((2,4.5,3),(14,5,13),1),box((4,5,5),(12,5.15,8),6)]
        for x in (3,11): e.append(box((x,5,10),(x+2,5.4,12.5),9))
        e += [box((5,5,9),(10,5.5,11),0),box((3,4.51,3.4),(13,4.55,4.5),5)]
    elif f in ('megadrive','genesis'):
        e += [box((2,4.5,3),(14,4.8,13),1),box((4,4.8,4),(12,5,10),2),box((4.5,5.01,5),(11.5,5.15,7.5),6)]
        e += [box((3,4.81,10),(13,4.86,12.5),5),box((2.5,4.85,7.5),(4,5.2,9),9),box((12.3,4.85,7.5),(13.5,5.2,9),1)]
        e += [box((5,5.02,8.3),(11,5.07,9.7),3)]
    else: # N64: sculpted shoulders and a central top-loading slot.
        e += [box((2,4.5,3),(14,5,12),1),box((4,5,4),(12,5.7,9),0),box((4.8,5.71,5),(11.2,5.8,7.8),6)]
        for x in (2,12):e.append(box((x,4.5,9),(x+2,5.2,12),9))
        e += [box((4,5.02,10),(12,5.07,12),5)]
        for x in (3,6,9,12):e.append(box((x,1.7,1.72),(x+1.4,2.8,1.86),2))
    return model(f,e,'block')

def write(kind,name,data):
    path=ASSETS/kind/f'{name}.json';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n')

def generate():
    for color,name in [('#b8d491','power_on'),('#754947','power_off'),('#243b36','lcd_off'),('#7e9b69','lcd_on')]:
        im=Image.new('RGB',(16,16),color);d=ImageDraw.Draw(im)
        if name.startswith('lcd'):
            for y in range(2,15,2):d.line((1,y,14,y),fill=tint(color,-5))
            d.line((1,1,8,1),fill=tint(color,25));d.line((1,2,1,10),fill=tint(color,15))
            if name=='lcd_on':word(d,'R',6,5,'#2c4738')
        else:
            d.rectangle((0,0,15,15),outline=tint(color,-23));d.line((1,1,13,1),fill=tint(color,21))
        p=ASSETS/f'textures/block/console/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
    for family in FAMILIES:
        atlas(family)
        if family!='genesis':write('models/item',family,cartridge(family))
        if family in ('nes','gb','gbc','gba'):
            m=handheld(family);m['overrides']=[{'predicate':{'retrocraft:powered':1},'model':f'retrocraft:item/{family}_handheld_on'}]
            write('models/item',family+'_handheld',m)
            write('models/item',family+'_handheld_on',{'parent':f'retrocraft:item/{family}_handheld','textures':{'screen':'retrocraft:block/console/lcd_on','indicator':'retrocraft:block/console/power_on'}})
        if family not in ('gb','gbc','gba'):
            name=family+'_console';write('models/block',name,console(family))
            write('models/block',name+'_on',{'parent':f'retrocraft:block/{name}','textures':{'indicator':'retrocraft:block/console/power_on'}})
            write('models/item',name,{'parent':f'retrocraft:block/{name}'})
            variants={}
            for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
                for powered in [False,True]:variants[f'facing={facing},powered={str(powered).lower()}']={'model':f'retrocraft:block/{name}'+('_on' if powered else ''),'y':angle}
            write('blockstates',name,{'variants':variants})
    print('Generated 8 cartridge models, 4 handhelds, 6 consoles and 13 original textures.')

if __name__=='__main__': generate()
