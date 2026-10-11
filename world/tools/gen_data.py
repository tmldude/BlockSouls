#!/usr/bin/env python3
"""Writes world's data + asset JSON (worldgen, loot, recipes, tags, enchantments, models, lang).

    python3 world/tools/gen_data.py

Hand-editing the JSON is fine too; this just keeps the repetitive files consistent. Formats follow vanilla 26.3
(loot "modifier"/"condition", worldgen "feature" folder, item model definitions in assets/<ns>/items).
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
CLIENT = os.path.join(os.path.dirname(__file__), "..", "src", "client", "resources")
NS = "blocksouls_world"
DATA = os.path.join(ROOT, "data", NS)
ASSETS = os.path.join(CLIENT, "assets", NS)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def ns(x):
    return x if ":" in x else f"{NS}:{x}"


# ---------------------------------------------------------------- worldgen

def ore_target(state, tag):
    return {"state": state, "target": {"predicate_type": "minecraft:tag_match", "tag": tag}}


write(f"{DATA}/worldgen/feature/ore_gunmetal.json", {
    "type": "minecraft:ore",
    "discard_chance_on_air_exposure": 0.0,
    "size": 7,
    "targets": [
        ore_target(f"{NS}:gunmetal_ore", "minecraft:stone_ore_replaceables"),
        ore_target(f"{NS}:deepslate_gunmetal_ore", "minecraft:deepslate_ore_replaceables"),
    ],
})
write(f"{DATA}/worldgen/placed_feature/ore_gunmetal.json", {
    "feature": f"{NS}:ore_gunmetal",
    "placement": [
        {"type": f"{NS}:config_count", "key": "gunmetal_ore"},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {
            "type": "minecraft:trapezoid", "min_inclusive": {"absolute": -32}, "max_inclusive": {"absolute": 48}}},
        {"type": "minecraft:biome"},
    ],
})

for name, key in (("wreck", "wreck"), ("checkpoint", "checkpoint"), ("scrap_piles", "scrap_pile")):
    write(f"{DATA}/worldgen/feature/{name}.json", {"type": f"{NS}:{name}"})
    write(f"{DATA}/worldgen/placed_feature/{name}.json", {
        "feature": f"{NS}:{name}",
        "placement": [
            {"type": f"{NS}:config_rarity", "key": key},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
            {"type": "minecraft:biome"},
        ],
    })

write(f"{DATA}/worldgen/feature/blight_patch.json", {"type": f"{NS}:blight_patch", "radius": 4})
for density in ("dense", "sparse"):
    write(f"{DATA}/worldgen/placed_feature/blight_patch_{density}.json", {
        "feature": f"{NS}:blight_patch",
        "placement": [
            {"type": f"{NS}:config_count", "key": f"blight_{density}"},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {
                "type": "minecraft:uniform", "min_inclusive": {"above_bottom": 8}, "max_inclusive": {"below_top": 8}}},
            {"type": "minecraft:biome"},
        ],
    })

# ---------------------------------------------------------------- loot

def item(name, count=None, weight=None, modifiers=None, condition=None):
    e = {"type": "minecraft:item", "name": name}
    mods = list(modifiers or [])
    if count is not None:
        if isinstance(count, tuple):
            mods.insert(0, {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
        else:
            mods.insert(0, {"type": "minecraft:set_count", "count": count})
    if mods:
        e["modifier"] = mods if len(mods) > 1 else mods[0]
    if weight is not None:
        e["weight"] = weight
    if condition is not None:
        e["condition"] = condition
    return e


def pool(entries, rolls=1, condition=None):
    p = {"entries": entries, "rolls": rolls}
    if condition is not None:
        p["condition"] = condition
    return p


def tag(path, values):
    write(path, {"values": values})


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def block_table(name, pools):
    write(f"{DATA}/loot_table/blocks/{name}.json",
          {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{name}"})


SURVIVES = {"type": "minecraft:survives_explosion"}

for b in ("raw_gunmetal_block", "gunmetal_block", "blighted_netherrack"):
    block_table(b, [pool([item(f"{NS}:{b}")], condition=SURVIVES)])

for ore in ("gunmetal_ore", "deepslate_gunmetal_ore"):
    block_table(ore, [pool([{
        "type": "minecraft:alternatives",
        "children": [
            item(f"{NS}:{ore}", condition="minecraft:tool/can_silk_touch"),
            item(f"{NS}:raw_gunmetal", modifiers=[
                {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"type": "minecraft:explosion_decay"},
            ]),
        ],
    }])])

block_table("scrap_pile", [pool([item(f"{NS}:scrap", count=(1, 3), modifiers=[{"type": "minecraft:explosion_decay"}])])])
block_table("blight_heart", [pool([item(f"{NS}:blight_core")], condition=SURVIVES)])
for plant in ("blight_growth", "blight_veins"):
    block_table(plant, [pool([item(f"{NS}:{plant}", condition="minecraft:tool/can_shear")])])

BOOK = lambda weight: item("minecraft:book", weight=weight, modifiers=[
    {"type": "minecraft:enchant_randomly", "options": f"#{NS}:wacky"}])


def tag_entry(t, weight, count=None):
    e = {"type": "minecraft:tag", "items": f"#{NS}:{t}", "expand": True, "weight": weight}
    if count is not None:
        e["modifier"] = {"type": "minecraft:set_count", "count": uniform(*count)}
    return e


# Other modules' items come in through our own tags with optional entries, so these tables load (and the tests
# pass) whether or not guns / vehicles are installed. Guns themselves are added in code (WorldLoot) because their
# roll_perks function only exists when guns is loaded.
write(f"{DATA}/loot_table/chests/wreck.json", {
    "type": "minecraft:chest",
    "pools": [
        pool([
            item(f"{NS}:scrap", count=(2, 6), weight=30),
            item(f"{NS}:raw_gunmetal", count=(1, 3), weight=12),
            item(f"{NS}:gunmetal_nugget", count=(2, 7), weight=12),
            item("minecraft:iron_ingot", count=(1, 3), weight=10),
            item("minecraft:iron_nugget", count=(3, 9), weight=10),
            item("minecraft:bread", count=(1, 3), weight=10),
            item("minecraft:cooked_beef", count=(1, 2), weight=6),
            item("minecraft:dried_kelp", count=(2, 6), weight=6),
            item("minecraft:coal", count=(1, 4), weight=8),
        ], rolls=uniform(3, 6)),
        pool([
            {"type": "minecraft:empty", "weight": 10},
            tag_entry("loot/ammo", 6, count=(4, 12)),
            tag_entry("loot/vehicle_wheels", 4),
            tag_entry("loot/vehicle_engines", 1),
        ]),
    ],
    "random_sequence": f"{NS}:chests/wreck",
})

write(f"{DATA}/loot_table/chests/checkpoint.json", {
    "type": "minecraft:chest",
    "pools": [
        pool([
            item(f"{NS}:gunmetal_ingot", count=(1, 4), weight=14),
            item(f"{NS}:scrap", count=(2, 5), weight=12),
            item("minecraft:iron_ingot", count=(2, 5), weight=10),
            item("minecraft:gold_ingot", count=(1, 3), weight=4),
            item("minecraft:cooked_porkchop", count=(1, 3), weight=8),
            item("minecraft:golden_apple", weight=2),
            item("minecraft:arrow", count=(4, 12), weight=6),
        ], rolls=uniform(3, 5)),
        pool([
            tag_entry("loot/ammo", 8, count=(8, 20)),
            BOOK(6),
            {"type": "minecraft:empty", "weight": 2},
        ], rolls=uniform(1, 2)),
    ],
    "random_sequence": f"{NS}:chests/checkpoint",
})

tag(f"{DATA}/tags/item/loot/ammo.json", [{"id": "#blocksouls:ammo", "required": False}])
tag(f"{DATA}/tags/item/loot/vehicle_wheels.json", [{"id": "blocksouls_vehicles:wheel", "required": False}])
tag(f"{DATA}/tags/item/loot/vehicle_engines.json", [{"id": "blocksouls_vehicles:engine", "required": False}])
# Guns rolled in structure chests. Ids are the guns brief's names; entries are optional, so a mismatch is just empty.
tag(f"{DATA}/tags/item/loot/guns.json", [{"id": f"blocksouls_guns:{g}", "required": False}
                                          for g in ("hand_cannon", "auto_rifle", "shotgun", "sniper")])

write(f"{DATA}/loot_table/inject/materials.json", {"type": "minecraft:chest", "pools": [pool([
    item(f"{NS}:scrap", count=(1, 4), weight=10),
    item(f"{NS}:gunmetal_nugget", count=(2, 6), weight=6),
    item(f"{NS}:raw_gunmetal", count=(1, 2), weight=4),
    item(f"{NS}:gunmetal_ingot", weight=2),
])]})
write(f"{DATA}/loot_table/inject/wacky_books.json", {"type": "minecraft:chest", "pools": [pool([BOOK(1)])]})
write(f"{DATA}/loot_table/inject/wave_common.json", {"type": "minecraft:entity", "pools": [pool([
    item(f"{NS}:scrap", count=(1, 2), weight=10),
    item(f"{NS}:gunmetal_nugget", count=(1, 3), weight=5),
])]})
write(f"{DATA}/loot_table/inject/wave_rare.json", {"type": "minecraft:entity", "pools": [pool([
    item(f"{NS}:scrap", count=(2, 4), weight=8),
    item(f"{NS}:gunmetal_ingot", weight=5),
    item(f"{NS}:raw_gunmetal", count=(1, 2), weight=4),
])]})
write(f"{DATA}/loot_table/inject/wave_legendary.json", {"type": "minecraft:entity", "pools": [pool([
    item(f"{NS}:gunmetal_ingot", count=(1, 3), weight=8),
    item(f"{NS}:scrap", count=(3, 6), weight=6),
    BOOK(2),
])]})
write(f"{DATA}/loot_table/inject/wave_clear.json", {"type": "minecraft:chest", "pools": [pool([
    item(f"{NS}:scrap", count=(2, 6), weight=10),
    item(f"{NS}:gunmetal_ingot", count=(1, 2), weight=6),
    BOOK(2),
], rolls=uniform(1, 2))]})

# ---------------------------------------------------------------- recipes

def recipe(name, obj):
    write(f"{DATA}/recipe/{name}.json", obj)


for kind, time in (("smelting", 200), ("blasting", 100)):
    for src in ("raw_gunmetal", "gunmetal_ore", "deepslate_gunmetal_ore"):
        recipe(f"gunmetal_ingot_from_{kind}_{src}", {
            "type": f"minecraft:{kind}", "category": "misc", "cookingtime": time, "experience": 0.7,
            "group": "gunmetal_ingot", "ingredient": f"{NS}:{src}", "result": {"id": f"{NS}:gunmetal_ingot"}})


def pack(big, small, name_pack, name_unpack, category="building"):
    recipe(name_pack, {"type": "minecraft:crafting_shaped", "category": category, "key": {"#": f"{NS}:{small}"},
                       "pattern": ["###", "###", "###"], "result": {"id": f"{NS}:{big}"}})
    recipe(name_unpack, {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [f"{NS}:{big}"],
                         "result": {"id": f"{NS}:{small}", "count": 9}})


pack("gunmetal_block", "gunmetal_ingot", "gunmetal_block", "gunmetal_ingot_from_gunmetal_block")
pack("raw_gunmetal_block", "raw_gunmetal", "raw_gunmetal_block", "raw_gunmetal")
pack("gunmetal_ingot", "gunmetal_nugget", "gunmetal_ingot_from_nuggets", "gunmetal_nugget", category="misc")

ARMOR_PATTERNS = {
    "helmet": ["ABA", "A A"],
    "chestplate": ["A A", "ABA", "AAA"],
    "leggings": ["ABA", "A A", "A A"],
    "boots": ["A A", "B B"],
}
for piece, pattern in ARMOR_PATTERNS.items():
    recipe(f"scrap_plate_{piece}", {"type": "minecraft:crafting_shaped", "category": "equipment",
                                    "key": {"A": f"{NS}:scrap", "B": "minecraft:iron_ingot"}, "pattern": pattern,
                                    "result": {"id": f"{NS}:scrap_plate_{piece}"}})
    recipe(f"blight_forged_{piece}", {"type": "minecraft:crafting_shaped", "category": "equipment",
                                      "key": {"A": f"{NS}:gunmetal_ingot", "B": f"{NS}:blight_core"}, "pattern": pattern,
                                      "result": {"id": f"{NS}:blight_forged_{piece}"}})

# ---------------------------------------------------------------- tags

MC_TAGS = os.path.join(ROOT, "data", "minecraft", "tags")
ORES = [f"{NS}:gunmetal_ore", f"{NS}:deepslate_gunmetal_ore"]
STORAGE = [f"{NS}:raw_gunmetal_block", f"{NS}:gunmetal_block"]
tag(f"{MC_TAGS}/block/mineable/pickaxe.json",
    ORES + STORAGE + [f"{NS}:blighted_netherrack", f"{NS}:blight_heart", f"{NS}:scrap_pile"])
tag(f"{MC_TAGS}/block/needs_stone_tool.json", ORES + STORAGE)
tag(f"{MC_TAGS}/block/needs_iron_tool.json", [f"{NS}:blight_heart"])
tag(f"{MC_TAGS}/block/infiniburn_nether.json", [f"{NS}:blighted_netherrack"])
tag(f"{MC_TAGS}/block/base_stone_nether.json", [f"{NS}:blighted_netherrack"])
tag(f"{MC_TAGS}/block/replaceable_by_trees.json", [f"{NS}:blight_growth"])

PIECES = {"helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
for piece, t in PIECES.items():
    tag(f"{MC_TAGS}/item/{t}.json", [f"{NS}:scrap_plate_{piece}", f"{NS}:blight_forged_{piece}"])
tag(f"{MC_TAGS}/item/trimmable_armor.json",
    [f"{NS}:{s}_{p}" for s in ("scrap_plate", "blight_forged") for p in PIECES])
tag(f"{DATA}/tags/item/repairs_scrap_plate_armor.json", [f"{NS}:scrap"])
tag(f"{DATA}/tags/item/repairs_blight_forged_armor.json", [f"{NS}:gunmetal_ingot"])

GUNS = {"id": "#blocksouls:guns", "required": False}
tag(f"{DATA}/tags/item/enchantable/bouncy.json", ["minecraft:bow", "minecraft:crossbow", "minecraft:trident", GUNS])
tag(f"{DATA}/tags/item/enchantable/combustion.json", ["#minecraft:swords", "#minecraft:axes", "minecraft:bow", GUNS])
tag(f"{DATA}/tags/item/enchantable/vampiric.json", ["#minecraft:swords", "#minecraft:axes", GUNS])
tag(f"{DATA}/tags/item/enchantable/grapple.json", ["minecraft:bow", "minecraft:crossbow", GUNS])

WACKY = ["bouncy", "combustion", "vampiric", "grapple", "magnetized", "moonwalk"]
tag(f"{DATA}/tags/enchantment/wacky.json", [f"{NS}:{e}" for e in WACKY])
tag(f"{MC_TAGS}/enchantment/in_enchanting_table.json", [f"{NS}:{e}" for e in WACKY])
tag(f"{MC_TAGS}/enchantment/tooltip_order.json", [f"{NS}:{e}" for e in WACKY])

# ---------------------------------------------------------------- enchantments

def enchantment(name, supported, max_level, slots, weight=2, effects=None, anvil=4, min_base=15, max_base=65):
    obj = {
        "anvil_cost": anvil,
        "description": {"translate": f"enchantment.{NS}.{name}"},
        "max_cost": {"base": max_base, "per_level_above_first": 10},
        "max_level": max_level,
        "min_cost": {"base": min_base, "per_level_above_first": 10},
        "slots": slots,
        "supported_items": supported,
        "weight": weight,
    }
    if effects:
        obj["effects"] = effects
    write(f"{DATA}/enchantment/{name}.json", obj)


enchantment("bouncy", f"#{NS}:enchantable/bouncy", 2, ["mainhand"])
enchantment("combustion", f"#{NS}:enchantable/combustion", 1, ["mainhand"], weight=1, min_base=20)
enchantment("vampiric", f"#{NS}:enchantable/vampiric", 3, ["mainhand"])
enchantment("grapple", f"#{NS}:enchantable/grapple", 1, ["mainhand"], weight=1, min_base=20)
enchantment("magnetized", "#minecraft:enchantable/leg_armor", 2, ["legs"])
enchantment("moonwalk", "#minecraft:enchantable/foot_armor", 2, ["feet"], effects={
    "minecraft:attributes": [
        {"id": f"{NS}:enchantment.moonwalk.gravity", "attribute": "minecraft:gravity",
         "amount": {"type": "minecraft:linear", "base": -0.3, "per_level_above_first": -0.2},
         "operation": "add_multiplied_base"},
        {"id": f"{NS}:enchantment.moonwalk.safe_fall", "attribute": "minecraft:safe_fall_distance",
         "amount": 5.0, "operation": "add_value"},
    ]
})

# ---------------------------------------------------------------- assets

def model(path, obj):
    write(f"{ASSETS}/models/{path}.json", obj)


def item_def(name, model_path):
    write(f"{ASSETS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:{model_path}"}})


CUBES = ["gunmetal_ore", "deepslate_gunmetal_ore", "raw_gunmetal_block", "gunmetal_block", "blighted_netherrack",
         "blight_heart"]
for b in CUBES:
    write(f"{ASSETS}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    model(f"block/{b}", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{b}"}})
    item_def(b, f"block/{b}")

write(f"{ASSETS}/blockstates/scrap_pile.json", {"variants": {"": [
    {"model": f"{NS}:block/scrap_pile"}, {"model": f"{NS}:block/scrap_pile", "y": 90},
    {"model": f"{NS}:block/scrap_pile", "y": 180}, {"model": f"{NS}:block/scrap_pile", "y": 270}]}})
model("block/scrap_pile", {
    "parent": "minecraft:block/block",
    "textures": {"particle": f"{NS}:block/scrap_pile", "pile": f"{NS}:block/scrap_pile"},
    "elements": [
        {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {
            d: {"uv": [1, 13, 15, 16] if d not in ("up", "down") else [1, 1, 15, 15], "texture": "#pile"}
            for d in ("north", "south", "east", "west", "up", "down")}},
        {"from": [3, 3, 2], "to": [12, 6, 11], "faces": {
            d: {"uv": [3, 10, 12, 13] if d not in ("up", "down") else [3, 2, 12, 11], "texture": "#pile"}
            for d in ("north", "south", "east", "west", "up", "down")}},
        {"from": [8, 3, 9], "to": [14, 5, 14], "faces": {
            d: {"uv": [8, 11, 14, 13] if d not in ("up", "down") else [8, 9, 14, 14], "texture": "#pile"}
            for d in ("north", "south", "east", "west", "up")}},
    ],
})
item_def("scrap_pile", "block/scrap_pile")

write(f"{ASSETS}/blockstates/blight_growth.json", {"variants": {"": {"model": f"{NS}:block/blight_growth"}}})
model("block/blight_growth", {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/blight_growth"}})
model("item/blight_growth", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/blight_growth"}})
item_def("blight_growth", "item/blight_growth")

# Veins: multipart like glow lichen (one face model rotated per side).
FACES = {"north": {}, "south": {"y": 180}, "west": {"y": 270}, "east": {"y": 90}, "up": {"x": 270}, "down": {"x": 90}}
parts = []
for face, rot in FACES.items():
    parts.append({"when": {face: "true"}, "apply": dict({"model": f"{NS}:block/blight_veins"}, **rot)})
    parts.append({"when": {"OR": [{d: "false" for d in FACES}]}, "apply": dict({"model": f"{NS}:block/blight_veins"}, **rot)})
write(f"{ASSETS}/blockstates/blight_veins.json", {"multipart": [p for p in parts if "OR" not in p["when"]]})
model("block/blight_veins", {
    "ambientocclusion": False,
    "textures": {"particle": f"{NS}:block/blight_veins", "veins": f"{NS}:block/blight_veins"},
    "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1], "faces": {
        "north": {"uv": [16, 0, 0, 16], "texture": "#veins"}, "south": {"uv": [0, 0, 16, 16], "texture": "#veins"}}}],
})
model("item/blight_veins", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/blight_veins"}})
item_def("blight_veins", "item/blight_veins")

FLAT = ["scrap", "raw_gunmetal", "gunmetal_ingot", "gunmetal_nugget", "blight_core"] + \
       [f"{s}_{p}" for s in ("scrap_plate", "blight_forged") for p in PIECES]
for i in FLAT:
    model(f"item/{i}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{i}"}})
    item_def(i, f"item/{i}")

for s in ("scrap_plate", "blight_forged"):
    write(f"{ASSETS}/equipment/{s}.json", {"layers": {
        "humanoid": [{"texture": f"{NS}:{s}"}],
        "humanoid_leggings": [{"texture": f"{NS}:{s}"}],
    }})

LANG = {
    "itemGroup.blocksouls_world.world": "BlockSouls: World",
    f"item.{NS}.scrap": "Scrap",
    f"item.{NS}.raw_gunmetal": "Raw Gunmetal",
    f"item.{NS}.gunmetal_ingot": "Gunmetal Ingot",
    f"item.{NS}.gunmetal_nugget": "Gunmetal Nugget",
    f"item.{NS}.blight_core": "Blight Core",
    f"block.{NS}.gunmetal_ore": "Gunmetal Ore",
    f"block.{NS}.deepslate_gunmetal_ore": "Deepslate Gunmetal Ore",
    f"block.{NS}.raw_gunmetal_block": "Block of Raw Gunmetal",
    f"block.{NS}.gunmetal_block": "Block of Gunmetal",
    f"block.{NS}.scrap_pile": "Scrap Pile",
    f"block.{NS}.blighted_netherrack": "Blighted Netherrack",
    f"block.{NS}.blight_growth": "Blight Growth",
    f"block.{NS}.blight_veins": "Blight Veins",
    f"block.{NS}.blight_heart": "Blight Heart",
    f"item.{NS}.scrap_plate_helmet": "Scrap-Plate Helmet",
    f"item.{NS}.scrap_plate_chestplate": "Scrap-Plate Chestplate",
    f"item.{NS}.scrap_plate_leggings": "Scrap-Plate Leggings",
    f"item.{NS}.scrap_plate_boots": "Scrap-Plate Boots",
    f"item.{NS}.blight_forged_helmet": "Blight-Forged Helmet",
    f"item.{NS}.blight_forged_chestplate": "Blight-Forged Chestplate",
    f"item.{NS}.blight_forged_leggings": "Blight-Forged Leggings",
    f"item.{NS}.blight_forged_boots": "Blight-Forged Boots",
    f"enchantment.{NS}.bouncy": "Bouncy",
    f"enchantment.{NS}.combustion": "Combustion",
    f"enchantment.{NS}.vampiric": "Vampiric",
    f"enchantment.{NS}.grapple": "Grapple",
    f"enchantment.{NS}.magnetized": "Magnetized",
    f"enchantment.{NS}.moonwalk": "Moonwalk",
    f"enchantment.{NS}.bouncy.desc": "Projectiles ricochet off blocks once per level.",
    f"enchantment.{NS}.combustion.desc": "Kills explode, hurting nearby mobs.",
    f"enchantment.{NS}.vampiric.desc": "Heals 1 heart per level on a kill.",
    f"enchantment.{NS}.grapple.desc": "Hits within 30 blocks pull you to them.",
    f"enchantment.{NS}.magnetized.desc": "Pulls nearby drops and experience to you.",
    f"enchantment.{NS}.moonwalk.desc": "Lower gravity; no fall damage under 8 blocks.",
    f"tooltip.{NS}.set_bonus": "Full set:",
    f"tooltip.{NS}.scrap_plate_bonus": "-30% explosion damage, +1 knockback resistance",
    f"tooltip.{NS}.blight_forged_bonus": "Fire immunity; attackers take 2 damage",
}
write(f"{ASSETS}/lang/en_us.json", LANG)
