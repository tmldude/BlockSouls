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
- **Interim slot tags until slice 3 (replaced there):** the `fortress` structure fell back to the checkpoint ruin and the
  `soulfire_door` secret to the cracked wall.

## Slice 2: cinder ore node and ashcap herb

- **Cinder ore** (`blocksouls_world:cinder_ore`) is a rare node in every Infernal template's `nodeMix` (weight 1,
  beside scrap and gunmetal). It pops in `nodeHits` like the others; its loot (`nodes/cinder_ore`) is cinder 1–2
  (weight 6) or scrap 1–2 (weight 2), rolled `nodeBurstRolls` times. It glows faintly (light 6).
- **Ashcap herbs are NODE slots tagged with the herb's block id**, so common's `FloorSlotKind` doesn't change. They
  use Blight's herb API on main: each Infernal template sets `"herbs": {"blocksouls_world:ashcap": 2}`, and the planner
  scatters `round(2 × floorSize)` of them (2 solo, 3 at 8 players). The planner's `extras` (rare nodes and herbs) now
  also keeps clear of lava channels; templates without channels pass null, so their plans don't change. The ashcap is an
  `AshcapBlock`, a `HerbBlock` (pops in one hit, light 9, non-colliding) that sheds spore and flame particles. It rolls
  `nodeBurstRolls` like a node, unlike the ghostcap's fixed 1, because the tonic takes 2 ashcap.
- **What cinder and ashcap make** (each drop feeds 2+ recipes):
  - **Cinder Plate Chestplate** (forge): Scrap Plate Chestplate + 4 cinder. Scrap-plate stats a step up (defense 5,
    toughness 1) and −50% burn time (the `burning_time` attribute), repaired with cinder. Its upgrade chain leads
    into the Blight-Forged Chestplate.
  - **Combustion book** (forge): book + 3 cinder + gunmetal ingot → an enchanted book with Combustion I.
  - **The gun fire-perk reroll** is the Infernal enchant spec (`blocksouls_board/enchant/infernal.json`): on floor 2,
    3 cinder + 8 souls rolls one of Combustion, Fire Aspect, Flame or Fire Protection onto the held item.
  - **Ashcap Tonic** (brew): glass bottle + 2 ashcap → fire resistance for `tonicSeconds` (90). This is the drink that
    lets you wade the lava (slice 1's hazard).
  - **Scorch Draught** (brew): glass bottle + ashcap + cinder → the new Scorching effect for `scorchSeconds` (120):
    your melee hits set the target alight for `scorchIgniteSeconds` (4) × level.
  - Brews stack to 16 and leave their bottle. Every board entry is `minFloor` 2, so they show from the Infernal on.
- **Sources:** board source entries name where cinder and ashcap come from ("Cinder ore nodes (Infernal)",
  "Ashcap herbs (Infernal)").
- **Gametests use a creative mock player** (vanilla's `makeMockServerPlayerInLevel` is always creative), so the lava
  test calls `InfernalHazard.burn` (the check minus the game-mode exemption), and the tonic's bottle is checked
  through its use-remainder component (creative doesn't use the stack up).

## Slice 3: fortress, soulfire door, events, roster, mini-bosses

- **The fortress stub** (`InfernalFortress`, the `fortress` structure tag) is three rooms in a row along a random
  horizontal direction, 17 × 7 blocks: a gate (fences), the hall (soul campfire, the **bastion chest**) and the vault.
  Nether bricks with ~1 in 6 cracked; the gate and hall walls crumble toward the top (it is a ruin), the vault is
  whole polished-blackstone bricks with a roof. A footing of bricks fills under the corners that reach past the
  flattened slot. Its blocks are ordinary and breakable, like the Surface huts: digging into the vault is possible but
  slow, and the door is the intended way in.
- **Bastion chest** (`chests/infernal_bastion`): one of the Cinder Plate Chestplate (heat armor) or a gun from
  `#blocksouls_world:loot/guns`, plus 1–2 Scorch Draughts and 2–4 cinder. "Fire gun" is read as a gun plus the
  draught that makes its hits ignite; there is no fire-element gun to drop. Both drops are exempt in the recipe web as
  gear and consumable.
- **Soulfire door secret:** the template's SECRET slot is now `water_pocket`, a 3×3 spring (water sources over clay)
  on another lobe than the fortress (a planner test checks the straight line between them crosses lava over 40 seeds
  at 1 and 8 players; `infernal_rift`'s pocket moved to its south lobe for this). The vault door is two
  `blocksouls_world:soulfire_seal` blocks: unbreakable, light 10, soul-flame particles. A water bucket used on either
  puts out every seal block touching it (cloud, smoke, extinguish sound) and empties the bucket (not in creative).
  The fortress records a SECRET slot tagged `soulfire_door` in front of the door so tests and `/bs floor goto` find it.
- **The exotic:** the vault chest (`chests/soulfire_vault`) holds cinder, gunmetal, a golden apple and ammo; guns adds
  an exotic at chance 1.0 through `exoticTables` (lane exception, guns). Guns' `CURRENT_DEFAULTS` goes to 2 with a
  generic migration: every default exotic table a file lacks is added (hand-set chances are kept), so later floor
  sessions only add their defaults line inside the marked block.
- **Director events** (world `InfernalEvents`, placed through `DirectorEvents.PLACE_EVENT`; the game lines are in
  `DirectorConfig`'s pool after Blight's, tagged `infernal`, lane exception):
  - `soulfire_altar` (weight 4): the game's blood altar on a 5×5 soul-soil pad with soul fire at the corners.
  - `lava_chest` (weight 4): the game's cursed chest on a 3×3 islet in a 3-wide lava moat with one crimson-plank bridge
    that burns into lava after `infernal.lavaChestBridgeSeconds` (60). The timer is not saved, like Skyrift's falling
    shard: a bridge standing at a restart stays.
  - `stranded_trader` (weight 3): a wandering trader on a basalt pillar in the same moat, no bridge (build one or
    drink a tonic). Permanent fire resistance, leaves after `strandedTraderMinutes` (6). On top of his vanilla offers
    he sells an Ashcap Tonic for `traderTonicCinder` (2) cinder and a Scorch Draught for `traderDraughtCinder` (3).
  - Existing director.json files get these through the migration: `CURRENT_DEFAULTS` goes to 4, which reruns Blight's
    revision-3 rule (add every default floor-tagged event a file lacks, by id and tags), so a file already at 3 gets them.
- **No wall-buy:** the spec's "fire-gun wall-buy" has no wall-buy system anywhere in the code to hang it on; skipped.
- **Mini-bosses:** `miniBossesByFloorTag.infernal` = Ashwyrm, Furnace Knight. There is no Hound-pack archetype in
  bosses or mobs, so the pool is those two.
- **Roster and affixes** (lane exception, mobs): the floor-2 roster (shambler, crawler, screamer, spitter, bloater,
  brute) already matches the spec, and roster tags are all-required (tagging a mob `infernal` would bar it from
  other floors), so the roster is unchanged. `weightsByFloorTag.infernal` = burning 5 makes burning the common affix.
- **Siege twist:** slice 1's lava rise already is the floor's siege twist; nothing more in this slice.
