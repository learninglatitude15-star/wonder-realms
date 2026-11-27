#!/usr/bin/env python3
"""Generates the Overcloud, the realm above the sky (Wonder Realms 2.0): its dimension and dimension type, the
terrain (floating continents over a sea of clouds), its biomes, the islands placed in it, the Cloud block and the
Storm Eye (textures, models, loot, recipe) and their names.

Run from the wonder-realms folder after generate_gear_assets.py:  python3 tools/generate_overcloud.py
Set WR_RESOURCES to write into another resources folder (a preview copy) instead of src/main/resources.
"""
import json
import math
import os
from pathlib import Path

import item_art as art
from generate_dragon_assets import write_png
from texkit import h2, mix, tone

ROOT = Path(os.environ.get("WR_RESOURCES") or Path(__file__).resolve().parent.parent / "src" / "main" / "resources")
ASSETS = ROOT / "assets" / "wonderrealms"
DATA = ROOT / "data" / "wonderrealms"
NS = "wonderrealms"

# The world runs from y 48 to y 320. Below y 64 (sea level) every empty block is cloud: the cloud sea, sixteen blocks
# deep, with nothing under it. The continents float from about y 80 to y 230; the placed islands drift above them.
MIN_Y, HEIGHT, SEA_LEVEL = 48, 272, 64


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


# --- The dimension ----------------------------------------------------------------------------------------------------

def dimension_type():
    """Day and night and the Overworld's music, a bright sky, and a high layer of ordinary clouds above."""
    return {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:audio/ambient_sounds": {"mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave",
                                                        "tick_delay": 6000}},
            "minecraft:audio/background_music": {
                "creative": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.creative"},
                "default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game"},
            },
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark",
                                            "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
            "minecraft:gameplay/nether_portal_spawns_piglin": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/ambient_light_color": "#0a0a0a",
            "minecraft:visual/cloud_color": "#ccffffff",
            "minecraft:visual/cloud_height": 300.0,
            "minecraft:visual/fog_color": "#e1f1ff",
            "minecraft:visual/sky_color": "#8cc8ff",
        },
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:overworld",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_skylight": True,
        "height": HEIGHT,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": HEIGHT,
        "min_y": MIN_Y,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
        "timelines": "#minecraft:in_overworld",
    }


# Island shaping. The End's island noise alone fills the whole band with land, so it is pulled down: hardest at the
# top and bottom of the band (islands come out lens-shaped, thickest near y 150), and wherever a slow 2D noise says
# open sky (so archipelagos are parted by wide gulfs of air over the clouds).
BAND_BOTTOM, BAND_MIDDLE, BAND_TOP, BAND_PULL = 88, 150, 236, -1.3
GULF_SCALE, GULF_STRENGTH, GULF_BIAS = 1.6, 0.9, -0.35
# The biomes come from the Overworld's climate noise, sampled twelve times finer, so they are a few hundred blocks across.
CLIMATE_SCALE = 3.0


def climate(noise):
    return {"type": "minecraft:noise", "noise": noise, "shift_x": "minecraft:shift_x", "shift_z": "minecraft:shift_z",
            "xz_scale": CLIMATE_SCALE, "y_scale": 0.0}


def material_rule():
    """The Overworld's floating-island surfaces (grass over dirt over stone, and ores), only above the clouds: the
    cloud sea is the dimension's 'fluid', but grass would otherwise grow on top of it."""
    return {"type": "minecraft:condition",
            "if_true": {"type": "minecraft:y_above", "add_stone_depth": False, "anchor": {"absolute": SEA_LEVEL + 2},
                        "surface_depth_multiplier": 0},
            "then_run": "minecraft:overworld_floating_islands"}


def noise_settings():
    """Floating islands in a band from y 88 to 236 over the cloud sea: the End's island noise, pulled in at the top and
    bottom of the band and across wide gulfs. The sea level fills everything empty below y 64 with cloud."""
    def gradient(y0, v0, y1, v1):
        return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "from_value": v0, "to_coordinate": y1, "to_value": v1}

    lens = {"type": "minecraft:min", "left": gradient(BAND_BOTTOM, BAND_PULL, BAND_MIDDLE, 0.0),
            "right": gradient(BAND_MIDDLE, 0.0, BAND_TOP, BAND_PULL)}
    gulfs = {"type": "minecraft:add", "left": GULF_BIAS, "right": {"type": "minecraft:mul", "left": GULF_STRENGTH, "right": {
        "type": "minecraft:noise", "noise": "minecraft:continentalness", "shift_x": "minecraft:shift_x", "shift_z": "minecraft:shift_z",
        "xz_scale": GULF_SCALE, "y_scale": 0.0}}}
    shaped = {"type": "minecraft:add", "left": "minecraft:end/base_3d_noise", "right": {"type": "minecraft:add", "left": lens, "right": gulfs}}
    below = gradient(BAND_BOTTOM - 16, 0.0, BAND_BOTTOM, 1.0)
    density = {"type": "minecraft:add", "left": {"type": "minecraft:squeeze", "input": {
        "type": "minecraft:interpolated", "cell_size_xz": 8, "cell_size_y": 4, "input": {
            "type": "minecraft:mul", "right": 0.64, "left": {"type": "minecraft:blend_density", "input": {
                "type": "minecraft:lerp", "alpha": below, "first": -0.234375, "second": shaped}}}}},
        "right": {"type": "minecraft:beardifier"}}
    router = {"chunk_surface_level": 0.0, "continents": 0.0, "depth": 0.0, "erosion": 0.0, "final_density": density, "ridges": 0.0,
              "temperature": climate("minecraft:temperature"), "vegetation": climate("minecraft:vegetation")}
    return {
        "debug_functions": [],
        "default_block": "minecraft:stone",
        "default_fluid": f"{NS}:cloud",
        "disable_mob_generation": False,
        "legacy_random_source": True,
        "material_rule": f"{NS}:overcloud",
        "noise": {"height": HEIGHT, "min_y": MIN_Y},
        "noise_router": router,
        "sea_level": SEA_LEVEL,
        "spawn_target": [],
    }


# --- Biomes -----------------------------------------------------------------------------------------------------------
# Within each generation step every biome takes its features in this one order (a biome lists the ones it uses), so
# no two biomes can disagree about which comes first.
STEPS = 11
ORDER = {
    2: [f"{NS}:overcloud_geode"],
    4: [f"{NS}:overcloud_island"],
    6: ["minecraft:ore_coal_upper", "minecraft:ore_iron_upper", "minecraft:ore_copper", "minecraft:ore_emerald", "minecraft:ore_diamond"],
    8: ["minecraft:spring_water"],
    9: ["minecraft:glow_lichen", "minecraft:trees_plains", "minecraft:trees_meadow", "minecraft:trees_cherry", "minecraft:trees_windswept_hills",
        "minecraft:trees_savanna", "minecraft:trees_birch", "minecraft:flower_meadow", "minecraft:flower_cherry",
        "minecraft:wildflowers_meadow", "minecraft:forest_flowers", "minecraft:patch_grass_meadow", "minecraft:patch_grass_savanna",
        "minecraft:patch_grass_normal", "minecraft:patch_tall_grass", "minecraft:patch_dead_bush", "minecraft:patch_pumpkin"],
}
EVERYWHERE = {f"{NS}:overcloud_island", "minecraft:ore_coal_upper", "minecraft:ore_iron_upper", "minecraft:ore_copper",
              "minecraft:ore_emerald", "minecraft:ore_diamond", "minecraft:spring_water"}


def spawn(mob, weight, low=1, high=1):
    count = low if low == high else {"type": "minecraft:uniform", "min_inclusive": low, "max_inclusive": high}
    return {"type": mob, "count": count, "weight": weight}


def dragons(**weights):
    return [spawn(f"{NS}:{kind}_dragon", weight) for kind, weight in weights.items()]


BIOMES = {
    # The commonest: golden-green meadows, grazing flocks, sky villages, and Sun and Nimbus dragons.
    "sunlit_meadows": dict(
        name="Sunlit Meadows", temperature=0.7, downfall=0.8, rain=True, sky="#8cc8ff", fog="#e1f1ff", water="#5bb6ff",
        climate=([-0.2, 0.55], [-1.0, 0.3]),
        features={"minecraft:trees_plains", "minecraft:trees_meadow", "minecraft:flower_meadow", "minecraft:wildflowers_meadow",
                  "minecraft:patch_grass_meadow", "minecraft:patch_tall_grass", "minecraft:patch_pumpkin"},
        creatures=[spawn("minecraft:sheep", 12, 2, 4), spawn("minecraft:cow", 8, 2, 4), spawn("minecraft:chicken", 10, 2, 4),
                   spawn("minecraft:rabbit", 4, 2, 3)] + dragons(sun=2, nimbus=1, zephyr=2, harvest=1, tiger=1, fang=1, chorus=1,
                                                                 melody=1, venom=1, mud=1),
        particles=None),
    # Dark, wind-bent heights under a grey sky, crackling with sparks: Storm, Nimbus and Pearl dragons nest here.
    "storm_heights": dict(
        name="Storm Heights", temperature=0.2, downfall=0.9, rain=True, sky="#6b78a6", fog="#9aa6c4", water="#3d57a3",
        climate=([-0.2, 0.55], [0.3, 1.0]),
        features={"minecraft:glow_lichen", "minecraft:trees_windswept_hills", "minecraft:patch_grass_normal", "minecraft:patch_dead_bush"},
        creatures=[spawn("minecraft:goat", 6, 1, 3)] + dragons(storm=3, nimbus=2, pearl=2, hail=2, phantom=2, ghast=1, void=1,
                                                               clockwork=1, redstone=1, shulker=1, abyss=1),
        particles=("minecraft:electric_spark", 0.0015)),
    # Cold, shimmering country of amethyst: geodes in the cliffs, pink cherry groves, and Crystal, Frost, Aurora, Moon and
    # Star dragons.
    "crystal_reaches": dict(
        name="Crystal Reaches", temperature=0.4, downfall=0.4, rain=True, sky="#b7a7ff", fog="#eadfff", water="#8f7dff",
        climate=([-1.0, -0.2], [-1.0, 1.0]),
        features={f"{NS}:overcloud_geode", "minecraft:glow_lichen", "minecraft:trees_cherry", "minecraft:flower_cherry",
                  "minecraft:patch_grass_normal"},
        creatures=[spawn("minecraft:rabbit", 4, 2, 3)] + dragons(crystal=3, frost=2, aurora=2, moon=2, star=1, pearl=1, prism=2, ice=1,
                                                                 snow=1, glow=1, nebula=1, warped=1, dripstone=1),
        particles=("minecraft:end_rod", 0.0008)),
    # Warm, dry peaks under a sunset haze where dragons of every kind gather, the Phoenix and Gold dragons among them.
    "dragon_peaks": dict(
        name="Dragon Peaks", temperature=1.0, downfall=0.2, rain=False, sky="#9fc1ff", fog="#ffd9b8", water="#4fb0d0",
        climate=([0.55, 1.0], [-1.0, 1.0]),
        features={"minecraft:trees_savanna", "minecraft:patch_grass_savanna", "minecraft:flower_meadow"},
        creatures=[spawn("minecraft:llama", 3, 2, 4)] + dragons(ember=3, earth=3, honey=2, verdant=2, air=2, rainbow=1, phoenix=2, gold=2,
                                                               star=1, eclipse=2, magma=1, blaze=1, basalt=1, soul=1, crimson=1, sand=1),
        particles=("minecraft:white_ash", 0.0015)),
}


def biome(spec):
    attributes = {
        "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {"creature": spec["creatures"]}},
                                                  "modifier": "overlay"},
        "minecraft:visual/fog_color": spec["fog"],
        "minecraft:visual/sky_color": spec["sky"],
    }
    if spec["particles"]:
        particle, chance = spec["particles"]
        attributes["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle}, "probability": chance}],
                                                            "modifier": "append"}
    uses = spec["features"] | EVERYWHERE
    return {
        "attributes": attributes,
        "carvers": [],
        "downfall": spec["downfall"],
        "effects": {"water_color": spec["water"]},
        "features": [[f for f in ORDER.get(step, []) if f in uses] for step in range(STEPS)],
        "has_precipitation": spec["rain"],
        "temperature": spec["temperature"],
    }


def dimension():
    full = [-1.0, 1.0]
    biomes = [{"biome": f"{NS}:{key}", "parameters": {
        "temperature": spec["climate"][0], "humidity": spec["climate"][1], "continentalness": full, "erosion": full,
        "weirdness": full, "depth": full, "offset": 0.0}} for key, spec in BIOMES.items()]
    return {"type": f"{NS}:overcloud", "generator": {
        "type": "minecraft:noise", "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}, "settings": f"{NS}:overcloud"}}


# The sky islands, villages, citadels and roosts of the Overcloud: the Overworld's feature, far more often (and the
# feature itself floats them higher and favours the grander kinds there).
ISLANDS = {"feature": f"{NS}:sky_island", "placement": [{"type": "minecraft:rarity_filter", "chance": 28}, {"type": "minecraft:in_square"},
                                                         {"type": "minecraft:biome"}]}


# Vanilla's geodes sit just above the world's floor, which here is the bottom of the cloud sea: these are the same
# geodes, set inside the floating land instead (a geode only forms where it is wrapped in solid ground, so these turn
# up in the islands' cliffs and bellies, never hanging in the open air).
GEODES = {"feature": "minecraft:amethyst_geode", "placement": [
    {"type": "minecraft:rarity_filter", "chance": 5}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 96},
                                                  "max_inclusive": {"absolute": 224}}},
    {"type": "minecraft:biome"}]}


# --- Blocks and items -------------------------------------------------------------------------------------------------

def tiled(x, y, seed, cells, size=16):
    """Smooth noise that wraps around a size-by-size tile, so a block texture has no seams."""
    fx, fy = x * cells / size, y * cells / size
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    g = lambda i, j: h2(i % cells, j % cells, seed)
    a, b, c, d = g(x0, y0), g(x0 + 1, y0), g(x0, y0 + 1), g(x0 + 1, y0 + 1)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def cloud_texture():
    """Soft white puffs with faint blue-grey hollows between them, seamless when tiled across the cloud sea."""
    light, shadow = (0xfb, 0xfd, 0xff), (0xd2, 0xdd, 0xec)
    img = []
    for y in range(16):
        row = []
        for x in range(16):
            n = tiled(x, y, 71, 4) * 0.65 + tiled(x, y, 73, 8) * 0.35
            c = mix(shadow, light, min(1.0, max(0.0, (n - 0.3) * 2.2)))
            if n > 0.72:
                c = tone(c, 0.3)
            row.append((*c, 255))
        img.append(row)
    return img


def storm_eye_icon():
    """A storm caught in an orb: deep indigo cloud swirling round a golden dragon's eye with a black slit, under a dark
    lid, lit from the upper left with a glint on the orb and one in the eye."""
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            half = 3.4 * (1 - (dx / 4.9) ** 2)  # half the almond eye's height in this column
            if d > 6.7:
                row += "."
            elif abs(dy) <= half - 0.35:
                row += "p" if abs(dx) < 1 and abs(dy) < 2.6 else "I"
            elif abs(dy) <= half + 0.75 and abs(dx) < 5.6:
                row += "k"
            else:
                band = math.sin(2 * math.atan2(dy, dx) - d * 1.05)
                row += "s" if band > 0.55 and d > 3.2 else "S" if band > 0.1 and d > 3.2 else "O"
        rows.append(row)
    for x, y in ((4, 3), (6, 6)):
        rows[y] = rows[y][:x] + "g" + rows[y][x + 1:]
    return art.sprite(rows, {"O": art.mat((0x3c, 0x34, 0x86), -0.65, 0.45), "S": art.flat((0x6a, 0x6c, 0xc0)),
                             "s": art.flat((0xc4, 0xcc, 0xf4)), "k": art.flat((0x22, 0x1a, 0x4a)),
                             "I": art.mat((0xf2, 0xb8, 0x2c), -0.55, 0.55), "p": art.flat((0x14, 0x0c, 0x24)),
                             "g": art.flat((0xff, 0xff, 0xff))},
                      overlay="sSkpg", after="g")


def main():
    dump(DATA / "dimension_type" / "overcloud.json", dimension_type())
    dump(DATA / "dimension" / "overcloud.json", dimension())
    dump(DATA / "worldgen" / "noise_settings" / "overcloud.json", noise_settings())
    dump(DATA / "worldgen" / "material_rule" / "overcloud.json", material_rule())
    for key, spec in BIOMES.items():
        dump(DATA / "worldgen" / "biome" / f"{key}.json", biome(spec))
    dump(DATA / "worldgen" / "placed_feature" / "overcloud_island.json", ISLANDS)
    dump(DATA / "worldgen" / "placed_feature" / "overcloud_geode.json", GEODES)

    # The Cloud block: a plain cube, dropping itself.
    write_png(ASSETS / "textures" / "block" / "cloud.png", cloud_texture())
    dump(ASSETS / "blockstates" / "cloud.json", {"variants": {"": {"model": f"{NS}:block/cloud"}}})
    dump(ASSETS / "models" / "block" / "cloud.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/cloud"}})
    dump(ASSETS / "items" / "cloud.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/cloud"}})
    dump(DATA / "loot_table" / "blocks" / "cloud.json", {"type": "minecraft:block", "random_sequence": f"{NS}:blocks/cloud", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:cloud"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    # The Storm Eye: dropped by the Storm Leviathan, or made from Elder Scales round a Heart of the Sea.
    write_png(ASSETS / "textures" / "item" / "storm_eye.png", storm_eye_icon())
    dump(ASSETS / "models" / "item" / "storm_eye.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/storm_eye"}})
    dump(ASSETS / "items" / "storm_eye.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/storm_eye"}})
    dump(DATA / "recipe" / "storm_eye.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" S ", "SHS", " S "],
                                              "key": {"S": f"{NS}:elder_scale", "H": "minecraft:heart_of_the_sea"},
                                              "result": {"id": f"{NS}:storm_eye", "count": 1}})

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text())
    lang.update({f"block.{NS}.cloud": "Cloud", f"item.{NS}.storm_eye": "Storm Eye", f"dimension.{NS}.overcloud": "The Overcloud"})
    lang.update({f"biome.{NS}.{key}": spec["name"] for key, spec in BIOMES.items()})
    lang_path.write_text(json.dumps(dict(sorted(lang.items())), indent=2) + "\n")
    print(f"Wrote the Overcloud: dimension, terrain, {len(BIOMES)} biomes, islands, the Cloud block and the Storm Eye")


if __name__ == "__main__":
    main()
