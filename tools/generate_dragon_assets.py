#!/usr/bin/env python3
"""Generates the dragon model, animations, skins (plain and saddled), egg textures/models/blockstates, spawn eggs
(the dragons' and the Pixel Miner's) and their lang entries.

Run from the wonder-realms folder:  python3 tools/generate_dragon_assets.py

The model is built from boxes (Bedrock geometry, as GeckoLib reads it). Each box gets a spot on the texture via
simple shelf packing, and every face is painted from the element's palette with a scale pattern, so changing the
model or a palette regenerates everything consistently.
"""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "wonderrealms"
NS = "wonderrealms"
TEX = 128

# --- Palettes: dark scale, base scale, light scale, belly, membrane, horn, eye --------------------------------
PALETTES = {
    "ember": dict(dark=(0x6a, 0x12, 0x0c), base=(0xb8, 0x2a, 0x16), light=(0xe4, 0x5a, 0x24), belly=(0xf0, 0xa8, 0x3a),
                  membrane=(0xd8, 0x6a, 0x22), horn=(0x3a, 0x22, 0x18), eye=(0xff, 0xe0, 0x40)),
    "frost": dict(dark=(0x4a, 0x7e, 0xa8), base=(0x8c, 0xc4, 0xe4), light=(0xd8, 0xf2, 0xff), belly=(0xf2, 0xfb, 0xff),
                  membrane=(0xb4, 0xe0, 0xf4), horn=(0xe8, 0xf4, 0xfa), eye=(0x40, 0xd8, 0xff)),
    "ender": dict(dark=(0x0c, 0x08, 0x12), base=(0x1c, 0x16, 0x26), light=(0x3a, 0x2a, 0x4e), belly=(0x6a, 0x4a, 0x8a),
                  membrane=(0x2a, 0x1a, 0x3a), horn=(0x2a, 0x24, 0x30), eye=(0xd8, 0x6a, 0xff)),
    "storm": dict(dark=(0x1c, 0x1a, 0x2e), base=(0x3a, 0x36, 0x5c), light=(0x6a, 0x5e, 0x9a), belly=(0x9a, 0x8e, 0xc0),
                  membrane=(0x52, 0x46, 0x86), horn=(0xe8, 0xd8, 0x50), eye=(0xff, 0xf2, 0x60)),
    "verdant": dict(dark=(0x1a, 0x44, 0x20), base=(0x3a, 0x86, 0x3a), light=(0x8c, 0xcc, 0x58), belly=(0xdc, 0xe8, 0xa0),
                    membrane=(0xd0, 0x7c, 0xb8), horn=(0x6a, 0x48, 0x28), eye=(0xff, 0xc8, 0x40)),
}

# --- Model: bone -> (parent, pivot, [(name, origin, size, kind, mirror)]) ---------------------------------------
# Units are pixels (1/16 block). The dragon faces -Z (its head is at negative Z).
BONES = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 14, 0], [("body", [-7, 10, -10], [14, 12, 22], "scales", False)]),
    # The back is left clear for the saddle and a second rider; spikes run along the neck and the tail.
    ("neck", "body", [0, 18, -10], [
        ("neck", [-3, 16, -20], [6, 6, 10], "scales", False),
        ("spike_small", [-1, 22, -17], [2, 2, 2], "horn", False),
    ]),
    ("head", "neck", [0, 19, -20], [
        ("skull", [-5, 15, -30], [10, 8, 10], "head", False),
        ("snout", [-3, 15, -36], [6, 5, 6], "scales", False),
        ("horn", [2, 22, -24], [2, 6, 2], "horn", False),
        ("horn", [-4, 22, -24], [2, 6, 2], "horn", True),
    ]),
    ("jaw", "head", [0, 15, -27], [("jaw", [-3, 12, -36], [6, 3, 8], "belly", False)]),
    ("tail", "body", [0, 16, 12], [
        ("tail1", [-4, 12, 12], [8, 7, 12], "scales", False),
        ("spike", [-1, 19, 16], [2, 3, 2], "horn", False),
    ]),
    ("tail2", "tail", [0, 15, 24], [
        ("tail2", [-3, 13, 24], [6, 5, 12], "scales", False),
        ("spike_small", [-1, 18, 28], [2, 2, 2], "horn", False),
    ]),
    ("tail3", "tail2", [0, 15, 36], [("tail3", [-2, 14, 36], [4, 3, 12], "scales", False)]),
    ("wing_left", "body", [7, 20, -4], [
        ("wing_arm", [7, 20, -6], [20, 2, 3], "scales", False),
        ("wing_membrane", [7, 20.5, -3], [20, 1, 16], "membrane", False),
    ]),
    ("wing_right", "body", [-7, 20, -4], [
        ("wing_arm", [-27, 20, -6], [20, 2, 3], "scales", True),
        ("wing_membrane", [-27, 20.5, -3], [20, 1, 16], "membrane", True),
    ]),
    ("leg_front_left", "root", [5, 12, -6], [("leg_front", [3, 0, -8], [4, 12, 4], "scales", False)]),
    ("leg_front_right", "root", [-5, 12, -6], [("leg_front", [-7, 0, -8], [4, 12, 4], "scales", True)]),
    ("leg_back_left", "root", [5, 12, 8], [("leg_back", [3, 0, 6], [5, 12, 5], "scales", False)]),
    ("leg_back_right", "root", [-5, 12, 8], [("leg_back", [-8, 0, 6], [5, 12, 5], "scales", True)]),
]


def box_uv_size(size):
    w, h, d = (int(round(s)) for s in size)
    return 2 * (d + w), d + h


def pack():
    """Assigns each distinct cube name a texture spot (shelf packing). Mirrored cubes share their twin's spot."""
    spots, x, y, shelf = {}, 0, 0, 0
    order = []
    for _, _, _, cubes in BONES:
        for name, _, size, kind, _ in cubes:
            if name not in spots and name not in order:
                order.append(name)
                spots[name] = (size, kind)
    placed = {}
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


def faces(u, v, size):
    """Box UV face rectangles: name -> (x, y, w, h)."""
    w, h, d = (int(round(s)) for s in size)
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2654435761) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 0xFFFF


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def paint_skin(pal, placed):
    img = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]

    def put(x, y, c):
        img[y][x] = (*c, 255)

    for name, (u, v, size, kind) in placed.items():
        for face, (fx, fy, fw, fh) in faces(u, v, size).items():
            for yy in range(fh):
                for xx in range(fw):
                    px, py = fx + xx, fy + yy
                    n = noise(px, py, 7)
                    if kind == "membrane":
                        c = shade(pal["membrane"], 0.85 + 0.25 * noise(px // 3, py, 3))
                        if xx % 5 == 0:  # finger bones through the membrane
                            c = pal["dark"]
                    elif kind == "horn":
                        c = shade(pal["horn"], 0.9 + 0.2 * n)
                    elif kind == "belly" or face == "bottom":
                        c = shade(pal["belly"], 0.9 + 0.15 * n)
                        if yy % 3 == 0:
                            c = shade(pal["belly"], 0.8)
                    else:
                        # Overlapping scale rows: dark edges, light highlights.
                        if (xx + (yy // 2) % 2) % 3 == 0 and yy % 2 == 0:
                            c = pal["dark"]
                        elif n > 0.82:
                            c = pal["light"]
                        else:
                            c = shade(pal["base"], 0.92 + 0.16 * n)
                        if face == "top" and n > 0.6:
                            c = pal["light"]
                    put(px, py, c)
        if kind == "head":
            # Glowing eyes near the front-top of both sides of the skull.
            for face in ("east", "west"):
                fx, fy, fw, fh = faces(u, v, size)[face]
                for ex in (1, fw - 3):
                    for dx in range(2):
                        put(fx + ex + dx, fy + 2, pal["eye"])
                        put(fx + ex + dx, fy + 3, shade(pal["eye"], 0.7))
    return img


LEATHER = dict(base=(0x7a, 0x4a, 0x26), light=(0x96, 0x60, 0x30), dark=(0x4a, 0x2c, 0x16),
               stitch=(0xd8, 0xb8, 0x78), iron=(0xb8, 0xb8, 0xc0))


def paint_saddle(img, placed):
    """Paints a saddle onto a copy of a skin: a seat on the middle of the back, skirts and a girth strap with an
    iron buckle down both flanks, and the strap under the belly. Everything is centred on the body's length, so it
    reads the same whichever way a face's texture runs."""
    img = [row[:] for row in img]
    u, v, size, _ = placed["body"]
    f = faces(u, v, size)
    w, h, d = (int(round(n)) for n in size)
    lo, hi = d // 2 - 4, d // 2 + 3  # saddle rows along the body (8 of them)

    def put(x, y, c):
        img[y][x] = (*c, 255)

    fx, fy, _, _ = f["top"]
    for z in range(lo, hi + 1):
        for x in range(w):
            c = LEATHER["base"] if 0 < x < w - 1 else LEATHER["dark"]
            if z in (lo, hi):
                c = LEATHER["dark"]
            elif z in (lo + 1, hi - 1):
                c = LEATHER["stitch"] if x % 2 == 0 and 0 < x < w - 1 else LEATHER["base"]
            elif 3 <= x <= w - 4:
                c = LEATHER["light"] if (x + z) % 5 else LEATHER["base"]  # the worn seat
            put(fx + x, fy + z, c)
    for side in ("east", "west"):
        fx, fy, _, _ = f[side]
        for z in range(lo, hi + 1):
            for y in range(4):  # skirt
                c = LEATHER["dark"] if y == 3 or z in (lo, hi) else LEATHER["base"]
                if y == 1 and z % 2 == 0 and lo < z < hi:
                    c = LEATHER["stitch"]
                put(fx + z, fy + y, c)
        for z in range(d // 2 - 2, d // 2 + 2):  # girth strap
            for y in range(4, h):
                put(fx + z, fy + y, LEATHER["iron"] if y in (6, 7) else LEATHER["dark"])
    fx, fy, _, _ = f["bottom"]
    for z in range(d // 2 - 2, d // 2 + 2):
        for x in range(w):
            put(fx + x, fy + z, LEATHER["dark"])
    return img


METALS = {
    "iron": dict(light=(0xe6, 0xe6, 0xea), mid=(0xb2, 0xb4, 0xba), dark=(0x70, 0x72, 0x7a), rivet=(0x50, 0x52, 0x58)),
    "golden": dict(light=(0xff, 0xf4, 0x8a), mid=(0xf0, 0xc4, 0x38), dark=(0xb0, 0x7c, 0x14), rivet=(0x7a, 0x50, 0x08)),
    "diamond": dict(light=(0xd0, 0xff, 0xfa), mid=(0x5c, 0xe0, 0xd8), dark=(0x22, 0x9a, 0x96), rivet=(0x14, 0x5e, 0x5c)),
    "netherite": dict(light=(0x7a, 0x70, 0x76), mid=(0x4c, 0x44, 0x4a), dark=(0x30, 0x2a, 0x2e), rivet=(0xd8, 0xa8, 0x5a)),
}

# Which faces of which cubes get plated, and how many rows down from the top edge (None = the whole face).
ARMOR_PLATES = {
    "skull": {"top": None, "north": 3},
    "snout": {"top": None},
    "neck": {"top": None, "east": None, "west": None},
    "body": {"top": None, "north": None, "east": 8, "west": 8},
    "tail1": {"top": None, "east": 4, "west": 4},
    "leg_front": {"north": 6, "east": 6, "west": 6, "south": 6},
    "leg_back": {"north": 6, "east": 6, "west": 6, "south": 6},
}


def paint_armor(img, placed, metal):
    """Paints overlapping armor plates onto a copy of a skin: riveted plates over the head, neck, back, flanks,
    tail and thighs, leaving the eyes, belly, horns and wings bare."""
    img = [row[:] for row in img]
    m = METALS[metal]
    for name, plated in ARMOR_PLATES.items():
        u, v, size, _ = placed[name]
        f = faces(u, v, size)
        for face, rows in plated.items():
            fx, fy, fw, fh = f[face]
            for yy in range(fh if rows is None else min(rows, fh)):
                for xx in range(fw):
                    if (xx, yy) in ((0, 0), (fw - 1, 0)) and face != "top":
                        continue  # rounded corners
                    c = shade(m["mid"], 0.9 + 0.15 * noise(fx + xx, fy + yy, 21))
                    if yy % 4 == 0:
                        c = m["light"]  # the upper lip of each plate catches the light
                    elif yy % 4 == 3:
                        c = m["dark"]  # and its lower edge overlaps the next
                    if yy % 4 == 1 and xx % 4 == 1:
                        c = m["rivet"]
                    if rows is not None and yy == min(rows, fh) - 1:
                        c = m["dark"]  # hem of the barding
                    img[fy + yy][fx + xx] = (*c, 255)
    return img


def write_png(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in img)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


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
            "description": {"identifier": "geometry.dragon", "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 8, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones,
        }],
    }


def kf(*pairs):
    """Keyframes: kf((0, [x,y,z]), (1, [x,y,z]), ...) -> {"0.0": [...], ...}"""
    return {f"{t:.2f}": v for t, v in pairs}


# GeckoLib turns X and Y keyframe rotations the Bedrock way round but not Z: in model space this is a turn of
# -80 degrees about Y (wing swept back along the flank) then the membrane tipped up 30 degrees, and scaled down
# so the membranes bunch into a low ridge along the spine instead of crossing over each other.
FOLDED = {
    "wing_left": {"rotation": [0, -80, 30], "scale": [0.8, 1, 0.45]},
    "wing_right": {"rotation": [0, 80, -30], "scale": [0.8, 1, 0.45]},
}


def animations():
    tail_sway = lambda amp, length: {
        "tail": {"rotation": kf((0, [0, -amp, 0]), (length / 2, [0, amp, 0]), (length, [0, -amp, 0]))},
        "tail2": {"rotation": kf((0, [0, -amp, 0]), (length / 2, [0, amp, 0]), (length, [0, -amp, 0]))},
        "tail3": {"rotation": kf((0, [0, -amp * 1.4, 0]), (length / 2, [0, amp * 1.4, 0]), (length, [0, -amp * 1.4, 0]))},
    }
    idle = {**FOLDED, **tail_sway(6, 4),
            "body": {"position": kf((0, [0, 0, 0]), (2, [0, 0.4, 0]), (4, [0, 0, 0]))},
            "neck": {"rotation": kf((0, [0, 0, 0]), (2, [-3, 0, 0]), (4, [0, 0, 0]))}}
    leg = lambda a: {"rotation": kf((0, [a, 0, 0]), (0.5, [-a, 0, 0]), (1, [a, 0, 0]))}
    walk = {**FOLDED, **tail_sway(10, 1),
            "leg_front_left": leg(25), "leg_back_right": leg(25),
            "leg_front_right": leg(-25), "leg_back_left": leg(-25),
            "neck": {"rotation": kf((0, [2, 0, 0]), (0.5, [-2, 0, 0]), (1, [2, 0, 0]))}}
    flap = lambda s: {"rotation": kf((0, [0, 0, 35 * s]), (0.4, [0, 0, -30 * s]), (0.8, [0, 0, 35 * s]))}
    fly = {"wing_left": flap(1), "wing_right": flap(-1), **tail_sway(5, 1.6),
           "body": {"position": kf((0, [0, 0, 0]), (0.4, [0, 1.2, 0]), (0.8, [0, 0, 0]))},
           "leg_front_left": {"rotation": [45, 0, 0]}, "leg_front_right": {"rotation": [45, 0, 0]},
           "leg_back_left": {"rotation": [60, 0, 0]}, "leg_back_right": {"rotation": [60, 0, 0]}}
    sit = {**FOLDED, **tail_sway(3, 5),
           "body": {"position": [0, -5, 0], "rotation": [-6, 0, 0]},
           "leg_back_left": {"rotation": [-60, 0, 0], "position": [0, -3, 2]},
           "leg_back_right": {"rotation": [-60, 0, 0], "position": [0, -3, 2]},
           "leg_front_left": {"position": [0, -2, 0]}, "leg_front_right": {"position": [0, -2, 0]}}
    breath = {"jaw": {"rotation": kf((0, [0, 0, 0]), (0.15, [35, 0, 0]), (0.9, [35, 0, 0]), (1.1, [0, 0, 0]))},
              "head": {"rotation": kf((0, [0, 0, 0]), (0.15, [-8, 0, 0]), (0.9, [-8, 0, 0]), (1.1, [0, 0, 0]))}}

    def anim(bones, length, loop=True):
        return {"loop": loop, "animation_length": length, "bones": bones}

    return {"format_version": "1.8.0", "animations": {
        "misc.idle": anim(idle, 4), "move.walk": anim(walk, 1), "move.fly": anim(fly, 0.8),
        "misc.sit": anim(sit, 5), "attack.breath": anim(breath, 1.1, loop=False),
    }}


def egg_texture(pal, stage):
    img = []
    for y in range(16):
        row = []
        for x in range(16):
            n = noise(x, y, 11)
            c = shade(pal["base"], 0.85 + 0.25 * n)
            if n > 0.8:
                c = pal["light"]
            elif n < 0.12:
                c = pal["dark"]
            row.append((*c, 255))
        img.append(row)
    # Cracks spread as the egg gets closer to hatching.
    cracks = [[(7, 2), (8, 3), (7, 4), (8, 5)], [(3, 6), (4, 7), (5, 7), (6, 8)], [(11, 8), (10, 9), (11, 10), (12, 11), (11, 12)]]
    glow = pal["eye"]
    for i in range(stage):
        for x, y in cracks[i]:
            img[y][x] = (*glow, 255)
    return img


SPAWN_EGGS = {
    "ember_dragon": ((0xb8, 0x2a, 0x16), (0xf0, 0xa8, 0x3a), "Ember Dragon Spawn Egg"),
    "frost_dragon": ((0x8c, 0xc4, 0xe4), (0xf2, 0xfb, 0xff), "Frost Dragon Spawn Egg"),
    "storm_dragon": ((0x3a, 0x36, 0x5c), (0xe8, 0xd8, 0x50), "Storm Dragon Spawn Egg"),
    "verdant_dragon": ((0x3a, 0x86, 0x3a), (0xd8, 0x7c, 0xc0), "Verdant Dragon Spawn Egg"),
    "ender_dragon": ((0x1c, 0x16, 0x26), (0xd8, 0x6a, 0xff), "Ender Dragon Spawn Egg"),
    "pixel_miner": ((0x7a, 0x5a, 0x3a), (0xf0, 0xc0, 0x40), "Pixel Miner Spawn Egg"),
}


def spawn_egg_texture(base, spot):
    """A 16x16 spawn egg: a shaded egg in the mob's main colour, spotted with its second colour, outlined darker."""
    inside = set()
    for y in range(16):
        for x in range(16):
            dy = (y + 0.5 - 9) / 6.4
            if abs(dy) >= 1:
                continue
            rx = 4.6 * (1 - dy * dy) ** 0.5 * (1 - 0.18 * max(0.0, -dy))  # narrower towards the top
            if abs(x + 0.5 - 8) <= rx:
                inside.add((x, y))
    spots = {(6, 5), (7, 5), (9, 7), (10, 7), (10, 8), (5, 9), (6, 9), (8, 11), (9, 11), (8, 12), (11, 11), (6, 13), (7, 13)}
    img = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for x, y in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        light = 1.15 - 0.05 * ((x - 5) ** 2 + (y - 5) ** 2) ** 0.5  # lit from the upper left
        if edge:
            c = shade(base, 0.55)
        elif (x, y) in spots:
            c = shade(spot, max(0.75, min(1.1, light + 0.2)))
        elif (x, y) in ((6, 4), (5, 5), (5, 6)):
            c = shade(base, 1.45)  # highlight
        else:
            c = shade(base, max(0.7, min(1.2, light + 0.25)))
        img[y][x] = (*c, 255)
    return img


def main():
    placed = pack()
    (ASSETS / "geckolib" / "models" / "entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geckolib" / "models" / "entity" / "dragon.geo.json").write_text(json.dumps(geometry(placed), indent=1))
    (ASSETS / "geckolib" / "animations" / "entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geckolib" / "animations" / "entity" / "dragon.animation.json").write_text(json.dumps(animations(), indent=1))

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    names = {"ember": ("Ember Dragon", "Ember Egg"), "frost": ("Frost Dragon", "Frost Egg"), "storm": ("Storm Dragon", "Storm Egg"),
             "verdant": ("Verdant Dragon", "Verdant Egg"), "ender": ("Ender Dragon", None)}
    for element, pal in PALETTES.items():
        skin = paint_skin(pal, placed)
        # Every combination of armor (or none) and saddle (or none) gets its own skin.
        for metal in [None, *METALS]:
            armored = skin if metal is None else paint_armor(skin, placed, metal)
            name = f"{element}_dragon" + ("" if metal is None else f"_{metal}")
            write_png(ASSETS / "textures" / "entity" / f"{name}.png", armored)
            write_png(ASSETS / "textures" / "entity" / f"{name}_saddled.png", paint_saddle(armored, placed))
        lang[f"entity.{NS}.{element}_dragon"] = names[element][0]
        if names[element][1] is None:
            continue  # the ender dragon hatches from the End's own Dragon Egg
        egg = f"{element}_egg"
        variants = {}
        for stage in range(4):
            write_png(ASSETS / "textures" / "block" / f"{egg}_{stage}.png", egg_texture(pal, stage))
            model = {"parent": "minecraft:block/dragon_egg", "textures": {"all": f"{NS}:block/{egg}_{stage}", "particle": f"{NS}:block/{egg}_{stage}"}}
            (ASSETS / "models" / "block").mkdir(parents=True, exist_ok=True)
            (ASSETS / "models" / "block" / f"{egg}_{stage}.json").write_text(json.dumps(model, indent=2))
            variants[f"hatch={stage}"] = {"model": f"{NS}:block/{egg}_{stage}"}
        (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
        (ASSETS / "blockstates" / f"{egg}.json").write_text(json.dumps({"variants": variants}, indent=2))
        (ASSETS / "items").mkdir(parents=True, exist_ok=True)
        (ASSETS / "items" / f"{egg}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:block/{egg}_0"}}, indent=2))
        lang[f"entity.{NS}.{element}_dragon"] = names[element][0]
        lang[f"block.{NS}.{egg}"] = names[element][1]
    for mob, (base, spot, name) in SPAWN_EGGS.items():
        egg = f"{mob}_spawn_egg"
        write_png(ASSETS / "textures" / "item" / f"{egg}.png", spawn_egg_texture(base, spot))
        (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
        (ASSETS / "models" / "item" / f"{egg}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{egg}"}}, indent=2))
        (ASSETS / "items" / f"{egg}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/{egg}"}}, indent=2))
        lang[f"item.{NS}.{egg}"] = name
    lang["key.wonderrealms.dragon_breath"] = "Dragon Breath / Airship Cannon"
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Packed", len(placed), "cube textures; wrote model, animations,", len(PALETTES), "dragons x 5 armors x 2 saddles,",
          len(PALETTES) - 1, "eggs x 4 stages,",
          len(SPAWN_EGGS), "spawn eggs")


if __name__ == "__main__":
    main()
