# v2-floor2-infernal session decisions

Defaults picked while building floor 2, Infernal (Stage 6C wave A; brief `docs/sessions/v2-floor2-infernal.md`, spec
"Floor 2 — Infernal" in `docs/design/floors.md`). Same style as `DECISIONS.md`.

## Slice 1: template pool, terrain, hazard

- **Pool `blocksouls_world:infernal`, three templates, floor 2 only:** `infernal_forks` (three radial channels, the
  default look), `infernal_rift` (two radials: two big lobes plus the camp, with a wreck), `infernal_caldera` (a wider
  ring, `ringFraction` 0.4, and wider channels). All tagged `infernal`, `minFloor` = `maxFloor` = 2, no trees, scrap and
  gunmetal nodes (the rare cinder node joins the mix in slice 2).
- **Act 2 of `RunConfig.floorActs` defaults to the Infernal pool** (one line in `game`, the brief's lane exception).
  No defaults migration: a `game-run.json` written before this keeps `surface` for act 2 until it is edited or
  deleted. A migration would bump `CURRENT_DEFAULTS`, which every floor session would collide on.
- **Lobes are cut by lava channels, described in the template** (`channels`): a ring around the camp at
  `ringFraction` of the radius, plus 2–3 radial cuts from the ring to `edgeMargin` (5) short of the edge. That makes
  3–4 lobes, counting the camp's. Channels are `width` 3 and their lava sits `lavaDepth` 2 under the ground. The banks
  flatten to the ground level, the bed is magma over the base block, and radials stop short of the rim so lava never
  spills into the void. The geometry is pure (`LavaChannels`) and turns with the floor's rotation and mirroring.
  The world rebuilds it from the template, the floor radius and the seed, so nothing new is saved.
- **Slots keep out of the lava.** Template slots are placed clear of the channels by hand. Scattered nodes and crates
  reject points within `slotClearance` of a channel. Siege ring points that land in the lava slide along the ring
  in 5° steps until clear. Caves sit at least 16 blocks from a channel (the stair and hall reach about 15), so a cave
  never cuts into the lava. The unit tests check all of this at 1 and 8 players over 40 seeds.
- **Bridges:**
  - Every outer lobe has a ring bridge into the camp. Exactly one of them (`highBridge`) is a polished-blackstone arch
    level with the ground.
  - The rest are crimson-plank decks one block over the lava. Crimson is fireproof and breakable.
  - Radial bridges, between outer lobes, are stone arches too (`radialBridgesHigh`), so siege mobs on any outer lobe
    can still walk round to the camp after the rise.
- **The hazard, part 1: lava is the death plane.** A player in lava on an `infernal` floor without fire resistance
  goes back to the camp with the fall penalty (`WorldFloors.fellOff`), checked every 10 ticks. Fire resistance (the
  brew-board tonic in slice 2) lets you wade, a reward for the herb lane. Toggle: `floors.json` `infernal.lavaSendsBack`.
- **The hazard, part 2: the lava rises at the siege midpoint.**
  - The siege is the portal charge. `riseSeconds` (90) after `PORTAL_CHARGE_STARTED`, the lava rises `riseBlocks` (1).
    That is half the default 180-second charge; the world keeps its own number so the charge length can change
    without moving it.
  - The rise fills air, fire and low decks in channel columns, so the low bridges sink. The camp is then reachable
    only over the one stone arch: the siege funnels into one chokepoint.
  - Show, don't tell: for `warnSeconds` (5) before the rise, the channels throw lava and smoke and every player on the
    floor hears a deep lava rumble. There is no text.
  - The rise is live state only. A server restart mid-siege skips it, and a finished charge cancels a pending rise.
- **Look:** a new biome `blocksouls_world:infernal` (red sky and fog, white ash and ash particles, basalt-delta
  ambience; water does not evaporate, because slice 3's secret needs a bucket of it) is painted over the island's
  chunks after building by the shared template `biome` field (Skyrift's painter; the same fill `/fillbiome` does,
  resent to clients). Ground: blackstone top
  with magma, basalt, netherrack and soul-soil accents (`terrain.accents`, percent per top block, seeded per column),
  basalt fill, blackstone base, basalt spires and eternal fires on netherrack. The camp is soul soil with a soul
  campfire.
- **Debug:**
  - `/bs floor hazard lava_warn|lava_rise|lava_schedule` (registered with Skyrift's shared `FloorHazards`) acts on the
    Infernal floor you stand on.
  - `/bs floor open <act> <players> <pool>` opens a floor from any pool. The floor number is the act, so
    `/bs floor open 2 1 blocksouls_world:infernal` opens an Infernal floor.
  - `/bs floor goto <slot kind>` already works on any floor.
  - `open` registers as its own `/bs floor` contributor (`InfernalCommands`), and Brigadier merges the nodes.
- **Interim slot tags until slice 3:** the `fortress` structure falls back to the checkpoint ruin and the
  `soulfire_door` secret to the cracked wall (the builder's defaults for unknown tags).
