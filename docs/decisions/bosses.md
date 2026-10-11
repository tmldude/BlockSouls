# Decisions: bosses (Stage 2a)

Defaults picked by the `bosses` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
How to build on the framework: `bosses/README.md`.

## Framework
- **Bosses extend `Monster`** with a hand-written fight loop in `customServerAiStep` instead of AI goals: the attack
  state machine owns movement while an attack runs, and only `FloatGoal` stays as a goal. Attacks advance through
  fixed-length stages, so they can't softlock; an attack may only end its active part early.
- **Attack choice:** weighted random among attacks that are off cooldown, in range and allowed (`canUse`); the
  previous attack's weight ×0.35 so moves rarely repeat back to back. 10 ticks between attacks. Cooldowns count from
  the end of recovery. Phase multipliers scale damage, cooldowns, recovery and chase speed.
- **Direction tracking:** the boss turns with its target for the first 70% of a windup, then locks, so dodging late
  works. Per-attack override (`trackFraction`).
- **Phases** move forward one at a time and only between attacks (a big burst still plays every transition, after
  the current attack finishes). Each transition is a 2 s invulnerable roar (`staggerTicks` 40); damage that bypasses
  invulnerability (`/kill`) still lands.
- **Targeting:** threat = damage dealt (×0.8 decay every 2 s re-evaluation) + up to 15 for being close (falls to 0 at
  16 blocks); a new target must beat the current one by 25%. Creative players fight (can engage by hitting, keep a
  fight alive) but are never targeted.
- **Engage:** a player hits the boss, or a survival/adventure player who isn't downed walks within the arena radius of
  its home. Everyone already standing in the arena joins; later arrivals join on entering. Proximity engage ignores
  creative so admins can fly around.
- **Health scaling** uses the most players seen in the fight so far (leaving or going down doesn't weaken the boss),
  recomputed as people join, keeping the health fraction. Resets go back to the 1-player value.
- **Reset:** nobody in the fight (alive, not downed, inside radius + 1.5) for 10 s → wall down, full heal, attacks and
  phase reset, walk home (teleport home if the walk takes over 10 s). The boss is invulnerable while walking home.
- **Anti-cheese:** while engaged, damage from players outside the arena is ×0.25 (bullets can cross the fog inward).
  Bosses ignore fall, suffocation, cramming and drowning damage, can't be leashed, never use portals.
- **Never despawn:** persistence required and `checkDespawn` is a no-op (also in peaceful).
- **Restart mid-fight = reset:** the wall's block list is saved with the boss and removed on the first tick after
  loading, health restored. Engaged state isn't saved.
- **Boss ids are the entity ids** (`blocksouls_bosses:furnace_knight` = `CommonIds.FURNACE_KNIGHT`).

## Fog wall
- **One-way blocks**, not entities: a ring (1 block thick, gap-free) of `blocksouls_bosses:fog_wall` with a
  `facing` property pointing inward. An entity whose body is fully past the inner face collides; anything else passes.
  Pure position math, so client prediction matches the server with no sync. Translucent texture with drifting ash
  particles, light level 4, unbreakable, no item, no drops.
- **Placement:** only air and replaceable non-fluid blocks become wall (never terrain or builds), 4 below to 10 above
  the boss's home. It remembers exactly what it placed and only removes blocks that are still fog wall.
- **Known limit:** the wall is 15 blocks tall, not a dome; wall-climbing or web-swinging over it is possible. Not
  worth a ceiling for now.

## Boss bar
- **No vanilla `ServerBossEvent` at all** (it would draw vanilla's bar too). The server sends our own payload to
  participants and to anyone within 48 blocks: on change, and a keepalive every second; the client drops bars it
  hasn't heard about for 3 s.
- **Look:** top center at y 26 (under the game's run-info HUD, which ends at y 22), 55% of screen width capped at
  256 px, name above, crimson fill, pale trailing chunk (holds 0.6 s, then drains at 60% of the bar per second; new
  hits restart the hold), dark notches at phase thresholds, 0.5 s shake + white flash when the phase goes up. Up to two
  bars, the fights you're in first, then nearest.
- **Music** rides on the bar payload: players in the fight hear the boss's music event looped (vanilla music is held
  off meanwhile). Placeholder: `music_disc.pigstep` at pitch 0.8 for the Furnace Knight.

## Lock-on
- **Middle mouse** (`key.blocksouls.lock_on`). Targets: bosses and elites (`#blocksouls:bosses`, `#blocksouls:elites`)
  within 32 blocks, within 60° of where you're looking, in line of sight; nearest first.
- **The brief's "key press breaks the lock" vs "the key again cycles"**: pressing the key while locked cycles to the
  next target (left to right) and unlocks only when there's no other target; **sneak + key always unlocks**. The mouse
  wheel cycles while locked (and doesn't move the hotbar then; number keys still do), with a 150 ms debounce.
- Camera eases 25% of the remaining angle per 1/60 s toward the target's chest (65% of its height), applied every
  frame right after mouse look, so the mouse still works against it. Breaks on 1 s without line of sight, beyond
  40 blocks, death, or spectator mode. Reticle: a pulsing diamond projected onto the target's chest.

## Multipart hitboxes
- **`BossPart` mirrors vanilla's `EnderDragonPart`** (an `Entity` never added to the level, ids = boss id + n). Two
  main mixins make them work outside the dragon: `Level.getEntities(Entity, AABB, Predicate)` adds them (crosshair,
  bullets, sweeps), `ServerLevel.getEntityOrPart` resolves their ids (attack packets). Bosses with parts are tracked
  per level through entity load/unload events on both sides.
- **The Furnace Knight's helm is a ×1.5 weak point** (0.9 × 0.8 box on top of a 2.5-tall body hitbox), so the helper
  is exercised by a real boss. Guns' own precision bonus doesn't stack: a part isn't a `LivingEntity`.

## Models
- **Vanilla `AnimationDefinition` keyframes**, one per attack and stage, baked lazily by `BossModel` and played from
  the synced stage clock (the client times stage changes itself: no per-tick sync). Procedural `idle` (walk, breath)
  runs first; keyframes add on top. `Anim` is a short builder over vanilla's keyframe API.
- Animation lengths are derived from the same `AttackStats` the server uses, so config changes keep them in step.

## Field-boss spawner
- **Two schedules:** `DAILY` (one roll per in-game day, `chancePerDay`; on a hit the spawner keeps looking for a spot
  that day) and `WATCH` (checked every 5 s scan, no roll). Rules are shuffled each scan; at most one spawn per scan.
- **"At most one roaming field boss"**: bosses tied to a place (`roaming() == false`, the Furnace Knight at its
  bastion) don't count, so a waiting guard doesn't block the Pale Rider. Each kind is out at most once at a time.
- **Killed this run** is cleared on `RunEvents.RUN_STARTED`. Leaving (or being discarded) starts the kind's cooldown.
  Live bosses are recorded in saved data; a record whose chunk is loaded but whose entity is gone is dropped.
- **Spawn distance:** ≥ 200 blocks from world spawn in the overworld only (the Nether/End aren't near spawn).
- **Announcement:** a raid horn played 24 blocks toward the boss (so it reads as "over there") for players within
  192 blocks, a visual-only lightning flash at the boss, and a campfire-signal smoke column above it every 4 ticks
  until engaged. Nothing in chat.
- **Without the game module** `Runs` reports `NOT_STARTED`, so field bosses never appear on their own
  (`requireActiveRun`, config). `/bs boss field roll` ignores that.

## Furnace Knight
- **Trigger:** a player in the Nether within 64 blocks of a bastion's center (searching loaded chunks only); no roll,
  5 min cooldown after it leaves. **"Entrance":** bastions have no marked entrance, so the knight stands just outside
  the bastion's bounding box on the side the player approaches from, on the first standable 2×2×4 spot along that
  line (falls back inward). Faces outward.
- **Stats:** 400 HP, armor 10, speed 0.26, fire-immune, knockback-immune, arena radius 18, hitbox 1.4 × 2.5 (+ helm),
  drawn 3.2 blocks tall.
- **Moves (P1):** slam 1 s windup, a fire line 8 long × 2 wide travelling over 4 ticks, 14 dmg, 1.2 s recovery; sweep
  0.8 s windup, 270° arc radius 4.5, only 0.9 blocks high (a jump clears it), 10 dmg; breath 1.2 s windup, 2 s of a
  50° × 5-block cone that turns 2.5°/tick after its target, 3 dmg per 0.5 s of contact + 4 s of fire; charge 1 s
  windup, 12 blocks in 0.6 s, 12 dmg + knockback, stops on hitting a wall, used only at 6–16 blocks.
- **P2 (below 50%):** roar, blade ignites (glow layer + flames), ×1.25 damage, recovery ×0.7, cooldowns ×0.7, chase
  ×1.15, adds fire pillars: 3–5 circles of radius 1.5 (one under each player in the fight first, the rest within
  5 blocks of them), 1.5 s windup, 10 dmg + 3 s fire.
- **Drops:** 4 blaze rods + 2 Ender Eyes (its own loot table), plus `boss_drops/furnace_knight_world` (1 blight core,
  needs world) and `boss_drops/legendary_gun` (one of hand cannon / auto rifle / shotgun / sniper rifle at legendary,
  needs guns) as extra tables. 50 XP. "Configurable" = a datapack overriding those tables.
- **Placeholder sounds** are re-pitched vanilla events (iron golem, blaze, ravager, furnace crackle); placeholder skin
  from `tools/assets/boss_skins.py` (dark steel + ember glow).
- **Fight length:** not play-tested in this sandbox (no client). Numbers are the brief's; tune `boss-furnace_knight.json`
  in Stage 3 if the fight runs outside 3–5 minutes.

## Contract additions
None: `CommonIds`, `CommonTags.BOSSES` and `BossEvents` already had what this needed.
