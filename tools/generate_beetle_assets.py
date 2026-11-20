#!/usr/bin/env python3
"""Generates the rhino beetle: its GeckoLib model and animations, and its skins (plain and saddled).

Run from the wonder-realms folder after generate_dragon_assets.py:  python3 tools/generate_beetle_assets.py
(The beetle's spawn egg is drawn with the others, in generate_dragon_assets.py.)
"""
import json
from pathlib import Path

from generate_dragon_assets import LEATHER, box_uv_size, faces, kf, noise, shade, write_png

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "wonderrealms"
NS = "wonderrealms"
TEX = 64

PALETTE = dict(dark=(0x1e, 0x12, 0x0a), base=(0x3a, 0x22, 0x12), light=(0x6a, 0x42, 0x24), shine=(0xa8, 0x80, 0x58),
               belly=(0x5a, 0x3a, 0x22), horn=(0x24, 0x18, 0x10), leg=(0x2a, 0x1a, 0x10), eye=(0xd8, 0xa0, 0x30))

# --- Model: bone -> (parent, pivot, [(name, origin, size, kind, mirror)]). Pixels; the beetle faces -Z. -------------
# Six legs, each an L: a thigh out from the body and a shin down to the ground. Front and back legs swing together
# with the opposite middle leg (a tripod gait); the walk animation does the rest.
def legs():
    bones = []
    for side, sign in (("left", 1), ("right", -1)):
        for place, z, thigh in (("front", -5, "thigh_front"), ("middle", 0, "thigh"), ("back", 5, "thigh")):
            length = 4 if thigh == "thigh_front" else 5
            hip_x = 4 * sign
            thigh_origin = [4, 4, z] if sign > 0 else [-4 - length, 4, z]
            shin_origin = [3 + length, 0, z] if sign > 0 else [-4 - length, 0, z]
            bones.append((f"leg_{place}_{side}", "body", [hip_x, 5, z + 0.5], [
                (thigh, thigh_origin, [length, 1, 1], "leg", sign < 0),
                ("shin", shin_origin, [1, 4, 1], "leg", sign < 0),
            ]))
    return bones


BONES = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 4, 0], [
        ("shell", [-5, 4, -2], [10, 5, 11], "shell", False),  # the wing cases
        ("pronotum", [-4, 4, -6], [8, 4, 4], "shell", False),  # the shield behind the head
        ("belly", [-4, 3, -5], [8, 1, 13], "belly", False),
        ("thorn", [-1, 8, -6], [2, 2, 3], "horn", False),  # the little horn on the shield
    ]),
    ("head", "body", [0, 6, -6], [
        ("head", [-2.5, 4, -9], [5, 3, 3], "head", False),
        ("horn", [-1, 5, -13], [2, 2, 4], "horn", False),
        ("horn_tip", [-1, 7, -13], [2, 4, 2], "horn", False),  # the great horn curves up at the end
    ]),
    *legs(),
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
            "description": {"identifier": "geometry.rhino_beetle", "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 3, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones,
        }],
    }


def animations():
    stride = lambda a: {"rotation": kf((0, [0, a, 0]), (0.2, [0, -a, 0]), (0.4, [0, a, 0]))}
    walk = {f"leg_{place}_{side}": stride(22 if place != "middle" else -22)
            for place in ("front", "middle", "back") for side in ("left", "right")}
    walk["head"] = {"rotation": kf((0, [0, 3, 0]), (0.2, [0, -3, 0]), (0.4, [0, 3, 0]))}
    walk["body"] = {"position": kf((0, [0, 0, 0]), (0.1, [0, 0.2, 0]), (0.2, [0, 0, 0]), (0.3, [0, 0.2, 0]), (0.4, [0, 0, 0]))}
    idle = {"head": {"rotation": kf((0, [0, 0, 0]), (1.5, [-5, 0, 0]), (3, [0, 0, 0]))},
            "body": {"position": kf((0, [0, 0, 0]), (1.5, [0, 0.15, 0]), (3, [0, 0, 0]))}}
    return {"format_version": "1.8.0", "animations": {
        "misc.idle": {"loop": True, "animation_length": 3, "bones": idle},
        "move.walk": {"loop": True, "animation_length": 0.4, "bones": walk},
    }}


def paint_skin(placed):
    pal = PALETTE
    img = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
    for name, (u, v, size, kind) in placed.items():
        for face, (fx, fy, fw, fh) in faces(u, v, size).items():
            for yy in range(fh):
                for xx in range(fw):
                    n = noise(fx + xx, fy + yy, 13)
                    if kind == "shell":
                        c = shade(pal["base"], 0.9 + 0.2 * n)
                        if face == "top":
                            if xx == fw // 2 or xx == fw // 2 - 1:
                                c = pal["dark"]  # the seam where the wing cases meet
                            elif (xx == 2 or xx == fw - 3) and 1 <= yy <= fh - 3:
                                c = pal["shine"] if n > 0.4 else pal["light"]  # a glossy highlight down each case
                        elif yy == 0 or face == "bottom":
                            c = pal["dark"]
                    elif kind == "horn":
                        c = shade(pal["horn"], 0.9 + 0.3 * n)
                        if face == "top" and n > 0.6:
                            c = pal["light"]
                    elif kind == "head":
                        c = shade(pal["dark"], 1.0 + 0.4 * n)
                    elif kind == "belly":
                        c = shade(pal["belly"], 0.85 + 0.2 * n)
                        if yy % 3 == 0:
                            c = shade(pal["belly"], 0.7)  # the segments of the underside
                    else:
                        c = shade(pal["leg"], 0.85 + 0.3 * n)
                    img[fy + yy][fx + xx] = (*c, 255)
        if kind == "head":
            for face in ("east", "west"):
                fx, fy, fw, fh = faces(u, v, size)[face]
                img[fy + 1][fx + 1] = (*pal["eye"], 255)
    return img


def paint_saddle(img, placed):
    """A small leather saddle in the middle of the wing cases, strapped round under the belly."""
    img = [row[:] for row in img]
    u, v, size, _ = placed["shell"]
    f = faces(u, v, size)
    w, h, d = (int(round(n)) for n in size)
    lo, hi = d // 2 - 3, d // 2 + 2
    fx, fy, _, _ = f["top"]
    for z in range(lo, hi + 1):
        for x in range(1, w - 1):
            c = LEATHER["dark"] if z in (lo, hi) or x in (1, w - 2) else LEATHER["light"] if (x + z) % 4 else LEATHER["base"]
            img[fy + z][fx + x] = (*c, 255)
    for side in ("east", "west"):
        fx, fy, _, _ = f[side]
        for y in range(h):
            for z in (d // 2 - 1, d // 2):
                img[fy + y][fx + z] = (*(LEATHER["iron"] if y == 2 else LEATHER["dark"]), 255)
    return img


def main():
    placed = pack()
    models = ASSETS / "geckolib" / "models" / "entity"
    models.mkdir(parents=True, exist_ok=True)
    (models / "rhino_beetle.geo.json").write_text(json.dumps(geometry(placed), indent=1))
    anims = ASSETS / "geckolib" / "animations" / "entity"
    anims.mkdir(parents=True, exist_ok=True)
    (anims / "rhino_beetle.animation.json").write_text(json.dumps(animations(), indent=1))
    skin = paint_skin(placed)
    write_png(ASSETS / "textures" / "entity" / "rhino_beetle.png", skin)
    write_png(ASSETS / "textures" / "entity" / "rhino_beetle_saddled.png", paint_saddle(skin, placed))
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang[f"entity.{NS}.rhino_beetle"] = "Rhino Beetle"
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Packed", len(placed), "beetle cube textures; wrote the model, 2 animations and 2 skins")


if __name__ == "__main__":
    main()
