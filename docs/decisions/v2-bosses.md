# Decisions: v2-bosses (Stage 6B)

Defaults picked by the `v2-bosses` session (PLAN2 §3h, the `bosses` half of §3k Tier 1). Same rules as `DECISIONS.md`
(the root file wins on conflict). Framework numbers live in `config/blocksouls/bosses.json` (`posture`, `picker`,
`reactions`, `affixes`, `presentation`); archetype numbers in each boss's own config file.

## Fairness
- **The invariants are constants** in `bosses/logic/Fairness.java`: big hit = more than 30% of a player's max health,
  big hits need at least 15 ticks of windup, reactions wait 6–8 ticks and come at most every 40 ticks, utility
  scoring stays within 0.25×–3× of an attack's weight, and feints only appear from the second phase on. Config values
  that touch them are clamped, so a server file can make a boss slower but never faster than the floor.
- **The readability rule is a unit test** (`ReadabilityTest`). It walks every boss config reflectively, including the
  nested archetype params, and checks base damage × the highest phase damage multiplier against a 20 HP player.
  Damage scaling for party size doesn't enter: health scales with the party, damage doesn't.
- **"Telegraphs never lie":** every archetype calls `AttackRun.telegraphed()` from its first windup tick. The
  gametests force each archetype and check that the first telegraph comes before the end of the windup and before
  the first hit.

## Posture
- **Base poise 100**, scaled for the party with `PartyScaling` like health (`PostureMath.maxPoise`, tested at 1 and
  8 players). Precision hits give 5 + 0.4 × damage, weak-point hits 3, and a roll inside an attack's hit window
  (within 6 ticks before it, and in reach) gives 14, once per player per attack. Poise decays at 0.6/tick after 3 s
  without gain.
- **The visceral window lasts 3 s** (60 ticks). The boss stands still and doesn't attack, and takes ×2 damage. After
  it, poise can't build for 5 s (lockout), so posture can't be chained.
- **Target stamina is unknown to the boss side** (guns/movement own stamina and expose nothing in `common` yet), so
  the picker's stamina term uses 1.0 (fresh). The weight stays in the config for when it's exposed.

## Archetypes
- **The archetype params live in a pure-Java class** (`archetype/ArchetypeParams`). The configs embed them, and the
  offline unit-test runner loads configs without Minecraft on the classpath.
- **Each boss gets 2+ archetypes** next to its v1 moves (Furnace Knight: ring, pools, leap, feint; Stormplate:
  cross, beam, charge, summon, feint; Pale Rider: burst, wisps, leap, feint; Ashwyrm: burrow, spiral, pools; the
  final dragon: void beam, void spiral, wisps). They reuse the boss's v1 animations, so no model work was needed.
- **Feints:** the bait move's start and part of its windup play, then the real move starts with its own full windup.
  Only from the second phase on, only into a follow-up whose windup meets the big-hit floor, and never into a move a
  broken part took away.
- **Burrow:** the boss is untouchable while under (invisible, no physics) and surfaces where its plume is. The last
  max(15, `warnTicks`) ticks of the windup stop the plume and fill the eruption circle. A burrowed boss saved to disk
  surfaces when it loads.
- **Summon-shield:** blocks all damage while its adds live, with a 30 s failsafe so a lost add can't make the boss
  immortal. The adds are wave mobs of the configured tier, or zombies without the mobs module.
- **Sticking charge:** a charge that hits a wall ends early and leaves the boss stuck in a long recovery (40 ticks
  on the Furnace Knight). The v1 Furnace Knight charge does the same now.
- **Sidesteps** (Furnace Knight, Stormplate, Pale Rider): only against a player who has held their aim on the boss
  for 7 ticks (clamped to 6–8), at most every 5 s, at 50% chance.

## Part breaks
- **Stormplate's left pauldron** breaks after taking 8% of max health and takes the spear throw away, along with the
  feint into it. The knight gets 12% faster. The left pauldron now has its own UVs (`stormplate.py` `pauldron_l`),
  so only the broken one shows the damage.
- **Ashwyrm's tail** breaks the same way and takes `tail_sweep` away. The whole tail chain shows the damage.
- A reset restores the parts.

## Affixes
- **Field bosses in Expedition, from floor 2**, always carry one (chance 1.0). Outside Expedition they never do. The
  boss attaches its affix itself when it spawns, rather than going through `Affixes.apply`: bosses have their own
  bars and names, and the affix needs the boss's hit hooks.
- The name gets the affix prefix ("Burning Ashwyrm"), and the skin is tinted by a redraw layer.

## Presentation
- **Name splash:** shown for 1.5 s to everyone in the fight or in bar range, once per fight. It fires on engage,
  not on joining.
- **Hit-stop:** 2 ticks on the boss when it lands a hit worth 30% of a player's health, or takes one worth 4% of its
  own. A part break gets twice that.
- **Damage states** are overlays drawn below 66% and 33% health. **Cracks** glow in the boss's theme colour; each
  phase's set holds the last one's plus more. Both are generated from each boss's skin by
  `tools/assets/boss_damage.py`.

## Debug
- `/bs boss posture [add <n> | break]`, `/bs boss break <part>`, `/bs boss affix <name|none>`, and
  `/bs boss attack <name>`, which forces any attack the boss has.
