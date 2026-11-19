#!/usr/bin/env python3
"""Generates Wonder Realms textures, item models, item definitions, recipes and lang entries.

Run from the wonder-realms folder:  python3 tools/generate_assets.py
Pixel art is drawn from the character grids below (no image library needed).
"""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"


def write_png(path, rows, palette):
    """rows: equal-length strings; palette: char -> (r, g, b, a). Unlisted chars are transparent."""
    path.parent.mkdir(parents=True, exist_ok=True)
    width, height = len(rows[0]), len(rows)
    raw = b""
    for row in rows:
        assert len(row) == width, (path, row)
        raw += b"\x00" + b"".join(bytes(palette.get(c, (0, 0, 0, 0))) for c in row)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def rgba(hex_color, a=255):
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


BOTTLE = [
    "................",
    "......kkkk......",
    "......kCCk......",
    ".......gg.......",
    "......g..g......",
    ".....g....g.....",
    "....g.LLLL.g....",
    "...g.LLsLLLhg...",
    "...gLLLLLLLhg...",
    "...gLLsLLLLLg...",
    "...gLDLLLsLLg...",
    "...gLDDLLLLLg...",
    "....gDDDLLLg....",
    ".....gggggg.....",
    "................",
    "................",
]


def bottle(light, dark, sparkle):
    return {"k": rgba("6b4a2c"), "C": rgba("9a7046"), "g": rgba("d8eef4", 200), "h": rgba("ffffff", 220),
            "L": rgba(light, 230), "D": rgba(dark, 240), "s": rgba(sparkle, 255)}


DEWDROP = [
    "................",
    "................",
    ".......w........",
    ".......w........",
    "......wbw.......",
    "......wbb.......",
    ".....wbbbb......",
    ".....wbbbb......",
    "....whbbbbb.....",
    "....whbbbbbd....",
    "....wbbbbbbd....",
    "....wbbbbbbd....",
    ".....bbbbbd.....",
    "......dddd......",
    "................",
    "................",
]
DEWDROP_PAL = {"w": rgba("e8fbff", 230), "h": rgba("ffffff", 255), "b": rgba("7fd0f0", 200), "d": rgba("3a8fc0", 220)}

# 18x18 status effect icon: a figure shrinking between two inward arrows.
SHRUNKEN_ICON = [
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
SHRUNKEN_PAL = {"a": rgba("c8a8ff"), "p": rgba("ffffff")}

ITEMS = {
    "shrinking_draught": ("Shrinking Draught", BOTTLE, bottle("a874e8", "6a3cb0", "f0e0ff")),
    "growth_draught": ("Growth Draught", BOTTLE, bottle("74d060", "3c8a30", "e8ffd8")),
    "dewdrop": ("Dewdrop", DEWDROP, DEWDROP_PAL),
}

RECIPES = {
    # Alice's mushroom: one side shrinks, the other grows.
    "shrinking_draught": ["minecraft:glass_bottle", "minecraft:brown_mushroom", "minecraft:amethyst_shard"],
    "growth_draught": ["minecraft:glass_bottle", "minecraft:red_mushroom", "minecraft:bone_meal"],
}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    lang = {"effect.wonderrealms.shrunken": "Shrunken"}
    for item, (name, rows, pal) in ITEMS.items():
        write_png(ASSETS / "textures" / "item" / f"{item}.png", rows, pal)
        dump(ASSETS / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
        dump(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item}"}})
        lang[f"item.{NS}.{item}"] = name
    for item, ingredients in RECIPES.items():
        dump(DATA / "recipe" / f"{item}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": ingredients,
            "result": {"id": f"{NS}:{item}", "count": 1},
        })
    write_png(ASSETS / "textures" / "mob_effect" / "shrunken.png", SHRUNKEN_ICON, SHRUNKEN_PAL)
    write_png(ASSETS / "icon.png", BOTTLE, bottle("a874e8", "6a3cb0", "f0e0ff"))
    dump(ASSETS / "lang" / "en_us.json", dict(sorted(lang.items())))
    print("Generated", len(ITEMS), "items, effect icon, recipes and lang")


if __name__ == "__main__":
    main()
