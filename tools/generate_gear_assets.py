#!/usr/bin/env python3
"""Generates the Wonder Realms 1.2 gear: dragon armor, the dragon whistle, the shrink ray, the pixel chisel and the
airship cannon. Writes their item icons, item models and definitions, crafting (and smithing) recipes, and names.

Run from the wonder-realms folder after generate_assets.py:  python3 tools/generate_gear_assets.py
Pixel art is drawn from the character grids below (no image library needed).
"""
import json
from pathlib import Path

from generate_assets import write_png, rgba

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

ITEMS = {
    **{f"{metal}_dragon_armor": (f"{metal.capitalize()} Dragon Armor", ARMOR, armor_palette(*colors)) for metal, colors in METALS.items()},
    "dragon_whistle": ("Dragon Whistle", WHISTLE, WHISTLE_PAL),
    "shrink_ray": ("Shrink Ray", SHRINK_RAY, SHRINK_RAY_PAL),
    "pixel_chisel": ("Pixel Chisel", CHISEL, CHISEL_PAL),
    "airship_cannon": ("Airship Cannon", CANNON, CANNON_PAL),
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
}


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
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print("Generated", len(ITEMS), "gear items and", len(RECIPES), "recipes")


if __name__ == "__main__":
    main()
