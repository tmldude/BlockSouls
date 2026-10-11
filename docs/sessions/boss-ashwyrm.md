# Session: boss-ashwyrm (Stage 2b)

**Module:** `bosses` (package `bosses/ashwyrm/`). **Branch:** `claude/boss-ashwyrm`.
**PR title:** `bosses: Ashwyrm`.

Build the **Ashwyrm**, a dragon field boss, on the framework from the `bosses` session. Read
`docs/sessions/README.md`, PLAN.md §0b and §6 D2/D2b, and **`bosses/README.md`** first.

## You own
`bosses/src/*/java/.../bosses/ashwyrm/**`, its assets/data (`ashwyrm*` paths), and one registration line each in the
shared bosses init/client init/spawner. Other sessions work in `bosses/` in parallel: don't touch their packages or
the framework; minimal framework additions only, listed under "Framework changes" in your PR.

## The boss
- **Where/when:** lands on a **mountain peak or high hill** (windswept/peaks/high terrain; spawn rule picks the
  highest open ground within range of a player, chance per in-game day, config) with a roar and a column of ash smoke
  visible from afar. Leaves after its unengaged timeout by flying off.
- **Look:** ~12 blocks long, 4 legs, wings, long neck and tail, ash-grey scales with ember-glowing cracks; palette
  `ash` + `ember`. Custom multi-part model with wing flap, neck and tail sway animations.
- **Hitboxes:** body + a **head weak point (2× damage)** via the framework's multipart helper.
- **Stats:** 600 HP (framework scaling), fire-immune, knockback-immune.
- **P1 (100–60%), ground:** *Bite* (0.6 s windup, front), *Tail sweep* (1 s windup, 360°, jump/roll), *Fire breath*
  (1.2 s windup, cone 10 blocks for 3 s — roll through or get behind), *Wing buffet* (knockback ring).
- **P2 (60–25%), air:** takes off and strafes: *Fireball run* (3 fireballs along a telegraphed line; fire on impact
  that **doesn't spread or burn blocks** — config), circles, and every ~25 s lands for a **6 s vulnerability window
  (2× damage, stunned)**.
- **P3 (<25%), ground, enraged:** faster, adds *Firestorm* (meteors on each player with 1.5 s telegraph circles).
- **No terrain damage** (config `griefing: false` default).
- **Drops:** **2 Ender Eyes**, 4–6 `ashwyrm_scale` (a bosses-owned material), 1 `ember_barrel` (a bosses-owned gun
  part that later guns recipes can use by id), a legendary gun (`roll_perks`), lots of XP.

## Tests
Gametests: phase machine P1→P2→P3 with scripted health, lands periodically in P2, head hit deals 2×, no blocks
broken by fireballs, `BossEvents.KILLED` fires with `CommonIds.ASHWYRM`.

## Done when
CI green; `/bs boss spawn ashwyrm` is an epic, readable ~5 min fight for 2–4 players; `docs/decisions/boss-ashwyrm.md`.
