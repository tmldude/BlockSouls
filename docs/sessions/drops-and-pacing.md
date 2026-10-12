# Session: drops-and-pacing (v0.11, playtest fixes)

**Lanes:** `game` (wave rewards and `RunConfig` defaults), `mobs` loot tables, `guns`. **Branch:**
`claude/drops-and-pacing`. **PR title:** `game/guns/mobs: wave drops that feel good, faster pacing`.

Owner playtest (`docs/playtest/2026-10-12-owner-feedback.md`, items 3 and 4): "drops from waves were not good / need
to feel more roguelike" and "pacing feels really slow". Tune and add guarantees. Don't redesign the loop (v2 owns
structure). Every number goes in config.

## Drops (roguelike = frequent, visible, escalating)
- **Guarantees:** every cleared wave drops a visible reward at the last kill (a small reward bundle scaled by wave and
  `PartyScaling`); a guaranteed gun (or the full parts for one) by the end of night 2 if the party has none beyond
  the starter pistol; elites always drop something worth walking to.
- **Legible rarity:** rare and legendary gun drops on the ground get a colored beam/glow and a sound sting; the
  ground item name shows rarity color.
- **Ammo:** bigger ammo stacks and drops.
- **Materials:** stay in their 2+ recipe web (`docs/CONTENT.md`).
- The `world` session raises material drop rates. Don't edit `world/`.

## Pacing (defaults only, in `RunConfig`)
- Shorter wave breaks: 20 s → ~10 s.
- One more early wave per night: `wavesPerNight` starts 1, 2, 2…
- An earlier dusk cue.
- A cheaper first real gun recipe, so the loop starts shooting sooner.

Log every changed default with the old value in `docs/decisions/drops-and-pacing.md`.

## Also
Gun parts get tooltips saying which guns use them.

## Tests
- Unit: the reward-guarantee logic, including night-2 gun pity, at 1 and 8 players.
- Gametests:
  - clearing a wave spawns the reward;
  - an elite always drops;
  - a rare gun item entity carries the beam marker.

## Done when
CI green; the crafting check passes; `docs/decisions/drops-and-pacing.md`.
