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
