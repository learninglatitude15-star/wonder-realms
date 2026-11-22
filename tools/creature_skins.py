"""Skins for the creatures other than the dragons (the 1.4.0+ texture pass): the sky whale and Storm Leviathan, the
rhino beetle, dragonfly, grasshopper and firefly, their saddles, and the Firefly Jar. Painted with texkit; texel
placement follows GeckoLib's box UVs (see dragon_skins.frame)."""
import math

from dragon_skins import (BLANKET, GOLD, IRON, LEATHER, SADDLE, box_faces, frame, shade_pick, skin_ramp, soft_ramp)
from texkit import bayer, blank, fbm, h2, mix, tone


def paint_model(placed, tex, painter):
    """painter(name, kind, face, xx, yy, fw, fh, px, py) -> colour (RGB or RGBA) or None for clear."""
    img = blank(tex, tex)
    for name, (u, v, size, kind) in placed.items():
        for face, (fx, fy, fw, fh) in box_faces(u, v, size).items():
            for yy in range(fh):
                for xx in range(fw):
                    c = painter(name, kind, face, xx, yy, fw, fh, fx + xx, fy + yy)
                    if c is not None:
                        img[fy + yy][fx + xx] = (*c[:3], c[3] if len(c) > 3 else 255)
    return img


def overpaint(img, placed, cube, faces_, painter):
    """A copy with some faces of one cube painted over: painter(face, xx, yy, fw, fh, px, py) -> colour or None."""
    img = [row[:] for row in img]
    u, v, size, _ = placed[cube]
    for face, (fx, fy, fw, fh) in box_faces(u, v, size).items():
        if face not in faces_:
            continue
        for yy in range(fh):
            for xx in range(fw):
                c = painter(face, xx, yy, fw, fh, fx + xx, fy + yy)
                if c is not None:
                    img[fy + yy][fx + xx] = (*c[:3], 255)
    return img


def side_level(down, fh, top=0.28, bottom=-0.4):
    return top + (bottom - top) * down / max(1, fh - 1)


# --- Sky whale and Storm Leviathan ------------------------------------------------------------------------------------

WHALE = dict(back=(0x2a, 0x40, 0x80), flank=(0x4a, 0x6a, 0xb8), belly=(0xd8, 0xde, 0xf2), cloud=(0xee, 0xf4, 0xff),
             fin=(0x34, 0x4e, 0x96), barnacle=(0xc8, 0xc0, 0xae), eye=(0x10, 0x10, 0x1a), mouth=(0x16, 0x1c, 0x38))
LEVIATHAN = dict(back=(0x26, 0x2a, 0x36), flank=(0x3e, 0x44, 0x56), belly=(0x74, 0x7a, 0x8e), cloud=(0x52, 0x58, 0x6e),
                 fin=(0x20, 0x24, 0x30), barnacle=(0x8a, 0x8a, 0x92), eye=(0xff, 0xe0, 0x40), mouth=(0x0c, 0x0e, 0x14))
BOLT = (0xff, 0xf0, 0x60)


class Whale:
    def __init__(self, pal, storm=False):
        self.p, self.storm = pal, storm
        self.back = skin_ramp(tone(pal["back"], -0.6), pal["back"], pal["flank"])
        self.flank = skin_ramp(pal["back"], pal["flank"], tone(pal["flank"], 0.5))
        self.belly = skin_ramp(tone(pal["belly"], -0.55), pal["belly"], tone(pal["belly"], 0.4))
        self.cloud = soft_ramp(pal["cloud"], 0.6)
        self.fin = skin_ramp(tone(pal["fin"], -0.6), pal["fin"], pal["flank"])
        self.scars = set()

    def hide(self, name, face, xx, yy, fw, fh, px, py):
        p = self.p
        along, down, across = frame(face, xx, yy, fw, fh)
        mottle = (fbm(px, py, 31, 6) - 0.5) * (0.25 if self.storm else 0.4)
        cloud = fbm(px * 1.0, py * 1.3, 47 if not self.storm else 53, 5)
        if face == "bottom":
            if name == "body" and along < 22 and across % 2 == 0:
                return shade_pick(self.belly, -0.45, px, py)  # throat grooves
            return shade_pick(self.belly, 0.05 - 0.15 * (min(across, fw - 1 - across) == 0), px, py)
        if face == "top":
            c = shade_pick(self.back, 0.1 + mottle, px, py)
            if name == "body" and along < 9 and h2(px // 2, py // 2, 61) > 0.8:
                return self.barnacle(px, py)
            if cloud > 0.62:
                c = shade_pick(self.cloud, (cloud - 0.7) * 3, px, py)
            return c
        t = down / max(1, fh - 1) + (bayer(px, py) - 0.5) * 0.1
        if face in ("east", "west") and name == "body":
            if along is not None and 3 <= along <= 6 and 6 <= down <= 10:
                ring = mix(self.flank[3], self.belly[3], 0.35)  # the pale patch round the eye
                return ring if (along in (3, 6) or down in (6, 10)) else tone(ring, 0.15)
            if down == fh - 1 and along < 24:
                return p["mouth"]  # the long line of the mouth
        if t < 0.42:
            c = shade_pick(self.back, side_level(down, fh, 0.3, -0.05) + mottle, px, py)
        elif t < 0.74:
            c = shade_pick(self.flank, side_level(down, fh, 0.25, -0.25) + mottle * 0.6, px, py)
        else:
            c = shade_pick(self.belly, 0.1 - 0.35 * (t - 0.74) / 0.26, px, py)
        if t < 0.6 and cloud > 0.64:
            c = shade_pick(self.cloud, (cloud - 0.72) * 3 - 0.1, px, py)
        if face == "north" and name == "body" and down < 5 and h2(px // 2, py // 2, 61) > 0.82:
            return self.barnacle(px, py)
        return c

    def barnacle(self, px, py):
        b = self.p["barnacle"]
        return tone(b, -0.6) if h2(px, py, 62) > 0.65 else tone(b, 0.2 if (px + py) % 2 else -0.1)

    def fin_texel(self, name, face, xx, yy, fw, fh, px, py):
        """Flippers: dark on top, pale beneath, knobbly along the leading edge. The fluke is cut into a whale's tail."""
        along, down, across = frame(face, xx, yy, fw, fh)
        if name == "fluke":
            if face in ("south", "east", "west"):
                return None  # its edges are open so the cut-out shape shows
            if face in ("top", "bottom"):
                c = abs(across + 0.5 - fw / 2) / (fw / 2)
                lead = 1.0 + 6.5 * c ** 1.6
                trail = fh - 3.2 * max(0.0, 1 - c / 0.16) - 2.4 * c ** 4
                if not lead <= along + 0.5 <= trail:
                    return None
                if face == "bottom":
                    return shade_pick(self.belly, -0.15 - 0.2 * c, px, py)
                edge = along + 0.5 > trail - 1.2
                return shade_pick(self.fin, (0.35 if edge else 0.05) - 0.25 * c + (fbm(px, py, 9, 4) - 0.5) * 0.3, px, py)
            return self.fin[2]
        # The flippers (left one painted; the right is its mirror). "out" runs from the body to the tip.
        out = {"top": xx, "bottom": xx, "north": xx, "south": fw - 1 - xx}.get(face, 0)
        if face == "bottom":
            return shade_pick(self.belly, 0.1 - 0.3 * out / max(1, fw - 1), px, py)
        if face == "north" or (face == "top" and along == 0):
            return self.belly[4] if out % 3 == 1 else self.fin[4]  # knobbles on the leading edge
        if face in ("east", "west"):
            return self.fin[2]
        if face == "south":
            return self.fin[4]  # the pale trailing edge
        level = 0.15 - 0.3 * (along / max(1, fh - 1)) + (0.25 if along >= fh - 1 else 0)
        return shade_pick(self.fin, level + (fbm(px, py, 11, 4) - 0.5) * 0.3, px, py)

    def texel(self, name, kind, face, xx, yy, fw, fh, px, py):
        if (px, py) in self.scars:
            return BOLT
        if kind == "eye":
            if self.storm:
                return tone(self.p["eye"], 0.4) if (xx, yy) == (0, 0) else self.p["eye"]
            return (0xff, 0xff, 0xff) if (xx, yy) == (0, 0) else self.p["eye"]
        if kind == "jaw":
            along, down, across = frame(face, xx, yy, fw, fh)
            if face in ("east", "west", "north"):
                return self.p["mouth"] if down == 0 else self.belly[2]
            if face == "bottom" and across % 2 == 0:
                return shade_pick(self.belly, -0.45, px, py)  # throat grooves run on under the chin
            return shade_pick(self.belly, 0.05, px, py)
        if kind == "fin":
            return self.fin_texel(name, face, xx, yy, fw, fh, px, py)
        return self.hide(name, face, xx, yy, fw, fh, px, py)


def leviathan_scars(placed):
    """Jagged lightning scars down the Leviathan's back and flanks, unbroken (each step sideways is joined up), with
    a fork part way down: texel positions shared by the skin and the glowmask."""
    scars = set()
    for name in ("body", "tail1", "tail2"):
        u, v, size, _ = placed[name]
        for face, (fx, fy, fw, fh) in box_faces(u, v, size).items():
            if face not in ("top", "east", "west"):
                continue
            step = 12 if name == "body" else 9
            for k, start in enumerate(range(4, fw - 3, step)):
                x, y = start, 0
                end = fh - (3 if face != "top" else 0)
                while y < end:
                    scars.add((fx + x, fy + y))
                    y += 1
                    dx = (2, 0, -1, -2, 0, 1, 2, -1)[(y + 3 * k) % 8] if y % 2 == 0 else 0
                    for _ in range(abs(dx)):  # join each sideways step so the scar stays one line
                        x = max(0, min(fw - 1, x + (1 if dx > 0 else -1)))
                        scars.add((fx + x, fy + y - 1))
                    if y == end // 2 and h2(k, len(name), 5) > 0.35:  # a fork
                        bx, by = x, y
                        for i in range(5):
                            bx, by = bx + (1 if i % 2 == 0 else 0), by + 1
                            if bx < fw and by < end:
                                scars.add((fx + bx, fy + by))
    return scars


def whale_skins(placed, tex):
    whale = paint_model(placed, tex, Whale(WHALE).texel)
    storm = Whale(LEVIATHAN, storm=True)
    storm.scars = leviathan_scars(placed)
    leviathan = paint_model(placed, tex, storm.texel)
    glow = blank(tex, tex)
    for x, y in storm.scars:
        glow[y][x] = (*BOLT, 255)
    for name, (u, v, size, kind) in placed.items():
        if kind == "eye":
            for fx, fy, fw, fh in box_faces(u, v, size).values():
                for yy in range(fh):
                    for xx in range(fw):
                        glow[fy + yy][fx + xx] = leviathan[fy + yy][fx + xx]
    return whale, leviathan, glow


def whale_saddle(img, placed):
    """A long saddle blanket for four across the middle of its back: red with a woven diamond pattern, fringed with
    gold, and strapped underneath with a buckled girth."""
    _, _, (w, h, d), _ = placed["body"]
    start, end = d // 2 - 7, d // 2 + 7

    def top(face, xx, yy, fw, fh, px, py):
        along = fh - 1 - yy
        if not start <= along < end:
            return None
        if along in (start, end - 1):
            return GOLD[5] if xx % 2 == 0 else GOLD[3]
        if xx in (0, fw - 1):
            return GOLD[3]
        if xx in (1, fw - 2):
            return BLANKET[1]
        diamond = (abs((xx % 6) - 3) + abs(((along - start) % 6) - 3)) == 3
        return BLANKET[5] if diamond else shade_pick(BLANKET, 0.1 + 0.1 * (xx % 2), px, py)

    def side(face, xx, yy, fw, fh, px, py):
        along = xx if face == "west" else fw - 1 - xx
        if along in (d // 2 - 1, d // 2):
            return IRON[5] if yy == 6 else IRON[2] if yy == 7 else SADDLE[1]  # the girth strap and buckle
        if not start <= along < end or yy > 4:
            return None
        if yy == 4:
            return GOLD[5] if along % 2 == 0 else GOLD[2]  # the fringe
        if along in (start, end - 1):
            return GOLD[3]
        return shade_pick(BLANKET, 0.15 - 0.15 * yy, px, py)

    def belly(face, xx, yy, fw, fh, px, py):
        along = fh - 1 - yy
        return SADDLE[1] if along in (d // 2 - 1, d // 2) else None

    img = overpaint(img, placed, "body", ("top",), top)
    img = overpaint(img, placed, "body", ("east", "west"), side)
    return overpaint(img, placed, "body", ("bottom",), belly)


# --- Rhino beetle -----------------------------------------------------------------------------------------------------

BEETLE = dict(dark=(0x1e, 0x10, 0x08), base=(0x4a, 0x24, 0x10), light=(0x8a, 0x4a, 0x22), belly=(0x5a, 0x3a, 0x22),
              horn=(0x22, 0x16, 0x0e), leg=(0x2a, 0x1a, 0x10), eye=(0xd8, 0xa0, 0x30))


def beetle_texel(name, kind, face, xx, yy, fw, fh, px, py):
    """A glossy mahogany beetle: domed wing cases with a seam, rows of fine pits, a bright gloss down each case, a
    polished shield, a black horn with a shine, and jointed legs."""
    p = BEETLE
    shell = skin_ramp(p["dark"], p["base"], p["light"])
    along, down, across = frame(face, xx, yy, fw, fh)
    if kind == "shell":
        if face == "top":
            half = fw // 2
            inner = across if across < half else fw - 1 - across  # 0 at the outer edge of each case
            if across in (half - 1, half):
                return shell[0] if across == half - 1 else shell[1]  # the seam between the wing cases
            dome = math.sin(math.pi * (inner + 0.5) / half)
            level = -0.55 + 1.0 * dome - 0.25 * along / max(1, fh - 1)
            c = shade_pick(shell, level, px, py)
            if inner == 2 and 1 <= along <= fh - 3:
                c = tone(p["light"], 0.75 if along % 3 == 1 else 0.4)  # the gloss down each case
            elif inner in (1, 3) and along % 2 == 0 and name == "shell":
                c = tone(c, -0.25)  # rows of fine pits
            return c
        if face == "bottom":
            return shell[0]
        level = side_level(down, fh, 0.15, -0.65)
        c = shade_pick(shell, level, px, py)
        if down == 1 and (along if along is not None else across) % 4 == 1:
            c = tone(p["light"], 0.5)  # a glint along the curve
        return c
    if kind == "horn":
        hr = skin_ramp(tone(p["horn"], -0.4), p["horn"], (0x5a, 0x3e, 0x2a))
        if face == "top":
            return hr[6] if (xx + yy) % 3 == 0 else hr[4]
        return shade_pick(hr, side_level(down if down is not None else 0, fh, 0.45, -0.5), px, py)
    if kind == "head":
        if face in ("east", "west") and down == 1 and along == 1:
            return p["eye"]
        if face in ("east", "west") and down == 0 and along == 1:
            return tone(p["eye"], 0.5)
        return shade_pick(skin_ramp(tone(p["dark"], -0.3), p["dark"], p["base"]), side_level(down or 0, fh, 0.3, -0.4), px, py)
    if kind == "belly":
        if face == "bottom":
            seg = along % 3
            return shade_pick(skin_ramp(tone(p["belly"], -0.5), p["belly"], tone(p["belly"], 0.4)), (0.25, 0.0, -0.5)[seg], px, py)
        return shade_pick(skin_ramp(tone(p["belly"], -0.5), p["belly"], tone(p["belly"], 0.4)), -0.3, px, py)
    # legs: jointed, darker at the joints and the feet, with a sheen on top
    lr = skin_ramp(tone(p["leg"], -0.4), p["leg"], p["light"])
    if face == "top":
        return lr[5]
    if down is not None and down == fh - 1:
        return lr[0]
    seg = (along if along is not None else (down or 0))
    return lr[1] if seg % 3 == 2 else lr[3]


def beetle_saddle(img, placed):
    """A small saddle in the middle of the wing cases, strapped round under the belly."""
    _, _, (w, h, d), _ = placed["shell"]
    lo, hi = d // 2 - 3, d // 2 + 2

    def top(face, xx, yy, fw, fh, px, py):
        along = fh - 1 - yy
        if not (lo <= along <= hi and 1 <= xx <= fw - 2):
            return None
        if along == lo:
            return SADDLE[5]  # the pommel's edge
        if along in (hi,) or xx in (1, fw - 2):
            return SADDLE[1]
        if xx in (2, fw - 3):
            return LEATHER["stitch"] if along % 2 == 0 else SADDLE[2]
        return shade_pick(SADDLE, 0.35 - 0.12 * abs(along - (lo + hi) / 2), px, py)

    def side(face, xx, yy, fw, fh, px, py):
        along = xx if face == "west" else fw - 1 - xx
        if along in (d // 2 - 1, d // 2):
            return IRON[5] if yy == 2 else SADDLE[1]
        return None

    img = overpaint(img, placed, "shell", ("top",), top)
    return overpaint(img, placed, "shell", ("east", "west"), side)


# --- Dragonfly --------------------------------------------------------------------------------------------------------

DRAGONFLY = dict(dark=(0x0a, 0x30, 0x40), base=(0x1a, 0x8a, 0xa0), light=(0x6a, 0xe0, 0xe0), eye=(0x5a, 0xd8, 0x4a),
                 vein=(0xb8, 0xd8, 0xe8), spot=(0x2a, 0x3a, 0x4a), stripe=(0xd8, 0xf0, 0x6a))


def dragonfly_texel(name, kind, face, xx, yy, fw, fh, px, py):
    """Metallic teal with dark shoulder stripes, a ringed tail with pale spots, huge faceted green eyes, and lace wings:
    veins round clear panes (the panes are cut out, so you see through them)."""
    p = DRAGONFLY
    body = skin_ramp(p["dark"], p["base"], p["light"])
    along, down, across = frame(face, xx, yy, fw, fh)
    if kind == "wing":
        out = xx  # from the body out to the tip (top and bottom faces)
        if face not in ("top", "bottom"):
            return p["vein"]
        if out >= fw - 3 and along == 0:
            return p["spot"]  # the dark mark near each wing tip
        if along == 0:
            return tone(p["vein"], -0.2)  # the stiff leading edge
        if along == fh - 1 or out % 3 == 0 or out == fw - 1:
            return p["vein"]
        return None  # a clear pane
    if kind == "eye":
        er = skin_ramp(tone(p["eye"], -0.6), p["eye"], tone(p["eye"], 0.6))
        if face == "top" and (xx, yy) == (0, 0):
            return (0xf0, 0xff, 0xe8)
        return er[4] if (px + py) % 2 == 0 else er[2]  # facets
    if kind == "tail":
        if face in ("north", "south"):
            return body[1]
        seg = along % 3 if along is not None else 0
        if seg == 2:
            return body[0]  # the joint between segments
        if face == "top" and seg == 0:
            return tone(p["light"], 0.4)  # a pale spot on each segment
        return shade_pick(body, 0.3 if face == "top" else -0.1 if face != "bottom" else -0.5, px, py)
    # thorax and head: iridescent, with dark diagonal stripes on the sides
    if face in ("east", "west") and name == "thorax" and (along + (down or 0)) % 3 == 0:
        return body[0]
    if face == "top" and name == "thorax" and across in (0, fw - 1):
        return p["stripe"]
    level = 0.35 if face == "top" else -0.4 if face == "bottom" else side_level(down or 0, fh, 0.3, -0.3)
    c = shade_pick(body, level, px, py)
    if h2(px, py, 19) > 0.85:
        c = tone(c, 0.35)  # metallic shimmer
    return c


def dragonfly_saddle(img, placed):
    def top(face, xx, yy, fw, fh, px, py):
        if yy in (0, fh - 1):
            return SADDLE[1]
        return LEATHER["stitch"] if xx in (0, fw - 1) and yy % 2 else shade_pick(SADDLE, 0.3, px, py)

    def side(face, xx, yy, fw, fh, px, py):
        return (IRON[5] if yy == 1 else SADDLE[1]) if xx == fw // 2 else None

    img = overpaint(img, placed, "thorax", ("top",), top)
    return overpaint(img, placed, "thorax", ("east", "west"), side)


# --- Grasshopper ------------------------------------------------------------------------------------------------------

HOPPER = dict(base=(0x5a, 0xa0, 0x3a), dark=(0x2a, 0x56, 0x1c), light=(0xa4, 0xd4, 0x5c), belly=(0xc8, 0xd8, 0x7a),
              wing=(0x7a, 0x8e, 0x48), vein=(0x4a, 0x5a, 0x2a), eye=(0x4a, 0x34, 0x1e), shin=(0xc0, 0x4a, 0x3a))


def grasshopper_texel(name, kind, face, xx, yy, fw, fh, px, py):
    """Leaf green with a pale stripe down its back, a saddle-shaped shield, a ringed tail, leathery spotted wings, big
    brown eyes, herringbone thighs, red spiny shins."""
    p = HOPPER
    g = skin_ramp(p["dark"], p["base"], p["light"])
    along, down, across = frame(face, xx, yy, fw, fh)
    if kind == "wing":
        wr = skin_ramp(tone(p["wing"], -0.5), p["wing"], tone(p["wing"], 0.4))
        if face in ("top", "bottom"):
            if (across + along) % 4 == 0 or along % 4 == 0:
                return wr[1]  # the veins
            if h2(px // 2, py // 2, 23) > 0.75:
                return tone(p["eye"], 0.1)  # dark spots on the wings
            return shade_pick(wr, 0.25 - 0.4 * along / max(1, fh - 1) - (0.4 if face == "bottom" else 0), px, py)
        return wr[1]
    if kind == "eye":
        er = skin_ramp(tone(p["eye"], -0.5), p["eye"], tone(p["eye"], 0.6))
        return (0xe8, 0xf0, 0xd8) if (xx, yy) == (0, 0) else er[4] if (xx + yy) % 2 else er[2]
    if kind == "antenna":
        return g[5] if along is not None and along == 0 else g[1]
    if kind == "thigh":
        if face == "bottom":
            return (0xc0, 0x4a, 0x3a) if xx % 2 == 0 else g[0]  # the spiny underside
        if face in ("east", "west"):
            chevron = (along + abs(down - fh // 2)) % 3 == 0
            return g[5] if chevron else shade_pick(g, side_level(down, fh, 0.3, -0.2), px, py)  # herringbone
        return shade_pick(g, 0.2 if face == "top" else -0.2, px, py)
    if kind == "leg":
        if name == "shin":
            sr = skin_ramp(tone(p["shin"], -0.6), p["shin"], tone(p["shin"], 0.5))
            if down is not None and down % 2 == 1 and face in ("south", "east", "west"):
                return sr[0]  # spines
            return sr[4] if down == 0 else sr[3]
        return g[0] if down == fh - 1 else g[4] if down == 0 else g[3]
    if face == "bottom":
        return shade_pick(skin_ramp(tone(p["belly"], -0.5), p["belly"], tone(p["belly"], 0.4)), 0.1 if (along or 0) % 2 else -0.3, px, py)
    if kind == "abdomen":
        seg = along % 2 if along is not None else 0
        if face == "top":
            return g[1] if seg == 1 else (g[6] if abs(across - (fw - 1) / 2) < 1 else g[4])
        if face in ("east", "west"):
            if down == fh - 1:
                return mix(g[3], p["belly"], 0.6)
            return g[1] if seg == 1 else shade_pick(g, side_level(down, fh, 0.3, -0.2), px, py)
        return g[2]
    if kind == "head":
        if face == "north":
            if down >= fh - 2:
                return g[0] if across in (fw // 2 - 1, fw // 2) else p["belly"]  # mouthparts
            return shade_pick(g, 0.2 - 0.1 * down, px, py)
        return shade_pick(g, 0.35 if face == "top" else side_level(down or 0, fh, 0.3, -0.25), px, py)
    # thorax: a saddle-shaped shield with a pale keel and dark edges
    if face == "top":
        return g[6] if abs(across - (fw - 1) / 2) < 1 else g[4]
    if face in ("east", "west"):
        if down == 0:
            return g[5]
        if down == fh - 1:
            return g[1]
        return shade_pick(g, side_level(down, fh, 0.3, -0.25), px, py)
    return g[3]


def grasshopper_saddle(img, placed):
    def top(face, xx, yy, fw, fh, px, py):
        if yy in (0, fh - 1):
            return SADDLE[1]
        if xx in (0, fw - 1):
            return SADDLE[2]
        return SADDLE[5] if yy == fh - 2 else shade_pick(SADDLE, 0.3, px, py)

    def side(face, xx, yy, fw, fh, px, py):
        return (IRON[5] if yy == 1 else SADDLE[1]) if xx == fw // 2 else None

    img = overpaint(img, placed, "thorax", ("top",), top)
    return overpaint(img, placed, "thorax", ("east", "west"), side)


# --- Firefly ----------------------------------------------------------------------------------------------------------

FLY = dict(body=(0x2a, 0x22, 0x1e), shield=(0xe8, 0x80, 0x4a), spot=(0x1a, 0x12, 0x10), wing=(0x3a, 0x30, 0x2a),
           edge=(0xe0, 0xc0, 0x70), lantern=(0xd8, 0xf0, 0x70), bright=(0xf6, 0xff, 0xb8))


def firefly_texel(name, kind, face, xx, yy, fw, fh, px, py):
    """A dark little beetle: a rosy shield with a black spot over its head, dusky wing cases edged pale yellow, and a
    fat lantern of a tail in glowing rings."""
    p = FLY
    along, down, across = frame(face, xx, yy, fw, fh)
    if kind == "wing":
        if xx in (0, fw - 1):
            return p["edge"]
        return tone(p["wing"], 0.25 if (xx + yy) % 3 == 0 else 0)
    if kind == "lantern":
        lr = [tone(p["lantern"], -0.3), p["lantern"], tone(p["lantern"], 0.3), p["bright"]]
        if face == "top" and (along or 0) < 1:
            return tone(p["body"], 0.2)
        ring = (along if along is not None else down or 0) % 2
        return lr[3] if ring == 0 and face != "top" else lr[1] if ring else lr[2]
    if kind == "head":
        return (0x3a, 0x30, 0x2a) if face != "north" else (0x5a, 0x4a, 0x3a)
    if face == "top":
        if across == fw // 2 and along is not None and along >= 1:
            return p["spot"]  # the black spot on its shield
        return tone(p["shield"], 0.25 if along == 0 else 0)
    return tone(p["body"], 0.15 if down == 0 else -0.1)


def firefly_glow(name, kind, face, xx, yy, fw, fh, px, py):
    return firefly_texel(name, kind, face, xx, yy, fw, fh, px, py) if kind == "lantern" else None


# --- Firefly Jar ------------------------------------------------------------------------------------------------------
# The jar's 16x16 texture: glass sides (clear but for the rim, a tint at the edges and a glint), the base, a cork lid,
# and the fireflies inside (glowing dots on clear). Clear pixels cut out, so you see the fireflies through the glass.

def jar_texture():
    glass, glass_dim, glint = (0xd0, 0xec, 0xf0, 255), (0xa8, 0xc8, 0xd0, 255), (0xff, 0xff, 0xff, 255)
    cork = skin_ramp((0x5a, 0x3a, 0x20), (0xa8, 0x78, 0x48), (0xd8, 0xb0, 0x80))
    img = blank(16, 16)
    for y in range(8):  # side: 6 x 8 at (0, 0)
        for x in range(6):
            if y == 0:
                img[y][x] = glass  # the rim
            elif y == 7 or x in (0, 5):
                img[y][x] = glass_dim
    img[2][1], img[3][1], img[4][1], img[2][2] = glint, glint, (0xe8, 0xf8, 0xfc, 255), (0xe8, 0xf8, 0xfc, 255)
    for y in range(6):  # base: 6 x 6 at (6, 0)
        for x in range(6, 12):
            img[y][x] = glass if (x + y) % 3 else glass_dim
    for y in range(9, 16):  # lid top: 7 x 7 at (0, 9)
        for x in range(7):
            edge = x in (0, 6) or y in (9, 15)
            ring = abs(x - 3) + abs(y - 12)
            img[y][x] = (*(cork[1] if edge else cork[5] if ring == 0 else cork[4] if ring < 2 else cork[3] if (x + y) % 3 else cork[2]), 255)
    for y in range(9, 11):  # lid side: 7 x 2 at (7, 9)
        for x in range(7, 14):
            img[y][x] = (*(cork[1] if y == 10 else cork[4] if x % 2 else cork[3]), 255)
    bright, glow = (0xf8, 0xff, 0xc0, 255), (0xd0, 0xf0, 0x68, 255)
    for x, y in ((12, 1), (14, 0), (15, 3), (13, 3), (12, 4), (14, 2), (15, 1)):  # the fireflies: 4 x 5 at (12, 0)
        img[y][x] = bright if (x + y) % 2 else glow
    return img


# --- Airship ----------------------------------------------------------------------------------------------------------

WOOD = skin_ramp((0x4a, 0x2e, 0x16), (0x9a, 0x6a, 0x3a), (0xd0, 0x9a, 0x5c))
DARKWOOD = skin_ramp((0x2a, 0x18, 0x0a), (0x5a, 0x3a, 0x20), (0x8a, 0x60, 0x38))
METAL = skin_ramp((0x2a, 0x2c, 0x32), (0x6a, 0x6e, 0x74), (0xb8, 0xbc, 0xc4))
GUNMETAL = skin_ramp((0x10, 0x10, 0x14), (0x2e, 0x30, 0x34), (0x6a, 0x6e, 0x78))
ROPE = skin_ramp((0x6a, 0x50, 0x2a), (0xc8, 0xa8, 0x70), (0xf0, 0xdc, 0xa8))


def airship_painter(stripes):
    """Envelope: puffy striped gores with stitched seams, lit from above, and a rope net round its lower half. Hull:
    planks with grain, seams and nails. Iron: riveted plates; the engine's firebox glows behind a grate."""
    ramps = [skin_ramp(tone(c, -0.65), c, tone(c, 0.45)) for c in stripes]

    def texel(name, kind, face, xx, yy, fw, fh, px, py):
        along, down, across = frame(face, xx, yy, fw, fh)
        if kind == "cloth":
            if face in ("east", "west"):
                a, level = along, 0.32 - 0.8 * down / max(1, fh - 1)
            elif face in ("north", "south"):
                a, level = across, 0.25 - 0.7 * down / max(1, fh - 1)
            elif face == "top":
                a, level = across, 0.42 + 0.12 * (1 - abs(across - (fw - 1) / 2) / ((fw - 1) / 2))
            else:
                a, level = across, -0.62
            k = a % 6
            level += -0.28 if k == 0 else 0.12 if k in (2, 3) else 0
            c = shade_pick(ramps[(a // 6) % 2], level, px, py)
            if face != "top" and face != "bottom" and down is not None:
                if down == fh - 6:
                    return shade_pick(ROPE, 0.1, px, py)  # the load band
                if down > fh - 6 and ((a + down) % 5 == 0 or (a - down) % 5 == 0):
                    return shade_pick(ROPE, 0.25 - 0.4 * (down - (fh - 6)) / 6, px, py)  # the net
            return c
        if kind in ("wood", "darkwood"):
            r = WOOD if kind == "wood" else DARKWOOD
            if face in ("top", "bottom"):
                plank, pos = across // 3, along
                seam = across % 3 == 2
            else:
                plank, pos = (down or 0) // 2, (along if along is not None else across)
                seam = (down or 0) % 2 == 1 and (down or 0) < fh - 1
            grain = (fbm(pos * 0.35, plank * 7.0, 41 + len(name), 2.5) - 0.5) * 0.5
            level = (0.35 if face == "top" else -0.45 if face == "bottom" else side_level(down or 0, fh, 0.25, -0.25)) + grain
            if seam:
                level -= 0.45
            if not seam and pos % 12 == 1 and (face in ("top",) or (down or 0) % 2 == 0):
                return r[0]  # a nail
            if name == "side" and face == "top":
                return r[5] if along % 2 else r[4]  # the polished gunwale
            return shade_pick(r, level, px, py)
        if kind == "rope":
            return ROPE[4] if (px + py) % 3 == 0 else ROPE[2] if (px + py) % 3 == 1 else ROPE[3]
        if kind == "gunmetal":
            r = GUNMETAL
            if face in ("east", "west", "top", "bottom") and along is not None and along % 4 == 0:
                return r[4]  # bands round the barrel
            return shade_pick(r, 0.3 if face == "top" else -0.2, px, py)
        # iron
        r = METAL
        if name == "engine" and face == "north":
            if 2 <= yy <= 4 and 2 <= xx <= fw - 3:
                return (0x3a, 0x2a, 0x22) if xx % 2 == 0 else ((0xff, 0xc0, 0x50) if yy == 3 else (0xff, 0x7a, 0x20))  # grate and fire
        if name == "chimney" and face in ("top",):
            return (0x14, 0x12, 0x12)
        if name == "chimney" and down is not None and down < 2:
            return (0x22, 0x20, 0x20)  # soot round the top
        level = 0.3 if face == "top" else -0.4 if face == "bottom" else side_level(down or 0, fh, 0.2, -0.3)
        a = along if along is not None else (across or 0)
        if (down or 0) % 3 == 1 and a % 3 == 1:
            return r[5]  # rivets
        return shade_pick(r, level, px, py)
    return texel


# --- Pixel Miner ------------------------------------------------------------------------------------------------------

MINER = {"skin": (0xe8, 0xb8, 0x90), "beard": (0xd0, 0xcc, 0xc4), "shirt": (0x3a, 0x5a, 0x9a), "pants": (0x5a, 0x40, 0x2a),
         "helmet": (0xf0, 0xc0, 0x30), "lamp": (0xff, 0xff, 0xb0), "wood": (0x8a, 0x5e, 0x34), "iron": (0xb0, 0xb4, 0xb8),
         "boot": (0x2a, 0x20, 0x1a), "strap": (0x6a, 0x46, 0x24)}


def miner_texel(name, kind, face, xx, yy, fw, fh, px, py):
    """A tiny bearded miner: eyes and a nose under the helmet's brim, a big grey beard, a blue shirt with braces, a belt,
    brown trousers and boots, a shining yellow helmet with its lamp, and a pick with an iron head."""
    m = MINER
    along, down, across = frame(face, xx, yy, fw, fh)
    r = lambda c: skin_ramp(tone(c, -0.6), c, tone(c, 0.45))
    if kind == "face":
        sk, bd = r(m["skin"]), r(m["beard"])
        if face == "north":
            if down == 2:
                return (0x1e, 0x16, 0x12) if across in (1, fw - 2) else sk[4]  # the eyes, just under the brim
            if down == 3:
                return sk[1] if across == fw // 2 else bd[2] if across in (1, fw - 2) else bd[4]  # nose and moustache
            if down == 4:
                return bd[3] if across % 2 else bd[4]
            return sk[3]
        if face in ("east", "west"):
            if down >= 3 and along <= 2:
                return bd[3] if (along + down) % 2 else bd[4]  # the beard round the jaw
            if down == 2 and along == 3:
                return sk[1]  # an ear
            return sk[2 if face == "west" else 3]
        if face == "south":
            return (0x8a, 0x8a, 0x84) if down >= 2 else sk[2]  # hair at the back
        return sk[3]
    if kind == "helmet":
        hr = r(m["helmet"])
        if face == "top":
            return hr[6] if (across + along) % 5 == 0 else hr[5]
        if down == fh - 1:
            return hr[2]  # the brim's edge
        if face == "north" and down == 0:
            return hr[6]
        return shade_pick(hr, 0.35 - 0.4 * (down or 0) / max(1, fh - 1), px, py)
    if kind == "lamp":
        return m["lamp"] if face == "north" else (0xff, 0xe0, 0x70)
    if kind == "shirt":
        sh = r(m["shirt"])
        if name == "arm":
            if down is not None and down >= fh - 1:
                return r(m["skin"])[3]  # hands
            return shade_pick(sh, side_level(down or 0, fh, 0.25, -0.3), px, py)
        if face in ("north", "south") and across in (0, fw - 1):
            return m["strap"]  # braces
        if down == fh - 1 and face != "top":
            return (0x2a, 0x1a, 0x10) if across != fw // 2 and along != 1 else (0xd8, 0xb0, 0x40)  # belt and buckle
        if face == "top":
            return m["strap"] if across in (0, fw - 1) else sh[4]
        return shade_pick(sh, side_level(down or 0, fh, 0.25, -0.25), px, py)
    if kind == "pants":
        if down is not None and down >= fh - 1 and face != "top":
            return r(m["boot"])[4] if face == "north" else r(m["boot"])[3]
        if face == "bottom":
            return r(m["boot"])[1]
        return shade_pick(r(m["pants"]), side_level(down or 0, fh, 0.15, -0.35), px, py)
    if kind == "wood":
        return r(m["wood"])[4] if (px + py) % 3 == 0 else r(m["wood"])[3]
    ir = r(m["iron"])
    return ir[5] if (down == 0 or face == "top") else ir[3] if (down or 0) % 2 else ir[2]
