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
- **Combat and roll events are not ours** (orchestrator reconciliation, 2026-10-12): `CombatEvents.HIT(shooter,
  target, damage, precision, killed)` / `RELOADED(player, rounds)` come from v2-builds (#46) and are **fired by guns**
  (PR 3); `PlayerMoveEvents.ROLLED` comes from v2-players (#48) and is fired by movement. Ultimate charge listens to
  `HIT` with `precision == true`; kills come from Fabric's `AFTER_KILLED_OTHER_ENTITY` so ability and melee kills
  count too. Guns' reload-cancel listens to `ROLLED`, and guns gate firing on `PlayerStatus.canShoot` (#48), not
  `!isDowned`. The earlier `CombatEvents.PRECISION_HIT` / `MovementEvents.ROLL_STARTED` drafts were dropped.
- **Party curves** added to `party.json` (additive fields on `PartyConfig`): `ultimateCharge` (0.25, 0.9: 8 players
  ×2.4 charge per kill — each player's share of kills shrinks as wave size grows slower than the party) and
  `abilityHeal` (0.1, 0.9: 8 players ×1.58 on healing dart / overshield amounts). Tested at 1 and 8.
