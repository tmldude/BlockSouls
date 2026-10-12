# Decisions: v2-builds

Defaults picked while building the synergy build content (`docs/sessions/v2-builds.md`, PLAN2 §3a). Same style as
`DECISIONS.md`: where this conflicts with a plan file, this logs what was actually built.

## Contracts (PR 1, `common`)

- **`CombatEvents.HIT / RELOADED` are the one combat-event class** (reconciled with the orchestrator): guns fires
  them; the build rules listen. `MovementEvents` was dropped in favor of #48's `PlayerMoveEvents.ROLLED`.
- **Providers, not events, for things another module *does* on request:** `Stamina.add` (movement installs),
  `WeaponHooks.reload` (guns installs), `BuildHooks` (game installs: apply/purge curses, trinket offers). Each is a
  no-op until its module installs it, so every module keeps working standalone and the keystones that depend on
  them (Deadeye Wind, Covering Fire) are inert rather than broken until the wiring lands.
- **The Soul is an item** (`blocksouls_game:soul`, `CommonIds.SOUL`), registered by `game`: it drops, stacks, trades
  and sits in recipes like any material, so the shopkeeper (director), rune trees (players) and soul-infused
  crafting all read one thing. Skipping a relic pays in it.
- **Trinket offers are an item too** (`blocksouls_game:trinket_case`, `CommonIds.TRINKET_CASE`): using the case
  shows the pick-1-of-3. That way a loot table (pylon, cursed chest, structure chest, a floor's secret) can offer a
  trinket by dropping the case, offers survive logout and closing the screen, and a party can hand a case to the
  player who needs it. `BuildHooks.offerTrinkets` simply gives a case.
- `BuildHooks` lives in `common.synergy` (beside `SynergyTags`), not a `common.build` package: a directory named
  `build/` is swallowed by the repo's Gradle ignore rule (CI caught it).

## Schema

- **Boons, curses and trinkets share one JSON schema and one id space**, in three folders
  (`data/<ns>/boons|curses|trinkets/`). The folder sets the `Kind`; curses and trinkets may omit `tier` (it only
  colors the frame). A trinket and a boon can't share a path.
- **`levels` replaces `stackable`** (still accepted). Ordinary boons default to `boons.json`'s
  `levels.defaultLevels` = **3 pips**; keystones, curses and trinkets are always 1. Binary boons (Night Eyes,
  Fire Ward, flight, double jump, the combos) say `"levels": 1`; effect boons level their amplifier, attribute
  boons multiply their amount, trait boons their trait level.
- **`tags`** default to the `blocksouls:` namespace; every shipped boon declares at least one (unit-tested). Curses
  carry no tags: a curse isn't a build. Trinkets do, so the build line can lean on them.
- **`keystone: true`** needs a rare or legendary tier. A keystone without a trigger tag (Ricochet Doctrine) is legal
  and simply never excluded.
- **Effect type `rule`** names code in `BoonRules` (`blocksouls_game:` default namespace). The catalog warns on
  unknown rules the way it warns on unknown items.

## Offers

- **Weighting:** a card's draw weight is `1 + 0.5 × (owned stacks of its tags)`, capped at `1 + 3.0`
  (`offers.tagWeightPerStack / tagWeightMax`). Three fire stacks make a fire boon ~70% of the first draw against
  ~50% flat (unit-tested). The Wildcard and the tier fallback are unchanged.
- **Keystones need investment:** a keystone only joins the pool once the player owns `keystoneMinTagStacks` (1)
  stacks of one of its tags, so the first relic never offers a rule-changer the build can't use.
- **One keystone per trigger tag** (`offers.oneKeystonePerTrigger`): owning Powder Burn hides Undertow (both
  `on_reload`). Trigger tags are recognized by the `on_` path prefix in the pure roller; the catalog cross-checks
  the real `SynergyTags` category at load.
- **Upgrade card:** with `offers.upgradeCardChance` (0.15) and something to level, the first card becomes
  "upgrade a random owned boon" (the Wildcard may still take the last). Picking it levels a random non-maxed boon.
- **Skip pays souls:** `souls.skipCommon/Rare/Legendary` = 1 / 2 / 4, plus trinket bonuses (Soul Jar +1). The relic
  is consumed; the offer is cleared.
- **Reroll Charm** (soul-infused crafting: 3 souls + 2 gunmetal): rerolls the pending relic or trinket offer.
  It is the builds side's soul recipe (R1 for souls); v2-guns adds the second.

## Keystones

Five seeds plus three of ours; numbers in `boons.json` `rules.*`:
- *Powder Burn* (legendary, on_reload + explosion): the last 6 hits are remembered; a reload deals 50% of their
  summed damage to hostiles within 3.5 blocks.
- *Ricochet Doctrine* (rare, ricochet + kinetic): the `ricochet_inherit` trait; guns applies it.
- *Red Ledger* (legendary, on_kill + heal): natural (food) regen off via a `FoodData.tick` redirect; kills grant
  +2 absorption up to 20 (ten hearts). The JSON also raises `minecraft:max_absorption` by 20, because vanilla
  clamps absorption to that attribute (default 0; CI caught it).
- *Ember Step* (rare, on_roll + fire): 10 ticks of trail after a roll: hostiles within 1.5 blocks burn (60 ticks)
  and take 2 damage each tick; short-lived fire blocks are placed and removed again after 60 ticks.
- *Deadeye Wind* (rare, on_precision + kinetic): +25 stamina per precision hit (× level) through `Stamina`.
- *Storm Chorus* (legendary, on_kill + storm): every 4th kill strikes the nearest hostile within 12 blocks.
- *Blight Tithe* (rare, on_precision + blight + heal): precision hits wither (II, 3 s); a kill on a withered
  target heals 2 hearts.
- *Undertow* (rare, on_reload + pull): a finished reload pulls hostiles within 8 blocks toward you.

## Curses

- Hemorrhage (healing ×0.75, a `LivingEntity.heal` argument mixin), Loud (idle hostiles within 32 blocks target
  you every second), Heavy Pockets (−3 souls on going down, taken before the downed system wipes the curse),
  Brittle (−4 armor), Leaden (−10% speed). Cap `curses.maxCurses` = 5. `BuildHooks.applyRandomCurse` picks among
  the ones not owned. Lost on going down with the boons; purged one at a time (most recent first by default).

## Trinkets

- 3 slots (`trinkets.slots`); **a pick with full slots replaces the oldest trinket** (shown on the screen) rather
  than refusing, so late offers stay interesting.
- Dawn Clock exposes a prep-time multiplier through `BoonRules.prepTimeMultiplier(server)` (party max; one clock
  suffices). **The run's clock lane (spine/director) reads it** when it lengthens the day; not wired in this PR.
- Last Shell: once per siege (reset at dusk), a lethal hit leaves you at 1 heart with 2 s of full immunity;
  implemented as an `ALLOW_DEATH` phase ordered before the downed system's listener.
- Magnet Core (items within 6 blocks fly to you), Siege Lens (at dawn, names which of the next 2 nights are Blood
  Moons; the run seed is package-private to `game/run`, so random modifiers stay unpredicted until that lane
  exposes a forecast), Soul Jar, Thick Coat (+3 armor), Lucky Coin (+1 luck), Iron Lung (+1 stamina regen trait).
- **Every participant of a boss kill gets a Trinket Case** (`trinkets.caseOnBossKill`), like dawn relics are
  per player; nothing in the build systems scales with party size, so the 1- and 8-player gametests assert the
  per-player grant instead of a curve. (Party-dependent values would go through `PartyScaling`.)

## Cross-player boons

- Covering Fire (precision kills reload the nearest standing ally within 24 blocks via `WeaponHooks`), Shared
  Breath (a revive gives reviver and revived Resistance V for 200 ticks), Sworn Partner (sneak-use a teammate while
  owning it: both get the higher of the two players' own stamina-regen trait levels; the partner is a persistent
  attachment, re-applied whenever either build changes).

## HUD and client

- **Build line**: top-left corner, icon + name + stacks for the top `offers.buildLineTags` (2) tags, sent by the
  server with every boon sync (`BuildLine` payload); tag sprites generated into `gui/sprites/synergy/`.
- **Trinket row**: three framed slots right of the hotbar (bone-colored frames; empty slots dim).
- Curses show in the boon row with a blight-red frame; keystones carry a gold key mark on their icon.
- `boons.json` carries `defaultsVersion` + `migrate()` from day one so a later default change can migrate.
