#!/usr/bin/env python3
"""Build Retrocraft's console component item textures and flat item models.

Development only: Python 3 + Pillow. Each accessory is a hand-placed 16x16 pixel map; one
character per texel, '.' is transparent. Silhouettes first, then a single highlight/shadow pair.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageColor

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/retrocraft"

PALETTE = {
    "k": "#14151a",  # outline
    "d": "#292b32",  # dark plastic
    "m": "#666970",  # mid gray
    "g": "#aaa9a5",  # gray plastic
    "l": "#d3d1c9",  # light gray
    "w": "#e8e7df",  # white plastic
    "r": "#b42d42",  # red
    "o": "#d96a2b",  # orange
    "p": "#69618c",  # purple
    "b": "#3b6fb5",  # blue
    "c": "#7fc3d9",  # cyan / lcd
    "y": "#c4aa69",  # gold contacts
    "n": "#8c9a5e",  # green lcd
}

ART = {
    "nes_zapper": [
        "................",
        "................",
        "..kkkkkkkkkkk...",
        ".kooooooooooook.",
        ".kodddddooooook.",
        ".kooooooooooook.",
        "..kkkkkkkoooook.",
        ".........kooook.",
        ".......kkkooook.",
        "......kdkkooook.",
        "......kdk.kooook",
        ".......kk.kooook",
        "..........kooook",
        "...........kkkk.",
        "................",
        "................",
    ],
    "snes_multitap": [
        "................",
        "................",
        "....kkkkkkkk....",
        "...kllllllllk...",
        "..kllllllllllk..",
        "..kldkldkldkdk..",
        "..klmklmklmkmk..",
        "..kllllllllllk..",
        "..klpppppppplk..",
        "..kllllllllllk..",
        "..kggggggggggk..",
        "...kkkkkkkkkk...",
        "......kmmk......",
        "......kmmk......",
        ".......kk.......",
        "................",
    ],
    "gb_link_cable": [
        "................",
        ".kkk............",
        ".kgk.....kkkk...",
        ".kgk...kkppppkk.",
        ".kgk..kpp....pk.",
        "..kp.kp.......pk",
        "..kp.kp......kpk",
        "..kp..kpp...kpk.",
        "...kp..kkpppk...",
        "....kp...kkk....",
        ".....kpp....kkk.",
        "......kkpp..kgk.",
        "........kkppkgk.",
        "..........kkkgk.",
        ".............kk.",
        "................",
    ],
    "n64_expansion_pak": [
        "................",
        "....kkkkkkkk....",
        "...krrrrrrrrk...",
        "...krwwwwwwrk...",
        "...krrrrrrrrk...",
        "...kddddddddk...",
        "...kdmmmmmmdk...",
        "...kdmkkkkmdk...",
        "...kdmmmmmmdk...",
        "...kddddddddk...",
        "...kddddddddk...",
        "...kddddddddk...",
        "...kdyyyyyydk...",
        "...kkkkkkkkkk...",
        "................",
        "................",
    ],
    "n64_controller_pak": [
        "................",
        "................",
        "...kkkkkkkkkk...",
        "...kggggggggk...",
        "...kgllllllgk...",
        "...kglmmmmlgk...",
        "...kgllllllgk...",
        "...kglmmmmlgk...",
        "...kgllllllgk...",
        "...kggggggggk...",
        "...kggggggggk...",
        "...kmmmmmmmmk...",
        "...kyyyyyyyyk...",
        "...kkkkkkkkkk...",
        "................",
        "................",
    ],
    "n64_rumble_pak": [
        "................",
        "...kkkkkkkkkk...",
        "...kddddddddk...",
        "...kdmmmmmmdk...",
        "...kdmrrrmmdk...",
        "...kdmrmmrmdk...",
        "...kdmrrrmmdk...",
        "...kdmrmrmmdk...",
        "...kdmrmmrmdk...",
        "...kdmmmmmmdk...",
        "...kddddddddk...",
        "...kddddddddk...",
        "...kdyyyyyydk...",
        "...kkkkkkkkkk...",
        "................",
        "................",
    ],
    "ps1_memory_card": [
        "................",
        "................",
        "..kkkkkkkkkk....",
        "..kgggggggggk...",
        "..kgwwwwwwwggk..",
        "..kgwrrbbwwwgk..",
        "..kgwwwwwwwwgk..",
        "..kgwmmmmmmwgk..",
        "..kgwwwwwwwwgk..",
        "..kggggggggggk..",
        "..kgmgmgmgmggk..",
        "..kggggggggggk..",
        "..kkkkkkkkkkkk..",
        "................",
        "................",
        "................",
    ],
    "ps1_multitap": [
        "................",
        "................",
        "..kkkkkkkkkkkk..",
        "..kggggggggggk..",
        "..kgkkgkkgkkgk..",
        "..kgkdgkdgkdgk..",
        "..kggggggggggk..",
        "..kgkkgkkgkkgk..",
        "..kgkmgkmgkmgk..",
        "..kggggggggggk..",
        "..kllllllllllk..",
        "..kkkkkkkkkkkk..",
        "......kddk......",
        "......kddk......",
        ".......kk.......",
        "................",
    ],
    "dc_vmu": [
        "................",
        "...kkkkkkkkkk...",
        "..kwwwwwwwwwwk..",
        "..kwkkkkkkkkwk..",
        "..kwknnnnnnkwk..",
        "..kwknnknnnkwk..",
        "..kwknnnnnnkwk..",
        "..kwkkkkkkkkwk..",
        "..kwwwwwwwwwwk..",
        "..kwwkwwwwwwwk..",
        "..kwkkkwwwkwwk..",
        "..kwwkwwwkwkwk..",
        "..kwwwwwwwwwwk..",
        "...kbbbbbbbbk...",
        "....kkkkkkkk....",
        "................",
    ],
    "dc_puru_puru": [
        "................",
        "................",
        "...kkkkkkkkkk...",
        "..kwwwwwwwwwwk..",
        "..kwwwwwwwwwwk..",
        "..kwwbbbbbbwwk..",
        "..kwbbwwwwbbwk..",
        "..kwbwwbbwwbwk..",
        "..kwbbwwwwbbwk..",
        "..kwwbbbbbbwwk..",
        "..kwwwwwwwwwwk..",
        "..klllllllllk...",
        "...kyyyyyyyyk...",
        "...kkkkkkkkkk...",
        "................",
        "................",
    ],
    "wii_nunchuk": [
        "................",
        "......kkkk......",
        ".....kwwwwk.....",
        "....kwkkkkwk....",
        "....kwkmmkwk....",
        "....kwkkkkwk....",
        "...kwwwwwwwwk...",
        "...kwwwwwwwwk...",
        "...kwwwwwwwwk...",
        "....kwwwwwwk....",
        ".....kwwwwk.....",
        "......kllk......",
        ".......kk.......",
        ".......km.......",
        "........km......",
        ".........kk.....",
    ],
    "wii_classic_controller": [
        "................",
        "................",
        "................",
        "..kkkkk..kkkkk..",
        ".kwwwwwkkwwwwwk.",
        "kwwkwwwwwwwwkwwk",
        "kwkkkwwwwwwkwkwk",
        "kwwkwwkkwwwwkwwk",
        "kwwwwkmkkmkwwwwk",
        "kwwwwkkkkkkwwwwk",
        ".kwwwwwwwwwwwwk.",
        "..kwwwk..kwwwk..",
        "...kkk....kkk...",
        "................",
        "................",
        "................",
    ],
}


def paint(name: str, rows: list[str]) -> Image.Image:
    if len(rows) != 16 or any(len(row) != 16 for row in rows):
        raise ValueError(f"{name}: art must be 16x16")
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                image.putpixel((x, y), ImageColor.getrgb(PALETTE[key]) + (255,))
    return image


def main() -> None:
    textures = ASSETS / "textures/item/component"
    models = ASSETS / "models/item"
    textures.mkdir(parents=True, exist_ok=True)
    for name, rows in ART.items():
        paint(name, rows).save(textures / f"{name}.png")
        model = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"retrocraft:item/component/{name}"},
        }
        (models / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")


if __name__ == "__main__":
    main()
