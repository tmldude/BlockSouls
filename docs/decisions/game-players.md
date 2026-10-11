# Decisions: game-players

Defaults picked while building downed/revive, boons and the run HUD (`docs/sessions/game-players.md`).
Tunable numbers live in `config/blocksouls/game-players.json` (`PlayersConfig`).

## Downed and revive

- **Who can go down:** a `ServerPlayer` not in creative/spectator while `Runs.get(server).isActive()`. Outside a
  run (no game-loop, `NOT_STARTED`, `OVER`) lethal damage is a vanilla death.
- **Invulnerable means everything but `bypasses_invulnerability`** (void, `/kill`), which is a vanilla death that
  clears the downed flag and boons. Starvation, drowning, fire and lava can't hurt a downed player.
- **Crawl:** a mixin on `Player.getDesiredPose` returns `SWIMMING` while downed (both sides, since the flag is
  synced). **Mob targeting:** a mixin makes `Player.canBeSeenAsEnemy` false while downed. Vanilla's
  `Mob.getTarget`/`setTarget`, `TargetingConditions` and the brain's `StopAttackingIfTargetInvalid` all check it,
  so mobs drop a downed player and won't re-target them, with no per-tick scan.
- **Speed −85%** (`ADD_MULTIPLIED_TOTAL` −0.85) and **jump strength −100%** so a downed player can't hop around.
  Both are transient modifiers re-added each tick while downed. Dropping items is still allowed (pass your gear).
- **Down persists across logout/restart** through a persistent `blocksouls_game:was_downed` attachment (common's
  `PlayerStatus` flag is synced but not saved). On rejoin outside an active run the flag is cleared instead.
- **Revive:** the client sends start/stop; start is re-sent every 10 ticks while use is held so an attempt the
  server cancelled (out of range) resumes on its own. Several players can revive the same target; the first to
  finish wins. Holding use on a downed player is consumed (`UseEntityCallback` → `SUCCESS`) so it doesn't eat
  food or use the held item. Range is feet-to-feet `distanceTo` ≤ 2.5.
- **Team wipe** is re-checked right after someone goes down **and once a second** (config), so a downed player
  whose last standing teammate logs off ends the run instead of being stuck. Run start and run over stand everyone
  up; run start also clears boons.
- A chat line "`<name>` is down" goes to everyone (mechanics only), with a low hurt sound.

## Boons

- **Data:** `data/<ns>/boons/<path>.json`, boon id `<ns>:<path>`, loaded by a Fabric `SimpleReloadListener`
  (`ResourceLoader.get(SERVER_DATA)`). `stackable` is an int (max level, default 1). Malformed files are skipped
  with an error log; unknown effect/attribute/item ids only warn (other modules' ids are absent in isolated runs).
- **Stacking:** effect amplifier +1 per extra level, attribute amount × level, trait level × boon level.
  Overlapping effects (Swift Feet + Afterimage) use the highest amplifier.
- **Effects** are infinite, ambient, no particles, icon shown, re-applied every second if removed (milk).
- **Attribute modifiers are permanent** (saved with the player) so +max health survives relogging without the
  health being clamped on load. They're re-applied on join and after `/reload`.
- **Going down clears effects (only infinite ones of the boon's types), modifiers and traits. Granted items stay:**
  a Sky Rider Elytra is gear like anything else.
- **Combos:** tier Legendary, colored purple in the HUD. Once their requirements are owned they can appear on a
  relic of **any** tier (config `combosAnyTier`), otherwise building them would hinge on rare Legendary relics.
  Combo cards never appear in a Wildcard list.
- **Card rolling:** distinct cards from the relic's tier (+ unlocked combos). If the tier runs dry, remaining slots
  come from lower tiers first, then higher. A Wildcard replaces the last card and needs ≥ 2 options in the tier.
  A relic with nothing left to offer isn't consumed (chat line says so).
- **Pending offers:** the server keeps a player's rolled cards until they pick, so closing the screen and using
  the relic again shows the same cards (no rerolling by reopening). Kept in memory; a restart re-rolls. Picking
  consumes one relic of that tier from the hands first, then the inventory.
- **Relics** are three items (`relic_common`, `relic_rare`, `relic_legendary`), stack to 16, rarity colors
  uncommon/rare/epic. Dropped at each standing in-run player's feet at `RunEvents.DAWN`, tier from `RunState.wave`.
- **Names (22 boons):** brief names kept except "Lucky" → **Four-Leaf**, "Strength I" → **Brawler**, "Haste II" →
  **Quickpick**, "Regeneration I" → **Mending Blood**. Moon Boots' "no fall damage" is `safe_fall_distance` +256.

## HUD

- **Boons row sits to the left of the hotbar** (past the offhand slot), growing leftward, 6 per row, wrapping
  upward, instead of above the hotbar: above-hotbar space is taken by health/armor, the held-item name and action
  bar messages. Level pips are drawn above stackable boons.
- **Run info** is top center; boss bars draw below it. Phase icons: sun (day), crossed swords (wave), hourglass
  (break), moon (quiet night), skull (run over). Countdown `m:ss` when `secondsToNext ≥ 0`, else a small skull + mobs
  left during a wave. The wave numeral turns red in endless mode.
- **Teammate markers** are projected onto the HUD from the camera (so they show through walls), pinned to the screen
  edge when off-screen, hidden past 128 blocks (config). Markers and the reviving bar use `addLast`, not the
  crosshair's slot, because the crosshair's render condition hides it in third person.
- Every element checks `gui.hud.isHidden()` (F1). Toggles in the `hud` section of `game-players.json` (each client
  reads its own file).

## Testing

- Gametest mock players need `handleAcceptPlayerLoad` (vanilla treats a not-yet-loaded client as invulnerable) and
  `abilities.invulnerable = false` (mocks report creative). `Downed.forceInRun(player)` makes a mock count as in
  an active run; forced players are left out of the real wipe check and the run start/over handlers so parallel
  game-loop tests are never affected. The wipe gametest asserts `Downed.isTeamWiped` on the mocks.
