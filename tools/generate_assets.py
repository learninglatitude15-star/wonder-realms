#!/usr/bin/env python3
"""Generates Wonder Realms textures, item models, item definitions, recipes and lang entries.

Run from the wonder-realms folder:  python3 tools/generate_assets.py
The icons are drawn by item_art.py (no image library needed).
"""
import json
import os
import struct
import zlib
from pathlib import Path

import item_art as art

ROOT = Path(os.environ.get("WR_RESOURCES") or Path(__file__).resolve().parent.parent / "src" / "main" / "resources")
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"


def save_png(path, img):
    """Writes an RGBA image (rows of (r, g, b, a) tuples)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in img)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(img[0]), len(img), 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


SHRINKING = ((0xa8, 0x74, 0xe8), (0xf0, 0xe0, 0xff))
ITEMS = {
    "shrinking_draught": ("Shrinking Draught", lambda: art.bottle_icon(*SHRINKING)),
    "growth_draught": ("Growth Draught", lambda: art.bottle_icon((0x74, 0xd0, 0x60), (0xe8, 0xff, 0xd8))),
    "dewdrop": ("Dewdrop", art.dewdrop_icon),
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
    for item, (name, draw) in ITEMS.items():
        save_png(ASSETS / "textures" / "item" / f"{item}.png", draw())
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
    save_png(ASSETS / "textures" / "mob_effect" / "shrunken.png", art.shrunken_icon())
    save_png(ASSETS / "icon.png", art.bottle_icon(*SHRINKING))
    dump(ASSETS / "lang" / "en_us.json", dict(sorted(lang.items())))
    print("Generated", len(ITEMS), "items, effect icon, recipes and lang")


if __name__ == "__main__":
    main()
