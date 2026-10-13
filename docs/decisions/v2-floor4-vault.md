# Decisions: v2-floor4-vault (Stage 6C)

Defaults picked by the `v2-floor4-vault` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Spec: the "Floor 4 — The Vault" block of `docs/design/floors.md`. Code: `world/.../floor/BoxLayout.java` and
`BoxPlanner.java` (pure, unit-tested), `VaultBuilder.java`, `VaultWallBlock.java`, `VaultHazards.java`, `VaultConfig.java`.

## PR 1: template pool, terrain, hazard

### Templates and shape
- **`shape: "box"` on the template schema** (default `"island"`, so every existing template parses as before). A box
  template also takes `height` (default 24, at least 12) and a `box` block of layout knobs (rooms, shortcuts, dead
  ends, closets, corridor width/height, room/hall height, wall-node share, mossy/cracked shares). Box slots must sit
  inside the walls (`max(|x|,|z|) <= radius - 3`); island templates keep the circle check.
- **The island planner is untouched.** `FloorPlanner.plan` hands box templates to `BoxPlanner`, which keeps the same
  contract (party scaling of size/nodes/crates, quarter turn + mirror, sorted slots, escalation tags). `FloorPlan` is
  unchanged: the builder regenerates the identical `BoxLayout` from the plan's room slots and the seed.
- **Pool `blocksouls_world:vault`, three templates:** `vault_halls` (weight 2, the baseline), `vault_crypt` (weight 2,
  more rooms, closets and dead ends) and `vault_cistern` (weight 1, fewer rooms, more shortcuts, mossier, scrap-heavy).
  All floor 4 only, radius 40 (an 81×81 box at 1 player; `floorSize` grows it like the islands), ground Y 60, floor
  block deepslate tiles, no trees.
- **The empty structure slot defaults to tag `vault`** (the locked vault room; PR 3 fills it) and the empty secret
  slot to `hollow_wall`, instead of drawing from `FloorsConfig.structures`/`secrets` (those are island set pieces).

### Layout (BoxLayout)
- **Rooms per room slot** (camp, exit portal, structure, mini-boss, event, secret), half-size from the slot radius;
  the camp and mini-boss rooms are tall halls (`hallHeight` 9), others `roomHeight` 6. Filler rooms (`rooms` total)
  pad it out and take the loose nodes and crates.
- **Corridors:** a spanning tree (Prim's, from the camp) carved as L-shaped corridors 3 wide, 4 high; each corridor
  picks the L that keeps clear of the secret room. **Shortcuts** are extra non-tree corridors sealed mid-way by a
  cracked-brick plug (the cracked texture is the only tell). **Dead ends** are 6–12 long stubs into solid rock.
- **Plugs only sit where they can't be walked around:** a plug's cross-section must be in a straight run of `half + 1`
  cells either way and its square *and walls* must have been solid before the corridor was cut (otherwise a corner's
  carve or a room flush against the tunnel opens a way past it; found by the reachability test).
- **Secret:** the secret room plus a tunnel to the nearest room, sealed by a hollow-wall plug at the last solid
  straight stretch; with no such stretch, the secret room's whole rim is hollow wall.
- **Mob closets:** a 3×3, 3-high pocket behind a one-column plug in a corridor's side wall.
- **Nodes:** template node slots are kept only where the layout is open (a rotated slot can land in rock); the rest
  are scattered, `wallNodeShare` (0.4) of them **set into a corridor's wall face** (the node replaces the brick, rock
  behind it, so it's visible and hittable) and the rest in filler rooms. The spec's "buried in walls" is read as
  "embedded in the walls", not "behind a brick", because plain vault walls don't break (below).
- **Siege points** are corridor cells outside the camp hall, farthest from the camp first and spread apart, so the
  waves come down the corridors (the siege twist; barricade kits matter at the corridor mouths).

### Terrain and walls
- **A bedrock shell** (floor under, lid at `groundY + height`, the four sides one column out) with vault bricks
  everywhere the layout is solid. Three wall blocks, plain/mossy/cracked vault bricks (variant from a position hash,
  shares from the template), all unbreakable (`strength -1`), no items, no loot, no mob spawns on them. Placeholder
  models reuse vanilla deepslate bricks / mossy stone bricks / cracked deepslate bricks textures.
- **Plain vault walls never break**: hitting one just rings. Only plug walls break, after `wallHits` (3) hits, which
  is what makes the shortcuts, closets and the secret mean something.
- **Each wall block carries `content`** (none/shortcut/closet/hollow) up to corridor height, so a burst collapses the
  whole connected plug (flood fill, `plugCollapseLimit` 64) and runs what it hid.
- **Hollow wall tell:** a duller, lower "pot" sound on each hit (pitch 0.6) and water drips on its faces that open onto
  air (client `animateTick`) plus drip particles when hit.
- **Closet:** bursting a closet plug releases `closetSwarm` (4) silverfish × `PartyScaling.directorBurst(players)`
  (players within `closetPartyReach` 48, clamped 1–8) plus `closetBrutes` (1) zombie, all angry at whoever broke it.
  All in `config/blocksouls/vault.json`.
- **Lighting:** dark except lanterns in the camp hall's corners. Monsters may spawn naturally in the dark corridors
  (the floors dimension's monster spawn light level is 0); left as-is, it's the floor's mood. Revisit if it swamps
  the Director.
- **`PlacedFloor` radius for a box** is `ceil((R + 1) * √2)`, so `contains()` covers the corners of the square.

### Wiring and commands
- **Game lane exception:** `RunConfig.defaultFloorActs` gives floor 4 pool `blocksouls_world:vault` (marked block).
  Existing `config/blocksouls/run.json` files keep their stored pools until regenerated.
- **Commands:** `/bs floor open <act> <players> vault` (main's pool argument) opens a vault. The vault registers
  `closet`, `hollow` and `shortcut` with `FloorHazards`, so `/bs floor hazard <trigger>` bursts the nearest such wall
  within 48 blocks (a closet with none near releases one at you). `/bs floor goto secret` stands you in the vault's
  secret room (the Y+0 opening offset now only applies when the slot block is air).
- **Tests:** `VaultPlannerTest` (300-seed stress-checked, ships at 40) checks at 1 and 8 players that every slot is
  in bounds and in the open, every room is reachable from the camp, the secret and closets are sealed until their
  plug breaks, shortcuts show up, and 8 players get a bigger box, more nodes and bigger closet swarms. The surface
  "every slot kind" test now only runs on `blocksouls_world:surface` templates (the vault has no cave or wreck).
  `VaultGameTests` opens a vault, pops a node, checks a plain wall holds, bursts the hollow wall and a closet.
- **The secret's hidden stair goes up** (the box has headroom under the lid, none under the floor); PR 3.
