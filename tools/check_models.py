#!/usr/bin/env python3
"""Validate asset references, vanilla model limits, live LCD alignment and block outlines."""
import json
import math
import re
from pathlib import Path

from PIL import Image, ImageColor

from generate_models import foldable_pose

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets"
MODELS = ASSETS / "retrocraft/models"
VERIFIED_TEXTURES = set()


def resolve(ref, trail=()):
    if ref.startswith("minecraft:"):
        return {}
    if ref in trail:
        raise ValueError(f"Model parent cycle: {trail} -> {ref}")
    ns, path = ref.split(":")
    d = json.loads((ASSETS / ns / "models" / f"{path}.json").read_text())
    parent = resolve(d["parent"], trail + (ref,)) if "parent" in d else {}
    return {
        **parent,
        **d,
        "textures": {**parent.get("textures", {}), **d.get("textures", {})},
    }


def texture(d, ref):
    seen = set()
    while ref.startswith("#"):
        if ref in seen:
            raise ValueError("Texture alias cycle: " + ref)
        seen.add(ref)
        ref = d["textures"][ref[1:]]
    ns, path = ref.split(":")
    p = ASSETS / ns / "textures" / f"{path}.png"
    if not p.exists():
        raise ValueError(f"Missing texture {p}")
    if p not in VERIFIED_TEXTURES:
        with Image.open(p) as im:
            im.verify()
        VERIFIED_TEXTURES.add(p)
    return p


def main():
    count = 0
    for p in sorted(MODELS.rglob("*.json")):
        ref = "retrocraft:" + str(p.relative_to(MODELS).with_suffix(""))
        d = resolve(ref)
        count += 1
        for name, value in d.get("textures", {}).items():
            texture(d, value)
        for e in d.get("elements", []):
            for v in e["from"] + e["to"]:
                if not math.isfinite(v) or not -16 <= v <= 32:
                    raise ValueError(f"{ref}: coordinate {v} outside vanilla limits")
            if not all(e["from"][i] < e["to"][i] for i in range(3)):
                raise ValueError(f"{ref}: empty or inverted cuboid {e}")
            for f in e["faces"].values():
                texture(d, f["texture"])
                if any(
                    not math.isfinite(v) or not 0 <= v <= 16 for v in f.get("uv", [])
                ):
                    raise ValueError(f"{ref}: UV outside texture")
                if f.get("rotation", 0) not in (0, 90, 180, 270):
                    raise ValueError(f"{ref}: unsupported UV rotation")
            if e.get("rotation", {}).get("angle", 0) not in (-45, -22.5, 0, 22.5, 45):
                raise ValueError(f"{ref}: unsupported element rotation")
        for override in d.get("overrides", []):
            resolve(override["model"])
    for p in (ASSETS / "retrocraft/blockstates").glob("*.json"):
        for variants in json.loads(p.read_text())["variants"].values():
            for variant in variants if isinstance(variants, list) else [variants]:
                resolve(variant["model"])
    # The live video quad must be the actual glass bounds, not the old screen position.
    java = (
        ROOT / "src/main/java/com/sammwy/retrocraft/client/render/Thumbnails.java"
    ).read_text()
    screens = re.findall(r'"(\w+)", new float\[\] \{([^}]+)\}', java)
    if len(screens) != 10:
        raise ValueError("Could not find all ten live LCD rectangles")
    pose = foldable_pose()
    opening = 180 - abs(pose["lid"]["angle"] - pose["base"]["angle"])
    if not 110 <= opening <= 135 or not pose["base"]["angle"] < 0 < pose["lid"]["angle"]:
        raise ValueError("Foldable pose must form a natural opening toward the viewer")
    seen_screens = set()
    for name, values in screens:
        bottom = name in seen_screens
        seen_screens.add(name)
        rect = [float(v.strip().removesuffix("f")) for v in values.split(",")]
        d = resolve("retrocraft:item/" + name)
        glass = next(e for e in d["elements"] if e.get("name") == ("touch LCD glass" if bottom else "LCD glass"))
        if name in ("gba_sp", "nds", "3ds"):
            half = "base" if bottom else "lid"
            if glass.get("rotation") != pose[half]:
                raise ValueError(f"{name}: live {half} rotation does not match its glass")
        a, b = glass["from"], glass["to"]
        expected = [a[0], b[0], a[1], b[1], b[2]]
        if any(abs(x - y) > 0.0001 for x, y in zip(rect, expected)):
            raise ValueError(
                f"{name}: live picture does not fit glass {rect} != {expected}"
            )
        on = resolve("retrocraft:item/" + name + "_on")
        if d["elements"] != on["elements"]:
            raise ValueError(f"{name}: powered geometry changed")
        if d["textures"]["screen"] == on["textures"]["screen"]:
            raise ValueError(f"{name}: no powered LCD texture")
    # Check Java selection bounds against the actual model, including its rotation-sensitive depth.
    # Each console class passes its outline to its super constructor, inline or as a constant.
    console = ROOT / "src/main/java/com/sammwy/retrocraft/server/console"
    java = "\n".join(
        p.read_text()
        for p in [console / "ArcadeConsole.java", *sorted((console / "builtin").glob("*.java"))]
    )
    named = dict(re.findall(r"(\w+)\s*=\s*new Bounds\(([^)]+)\)", java))
    outlines = [
        (name, inline or named[variable])
        for name, inline, variable in re.findall(
            r'super\(\s*"(\w+)",\s*"[^"]*",\s*[\w.]+,\s*'
            r"(?:new Bounds\(([^)]+)\)|(?:\w+\.)?(\w+))",
            java,
        )
        if inline or variable in named
    ]
    arcades = ("arcade", "arcade_tetris", "arcade_space_invaders", "arcade_cabinet")
    outlines += [(name, named["CABINET"]) for name in arcades]
    checked = 0
    for name, values in outlines:
        bounds = [float(v.strip()) for v in values.split(",")]
        d = resolve("retrocraft:block/" + name)
        es = d["elements"]
        if name in arcades:
            roof = resolve("retrocraft:block/" + name + "_top")["elements"]
            es = es + [
                {
                    **e,
                    "from": [e["from"][0], e["from"][1] + 32, e["from"][2]],
                    "to": [e["to"][0], e["to"][1] + 32, e["to"][2]],
                }
                for e in roof
            ]
        x0 = min(e["from"][0] for e in es)
        z0 = min(e["from"][2] for e in es)
        x1 = max(e["to"][0] for e in es)
        z1 = max(e["to"][2] for e in es)
        y1 = max(e["to"][1] for e in es)
        if not (
            bounds[0] <= x0 + 0.0001
            and bounds[1] <= z0 + 0.0001
            and bounds[2] >= x1 - 0.0001
            and bounds[3] >= z1 - 0.0001
            and bounds[4] >= y1 - 0.0001
        ):
            raise ValueError(
                f"{name}: selection bounds clip the casing: {bounds} / {[x0,z0,x1,z1,y1]}"
            )
        checked += 1
    if checked != 14:
        raise ValueError(f"Expected outlines for 14 consoles, found {checked}")
    for name in ("ps1", "ps2", "dreamcast", "gamecube", "wii"):
        d = resolve("retrocraft:item/" + name + "_disc")
        if any(
            e["from"][0] < 8 < e["to"][0] and e["from"][1] < 8 < e["to"][1]
            for e in d["elements"]
        ):
            raise ValueError(f"{name}: geometry fills the disc center hole")
    # The disc color is a platform identifier across its whole shell, including the back.
    families = {
        "gamecube": "#1b1c23",
        "wii": "#78bee6",
        "ps2": "#5747a5",
        "ps1": "#eeeae2",
        "dreamcast": "#df783e",
    }
    for name, color in families.items():
        d = resolve("retrocraft:item/" + name + "_disc")
        face = d["elements"][0]["faces"]["north"]
        with Image.open(texture(d, face["texture"])) as im:
            uv = face["uv"]
            pixel = im.convert("RGB").getpixel(
                (
                    int((uv[0] + uv[2]) / 32 * im.width),
                    int((uv[1] + uv[3]) / 32 * im.height),
                )
            )
            if pixel != ImageColor.getrgb(color):
                raise ValueError(
                    f"{name}: the full disc shell lost its identifying color"
                )
    constants = dict(
        (k, float(v))
        for k, v in re.findall(
            r"(SCREEN_\w+)\s*=\s*(-?[\d.]+)f?",
            (
                ROOT
                / "src/main/java/com/sammwy/retrocraft/server/block/ArcadeCabinet.java"
            ).read_text(),
        )
    )
    for name in ("arcade", "arcade_tetris", "arcade_space_invaders"):
        d = resolve("retrocraft:block/" + name)
        glass = next(e for e in d["elements"] if e.get("name") == "CRT glass")
        expected = [
            constants[k] for k in ("SCREEN_X0", "SCREEN_X1", "SCREEN_Y0", "SCREEN_Y1")
        ]
        if [
            glass["from"][0],
            glass["to"][0],
            glass["from"][1],
            glass["to"][1],
        ] != expected:
            raise ValueError(
                f"{name}: live arcade CRT coordinates do not match its glass"
            )
        if glass["rotation"] != {
            "origin": [8, constants["SCREEN_Y0"], constants["SCREEN_Z"]],
            "axis": "x",
            "angle": constants["SCREEN_TILT"],
        }:
            raise ValueError(f"{name}: live CRT tilt does not match the baked glass")
        shaft = next(e for e in d["elements"] if e.get("name") == "joystick shaft")
        knob = next(e for e in d["elements"] if e.get("name") == "joystick ball lower")
        washer = [
            e
            for e in d["elements"]
            if e.get("name", "").startswith("joystick dust washer")
        ]
        if shaft["to"][1] < knob["from"][1] or shaft["from"][1] > max(
            e["to"][1] for e in washer
        ):
            raise ValueError(f"{name}: disconnected joystick shaft")
        if knob["to"][2] >= constants["SCREEN_Z"]:
            raise ValueError(f"{name}: joystick intrudes into the CRT")
        loot = json.loads(
            (
                ROOT
                / "src/main/resources/data/retrocraft/loot_table/blocks"
                / f"{name}.json"
            ).read_text()
        )
        if loot["pools"][0]["entries"][0]["name"] != "retrocraft:" + name:
            raise ValueError(f"{name}: breaking the skin drops a different cabinet")
    print(
        f"Validated {count} models, all blockstates and textures, 10 live LCDs (including 3 tilted lids and 2 touch screens at a {opening:g}-degree opening), 14 outlines, 5 colored disc rings and 3 arcade CRTs/joysticks/loot tables."
    )


if __name__ == "__main__":
    main()
