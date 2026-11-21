#!/usr/bin/env python3
"""Generates the creatures new in 1.4 besides the dragonfly: their GeckoLib models, animations and skins. The
grasshopper (plain and saddled), the firefly (with the glowmask that lights its tail) and the sky whale (plain and
saddled), plus the Firefly Jar block.

Run from the wonder-realms folder after generate_dragonfly_assets.py:  python3 tools/generate_creature_assets.py
(Their spawn eggs are drawn with the others, in generate_dragon_assets.py.)
"""
import json
import math
from pathlib import Path

from generate_dragon_assets import LEATHER, box_uv_size, faces, kf, noise, shade, write_png

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
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


def paint(placed, tex, painter):
    """Paints every face of every cube: painter(name, kind, face, x, y, width, height, noise) gives a colour or None."""
    img = [[(0, 0, 0, 0)] * tex for _ in range(tex)]
    for name, (u, v, size, kind) in placed.items():
        for face, (fx, fy, fw, fh) in faces(u, v, size).items():
            for yy in range(fh):
                for xx in range(fw):
                    c = painter(name, kind, face, xx, yy, fw, fh, noise(fx + xx, fy + yy, 23))
                    if c is not None:
                        img[fy + yy][fx + xx] = c if len(c) == 4 else (*c, 255)
    return img


def overpaint(img, placed, cube, face_names, painter):
    """A copy of a skin with some faces of one cube painted over: painter(face, x, y, width, height) or None to keep."""
    img = [row[:] for row in img]
    u, v, size, _ = placed[cube]
    for face, (fx, fy, fw, fh) in faces(u, v, size).items():
        if face not in face_names:
            continue
        for yy in range(fh):
            for xx in range(fw):
                c = painter(face, xx, yy, fw, fh)
                if c is not None:
                    img[fy + yy][fx + xx] = (*c, 255)
    return img


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
HOPPER = dict(base=(0x5a, 0xa0, 0x3a), dark=(0x2e, 0x5a, 0x1e), light=(0x9a, 0xd0, 0x5a), belly=(0xc8, 0xd8, 0x7a),
              wing=(0x7a, 0x96, 0x4a), vein=(0x4a, 0x62, 0x2a), eye=(0x3a, 0x2a, 0x1a), glint=(0xd0, 0xe0, 0xc0),
              leg=(0x4a, 0x86, 0x2e), spine=(0x9a, 0x3a, 0x2a))


def grasshopper_pixel(name, kind, face, xx, yy, fw, fh, n):
    p = HOPPER
    if kind == "wing":
        c = p["vein"] if (xx + yy) % 4 == 0 else p["wing"]
        return shade(c, 0.8 if face == "bottom" else 0.9 + 0.2 * n)
    if kind == "eye":
        return p["glint"] if xx == 0 and yy == 0 else shade(p["eye"], 0.9 + 0.3 * n)
    if kind == "antenna":
        return shade(p["dark"], 0.9 + 0.3 * n)
    if kind == "leg":
        return p["dark"] if yy == fh - 1 else shade(p["leg"], 0.9 + 0.2 * n)
    if kind == "thigh":
        if face == "bottom":
            return p["spine"] if xx % 2 == 0 else p["dark"]  # the spiny underside
        if face in ("east", "west"):
            return p["light"] if (xx + abs(yy - fh // 2)) % 3 == 0 else shade(p["base"], 0.9 + 0.2 * n)  # chevrons
        return shade(p["base"], 0.85 + 0.2 * n)
    if face == "bottom":
        return shade(p["belly"], 0.85 + 0.2 * n)
    if kind == "abdomen":
        c = shade(p["base"], 0.9 + 0.2 * n)
        if face in ("top", "east", "west") and (yy if face == "top" else xx) % 2 == 0:
            c = shade(p["dark"], 1.1)  # the segments down its tail
        return c
    if kind == "head":
        if face == "north" and yy >= fh - 2:
            return p["dark"] if xx in (fw // 2 - 1, fw // 2) else p["belly"]  # mouthparts
        return shade(p["base"], 0.95 + 0.2 * n)
    c = shade(p["base"], 0.9 + 0.2 * n)
    if face == "top" and xx in (fw // 2 - 1, fw // 2):
        c = p["light"]  # a pale stripe down its back
    return c


def grasshopper_saddle(img, placed):
    """A tiny saddle across the thorax and the front of the wings, strapped round underneath."""
    leather = lambda face, xx, yy, fw, fh: LEATHER["dark"] if yy in (0, fh - 1) else LEATHER["light"] if (xx + yy) % 3 else LEATHER["base"]
    img = overpaint(img, placed, "thorax", ("top",), leather)
    img = overpaint(img, placed, "thorax", ("east", "west"),
                    lambda face, xx, yy, fw, fh: (LEATHER["iron"] if yy == 1 else LEATHER["dark"]) if xx == fw // 2 else None)
    return img


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
FLY = dict(body=(0x2a, 0x22, 0x1e), shield=(0xe0, 0x6a, 0x3a), spot=(0x1a, 0x12, 0x10), wing=(0x5a, 0x4a, 0x3e),
           edge=(0xc8, 0xa0, 0x60), lantern=(0xd8, 0xf0, 0x70), bright=(0xf6, 0xff, 0xb0))


def firefly_pixel(name, kind, face, xx, yy, fw, fh, n):
    p = FLY
    if kind == "wing":
        return p["edge"] if xx in (0, fw - 1) else shade(p["wing"], 0.9 + 0.2 * n)
    if kind == "lantern":
        return p["bright"] if n > 0.7 else shade(p["lantern"], 0.9 + 0.15 * n)
    if kind == "head":
        return shade(p["body"], 1.0 + 0.4 * n)
    if face == "top":
        return p["spot"] if xx == fw // 2 and yy < 2 else p["shield"]  # the orange shield over its head
    return shade(p["body"], 0.9 + 0.3 * n)


def firefly_glow(name, kind, face, xx, yy, fw, fh, n):
    return firefly_pixel(name, kind, face, xx, yy, fw, fh, n) if kind == "lantern" else None


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
WHALE_PAL = dict(back=(0x2e, 0x44, 0x86), dark=(0x1e, 0x2c, 0x5a), light=(0x4a, 0x66, 0xb0), cloud=(0xe8, 0xf0, 0xff),
                 belly=(0xd8, 0xdc, 0xf0), groove=(0xa8, 0xb0, 0xd0), barnacle=(0xc8, 0xc4, 0xb8), eye=(0x10, 0x10, 0x18),
                 fin=(0x3a, 0x52, 0x9a))
BLANKET = dict(red=(0xb0, 0x20, 0x30), deep=(0x80, 0x14, 0x22), gold=(0xe8, 0xc0, 0x40))


def whale_pixel(name, kind, face, xx, yy, fw, fh, n):
    p = WHALE_PAL
    if kind == "eye":
        return (0xff, 0xff, 0xff) if xx == 0 and yy == 0 else p["eye"]
    if kind == "jaw" or face == "bottom":
        if face in ("east", "west") and yy == 0:
            return p["dark"]  # the long line of its mouth
        return p["groove"] if face == "bottom" and xx % 3 == 0 else shade(p["belly"], 0.92 + 0.12 * n)
    if kind == "fin":
        return p["light"] if n > 0.85 else shade(p["fin"], 0.9 + 0.2 * n)
    # The hide: deep blue, lighter down the flanks and pale toward the belly, with drifting cloud-white patches.
    patch = noise(xx // 3, yy // 3, len(name) * 7 + (0 if face == "top" else 3))
    if face in ("east", "west") and yy >= fh - 3:
        return shade(p["belly"], 0.95 + 0.1 * n)
    if patch > 0.86:
        return shade(p["cloud"], 0.95 + 0.08 * n)
    if face == "top" and name == "body" and n > 0.97:
        return p["barnacle"]
    c = shade(p["back"], 0.9 + 0.2 * n)
    if face in ("east", "west") and yy >= fh // 2:
        c = shade(p["light"], 0.9 + 0.15 * n)
    return c


# The Storm Leviathan wears the whale's shape in storm colours: charcoal and slate, thundercloud patches, and
# zigzag lightning scars down its back and flanks that glow (with its eyes) from the glowmask.
LEVIATHAN_PAL = dict(back=(0x2a, 0x2e, 0x3a), dark=(0x14, 0x16, 0x1e), light=(0x4a, 0x50, 0x64), cloud=(0x3e, 0x44, 0x56),
                     belly=(0x6a, 0x70, 0x84), groove=(0x4a, 0x50, 0x60), barnacle=(0x8a, 0x8a, 0x90), eye=(0xff, 0xe0, 0x40),
                     fin=(0x22, 0x26, 0x32))
BOLT = (0xff, 0xf0, 0x60)


def is_scar(name, kind, face, xx, yy):
    if kind != "hide" or face not in ("top", "east", "west"):
        return False
    zig = yy % 6 if (yy // 6) % 2 == 0 else 5 - yy % 6
    return (xx + zig) % 13 == 0


def leviathan_pixel(name, kind, face, xx, yy, fw, fh, n):
    if kind == "eye":
        return LEVIATHAN_PAL["eye"]
    if is_scar(name, kind, face, xx, yy):
        return BOLT
    global WHALE_PAL
    saved, WHALE_PAL = WHALE_PAL, LEVIATHAN_PAL
    try:
        return whale_pixel(name, kind, face, xx, yy, fw, fh, n)
    finally:
        WHALE_PAL = saved


def leviathan_glow(name, kind, face, xx, yy, fw, fh, n):
    if kind == "eye":
        return LEVIATHAN_PAL["eye"]
    return BOLT if is_scar(name, kind, face, xx, yy) else None


def whale_saddle(img, placed):
    """A long saddle blanket across the middle of its back, room for four, fringed with gold and strapped underneath."""
    u, v, (w, h, d), _ = placed["body"]
    start, end = d // 2 - 7, d // 2 + 7  # centred along its length

    def top(face, xx, yy, fw, fh):
        if not start <= yy < end:
            return None
        if yy in (start, end - 1) or xx in (0, fw - 1):
            return BLANKET["gold"]
        return BLANKET["deep"] if (xx // 2 + yy // 2) % 4 == 0 else BLANKET["red"]

    def side(face, xx, yy, fw, fh):
        if xx in (d // 2 - 1, d // 2):
            return LEATHER["iron"] if yy == 6 else LEATHER["dark"]  # the girth strap
        if not start <= xx < end or yy > 4:
            return None
        return BLANKET["gold"] if yy == 4 or xx in (start, end - 1) else BLANKET["red"]

    def belly(face, xx, yy, fw, fh):
        return LEATHER["dark"] if yy in (d // 2 - 1, d // 2) else None

    img = overpaint(img, placed, "body", ("top",), top)
    img = overpaint(img, placed, "body", ("east", "west"), side)
    return overpaint(img, placed, "body", ("bottom",), belly)


def whale_animations():
    swim = {"tail": {"rotation": wave(7, 4, 0.0)},
            "tail2": {"rotation": wave(11, 4, 0.12)},
            "fluke": {"rotation": wave(16, 4, 0.25)},
            "fin_left": {"rotation": wave(12, 4, 0.1, axis=2)},
            "fin_right": {"rotation": wave(-12, 4, 0.1, axis=2)},
            "body": {"rotation": wave(2, 4, 0.5), "position": wave(0.6, 4, 0.25, axis=1)}}
    return {"move.swim": {"loop": True, "animation_length": 4, "bones": swim}}


# --- The Firefly Jar --------------------------------------------------------------------------------------------
# A 16x16 texture: the jar's glass sides (clear but for the rim and a glint), its base, a cork lid, and the cloud of
# fireflies inside (bright dots on clear). Clear pixels make the block cut out, so you see the fireflies through it.

GLASS, GLINT, CORK, CORK_DARK = (0xd8, 0xf0, 0xf0, 255), (0xff, 0xff, 0xff, 255), (0xa8, 0x78, 0x48, 255), (0x6a, 0x4a, 0x2a, 255)
GLOW, GLOW_BRIGHT = (0xd8, 0xf0, 0x70, 255), (0xf6, 0xff, 0xb0, 255)


def jar_texture():
    img = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(8):  # side: 6 x 8 at (0, 0)
        for x in range(6):
            if x in (0, 5) or y in (0, 7):
                img[y][x] = GLASS
    img[2][1] = img[3][1] = GLINT
    for y in range(6):  # base: 6 x 6 at (6, 0)
        for x in range(6, 12):
            img[y][x] = GLASS
    for y in range(9, 16):  # lid top: 7 x 7 at (0, 9)
        for x in range(7):
            img[y][x] = CORK_DARK if x in (0, 6) or y in (9, 15) else CORK
    for y in range(9, 11):  # lid side: 7 x 2 at (7, 9)
        for x in range(7, 14):
            img[y][x] = CORK_DARK if y == 10 else CORK
    for x, y in ((12, 1), (14, 0), (15, 3), (13, 3), (12, 4), (14, 2)):  # the fireflies: 4 x 5 at (12, 0)
        img[y][x] = GLOW_BRIGHT if (x + y) % 2 else GLOW
    return img


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
    skin = paint(placed, GRASSHOPPER_TEX, grasshopper_pixel)
    write_png(ASSETS / "textures" / "entity" / "grasshopper.png", skin)
    write_png(ASSETS / "textures" / "entity" / "grasshopper_saddled.png", grasshopper_saddle(skin, placed))

    placed = pack(FIREFLY, FIREFLY_TEX)
    write_model("firefly", FIREFLY, placed, FIREFLY_TEX, (1, 1, 0.2), firefly_animations())
    write_png(ASSETS / "textures" / "entity" / "firefly.png", paint(placed, FIREFLY_TEX, firefly_pixel))
    write_png(ASSETS / "textures" / "entity" / "firefly_glowmask.png", paint(placed, FIREFLY_TEX, firefly_glow))

    placed = pack(WHALE, WHALE_TEX)
    write_model("sky_whale", WHALE, placed, WHALE_TEX, (6, 3, 1), whale_animations())
    skin = paint(placed, WHALE_TEX, whale_pixel)
    write_png(ASSETS / "textures" / "entity" / "sky_whale.png", skin)
    write_png(ASSETS / "textures" / "entity" / "sky_whale_saddled.png", whale_saddle(skin, placed))
    write_png(ASSETS / "textures" / "entity" / "storm_leviathan.png", paint(placed, WHALE_TEX, leviathan_pixel))
    write_png(ASSETS / "textures" / "entity" / "storm_leviathan_glowmask.png", paint(placed, WHALE_TEX, leviathan_glow))

    write_png(ASSETS / "textures" / "block" / "firefly_jar.png", jar_texture())
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
