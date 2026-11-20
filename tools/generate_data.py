#!/usr/bin/env python3
"""Generates Wonder Realms' data and text: the advancements (with their sky background), the chest loot tables
for sky islands, the dragon entity tag, and every line of text that isn't an item, block or entity name (chat
and overlay messages, item tooltips, the airship hold, advancement titles).

Run from the wonder-realms folder after the other generators (generate_assets.py starts the lang file afresh):
    python3 tools/generate_data.py
"""
import json
from pathlib import Path

from generate_dragon_assets import shade, write_png, noise

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"

DRAGONS = [f"{NS}:{element}_dragon" for element in ("ember", "frost", "storm", "verdant", "ender")]

# --- Text ------------------------------------------------------------------------------------------------------

TEXT = {
    "block.wonderrealms.pixelated_block": "Carved Block",
    "container.wonderrealms.airship": "Airship Hold",
    "message.wonderrealms.airship_fuel": "Fuel: %sm %ss",
    "message.wonderrealms.airship_no_fuel": "Out of fuel! Use coal on the airship.",
    "message.wonderrealms.outpost_held": "The pillagers still hold this outpost. Drive them out first!",
    "message.wonderrealms.too_young": "This dragon is too young to ride. Feed it to help it grow.",
    "message.wonderrealms.dragon_sits": "Your dragon settles down to wait.",
    "message.wonderrealms.dragon_follows": "Your dragon follows you.",
    "message.wonderrealms.whistle_come": "Your dragons heard you! (%s on the way)",
    "message.wonderrealms.whistle_sit": "Your dragons settle down to wait. (%s)",
    "message.wonderrealms.whistle_unheard": "No dragon of yours is close enough to hear.",
    "message.wonderrealms.cannon_no_fuel": "The cannon needs fuel: each shot burns ten seconds of it.",
}

TOOLTIPS = {
    "shrinking_draught": ["Shrinks you to a tenth of your size for five minutes.",
                          "Tiny, you climb walls, spot dewdrops and can carve single pixels."],
    "growth_draught": ["Brings you back to your full size."],
    "dewdrop": ["Only tiny eyes can find these in the grass.",
                "Pixel Miners pay well for them."],
    "gem_pixel": ["Nine of them craft into a whole gem."],
    "airship": ["Place it on the ground, then fuel it with coal, charcoal or blaze rods.",
                "WASD steers, jump climbs, sprint descends.",
                "Sneak and use it empty-handed to open the cargo hold.",
                "Use dye on it to color the balloon."],
    "airship_cannon": ["Use on your airship to mount it at the bow.",
                       "At the helm, press V to fire. Each shot burns some fuel.",
                       "Planks patch a hull holed by cannon fire."],
    "dragon_armor": ["Use on your tamed dragon to armor it.",
                     "Shears take it off again."],
    "dragon_whistle": ["Calls every dragon of yours within earshot.",
                       "Sneak and use it to tell them to sit and wait."],
    "shrink_ray": ["Zap a mob to shrink it for 30 seconds.",
                   "Shrunken mobs can barely hurt you."],
    "pixel_chisel": ["Hold it to carve blocks pixel by pixel,",
                     "even at full size."],
    "dragon_egg": ["Set it down and wait: it hatches into a dragon",
                   "that bonds with whoever is closest."],
    "ender_egg": ["Something stirs inside... perhaps it's hungry for chorus fruit?"],
}

# --- Advancements ----------------------------------------------------------------------------------------------
# id: (parent, icon, title, description, frame, criteria). A criteria of None means the mod's code awards it
# (an impossible criterion named "done", granted by WonderAdvancements).


def entity(predicate):
    return {"type": "minecraft:entity_properties", "entity": "this", "predicate": predicate}


ADVANCEMENTS = {
    "root": (None, f"{NS}:shrinking_draught", "Wonder Realms", "Shrink to the size of a bug, sail the skies, and tame a dragon",
             "task", {"joined": {"trigger": "minecraft:tick"}}),

    # Tiny Worlds
    "shrink": ("root", f"{NS}:shrinking_draught", "Honey, I Shrunk Myself", "Drink a Shrinking Draught", "task",
               {"shrunk": {"trigger": "minecraft:effects_changed", "conditions": {"effects": {f"{NS}:shrunken": {}}}}}),
    "dewdrop": ("shrink", f"{NS}:dewdrop", "Morning Dew", "Find a dewdrop in the grass while tiny, and drink it", "task",
                {"drank": {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": f"{NS}:dewdrop"}}}}),
    "pixel_trade": ("dewdrop", f"{NS}:emerald_pixel", "Small Business", "Trade a dewdrop with a Pixel Miner", "task", None),
    "pixel_gem": ("pixel_trade", f"{NS}:diamond_pixel", "Nine Pixels Make a Gem", "Craft a whole diamond from Diamond Pixels", "goal",
                  {"crafted": {"trigger": "minecraft:recipe_crafted", "conditions": {"recipes": f"{NS}:diamond_from_pixels"}}}),
    "carve_pixel": ("shrink", "minecraft:iron_pickaxe", "Pixel Perfect", "While tiny, carve a single pixel out of a block", "task", None),
    "giant_slayer": ("shrink", "minecraft:spider_eye", "Giant Slayer", "Defeat a bug while you're tiny", "goal", None),

    # Dragons
    "find_egg": ("root", f"{NS}:ember_egg", "Here Be Dragons", "Win a dragon egg from a sky island dragon", "task",
                 {"egg": {"trigger": "minecraft:inventory_changed",
                          "conditions": {"items": [{"items": [f"{NS}:ember_egg", f"{NS}:frost_egg", f"{NS}:storm_egg", f"{NS}:verdant_egg"]}]}}}),
    "hatch_dragon": ("find_egg", f"{NS}:frost_egg", "Hatchling", "Hatch a dragon egg", "task", None),
    "hatch_ender_dragon": ("hatch_dragon", "minecraft:dragon_egg", "The Dragon Reborn",
                           "Hatch an Ender Dragon by feeding the End's Dragon Egg chorus fruit", "challenge", None),
    "tame_dragon": ("root", "minecraft:lead", "Dragon Tamer", "Tame or hatch a dragon", "task",
                    {"tamed": {"trigger": "minecraft:tame_animal",
                               "conditions": {"entity": entity({"minecraft:entity_type": f"#{NS}:dragons"})}}}),
    "ride_dragon": ("tame_dragon", "minecraft:saddle", "Dragon Rider", "Saddle up and ride your grown dragon", "task",
                    {"rode": {"trigger": "minecraft:started_riding",
                              "conditions": {"player": entity({"minecraft:vehicle": {"minecraft:entity_type": f"#{NS}:dragons"}})}}}),
    "touch_the_sky": ("ride_dragon", "minecraft:elytra", "Touch the Sky", "Fly a dragon higher than y 256", "goal", None),
    "liberate_dragon": ("tame_dragon", "minecraft:iron_bars", "Liberator",
                        "Win over a pillager's dragon once you've conquered their sky outpost", "challenge", None),

    # Sky islands and airships
    "airship": ("root", f"{NS}:airship", "Up, Up and Away", "Climb aboard an airship", "task",
                {"boarded": {"trigger": "minecraft:started_riding",
                             "conditions": {"player": entity({"minecraft:vehicle": {"minecraft:entity_type": f"{NS}:airship"}})}}}),
    "sky_pirate": ("airship", "minecraft:crossbow", "Sky Pirate", "Take the helm of a captured pillager airship", "goal", None),

    # 1.2: more for dragons, Tiny Worlds and airships
    "dragon_armor": ("tame_dragon", f"{NS}:iron_dragon_armor", "Knight of the Sky", "Put armor on your dragon", "task", None),
    "netherite_dragon": ("dragon_armor", f"{NS}:netherite_dragon_armor", "Unbreakable", "Give your dragon netherite armor", "challenge", None),
    "whistle": ("tame_dragon", f"{NS}:dragon_whistle", "Here, Dragon!", "Call your dragons with a Dragon Whistle", "task", None),
    "green_thumb": ("tame_dragon", "minecraft:bone_meal", "Green Thumb", "Make plants grow with a Verdant dragon's breath", "task", None),
    "two_riders": ("ride_dragon", "minecraft:cake", "Room for Two", "Ride a dragon with a friend", "task", None),
    "all_dragons": ("tame_dragon", "minecraft:dragon_head", "Dragon Collector", "Tame every kind of dragon: Ember, Frost, Storm, Verdant and Ender",
                    "challenge", {element: {"trigger": "minecraft:tame_animal",
                                            "conditions": {"entity": entity({"minecraft:entity_type": f"{NS}:{element}_dragon"})}}
                    for element in ("ember", "frost", "storm", "verdant", "ender")}),
    "shrink_mob": ("shrink", f"{NS}:shrink_ray", "Honey, I Shrunk the Mobs", "Shrink a mob with a Shrink Ray", "task", None),
    "tiny_terror": ("shrink_mob", "minecraft:creeper_head", "Tiny Terror", "Shrink a creeper", "goal", None),
    "sculptor": ("carve_pixel", f"{NS}:pixel_chisel", "Sculptor", "Carve a pixel at full size with a Pixel Chisel", "task", None),
    "paint_the_sky": ("airship", "minecraft:light_blue_dye", "Paint the Sky", "Dye an airship's balloon", "task", None),
    "broadside": ("airship", f"{NS}:airship_cannon", "Broadside!", "Fire an airship's cannon", "task", None),
    "shot_down": ("broadside", "minecraft:fire_charge", "Shot Down", "Sink a pillager airship with cannon fire", "challenge", None),
}

# Advancements whose criteria must all be met, rather than any one of them.
ALL_REQUIRED = {"all_dragons"}


def advancement(key, parent, icon, title, description, frame, criteria):
    display = {
        "icon": {"id": icon},
        "title": {"translate": f"advancements.{NS}.{key}.title"},
        "description": {"translate": f"advancements.{NS}.{key}.description"},
    }
    if frame != "task":
        display["frame"] = frame
    if parent is None:
        display.update(background=f"{NS}:gui/advancements/backgrounds/sky", show_toast=False, announce_to_chat=False)
    if criteria is None:
        criteria = {"done": {"trigger": "minecraft:impossible"}}
    requirements = [[name] for name in criteria] if key in ALL_REQUIRED else [list(criteria)]
    result = {"criteria": criteria, "display": display, "requirements": requirements}
    if parent is not None:
        result["parent"] = f"{NS}:{parent}"
    return result


def sky_background():
    """The advancement screen's tile: a deep evening sky with soft drifting clouds, dark enough for the text."""
    sky, cloud = (0x2a, 0x3e, 0x66), (0x4e, 0x66, 0x94)
    img = []
    for y in range(16):
        row = []
        for x in range(16):
            puff = sum(noise((x + dx) // 3, (y + dy) // 2, 41) for dx in (0, 2) for dy in (0, 1)) / 4
            c = cloud if puff > 0.62 else shade(sky, 0.92 + 0.12 * noise(x, y, 5))
            row.append((*c, 255))
        img.append(row)
    return img


# --- Loot ------------------------------------------------------------------------------------------------------


def item(name, weight=1, count=None, enchant=False):
    entry = {"type": "minecraft:item", "name": name if ":" in name else f"minecraft:{name}"}
    if weight != 1:
        entry["weight"] = weight
    if count:
        entry["modifier"] = {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}}
    elif enchant:
        entry["modifier"] = {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}
    return entry


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


def pool(rolls, *entries):
    rolls = rolls if isinstance(rolls, int) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}
    return {"rolls": rolls, "entries": list(entries)}


def chest(name, *pools):
    return {"type": "minecraft:chest", "pools": list(pools), "random_sequence": f"{NS}:chests/{name}"}


LOOT = {
    # A ruined shrine on a wild island: travelling gear, a little treasure, and Tiny Worlds draughts.
    "sky_island": chest("sky_island",
        pool((3, 6),
             item("gold_ingot", 15, (2, 5)), item("iron_ingot", 15, (3, 7)), item("emerald", 8, (1, 4)),
             item("diamond", 4, (1, 2)), item("golden_apple", 6), item("feather", 10, (4, 10)), item("coal", 10, (4, 12)),
             item("bread", 10, (2, 5)), item(f"{NS}:shrinking_draught", 8, (1, 2)), item(f"{NS}:growth_draught", 5),
             item("saddle", 4), item("book", 4, enchant=True), item("name_tag", 3),
             item(f"{NS}:pixel_chisel", 4), item(f"{NS}:dragon_whistle", 3), item(f"{NS}:shrink_ray", 2)),
        pool(1, item(f"{NS}:airship"), empty(11))),
    # What a roost's dragon guards: gold above all, and always a saddle for whoever means to ride.
    "dragon_hoard": chest("dragon_hoard",
        pool((4, 7),
             item("gold_ingot", 20, (3, 8)), item("gold_nugget", 15, (6, 16)), item("raw_gold", 10, (2, 5)),
             item("emerald", 10, (2, 6)), item("diamond", 6, (1, 3)), item("golden_apple", 6), item("golden_carrot", 6, (2, 5)),
             item("lapis_lazuli", 8, (3, 9)), item("amethyst_shard", 8, (2, 6)), item("golden_horse_armor", 3),
             item("bone", 10, (2, 6)), item(f"{NS}:iron_dragon_armor", 4), item(f"{NS}:golden_dragon_armor", 3),
             item(f"{NS}:diamond_dragon_armor", 1)),
        pool(1, item("saddle")),
        pool(1, item("enchanted_golden_apple"), empty(12))),
    # A sky outpost's lookout: pillager supplies, and sometimes a spare airship.
    "sky_outpost": chest("sky_outpost",
        pool(1, item("crossbow"), empty(1)),
        pool((2, 4),
             item("arrow", 10, (6, 16)), item("emerald", 8, (2, 5)), item("iron_ingot", 8, (2, 6)), item("coal", 8, (4, 12)),
             item("dark_oak_log", 5, (2, 4)), item("string", 8, (2, 6)), item("wheat", 6, (3, 7)), item("golden_apple", 3),
             item(f"{NS}:shrinking_draught", 2), item("saddle", 3)),
        pool(1, item(f"{NS}:airship"), empty(4)),
        pool(1, item(f"{NS}:airship_cannon"), empty(5)),
        pool(1, item("book", enchant=True), empty(3))),
}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang.update(TEXT)
    for key, lines in TOOLTIPS.items():
        for i, line in enumerate(lines, 1):
            lang[f"tooltip.{NS}.{key}.{i}"] = line

    for key, (parent, icon, title, description, frame, criteria) in ADVANCEMENTS.items():
        dump(DATA / "advancement" / f"{key}.json", advancement(key, parent, icon, title, description, frame, criteria))
        lang[f"advancements.{NS}.{key}.title"] = title
        lang[f"advancements.{NS}.{key}.description"] = description
    write_png(ASSETS / "textures" / "gui" / "advancements" / "backgrounds" / "sky.png", sky_background())

    for name, table in LOOT.items():
        dump(DATA / "loot_table" / "chests" / f"{name}.json", table)
    dump(DATA / "tags" / "entity_type" / "dragons.json", {"values": DRAGONS})

    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print(f"Wrote {len(ADVANCEMENTS)} advancements, {len(LOOT)} loot tables, the dragon tag and {len(lang)} lang entries")


if __name__ == "__main__":
    main()
