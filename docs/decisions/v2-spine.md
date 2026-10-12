# v2-spine session decisions

Defaults picked while building the Stage 5 spine (PLAN2.md §0–§2, §4–§5; brief `docs/sessions/v2-spine.md`).
Same style as `DECISIONS.md`: where this conflicts with a plan file, this logs what was actually built.

## Common v2 contracts (PR 1)

- **Threat = the wave counter, promoted.** PLAN2 calls the threat counter "the v1 wave counter, promoted";
  the persisted value is `RunData.wave` unchanged (NBT key `wave`), the new accessor is `RunState.threat()`.
  `wave()` stays as the v1 accessor — not `@Deprecated`, because the classic HUD and run summary legitimately
  present the number as a wave counter; the two names are two readings of one value.
- **Threat curves** (`config/blocksouls/threat.json`, loaded by common beside `party.json`): each is
  `1 + perThreat · threat^exponent` capped at `max` (0 = uncapped) — same shape family as `PartyScaling`,
  so threat 0 is always ×1. Defaults: bossHealth `(0.04, 0.9, max 3.0)` ≈ ×1.6 at threat 20; bossDamage
  `(0.03, 0.9, max 2.5)` ≈ ×1.4 at threat 20. Caps exist for endless mode. Multiplied with `PartyScaling`
  at the call site, never folded into it.
- **The `waveSize` threat curve defaults to neutral (×1).** The classic wave formula already grows with the
  counter (`countPerWave`); a non-1 default would double-scale every ordinary siege. The curve is the lever
  for v2 features that want extra wave pressure (Director, portal-charge sieges) without touching the base
  formula.
- **Act events semantics:** `ACT_STARTED(act)` = the act became open and is the run's frontier (fires for
  act 1 at run start, for act N+1 when act N's charge completes); `PORTAL_CHARGE_STARTED/COMPLETED(act)` =
  the act whose *exit* portal is being charged / finished charging; `ACT_ENTERED(act)` = the party majority
  arrived in the act's dimension for the first time this run.
- **`Acts` defaults to a single always-open overworld act** (`Acts.CLASSIC`) when no provider is installed,
  mirroring `Runs`: modules keep working standalone and under the classic run shape.
- **`Act.dimension` is an `Identifier`, not a `ResourceKey<Level>`:** unknown/unregistered dimensions are
  legal act targets (Stage 6 registers the Blightheart and the Skyrift); travel just isn't possible until
  the dimension exists.
- **Capabilities are on/off verbs; magnitudes stay traits.** `CLIMB`, `SWING`, `MULTI_JUMP`, `GLIDE` as
  extensible `Identifier`s (origins are data-driven, so no enum). "No attachment = all granted" encodes the
  v1/classic behavior for free — a fresh player or a classic server never carries a restriction set.
- **Capabilities survive going down and death (`copyOnDeath`), unlike `PlayerTraits`:** an origin's movement
  signature is identity, not a boon, and the boon wipe on going down is already the punishment (PLAN2 §3a).
- **Capabilities sync to all clients** (`AttachmentSyncPredicate.all()`), not target-only like traits: other
  players' climb/swing/glide poses render on every client, and the set is a handful of ids for ≤8 players.
- **SynergyTags registry is init-time single-threaded** (mod initializers), reads thread-safe afterwards.
  The "tags of a thing" contract is `SynergyTags.Source` feeding `tag → stack count` per player: counts (not
  booleans) because relic weighting and the HUD build line need "top two tags by stacks" (PLAN2 §3a).

## The game spine (PR 2)

- **One synthetic cycle per run, not per dimension.** PLAN2's "one rhythm" taken literally: every sun-less
  dimension reads the same persisted `SyntheticCycle`, started at run start aligned to the overworld's
  time of day and advanced at the same clock rate, so hopping dimensions keeps roughly one continuous
  rhythm instead of juggling phases. It pauses with the sun's clock when the server is empty.
- **The scheduler runs off the active dimension's clock, one code path.** `RunSystem.activeClock` is the
  overworld `SunClock` in classic (byte-for-byte the v1 reads) or whenever the party's act is the
  overworld; the `SyntheticClock` elsewhere. Dusk/dawn edge detection, wave chaining, dawn rules and
  relic drops are the existing v1 code reading through the interface. Switching dimensions mid-night can
  re-enter or skip a dusk edge; the shared rhythm keeps that rare, and it's accepted skeleton behavior.
- **The siege follows the party through the existing stray mechanic.** Waves spawn in the active
  dimension; mobs left in an abandoned dimension read as strays (entity unreachable), get dropped from
  tracking after `straySeconds` and respawn near the party — no per-wave dimension bookkeeping. Leftovers
  on the ground are discarded when their chunks next load (the v1 `ENTITY_LOAD` rule, now in any
  dimension); discard/burn sweeps search all levels.
- **The portal charge reuses `startWave` verbatim** ("a wave at current threat via the existing wave spawn
  path"): the counter increments per defense wave exactly like a night wave, and a cleared defense wave
  starts the next one so the pressure holds for the whole charge. Dawn mid-charge ends the wave like any
  other; the charge loop restarts one within a second. Completion ends the running wave by dawn rules
  (ordinaries burn, elites hunt). Default `portalChargeSeconds` 180.
- **Leaving the act aborts the charge** (party-follow switches the current act → `cancelCharge`), matching
  "wiping the attempt just means re-triggering it later". A charge on the last act fires
  `PORTAL_CHARGE_COMPLETED` with nothing to open — Stage 6/7 wires the finale portal to it.
- **Aliased act dimensions** (two acts sharing a dimension — only in test skeletons until Stage 6): the
  dimension belongs to the **highest open act** with it, so opening the next act in a shared dimension
  moves the party forward; the "ties → earlier act" rule stays for player counts across distinct
  dimensions (`ActMath`). Real acts have unique dimensions, so live runs never hit the alias rule.
- **Gametests avoid cross-dimension player teleports**: `ServerPlayer.teleportTo(otherLevel, …)` on a
  mock player does not take effect in the gametest harness (observed on CI; the party stayed counted in
  the overworld). The Nether siege test instead runs natively in the Nether via the `@GameTest
  (dimension = "minecraft:the_nether")` field, which also exercises real cross-dimension party-follow
  (the player genuinely sits in the Nether when act 2 opens); the act-skeleton walk aliases all acts to
  the test's own dimension and advances purely by charges via the alias rule above.
- **Threat command is the wave counter**: `/bs threat get|set` reads/writes `RunData.wave` — one value,
  two readings, per the PR 1 decision.
- **Expedition visuals outside the overworld**: the dusk sky/fog tint plus a modest block-light dim
  (×0.65 at full night) driven by the synced synthetic phase; the overworld keeps its sun-driven v1 look.
  The End gets the same vibe switch during a run — acceptable until the Graphics stage restyles the raid.
- **Classic neutrality**: every new sync field is constant in classic (`-1`/`0`/act 1), the act machine
  and synthetic cycle never tick, and `/bs act charge` fails with a pointer at `runShape`. The classic
  gametests are untouched; `act_classic` asserts the contracts read classic values.

## Follow-ups after the spine merges (from the v2 orchestrator, 2026-10-12)

- **v1 PR #42** adds `defaultsVersion` + `RunConfig.migrate` to `game-run.json` (new pacing defaults). If
  the spine PR needs a base merge over it: keep the `defaultsVersion` field and the `migrate` call, slot
  `runShape`/`acts`/`portalChargeSeconds` in beside them. From here on, no config-default change in v2
  work without that migrate pattern (DECISIONS.md rule). The spine's gametests use scripted time (skips,
  2-second charges), so the new pacing defaults don't affect them.
- **v1 PR #41** adds `ConfigEvents.RELOADED` (`/bs config reload`). Once #40 and #41 are both in main:
  a small v2 PR wiring the spine's config reads to it. The synthetic clock already reads
  `dayLengthMinutes` live through a supplier, so the main candidates are `ActMachine.load` (the parsed
  act list) and anything else cached from `game-run.json` at init.
