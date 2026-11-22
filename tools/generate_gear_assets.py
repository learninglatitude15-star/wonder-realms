#!/usr/bin/env python3
"""Generates the Wonder Realms gear: dragon armor, the dragon whistle, the shrink ray, the pixel chisel, the airship
cannon, dragon and Elder scales, the Dragon Heart, and dragonscale armor (with its armor textures). Writes their
item icons, item models and definitions, crafting (and smithing) recipes, and names.

Run from the wonder-realms folder after generate_assets.py:  python3 tools/generate_gear_assets.py
The icons and worn textures are drawn by item_art.py (shaped parts, bevel shading and outlines; no image library).
"""
import json
import os
from pathlib import Path

import item_art as art
from generate_dragon_assets import write_png as write_image

ROOT = Path(os.environ.get("WR_RESOURCES") or Path(__file__).resolve().parent.parent / "src" / "main" / "resources")
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"


SCALE_COLORS = {"dragon_scale": (0x2a, 0xa8, 0x8a), "elder_scale": (0xc0, 0x8a, 0x30)}
ITEMS = {
    **{f"{metal}_dragon_armor": (f"{metal.capitalize()} Dragon Armor", lambda metal=metal: art.dragon_armor(metal)) for metal in art.METAL_COLORS},
    "dragon_whistle": ("Dragon Whistle", art.whistle_icon),
    "shrink_ray": ("Shrink Ray", art.shrink_ray_icon),
    "pixel_chisel": ("Pixel Chisel", art.chisel_icon),
    "airship_cannon": ("Airship Cannon", art.cannon_icon),
    "dragon_scale": ("Dragon Scale", lambda: art.scale_icon(SCALE_COLORS["dragon_scale"])),
    "elder_scale": ("Elder Scale", lambda: art.scale_icon(SCALE_COLORS["elder_scale"])),
    "dragon_heart": ("Dragon Heart", art.heart_icon),
    "dragon_wings": ("Dragon Wings", art.wings_icon),
    **{f"dragonscale_{piece}": (f"Dragonscale {piece.capitalize()}", lambda piece=piece: art.dragonscale_icon(piece))
       for piece in art.DRAGONSCALE_PIECES},
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


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    for item, (name, draw) in ITEMS.items():
        write_image(ASSETS / "textures" / "item" / f"{item}.png", draw())
        dump(ASSETS / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
        dump(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item}"}})
        lang[f"item.{NS}.{item}"] = name
    for name, recipe in RECIPES.items():
        dump(DATA / "recipe" / f"{name}.json", recipe)
    # Dragonscale armor: how it looks when worn, and what repairs it.
    main_layer, leggings_layer = art.dragonscale_worn()
    write_image(ASSETS / "textures" / "entity" / "equipment" / "humanoid" / "dragonscale.png", main_layer)
    write_image(ASSETS / "textures" / "entity" / "equipment" / "humanoid_leggings" / "dragonscale.png", leggings_layer)
    dump(ASSETS / "equipment" / "dragonscale.json", {"layers": {"humanoid": [{"texture": f"{NS}:dragonscale"}],
                                                                "humanoid_leggings": [{"texture": f"{NS}:dragonscale"}]}})
    dump(DATA / "tags" / "item" / "repairs_dragonscale_armor.json", {"values": [f"{NS}:dragon_scale"]})
    # Dragon Wings: how they look when worn, like an elytra's wings.
    write_image(ASSETS / "textures" / "entity" / "equipment" / "wings" / "dragon_wings.png", art.dragon_wings_worn())
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
