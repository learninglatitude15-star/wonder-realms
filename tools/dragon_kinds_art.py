"""Skins for the dragons added in 2.0: eight of the Overworld's wilds and eight of the Overcloud.

Each kind has a palette (as in generate_dragon_assets.PALETTES) and markings of its own, painted onto the shared
dragon model by dragon_skins.py through the hooks below: scales(), belly(), horn(), membrane(), stamps() and egg().
Everything is drawn in the 1.4.0+ style: hue-shifted shading, shapes that read as real materials (jelly, plates, bone,
feathers, nacre), and markings that belong to each kind.
"""
import math

from texkit import bayer, fbm, h2, mix, tone, voronoi

WHITE = (0xff, 0xff, 0xff)

# dark scale, base scale, light scale, belly, membrane, horn, eye (as the first dragons' palettes).
PALETTES = {
    # The Overworld's wilds.
    "slime": dict(dark=(0x2e, 0x7a, 0x22), base=(0x6c, 0xc4, 0x48), light=(0xb4, 0xf0, 0x84), belly=(0xcc, 0xf4, 0xa8),
                  membrane=(0x8e, 0xd8, 0x66), horn=(0x4a, 0x96, 0x30), eye=(0x1a, 0x38, 0x12)),
    "mushroom": dict(dark=(0x72, 0x12, 0x12), base=(0xc4, 0x2c, 0x26), light=(0xf0, 0x5c, 0x48), belly=(0xee, 0xe0, 0xc6),
                     membrane=(0xe6, 0xcc, 0xb2), horn=(0xee, 0xe0, 0xc4), eye=(0xff, 0xd0, 0x40)),
    "blossom": dict(dark=(0xb8, 0x58, 0x84), base=(0xf0, 0x9e, 0xc2), light=(0xff, 0xd4, 0xe6), belly=(0xff, 0xf0, 0xf6),
                    membrane=(0xff, 0xc2, 0xda), horn=(0x5a, 0x38, 0x2a), eye=(0x4c, 0xc8, 0x6e)),
    "iron": dict(dark=(0x44, 0x46, 0x50), base=(0x96, 0x98, 0xa4), light=(0xdc, 0xde, 0xe6), belly=(0x6c, 0x6e, 0x78),
                 membrane=(0x7c, 0x80, 0x8c), horn=(0x38, 0x38, 0x40), eye=(0xff, 0x8a, 0x2c)),
    "bone": dict(dark=(0x14, 0x12, 0x16), base=(0x30, 0x2c, 0x34), light=(0x52, 0x4c, 0x58), belly=(0x40, 0x3a, 0x42),
                 membrane=(0x3a, 0x32, 0x38), horn=(0xe8, 0xe0, 0xcc), eye=(0xff, 0x40, 0x30)),
    "jungle": dict(dark=(0x0c, 0x56, 0x28), base=(0x28, 0x98, 0x48), light=(0x6c, 0xd0, 0x6c), belly=(0xd6, 0xe8, 0x6e),
                   membrane=(0x46, 0xac, 0x3e), horn=(0x68, 0x48, 0x26), eye=(0xff, 0xa0, 0x20)),
    "echo": dict(dark=(0x04, 0x10, 0x14), base=(0x0c, 0x2a, 0x32), light=(0x1c, 0x46, 0x50), belly=(0x12, 0x30, 0x38),
                 membrane=(0x0e, 0x26, 0x30), horn=(0xb4, 0xd0, 0xcc), eye=(0x40, 0xff, 0xf0)),
    "coral": dict(dark=(0xbe, 0x38, 0x3c), base=(0xff, 0x6c, 0x52), light=(0xff, 0xaa, 0x86), belly=(0xff, 0xe2, 0xd2),
                  membrane=(0x34, 0xcc, 0xbc), horn=(0xff, 0x5a, 0x8a), eye=(0x3c, 0xe0, 0xff)),
    # Since 2.1: painted pale all over, because the game tints a Chameleon dragon to the colour of whatever it stands on.
    "chameleon": dict(dark=(0x8e, 0x90, 0x8c), base=(0xd2, 0xd4, 0xd0), light=(0xf4, 0xf6, 0xf2), belly=(0xee, 0xf0, 0xe6),
                      membrane=(0xe2, 0xe4, 0xe0), horn=(0xb2, 0xb2, 0xaa), eye=(0x22, 0x22, 0x22)),
    # The Overcloud.
    "sun": dict(dark=(0xc4, 0x66, 0x0c), base=(0xff, 0xb2, 0x22), light=(0xff, 0xe4, 0x76), belly=(0xff, 0xf8, 0xd0),
                membrane=(0xff, 0xb8, 0x30), horn=(0xff, 0xf4, 0xcc), eye=(0xff, 0xfc, 0xe0)),
    "moon": dict(dark=(0x6c, 0x74, 0x98), base=(0xc8, 0xce, 0xe6), light=(0xf6, 0xf8, 0xff), belly=(0xf2, 0xf2, 0xfc),
                 membrane=(0x22, 0x2e, 0x58), horn=(0xf4, 0xf6, 0xfc), eye=(0xff, 0xe6, 0x9c)),
    "star": dict(dark=(0x08, 0x0a, 0x24), base=(0x18, 0x20, 0x58), light=(0x34, 0x42, 0x8e), belly=(0x48, 0x46, 0x9e),
                 membrane=(0x10, 0x10, 0x30), horn=(0xff, 0xee, 0xae), eye=(0xf8, 0xf8, 0xff)),
    "aurora": dict(dark=(0x06, 0x1e, 0x2a), base=(0x0e, 0x3a, 0x4a), light=(0x1e, 0x5a, 0x6a), belly=(0xc6, 0xf2, 0xec),
                   membrane=(0x20, 0x9a, 0x8a), horn=(0xe0, 0xff, 0xf6), eye=(0x5c, 0xff, 0x98)),
    "phoenix": dict(dark=(0x9a, 0x16, 0x0a), base=(0xff, 0x5c, 0x16), light=(0xff, 0xb4, 0x3c), belly=(0xff, 0xe2, 0x6a),
                    membrane=(0xff, 0x84, 0x22), horn=(0xff, 0xde, 0x7c), eye=(0xff, 0xf0, 0xa0)),
    "nimbus": dict(dark=(0x2c, 0x34, 0x4c), base=(0x5e, 0x6c, 0x8c), light=(0xc6, 0xd0, 0xe2), belly=(0xe2, 0xe8, 0xf2),
                   membrane=(0x58, 0x60, 0x72), horn=(0xc0, 0xc8, 0xd4), eye=(0x56, 0xc6, 0xff)),
    "gold": dict(dark=(0x86, 0x56, 0x08), base=(0xe6, 0xb4, 0x2e), light=(0xff, 0xee, 0x9e), belly=(0xff, 0xf0, 0xc4),
                 membrane=(0x9c, 0x1a, 0x2a), horn=(0xc8, 0x1c, 0x3a), eye=(0x2e, 0xe0, 0x6c)),
    "pearl": dict(dark=(0xac, 0xa4, 0xc4), base=(0xee, 0xea, 0xf4), light=(0xff, 0xff, 0xff), belly=(0xf6, 0xf2, 0xff),
                  membrane=(0xee, 0xda, 0xf2), horn=(0xe6, 0xde, 0xf2), eye=(0xae, 0x5c, 0xff)),
}

OVERWORLD = ("slime", "mushroom", "blossom", "iron", "bone", "jungle", "echo", "coral", "chameleon")
OVERCLOUD = ("sun", "moon", "star", "aurora", "phoenix", "nimbus", "gold", "pearl")
NAMES = {kind: (f"{kind.capitalize()} Dragon", f"{kind.capitalize()} Egg") for kind in OVERWORLD + OVERCLOUD}
# Spawn eggs: (main colour, spots).
SPAWN_EGGS = {f"{kind}_dragon": (PALETTES[kind]["base"], spot, f"{kind.capitalize()} Dragon Spawn Egg") for kind, spot in {
    "slime": (0x2e, 0x7a, 0x22), "mushroom": (0xf4, 0xee, 0xe0), "blossom": (0xff, 0xf4, 0xf8), "iron": (0xff, 0x8a, 0x2c),
    "bone": (0xe8, 0xe0, 0xcc), "jungle": (0xe8, 0xd0, 0x40), "echo": (0x40, 0xff, 0xf0), "coral": (0x34, 0xcc, 0xbc),
    "sun": (0xff, 0xf6, 0xc8), "moon": (0x3a, 0x46, 0x74), "star": (0xff, 0xee, 0xae), "aurora": (0x5c, 0xff, 0x98),
    "phoenix": (0xff, 0xd8, 0x50), "nimbus": (0x56, 0xc6, 0xff), "gold": (0xc8, 0x1c, 0x3a), "pearl": (0xae, 0x5c, 0xff),
    "chameleon": (0xff, 0x6f, 0xb4),
}.items()}
# The Chameleon's own colours, for its spawn egg and its egg (its scales are pale, for the game to tint).
CHAMELEON_GREEN = (0x7c, 0xd6, 0x4a)
CHAMELEON_PATCHES = (CHAMELEON_GREEN, (0xff, 0x6f, 0xb4), (0xff, 0xd0, 0x40), (0x4a, 0xb8, 0xff), (0xff, 0x8a, 0x3c))
SPAWN_EGGS["chameleon_dragon"] = (CHAMELEON_GREEN, (0xff, 0x6f, 0xb4), "Chameleon Dragon Spawn Egg")

MUSHROOM_SPOT = (0xf6, 0xf0, 0xe0)
BLOSSOM_PETAL, BLOSSOM_HEART = (0xff, 0xf6, 0xfa), (0xff, 0xd8, 0x48)
RUST = (0x8a, 0x48, 0x1c)
BONE = (0xec, 0xe4, 0xd0)
ROSETTE, ROSETTE_HEART = (0x0a, 0x36, 0x16), (0xe0, 0xc8, 0x3c)
SCULK_GLOW, SCULK_BRIGHT = (0x24, 0xd8, 0xd8), (0x9a, 0xff, 0xf6)
TURQUOISE = (0x34, 0xcc, 0xbc)
SUNSHINE, SUN_CORE = (0xff, 0xf4, 0xa8), (0xff, 0xff, 0xf2)
MOONLIGHT = (0xff, 0xf6, 0xd8)
STARLIGHT = ((0xff, 0xff, 0xff), (0xff, 0xf2, 0xb0), (0xd0, 0xe0, 0xff))
AURORA = ((0x40, 0xf0, 0x80), (0x30, 0xe0, 0xd0), (0x50, 0x90, 0xff), (0xa0, 0x60, 0xf0))
EMBERS = (0xff, 0xf0, 0x9a)
RAIN = (0x6a, 0xb4, 0xff)
GEMS = ((0xe0, 0x20, 0x3a), (0x30, 0xd0, 0x60), (0x30, 0x60, 0xf0))
NACRE = ((0xff, 0xcc, 0xe8), (0xdc, 0xd4, 0xff), (0xcc, 0xf2, 0xff), (0xd4, 0xff, 0xe4), (0xff, 0xe6, 0xcc))


def pick(r, level, x, y, dither=0.45):
    """A colour for a light level (-1..1) from a seven-tone ramp, half-dithered (as dragon_skins.shade_pick)."""
    value = 3 + level * 3 + (bayer(x, y) - 0.5) * dither
    return r[max(0, min(6, int(math.floor(value + 0.5))))]


def ramp7(c, lo=-0.6, hi=0.5):
    return [tone(c, lo * 1.6), tone(c, lo), tone(c, lo * 0.5), c, tone(c, hi * 0.5), tone(c, hi), tone(c, hi * 1.5)]


def nearest(a, b, seed, cell, chance):
    """(dx, dy) from a texel to the nearest of a scatter of points (one per cell, kept with this chance), and its id."""
    gx, gy = math.floor(a / cell), math.floor(b / cell)
    best = (99.0, 99.0, None)
    for ox in (-1, 0, 1):
        for oy in (-1, 0, 1):
            cx, cy = gx + ox, gy + oy
            if h2(cx, cy, seed + 7) > chance:
                continue
            px = (cx + 0.25 + 0.5 * h2(cx, cy, seed)) * cell
            py = (cy + 0.25 + 0.5 * h2(cx, cy, seed + 1)) * cell
            dx, dy = a + 0.5 - px, b + 0.5 - py
            if dx * dx + dy * dy < best[0] ** 2 + best[1] ** 2:
                best = (dx, dy, (cx, cy))
    return best



# --- The thirty of 2.1 ----------------------------------------------------------------------------------------------
# Each: (dark, base, light, belly, membrane, horn, eye), then its markings (a pattern and its colour, or colours) and the
# spots on its spawn egg. The patterns are shared: ice and magma crack and glow with veins, a tiger is striped, basalt and
# clockwork are plated, glow and abyss dragons are spotted with light, a nebula swirls with clouds and stars.
MORE = {
    "ice": ((0x4a, 0x7c, 0xb4), (0x9c, 0xd0, 0xf0), (0xe4, 0xf6, 0xff), (0xf2, 0xfb, 0xff), (0xb8, 0xe2, 0xf8), (0xf0, 0xfa, 0xff), (0x20, 0x70, 0xe0),
            "veins", (0xff, 0xff, 0xff), (0xff, 0xff, 0xff)),
    "snow": ((0x9a, 0xa8, 0xb8), (0xe8, 0xee, 0xf4), (0xff, 0xff, 0xff), (0xf8, 0xfa, 0xfc), (0xdc, 0xe6, 0xf0), (0x6a, 0x50, 0x3a), (0x24, 0x24, 0x30),
             "speckles", (0xb8, 0xd0, 0xe8), (0x6a, 0x50, 0x3a)),
    "abyss": ((0x04, 0x0c, 0x24), (0x0c, 0x22, 0x4a), (0x1c, 0x3c, 0x70), (0x14, 0x30, 0x58), (0x0a, 0x1a, 0x3a), (0x60, 0x80, 0xa0), (0x40, 0xff, 0xe0),
              "spots", (0x40, 0xff, 0xd8), (0x40, 0xff, 0xd8)),
    "glow": ((0x0c, 0x40, 0x3c), (0x1c, 0x80, 0x70), (0x48, 0xc8, 0xa8), (0xa8, 0xf0, 0xd0), (0x30, 0xa0, 0x8c), (0xe8, 0xf4, 0x80), (0xf0, 0xff, 0x60),
             "spots", (0xd8, 0xff, 0x70), (0xd8, 0xff, 0x70)),
    "dripstone": ((0x5a, 0x46, 0x38), (0x9a, 0x80, 0x68), (0xc8, 0xb0, 0x94), (0xd4, 0xc0, 0xa4), (0x8a, 0x72, 0x5c), (0xd8, 0xc8, 0xb0), (0xff, 0xa8, 0x30),
                  "plates", (0x6a, 0x54, 0x44), (0xc8, 0xb0, 0x94)),
    "mud": ((0x30, 0x24, 0x1c), (0x5c, 0x48, 0x38), (0x86, 0x6c, 0x54), (0x9c, 0x88, 0x70), (0x4c, 0x3c, 0x30), (0x2c, 0x5a, 0x2a), (0xd8, 0xc0, 0x30),
            "dapple", (0x3c, 0x2c, 0x22), (0x2c, 0x5a, 0x2a)),
    "venom": ((0x24, 0x10, 0x30), (0x4c, 0x22, 0x5c), (0x74, 0x3c, 0x86), (0x9c, 0xe0, 0x4c), (0x3a, 0x18, 0x48), (0x9c, 0xe0, 0x4c), (0xb8, 0xff, 0x40),
              "spots", (0x9c, 0xe0, 0x4c), (0x9c, 0xe0, 0x4c)),
    "harvest": ((0x8a, 0x5c, 0x1c), (0xd8, 0xa8, 0x3c), (0xf4, 0xd6, 0x7a), (0xf8, 0xe8, 0xb4), (0xe0, 0x8c, 0x2c), (0x6a, 0x8c, 0x2c), (0x3a, 0x2a, 0x14),
                "stripes", (0xb8, 0x80, 0x24), (0xe0, 0x8c, 0x2c)),
    "sand": ((0xb0, 0x90, 0x58), (0xe0, 0xc8, 0x8c), (0xf8, 0xec, 0xc4), (0xfa, 0xf2, 0xd8), (0xd0, 0xb0, 0x70), (0xa0, 0x70, 0x40), (0x40, 0x80, 0xa0),
             "ripples", (0xc8, 0xa8, 0x6c), (0xa0, 0x70, 0x40)),
    "tiger": ((0x8c, 0x3c, 0x0c), (0xec, 0x84, 0x20), (0xff, 0xb8, 0x58), (0xf8, 0xf0, 0xe4), (0xd8, 0x70, 0x1c), (0xf4, 0xec, 0xdc), (0x60, 0xd8, 0x40),
              "stripes", (0x1c, 0x14, 0x10), (0x1c, 0x14, 0x10)),
    "fang": ((0x40, 0x44, 0x4c), (0x80, 0x86, 0x90), (0xb8, 0xbe, 0xc6), (0xe4, 0xe6, 0xea), (0x6c, 0x70, 0x78), (0xf0, 0xee, 0xe6), (0xf0, 0xc0, 0x30),
             "dapple", (0xd8, 0xdc, 0xe2), (0xf0, 0xc0, 0x30)),
    "magma": ((0x24, 0x0c, 0x08), (0x4c, 0x18, 0x10), (0x7c, 0x2c, 0x18), (0x5c, 0x20, 0x14), (0x3c, 0x12, 0x0c), (0x18, 0x10, 0x10), (0xff, 0xc8, 0x30),
              "veins", (0xff, 0x8c, 0x1c), (0xff, 0x8c, 0x1c)),
    "crimson": ((0x4c, 0x08, 0x10), (0x94, 0x18, 0x24), (0xc8, 0x38, 0x3c), (0xd8, 0x6c, 0x5c), (0x7c, 0x10, 0x1c), (0xff, 0xa0, 0x40), (0xff, 0xd0, 0x60),
                "dapple", (0xff, 0x90, 0x40), (0xff, 0x90, 0x40)),
    "warped": ((0x06, 0x30, 0x34), (0x14, 0x6c, 0x6c), (0x2c, 0xa8, 0x98), (0x58, 0xd8, 0xc0), (0x10, 0x54, 0x58), (0xc0, 0x50, 0xc0), (0xff, 0x70, 0xe0),
               "spots", (0x40, 0xe8, 0xd0), (0xe0, 0x50, 0xc8)),
    "soul": ((0x24, 0x1c, 0x16), (0x4c, 0x3c, 0x30), (0x6c, 0x58, 0x48), (0x5c, 0x4c, 0x40), (0x3a, 0x2e, 0x26), (0xc8, 0xe8, 0xf0), (0x50, 0xf0, 0xff),
             "veins", (0x50, 0xe8, 0xff), (0x50, 0xe8, 0xff)),
    "basalt": ((0x1c, 0x1c, 0x20), (0x40, 0x40, 0x46), (0x68, 0x68, 0x70), (0x54, 0x54, 0x5a), (0x30, 0x30, 0x36), (0x88, 0x88, 0x90), (0xff, 0x60, 0x20),
               "plates", (0x24, 0x24, 0x28), (0x88, 0x88, 0x90)),
    "blaze": ((0xa0, 0x4c, 0x00), (0xf0, 0x9c, 0x10), (0xff, 0xd8, 0x40), (0xff, 0xe8, 0x80), (0xff, 0x78, 0x10), (0x60, 0x40, 0x18), (0xff, 0xff, 0xc0),
              "stripes", (0xff, 0xf0, 0x80), (0x60, 0x40, 0x18)),
    "ghast": ((0xb8, 0xb8, 0xbc), (0xec, 0xec, 0xee), (0xff, 0xff, 0xff), (0xf8, 0xf8, 0xfa), (0xe0, 0xe0, 0xe4), (0xd0, 0xd0, 0xd4), (0x40, 0x40, 0x48),
              "speckles", (0xc8, 0xc8, 0xcc), (0x80, 0x80, 0x88)),
    "void": ((0x04, 0x02, 0x0a), (0x10, 0x08, 0x1c), (0x22, 0x14, 0x34), (0x1a, 0x10, 0x28), (0x0c, 0x06, 0x16), (0x50, 0x30, 0x78), (0xc0, 0x60, 0xff),
             "speckles", (0xb0, 0x70, 0xff), (0xb0, 0x70, 0xff)),
    "chorus": ((0x50, 0x34, 0x64), (0x8c, 0x64, 0xa4), (0xc4, 0x9c, 0xd8), (0xe0, 0xcc, 0xec), (0x7a, 0x54, 0x90), (0xe8, 0xd8, 0xf0), (0xff, 0xf0, 0x80),
               "spots", (0xe8, 0xd4, 0xf4), (0xe8, 0xd4, 0xf4)),
    "shulker": ((0x5c, 0x3c, 0x68), (0x9c, 0x6c, 0xa4), (0xc8, 0x9c, 0xcc), (0xe8, 0xe0, 0xb0), (0x8c, 0x60, 0x94), (0xe0, 0xd4, 0xa0), (0xf0, 0xe8, 0x60),
                "plates", (0x6c, 0x48, 0x78), (0xe8, 0xe0, 0xb0)),
    "nebula": ((0x14, 0x0c, 0x3c), (0x30, 0x1c, 0x70), (0x5c, 0x34, 0xa8), (0x8c, 0x5c, 0xd8), (0x24, 0x14, 0x58), (0xf0, 0xd8, 0xff), (0xff, 0x80, 0xe0),
               "nebula", ((0xff, 0x6a, 0xd8), (0x6a, 0x9c, 0xff)), (0xff, 0x6a, 0xd8)),
    "hail": ((0x6c, 0x80, 0x98), (0xbc, 0xcc, 0xdc), (0xee, 0xf4, 0xfa), (0xf6, 0xfa, 0xfc), (0xa8, 0xbc, 0xd0), (0xd8, 0xe8, 0xf8), (0x4c, 0x8c, 0xd8),
             "spots", (0xff, 0xff, 0xff), (0xff, 0xff, 0xff)),
    "eclipse": ((0x06, 0x04, 0x0c), (0x16, 0x10, 0x24), (0x2c, 0x22, 0x44), (0xf0, 0xb0, 0x40), (0x10, 0x0a, 0x1c), (0xff, 0xc8, 0x50), (0xff, 0xd0, 0x60),
                "speckles", (0xff, 0xb8, 0x40), (0xff, 0xb8, 0x40)),
    "prism": ((0x7c, 0x90, 0xb8), (0xd8, 0xe8, 0xff), (0xff, 0xff, 0xff), (0xf8, 0xfc, 0xff), (0xe8, 0xf0, 0xff), (0xff, 0xff, 0xff), (0xff, 0x60, 0xc0),
              "prism", ((0xff, 0x70, 0x70), (0xff, 0xc0, 0x50), (0xf0, 0xf0, 0x60), (0x70, 0xe8, 0x80), (0x70, 0xb8, 0xff), (0xc0, 0x80, 0xff)),
              (0xff, 0x7c, 0xe8)),
    "zephyr": ((0x6c, 0xa0, 0xa8), (0xb8, 0xe8, 0xe4), (0xf0, 0xff, 0xfc), (0xf4, 0xff, 0xfc), (0xd4, 0xf4, 0xf0), (0xe0, 0xf8, 0xf4), (0x30, 0xa0, 0x90),
               "ripples", (0xff, 0xff, 0xff), (0xff, 0xff, 0xff)),
    "phantom": ((0x14, 0x1c, 0x34), (0x2c, 0x3c, 0x64), (0x4c, 0x64, 0x98), (0x7c, 0x98, 0xc8), (0x3a, 0x50, 0x80), (0xb0, 0xc8, 0xe8), (0x8c, 0xff, 0x60),
                "dapple", (0x5c, 0x80, 0xc0), (0x8c, 0xff, 0x60)),
    "redstone": ((0x40, 0x08, 0x08), (0x8c, 0x10, 0x10), (0xd0, 0x28, 0x20), (0x9c, 0x94, 0x90), (0x6c, 0x0c, 0x0c), (0xc8, 0xc0, 0xb8), (0xff, 0x40, 0x30),
                 "veins", (0xff, 0x40, 0x30), (0xff, 0x40, 0x30)),
    "clockwork": ((0x6c, 0x44, 0x18), (0xb8, 0x80, 0x34), (0xe8, 0xc0, 0x6c), (0xd8, 0x9c, 0x58), (0x9c, 0x6c, 0x2c), (0x58, 0x9c, 0x88), (0x60, 0xf0, 0xd0),
                  "plates", (0x58, 0x9c, 0x88), (0x58, 0x9c, 0x88)),
    "melody": ((0x30, 0x2c, 0x5c), (0x5c, 0x58, 0xa8), (0x8c, 0x88, 0xd8), (0xe8, 0xe4, 0xf8), (0x48, 0x44, 0x8c), (0xff, 0xd8, 0x40), (0xff, 0xe8, 0x80),
               "spots", ((0xff, 0x60, 0x60), (0x60, 0xd0, 0x60), (0x60, 0xa0, 0xff), (0xff, 0xd8, 0x40)), (0xff, 0xd8, 0x40)),
}
for _kind, _spec in MORE.items():
    PALETTES[_kind] = dict(zip(("dark", "base", "light", "belly", "membrane", "horn", "eye"), _spec[:7]))
    NAMES[_kind] = (f"{_kind.capitalize()} Dragon", f"{_kind.capitalize()} Egg")
    SPAWN_EGGS[f"{_kind}_dragon"] = (_spec[1], _spec[9], f"{_kind.capitalize()} Dragon Spawn Egg")


def _mark(spec, a, b, seed):
    """One of a pattern's colours (it may have several), chosen per cell."""
    mark = spec[8]
    if isinstance(mark[0], tuple):
        return mark[int(h2(a // 3, b // 3, seed + 9) * len(mark)) % len(mark)]
    return mark


def more_pattern(kind, c, a, b, px, py, along, down, seed, flank=False):
    """A 2.1 dragon's markings on a texel: a, b are the face's own coordinates, px, py the texture's."""
    spec = MORE[kind]
    pattern = spec[7]
    if pattern == "speckles":
        if h2(px, py, seed) > 0.93:
            c = _mark(spec, a, b, seed)
    elif pattern == "veins":
        d1, d2, _ = voronoi(a + 0.5, b + 0.5, seed, 4.0)
        if d2 - d1 < 0.4:
            c = _mark(spec, a, b, seed)
        elif d2 - d1 < 0.8:
            c = mix(c, _mark(spec, a, b, seed), 0.35)
    elif pattern == "spots":
        dx, dy, spot = nearest(a, b, seed, 5.0, 0.7)
        d = math.hypot(dx, dy)
        if spot and d < 0.9:
            c = _mark(spec, *spot, seed)
        elif spot and d < 1.5:
            c = mix(c, _mark(spec, *spot, seed), 0.4)
    elif pattern == "stripes":
        if (along + (down // 2)) % 5 < 2 and (flank or h2(px, py, seed) > 0.3):
            c = _mark(spec, a, b, seed)
    elif pattern == "ripples":
        if (along + round(1.2 * math.sin(down * 0.9))) % 4 == 0:
            c = mix(c, _mark(spec, a, b, seed), 0.7)
    elif pattern == "plates":
        if a % 4 == 0 or b % 4 == 0:
            c = tone(c, -0.25)
        elif a % 4 == 2 and b % 4 == 2:
            c = _mark(spec, a, b, seed)  # a rivet, a gear, a bump
    elif pattern == "dapple":
        if fbm(px, py, seed, 5) > 0.6:
            c = mix(c, _mark(spec, a, b, seed), 0.7)
    elif pattern == "nebula":
        cloud = fbm(px, py, seed, 6)
        if cloud > 0.55:
            c = mix(c, spec[8][0] if fbm(px, py, seed + 3, 9) > 0.5 else spec[8][1], min(0.8, (cloud - 0.55) * 3))
        if h2(px, py, seed + 5) > 0.97:
            c = (0xff, 0xff, 0xff)  # a star
    elif pattern == "prism":
        d1, d2, ident = voronoi(a + 0.5, b + 0.5, seed, 3.0)
        c = mix(c, spec[8][int(h2(*ident, seed) * len(spec[8])) % len(spec[8])], 0.45)
        if d2 - d1 < 0.3:
            c = (0xff, 0xff, 0xff)  # the facets' bright edges
    return c

# --- Scales ---------------------------------------------------------------------------------------------------------

def scales(skin, c, x):
    """The kind's markings on its scaled hide. x: name, face, along, down, across, fw, fh, px, py, a, b, rim, level."""
    e, pal = skin.element, skin.pal
    side = x.face in ("east", "west")
    upper = x.face == "top" or (x.down is not None and x.down < x.fh * 0.62)
    seed = skin.seed + len(x.name)
    if e in MORE:
        return more_pattern(e, c, x.a, x.b, x.px, x.py, x.along or 0, x.down or 0, seed, flank=side)
    if e == "slime":
        # Jelly, not scales: smooth and glossy, with darker goo deep inside and bubbles caught in it.
        level = (0.3 if x.face == "top" else 0.32 - 0.62 * (x.down or 0) / max(1, x.fh - 1)) + (fbm(x.px, x.py, seed, 5) - 0.5) * 0.3
        c = pick(skin.scales, level, x.px, x.py, dither=0.3)
        if fbm(x.px, x.py, seed + 4, 7) > 0.6:
            c = tone(c, -0.32)
        if h2(x.px, x.py, 31) > 0.975:
            c = mix(c, WHITE, 0.55)  # a bubble
        if x.face == "top" and h2(x.px, x.py, 32) > 0.93:
            c = mix(c, WHITE, 0.75)  # gloss
    elif e == "mushroom" and upper and x.name not in ("leg_front", "leg_back"):
        dx, dy, spot = nearest(x.a, x.b, seed, 5.5, 0.62)
        d = math.hypot(dx, dy)
        radius = 1.0 + 0.6 * h2(*spot, seed) if spot else 0
        if d < radius:
            c = pick(ramp7(MUSHROOM_SPOT, -0.4, 0.3), 0.25 - 0.35 * (dy / max(radius, 0.1)), x.px, x.py, dither=0.2)
        elif d < radius + 0.7:
            c = tone(c, 0.12)
    elif e == "blossom" and upper:
        dx, dy, flower = nearest(x.a, x.b, seed, 5.0, 0.62)
        if flower and abs(dx) < 0.6 and abs(dy) < 0.6:
            c = BLOSSOM_HEART
        elif flower and ((abs(dx) < 0.6 and abs(dy) < 1.7) or (abs(dy) < 0.6 and abs(dx) < 1.7)):
            c = BLOSSOM_PETAL if dy <= 0 else tone(BLOSSOM_PETAL, -0.12)
    elif e == "iron":
        # Riveted plates, staggered like armor, with a little rust in the seams.
        pw, ph = 5, 4
        pa, pb = x.a % pw, (x.b + (x.a // pw) % 2 * 2) % ph
        level = (0.2 if x.face == "top" else 0.3 - 0.45 * (x.down or 0) / max(1, x.fh - 1)) - 0.1 * pb
        if pa == pw - 1 or pb == ph - 1:
            level = -0.6  # the seam
        elif pb == 0:
            level += 0.35  # the plate's lip
        c = pick(skin.scales, level, x.px, x.py, dither=0.25)
        if pa == 1 and pb == 1:
            c = tone(pal["light"], 0.3)  # a rivet
        elif pa == 2 and pb == 2 and level > -0.6:
            c = tone(c, -0.25)
        if fbm(x.px, x.py, seed + 3, 5) > 0.66 and (pa == pw - 1 or pb == ph - 1 or h2(x.px, x.py, 33) > 0.6):
            c = mix(c, RUST, 0.6)
    elif e == "bone":
        c = bone_marking(skin, c, x)
    elif e == "jungle" and upper:
        dx, dy, spot = nearest(x.a, x.b, seed, 5.0, 0.7)
        d = math.hypot(dx, dy)
        if spot and d < 0.85:
            c = tone(ROSETTE_HEART, -0.1 * (dy > 0))
        elif spot and d < 1.9 and h2(x.px, x.py, 34) > 0.2:
            c = ROSETTE
    elif e == "echo":
        d1, d2, ident = voronoi(x.a + 0.5, x.b + 0.5, seed, 4.0)
        if d2 - d1 < 0.42 and (x.face == "top" or (x.down is not None and 1 <= x.down < x.fh - 1)):
            c = SCULK_BRIGHT if h2(*ident, seed + 2) > 0.8 else SCULK_GLOW  # sculk veins glowing through the hide
        elif h2(x.px, x.py, 35) > 0.988:
            c = SCULK_BRIGHT
    elif e == "chameleon":
        # Bands across the back and a pale stripe along each flank, in shades of one colour, with fine speckles.
        band = ((x.along or 0) // 2 + (0 if x.face == "top" else (x.down or 0) // 3)) % 4 == 0
        if side and x.down is not None and abs(x.down - x.fh * 0.45) < 0.8:
            c = tone(c, 0.3)
        elif upper and band:
            c = tone(c, -0.24)
        if h2(x.px, x.py, 37) > 0.94:
            c = tone(c, 0.25)
    elif e == "coral":
        if x.face == "top" and abs(x.across - (x.fw - 1) / 2) < 1.1 and x.name in ("body", "neck", "tail1", "tail2", "tail3"):
            c = pick(ramp7(TURQUOISE), 0.25 if x.along % 2 else -0.05, x.px, x.py)  # a reef-blue stripe down the back
        elif upper:
            dx, dy, bubble = nearest(x.a, x.b, seed, 5.0, 0.55)
            d = math.hypot(dx, dy)
            if bubble and 0.85 < d < 1.55:
                c = mix(c, (0xff, 0xf4, 0xee), 0.75)
            elif bubble and d < 0.55 and dx < 0 and dy < 0:
                c = WHITE  # the bubble's glint
    elif e == "sun":
        if x.rim and fbm(x.px, x.py, seed, 5) > 0.45:
            c = SUNSHINE  # light shining between the scales
        if x.name == "body" and side:
            dx, dy = x.along - (x.fw - 1) / 2, x.down - (x.fh - 1) * 0.45
            r = math.hypot(dx, dy * 1.2)
            if r < 2.4:
                c = SUN_CORE
            elif r < 5.6 and abs(math.sin(4 * math.atan2(dy, dx))) > 0.82:
                c = SUNSHINE  # a sunburst on each flank
    elif e == "moon":
        if x.name == "body" and side:
            dx, dy = x.along - (x.fw - 1) / 2, x.down - (x.fh - 1) * 0.42
            if math.hypot(dx, dy) < 3.3 and math.hypot(dx - 1.6, dy - 0.6) > 2.7:
                return pick(ramp7(MOONLIGHT, -0.3, 0.2), 0.2 - 0.1 * dy, x.px, x.py, dither=0.2)  # a crescent moon
        if upper:
            dx, dy, crater = nearest(x.a, x.b, seed, 6.0, 0.5)
            radius = 1.1 + 0.7 * h2(*crater, seed) if crater else 0
            d = math.hypot(dx, dy)
            if d < radius:
                c = tone(c, -0.22 - (0.12 if dx > 0 or dy > 0 else 0))
            elif d < radius + 0.75 and dx > 0 and dy > 0:
                c = tone(c, 0.3)  # the crater's sunlit rim
    elif e == "star":
        roll = h2(x.px, x.py, 36)
        if roll > 0.962:
            c = STARLIGHT[int(h2(x.px, x.py, 37) * 3)]
        elif roll > 0.94:
            c = mix(c, (0x8a, 0x9a, 0xff), 0.35)
    elif e == "aurora":
        if side and x.name in ("body", "neck", "tail1", "tail2", "tail3"):
            wave = math.sin(x.along * 0.55 + seed) * 1.4
            reach = (x.down - 1 - wave) / max(1, x.fh * 0.75)
            if 0 <= reach <= 1:
                strength = (1 - reach) * (0.55 + 0.45 * math.sin(x.along * 1.7 + seed) ** 2)
                hue = AURORA[int((x.along + x.down * 0.4) / 4) % 4]
                c = mix(c, hue, min(0.85, strength * 0.9))
        elif x.face == "top":
            c = mix(c, AURORA[(x.along // 3) % 4], 0.3)
    elif e == "phoenix":
        heat = 1.0 if x.face == "top" else 1 - (x.down or 0) / max(1, x.fh - 1)
        c = mix(c, (0xff, 0xd8, 0x50), heat * 0.4)  # a firebird: red low down, burning gold along the back
        if x.name in ("body", "tail1", "tail2", "tail3", "neck") and x.face in ("top", "east", "west"):
            # Feathers: a chevron in every row.
            mid = (x.fw - 1) / 2 if x.face == "top" else 0
            if (x.b + int(abs(x.a - mid) * 0.5 if x.face == "top" else 0)) % 3 == 2:
                c = tone(c, -0.3)
        if h2(x.px, x.py, 38) > 0.975:
            c = EMBERS
    elif e == "nimbus":
        puff = fbm(x.px, x.py, seed, 4)
        level = (0.28 if x.face == "top" else 0.3 - 0.75 * (x.down or 0) / max(1, x.fh - 1)) + (puff - 0.5) * 0.9
        c = pick(skin.scales, level, x.px, x.py, dither=0.35)
        if puff > 0.62 and upper:
            c = mix(c, WHITE, 0.6)  # billows of white cloud on top
        if side and x.down > x.fh * 0.45 and h2(x.px, x.py // 3, 39) > 0.94:
            c = RAIN  # rain falling from its underside
    elif e == "gold":
        if x.rim:
            c = tone(c, 0.45)  # every scale edged like a coin
        if x.face == "top" and abs(x.across - (x.fw - 1) / 2) < 1 and x.along % 3 == 1 and x.name in ("body", "neck", "tail1", "tail2"):
            gem = GEMS[(x.along // 3) % 3]
            c = tone(gem, 0.4) if x.across < (x.fw - 1) / 2 else gem  # a gem set into its spine
    elif e == "pearl":
        level = x.level * 0.45 + 0.15
        c = pick(skin.scales, level, x.px, x.py, dither=0.3)
        sheen = NACRE[int((x.a + (x.b or 0) * 1.5 + x.px * 0.15) / 3.5) % len(NACRE)]
        c = mix(c, sheen, 0.32)
    return c


def bone_marking(skin, c, x):
    """A dark hide with a skeleton showing white: ribs down the flanks, vertebrae along the spine, a skull for a head."""
    if x.name in ("skull", "snout"):
        return pick(ramp7(BONE, -0.5, 0.25), x.level * 0.6, x.px, x.py, dither=0.3)
    if x.name == "body" and x.face in ("east", "west"):
        if 5 <= x.along <= x.fw - 5 and 2 <= x.down <= x.fh - 3 and (x.along - 5 + x.down // 4) % 3 == 0:
            return pick(ramp7(BONE, -0.5, 0.25), 0.3 - 0.5 * x.down / x.fh, x.px, x.py, dither=0.2)
    if x.face == "top" and x.name in ("body", "neck", "tail1", "tail2", "tail3"):
        if abs(x.across - (x.fw - 1) / 2) < 1 and x.along % 2 == 0:
            return pick(ramp7(BONE, -0.5, 0.25), 0.2, x.px, x.py)
    if x.name.startswith("tail") and x.face in ("east", "west") and x.down == x.fh // 2 and x.along % 3 == 0:
        return tone(BONE, -0.15)
    if x.name.startswith("leg") and x.face in ("east", "west", "north") and x.along is not None and abs(x.along - (x.fw - 1) / 2) < 0.6:
        return tone(BONE, -0.1 - 0.2 * (x.down or 0) / max(1, x.fh))
    return c


def belly(skin, c, along, across, fw, px, py):
    e = skin.element
    if e == "iron" and along % 3 == 0 and across in (1, fw - 2):
        return tone(skin.pal["light"], 0.2)  # rivets
    if e == "bone" and along % 3 == 1:
        return tone(BONE, -0.2) if across in (0, fw - 1) else c  # the ribs' ends
    if e == "echo" and along % 3 == 2:
        return SCULK_GLOW
    if e == "mushroom" and across % 2 == 1:
        return tone(c, -0.18)  # gills
    if e == "star" and h2(px, py, 40) > 0.95:
        return STARLIGHT[0]
    if e == "aurora":
        return mix(c, AURORA[(along // 2) % 4], 0.18)
    return c


def horn(skin, c, face, yy, fh):
    e = skin.element
    tip = face == "top" or yy == 0
    if e == "iron" and (tip or yy == 1):
        return RUST
    if e == "blossom" and tip:
        return BLOSSOM_PETAL  # blossom on the branch
    if e == "sun" and tip:
        return SUN_CORE
    if e == "echo" and tip:
        return SCULK_BRIGHT
    if e == "phoenix" and tip:
        return EMBERS
    if e == "coral" and yy % 2 == 1 and face in ("east", "west"):
        return tone(c, 0.3)  # knobbly coral
    if e == "gold" and tip:
        return tone(c, 0.6)
    return c


# --- Wings ----------------------------------------------------------------------------------------------------------

def membrane(skin, c, g, t, part, level):
    """The wing membrane: g runs from the body to the wingtip (0..39), t from the leading edge back (0..15)."""
    e, pal = skin.element, skin.pal
    if part in ("bone", "nail"):
        return c
    if e in MORE:
        return more_pattern(e, c, g, t, g, t, g, t, 77)
    if e == "slime":
        if fbm(g, t, 61, 4) > 0.62:
            c = tone(c, -0.25)
        if h2(g, t, 62) > 0.97:
            c = mix(c, WHITE, 0.6)
    elif e == "mushroom":
        if g % 2 == 0:
            c = tone(c, -0.15)  # gills fanning out
        dx, dy, spot = nearest(g, t, 63, 6.0, 0.4)
        if spot and math.hypot(dx, dy) < 1.0:
            c = MUSHROOM_SPOT
    elif e == "blossom":
        dx, dy, flower = nearest(g, t, 64, 6.5, 0.45)
        if flower and abs(dx) < 0.6 and abs(dy) < 0.6:
            c = BLOSSOM_HEART
        elif flower and ((abs(dx) < 0.6 and abs(dy) < 1.7) or (abs(dy) < 0.6 and abs(dx) < 1.7)):
            c = BLOSSOM_PETAL
    elif e == "iron":
        if (g // 5 + t // 4) % 2:
            c = tone(c, -0.08)
        if g % 5 == 2 and t % 4 == 1:
            c = tone(pal["light"], 0.1)  # rivets
    elif e == "bone":
        # Ragged and holed, like an old flag: the bones show through.
        if part == "skin" and t > 3 and fbm(g, t, 65, 3) > 0.7:
            return None
        if part == "rim" and h2(g, t, 66) > 0.6:
            return None
    elif e == "jungle":
        if abs(math.sin(g * 0.7 + t * 0.45)) < 0.16 and t > 1:
            c = tone(c, 0.35)  # leaf veins
        elif part == "rim":
            c = tone(c, -0.3)
    elif e == "echo":
        d1, d2, _ = voronoi(g + 0.5, t + 0.5, 67, 4.5)
        if d2 - d1 < 0.4:
            c = SCULK_GLOW
    elif e == "chameleon":
        if g % 4 == 0 and t > 1:
            c = tone(c, -0.16)  # ribs fanning out
    elif e == "coral":
        c = mix(c, (0x1c, 0x8c, 0xa8), t / 15 * 0.5)
        if g % 3 == 0:
            c = tone(c, -0.2)  # fin rays
    elif e == "sun":
        c = mix(c, (0xff, 0xe6, 0x60), t / 15 * 0.6)
        angle = math.atan2(t + 0.5, g - 20 + 0.5)
        if abs(math.sin(angle * 9)) > 0.92 and t > 1:
            c = SUNSHINE  # rays from the wrist
    elif e == "moon":
        roll = h2(g, t, 68)
        if roll > 0.965:
            c = WHITE
        elif roll > 0.94:
            c = (0x8a, 0x98, 0xd0)
    elif e == "star":
        cloud = fbm(g, t, 69, 6)
        if cloud > 0.58:
            c = mix(c, (0x7a, 0x3a, 0xaa) if cloud < 0.68 else (0xd0, 0x60, 0xb0), 0.55)  # a nebula
        if h2(g, t, 70) > 0.955:
            c = STARLIGHT[int(h2(g, t, 71) * 3)]
    elif e == "aurora":
        hue = AURORA[min(3, g // 10)]
        c = mix(c, hue, 0.55 + 0.25 * math.sin(g * 1.3) ** 2)
        c = tone(c, -0.35 * t / 15)
    elif e == "phoenix":
        c = mix(mix((0xc8, 0x22, 0x10), c, min(1, t / 7)), (0xff, 0xe0, 0x60), max(0, (t - 8) / 8))
        if (g + t // 2) % 3 == 0:
            c = tone(c, -0.2)  # feather edges
    elif e == "nimbus":
        if (g + t) % 6 == 0 and t > 2:
            c = mix(c, RAIN, 0.6)  # rain streaks
    elif e == "gold":
        if part == "rim":
            c = (0xf0, 0xc8, 0x48)  # gold trim
        elif (g + t) % 4 == 0:
            c = tone(c, 0.12)  # the velvet's sheen
    elif e == "pearl":
        c = mix(c, NACRE[((g + t) // 4) % len(NACRE)], 0.45)
    return c


# --- Stamps: markings drawn over the whole skin -----------------------------------------------------------------

def stamps(img, placed, skin, box_faces):
    if skin.element != "star":
        return img
    # Constellations across the Star dragon's flanks: bright stars joined by faint lines.
    shapes = (((2, 3), (5, 2), (8, 4), (11, 3), (13, 6)), ((3, 7), (6, 5), (9, 7), (7, 9)))
    line, bright = (0x58, 0x66, 0xc8), (0xff, 0xff, 0xff)
    for name, offset in (("body", 2), ("tail1", 0), ("neck", 0)):
        u, v, size, _ = placed[name]
        f = box_faces(u, v, size)
        for k, face in enumerate(("east", "west")):
            fx, fy, fw, fh = f[face]
            points = shapes[k % len(shapes)]
            for (a0, b0), (a1, b1) in zip(points, points[1:]):
                steps = max(abs(a1 - a0), abs(b1 - b0))
                for i in range(steps + 1):
                    along, down = round(a0 + (a1 - a0) * i / steps) + offset, round(b0 + (b1 - b0) * i / steps)
                    if 0 <= along < fw and 0 <= down < fh:
                        xx = along if face == "west" else fw - 1 - along
                        img[fy + down][fx + xx] = (*mix(img[fy + down][fx + xx], line, 0.7), 255)
            for along, down in points:
                along += offset
                if 0 <= along < fw and 0 <= down < fh:
                    xx = along if face == "west" else fw - 1 - along
                    img[fy + down][fx + xx] = (*bright, 255)
    return img


# --- Eggs -----------------------------------------------------------------------------------------------------------

def egg(element, pal, c, x, y, level, shell, spot):
    """A 2.0 dragon's egg shell texel: the shell's shading in {@code c}, then the kind's own pattern."""
    seed = sum(ord(ch) for ch in element)
    if element in MORE:
        return more_pattern(element, c, x, y, x, y, x, y, seed)
    if element == "slime":
        c = mix(c, WHITE, 0.5) if h2(x, y, 81) > 0.93 else tone(c, -0.3) if fbm(x, y, seed, 5) > 0.6 else c
    elif element == "mushroom":
        dx, dy, s = nearest(x, y, seed, 5, 0.7)
        if s and math.hypot(dx, dy) < 1.3:
            c = MUSHROOM_SPOT
    elif element == "blossom":
        dx, dy, s = nearest(x, y, seed, 5, 0.6)
        if s and abs(dx) < 0.6 and abs(dy) < 0.6:
            c = BLOSSOM_HEART
        elif s and ((abs(dx) < 0.6 and abs(dy) < 1.7) or (abs(dy) < 0.6 and abs(dx) < 1.7)):
            c = BLOSSOM_PETAL
    elif element == "iron":
        if x % 5 == 4 or y % 4 == 3:
            c = shell[1]
        elif x % 5 == 1 and y % 4 == 1:
            c = shell[6]
    elif element == "bone":
        if (y + x // 6) % 4 == 1 and 3 < x < 13:
            c = tone(BONE, -0.2 + 0.3 * (y < 8))
    elif element == "jungle":
        dx, dy, s = nearest(x, y, seed, 4.5, 0.7)
        d = math.hypot(dx, dy)
        if s and d < 0.8:
            c = ROSETTE_HEART
        elif s and d < 1.7:
            c = ROSETTE
    elif element == "echo":
        d1, d2, _ = voronoi(x + 0.5, y + 0.5, seed, 4.0)
        if d2 - d1 < 0.5:
            c = SCULK_GLOW
    elif element == "coral":
        dx, dy, s = nearest(x, y, seed, 5, 0.6)
        if s and 0.8 < math.hypot(dx, dy) < 1.5:
            c = (0xff, 0xf4, 0xee)
    elif element == "sun":
        dx, dy = x - 7.5, y - 8.5
        if math.hypot(dx, dy) < 2.6:
            c = SUN_CORE
        elif abs(math.sin(4 * math.atan2(dy, dx))) > 0.9:
            c = SUNSHINE
    elif element == "moon":
        dx, dy, s = nearest(x, y, seed, 5, 0.55)
        if s and math.hypot(dx, dy) < 1.4:
            c = tone(c, -0.25)
    elif element == "star":
        if h2(x, y, 82) > 0.9:
            c = STARLIGHT[int(h2(x, y, 83) * 3)]
    elif element == "aurora":
        c = mix(c, AURORA[(y // 3) % 4], 0.5 + 0.3 * math.sin(x * 0.9 + y) ** 2)
    elif element == "phoenix":
        if (y + round(1.5 * math.sin(x * 0.9))) % 4 == 0:
            c = (0xff, 0xd8, 0x50)
    elif element == "nimbus":
        if fbm(x, y, seed, 4) > 0.58:
            c = mix(c, WHITE, 0.65)
    elif element == "gold":
        if h2(x, y, 84) > 0.92:
            c = GEMS[int(h2(x, y, 85) * 3)]
    elif element == "pearl":
        c = mix(c, NACRE[(x + y) // 3 % len(NACRE)], 0.4)
    elif element == "chameleon":
        # A patchwork of every colour it might turn.
        d1, d2, ident = voronoi(x + 0.5, y + 0.5, seed, 3.5)
        c = mix(c, CHAMELEON_PATCHES[int(h2(*ident, seed) * len(CHAMELEON_PATCHES)) % len(CHAMELEON_PATCHES)], 0.75)
        if d2 - d1 < 0.35:
            c = tone(c, -0.22)
    return c
