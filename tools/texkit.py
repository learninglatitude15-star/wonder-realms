"""texkit: painting helpers shared by the texture generators (the 1.4.0+ texture pass).

- tone(): hue-shifted shading. Shadows go darker, a little more saturated and drift toward blue-violet; highlights go
  brighter, paler and drift toward warm yellow, the way pixel artists build colour ramps (instead of only scaling RGB).
- Smooth value noise and fbm for coherent patches (clouds, moss, frost) instead of per-pixel static.
- Ordered (Bayer) dithering to blend two tones over a few pixels.
- Cell patterns: staggered overlapping scales, Voronoi plates, a honeycomb, and a glyph stamp.
- Sprite tools for 16x16 icons: shaded ellipses and a selective (colour-aware) outline.
"""
import colorsys
import math


def clamp8(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp8(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _toward(h, target, amount):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return (h + d * amount) % 1.0


def tone(c, level):
    """level < 0 shades (to about -1), level > 0 lights (to about +1); 0 returns the colour."""
    r, g, b = (v / 255 for v in c[:3])
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if level < 0:
        t = min(1.2, -level)
        v *= 1 - 0.58 * t
        if s > 0.08:
            h = _toward(h, 0.70, 0.10 * t)
            s = min(1.0, s + (1 - s) * 0.22 * t)
        else:  # greys and whites shade toward a cool blue-grey
            h, s = 0.62, s + 0.10 * t
    elif level > 0:
        t = min(1.2, level)
        v = min(1.0, v + (1 - v) * 0.5 * t + 0.10 * t)
        if s > 0.08:
            h = _toward(h, 0.14, 0.07 * t)
        s *= 1 - 0.38 * t
    r, g, b = colorsys.hsv_to_rgb(h, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return clamp8(r * 255), clamp8(g * 255), clamp8(b * 255)


def ramp(c, n=6, lo=-0.85, hi=0.65):
    return [tone(c, lo + (hi - lo) * i / (n - 1)) for i in range(n)]


# --- Noise ------------------------------------------------------------------------------------------------------------

def h2(x, y, seed=0):
    n = (int(x) * 374761393 + int(y) * 668265263 + int(seed) * 2654435761) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


def vnoise(x, y, seed, scale):
    fx, fy = x / scale, y / scale
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b = h2(x0, y0, seed), h2(x0 + 1, y0, seed)
    c, d = h2(x0, y0 + 1, seed), h2(x0 + 1, y0 + 1, seed)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def fbm(x, y, seed, scale, octaves=3):
    total, weight, norm = 0.0, 1.0, 0.0
    for i in range(octaves):
        total += vnoise(x, y, seed + i * 97, scale / (2 ** i)) * weight
        norm += weight
        weight *= 0.5
    return total / norm


BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def bayer(x, y):
    return (BAYER[y & 3][x & 3] + 0.5) / 16


# --- Cell patterns ----------------------------------------------------------------------------------------------------

def scale_cell(a, b, w, h):
    """Staggered scale lattice: rows of scales h tall and w wide, every other row offset by half a scale.
    Returns (cell id, x in cell, y in cell). b runs the way the scales overlap (toward each scale's rim)."""
    row = b // h
    off = (w // 2) if row % 2 else 0
    return (int((a + off) // w), int(row)), int((a + off) % w), int(b % h)


def scale_light(cx, cy, w, h):
    """Light level of a pixel within one scale of a staggered lattice. Each scale is a rounded tongue hanging from the
    top middle of its cell: bright up top, darkening toward its rim; the cell's lower corners are the gaps where the
    next row's scales show from underneath, in shadow."""
    lx, ly = cx + 0.5, cy + 0.5
    nx, ny = (lx - w / 2) / (w / 2 + 0.25), ly / (h + 0.15)
    d = (nx * nx + ny * ny) ** 0.5
    if d > 1.0:
        return -0.62
    return 0.42 - 0.95 * d * d - 0.16 * nx


def scale_gap(cx, cy, w, h):
    lx, ly = cx + 0.5, cy + 0.5
    nx, ny = (lx - w / 2) / (w / 2 + 0.25), ly / (h + 0.15)
    return nx * nx + ny * ny > 1.0


def voronoi(x, y, seed, cell):
    """Distances to the nearest and second-nearest jittered points, and the nearest point's id."""
    gx, gy = math.floor(x / cell), math.floor(y / cell)
    best, second, ident = 1e9, 1e9, (0, 0)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            cx, cy = gx + ox, gy + oy
            px = (cx + 0.15 + 0.7 * h2(cx, cy, seed)) * cell
            py = (cy + 0.15 + 0.7 * h2(cx, cy, seed + 1)) * cell
            d = math.hypot(x - px, y - py)
            if d < best:
                best, second, ident = d, best, (cx, cy)
            elif d < second:
                second = d
    return best, second, ident


def honeycomb(x, y, size=3):
    """1 on the walls of a small hexagonal comb, else 0 (an offset-brick approximation that reads as hexes)."""
    row = y // size
    off = (size + 1) // 2 if row % 2 else 0
    cx = (x + off) % (size * 2)
    cy = y % size
    if cy == 0:
        return 1 if cx < size else 0
    return 1 if cx in (0, size) else 0


# --- Sprites ----------------------------------------------------------------------------------------------------------

def blank(w=16, h=16):
    return [[(0, 0, 0, 0)] * w for _ in range(h)]


def outline(img, darken=-0.9, color=None, diagonal=False):
    """Selective outline: each empty pixel touching the sprite takes a deep shade of its neighbour's colour (or a set
    colour), the way modern pixel art outlines read softer than pure black."""
    h, w = len(img), len(img[0])
    out = [row[:] for row in img]
    steps = ((1, 0), (-1, 0), (0, 1), (0, -1)) + (((1, 1), (-1, -1), (1, -1), (-1, 1)) if diagonal else ())
    for y in range(h):
        for x in range(w):
            if img[y][x][3]:
                continue
            for dx, dy in steps:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and img[ny][nx][3]:
                    out[y][x] = (*(color or tone(img[ny][nx], darken)), 255)
                    break
    return out


def egg_mask(cx=8.0, cy=9.0, rx=4.6, ry=6.4, taper=0.18, size=16):
    inside = set()
    for y in range(size):
        for x in range(size):
            dy = (y + 0.5 - cy) / ry
            if abs(dy) >= 1:
                continue
            r = rx * (1 - dy * dy) ** 0.5 * (1 - taper * max(0.0, -dy))
            if abs(x + 0.5 - cx) <= r:
                inside.add((x, y))
    return inside


def sphere_light(x, y, cx, cy, rx, ry, lx=-0.55, ly=-0.6):
    """A shading level for a point on an ellipse lit from the upper left: about +0.8 at the highlight, -0.8 at the
    far rim."""
    nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
    nz = max(0.0, 1 - nx * nx - ny * ny) ** 0.5
    lz = (1 - lx * lx - ly * ly) ** 0.5
    d = nx * lx + ny * ly + nz * lz
    return max(-1.0, min(1.0, (d - 0.45) * 1.6))
