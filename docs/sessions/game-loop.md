# Session: game-loop (Stage 1)

**Module:** `game`. **Branch:** `claude/game-loop`. **PR title:** `game: run loop, waves, day/night`.

You build the clock that drives a BlockSouls run: the run lifecycle, the short day/night cycle, the night waves,
wave drops, the starter kit, sleep and dimension rules, difficulty presets and endless mode. Read
`docs/sessions/README.md` (protocol) and PLAN.md §0b first; this brief turns §0b into numbers.

## You own
- `game/src/main/java/.../game/run/**` (start from the stub `RunSystem`; keep `init()` and `endRun(server)`).
- `game/src/client/java/.../game/client/run/**` (`ClientRun` exists: fill it from your sync payload; sky/fog/sound cues).
- `game/src/main/resources/data/blocksouls_game/**` except `boons/` (that's game-players).
- `game/src/test/**` and `game/src/gametest/**` files named `Run*`/`Wave*`.
- Shared with **game-players** (same module, parallel): `BlockSoulsGame*.java` (one `init()` line each, already
  there), `fabric.mod.json`, `assets/blocksouls_game/lang/en_us.json` (add keys; expect a trivial merge).

**game-players** owns `game/player/**`, `game/client/hud/**`, the HUD, downed/revive and boons. Its HUD reads
`ClientRun.get()` for the wave/phase/timer, and it calls `RunSystem.endRun(server)` when everyone is down.
Don't draw HUD elements yourself.

## Contracts
- **Install** `Runs.setProvider(...)` in `init()`. Fire every `RunEvents` event at the right moment (server thread).
- Spawn wave mobs only through `WaveMobs.eligible(wave)` (weights; `WaveMobSpec.factory`). If the registry is
  empty (mobs mod absent, e.g. your own gametests), fall back to vanilla zombies so the loop still runs.
- Listen to `BossEvents.KILLED` with `CommonIds.FINAL_BOSS` → endless mode. Count every boss kill for the summary.
- Wave drop tables: `CommonIds.WAVE_DROPS_COMMON/RARE/LEGENDARY`, `WAVE_CLEAR_REWARD`. **You create these loot
  tables** in data with vanilla-only base pools (food, arrows, iron/gold nuggets, bread, torches); guns and world add
  their own pools with `LootTableEvents.MODIFY`.
- Starter Pistol = `CommonIds.STARTER_PISTOL`, looked up in `BuiltInRegistries.ITEM` (absent → skip it, log once).
- Elites = `CommonTags.ELITES`, wave-only mobs = `CommonTags.WAVE_MOBS`.

## Run lifecycle
- State persists in a `SavedData` on the overworld: `RunState` + wave bookkeeping + set of players who got the
  start kit + run start game time + bosses killed + the night modifier roll. Survives restarts.
- **Auto-start:** the first time a player joins a world with `NOT_STARTED`, start the run with the config's default
  difficulty. `/bs run start [difficulty]` starts it explicitly (also restarts a finished run in dev).
- **Phases** (`RunPhase`): `DAY` → at dusk `WAVE` → `WAVE_BREAK` (between waves) → `WAVE` ... → `NIGHT_QUIET` (the
  night's waves are done) → at dawn `DAY`. `OVER` after `endRun`.
- **`endRun(server)`**: phase `OVER`, stop and discard active wave mobs, fire `RUN_OVER`, show every player the
  **run summary** (title + chat lines, mechanics/stats only): waves survived, nights, bosses felled (names),
  run time, difficulty. Players go to spectator. Waves never restart until a new run.
- **`/bs run new`**: writes the marker file `blocksouls-new-run` in the server directory (the Docker entrypoint wipes
  the world on next start when it sees it) and stops the server; compose restarts it. In dev (no Docker) just log
  that the world must be deleted by hand. Optional arg `[difficulty]` is written into the marker and becomes the
  new run's difficulty (add a line to `docker/entrypoint.sh` only if needed — prefer reading the marker contents
  from your mod on next start before the entrypoint deletes it; if that's not possible, write the difficulty into
  `config/blocksouls/game-run.json` instead).
- Append one JSON line per finished run to `blocksouls-runs.log` in the server directory.

## Day/night
- Full cycle **17 minutes** (config `dayLengthMinutes`, default 17.0). Vanilla advances 1 day-time tick per tick;
  add the fractional extra (`20/17 - 1` ≈ 0.176) via an accumulator so the day speeds up uniformly. Check how 26.3
  stores day time (search vanilla for `setDayTime` / clocks / the `advance_time`-style game rule) and respect the
  daylight game rule if it's off. Only advance while at least one player is online (vanilla's pause-when-empty
  already stops ticking on dedicated servers; double-check).
- **Dusk** = day time 13 000 (mod 24 000), **dawn** = 23 000. Fire `DUSK(night)` / `DAWN(night)`; `night` counts up
  from 1 and only counts nights with someone online.
- **Dusk cues (show, don't tell):** at day time 11 500 play a distant ominous sound to every overworld player
  (a vanilla sound event pitched down is fine for now) and start tinting the sky/fog slightly; at dusk the
  wave starts.

## Waves
- One server-wide wave counter that only counts up (`RunState.wave` = number of the current or last wave).
- **Per night budget:** `wavesPerNight` config list, default `[1, 1, 2, 2, 2, 2, 2, 3]` (last value repeats);
  endless adds +1. Wave N+1 starts **20 s** (config) after wave N is cleared, if the budget allows and at least
  **60 s** (config) of night is left. Otherwise phase `NIGHT_QUIET`.
- **Dawn ends the current wave** (`WAVE_ENDED(wave, false)`): ordinary wave mobs are set on fire and discarded
  after 5 s; **elites** (`CommonTags.ELITES`) keep hunting until killed (keep them persistent, keep targeting).
- **Size:** `count = round((6 + 3·wave) · playerFactor · difficulty.waveSize · modifierFactor)`,
  `playerFactor = 0.6 + 0.4·onlineOverworldPlayers`. Max **30 alive** at once (config), the rest trickle in as
  others die. Spawned 24–40 blocks from a random overworld player, on a valid standable surface, not in water or
  inside blocks, not within 12 blocks of any player, preferring dark spots. Wave mobs target the nearest player,
  follow range 64, are persistent while the wave runs.
- **Scaling** (applied by you as attribute modifiers after the factory): max health ×(1 + 0.08·(wave−1)) ×
  `difficulty.mobHealth`; attack damage ×(1 + 0.05·(wave−1)) × `difficulty.mobDamage`. Put these formulas in a
  pure class (`WaveMath`) with unit tests.
- **Roster:** pick by `WaveMobSpec.weight` among `eligible(wave)`. Elites: at most `1 + wave/4` per wave. Each run
  shuffles the roster order within tiers: per-run seeded random offset of ±1 on each spec's `minWave` (never
  below 1), stored in the save.
- **Block breakers:** `WaveContext.breaksBlocks` is true for `min(0.10 + 0.02·(wave−1), 0.40)` of spawns (config).
  The mobs mod makes them actually break blocks.
- **Stragglers:** a wave is cleared when all its mobs are dead. Mobs that end up >96 blocks from every player for
  30 s are discarded and respawned near a player; when ≤3 remain for 45 s they get Glowing.
- **Nobody in the overworld at dusk** (all in the Nether/End): the night still counts, and the wave counter
  advances by that night's budget with no mobs and no loot (hiding isn't an escape).
- `WAVE_STARTED`/`WAVE_ENDED` titles are allowed but minimal: just the wave number, big, briefly (a horn sound).

## Night modifiers (rolled per night, shown through the world)
- **Blood Moon** every 4th night (config): +50% count, drops roll one tier higher, red sky/fog tint and red moon on
  the client (client mixin or Fabric sky/fog hooks; check what 26.3 exposes).
- **Fog** (`blocksouls_game:fog`): client fog distance down to ~24 blocks for overworld players.
- **Sprinter** (`blocksouls_game:sprinter`): wave mobs +30% movement speed.
- Non-Blood-Moon nights roll one modifier with 40% chance (config). Ids go in `WaveContext.nightModifiers` and in
  the sync payload. No modifier text anywhere.

## Drops and rewards
- Each wave mob killed by a player rolls: 35% a drop from the wave's tier table (config). Tier odds by wave:
  waves 1–3 common only; 4–6: 80/20 common/rare; 7+: 65/30/5 common/rare/legendary (config table; Blood Moon
  shifts one tier up). Elites always drop from rare-or-better.
- Clearing a wave places a reward chest (`WAVE_CLEAR_REWARD`) near the player who got the last kill (on a free
  spot; never overwrite blocks), with a sound and particles.

## Start kit and Starter Pistol
- Players online when the run starts: stone pickaxe + Starter Pistol. Anyone joining later in an active run who
  never got a kit: Starter Pistol only, and only while wave ≤ 3.
- **When wave 3 ends** (cleared or dawn): remove every Starter Pistol from online players' inventories (incl. ender
  chest and cursor/crafting slots) with the item-break sound, particles and one short silly chat line. Players who
  were offline lose theirs on next join. Track it in the save.

## Sleep and dimensions
- No sleeping while a wave is active or in a break (use Fabric's sleep events; a short "You can't sleep now"-style
  vanilla message). Allowed in `NIGHT_QUIET`; sleeping through skips to dawn as usual (fire `DAWN`).
- Waves only spawn in the overworld (see "Nobody in the overworld" above).

## Difficulty
- Presets in config (`difficulties` map): `easy` (0.75/0.8/0.75), `normal` (1/1/1), `hard` (1.25/1.25/1.2),
  `nightmare` (1.6/1.6/1.5) for waveSize/mobHealth/mobDamage. Chosen at run start only.

## Endless
- On `BossEvents.KILLED` for `FINAL_BOSS`: `endless = true`, fire `ENDLESS_STARTED`, a big title once. Waves
  continue forever; budget +1 per night; scaling continues.

## Client sync
- A clientbound payload with `RunState`, seconds-to-next, mobs left, night modifiers; send on change + every second
  during countdowns, and on join. Fill `ClientRun` on receipt.

## Debug commands (`/bs ...`)
`run start [difficulty]`, `run new [difficulty]`, `run state`, `run over`, `run endless`, `wave start [n]`,
`wave end`, `wave clear` (kills current wave mobs), `wave skip` (advance counter), `time dusk|dawn|night|day`,
`night modifier <id|none>`.

## Config
`config/blocksouls/game-run.json`, class `game/run/RunConfig` (game-players has its own `game-players.json`).
Every number above.

## Tests
- Unit: `WaveMath` (count, scaling, breaker share, tier odds, budget per night), day-length accumulator, night
  modifier roll (seeded), roster shuffle.
- Gametests: run auto-starts on a mock player join and grants the kit; `/bs`-equivalent API starts a wave and spawns
  mobs (vanilla fallback); clearing all mobs ends the wave and chains the next after the break; dawn ends the wave
  and keeps an elite; wave 3 ending removes the Starter Pistol (use any item registered under that id or skip if
  absent); `endRun` sets `OVER` and fires `RUN_OVER`.

## Done when
CI green; `./gradlew :game:runServer` with a client: a run starts on join, dusk cue plays, wave 1 starts at dusk,
mobs come, clearing chains waves per the budget, dawn pauses, the counter persists across a restart; all debug
commands work; `docs/decisions/game-loop.md`.

## Out of scope
HUD drawing, downed/revive, boons (game-players). Mob behavior/models (mobs). Gun items (guns).
