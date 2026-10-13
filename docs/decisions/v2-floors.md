# v2-floors session decisions

Defaults picked while building Stage 6A's floors (PLAN2.md §0–§2; brief `docs/sessions/v2-floors.md`). Same style
as `DECISIONS.md`: where this conflicts with a plan file, this logs what was actually built.

## Common floor contracts (PR 1)

- **A floor is described twice: the request and the result.** `FloorRequest` (act ordinal, template pool id,
  floor number, run seed, party size) is what the game hands over when an act opens; `PlacedFloor` (template,
  dimension, camp anchor, radius, ground level, seed, tags, filled slots) is the immutable record the world
  returns and every module reads through `Floors`. Nothing else in `common` knows what a template looks like:
  templates, pools and escalation are the world's data.
- **Slots are the cross-module surface.** `FloorSlot(kind, tag, pos, radius)` with `FloorSlotKind` = camp,
  exit portal, node, structure, crate, mini-boss, secret, event, cave, wreck, siege ring. The world places the
  terrain and marks the slot; the logic belongs to the feature's owner (Director: events, mini-boss, camp
  objects; vehicles: the wreck; the wave spawner: siege rings). `tag` carries the kind-specific detail as a
  plain string (node block id, structure kind, loot table, ring index) so the enum never needs a payload type.
- **`Floors` defaults to "no floors"** (empty list, `open` → empty) when no provider is installed, mirroring
  `Acts`/`Runs`: the classic run shape and a server without the world module never see a floor. `open` is
  idempotent per act (asking twice returns the floor already placed), so the game can call it from
  `ACT_STARTED` without bookkeeping.
- **Events** (`FloorEvents`): `FLOOR_OPENED` (before the party is sent, so listeners can fill slots),
  `NODE_POPPED`, `CRATE_OPENED` (first open only), `EXIT_PORTAL_USED` (unlit frame used → the game starts the
  spine's portal charge), `EXIT_PORTAL_LIT` (charge completed → the frame leads on). Node and crate events
  carry the floor (null for a debug-placed node outside any floor) so the Director's loot cadence can count
  per floor without a lookup.
- **Party curves in `party.json`:** `floorSize` (0.07, 0.9: ×1.4 radius at 8 players ≈ twice the area),
  `floorNodes` (0.25, 0.85: ×2.3 at 8), `floorCrates` (0.15, 0.9: ×1.9 at 8). Radius rather than area so the
  walk across a floor stays short for big parties; nodes grow faster than crates because nodes are the
  per-player income and crates the shared jackpots.
- **Ids in `CommonIds`:** the floor dimension `blocksouls_world:floors`, the three node blocks (`scrap_node`,
  `gunmetal_node`, `resin_node`), the Surface rare `amber_resin`, and the `floor_portal` block, so the game and
  Director can reference them without importing `world`.

## Floor dimension and the first Surface template (PR 2, step 1)

- **The game asks for floors through its act list.** `RunConfig.floorActs` (used when `runShape` is `"floors"`)
  is a 6-act list like `acts`, each `ActSpec` naming its template `pool` (default `blocksouls_world:surface`;
  acts 2–6 reuse the Surface pool and get meaner through escalation tags). `ActMachine` calls `Floors.open`
  before every `ACT_STARTED` (run start, charge completion, `/bs act advance`). That is the only game/run
  change. `"expedition"` and `"classic"` behave as before.
- **One void dimension, one floor per grid cell.** `blocksouls_world:floors` is a flat generator with no layers,
  structures, features or lakes. Each floor takes the next cell of a `gridColumns`-wide grid, `gridSpacing`
  (512) blocks apart. The cell counter is saved and never reset, so a new run never builds over an old run's
  islands (they stay, unused). Party-follow keys players per floor (`floor#<act>`).
- **The world moves players.** The world teleports everyone to floor 1's camp when act 1's floor opens.
  Joining or respawning in a floors run sends the player to the current floor's camp. `/bs act advance` and
  walking through a lit exit portal send them to the next floor's camp.
- **Death plane:** falling `deathPlaneDepth` (24) below the island's ground sends the player to the camp and
  costs `fallPenalty` (4) health, never below 1. It is checked once a second.
- **Camp objects come from the Director, not the world.** The Director's `DayLoop` already places the horn and
  shopkeeper at the camp and the event and mini-boss at the `EVENT` / `MINI_BOSS` slots on `ACT_ENTERED`. The
  world only shapes those spots: a coarse-dirt camp with a campfire, a gravel clearing, and a lantern-post arena.
  It never calls `CampObjects.place` itself, so nothing is placed twice.
- **Exit portal:** a polished-blackstone frame around a 1×3 column of `floor_portal` blocks. The portal stays
  solid and unlit until the spine's `PORTAL_CHARGE_COMPLETED`. Then the world lights it (`EXIT_PORTAL_LIT`) and
  walking in or using it takes players to the next floor. Using it unlit asks the spine to start the charge
  (`EXIT_PORTAL_USED`).
- **Nodes are hit-counted, not mined.** `scrap_node` and `gunmetal_node` are unbreakable. Each attack adds a hit
  (`nodeHits` = 3, inside the brief's 2–4). The last hit pops the node, which rolls its loot table
  `<ns>:nodes/<node>` `nodeBurstRolls` (3) times and fires `NODE_POPPED`. The node blocks have no item and no
  loot of their own, so the recipe web's R1 doesn't apply to them. Their drops (scrap, raw gunmetal, nuggets)
  are already in the web. The floor's rare node (resin/amber) waits for step 2 and its recipes.
- **Templates are data, plans are pure.** `data/<ns>/floor_template/*.json` is reloaded with datapacks.
  `FloorPlanner` (pure, unit-tested) picks a template from the pool by weight and floor range. It scales radius,
  nodes and crates with `PartyScaling.floorSize/floorNodes/floorCrates`, rotates the slots by a random quarter
  turn and mirrors them, and fills empty structure/secret tags from config. It tops up nodes and crates with
  scattered points that keep clear of slots, and rings the camp with `siegeRingPoints` siege points at
  `siegeRingFraction` of the radius.
- **Escalation tags** are `floor_<n>` plus every `escalationTags` entry at or below the floor number (2:
  `more_elites`, 3: `stingy_nodes`, 4: `mean_mini_boss`, 5: `elite_affixes`). `nodeFactorByFloor` trims node
  counts on later floors.
- **Structures are placed by code in step 1.** The `outpost` and `wreck_yard` structure kinds reuse the world's
  `StructurePlacer` checkpoint and wreck layouts, and their chests become `CRATE` slots. Registering them as
  real structures for `/locate` is step 2.
- **Cave and secret:** the cave is an 8-step stair tunnel to a lantern-lit hall with a chest and two gunmetal
  nodes. The secret is either a hidden drop (a trapdoor over a shaft to a vault) or a mossy hut with a cracked
  wall. Both have a `chests/floor_secret` chest. Loose crates use `chests/floor_crate`. Chest loot uses only the
  world's own items and `loot/*` item tags, so it never references another module's tables.
- **Look:** the floors dimension type sets fog, sky and cloud colours plus cloud height through environment
  attributes, and uses the overworld timelines and clock. Per-floor looks (biome-level attributes per template
  pool) wait for step 2.
