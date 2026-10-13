# Session: v2-mobs (Stage 6B)

**Modules:** `mobs` + `common` (additions only; the affix contract) + one surgical hook in
`game/run/director/**` (where the Director rolls an "affixed elite"; list it under "Lane exceptions" in the PR).
**Branch:** `claude/v2-mobs`. **PRs, in order:** `common: elite affix contract` →
`mobs: elite affixes, act rosters, flanking + attack scoring`.

You own PLAN2 §4's `mobs` row ("act 3/4 rosters, influence-map pathing, affix system on elites") and the
`mobs` half of §3k Tier 1. Read `docs/sessions/README.md` (protocol), `PLAN2.md` §0–§2, **§3h's affix
paragraph**, §3k, `docs/CONTENT.md` (mob standard, generated art, test rosters), DECISIONS.md's v2 entries,
`docs/decisions/v2-director.md` (the Director already picks spawn angle from its influence map, biases
elites, and spawns "affixed elites" for day mobs and ambushes — today that is only a health bonus and an
effect amplifier in `DirectorConfig`; you make affixes real) and `docs/decisions/v2-floors.md` (escalation
tags `more_elites`, `elite_affixes`, `mean_mini_boss`, `stingy_nodes` — you consume `elite_affixes` and
`more_elites`).

## You own
- **Affix contract in `common`** (PR 1): `common.mob.Affix` enum (`BURNING`, `FROSTED`, `VOLATILE`,
  `MAGNETIC`; PLAN2 §3h), `common.mob.Affixes` provider: `apply(LivingEntity, Affix)`, `of(LivingEntity)`
  (what it carries), `roll(RandomSource, int floor)` — default provider is a no-op until mobs installs it.
  Affix is persisted on the entity (attachment or NBT) so it survives chunk reload and shows in the
  nameplate ("Burning Brute"). Bosses v2 applies the same contract to field bosses; keep it entity-agnostic.
- **Affix behaviour** (PR 2, `mobs`): palette shift (one tinted overlay layer per affix on the existing mob
  models; generated via `tools/assets`) + exactly one modifier each: Burning leaves a short fire trail and
  ignites on hit; Frosted slows on hit and takes less projectile damage; Volatile explodes on death (no block
  damage); Magnetic pulls dropped items/players' projectiles slightly off course (config). Every number in
  `mobs.json`; readability rule from CONTENT.md holds (telegraphs never lie).
- **Act rosters**: `WaveMobSpec` gains act/floor eligibility (`minFloor`, `tags`), and `WaveMobs.eligible`
  gets an overload by floor and escalation tags (additive; keep the old signature). Define the roster for
  floors 1–6 on Surface variants now (mixing the existing mobs + 2 new ones: a ranged **Spitter** and a
  shielded **Warden-type** that must be flanked), with blight/sky mobs deferred until the owner's per-floor
  design (leave the roster data-driven so they slot in later).
- **Flanking**: hordes use the Director's spawn angle today; you add the pathing side — a `FlankGoal` that
  approaches from the side the party is not facing when the mob is a flanker (the Director's composition
  tag), and `AttackPicker`-style utility scoring for the mobs that have more than one attack (Brute,
  MutantBrute, Screamer): distance, angle, target just-rolled, airborne, per-attack cooldown/repeat penalty.
- `/bs mob affix <player|target> <affix>`, `/bs mob roster <floor>` debug commands; `docs/decisions/v2-mobs.md`.

## Constraints
- `common` additions in their own first PR; never change existing signatures.
- Every number in config; anything per-party through `PartyScaling` (elite share is already there:
  `directorEliteShare`), tested at 1 and 8.
- Don't touch `bosses/`, `guns/`, `abilities/`, `movement/`, `world/`, `game/player/**`, HUD. In `game/`,
  only the one Director call site that rolls an affixed elite (replace its ad-hoc buff with `Affixes.apply`).
- Classic mode: affixes only appear in Expedition, or when `mobs.json` `affixesInClassic` is true (default false).
- CI green is the bar; gametests: an affixed elite of each kind spawns, shows its name, and its modifier
  fires; roster eligibility at floors 1 and 6; a flanker reaches the player's back in a test arena.

## Done when
Four affixes real and visible; the Director's affixed elites use them; floor rosters 1–6 data-driven with
two new mobs; flankers flank; multi-attack mobs pick by utility; decisions logged; CI green.
