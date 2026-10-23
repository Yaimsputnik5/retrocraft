"""Original pixel artwork for the three arcade skins; no external artwork or fonts."""

from PIL import Image, ImageDraw

THEMES = {
    "pacman": {
        "id": "mame_console",
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
    else:
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
    else:
        text(im, "PAC-MAN", (9, 9), "#ae4b3c", 3)
        text(im, "PAC-MAN", (7, 7), "#f4d23d", 3)
        text(im, "1UP", (42, 23), "#e0d29a")
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
    else:
        for x in (8, 35, 62):
            sprite(im, INVADER, x, 25, 1, "#75b9ad")
    text(im, "MOVE", (53, 4), "#e3dba9")
    text(im, "FIRE", (8, 4), "#e3dba9")
    return im
