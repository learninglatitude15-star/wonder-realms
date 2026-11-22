#!/usr/bin/env python3
"""Texture preview: renders the GeckoLib models with their skins and animations (posed and mapped as GeckoLib does)
and lays item and block textures out on sheets, so changes can be looked at before they go into the mod.

    WR_RESOURCES=SOME_COPY python3 tools/generate_...        (generate into a copy of src/main/resources)
    python3 tools/texture_preview.py OLD_RESOURCES NEW_RESOURCES OUT_DIR [MINECRAFT_CLIENT_JAR]

writes OUT_DIR/index.html (images embedded), old beside new. The client jar is only read for the vanilla dragon egg
model, to show eggs in 3D. Nothing here touches the mod.
"""
import base64
import json
import math
import struct
import sys
import zlib
from pathlib import Path


# --- PNG in and out -------------------------------------------------------------------------------------------------

def read_png(path):
    data = Path(path).read_bytes()
    pos, idat, w, h, ctype = 8, b"", 0, 0, 6
    palette, trns = [], b""
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        kind, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            w, h, _, ctype = struct.unpack(">IIBB", body[:10])
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = {6: 4, 2: 3, 3: 1, 0: 1, 4: 2}[ctype]
    raw, rows, stride, prev, i = zlib.decompress(idat), [], w * bpp, bytearray(w * bpp), 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        if ctype == 6:
            rows.append([tuple(line[k:k + 4]) for k in range(0, stride, 4)])
        elif ctype == 2:
            rows.append([(*line[k:k + 3], 255) for k in range(0, stride, 3)])
        elif ctype == 3:
            rows.append([(*palette[k], trns[k] if k < len(trns) else 255) for k in line])
        elif ctype == 0:
            rows.append([(k, k, k, 255) for k in line])
        else:
            rows.append([(line[k], line[k], line[k], line[k + 1]) for k in range(0, stride, 2)])
        prev = line
    return rows


def png_bytes(img):
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in img)

    def chunk(kind, d):
        return struct.pack(">I", len(d)) + kind + d + struct.pack(">I", zlib.crc32(kind + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# --- GeckoLib model -> textured quads -------------------------------------------------------------------------------

def _rot(rx, ry, rz):
    """Rotation matrix applied Z, then Y, then X (as GeckoLib poses a bone), angles in degrees."""
    def rx_m(a):
        c, s = math.cos(a), math.sin(a)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def ry_m(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def rz_m(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul(mul(rz_m(math.radians(rz)), ry_m(math.radians(ry))), rx_m(math.radians(rx)))


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def winding_normal(pts):
    """The front of a quad is the side it winds counter-clockwise from, mirrored cubes included."""
    a = [pts[1][i] - pts[0][i] for i in range(3)]
    b = [pts[3][i] - pts[0][i] for i in range(3)]
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def sample_pose(animations, name, t):
    """The pose of an animation at time t, interpolated in straight lines between keyframes as GeckoLib does."""
    def at(channel):
        if isinstance(channel, list):
            return channel
        keys = sorted(((float(k), v) for k, v in channel.items()), key=lambda kv: kv[0])
        if t <= keys[0][0]:
            return keys[0][1]
        for (t0, a), (t1, b) in zip(keys, keys[1:]):
            if t <= t1:
                f = (t - t0) / (t1 - t0)
                return [a[i] + (b[i] - a[i]) * f for i in range(3)]
        return keys[-1][1]

    bones = animations["animations"][name]["bones"]
    return {bone: {kind: at(channel) for kind, channel in parts.items()} for bone, parts in bones.items()}


def model_quads(geo, pose=None):
    """Quads [(4 points, 4 uvs, normal, (bone, cube index, face))] in GeckoLib model space (Bedrock X flipped), in
    pixels. pose: bone -> {"rotation", "position", "scale": [x, y, z]} as written in the animation files (see
    sample_pose). A bone's transform is translate, rotate Z-Y-X, scale, all about its pivot, and children inherit it."""
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    bones = {b["name"]: b for b in g["bones"]}
    pose = pose or {}

    def bone_transform(name):
        """Returns a function mapping a point in this bone's space to model space."""
        chain = []
        while name:
            chain.append(bones[name])
            name = bones[name].get("parent")
        funcs = []
        for b in chain:  # innermost first
            px, py, pz = b.get("pivot", [0, 0, 0])
            pivot = (-px, py, pz)
            rx, ry, rz = b.get("rotation", [0, 0, 0])
            p = pose.get(b["name"], {})
            ax, ay, az = p.get("rotation", [0, 0, 0])
            # GeckoLib negates X and Y rotations from Bedrock files (and animations) but not Z.
            m = _rot(-(rx + ax), -(ry + ay), rz + az)
            tx, ty, tz = p.get("position", [0, 0, 0])
            funcs.append((pivot, m, (-tx, ty, tz), p.get("scale", [1, 1, 1])))

        def f(v):
            for pivot, m, t, k in funcs:
                v = [(v[i] - pivot[i]) * k[i] for i in range(3)]
                v = _apply(m, v)
                v = [v[i] + pivot[i] + t[i] for i in range(3)]
            return v
        return f

    quads = []
    for b in g["bones"]:
        tf = bone_transform(b["name"])
        for index, c in enumerate(b.get("cubes", [])):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            inf = c.get("inflate", 0)
            x0, y0, z0 = -(ox + sx), oy, oz
            mirror = bool(c.get("mirror"))
            blb = (x0 - inf, y0 - inf, z0 - inf)
            brb = (x0 - inf, y0 - inf, z0 + sz + inf)
            tlb = (x0 - inf, y0 + sy + inf, z0 - inf)
            trb = (x0 - inf, y0 + sy + inf, z0 + sz + inf)
            tlf = (x0 + sx + inf, y0 + sy + inf, z0 - inf)
            trf = (x0 + sx + inf, y0 + sy + inf, z0 + sz + inf)
            blf = (x0 + sx + inf, y0 - inf, z0 - inf)
            brf = (x0 + sx + inf, y0 - inf, z0 + sz + inf)
            west, east = [trb, tlb, blb, brb], [tlf, trf, brf, blf]
            sets = {"west": east if mirror else west, "east": west if mirror else east,
                    "north": [tlb, tlf, blf, blb], "south": [trf, trb, brb, brf],
                    "up": [trb, trf, tlf, tlb], "down": [blb, blf, brf, brb]}
            u, v = c["uv"] if isinstance(c["uv"], list) else (0, 0)
            fx, fy, fz = math.floor(sx), math.floor(sy), math.floor(sz)
            box = {"west": (u + fz + fx, v + fz, fz, fy), "east": (u, v + fz, fz, fy),
                   "north": (u + fz, v + fz, fx, fy), "south": (u + fz + fx + fz, v + fz, fx, fy),
                   "up": (u + fz, v, fx, fz), "down": (u + fz + fx, v + fz, fx, -fz)}
            for face, verts in sets.items():
                if isinstance(c["uv"], dict):
                    spec = c["uv"].get(face)
                    if not spec:
                        continue
                    (fu, fv), (us, vs) = spec["uv"], spec["uv_size"]
                else:
                    fu, fv, us, vs = box[face]
                u0, u1, v0, v1 = fu, fu + us, fv, fv + vs
                if not mirror:
                    u0, u1 = u1, u0
                uvs = [(u0 / tw, v0 / th), (u1 / tw, v0 / th), (u1 / tw, v1 / th), (u0 / tw, v1 / th)]
                pts = [tf(list(p)) for p in verts]
                quads.append((pts, uvs, winding_normal(pts), (b["name"], index, face)))
    return quads, tw, th


LIGHT0 = (0.2, 1.0, -0.7)
LIGHT1 = (-0.2, 1.0, 0.7)


def _norm(v):
    m = math.sqrt(sum(x * x for x in v)) or 1
    return [x / m for x in v]


def block_quads(model):
    """Quads for a Java block model's cuboid elements (no rotations), in model pixels, UVs from 0..16 to 0..1."""
    quads = []
    for el in model["elements"]:
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        corners = {
            "north": ([(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], (0, 0, -1)),
            "south": ([(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], (0, 0, 1)),
            "west": ([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], (-1, 0, 0)),
            "east": ([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], (1, 0, 0)),
            "up": ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0)),
            "down": ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], (0, -1, 0)),
        }
        for face, spec in el["faces"].items():
            u0, v0, u1, v1 = (c / 16 for c in spec["uv"])
            pts, normal = corners[face]
            quads.append(([list(p) for p in pts], [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], list(normal)))
    return quads


BLOCK_SHADE = ((0.6, 0.6), (0.5, 1.0), (0.8, 0.8))  # a block face's shade by axis: (negative, positive) side


def projector(yaw=35, pitch=28, flip=False):
    """Model point -> (screen right, screen down, toward the viewer)."""
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p
        if flip:
            x, z = -x, -z
        x, z = x * cy - z * sy_, x * sy_ + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, -y, z
    return view


def render(geo, texture, yaw=35, pitch=28, scale=4, pose=None, pad=6, flip=False, quads=None, cull=False, block=False,
           bounds=None):
    """An orthographic render on a transparent background, lit as Minecraft lights it. Entities use entityCutout,
    which in 26.3 culls nothing and lights whichever side of a face is seen (PER_FACE_LIGHTING); blocks cull faces
    turned away and shade by direction. bounds (min x, min y, max x, max y on screen) keeps animation frames in step."""
    if quads is None:
        quads = model_quads(geo, pose)[0]
    tex_h, tex_w = len(texture), len(texture[0])
    view = projector(yaw, pitch, flip)
    l0, l1 = _norm(LIGHT0), _norm(LIGHT1)

    def lit(n):
        return min(1.0, 0.4 + 0.6 * (max(0, sum(a * b for a, b in zip(n, l0))) + max(0, sum(a * b for a, b in zip(n, l1)))))

    projected = []
    for pts, uvs, normal, *_ in quads:
        if sum(x * x for x in normal) < 1e-12:
            continue  # a side of a cube with no thickness
        n = _norm(normal)
        toward = view(n)[2]  # > 0: we are looking at the front of the face
        if toward < 0:
            if cull or block:
                continue
            n = [-x for x in n]
        if block:
            axis = max(range(3), key=lambda i: abs(n[i]))
            light = BLOCK_SHADE[axis][1 if n[axis] > 0 else 0]
        else:
            light = lit(n)
        projected.append(([view(p) for p in pts], uvs, light))
    xs = [p[0] for q in projected for p in q[0]]
    ys = [p[1] for q in projected for p in q[0]]
    minx, miny, maxx, maxy = bounds or (min(xs), min(ys), max(xs), max(ys))
    W = int((maxx - minx) * scale) + pad * 2 + 1
    H = int((maxy - miny) * scale) + pad * 2 + 1
    img = [[(0, 0, 0, 0)] * W for _ in range(H)]
    depth = [[-1e9] * W for _ in range(H)]
    for vp, uvs, light in projected:
        sp_ = [((p[0] - minx) * scale + pad, (p[1] - miny) * scale + pad, p[2]) for p in vp]
        p0, p1, p3 = sp_[0], sp_[1], sp_[3]
        ax, ay = p1[0] - p0[0], p1[1] - p0[1]
        bx, by = p3[0] - p0[0], p3[1] - p0[1]
        det = ax * by - ay * bx
        if abs(det) < 1e-6:
            continue
        bx0 = max(0, int(min(p[0] for p in sp_)))
        bx1 = min(W - 1, int(max(p[0] for p in sp_)) + 1)
        by0 = max(0, int(min(p[1] for p in sp_)))
        by1 = min(H - 1, int(max(p[1] for p in sp_)) + 1)
        umin = int(round(min(t[0] for t in uvs) * tex_w))
        umax = int(round(max(t[0] for t in uvs) * tex_w)) - 1
        vmin = int(round(min(t[1] for t in uvs) * tex_h))
        vmax = int(round(max(t[1] for t in uvs) * tex_h)) - 1
        if umax < umin or vmax < vmin:
            continue
        du1, dv1 = uvs[1][0] - uvs[0][0], uvs[1][1] - uvs[0][1]
        du3, dv3 = uvs[3][0] - uvs[0][0], uvs[3][1] - uvs[0][1]
        dz1, dz3 = p1[2] - p0[2], p3[2] - p0[2]
        for py in range(by0, by1 + 1):
            ry = py + 0.5 - p0[1]
            row, drow = img[py], depth[py]
            for px in range(bx0, bx1 + 1):
                rx = px + 0.5 - p0[0]
                a = (rx * by - ry * bx) / det
                b = (ax * ry - ay * rx) / det
                if a < 0 or a > 1 or b < 0 or b > 1:
                    continue
                z = p0[2] + a * dz1 + b * dz3
                if z < drow[px] - 1e-6:
                    continue  # at equal depth the later face wins, as the game's depth test has it
                tu = uvs[0][0] + a * du1 + b * du3
                tv = uvs[0][1] + a * dv1 + b * dv3
                tx = min(umax, max(umin, int(tu * tex_w)))
                ty_ = min(vmax, max(vmin, int(tv * tex_h)))
                c = texture[ty_][tx]
                if c[3] < 26:
                    continue
                drow[px] = z
                row[px] = (int(c[0] * light), int(c[1] * light), int(c[2] * light), 255)
    return img


# --- Sheets ---------------------------------------------------------------------------------------------------------

BG = (34, 36, 44, 255)


def blank(w, h, c=BG):
    return [[c] * w for _ in range(h)]


def blit(dst, src, ox, oy, scale=1, checker=False):
    for y, row in enumerate(src):
        for x, p in enumerate(row):
            if p[3] == 0:
                if not checker:
                    continue
                c = (70, 72, 82, 255) if ((x * scale) // 8 + (y * scale) // 8) % 2 else (56, 58, 68, 255)
            elif p[3] < 255:
                a = p[3] / 255
                c = tuple(int(p[i] * a + 60 * (1 - a)) for i in range(3)) + (255,)
            else:
                c = p
            for dy in range(scale):
                line = dst[oy + y * scale + dy]
                for dx in range(scale):
                    line[ox + x * scale + dx] = c


def grid(images, cols, scale=1, gap=6, checker=True):
    cw = max(len(i[0]) for i in images) * scale
    ch = max(len(i) for i in images) * scale
    rows = (len(images) + cols - 1) // cols
    out = blank(cols * (cw + gap) + gap, rows * (ch + gap) + gap)
    for k, img in enumerate(images):
        ox = gap + (k % cols) * (cw + gap) + (cw - len(img[0]) * scale) // 2
        oy = gap + (k // cols) * (ch + gap) + (ch - len(img) * scale) // 2
        blit(out, img, ox, oy, scale, checker)
    return out


# --- The preview page -----------------------------------------------------------------------------------------------

def _b64(img):
    return "data:image/png;base64," + base64.b64encode(png_bytes(img)).decode()


def _upscale(img, k):
    return [[p for p in row for _ in range(k)] for row in img for _ in range(k)]


def _on_checker(img, k):
    """An icon scaled up k times on a soft checkerboard, so clear pixels show."""
    big = _upscale(img, k)
    out = []
    for y, row in enumerate(big):
        line = []
        for x, p in enumerate(row):
            bg = (58, 60, 70) if ((x // 8) + (y // 8)) % 2 else (48, 50, 60)
            a = p[3] / 255
            line.append(tuple(int(p[i] * a + bg[i] * (1 - a)) for i in range(3)) + (255,))
        out.append(line)
    return out


def _figure(img, caption, css):
    """A figure from an image, or from a list of same-sized frames, played as a loop."""
    if isinstance(img[0][0][0], tuple):  # frames, not one image
        width = len(img[0][0])
        strip = [[p for frame in img for p in frame[y]] for y in range(len(img[0]))]
        body = (f'<div class="film" style="width:{width}px"><img src="{_b64(strip)}" '
                f'style="width:{width * len(img)}px;animation:film 1.6s steps({len(img)}) infinite"></div>')
    else:
        body = f'<img src="{_b64(img)}">'
    return f'<figure class="{css}"><figcaption>{caption}</figcaption>{body}</figure>'


def build(old_root, new_root, out_dir, client_jar=None):
    """Writes OUT_DIR/index.html: before/after renders of the models and sheets of the icons and other textures.
    Returns (sections, the new textures that ended up nowhere on the page)."""
    import zipfile
    old_root, new_root, out_dir = Path(old_root), Path(new_root), Path(out_dir)
    A = "assets/wonderrealms/"
    T = A + "textures/"
    shown = set()

    def tex(root, path, base=T):
        if root is new_root:
            shown.add(base + path)
        return read_png(root / base / path)

    def geo(root, name):
        return json.loads((root / A / "geckolib/models/entity" / f"{name}.geo.json").read_text())

    def anims(root, name):
        return json.loads((root / A / "geckolib/animations/entity" / f"{name}.animation.json").read_text())

    both = (old_root, new_root)

    def model_pair(label, model, texture, scale=3, flip=True, yaw=35, pitch=28, anim=None, at=0):
        before, after = [render(geo(r, model), tex(r, f"entity/{texture}.png"), yaw=yaw, pitch=pitch, scale=scale, flip=flip,
                                pose=sample_pose(anims(r, model), anim, at) if anim else None) for r in both]
        return (label, before, after)

    def flat_pair(label, paths, k=5, cols=7, base=T):
        before, after = [grid([_on_checker(tex(r, p, base), k) for p in paths], min(cols, len(paths)), 1, gap=10, checker=False)
                         for r in both]
        return (label, before, after)

    def span(root):
        xs = [p[0] for q in model_quads(geo(root, "dragon"))[0] for p in q[0]]
        return (max(xs) - min(xs)) / 16

    def flapping(label, texture, yaw, pitch, scale):
        """Both models flapping, in step and in the same frame, so their sizes compare."""
        steps = 16
        posed = []
        for r in both:
            a = anims(r, "dragon")
            length = a["animations"]["move.fly"]["animation_length"]
            g = geo(r, "dragon")
            posed.append([model_quads(g, sample_pose(a, "move.fly", (1.6 * i / steps) % length))[0] for i in range(steps)])
        view = projector(yaw, pitch, True)
        pts = [view(p) for film in posed for quads in film for q in quads for p in q[0]]
        box = (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))
        films = []
        for r, film in zip(both, posed):
            skin = tex(r, f"entity/{texture}.png")
            films.append([render(None, skin, yaw=yaw, pitch=pitch, scale=scale, flip=True, quads=q, bounds=box) for q in film])
        return (label, films[0], films[1])

    sections = []
    sections.append(("Longer wings", f"Each wing is twice as long: wingspan goes from {span(old_root):.1f} to {span(new_root):.1f} blocks "
                     "on a grown dragon. A new wrist joint lets the tips trail as it flaps and lets the wing fold up on the ground.",
                     [flapping("Flying, from the front", "ember_dragon", 0, 12, 3),
                      flapping("Flying, from above", "frost_dragon_saddled", 25, 50, 3),
                      model_pair("Wingspan, from above", "dragon", "elder_dragon", scale=3, yaw=0, pitch=90),
                      model_pair("Standing, folded", "dragon", "ember_dragon", anim="misc.idle"),
                      model_pair("Standing, folded, side on", "dragon", "tide_dragon", yaw=90, pitch=5, anim="misc.idle"),
                      model_pair("Sitting", "dragon", "verdant_dragon_saddled", yaw=-40, anim="misc.sit")]))

    dragons = ["ember", "frost", "storm", "verdant", "tide", "crystal", "shadow", "earth", "honey", "rainbow", "air", "ender", "elder"]
    names = {"ender": "Ender (from the End)", "elder": "Elder Dragon"}
    sections.append(("Dragons", "Every kind: overlapping shaded scales, belly plates, eyes with pupils, teeth, claws, long "
                     "scalloped wings and each element's own markings.",
                     [model_pair(names.get(d, d.capitalize()), "dragon", f"{d}_dragon") for d in dragons]))

    def dress(root):
        g = geo(root, "dragon")
        pose = sample_pose(anims(root, "dragon"), "misc.idle", 0)
        outfits = [metal + tack for metal in ("", "_iron", "_golden", "_diamond", "_netherite", "_elder")
                   for tack in ("", "_saddled", "_saddled_bags")]
        return grid([render(g, tex(root, f"entity/{d}_dragon{o}.png"), yaw=55, pitch=22, scale=1.5, flip=True, pose=pose, pad=2)
                     for d in dragons for o in outfits], len(outfits), 1, gap=2, checker=False)

    sections.append(("Armor, saddles and bags", "Riveted overlapping plates with a trimmed hem, a saddle on a red blanket, "
                     "saddlebags with flaps and buckles.",
                     [model_pair("Ember + netherite armor, saddle, bags", "dragon", "ember_dragon_netherite_saddled_bags", anim="misc.idle"),
                      model_pair("Frost + diamond armor, saddle", "dragon", "frost_dragon_diamond_saddled", anim="misc.idle"),
                      model_pair("Honey + gold armor", "dragon", "honey_dragon_golden", anim="misc.idle"),
                      model_pair("Tide + iron armor, saddle, bags", "dragon", "tide_dragon_iron_saddled_bags", flip=False, yaw=30, anim="misc.idle"),
                      model_pair("Elder + elder armor, saddle, bags", "dragon", "elder_dragon_elder_saddled_bags", flip=False, yaw=30, anim="misc.idle"),
                      ("All 234 dragon skins, new (a row per kind; none, iron, gold, diamond, netherite and elder armor, "
                       "each bare, saddled and with bags)", None, dress(new_root))]))

    sections.append(("Sky creatures", "A whale-shaped tail, soft cloud patches and barnacles; glowing lightning scars on the "
                     "Storm Leviathan; lace wings on dragonflies.",
                     [model_pair("Sky Whale", "sky_whale", "sky_whale"),
                      model_pair("Sky Whale, saddled", "sky_whale", "sky_whale_saddled", flip=False, yaw=30),
                      model_pair("Storm Leviathan", "sky_whale", "storm_leviathan"),
                      model_pair("Rhino Beetle", "rhino_beetle", "rhino_beetle", scale=10),
                      model_pair("Rhino Beetle, saddled", "rhino_beetle", "rhino_beetle_saddled", scale=10),
                      model_pair("Dragonfly", "dragonfly", "dragonfly", scale=10),
                      model_pair("Dragonfly, saddled", "dragonfly", "dragonfly_saddled", scale=10),
                      model_pair("Grasshopper", "grasshopper", "grasshopper", scale=14),
                      model_pair("Grasshopper, saddled", "grasshopper", "grasshopper_saddled", scale=14),
                      model_pair("Firefly", "firefly", "firefly", scale=26),
                      flat_pair("What glows in the dark: Leviathan and firefly",
                                ["entity/storm_leviathan_glowmask.png", "entity/firefly_glowmask.png"], k=3),
                      model_pair("Pixel Miner", "pixel_miner", "pixel_miner", scale=16, yaw=25, pitch=15)]))

    colours = sorted(p.stem for p in (new_root / T / "entity").glob("airship*.png"))
    fleet = [grid([render(geo(r, "airship"), tex(r, f"entity/{c}.png"), scale=1.5, flip=True) for c in colours], 6, 1, checker=False)
             for r in both]
    sections.append(("Airships", "Shaded balloon stripes with a rope net, planked hull with nails, riveted engine.",
                     [model_pair("Airship with cannon", "airship_cannon", "airship"), ("Every colour", fleet[0], fleet[1])]))

    egg_cards = []
    egg_model = None
    if client_jar:
        with zipfile.ZipFile(client_jar) as z:
            egg_model = json.loads(z.read("assets/minecraft/models/block/dragon_egg.json"))
    for kind in dragons:
        if not (new_root / T / f"block/{kind}_egg_0.png").exists():
            continue
        pair = []
        for r in both:
            stages = [tex(r, f"block/{kind}_egg_{stage}.png") for stage in range(4)]
            frames = ([render(None, t, yaw=40, pitch=25, scale=7, quads=block_quads(egg_model), block=True) for t in stages]
                      if egg_model else [_on_checker(t, 6) for t in stages])
            pair.append(grid(frames, 4, 1, gap=8, checker=False))
        egg_cards.append((f"{kind.capitalize()} Egg, as it hatches", pair[0], pair[1]))
    sections.append(("Dragon eggs", "Each element's markings, with glowing cracks that spread.", egg_cards))

    items = sorted(p.name for p in (new_root / T / "item").glob("*.png"))
    spawn = [f"item/{n}" for n in items if n.endswith("spawn_egg.png")]
    others = [f"item/{n}" for n in items if not n.endswith("spawn_egg.png")]
    changed = [p for p in others if (old_root / T / p).read_bytes() != (new_root / T / p).read_bytes()]
    same = [p for p in others if p not in changed]
    sections.append(("Spawn eggs", "Round shading, neat spots and a soft outline.", [flat_pair("All spawn eggs", spawn)]))
    cards = [flat_pair("Redrawn items", changed)]
    if same:
        cards.append(flat_pair("Not redrawn: " + ", ".join(Path(p).stem.replace("_", " ").title() for p in same), same))
    sections.append(("Items", "Lit from the top left, with bevels and colour-matched outlines.", cards))

    jar_model = json.loads((new_root / A / "models/block/firefly_jar.json").read_text())
    jars = [render(None, tex(r, "block/firefly_jar.png"), yaw=40, pitch=25, scale=14, quads=block_quads(jar_model), block=True)
            for r in both]

    def tiled(img, n=4, k=4):
        return _upscale([[img[y % 16][x % 16] for x in range(16 * n)] for y in range(16 * n)], k)

    sky = [tiled(tex(r, "gui/advancements/backgrounds/sky.png")) for r in both]
    sections.append(("Everything else", "", [
        flat_pair("Worn dragonscale armor and Dragon Wings glider (texture sheets)",
                  ["entity/equipment/humanoid/dragonscale.png", "entity/equipment/humanoid_leggings/dragonscale.png",
                   "entity/equipment/wings/dragon_wings.png"], k=4),
        ("Firefly Jar", jars[0], jars[1]),
        flat_pair("Shrunken effect", ["mob_effect/shrunken.png"], k=6),
        flat_pair("Mod icon", ["icon.png"], k=6, base=A),
        ("Advancements backdrop (tiled)", sky[0], sky[1])]))

    out_dir.mkdir(parents=True, exist_ok=True)
    html = ["""<!DOCTYPE html><html><head><meta charset="utf-8"><title>Wonder Realms preview: before and after</title>
<style>
body{background:#1b1d24;color:#e8e8ee;font:15px/1.45 -apple-system,system-ui,sans-serif;margin:0;padding:24px 28px 60px}
h1{font-size:24px;margin:0 0 4px}h2{font-size:19px;margin:34px 0 4px;border-top:1px solid #333744;padding-top:20px}
p.lead{color:#aab0c0;margin:0 0 14px;max-width:900px}
.card{background:#23262f;border-radius:10px;padding:12px 14px;margin:12px 12px 12px 0;display:inline-block;vertical-align:top;max-width:100%;box-sizing:border-box}
.card h3{font-size:14px;margin:0 0 8px;color:#cfd4e4;font-weight:600;max-width:1100px}
.pair{display:flex;gap:14px;align-items:flex-start;flex-wrap:wrap}
.pair figure{margin:0;max-width:100%}.pair figcaption{font-size:12px;color:#8c93a6;margin-bottom:4px;text-transform:uppercase;letter-spacing:.06em}
.pair figure.new figcaption{color:#7fd88f}
img{image-rendering:pixelated;display:block;max-width:100%}
.film{overflow:hidden;max-width:100%}.film img{max-width:none}
@keyframes film{to{transform:translateX(-100%)}}
@media (prefers-reduced-motion:reduce){.film img{animation:none!important}}
</style></head><body>
<h1>Wonder Realms preview: before and after</h1>
<p class="lead">Before is what's in the game now; after is what would go in. These are drawn from the model files, not
screenshots from the game. Nothing has been put into the mod yet.</p>"""]
    for title, lead, cards in sections:
        html.append(f"<h2>{title}</h2>")
        if lead:
            html.append(f'<p class="lead">{lead}</p>')
        for label, before, after in cards:
            figures = ("" if before is None else _figure(before, "Before", "old")) + _figure(after, "After", "new")
            html.append(f'<div class="card"><h3>{label}</h3><div class="pair">{figures}</div></div>')
    html.append("</body></html>")
    (out_dir / "index.html").write_text("\n".join(html))
    unseen = sorted(str(p.relative_to(new_root)) for p in (new_root / A).rglob("*.png") if str(p.relative_to(new_root)) not in shown)
    return sections, unseen


if __name__ == "__main__":
    _, missing = build(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None)
    print("wrote", Path(sys.argv[3]) / "index.html")
    print(f"{len(missing)} textures not on the page", *missing)
