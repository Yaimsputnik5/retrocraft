#!/usr/bin/env python3
"""Generate compact background/overlay pairs for the registered handheld HUDs.

Native code/pixel assets, no external art inputs. Insets come from the client registrations;
64x64 source images are nine-sliced so neither border thickness nor corner pixels stretch.
"""
import argparse
import re
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageColor

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'common/src/main/resources/assets/retrocraft/textures/gui/console_hud'
# shell / recess / accent. Keep the artwork limited to the screen surround.
PALETTES = {
    'gb': ('#c8c4b8', '#4a4a5a', '#9b2d5f'),
    'gbc': ('#1f8d8a', '#2d2d35', '#4b3a7a'),
    'gba': ('#584c9e', '#1c1b23', '#8d85c4'),
    'gba_sp': ('#8193ad', '#282c34', '#c0c9d4'),
    'nds': ('#d4d6dc', '#45484f', '#8e969e'),
    '3ds': ('#258f9e', '#15191e', '#68c4cc'),
    'psp': ('#24262d', '#101216', '#bdc2c9'),
    'nes_handheld': ('#c7c5c0', '#141417', '#c1392b'),
}


def margins():
    source = (ROOT / 'common/src/main/java/com/sammwy/retrocraft/client/ui/hud/DefaultConsoleHuds.java').read_text()
    values = {name: tuple(map(int, border.split(','))) for name, border in re.findall(
        r'single\(\s*"(\w+)"\s*,.*?new HudInsets\(([^)]+)\)\)', source, re.S)}
    for name in re.findall(r'dual\(\s*"(\w+)"\s*,', source):
        values[name] = (14, 14, 14, 14)
    if values.keys() != PALETTES.keys():
        raise ValueError('HUD artwork and client registrations differ')
    return values


def shade(color, factor):
    return tuple(min(255, round(c * factor)) for c in ImageColor.getrgb(color))


def rounded(draw, box, color):
    x0,y0,x1,y1 = box
    draw.rectangle((x0+2,y0,x1-2,y1),fill=color)
    draw.rectangle((x0,y0+2,x1,y1-2),fill=color)
    draw.rectangle((x0+1,y0+1,x1-1,y1-1),fill=color)


def generate(output):
    for name, (left, top, right, bottom) in margins().items():
        shell, recess, accent = PALETTES[name]
        background = Image.new('RGBA',(64,64),(0,0,0,0))
        d = ImageDraw.Draw(background)
        rounded(d,(0,0,63,63),shell)
        d.line((3,1,60,1),fill=shade(shell,1.25))
        d.line((3,62,60,62),fill=shade(shell,.6))
        rounded(d,(4,4,59,59),recess)
        d.rectangle((left-1,top-1,64-right,64-bottom),fill='#080a0e')
        if name == 'gb':
            for y,color in ((5,'#7a2a7c'),(7,'#2f3c8f'),(9,'#9b2d5f')):
                d.line((left-4,y,58,y),fill=color)
        elif name in ('gba','nes_handheld'):
            d.line((left,64-bottom+3,63-right,64-bottom+3),fill=accent)
        overlay = Image.new('RGBA',(64,64),(0,0,0,0))
        d = ImageDraw.Draw(overlay)
        d.rectangle((left-1,top-1,64-right,64-bottom),outline='#080a0e')
        d.line((left,top-2,63-right,top-2),fill=shade(recess,1.35))
        # Always leave the entire video area transparent; gameplay stays crisp and unobscured.
        assert overlay.crop((left,top,64-right,64-bottom)).getbbox() is None
        folder = output / name
        folder.mkdir(parents=True,exist_ok=True)
        background.save(folder/'background.png')
        overlay.save(folder/'overlay.png')
    print('Generated 8 handheld HUDs, each with a background and transparent overlay.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            generate(temp)
            changed = [str(p.relative_to(temp)) for p in temp.rglob('*.png')
                       if not (OUTPUT/p.relative_to(temp)).exists()
                       or p.read_bytes() != (OUTPUT/p.relative_to(temp)).read_bytes()]
            if changed:
                raise SystemExit('Outdated HUD artwork:\n'+'\n'.join(changed))
        print('All HUD textures match; overlay windows are transparent.')
    else:
        generate(OUTPUT)


if __name__ == '__main__':
    main()
