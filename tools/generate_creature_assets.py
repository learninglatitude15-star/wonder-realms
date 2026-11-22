#!/usr/bin/env python3
"""Generates the creatures new in 1.4 besides the dragonfly: their GeckoLib models, animations and skins. The
grasshopper (plain and saddled), the firefly (with the glowmask that lights its tail) and the sky whale (plain and
saddled), plus the Firefly Jar block.

Run from the wonder-realms folder after generate_dragonfly_assets.py:  python3 tools/generate_creature_assets.py
(Their spawn eggs are drawn with the others, in generate_dragon_assets.py; their skins are painted by creature_skins.py.)
"""
import json
import os
import math
from pathlib import Path

import creature_skins as skins
from generate_dragon_assets import box_uv_size, kf, write_png

ROOT = Path(os.environ.get("WR_RESOURCES") or Path(__file__).resolve().parent.parent / "src" / "main" / "resources")
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"


# --- Shared: packing, geometry and painting ---------------------------------------------------------------------
# A model is a list of bones: (name, parent, pivot, [(cube name, origin, size, kind, mirror)]), in pixels, facing -Z.

def pack(bones, tex):
    """Gives each distinct cube name a spot on the texture (shelf packing). Mirrored cubes share their twin's spot."""
    spots, order = {}, []
    for _, _, _, cubes in bones:
        for name, _, size, kind, _ in cubes:
            if name not in spots:
                order.append(name)
                spots[name] = (size, kind)
    placed, x, y, shelf = {}, 0, 0, 0
    for name in sorted(order, key=lambda n: -box_uv_size(spots[n][0])[1]):
        size, kind = spots[name]
        bw, bh = box_uv_size(size)
        if x + bw > tex:
            x, y, shelf = 0, y + shelf, 0
        assert y + bh <= tex, f"texture full at {name}"
        placed[name] = (x, y, size, kind)
        x += bw
        shelf = max(shelf, bh)
    return placed


def geometry(identifier, bones, placed, tex, bounds):
    out = []
    for name, parent, pivot, cubes in bones:
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
        out.append(bone)
    width, height, offset = bounds
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{identifier}", "texture_width": tex, "texture_height": tex,
                        "visible_bounds_width": width, "visible_bounds_height": height, "visible_bounds_offset": [0, offset, 0]},
        "bones": out,
    }]}


def wave(amplitude, length, phase=0.0, steps=8, axis=0, offset=0.0):
    """A smooth looping swing: keyframes of a sine wave on one rotation axis."""
    frames = []
    for i in range(steps + 1):
        t = length * i / steps
        value = [0, 0, 0]
        value[axis] = round(offset + amplitude * math.sin(2 * math.pi * (t / length - phase)), 2)
        frames.append((t, value))
    return kf(*frames)


def write_model(name, bones, placed, tex, bounds, animations):
    models = ASSETS / "geckolib" / "models" / "entity"
    models.mkdir(parents=True, exist_ok=True)
    (models / f"{name}.geo.json").write_text(json.dumps(geometry(name, bones, placed, tex, bounds), indent=1))
    anims = ASSETS / "geckolib" / "animations" / "entity"
    anims.mkdir(parents=True, exist_ok=True)
    (anims / f"{name}.animation.json").write_text(json.dumps({"format_version": "1.8.0", "animations": animations}, indent=1))


# --- Grasshopper --------------------------------------------------------------------------------------------------
# A long green body under folded wings, a big blunt head with eyes and antennae, and great folded hind legs.

GRASSHOPPER_TEX = 64
GRASSHOPPER = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 5, 0], [
        ("thorax", [-2, 3, -4], [4, 4, 5], "body", False),
        ("abdomen", [-1.5, 3.5, 1], [3, 3, 8], "abdomen", False),
    ]),
    ("wings", "body", [0, 7, -2], [("wings", [-2, 6.5, -2], [4, 1, 11], "wing", False)]),
    ("head", "body", [0, 6, -4], [
        ("head", [-2, 3.5, -8], [4, 5, 4], "head", False),
        ("eye", [1.5, 6, -7.5], [1, 2, 2], "eye", False),
        ("eye", [-2.5, 6, -7.5], [1, 2, 2], "eye", True),
        ("antenna", [0.5, 8.5, -12], [1, 1, 5], "antenna", False),
        ("antenna", [-1.5, 8.5, -12], [1, 1, 5], "antenna", True),
    ]),
    ("leg_front_left", "body", [2, 4, -3], [("leg_front", [1.5, 0, -3.5], [1, 4, 1], "leg", False)]),
    ("leg_front_right", "body", [-2, 4, -3], [("leg_front", [-2.5, 0, -3.5], [1, 4, 1], "leg", True)]),
    ("leg_middle_left", "body", [2, 4, -1], [("leg_middle", [1.5, 0, -1.5], [1, 4, 1], "leg", False)]),
    ("leg_middle_right", "body", [-2, 4, -1], [("leg_middle", [-2.5, 0, -1.5], [1, 4, 1], "leg", True)]),
    ("leg_back_left", "body", [3, 6, 0], [
        ("thigh", [2, 4, -1], [2, 4, 7], "thigh", False),
        ("shin", [2.5, 0, 5], [1, 5, 1], "leg", False),
    ]),
    ("leg_back_right", "body", [-3, 6, 0], [
        ("thigh", [-4, 4, -1], [2, 4, 7], "thigh", True),
        ("shin", [-3.5, 0, 5], [1, 5, 1], "leg", True),
    ]),
]
def grasshopper_animations():
    stride = lambda a: {"rotation": kf((0, [a, 0, 0]), (0.2, [-a, 0, 0]), (0.4, [a, 0, 0]))}
    walk = {"leg_front_left": stride(25), "leg_front_right": stride(-25), "leg_middle_left": stride(-25), "leg_middle_right": stride(25),
            "leg_back_left": stride(6), "leg_back_right": stride(-6),
            "body": {"position": kf((0, [0, 0, 0]), (0.1, [0, 0.2, 0]), (0.2, [0, 0, 0]), (0.3, [0, 0.2, 0]), (0.4, [0, 0, 0]))}}
    idle = {"head": {"rotation": kf((0, [0, 0, 0]), (1.0, [-4, 3, 0]), (2.0, [0, 0, 0]), (3.0, [-3, -3, 0]), (4.0, [0, 0, 0]))},
            "body": {"position": kf((0, [0, 0, 0]), (2.0, [0, 0.15, 0]), (4.0, [0, 0, 0]))}}
    # In the air: hind legs flung out straight behind, front legs tucked, wings lifted and buzzing.
    hop = {"leg_back_left": {"rotation": kf((0, [-35, 0, 0]), (0.5, [-35, 0, 0]))},
           "leg_back_right": {"rotation": kf((0, [-35, 0, 0]), (0.5, [-35, 0, 0]))},
           **{f"leg_{place}_{side}": {"rotation": kf((0, [40, 0, 0]), (0.5, [40, 0, 0]))}
              for place in ("front", "middle") for side in ("left", "right")},
           "wings": {"rotation": kf((0, [-12, 0, 0]), (0.05, [-20, 0, 0]), (0.1, [-12, 0, 0]), (0.15, [-20, 0, 0]),
                                    (0.2, [-12, 0, 0]), (0.25, [-20, 0, 0]), (0.3, [-12, 0, 0]), (0.35, [-20, 0, 0]),
                                    (0.4, [-12, 0, 0]), (0.45, [-20, 0, 0]), (0.5, [-12, 0, 0]))}}
    return {"misc.idle": {"loop": True, "animation_length": 4, "bones": idle},
            "move.walk": {"loop": True, "animation_length": 0.4, "bones": walk},
            "move.hop": {"loop": True, "animation_length": 0.5, "bones": hop}}


# --- Firefly ------------------------------------------------------------------------------------------------------
# A little dark beetle with an orange shield over its head and a fat tail that glows (the glowmask lights it).

FIREFLY_TEX = 32
FIREFLY = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 2, 0], [
        ("thorax", [-1.5, 1, -2], [3, 2, 3], "body", False),
        ("lantern", [-1.5, 0.5, 1], [3, 2, 4], "lantern", False),
    ]),
    ("head", "body", [0, 2, -2], [("head", [-1, 1, -4], [2, 2, 2], "head", False)]),
    ("wing_left", "body", [0.5, 3, -1.5], [("wing", [0.5, 3, -1.5], [3, 0, 5], "wing", False)]),
    ("wing_right", "body", [-0.5, 3, -1.5], [("wing", [-3.5, 3, -1.5], [3, 0, 5], "wing", True)]),
]
def firefly_animations():
    beat = []
    for i in range(25):
        t = round(0.05 * i, 2)
        beat.append((t, 35 if i % 2 == 0 else -15))
    fly = {"wing_left": {"rotation": kf(*[(t, [0, 0, a]) for t, a in beat])},
           "wing_right": {"rotation": kf(*[(t, [0, 0, -a]) for t, a in beat])},
           "body": {"position": wave(0.4, 1.2, axis=1)}}
    return {"move.fly": {"loop": True, "animation_length": 1.2, "bones": fly}}


# --- Sky whale ----------------------------------------------------------------------------------------------------
# A great blue body speckled with cloud-white, a pale grooved belly, broad fins and a wide tail fluke.

WHALE_TEX = 128
WHALE = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 10, 0], [
        ("body", [-11, 2, -18], [22, 16, 32], "hide", False),
        ("jaw", [-10, 0, -18], [20, 2, 24], "jaw", False),
        ("eye", [11, 8, -14], [1, 2, 2], "eye", False),
        ("eye", [-12, 8, -14], [1, 2, 2], "eye", True),
    ]),
    ("fin_left", "body", [11, 7, -6], [("fin", [11, 6, -9], [12, 2, 7], "fin", False)]),
    ("fin_right", "body", [-11, 7, -6], [("fin", [-23, 6, -9], [12, 2, 7], "fin", True)]),
    ("tail", "body", [0, 10, 14], [("tail1", [-7, 4, 14], [14, 11, 14], "hide", False)]),
    ("tail2", "tail", [0, 9, 28], [("tail2", [-4, 6, 28], [8, 7, 12], "hide", False)]),
    ("fluke", "tail2", [0, 9, 40], [("fluke", [-13, 8, 39], [26, 2, 10], "fin", False)]),
]
def whale_animations():
    swim = {"tail": {"rotation": wave(7, 4, 0.0)},
            "tail2": {"rotation": wave(11, 4, 0.12)},
            "fluke": {"rotation": wave(16, 4, 0.25)},
            "fin_left": {"rotation": wave(12, 4, 0.1, axis=2)},
            "fin_right": {"rotation": wave(-12, 4, 0.1, axis=2)},
            "body": {"rotation": wave(2, 4, 0.5), "position": wave(0.6, 4, 0.25, axis=1)}}
    return {"move.swim": {"loop": True, "animation_length": 4, "bones": swim}}


def jar_model():
    def face(uv, texture="#jar"):
        return {"uv": uv, "texture": texture}
    side = [0, 0, 6, 8]
    return {
        "parent": "minecraft:block/block",
        "textures": {"jar": f"{NS}:block/firefly_jar", "particle": f"{NS}:block/firefly_jar"},
        "elements": [
            {"from": [5, 0, 5], "to": [11, 8, 11],
             "faces": {"down": {**face([6, 0, 12, 6]), "cullface": "down"}, "north": face(side), "south": face(side),
                       "west": face(side), "east": face(side)}},
            {"from": [6, 1, 6], "to": [10, 6, 10],
             "faces": {d: face([12, 0, 16, 5]) for d in ("north", "south", "west", "east")}},
            {"from": [4.5, 8, 4.5], "to": [11.5, 10, 11.5],
             "faces": {"up": face([0, 9, 7, 16]), "down": face([0, 9, 7, 16]),
                       **{d: face([7, 9, 14, 11]) for d in ("north", "south", "west", "east")}}},
        ],
    }


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    placed = pack(GRASSHOPPER, GRASSHOPPER_TEX)
    write_model("grasshopper", GRASSHOPPER, placed, GRASSHOPPER_TEX, (2, 1.5, 0.5), grasshopper_animations())
    skin = skins.paint_model(placed, GRASSHOPPER_TEX, skins.grasshopper_texel)
    write_png(ASSETS / "textures" / "entity" / "grasshopper.png", skin)
    write_png(ASSETS / "textures" / "entity" / "grasshopper_saddled.png", skins.grasshopper_saddle(skin, placed))

    placed = pack(FIREFLY, FIREFLY_TEX)
    write_model("firefly", FIREFLY, placed, FIREFLY_TEX, (1, 1, 0.2), firefly_animations())
    write_png(ASSETS / "textures" / "entity" / "firefly.png", skins.paint_model(placed, FIREFLY_TEX, skins.firefly_texel))
    write_png(ASSETS / "textures" / "entity" / "firefly_glowmask.png", skins.paint_model(placed, FIREFLY_TEX, skins.firefly_glow))

    placed = pack(WHALE, WHALE_TEX)
    write_model("sky_whale", WHALE, placed, WHALE_TEX, (6, 3, 1), whale_animations())
    skin, leviathan, glow = skins.whale_skins(placed, WHALE_TEX)
    write_png(ASSETS / "textures" / "entity" / "sky_whale.png", skin)
    write_png(ASSETS / "textures" / "entity" / "sky_whale_saddled.png", skins.whale_saddle(skin, placed))
    write_png(ASSETS / "textures" / "entity" / "storm_leviathan.png", leviathan)
    write_png(ASSETS / "textures" / "entity" / "storm_leviathan_glowmask.png", glow)

    write_png(ASSETS / "textures" / "block" / "firefly_jar.png", skins.jar_texture())
    dump(ASSETS / "models" / "block" / "firefly_jar.json", jar_model())
    dump(ASSETS / "blockstates" / "firefly_jar.json", {"variants": {"": {"model": f"{NS}:block/firefly_jar"}}})
    dump(ASSETS / "items" / "firefly_jar.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/firefly_jar"}})

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang.update({f"entity.{NS}.grasshopper": "Grasshopper", f"entity.{NS}.firefly": "Firefly",
                 f"entity.{NS}.sky_whale": "Sky Whale", f"block.{NS}.firefly_jar": "Firefly Jar",
                 f"entity.{NS}.storm_leviathan": "Storm Leviathan"})
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Wrote the grasshopper, firefly, sky whale and Storm Leviathan (models, animations, 6 skins and 2 glowmasks) and the Firefly Jar")


if __name__ == "__main__":
    main()
