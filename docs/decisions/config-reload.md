# Config reload session (v0.11) decisions

Defaults picked while adding `/bs config reload` and the tuning map. Same rules as `DECISIONS.md`: entries here
override `PLAN.md`; the root file wins on conflict.

## Reload
- **`Config.load` tracks the instance it returns, per file name; the latest load of a name wins.** Movement loads
  `movement.json` into a temporary object and copies it into `BlockSoulsMovement.CONFIG`, so its `RELOADED`
  listener copies the reloaded instance into `CONFIG` (the tracked one isn't the live one there).
- **Copy rule (`Config.copyFields`):** every non-static, non-transient field, superclasses included. Plain classes
  (our config sections) are copied into recursively so references held by systems (`StaminaAttachment` keeps
  `CONFIG.stamina`, boss `Attack`s keep their `AttackStats`) stay live. Mutable lists, sets and maps are cleared and
  refilled in place; immutable ones (`List.of` defaults) are replaced. Strings, numbers, enums, records and arrays are
  assigned. Final fields are only copied into, never reassigned.
- **Reload is all-or-nothing per file.** The file is parsed into a fresh object first; a parse or type error keeps
  every current value, skips `RELOADED`, and is reported in chat (`sendFailure`) and the log.
- **Missing keys go back to their defaults** (same as at startup). A missing file is an error (values kept), not a
  reset.
- **Reload doesn't rewrite the file** (startup load still does, to add new keys), so an open editor's copy isn't
  clobbered mid-tuning.
- **`/bs config reload` with no name reloads every loaded file**; the name argument suggests the loaded names.
  Permission is `/bs`'s (op level 2). No server→client broadcast of the chat line beyond vanilla's admin feedback.

## `ConfigEvents.RELOADED`
- `common.event.ConfigEvents.RELOADED` (`Reloaded.onReloaded(String name, Object config)`), fired after a successful
  copy with the live instance. `ConfigEvents.reload(name)` = `Config.reload` + the event; the command uses it.
- **Listeners:** `party` → `PartyScaling.setConfig`, `threat` → `Threat.setConfig` (registered in
  `BlockSoulsCommon` so the math classes stay Fabric-free for unit tests); `movement` → copy into `CONFIG` and re-send
  `ConfigSync` to every player; `guns` → rebuild the weapon stat table (`Weapons.apply`) and re-send it.
- **SiegeClock (v2) doesn't exist on `main` yet.** When it lands it reads its cycle length from config at use time or
  listens to `RELOADED` for its file; noted for the v2 orchestrator.

## Cached values (audit)
Fixed here (cheap): guns weapon stats + client sync (listener); `Downed` movement penalty, `SetBonuses` scrap-plate
and `DragonscaleArmor` knockback modifiers now update a stale amount instead of only adding when absent.

Left as is, documented in `docs/TUNING.md`:
- **Restart needed (registry time):** mob/boss/Wraith Steed default attributes, gun `adsMoveSpeed` (item component),
  Nether biome spawn modifications, structure spacing (`ConfigSpreadPlacement`), `WaveMobSpec` `minWave`/`weight`
  (immutable records in common's `WaveMobs`, which rejects re-registration), boss `BossDefinition`s (phase
  thresholds/multipliers; rebuilding mid-fight would break running attacks' indices).
- **Needs vanilla `/reload`:** loot chances in `GunLoot` and `WorldLoot` (baked in `LootTableEvents.MODIFY`).
- **Applies next time:** run difficulty and roster shuffle (next run), night budget and modifiers (next dusk), boss
  XP and weak-point multipliers (next spawn), kart max health/fuel (next kart), flight speed (next flight).
- **Client-local, not synced:** mob/boss model tweaks, `game-players.json` HUD flags and revive range, kart physics,
  world fog. Only movement and gun stats are synced.

## Movement fields added for the tuning map
- `web.gravityMult` (1.0) scales gravity inside `Pendulum.step` while swinging, and `web.ropeSlack` (0.05) replaces the
  old `TAUT_EPSILON` constant. Defaults reproduce the previous behavior exactly; they exist so the owner's tuning
  session can turn "gravity while swinging" and "rope slack" (feedback item 5). A small reach into movement's lane,
  logged here.
