#!/usr/bin/env python3
"""Generates the Wonder Realms gear: dragon armor, the dragon whistle, the shrink ray, the pixel chisel, the airship
cannon, dragon and Elder scales, the Dragon Heart, and dragonscale armor (with its armor textures). Writes their
item icons, item models and definitions, crafting (and smithing) recipes, and names.

Run from the wonder-realms folder after generate_assets.py:  python3 tools/generate_gear_assets.py
Pixel art is drawn from the character grids below (no image library needed).
"""
import json
from pathlib import Path

from generate_assets import write_png, rgba
from generate_dragon_assets import write_png as write_image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"


def grid(*rows):
    """Pads or trims each row to 16 pixels, so the art below can be drawn without counting dots."""
    rows = [row.ljust(16, ".")[:16] for row in rows]
    return rows + ["." * 16] * (16 - len(rows))


# A barding plate for a dragon's neck and chest, horned and riveted, with a leather strap.
ARMOR = grid(
    "................",
    ".........h......",
    "........hMd.....",
    ".......hMMd.....",
    "...hhhhMMMMd....",
    "..hMMMMMMMMMd...",
    "..hMrMMMMMrMd...",
    "..hMMMMMMMMMd...",
    "...dMMMMMMMd....",
    "....kdMMMdk.....",
    "...hMMMMMMMd....",
    "..hMMrMMMrMMd...",
    "..hMMMMMMMMMd...",
    "...dMMMMMMMd....",
    "....ddddddd.....",
)
METALS = {
    "iron": ("e6e6ea", "b2b4ba", "70727a", "50525a"),
    "golden": ("fff48a", "f0c438", "b07c14", "7a5008"),
    "diamond": ("d0fffa", "5ce0d8", "229a96", "145e5c"),
    "netherite": ("7a7076", "4c444a", "302a2e", "d8a85a"),
    "elder": ("fff4d8", "d8b870", "8a642a", "c82030"),
}


def armor_palette(light, mid, dark, rivet):
    return {"h": rgba(light), "M": rgba(mid), "d": rgba(dark), "r": rgba(rivet), "k": rgba("6b4423")}


WHISTLE = grid(
    "................",
    "................",
    "..........ss....",
    ".........s..s...",
    ".........s...s..",
    "..........s..s..",
    "...bbbbbbbbs.s..",
    "..bWWWWWWWWb.s..",
    ".bWWhWWWWWWWbs..",
    ".bWkkWWWWWWWb...",
    "..bWWWWWWWWb....",
    "...bbbbbbbb.....",
)
WHISTLE_PAL = {"W": rgba("f0ead6"), "h": rgba("ffffff"), "k": rgba("5a4a3a"), "b": rgba("9a9078"), "s": rgba("c8a870")}

SHRINK_RAY = grid(
    "................",
    "................",
    "...........pp...",
    "..........pPPp..",
    "...gggggggPPPp..",
    "..gGGGGGGGGPp...",
    "..gGhhGGGGGGg...",
    "..gGGGGGGGGGg...",
    "...gggGGgggg....",
    ".....gGGg.......",
    ".....gGGg.......",
    ".....gGGg.......",
    "......gg........",
)
SHRINK_RAY_PAL = {"g": rgba("3a3a46"), "G": rgba("8a8ea0"), "h": rgba("d8dcef"), "P": rgba("e0a0ff"), "p": rgba("8a3ad8")}

CHISEL = grid(
    "................",
    "............ss..",
    "...........sSs..",
    "..........sSs...",
    ".........sSs....",
    "........sSs.....",
    ".......wws......",
    "......wWw.......",
    ".....wWw........",
    "....wWw.........",
    "...wWw..........",
    "..wWw.....a.....",
    "..ww.....aAa....",
    "..........a.....",
)
CHISEL_PAL = {"s": rgba("5a5e66"), "S": rgba("c8ccd6"), "w": rgba("5a3a20"), "W": rgba("9a6a3a"), "a": rgba("8a5ad8"), "A": rgba("f0e0ff")}

CANNON = grid(
    "................",
    "................",
    "................",
    "..........kk....",
    ".....kkkkkkKk...",
    "...kkKKKKKKKKk..",
    "..kKKhhKKKKKKk..",
    "..kKKKKKKKKKKk..",
    "...kkKKKKKKkk...",
    "....wWWWWWw.....",
    "...wWWWWWWWw....",
    "..wwOwwwwwOww...",
    "..wOOOw..wOOOw..",
    "...OOO....OOO...",
)
CANNON_PAL = {"k": rgba("1e2024"), "K": rgba("3a3e46"), "h": rgba("6a707a"), "w": rgba("4a3018"), "W": rgba("7a5230"), "O": rgba("2a2a2e")}

SCALE = grid(
    "................",
    "................",
    "......hhhh......",
    ".....hLLLLh.....",
    "....hLLMMMMd....",
    "....hLMMMMMd....",
    "...hLMMMMMMMd...",
    "...hLMMMMMMMd...",
    "...hMMMMMMMMd...",
    "....dMMMMMMd....",
    "....dMMMMMMd....",
    ".....dMMMMd.....",
    "......dMMd......",
    ".......dd.......",
)


def scale_palette(edge, shine, mid, dark):
    return {"h": rgba(edge), "L": rgba(shine), "M": rgba(mid), "d": rgba(dark)}


HEART = grid(
    "................",
    "................",
    "...ddd....ddd...",
    "..dRRRd..dRRRd..",
    ".dRRhRRddRRRRRd.",
    ".dRhhRRRRRRRRRd.",
    ".dRRRRRRGRRRRRd.",
    ".dRRRRRGGGRRRRd.",
    "..dRRRRRGRRRRd..",
    "...dRRRRRRRRd...",
    "....dRRRRRRd....",
    ".....dRRRRd.....",
    "......dRRd......",
    ".......dd.......",
)
HEART_PAL = {"d": rgba("4a0808"), "R": rgba("b81c1c"), "h": rgba("ff8a7a"), "G": rgba("ffd040")}

# Dragonscale armor: teal-green scales trimmed with gold.
DRAGONSCALE_PAL = {"d": rgba("0e3a30"), "M": rgba("1f8a6a"), "L": rgba("6ad8b0"), "g": rgba("e0b040")}
DRAGONSCALE_PIECES = {
    "helmet": grid(
        "................",
        "................",
        "................",
        "....dddddddd....",
        "...dMLMLMLMMd...",
        "..dMMMMMMMMMMd..",
        "..dMLMLMLMLMMd..",
        "..dMMggggggMMd..",
        "..dMd......dMd..",
        "..dMd......dMd..",
        "..ddd......ddd..",
    ),
    "chestplate": grid(
        "................",
        "..ddd......ddd..",
        ".dMMMd....dMMMd.",
        ".dMLMMddddMMLMd.",
        ".dMMMMMMMMMMMMd.",
        "..ddMLMLMLMLdd..",
        "...dMMMMMMMMd...",
        "...dMLMLMLMLd...",
        "...dMMMMMMMMd...",
        "...dMLMLMLMLd...",
        "...dMMMMMMMMd...",
        "...dggggggggd...",
        "...dddddddddd...",
    ),
    "leggings": grid(
        "................",
        "...dddddddddd...",
        "...dggggggggd...",
        "...dMMMMMMMMd...",
        "...dMLMddMLMd...",
        "...dMMMddMMMd...",
        "...dMLd..dMLd...",
        "...dMMd..dMMd...",
        "...dMLd..dMLd...",
        "...dMMd..dMMd...",
        "...dMLd..dMLd...",
        "...dddd..dddd...",
    ),
    "boots": grid(
        "................",
        "................",
        "................",
        "................",
        "................",
        "...dddd..dddd...",
        "...dMLd..dMLd...",
        "...dMMd..dMMd...",
        "...dMLd..dMLd...",
        "..ddMMd..dMMdd..",
        ".dMMMMd..dMMMMd.",
        ".dggggd..dggggd.",
        ".dddddd..dddddd.",
    ),
}

# Dragon Wings, folded: a pair of teal membrane wings with pale veins, like an elytra.
WINGS = grid(
    "................",
    "..dd........dd..",
    ".dMMd......dMMd.",
    ".dMLMd....dMLMd.",
    "dMMLMMd..dMMLMMd",
    "dMLMMLd..dLMMLMd",
    "dMMLMMd..dMMLMMd",
    "dMLMMLd..dLMMLMd",
    ".dMMLMd..dMLMMd.",
    ".dMLMMd..dMMLMd.",
    "..dMMLd..dLMMd..",
    "..dMLMd..dMLMd..",
    "...dMd....dMd...",
    "...dd......dd...",
)

ITEMS = {
    **{f"{metal}_dragon_armor": (f"{metal.capitalize()} Dragon Armor", ARMOR, armor_palette(*colors)) for metal, colors in METALS.items()},
    "dragon_whistle": ("Dragon Whistle", WHISTLE, WHISTLE_PAL),
    "shrink_ray": ("Shrink Ray", SHRINK_RAY, SHRINK_RAY_PAL),
    "pixel_chisel": ("Pixel Chisel", CHISEL, CHISEL_PAL),
    "airship_cannon": ("Airship Cannon", CANNON, CANNON_PAL),
    "dragon_scale": ("Dragon Scale", SCALE, scale_palette("bff8e8", "8ff0d0", "2aa88a", "146450")),
    "elder_scale": ("Elder Scale", SCALE, scale_palette("fff4d0", "ffd870", "c08a30", "6a3a10")),
    "dragon_heart": ("Dragon Heart", HEART, HEART_PAL),
    "dragon_wings": ("Dragon Wings", WINGS, DRAGONSCALE_PAL),
    **{f"dragonscale_{piece}": (f"Dragonscale {piece.capitalize()}", rows, DRAGONSCALE_PAL) for piece, rows in DRAGONSCALE_PIECES.items()},
}


def shaped(category, pattern, key, result):
    return {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key, "result": {"id": result, "count": 1}}


def shapeless(category, ingredients, result):
    return {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": {"id": result, "count": 1}}


RECIPES = {
    # Plate armor stitched to a leather backing.
    **{f"{metal}_dragon_armor": shaped("equipment", ["I I", "ILI", "III"], {"I": material, "L": "minecraft:leather"}, f"{NS}:{metal}_dragon_armor")
       for metal, material in (("iron", "minecraft:iron_ingot"), ("golden", "minecraft:gold_ingot"), ("diamond", "minecraft:diamond"))},
    "netherite_dragon_armor": {
        "type": "minecraft:smithing_transform",
        "template": "minecraft:netherite_upgrade_smithing_template",
        "base": f"{NS}:diamond_dragon_armor",
        "addition": "#minecraft:netherite_tool_materials",
        "result": {"id": f"{NS}:netherite_dragon_armor"},
    },
    "dragon_whistle": shapeless("equipment", ["minecraft:bone", "minecraft:iron_nugget", "minecraft:string"], f"{NS}:dragon_whistle"),
    "shrink_ray": shaped("equipment", [" A ", "IDI", " I "],
                         {"A": "minecraft:amethyst_shard", "I": "minecraft:iron_ingot", "D": f"{NS}:shrinking_draught"}, f"{NS}:shrink_ray"),
    "pixel_chisel": shapeless("equipment", ["minecraft:iron_ingot", "minecraft:stick", "minecraft:amethyst_shard"], f"{NS}:pixel_chisel"),
    "airship_cannon": shaped("misc", ["III", "GDG"], {"I": "minecraft:iron_ingot", "G": "minecraft:gunpowder", "D": "minecraft:dispenser"},
                             f"{NS}:airship_cannon"),
    # The Elder Dragon's scales, worked over netherite dragon armor.
    "elder_dragon_armor": shaped("equipment", ["SSS", "SNS", "SSS"], {"S": f"{NS}:elder_scale", "N": f"{NS}:netherite_dragon_armor"},
                                 f"{NS}:elder_dragon_armor"),
    # Dragon scales stretched over phantom membrane.
    "dragon_wings": shaped("equipment", ["SMS", "SMS", "S S"], {"S": f"{NS}:dragon_scale", "M": "minecraft:phantom_membrane"},
                           f"{NS}:dragon_wings"),
    # Two Elder Scales worked onto a netherite sword.
    "elder_blade": shaped("equipment", ["S", "S", "N"], {"S": f"{NS}:elder_scale", "N": "minecraft:netherite_sword"}, f"{NS}:elder_blade"),
    # Dragonscale armor takes the shapes of any other armor.
    **{f"dragonscale_{piece}": shaped("equipment", pattern, {"S": f"{NS}:dragon_scale"}, f"{NS}:dragonscale_{piece}")
       for piece, pattern in (("helmet", ["SSS", "S S"]), ("chestplate", ["S S", "SSS", "SSS"]),
                              ("leggings", ["SSS", "S S", "S S"]), ("boots", ["S S", "S S"]))},
}


def scale_pixel(x, y):
    """Dragonscale armor, as worn: overlapping teal scales with dark edges and a light catch on each."""
    n = (x * 7 + y * 13) % 5
    if y % 2 == 0 and (x + (y // 2) % 2 * 2) % 4 == 0:
        return DRAGONSCALE_PAL["d"]
    if n == 0:
        return DRAGONSCALE_PAL["L"]
    return DRAGONSCALE_PAL["M"]


def armor_layer(paint):
    """A 64x32 humanoid armor texture, painting only the pixels where paint(x, y) is true."""
    img = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    for y in range(32):
        for x in range(64):
            if paint(x, y):
                img[y][x] = scale_pixel(x, y)
    return img


def armor_textures():
    """The worn look of dragonscale armor: a helmet with an open face, a chestplate with sleeves and a gold-trimmed
    hem, and boots on the main layer; leggings and a scaled belt on the leggings layer."""
    def outer(x, y):
        head = y < 16 and x < 32 and not (8 <= x < 16 and 8 <= y < 16 and 9 <= x <= 14 and y >= 10)  # the face stays open
        body = 16 <= x < 40 and 16 <= y < 32
        arms = 40 <= x < 56 and 16 <= y < 32
        boots = x < 16 and (y >= 27 or (8 <= x < 12 and 16 <= y < 20))  # the feet and soles of the leg boxes
        return head or body or arms or boots

    def leggings(x, y):
        legs = x < 16 and 20 <= y < 30
        belt = 16 <= x < 40 and 28 <= y < 32
        return legs or belt

    main = armor_layer(outer)
    for x in range(16, 40):
        main[31][x] = DRAGONSCALE_PAL["g"]  # gold trim at the chestplate's hem
    return main, armor_layer(leggings)


def elder_blade_texture():
    """The Elder Blade, held point up and to the right like any sword: an ivory blade edged in gold and red, a gold
    crossguard set with a red gem, a dark grip and a gold pommel, all outlined dark."""
    colors = {"L": (0xff, 0xfa, 0xe8), "M": (0xf0, 0xe0, 0xb0), "E": (0xc8, 0x20, 0x30), "g": (0xe8, 0xb8, 0x40),
              "G": (0xff, 0xe0, 0x70), "r": (0xff, 0x30, 0x30), "k": (0x4a, 0x2a, 0x16), "o": (0x2a, 0x16, 0x0a)}
    px = {}
    for x in range(5, 15):  # the blade runs along the diagonal x + y = 15, from the guard to the point
        px[(x, 15 - x)] = "M"
        px[(x - 1, 15 - x)] = "L"  # the bright upper edge
        px[(x, 16 - x)] = "E"  # the red-glowing lower edge
    px[(15, 0)] = "L"
    for x, y in ((2, 9), (3, 10), (5, 12), (6, 13)):  # the crossguard, square to the blade
        px[(x, y)] = "g"
    px[(3, 9)] = px[(6, 12)] = "G"
    px[(4, 11)] = "r"  # the gem where the blade meets the guard
    px[(3, 12)] = px[(2, 13)] = "k"  # the grip
    px[(1, 14)] = "G"
    px[(0, 15)] = "g"  # the pommel
    img = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for (x, y), key in px.items():
        img[y][x] = (*colors[key], 255)
    for (x, y) in list(px):  # a dark outline round it all
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in px and img[ny][nx][3] == 0:
                img[ny][nx] = (*colors["o"], 255)
    return img


def wings_texture():
    """Dragon Wings as worn, on the 64x32 elytra layout (each wing is a 10x20x2 box at 22,0): teal membrane, a dark
    arm bone along the top, dark finger bones down it, and a scalloped trailing edge between the fingers."""
    img = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    pal = DRAGONSCALE_PAL
    for y in range(22):
        for x in range(22, 46):
            if y < 2 and not 24 <= x < 44:
                continue  # the box's top and bottom faces are only 10 wide
            fx = (x - 24) % 12  # across the front (24-33) or back (36-45) face
            finger = x in (26, 29, 32, 38, 41, 44)
            if y >= 20 and not finger and (y == 21 or fx % 3 == 1):
                continue  # the scalloped trailing edge
            if y < 4:
                c = pal["d"]  # the arm bone along the top
            elif finger:
                c = pal["d"]
            else:
                c = pal["L"] if (x * 7 + y * 3) % 11 == 0 else pal["M"]
            img[y][x] = c
    return img


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    for item, (name, rows, palette) in ITEMS.items():
        write_png(ASSETS / "textures" / "item" / f"{item}.png", rows, palette)
        dump(ASSETS / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
        dump(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item}"}})
        lang[f"item.{NS}.{item}"] = name
    for name, recipe in RECIPES.items():
        dump(DATA / "recipe" / f"{name}.json", recipe)
    # Dragonscale armor: how it looks when worn, and what repairs it.
    main_layer, leggings_layer = armor_textures()
    write_image(ASSETS / "textures" / "entity" / "equipment" / "humanoid" / "dragonscale.png", main_layer)
    write_image(ASSETS / "textures" / "entity" / "equipment" / "humanoid_leggings" / "dragonscale.png", leggings_layer)
    dump(ASSETS / "equipment" / "dragonscale.json", {"layers": {"humanoid": [{"texture": f"{NS}:dragonscale"}],
                                                                "humanoid_leggings": [{"texture": f"{NS}:dragonscale"}]}})
    dump(DATA / "tags" / "item" / "repairs_dragonscale_armor.json", {"values": [f"{NS}:dragon_scale"]})
    # Dragon Wings: how they look when worn, like an elytra's wings.
    write_image(ASSETS / "textures" / "entity" / "equipment" / "wings" / "dragon_wings.png", wings_texture())
    dump(ASSETS / "equipment" / "dragon_wings.json", {"layers": {"wings": [{"texture": f"{NS}:dragon_wings"}]}})
    # The Elder Blade: drawn pixel by pixel, held like a sword, repaired with Elder Scales.
    write_image(ASSETS / "textures" / "item" / "elder_blade.png", elder_blade_texture())
    dump(ASSETS / "models" / "item" / "elder_blade.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/elder_blade"}})
    dump(ASSETS / "items" / "elder_blade.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/elder_blade"}})
    lang[f"item.{NS}.elder_blade"] = "Elder Blade"
    dump(DATA / "tags" / "item" / "repairs_elder_blade.json", {"values": [f"{NS}:elder_scale"]})
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Generated", len(ITEMS) + 1, "gear items and", len(RECIPES), "recipes")


if __name__ == "__main__":
    main()
