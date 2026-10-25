#!/usr/bin/env python3
"""Render the committed Minecraft JSON/PNG assets without launching the game.

Development only: Python 3 + Pillow + numpy. This is an orthographic software rasterizer,
not Minecraft: use it for silhouettes, UVs and contact sheets, not in-game lighting.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets"


def load_model(ref):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    p = ASSETS / ns / "models" / f"{path}.json"
    if not p.exists():
        return {}
    d = json.loads(p.read_text())
    parent = load_model(d["parent"]) if "parent" in d else {}
    return {
        **parent,
        **d,
        "textures": {**parent.get("textures", {}), **d.get("textures", {})},
        "display": {**parent.get("display", {}), **d.get("display", {})},
    }


def rotation(axis, angle):
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    return np.array(
        {
            "x": [[1, 0, 0], [0, c, -s], [0, s, c]],
            "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]],
            "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]],
        }[axis]
    )


def faces(e):
    x0, y0, z0 = e["from"]
    x1, y1, z1 = e["to"]
    # Each face is ordered top-left, bottom-left, bottom-right, top-right in UV space.
    return {
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
    }


def render(ref, size=400, view="front", powered=False):
    if powered and ref.startswith("retrocraft:item/mame") and ref.endswith("_console"):
        d = load_model(ref)
        d["textures"].update(
            load_model(ref.replace(":item/", ":block/") + "_on")["textures"]
        )
    else:
        d = load_model(ref + ("_on" if powered else ""))
    if not d.get("elements"):
        raise ValueError(f"No cuboids: {ref}")
    south = ":item/" in ref and not ref.endswith("_console")
    eye = np.array([0.35, 0.2, 1.0] if south else [0.75, 0.85, -1.0])
    if view == "back":
        eye[2] *= -1
    if view == "straight":
        eye = np.array([0, 0, 1 if south else -1], dtype=float)
    if view == "side":
        eye = np.array([1, 0, 0], dtype=float)
    eye /= np.linalg.norm(eye)
    right = np.cross([0, 1, 0], eye)
    right /= np.linalg.norm(right)
    up = np.cross(eye, right)
    basis = np.array([right, up, eye])
    quads = []
    textures = {}
    for e in d["elements"]:
        for face, vertices in faces(e).items():
            f = e.get("faces", {}).get(face)
            if f is None:
                continue
            pos = np.array(vertices, dtype=float)
            if "rotation" in e:
                r = e["rotation"]
                origin = np.array(r["origin"])
                pos = (pos - origin) @ rotation(r["axis"], r["angle"]).T + origin
            normal = np.cross(pos[1] - pos[0], pos[2] - pos[0])
            normal /= np.linalg.norm(normal)
            if np.dot(normal, eye) < 0:
                continue
            tex = f["texture"]
            seen = set()
            while tex.startswith("#"):
                if tex in seen:
                    raise ValueError(f"Texture cycle: {tex}")
                seen.add(tex)
                tex = d["textures"][tex[1:]]
            if tex not in textures:
                ns, path = tex.split(":")
                textures[tex] = np.array(
                    Image.open(ASSETS / ns / "textures" / f"{path}.png").convert("RGBA")
                )
            uv = f.get("uv", [0, 0, 16, 16])
            u0, v0, u1, v1 = np.array(uv) / 16
            coords = np.array([(u0, v0), (u0, v1), (u1, v1), (u1, v0)])
            if "rotation" in f:
                coords = np.roll(coords, -f["rotation"] // 90, axis=0)
            light = (
                1
                if e.get("shade") is False
                else {
                    "up": 1,
                    "down": 0.5,
                    "north": 0.82,
                    "south": 0.82,
                    "east": 0.65,
                    "west": 0.65,
                }[face]
            )
            quads.append((pos @ basis.T, coords, textures[tex], light))
    allpos = np.concatenate([q[0] for q in quads])
    low = allpos.min(axis=0)
    high = allpos.max(axis=0)
    scale = (size - 48) / max(high[0] - low[0], high[1] - low[1])
    center = (low + high) / 2
    canvas = np.full((size, size, 4), [229, 233, 237, 255], dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    for pos, uv, tex, light in quads:
        p = pos.copy()
        p[:, :2] = (p[:, :2] - center[:2]) * scale + size / 2
        p[:, 1] = size - p[:, 1]
        for ids in [(0, 1, 2), (0, 2, 3)]:
            a, b, c = p[list(ids)]
            t = uv[list(ids)]
            x0 = max(0, int(min(a[0], b[0], c[0])))
            x1 = min(size - 1, int(max(a[0], b[0], c[0])) + 1)
            y0 = max(0, int(min(a[1], b[1], c[1])))
            y1 = min(size - 1, int(max(a[1], b[1], c[1])) + 1)
            yy, xx = np.mgrid[y0:y1, x0:x1]
            xx = xx + 0.5
            yy = yy + 0.5
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-8:
                continue
            wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / den
            wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / den
            wc = 1 - wa - wb
            z = wa * a[2] + wb * b[2] + wc * c[2]
            mask = (
                (wa >= -1e-7)
                & (wb >= -1e-7)
                & (wc >= -1e-7)
                & (z > depth[y0:y1, x0:x1])
            )
            u = wa * t[0, 0] + wb * t[1, 0] + wc * t[2, 0]
            v = wa * t[0, 1] + wb * t[1, 1] + wc * t[2, 1]
            tx = np.clip((u * tex.shape[1]).astype(int), 0, tex.shape[1] - 1)
            ty = np.clip((v * tex.shape[0]).astype(int), 0, tex.shape[0] - 1)
            colors = tex[ty, tx].copy()
            mask &= colors[:, :, 3] > 0
            colors[:, :, :3] = (colors[:, :, :3] * light).astype(np.uint8)
            canvas[y0:y1, x0:x1][mask] = colors[mask]
            depth[y0:y1, x0:x1][mask] = z[mask]
    return Image.fromarray(canvas)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", type=Path, default=ROOT / "tmp/model-previews")
    p.add_argument("--view", choices=["front", "back", "straight", "side"], default="front")
    p.add_argument("--powered", action="store_true")
    a = p.parse_args()
    a.output.mkdir(parents=True, exist_ok=True)
    groups = {
        "consoles": [
            ("NES", "block/nes_console"),
            ("SNES", "block/snes_console"),
            ("Mega Drive", "block/megadrive_console"),
            ("Genesis", "block/genesis_console"),
            ("Nintendo 64", "block/n64_console"),
            ("PlayStation", "block/ps1_console"),
            ("PlayStation 2", "block/ps2_console"),
            ("Dreamcast", "block/dreamcast_console"),
            ("GameCube", "block/gamecube_console"),
            ("Wii", "block/wii_console"),
            ("Generic arcade", "item/mame_console"),
            ("Tetris arcade", "item/mame_tetris_console"),
            ("Space Invaders arcade", "item/mame_space_invaders_console"),
        ],
        "arcades": [
            ("Generic", "item/mame_console"),
            ("Pac-Man", "item/mame_pacman_console"),
            ("Donkey Kong", "item/mame_donkey_kong_console"),
            ("Galaga", "item/mame_galaga_console"),
            ("Street Fighter", "item/mame_street_fighter_console"),
            ("Metal Slug", "item/mame_metal_slug_console"),
            ("Mortal Kombat", "item/mame_mortal_kombat_console"),
            ("Bubble Bobble", "item/mame_bubble_bobble_console"),
            ("Tetris", "item/mame_tetris_console"),
            ("Space Invaders", "item/mame_space_invaders_console"),
        ],
        "handhelds": [
            (s.upper(), "item/" + s + "_handheld") for s in ("gb", "gbc", "gba", "gba_sp", "nds", "3ds", "nes")
        ],
        "games": [
            (s.upper(), "item/" + s)
            for s in (
                "nes",
                "snes",
                "gb",
                "gbc",
                "gba",
                "nds",
                "3ds",
                "megadrive",
                "n64",
                "mame",
                "ps1",
                "ps2",
                "dreamcast",
                "gamecube",
                "wii",
            )
        ],
    }
    for group, models in groups.items():
        if a.powered and group == "games":
            continue
        cols = 3 if group == "arcades" else 4
        cell = 420 if group == "arcades" else 320
        sheet = Image.new(
            "RGB", (cols * cell, math.ceil(len(models) / cols) * (cell + 30)), "#e5e9ed"
        )
        draw = ImageDraw.Draw(sheet)
        for i, (name, ref) in enumerate(models):
            # Arcade inventory contains the roof; the powered world variant has only the lower body.
            on = a.powered
            im = render("retrocraft:" + ref, 640, a.view, on)
            im.save(a.output / f'{ref.split("/")[-1]}.png')
            sheet.paste(
                im.resize((cell, cell), Image.Resampling.LANCZOS),
                ((i % cols) * cell, (i // cols) * (cell + 30)),
            )
            draw.text(
                ((i % cols) * cell + 12, (i // cols) * (cell + 30) + cell),
                name,
                fill="#242b37",
            )
        sheet.save(a.output / f"{group}.png")
    print(a.output)


if __name__ == "__main__":
    main()
