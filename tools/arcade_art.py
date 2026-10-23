"""Cabinet prints: original classic art and curated arcade sprites (see art/arcade)."""

from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw

THEMES = {
    "pacman": {
        "id": "mame_pacman_console",
        "title": "PAC-MAN",
        "shell": "#e5bb32",
        "trim": "#171b29",
        "accent": "#eac841",
        "ink": "#182e4a",
    },
    "tetris": {
        "id": "mame_tetris_console",
        "title": "TETRIS",
        "shell": "#234c85",
        "trim": "#172132",
        "accent": "#59b8d7",
        "ink": "#e3e7e8",
    },
    "space_invaders": {
        "id": "mame_space_invaders_console",
        "title": "SPACE INVADERS",
        "shell": "#182b37",
        "trim": "#101821",
        "accent": "#69c6b7",
        "ink": "#c4d968",
    },
}

# Additional families share the cabinet geometry, with their own palette and pixel artwork.
for key, title, shell, accent in [
    ("generic", "ARCADE", "#282b38", "#6bc6d9"),
    ("donkey_kong", "DONKEY KONG", "#5691b2", "#efc58c"),
    ("galaga", "GALAGA", "#161f3d", "#65b4e3"),
    ("street_fighter", "STREET FIGHTER", "#234a92", "#f5ae48"),
    ("metal_slug", "METAL SLUG", "#b4473c", "#e9d7aa"),
    ("mortal_kombat", "MORTAL KOMBAT", "#2c2430", "#d6ab51"),
    ("bubble_bobble", "BUBBLE BOBBLE", "#25827e", "#9cde76"),
]:
    THEMES[key] = {"id": "mame_console" if key == "generic" else "mame_" + key + "_console",
                   "title": title, "shell": shell, "trim": "#141923",
                   "accent": accent, "ink": "#e5e6df"}


INVADER = [
    "00100000100",
    "00010001000",
    "00111111100",
    "01101110110",
    "11111111111",
    "10111111101",
    "10100000101",
    "00011011000",
]
GHOST = [
    "001111100",
    "011111110",
    "111111111",
    "111111111",
    "111111111",
    "111111111",
    "111111111",
    "110101011",
]


def sprite(im, rows, x, y, scale, color):
    d = ImageDraw.Draw(im)
    for j, row in enumerate(rows):
        for i, v in enumerate(row):
            if v == "1":
                d.rectangle(
                    (
                        x + i * scale,
                        y + j * scale,
                        x + (i + 1) * scale - 1,
                        y + (j + 1) * scale - 1,
                    ),
                    fill=color,
                )


def ghost(im, x, y, scale, color):
    sprite(im, GHOST, x, y, scale, color)
    d = ImageDraw.Draw(im)
    for xx in (x + 2 * scale, x + 6 * scale):
        d.rectangle((xx, y + 2 * scale, xx + scale, y + 4 * scale), fill="#f0eeee")
        d.rectangle(
            (xx, y + 3 * scale, xx + scale - 1, y + 4 * scale - 1), fill="#324e9b"
        )


def pacman(im, x, y, r):
    d = ImageDraw.Draw(im)
    d.pieslice(
        (x - r, y - r, x + r, y + r),
        30,
        330,
        fill="#ffda32",
        outline="#202435",
        width=2,
    )
    d.rectangle((x + 1, y - r // 2, x + 3, y - r // 2 + 2), fill="#242332")


def block(im, x, y, size, color):
    d = ImageDraw.Draw(im)
    d.rectangle((x, y, x + size - 1, y + size - 1), fill=color)
    d.line((x, y, x + size - 1, y, x + size - 1, y + size - 1), fill="#111c31", width=1)
    d.line(
        (x + 1, y + size - 2, x + 1, y + 1, x + size - 2, y + 1),
        fill="#c4d9dc",
        width=1,
    )


def tetris_stack(im, left, top, cell, cols=8, rows=8):
    colors = [
        "#4cb9d6",
        "#edc452",
        "#ac69b9",
        "#65ac65",
        "#d26060",
        "#547dce",
        "#d58d4c",
    ]
    # Recognizable, connected tetromino silhouettes rather than random colored confetti.
    pieces = [
        (0, 6, 0, [(0, 0), (1, 0), (2, 0), (3, 0)]),
        (4, 6, 1, [(0, 0), (1, 0), (0, 1), (1, 1)]),
        (0, 3, 2, [(0, 1), (1, 1), (2, 1), (1, 0)]),
        (4, 3, 3, [(1, 0), (2, 0), (0, 1), (1, 1)]),
        (1, 0, 4, [(0, 0), (1, 0), (1, 1), (2, 1)]),
        (5, 0, 5, [(0, 0), (0, 1), (1, 1), (2, 1)]),
        (0, 6, 6, [(0, 0), (0, 1), (1, 1), (2, 1)]),
    ]
    for px, py, color, coords in pieces:
        for x, y in coords:
            if px + x < cols and py + y < rows:
                block(
                    im,
                    left + (px + x) * cell,
                    top + (py + y) * cell,
                    cell,
                    colors[color],
                )


def colored_title(im, title, xy, text, scale=2):
    palette = ["#d95e54", "#ebad54", "#e0d062", "#72b98b", "#6baacb", "#ac87c6"]
    x, y = xy
    for i, c in enumerate(title):
        text(im, c, (x + i * 4 * scale, y), palette[i % len(palette)], scale)


def side_art(theme, text):
    t = THEMES[theme]
    im = Image.new("RGBA", (88, 192), t["shell"])
    d = ImageDraw.Draw(im)
    d.rectangle((3, 3, 84, 188), outline=t["trim"], width=3)
    if theme == "pacman":
        text(im, "PAC-MAN", (32, 21), "#a54635")
        text(im, "PAC-MAN", (31, 20), "#182e4a")
        d.ellipse((10, 58, 78, 126), fill="#294f7d", outline="#172538", width=3)
        pacman(im, 42, 91, 22)
        for x in (66, 75):
            d.rectangle((x, 89, x + 3, 92), fill="#ffebbd")
        ghost(im, 12, 139, 2, "#b54c43")
        ghost(im, 48, 148, 2, "#608eae")
        text(im, "1UP", (35, 119), "#f6e5b4")
    elif theme == "tetris":
        d.polygon([(5, 48), (82, 30), (82, 39), (5, 57)], fill="#bc6466")
        d.polygon([(5, 58), (82, 40), (82, 43), (5, 61)], fill="#e1b366")
        colored_title(im, "TETRIS", (32, 20), text, 1)
        d.rectangle((10, 66, 77, 144), fill="#152d4d", outline="#598db6", width=2)
        for x in range(12, 77, 8):
            d.line((x, 68, x, 142), fill="#234664")
        for y in range(68, 143, 8):
            d.line((12, y, 75, y), fill="#234664")
        tetris_stack(im, 12, 78, 8)
        text(im, "NEXT", (36, 153), "#afc5da")
        for x, y in [(0, 0), (1, 0), (2, 0), (1, 1)]:
            block(im, 31 + x * 8, 165 + y * 8, 8, "#af7cb9")
    elif theme == "space_invaders":
        for i in range(32):
            x = 8 + (i * 37) % 71
            y = 36 + (i * 31) % 144
            d.point((x, y), fill="#a4bcc4")
        text(im, "SPACE", (35, 15), "#e5d689")
        text(im, "INVADERS", (29, 23), "#6ebbb3")
        sprite(im, INVADER, 22, 65, 4, "#68b9c4")
        sprite(im, INVADER, 12, 112, 2, "#c2cf6c")
        sprite(im, INVADER, 52, 133, 2, "#c78877")
        d.polygon(
            [(6, 176), (21, 170), (38, 177), (55, 166), (81, 173), (81, 186), (6, 186)],
            fill="#596e7a",
        )
        d.rectangle((33, 162, 54, 169), fill="#9ebd7b")
        d.rectangle((42, 156, 45, 162), fill="#9ebd7b")
    else:
        illustrated_side(im, theme, t, text)
    return im


def marquee(theme, text):
    t = THEMES[theme]
    im = Image.new("RGBA", (96, 32), t["trim"])
    d = ImageDraw.Draw(im)
    d.rectangle((1, 1, 94, 30), outline=t["accent"], width=2)
    if theme == "space_invaders":
        text(im, "SPACE", (29, 5), "#dfd596", 2)
        text(im, "INVADERS", (17, 18), "#80c5b4", 2)
    elif theme == "tetris":
        colored_title(im, "TETRIS", (13, 8), text, 3)
    elif theme == "pacman":
        text(im, "PAC-MAN", (9, 9), "#ae4b3c", 3)
        text(im, "PAC-MAN", (7, 7), "#f4d23d", 3)
        text(im, "1UP", (42, 23), "#e0d29a")
    else:
        return illustrated_marquee(theme, t, text)
    return im


def control_art(theme, text):
    t = THEMES[theme]
    im = Image.new("RGBA", (84, 36), t["trim"])
    d = ImageDraw.Draw(im)
    d.rectangle((1, 1, 82, 34), outline=t["accent"], width=1)
    if theme == "pacman":
        for x in (9, 22, 61, 74):
            d.rectangle((x, 5, x + 6, 11), outline="#356aa2")
        for x in range(6, 80, 7):
            d.rectangle((x, 27, x + 1, 28), fill="#e4d9b4")
        pacman(im, 44, 28, 4)
    elif theme == "tetris":
        for i, c in enumerate(["#e4bc4d", "#9e6bb5", "#62ad87", "#598fd0"]):
            block(im, 8 + i * 18, 25, 7, c)
    elif theme == "space_invaders":
        for x in (8, 35, 62):
            sprite(im, INVADER, x, 25, 1, "#75b9ad")
    else:
        return illustrated_controls(im, theme, t, text)
    text(im, "MOVE", (53, 4), "#e3dba9")
    text(im, "FIRE", (8, 4), "#e3dba9")
    return im


ART = Path(__file__).resolve().parent / "art/arcade"


@lru_cache(maxsize=None)
def artwork(name):
    with Image.open(ART / f"{name}.png") as source:
        return source.convert("RGBA")


def decal(im, name, x, y, scale=1, bounds=None, flip=False, ink=None):
    art = artwork(name)
    if ink:
        tinted = Image.new("RGBA", art.size, ink)
        tinted.putalpha(art.getchannel("A"))
        art = tinted
    if flip:
        art = art.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if bounds:
        scale = min(bounds[0] / art.width, bounds[1] / art.height)
    size = (max(1, round(art.width * scale)), max(1, round(art.height * scale)))
    if size != art.size:
        art = art.resize(size, Image.Resampling.NEAREST)
    im.alpha_composite(art, (x, y))


def center_decal(im, name, y, bounds, ink=None):
    art = artwork(name)
    scale = min(bounds[0] / art.width, bounds[1] / art.height)
    x = (im.width - round(art.width * scale)) // 2
    decal(im, name, x, y, bounds=bounds, ink=ink)


def caption(im, label, y, text, ink, scale=1):
    text(im, label, ((im.width - (len(label) * 4 - 1) * scale) // 2, y), ink, scale)


def girder(d, y, slope=0):
    d.line((8, y, 79, y + slope), fill="#77384b", width=6)
    d.line((8, y - 2, 79, y + slope - 2), fill="#ea8a88")
    for x in range(10, 73, 8):
        yy = y + (x - 8) * slope // 71
        d.line((x, yy + 1, x + 4, yy - 2, x + 8, yy + 1), fill="#cf6874")


def bubble(d, x, y, radius):
    d.ellipse((x - radius, y - radius, x + radius, y + radius), outline="#9cdee1")
    d.arc((x - radius + 2, y - radius + 2, x + radius - 2, y + radius - 2),
          185, 280, fill="#f1eed9", width=2)


def illustrated_side(im, theme, t, text):
    """Keep the classic cabinet grid; print recognizable game art within its margins."""
    d = ImageDraw.Draw(im)
    if theme == "metal_slug":
        # Neo Geo MVS red/cream, with Marco's actual red vest and the round SV-001.
        d.rectangle((8, 48, 79, 186), fill="#233036")
        center_decal(im, "metal_slug_logo", 12, (48, 30))
        decal(im, "marco", 15, 51, 2)
        decal(im, "sv001", 14, 130)
        d.line((8, 47, 79, 47), fill="#e2d1a4")
    elif theme == "street_fighter":
        center_decal(im, "street_fighter_logo", 14, (48, 28))
        d.ellipse((14, 77, 74, 157), fill="#426d9c")
        # His head sits below the narrowest cheek; the whole stance remains visible.
        decal(im, "ryu", 9, 91)
        d.line((8, 183, 79, 183), fill="#c0534d", width=2)
        d.line((8, 186, 79, 186), fill="#e9bf73")
    elif theme == "mortal_kombat":
        center_decal(im, "mortal_kombat_logo", 11, (48, 28))
        # The digitized arcade fighter retains his hood, mask and yellow tabard.
        center_decal(im, "scorpion", 42, (64, 125))
    elif theme == "donkey_kong":
        caption(im, "DONKEY", 13, text, "#74374c", 2)
        caption(im, "DONKEY", 12, text, "#f3d6ac", 2)
        caption(im, "KONG", 26, text, "#74374c", 2)
        caption(im, "KONG", 25, text, "#f3d6ac", 2)
        center_decal(im, "donkey_kong", 76, (60, 48))
        girder(d, 128, -5)
        girder(d, 154, 6)
        girder(d, 182, -5)
        for x, y in ((22, 130), (62, 155)):
            for xx in (x, x + 8):
                d.line((xx, y, xx, y + 24), fill="#c1dfdb", width=2)
            for yy in range(y + 4, y + 24, 5):
                d.line((x, yy, x + 8, yy), fill="#c1dfdb")
        decal(im, "barrel", 44, 141)
        decal(im, "barrel", 16, 164)
        decal(im, "jumpman", 53, 139)
    elif theme == "galaga":
        for i in range(40):
            d.point((8 + i * 37 % 72, 48 + i * 29 % 136),
                    fill=("#6d8ca1", "#c0b7ac", "#7aafac")[i % 3])
        center_decal(im, "galaga_logo", 14, (48, 25), ink="#b3cf78")
        center_decal(im, "galaga_boss", 87, (45, 45))
        decal(im, "galaga_bee", 14, 56)
        decal(im, "galaga_butterfly", 57, 58)
        for x in (38, 49):
            d.line((x, 137, x, 145), fill="#edb4a0", width=2)
        center_decal(im, "galaga_fighter", 153, (28, 30))
    elif theme == "bubble_bobble":
        center_decal(im, "bubble_bobble_logo", 10, (48, 39))
        for x, y, r in ((66, 69, 9), (17, 123, 10), (69, 120, 6), (22, 174, 9)):
            bubble(d, x, y, r)
        decal(im, "bub", 12, 66, 3)
        decal(im, "bob", 28, 133, 3)
    else:
        # The all-games cabinet uses the same motifs as the three original skins.
        colored_title(im, "ARCADE", (9, 19), text, 3)
        for i in range(24):
            d.point((8 + i * 31 % 72, 50 + i * 23 % 133), fill="#647b91")
        sprite(im, INVADER, 22, 61, 4, "#77bdb5")
        pacman(im, 27, 124, 16)
        ghost(im, 55, 110, 2, "#d17a83")
        for px, py in ((0, 1), (1, 1), (2, 1), (1, 0)):
            block(im, 15 + px * 9, 155 + py * 9, 9, "#be89c7")
        for px, py in ((0, 0), (1, 0), (0, 1), (1, 1)):
            block(im, 55 + px * 9, 155 + py * 9, 9, "#e0bf62")


def illustrated_marquee(theme, t, text):
    # Match the physical 20 x 8.5 face. Extra texels preserve the actual logotypes.
    im = Image.new("RGBA", (192, 80), t["trim"])
    d = ImageDraw.Draw(im)
    d.rectangle((2, 2, 189, 77), outline=t["accent"], width=3)
    logos = {
        "metal_slug": "metal_slug_logo",
        "street_fighter": "street_fighter_logo",
        "mortal_kombat": "mortal_kombat_wide",
        "galaga": "galaga_logo",
        "bubble_bobble": "bubble_bobble_logo",
    }
    if theme in logos:
        name = logos[theme]
        height = 66 if theme != "mortal_kombat" else 46
        art = artwork(name)
        scale = min(176 / art.width, height / art.height)
        y = (80 - round(art.height * scale)) // 2
        center_decal(im, name, y, (176, height), ink="#b3cf78" if theme == "galaga" else None)
        if theme == "metal_slug":
            d.line((12, 18, 12, 61), fill="#a65c4c", width=2)
            d.line((179, 18, 179, 61), fill="#a65c4c", width=2)
        elif theme == "galaga":
            for x, y in ((12, 18), (179, 58), (25, 62), (164, 16)):
                d.rectangle((x, y, x + 1, y + 1), fill="#b8cfcc")
        elif theme == "bubble_bobble":
            decal(im, "bub", 12, 26, 2)
            decal(im, "bob", 148, 26, 2, flip=True)
    elif theme == "donkey_kong":
        caption(im, "DONKEY", 15, text, "#cd7668", 4)
        caption(im, "DONKEY", 13, text, "#f3d6ac", 4)
        caption(im, "KONG", 45, text, "#cd7668", 4)
        caption(im, "KONG", 43, text, "#f3d6ac", 4)
        decal(im, "barrel", 12, 29, 2)
        decal(im, "barrel", 156, 29, 2)
    else:
        colored_title(im, "ARCADE", (26, 15), text, 6)
        caption(im, "INSERT COIN", 57, text, "#b7d0cf", 2)
    return im


def illustrated_controls(im, theme, t, text):
    d = ImageDraw.Draw(im)
    if theme == "metal_slug":
        decal(im, "sv001", 7, 17, bounds=(18, 17))
        for x, label in ((34, "A"), (50, "B"), (66, "C")):
            d.rectangle((x, 25, x + 9, 32), outline="#bbbc94")
            text(im, label, (x + 3, 27), "#e3d6b1")
        text(im, "FIRE", (8, 4), "#e3dba9")
    elif theme == "street_fighter":
        for x in (8, 32, 56):
            d.polygon([(x, 30), (x + 7, 30), (x + 5, 27), (x + 8, 27)], fill="#e2b66e")
        text(im, "PUNCH", (6, 4), "#e2c693")
        text(im, "KICK", (6, 12), "#be8974")
    elif theme == "mortal_kombat":
        for x in range(9, 67, 5):
            d.rectangle((x, 28, x + 4, 30), outline="#bc954e")
        d.polygon([(66, 26), (76, 29), (66, 32), (69, 29)], fill="#e6d7aa")
        text(im, "PUNCH", (6, 4), "#d6ba80")
        text(im, "KICK", (6, 12), "#a88765")
    elif theme == "donkey_kong":
        d.line((5, 32, 78, 32), fill="#ce7a7d", width=2)
        for x in (10, 36, 62):
            decal(im, "barrel", x, 22)
        text(im, "JUMP", (8, 4), "#e3dba9")
    elif theme == "galaga":
        for x, name in ((9, "galaga_bee"), (37, "galaga_butterfly"), (65, "galaga_boss")):
            decal(im, name, x, 23, bounds=(10, 10))
        text(im, "FIRE", (8, 4), "#e3dba9")
    elif theme == "bubble_bobble":
        decal(im, "bub", 8, 20, bounds=(13, 13))
        decal(im, "bob", 64, 20, bounds=(13, 13))
        bubble(d, 35, 28, 4)
        bubble(d, 48, 28, 3)
        text(im, "BUBBLE", (5, 4), "#d6e2b4")
    else:
        for x in (9, 36, 63):
            sprite(im, INVADER, x, 25, 1, "#7dbcb5")
        text(im, "FIRE", (8, 4), "#e3dba9")
    text(im, "MOVE", (53, 4), "#e3dba9")
    return im
