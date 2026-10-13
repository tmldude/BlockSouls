# Decisions: v2-floor6-arena (Stage 6C, wave B)

Defaults picked by the `v2-floor6-arena` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Spec: `docs/design/floors.md` "Floor 6 — Final Arena". Code: `bosses/src/*/java/.../bosses/riftwarden/`,
`world/.../floor/ArenaFloor.java`, numbers in `config/blocksouls/boss-riftwarden.json` and `party.json`.

## The floor
- **Template `arena_final.json`** in a new pool `blocksouls_world:arena`, which `RunConfig` assigns to floor 6
  ("Final Arena"). Tags `arena` and `timeless`. Radius 48, flat (no hills), blackstone/deepslate, no trees, crates or
  biome (an End biome would spawn endermen into the fight). `FloorTemplate.validate` requires one exit portal and a
  non-empty node mix, so the template keeps a portal beside the camp and a single scrap node. Neither matters: the
  boss kill ends the run.
- **The fight ring is a STRUCTURE slot tagged `final_arena`** (r 18, 28 blocks from the camp). `FloorBuilder`
  hands that tag to `ArenaFloor.build` instead of a ruin: a polished blackstone disc with inlay rings, eight pillars
  with soul lanterns, a wall ring, 16 blocks of headroom and a 3×3 crying-obsidian **trigger plate** in the centre.
- **Timeless floors** (template tag `timeless`, `ActMachine.timeless`): the Director places the camp and skips the
  day, the events and the mini-boss (`DayLoop`). The synthetic clock is held at day: it does not advance, and it is
  skipped to dawn if it is night when the act starts. So no siege runs. The camp shopkeeper still spawns.
- **`/bs floor goto arena`** teleports to the camp of the placed arena floor. If there is none, it opens one as act
  6 (solo-sized) first.

## The boss: the Riftwarden
- **Name:** "Riftwarden" (original). 1.6 × 4.2 hitbox, model drawn at 1.3×, fire-immune. Base 900 HP, armor 14,
  speed 0.26. Health × `BossScaling` (`PartyScaling`) for the party, like every boss.
- **Start:** stepping on the plate (within `plateRadius` 1.6 horizontally) spawns the boss on the plate if needed and
  engages everyone in the arena. After a wipe the boss goes home; walking back into the ring re-engages it (the
  framework default), as does the plate.
- **Arena radius** `max(arenaRadius 19, ring radius + 1)`, so the fog wall sits just outside the wall ring.
- **P1** (above 60%): ring wave, cross, leap, charge. **P2** (60%, transformed): adds beam, orbs, a summon (adds) and
  a feint. Damage ×1.2, recovery and cooldowns ×0.75, speed ×1.15. **P3** (30%, the damage rush): only the ring and
  cross, slower and with longer recoveries (×1.4 recovery, ×1.6 cooldowns, ×0.8 speed). This leaves openings to deal
  damage. Every big hit has ≥ 15 windup ticks (`ReadabilityTest` covers the Riftwarden).
- **Debug:** `/bs boss final p1|p2|p3` forces the phase on the arena boss of your floor, else the nearest boss within
  48 blocks, else a new one 8 blocks away. It sets health to just under the phase threshold so the phase holds.
- **Run end:** `RunSystem.onBossKilled` ends the run (`endRun`) when the Riftwarden dies. Classic runs keep the
  dragon raid as their final boss.

## Posture, parts, adds and hazards (slice 2)
- **Posture:** the framework default poise (`poise` 0 in the config = default). The Riftwarden adds nothing special.
- **Part break: the rift core.** The crystal floating before the chest is a weak point (×1.5 damage). After 8% of
  max HP lands on it, it breaks: the boss loses the beam and is 10% slower. The core vanishes from the model. It
  can be broken in P1, before the beam ever appears, which rewards aiming early.
- **Transformed (P2):** rift-seam glow, the blades lengthen, a torrent of motes, and a wither roar on entry.
- **Adds:** the summon draws from floor 5's roster: `WaveMobs.eligible(tier 5, addsFloor 5, every roster's tags +
  more_elites)`. Floor 5 carries every roster, so the union of all spec tags stands in for its tag set. bosses
  can't read world's floor tags. Count = 3 × `arenaAdds` (11 at 8 players), capped at `maxAdds` 12. This goes
  through two new overridable hooks on `SummonShield` (`count`, `roster`); their defaults leave every other boss
  unchanged.
- **Arena hazards: rift fissures** (P2 and P3), owned by the boss rather than the floor. Every 9 s, circles (r 1.75)
  open under the players: one per player first, any extras within 4 blocks of one. They fill for 30 ticks and then
  erupt for 6 × phase damage. Only players standing in a drawn circle at the eruption are hurt, once each. Count =
  2 × `arenaHazards` (5 at 8 players), capped at 8.

## Party scaling (1–8)
- New `party.json` curves, all 1.0 solo:
  - `rushWindow (-0.025, 0.9)`: the P3 window shrinks a little with more players (×0.86 at 8), floor ×0.25.
  - `arenaAdds (0.45, 0.9)`: P2 adds ×3.6 at 8.
  - `arenaHazards (0.25, 0.9)`: arena hazards ×2.4 at 8.
- Arena hazards live in the bosses module (fight hazards scaled by `arenaHazards`), not in the world floor. They
  only matter during the fight.

## The damage rush (slice 3)
- **P3 is the rush.** Entering it opens a window of `rushSeconds` 60 × `rushWindow(players)` (×0.86 at 8) ×
  `rushShrink` 0.8^(rush − 1), never under `minRushSeconds` 25. It is fixed when the rush starts. Dealing
  `rushFraction` 0.25 of max HP during the window kills the Warden on the spot (the hit's source gets the kill),
  which fires the kill event and ends the run. P3 starts at 30%, so a landed rush always comes before the bar
  empties. Running the bar out also wins.
- **The clock pauses** while the Warden is staggered or shielded, since no damage can land then (this includes the
  P3 entry stagger). Participants see the seconds and the % dealt in the action bar every half second, plus a
  bell 5 s before the end.
- **A timed-out rush** heals it to `rushFailHealShare` 0.59 (clamped between the P3 and P2 lines; it never lowers
  health) and forces P2 again: the adds, beam and fissures come back. The next drop under 30% starts the next,
  shorter rush.
- **After `maxRushes` 3 failed rushes the Warden is *unbound*** instead of a wipe: no more timer or healing, it stays
  in P3 and hits ×`unboundDamage` 1.5 until the bar runs out. This keeps solo completable; it just gets harder.
  The plan's "telegraphed wipe" is dropped, because a hard fail state would make a slow solo build unwinnable.
- **Wipes reset everything:** a reset (`onReset`) clears the rush count and the unbound state.
- **Tests:** `RiftMathTest` covers the window, win and last-rush math at 1 and 8 players. `RiftwardenGameTests`
  covers landing the fraction (kills it, kill event) and a timed-out rush (back to P2 at 59%).
  `RunGameTests.riftwardenKillEndsTheRun` checks that the kill event ends a running run.

## The floor-6 shop: tonics and ammo only
- **Data, not code:** board entries only narrow, so `tools/content/gen_floor6_shop.py` writes one entry per other
  BlockSouls recipe, capping it at floor 5. The entries go in `world/.../blocksouls_board/entry/floor6_arena/`,
  as `{"recipe", "maxFloor": 5}`.
- **Kept (no file):** results in `#blocksouls:ammo` or `#blocksouls:brew_board`, and ids naming a tonic, antidote,
  draught, broth or ammo. Draughts and broths are brewed consumables, which is what the arena's "tonics" means.
- **Recipes with their own entry elsewhere** (floor gear such as the skysteel featherboots or the cinder plate) get
  no second file, because for two entries on one recipe the last one loaded wins, and that order depends on file
  names. Instead, the script writes `"maxFloor": 5` into that file with a minimal text edit, unless it starts on
  floor 6 or later. This edits other floor sessions' entry files; it is the only way to cap them that does not
  depend on load order.
- **New recipes show on floor 6 until the script is rerun.** `--check` reports missing or stale files and
  uncapped entries.
