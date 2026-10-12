# Decisions: v2-players (Stage 6A)

Defaults picked while building origins, movement classes, solo Last Stand, downed gameplay and the run summary v2
(`docs/sessions/v2-players.md`). Same style as `DECISIONS.md`; where they conflict, `DECISIONS.md` wins.

## Contract additions (`common`, first PR)

- **`Capabilities` default set = the v1 verbs.** With no set attached, `has()` is true for climb, swing and
  multi-jump (`Capabilities.V1_VERBS`) and false for `GLIDE`. Glide is v2-only (Zephyr): an all-granted player
  pressing jump in the air must keep v1 behavior, so verbs v1 never had are opt-in through an explicit set
  (an origin, or a cross-training `grant`). `grant` stays a no-op on the default set, as before.
- **`PlayerStatus.canShoot(player)`** replaces `!isDowned(player)` as the gun gate. Downed players may fire
  while the game has downed shooting on (`setDownedShooting`, from `game-players.json`); the v1 rule is the
  `false` default. **Handoff:** `guns` has three `isDowned` checks (`GunServer.canShoot`, `GunItem.use`,
  `GunClient`) that must become `PlayerStatus.canShoot` for downed gameplay to work in the full pack; the game
  side (lent pistol, half damage) is built and tested without it.
- **`PlayerMoveEvents.ROLLED` / `GLIDE_STARTED`** (server): movement fires them; origins react (Bloodbound's
  blood mist on roll).
- **`PlayerTraits.STAMINA_MAX_PENALTY`**: −25 max stamina per level (movement's `traits.staminaMaxPenaltyPerLevel`,
  floor `traits.staminaMaxFloor`). Origins with "−25 max stamina" set level 1. A trait rather than a new
  attribute because stamina is movement's own number, not a vanilla attribute.
