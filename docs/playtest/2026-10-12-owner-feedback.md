# Playtest feedback — owner, 2026-10-12 (v0.9 era build)

Raw feedback, triaged into actionable work. Quick wins belong in the v1 polish lane (v0.10.x);
structural answers are already in PLAN2.md (noted per item). Every number below is a starting point —
tunable in config, owner has final say on feel.

## 1. "I don't know how to make anything / the guns" + "couldn't repair a car"

**The #1 blocker: discoverability.** Three fixes, all v1-lane:
- **Ship a recipe viewer in the modpack**: EMI (prefer, lighter) or REI — whichever has a solid 26.3
  Fabric build; client-side entry in the pack, optional on the server. This alone fixes "how do I craft".
- **Vanilla recipe book**: our recipes should unlock as their ingredients are picked up (unlock criteria
  on every recipe), so they appear in the crafting-table book without any mod.
- **Advancements as the tutorial** (show, don't tell): one small advancement chain per loop —
  scrap → gunmetal ingot → first gun; find a wreck → craft wrench → repair the kart ("Grease Monkey");
  first boss, first boon. Each advancement's description states the *how* ("Use a Wrench on a wreck with
  4 Scrap Plates in your inventory"). Wrecks should also glow/particle-hint when a player holds the wrench.
- Tooltips: wrench and vehicle parts say what they repair; gun parts say which bench/recipe uses them.

## 2. "Resources feel far and few between"

- Raise `world` generation defaults: gunmetal vein count and scrap-pile frequency up (~2× as a starting
  point), scrap piles biased near structures/roads so early loops find them.
- Wave kills should drop crafting materials more reliably (see 3) so fighting is also mining.

## 3. "Drops from waves were not good / need to feel more roguelike"

Roguelike drops = frequent, visible, escalating:
- **Guarantees, not just rolls**: every cleared wave drops at least something visible; first gun (or gun
  recipe's full parts) guaranteed by end of night 1–2; elites always drop something worth walking to.
- **Make rarity legible**: rare+ drops get a glow/beam particle and a sound sting; gun drops show rarity
  color on the ground item name.
- Bigger ammo stacks, occasional material bursts; boss/elite "soul shard" drips so the Souls currency is
  felt early.
- Structural fix is v2 (synergy builds, economy objects, relic weighting) — this item is about v1 feel.

## 4. "Pacing feels really slow"

- v1 tuning levers (config, playtest to taste): shorter between-wave breaks, one more wave per night
  early, dusk cue earlier, cheaper first-gun recipe so the loop starts shooting sooner.
- The structural answer is v2's Expedition (acts + player-paced portal charge + threat ratchet) — don't
  over-rotate v1's open loop; tune, don't redesign.

## 5. "Web swinging feels off" / "Mario jump feels a little off"

Feel can only be tuned in-game, so give the owner the loop:
- **`/bs config reload`** (all modules) so movement.json edits apply live without restart — if any config
  isn't hot-reloadable, make it so.
- A short tuning map in docs (which field fixes which complaint):
  swing: `ropeLength` slack, `swingBoost`, `releaseBoost`, raycast `maxDistance`, gravity during swing;
  jump: `holdBoost` per-tick, hold cap ticks, `fallGravityMult`, coyote/buffer ticks, triple-jump window.
- Defaults pass after the owner tunes: whatever feels right becomes the new default, logged.

## 6. "A ton of other mini details missing"

Standing items for the polish backlog: hit markers / damage feedback on gunfire, low-ammo and no-ammo
cues, wave-clear fanfare + reward-chest beam, downed-teammate screen-edge indicator, dusk warning sound
earlier, death recap line (what killed you, with what). Each is small; batch them.

## Routing

- Items 1–3, 6 and the `/bs config reload` of 5: **v1 polish lane** (v0.10.x), coordinated with the v1
  orchestrator.
- Item 4's structure and item 3's systems: **v2** (PLAN2.md), already in flight.
- Item 5's tuning session: the owner + live reload; defaults PR afterwards.
