"""Dragon skins, tack, armor and eggs, painted with texkit (the 1.4.0+ texture pass).

Where a texel lands on the model follows GeckoLib's box UVs: on a cube that isn't mirrored, the "west" face is the
creature's left flank with its front (-Z) end at x = 0, the "east" face is the right flank with its front end at the far
side, and the top and bottom faces have the back (+Z) end on their first row. Mirrored twins (the right legs and wing)
come out as mirror images, so everything is painted for the left side.
"""
import math
from types import SimpleNamespace

import dragon_kinds_art as kinds
from texkit import (bayer, blank, egg_mask, fbm, h2, honeycomb, mix, outline, scale_cell, scale_gap, scale_light,
                    sphere_light, tone, voronoi)

TOOTH = (0xf6, 0xf0, 0xdc)
MAGMA = [(0x7a, 0x14, 0x08), (0xd8, 0x46, 0x0e), (0xff, 0x8e, 0x1e), (0xff, 0xd0, 0x48), (0xff, 0xf4, 0xa8)]
RAINBOW = [(0xff, 0x40, 0x40), (0xff, 0x9a, 0x30), (0xff, 0xe6, 0x40), (0x50, 0xd0, 0x50), (0x40, 0xa8, 0xff),
           (0x58, 0x58, 0xe8), (0xb0, 0x58, 0xe8)]
GLYPHS = [("#.#", ".#.", "#.#"), ("##.", "#.#", ".##"), (".#.", "###", ".#."), ("#..", "###", "..#"), ("###", "#..", "##.")]
LENGTHWISE = ("body", "neck", "tail1", "tail2", "tail3", "snout", "skull", "jaw")


def box_faces(u, v, size):
    w, h, d = (int(round(s)) for s in size)
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


def frame(face, xx, yy, fw, fh):
    """(along, down, across): texels back from the cube's front end (flanks, top, bottom), texels down from its top
    (side faces), texels in from the creature's right side (top, bottom, front and back faces)."""
    if face == "west":
        return xx, yy, None
    if face == "east":
        return fw - 1 - xx, yy, None
    if face in ("north", "south"):
        return None, yy, xx
    return fh - 1 - yy, None, xx


def skin_ramp(dark, base, light):
    """Seven tones, index 3 the base colour; the palette's own dark and light sit at 1 and 5."""
    return [tone(dark, -0.45), dark, mix(dark, base, 0.55), base, mix(base, light, 0.5), light, tone(light, 0.45)]


def soft_ramp(c, spread=1.0):
    return [tone(c, -0.75 * spread), tone(c, -0.5 * spread), tone(c, -0.25 * spread), c, tone(c, 0.25 * spread),
            tone(c, 0.5 * spread), tone(c, 0.75 * spread)]


def shade_pick(r, level, x, y, dither=0.45):
    """Colour for a light level (-1..1) from a seven-tone ramp, half-dithered so gradients don't band."""
    value = 3 + level * 3 + (bayer(x, y) - 0.5) * dither
    return r[max(0, min(6, int(math.floor(value + 0.5))))]


# --- The wing's sheet of membrane ---------------------------------------------------------------------------------

WING_SEGMENT, WING_DEPTH = 20, 16  # each half's length (shoulder to wrist, wrist to point), and the membrane's depth
WRIST = (20.0, 0.0)
FINGER_TIPS = ((20.5, 16.0), (28.5, 14.5), (35.0, 9.5))  # where the fingers reach the trailing edge
WING_POINT = (40.0, 1.0)
SCALLOPS = (4.2, 2.6, 2.4, 1.8)  # how deep the edge is cut between the body, each finger and the point


def wing_sheet():
    """(g, t) -> what each texel of the spread membrane is: a finger bone or its nail, the lit side of a bone, the
    rim, or plain skin. Texels that are cut away aren't in it. The bones all fan out from the wrist."""
    edge = [(0.0, float(WING_DEPTH)), *FINGER_TIPS, WING_POINT]
    shape = [(WING_POINT[0], 0.0), (0.0, 0.0)]
    for (ax, ay), (bx, by), depth in zip(edge, edge[1:], SCALLOPS):
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy)
        for i in range(17):
            k = i / 16
            sag = depth * 4 * k * (1 - k)
            shape.append((ax + dx * k + dy / length * sag, ay + dy * k - dx / length * sag))

    def inside(x, y):
        hit = False
        for (ax, ay), (bx, by) in zip(shape, shape[1:] + shape[:1]):
            if (ay > y) != (by > y) and x < ax + (y - ay) * (bx - ax) / (by - ay):
                hit = not hit
        return hit

    sheet = {}
    for g in range(2 * WING_SEGMENT):
        for t in range(WING_DEPTH):
            x, y = g + 0.5, t + 0.5
            part = None
            for fx, fy in FINGER_TIPS:
                dx, dy = fx - WRIST[0], fy - WRIST[1]
                length = math.hypot(dx, dy)
                k = ((x - WRIST[0]) * dx + (y - WRIST[1]) * dy) / (length * length)
                if not 0.06 <= k <= 1:
                    continue
                side = ((x - WRIST[0]) * dy - (y - WRIST[1]) * dx) / length
                if abs(side) <= 0.5:
                    part = "nail" if k > 0.91 else "bone"
                    break
                if 0.5 < side < 1.4:
                    part = "lit"
            if part in ("bone", "nail") or inside(x, y):
                sheet[g, t] = part or "skin"
    for (g, t), part in list(sheet.items()):
        if part in ("bone", "nail"):
            continue
        around = ((g + 1, t), (g, t + 1), (g - 1, t))
        if any(n not in sheet for n in around if n[0] >= 0):
            sheet[g, t] = "rim"
    return sheet


WING_SHEET = wing_sheet()


# --- Skins ------------------------------------------------------------------------------------------------------------

class Skin:
    def __init__(self, element, pal):
        self.element, self.pal = element, pal
        self.scales = skin_ramp(pal["dark"], pal["base"], pal["light"])
        belly = pal["belly"]
        self.belly = skin_ramp(tone(belly, -0.55), belly, tone(belly, 0.45))
        self.horn = soft_ramp(pal["horn"], 1.1)
        self.membrane = soft_ramp(pal["membrane"], 0.9)
        self.eye = pal["eye"]
        self.bands = [skin_ramp(tone(c, -0.6), c, tone(c, 0.5)) for c in RAINBOW]
        if "stripe" in pal:
            self.stripe = skin_ramp(tone(pal["stripe"], -0.3), pal["stripe"], tone(pal["stripe"], 0.5))
        self.seed = sum(ord(ch) for ch in element)

    # Scaled hide: the body, neck, tail, snout, skull, legs and wing arms.
    def scales_texel(self, name, face, xx, yy, fw, fh, px, py):
        e, pal = self.element, self.pal
        along, down, across = frame(face, xx, yy, fw, fh)
        if face == "bottom" and name in LENGTHWISE:
            return self.belly_texel(along, across, fw, px, py)
        big = name in ("body", "tail1")
        sw, sh = (4, 3) if big else (3, 3) if name.startswith("leg") else (4, 2) if name in ("neck", "tail2") else (3, 2)
        if face == "top":
            a, b = across, along
            level = 0.18
            mid = abs(across - (fw - 1) / 2)
            if name in ("body", "neck", "tail1", "tail2", "tail3") and mid < 1:
                level -= 0.12  # the spine's ridge
        elif face == "bottom":
            a, b = across, along
            level = -0.3
        else:
            a = along if along is not None else across
            b = down
            level = 0.24 - 0.52 * down / max(1, fh - 1)
            if down == 0:
                level += 0.12  # the edge catches the light
        cell, cx, cy = scale_cell(a, b, sw, sh)
        rim = scale_gap(cx, cy, sw, sh)
        soft = 0.5 if name.startswith("leg") else 0.75
        level += scale_light(cx, cy, sw, sh) * soft + (h2(cell[0], cell[1], self.seed + len(name)) - 0.5) * 0.22
        ramp_ = self.scales

        # Each element's own look.
        if e == "earth":
            d1, d2, ident = voronoi(a + 0.5, b + 0.5, self.seed + len(name), 4.2)
            level = (0.18 if face == "top" else 0.2 - 0.5 * (down or 0) / max(1, fh - 1)) + (h2(*ident, self.seed) - 0.5) * 0.5
            if d2 - d1 < 0.9:
                level = -0.85  # the cracks between rock plates
            elif d1 < 1.1:
                level += 0.25
            if h2(px, py, 77) > 0.93 and d2 - d1 >= 0.9:
                return tone(pal["fleck"], (h2(px, py, 78) - 0.5) * 0.6)
        elif e == "crystal" and (face == "top" or (down is not None and down < fh * 0.7)):
            d1, d2, ident = voronoi(a + 0.5, b + 0.5, self.seed + len(name), 3.6)
            level = 0.15 + (h2(*ident, self.seed) - 0.5) * 0.9
            if d2 - d1 < 0.7:
                level = 0.95  # bright facet edges
        elif e == "honey" and name in ("body", "neck", "tail1", "tail2", "tail3") and face in ("top", "east", "west"):
            band = along // 3
            fuzz = (along % 3 == 0) and bayer(px, py) > 0.5
            if (band % 2 == 1) != fuzz:
                ramp_ = self.stripe
        elif e == "rainbow":
            ramp_ = self.bands[((along if along is not None else (across or 0)) // 2) % 7]
        elif e == "elder" and rim and (face == "top" or (down is not None and down < fh * 0.6)) and cell[1] % 2 == 0:
            level = 0.55  # gilded scale edges

        c = shade_pick(ramp_, level, px, py)

        if e == "ember" and rim and fbm(px, py, self.seed, 5) > 0.47:
            heat = fbm(px, py, self.seed + 9, 3)
            c = MAGMA[min(4, 1 + int(heat * 4))]  # magma glowing between the scales
        elif e == "frost":
            if rim and face == "top" and fbm(px, py, self.seed, 4) > 0.45:
                c = tone((0xf4, 0xfa, 0xff), -0.05)  # rime on the scale edges
            elif h2(px, py, 5) > 0.985:
                c = (0xff, 0xff, 0xff)
        elif e == "verdant" and (face == "top" or (down is not None and down < fh * 0.45)):
            m = fbm(px, py, self.seed, 6)
            if m > 0.58:
                moss = (0x9a, 0xc8, 0x4a) if m > 0.66 else (0x6a, 0xa8, 0x3a)
                c = shade_pick(soft_ramp(moss, 0.6), level * 0.5, px, py)
            if face == "top" and h2(px, py, 6) > 0.975:
                c = [(0xff, 0xc8, 0xe8), (0xff, 0xff, 0xff), (0xff, 0xe0, 0x50)][int(h2(px, py, 7) * 3)]
        elif e == "tide" and face in ("east", "west"):
            if (down + round(1.4 * math.sin(along * 0.75))) % 5 == 0 and down < fh - 2:
                c = tone(pal["light"], 0.35)  # wave lines along the flanks
            elif h2(px, py, 8) > 0.98:
                c = (0xe8, 0xff, 0xff)
        elif e == "air":
            cloud = fbm(px, py, self.seed, 5)
            if cloud > 0.6:
                c = tone(c, 0.35)
            elif cloud < 0.36:
                c = mix(c, (0x9c, 0xc8, 0xec), 0.3)  # blue sky between the puffs
            if face in ("east", "west") and abs(math.sin(along * 0.45 + down * 0.9)) < 0.1 and 1 < down < fh - 2:
                c = (0xb8, 0xdc, 0xf6)  # wind swirls
        elif e == "ender" and h2(px, py, 9) > 0.972:
            c = tone(self.eye, (h2(px, py, 10) - 0.5) * 0.5)  # specks of starlight
        elif e == "shadow" and rim:
            c = mix(c, (0x3a, 0x22, 0x5a), 0.35)

        if e in kinds.PALETTES:  # the 2.0 dragons' own markings
            c = kinds.scales(self, c, SimpleNamespace(name=name, face=face, along=along, down=down, across=across, fw=fw, fh=fh,
                                                     px=px, py=py, a=a, b=b, rim=rim, level=level))

        # Countershading: the lower flanks fade into the belly colour.
        if down is not None and face in ("east", "west") and name in ("body", "neck", "tail1", "tail2", "tail3"):
            t = (down / max(1, fh - 1) - 0.72) / 0.28
            if t > 0:
                c = mix(c, shade_pick(self.belly, -0.1, px, py), min(0.6, t * 0.6))
        return c

    def belly_texel(self, along, across, fw, px, py):
        """Belly plates across the underside, a little rounded."""
        level = (0.32, 0.06, -0.42)[along % 3]
        edge = min(across, fw - 1 - across)
        level -= 0.32 if edge == 0 else 0.12 if edge == 1 else 0
        c = shade_pick(self.belly, level, px, py)
        if self.element == "ember" and along % 3 == 2 and h2(px, py, 3) > 0.6:
            c = MAGMA[2]
        elif self.element in kinds.PALETTES:
            c = kinds.belly(self, c, along, across, fw, px, py)
        return c

    def chest_texel(self, xx, yy, fw, fh, px, py):
        """The chest below the neck: belly plates, from the body's front face."""
        if yy < fh * 0.45:
            return None
        return self.belly_texel(yy, xx, fw, px, py)

    def horn_texel(self, face, xx, yy, fw, fh, px, py):
        if face == "top":
            c = self.horn[6]
        elif face == "bottom":
            c = self.horn[1]
        else:
            t = yy / max(1, fh - 1)
            level = 0.7 - 1.3 * t - (0.18 if yy % 2 else 0) + (0.12 if xx == 0 else 0)
            c = shade_pick(self.horn, level, px, py)
        if self.element == "ember" and (face == "top" or yy == 0):
            c = MAGMA[2]  # smouldering tips
        elif self.element == "storm" and (face == "top" or yy == 0):
            c = (0xff, 0xff, 0xc8)
        elif self.element in kinds.PALETTES:
            c = kinds.horn(self, c, face, yy, fh)
        return c

    def eye_texel(self, along, down):
        """Eyes near the front of the skull: a brow ridge, a glint and a slit pupil."""
        eye = self.eye
        if down == 1 and along <= 4:
            return "brow"
        if down in (2, 3) and 1 <= along <= 3:
            if along == 2:
                return tone(eye, -1.0) if down == 2 else tone(eye, -1.1)
            if down == 2:
                return tone(eye, 0.55) if along == 1 else eye
            return tone(eye, -0.3)
        return None

    def head_texel(self, name, face, xx, yy, fw, fh, px, py):
        along, down, across = frame(face, xx, yy, fw, fh)
        if face in ("east", "west"):
            got = self.eye_texel(along, down)
            if got == "brow":
                return self.scales[0]
            if got is not None:
                return got
        if face == "north" and down <= 1:
            return self.scales[1] if down == 1 else self.scales[2]  # the brow
        return self.scales_texel(name, face, xx, yy, fw, fh, px, py)

    def snout_texel(self, face, xx, yy, fw, fh, px, py):
        along, down, across = frame(face, xx, yy, fw, fh)
        if face == "north":
            if down == fh - 1:
                return TOOTH if across in (1, fw - 2) else self.scales[0]  # fangs and the mouth's edge
            if down == 1 and across in (1, fw - 2):
                return tone(self.scales[0], -0.4)  # nostrils
            if down == 2 and across in (1, fw - 2):
                return self.scales[1]
        if face in ("east", "west") and down == fh - 1:
            return (TOOTH if along % 2 == 0 else tone(TOOTH, -0.35)) if along < fw - 1 else self.scales[0]
        if face == "top" and along == 0 and across in (1, fw - 2):
            return self.scales[1]
        return self.scales_texel("snout", face, xx, yy, fw, fh, px, py)

    def jaw_texel(self, face, xx, yy, fw, fh, px, py):
        along, down, across = frame(face, xx, yy, fw, fh)
        if face in ("east", "west"):
            if down == 0:
                return TOOTH if along % 2 == 1 and along < fw - 2 else tone(self.belly[2], -0.3)
            return shade_pick(self.belly, 0.25 - 0.4 * down / max(1, fh - 1) - (0.3 if along % 3 == 2 else 0), px, py)
        if face == "north":
            if down == 0:
                return TOOTH if across in (0, fw - 1) else tone(self.belly[2], -0.3)
            return shade_pick(self.belly, 0.1 - 0.3 * down, px, py)
        if face == "top":
            return (0x8a, 0x2a, 0x2a) if 0 < across < fw - 1 else tone(self.belly[2], -0.3)  # inside the mouth
        if face == "south":
            return shade_pick(self.belly, -0.35, px, py)
        return self.belly_texel(along, across, fw, px, py)

    def leg_texel(self, name, face, xx, yy, fw, fh, px, py):
        along, down, across = frame(face, xx, yy, fw, fh)
        if face == "bottom":
            return self.scales[0]
        if face == "top":
            return self.scales[2]
        claws = (0, 2, 4) if fw == 5 else (0, 3)
        if face == "north" and down >= fh - 2:
            if across in claws:
                return self.horn[5] if down == fh - 1 else self.horn[3]
            return self.scales[0] if down == fh - 1 else self.scales[1]
        if down == fh - 1:
            return self.scales[0]
        c = self.scales_texel(name, face, xx, yy, fw, fh, px, py)
        if down < 3:
            c = tone(c, 0.08)  # the thigh catches the light
        return c

    def arm_texel(self, name, face, xx, yy, fw, fh, px, py):
        """The bones along the wing's leading edge: the arm out to the wrist, then the long finger to a clawed point."""
        if name == "wing_finger":
            out = {"top": xx, "bottom": xx, "north": xx, "south": fw - 1 - xx}.get(face)
            if face == "west" or (out is not None and out >= fw - 3):
                return self.horn[5] if yy == 0 else self.horn[3]
        c = self.scales_texel("wing_arm", face, xx, yy, fw, fh, px, py)
        return tone(c, -0.12)

    def membrane_texel(self, name, face, xx, yy, fw, fh):
        """Both halves of the membrane, painted as one sheet: g runs from the body out to the point, t from the
        leading edge back. Top and bottom match texel for texel, and the edges are open wherever the sheet is cut
        away, so the scallops and the point show from every side."""
        outer = name == "wing_tip_membrane"
        last, back = WING_SEGMENT - 1, WING_DEPTH - 1
        g, t = {
            "top": (xx, fh - 1 - yy), "bottom": (xx, fh - 1 - yy),
            "north": (xx, 0), "south": (last - xx, back),
            "west": (last, xx), "east": (0, back - xx),
        }[face]
        if outer:
            g += WING_SEGMENT
        part = WING_SHEET.get((g, t))
        if part is None:
            return None
        if face == "north":
            return self.scales[1]
        if face == "east" or (face == "west" and not outer):
            return self.membrane[2]  # where it meets the body, and where the halves meet at the wrist
        e, pal = self.element, self.pal
        if part in ("bone", "nail"):
            if part == "nail":
                return self.horn[5]
            return self.horn[2] if e not in ("frost", "crystal", "air", "rainbow") else tone(pal["membrane"], -0.45)
        level = -0.35 + 0.6 * t / back + (fbm(g, t, self.seed, 4) - 0.5) * 0.35
        if part == "lit":
            level += 0.18
        elif part == "rim":
            level -= 0.3
        elif abs(math.sin(g * 0.9 + t * 0.35) * 2.2 + math.sin(t * 0.6)) < 0.18 and t > 2:
            level -= 0.25  # veins
        c = shade_pick(self.membrane, level, g, t)
        if e == "honey" and honeycomb(g, t, 3):
            c = tone((0xe0, 0xa8, 0x30), -0.1)
        elif e == "tide" and g % 3 == 0:
            c = tone(c, -0.25)  # fin rays
        elif e == "air" and fbm(g, t, self.seed + 3, 4) > 0.6:
            c = mix(c, (0xff, 0xff, 0xff), 0.7)  # wisps of cloud
        elif e == "ender" and h2(g, t, 11) > 0.96:
            c = tone(self.eye, 0.2)
        elif e == "rainbow":
            c = mix(c, RAINBOW[((g + t) // 4) % 7], 0.32)
        elif e == "crystal":
            d1, d2, _ = voronoi(g + 0.5, t + 0.5, 4, 3.5)
            if d2 - d1 < 0.6:
                c = tone(c, 0.45)
        elif e == "storm" and h2(g, t, 12) > 0.975:
            c = (0xff, 0xf0, 0x70)
        elif e == "frost" and fbm(g, t, 21, 3) > 0.62:
            c = (0xf4, 0xfc, 0xff)
        elif e == "ember" and (part == "rim" or t >= back - 2) and h2(g, t, 13) > 0.5:
            c = MAGMA[2]
        elif e in kinds.PALETTES:
            c = kinds.membrane(self, c, g, t, part, level)
        return c


def _stamp_bolts(img, placed, skin):
    """Lightning zigzags down the Storm Dragon's flanks."""
    zig = (0, 1, 2, 1, 0, -1, 0, 1, 2, 1)
    yellow = (0xff, 0xf4, 0x90)
    for name, starts in (("body", (5, 15)), ("tail1", (5,)), ("neck", (4,))):
        u, v, size, _ = placed[name]
        f = box_faces(u, v, size)
        for face in ("east", "west"):
            fx, fy, fw, fh = f[face]
            for start in starts:
                for down in range(1, min(len(zig), fh - 2)):
                    along = start + zig[down]
                    xx = along if face == "west" else fw - 1 - along
                    if 0 <= xx < fw:
                        img[fy + down][fx + xx] = (*yellow, 255)
                        for dx in (-1, 1):
                            if 0 <= xx + dx < fw and img[fy + down][fx + xx + dx][:3] != yellow:
                                old = img[fy + down][fx + xx + dx]
                                img[fy + down][fx + xx + dx] = (*mix(old, yellow, 0.35), 255)
    return img


def _stamp_runes(img, placed, skin):
    """Faintly glowing runes on the Shadow Dragon's flanks."""
    glow = skin.eye
    k = 0
    for name, spots in (("body", (3, 9, 15)), ("tail1", (4,)), ("neck", (3,))):
        u, v, size, _ = placed[name]
        f = box_faces(u, v, size)
        for face in ("east", "west"):
            fx, fy, fw, fh = f[face]
            for along0 in spots:
                glyph = GLYPHS[k % len(GLYPHS)]
                k += 1
                for gy, row in enumerate(glyph):
                    for gx, ch in enumerate(row):
                        along, down = along0 + gx, 2 + gy
                        xx = along if face == "west" else fw - 1 - along
                        if ch == "#" and 0 <= xx < fw and down < fh:
                            img[fy + down][fx + xx] = (*tone(glow, -0.15), 255)
    return img


def paint_skin(element, pal, placed, tex):
    skin = Skin(element, pal)
    img = blank(tex, tex)
    for name, (u, v, size, kind) in placed.items():
        if kind == "bag":
            continue  # clear: the saddlebags only show once they're painted on
        for face, (fx, fy, fw, fh) in box_faces(u, v, size).items():
            for yy in range(fh):
                for xx in range(fw):
                    px, py = fx + xx, fy + yy
                    if kind == "membrane":
                        c = skin.membrane_texel(name, face, xx, yy, fw, fh)
                    elif kind == "horn":
                        c = skin.horn_texel(face, xx, yy, fw, fh, px, py)
                    elif kind == "head":
                        c = skin.head_texel(name, face, xx, yy, fw, fh, px, py)
                    elif kind == "belly":
                        c = skin.jaw_texel(face, xx, yy, fw, fh, px, py)
                    elif name == "snout":
                        c = skin.snout_texel(face, xx, yy, fw, fh, px, py)
                    elif name.startswith("leg"):
                        c = skin.leg_texel(name, face, xx, yy, fw, fh, px, py)
                    elif name in ("wing_arm", "wing_finger"):
                        c = skin.arm_texel(name, face, xx, yy, fw, fh, px, py)
                    elif name == "body" and face == "north":
                        c = skin.chest_texel(xx, yy, fw, fh, px, py) or skin.scales_texel(name, face, xx, yy, fw, fh, px, py)
                    else:
                        c = skin.scales_texel(name, face, xx, yy, fw, fh, px, py)
                    if c is not None:
                        img[py][px] = (*c[:3], 255)
    if element == "storm":
        img = _stamp_bolts(img, placed, skin)
    elif element == "shadow":
        img = _stamp_runes(img, placed, skin)
    elif element in kinds.PALETTES:
        img = kinds.stamps(img, placed, skin, box_faces)
    return img


# --- Tack -------------------------------------------------------------------------------------------------------------

LEATHER = dict(base=(0x7a, 0x4a, 0x26), light=(0x96, 0x60, 0x30), dark=(0x4a, 0x2c, 0x16),
               stitch=(0xe0, 0xc4, 0x88), iron=(0xb8, 0xb8, 0xc0))
SADDLE = skin_ramp((0x3e, 0x22, 0x10), (0x7a, 0x4a, 0x26), (0xa8, 0x70, 0x3c))
BLANKET = skin_ramp((0x5a, 0x0c, 0x18), (0xa8, 0x1c, 0x2c), (0xe0, 0x50, 0x50))
GOLD = skin_ramp((0x7a, 0x50, 0x08), (0xe0, 0xae, 0x30), (0xff, 0xe8, 0x80))
IRON = skin_ramp((0x4a, 0x4c, 0x54), (0x9a, 0x9c, 0xa6), (0xe6, 0xe8, 0xee))


def paint_saddle(img, placed):
    """A saddle on the middle of the back: a red blanket with gold trim under a stitched leather seat with a raised
    pommel (front) and cantle (back), skirts and a girth strap with an iron buckle down both flanks, the strap under
    the belly."""
    img = [row[:] for row in img]
    u, v, size, _ = placed["body"]
    f = box_faces(u, v, size)
    w, h, d = (int(round(n)) for n in size)
    lo, hi = d // 2 - 4, d // 2 + 3  # saddle rows along the body (from the front)

    def put(face, xx, yy, c):
        fx, fy, _, _ = f[face]
        img[fy + yy][fx + xx] = (*c[:3], 255)

    fx, fy, fw, fh = f["top"]
    for along in range(lo - 1, hi + 2):
        yy = fh - 1 - along
        for x in range(w):
            if along in (lo - 1, hi + 1):
                c = GOLD[4] if x % 2 == 0 else BLANKET[2]  # the blanket's fringe, front and back
            elif x in (0, w - 1):
                c = BLANKET[3]
            else:
                edge = min(x - 1, w - 2 - x)
                if along in (lo, lo + 1):  # the pommel
                    c = SADDLE[5] if along == lo else SADDLE[2]
                elif along in (hi, hi - 1):  # the cantle
                    c = SADDLE[1] if along == hi else SADDLE[5]
                elif edge == 0:
                    c = SADDLE[1]
                elif edge == 1:
                    c = LEATHER["stitch"] if along % 2 == 0 else SADDLE[2]
                else:
                    c = shade_pick(SADDLE, 0.45 - 0.5 * abs(along - (lo + hi) / 2) / 3 - 0.05 * edge, x, yy)
            put("top", x, yy, c)
    for side in ("east", "west"):
        fx, fy, fw, fh = f[side]
        for along in range(lo - 1, hi + 2):
            xx = along if side == "west" else fw - 1 - along
            for down in range(6):
                if down < 4:
                    if along in (lo - 1, hi + 1):
                        c = BLANKET[3] if down < 3 else GOLD[3]
                    elif along in (lo, hi):
                        c = SADDLE[1]
                    elif down == 3:
                        c = SADDLE[0]
                    elif down == 1 and along % 2 == 0:
                        c = LEATHER["stitch"]
                    else:
                        c = shade_pick(SADDLE, 0.2 - 0.25 * down, xx, down)
                elif down == 4:
                    c = BLANKET[4] if (along - lo) % 2 == 0 else BLANKET[3]
                else:
                    c = GOLD[4] if (along - lo) % 2 == 0 else GOLD[2]  # the blanket's gold trim
                put(side, xx, down, c)
        for along in range(d // 2 - 2, d // 2 + 2):  # girth strap and its buckle
            xx = along if side == "west" else fw - 1 - along
            for down in range(6, h):
                edge = along in (d // 2 - 2, d // 2 + 1)
                c = SADDLE[1] if edge else SADDLE[2]
                if down in (7, 8):
                    c = IRON[5] if (down == 7 and not edge) else IRON[2] if down == 8 else IRON[3]
                put(side, xx, down, c)
    fx, fy, fw, fh = f["bottom"]
    for along in range(d // 2 - 2, d // 2 + 2):
        for x in range(w):
            put("bottom", x, fh - 1 - along, SADDLE[1] if along in (d // 2 - 2, d // 2 + 1) else SADDLE[2])
    return img


def paint_bags(img, placed):
    """Saddlebags: stitched leather bags with a buckled flap over the top."""
    img = [row[:] for row in img]
    u, v, size, _ = placed["saddlebag"]
    for face, (fx, fy, fw, fh) in box_faces(u, v, size).items():
        for yy in range(fh):
            for xx in range(fw):
                if face in ("top", "bottom"):
                    rim = xx in (0, fw - 1) or yy in (0, fh - 1)
                    c = SADDLE[1] if rim else shade_pick(SADDLE, 0.35 if face == "top" else -0.3, fx + xx, fy + yy)
                elif yy == 0:
                    c = SADDLE[5]
                elif yy == 3:
                    c = SADDLE[0]  # under the flap
                elif yy < 3:
                    mid = fw // 2
                    if xx in (mid - 1, mid) and yy == 2:
                        c = IRON[5] if xx == mid - 1 else IRON[2]  # the buckle
                    elif xx in (mid - 1, mid):
                        c = SADDLE[1]  # the strap
                    else:
                        c = SADDLE[4] if yy == 1 else SADDLE[3]
                elif xx in (0, fw - 1):
                    c = SADDLE[1]
                elif yy == fh - 2 and xx % 2 == 1:
                    c = LEATHER["stitch"]
                else:
                    c = shade_pick(SADDLE, 0.1 - 0.35 * (yy - 3) / max(1, fh - 4), fx + xx, fy + yy)
                img[fy + yy][fx + xx] = (*c[:3], 255)
    return img


# --- Armor ------------------------------------------------------------------------------------------------------------

METALS = {
    "iron": dict(light=(0xe6, 0xe6, 0xea), mid=(0xb2, 0xb4, 0xba), dark=(0x70, 0x72, 0x7a), rivet=(0x50, 0x52, 0x58),
                 trim=(0x5a, 0x5c, 0x66)),
    "golden": dict(light=(0xff, 0xf4, 0x8a), mid=(0xf0, 0xc4, 0x38), dark=(0xb0, 0x7c, 0x14), rivet=(0x7a, 0x50, 0x08),
                   trim=(0xc8, 0x8a, 0x10)),
    "diamond": dict(light=(0xd0, 0xff, 0xfa), mid=(0x5c, 0xe0, 0xd8), dark=(0x22, 0x9a, 0x96), rivet=(0x14, 0x5e, 0x5c),
                    trim=(0x1a, 0x7a, 0x78)),
    "netherite": dict(light=(0x7a, 0x70, 0x76), mid=(0x4c, 0x44, 0x4a), dark=(0x30, 0x2a, 0x2e), rivet=(0xd8, 0xa8, 0x5a),
                      trim=(0xc8, 0x98, 0x48)),
    "elder": dict(light=(0xff, 0xf4, 0xd8), mid=(0xd8, 0xb8, 0x70), dark=(0x8a, 0x64, 0x2a), rivet=(0xc8, 0x20, 0x30),
                  trim=(0xe8, 0xb8, 0x40)),
}

# Which faces of which cubes get plated, and how many rows in from the top edge (None = the whole face).
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
    """Lamellar barding: rows of overlapping plates, each with a bright upper lip, a polished face with a glint and a
    shadowed lower edge, riveted and split into segments, with a trimmed hem. The eyes, belly, horns and wings stay
    bare."""
    img = [row[:] for row in img]
    m = METALS[metal]
    r = [tone(m["dark"], -0.4), m["dark"], mix(m["dark"], m["mid"], 0.5), m["mid"], mix(m["mid"], m["light"], 0.5),
         m["light"], tone(m["light"], 0.4)]
    for name, plated in ARMOR_PLATES.items():
        u, v, size, _ = placed[name]
        f = box_faces(u, v, size)
        for face, rows in plated.items():
            fx, fy, fw, fh = f[face]
            for yy in range(fh):
                for xx in range(fw):
                    along, down, across = frame(face, xx, yy, fw, fh)
                    if face == "top":
                        b, a, limit = along, across, None
                    else:
                        b, a, limit = down, (along if along is not None else across), rows
                    if limit is not None and b >= min(limit, fh):
                        continue
                    if face != "top" and b == 0 and (a == 0 or a == (fw - 1)):
                        continue  # rounded corners
                    band, seg = b % 4, (a + (b // 4) * 3) % 6
                    level = (0.62, 0.22, -0.02, -0.62)[band]
                    if seg == 0 and band in (1, 2):
                        level -= 0.4  # the gap between segments
                    elif band == 1 and seg == 3:
                        level = 0.95  # a glint on each plate
                    elif band == 2 and seg == 4:
                        level += 0.2
                    c = shade_pick(r, level, fx + xx, fy + yy, dither=0.25)
                    if band == 1 and seg == 1:
                        c = m["rivet"]
                    if metal == "netherite" and band in (1, 2) and h2(fx + xx, fy + yy, 31) > 0.85:
                        c = tone(c, -0.25)  # netherite's rough grain
                    elif metal == "diamond" and (a + b) % 7 == 0 and band in (1, 2):
                        c = r[6]  # facets catching the light
                    if limit is not None and b == min(limit, fh) - 1:
                        c = m["trim"] if a % 2 else tone(m["trim"], 0.3)  # the hem
                    img[fy + yy][fx + xx] = (*c[:3], 255)
    return img


# --- Eggs -------------------------------------------------------------------------------------------------------------

CRACKS = [
    [(7, 1), (7, 2), (8, 3), (7, 4), (8, 5), (9, 5)],
    [(2, 6), (3, 7), (4, 7), (5, 8), (5, 9), (6, 9), (4, 6)],
    [(12, 7), (11, 8), (11, 9), (12, 10), (13, 11), (12, 12), (10, 9), (9, 10)],
]


def egg_texture(element, pal, stage):
    """A dragon egg's shell (the block's texture): lit from above, with big soft speckles in the element's colours, the
    element's own markings, and glowing cracks that spread as it gets ready to hatch."""
    shell = skin_ramp(pal["dark"], pal["base"], pal["light"])
    img = blank(16, 16)
    seed = sum(ord(ch) for ch in element)
    for y in range(16):
        for x in range(16):
            level = 0.42 - 0.75 * y / 15 + (fbm(x, y, seed, 4) - 0.5) * 0.5
            r = shell
            if element == "rainbow":
                r = skin_ramp(tone(RAINBOW[y * 7 // 16], -0.6), RAINBOW[y * 7 // 16], tone(RAINBOW[y * 7 // 16], 0.5))
            elif element == "honey" and y in (5, 6, 10, 11):
                r = skin_ramp(tone(pal["stripe"], -0.3), pal["stripe"], tone(pal["stripe"], 0.5))
            c = shade_pick(r, level, x, y, dither=0.35)
            spot = fbm(x + 40, y, seed + 5, 3)
            if element in kinds.PALETTES:
                c = kinds.egg(element, pal, c, x, y, level, shell, spot)
            elif element == "earth":
                d1, d2, _ = voronoi(x + 0.5, y + 0.5, seed, 4.5)
                if d2 - d1 < 0.8:
                    c = shell[0]
            elif element == "crystal":
                d1, d2, ident = voronoi(x * 1.0 + 0.5 * y, y + 0.5, seed, 5.5)
                c = shade_pick(shell, level + (h2(*ident, seed) - 0.5) * 0.7 - d1 * 0.08, x, y, dither=0.2)
                if d2 - d1 < 0.6:
                    c = shell[6]
            elif element == "frost" and spot > 0.66 and h2(x, y, 3) > 0.3:
                c = (0xf4, 0xfa, 0xff)  # patches of rime
            elif element == "verdant" and spot > 0.62:
                c = (0x9a, 0xc8, 0x4a)
            elif element == "air":
                if spot > 0.58:
                    c = (0xff, 0xff, 0xff)
                elif spot < 0.4:
                    c = mix(c, (0x9c, 0xc8, 0xec), 0.35)
            elif element == "ember" and spot > 0.64:
                c = MAGMA[1 + int((spot - 0.64) * 12) % 3]
            elif element == "tide" and (y + round(1.2 * math.sin(x * 0.8))) % 5 == 0:
                c = tone(pal["light"], 0.3)
            elif element == "storm" and spot > 0.64:
                c = shade_pick(shell, level + 0.45, x, y)  # paler stormcloud patches
            elif element in ("shadow", "ender") and h2(x, y, seed) > 0.93:
                c = tone(pal["eye"], -0.2)
            elif spot > 0.68:
                c = shade_pick(skin_ramp(tone(pal["belly"], -0.5), pal["belly"], tone(pal["belly"], 0.5)), level, x, y)
            if element == "storm" and (x, y) in ((4, 3), (5, 4), (4, 5), (5, 6), (11, 9), (10, 10), (11, 11)):
                c = (0xff, 0xf0, 0x70)
            img[y][x] = (*c, 255)
    glow, rim = pal["eye"], tone(pal["dark"], -0.6)
    for i in range(stage):
        crack = CRACKS[i]
        for x, y in crack:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in crack:
                    img[ny][nx] = (*mix(img[ny][nx], rim, 0.55), 255)
        for k, (x, y) in enumerate(crack):
            img[y][x] = (*(tone(glow, 0.5) if k % 3 == 1 else glow), 255)
    return img


SPAWN_SPOTS = {(6, 4), (7, 4), (6, 5), (9, 6), (10, 6), (10, 7), (5, 8), (6, 8), (5, 9), (8, 10), (9, 10), (8, 11),
               (11, 10), (11, 11), (6, 12), (7, 12)}


def spawn_egg_texture(base, spot):
    """A 16x16 spawn egg: a round-shaded egg in the mob's main colour with spots of its second colour (shaded with the
    egg), a glint, and a deep, colour-matched outline."""
    inside = egg_mask(cx=8.0, cy=8.3, rx=4.7, ry=6.4, taper=0.18)
    br = skin_ramp(tone(base, -0.7), base, tone(base, 0.55))
    sr = skin_ramp(tone(spot, -0.6), spot, tone(spot, 0.5))
    img = blank(16, 16)
    for x, y in inside:
        level = sphere_light(x, y, 8.0, 8.3, 4.7, 6.4) * 0.85
        c = shade_pick(sr if (x, y) in SPAWN_SPOTS else br, level, x, y, dither=0.7)
        img[y][x] = (*c, 255)
    for x, y in ((6, 3), (5, 4)):
        if (x, y) in inside and (x, y) not in SPAWN_SPOTS:
            img[y][x] = (*tone(base, 0.95), 255)
    return outline(img, darken=-1.0)
