# v2-boards session decisions

Defaults picked while building the shopkeeper's forge and brew boards (brief `docs/sessions/v2-boards.md`, owner
decisions D1 and D3 in `docs/design/floors.md`). Same style as `DECISIONS.md`.

## Recipes stay vanilla JSON; floors runs hide them from the grids

- **No new recipe types.** BlockSouls recipes keep their vanilla types (`minecraft:crafting_shaped`,
  `crafting_shapeless`, `smelting`, `smithing_transform`). In a **floors** run (`runShape == "floors"`, run
  active) a `game` mixin drops every recipe whose id namespace starts with `blocksouls_` from the `crafting` and
  `smithing` lookups, so the 2×2 grid, crafting tables (also turned off, below), crafters and smithing tables
  never match one. The shopkeeper's boards read the same recipes and craft them.
  Why not wrap them in `forge`/`brew` recipe types (the brief's preferred shape): classic runs keep crafting
  guns and armor at a table unchanged, no other module's recipe JSON has to be migrated, the recipe-unlock
  advancements (R3) and `check_recipe_web.py` keep reading the files they read today, and a floor session
  writes an ordinary recipe it can test in a classic world.
- **Furnaces keep smelting** (ore → ingot isn't crafting). The smelting recipes are also listed on the forge
  board; their `blasting` twins aren't (the same conversion twice).

## Which board

A BlockSouls recipe is on exactly one board, decided in this order:

1. an entry file (below) with `"board"`;
2. its result is in the item tag `#blocksouls:brew_board` → brew, `#blocksouls:forge_board` → forge
   (`CommonTags.BREW_BOARD` / `FORGE_BOARD`);
3. its result is food, has the `consumable` component, or is in `#blocksouls:ammo` → **brew**;
4. everything else → **forge** (armor, gun parts, guns, ingots, blocks).

Every BlockSouls recipe is on a board even without an entry file: entries only narrow (floors, tags) or move it.

## Data formats (the contract floor sessions write against)

All four folders are read from every namespace on `/reload` (`CommonIds.BOARD_*`). Files in your own module,
e.g. `world/src/main/resources/data/blocksouls_world/blocksouls_board/entry/cinder_plate_helmet.json`.
Unknown fields are ignored; a bad file is logged and skipped (it never breaks the reload). Item and
enchantment ids that don't exist (a module not installed) make that file inert, not an error.

### `blocksouls_board/entry/<any>.json`: where and when a recipe shows

```json
{
  "recipe": "blocksouls_world:cinder_plate_helmet",
  "board": "forge",
  "minFloor": 2,
  "maxFloor": 99,
  "floorTags": ["infernal"],
  "hidden": false
}
```

| Field | Default | Meaning |
|---|---|---|
| `recipe` | required | the recipe id (any namespace starting `blocksouls_`) |
| `board` | by the rules above | `"forge"` or `"brew"` |
| `minFloor` / `maxFloor` | 1 / 99 | shown only on floors in this range (inclusive) |
| `floorTags` | `[]` | when not empty, shown only on a floor whose `PlacedFloor.tags()` has **at least one** of these |
| `hidden` | false | true takes the recipe off both boards (it still exists for classic runs) |

The floor is `Floors.at(server, player's dimension, player's position)`: its `floorNumber()` and `tags()`. A
player not standing on a placed floor (classic, expedition, a test world) counts as floor = the act ordinal
(at least 1) with no tags, so entries with `floorTags` don't show there. Two entry files for the same recipe:
the last one loaded wins (namespace then path order), logged as a warning.

### `blocksouls_board/source/<any>.json`: "where to get it" for an ingredient

```json
{ "item": "blocksouls_world:cinder", "icon": "blocksouls_world:cinder_node", "label": "Cinder nodes (Infernal)" }
```

`icon` is the item shown in the ingredient's slot, `label` the line under it. These merge over
`DirectorConfig.Shop.sources` (config first, then data in load order; data wins on the same `item`), and the
trade board's prices use the merged map too. An ingredient with no source shows its own icon.

### `blocksouls_board/enchant/<any>.json`: a floor's forge enchant service

```json
{
  "minFloor": 2, "maxFloor": 2, "floorTags": ["infernal"],
  "metal": "blocksouls_world:cinder", "metalCount": 3, "souls": 8,
  "enchantments": ["minecraft:fire_protection", "minecraft:fire_aspect", "minecraft:flame"]
}
```

The service picks the spec for the player's floor: matching specs with `floorTags` beat ones without, then the
highest `minFloor`. Pay `metalCount` × `metal` + `souls` → one random enchantment from the list that fits the
held item (main hand), at level 1, or +1 level if the item already has it (never past the enchantment's max).
No enchantment fits → nothing is taken. `game` ships the floor-1 spec (gunmetal).

### `blocksouls_board/upgrade/<any>.json`: a gear tier chain (the §3d forge ritual for armor)

```json
{ "chain": ["minecraft:chainmail_helmet", "blocksouls_world:scrap_plate_helmet", "blocksouls_world:blight_forged_helmet"] }
```

Hold a piece and carry a second of the **same item** → both are taken, plus the upgrade price (config
`DirectorConfig.Shop.upgrade*`), and you get the next item in its chain, keeping the held piece's enchantments.
An item that has a next tier in several chains uses the first chain loaded (namespace then path order), so don't
re-link existing pieces: add your floor's armor as a chain that **starts** with it and joins the existing ladder,
e.g. `["blocksouls_world:cinder_plate_chestplate", "blocksouls_world:blight_forged_chestplate"]`. `game` ships the
default ladders: leather → chainmail → scrap plate → blight-forged, per piece.
