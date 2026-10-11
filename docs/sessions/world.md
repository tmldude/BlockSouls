# Session: world (Stage 1)

**Module:** `world`. **Branch:** `claude/world`. **PR title:** `world: materials, structures, infected Nether, enchantments, armor`.

You build the world the run plays in: new materials and an ore, two wreck/ruin structures, loot, the infected Nether
look, Ender Eye sources, six wacky enchantments and two armor sets. Read `docs/sessions/README.md` and PLAN.md §0b
first. **Show, don't tell:** the world explains itself (wrecks, ruins, an infected Nether); no lore text.

## You own
Everything under `world/`.

## Contracts
- Item ids fixed in `CommonIds`: `SCRAP`, `GUNMETAL_INGOT`, `BLIGHT_CORE`. Guns' and vehicles' recipes use them.
- Enchantment ids fixed in `CommonIds`: `BOUNCY`, `COMBUSTION`, `VAMPIRIC`, `GRAPPLE`, `MAGNETIZED`, `MOONWALK`.
  Guns implements Bouncy on bullets; you do everything else (incl. Bouncy on arrows/tridents).
- Structures place a kart wreck entity by id `CommonIds.KART_WRECK` (look it up in `BuiltInRegistries.ENTITY_TYPE`;
  if absent, place nothing). Vehicles owns the entity.
- Add materials to the game's wave tables (`CommonIds.WAVE_DROPS_*`, `WAVE_CLEAR_REWARD`) with `LootTableEvents.MODIFY`.
- Guns are in `#blocksouls:guns` (filled by guns); reference that tag in enchantment `supported_items`.

## Materials and ore
- **Scrap** (item): from scrap piles (a block found on the surface around wrecks and in villages; breaks into 1–3
  scrap), salvaged from structure chests, and wave drops.
- **Gunmetal:** `gunmetal_ore` + `deepslate_gunmetal_ore`, veins of 3–7 from y −32 to 48, slightly rarer than iron
  (config counts) → `raw_gunmetal` → smelt/blast → `gunmetal_ingot`; storage blocks for raw and ingot; nuggets.
  Mining tier: stone pickaxe. Tools not needed.
- **Blight core** (item): from rare `blight_heart` blocks in the Nether (glowing, fleshy, needs iron pickaxe, drops 1)
  and from the Furnace Knight (bosses' loot table).

## Structures (code-placed features, not jigsaw NBT; keep them small and cheap)
- **Roadside wreck:** a crashed vehicle shell of blocks (iron blocks/bars, black concrete, smashed glass), a few scrap
  piles, a loot chest, and a kart wreck entity next to it. Plains, savanna, desert, taiga, meadow; ~1 per 12×12 chunks
  (config).
- **Collapsed checkpoint:** a ruined barrier (stone brick/concrete walls, sandbags = brown wool/mud bricks, a broken
  watchtower), 1–2 loot chests, scrap piles; rarer (~1 per 24×24 chunks), any overworld land biome except oceans.
- Loot tables: `blocksouls_world:chests/wreck` (scrap, gunmetal, iron, food, a chance of ammo/guns via guns' item
  ids and `blocksouls_guns:roll_perks`, vehicle parts by id `blocksouls_vehicles:wheel` / `engine`), and
  `chests/checkpoint` (better: guns, ammo, wacky enchanted books, gunmetal).
- Generate via Fabric biome modifications + `PlacedFeature`s; spawning the wreck entity from a feature is fine
  (`WorldGenLevel.addFreshEntity`).

## Loot injection (vanilla chests)
Scrap and gunmetal into common vanilla chests; wacky enchanted books into dungeon/mineshaft/stronghold/bastion/
ancient city/end city (config weights).

## Ender Eye sources
2-hour runs need ~12 eyes. Bosses drop eyes (bosses' tables). You: endermen drop +1 pearl and have a 15% Ender Eye
drop (config); Looting still works.

## Infected Nether (look and feel)
- **Blighted netherrack** (fleshy, pulsing texture), **blight growth** (a decorative plant), **blight veins** on
  walls; patches in every Nether biome via biome modification, denser in crimson/nether wastes.
- **Ash fog:** client-side, in the Nether: thicker, ash-colored fog and drifting ash particles. Find the 26.3 fog /
  environment hooks (Fabric API or a client mixin on the fog renderer).

## Wacky enchantments (data-driven `data/blocksouls_world/enchantment/*.json`; code where JSON can't express it)
| Enchantment | On | Max | Effect |
|---|---|---|---|
| **Bouncy** | bows, crossbows, tridents (+guns via guns) | 2 | Projectiles ricochet off blocks once per level. |
| **Combustion** | swords, axes, bows, guns | 1 | Kills explode (radius 2, damages mobs, never breaks blocks or hurts the owner). |
| **Vampiric** | swords, axes, guns | 3 | Heal 1 heart per level on kill. |
| **Grapple** | bows, crossbows, guns | 1 | Hitting an entity or block within 30 blocks pulls you toward it. |
| **Magnetized** | leggings | 2 | Pull dropped items and XP within 6/10 blocks. |
| **Moonwalk** | boots | 2 | −30% / −50% gravity, no fall damage below 8 blocks (attribute effects: `gravity`, `safe_fall_distance`). |

On-kill effects: listen to the death event and read the enchantment level on the killer's weapon from the
damage source (works for guns because guns attribute bullet damage to the player + gun). Enchanting table: add
them to `#minecraft:in_enchanting_table` with low weight so they're findable; books in loot as above. Names and
tooltips state mechanics only.

## Armor sets (with 4-piece set bonuses)
- **Scrap-plate** (early, ~iron tier, crafted from scrap + iron): set bonus −30% explosion damage, +1 knockback
  resistance. Rust/scrap look.
- **Blight-forged** (Nether, between diamond and netherite, crafted from gunmetal + blight cores, smithed on
  diamond armor or crafted directly — decide): set bonus fire immunity and attackers take 2 damage when they hit you.
  Blight look.
- 26.3 armor uses equipment assets (`assets/<ns>/equipment/*.json` + `textures/entity/equipment/...`); find the
  current format in the vanilla source. Item textures via `gen.py helmet|chestplate|leggings|boots <palette>`;
  armor layer textures via PIL in the same palettes.

## Debug commands
`world place wreck|checkpoint` (at your feet), `world blight <radius>` (convert netherrack around you),
`world locate wreck|checkpoint`.

## Tests
- Unit: any pure helpers (structure layout generators returning block lists, enchantment math).
- Gametests: placing each structure via the feature produces a chest with loot and (if the type exists) a wreck
  entity; Combustion kill explodes without breaking blocks; Vampiric heals; Moonwalk boots lower gravity; the
  scrap-plate set bonus reduces explosion damage; gunmetal ore drops raw gunmetal.

## Done when
CI green; in `runClient` a new world has wrecks and checkpoints, gunmetal ore underground, an infected Nether with
ash fog; enchantments and armor sets work; `docs/decisions/world.md`.

## Out of scope
Radiation zones, hazmat/dragonscale armor, more structures (growth/bosses). Kart entity (vehicles).
