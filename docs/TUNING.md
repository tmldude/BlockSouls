# Tuning map

Which config field to turn for which feel complaint. Every file is `config/blocksouls/<file>.json` on the server
(in single player: the game's own `config/` folder). Defaults below are the shipped values; check the file for the
current ones.

## The live loop

1. Edit the JSON and save.
2. In game (op): **`/bs config reload movement`** (or `/bs config reload` for every file). The new values apply at
   once; movement and gun stats are re-sent to every connected player.
3. Bad JSON keeps the old values and says why in chat. A key you delete goes back to its default. The file is not
   rewritten by a reload, so your editor's copy stays as you left it.

`/bs move config reload` still works and does the same for `movement.json` only.

## Web swing — `movement.json` → `web`

| Complaint | Field | Default | Turn it |
|---|---|---|---|
| Rope feels too short / too long | — rope length is the distance to where the web lands, at least `minLength` | 3 | Raise `minLength` for longer short-range swings |
| Rope feels like a rigid stick / too elastic | `ropeSlack` | 0.05 | Up = the rope only pulls once really stretched (looser, more fall before the catch); down = snappier |
| Swing feels sluggish, doesn't build speed | `swingBoost` | 0.04 | Up = more speed added per tick on the down-arc |
| Swing bleeds speed / never slows | `drag` | 0.99 | 1.0 = no drag; 0.97 = noticeably damped |
| Too floaty / too heavy while swinging | `gravityMult` | 1.0 | Below 1 = floatier arcs; above 1 = snappier, faster swings |
| Letting go doesn't fling you up enough | `releaseBoost` | 0.15 | Up = higher pop on release |
| Top speed too low / too high | `maxSpeed` | 2.5 | Blocks per tick (2.5 ≈ 50 m/s) |
| Webs miss / can't reach far enough | `range` | 32 | Blocks; the server allows `range × serverRangeTolerance` (1.1) |
| Aiming slightly too low misses | `fallbackRays`, `fallbackRayStepDegrees` | 3, 6 | More rays / bigger steps = more forgiving aim upward |
| Reeling in too slow | `reelSpeed` | 0.3 | Blocks of rope per tick while holding sneak |
| Fall damage right after letting go | `noFallTicksAfterRelease` | 10 | Ticks (20 = 1 s) |
| Too few swings before running dry | `staminaCost` (and `stamina.regenPerSecond`) | 10 (15) | Per web shot |
| Server drops the swing on lag | `serverSlack` | 4 | Blocks past rope length before the server cuts it |

## Jump — `movement.json` → `jump`

| Complaint | Field | Default | Turn it |
|---|---|---|---|
| Tap jump too high / too low | `tapJumpMult` | 0.85 | × vanilla jump; holding adds on top |
| Holding jump doesn't add enough height | `holdBoost` | 0.03 | Upward speed added per tick while held and rising |
| Held jump too long / too floaty | `maxHoldTicks` | 8 | The hold cap: ticks the boost can apply |
| Falls feel floaty (or too heavy) | `fallGravityMult` | 1.6 | × vanilla gravity while falling; 1.0 = vanilla |
| Falls get too fast | `maxFallSpeed` | 3.9 | Extra fall gravity stops adding past this |
| Walking off a ledge eats the jump | `coyoteTicks` | 3 | Ticks after leaving a ledge a jump still counts |
| Jump pressed just before landing is lost | `jumpBufferTicks` | 5 | Ticks a press is remembered until landing |
| Triple jump hard to chain | `tripleJumpWindowTicks` | 10 | Max ticks on the ground between chained jumps |
| 2nd / 3rd jump not rewarding enough | `secondJumpBonus`, `thirdJumpBonus` | 0.15, 0.30 | Launch bonus on top of the tap jump |
| Don't want the chain at all | `tripleJump` | true | |

Related: `roll` (dodge speed, i-frames `iframeStart`–`iframeEnd`), `climb` (`climbSpeed`, `ledgeGrabHeight`,
`ledgeBoostUp`), `stamina` (`max`, `regenPerSecond`, `regenDelayTicks`).

## Waves and pacing — `game-run.json`

| Complaint | Field | Default | Turn it |
|---|---|---|---|
| Not enough breathing room between waves | `waveBreakSeconds` | 20 | Seconds between waves in a night |
| Nights too busy / too empty | `wavesPerNight` | [1, 1, 2, 2, 2, 2, 2, 3] | By night number; the last value repeats. Applies from the next dusk |
| Endless mode ramps too fast | `endlessExtraWavesPerNight` | 1 | |
| A wave starts right before dawn | `minNightLeftSeconds` | 60 | A new wave needs this much night left |
| No warning before night | `duskCueTime` | 11500 | Day time (dusk is `duskTime` 13000, dawn `dawnTime` 23000) |
| Days / nights too long | `dayLengthMinutes` | 17.0 | Real minutes per full cycle (vanilla 20); applies within ~5 s |
| Waves too big / too small | `baseCount`, `countPerWave` | 6, 3 | Before party and difficulty scaling (`party.json` → `waveSize`) |
| Mobs too tanky as waves climb | `healthPerWave`, `damagePerWave` | 0.08, 0.05 | Applies to mobs spawned after the reload |

Party-size curves live in `party.json` (`waveSize`, `aliveCapBase`/`aliveCapPerPlayer`/`aliveCapMax`, `elites`,
`bossHealth`); threat curves in `threat.json`. Both reload live.

## What a reload can't change

These are read once when the game registers content, so they need a server restart:

- Mob and boss base attributes (health, armor, speed, damage in the attribute map): `mobs.json` stats, each
  `boss-*.json`'s `health`/`armor`/`speed`, the Wraith Steed in `boss-pale_rider.json`.
- Gun aim-down-sights move speed (`guns.json` → `shooting.adsMoveSpeed`).
- World generation: Nether spawn weights (`mobs.json` → `infectedPiglin`), wreck/checkpoint spacing (`world.json`).
- Boss phase thresholds and phase multipliers (built into each boss's definition at startup).
- Wave mob roster `minWave` / `weight` (`mobs.json`).

Loot chances (`guns.json` → `loot`, `world.json` loot fields) are baked into loot tables: after reloading the config,
run vanilla **`/reload`** too.

Per-run and per-entity values apply to the next one: difficulty presets (next run), boss XP and weak-point
multipliers (next spawn), kart max health/fuel (next kart).

Client-only settings that aren't synced (boss and mob model tweaks, HUD flags in `game-players.json`, kart driving
physics in `vehicles.json`, client fog) use each player's own file: a server reload reaches them only in single
player.
