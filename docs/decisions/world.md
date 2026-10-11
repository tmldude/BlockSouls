# World decisions

Defaults picked by the `world` session (Stage 1). Same rules as `DECISIONS.md`.

## Materials and ore
- **Gunmetal veins:** vanilla `minecraft:ore` feature, size 7 (3–7 blocks per vein in practice), trapezoid
  y −32..48, **7 veins per chunk** (iron's middle band has 10). Replaces stone / deepslate via the vanilla
  `*_ore_replaceables` tags. Smelts in 10 s (furnace) / 5 s (blast furnace), 0.7 xp. Ores themselves smelt too.
- **Counts and rarities are config, not data.** Two placement modifiers read `world.json` live:
  `blocksouls_world:config_count` and `blocksouls_world:config_rarity` (`{"key": ...}`), so admins tune ore density
  and structure rarity without a datapack.
- **Scrap piles in villages: not done.** Injecting into village jigsaw pieces needs template edits; instead loose scrap
  piles (1–3 per cluster) scatter on dry land in every overworld land biome, ~1 cluster per 24 chunks, and every wreck
  and checkpoint has 2–4. Village chests get scrap through loot injection.
- **Blight core** is `fireResistant` and uncommon rarity. Blight hearts drop exactly one core (no Fortune bonus,
  no silk touch) and need an iron pickaxe.

## Structures
- **Features, not structures.** Both are code-placed features (`Feature` records registered in `FEATURE_TYPE`) from
  pure layout generators (`gen/layout/*Layout`, unit-tested). Footprint stays within 7 blocks of the origin so a
  feature never writes outside its 3×3 chunk window.
- **Site check:** ground must be dry, solid, not leaves/logs, and within 2 (wreck) / 3 (checkpoint) blocks of level at
  the corners; otherwise the chunk is skipped. Bottom layers are propped up with the local ground block (≤ 4 deep).
- **Wreck biomes:** plains, sunflower plains, savanna (+plateau, windswept), desert, taiga (+snowy, old growth pine /
  spruce), meadow. **Checkpoint:** any overworld biome except oceans, rivers and beaches.
- **Kart wreck** spawns beside the car (3 blocks off its side) if `blocksouls_vehicles:kart_wreck` is registered.
- **`/bs world locate`** reads a saved log (`StructureLog`) of wrecks/checkpoints as they generate; only explored
  chunks are known, since features aren't searchable like registered structures.
- **Cross-module loot without hard ids.** Chest tables reference other modules through our own item tags with
  optional entries (`#blocksouls_world:loot/ammo` → `#blocksouls:ammo`, `loot/vehicle_wheels` → `blocksouls_vehicles:wheel`,
  `loot/vehicle_engines`), so they load (and tests pass) without guns/vehicles installed. **Guns** in wreck (20%,
  common) / checkpoint (50%, rare) chests are added in code from `#blocksouls_world:loot/guns`, with
  `blocksouls_guns:roll_perks` only when that function is registered. The tag lists the guns brief's names
  (`hand_cannon`, `auto_rifle`, `shotgun`, `sniper`) as optional ids; **integration: check them against guns' real ids.**

## Loot injection
- Scrap/gunmetal roll (60%) into dungeon, mineshaft, village (smiths and houses), desert/jungle temple, shipwreck
  supply, outpost, ruined portal, igloo, bastion (other) and fortress chests. Wacky books (25%) into dungeon,
  mineshaft, stronghold (all three), bastion (treasure/other/bridge), ancient city and end city. Both lists and chances
  in config. Datapack-replaced tables are left alone.
- **Wave tables** get a nested roll of our `inject/wave_*` tables: common 25%, rare 40%, legendary 60% (config); the
  clear reward always gets one.
- **Endermen:** +1 pearl (config) and a 15% Ender Eye, +5% per Looting level (config). The vanilla pearl pool (with
  Looting) is untouched.

## Infected Nether
- Blight patches are blobs (radius 3–5) that convert netherrack/nylium; 1 in 4 exposed tops grows a blight growth,
  1 in 5 exposed sides gets veins (glow-lichen-style multiface, light 3), 12% of patches hold a blight heart (light 10).
  **3 patches/chunk** in crimson forest and nether wastes, **1** elsewhere in the Nether (config).
- Blighted netherrack and the heart use slow brightness-pulsing animated textures; blighted netherrack is in
  `base_stone_nether` and `infiniburn_nether` so it behaves like netherrack.
- **Ash fog:** a client mixin on `FogRenderer.setupFog` (RETURN) pulls the atmospheric fog in to 4–48 blocks and blends
  its color 60% toward ash gray in the Nether; never thins fog that's already thicker (lava, blindness). Ash and white
  ash particles drift around the player, 3 per tick. All in config (`ashFog`).

## Wacky enchantments
- Definitions are data. Weights 2 (Combustion and Grapple 1); min cost 15 (20) + 10/level. All six are in
  `#minecraft:in_enchanting_table`, `#blocksouls_world:wacky` (used by loot books) and `tooltip_order`.
- **On-kill** (Combustion, Vampiric) reads the weapon from `DamageSource.getWeaponItem()`, else the killer's main hand.
  Explosion kills never trigger them, so Combustion can't chain.
- **Combustion** blast: radius 2, no fire, `ExplosionInteraction.NONE`, damages and knocks back **mobs only**: never
  players (owner or teammates) or dropped items.
- **Grapple** fires on ranged hits only (a melee swing never pulls): entity hits through `AFTER_DAMAGE` (arrows and
  guns), block hits through the arrow mixin. Pull is 1.6 blocks/tick toward the target (40% minimum for short pulls)
  plus 0.25 lift, and resets fall distance.
- **Bouncy** (arrows/tridents) reflects the velocity off the hit face, keeping 70% speed; stops bouncing under 0.2
  blocks/tick. Guns implement bullets.
- **Magnetized** pulls items and XP within 6/10 blocks at 0.35 blocks/tick; items still on pickup delay (just thrown)
  are left alone.
- **Moonwalk**: −30% / −50% gravity and +5 safe fall distance (3 → 8 blocks) at both levels, pure attribute effects.
- Tooltips: one gray mechanics line under each wacky enchantment (items and books).

## Armor
- **Scrap-plate:** durability 16, defense (helmet/chest/legs/boots) 2/6/5/2 like iron, toughness 0.5, enchantability 9, repaired with scrap.
  Crafted from scrap + 1 iron ingot per piece (2 for boots). Set bonus: −30% explosion damage (mixin on
  `LivingEntity.hurtServer`), +0.1 knockback resistance (vanilla shows it as "+1").
- **Blight-forged: crafted directly** (no smithing template to find): gunmetal ingots + blight cores (5 cores per set).
  Durability 35, defense 3/8/6/3 like diamond, toughness 2.5, knockback 0.05/piece, enchantability 14, fire resistant
  item, repaired with gunmetal. Set bonus: immune to fire damage (and fire is put out each tick), attackers take 2
  damage (thorns damage type, so two blight-forged fighters can't loop).
- Set bonuses apply to any living entity for damage; the passive parts (knockback, extinguish) tick for players only.
