#!/usr/bin/env python3
"""Original texel-aligned artwork and cuboid models. Run with Python + Pillow.
No console logos, photographs, or borrowed Minecraft textures are used.

Every cuboid face gets its own hand-painted region in a per-family 128x128 atlas
at 2 texels per model unit, so shapes, labels, buttons and ports line up with the
geometry instead of being stretched generic tiles.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json, random

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/retrocraft'
PX = 2          # texels per model unit
SIZE = 128      # atlas size in texels
FACES = ['north', 'south', 'east', 'west', 'up', 'down']
REFS = {'#screen', '#indicator'}

def rgb(c): return tuple(int(c[i:i+2], 16) for i in (1, 3, 5)) if isinstance(c, str) else tuple(c)
def hexa(t): return '#%02x%02x%02x' % tuple(t)
def tint(c, n): return hexa(max(0, min(255, v + n)) for v in rgb(c))
def mix(a, b, t): return hexa(int(x + (y - x) * t) for x, y in zip(rgb(a), rgb(b)))

def fill(d, x, y, w, h, color, rnd, noise=3, bevel=0):
    base = rgb(color)
    for yy in range(h):
        for xx in range(w):
            dv = rnd.choice([-noise, -1, 0, 0, 0, 0, 1, noise]) if noise else 0
            if bevel and w >= 3 and h >= 3:
                if yy == 0 or xx == 0: dv += bevel
                if yy == h - 1 or xx == w - 1: dv -= bevel
            d.point((x + xx, y + yy), fill=tint(base, dv))

class Face:
    """Painter helper. Units are model units measured from the face's top-left corner."""
    def __init__(self, d, x, y, w, h, rnd): self.d, self.x, self.y, self.w, self.h, self.rnd = d, x, y, w, h, rnd
    @property
    def units(self): return self.w / PX, self.h / PX
    def px(self, u, v): return self.x + int(round(u * PX)), self.y + int(round(v * PX))
    def rect(self, u0, v0, u1, v1, color, noise=0, bevel=0, outline=None):
        x0, y0 = self.px(u0, v0); x1, y1 = self.px(u1, v1)
        x1, y1 = max(x1, x0 + 1), max(y1, y0 + 1)
        x0, y0 = max(x0, self.x), max(y0, self.y); x1, y1 = min(x1, self.x + self.w), min(y1, self.y + self.h)
        if x1 <= x0 or y1 <= y0: return
        fill(self.d, x0, y0, x1 - x0, y1 - y0, color, self.rnd, noise, bevel)
        if outline: self.d.rectangle((x0, y0, x1 - 1, y1 - 1), outline=rgb(outline))
    def dot(self, u, v, color):
        x, y = self.px(u, v)
        if self.x <= x < self.x + self.w and self.y <= y < self.y + self.h: self.d.point((x, y), fill=rgb(color))
    def hline(self, u0, u1, v, color): self.rect(u0, v, u1, v + .5, color)
    def vline(self, u, v0, v1, color): self.rect(u, v0, u + .5, v1, color)
    def text(self, word, u, v, color):
        x, y = self.px(u, v)
        for letter in word:
            for dy, row in enumerate(GLYPHS.get(letter, GLYPHS[' '])):
                for dx, bit in enumerate(row):
                    if bit == '1' and x + dx < self.x + self.w and y + dy < self.y + self.h: self.d.point((x + dx, y + dy), fill=rgb(color))
            x += 4

GLYPHS = {
'R':['110','101','110','101','101'], 'E':['111','100','110','100','111'], 'T':['111','010','010','010','010'],
'O':['111','101','101','101','111'], 'N':['101','111','111','111','101'], 'S':['111','100','111','001','111'],
'G':['111','100','101','101','111'], 'B':['110','101','110','101','110'], 'C':['111','100','100','100','111'],
'A':['010','101','111','101','101'], 'M':['101','111','111','101','101'], 'D':['110','101','101','101','110'],
'P':['110','101','110','100','100'], 'X':['101','101','010','101','101'], 'Y':['101','101','010','010','010'],
'I':['111','010','010','010','111'], 'L':['100','100','100','100','111'], 'U':['101','101','101','101','111'],
'V':['101','101','101','101','010'], 'H':['101','101','111','101','101'], 'K':['101','101','110','101','101'],
'0':['111','101','101','101','111'], '1':['010','110','010','010','111'], '2':['110','001','010','100','111'],
'4':['101','101','111','001','001'], '6':['011','100','111','101','111'], '8':['111','101','111','101','111'],
'-':['000','000','111','000','000'], ' ':['000']*5,
}

class Atlas:
    def __init__(self, name, size=SIZE):
        self.name = name; self.size = size; self.im = Image.new('RGBA', (size, size), (0, 0, 0, 0)); self.d = ImageDraw.Draw(self.im)
        self.rows = []; self.cache = {}; self.rnd = random.Random(name)
    def alloc(self, w, h):
        best = None
        for row in self.rows:
            if row['h'] >= h and row['x'] + w <= self.size and (best is None or row['h'] < best['h']): best = row
        if best is not None and best['h'] <= h * 2 + 1:
            x = best['x']; best['x'] += w; return x, best['y']
        y = sum(r['h'] for r in self.rows)
        if y + h > self.size: raise RuntimeError(f'atlas {self.name} overflow')
        self.rows.append({'y': y, 'h': h, 'x': w}); return 0, y
    def face(self, w, h, color, painter=None, noise=3, bevel=0):
        w, h = max(1, int(round(w * PX))), max(1, int(round(h * PX)))
        key = (w, h, color, noise, bevel) if painter is None else None
        if key in self.cache: return self.cache[key]
        x, y = self.alloc(w, h)
        fill(self.d, x, y, w, h, color, self.rnd, noise, bevel)
        if painter: painter(Face(self.d, x, y, w, h, self.rnd))
        uv = [x * 16 / self.size, y * 16 / self.size, (x + w) * 16 / self.size, (y + h) * 16 / self.size]
        if key: self.cache[key] = uv
        return uv
    def save(self):
        out = ASSETS / f'textures/block/console/{self.name}.png'; out.parent.mkdir(parents=True, exist_ok=True); self.im.save(out)

class Model:
    def __init__(self, atlas): self.atlas = atlas; self.elements = []
    def box(self, a, b, color, faces=None, bevel=8, noise=3, shade=True, rotation=None, skip=()):
        x0, y0, z0 = a; x1, y1, z1 = b
        dims = {'north': (x1 - x0, y1 - y0), 'south': (x1 - x0, y1 - y0), 'east': (z1 - z0, y1 - y0),
                'west': (z1 - z0, y1 - y0), 'up': (x1 - x0, z1 - z0), 'down': (x1 - x0, z1 - z0)}
        out = {}
        for f in FACES:
            if f in skip: continue
            spec = (faces or {}).get(f, color)
            if spec is None: continue
            if isinstance(spec, str) and spec in REFS: out[f] = {'texture': spec, 'uv': [0, 0, 16, 16]}; continue
            col, painter = spec if isinstance(spec, tuple) else (spec, None)
            w, h = dims[f]
            out[f] = {'texture': '#atlas', 'uv': self.atlas.face(w, h, col, painter, noise, bevel)}
        e = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': out}
        if not shade: e['shade'] = False
        if rotation: e['rotation'] = rotation
        self.elements.append(e); return e
    def lamp(self, a, b): return self.box(a, b, None, {f: '#indicator' for f in FACES}, shade=False)

def many(*painters):
    def paint(F):
        for p in painters: p(F)
    return paint

# ---------------------------------------------------------------- painters

def label(p, u0, v0, u1, v1, seed=1, header=True, rounded=False, dark=False):
    """Printed cartridge label: title band, pixel landscape artwork, registration stripe."""
    def paint(F):
        rnd = random.Random(seed); paper, accent, art = p['paper'], p['accent'], p['art']
        F.rect(u0, v0, u1, v1, paper, outline=tint(paper, -45))
        if rounded:
            shell = p.get('cart', p['body'])
            for cu, cv in [(u0, v0), (u1 - .5, v0), (u0, v1 - .5), (u1 - .5, v1 - .5)]: F.dot(cu, cv, shell)
        w, h = u1 - u0, v1 - v0
        if dark:
            # Black label: artwork fills the upper two thirds, title and licence lines sit below it.
            a0, b0, a1, b1 = u0 + .5, v0 + .5, u1 - .5, v0 + .5 + round(h * .6 * 2) / 2
            F.rect(a0, b0, a1, b1, art); F.rect(a0, b0, a1, b0 + (b1 - b0) * .35, tint(art, -35))
            gh = max(.5, round((b1 - b0) * .25 * 2) / 2)
            F.rect(a0, b1 - gh, a1, b1, p['ground']); F.rect(a0, b1 - gh, a1, b1 - gh + .5, tint(p['ground'], 30))
            F.rect(a1 - 1.5, b0 + .5, a1 - .5, b0 + 1.5, '#f6d35b')
            hx = a0 + (a1 - a0) * .35
            F.rect(hx, b1 - gh - 1.5, hx + 1, b1 - gh, accent); F.rect(hx, b1 - gh - 1.5, hx + 1, b1 - gh - 1, '#f0c9a0')
            if a1 - a0 > 5:
                bx = a0 + (a1 - a0) * .7; F.rect(bx, b1 - gh - 1, bx + 1, b1 - gh, '#5a4a3a'); F.rect(bx - .5, b1 - gh - 1.5, bx + 1.5, b1 - gh - 1, '#4a8b3a')
            tv = b1 + .5
            F.rect(a0 + .5, tv, a1 - 1.5, tv + .5, '#f3eee2')
            if v1 - tv > 2.5:
                F.rect(a0 + .5, tv + 1, a0 + (a1 - a0) * .55, tv + 1.5, '#c9c4b4')
                F.rect(a1 - 1.5, v1 - 1.5, a1 - .5, v1 - .5, '#c9a24a'); F.rect(a0 + .5, v1 - 1, a0 + 2, v1 - .5, accent)
            return
        hb = max(1, round(h * .22 * 2) / 2) if header else .5
        if header:
            F.rect(u0 + .5, v0 + .5, u1 - .5, v0 + hb, p['header'])
            uu = u0 + 1; vv = v0 + .5 + max(.5, (hb - .5) / 2 - .25)
            while uu + 1.5 <= u1 - 1:
                F.rect(uu, vv, uu + rnd.choice([1, 1.5]), vv + .5, mix(p['header'], '#ffffff', .55)); uu += rnd.choice([2, 2.5])
        a0, b0, a1, b1 = u0 + .5, v0 + hb + .5, u1 - .5, v1 - 1
        if a1 - a0 < 2 or b1 - b0 < 2: return
        F.rect(a0, b0, a1, b1, mix(art, '#ffffff', .35))
        F.rect(a0, b0, a1, b0 + (b1 - b0) * .3, art)
        F.rect(a0, b0, a1, b0 + .5, tint(art, -25))
        gh = max(.5, round((b1 - b0) * .25 * 2) / 2)
        F.rect(a0, b1 - gh, a1, b1, p['ground'])
        F.rect(a0, b1 - gh, a1, b1 - gh + .5, tint(p['ground'], 30))
        F.rect(a1 - 1.5, b0 + .5, a1 - .5, b0 + 1.5, '#f6d35b')
        for i in range(int((a1 - a0) // 1.5)):
            hx = a0 + .5 + i * 1.5
            if rnd.random() < .5: F.rect(hx, b1 - gh - rnd.choice([.5, 1, 1.5]), hx + 1, b1 - gh, tint(art, -40))
        hx = a0 + (a1 - a0) * .3
        F.rect(hx, b1 - gh - 1.5, hx + 1, b1 - gh, accent); F.rect(hx, b1 - gh - 1.5, hx + 1, b1 - gh - 1, '#f0c9a0')
        if a1 - a0 > 6:
            bx = a0 + (a1 - a0) * .7
            F.rect(bx, b1 - gh - 1, bx + 1, b1 - gh, '#5a4a3a'); F.rect(bx - .5, b1 - gh - 1.5, bx + 1.5, b1 - gh - 1, '#4a8b3a')
        F.rect(u0 + .5, v1 - 1, u1 - .5, v1 - .5, accent)
    return paint

def grooves(u0, v0, u1, v1, n, color, horizontal=True):
    def paint(F):
        for i in range(n):
            if horizontal:
                v = v0 + (v1 - v0) * (i + .5) / n
                F.rect(u0, v - .25, u1, v + .25, tint(color, -45)); F.rect(u0, v + .25, u1, v + .5, tint(color, 22))
            else:
                u = u0 + (u1 - u0) * (i + .5) / n
                F.rect(u - .25, v0, u + .25, v1, tint(color, -45)); F.rect(u + .25, v0, u + .5, v1, tint(color, 22))
    return paint

def vents(u0, v0, u1, v1, n, dark='#16181c', horizontal=False):
    def paint(F):
        for i in range(n):
            if horizontal:
                v = v0 + (v1 - v0) * (i + .5) / n; F.rect(u0, v - .25, u1, v + .25, dark)
            else:
                u = u0 + (u1 - u0) * (i + .5) / n; F.rect(u - .25, v0, u + .25, v1, dark)
    return paint

def slot(u0, v0, u1, v1, rim='#6a6c70', dark='#101216'):
    def paint(F):
        F.rect(u0, v0, u1, v1, dark); F.rect(u0, v0, u1, v0 + .5, rim); F.rect(u0, v1 - .5, u1, v1, tint(rim, -30))
    return paint

def pins(u0, v0, u1, v1, gold='#d7b15a', gap='#2a2420'):
    def paint(F):
        F.rect(u0, v0, u1, v1, gap); u = u0
        while u + .5 <= u1 + .01:
            F.rect(u, v0, u + .5, v1, gold); F.rect(u, v0, u + .5, v0 + .5, tint(gold, 35)); u += 1
    return paint

def ports(u0, v0, u1, v1, n, style, shell='#3a3b40'):
    def paint(F):
        step = (u1 - u0) / n
        for i in range(n):
            a = u0 + step * i + step / 2
            if style == 'round':  # NES: round socket with a flat top in a square shell
                F.rect(a - 1, v0, a + 1, v1, shell); F.rect(a - .5, v0 + .5, a + .5, v1 - .5, '#101114'); F.dot(a - .5, v0 + .5, tint(shell, 40))
            elif style == 'snes':  # SNES: pill shaped socket in purple-grey frame
                F.rect(a - 1.25, v0, a + 1.25, v1, '#6c6880'); F.rect(a - .75, v0 + .5, a + .75, v1 - .5, '#15161a'); F.dot(a - .75, v0 + .5, '#9c97b4')
            elif style == 'de9':  # Mega Drive: trapezoid DE-9 socket
                F.rect(a - 1.5, v0, a + 1.5, v1, '#55575c'); F.rect(a - 1, v0 + .5, a + 1, v1 - .5, '#0f1013'); F.rect(a - .5, v1 - 1, a + .5, v1 - .5, '#0f1013')
                for k in (-.5, .5): F.dot(a + k, v0 + 1, '#6e6240')
            else:  # N64: square socket with an inner notch
                F.rect(a - 1, v0, a + 1, v1, '#55575d'); F.rect(a - .5, v0 + .5, a + .5, v1 - .5, '#0e0f12'); F.dot(a, v0 + .5, '#7d8088')
    return paint

def speaker(u0, v0, u1, v1, n, dark='#1b1b1f'):
    def paint(F):  # slanted slits as on the DMG: one texel wide, three texels apart, rising two rows per column
        x0, y0 = F.px(u0, v0); x1, y1 = F.px(u1, v1)
        for i in range(n):
            for r in range(y1 - y0):
                x = x0 + i * 3 + (y1 - y0 - 1 - r) // 2; y = y0 + r
                if x < x1 and x < F.x + F.w and y < F.y + F.h: F.d.point((x, y), fill=rgb(dark))
    return paint

def holes(u0, v0, cols, rows, pitch, dark='#1b1b1f'):
    def paint(F):
        for r in range(rows):
            for c in range(cols): F.dot(u0 + c * pitch, v0 + r * pitch, dark)
    return paint

def screws(points, color='#2c2d31'):
    def paint(F):
        for u, v in points: F.dot(u, v, color); F.dot(u + .5, v, tint(color, 40))
    return paint

def ring(cu, cv, r, inner, color, accents=None):
    def paint(F):  # Mega Drive raised disc with concentric grooves
        def ell(rr, **kw):
            x0, y0 = F.px(cu - rr, cv - rr); x1, y1 = F.px(cu + rr, cv + rr)
            F.d.ellipse((x0, y0, x1 - 1, y1 - 1), **kw)
        ell(r, fill=rgb(tint(color, -8)), outline=rgb(tint(color, -45)))
        ell(r - 1, outline=rgb(tint(color, 18)))
        ell(inner, fill=rgb(tint(color, -25)), outline=rgb(tint(color, -50)))
        if accents:
            import math
            for i in range(9):
                ang = math.pi * (1.1 + .8 * i / 8)
                F.dot(cu + math.cos(ang) * (r - .5), cv + math.sin(ang) * (r - .5), accents)
    return paint

def rect(u0, v0, u1, v1, color, **kw):
    return lambda F: F.rect(u0, v0, u1, v1, color, **kw)

def text(word, u, v, color): return lambda F: F.text(word, u, v, color)

# ---------------------------------------------------------------- families

FAMILIES = {
    'nes':       dict(body='#c7c5c0', dark='#4f5155', accent='#c1392b', paper='#141416', header='#1a1a1d', art='#4c8fd8', ground='#6a9e3c'),
    'snes':      dict(body='#b5b4bb', dark='#5b5a63', accent='#4f3f91', paper='#e6e4ea', header='#3c3650', art='#7c69c2', ground='#5f8f47'),
    'gb':        dict(body='#c8c4b8', cart='#a9a9a5', dark='#4d4b58', accent='#9b2d5f', paper='#dcd8c8', header='#3b3b44', art='#6b8fb0', ground='#7a7f4a'),
    'gbc':       dict(body='#1f8d8a', cart='#8fb0b8', dark='#2d2d35', accent='#4b3a7a', paper='#e4e2d8', header='#2a2a31', art='#e07a3c', ground='#56a06a'),
    'gba':       dict(body='#584c9e', cart='#7d72bd', dark='#1c1b23', accent='#8d85c4', paper='#e8e6ee', header='#2a2742', art='#4a8fd6', ground='#7c9e3e'),
    'megadrive': dict(body='#232428', dark='#15161a', accent='#4e8ad0', paper='#d9d9dc', header='#2a2c33', art='#3a6fb5', ground='#8c7a3a'),
    'genesis':   dict(body='#232428', dark='#15161a', accent='#d4a947', paper='#d9d9dc', header='#2a2c33', art='#c0583b', ground='#8c7a3a'),
    'n64':       dict(body='#44464c', dark='#2a2b30', accent='#c0392b', paper='#e6e4e0', header='#1e1f24', art='#3d9a6a', ground='#8b6b3c'),
    'mame':      dict(body='#2a2831', dark='#17161c', accent='#f0b53a', paper='#f1e9d2', header='#20202a', art='#2b3fa8', ground='#c04f8a'),
}

def item_display(kind):
    if kind == 'cart':
        return {
            'gui': {'rotation': [12, -28, 0], 'translation': [0, 0, 0], 'scale': [.9, .9, .9]},
            'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
            'fixed': {'rotation': [0, 180, 0], 'scale': [.8, .8, .8]},
            'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 2, 1], 'scale': [.55, .55, .55]},
            'thirdperson_lefthand': {'rotation': [0, -90, 0], 'translation': [0, 2, 1], 'scale': [.55, .55, .55]},
            'firstperson_righthand': {'rotation': [0, -20, 0], 'translation': [1, 2, 0], 'scale': [.7, .7, .7]},
            'firstperson_lefthand': {'rotation': [0, 20, 0], 'translation': [1, 2, 0], 'scale': [.7, .7, .7]},
        }
    return {
        'gui': {'rotation': [18, -30, 0], 'translation': [0, 0, 0], 'scale': [.95, .95, .95]},
        'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [.85, .85, .85]},
        'thirdperson_righthand': {'rotation': [-45, 90, 0], 'translation': [0, 3, 1], 'scale': [.55, .55, .55]},
        'thirdperson_lefthand': {'rotation': [-45, -90, 0], 'translation': [0, 3, 1], 'scale': [.55, .55, .55]},
        'firstperson_righthand': {'rotation': [-30, -30, 0], 'translation': [0, 2, 0], 'scale': [.75, .75, .75]},
        'firstperson_lefthand': {'rotation': [-30, 30, 0], 'translation': [0, 2, 0], 'scale': [.75, .75, .75]},
    }

def finish(m, family, kind):
    result = {'credit': 'Original Retrocraft pixel artwork and models',
              'textures': {'atlas': f'retrocraft:block/console/{family}', 'particle': f'retrocraft:block/console/{family}',
                           'indicator': 'retrocraft:block/console/power_off'}}
    if kind == 'block':
        # Consoles are authored as seen from the front (north): viewer's left at low x. The north face
        # has texture-left at x=16, so mirror the geometry and flip the top/bottom UVs to match.
        for e in m.elements:
            e['from'][0], e['to'][0] = 16 - e['to'][0], 16 - e['from'][0]
            faces = {}
            for side, face in e['faces'].items():
                if side in ('up', 'down'): u0, v0, u1, v1 = face['uv']; face['uv'] = [u1, v0, u0, v1]
                faces[{'east': 'west', 'west': 'east'}.get(side, side)] = face
            e['faces'] = faces
        result['parent'] = 'minecraft:block/block'
        result['display'] = {'gui': {'rotation': [25, 225, 0], 'translation': [0, 1, 0], 'scale': [.95, .95, .95]}}
        if family == 'mame':
            result['display'] = {
                'gui': {'rotation': [25, 225, 0], 'translation': [0, -1.5, 0], 'scale': [.5, .5, .5]},
                'ground': {'translation': [0, 3, 0], 'scale': [.25, .25, .25]},
                'fixed': {'rotation': [0, 180, 0], 'translation': [0, -4, 0], 'scale': [.5, .5, .5]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.2, .2, .2]},
                'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.2, .2, .2]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -2, 0], 'scale': [.25, .25, .25]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, -2, 0], 'scale': [.25, .25, .25]},
            }
    else:
        result['gui_light'] = 'front' if kind == 'cart' else 'side'
        result['display'] = item_display(kind)
    result['elements'] = m.elements
    return result

# ---------------------------------------------------------------- cartridges (front = south)

def cartridge(family, atlas):
    p = FAMILIES[family]; m = Model(atlas); body, dark = p.get('cart', p['body']), p['dark']
    if family == 'nes':
        # US Game Pak: vertical grip grooves on the left strip, black label on the right, arrow and three grooves below it.
        def front(F):
            F.rect(4, 9.6, 5.1, 10.4, tint(body, -22)); F.rect(4.3, 10.4, 4.8, 10.7, tint(body, -22))
            grooves(3.6, 10.8, 9.6, 11.7, 3, body)(F)
            label(p, 3.6, .4, 9.6, 8.6, 1, dark=True, rounded=True)(F)
        m.box((3, 2.5, 7.3), (13, 14.3, 8.7), body, {
            'south': (body, front),
            'north': (body, many(screws([(1, 1), (8.5, 1), (1, 10.5), (8.5, 10.5), (4.75, 6)]), rect(2.5, 3.5, 7.5, 7, tint(body, -10), bevel=-6))),
            'down': (body, slot(1, .2, 9, 1.2))}, bevel=10)
        for i in range(6):
            x = 3.35 + i * .5
            m.box((x, 3.2, 8.7), (x + .25, 13.6, 8.85), body, bevel=0)
    elif family == 'snes':
        # Wide US shell: ridged grip corners on top, big label, exposed dark connector with gold pins.
        m.box((1, 4, 7.3), (15, 12.5, 8.7), body, {
            'south': (body, label(p, 1, .8, 13, 7.9, 2)),
            'north': (body, screws([(1, 1), (13, 1), (1, 7.5), (13, 7.5)]))}, bevel=10)
        for x in (1, 11.5):
            m.box((x, 12.5, 7.3), (x + 3.5, 13.3, 8.7), body, {
                'south': (body, grooves(0, 0, 3.5, .8, 2, body)), 'up': (body, grooves(.3, 0, 3.2, 1.4, 3, body, False))}, bevel=6)
        m.box((4.5, 12.5, 7.6), (11.5, 12.8, 8.4), tint(body, -25), bevel=0)
        m.box((2.5, 3, 7.55), (13.5, 4, 8.45), dark, {'south': (dark, pins(.5, .4, 10.5, 1)), 'north': (dark, pins(.5, .4, 10.5, 1)), 'down': (dark, slot(.5, .15, 10.5, .75))}, bevel=0)
    elif family in ('gb', 'gbc'):
        # Game Pak (5.7 x 6.5 cm): grey DMG shell with the top-left lock notch, or clear Color shell showing the board.
        notch = family == 'gb'
        def clear(F):  # board, battery coin cell and ROM seen through translucent plastic
            F.rect(.5, .5, 7.5, 8.5, mix(body, '#3f6b4e', .55)); F.rect(1, .8, 7, 8.2, mix(body, '#3f6b4e', .7))
            F.rect(2, 1.1, 6, 1.6, mix(body, '#4a4a52', .7)); F.rect(5.2, 2.3, 6.8, 3.9, mix(body, '#d8dde0', .6)); F.rect(5.5, 2.6, 6.5, 3.6, mix(body, '#f0f2f4', .7))
            F.rect(1.3, 2.4, 4.6, 4.4, mix(body, '#1d1d22', .75)); F.rect(1.3, 5.2, 6.7, 5.7, mix(body, '#c9a24a', .6))
        top = 10.8 if notch else 12
        def front(F):
            if notch: label(p, .6, .9, 7.4, 7.3, 3, rounded=True)(F)
            else:
                clear(F); grooves(.3, .15, 7.7, 1.05, 2, body)(F); F.rect(2.6, .3, 5.4, .9, tint(body, 14))
                label(p, .6, 2.1, 7.4, 8.5, 3, rounded=True)(F)
        def back(F):
            if not notch: clear(F)
            screws([(3.75, top - 3.7)])(F); F.rect(.8, 1, 7.2, 5.5, tint(body, -12), bevel=-6)
        m.box((4, 3, 7.3), (12, top, 8.7), body, {
            'south': (body, front), 'north': (body, back),
            'up': (body, rect(.3, .3, 7.7, 1.1, tint(body, -10))), 'down': (body, slot(.7, .2, 7.3, 1.2))}, bevel=10)
        if notch:
            m.box((5.5, 10.8, 7.3), (12, 12, 8.7), body, {'south': (body, many(grooves(.3, .15, 6.2, 1.05, 2, body), rect(2, .3, 4.6, .9, tint(body, 14)))),
                                                        'up': (body, rect(.3, .3, 6.2, 1.1, tint(body, -10)))}, bevel=8)
    elif family == 'gba':
        # Game Pak (5.9 x 3.5 cm): light grey, thicker grip lip along the top, rounded label filling the face.
        m.box((2, 5, 7.4), (14, 10.8, 8.6), body, {
            'south': (body, label(p, .5, .4, 11.5, 5.4, 4, rounded=True)),
            'north': (body, many(screws([(6, 5.2)]), rect(1, .8, 11, 4, tint(body, -10), bevel=-6), rect(1.5, 1.2, 10.5, 1.6, tint(body, -40)), rect(1.5, 2, 7, 2.4, tint(body, -30)))),
            'east': (body, rect(.3, 1.2, .9, 3.6, tint(body, -30))), 'west': (body, rect(.3, 1.2, .9, 3.6, tint(body, -30))),
            'down': (body, slot(.6, .2, 11.4, 1))}, bevel=10)
        m.box((2, 10.8, 7.2), (14, 11.6, 8.8), body, {'south': (body, many(rect(.6, .2, 11.4, .6, tint(body, -14)), rect(4, .25, 8, .55, tint(body, 12)))),
                                                     'up': (body, rect(.5, .3, 11.5, 1.3, tint(body, -10)))}, bevel=8)
    elif family in ('megadrive', 'genesis'):
        # Black shell: thick grip lip with ridges across the top, thinner body that slides into the console.
        m.box((2, 3.5, 7.35), (14, 10.5, 8.65), body, {
            'south': (body, label(p, .8, .3, 11.2, 6.8, 6)),
            'north': (body, screws([(1, 6), (11, 6)]))}, bevel=10)
        m.box((2, 10.5, 7.1), (14, 12.5, 8.9), body, {
            'south': (body, grooves(.5, .3, 11.5, 1.7, 2, body)), 'north': (body, grooves(.5, .3, 11.5, 1.7, 2, body)),
            'up': (body, rect(1, .4, 11, 1.4, tint(body, -15)))}, bevel=10)
        m.box((3, 2.5, 7.6), (13, 3.5, 8.4), dark, {'south': (dark, pins(.4, .3, 9.6, 1)), 'north': (dark, pins(.4, .3, 9.6, 1)), 'down': (dark, slot(.3, .1, 9.7, .7))}, bevel=0)
    elif family == 'n64':
        # Charcoal shell: horizontal ridges across the top, raised centre lip, label below.
        m.box((1.5, 4, 7.3), (14.5, 12.5, 8.7), body, {
            'south': (body, many(grooves(.4, .2, 12.6, 1.8, 3, body), label(p, 1, 2, 12, 8, 7))),
            'north': (body, many(screws([(1, 1), (12, 1), (1, 7.5), (12, 7.5)]), rect(3, 3, 10, 6.5, tint(body, -10)))),
            'down': (body, slot(1, .2, 12, 1.2))}, bevel=10)
        m.box((4, 12.5, 7.5), (12, 13.1, 8.5), body, bevel=6)
        m.box((3, 3.2, 7.6), (13, 4, 8.4), dark, {'south': (dark, pins(.3, .2, 9.7, .8)), 'north': (dark, pins(.3, .2, 9.7, .8)), 'down': (dark, slot(.3, .1, 9.7, .7))}, bevel=0)
    else:  # arcade PCB
        pcb, chip = '#2d6a44', '#17171a'
        def traces(F):
            for u, v0, v1 in [(1, 1, 8.5), (1.5, 1, 4), (11, 2, 8.5), (10.5, 5, 9), (6.5, 7.5, 9)]: F.vline(u, v0, v1, tint(pcb, 30))
            for v, u0, u1 in [(1, 1, 6), (9, 1, 11.5), (7.5, 6.5, 10.5)]: F.hline(u0, u1, v, tint(pcb, 30))
            for u, v in [(1, 1), (6, 1), (11, 2), (1, 8.5), (11.5, 9), (10.5, 5)]: F.dot(u, v, '#d8c070')
            F.text('ARCADE', 1, 1.5, '#d8d8c8')
        m.box((2, 3.5, 7.8), (14, 13, 8.2), pcb, {'south': (pcb, traces), 'north': (pcb, many(traces, text('REV-2', 6, 6, '#d8d8c8')))}, bevel=0)
        m.box((3, 2.5, 7.85), (13, 3.5, 8.15), '#2a2420', {'south': ('#2a2420', pins(0, 0, 10, 1)), 'north': ('#2a2420', pins(0, 0, 10, 1))}, bevel=0)
        def chip_face(w, h): return (chip, many(rect(.4, .4, .9, .9, '#45454d'), rect(1.2, h / 2 - .25, w - 1, h / 2 + .25, '#5c5c66'), rect(1.2, h / 2 + .6, w - 2, h / 2 + 1, '#4a4a52')))
        m.box((3, 7, 8.2), (7.5, 12, 8.7), chip, {'south': chip_face(4.5, 5)}, bevel=6)
        m.box((8.5, 8.5, 8.2), (13, 12, 8.7), chip, {'south': chip_face(4.5, 3.5)}, bevel=6)
        m.box((3, 4.2, 8.2), (5.6, 6.5, 8.6), chip, {'south': chip_face(2.6, 2.3)}, bevel=6)
        m.box((6, 4.2, 8.2), (8.6, 6.5, 8.6), chip, {'south': chip_face(2.6, 2.3)}, bevel=6)
        m.box((9.8, 4.5, 8.2), (10.8, 5.5, 9), '#2b4e9e', bevel=6)
        m.box((11.8, 4.5, 8.2), (12.8, 5.5, 9), '#2b4e9e', bevel=6)
        m.box((9.3, 6.8, 8.2), (11.3, 7.5, 8.55), '#b9bcc4', bevel=6)
    return finish(m, family, 'cart')

# ---------------------------------------------------------------- handhelds (front = south)

def handheld(family, atlas):
    p = FAMILIES[family]; m = Model(atlas); body = p['body']
    def dpad(cx, cy, z, size, width, color, h=.5):
        m.box((cx - size / 2, cy - width / 2, z), (cx + size / 2, cy + width / 2, z + h), color, bevel=6)
        m.box((cx - width / 2, cy - size / 2, z), (cx + width / 2, cy + size / 2, z + h), color, bevel=6)
        m.box((cx - width / 2 + .1, cy - width / 2 + .1, z + h), (cx + width / 2 - .1, cy + width / 2 - .1, z + h + .05), tint(color, -20), bevel=0)
    def button(cx, cy, z, dia, color, h=.5):
        m.box((cx - dia / 2, cy - dia / 2, z), (cx + dia / 2, cy + dia / 2, z + h), color, {'south': (color, rect(0, 0, dia, dia, color, bevel=18))}, bevel=12)
    def pill(cx, cy, z, w, h, color):
        m.box((cx - w / 2, cy - h / 2, z), (cx + w / 2, cy + h / 2, z + .3), color, bevel=10)
    if family == 'gb':
        # DMG: tall warm-grey brick with cut lower-right corner, dark bezel with stripes, magenta A/B.
        bezel, screen = '#4a4a5a', '#8c9a5e'
        front = many(speaker(4.8, 10.6, 8.6, 13.6, 4), screws([(.4, .3), (8.6, .3), (.4, 13.3)]), rect(.6, 6.9, 1.4, 7.1, tint(body, -22)))
        m.box((3.5, 2, 6.8), (12.5, 15, 9.2), body, {
            'south': (body, front),
            'north': (body, many(rect(1.5, 7, 7.5, 12.5, tint(body, -10), bevel=-6), rect(1, .5, 8, 5.5, tint(body, -4), bevel=6), screws([(1.2, 6.5), (7.6, 6.5), (1.2, 12.6), (7.6, 12.6)]))),
            'up': (body, many(slot(1.5, .6, 7.5, 1.9), rect(.3, .8, 1.1, 1.6, tint(body, -30)))),
            'down': (body, many(rect(.8, .6, 1.8, 1.8, '#2b2b30'), rect(5.5, .7, 8.2, 1.7, '#2b2b30')))}, bevel=8)
        m.box((3.5, 1, 6.8), (11.5, 2, 9.2), body, bevel=8)
        m.box((11.5, 1.5, 6.8), (12, 2, 9.2), body, bevel=0)
        m.box((4.3, 8.6, 9.2), (11.7, 14.3, 9.35), bezel, {'south': (bezel, many(
            rect(0, .3, 7.4, .55, '#7a2a7c'), rect(0, .75, 7.4, 1, '#2f3c8f'), rect(0, 1.2, 7.4, 1.45, '#9b2d5f'),
            rect(.8, 2.6, 1.3, 2.75, '#9c9cae'), rect(1.2, 1.5, 3, 1.7, '#9c9cae')))}, bevel=4)
        m.box((6, 9.4, 9.35), (10.6, 13.6, 9.4), screen, {'south': '#screen'}, bevel=0)
        m.lamp((4.9, 11.4, 9.35), (5.3, 11.8, 9.42))
        dpad(5.6, 5.4, 9.2, 2.8, .9, '#242428')
        button(9.2, 4.9, 9.2, 1.2, p['accent']); button(10.9, 5.8, 9.2, 1.2, p['accent'])
        pill(6.7, 2.9, 9.2, 1.3, .5, '#6e707a'); pill(8.7, 2.9, 9.2, 1.3, .5, '#6e707a')
        m.box((4, 15, 7.5), (5.3, 15.5, 8.6), '#8e8c84', bevel=6)             # power switch
        m.box((12.5, 10, 7.6), (12.9, 11.8, 8.8), '#8e8c84', {'east': ('#8e8c84', grooves(0, 0, 1.2, 1.8, 3, '#8e8c84'))}, bevel=4)  # volume
        m.box((3.1, 10, 7.6), (3.5, 11.8, 8.8), '#8e8c84', {'west': ('#8e8c84', grooves(0, 0, 1.2, 1.8, 3, '#8e8c84'))}, bevel=4)   # contrast
    elif family == 'gbc':
        # Teal shell, smaller bezel with the LED to the left of the glass, grape A/B, IR window on top.
        bezel, screen = '#2d2d35', '#8c9a5e'
        front = many(speaker(4.8, 10, 8.6, 13, 4), screws([(.4, .3), (8.6, .3)]), rect(.4, 7.1, 1.3, 7.3, tint(body, -30)))
        m.box((3.5, 2.5, 6.9), (12.5, 15, 9.1), body, {
            'south': (body, front),
            'north': (body, many(rect(1.5, 7, 7.5, 12, tint(body, -10), bevel=-6), rect(1, .5, 8, 5.5, tint(body, -4), bevel=6), screws([(1.2, 6.5), (7.6, 6.5)]))),
            'up': (body, many(slot(1.5, .5, 7.5, 1.7), rect(3.7, .6, 5.3, 1.1, '#3a1d2a'))),
            'down': (body, rect(.8, .6, 1.8, 1.8, '#2b2b30'))}, bevel=8)
        m.box((4, 1.5, 6.9), (12, 2.5, 9.1), body, bevel=8)
        m.box((4.2, 9, 9.1), (11.8, 14.4, 9.25), bezel, {'south': (bezel, many(rect(.7, 2.3, 1.2, 2.5, '#8a8a99'), rect(1.1, 5, 3.1, 5.2, '#8a8a99')))}, bevel=4)
        m.box((5.9, 9.6, 9.25), (10.9, 13.9, 9.3), screen, {'south': '#screen'}, bevel=0)
        m.lamp((4.8, 11.8, 9.25), (5.2, 12.2, 9.32))
        dpad(5.6, 6.2, 9.1, 2.7, .9, '#1d1d22')
        button(9.2, 5.6, 9.1, 1.2, p['accent']); button(10.9, 6.5, 9.1, 1.2, p['accent'])
        pill(6.7, 3.6, 9.1, 1.3, .5, '#3b3b47'); pill(8.7, 3.6, 9.1, 1.3, .5, '#3b3b47')
        m.box((4, 15, 7.6), (5.3, 15.5, 8.6), tint(body, -30), bevel=6)
        m.box((12.5, 10, 7.7), (12.9, 11.6, 8.7), tint(body, -30), {'east': (tint(body, -30), grooves(0, 0, 1, 1.6, 3, tint(body, -30)))}, bevel=4)
    elif family == 'gba':
        # Landscape indigo shell: thick hand grips, black bezel, L/R shoulders, D-pad left, A/B right.
        bezel, screen, grip = p['dark'], '#8c9a5e', tint(body, -8)
        m.box((4, 4.3, 7.1), (12, 12, 9), body, {
            'south': (body, many(holes(5, 7.1, 3, 2, .7), rect(.6, 7.1, 2, 7.3, tint(body, -22)))),
            'north': (body, many(slot(1.5, .5, 6.5, 1.5, '#5a5a66'), screws([(.5, 6.9), (7.5, 6.9)]))),
            'up': (body, rect(.5, .6, 7.5, 1.3, tint(body, -25)))}, bevel=8)
        for x in (1, 11.5):
            m.box((x, 4, 6.8), (x + 3.5, 11.5, 9.2), grip, {
                'south': (grip, many(holes(1.9, 6, 3, 2, .6)) if x > 8 else many()),
                'north': (grip, rect(.6, 1, 2.9, 6.5, tint(grip, -8), bevel=-6))}, bevel=10)
            m.box((x + .5, 11.5, 7.2), (x + 3, 12.1, 8.4), '#4b4190', {'up': ('#4b4190', grooves(.3, 0, 2.2, 1.2, 1, '#4b4190'))}, bevel=6)
        m.box((4.5, 5.6, 9), (11.5, 11.6, 9.15), bezel, {'south': (bezel, many(rect(1, 5.4, 3.6, 5.6, '#777788'), rect(.3, 5.3, .7, 5.7, '#777788')))}, bevel=4)
        m.box((5.1, 6.6, 9.15), (10.9, 10.6, 9.2), screen, {'south': '#screen'}, bevel=0)
        m.lamp((4.8, 5.9, 9.15), (5.15, 6.25, 9.22))
        dpad(2.8, 9.2, 9.2, 2.4, .8, '#2a2740')
        button(12.6, 8.5, 9.2, 1.1, p['accent']); button(13.9, 9.3, 9.2, 1.1, p['accent'])
        pill(2.8, 6.6, 9.2, 1.2, .4, '#8d85c4'); pill(2.8, 5.8, 9.2, 1.2, .4, '#8d85c4')
        m.box((15, 9, 7.4), (15.3, 10.6, 8.6), tint(body, -30), bevel=4)   # volume wheel
    else:
        # NES handheld: grey shell with the controller's black face plate, red A/B and red pin stripes.
        plate, bezel, screen = '#2d2d31', '#141417', '#8c9a5e'
        m.box((1, 3.5, 7), (15, 12.5, 9), body, {
            'south': (body, screws([(.4, .3), (13.6, .3), (.4, 8.7), (13.6, 8.7)])),
            'north': (body, many(slot(3.5, .6, 10.5, 1.6, '#6a6a70'), rect(4, 3.5, 10, 8.5, tint(body, -10), bevel=-6))),
            'up': (body, rect(1, .5, 13, 1.5, tint(body, -25))),
            'down': (body, rect(5, .5, 9, 1.5, tint(body, -30)))}, bevel=8)
        m.box((1.5, 4, 9), (14.5, 12, 9.1), plate, {'south': (plate, many(
            rect(2, 3, 2.3, 5, p['accent']), rect(2.7, 3, 3, 5, p['accent']), rect(10, 3, 10.3, 5, p['accent']), rect(10.7, 3, 11, 5, p['accent']),
            rect(3.2, 7.2, 9.8, 7.5, p['accent']), rect(.4, 5.6, 2.9, 7.6, '#1d1d20', bevel=-8), rect(10.1, 2.3, 12.6, 3.7, '#1d1d20', bevel=-8)))}, bevel=3)
        m.box((4.5, 5.3, 9.1), (11.5, 11.3, 9.25), bezel, {'south': (bezel, rect(.6, 5.4, 3, 5.6, '#777788'))}, bevel=3)
        m.box((5.2, 6, 9.25), (10.8, 10.6, 9.3), screen, {'south': '#screen'}, bevel=0)
        m.lamp((2.1, 11.2, 9.1), (2.5, 11.6, 9.17))
        dpad(2.9, 8.4, 9.1, 2.5, .8, '#1d1d20')
        button(12.4, 7.6, 9.1, 1.2, p['accent']); button(13.8, 7.6, 9.1, 1.2, p['accent'])
        pill(6.7, 4.6, 9.1, 1.4, .45, '#1d1d20'); pill(8.7, 4.6, 9.1, 1.4, .45, '#1d1d20')
    r = finish(m, family, 'handheld'); r['textures']['screen'] = 'retrocraft:block/console/lcd_off'
    return r

# ---------------------------------------------------------------- docked consoles (front = north)

def console(family, atlas):
    p = FAMILIES[family]; m = Model(atlas); body, dark = p['body'], p['dark']
    def feet(xs, zs, y=0, h=.6):
        for x in xs:
            for z in zs: m.box((x, y, z), (x + 1.5, y + h, z + 1.5), '#2a2a2e', bevel=0)
    if family == 'nes':
        # Light grey deck, dark lower fascia with the front loading door and two round ports, red keys.
        feet((2, 12.5), (3.5, 11))
        m.box((1, .6, 2.5), (15, 6, 13.5), body, {
            'up': (body, many(vents(1, 6.5, 5, 10.5, 9, '#1e1f22', True), rect(0, 1.6, 14, 1.75, tint(body, -35)), rect(6.5, 6.2, 13.5, 10.5, tint(body, -6), bevel=-5))),
            'north': (body, many(rect(1.2, .6, 6, 1.3, '#1d1d20'), rect(1.2, 1.3, 6, 1.5, p['accent']), rect(1.2, 1.9, 4.5, 2.1, '#1d1d20'))),
            'south': (body, many(rect(1, 2, 2.5, 4.5, '#1d1d20'), rect(3, 2.5, 4, 3.5, '#d7c45a'), rect(4.5, 2.5, 5.5, 3.5, '#d7d7d7'), rect(6, 2.5, 7, 3.5, '#c84a4a'), vents(9, 1, 13, 4.5, 6, '#2a2a2e'))),
            'east': (body, vents(2, 1, 9, 4.5, 8, '#2a2a2e')), 'west': (body, vents(2, 1, 9, 4.5, 8, '#2a2a2e'))}, bevel=8)
        fascia = '#4a4c51'
        m.box((1, .6, 2.2), (15, 3.3, 2.5), fascia, {'north': (fascia, ports(.6, .6, 3.4, 2.1, 2, 'round'))}, bevel=6)
        m.box((4.2, .9, 1.9), (10.8, 3.1, 2.2), fascia, {'north': (fascia, many(rect(0, .35, 6.6, .55, tint(fascia, -40)), rect(2.4, 1.3, 4.2, 1.5, tint(fascia, 30))))}, bevel=8)
        for x in (11.3, 13.1):
            m.box((x, 1.3, 1.95), (x + 1.3, 2.3, 2.2), p['accent'], {'north': (p['accent'], rect(0, .2, 1.3, .35, tint(p['accent'], 45)))}, bevel=8)
        m.lamp((12.75, 1.5, 2.1), (12.95, 2, 2.25))
    elif family == 'snes':
        # Boxy US deck: raised cartridge block at the back, purple sliders left, eject key right, ports in a dark bay.
        feet((2, 12.5), (3.5, 11))
        lav, purple = '#8f86c6', '#4f3f91'
        m.box((1, .6, 2.5), (15, 4.5, 13.5), body, {
            'up': (body, many(rect(1.3, 1.1, 2.8, 2.9, '#6d6c76'), rect(3.4, 1.1, 4.9, 2.9, '#6d6c76'), rect(1, 3.6, 7, 3.8, tint(body, -30)), rect(10.5, 1.2, 13, 2.9, tint(body, -8), bevel=-5))),
            'north': (body, many(rect(1, .4, 7.5, 3.6, '#5a5964', bevel=-6), ports(1.5, 1.2, 7, 2.9, 2, 'snes'))),
            'south': (body, many(rect(1, 1, 2.5, 3.2, '#1d1d20'), rect(3, 1.5, 4, 2.5, '#d7c45a'), rect(4.5, 1.5, 5.5, 2.5, '#d7d7d7'), rect(6, 1.5, 7, 2.5, '#c84a4a'), vents(9, .8, 13.5, 3.4, 7, '#2a2a2e')))}, bevel=8)
        m.box((2, 4.5, 7), (14, 5.9, 13), body, {
            'up': (body, many(slot(2.5, 1.8, 9.5, 3.4, '#9a99a2'), vents(.4, 1, 1.8, 5.5, 3, '#2a2a2e'), vents(10.2, 1, 11.6, 5.5, 3, '#2a2a2e'))),
            'north': (body, rect(0, 1.1, 12, 1.25, tint(body, -30)))}, bevel=8)
        m.box((12.2, 5.9, 8.4), (13.6, 6.3, 10.2), purple, bevel=8)
        m.box((1.5, 4.5, 3.4), (2.8, 5.1, 4.4), lav, bevel=8); m.box((3.6, 4.5, 3.4), (4.9, 5.1, 4.4), lav, bevel=8)
        m.lamp((5.6, 4.5, 3.6), (6, 4.7, 4))
    elif family in ('megadrive', 'genesis'):
        # Black model-1 deck: ridged disc top-left, cartridge bay right, volume slider and headphone jack front-left.
        feet((2, 12.5), (3.5, 11))
        ac = p['accent']
        m.box((1, .6, 2.5), (15, 4.4, 13.5), body, {
            'up': (body, many(ring(4.2, 6.4, 3.5, 1.5, '#3a3c42', ac if family == 'genesis' else None), rect(2.7, 6.3, 5.7, 6.5, ac if family == 'megadrive' else tint(ac, -60)),
                               vents(.4, 9.5, 6.8, 10.6, 8, '#101216'), rect(1, 1, 1.5, 3, tint(body, 30)), rect(12.6, 1.3, 13.4, 2.1, tint(body, 25), bevel=-6))),
            'north': (body, many(ports(1.3, 1, 7.7, 2.9, 2, 'de9'), rect(10, 1.1, 12.5, 2.7, '#15161a', bevel=-6), rect(12.9, 1.6, 13.5, 2.2, '#0f1013'))),
            'south': (body, many(rect(1, 1, 3, 3, '#15161a'), rect(4, 1.4, 5, 2.4, '#d7c45a'), rect(5.5, 1.4, 6.5, 2.4, '#d7d7d7'), vents(8, .8, 13.5, 3.4, 8, '#101216'))),
            'east': (body, vents(1.5, 1, 9.5, 3.4, 8, '#101216'))}, bevel=8)
        m.box((7.5, 4.4, 6.5), (14.5, 5, 11.5), body, {'up': (body, slot(1, 1, 6, 4, '#4e5056', '#0c0d10'))}, bevel=8)
        m.box((1, 4.4, 3.2), (2.2, 5, 4.8), tint(body, 20), bevel=6)
        m.box((1.5, 4.4, 5.5), (2.2, 4.8, 8.5), '#1a1b1f', bevel=0); m.box((1.6, 4.8, 6.4), (2.1, 5.1, 7.2), '#6a6c72', bevel=6)
        m.lamp((3.5, 4.4, 3.5), (4, 4.7, 4))
    elif family == 'n64':
        # Charcoal deck with four raised shoulders around a central dome, slot on top, four ports in front.
        feet((2, 12.5), (3.5, 11))
        top = tint(body, 6)
        m.box((1, .6, 2.5), (15, 3.4, 13.5), body, {
            'north': (body, many(ports(1.2, 1, 12.8, 2.6, 4, 'n64'), rect(2.8, 2.6, 11.2, 2.8, tint(body, -20)))),
            'south': (body, many(rect(1, .8, 3, 2.6, '#15161a'), rect(4, 1, 5, 2, '#d7c45a'), rect(5.5, 1, 6.5, 2, '#d7d7d7'), vents(8, .6, 13.5, 2.4, 8, '#101216'))),
            'east': (body, vents(1.5, .6, 9.5, 2.4, 8, '#101216')), 'west': (body, vents(1.5, .6, 9.5, 2.4, 8, '#101216'))}, bevel=8)
        for x, z in [(1, 2.5), (11.5, 2.5), (1, 10), (11.5, 10)]:
            m.box((x, 3.4, z), (x + 3.5, 4.4, z + 3.5), top, {'up': (top, vents(.5, .5, 3, 3, 4, '#1e1f23', z > 5))}, bevel=8)
        m.box((3, 3.4, 4.5), (13, 5.3, 12.5), top, {
            'up': (top, many(slot(1.5, 3.8, 8.5, 5.6, '#6d7077', '#0c0d10'), rect(3, 1, 7, 3, tint(top, -6), bevel=-6))),
            'north': (top, rect(1, .6, 9, .8, tint(top, -25)))}, bevel=8)
        m.box((1.5, 4.4, 5.5), (3, 4.9, 7.5), '#d9d9db', bevel=8)                # power switch
        m.box((12.5, 4.4, 5.8), (13.6, 4.8, 6.9), tint(body, 30), bevel=8)       # reset
        m.lamp((2, 4.4, 8), (2.4, 4.7, 8.4))
    else:
        # Upright arcade cabinet, two and a half blocks tall and a little wider than a block: coin door, control panel with
        # stick and six buttons, the CRT (its glass is the live emulator frame drawn by DockedConsoleRenderer), speaker
        # grille and marquee. Everything above y=32 is emitted as the separate block/mame_console_top model (JSON limit).
        side, panel, accent, navy = '#2a2831', '#3b3845', p['accent'], '#1b1d3f'
        def side_art(F):
            w, h = F.units
            F.rect(1, 2, 2, h - 2, '#c04f8a'); F.rect(2, 4, 3, h - 4, '#2b7fc4'); F.rect(3, 6, 4, h - 6, accent)
            F.rect(4.5, h * .55, w - 1.5, h * .55 + .5, '#c04f8a'); F.rect(4.5, h * .55 + 1, w - 2.5, h * .55 + 1.5, '#2b7fc4')
        def coin_door(F):
            F.rect(2, 1.5, 16, 9.5, '#34323c', bevel=-8); F.rect(2.5, 2, 15.5, 2.5, tint(side, 20))
            for u in (5.4, 10.4): F.rect(u, 3.5, u + 1.6, 4, '#0c0c10'); F.rect(u - .2, 5, u + 1.8, 5.4, tint(accent, -30)); F.rect(u + .3, 2.8, u + 1.3, 3.3, '#c83a3a')
            F.rect(7, 6.5, 11, 8.5, '#1b1b20'); F.rect(7.4, 6.9, 10.6, 7.4, '#0c0c10')
        def grille(F):
            for r in range(3):
                for c in range(26): F.dot(1.2 + c * .6, .5 + r * .6, '#040406')
        m.box((-1, 0, 3), (17, 32, 16), side, {'east': (side, side_art), 'west': (side, side_art), 'north': (side, rect(1, 3, 17, 18, '#0c0c10')),
                                                'south': (side, many(vents(3, 3, 15, 8, 7, '#101216', True), vents(3, 22, 15, 27, 7, '#101216', True), rect(7.5, 29.5, 10.5, 31.5, '#0c0c10')))}, bevel=4)
        m.box((-1, 0, 1), (17, 11, 3), side, {'north': (side, coin_door)}, bevel=6)
        m.box((-1, 11, -1), (17, 14, 7), panel, {'up': (panel, rect(0, 0, 18, .3, tint(panel, 30))),
                                                 'north': (panel, many(rect(.5, .8, 17.5, 1.3, '#c04f8a'), rect(.5, 1.8, 17.5, 2.3, '#2b7fc4')))}, bevel=8)
        m.box((2.6, 14, 2.6), (3.4, 16, 3.4), '#1b1b20', bevel=0); m.box((2.2, 16, 2.2), (3.8, 17.6, 3.8), '#c83a3a', bevel=12)  # joystick
        for i, c in enumerate(['#c83a3a', '#f0b53a', '#3a9ad0', '#3ab064', '#e6e6e6', '#c04f8a']):
            x = 7.5 + (i % 3) * 2.4; z = 1 + (i // 3) * 2.6
            m.box((x, 14, z), (x + 1.6, 14.6, z + 1.6), c, bevel=14)
        m.box((-1, 14, 2.5), (17, 29, 3), '#0c0c10', {'north': ('#0c0c10', rect(1, 1, 17, 14, '#15161c', bevel=-10))}, bevel=0)
        m.box((1.5, 16, 3), (14.5, 26, 3.2), '#101216', {'north': '#screen'}, bevel=0)
        m.box((-.5, 26.5, 2.8), (16.5, 28.5, 3), '#1a1a20', {'north': ('#1a1a20', grille)}, bevel=0)
        m.box((-1, 29, 1), (17, 32, 3), navy, {'north': (navy, many(rect(.4, .3, 17.6, 2.7, navy, bevel=-14), rect(.4, .3, 17.6, .8, '#2b7fc4'), rect(.4, 2.3, 17.6, 2.8, '#c04f8a'),
                                                                  text('RETRO', 4.2, .5, accent), rect(14.2, .9, 15.4, 2.1, '#3ab064'), rect(14.5, .5, 15.1, .9, '#3ab064'), rect(14.5, 2.1, 15.1, 2.5, '#3ab064'))),
                                               'down': (navy, rect(.5, .2, 17.5, 1.8, tint(side, -10)))}, bevel=6)
        # Roof: overhanging cap with the accent strip, then the enclosed top of the cabinet.
        m.box((-1.5, 32, .5), (17.5, 34.5, 16.5), tint(side, -4), {'north': (tint(side, -4), rect(.5, .5, 18.5, 2, accent))}, bevel=6)
        m.box((-1, 34.5, 2), (17, 40, 16), side, {'east': (side, rect(1, 1, 13, 4.5, tint(side, -10), bevel=-6)), 'west': (side, rect(1, 1, 13, 4.5, tint(side, -10), bevel=-6)),
                                                  'north': (side, many(rect(1, 1.5, 17, 2, '#c04f8a'), rect(1, 2.5, 17, 3, '#2b7fc4'))), 'up': (side, vents(4, 4, 14, 10, 5, '#101216'))}, bevel=6)
        m.lamp((7, 2.8, .95), (7.5, 3.3, 1.05))
    if family == 'mame':
        top = Model(atlas); top.elements = [e for e in m.elements if e['from'][1] >= 32]; m.elements = [e for e in m.elements if e['from'][1] < 32]
        for e in top.elements: e['from'][1] -= 32; e['to'][1] -= 32
        r = finish(m, family, 'block'); r['textures']['screen'] = 'retrocraft:block/console/crt_off'
        return r, finish(top, family, 'block')
    return finish(m, family, 'block')

# ---------------------------------------------------------------- shared small textures

def small_textures():
    out = ASSETS / 'textures/block/console'; out.parent.mkdir(parents=True, exist_ok=True)
    for name, color in [('power_on', '#d9453a'), ('power_off', '#5a2a28')]:
        im = Image.new('RGB', (4, 4), rgb(color)); d = ImageDraw.Draw(im)
        d.point((0, 0), fill=tint(color, 45)); d.point((3, 3), fill=tint(color, -35)); im.save(out / f'{name}.png')
    gb = ['#0f380f', '#306230', '#8bac0f', '#9bbc0f']
    im = Image.new('RGB', (8, 8), rgb('#8c9a5e')); d = ImageDraw.Draw(im)
    for y in range(0, 8, 2): d.line((0, y, 7, y), fill=rgb('#86945a'))
    d.line((0, 0, 7, 0), fill=rgb('#a0ad6e')); im.save(out / 'lcd_off.png')
    im = Image.new('RGB', (8, 8), rgb(gb[3])); d = ImageDraw.Draw(im)
    d.rectangle((0, 6, 7, 7), fill=rgb(gb[1])); d.line((0, 6, 7, 6), fill=rgb(gb[2]))
    d.rectangle((4, 3, 6, 3), fill=rgb(gb[1]))
    d.rectangle((1, 4, 2, 5), fill=rgb(gb[0])); d.point((1, 3), fill=rgb(gb[0]))
    d.point((6, 1), fill=rgb(gb[2])); d.point((5, 1), fill=rgb(gb[2])); d.point((3, 0), fill=rgb(gb[2]))
    im.save(out / 'lcd_on.png')
    im = Image.new('RGB', (16, 16), rgb('#101218')); d = ImageDraw.Draw(im)
    for y in range(0, 16, 2): d.line((0, y, 15, y), fill=rgb('#0c0e13'))
    d.line((1, 1, 6, 1), fill=rgb('#2a2f3d')); d.line((1, 1, 1, 5), fill=rgb('#2a2f3d')); im.save(out / 'crt_off.png')
    im = Image.new('RGB', (16, 16), rgb('#101a4a')); d = ImageDraw.Draw(im)
    rnd = random.Random(7)
    for _ in range(14): d.point((rnd.randrange(16), rnd.randrange(2, 13)), fill=rgb(rnd.choice(['#e8ecff', '#8fa3ff'])))
    d.rectangle((0, 0, 15, 0), fill=rgb('#e6e6ee')); d.rectangle((0, 14, 15, 15), fill=rgb('#2d3a7a'))
    for x in (2, 6, 10): d.rectangle((x, 3, x + 2, 4), fill=rgb('#f0b53a')); d.point((x + 1, 5), fill=rgb('#f0b53a'))
    d.rectangle((7, 11, 9, 12), fill=rgb('#e6e6ee')); d.point((8, 10), fill=rgb('#c83a3a')); d.point((8, 8), fill=rgb('#3ab0ff'))
    for y in range(0, 16, 2): d.line((0, y, 15, y), fill=None)
    im.save(out / 'crt_on.png')

# ---------------------------------------------------------------- televisions (front = north; screen rectangles mirrored in TvBlock.Size)

TVS = {
    # name: (atlas size, footprint x0,x1, height, depth z0,z1, screen x0,x1,y0,y1, screen z)
    'tv_small':  dict(size=128, body=(1, 15, 0, 13, 3, 13), screen=(2.5, 13.5, 3, 11.25), z=2.9),
    'tv_medium': dict(size=256, body=(-8, 24, 0, 24, 2, 14), screen=(-5, 21, 3.25, 22.75), z=1.9),
    'tv_large':  dict(size=256, body=(-16, 32, 0, 32, 0, 16), screen=(-7, 23, 4.5, 27), z=-.1),
}

def television(name, atlas):
    t = TVS[name]; m = Model(atlas); x0, x1, y0, y1, z0, z1 = t['body']; sx0, sx1, sy0, sy1 = t['screen']; sz = t['z']
    if name == 'tv_small':
        # Portable set: warm grey plastic, carry handle, channel dial and a speaker grille beside the tube.
        shell, dark = '#c9c3b3', '#2b2b30'
        m.box((x0, 1, z0), (x1, y1, z1), shell, {'north': (shell, rect(.5, .5, 13.5, 11.5, dark, bevel=-8)),
                                                 'south': (shell, many(vents(2, 2, 12, 9, 8, '#8f8a7c', True), rect(3, 10, 5, 11.5, '#1d1d20'))),
                                                 'east': (shell, vents(2, 2, 8, 9, 5, '#8f8a7c')), 'west': (shell, vents(2, 2, 8, 9, 5, '#8f8a7c'))}, bevel=8)
        m.box((x0 + 1, y1, 5), (x1 - 1, y1 + .6, 6), '#5c5a58', bevel=6); m.box((x0 + 1, y1, 10), (x1 - 1, y1 + .6, 11), '#5c5a58', bevel=6)
        m.box((x0 + 1, y1 + .6, 5), (x0 + 2, y1 + 2.5, 11), '#5c5a58', bevel=6); m.box((x1 - 2, y1 + .6, 5), (x1 - 1, y1 + 2.5, 11), '#5c5a58', bevel=6)
        m.box((x0 + 1, y1 + 2.5, 5), (x1 - 1, y1 + 3, 11), '#5c5a58', bevel=6)
        for x in (x0 + 1, x1 - 2.5): m.box((x, 0, z0 + 1), (x + 1.5, 1, z1 - 1), dark, bevel=0)
        m.box((sx0 - .7, sy0 - .7, 2.7), (sx1 + .7, sy1 + .7, 3), dark, {'north': (dark, rect(.3, .3, sx1 - sx0 + 1.1, sy1 - sy0 + 1.1, '#15161c', bevel=-8))}, bevel=0)
        m.box((sx0, sy0, sz), (sx1, sy1, 3), '#101216', {'north': '#screen'}, bevel=0)
        m.box((x0 + 1, 1.3, 2.6), (x1 - 1, 2.6, 3), shell, {'north': (shell, many(holes(1, .3, 10, 2, .5, '#6a665c'), rect(9.5, .2, 10.7, 1.1, '#3b3b42', bevel=-8), rect(11.5, .3, 12.3, 1, '#3b3b42', bevel=-8)))}, bevel=4)
        m.lamp((x1 - 2.2, 1.6, 2.55), (x1 - 1.8, 2, 2.65))
    elif name == 'tv_medium':
        # Nineties black CRT, two blocks wide: curved-looking bezel, silver control strip and stereo grilles.
        shell, dark, silver = '#26262b', '#0f0f13', '#8a8a92'
        m.box((x0, 1, z0 + 1), (x1, y1, z1), shell, {'north': (shell, rect(1, 1, 31, 20, dark, bevel=-10)),
                                                     'south': (shell, many(vents(4, 3, 28, 12, 14, '#3a3a40', True), rect(6, 16, 10, 19, '#1d1d20'), rect(12, 16.5, 20, 18.5, '#3a3a40'))),
                                                     'east': (shell, vents(2, 3, 10, 14, 7, '#3a3a40')), 'west': (shell, vents(2, 3, 10, 14, 7, '#3a3a40'))}, bevel=8)
        for x in (x0 + 2, x1 - 4): m.box((x, 0, z0 + 2), (x + 2, 1, z1 - 2), dark, bevel=0)
        m.box((sx0 - 1.5, sy0 - 1.5, z0 + .6), (sx1 + 1.5, sy1 + 1.5, z0 + 1), dark, {'north': (dark, rect(.5, .5, sx1 - sx0 + 2.5, sy1 - sy0 + 2.5, '#15161c', bevel=-10))}, bevel=0)
        m.box((sx0, sy0, sz), (sx1, sy1, z0 + 1), '#101216', {'north': '#screen'}, bevel=0)
        m.box((x0 + 1, 1, z0 + .4), (x1 - 1, 3, z0 + 1), silver, {'north': (silver, many(holes(1, .4, 8, 3, .6, '#3b3b42'), holes(24, .4, 8, 3, .6, '#3b3b42'), rect(10, .5, 14, 1.5, '#1d1d20', bevel=-6), rect(16, .6, 17, 1.4, '#1d1d20'), rect(18, .6, 19, 1.4, '#1d1d20'), rect(20, .6, 21, 1.4, '#1d1d20')))}, bevel=4)
        m.lamp((x1 - 3.5, 1.7, z0 + .35), (x1 - 3, 2.2, z0 + .45))
    else:
        # Seventies console television, three blocks wide and two tall: walnut cabinet, cloth speaker panels, dial column.
        wood, grain, cloth, dark = '#6b4a2f', '#4f3520', '#a89170', '#15161a'
        def planks(F):
            w, h = F.units
            for i in range(int(h // 2)): F.rect(0, i * 2 + .9, w, i * 2 + 1.1, grain)
        m.box((x0, 2, z0 + 1), (x1, y1, z1), wood, {'north': (wood, rect(1, 1, 47, 29, dark, bevel=-8)), 'south': (wood, many(planks, rect(6, 22, 42, 28, '#2b2b30', bevel=-6), vents(8, 23, 40, 27, 10, '#101216'))),
                                                    'east': (wood, planks), 'west': (wood, planks), 'up': (wood, planks)}, bevel=8)
        for x in (x0 + 1, x1 - 3): m.box((x, 0, z0 + 2), (x + 2, 2, z0 + 4), grain, bevel=0); m.box((x, 0, z1 - 4), (x + 2, 2, z1 - 2), grain, bevel=0)
        m.box((sx0 - 2, sy0 - 2, z0 + .5), (sx1 + 2, sy1 + 2, z0 + 1), dark, {'north': (dark, rect(.7, .7, sx1 - sx0 + 3.3, sy1 - sy0 + 3.3, '#1c1d24', bevel=-10))}, bevel=0)
        m.box((sx0, sy0, sz), (sx1, sy1, z0 + 1), '#101216', {'north': '#screen'}, bevel=0)
        w = (sx0 - 2.5) - (x0 + 1)
        m.box((x0 + 1, 3, z0 + .7), (x0 + 1 + w, y1 - 1, z0 + 1), cloth, {'north': (cloth, holes(.5, .5, int(w * 2) - 1, int((y1 - 4) * 2) - 1, .5, tint(cloth, -35)))}, bevel=4)
        m.box((sx1 + 2.5, 3, z0 + .7), (x1 - 1, y1 - 1, z0 + 1), grain, {'north': (grain, many(rect(1, 1, w - .5, 6, '#2b2b30', bevel=-6), rect(1, 7.5, w - .5, 12.5, '#2b2b30', bevel=-6), rect(1, 18, w - .5, y1 - 6, cloth)))}, bevel=4)
        for y in (y1 - 7.5, y1 - 14): m.box((sx1 + 3.5, y, z0 + .2), (sx1 + 3.5 + w - 2.5, y + w - 2.5, z0 + .7), '#3b3b42', {'north': ('#3b3b42', rect((w - 2.5) / 2 - .2, .3, (w - 2.5) / 2 + .2, (w - 2.5) / 2, '#c9c3b3'))}, bevel=8)
        m.lamp((sx1 + 4.2, 15, z0 + .45), (sx1 + 4.8, 15.6, z0 + .55))
    r = finish(m, name, 'block'); r['textures']['screen'] = 'retrocraft:block/console/crt_off'
    scale = {'tv_small': .95, 'tv_medium': .5, 'tv_large': .33}[name]
    r['display'] = {
        'gui': {'rotation': [25, 225, 0], 'translation': [0, 1 if name == 'tv_small' else -1, 0], 'scale': [scale] * 3},
        'ground': {'translation': [0, 3, 0], 'scale': [scale * .5] * 3},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0 if name == 'tv_small' else -3, 0], 'scale': [scale] * 3},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [scale * .375] * 3},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [scale * .375] * 3},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [scale * .4] * 3},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [scale * .4] * 3},
    }
    return r

def write(kind, name, data):
    path = ASSETS / kind / f'{name}.json'; path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def generate():
    small_textures()
    for family in FAMILIES:
        atlas = Atlas(family, 256 if family == 'mame' else SIZE)
        if family != 'genesis': write('models/item', family, cartridge(family, atlas))
        if family in ('nes', 'gb', 'gbc', 'gba'):
            m = handheld(family, atlas); m['overrides'] = [{'predicate': {'retrocraft:powered': 1}, 'model': f'retrocraft:item/{family}_handheld_on'}]
            write('models/item', family + '_handheld', m)
            write('models/item', family + '_handheld_on', {'parent': f'retrocraft:item/{family}_handheld', 'textures': {'screen': 'retrocraft:block/console/lcd_on', 'indicator': 'retrocraft:block/console/power_on'}})
        if family not in ('gb', 'gbc', 'gba'):
            name = family + '_console'; built = console(family, atlas)
            if family == 'mame': built, top = built; write('models/block', name + '_top', top)
            write('models/block', name, built)
            on = {'parent': f'retrocraft:block/{name}', 'textures': {'indicator': 'retrocraft:block/console/power_on'}}
            if family == 'mame': on['textures']['screen'] = 'retrocraft:block/console/crt_on'
            write('models/block', name + '_on', on)
            write('models/item', name, {'parent': f'retrocraft:block/{name}'})
            variants = {}
            for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
                for powered in [False, True]: variants[f'facing={facing},powered={str(powered).lower()}'] = {'model': f'retrocraft:block/{name}' + ('_on' if powered else ''), 'y': angle}
            write('blockstates', name, {'variants': variants})
        atlas.save()
        used = sum(r['h'] for r in atlas.rows)
        print(f'{family}: atlas rows use {used}/{atlas.size} texels')
    for name in TVS:
        atlas = Atlas(name, TVS[name]['size'])
        write('models/block', name, television(name, atlas))
        write('models/block', name + '_on', {'parent': f'retrocraft:block/{name}', 'textures': {'indicator': 'retrocraft:block/console/power_on', 'screen': 'retrocraft:block/console/crt_on'}})
        write('models/item', name, {'parent': f'retrocraft:block/{name}'})
        variants = {}
        for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            for powered in [False, True]: variants[f'facing={facing},powered={str(powered).lower()}'] = {'model': f'retrocraft:block/{name}' + ('_on' if powered else ''), 'y': angle}
        write('blockstates', name, {'variants': variants})
        write('../../data/retrocraft/loot_table/blocks', name, {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'retrocraft:{name}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        atlas.save()
        print(f'{name}: atlas rows use {sum(r["h"] for r in atlas.rows)}/{atlas.size} texels')
    print('Generated 8 cartridge models, 4 handhelds, 6 consoles, 3 televisions and 18 original textures.')

if __name__ == '__main__': generate()
