# v2-director session decisions

Defaults picked while building the AI Director and the run economy (PLAN2.md §3; brief `docs/sessions/v2-director.md`).
Same style as `DECISIONS.md`: where this conflicts with a plan file, this logs what was actually built. Every
number below is a field in `config/blocksouls/director.json` (`DirectorConfig`, migrated by `defaultsVersion`).

## Scope and switches

- **Expedition only by default.** `Director.active` = `enabled && (expedition || enabledInClassic) && run active`.
  `enabledInClassic` defaults to false, so every scheduler hook answers with the scheduler's own fallback and
  the classic run is byte-for-byte unchanged.
- **Scheduler hooks are levers, not a second scheduler.** `game/run` asks a `DirectorHooks.Spawner` for: spawns
  this tick (token bucket), the spawn angle (influence map), preferred mobs (composition), an elite bias, and
  the intermission length. The wave roster, counts and elites stay the run's; the Director only reshapes timing,
  direction and mix.
- **The Director's clock is overworld game seconds**; log lines print `[mm:ss]` since the run started, so
  `/bs director log` reads as a run timeline.

## Stress and pacing

- **Stress** = weighted sum of five 0–1 readings over a 30 s window: damage taken (pool 30 HP per player,
  weight 0.4), downs (0.5 per player = full, 0.25), ammo shortfall (96 rounds per player = comfortable, 0.12),
  incoming DPS (0.13), and power deficit (boons vs `0.4 × threat`, 0.1), smoothed by 0.25 per second. No guns
  module means ammo reads as comfortable.
- **Moods:** stress above 0.75 starts a lull (12 s asked), below 0.25 a spike; 20 s cooldown between changes.
  A spike lasts one second (a burst of `burstSize × 2`, party-scaled by `PartyScaling.directorBurst`).
- **Lull ceiling (20 s) is a clamp, not a check.** `PacingBudget.clampLull` caps every lull at the ceiling
  whatever asked for it; the `VIOLATION` log kind exists only as a safety net.
- **Pacing budget (90 s):** no decision or threat for that long forces an action: a spike during a wave, a
  break cut to 3 s during an intermission, or a scout pack (3, party-scaled) during the day. Each is logged as a
  `VIOLATION` line so tuning can see when the budget was needed.
- **Elite bias** = `PartyScaling.directorEliteShare(n) − 0.1` (the roster's own baseline elites): high solo,
  gone at a full party, so a solo player still meets elites without 8 players drowning in them.

## Composition and the influence map

- **Composition:** a camping party (inside 8 blocks for 20 s) gets FLANKERS when it's being hurt (damage
  reading ≥ 0.2) and BRUTES when it's comfortable (dig them out); otherwise MIXED. Flankers take a 0.6 share of
  spawns, brutes 0.35; mob ids are config lists (`crawler`/`ash_hound`, `brute`/`mutant_brute`).
- **Influence map:** 8 sectors around the party; where players look (and so shoot) heats sectors (spread 0.4 to
  the neighbours), kills add 0.5, decay 0.9 per second. Spawns pick sectors weighted toward the cold side with
  sharpness 3; a flank spawn goes to the coldest sector opposite the party's facing. Flank decisions log at
  most every 5 s.

## The day (PLAN2 §3a)

- **Day timer:** 8 minutes per act (`minutesByAct` overrides; trinkets' prep multiplier applies). It only ticks
  while someone is online, and only in expedition runs. When it runs out the siege starts (`siegeAtDayEnd`).
- **Camp anchor:** the act floor's camp from `Floors.forAct`; with no floor (`placeWithoutFloor`) the first
  player's position. The horn and shopkeeper are placed at configured offsets from it. `Places.anchor` falls
  back to the first player and then the level spawn.
- **Day mobs:** a pack of 2 every 70 s (20 % elite, at most 10 alive).
- **Loot cadence:** if there's no real win (an elite, a node or a crate) for 30–60 s, a cache barrel turns up
  near a player. A win restarts the deadline (20 s).
- **Random events** are weighted by act. The mini-boss rotates through the roster by the run seed starting at
  act 2, skipping bosses that aren't installed. A "stranded ally" is a player-created iron golem (no new
  entity). The "wandering trader" event is the vanilla trader.

## The horn

- **Early-horn buff** grows with the share of the day skipped: 3 souls per minute skipped; past 25 % the
  siege drops shift up a tier; past 50 % everyone also gets a rare relic. An overclocked horn (sneak-use)
  also adds 1 threat and owes 1 rare relic per player when the siege completes. The horn only sounds during
  the day (a second blow during the siege is refused).

## Objectives

- **One siege objective** in about a third of sieges (chance 0.34), placed 30 blocks from the anchor. Each
  success pays a rare relic. The kinds are:
  - Supply drop: hold 6 blocks around a barrel for 90 s.
  - Volatile core: carry a glowing item to the camp.
  - Fleeing zombie: catch it within 60 s.
  - Generator: keep it alive (40 health) through the charge.
- **Stand-ins:** the barrel, the core item and the lodestone generator are vanilla blocks and items. The world
  module owns blocks, so the objectives don't add any; dedicated art can replace them later without a config
  change.
- **Objective and pylon state is live-only** (not saved). A restart mid-siege drops them, which is the same as
  an abandoned siege.
- **An abandoned siege** (the charge ends without completing because the party left the act) fails the
  objective and loses wagers. Relics owed by an overclocked horn stay owed until the next siege completes.
- `Objectives.position()` is public for gametests and `/bs director status`.

## Economy objects (PLAN2 §3c)

- **The world module owns the blocks** (blood altar, cursed chest, challenge pylon, camp horn) and the
  shopkeeper entity. Each one fires `EconomyEvents.CAMP_OBJECT_USED`, and game handles it, so the world module
  never imports game.
- **The blocks are unbreakable and immovable** (destroy speed −1, `PushReaction.IMMOVEABLE`, no loot table).
  The Director removes them when they're consumed.
- **The shopkeeper** is invulnerable, never despawns, and only looks at players. Each camp has exactly one:
  placing a new one removes the old. Only the main hand opens the board.
- **Blood altar:** pay 4 hearts (never below 1 heart) for a roll of `blocksouls_game:economy/blood_altar`.
  Sneak-use takes a random curse instead, and so does a player with no hearts left (`curseWithoutBoons`).
  Paying with a boon isn't offered because there's no boon-removal API. The hearts are a max-health modifier
  that is cleared at run end.
- **Cursed chest:** a roll of `economy/cursed_chest`, then the next siege's modifiers go up 1 tier, and the
  opener takes a curse. It is consumed on use.
- **Challenge pylon:** a 60 s circle that shrinks from 12 to 3 blocks. It sends a horde pack of 3
  (× `directorBurst`) every 8 s. Everyone inside at the end gets a legendary relic. The pylon is consumed
  either way.
- **Soul wager:** stake 10 souls in the first 30 s of a siege; it pays 3× on completion and is lost if the
  staker goes down.
- **Shop:** 6 offers drawn from the pool, by weight and act, seeded by the run seed and the act. The board is
  the same for everyone on a floor and changes per act. Prices are paid in items, and each price shows the
  icon of where that resource comes from. A rare offer must cost two or more resource kinds besides souls
  (checked at load). Fixed services: perk reroll (6 souls, through `WeaponHooks.rerollPerks`) and curse purge
  (10 souls).
- **Loot tables** live in game (`loot_table/economy/`) and use only existing gear and relic tables, so the
  recipe web rule (R1) adds no new drops.

## Code shape

- **`DirectorRules`** holds the pure rules (shop stocking, event pick, pylon radius) so the offline unit tests
  run without Minecraft on the classpath.
- **Gametests:** each Director gametest has its own test environment (and so its own batch), because the
  Director is global state. The expedition test scripts stress through `Director.setStress` with smoothing 0.
