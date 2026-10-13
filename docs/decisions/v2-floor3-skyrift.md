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
- **Herbs are NODE slots.** `FloorTemplate.herbs` (block, count, min, max) scatters herb blocks as `NODE` slots
  tagged with the block id, so no common change (FloorSlotKind stays as it is) and `NODE_POPPED` fires for them
  like any node. `HerbBlock` is a one-hit `NodeBlock` (`hitsToPop()` = 1) with a plant shape, no collision and an
  XZ offset. Cloudbloom: 2 per floor, `round(2 × floorNodes)` clamped to 2–3 (1 player: 2, 8 players: 3).
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
