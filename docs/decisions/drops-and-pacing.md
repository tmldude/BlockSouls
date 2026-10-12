# Drops and pacing decisions

Defaults picked by the `drops-and-pacing` session (v0.11, playtest fixes). Same rules as `DECISIONS.md`. Owner feedback
items 3 ("drops from waves were not good / need to feel more roguelike") and 4 ("pacing feels really slow") in
`docs/playtest/2026-10-12-owner-feedback.md`. Builds on v0.10.1's pacing pass (#42) and the `resources` session's
material rates (#43); doesn't touch either's numbers.

Every changed default migrates onto live servers the same way #42 did: each config has a `defaultsVersion`, a value
still at its old default moves to the new one on load, anything hand-tuned stays.

## Changed defaults (old → new)

| File · field | Old | New | Why |
|---|---|---|---|
| `game-run.json` `duskCueTime` | 11500 | **10000** | dusk warning ~90 s before dusk instead of ~45 s at 12-min days: time to get back and get ready |
| `game-run.json` `tierOdds` | rare from wave 4, legendary from 7 | **rare from 3, legendary from 6** | escalation is felt on night 2, not night 3 |
| `guns.json` `loot.wave{Common,Rare,Legendary}PrimaryChance` | 0.12 / 0.15 / 0.18 | **0.25 / 0.30 / 0.35** | ~2× ammo per tier roll |
| `guns.json` `loot.wave{Common,Rare,Legendary}SpecialChance` | 0.03 / 0.06 / 0.10 | **0.06 / 0.12 / 0.20** | same |
| `guns.json` wave-kill ammo stack | 4–12 primary, 2–4 special (hard-coded) | **8–20, 3–6** (`wavePrimaryMin/Max`, `waveSpecialMin/Max`) | bigger drops, now config |
| `guns.json` reward-chest ammo | 16–32 primary, 4–10 special (hard-coded) | **24–48, 6–12** per reward roll (`waveClear*Min/Max`) | |
| Primary / Special Ammo max stack | 64 / 32 | **99 / 64** (`loot.primaryAmmoStack`, `specialAmmoStack`) | "bigger ammo stacks"; 99 is vanilla's cap |
| `primary_ammo` recipe | ×16 | **×24** | |
| `gun_barrel` recipe | `GIG` (2 gunmetal + iron) | **`IGI` (1 gunmetal + 2 iron)** | cheaper first gun; gunmetal is the bottleneck |
| `hand_cannon` recipe | barrel + receiver + iron | **barrel + receiver** | first real gun: 2 gunmetal + scrap + redstone + 2 iron (was 3 gunmetal + 2 iron + …) |
| `mobs.json` `mutantBrute.minWave` | 7 | **6** | first elite on night 3's second wave instead of night 4 |

The new `guns.json`, `mobs.json` migrations are each module's revision 1; `game-run.json` is revision 2.

## New guarantees (all config)

- **Reward bundle at the last kill.** A cleared wave's chest goes where its last mob died (`rewardSearchRadius` 4; falls
  back to the last killer, then any player), and the reward table rolls
  `round((rewardRollsBase 1 + rewardRollsPerWave 0.15 · (wave − 1)) · PartyScaling.rewards)` times, 1 to
  `rewardRollsMax` 8. Solo: 1 roll at wave 1, 2 by wave 8. 8 players: 3 at wave 1. Each roll carries guns' ammo and its
  50% gun chance, so bigger parties and later waves get more guns. Kept a chest (not loose items): it's visible, it
  doesn't despawn, and the party can split it.
- **`PartyScaling.rewards`** (new `party.json` curve `rewards`, 0.5 per extra player, exponent 0.8: 2 → ×1.5,
  4 → ×2.2, 8 → ×3.4) scales party-shared loot, per CLAUDE.md's rule that anything player-count dependent goes
  through `PartyScaling`.
- **Night-2 pity gun.** From `gunPityNight` 2, if nobody in the party carries a gun other than the Starter Pistol,
  the next wave-clear chest gets `round(gunPityGuns 1 · PartyScaling.rewards)` guns (1 solo, 3 at 8 players) rolled at
  `pityGunTier` "rare" from the new `blocksouls_game:gun_pity` table (empty in game, filled by guns). If no wave is
  cleared that night, they land at the party's feet at dawn, glowing. Once per run. With 2 waves a night that's wave 3,
  one wave before the Starter Pistol breaks. A whole gun, not parts: parts still need a crafting table and gunmetal,
  which is exactly what a stuck party lacks.
- **Elites always drop.** Every elite kill rolls its tier table (rare or better, as before, ignoring `dropChance`) plus
  `eliteDropRolls` 1 roll of the new `blocksouls_game:wave_drops/elite` table (2 picks of iron, gold, diamonds, golden
  apple, pearls, emeralds; guns adds a gun at `eliteGunChance` 0.35 and 16–32 Primary Ammo). The drops glow
  (`eliteDropsGlow`) with a chime and a burst of particles. Elites drop even when a non-player killed them (they're
  rare and the party earned the fight); ordinary mobs still need a player kill.

## Legible rarity (guns)

- Rare and legendary gun item entities get a **particle beam** (dust in the tooltip's rarity color, aqua / light purple,
  `drops.beamHeight` 6 blocks every `beamIntervalTicks` 8, forced so it's seen from far), a **glow outline**, their
  **name in rarity color** above the item, and on landing a **sound sting** (`gun.drop.rare`: two rising bell notes;
  `gun.drop.legendary`: a three-note arpeggio with a swell). Both synthesized in `tools/assets/gun_sounds.py`.
  Each piece toggles in `guns.json` `drops`. Marker: entity tag `blocksouls_guns.rarity_beam`.
- Common guns stay plain: the beam means "worth a detour".
- The glow outline is vanilla's (white); colored outlines would need scoreboard teams per item, which leak into the
  world save. The beam and the name carry the color.

## Also

- Gun parts' tooltips list the guns they're crafted into ("Used in: Hand Cannon, Auto Rifle, …"), from
  `PartUses`; a unit test keeps it in step with the recipes.
- Debug: `/bs wave reward` (reward chest now, for the current wave), `/bs run pity` (force the pity gun at the
  party's feet); `/bs run state` shows `gunPityGiven`.
- Not changed: `dropChance`, wave sizes, breaks, day length (all #42). Materials in wave drops belong to `world`
  (`resources` session); the new elite table is open for `world` to add materials to later via `LootTableEvents`.
