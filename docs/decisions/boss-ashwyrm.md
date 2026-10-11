# Decisions: boss-ashwyrm (Stage 2b)

Defaults picked by the `boss-ashwyrm` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Config: `config/blocksouls/boss-ashwyrm.json` (`AshwyrmConfig`); every number below is a field there.

## Fight
- **Stats:** 600 HP base (framework scaling: +50% per extra player, difficulty), armor 8, fire-immune,
  knockback-immune, 500 XP. Arena radius **28** (it's 12 blocks long and flies; a smaller ring would cage it).
- **Hitboxes:** a 3.5 × 3.0 body box plus four `BossPart`s: **head ×2** (weak point), neck, tail and tail tip at ×1 so
  the long neck and tail can be hit at all. Part offsets match the model at rest; they don't follow attack animations.
- **Phase thresholds** 60% and 25%, as briefed. Each change plays the framework's 2 s invulnerable roar.
- **P1 (ground):** bite 0.6 s windup / 5 dmg (kept under the 30%-of-health line so the short windup passes the
  telegraph rule even at P3's ×1.2), tail sweep 1 s / 8 dmg, a 7-block circle swept slice by slice from behind over
  0.3 s, 1 block tall (jumpable), fire breath 1.2 s windup then a 10-block 50° cone for 3 s, 3 dmg every 0.5 s plus
  burning, turning 1.2°/tick after its target, wing buffet 0.9 s / 4 dmg, an 8-block ring with strong knockback.
- **P2 (air):** circles its home at 16 blocks (capped to arena − 4), 12 above the ground. **Fireball run**: the line
  and three impact circles are telegraphed for 1.5 s while it flies to the line's start; it then flies the line and
  drops one fireball per circle (8 dmg, radius 2.5), each leaving 2.5 s of burning ground (2 dmg / 0.5 s).
  **Landing**: after **20 s** airborne (the brief's "~25 s" counted from landing to landing including the 2 s descent
  and the run that was in progress) it glides onto a telegraphed circle near its target (6 dmg shockwave), then lies
  stunned for **6 s** taking ×2 (stacks with the head: ×4 on the head). It takes off again right after.
  Landing is an attack (`land`, recovery = stun), so the stun can't be interrupted by the attack picker; the ×2 is
  applied in `Ashwyrm.hurtServer` while that recovery runs.
- **P3 (ground, enraged):** P1's moves plus **Firestorm** (1.5 s telegraph circles under every player and 2 strays,
  meteors visible for the last 0.75 s, 8 dmg + burning). ×1.2 damage, ×1.3 speed, ×0.6 cooldowns, ×0.7 recovery. If it
  enters P3 in the air it drops out of the sky.
- **No terrain damage:** fireballs and meteors are particle projectiles with scripted impacts, never vanilla fireballs
  or explosions. `griefing: false` by default; when true they place a real fire block where they land (which can then
  spread under normal fire rules). Nothing ever breaks blocks.
- **Stuck on a cliff:** on the ground, if the target is 10+ blocks away and the path is blocked for 1.5 s, it hops
  toward them on its wings (3 s cooldown). Mountain peaks are terrible for pathfinding otherwise.

## Field spawn
- **Daily roll 30%** (`field.chancePerDay`), 10 min cooldown after leaving, leaves after 10 min unattended.
- **Spot:** 32 random loaded columns 48–128 blocks from a player (overworld only); the **highest** one that is in a
  `#minecraft:is_mountain` / `#minecraft:is_hill` biome **or** at Y ≥ 110, with a sturdy dry floor, a 3×3 × 5 clear
  space and open sky. Faces the player it came for.
- **Arrival:** a field spawn starts 30 blocks above its spot and glides down over 4 s with wing beats, then roars
  (heard ~100 blocks out) with a burst of ash and a tall signal-smoke column; the framework's lightning flash, horn and
  smoke beacon still apply. `/bs boss spawn` skips the arrival (spawns on the ground).
- **Leaving:** instead of the framework's puff, it roars and flies up and away for 5 s, then is removed (still counts
  as "left" for the cooldown). A player who engages it on the way out brings it back down.

## Drops
- Main table (vanilla + own items only): **2 Ender Eyes**, **4–6 `ashwyrm_scale`**, **1 `ember_barrel`**.
  Legendary gun via the shared `boss_drops/legendary_gun` extra table (only when guns is loaded). 500 XP.
- **`ashwyrm_scale`** (uncommon) and **`ember_barrel`** (rare, fire-resistant, stacks to 16) are plain items owned by
  `bosses`, ids in `CommonIds.ASHWYRM_SCALE` / `CommonIds.EMBER_BARREL` so guns/world recipes can use them (with a
  `blocksouls_bosses` load condition). Creative: Ingredients tab after blaze rods.

## Look and sound
- Vanilla `LayerDefinition` model drawn at **2×**: body, two-segment neck, horned head with a jaw, two-segment wings
  (bone + membrane), three-segment tail with a fin, four legs. ~12 blocks snout to tail and wingtip to wingtip.
  Everything hangs off `body` so the tail sweep spins the whole dragon. Procedural idle: neck/tail sway, breathing, a
  four-legged walk with wings folded; in flight the wings beat (tips trailing), legs tuck, neck stretches.
  Keyframes per attack stage, derived from the config's `AttackStats`.
- Textures from `tools/assets/ashwyrm.py` (own script, not `boss_skins.py`, so the parallel boss sessions don't edit
  the same file): ash-grey scale rows, pale belly, bone horns/claws, dark membranes. `ashwyrm_glow` (always): ember
  cracks, eyes, the inside of the jaws. `ashwyrm_enraged_glow` (P3): more cracks, smouldering membranes. Client
  particles: embers/ash off the body (every tick when enraged), nostril smoke, ash trail in flight, crit sparks over
  the head while stunned. The renderer's culling box is widened by 6.5 blocks so the neck and wings don't pop.
- Sounds: re-pitched vanilla placeholders (ender dragon growl/flap/hurt/death, ravager, ghast, explosions); music is
  vanilla `music.dragon`.

## Tests
`AshwyrmGameTests`: registration/tag/stats/items; a scripted phase walk P1 → P2 (takes off, lands at least once
with a vulnerability window, attacks keep finishing) → P3 (grounded, still attacking); head part ×2; stunned ×2; a
full fireball run over planks/grass/bookshelf/hay changes no block; `BossEvents.KILLED` with `CommonIds.ASHWYRM`; loot.
