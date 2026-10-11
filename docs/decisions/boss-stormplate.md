# Decisions: boss-stormplate (Stage 2b)

Defaults picked by the `boss-stormplate` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Code: `bosses/src/*/java/.../bosses/stormplate/`, numbers in `config/blocksouls/boss-stormplate_knight.json`.

## The boss
- **Size:** 1.6 × 4.0 hitbox, model drawn at 1.15× (56 px tall → 4 blocks). Base 500 HP, armor 12, speed 0.27. Not
  fire-immune (it's an overworld boss); immune to lightning (`thunderHit` is a no-op and lightning damage is ignored),
  knockback-immune through the framework attributes. No weak point: the brief has none, and the helm is high up on a
  4-block knight.
- **Arena radius 16** (fog wall) around a **ruin of radius 14**, so the pillars stand inside the fight and the wall
  goes up just outside the rubble.
- **Spear thrust windup 15 ticks, not 0.7 s (14):** its 12 damage × the phase-2 multiplier is a big hit, and the
  framework rule (tested) needs ≥ 15 ticks for those. Reach 7 blocks, 0.8 half-width.
- **Spear sweep:** 220° arc, radius 5.5, a 1-block-high band (jumpable), 18-tick windup.
- **Thunder call:** 1.5 s circles (radius 2) under every player in the fight, tracking only for the first 30% of the
  windup. The strike is a *visual* lightning bolt (no fire, no block damage, no stray damage) plus 10 lightning damage
  (`damageSources().lightningBolt()`) to whoever's in a circle, once per call even where circles overlap. Using the
  lightning damage type means the dragonscale set really is proof against it. Phase 2 adds one circle per player
  where they're heading (velocity × 0.6 of the windup, ±2.5–4 blocks).
- **Spear throw:** 20-tick windup with a line telegraph that stops at the first wall; 9 ticks out, a short crackling
  hang, 9 ticks back. Hits once going out and once coming back (a separate hit). The thrown spear is drawn with
  particles and the model's spear is hidden while it flies (no projectile entity: nothing to desync or get stuck).
  Min range 5 so it isn't wasted point blank.
- **Leap slam (phase 2):** 22-tick crouch with a landing ring that tracks the target for 70% of the windup, landing
  clamped 2.5 blocks inside the fog wall. A scripted 14-tick arc (no gravity, position set each tick), 14 damage in
  radius 4 with knockback on landing. Only from the ground; min range 4.
- **Charged puddles (phase 2):** each thunder strike in phase 2 leaves a puddle (radius 2), plus 4 scattered when
  phase 2 starts. A puddle lives 14 s and pulses every 2.5 s for 4 damage; a pale-gold ring fills in during the
  0.75 s before each pulse. At most 8 at once (oldest goes). They are boss-side state (not blocks or entities), so a
  reset or death clears them instantly.
- **Phase 2 (< 50%):** damage ×1.2, recovery ×0.7, cooldowns ×0.7, chase speed ×1.2, a bolt into the knight on the
  stagger, sparks and cloud get denser, ambient flickers twice as often.
- **"Real-looking lightning" never uses vanilla's world-wide thunder:** every bolt is visual-only *and silent*, with
  our own thunder sound at the strike (range ~48 blocks), so a fight doesn't rattle players across the map.
- **Music:** `music_disc.precipice` at 0.8 pitch as a placeholder (sounds are re-pitched vanilla events, like the
  Furnace Knight's).

## Ruin and storm
- **The ruin is built on the knight's first tick after a real spawn** (field spawn, `/bs boss spawn`, spawn egg: any
  `finalizeSpawn`), once; a saved flag stops it from being rebuilt after a reload. Game tests spawn without
  `finalizeSpawn`, so they build the ruin explicitly. The ruin is never removed.
- **What it may touch:** air and loose plants (`#replaceable` without fluids/fire, flowers, snow layers) become pillars
  and rubble; grass/dirt (`#dirt`, grass block) becomes floor. Everything else, including every player-placed block
  that isn't dirt, is left alone. Columns more than 3 blocks above/below the center's floor are skipped, so it
  hugs the terrain instead of building towers on slopes.
- **Layout:** chiseled stone bricks under the knight, a stone-brick dais (radius 2.5) and a ring path at radius 10,
  then a mix of cracked/plain/mossy stone bricks and cobblestone covering ~55% of the grass (denser inward); 9 broken
  2×2 pillars (2–7 high, each column snapped at its own height, some capped with a slab) at radius 11.5 with fallen
  rubble at their feet, and scattered rubble on the floor.
- **Storm cloud:** client-side dark dust particles 16 blocks over the arena center (synced to the client as the
  boss's `home`), forced visible at any distance; sparks drop out of it. **Flickers:** a silent visual bolt somewhere
  on the ruin about every 7 s (3.5 s in phase 2) while a player is within 128 blocks.
- **Kneels until engaged:** the model kneels while the boss isn't engaged (and isn't walking home after a reset).

## Field spawn
- **Daily, 30% chance**, overworld only, any time of day. A spot 36–60 blocks from a player where the center is
  grass/dirt under open sky and 24 samples on two rings (radius 7 and 14) are within 3 blocks of the center's height
  with no water. 10 tries per scan. Cooldown and leave timer 10 minutes each (framework defaults).

## Dragonscale armor
- **Netherite numbers:** durability 37, 3/6/8/3, toughness 3, 0.1 knockback per piece, enchantability 15,
  fire-resistant item, repaired with netherite ingots (`#blocksouls_bosses:repairs_dragonscale_armor`). Not craftable.
- **"+2 knockback resistance" = +0.2** on the attribute, which the tooltip shows as "+2" (vanilla ×10 display),
  applied while all four pieces are worn (same pattern as world's set bonuses).
- **Lightning immunity** cancels any `lightning_bolt` damage and puts out the fire a real bolt starts.
- **Chain lightning:** when a full-set wearer is credited with a kill (melee, bow, gun: anything whose damage source
  names them), the nearest living `Enemy` within 8 blocks of the corpse takes a visual bolt plus 8 damage credited to
  the wearer. Players are never chosen. 5 s cooldown per wearer, which also stops chains from chaining.
- **Names:** Dragonscale Helm / Chestplate / Greaves / Boots. Icons and the worn texture are painted by
  `tools/assets/stormplate.py` (storm-blue scales, gold trim).
- **Creative tab:** after netherite boots in Combat.

## Loot
- Main table: one of each dragonscale piece + 2 Ender Eyes (vanilla + own items only). Extra table: the shared
  `boss_drops/legendary_gun`.

## Art
- **`tools/assets/stormplate.py`** paints the skin, two glow layers (always: visor and spearhead; phase 2: lightning
  forks across the plate), the spawn egg, armor icons and the worn armor. It lives in its own file instead of
  `boss_skins.py` so the parallel boss sessions don't edit the same script.
