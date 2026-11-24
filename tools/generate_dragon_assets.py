#!/usr/bin/env python3
"""Generates the dragon model, animations, skins (plain and saddled), egg textures/models/blockstates, spawn eggs
(the dragons' and the Pixel Miner's) and their lang entries.

Run from the wonder-realms folder:  python3 tools/generate_dragon_assets.py

The model is built from boxes (Bedrock geometry, as GeckoLib reads it). Each box gets a spot on the texture via
simple shelf packing, and every face is painted from the element's palette by dragon_skins.py (shaded overlapping
scales, belly plates, eyes and teeth, scalloped wings and each element's own markings), so changing the model or a
palette regenerates everything consistently.

Set WR_RESOURCES to write into another resources folder (a preview copy) instead of src/main/resources.
"""
import json
import math
import os
import struct
import zlib
from pathlib import Path

import dragon_kinds_art
import dragon_skins
from dragon_skins import METALS

ROOT = Path(os.environ.get("WR_RESOURCES") or Path(__file__).resolve().parent.parent / "src" / "main" / "resources")
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
    "tide": dict(dark=(0x0e, 0x4a, 0x6a), base=(0x1e, 0x86, 0xa8), light=(0x6a, 0xd4, 0xe0), belly=(0xc8, 0xf0, 0xe4),
                 membrane=(0x3a, 0xb4, 0xb0), horn=(0xf0, 0xe6, 0xcc), eye=(0x80, 0xff, 0xf0)),
    "crystal": dict(dark=(0x5a, 0x3a, 0x8a), base=(0x9a, 0x72, 0xd0), light=(0xe0, 0xc8, 0xff), belly=(0xf4, 0xec, 0xff),
                    membrane=(0xc8, 0xa8, 0xf0), horn=(0xf8, 0xf4, 0xff), eye=(0xff, 0x9a, 0xf0)),
    "shadow": dict(dark=(0x08, 0x08, 0x0e), base=(0x1a, 0x1a, 0x26), light=(0x34, 0x34, 0x4a), belly=(0x4a, 0x48, 0x5e),
                   membrane=(0x22, 0x1a, 0x30), horn=(0x6a, 0x6a, 0x78), eye=(0x4a, 0xff, 0xe0)),
    # Earth: sandstone and clay, flecked with stone, with grey stone horns. "fleck" scatters stone chips over its back.
    "earth": dict(dark=(0x4a, 0x2a, 0x16), base=(0x9a, 0x5a, 0x2e), light=(0xd0, 0x8a, 0x4a), belly=(0xd8, 0xb8, 0x8a),
                  membrane=(0x8a, 0x6a, 0x4a), horn=(0x7a, 0x76, 0x72), eye=(0xff, 0xb0, 0x30), fleck=(0x8a, 0x86, 0x80)),
    # Honey: golden, banded black like a bee ("stripe"), with pale wings like a bee's.
    "honey": dict(dark=(0x8a, 0x5a, 0x0a), base=(0xf0, 0xa8, 0x20), light=(0xff, 0xd8, 0x60), belly=(0xff, 0xec, 0xb0),
                  membrane=(0xe8, 0xe0, 0xc8), horn=(0x2a, 0x1a, 0x0a), eye=(0xff, 0x6a, 0x10), stripe=(0x2a, 0x1a, 0x0a)),
    # Rainbow: scales in bands of every colour ("rainbow"), white belly and horns, pearly wings.
    "rainbow": dict(dark=(0x6a, 0x3a, 0x8a), base=(0xff, 0x60, 0x60), light=(0xff, 0xff, 0xff), belly=(0xfa, 0xf6, 0xff),
                    membrane=(0xf0, 0xe8, 0xff), horn=(0xff, 0xf4, 0xd8), eye=(0xff, 0x6a, 0xd0), rainbow=True),
    # Air: white as a cloud, with sky-blue wings and silver horns.
    "air": dict(dark=(0x8a, 0x9a, 0xb0), base=(0xe8, 0xee, 0xf6), light=(0xff, 0xff, 0xff), belly=(0xf8, 0xf8, 0xff),
                membrane=(0xb8, 0xd8, 0xf0), horn=(0xc8, 0xd0, 0xd8), eye=(0x60, 0xc8, 0xff)),
    # The Elder Dragon of the Sky Citadels: old bronze and gold, ivory horns, blood-red wings and eyes.
    "elder": dict(dark=(0x3a, 0x22, 0x10), base=(0x8a, 0x5a, 0x22), light=(0xe0, 0xb8, 0x60), belly=(0xf0, 0xe0, 0xc0),
                  membrane=(0x7a, 0x1a, 0x1a), horn=(0xf4, 0xec, 0xd8), eye=(0xff, 0x40, 0x30)),
    # 2.0: eight dragons of the Overworld's wilds and eight of the Overcloud (their markings are in dragon_kinds_art.py).
    **dragon_kinds_art.PALETTES,
}

# --- Model: bone -> (parent, pivot, [(name, origin, size, kind, mirror)]) ---------------------------------------
# Units are pixels (1/16 block). The dragon faces -Z (its head is at negative Z).

def wing_bones(side):
    """A wing: an arm out to the wrist, then a finger as long again out to the point, each with membrane behind it.
    The arm and finger bend at the shoulder and wrist. Each membrane is a childless bone of its own, so folding can
    gather it up (scale its Z) without squashing or skewing the bones that hang off the joints."""
    left = side == "left"

    def pivot(x, y, z):
        return [x if left else -x, y, z]

    def cube(name, origin, size, kind):
        x, y, z = origin
        return name, [x if left else -(x + size[0]), y, z], size, kind, not left

    wing = f"wing_{side}"
    return [
        (wing, "body", pivot(7, 21, -4.5), [
            cube("wing_arm", [7, 20, -6], [20, 2, 3], "scales"),
            cube("wing_claw", [26, 20.5, -8], [2, 1, 3], "horn"),  # the thumb's claw, on the wrist
        ]),
        (f"{wing}_skin", wing, pivot(7, 21, -3), [cube("wing_membrane", [7, 20.5, -3], [20, 1, 16], "membrane")]),
        (f"{wing}_tip", wing, pivot(27, 21, -4.5), [cube("wing_finger", [27, 20, -5], [20, 2, 2], "scales")]),
        (f"{wing}_tip_skin", f"{wing}_tip", pivot(27, 21, -3),
         [cube("wing_tip_membrane", [27, 20.5, -3], [20, 1, 16], "membrane")]),
    ]


BONES = [
    ("root", None, [0, 0, 0], []),
    ("body", "root", [0, 14, 0], [
        ("body", [-7, 10, -10], [14, 12, 22], "scales", False),
        # Saddlebags hang on both flanks behind the saddle. Only the "bags" skins paint them; elsewhere they're clear.
        ("saddlebag", [7, 13, 3], [3, 6, 8], "bag", False),
        ("saddlebag", [-10, 13, 3], [3, 6, 8], "bag", True),
    ]),
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
    *wing_bones("left"),
    *wing_bones("right"),
    ("leg_front_left", "root", [5, 12, -6], [("leg_front", [3, 0, -8], [4, 12, 4], "scales", False)]),
    ("leg_front_right", "root", [-5, 12, -6], [("leg_front", [-7, 0, -8], [4, 12, 4], "scales", True)]),
    ("leg_back_left", "root", [5, 12, 8], [("leg_back", [3, 0, 6], [5, 12, 5], "scales", False)]),
    ("leg_back_right", "root", [-5, 12, 8], [("leg_back", [-8, 0, 6], [5, 12, 5], "scales", True)]),
]


def box_uv_size(size):
    w, h, d = (int(round(s)) for s in size)
    return 2 * (d + w), d + h


# Cubes added after the skins were first laid out. They go on shelves below the others, so nothing already painted
# moves: the skins' patterns are seeded by where they sit on the texture.
ADDED = ("wing_claw", "wing_finger", "wing_tip_membrane")


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
    first = sorted((n for n in order if n not in ADDED), key=lambda n: -box_uv_size(spots[n][0])[1])
    later = sorted((n for n in order if n in ADDED), key=lambda n: -box_uv_size(spots[n][0])[1])
    for name in first + later:
        size, kind = spots[name]
        bw, bh = box_uv_size(size)
        if x + bw > TEX or name == later[0]:
            x, y, shelf = 0, y + shelf, 0
        assert y + bh <= TEX, "texture full"
        placed[name] = (x, y, size, kind)
        x += bw
        shelf = max(shelf, bh)
    return placed


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
                            "visible_bounds_width": 12, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones,
        }],
    }


def kf(*pairs):
    """Keyframes: kf((0, [x,y,z]), (1, [x,y,z]), ...) -> {"0.0": [...], ...}"""
    return {f"{t:.2f}": v for t, v in pairs}


def both_wings(shoulder, skin, wrist, tip_skin):
    """Channels for the left wing's four bones, and their mirror image for the right: mirroring across the body turns
    Y and Z rotations and X moves round, and leaves the rest."""
    flips = {"rotation": (1, -1, -1), "position": (-1, 1, 1), "scale": (1, 1, 1)}

    def flip(value, signs):
        if isinstance(value, dict):
            return {t: flip(v, signs) for t, v in value.items()}
        return [v * k if v else 0 for v, k in zip(value, signs)]

    out = {}
    for end, channels in (("", shoulder), ("_skin", skin), ("_tip", wrist), ("_tip_skin", tip_skin)):
        out[f"wing_left{end}"] = channels
        out[f"wing_right{end}"] = {kind: flip(value, flips[kind]) for kind, value in channels.items()}
    return out


# Folded, each wing stands on the flank as an upturned V: the arm climbs back to the wrist, about 35 pixels up, and the
# finger doubles back down past the hip, with the membrane gathered inside. It is set out from the flank far enough to
# clear both riders' arms and legs, the saddlebags and the swaying tail, and the finger sits a little proud of the arm
# so the two don't flicker where they overlap.
# (GeckoLib turns X and Y keyframe rotations the Bedrock way round but not Z: these act as Rz(-z) Ry(y) Rx(-x).)
FOLDED = both_wings(
    {"rotation": [-14.77, -51.42, -57.8], "position": [1.6, 0, 0]}, {"scale": [1, 1, 0.34]},
    {"rotation": [0, -75.93, 0], "position": [0, 0.65, 0]}, {"scale": [1, 1, 0.34]},
)


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
    # Two wing beats to each sway of the tail: 0.8 seconds a beat, as often as the flap sound plays. The shoulders
    # drive, and the wrists follow a little late, so the tips trail and curl like a whip.
    beat, length = 0.8, 1.6

    def swing(amount, middle=0.0, late=0.0):
        times = [i / 10 for i in range(17)]
        return kf(*((t, [0, 0, round(middle + amount * math.cos(2 * math.pi * (t - late) / beat), 2)]) for t in times))

    flap = both_wings(
        {"rotation": swing(26, -4), "position": [0, 0, 0]}, {"scale": [1, 1, 1]},
        {"rotation": swing(15, late=0.12), "position": [0, 0, 0]}, {"scale": [1, 1, 1]},
    )
    fly = {**flap, **tail_sway(5, length),
           "body": {"position": kf((0, [0, 0, 0]), (0.4, [0, 1.2, 0]), (0.8, [0, 0, 0]), (1.2, [0, 1.2, 0]),
                                   (1.6, [0, 0, 0]))},
           "leg_front_left": {"rotation": [45, 0, 0]}, "leg_front_right": {"rotation": [45, 0, 0]},
           "leg_back_left": {"rotation": [60, 0, 0]}, "leg_back_right": {"rotation": [60, 0, 0]}}
    sit = {**FOLDED, **tail_sway(3, 5),
           "body": {"position": [0, -5, 0], "rotation": [-6, 0, 0]},
           "leg_back_left": {"rotation": [-60, 0, 0], "position": [0, -3, 2]},
           "leg_back_right": {"rotation": [-60, 0, 0], "position": [0, -3, 2]},
           "leg_front_left": {"position": [0, -2, 0]}, "leg_front_right": {"position": [0, -2, 0]}}
    breath = {"jaw": {"rotation": kf((0, [0, 0, 0]), (0.15, [35, 0, 0]), (0.9, [35, 0, 0]), (1.1, [0, 0, 0]))},
              "head": {"rotation": kf((0, [0, 0, 0]), (0.15, [-8, 0, 0]), (0.9, [-8, 0, 0]), (1.1, [0, 0, 0]))}}

    def rear(in_flight):
        """Rearing back to unleash its power: up on its hind legs with its forelegs pawing the air, head thrown back and
        wings flung wide, a beat of the wings and a roar with jaws agape, then back down. On the ground it ends with its
        wings folded; in the air, with them spread to fly on (and its legs tucked up as they are in flight)."""
        folded = (FOLDED["wing_left"]["rotation"], FOLDED["wing_left"]["position"], FOLDED["wing_left_tip"]["rotation"],
                  FOLDED["wing_left_tip"]["position"])
        start = folded if not in_flight else ([0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0])
        end = start
        membrane = [1, 1, 0.34] if not in_flight else [1, 1, 1]
        wings = both_wings(
            {"rotation": kf((0, start[0]), (0.3, [0, 0, -40]), (0.55, [0, 0, 22]), (0.8, [0, 0, -40]), (1.05, [0, 0, 0]), (1.6, end[0])),
             "position": kf((0, start[1]), (0.3, [0, 0, 0]), (1.05, [0, 0, 0]), (1.6, end[1]))},
            {"scale": kf((0, membrane), (0.25, [1, 1, 1]), (1.15, [1, 1, 1]), (1.6, membrane))},
            {"rotation": kf((0, start[2]), (0.3, [0, 0, -15]), (0.55, [0, 0, 12]), (0.8, [0, 0, -15]), (1.05, [0, 0, 0]), (1.6, end[2])),
             "position": kf((0, start[3]), (0.3, [0, 0, 0]), (1.05, [0, 0, 0]), (1.6, end[3]))},
            {"scale": kf((0, membrane), (0.25, [1, 1, 1]), (1.15, [1, 1, 1]), (1.6, membrane))},
        )
        front, back = ([45, 0, 0], [60, 0, 0]) if in_flight else ([0, 0, 0], [0, 0, 0])
        paw = lambda a, b: {"rotation": kf((0, front), (0.3, [a, 0, 0]), (0.5, [b, 0, 0]), (0.7, [a + 5, 0, 0]), (0.9, [b, 0, 0]),
                                             (1.1, [a, 0, 0]), (1.45, front), (1.6, front)),
                              "position": kf((0, [0, 0, 0]), (0.3, [0, 6, 1]), (0.75, [0, 7, 1]), (1.1, [0, 6, 1]), (1.45, [0, 0, 0]),
                                             (1.6, [0, 0, 0]))}
        sway = lambda lift, amount: {"rotation": kf((0, [0, 0, 0]), (0.3, [lift, 0, 0]), (0.75, [lift + 3, amount, 0]),
                                                    (1.1, [lift, -amount, 0]), (1.45, [0, 0, 0]), (1.6, [0, 0, 0]))}
        return {**wings,
                "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [-32, 0, 0]), (0.75, [-36, 0, 0]), (1.1, [-30, 0, 0]), (1.45, [0, 0, 0]),
                                        (1.6, [0, 0, 0])),
                         "position": kf((0, [0, 0, 0]), (0.3, [0, 4, 2]), (0.75, [0, 5, 2]), (1.1, [0, 4, 2]), (1.45, [0, 0, 0]),
                                        (1.6, [0, 0, 0]))},
                "neck": {"rotation": kf((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.5, [-22, 0, 0]), (0.75, [-4, 0, 0]), (1.1, [-10, 0, 0]),
                                        (1.45, [0, 0, 0]), (1.6, [0, 0, 0]))},
                "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.5, [-25, 0, 0]), (0.75, [12, 0, 0]), (1.1, [4, 0, 0]),
                                        (1.45, [0, 0, 0]), (1.6, [0, 0, 0]))},
                "jaw": {"rotation": kf((0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.62, [40, 0, 0]), (1.05, [38, 0, 0]), (1.3, [0, 0, 0]),
                                       (1.6, [0, 0, 0]))},
                "tail": sway(15, 8), "tail2": sway(10, 10), "tail3": sway(6, 14),
                "leg_front_left": paw(-60, -95), "leg_front_right": paw(-75, -50),
                "leg_back_left": {"rotation": kf((0, back), (0.3, [8, 0, 0]), (1.1, [8, 0, 0]), (1.45, back), (1.6, back))},
                "leg_back_right": {"rotation": kf((0, back), (0.3, [8, 0, 0]), (1.1, [8, 0, 0]), (1.45, back), (1.6, back))}}

    def anim(bones, length, loop=True):
        return {"loop": loop, "animation_length": length, "bones": bones}

    return {"format_version": "1.8.0", "animations": {
        "misc.idle": anim(idle, 4), "move.walk": anim(walk, 1), "move.fly": anim(fly, length),
        "misc.sit": anim(sit, 5), "attack.breath": anim(breath, 1.1, loop=False),
        "attack.power": anim(rear(False), 1.6, loop=False), "attack.power_air": anim(rear(True), 1.6, loop=False),
    }}


SPAWN_EGGS = {
    "ember_dragon": ((0xb8, 0x2a, 0x16), (0xf0, 0xa8, 0x3a), "Ember Dragon Spawn Egg"),
    "frost_dragon": ((0x8c, 0xc4, 0xe4), (0xf2, 0xfb, 0xff), "Frost Dragon Spawn Egg"),
    "storm_dragon": ((0x3a, 0x36, 0x5c), (0xe8, 0xd8, 0x50), "Storm Dragon Spawn Egg"),
    "verdant_dragon": ((0x3a, 0x86, 0x3a), (0xd8, 0x7c, 0xc0), "Verdant Dragon Spawn Egg"),
    "tide_dragon": ((0x1e, 0x86, 0xa8), (0x9a, 0xf0, 0xe0), "Tide Dragon Spawn Egg"),
    "crystal_dragon": ((0x9a, 0x72, 0xd0), (0xf4, 0xec, 0xff), "Crystal Dragon Spawn Egg"),
    "shadow_dragon": ((0x1a, 0x1a, 0x26), (0x4a, 0xff, 0xe0), "Shadow Dragon Spawn Egg"),
    "ender_dragon": ((0x1c, 0x16, 0x26), (0xd8, 0x6a, 0xff), "Ender Dragon Spawn Egg"),
    "pixel_miner": ((0x7a, 0x5a, 0x3a), (0xf0, 0xc0, 0x40), "Pixel Miner Spawn Egg"),
    "rhino_beetle": ((0x3a, 0x22, 0x12), (0xa8, 0x80, 0x58), "Rhino Beetle Spawn Egg"),
    "dragonfly": ((0x1a, 0x8a, 0xa0), (0x7a, 0xf0, 0x5a), "Dragonfly Spawn Egg"),
    "elder_dragon": ((0x8a, 0x5a, 0x22), (0xff, 0x40, 0x30), "Elder Dragon Spawn Egg"),
    "earth_dragon": ((0x9a, 0x5a, 0x2e), (0x8a, 0x86, 0x80), "Earth Dragon Spawn Egg"),
    "honey_dragon": ((0xf0, 0xa8, 0x20), (0x2a, 0x1a, 0x0a), "Honey Dragon Spawn Egg"),
    "grasshopper": ((0x5a, 0xa0, 0x3a), (0xc8, 0xd8, 0x7a), "Grasshopper Spawn Egg"),
    "firefly": ((0x2a, 0x22, 0x1e), (0xe8, 0xff, 0x7a), "Firefly Spawn Egg"),
    "sky_whale": ((0x3a, 0x4e, 0x8e), (0xf0, 0xf4, 0xff), "Sky Whale Spawn Egg"),
    "rainbow_dragon": ((0xff, 0x50, 0x50), (0x40, 0xa8, 0xff), "Rainbow Dragon Spawn Egg"),
    "air_dragon": ((0xe8, 0xee, 0xf6), (0x60, 0xc8, 0xff), "Air Dragon Spawn Egg"),
    "storm_leviathan": ((0x2a, 0x2e, 0x3a), (0xff, 0xf0, 0x60), "Storm Leviathan Spawn Egg"),
    **dragon_kinds_art.SPAWN_EGGS,
}


def main():
    placed = pack()
    (ASSETS / "geckolib" / "models" / "entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geckolib" / "models" / "entity" / "dragon.geo.json").write_text(json.dumps(geometry(placed), indent=1))
    (ASSETS / "geckolib" / "animations" / "entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geckolib" / "animations" / "entity" / "dragon.animation.json").write_text(json.dumps(animations(), indent=1))

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    names = {"ember": ("Ember Dragon", "Ember Egg"), "frost": ("Frost Dragon", "Frost Egg"), "storm": ("Storm Dragon", "Storm Egg"),
             "verdant": ("Verdant Dragon", "Verdant Egg"), "tide": ("Tide Dragon", "Tide Egg"),
             "crystal": ("Crystal Dragon", "Crystal Egg"), "shadow": ("Shadow Dragon", "Shadow Egg"), "ender": ("Ender Dragon", None),
             "earth": ("Earth Dragon", "Earth Egg"), "honey": ("Honey Dragon", "Honey Egg"),
             "rainbow": ("Rainbow Dragon", "Rainbow Egg"), "air": ("Air Dragon", "Air Egg"),
             "elder": ("Elder Dragon", None), **dragon_kinds_art.NAMES}
    for element, pal in PALETTES.items():
        skin = dragon_skins.paint_skin(element, pal, placed, TEX)
        # Every combination of armor (or none) and saddle (or none) gets its own skin.
        for metal in [None, *METALS]:
            armored = skin if metal is None else dragon_skins.paint_armor(skin, placed, metal)
            name = f"{element}_dragon" + ("" if metal is None else f"_{metal}")
            write_png(ASSETS / "textures" / "entity" / f"{name}.png", armored)
            saddled = dragon_skins.paint_saddle(armored, placed)
            write_png(ASSETS / "textures" / "entity" / f"{name}_saddled.png", saddled)
            write_png(ASSETS / "textures" / "entity" / f"{name}_saddled_bags.png", dragon_skins.paint_bags(saddled, placed))
        lang[f"entity.{NS}.{element}_dragon"] = names[element][0]
        if names[element][1] is None:
            continue  # the ender dragon hatches from the End's own Dragon Egg
        egg = f"{element}_egg"
        variants = {}
        for stage in range(4):
            write_png(ASSETS / "textures" / "block" / f"{egg}_{stage}.png", dragon_skins.egg_texture(element, pal, stage))
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
        write_png(ASSETS / "textures" / "item" / f"{egg}.png", dragon_skins.spawn_egg_texture(base, spot))
        (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
        (ASSETS / "models" / "item" / f"{egg}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{egg}"}}, indent=2))
        (ASSETS / "items" / f"{egg}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/{egg}"}}, indent=2))
        lang[f"item.{NS}.{egg}"] = name
    lang["key.wonderrealms.dragon_breath"] = "Dragon Breath / Airship Cannon"
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Packed", len(placed), "cube textures; wrote model, animations,", len(PALETTES), "dragons x", len(METALS) + 1, "armors x 3 tack,",
          sum(1 for egg in names.values() if egg[1]), "eggs x 4 stages,",
          len(SPAWN_EGGS), "spawn eggs")


if __name__ == "__main__":
    main()
