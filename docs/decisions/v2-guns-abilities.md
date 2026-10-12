# Decisions: v2-guns-abilities (Stage 6A)

Defaults picked while building the ability contracts (`common`), the `abilities` module and guns v2 (exotics +
feel pass) where `PLAN2.md` §3j or the brief was silent. Same rules as `DECISIONS.md`.

## Contracts (`common`, PR 1)
- **Three slots as an enum** (`AbilitySlot`: tactical / signature / ultimate). A player holds one ability per slot
  (`AbilityState.equipped`). A definition names the slot it fits; the registry is keyed by ability id and
  **re-registering replaces** the definition, so a config reload can re-apply cooldowns without new API.
- **Charge model is per definition, not per slot** (`ChargeModel.COOLDOWN` / `KILL_CHARGE`): ultimates are kill-charged
  by default, but a future tactical could be too. `Abilities.isReady` reads the model, not the slot.
- **Cooldowns are stamped in the level's game time** (`Level.getGameTime()`, stored as `startedAt` + `readyAt`),
  never the server tick count: the world clock is persisted and synced, so the state survives relogs and restarts and
  the client draws the cooldown sweep from the same two numbers with no extra packet. A cast syncs the attachment once;
  nothing ticks per frame.
- **State attachment** (`blocksouls_common:abilities`): persistent, **copied on death** (like `Capabilities`, unlike
  traits: a kit is identity, not a boon) and synced to the owner only. Equipping keeps the slot's running cooldown
  (swapping abilities isn't a free reset).
- **Ultimate charge is 0..1** on the state; `ULTIMATE_READY` fires exactly once per fill (crossing 1 from below).
- **Cast request flow:** client keybind → `AbilityCastPayload(slot)` (registered by common) → `AbilityEvents.CAST_REQUESTED`.
  Common never casts; the abilities module listens, validates and fires `AbilityEvents.CAST` after the effect. Any
  module can ask for a cast with `Abilities.requestCast` (debug commands do).
- **Origin hook is an origin lookup, not a signature lookup**: `Origins.of(player)` returns the origin id (provider
  installed by whoever owns origins, v2-players); the abilities module maps origin → signature in its own config. One
  provider, no default origin: `null` means "no origins yet / classic", and the module falls back to its config.
- **Combat events:** `CombatEvents.PRECISION_HIT` (guns fires it; ultimate charge and keystones listen). Kills come
  from Fabric's `AFTER_KILLED_OTHER_ENTITY`, so ability and melee kills count too — no `GUN_KILL` event.
- **Roll query:** movement's roll state is a private attachment, so there is no roll query in `common`. Added
  `MovementEvents.ROLL_STARTED` (additive); guns' reload-cancel listens to it. **Movement must fire it** from
  `RollServer.tryStart`/`forceStart` (one line; reported to the orchestrator). Until then reload-cancel is inert.
- **Party curves** added to `party.json` (additive fields on `PartyConfig`): `ultimateCharge` (0.25, 0.9: 8 players
  ×2.4 charge per kill — each player's share of kills shrinks as wave size grows slower than the party) and
  `abilityHeal` (0.1, 0.9: 8 players ×1.58 on healing dart / overshield amounts). Tested at 1 and 8.

## Abilities module (PR 2)
- **Keys: Z / X / C** (tactical / signature / ultimate), one row under the left hand. G is vanilla's quick-actions
  key in 26.3 and Q is drop; X and C only collide with the creative toolbar activators. Registered through
  `common`'s `Keybinds`, rebindable like the rest.
- **No entities, no blocks.** Lobbed abilities (frag, web bomb) are a simulated arc ticked server-side with a
  particle trail (`Effects.lob`); zones (web bomb, fire wall, overclock, storm, frenzy) are tick callbacks
  (`Effects.zone`). The fire wall ignites mobs crossing a line instead of placing fire blocks (no spread, no grief);
  nothing an ability does breaks or places a block.
- **Friendly fire is off**: hostiles are living non-players, allies are players (`Targets`). Abilities never target
  the caster's own party; healing dart with no ally in the line heals the caster for a share (`selfShare`).
- **Fan the Hammer is its own hitscan** (six rays, config damage, no ammo) rather than a guns call: `abilities`
  never imports `guns`. Overclock is a scrap sentry zone (zaps the nearest mob on a timer) until a real turret
  exists; Transfusion drains mobs and heals the party in the radius (the revive-speed coupling needs a `game`
  revive hook, deferred to v2-players). Logged so the origin kits can be upgraded without changing the contract.
- **Ability damage type** `blocksouls_abilities:ability`: bypasses the hurt cooldown (fan the hammer, storm ticks)
  and vanilla knockback (each ability pushes on its own), caster as attacker (kill credit, charge, on-kill boons).
- **Ultimate charge:** 4.5% per kill, 2% per precision hit, elites ×2, bosses ×6, all × `PartyScaling.ultimateCharge`;
  solo that is 23 kills, eight players about 10 kills each. `on_kill` / `on_precision` boon stacks add 5% per stack
  (cap 50%). Kills come from Fabric's `AFTER_KILLED_OTHER_ENTITY` (any source), precision hits from
  `CombatEvents.PRECISION_HIT` (guns, PR 3).
- **Synergy scaling** is one number: damage and healing × `1 + 0.08 × shared tag stacks` (cap +60%), where shared
  = the build's stacks of tags the ability also carries. Cooldowns don't scale (legibility: a slot is ready when its
  sweep is done, always).
- **Party scaling:** healing dart, transfusion and party overshield amounts × `PartyScaling.abilityHeal`; charge
  as above. Party = players in the caster's dimension. Gametests cover 1 and 8 players.
- **Melee frenzy invulnerability** is an `ALLOW_DAMAGE` veto (like the roll's i-frames), not resistance V, so it
  reads as "untouchable" and ends at once if the player goes down.
- **Overshield** sets absorption directly and takes back what it gave when the timer ends (no potion effect, so it
  stacks with nothing and never shows a status icon).
- **Cooldowns read config at cast time** (`AbilityServer.cooldownTicks`), so `/bs config reload abilities` applies
  at once; definitions are re-registered on `ConfigEvents.RELOADED` for anything that reads them.
- **Signature by origin** is a config map (`kit.signatureByOrigin`, origin id → ability) refreshed on join and
  run start through `Origins.of`; with no origin (classic, pre-v2-players) everyone gets `defaultSignature`
  (fan the hammer). Zephyr maps to geyser and Gambler to fan the hammer until they get their own.
- **A fizzled cast costs nothing** (web pull with nothing in the line): the behavior returns false and no cooldown
  starts.
- **HUD** bottom-left (margins in config): 20px slots, cooldown sweep draining from the top plus a seconds label,
  the ultimate bar above the row; a denied press flashes the slot red, a full bar flashes and chimes once.
