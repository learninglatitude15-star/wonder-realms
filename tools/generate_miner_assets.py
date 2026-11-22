#!/usr/bin/env python3
"""Generates the Pixel Miner model, animations and skin, the Diamond/Emerald Pixel items, recipes and lang.

Run from the wonder-realms folder:  python3 tools/generate_miner_assets.py
The miner is modelled one block tall; the game shrinks it with the scale attribute.
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
TEX = 64

# bone -> (parent, pivot, [(cube name, origin, size, kind, mirror)]). Faces -Z.
BONES = [
    ("root", None, [0, 0, 0], []),
    ("leg_left", "root", [1, 5, 0], [("leg", [0, 0, -1], [2, 5, 2], "pants", False)]),
    ("leg_right", "root", [-1, 5, 0], [("leg", [-2, 0, -1], [2, 5, 2], "pants", True)]),
    ("body", "root", [0, 5, 0], [("body", [-2, 5, -1.5], [4, 5, 3], "shirt", False)]),
    ("head", "body", [0, 10, 0], [
        ("head", [-2.5, 10, -2.5], [5, 5, 5], "face", False),
        ("helmet", [-3, 13, -3], [6, 3, 6], "helmet", False),
        ("lamp", [-0.5, 14, -3.6], [1, 1, 1], "lamp", False),
    ]),
    ("arm_left", "body", [2.5, 9.5, 0], [("arm", [2, 5, -1], [2, 5, 2], "shirt", False)]),
    ("arm_right", "body", [-2.5, 9.5, 0], [("arm", [-4, 5, -1], [2, 5, 2], "shirt", True)]),
    ("pickaxe", "arm_right", [-3, 5.5, 0], [
        ("handle", [-3.5, 5, -5], [1, 1, 5], "wood", False),
        ("pick_head", [-3.5, 3.5, -6], [1, 4, 1], "iron", False),
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
        assert y + bh <= TEX
        placed[name] = (x, y, size, kind)
        x += bw
        shelf = max(shelf, bh)
    return placed


def skin(placed):
    """The miner's skin, painted by creature_skins (face under the helmet's brim, beard, braces, belt, boots, pick)."""
    return skins.paint_model(placed, TEX, skins.miner_texel)


def geometry(placed):
    bones = []
    for name, parent, pivot, cubes in BONES:
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
        "description": {"identifier": "geometry.pixel_miner", "texture_width": TEX, "texture_height": TEX,
                        "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}


def kf(*pairs):
    return {f"{t:.2f}": v for t, v in pairs}


def animations():
    swing = lambda a, length: {"rotation": kf((0, [a, 0, 0]), (length / 2, [-a, 0, 0]), (length, [a, 0, 0]))}
    idle = {"body": {"position": kf((0, [0, 0, 0]), (1.5, [0, 0.15, 0]), (3, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, -10, 0]), (1.5, [0, 10, 0]), (3, [0, -10, 0]))},
            "arm_right": {"rotation": [-20, 0, 0]}}
    walk = {"leg_left": swing(30, 0.6), "leg_right": swing(-30, 0.6), "arm_left": swing(-25, 0.6),
            "arm_right": {"rotation": [-20, 0, 0]}}
    mine = {"arm_right": {"rotation": kf((0, [-30, 0, 0]), (0.25, [-120, 0, 0]), (0.45, [-10, 0, 0]), (0.6, [-30, 0, 0]))},
            "body": {"rotation": kf((0, [5, 0, 0]), (0.25, [-5, 0, 0]), (0.45, [10, 0, 0]), (0.6, [5, 0, 0]))}}
    anim = lambda bones, length: {"loop": True, "animation_length": length, "bones": bones}
    return {"format_version": "1.8.0", "animations": {
        "misc.idle": anim(idle, 3), "move.walk": anim(walk, 0.6), "misc.mine": anim(mine, 0.6)}}


def main():
    placed = pack()
    geo_dir = ASSETS / "geckolib" / "models" / "entity"
    anim_dir = ASSETS / "geckolib" / "animations" / "entity"
    geo_dir.mkdir(parents=True, exist_ok=True)
    anim_dir.mkdir(parents=True, exist_ok=True)
    (geo_dir / "pixel_miner.geo.json").write_text(json.dumps(geometry(placed), indent=1))
    (anim_dir / "pixel_miner.animation.json").write_text(json.dumps(animations(), indent=1))
    write_png(ASSETS / "textures" / "entity" / "pixel_miner.png", skin(placed))

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang["entity.wonderrealms.pixel_miner"] = "Pixel Miner"
    gems = {"diamond_pixel": ("Diamond Pixel", (0xc8, 0xff, 0xf8), (0x4a, 0xe0, 0xd4), (0x1a, 0x8a, 0x84), "minecraft:diamond"),
            "emerald_pixel": ("Emerald Pixel", (0xa8, 0xff, 0xb8), (0x2a, 0xc8, 0x5a), (0x10, 0x7a, 0x30), "minecraft:emerald")}
    for item, (name, light, base, dark, result) in gems.items():
        write_png(ASSETS / "textures" / "item" / f"{item}.png", art.gem_icon(base))
        (ASSETS / "models" / "item" / f"{item}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}}, indent=2))
        (ASSETS / "items" / f"{item}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/{item}"}}, indent=2))
        (DATA / "recipe").mkdir(parents=True, exist_ok=True)
        (DATA / "recipe" / f"{item.replace('_pixel', '')}_from_pixels.json").write_text(json.dumps({
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [f"{NS}:{item}"] * 9, "result": {"id": result, "count": 1}}, indent=2))
        lang[f"item.{NS}.{item}"] = name
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Packed", len(placed), "miner cubes; wrote model, animations, skin, 2 pixel items and recipes")


if __name__ == "__main__":
    main()
