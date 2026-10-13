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

## Party scaling (1–8)
- New `party.json` curves, all 1.0 solo:
  - `rushWindow (-0.025, 0.9)`: the P3 window shrinks a little with more players (×0.86 at 8), floor ×0.25.
  - `arenaAdds (0.45, 0.9)`: P2 adds ×3.6 at 8.
  - `arenaHazards (0.25, 0.9)`: arena hazards ×2.4 at 8.
- Arena hazards live in the bosses module (fight hazards scaled by `arenaHazards`), not in the world floor. They
  only matter during the fight.

## Planned (later PRs)
- **Rush (PR 3):** on entering P3, track the damage dealt since the rush began. Dealing `rushFraction` × max HP (or
  killing it) wins. When the window runs out, the boss heals to `rushFailHealShare` (default = the P2 threshold) and
  P2 resumes. Window = `rushSeconds` (60) × `rushWindow(players)` × `rushShrink`^(rush − 1). After `maxRushes`
  failures comes a telegraphed wipe.
- **Shop filter:** floor-6 board entry files (tonics and ammo only) land once the board reader (#70) is on main.
