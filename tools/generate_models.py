#!/usr/bin/env python3
"""Build Retrocraft's vanilla cuboid models and hand-drawn pixel texture atlases.

Development only: Python 3 + Pillow. Local art inputs; no downloads or build-time dependency.
Coordinates: home console front = north (-Z), handheld/media front = south (+Z).
One model unit uses four texels; markings stay crisp and the plastic stays deliberately plain.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

from arcade_art import THEMES, control_art, marquee, side_art

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/retrocraft"
DENSITY = 4
FACES = ("north", "south", "east", "west", "up", "down")
BLACK = "#14151a"
DARK = "#292b32"
MID = "#666970"
GRAY = "#aaa9a5"
LIGHT = "#d3d1c9"
WHITE = "#e8e7df"
RED = "#b42d42"
PURPLE = "#69618c"
GOLD = "#c4aa69"

# A small, original 3x5 pixel alphabet. No antialiasing or system font dependencies.
FONT = dict(
    zip(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-./:",
        [
            "010101111101101",
            "110101110101110",
            "011100100100011",
            "110101101101110",
            "111100110100111",
            "111100110100100",
            "011100101101011",
            "101101111101101",
            "111010010010111",
            "001001001101010",
            "101101110101101",
            "100100100100111",
            "101111111101101",
            "101111111111101",
            "010101101101010",
            "110101110100100",
            "010101101111011",
            "110101110101101",
            "011100010001110",
            "111010010010010",
            "101101101101111",
            "101101101101010",
            "101101111111101",
            "101101010101101",
            "101101010010010",
            "111001010100111",
            "111101101101111",
            "010110010010111",
            "110001010100111",
            "110001010001110",
            "101101111001001",
            "111100110001110",
            "011100110101010",
            "111001010010010",
            "010101010101010",
            "010101011001110",
            "000000111000000",
            "000000000000010",
            "001001010100100",
            "000010000010000",
        ],
    )
)


def text(im, label, xy=None, color=BLACK, scale=1):
    width = max(0, len(label) * 4 - 1) * scale
    x, y = (
        xy
        if xy is not None
        else ((im.width - width) // 2, (im.height - 5 * scale) // 2)
    )
    d = ImageDraw.Draw(im)
    for c in label.upper():
        glyph = FONT.get(c, "0" * 15)
        for j, bit in enumerate(glyph):
            if bit == "1":
                xx, yy = x + (j % 3) * scale, y + (j // 3) * scale
                d.rectangle((xx, yy, xx + scale - 1, yy + scale - 1), fill=color)
        x += 4 * scale


def tile(w, h, color):
    return Image.new(
        "RGBA", (max(1, round(w * DENSITY)), max(1, round(h * DENSITY))), color
    )


def label(w, h, title, color=LIGHT, ink=BLACK):
    im = tile(w, h, color)
    im = Image.new(
        "RGBA", (max(im.width, len(title) * 4 + 1), max(im.height, 7)), color
    )
    text(im, title, color=ink)
    return im


def vents(w, h, color, ink=BLACK, vertical=True, step=3):
    im = tile(w, h, color)
    d = ImageDraw.Draw(im)
    if vertical:
        for x in range(2, im.width - 1, step):
            d.line((x, 1, x, im.height - 2), fill=ink)
    else:
        for y in range(2, im.height - 1, step):
            d.line((1, y, im.width - 2, y), fill=ink)
    return im


def ps_logo(im, x, y, size=1):
    d = ImageDraw.Draw(im)
    # Red upright P over the yellow, green and blue S.
    d.rectangle((x + 4 * size, y, x + 5 * size, y + 8 * size), fill="#dc423c")
    d.rectangle((x + 6 * size, y + size, x + 8 * size, y + 4 * size), fill="#dc423c")
    d.line((x, y + 7 * size, x + 3 * size, y + 6 * size), fill="#e6be45", width=size)
    d.line((x, y + 8 * size, x + 3 * size, y + 9 * size), fill="#4caba0", width=size)
    d.line(
        (x + 6 * size, y + 9 * size, x + 11 * size, y + 7 * size),
        fill="#5081c1",
        width=size,
    )


def swirl(im, x, y, r, ink="#dc653c"):
    pts = []
    for i in range(65):
        a = i / 64 * math.pi * 4.5
        rr = r * (0.14 + 0.86 * i / 64)
        pts.append((round(x + math.cos(a) * rr), round(y + math.sin(a) * rr)))
    ImageDraw.Draw(im).line(pts, fill=ink, width=2)


class Model:
    def __init__(self, name, folder="block", texture=None):
        self.name, self.folder = name, folder
        self.texture = texture or name.removesuffix("_console").removesuffix(
            "_handheld"
        )
        self.atlas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        self.x = self.y = self.row = 1
        self.regions = {}
        self.elements = []
        self.textures = {
            "atlas": f"retrocraft:block/console/{self.texture}",
            "particle": "#atlas",
        }
        self.display = console_display()
        self.overrides = []

    def region(self, im):
        if isinstance(im, str):
            im = tile(1, 1, im)
        digest = hashlib.sha256(im.tobytes() + str(im.size).encode()).hexdigest()
        if digest in self.regions:
            return self.regions[digest]
        w, h = im.size
        if self.x + w + 1 >= 512:
            self.x, self.y, self.row = 1, self.y + self.row + 2, 0
        if self.y + h + 1 >= 512:
            raise ValueError(f"Atlas overflow: {self.name}")
        self.atlas.paste(im, (self.x, self.y))
        # Extrude one texel around each region to prevent atlas filtering bleed.
        self.atlas.paste(im.crop((0, 0, w, 1)), (self.x, self.y - 1))
        self.atlas.paste(im.crop((0, h - 1, w, h)), (self.x, self.y + h))
        self.atlas.paste(im.crop((0, 0, 1, h)), (self.x - 1, self.y))
        self.atlas.paste(im.crop((w - 1, 0, w, h)), (self.x + w, self.y))
        rect = [self.x, self.y, self.x + w, self.y + h]
        self.regions[digest] = rect
        self.x += w + 2
        self.row = max(self.row, h)
        return rect

    def box(self, name, a, b, color, paints=None, rotation=None, shade=True):
        faces = {}
        for face in FACES:
            paint = (paints or {}).get(face, color)
            if face == "up" and isinstance(paint, Image.Image):
                paint = paint.transpose(Image.Transpose.ROTATE_180)
            if (
                isinstance(paint, str)
                and paint.startswith("#")
                and not (
                    len(paint) in (7, 9)
                    and all(c in "0123456789abcdefABCDEF" for c in paint[1:])
                )
            ):
                faces[face] = {"texture": paint, "uv": [0, 0, 16, 16]}
            else:
                faces[face] = {"texture": "#atlas", "uv": self.region(paint)}
        e = {"name": name, "from": a, "to": b, "faces": faces}
        if rotation:
            e["rotation"] = rotation
        if not shade:
            e["shade"] = False
        self.elements.append(e)
        return e

    def top(self, name, x0, x1, z0, z1, y, paint, color=LIGHT):
        return self.box(name, [x0, y, z0], [x1, y + 0.04, z1], color, {"up": paint})

    def front(self, name, x0, x1, y0, y1, z, paint, color=LIGHT):
        return self.box(name, [x0, y0, z - 0.04], [x1, y1, z], color, {"north": paint})

    def south(self, name, x0, x1, y0, y1, z, paint, color=LIGHT):
        return self.box(name, [x0, y0, z], [x1, y1, z + 0.04], color, {"south": paint})

    def led(self, a, b, color="red"):
        self.textures["indicator"] = f"retrocraft:block/console/led_{color}_off"
        self.box("power indicator", a, b, "#indicator", shade=False)

    def round_top(self, name, cx, cz, radius, y0, y1, color, paint=None):
        # Five coarse strips give a stepped, recognizably round low-poly outline.
        widths = [0.6, 0.9, 1, 0.9, 0.6]
        region = (
            self.region(paint.transpose(Image.Transpose.ROTATE_180)) if paint else None
        )
        for i, width in enumerate(widths):
            z0 = cz - radius + i * 2 * radius / 5
            z1 = cz - radius + (i + 1) * 2 * radius / 5
            x0, x1 = cx - radius * width, cx + radius * width
            e = self.box(f"{name} {i + 1}", [x0, y0, z0], [x1, y1, z1], color)
            if region:
                u0, v0, u1, v1 = region
                e["faces"]["up"]["uv"] = [
                    u0 + (1 - width) / 2 * (u1 - u0),
                    v0 + i / 5 * (v1 - v0),
                    u0 + (1 + width) / 2 * (u1 - u0),
                    v0 + (i + 1) / 5 * (v1 - v0),
                ]

    def save(self):
        # Crop to a power-of-two square and convert pixel regions to Minecraft's 0..16 UV space.
        used = max(
            self.x,
            self.y + self.row + 1,
            max((r[2] for r in self.regions.values()), default=1),
        )
        size = 2 ** math.ceil(math.log2(used))
        self.atlas.crop((0, 0, size, size)).save(
            ASSETS / "textures/block/console" / f"{self.texture}.png"
        )
        for e in self.elements:
            e["from"] = [round(v, 4) for v in e["from"]]
            e["to"] = [round(v, 4) for v in e["to"]]
            for face in e["faces"].values():
                if face["texture"] == "#atlas":
                    face["uv"] = [round(v * 16 / size, 6) for v in face["uv"]]
        d = {
            "credit": "Original Retrocraft pixel artwork; tools/generate_models.py",
            "textures": self.textures,
            "gui_light": "side",
            "display": self.display,
            "elements": self.elements,
        }
        if self.folder == "block":
            d["parent"] = "minecraft:block/block"
        if self.overrides:
            d["overrides"] = self.overrides
        write_json(f"models/{self.folder}/{self.name}.json", d)


def write_json(path, d):
    (ASSETS / path).write_text(json.dumps(d, indent=2) + "\n")


def console_display(scale=0.85):
    return {
        "gui": {
            "rotation": [28, 225, 0],
            "translation": [0, 2, 0],
            "scale": [scale] * 3,
        },
        "ground": {"translation": [0, 3, 0], "scale": [0.5] * 3},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.8] * 3},
        "thirdperson_righthand": {
            "rotation": [75, 45, 0],
            "translation": [0, 2.5, 0],
            "scale": [0.5] * 3,
        },
        "thirdperson_lefthand": {
            "rotation": [75, 225, 0],
            "translation": [0, 2.5, 0],
            "scale": [0.5] * 3,
        },
        "firstperson_righthand": {
            "rotation": [0, 45, 0],
            "translation": [0, 2, 0],
            "scale": [0.65] * 3,
        },
        "firstperson_lefthand": {
            "rotation": [0, 225, 0],
            "translation": [0, 2, 0],
            "scale": [0.65] * 3,
        },
    }


def item_display():
    return {
        "gui": {"rotation": [8, -18, 0], "scale": [1] * 3},
        "ground": {"translation": [0, 3, 0], "scale": [0.5] * 3},
        "fixed": {"scale": [1] * 3},
        "thirdperson_righthand": {
            "rotation": [-10, 0, 0],
            "translation": [0, 3, 1],
            "scale": [0.65] * 3,
        },
        "thirdperson_lefthand": {
            "rotation": [-10, 0, 0],
            "translation": [0, 3, 1],
            "scale": [0.65] * 3,
        },
        "firstperson_righthand": {
            "rotation": [0, -15, 0],
            "translation": [0, 2, 0],
            "scale": [0.85] * 3,
        },
        "firstperson_lefthand": {
            "rotation": [0, 15, 0],
            "translation": [0, 2, 0],
            "scale": [0.85] * 3,
        },
    }


def feet(m, x0=2, x1=14, z0=3, z1=13):
    for x in (x0, x1 - 1):
        for z in (z0, z1 - 1):
            m.box("rubber foot", [x, 0, z], [x + 1, 0.5, z + 1], DARK)


def controller_port(m, x, y, z, w=1.7, h=0.85, rim=MID, pins=5):
    im = tile(w, h, rim)
    d = ImageDraw.Draw(im)
    d.rectangle((1, 1, im.width - 2, im.height - 2), fill=BLACK)
    for i in range(pins):
        px = 2 + round(i * max(0, im.width - 5) / max(1, pins - 1))
        d.point((px, max(1, im.height // 2)), fill=GOLD)
    m.front("controller socket", x, x + w, y, y + h, z, im, rim)


def rear_details(m):
    # Rear sockets are pixel art on existing shell faces, not tiny extra cuboids.
    config = {
        "nes_console": ("dark lower shell", "#888882"),
        "snes_console": ("lower shell", "#979598"),
        "megadrive_console": ("rear bevel step", DARK),
        "genesis_console": ("rear bevel step", DARK),
        "n64_console": ("back shoulder", DARK),
        "ps1_console": ("middle shell", "#b4b5b0"),
        "ps2_console": ("upper overhanging slab", "#24262e"),
        "dreamcast_console": ("back shell", WHITE),
        "gamecube_console": ("indigo shell", "#50477e"),
        "wii_console": ("white chassis", WHITE),
    }
    if m.name not in config:
        return
    name, color = config[m.name]
    e = next(e for e in m.elements if e["name"] == name)
    im = tile(e["to"][0] - e["from"][0], e["to"][1] - e["from"][1], color)
    d = ImageDraw.Draw(im)
    w, h = im.size
    if m.name == "wii_console":
        for y in range(4, 24, 3):
            d.line((2, y, w - 3, y), fill=MID)
        for y in (29, 34):
            d.rectangle((2, y, w - 3, y + 2), fill=BLACK)
        for y, ink in ((40, "#8f876e"), (45, "#a79d49")):
            d.rectangle((2, y, w - 3, y + 2), fill=ink)
    else:
        # Mains / adapter socket, AV multi-out and RF / serial connector.
        port_y = max(1, h - 5)
        d.rectangle((3, port_y, 8, min(h - 2, port_y + 2)), fill=BLACK)
        d.rectangle(
            (w // 2 - 3, port_y, w // 2 + 4, min(h - 2, port_y + 2)), fill=BLACK
        )
        d.line((w // 2 - 2, port_y + 1, w // 2 + 3, port_y + 1), fill=MID)
        d.rectangle((w - 8, port_y, w - 5, min(h - 2, port_y + 2)), fill=MID)
        if m.name == "n64_console":
            d.rectangle((w - 15, 0, w - 2, h - 1), fill=BLACK)
            d.line((w - 14, 1, w - 3, 1), fill="#3f4148")
        if m.name == "ps1_console":
            d.rectangle((w // 2 - 8, 1, w // 2 + 7, min(h - 2, 4)), fill=MID)
            d.line((w // 2 - 7, 2, w // 2 + 6, 2), fill=BLACK)
        if m.name in ("ps2_console", "gamecube_console"):
            for y in range(1, max(2, h - 6), 2):
                d.line((w // 3, y, 2 * w // 3, y), fill=BLACK)
    e["faces"]["south"] = {"texture": "#atlas", "uv": m.region(im)}


def powered(m, color="red"):
    rear_details(m)
    m.save()
    write_json(
        f"models/block/{m.name}_on.json",
        {
            "parent": f"retrocraft:block/{m.name}",
            "textures": {"indicator": f"retrocraft:block/console/led_{color}_on"},
        },
    )
    write_json(f"models/item/{m.name}.json", {"parent": f"retrocraft:block/{m.name}"})


def nes():
    m = Model("nes_console")
    feet(m)
    # NES-001 front loader: the lid occupies the left 2/3, the charcoal stripe the right.
    m.box("dark lower shell", [1, 0.5, 2], [15, 2.8, 14], "#888882")
    m.box("upper shell", [1, 2.8, 2.1], [15, 5.8, 14], LIGHT)
    m.box("front loading recess", [5.1, 3.05, 2.02], [14.3, 5.05, 2.15], BLACK)
    m.box("cartridge door", [5.25, 3.3, 1.96], [14.25, 5.65, 2.05], LIGHT)
    m.front(
        "red Nintendo wordmark",
        8.7,
        13.7,
        3.5,
        4.2,
        1.94,
        label(5, 0.7, "NINTENDO", LIGHT, "#ac3442"),
        LIGHT,
    )
    m.top("door hinge", 5.2, 14.3, 2.1, 6.8, 5.8, LIGHT)
    m.top("lid seam", 5.1, 14.3, 6.8, 6.88, 5.8, MID, MID)
    m.top(
        "charcoal top stripe",
        2,
        4.5,
        2.1,
        14,
        5.8,
        vents(2.5, 11.9, DARK, BLACK, False),
        DARK,
    )
    m.front("charcoal front stripe", 2, 4.5, 2.8, 5.8, 2.09, DARK, DARK)
    for x in (2.3, 3.5):
        controller_port(m, x, 1.1, 1.96, 0.85, 1.15, DARK, 3)
    for x in (9.6, 12.0):
        m.box("power / reset key", [x, 1.1, 1.81], [x + 1.7, 2.1, 2.02], LIGHT)
    m.led([13.95, 1.3, 1.79], [14.3, 1.8, 1.9])
    m.top(
        "back ventilation", 5.3, 14.3, 11, 13.5, 5.81, vents(9, 2.5, LIGHT, MID), LIGHT
    )
    m.front(
        "front power labels",
        9.5,
        13.8,
        2.15,
        2.7,
        1.98,
        label(4.3, 0.55, "P R", "#888882", DARK),
        "#888882",
    )
    powered(m)


def snes():
    m = Model("snes_console")
    feet(m)
    m.box("lower shell", [1, 0.5, 2.5], [15, 2.7, 13.5], "#979598")
    m.box("wide front shell", [1, 2.7, 2.5], [15, 4.1, 8.2], "#c4c0c5")
    m.box("raised cartridge deck", [2, 2.7, 8.2], [14, 5.6, 13.2], "#c4c0c5")
    m.box("deck front riser", [2, 4.1, 6.6], [14, 5.2, 8.2], "#b4b0b8")
    m.top("cartridge slot recess", 4, 12, 8.7, 10.3, 5.6, DARK, DARK)
    m.top("cartridge slot flaps", 4.3, 11.7, 9.05, 9.95, 5.65, MID, MID)
    m.top("cartridge slot seam", 4.3, 11.7, 9.47, 9.55, 5.7, BLACK, BLACK)
    for x in (2.7, 11.65):
        m.box(
            "purple power / reset slider",
            [x, 4.1, 4.1],
            [x + 1.65, 4.55, 7.7],
            PURPLE,
            {"up": vents(1.65, 3.6, PURPLE, "#4c4669", False, 4)},
        )
    m.box(
        "eject key",
        [5.7, 4.1, 6.1],
        [10.3, 4.55, 7.9],
        "#96909f",
        {"up": label(4.6, 1.8, "EJECT", "#96909f", DARK)},
    )
    m.top(
        "system name",
        5.3,
        10.7,
        3,
        5.3,
        4.11,
        label(5.4, 2.3, "SNES", "#c4c0c5", "#56505f"),
    )
    for x in (9.8, 3.1):
        controller_port(m, x, 1.25, 2.47, 3.1, 1.2, "#5d5864", 7)
    m.led([13.6, 3.15, 2.44], [14, 3.55, 2.54])
    m.top("rear vents", 3, 13, 11.5, 12.7, 5.61, vents(10, 1.2, "#c4c0c5", MID))
    powered(m)


def sega(name):
    m = Model(name + "_console")
    feet(m, 2, 14, 3, 13)
    m.box("lower plinth", [1.3, 0.5, 2.8], [14.7, 2.1, 13.2], BLACK)
    m.box("wide upper case", [1, 2.1, 3], [15, 3.7, 12.8], "#25262c")
    m.box("front bevel step", [1.6, 2.1, 2.5], [14.4, 3.2, 3], DARK)
    m.box("rear bevel step", [1.6, 2.1, 12.8], [14.4, 3.4, 13.5], DARK)
    im = tile(9, 9, DARK)
    d = ImageDraw.Draw(im)
    d.ellipse((1, 1, im.width - 2, im.height - 2), outline=GOLD, width=1)
    text(im, "16-BIT", (6, 23), GOLD)
    text(im, "SEGA", (10, 6), "#d0d2da")
    m.round_top("round 16-bit cartridge deck", 7, 8.5, 4.5, 3.7, 4.3, DARK, im)
    m.top("cartridge slot", 3.1, 10.9, 8.4, 9.4, 4.31, BLACK, BLACK)
    m.top("slot lips", 3.3, 10.7, 8.8, 9, 4.35, "#606069", MID)
    m.top(
        "regional badge",
        4.1,
        11,
        3.2,
        4.6,
        3.71,
        label(
            6.9, 1.4, "GENESIS" if name == "genesis" else "MEGA DRIVE", DARK, "#c9cbd1"
        ),
        DARK,
    )
    m.top(
        "left ventilation",
        12.25,
        14.5,
        6.5,
        12.3,
        3.71,
        vents(2.25, 5.8, DARK, BLACK),
        DARK,
    )
    m.box("power switch", [12.6, 3.7, 4], [14.2, 4.05, 5.3], "#a4a3ab")
    m.box(
        "reset key",
        [11.5, 3.7, 5.6],
        [12.5, 4.05, 6.8],
        "#67768a" if name == "megadrive" else "#98989e",
    )
    m.top("volume slider channel", 13, 13.5, 7.5, 11, 3.75, BLACK, BLACK)
    m.box("volume slider", [12.8, 3.75, 8.6], [13.7, 4.05, 9.2], "#94949b")
    for x in (3.2, 8.3):
        controller_port(m, x, 1.15, 2.47, 2.4, 1.0, MID, 6)
    m.led([11.6, 3.72, 4.4], [12.05, 3.85, 4.85])
    powered(m)


def n64():
    m = Model("n64_console")
    feet(m)
    m.box("lower waist", [2, 0.5, 3], [14, 2.4, 13], BLACK)
    m.box("wide middle", [1, 1.4, 4], [15, 3.4, 12.2], DARK)
    m.box("front skirt", [1.8, 1.4, 2.5], [14.2, 3.15, 4], DARK)
    m.box("central front arch", [5.2, 3.15, 2.9], [10.8, 4.2, 5.5], DARK)
    m.box("back shoulder", [2, 2.4, 12.2], [14, 3.8, 13.5], DARK)
    m.box("raised upper crown", [2, 3.4, 5.5], [14, 4.5, 11.7], "#33343b")
    m.box("crown top", [3.1, 4.5, 6.5], [12.9, 5.1, 10.8], "#373840")
    m.top("cartridge collar", 3.8, 12.2, 8.4, 10.6, 5.11, MID, MID)
    m.top("cartridge opening", 4.1, 11.9, 8.8, 10.2, 5.16, BLACK, BLACK)
    m.top("slot flaps", 4.3, 11.7, 9.35, 9.65, 5.2, "#555761", MID)
    m.box(
        "expansion pak cover",
        [5.6, 3.4, 3.3],
        [10.4, 4.1, 6.1],
        BLACK,
        {"up": label(4.8, 2.8, "MEMORY", BLACK, "#73757e")},
    )
    m.box("power slider", [12.1, 3.4, 4], [13.8, 3.85, 5.8], "#7c7e86")
    m.box("reset button", [2.4, 3.4, 4.8], [3.7, 3.8, 6.1], "#7c7e86")
    for x in (2.35, 5.25, 8.15, 11.05):
        controller_port(m, x, 1.8, 2.47, 2.1, 1.05, MID, 3)
    badge = Image.new("RGBA", (8, 8), DARK)
    d = ImageDraw.Draw(badge)
    d.polygon(
        [
            (0, 4),
            (2, 2),
            (4, 5),
            (4, 1),
            (6, 2),
            (6, 6),
            (4, 7),
            (2, 4),
            (2, 7),
            (0, 6),
        ],
        fill="#428bcd",
    )
    d.line((1, 5, 1, 1, 3, 2, 5, 6, 5, 1), fill="#56ad58", width=1)
    d.point((5, 1), fill="#e8c944")
    d.point((0, 3), fill="#d24e43")
    m.front("four color N badge", 7.2, 8.8, 3.25, 4.1, 2.88, badge, DARK)
    m.led([12.9, 3.15, 2.9], [13.35, 3.4, 3.35])
    m.top("rear vents", 3.5, 12.5, 11.1, 12, 4.51, vents(9, 0.9, DARK, BLACK), DARK)
    powered(m)


def disc_lid(m, style, y, radius=4.1, color=LIGHT):
    im = tile(radius * 2, radius * 2, color)
    d = ImageDraw.Draw(im)
    if style == "ps1":
        ps_logo(im, im.width // 2 - 6, im.height // 2 - 6)
        text(im, "SONY", (im.width // 2 - 8, 3), "#636363")
    elif style == "dreamcast":
        swirl(im, im.width // 2, im.height // 2, 8)
    else:
        # White isometric GameCube mark over the black circular lid.
        cx, cy = im.width // 2, im.height // 2 - 2
        d.line(
            [
                (cx, cy - 7),
                (cx + 7, cy - 3),
                (cx + 7, cy + 5),
                (cx, cy + 9),
                (cx - 7, cy + 5),
                (cx - 7, cy - 3),
                (cx, cy - 7),
            ],
            fill=WHITE,
            width=1,
        )
        d.line([(cx - 7, cy - 3), (cx, cy + 1), (cx + 7, cy - 3)], fill=WHITE, width=1)
        d.line([(cx, cy + 1), (cx, cy + 9)], fill=WHITE, width=1)
    m.round_top("disc lid seam", 8, 8, radius, y, y + 0.04, MID)
    m.round_top("circular disc lid", 8, 8, radius - 0.12, y + 0.04, y + 0.22, color, im)


def ps1():
    m = Model("ps1_console")
    feet(m)
    m.box("lower shell", [1.2, 0.5, 2.5], [14.8, 1.9, 13.5], "#949591")
    m.box("middle shell", [1, 1.5, 2.5], [15, 3.1, 13.5], "#b4b5b0")
    m.box("upper shoulder", [1.4, 3.1, 2.8], [14.6, 3.65, 13.2], "#bfc0b9")
    disc_lid(m, "ps1", 3.65, 4.05, "#bfc0b9")
    for x in (1.65, 13.05):
        m.top(
            "side vents",
            x,
            x + 1.3,
            5.6,
            12.3,
            3.66,
            vents(1.3, 6.7, "#bfc0b9", "#70716e", False),
        )
    m.round_top("open button", 2.5, 4.5, 0.68, 3.65, 3.9, "#9da098")
    m.round_top("power button", 13.4, 4.6, 0.68, 3.65, 3.9, "#9da098")
    m.round_top("reset button", 13.4, 6.2, 0.46, 3.65, 3.88, "#9da098")
    for x in (3.05, 9.95):
        controller_port(m, x, 1.1, 2.46, 3, 1.05, "#73766d", 7)
        m.front("memory card slot", x, x + 3, 2.4, 2.85, 2.46, BLACK, MID)
    m.led([13.2, 3.65, 3.1], [13.55, 3.83, 3.45], "green")
    powered(m, "green")


def ps2():
    m = Model("ps2_console")
    feet(m)
    # Original fat PS2: asymmetric stacked slabs and a recessed horizontal disc tray.
    m.box("lower offset slab", [1.8, 0.5, 2.7], [15, 2.1, 13.5], BLACK)
    m.box(
        "upper overhanging slab",
        [1, 2.1, 2.3],
        [15, 4.3, 13.5],
        "#24262e",
        {
            "east": vents(11.2, 2.2, "#24262e", BLACK, False, 2),
            "west": vents(11.2, 2.2, "#24262e", BLACK, False, 2),
        },
    )
    m.box("flat roof", [1, 4.3, 2.3], [15, 4.65, 13.5], "#272932")
    top = tile(14, 11.2, "#272932")
    text(top, "PS2", (9, 15), "#4f83ca", 2)
    m.top("blue PS2 roof mark", 1, 15, 2.3, 13.5, 4.65, top, DARK)
    m.front(
        "drive tray bezel",
        2.8,
        12.1,
        2.55,
        3.95,
        2.26,
        label(9.3, 1.4, "DVD", BLACK, "#989da8"),
        BLACK,
    )
    m.front("drive tray slit", 3.1, 11.8, 2.55, 2.7, 2.21, BLACK, BLACK)
    p = tile(0.8, 0.9, BLACK)
    ps_logo(p, 0, 0)
    m.front("PS tray emblem", 10.7, 11.6, 2.8, 3.7, 2.2, p, BLACK)
    for x in (9.2, 12.3):
        controller_port(m, x, 1, 2.65, 2.1, 0.8, MID, 6)
        m.front("memory card slot", x, x + 2.1, 1.9, 2.3, 2.26, BLACK, BLACK)
    m.front(
        "front ventilation",
        2.7,
        8.3,
        0.85,
        2,
        2.65,
        vents(5.6, 1.15, DARK, BLACK, True, 3),
        DARK,
    )
    for y in (1.0, 1.65):
        m.front("USB socket", 1.85, 2.5, y, y + 0.4, 2.65, MID, MID)
    m.box("power button", [1.5, 3.25, 2.11], [2.25, 3.95, 2.31], MID)
    m.box("eject button", [1.5, 2.45, 2.11], [2.25, 3.08, 2.31], MID)
    m.led([1.73, 3.7, 2.05], [2.03, 3.95, 2.13], "green")
    m.textures["eject"] = "retrocraft:block/console/led_blue_off"
    m.box(
        "blue eject indicator",
        [1.73, 2.5, 2.04],
        [2.03, 2.75, 2.13],
        "#eject",
        shade=False,
    )
    powered(m, "green")
    p = ASSETS / "models/block/ps2_console_on.json"
    d = json.loads(p.read_text())
    d["textures"]["eject"] = "retrocraft:block/console/led_blue_on"
    write_json("models/block/ps2_console_on.json", d)


def dreamcast():
    m = Model("dreamcast_console")
    feet(m)
    m.box("base", [2, 0.5, 2.5], [14, 1.6, 13.5], "#b5b5ad")
    m.box("wide center shell", [1.6, 1.6, 3.5], [14.4, 3.65, 12.5], WHITE)
    m.box("front shell", [2, 1.6, 2.5], [14, 3.3, 3.5], WHITE)
    m.box("back shell", [2, 1.6, 12.5], [14, 3.5, 13.5], WHITE)
    m.box("top shoulder", [2, 3.65, 3.5], [14, 4, 12.5], WHITE)
    disc_lid(m, "dreamcast", 4, 4.2, WHITE)
    for x in (2.4, 5.15, 7.9, 10.65):
        controller_port(m, x, 1.3, 2.46, 2.2, 1.25, "#7f807b", 4)
    m.round_top("power key", 12.5, 3.8, 0.7, 3.65, 4.15, "#d0d1c7")
    m.round_top("open key", 3.5, 3.8, 0.7, 3.65, 4.15, "#d0d1c7")
    m.led([7.75, 3.4, 2.85], [8.25, 3.55, 3.35], "orange")
    m.box(
        "side modem",
        [1.35, 1.5, 5],
        [1.65, 3.3, 11.5],
        "#c3c4ba",
        {"west": vents(6.5, 1.8, "#c3c4ba", MID, True, 3)},
    )
    powered(m, "orange")


def gamecube():
    m = Model("gamecube_console")
    feet(m, 3, 13, 3, 13)
    m.display = console_display(0.85)
    m.display["gui"]["translation"] = [0, 0, 0]
    indigo = "#50477e"
    # The original is a squat cube (150 x 110 x 161 mm), not a tall tower.
    m.box("lower plinth", [2.5, 0.5, 2.7], [13.5, 1.5, 13.5], "#3e375e")
    m.box(
        "indigo shell",
        [2.35, 1.5, 2.5],
        [13.65, 8.7, 13.5],
        indigo,
        {
            "east": vents(11, 7.2, indigo, "#28263d", False, 4),
            "west": vents(11, 7.2, indigo, "#28263d", False, 4),
        },
    )
    m.box("top rim", [2.5, 8.7, 2.65], [13.5, 9.1, 13.35], indigo)
    disc_lid(m, "gamecube", 9.1, 4.05, BLACK)
    m.front(
        "silver controller panel",
        2.7,
        13.3,
        2.3,
        7.2,
        2.46,
        tile(10.6, 4.9, "#aaa9a3"),
        "#aaa9a3",
    )
    for x in (3.2, 5.85, 8.5, 11.15):
        # Coarse octagonal socket silhouette painted into a tiny square recess.
        im = tile(1.65, 1.65, "#aaa9a3")
        d = ImageDraw.Draw(im)
        d.polygon(
            [(2, 0), (4, 0), (6, 2), (6, 4), (4, 6), (2, 6), (0, 4), (0, 2)], fill=BLACK
        )
        d.point((3, 3), fill=MID)
        m.front("round controller socket", x, x + 1.65, 5, 6.65, 2.4, im, "#aaa9a3")
    for x in (4.1, 9.4):
        m.front(
            "memory card flap",
            x,
            x + 2.5,
            2.7,
            3.65,
            2.4,
            label(2.5, 0.95, "A" if x < 5 else "B", "#777773", BLACK),
            "#777773",
        )
    m.round_top("open button", 3.1, 3.1, 0.5, 9.1, 9.35, "#99968b")
    m.round_top("power button", 12.9, 11.8, 0.5, 9.1, 9.35, "#99968b")
    m.round_top("reset button", 12.9, 3.1, 0.4, 9.1, 9.3, "#99968b")
    m.led([7.8, 9.1, 3.3], [8.2, 9.3, 3.7], "orange")
    # Hollow carrying handle, visible from above and behind.
    for x in (3.7, 11.3):
        m.box("handle support", [x, 5.6, 13.5], [x + 1, 6.6, 15.2], "#393344")
    m.box("rear carrying handle", [3.7, 5.6, 14.5], [12.3, 6.6, 15.2], "#393344")
    powered(m, "orange")


def wii():
    m = Model("wii_console")
    m.display = console_display(0.9)
    m.display["gui"]["translation"] = [0, 0, 0]
    m.box("silver stand", [5.15, 0, 2.1], [10.85, 0.65, 14.3], "#9a9fa4")
    m.box("stand cradle", [5.8, 0.65, 3.2], [10.2, 1.2, 13.5], "#b1b6b9")
    # Wii silhouette: slightly raked parallel front and rear, achieved with coarse steps.
    m.box("white chassis", [6.4, 1.2, 3.1], [9.6, 13.7, 13.5], WHITE)
    m.box("raked top lip", [6.4, 13.7, 3.5], [9.6, 14.3, 13.9], WHITE)
    m.box("raked bottom lip", [6.4, 1.2, 2.7], [9.6, 1.8, 13.1], WHITE)
    m.front("front fascia", 6.4, 9.6, 1.8, 13.7, 3.06, tile(3.2, 11.9, WHITE), WHITE)
    m.front("blue slot rim", 7.1, 7.8, 4.6, 12, 3.0, "#eject", WHITE)
    m.front("vertical disc slot", 7.28, 7.62, 4.75, 11.85, 2.94, BLACK, BLACK)
    m.textures["eject"] = "retrocraft:block/console/led_blue_off"
    m.box("power button", [8.5, 12.1, 2.92], [9.1, 12.8, 3.07], "#c1c5c2")
    m.box("reset button", [8.5, 10.5, 2.92], [9.1, 11.05, 3.07], "#c1c5c2")
    m.box("eject button", [8.5, 4.3, 2.92], [9.1, 4.85, 3.07], "#c1c5c2")
    m.front("SD door seam", 8.35, 9.3, 6.5, 9.9, 3.0, WHITE, "#babeb9")
    m.front(
        "Wii mark",
        6.7,
        9.3,
        2.1,
        3.7,
        2.96,
        label(2.6, 1.6, "Wii", WHITE, "#999f9f"),
        WHITE,
    )
    m.top("top controller flaps", 6.5, 9.5, 4, 13.1, 14.3, tile(3, 9.1, WHITE), WHITE)
    m.top("top flap seam", 6.55, 9.45, 8, 8.12, 14.35, MID, MID)
    m.led([8.65, 13.05, 2.92], [8.95, 13.3, 3.03], "green")
    powered(m, "green")
    p = ASSETS / "models/block/wii_console_on.json"
    d = json.loads(p.read_text())
    d["textures"]["eject"] = "retrocraft:block/console/led_blue_on"
    write_json("models/block/wii_console_on.json", d)


def dpad(m, x, y, z, size=2.2, color=BLACK):
    a = size / 3
    m.box(
        "D-pad horizontal",
        [x - size / 2, y - a / 2, z],
        [x + size / 2, y + a / 2, z + 0.35],
        color,
    )
    m.box(
        "D-pad vertical",
        [x - a / 2, y - size / 2, z],
        [x + a / 2, y + size / 2, z + 0.35],
        color,
    )
    m.south(
        "D-pad center",
        x - a * 0.35,
        x + a * 0.35,
        y - a * 0.35,
        y + a * 0.35,
        z + 0.36,
        "#3e3e42",
        color,
    )


def button(m, name, x, y, z, r=0.55, color=RED):
    # Three rectangles form a coarse octagonal button, without smooth cylinders.
    m.box(
        name + " center",
        [x - r, y - r * 0.55, z],
        [x + r, y + r * 0.55, z + 0.28],
        color,
    )
    m.box(
        name + " ends", [x - r * 0.55, y - r, z], [x + r * 0.55, y + r, z + 0.28], color
    )


def handheld(name):
    m = Model(name + "_handheld", "item", name + "_handheld")
    m.display = item_display()
    shell = {"gb": "#d1cfc5", "gbc": "#268c91", "gba": "#514783", "nes": "#d1cfc5"}[
        name
    ]
    bezel = {"gb": "#666773", "gbc": BLACK, "gba": "#b2b4b6", "nes": DARK}[name]
    if name in ("gb", "gbc"):
        x0, x1 = (3.5, 12.5) if name == "gb" else (3.65, 12.35)
        m.box(
            "rear housing",
            [x0, 2, 6.7],
            [x1, 15.2, 8],
            "#b7b5ae" if name == "gb" else "#22787e",
        )
        m.box("front housing", [x0, 2, 8], [x1, 15.2, 9], shell)
        # The DMG has a visibly rounded lower-right corner. GBC has a narrower lower grip.
        m.box("lower shell step", [x0, 1.4, 6.8], [x1 - 0.45, 2, 9], shell)
        m.box("lower shell edge", [x0 + 0.2, 1, 6.95], [x1 - 1, 1.4, 8.9], shell)
        m.box("upper shell edge", [x0 + 0.2, 15.2, 6.85], [x1 - 0.2, 15.5, 8.9], shell)
        m.box("screen bezel", [x0 + 0.45, 8.4, 9], [x1 - 0.45, 14.8, 9.16], bezel)
        m.box("bezel lower step", [x0 + 0.7, 8.15, 9], [x1 - 0.8, 8.4, 9.16], bezel)
        # Native 160:144 aspect ratio, centered on the original's broad screen bezel.
        rect = (
            (5.2, 10.8, 9.0, 14.04, 9.22)
            if name == "gb"
            else (5.2, 10.8, 9.35, 14.39, 9.22)
        )
        m.box(
            "LCD glass",
            [rect[0], rect[2], 9.16],
            [rect[1], rect[3], rect[4]],
            "#screen",
            shade=False,
        )
        if name == "gb":
            stripe = tile(7.7, 0.45, bezel)
            d = ImageDraw.Draw(stripe)
            d.line((0, 0, stripe.width - 1, 0), fill="#9c4b73")
            d.line((0, 1, stripe.width - 1, 1), fill="#384976")
            m.south("DMG bezel stripes", 4.15, 11.85, 14.1, 14.55, 9.23, stripe, bezel)
            m.south(
                "Game Boy wordmark",
                4.25,
                11.75,
                7.4,
                8.05,
                9.02,
                label(7.5, 0.65, "GAME BOY", shell, "#344565"),
                shell,
            )
            # Slots run diagonally across the lower right speaker, represented with coarse steps.
            for i in range(5):
                m.south(
                    "speaker groove",
                    9.1 + i * 0.48,
                    9.3 + i * 0.48,
                    1.9 + i * 0.22,
                    3.6 + i * 0.22,
                    9.02,
                    "#737477",
                    shell,
                )
        else:
            mark = Image.new("RGBA", (33, 12), bezel)
            text(mark, "GAME BOY", (1, 0), "#c9cbd3")
            for i, c in enumerate(
                ["#c25277", "#b2aa4f", "#388bba", "#549367", "#b77f41"]
            ):
                text(mark, "COLOR"[i], (7 + i * 4, 7), c)
            m.south("Game Boy Color wordmark", 4.4, 11.7, 8.45, 9.35, 9.23, mark, bezel)
            speaker = Image.new("RGBA", (9, 9), shell)
            draw = ImageDraw.Draw(speaker)
            for x in (1, 3, 5, 7):
                for y in (1, 3, 5, 7):
                    draw.point((x, y), fill="#195458")
            m.south("speaker perforations", 9.4, 11, 2.1, 3.7, 9.02, speaker, shell)
        dpad(m, 5.15, 5.9, 9.03, 2.15)
        button(
            m, "B button", 9.4, 5.35, 9.03, 0.58, "#8e284f" if name == "gb" else DARK
        )
        button(m, "A button", 11, 6.1, 9.03, 0.58, "#8e284f" if name == "gb" else DARK)
        for x, y in ((6.4, 3.4), (8, 3.65)):
            m.box(
                "select / start",
                [x - 0.6, y - 0.16, 9.02],
                [x + 0.6, y + 0.16, 9.25],
                "#777982" if name == "gb" else DARK,
                rotation={"origin": [x, y, 9.02], "axis": "z", "angle": 22.5},
            )
        m.led([4.3, 11.6, 9.17], [4.6, 11.9, 9.25])
        m.box(
            "rear battery door",
            [x0 + 0.65, 2.5, 6.6],
            [x1 - 0.65, 7, 6.71],
            "#c3c1b8" if name == "gb" else "#247d82",
        )
        m.box(
            "rear cartridge slot",
            [x0 + 0.6, 10.1, 6.6],
            [x1 - 0.6, 14.7, 6.71],
            "#a9a79e" if name == "gb" else "#20676c",
        )
        m.box("power slider", [x0 + 0.7, 15.5, 7.3], [x0 + 2.2, 15.7, 8.2], DARK)
        m.box("headphone jack", [7.6, 0.85, 7.3], [8.4, 1.05, 8.1], BLACK)
    else:
        # AGB-001: wide screen, symmetric side grips, light gray bezel, gray controls.
        is_gba = name == "gba"
        z = 9.05
        m.box("middle shell", [2.1, 4.3, 6.8], [13.9, 12, 8.95], shell)
        m.box("upper shell step", [3.3, 12, 6.9], [12.7, 12.5, 8.85], shell)
        m.box("lower shell step", [3.3, 3.75, 6.9], [12.7, 4.3, 8.85], shell)
        m.box("left grip", [1.15, 5, 6.85], [2.1, 11.3, 8.9], shell)
        m.box("right grip", [13.9, 5, 6.85], [14.85, 11.3, 8.9], shell)
        m.box("left edge", [0.85, 6, 7], [1.15, 10.5, 8.7], shell)
        m.box("right edge", [14.85, 6, 7], [15.15, 10.5, 8.7], shell)
        m.box("wide screen surround", [4, 4.8, 8.95], [12, 11.9, 9.12], bezel)
        rect = (
            (4.9, 11.1, 6.3, 10.44, 9.22) if is_gba else (5.15, 10.85, 6.2, 10.55, 9.22)
        )
        m.box(
            "LCD glass",
            [rect[0], rect[2], 9.12],
            [rect[1], rect[3], rect[4]],
            "#screen",
            shade=False,
        )
        m.south(
            "bezel wordmark",
            4.2,
            11.8,
            5,
            5.85,
            9.23,
            label(
                7.6,
                0.85,
                "ADVANCE" if is_gba else "NES",
                bezel,
                "#4c4e58" if is_gba else LIGHT,
            ),
            bezel,
        )
        dpad(m, 2.7, 8.7, z, 2.2, "#aaaab0" if is_gba else BLACK)
        button(m, "B button", 12.65, 8, z, 0.55, "#aaaab0" if is_gba else RED)
        button(m, "A button", 14, 8.85, z, 0.55, "#aaaab0" if is_gba else RED)
        for y in (5.1, 6.05):
            m.box(
                "start / select",
                [2.4, y, 8.96],
                [3.4, y + 0.3, 9.2],
                "#aaaab0" if is_gba else BLACK,
            )
        for y in (5.1, 5.6, 6.1):
            m.south("speaker grille", 12.25, 14, y, y + 0.16, 9.01, "#29283c", shell)
        m.box("L shoulder", [1.4, 11.3, 7.1], [3.8, 12.2, 8.5], "#b8b8bd")
        m.box("R shoulder", [12.2, 11.3, 7.1], [14.6, 12.2, 8.5], "#b8b8bd")
        m.box(
            "battery door",
            [5.3, 5.4, 6.65],
            [10.7, 9.8, 6.81],
            "#423b6a" if is_gba else "#a4a49d",
        )
        m.box("rear game slot", [4.4, 10, 6.7], [11.6, 12.2, 6.82], DARK)
        m.led([12.4, 10.5, 9.01], [12.7, 10.8, 9.15], "green")
    m.textures["screen"] = (
        "retrocraft:block/console/lcd_dmg_off"
        if name == "gb"
        else "retrocraft:block/console/lcd_color_off"
    )
    m.overrides = [
        {
            "predicate": {"retrocraft:powered": 1},
            "model": f"retrocraft:item/{name}_handheld_on",
        }
    ]
    m.save()
    write_json(
        f"models/item/{name}_handheld_on.json",
        {
            "parent": f"retrocraft:item/{name}_handheld",
            "textures": {
                "screen": f'retrocraft:block/console/lcd_{"dmg" if name=="gb" else "color"}_on',
                "indicator": f'retrocraft:block/console/led_{"green" if name in ("gba","nes") else "red"}_on',
            },
        },
    )


def game_label(w, h, platform, accent):
    im = tile(w, h, "#e6dfc8")
    d = ImageDraw.Draw(im)
    header = 7
    d.rectangle((0, 0, im.width - 1, header), fill=accent)
    text(im, platform, (max(1, (im.width - len(platform) * 4 + 1) // 2), 1), WHITE)
    # Original generic game art: sky, blocky mountains, a rising sun and a path.
    d.rectangle((1, header + 1, im.width - 2, im.height - 2), fill="#4e7898")
    if im.height > 12:
        d.rectangle(
            (im.width - 9, header + 3, im.width - 5, header + 7), fill="#f1cb77"
        )
        ground = im.height - 3
        d.polygon(
            [
                (1, ground),
                (im.width // 3, header + 2),
                (im.width // 2, ground),
                (3 * im.width // 4, header + 4),
                (im.width - 2, ground),
            ],
            fill="#344e5b",
        )
        d.rectangle((1, ground, im.width - 2, im.height - 2), fill="#70905b")
        d.rectangle(
            (im.width // 2 - 2, ground, im.width // 2 + 2, im.height - 2),
            fill="#c6ac78",
        )
    return im


def cartridge(name):
    m = Model(name, "item", "media_" + name)
    m.display = item_display()
    shell = {
        "nes": "#aaa9a4",
        "snes": "#a9a6ac",
        "gb": "#a9aaa3",
        "gbc": "#33363a",
        "gba": "#30323d",
        "n64": "#a9aaa4",
        "megadrive": "#26282e",
        "mame": "#276b51",
    }[name]
    if name == "nes":
        m.box("NES cartridge body", [3, 2.1, 7.15], [13, 13.4, 8.85], shell)
        m.box("top lip", [3.5, 13.4, 7.15], [12.5, 13.9, 8.85], shell)
        m.box("connector foot", [3.5, 1.7, 7.3], [12.5, 2.1, 8.7], DARK)
        m.south(
            "ribbed left grip",
            3,
            5,
            3,
            12.8,
            8.87,
            vents(2, 9.8, shell, "#7f807b", False),
            shell,
        )
        m.south(
            "ribbed right grip",
            11.5,
            13,
            3,
            12.8,
            8.87,
            vents(1.5, 9.8, shell, "#7f807b", False),
            shell,
        )
        m.south("recessed label border", 5.25, 11.25, 5.3, 13.4, 8.86, DARK, shell)
        m.south(
            "NES game label",
            5.5,
            11,
            5.55,
            13.2,
            8.91,
            game_label(5.5, 7.65, "NES", "#b23c40"),
            shell,
        )
        m.south(
            "lower arrow",
            7.25,
            8.75,
            2.8,
            4,
            8.9,
            label(1.5, 1.2, "V", shell, "#777970"),
            shell,
        )
    elif name in ("gb", "gbc"):
        m.box("cartridge shell", [4, 3, 7.1], [12, 11.8, 8.9], shell)
        m.box("top shoulders", [4.4, 11.8, 7.1], [11.6, 12.2, 8.9], shell)
        m.box("top center notch", [5.1, 12.2, 7.1], [10.9, 12.5, 8.9], shell)
        m.south(
            "embossed system strip",
            4.25,
            11.75,
            10.4,
            11.8,
            8.92,
            label(
                7.5, 1.4, "GAME BOY", shell, "#73766d" if name == "gb" else "#737780"
            ),
            shell,
        )
        m.south("label recess", 4.7, 11.3, 4.2, 10, 8.92, "#65685e", shell)
        m.south(
            "game label",
            4.9,
            11.1,
            4.4,
            9.8,
            8.97,
            game_label(
                6.2,
                5.4,
                "GB" if name == "gb" else "COLOR",
                "#686d4e" if name == "gb" else "#785a91",
            ),
            shell,
        )
        for x in (4, 11.5):
            m.south(
                "side grip ribs",
                x,
                x + 0.5,
                5,
                10.2,
                8.94,
                vents(0.5, 5.2, shell, DARK, False, 3),
                shell,
            )
        m.box("connector edge", [4.5, 2.7, 7.25], [11.5, 3, 8.65], DARK)
    elif name == "gba":
        m.box("short AGB shell", [2.5, 5.1, 7.1], [13.5, 10.7, 8.9], shell)
        m.box("wide top grip", [2, 10.7, 7.05], [14, 11.3, 8.95], shell)
        m.box("connector edge", [3, 4.8, 7.25], [13, 5.1, 8.65], DARK)
        m.south(
            "embossed advance mark",
            2.8,
            13.2,
            9.6,
            10.6,
            8.92,
            label(10.4, 1, "GAME BOY ADVANCE", shell, "#747781"),
            shell,
        )
        m.south(
            "game label",
            4,
            12,
            5.8,
            9.3,
            8.94,
            game_label(8, 3.5, "GBA", "#503f7a"),
            shell,
        )
    elif name == "snes":
        m.box("wide SNS shell", [1, 3, 7.1], [15, 10.7, 8.9], shell)
        m.box("raised top shoulders", [1.5, 10.7, 7.1], [14.5, 11.6, 8.9], shell)
        m.box("top center", [3, 11.6, 7.1], [13, 12.2, 8.9], shell)
        for x in (1.4, 12.2):
            m.south(
                "molded end grooves",
                x,
                x + 2.4,
                4,
                10.3,
                8.92,
                vents(2.4, 6.3, shell, "#77747e", False, 4),
                shell,
            )
        m.south(
            "front label",
            4.1,
            11.9,
            4.7,
            11.8,
            8.94,
            game_label(7.8, 7.1, "SNES", "#71648f"),
            shell,
        )
        m.box("connector edge", [2.1, 2.6, 7.3], [13.9, 3, 8.7], DARK)
    elif name == "n64":
        m.box("NUS cartridge body", [1.5, 3.4, 7.1], [14.5, 10.5, 8.9], shell)
        m.box("rounded upper shoulders", [2.2, 10.5, 7.1], [13.8, 11.3, 8.9], shell)
        m.box("rounded upper center", [3.3, 11.3, 7.1], [12.7, 11.9, 8.9], shell)
        m.box("center arch", [5, 11.9, 7.1], [11, 12.1, 8.9], shell)
        m.south(
            "central label",
            4.1,
            11.9,
            4.1,
            10.8,
            8.94,
            game_label(7.8, 6.7, "N64", "#365d55"),
            shell,
        )
        for x in (2.2, 12.6):
            m.south("molded shoulder", x, x + 1.2, 4.2, 10.1, 8.93, "#92948c", shell)
        m.box("connector edge", [2.6, 3.1, 7.25], [13.4, 3.4, 8.75], DARK)
    elif name == "megadrive":
        m.box("Sega cartridge body", [2, 2.9, 7.05], [14, 11.3, 8.95], shell)
        m.box("rounded top shoulders", [2.5, 11.3, 7.05], [13.5, 12, 8.95], shell)
        m.box("rounded top center", [3.5, 12, 7.05], [12.5, 12.4, 8.95], shell)
        m.south(
            "game label",
            3.7,
            12.3,
            3.7,
            11.8,
            9,
            game_label(8.6, 8.1, "16-BIT", "#a43438"),
            shell,
        )
        for x in (2.3, 13):
            m.south(
                "grip ridges",
                x,
                x + 0.7,
                4.5,
                10.6,
                8.98,
                vents(0.7, 6.1, shell, BLACK, False),
                shell,
            )
        m.box("connector edge", [2.8, 2.55, 7.25], [13.2, 2.9, 8.75], BLACK)
    else:
        m.box(
            "arcade ROM board",
            [1.5, 3, 7.55],
            [14.5, 12.5, 8.05],
            shell,
            {"south": vents(13, 9.5, shell, "#3a8865", True, 5)},
        )
        for x in (2.4, 6.2, 10):
            for y in (5.3, 8.8):
                m.box(
                    "EPROM chip",
                    [x, y, 8.05],
                    [x + 2.7, y + 1.8, 8.5],
                    BLACK,
                    {"south": label(2.7, 1.8, "ROM", BLACK, "#a9a9a1")},
                )
        for i in range(20):
            m.box(
                "gold edge connector",
                [2 + i * 0.6, 2.5, 7.55],
                [2.35 + i * 0.6, 3.8, 8.08],
                GOLD,
            )
        m.south(
            "arcade label",
            4,
            12,
            11,
            12.3,
            8.09,
            label(8, 1.3, "ARCADE", LIGHT, DARK),
            shell,
        )
    # A simple rear molding, not a duplicate of the illustrated label.
    if name != "mame":
        m.front(
            "rear molding",
            5.4,
            10.6,
            6,
            8,
            7.1,
            label(5.2, 2, "RETRO", shell, "#777a78"),
            shell,
        )
    m.save()


def optical_disc(name):
    m = Model(name, "item", "media_" + name)
    m.display = item_display()
    radius = 4.3 if name == "gamecube" else 6.1
    body = {
        "ps1": "#eeeae2",
        "ps2": "#5747a5",
        "dreamcast": "#df783e",
        "gamecube": "#1b1c23",
        "wii": "#78bee6",
    }[name]
    ink = "#4d535d" if name == "ps1" else WHITE
    size = radius * 2
    face = tile(size, size, body)
    d = ImageDraw.Draw(face)
    d.ellipse(
        (1, 1, face.width - 2, face.height - 2),
        outline="#9ba3b0" if name == "ps1" else "#b0bed0",
        width=1,
    )
    title = {
        "ps1": "PS1",
        "ps2": "PLAYSTATION 2",
        "dreamcast": "DREAMCAST",
        "gamecube": "GAMECUBE",
        "wii": "NINTENDO",
    }[name]
    # Keep lettering above the hub; the family color covers the full disc, not just a stripe.
    if name == "ps2":
        title = "PS2"
    text(
        face,
        title,
        (max(1, (face.width - len(title) * 4 + 1) // 2), round(face.height * 0.25)),
        ink,
    )
    if name == "dreamcast":
        swirl(face, face.width // 2, face.height - 11, 7, WHITE)
    elif name == "ps1":
        ps_logo(face, face.width // 2 - 6, face.height - 14)
    elif name == "ps2":
        text(face, "PS2", (face.width // 2 - 11, face.height - 16), "#a6ccff", 2)
    elif name == "gamecube":
        cx, cy = face.width // 2, face.height - 9
        d.line(
            [
                (cx, cy - 4),
                (cx + 4, cy - 2),
                (cx + 4, cy + 4),
                (cx, cy + 5),
                (cx - 4, cy + 4),
                (cx - 4, cy - 2),
                (cx, cy - 4),
            ],
            fill=WHITE,
            width=1,
        )
        d.line([(cx - 4, cy - 2), (cx, cy + 1), (cx + 4, cy - 2)], fill=WHITE, width=1)
        d.line((cx, cy + 1, cx, cy + 5), fill=WHITE, width=1)
    else:
        # Wide W and two narrow dotted i's, matching the Wii mark's silhouette.
        x, y = face.width // 2 - 11, face.height - 15
        d.line(
            [(x, y), (x + 2, y + 9), (x + 5, y + 4), (x + 8, y + 9), (x + 10, y)],
            fill=WHITE,
            width=2,
        )
        for dx in (15, 21):
            d.rectangle((x + dx, y + 3, x + dx + 1, y + 9), fill=WHITE)
            d.rectangle((x + dx, y - 1, x + dx + 1, y), fill=WHITE)
    n, hole = 12, 0.8
    region = m.region(face)
    edges = sorted(
        set([-radius + i * 2 * radius / n for i in range(n + 1)] + [-hole, hole])
    )
    for i, (y0, y1) in enumerate(zip(edges, edges[1:])):
        half = math.sqrt(max(0, radius * radius - ((y0 + y1) / 2) ** 2))
        spans = (
            [(-half, half)]
            if y1 <= -hole or y0 >= hole
            else [(-half, -hole), (hole, half)]
        )
        for j, (x0, x1) in enumerate(spans):
            e = m.box(
                f"disc ring {i+1}.{j+1}",
                [8 + x0, 8 + y0, 7.8],
                [8 + x1, 8 + y1, 8.2],
                body,
            )
            u0, v0, u1, v1 = region
            e["faces"]["south"]["uv"] = [
                u0 + (x0 + radius) / size * (u1 - u0),
                v0 + (radius - y1) / size * (v1 - v0),
                u0 + (x1 + radius) / size * (u1 - u0),
                v0 + (radius - y0) / size * (v1 - v0),
            ]
    m.save()


def arcade_side_uv(m, e, art, offset=0, front=-3):
    u0, v0, u1, v1 = m.region(art)
    za, zb = e["from"][2], e["to"][2]
    ya, yb = e["from"][1] + offset, e["to"][1] + offset
    depth = 19 - front
    # Both cabinet sides have readable lettering, with a continuous graphic across the roof seam.
    for face, lo, hi in [
        ("west", (za - front) / depth, (zb - front) / depth),
        ("east", (19 - zb) / depth, (19 - za) / depth),
    ]:
        e["faces"][face] = {
            "texture": "#atlas",
            "uv": [
                u0 + lo * (u1 - u0),
                v0 + (48 - yb) / 48 * (v1 - v0),
                u0 + hi * (u1 - u0),
                v0 + (48 - ya) / 48 * (v1 - v0),
            ],
        }


def arcade(theme):
    t = THEMES[theme]
    name = t["id"]
    shell, trim = t["shell"], t["trim"]
    m = Model(name, texture="arcade_" + theme)
    art = side_art(theme, text)
    # The new prints use the actual cheek depth (-1..19). The original three
    # retain their established UV layout, including the space below the deck lip.
    print_front = -3 if theme in ("pacman", "tetris", "space_invaders") else -1
    m.box("black plinth", [-4, 0, -3], [20, 1.5, 19], trim)
    m.box("lower cabinet", [-2.5, 1.5, 0], [18.5, 14.5, 18.7], shell)
    m.box(
        "rear spine",
        [-2.5, 14.5, 11],
        [18.5, 32, 18.7],
        trim,
        {"south": vents(21, 17.5, trim, BLACK, False, 5)},
    )
    for x0, x1 in [(-4, -2.5), (18.5, 20)]:
        for y0, y1, z0 in [
            (1.5, 14.5, 0),
            (14.5, 20, -1),
            (20, 24, 2),
            (24, 28, 4),
            (28, 32, 6),
        ]:
            e = m.box("sculpted side cheek", [x0, y0, z0], [x1, y1, 19], shell)
            arcade_side_uv(m, e, art, front=print_front)
    m.box(
        "projecting control deck",
        [-4.04, 14.5, -3],
        [20.04, 17.6, 6],
        trim,
        {"up": control_art(theme, text)},
    )
    m.front(
        "control deck front trim",
        -3.5,
        19.5,
        15.2,
        16.2,
        -3.01,
        t["accent"],
        t["accent"],
    )
    m.front("coin door", 3, 13, 3.1, 12.4, -0.03, tile(10, 9.3, "#252b32"), trim)
    for x in (4.2, 8.8):
        im = tile(2.8, 4.3, "#4d555a")
        text(im, "25", (2, 2), "#e7dac0")
        ImageDraw.Draw(im).rectangle((3, 9, 7, 13), fill="#dca568")
        m.front("coin mechanism", x, x + 2.8, 7.3, 11.6, -0.1, im, trim)
        m.front("coin return", x, x + 2.8, 4.6, 6.3, -0.11, BLACK, trim)
    m.front("coin door lock", 7.65, 8.35, 3.7, 4.4, -0.12, MID, trim)
    # Washer, continuous shaft and a stepped ball top. The stick is in front of the glass.
    m.round_top("joystick dust washer", 13, 0.7, 1.2, 17.6, 17.9, BLACK)
    m.box("joystick shaft", [12.73, 17.85, 0.43], [13.27, 20, 0.97], "#9ba2a8")
    m.box("joystick ball lower", [12.35, 19.65, 0.05], [13.65, 20.1, 1.35], RED)
    m.box("joystick ball middle", [12, 20, -0.3], [14, 21, 1.7], RED)
    m.box("joystick ball cap", [12.35, 20.95, 0.05], [13.65, 21.4, 1.35], "#cd4b43")
    for i, (x, z) in enumerate(
        [(3, -0.4), (5.8, -0.4), (8.6, -0.4), (3, 2.2), (5.8, 2.2), (8.6, 2.2)]
    ):
        m.round_top(
            "action button",
            x,
            z,
            0.85,
            17.6,
            18.05,
            ["#d1d1c0", "#d3614e", "#639cbd"][i % 3],
        )
    # The CRT slopes away from the player, while the live video follows the exact same transform.
    r = {"origin": [8, 19, 4.5], "axis": "x", "angle": 22.5}
    m.box("left CRT frame", [-2.5, 17.5, 4.5], [-1, 32, 5.3], trim, rotation=r)
    m.box("right CRT frame", [17, 17.5, 4.5], [18.5, 32, 5.3], trim, rotation=r)
    m.box("bottom CRT frame", [-1, 17.5, 4.5], [17, 19, 5.3], trim, rotation=r)
    m.box("top CRT frame", [-1, 31, 4.5], [17, 32, 5.3], trim, rotation=r)
    m.textures["screen"] = "retrocraft:block/console/crt_off"
    m.box(
        "CRT glass", [-1, 19, 4.55], [17, 31, 4.9], "#screen", rotation=r, shade=False
    )
    m.led([15.5, 11.5, -0.08], [16, 12, -0.01])
    powered(m)
    write_json(
        "models/block/" + name + "_on.json",
        {
            "parent": "retrocraft:block/" + name,
            "textures": {
                "indicator": "retrocraft:block/console/led_red_on",
                "screen": "retrocraft:block/console/crt_on",
            },
        },
    )
    # Roof: local 0..16 corresponds to world 32..48, above vanilla's per-model coordinate limit.
    top = Model(name + "_top", texture="arcade_" + theme + "_top")
    top.box("marquee housing", [-2.5, 0, 4], [18.5, 12, 18.7], trim)
    for x0, x1 in [(-4, -2.5), (18.5, 20)]:
        e = top.box("upper side panel", [x0, 0, 3], [x1, 16, 19], shell)
        arcade_side_uv(top, e, art, 32, front=print_front)
    top.box("marquee face frame", [-2.5, 1, 2.8], [18.5, 11.5, 4], trim)
    top.front("illustrated marquee", -2, 18, 2, 10.5, 2.78, marquee(theme, text), trim)
    top.box("marquee upper molding", [-2.5, 12, 3], [18.5, 14, 19], trim)
    top.box("cabinet crown", [-2.5, 14, 3], [18.5, 16, 19], trim)
    top.save()
    body = json.loads((ASSETS / "models/block" / f"{name}.json").read_text())
    roof = json.loads((ASSETS / "models/block" / f"{name}_top.json").read_text())
    body.pop("parent", None)
    body["textures"]["roof"] = roof["textures"]["atlas"]
    for e in roof["elements"]:
        for v in ("from", "to"):
            e[v][1] += 32
        for f in e["faces"].values():
            f["texture"] = "#roof"
        body["elements"].append(e)
    factor = 0.625
    for e in body["elements"]:
        for key in ("from", "to"):
            e[key] = [
                round(v * factor if axis == 1 else 8 + (v - 8) * factor, 4)
                for axis, v in enumerate(e[key])
            ]
        if "rotation" in e:
            e["rotation"]["origin"] = [
                round(v * factor if axis == 1 else 8 + (v - 8) * factor, 4)
                for axis, v in enumerate(e["rotation"]["origin"])
            ]
    body["display"] = console_display(0.53)
    body["display"]["gui"]["translation"] = [0, -3, 0]
    body["display"]["ground"]["scale"] = [0.25] * 3
    body["display"]["fixed"] = {
        "rotation": [0, 180, 0],
        "translation": [0, -4, 0],
        "scale": [0.35] * 3,
    }
    for hand in ("righthand", "lefthand"):
        body["display"]["firstperson_" + hand]["scale"] = [0.35] * 3
        body["display"]["thirdperson_" + hand]["scale"] = [0.3] * 3
    write_json(f"models/item/{name}.json", body)
    write_json(
        f"blockstates/{name}.json",
        {
            "variants": {
                f"facing={facing},powered={str(on).lower()}": {
                    "model": f"retrocraft:block/{name}" + ("_on" if on else ""),
                    "y": angle,
                }
                for facing, angle in [
                    ("north", 0),
                    ("east", 90),
                    ("south", 180),
                    ("west", 270),
                ]
                for on in (False, True)
            }
        },
    )


def shared_textures():
    colors = {
        "red": ("#42282b", "#df4b3e"),
        "green": ("#28352a", "#67c967"),
        "orange": ("#49322a", "#ee9b46"),
        "blue": ("#3c474e", "#54b8eb"),
    }
    for name, (off, on) in colors.items():
        for state, c in [("off", off), ("on", on)]:
            im = Image.new("RGBA", (4, 4), c)
            if state == "on":
                ImageDraw.Draw(im).rectangle(
                    (1, 1, 2, 2),
                    fill={
                        "red": "#ff9a71",
                        "green": "#b3ed8b",
                        "orange": "#ffcd83",
                        "blue": "#a0e2ff",
                    }[name],
                )
            im.save(ASSETS / "textures/block/console" / f"led_{name}_{state}.png")
    for name, c in [
        ("dmg_off", "#84915b"),
        ("dmg_on", "#a5b36d"),
        ("color_off", "#39444d"),
        ("color_on", "#778b84"),
    ]:
        im = Image.new("RGBA", (8, 8), c)
        ImageDraw.Draw(im).line(
            (0, 0, 7, 0), fill="#718053" if name.startswith("dmg") else "#303b46"
        )
        im.save(ASSETS / "textures/block/console" / f"lcd_{name}.png")


def generate():
    for folder in (
        "textures/block/console",
        "models/block",
        "models/item",
        "blockstates",
    ):
        (ASSETS / folder).mkdir(parents=True, exist_ok=True)
    shared_textures()
    nes()
    snes()
    sega("megadrive")
    sega("genesis")
    n64()
    ps1()
    ps2()
    dreamcast()
    gamecube()
    wii()
    for theme in THEMES:
        arcade(theme)
    write_json("models/item/mame_internal_console.json", {
        "parent": "retrocraft:item/mame_console",
        "overrides": [
            {"predicate": {"custom_model_data": i}, "model": "retrocraft:item/" + name}
            for i, name in enumerate([
                "mame_pacman_console", "mame_tetris_console", "mame_space_invaders_console",
                "mame_donkey_kong_console", "mame_galaga_console", "mame_street_fighter_console",
                "mame_metal_slug_console", "mame_mortal_kombat_console", "mame_bubble_bobble_console"
            ], 1)
        ]
    })
    for suffix in ("", "_on", "_top"):
        write_json("models/block/mame_internal_console" + suffix + ".json", {
            "parent": "retrocraft:block/mame_console" + suffix
        })
    write_json("blockstates/mame_internal_console.json", {
        "variants": {"": {"model": "retrocraft:block/mame_console"}}
    })
    for name in ("gb", "gbc", "gba", "nes"):
        handheld(name)
    for name in ("nes", "snes", "gb", "gbc", "gba", "n64", "megadrive", "mame"):
        cartridge(name)
    for name in ("ps1", "ps2", "dreamcast", "gamecube", "wii"):
        optical_disc(name)
    print(
        "Generated 10 home consoles, 10 arcade skins, 4 handhelds and 13 game media models."
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Compare generated files with committed assets without changing them",
    )
    args = parser.parse_args()
    if args.check:
        committed = ASSETS
        with tempfile.TemporaryDirectory() as temporary:
            ASSETS = Path(temporary)
            generate()
            different = [
                str(p.relative_to(ASSETS))
                for p in ASSETS.rglob("*")
                if p.is_file()
                and (
                    not (committed / p.relative_to(ASSETS)).exists()
                    or p.read_bytes()
                    != (committed / p.relative_to(ASSETS)).read_bytes()
                )
            ]
            if different:
                raise SystemExit("Outdated generated assets:\n" + "\n".join(different))
        print("All generated assets match.")
    else:
        generate()
