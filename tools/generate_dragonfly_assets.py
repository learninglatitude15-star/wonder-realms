#!/usr/bin/env python3
"""Generates the dragonfly: its GeckoLib model and wing-beat animation, and its skins (plain and saddled).

Run from the wonder-realms folder after generate_beetle_assets.py:  python3 tools/generate_dragonfly_assets.py
(The dragonfly's spawn egg is drawn with the others, in generate_dragon_assets.py.)
"""
import json
from pathlib import Path

from generate_dragon_assets import LEATHER, box_uv_size, faces, kf, noise, shade, write_png

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "wonderrealms"
NS = "wonderrealms"
TEX = 64

PALETTE = dict(dark=(0x0a, 0x3a, 0x48), base=(0x1a, 0x8a, 0xa0), light=(0x6a, 0xe0, 0xe0), eye=(0x7a, 0xf0, 0x5a),
               wing=(0xe0, 0xf0, 0xf8), vein=(0x9a, 0xb8, 0xc8), spot=(0x3a, 0x4a, 0x5a))

# --- Model: bone -> (parent, pivot, [(name, origin, size, kind, mirror)]). Pixels; the dragonfly faces -Z. ---------
# Four flat wings (cubes with no thickness), a short thorax to sit on, and a long, banded tail.
BONES = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 8, 0], [
        ("thorax", [-1.5, 7, -3], [3, 3, 4], "body", False),
        ("abdomen", [-1, 7.5, 1], [2, 2, 12], "tail", False),
    ]),
    ("head", "body", [0, 8.5, -3], [
        ("head", [-1.5, 7.5, -5], [3, 2, 2], "body", False),
        ("eye", [0.5, 8, -6], [2, 2, 2], "eye", False),
        ("eye", [-2.5, 8, -6], [2, 2, 2], "eye", True),
    ]),
    ("wing_front_left", "body", [1.5, 10, -1.5], [("wing_front", [1.5, 10, -2.5], [10, 0, 3], "wing", False)]),
    ("wing_back_left", "body", [1.5, 10, 1], [("wing_back", [1.5, 10, 0], [9, 0, 3], "wing", False)]),
    ("wing_front_right", "body", [-1.5, 10, -1.5], [("wing_front", [-11.5, 10, -2.5], [10, 0, 3], "wing", True)]),
    ("wing_back_right", "body", [-1.5, 10, 1], [("wing_back", [-10.5, 10, 0], [9, 0, 3], "wing", True)]),
]


def pack():
    """Gives each distinct cube name a texture spot (shelf packing). Mirrored cubes share their twin's spot."""
    spots, order = {}, []
    for _, _, _, cubes in BONES:
        for name, _, size, kind, _ in cubes:
            if name not in spots:
                order.append(name)
                spots[name] = (size, kind)
    placed, x, y, shelf = {}, 0, 0, 0
    for name in sorted(order, key=lambda n: -box_uv_size(spots[n][0])[1]):
        size, kind = spots[name]
        bw, bh = box_uv_size(size)
        if x + bw > TEX:
            x, y, shelf = 0, y + shelf, 0
        assert y + bh <= TEX, "texture full"
        placed[name] = (x, y, size, kind)
        x += bw
        shelf = max(shelf, bh)
    return placed


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
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.dragonfly", "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 3, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones,
        }],
    }


def animations():
    # The wings never stop: front and back pairs beat out of step, as a dragonfly's do.
    beat = lambda a: {"rotation": kf((0, [0, 0, a]), (0.06, [0, 0, -a]), (0.12, [0, 0, a]))}
    fly = {"wing_front_left": beat(30), "wing_back_left": beat(-30), "wing_front_right": beat(-30), "wing_back_right": beat(30)}
    return {"format_version": "1.8.0", "animations": {"move.fly": {"loop": True, "animation_length": 0.12, "bones": fly}}}


def paint_skin(placed):
    pal = PALETTE
    img = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
    for name, (u, v, size, kind) in placed.items():
        for face, (fx, fy, fw, fh) in faces(u, v, size).items():
            for yy in range(fh):
                for xx in range(fw):
                    n = noise(fx + xx, fy + yy, 17)
                    if kind == "wing":
                        c = pal["wing"]
                        if yy == 0 or xx % 3 == 0:
                            c = pal["vein"]  # the leading edge and the veins across
                        if xx >= fw - 2 and yy == 0:
                            c = pal["spot"]  # the dark spot near each wing tip
                    elif kind == "eye":
                        c = shade(pal["eye"], 0.85 + 0.3 * n)
                        if face == "top" and xx == 0 and yy == 0:
                            c = (0xf0, 0xff, 0xe0)
                    elif kind == "tail":
                        c = shade(pal["base"], 0.85 + 0.3 * n)
                        if face in ("east", "west", "top", "bottom") and xx % 3 == 2:
                            c = pal["dark"]  # the bands down its tail (along the length of the side faces)
                        if face in ("north", "south"):
                            c = pal["dark"]
                    else:
                        c = shade(pal["base"], 0.8 + 0.3 * n)
                        if n > 0.8:
                            c = pal["light"]  # the metallic shimmer
                    img[fy + yy][fx + xx] = (*c, 255)
    return img


def paint_saddle(img, placed):
    """A tiny leather saddle on the thorax, strapped round underneath."""
    img = [row[:] for row in img]
    u, v, size, _ = placed["thorax"]
    f = faces(u, v, size)
    fx, fy, fw, fh = f["top"]
    for yy in range(fh):
        for xx in range(fw):
            c = LEATHER["dark"] if yy in (0, fh - 1) else LEATHER["light"] if (xx + yy) % 3 else LEATHER["base"]
            img[fy + yy][fx + xx] = (*c, 255)
    for side in ("east", "west"):
        fx, fy, fw, fh = f[side]
        for yy in range(fh):
            img[fy + yy][fx + fw // 2] = (*(LEATHER["iron"] if yy == 1 else LEATHER["dark"]), 255)
    return img


def main():
    placed = pack()
    models = ASSETS / "geckolib" / "models" / "entity"
    models.mkdir(parents=True, exist_ok=True)
    (models / "dragonfly.geo.json").write_text(json.dumps(geometry(placed), indent=1))
    anims = ASSETS / "geckolib" / "animations" / "entity"
    anims.mkdir(parents=True, exist_ok=True)
    (anims / "dragonfly.animation.json").write_text(json.dumps(animations(), indent=1))
    skin = paint_skin(placed)
    write_png(ASSETS / "textures" / "entity" / "dragonfly.png", skin)
    write_png(ASSETS / "textures" / "entity" / "dragonfly_saddled.png", paint_saddle(skin, placed))
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang[f"entity.{NS}.dragonfly"] = "Dragonfly"
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Packed", len(placed), "dragonfly cube textures; wrote the model, its wing beat and 2 skins")


if __name__ == "__main__":
    main()
