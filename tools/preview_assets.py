#!/usr/bin/env python3
"""Orthographic, textured contact sheet of the actual shipped cuboid models (Pillow + NumPy)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageEnhance
import json, math
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/retrocraft'
C,S=math.cos(math.radians(25)),math.sin(math.radians(25))
P,Q=math.cos(math.radians(18)),math.sin(math.radians(18))

def transform(v, reverse=False):
    if reverse:v=(16-v[0],v[1],16-v[2])
    x,y,z=(k-8 for k in v);depth=x*S+z*C
    return x*C-z*S, -(y*P-depth*Q), y*Q+depth*P

def resolve(name):
    data=json.loads((A/'models'/f'{name}.json').read_text())
    parent=data.get('parent','')
    if parent.startswith('retrocraft:'):
        base=resolve(parent.split(':',1)[1]);base.update({k:v for k,v in data.items() if k!='textures'})
        base['textures']={**base.get('textures',{}),**data.get('textures',{})};return base
    return data

def render(name,w,h):
    m=resolve(name);faces=[];reverse=name.startswith('block/')
    for e in m['elements']:
        x,y,z=e['from'];X,Y,Z=e['to']
        corners={
            'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
            'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
            'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
            'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
            'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
            'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
        }
        normals={'south':(0,0,1),'north':(0,0,-1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
        for side,face in e['faces'].items():
            nx,ny,nz=normals[side]
            if reverse:nx,nz=-nx,-nz
            if ny*Q+(nx*S+nz*C)*P<=0:continue
            pts=[transform(p,reverse) for p in corners[side]]
            texture=face['texture']
            while texture.startswith('#'):texture=m['textures'][texture[1:]]
            image=Image.open(A/'textures'/(texture.split(':',1)[1]+'.png')).convert('RGB')
            u,v,U,V=face.get('uv',[0,0,16,16]);fw,fh=image.size
            image=image.crop((round(min(u,U)/16*fw),round(min(v,V)/16*fh),round(max(u,U)/16*fw),round(max(v,V)/16*fh)))
            if u>U:image=image.transpose(Image.FLIP_LEFT_RIGHT)
            if v>V:image=image.transpose(Image.FLIP_TOP_BOTTOM)
            brightness=1 if not e.get('shade',True) else {'up':1,'south':.87,'east':.68}.get(side,.78)
            image=ImageEnhance.Brightness(image).enhance(brightness)
            faces.append((sum(p[2] for p in pts)/4,pts,image))
    bounds=[p for _,ps,_ in faces for p in ps]
    minx,maxx=min(p[0] for p in bounds),max(p[0] for p in bounds)
    miny,maxy=min(p[1] for p in bounds),max(p[1] for p in bounds)
    scale=min((w-40)/(maxx-minx),(h-28)/(maxy-miny));ox=w/2-(minx+maxx)*scale/2;oy=h/2-(miny+maxy)*scale/2
    pixels=np.zeros((h,w,4),dtype=np.uint8);depths=np.full((h,w),-np.inf)
    for _,ps,img in sorted(faces,key=lambda f:f[0]):
        pts=[(p[0]*scale+ox,p[1]*scale+oy) for p in ps]
        p0,p1,_,p3=pts;tw,th=img.size
        ax=(p1[0]-p0[0])/tw;ay=(p1[1]-p0[1])/tw;bx=(p3[0]-p0[0])/th;by=(p3[1]-p0[1])/th
        det=ax*by-ay*bx
        if abs(det)<.001:continue
        a,b=by/det,-bx/det;d,e=-ay/det,ax/det
        matrix=(a,b,-a*p0[0]-b*p0[1],d,e,-d*p0[0]-e*p0[1])
        xx0=max(0,int(min(p[0] for p in pts)));xx1=min(w,int(math.ceil(max(p[0] for p in pts))))
        yy0=max(0,int(min(p[1] for p in pts)));yy1=min(h,int(math.ceil(max(p[1] for p in pts))))
        yy,xx=np.mgrid[yy0:yy1,xx0:xx1];xx=xx+.5;yy=yy+.5
        u=a*xx+b*yy+matrix[2];v=d*xx+e*yy+matrix[5]
        z=ps[0][2]+u/tw*(ps[1][2]-ps[0][2])+v/th*(ps[3][2]-ps[0][2])
        current=depths[yy0:yy1,xx0:xx1]
        mask=(u>=0)&(v>=0)&(u<tw)&(v<th)&(z>=current)
        sampled=np.asarray(img)[np.clip(v.astype(int),0,th-1),np.clip(u.astype(int),0,tw-1)]
        target=pixels[yy0:yy1,xx0:xx1];target[mask,:3]=sampled[mask];target[mask,3]=255;current[mask]=z[mask]
    return Image.fromarray(pixels)


def main():
    font='/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf'
    if not Path(font).exists():font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    body=ImageFont.truetype(font,16);heading=ImageFont.truetype(font,22)
    im=Image.new('RGB',(960,1272),'#1d242a');d=ImageDraw.Draw(im)
    d.text((24,18),'RETROCRAFT  /  MODELOS ORIGINALES',font=heading,fill='#e4dcc8')
    groups=[('CARTUCHOS',[(x,x.upper()) for x in ['nes','snes','gb','gbc','gba','megadrive','n64','mame']],4,188),
            ('PORTÁTILES',[(x+'_handheld_on',n) for x,n in [('nes','NES Handheld'),('gb','Game Boy'),('gbc','Game Boy Color'),('gba','Game Boy Advance')]],4,212),
            ('CONSOLAS DE MESA',[(x+'_console',n) for x,n in [('nes','NES'),('snes','SNES'),('megadrive','Mega Drive'),('genesis','Genesis'),('n64','Nintendo 64'),('mame','Arcade')]],3,242)]
    y=64
    for title,models,columns,rowh in groups:
        d.line((24,y,936,y),fill='#59615e');d.text((24,y+9),title,font=body,fill='#bfb79f');y+=38
        cellw=912//columns
        for i,(name,label) in enumerate(models):
            x=24+i%columns*cellw;cy=y+i//columns*rowh
            d.rectangle((x+4,cy+4,x+cellw-4,cy+rowh-8),fill='#293239')
            kind='block' if '_console' in name else 'item'
            img=render(f'{kind}/{name}',cellw-16,rowh-44)
            im.paste(img,(x+8,cy+8),img)
            width=d.textbbox((0,0),label,font=body)[2];d.text((x+(cellw-width)/2,cy+rowh-32),label,font=body,fill='#e1daca')
        y+=math.ceil(len(models)/columns)*rowh
    im=im.crop((0,0,960,min(im.height,y+16)))
    target=ROOT/'docs/assets-preview.png';target.parent.mkdir(exist_ok=True);im.save(target)
    print(target)
if __name__=='__main__':main()
