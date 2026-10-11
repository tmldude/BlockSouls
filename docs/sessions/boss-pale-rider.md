# Session: boss-pale-rider (Stage 2b)

**Module:** `bosses` (package `bosses/palerider/`). **Branch:** `claude/boss-pale-rider`.
**PR title:** `bosses: Pale Rider + Wraith Steed`.

Build the **Pale Rider** field boss on the framework from the `bosses` session. Read `docs/sessions/README.md`,
PLAN.md §0b and §6 D2b, and **`bosses/README.md`** (how to add a boss) first.

## You own
`bosses/src/*/java/.../bosses/palerider/**`, its assets/data (`pale_rider*`, `wraith_steed*` paths), and one
registration line each in the shared bosses init/client init/spawner. Three other sessions work in `bosses/` at the
same time: don't edit their packages or the framework. If the framework lacks something, add it in the smallest
possible way and list it under "Framework changes" in your PR.

## The boss
- **Where/when:** roams **plains/meadow/savanna at night** (field-boss spawn rule: chance per night, config),
  appearing in pale fog with distant hoofbeats. Leaves at dawn if unengaged.
- **Look:** a ghostly horseman fused to a spectral horse (one entity, one custom model, ~3.5 blocks tall), pale
  translucent glow, a long lance, a tattered cloak that sways; palette `pale`.
- **Stats:** 360 HP (framework scaling), fast, knockback-immune.
- **P1 (100–55%), mounted:** *Lance charge* (1 s windup, a long line telegraph, runs through 20 blocks, 13 dmg,
  turns around for a second pass), *Spectral sweep* (0.8 s windup, wide arc around it), *Rearing stomp* (1 s
  windup, ring shockwave radius 4), *Wraith call* (summons 3 fast spectral skeleton riders/adds once per phase).
- **P2 (<55%), unhorsed:** the horse dissolves into mist; the rider fights on foot, floats short distances,
  throws 3 spectral spears in a fan (telegraphed lines), blinks behind its target (0.8 s shimmer telegraph), and
  every 20 s the spectral horse charges across the arena once (telegraphed lane).
- **Drops:** **Wraith Steed whistle** (always), **2 Ender Eyes**, a legendary gun (`roll_perks`), XP.

## Wraith Steed (the payoff)
- The whistle summons a **spectral horse** bound to its owner: rideable, fast (faster than the best vanilla horse),
  **double jump** in mid-air, no fall damage, can't be leashed/bred/ridden by others. Translucent pale model (reuse
  the boss's horse half).
- Dismount → it fades after 5 s. Killed (it has 30 HP) → whistle on 60 s cooldown (item cooldown). Re-use while out
  → recalls it to you. Whistle is unique, keeps on going down (it's gear).

## Tests
Unit: attack timings / pick rules you add. Gametests: phase machine P1→P2 without softlock; the whistle summons a
steed owned by the user and a second use recalls it; killing the boss drops the whistle and fires
`BossEvents.KILLED` with `CommonIds.PALE_RIDER`.

## Done when
CI green; `/bs boss spawn pale_rider` is a readable ~3–5 min fight; the Wraith Steed is a joy to ride (double jump
over a ravine); `docs/decisions/boss-pale-rider.md`.
