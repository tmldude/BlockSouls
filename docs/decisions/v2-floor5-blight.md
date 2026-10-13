# v2-floor5-blight session decisions

Defaults chosen while building floor 5, the Blighted Surface (brief `docs/sessions/v2-floor5-blight.md`, spec
"Floor 5 — Blighted Surface" in `docs/design/floors.md`). Same style as `DECISIONS.md`. Where this file conflicts with
a plan file, this file records what was actually built.

## Slice 1: template pool, look, hazard

### Templates and pool

- **Three templates** sit in pool `blocksouls_world:blight`: `blight_crossroads`, `blight_ridge` and
  `blight_scrapfield`.
  - Each keeps its Surface counterpart's layout and slot positions, so the floor reads as "the Surface, gone wrong".
    It also keeps a known-good build (every slot kind, slots on the island).
  - `minFloor` = `maxFloor` = 5.
  - Tags: `blight`, `more_elites`, `elite_affixes`, `mean_mini_boss`, `stingy_nodes`, plus a per-template flavour tag.
  - The escalation tags would come anyway at floor 5. They are written out so the pool also works if
    `/bs floor open` draws it at another floor number.
- **Structure slot = `outpost`** (the broken outpost) on all three. Slice 3 turns it into the nest.
- **The secret slot is untagged** for now, so the planner fills it from `floors.json` `secrets`. Slice 3 gives it the
  grave tag.
- **Node mix:** Surface's mix for now. Slice 2 adds `blight_crystal`.
- **Act 5 uses the pool.** `RunConfig.defaultFloorActs` sets act 5's pool to `blocksouls_world:blight` (an `if (floor == 5)`
  line in the marked floor-pool block, beside Skyrift's floor 3).
  - There's no `defaultsVersion` migration. Floor acts are unreleased v2 config, and stage-6 sibling floors edit the
    same list.
  - An existing `config/blocksouls/run.json` keeps Surface on act 5 until it's deleted or edited.

### Look

- **Biome `blocksouls_world:blight`:**
  - Purple fog (`#4a2a5c`), fog from 8 to 72 blocks.
  - Dark sky (`#2a1836`), so it is dim even at noon.
  - Sky light: `sky_light_factor` 0.55 and a violet `sky_light_color`.
  - Dead olive-brown grass and foliage.
  - White ash plus a few warped spores in the air. No music.
  - `gameplay/sky_light_level` can't be set per biome (it isn't positional), so "dark at noon" is visual. Mob
    spawning still follows the Director, not light.
- **Painting the biome:** `FloorTemplate.biome` names it. Skyrift (#68) added the same field and its painter
  (`SkyShards.paintBiome`), so the blight floors use that; this slice's own painter was dropped in the rebase.
  - Islands sit 512 blocks apart and grid cells are never reused, so whole chunks never spill onto a neighbour.
  - An empty `biome` (every Surface template) skips painting.
- **`terrain.topMix`:** weighted top blocks (coarse dirt, podzol, dead grass, rooted dirt, gravel or mud by template).
  - The block is picked by a hash of the column in 2×2 clumps, not by the build's random stream. So Surface floors
    (no `topMix`) build block-for-block as before.
- **`decor`** (null on Surface templates):
  - `trees: "dead"`: bare dark-oak trunks of 3–5 logs with a stub branch, instead of the oak feature. Count is the
    Surface count + 6.
  - 10 patches (radius 3) of blighted netherrack, with blight growth on 35 % of them. These are the "tendrils".
    Blight growth only survives on blighted netherrack, which is why it's patches and not a scatter.
  - Dead bushes, short and tall dry grass scattered on open ground.
  - Patch and plant counts scale with the island's area (party size).
  - Decor runs after everything else, so it never shifts slot contents.

### Hazard ("danger scaling")

The hazard lives in **game** (`run/director/FloorHazards`, config `config/blocksouls/floor_hazards.json`). It's a lane
exception: the Director already owns the day and day mobs, and timed area effects can't be expressed as world-side
data.

- **Keyed by floor tag:** `byTag.blight`. The first tag the current act's floor carries wins. Other floors can add
  entries without code.
- **It runs inside the Director's per-second loop.** So it's off whenever the Director is off (classic runs,
  `enabled: false`).
- **State is live-only.** A restart mid-day starts the timers over. Field bosses already on the island stay; the
  hazard just stops tracking them.

#### Day-mob density

- `byTag.blight.dayMobs` replaces `director.json`'s `dayMobs` on the floor:
  - a pack of 4 every 30 s (normally 2 every 70 s);
  - 35 % elite (normally 20 %);
  - at most 24 alive (normally 10).
- Pack and cap still scale by `PartyScaling.directorBurst`.
- DayLoop change: one line, `FloorHazards.dayMobs(server, config().dayMobs)`.

#### Roaming field bosses

- Configured bosses: `blocksouls_bosses:blightheart` (it arrives in slice 3; until then the id is unknown and skipped)
  and `blocksouls_bosses:pale_rider`.
- Each listed boss walks at most once at a time, with at most 2 out.
- **Spawning:**
  - They appear 30 s into the day, then are re-checked every 180 s.
  - Only by day. Once out they stay through the night.
  - Placement: between 24 blocks from the camp (and from every player) and 85 % of the island radius.
- **Wandering:** every 5 s, a boss with no target and an idle path walks an 18-block leg at speed 0.8. The leg is
  clamped to the island.
- **Health** comes from the bosses module (`PartyScaling.bossHealth`). The count is not party-scaled; the brief says
  two.

#### Zone problems

- **Timing:** the first comes 60 s into the day, then one every 100 s, by day only (not during the siege).
- **The list is data:** `{id, type, weight, seconds, …}`, with `type` one of `storm`, `surge` or `dead_camp`.
  - `blight_storm` (weight 3, 45 s):
    - A circle of radius 9 × `floorSize` rolls in from the island edge opposite a random player and drifts toward
      them at 0.9 blocks/s.
    - When it arrives it picks another player.
    - It deals 2 magic damage per second to anyone inside.
    - Witch and ink particles show the circle. The centre stays inside the island minus half the radius.
  - `spawn_surge` (weight 2): an instant Director pack of 6 × `directorBurst`, 30 % elite.
  - `dead_camp` (weight 1, 60 s): the shopkeeper refuses ("fled the blight"). Economy's SHOPKEEPER case checks
    `FloorHazards.shopClosed` (one line). Wagers go through the shopkeeper too, so they're closed as well.
- **Announcements:** each problem is announced in chat (lang `message.blocksouls_game.hazard.<id>`) with a warden
  heartbeat.

### Debug commands

- `/bs director hazard <trigger>` fires `blight_storm`, `spawn_surge` or `dead_camp` (by id or type) now. `bosses` sends
  out the field bosses.
  - It sits under `/bs director`, not `/bs floor hazard`: Skyrift (#68) already owns `/bs floor hazard <trigger>` for
    world-side floor triggers, and game can't add to world's registry (no cross-module imports). Two `trigger` argument
    nodes under one literal would clash.
- `/bs floor open <act> [players] [pool]` takes an optional pool name, e.g. `blight`.
- `/bs floor goto <kind>` (already there) now suggests slot kinds.
