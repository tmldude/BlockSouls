# Session: v2-bosses (Stage 6B)

**Modules:** `bosses` (+ `common` additions only if you truly need them). **Branch:** `claude/v2-bosses`.
**PR title:** `bosses: posture + attack archetypes + part breaks + affixes + name-splash`. One PR, pushed
early as a draft; a second PR only if the first grows past ~4k lines.

You own PLAN2 **§3h in full** and the `bosses` half of §3k Tier 1. Read `docs/sessions/README.md`
(protocol), `PLAN2.md` §0–§2 and §3h, `docs/CONTENT.md` (boss standard: readability rule, >30% health ⇒
≥15 ticks windup, config-locked), DECISIONS.md's v2 entries, `docs/decisions/v2-director.md` (mini-boss
rotation spawns field bosses through `common.boss.BossSpawns` from act 2) and the existing boss framework
(`bosses/api/*`: `Attack`, `AttackRun`, `BossDefinition`, `BossPart(s)`, `BossPhase`, `Telegraphs`;
`bosses/logic/AttackPicker`). Three bosses exist: Furnace Knight, Stormplate, Pale Rider, plus Ashwyrm and
the raid. Extend the framework; do not rewrite it.

## You own, in priority order
1. **Posture / stagger** (highest priority, the Souls rhythm): hidden poise per boss filled by precision
   hits (`CombatEvents.HIT` with the precision flag guns already sends) and well-timed rolls
   (`PlayerMoveEvents.ROLLED` inside an attack's hit window); when full → a 3 s **visceral window** at 2×
   damage with its own animation/sound/particle, then poise resets and decays. Shown as a thin bar under the
   boss bar (`bosses/bar`). Every number in `bosses.json`; per-boss overrides allowed in `BossDefinition`.
2. **Attack archetypes** as reusable `Attack` implementations every boss can compose: expanding rings,
   lingering pools, line/cross AoEs, rotating beam; radial burst with a gap; 1 s delayed-homing orbs; spiral
   (flight phase); summon-and-shield (shield until 3 adds die; adds via `common.mob.WaveMobs` ids); feints
   (telegraph A cancels into B, both with one safe answer) gated to late phases. Wire at least two new
   archetypes into each existing boss so the change is felt.
3. **Part breaks**: sustained damage on a `BossPart` severs it — lose an attack, gain speed
   (Stormplate's pauldron, Ashwyrm's tail); texture swap on the broken part.
4. **Movement vocabulary**: leap with shadow marker; Ashwyrm burrow phase (dirt-plume tracking, eruption
   telegraph); reactive sidestep vs. aim with a 6–8 tick reaction floor and cooldown; charge that sticks
   2 s on hitting terrain.
5. **Affixes on field bosses**: the Burning/Frosted/Volatile/Magnetic contract lands in `common.mob.Affix` /
   `Affixes` from the `v2-mobs` session (its first PR). Until it is on main, code against
   `common.mob.Affixes` as a no-op provider you add yourself (same package/names as in
   `docs/sessions/v2-mobs.md`; if both land, main's copy wins). Field-boss rolls get an affix from floor 2.
6. **Presentation**: 1.5 s boss name-splash + sting at fog-wall rise; 2-tick hit-stop on heavy hits;
   emissive crack textures intensifying per phase; damage-state texture swaps at thresholds. Generated art via
   `tools/assets`.
7. `AttackPicker` utility scoring (§3k Tier 1): distance, angle, target stamina, just-rolled, airborne,
   per-attack cooldown/repeat penalty — config weights.
8. `/bs boss posture|break|affix ...` debug commands; `docs/decisions/v2-bosses.md`.

## Constraints
- Fairness invariants are config-locked constants (telegraphs never lie; reaction floors; adaptation caps),
  not per-boss tunables. The readability rule is a unit test.
- Every number in config; boss health/posture per party through `PartyScaling` (existing boss curves),
  tested at 1 and 8.
- Don't touch `mobs/`, `guns/`, `abilities/`, `movement/`, `world/`, `game/**`, HUD. No new bosses
  (Blightheart / Skyrift bosses wait on the owner's floor designs).
- Classic mode: posture and archetypes apply in both shapes (they are the bosses), affixes only in Expedition.
- CI green is the bar; gametests: posture fills and opens a visceral window; a part breaks and its attack
  disappears; each archetype runs its telegraph before its hit; name-splash fires once per fight.

## Done when
Posture live on all bosses; archetypes composed into every boss; two part breaks; the movement vocabulary
on at least two bosses; field-boss affixes; name-splash and hit-stop; decisions logged; CI green.
