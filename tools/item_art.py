"""Item icons, the effect icon, the advancement backdrop and worn-armor textures (the 1.4.0+ texture pass).

Icons are drawn as shape grids where each letter is a part (one material). Every part is shaded as a bevelled piece
lit from the upper left (a gentle gradient across the part, a bright upper/left rim, a shaded lower/right rim), and
the whole sprite then gets a selective outline in a deep shade of the part beside it, as vanilla items have.
"""
import math

from dragon_skins import shade_pick, skin_ramp
from texkit import blank, h2, mix, scale_cell, scale_light, tone

FLAT = "flat"


def mat(c, lo=-0.62, hi=0.5):
    return skin_ramp(tone(c, lo), c, tone(c, hi))


def flat(c):
    return (FLAT, c)


def sprite(rows, mats, overlay="", after="", patterns=None, outline=True):
    """rows: equal strings; mats: char -> 7-tone ramp or flat(colour). Overlay chars (flats) don't break the bevel of
    the part they sit in; 'after' chars are drawn last, without an outline (sparkles). patterns: char -> f(x, y) giving
    an extra light level (surface texture such as scales)."""
    h, w = len(rows), len(rows[0])
    for row in rows:
        assert len(row) == w, (row, w)
    patterns = patterns or {}
    img = blank(w, h)
    darkest = {}
    boxes = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in mats:
                b = boxes.setdefault(ch, [x, y, x, y])
                b[0], b[1], b[2], b[3] = min(b[0], x), min(b[1], y), max(b[2], x), max(b[3], y)

    def same(ch, x, y):
        return 0 <= x < w and 0 <= y < h and (rows[y][x] == ch or rows[y][x] in overlay)

    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in mats or ch in after:
                continue
            m = mats[ch]
            if m[0] == FLAT:
                c = m[1]
                darkest[(x, y)] = tone(c, -0.75)
            else:
                x0, y0, x1, y1 = boxes[ch]
                gx = (x - x0) / max(1, x1 - x0)
                gy = (y - y0) / max(1, y1 - y0)
                level = 0.3 - 0.55 * (0.45 * gx + 0.55 * gy)
                if not same(ch, x, y - 1):
                    level += 0.34
                if not same(ch, x - 1, y):
                    level += 0.16
                if not same(ch, x, y + 1):
                    level -= 0.34
                if not same(ch, x + 1, y):
                    level -= 0.16
                if ch in patterns:
                    level += patterns[ch](x, y)
                c = shade_pick(m, level, x, y, dither=0.3)
                darkest[(x, y)] = tone(m[0], -0.35)
            img[y][x] = (*c[:3], 255)
    if outline:
        out = [r[:] for r in img]
        for y in range(h):
            for x in range(w):
                if img[y][x][3]:
                    continue
                for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and img[ny][nx][3] and (nx, ny) in darkest:
                        out[y][x] = (*darkest[(nx, ny)], 255)
                        break
        img = out
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in after:
                img[y][x] = (*mats[ch][1][:3], 255)
    return img


def scales_pattern(x, y):
    _, cx, cy = scale_cell(x, y, 3, 2)
    return scale_light(cx, cy, 3, 2) * 0.55


# --- Dragon armor -----------------------------------------------------------------------------------------------------

ARMOR = [
    "................",
    "...........H....",
    "..........HH....",
    ".........HHA....",
    "....AAAAAAAAA...",
    "...AgAAAAAAAAA..",
    "...AArAAAAAArA..",
    "...AAAAAAAAAAA..",
    "....AAAAAAAAA...",
    ".....SSSSSSS....",
    "....BBBBBBBBB...",
    "...BgrBBBBBrBB..",
    "...BBBBBBBBBBB..",
    "....BBBBBBBBB...",
    "......BBBBB.....",
    "................",
]
METAL_COLORS = {
    "iron": ((0xb2, 0xb4, 0xba), (0x50, 0x52, 0x58)),
    "golden": ((0xf0, 0xc4, 0x38), (0x7a, 0x50, 0x08)),
    "diamond": ((0x5c, 0xe0, 0xd8), (0x14, 0x5e, 0x5c)),
    "netherite": ((0x4c, 0x44, 0x4a), (0xd8, 0xa8, 0x5a)),
    "elder": ((0xd8, 0xb8, 0x70), (0xc8, 0x20, 0x30)),
}


def dragon_armor(metal):
    body, rivet = METAL_COLORS[metal]
    m = mat(body, -0.7, 0.6)
    return sprite(ARMOR, {"A": m, "B": m, "H": mat(tone(body, 0.2), -0.6, 0.6), "S": mat((0x6b, 0x44, 0x23)),
                          "r": flat(rivet), "g": flat(tone(body, 1.0))}, overlay="rg")


# --- Scales, heart, whistle -------------------------------------------------------------------------------------------

SCALE = [
    "................",
    "................",
    "......SSSS......",
    ".....SSSSSS.....",
    "....SSSSSSSS....",
    "....SSgSSSSS....",
    "...SSSgSSSSSS...",
    "...SSSSSSSSSS...",
    "...SzSSSSSSzS...",
    "....SzzSSzzS....",
    "....SSSzzSSS....",
    ".....SSSSSS.....",
    "......SSSS......",
    ".......SS.......",
    "................",
    "................",
]


def scale_icon(color):
    return sprite(SCALE, {"S": mat(color, -0.7, 0.55), "g": flat(tone(color, 1.0)), "z": flat(tone(color, -0.45))}, overlay="gz")


HEART = [
    "................",
    "................",
    "...HHH....HHH...",
    "..HHHHH..HHHHH..",
    ".HHgHHHHHHHHHHH.",
    ".HgHHHHHHHHHHHH.",
    ".HHHHHHHGHHHHHH.",
    ".HHHHHHGGGHHHHH.",
    "..HHHHHHGHHHHH..",
    "...HHHHHHHHHH...",
    "....HHHHHHHH....",
    ".....HHHHHH.....",
    "......HHHH......",
    ".......HH.......",
    "................",
    "................",
]


def heart_icon():
    return sprite(HEART, {"H": mat((0xb8, 0x1c, 0x22), -0.7, 0.5), "g": flat((0xff, 0xb8, 0xa8)),
                          "G": flat((0xff, 0xd8, 0x50))}, overlay="gG")


WHISTLE = [
    "................",
    "................",
    "...........ss...",
    "..........s..s..",
    "..........s..s..",
    "...........ss...",
    "...........s....",
    "...WWWWWWWWb....",
    "..WWkWWWWWWb....",
    ".MWWWWWWWWWbb...",
    "..WWWWWWWWWb....",
    "...WWWWWWWWb....",
    "................",
    "................",
    "................",
    "................",
]


def whistle_icon():
    return sprite(WHISTLE, {"W": mat((0xf0, 0xe6, 0xcc), -0.6, 0.4), "M": mat((0xc8, 0xb8, 0x98)), "b": mat((0xd8, 0xa8, 0x48)),
                            "k": flat((0x4a, 0x3a, 0x2a)), "s": flat((0xc8, 0xa8, 0x70))}, overlay="k")


WINGS = [
    "................",
    "..BBB......BBB..",
    ".BBMMM....MMMBB.",
    ".BMMMMM..MMMMMB.",
    ".BMMFMM..MMFMMB.",
    ".BMMFMM..MMFMMB.",
    ".BMFMMM..MMMFMB.",
    ".BMFMMM..MMMFMB.",
    ".BMFMM....MMFMB.",
    "..MFMM....MMFM..",
    "..MFMM....MMFM..",
    "..MMM......MMM..",
    "...MM......MM...",
    "...M........M...",
    "................",
    "................",
]


def wings_icon():
    teal = (0x1f, 0x8a, 0x6a)
    return sprite(WINGS, {"M": mat(teal, -0.6, 0.6), "B": mat((0x0e, 0x4a, 0x3a), -0.4, 0.4), "F": flat(tone(teal, -0.5))},
                  overlay="F")


# --- Dragonscale armor ------------------------------------------------------------------------------------------------

SCALE_TEAL = (0x1f, 0x8a, 0x6a)
GOLD_TRIM = (0xe0, 0xb0, 0x40)
DRAGONSCALE_PIECES = {
    "helmet": [
        "................",
        "..H..........H..",
        "..HH........HH..",
        "...HSSSSSSSSH...",
        "...SSSSSSSSSS...",
        "..SSSSSSSSSSSS..",
        "..SSSSSSSSSSSS..",
        "..SSGGGGGGGGSS..",
        "..SSS......SSS..",
        "..SSS......SSS..",
        "..SS........SS..",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "..PPP......PPP..",
        ".PPPPP....PPPPP.",
        ".PPPPSSSSSSPPPP.",
        ".PPPPSSSSSSPPPP.",
        "..PPSSSSSSSSPP..",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...GGGGGGGGGG...",
        "................",
        "................",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "...GGGGGGGGGG...",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "................",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "...SSSS..SSSS...",
        "..SSSSS..SSSSS..",
        ".SSSSSS..SSSSSS.",
        ".GGGGGG..GGGGGG.",
        "................",
        "................",
        "................",
        "................",
    ],
}


def dragonscale_icon(piece):
    m = mat(SCALE_TEAL, -0.65, 0.6)
    return sprite(DRAGONSCALE_PIECES[piece], {"S": m, "P": m, "G": mat(GOLD_TRIM), "H": mat((0xf0, 0xe8, 0xd0))},
                  patterns={"S": scales_pattern, "P": scales_pattern})


def dragonscale_worn():
    """The worn dragonscale armor (64x32 humanoid layers): shaded overlapping teal scales on every part, gold trim at the
    chestplate's hem and the boots' cuffs, the face left open."""
    def scale_px(x, y):
        cell, cx, cy = scale_cell(x, y, 4, 3)
        level = scale_light(cx, cy, 4, 3) * 0.8 + (h2(cell[0], cell[1], 3) - 0.5) * 0.2
        return shade_pick(mat(SCALE_TEAL, -0.7, 0.6), level, x, y, dither=0.3)

    def layer(paint):
        img = blank(64, 32)
        for y in range(32):
            for x in range(64):
                if paint(x, y):
                    img[y][x] = (*scale_px(x, y), 255)
        return img

    def outer(x, y):
        head = y < 16 and x < 32 and not (8 <= x < 16 and 8 <= y < 16 and 9 <= x <= 14 and y >= 10)  # the face stays open
        body = 16 <= x < 40 and 16 <= y < 32
        arms = 40 <= x < 56 and 16 <= y < 32
        boots = x < 16 and (y >= 27 or (8 <= x < 12 and 16 <= y < 20))
        return head or body or arms or boots

    def leggings(x, y):
        return (x < 16 and 20 <= y < 30) or (16 <= x < 40 and 28 <= y < 32)

    main = layer(outer)
    gold = mat(GOLD_TRIM)
    for x in range(16, 40):
        main[31][x] = (*(gold[5] if x % 2 else gold[3]), 255)  # gold at the chestplate's hem
        main[30][x] = (*gold[2], 255)
    for x in range(0, 16):
        if main[31][x][3]:
            main[31][x] = (*(gold[4] if x % 2 else gold[3]), 255)  # and the boots' cuffs
    legs = layer(leggings)
    for x in range(16, 40):
        legs[28][x] = (*(gold[5] if x % 3 == 0 else gold[3]), 255)  # a gold-studded belt
    return main, legs


def dragon_wings_worn():
    """Dragon Wings as worn, on the 64x32 elytra layout (each wing a 10x20x2 box at 22,0): a scaled arm bone along the
    top, finger bones fanning down, a shaded teal membrane and a scalloped trailing edge."""
    img = blank(64, 32)
    mem, bone = mat(SCALE_TEAL, -0.6, 0.6), mat((0x0e, 0x4a, 0x3a), -0.4, 0.5)
    for y in range(22):
        for x in range(22, 46):
            if y < 2 and not 24 <= x < 44:
                continue  # the box's top and bottom faces are only 10 wide
            fx = (x - 24) % 12
            finger = x in (26, 29, 32, 38, 41, 44)
            if y >= 20 and not finger and (y == 21 or fx % 3 == 1):
                continue  # the scalloped trailing edge
            if y < 4:
                c = bone[5] if y == 2 else bone[3] if y == 3 else bone[4]
            elif finger:
                c = bone[2]
            else:
                c = shade_pick(mem, 0.35 - 0.6 * y / 21 + (0.18 if (x - 1) in (26, 29, 32, 38, 41, 44) else 0), x, y)
            img[y][x] = (*c, 255)
    return img


# --- Tools and gadgets ------------------------------------------------------------------------------------------------

SHRINK_RAY = [
    "................",
    "................",
    "...........CC...",
    "..........CCCC..",
    "...BBBBBBBCCCC..",
    "..BBBBBBBBBCC...",
    "..BgBRBRBBBBB...",
    "..BBBRBRBBBBB...",
    "...BBBHHBBBB....",
    ".....HHH........",
    ".....HHH........",
    ".....HHH........",
    "......HH........",
    "................",
    "................",
    "................",
]


def shrink_ray_icon():
    return sprite(SHRINK_RAY, {"B": mat((0x8a, 0x8e, 0xa0)), "C": mat((0xc0, 0x80, 0xf0), -0.6, 0.6), "H": mat((0x3a, 0x3a, 0x46)),
                               "R": flat((0xd8, 0x8a, 0x40)), "g": flat((0xe8, 0xec, 0xff))}, overlay="Rg")


CHISEL = [
    "................",
    "............TT..",
    "...........TTT..",
    "..........TTT...",
    ".........TTT....",
    "........TTT.....",
    ".......FFT......",
    "......WFF.......",
    ".....WWW........",
    "....WWW.........",
    "...WWW..........",
    "..WWW.....A.....",
    "..WW.....AAA....",
    "..........A.....",
    "................",
    "................",
]


def chisel_icon():
    return sprite(CHISEL, {"T": mat((0xc8, 0xcc, 0xd6), -0.7, 0.5), "F": mat((0xd8, 0xa8, 0x48)), "W": mat((0x9a, 0x6a, 0x3a)),
                           "A": flat((0xe8, 0xd0, 0xff))}, after="A")


CANNON = [
    "................",
    "................",
    "................",
    "...........MM...",
    "......KKKKKKMM..",
    "....KKKKKKKKMM..",
    "...KKKKKKKKKMM..",
    "...KKKKKKKKKKM..",
    "....KKKKKKKK....",
    ".....WWWWWW.....",
    "....WWWWWWWW....",
    "...OOWWWWWWOO...",
    "..OOhO....OhOO..",
    "..OOOO....OOOO..",
    "...OO......OO...",
    "................",
]


def cannon_icon():
    return sprite(CANNON, {"K": mat((0x3a, 0x3e, 0x46), -0.5, 0.7), "M": mat((0x4a, 0x4e, 0x56), -0.5, 0.6),
                           "W": mat((0x7a, 0x52, 0x30)), "O": mat((0x4a, 0x30, 0x18)), "h": flat((0xa8, 0xa8, 0xb0))}, overlay="h")


AIRSHIP = [
    "................",
    ".....RRCCRR.....",
    "...CCRRCCRRCC...",
    "..RCCRRCCRRCCR..",
    "..RCCRRCCRRCCR..",
    "..RCCRRCCRRCCR..",
    "...CCRRCCRRCC...",
    ".....RRCCRR.....",
    "......r..r......",
    ".....r....r.....",
    "..WWWWWWWWWWWW..",
    "..WwwwwwwwwwwW..",
    "...WWWWWWWWWW...",
    "....KKKKKKKK....",
    "................",
    "................",
]


def airship_icon():
    return sprite(AIRSHIP, {"R": mat((0xc0, 0x30, 0x2a)), "C": mat((0xf0, 0xe4, 0xc8), -0.5, 0.3), "r": flat((0xc8, 0xa8, 0x70)),
                            "W": mat((0x9a, 0x6a, 0x3a)), "w": flat((0x6a, 0x46, 0x22)), "K": mat((0x5a, 0x3a, 0x20))}, overlay="w")


# --- Bottles, dewdrop, gem pixels -------------------------------------------------------------------------------------

BOTTLE = [
    "................",
    "......KKKK......",
    "......KKKK......",
    ".......gg.......",
    ".......gg.......",
    ".....gggggg.....",
    "....gLLLLLLg....",
    "...gLLLLLLLLg...",
    "...gLhLLLsLLg...",
    "...gLhLLLLLLg...",
    "...gLLLsLLLLg...",
    "...gLLLLLLsLg...",
    "....gLLLLLLg....",
    ".....gggggg.....",
    "................",
    "................",
]


def bottle_icon(liquid, sparkle):
    return sprite(BOTTLE, {"K": mat((0x9a, 0x70, 0x46)), "g": mat((0xc8, 0xe8, 0xf0), -0.5, 0.4), "L": mat(liquid, -0.6, 0.55),
                           "h": flat((0xff, 0xff, 0xff)), "s": flat(sparkle)}, overlay="hs")


DEWDROP = [
    "................",
    "................",
    ".......D........",
    ".......D........",
    "......DDD.......",
    "......DDD.......",
    ".....DDDDD......",
    ".....DhDDD......",
    "....DDhDDDD.....",
    "....DDDDDDD.....",
    "....DDDDDDD.....",
    "....DDDDDDD.....",
    ".....DDDDD......",
    "......DDD.......",
    "................",
    "................",
]


def dewdrop_icon():
    return sprite(DEWDROP, {"D": mat((0x6a, 0xc8, 0xf0), -0.6, 0.6), "h": flat((0xff, 0xff, 0xff))}, overlay="h")


GEM = [
    "................",
    "............w...",
    "...........www..",
    "............w...",
    "....GGGGGGGG....",
    "....GGGGGGGG....",
    "....GGFFFFGG....",
    "....GGFFFFGG....",
    "....GGFFFFGG....",
    "....GGFFFFGG....",
    "....GGGGGGGG....",
    "....GGGGGGGG....",
    "................",
    "..w.............",
    ".www............",
    "..w.............",
]


def gem_icon(color):
    return sprite(GEM, {"G": mat(color, -0.7, 0.5), "F": mat(tone(color, 0.35), -0.5, 0.6), "w": flat((0xff, 0xff, 0xff))}, after="w")


# --- Effect icon and advancement backdrop ------------------------------------------------------------------------------

SHRUNKEN = [
    "..................",
    "..a............a..",
    "..aa..........aa..",
    "..aaa........aaa..",
    "...aaa......aaa...",
    "....aa......aa....",
    "..................",
    "........pp........",
    "........pp........",
    ".......pppp.......",
    "........pp........",
    ".......p..p.......",
    "....aa......aa....",
    "...aaa......aaa...",
    "..aaa........aaa..",
    "..aa..........aa..",
    "..a............a..",
    "..................",
]


def shrunken_icon():
    return sprite(SHRUNKEN, {"a": mat((0xb8, 0x98, 0xf8), -0.6, 0.5), "p": mat((0xf4, 0xf4, 0xff), -0.4, 0.2)})


def asleep_icon():
    """The Asleep effect (a Moon dragon's): a pale crescent moon with a big Z and a little z drifting up from it."""
    rows = []
    for y in range(18):
        row = ""
        for x in range(18):
            moon = math.hypot(x + 0.5 - 7.2, y + 0.5 - 10) < 6.3 and math.hypot(x + 0.5 - 10.4, y + 0.5 - 7.6) > 5.3
            row += "m" if moon else "."
        rows.append(row)
    for (zx, zy, w) in ((12, 1, 5), (13, 8, 4)):
        for i in range(w):
            y = zy + i
            if i in (0, w - 1):
                rows[y] = rows[y][:zx] + "z" * w + rows[y][zx + w:]
            else:
                x = zx + w - 1 - i
                rows[y] = rows[y][:x] + "z" + rows[y][x + 1:]
    return sprite(rows, {"m": mat((0xe6, 0xec, 0xff), -0.55, 0.3), "z": mat((0xb0, 0xc0, 0xff), -0.5, 0.4)})


BOUNCY = [
    "..................",
    "..................",
    "..................",
    "....ssssssssss....",
    "....sggsssssss....",
    "....sgcccccccs....",
    "....scceccceccs...",
    "....scceccceccs...",
    "....sccccccccs....",
    "....sccckkkccs....",
    "....scccccccss....",
    "....ssssssssss....",
    "..................",
    "..................",
    ".....llllllll.....",
    "..................",
    "..................",
    "..................",
]


def bouncy_icon():
    """The Bouncy effect (a Slime dragon's gift): a jelly slime cube, mid-bounce over its shadow, its core showing."""
    rows = [r[:18] for r in BOUNCY]
    return sprite(rows, {"s": mat((0x7e, 0xd0, 0x58), -0.6, 0.45), "c": mat((0x5e, 0xb0, 0x40), -0.5, 0.3),
                         "g": flat((0xe8, 0xff, 0xd8)), "e": flat((0x1a, 0x38, 0x12)), "k": flat((0x2a, 0x5a, 0x1e)),
                         "l": flat((0x3a, 0x4a, 0x34))}, overlay="cgek")


def sky_backdrop():
    """The advancement screen's tile: a deep evening sky with soft clouds, seamless when tiled."""
    sky, cloud = (0x2a, 0x3e, 0x66), (0x50, 0x68, 0x98)

    def tiled(x, y, seed, period):
        fx, fy = x / 4, y / 4
        x0, y0 = math.floor(fx), math.floor(fy)
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        g = lambda i, j: h2(i % period, j % period, seed)
        a, b, c, d = g(x0, y0), g(x0 + 1, y0), g(x0, y0 + 1), g(x0 + 1, y0 + 1)
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty

    img = blank(16, 16)
    for y in range(16):
        for x in range(16):
            n = tiled(x, y, 41, 4) * 0.7 + tiled(x * 2, y * 2, 43, 8) * 0.3
            c = shade_pick(mat(sky, -0.4, 0.3), (n - 0.5) * 0.8, x, y, dither=0.6)
            if n > 0.6:
                c = mix(cloud, (0x7a, 0x8e, 0xbc), (n - 0.6) * 2.5)
            img[y][x] = (*c, 255)
    return img
