# Session: v2-floor6-arena (Stage 6C, wave B: starts after `v2-boards` merges)

**Modules:** `bosses` (the final boss and its three phases) + `world` (the arena template, pool `blocksouls_world:arena`,
act 6 of `floorActs`: lane exception, one line in `game`) + `game` data (the floor-6 shop restriction: tonics and
ammo only, via board entry filters). **Branch:** `claude/v2-floor6-arena`. **PRs:** (1) arena template + phase
skeleton with a placeholder boss, CI green; (2) the boss proper; (3) the damage rush, run end, tuning hooks.

Read `docs/sessions/README.md` (protocol), **`docs/design/floors.md` ("Floor 6 — Final Arena")**, DECISIONS.md
"Floors 2–6 first draft", `docs/decisions/v2-bosses.md` (posture, archetypes, part breaks: build on them),
`docs/decisions/final-raid.md` and `docs/decisions/bosses.md` (the v1 dragon raid you replace as the run's end; keep
it reachable in classic runs), `docs/decisions/v2-floors.md`, `docs/decisions/v2-boards.md`, `docs/decisions/v2-spine.md`
(run end, souls kept, rune tree hand-off), `docs/CONTENT.md` (boss standard).

## You own
- **The arena floor**: one template (no day timer: the act opens straight into the fight once the party steps on the
  arena's trigger plate; config), the camp at the arena's edge with the shopkeeper (floor-6 board filter: tonics,
  ammo, nothing else), arena hazards that scale with `PartyScaling`, the bound.
- **The final boss** ("the Warden of the Rift" as a working name; pick an original one): a bosses v2 boss with posture
  and part breaks. **P1** the boss; **P2** transformed (new moveset, adds from floor 5's roster via
  `WaveMobs.eligible(wave, 5, tags)`); **P3 damage rush**: a timed window (60 s, config) where the boss is exposed and
  the party must deal `rushFraction` of its max health or it heals back to the P2 threshold and P2 resumes (max
  rushes config; each later rush shorter). Telegraphs never lie; every number in `bosses.json`.
- **Run end**: the P3 kill ends the run through the spine's existing run-end path (souls kept, summary); classic
  runs keep the dragon raid.
- **Debug:** `/bs boss final <p1|p2|p3>` jumps phases; `/bs floor goto arena`.
- **Tests:** unit tests for the rush math at 1 and 8 players; a gametest that spawns the boss, forces P3, deals the
  fraction, and asserts the run ends.
- Placeholder model/art via `tools/assets`; a design sub-session for real art is a later wave (v1 practice).
- `docs/decisions/v2-floor6-arena.md`.

## Not yours
Floors 1–5; the boards' Java; the Director's pacing. Don't change bosses v2 contracts; extend them.

## Done when
A floors run that clears floor 5 lands in the arena, fights all three phases, the damage rush works at 1 and 8
players, the run ends on the kill, and CI is green. Say so in the last PR and stop.
