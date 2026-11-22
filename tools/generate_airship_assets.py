#!/usr/bin/env python3
"""Generates the airship models (with and without a bow cannon), animations, skins (the classic striped balloon
and one per dye color), item icon, recipe and lang.

Run from the wonder-realms/tools folder:  python3 generate_airship_assets.py
"""
import json
import os
from pathlib import Path

import creature_skins as skins
import item_art as art
from generate_dragon_assets import box_uv_size, write_png

ROOT = Path(os.environ.get("WR_RESOURCES") or Path(__file__).resolve().parent.parent / "src" / "main" / "resources")
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"
TEX = 256

COLORS = {"wood": (0x9a, 0x6a, 0x3a), "darkwood": (0x5a, 0x3a, 0x20), "iron": (0x6a, 0x6e, 0x74),
          "rope": (0xc8, 0xa8, 0x70), "red": (0xc0, 0x30, 0x2a), "cream": (0xf0, 0xe4, 0xc8), "gunmetal": (0x2e, 0x30, 0x34)}

# Dye colors in Minecraft's id order, with their names: each gets a balloon striped in that color and a lighter tint.
DYES = [("white", 0xF9FFFE), ("orange", 0xF9801D), ("magenta", 0xC74EBD), ("light_blue", 0x3AB3DA), ("yellow", 0xFED83D),
        ("lime", 0x80C71F), ("pink", 0xF38BAA), ("gray", 0x474F52), ("light_gray", 0x9D9D97), ("cyan", 0x169C9C),
        ("purple", 0x8932B8), ("blue", 0x3C44AA), ("brown", 0x835432), ("green", 0x5E7C16), ("red", 0xB02E26), ("black", 0x1D1D21)]


def rgb(value):
    return (value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF)


def tint(color, amount):
    """Mixes a color toward cream (amount > 0) or toward black (amount < 0)."""
    target = COLORS["cream"] if amount > 0 else (0, 0, 0)
    a = abs(amount)
    return tuple(int(c + (t - c) * a) for c, t in zip(color, target))

# Faces -Z (the bow points north). Units are pixels.
BONES = [
    ("root", None, [0, 0, 0], []),
    ("hull", "root", [0, 0, 0], [
        ("floor", [-12, 0, -24], [24, 2, 48], "wood", False),
        ("keel", [-2, -2, -22], [4, 2, 44], "darkwood", False),
        ("side", [10, 2, -24], [2, 8, 48], "wood", False),
        ("side", [-12, 2, -24], [2, 8, 48], "wood", True),
        ("end", [-10, 2, -24], [20, 8, 2], "wood", False),
        ("end", [-10, 2, 22], [20, 8, 2], "wood", True),
        ("engine", [-4, 2, 13], [8, 7, 6], "iron", False),
        ("chimney", [-1, 9, 15], [2, 7, 2], "iron", False),
    ]),
    ("ropes", "root", [0, 10, 0], [
        ("rope", [10, 10, -19], [1, 28, 1], "rope", False),
        ("rope", [-11, 10, -19], [1, 28, 1], "rope", False),
        ("rope", [10, 10, 18], [1, 28, 1], "rope", False),
        ("rope", [-11, 10, 18], [1, 28, 1], "rope", False),
    ]),
    ("balloon", "root", [0, 49, 0], [("envelope", [-15, 38, -30], [30, 22, 60], "cloth", False)]),
    ("wheel", "hull", [0, 13, 19], [
        ("wheel_post", [-0.5, 2, 19], [1, 10, 1], "darkwood", False),
        ("spoke_h", [-3, 12.5, 18.5], [6, 1, 1], "wood", False),
        ("spoke_v", [-0.5, 10, 18.5], [1, 6, 1], "wood", False),
    ]),
    # Only in the cannon model: a gun carriage behind the bow wall, its barrel run out over the top.
    ("cannon", "hull", [0, 2, -20], [
        ("carriage", [-3, 2, -21], [6, 8, 4], "darkwood", False),
        ("barrel", [-1.5, 10, -31], [3, 3, 12], "gunmetal", False),
        ("muzzle", [-2, 9.5, -32], [4, 4, 1], "gunmetal", False),
    ]),
    ("propeller", "root", [0, 8, 30], [
        ("shaft", [-0.5, 7.5, 24], [1, 1, 6], "iron", False),
        ("blade_h", [-7, 7.5, 30], [14, 1, 1], "iron", False),
        ("blade_v", [-0.5, 1, 30], [1, 14, 1], "iron", False),
    ]),
]


def pack():
    spots, order = {}, []
    for _, _, _, cubes in BONES:
        for name, _, size, kind, _ in cubes:
            if name not in spots:
                spots[name] = (size, kind)
                order.append(name)
    placed, x, y, shelf = {}, 0, 0, 0
    for name in sorted(order, key=lambda n: -box_uv_size(spots[n][0])[1]):
        size, kind = spots[name]
        bw, bh = box_uv_size(size)
        if x + bw > TEX:
            x, y, shelf = 0, y + shelf, 0
        assert y + bh <= TEX, name
        placed[name] = (x, y, size, kind)
        x += bw
        shelf = max(shelf, bh)
    return placed


def skin(placed, stripes=(COLORS["red"], COLORS["cream"])):
    """The airship's skin, painted by creature_skins: shaded striped envelope with a rope net, planked hull, iron."""
    return skins.paint_model(placed, TEX, skins.airship_painter(stripes))


def geometry(placed, cannon=False):
    bones = []
    for name, parent, pivot, cubes in BONES:
        if name == "cannon" and not cannon:
            continue
        bone = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        if cubes:
            bone["cubes"] = []
            for cname, origin, size, _, mirror in cubes:
                u, v, _, _ = placed[cname]
                cube = {"origin": origin, "size": size, "uv": [u, v]}
                if mirror:
                    cube["mirror"] = True
                bone["cubes"].append(cube)
        bones.append(bone)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.airship", "texture_width": TEX, "texture_height": TEX,
                        "visible_bounds_width": 6, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}


def kf(*pairs):
    return {f"{t:.2f}": v for t, v in pairs}


def animations():
    bob = {"balloon": {"position": kf((0, [0, 0, 0]), (2, [0, 0.6, 0]), (4, [0, 0, 0]))}}
    idle = {**bob, "propeller": {"rotation": [0, 0, 0]}}
    fly = {"balloon": {"position": kf((0, [0, 0, 0]), (1, [0, 0.4, 0]), (2, [0, 0, 0])),
                       "rotation": kf((0, [1, 0, 0]), (1, [-1, 0, 0]), (2, [1, 0, 0]))},
           "propeller": {"rotation": kf((0, [0, 0, 0]), (0.2, [0, 0, 180]), (0.4, [0, 0, 360]))}}
    anim = lambda bones, length: {"loop": True, "animation_length": length, "bones": bones}
    return {"format_version": "1.8.0", "animations": {"misc.idle": anim(idle, 4), "move.fly": anim(fly, 0.4 if False else 2)}}


def main():
    placed = pack()
    (ASSETS / "geckolib" / "models" / "entity" / "airship.geo.json").write_text(json.dumps(geometry(placed), indent=1))
    (ASSETS / "geckolib" / "models" / "entity" / "airship_cannon.geo.json").write_text(json.dumps(geometry(placed, cannon=True), indent=1))
    # Propeller spins faster than the balloon sways: give it its own short loop inside a longer animation.
    anims = animations()
    fly = anims["animations"]["move.fly"]
    fly["bones"]["propeller"]["rotation"] = {f"{t:.2f}": [0, 0, a] for t, a in [(i * 0.1, (i % 4) * 90 + (360 * (i // 4))) for i in range(21)]}
    (ASSETS / "geckolib" / "animations" / "entity" / "airship.animation.json").write_text(json.dumps(anims, indent=1))
    write_png(ASSETS / "textures" / "entity" / "airship.png", skin(placed))
    for name, value in DYES:
        color = rgb(value)
        light = name in ("white", "light_gray", "yellow")
        write_png(ASSETS / "textures" / "entity" / f"airship_{name}.png", skin(placed, (color, tint(color, -0.25) if light else tint(color, 0.45))))

    write_png(ASSETS / "textures" / "item" / "airship.png", art.airship_icon())
    (ASSETS / "models" / "item" / "airship.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/airship"}}, indent=2))
    (ASSETS / "items" / "airship.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/airship"}}, indent=2))
    (DATA / "recipe" / "airship.json").write_text(json.dumps({
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["WWW", "SFS", "PBP"],
        "key": {"W": "#minecraft:wool", "S": "minecraft:string", "F": "minecraft:furnace", "P": "#minecraft:planks", "B": "#minecraft:boats"},
        "result": {"id": f"{NS}:airship", "count": 1}}, indent=2))

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang["entity.wonderrealms.airship"] = "Airship"
    lang["item.wonderrealms.airship"] = "Airship"
    lang["entity.wonderrealms.cannonball"] = "Cannonball"
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Packed", len(placed), "airship cubes; wrote 2 models, animations,", len(DYES) + 1, "skins, icon, recipe")


if __name__ == "__main__":
    main()
