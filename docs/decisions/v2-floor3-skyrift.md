# v2-floor3-skyrift session decisions

Defaults picked while building floor 3, Skyrift (brief `docs/sessions/v2-floor3-skyrift.md`, spec "Floor 3 —
Skyrift" in `docs/design/floors.md`). Same style as `DECISIONS.md`: where this conflicts with a plan file, this logs
what was actually built.

## Pool, terrain and hazard (PR 1)

- **Three templates in pool `blocksouls_world:skyrift`, floors 3 only:** `skyrift_spire` (weight 2, radius 54, the
  default look), `skyrift_archipelago` (radius 48, 7 shards, the windiest rim at 1.3×) and `skyrift_terraces`
  (radius 58, 5 shards). Each has every slot kind so the shared planner/gametest invariants hold unchanged. Terrain is
  grass over calcite over diorite: pale stone under a pale turf, no custom block needed.
- **Template schema grows only optional fields, all defaulting to Surface's behaviour:** `edgeWall` (true),
  `biome` (""), `rimWind` (0), `shards` (count 0) and `elevatedSiegePoints` (0). An old template plans and builds
  exactly as before; shards draw from their own `Random` (seed ^ "SHARD") so adding them never moves another slot.
- **Shards:** count is `round(shards.count × PartyScaling.floorSize)` capped at `shards.maxCount` (8), so the sky
  fills as the party grows (tested at 1 and 8). They are spread by sector, rise 8–20 blocks above the ground, keep
  `size + 3` blocks from the camp, portal and structure and from each other, and a shard past the rim keeps its
  gap to the island ≤ `maxGap` (9) — one web swing. A low shard's underside is capped so it never touches the
  island's hills.
- **Vines:** a shard over the island hangs a glow-berry (cave vine) strand to three blocks above the ground with
  `vineChance` (0.5): climbable both ways, so a party without web-swing can still reach the high ground.
- **Elevated siege points:** up to `elevatedSiegePoints` siege-ring points move onto the nearest unused shard that
  lies *over* the island (never one past the rim), so mobs that spawn there drop onto the fight, not into the void.
  `PlannedSlot` gained a `rise`; the placed slot sits on the shard's top.
- **No glass wall; the edge is the hazard.** Falling off reuses the existing death plane (teleport to camp + fall
  penalty). Rim wind pushes players outward inside the last `rimWindBand` (6) blocks of the island, linearly up to
  `rimWindStrength` (0.03 blocks/tick², × the template's `rimWind`) at the edge, only within `rimWindHeight` (5)
  of the ground (calm on the shards), applied every 2 ticks. **Sneaking cuts it to 25%** — the counterplay is
  crouch near the edge. All numbers are `floors.json` fields.
- **Sky look via a painted biome.** `blocksouls_world:skyrift` (warm gold sky, cream fog, low bright clouds,
  pale-blue grass and leaves, no rain) is painted over the floor's square cell, every height, after building, and
  the chunks' biomes are resent. Templates with no `biome` keep the dimension's own.
- **Hazard triggers are a registry.** `FloorHazards` maps names to triggers; Skyrift registers `gust` (one hard
  rim gust, `gustFactor` 12× the rim wind) and `fall` (the death-plane penalty). `/bs floor hazard <trigger>`
  fires one on the player's current floor. Later floors add their own names.
- **`/bs floor open <act> [players] [pool]`** gained an optional pool word (`skyrift` → `blocksouls_world:skyrift`)
  so any floor's pool can be opened from the command line; `/bs floor goto <slot>` works unchanged.
- **Placeholders until slice 3:** the structure slot is tagged `sky_temple` and the secret slot `sky_seed`; until
  those builders exist they fall back to the checkpoint ruin and the cracked-wall hut. Nodes use scrap/gunmetal
  until the skysteel node lands (slice 2).
- **Lane exception (game):** `RunConfig.defaultFloorActs` sets floor 3's pool to `blocksouls_world:skyrift`,
  inside a marked "floor pools (Stage 6C)" block so the other floor sessions add one line each.

## PR 2: skysteel, cloudbloom and the sky kit

- **Skysteel only grows on shards.** Each template's `shards.node` places `shards.nodes` skysteel nodes (2, or 3 on
  the archipelago) on shards that carry no raised siege point, scaled by `PartyScaling.floorNodes` and capped
  by the free shards. Skysteel left the ground `nodeMix`: going out on the shards is how you get the rare.
- **Herbs are NODE slots.** The shared `FloorTemplate.herbs` map (from Blight, #76) scatters herb blocks as `NODE` slots
  tagged with the block id, so no common change (FloorSlotKind stays as it is) and `NODE_POPPED` fires for them
  like any node. `HerbBlock` is a one-hit `NodeBlock` (`hitsToPop()` = 1) with a plant shape, no collision and an
  XZ offset; cloudbloom uses the shared `HerbBlock` as-is. Cloudbloom: `{"blocksouls_world:cloudbloom_plant": 2}`, × `floorSize`
  (1 player: 2, 8 players: 3).
- **Shard nodes find their floor.** A shard past the rim isn't inside `Floors.at`, so `popNode` falls back to the
  nearest floor in the same dimension whose camp is within 1.5 × radius + 24 blocks (`WorldFloors.floorNear`).
- **Recipes.**
  - Skysteel featherboots: 4 skysteel and a cloudbloom, as boots.
  - Cloudbloom tonic: 2 cloudbloom and a bottle → slow falling for 60 s.
  - Updraft tonic: cloudbloom, skysteel, a feather and a bottle → Jump Boost II for 45 s plus slow falling
    for 15 s.
  - Gun barrels from skysteel: 2 skysteel and a gunmetal → 2 `blocksouls_guns:gun_barrel` (only with guns
    loaded).
  - Skysteel and cloudbloom are each in 2+ recipes.
- **Featherboots.**
  - Skysteel armor material: durability 22, boots defense 2, toughness 0.5, enchantability 15, repaired with
    skysteel.
  - Plus +4 safe fall distance and −33% fall damage, so a missed shard jump hurts less (not a full cancel: the
    rim fall still costs its penalty).
- **Boards (docs/decisions/v2-boards.md).**
  - Entry files: featherboots and gun barrels on forge, the two tonics on brew. Each with `minFloor` 3 and
    `floorTags` `["skyrift"]`.
  - Source files: skysteel and cloudbloom. These replace the DirectorConfig edit the draft had, so there is no
    game lane exception.
  - Enchant spec: Skyrift's enchant service takes 3 skysteel and 8 souls for one of feather falling,
    projectile protection, power or punch.
  - Upgrade chain: featherboots → blight-forged boots.

## PR 3: sky temple, glowing seed, events, siege twist and roster (the floor is done)

- **Sky temple** (`SkyTemple`, the `sky_temple` structure slot): three calcite tiers (half-sizes 5, 4, 3, 6 blocks
  apart) with quartz corner pillars, climbed by a ladder up the middle; the roof holds the seed pedestal and the top
  chest. The chest (`chests/sky_temple`) rolls **one** of the featherboots or a long-range gun
  (`#blocksouls_world:loot/long_range_guns`, the sniper rifle, `required: false`), plus skysteel, cloudbloom and
  tonics. Without the guns module the gun entry is empty, so that roll can come up with nothing; the boots still
  come up half the time. The temple is placed by code, like PR 1's structures.
- **Glowing seed secret.** The pedestal on the temple roof hands out one `glowing_seed` (it stores the pedestal's
  position). Use it on the **sky altar** (the `sky_seed` secret slot, moved next to camp in all three templates)
  to plant it.
  - Planting pays everyone on the same floor a **legendary relic** (`blocksouls_game:relic_legendary`). Without
    the game module, they get a roll of `chests/floor_secret` instead.
  - The seed **dims** on any damage taken, on the fall back to camp, and when it is dropped or tossed. A dimmed
    seed leaves the inventory and its pedestal relights, so the carry can be retried.
  - The altar stays lit once planted, so the secret pays once per floor.
- **Director events go through a common hook.** `DirectorEvents.PLACE_EVENT` (common) is fired by the Director
  when it places an event id it doesn't own, so the world builds Skyrift's three events without game importing
  world. The events are floor-tagged `EventSpec`s (`floorTags: ["skyrift"]`) in `DirectorConfig`. A spec with
  floor tags only rolls on a floor carrying one of those tags. The hook and the floor-tag mechanics landed on main
  with the Vault's #79, so this slice only adds Skyrift's entries plus a `defaultsVersion` bump (Director 3,
  mobs 2) that tops up a file already migrated to the Vault's defaults (missing floor events, rosters and affix
  mixes join; tags tuned by hand are kept).
  - **Wind altar:** offer a held common or rare relic. Win (`windAltarWinChance` 0.5): you get the next-tier
    relic plus `windAltarTonics` (2) cloudbloom tonics. Lose: you get slow fall and a push. One offer per altar.
    The brief's "rune" prize is the tier-up relic, because there is no rune item.
  - **Falling-shard chest:** a calcite disc `fallingShardRise` (8) above the slot, with a ladder up and a crate
    chest on it. After `fallingShardSeconds` (45) it breaks up into falling blocks and the chest is gone.
  - **Bridge ambush:** a plank bridge between two stone pillars, with a crate chest on the deck. It is an
    ambush spec, so the Director's pack springs on whoever reaches it.
  - Event state (shards counting down) is live-only, like the Director's objectives.
- **Siege twist (waves come from the shards):** during a wave, each wave mob that loads in a Skyrift floor has a
  `shardSiegeChance` (0.4) chance to be moved to a random raised siege point (the shards PR 1 put up). It gets
  `shardSiegeSlowFall` (160) ticks of slow falling and drops in. Each mob is tagged so it's only moved once. The
  world does this, not the wave spawner, so `game/run` isn't touched.
- **Mini-boss pool** (`miniBossesByFloorTag.skyrift`): Stormplate (`blocksouls_bosses:stormplate_knight`), the
  Storm Roc and the Glider Matriarch. Like the base roster, bosses that aren't installed are skipped.
  - **Storm Roc:** a size-6 phantom, `rocHealth` 90. Every `rocGustTicks` (140) it pushes players inside
    `rocGustRadius` (9) outward at `rocGustStrength` (1.3), toward the edge.
  - **Glider Matriarch:** a size-3 phantom, `matriarchHealth` 60. She calls a glider pack at spawn and again at
    half health.
  - **Glider pack:** `gliderPack` (4) small frosted sky gliders, `gliderHealth` 8.
  - Health scales with `PartyScaling.bossHealth` and pack size with `directorBurst` (`SkyBossMath`, tested at 1
    and 8 players). All three use the vanilla phantom model and texture; dedicated art can replace them without
    touching code.
  - The Matriarch's "pack called" flags aren't saved, so after a reload she can call a pack once more.
- **Roster:** `AffixConfig.weightsByFloorTag.skyrift` makes **frosted** the common affix (weight 5) on Skyrift.
  The brief's other roster entries map to existing mobs, because roster entries can't be weighted per floor:
  - flyers: the gliders and the Roc
  - leapers: the crawlers
  - snipers: the spitters
- **Lane exceptions** (defaults only, each in a marked Skyrift block; the mechanics came with #79):
  - game `DirectorConfig`: three Skyrift events, the `skyrift` mini-boss roster, migration 3
  - mobs `MobsConfig`: the `skyrift` affix mix (frosted), migration 2
- **Exemptions:** the featherboots are gear and the cloudbloom tonic is a consumable. Both now drop from the
  temple chest, so they are listed in `recipe_web_exempt.json`.
