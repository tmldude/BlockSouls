# game-loop decisions

Defaults picked by the game-loop session (run lifecycle, day/night, waves). Newest at the bottom.

## Day/night
- **Vanilla's clock rate, not our own accumulator.** 26.3 replaced day time with world clocks
  (`ServerClockManager`); each clock has a `rate` with a fractional `partialTick` accumulator, and the rate is synced
  to clients so the sky moves smoothly. The run sets the overworld clock's rate to `20 / dayLengthMinutes`
  (1.176 for 17 min). The clock already respects the `advance_time` game rule. `WaveMath.clockRate` and a unit test
  that replays vanilla's accumulator cover the math. The rate is set once a run exists (not on worlds without one).
- **Time stops while the server is empty.** Dedicated servers keep ticking for 60 s after the last player leaves, so
  the run pauses the overworld clock when nobody is online and unpauses it on join. It only undoes its own pause
  (`clockPausedByRun`), never an admin's `/time pause`.
- **Dusk/dawn are edges, not exact ticks.** The loop compares "in the night window" this tick with last tick, so time
  jumps (`/time set`, sleeping, `/bs time`) still fire exactly one `DUSK` / `DAWN`. A dusk with nobody online doesn't
  count (no `DUSK`, no night number) and its dawn fires no `DAWN`.
- **Dusk cue:** `ambient.cave` at pitch 0.5 for each overworld player at day time 11 500. The sky/fog tint is
  computed on the client from the synced dusk-cue/dusk/dawn times, so it ramps in smoothly from 11 500 to 13 000 and
  fades out over the last 1 000 ticks before dawn.

## Run lifecycle
- **Saved data lives in the server's world data storage** (`data/blocksouls_game/run.dat`) rather than "on the
  overworld" level: 26.3 has `MinecraftServer.getDataStorage()` for world-global data, which is the same file set
  the overworld used to own.
- **`/bs run new [difficulty]`** stores the next run's difficulty in `game-run.json` (`nextRunDifficulty`, used once
  by the next auto-start, then cleared), because the Docker entrypoint deletes the marker before the mod starts.
  Without an argument the next run keeps the current difficulty. The marker contains the difficulty id too, for
  humans. Docker is detected by `/etc/blocksouls.env` (written by our image); elsewhere the command only logs.
- **`/bs run start` while a run is active** discards its wave mobs and starts over (dev convenience). Spectators
  (from a finished run) are put back in survival.
- **Players joining a finished run** go to spectator too.
- **"Waves survived"** in the summary doesn't count a wave the team died in.

## Waves
- **Roster shuffle without breaking the contract:** candidates come from `WaveMobs.eligible(wave + 1)` and are kept
  if their shifted first wave (±1, seeded per run, never below 1) is ≤ the wave. If the shuffle leaves a wave with
  no candidates, the unshuffled roster is used; if that's empty too, vanilla zombies.
- **Spawning trickles in** at up to 3 mobs per tick (max 30 alive). The spawner tries the anchor player's height
  first (±8 blocks, so caves and bases work) and then the surface, picks the darkest of up to 16 candidates and
  accepts the first spot at light ≤ 7. A spot that can't be found just retries next tick.
- **Elites** are `WaveMobSpec.elite()` or `CommonTags.ELITES`, tagged `blocksouls_wave_elite`. At dawn they move to a
  saved "hunters" list that keeps re-targeting the nearest player once a second until they die.
- **Ordinary wave mobs at dawn** are set on fire and discarded after 5 s with a puff of smoke. Wave mobs that load
  in from disk when they're no longer tracked (e.g. after a restart mid-burn) are discarded; tracked elites resume.
- **Straggler respawn** also covers mobs in unloaded chunks (they count as "far from every player").
- **Nobody in the overworld at dusk:** the counter advances by the night's budget and `WAVE_STARTED` /
  `WAVE_ENDED(wave, false)` still fire for each skipped wave, so listeners (boons, the pistol break) stay in step.
- **Targets:** wave mobs target the nearest survival/adventure player in the overworld (creative players are
  present for wave size, but vanilla AI ignores them anyway).
- **`/bs wave skip`** only advances the counter (no events), apart from breaking pistols if it passes wave 3.

## Drops
- **Elites always drop** (rare or better), ordinary wave mobs drop with 35%. Blood Moon shifts one tier up first,
  then the elite floor applies.
- **Base pools** use only vanilla items; `wave_drops/*` are `entity` tables (rolled with the mob's death context so
  other modules' conditions work), `wave_clear_reward` is a `chest` table set on the chest (rolled when opened).
- **The reward chest** goes on the nearest free spot (air above a sturdy block) within 4 blocks of the last killer,
  else of any overworld player; nothing is placed if there's no free spot.

## Starter kit
- **The pistol-break line:** "Your Starter Pistol fell apart. It had a good run." with the item-break sound
  (pitch 0.6) and item particles.
- **Late joiners after the pistols broke** get nothing; joining players who still hold one lose it then.

## Client
- **Sky/fog via one mixin on `EnvironmentAttributeProbe.getValue`** (the camera's and sky renderer's view of
  environment attributes) and one `@ModifyArg` on `SkyRenderer.renderMoon`'s color for the red moon. Tints: dusk 18%
  toward a dusky violet; Blood Moon 60% toward deep red. Fog nights lerp fog end to 24 blocks.

## Game tests
- Each run game test has its own test environment (an empty `all_of`), because vanilla runs a batch per environment
  one after another. The run is global state, so this keeps run tests from overlapping. A test mod registers a plain
  item under `blocksouls_guns:starter_pistol` when guns isn't loaded, so the kit and the pistol break are testable.
