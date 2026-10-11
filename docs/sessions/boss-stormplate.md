# Session: boss-stormplate (Stage 2b)

**Module:** `bosses` (package `bosses/stormplate/`). **Branch:** `claude/boss-stormplate`.
**PR title:** `bosses: Stormplate Knight + dragonscale armor`.

Build the **Stormplate Knight** field boss and its dragonscale armor drop on the framework from the `bosses`
session. Read `docs/sessions/README.md`, PLAN.md §0b and §6 D2b, and **`bosses/README.md`** first.

## You own
`bosses/src/*/java/.../bosses/stormplate/**`, its assets/data (`stormplate*`, `dragonscale*` paths), and one
registration line each in the shared bosses init/client init/spawner. Other sessions work in `bosses/` in parallel:
don't touch their packages or the framework; minimal framework additions only, listed under "Framework changes".

## The boss
- **Where/when:** appears in a **ruined arena** it brings with it: when its spawn rule fires (chance per in-game day
  near a player, flat open ground, config), build a small ring of broken stone-brick pillars and cracked floor
  (code-placed, ~radius 14; only replaces air/plants/grass/dirt surface, never player blocks), a storm cloud above,
  lightning flickers. The knight kneels in the center until engaged. The ruin stays after the fight.
- **Look:** a towering 4-block knight in layered dragon-scale plate, a lightning spear, a tattered cape; palette
  `storm` (deep blue + gold); sparks crawl over the armor.
- **Stats:** 500 HP (framework scaling), lightning-immune, knockback-immune.
- **P1 (100–50%):** *Spear thrust* (0.7 s windup, long reach line), *Spear sweep* (wide arc), *Thunder call*
  (1.5 s telegraph circles under each player, then real-looking lightning: 10 dmg, no fire, no block damage),
  *Spear throw* (throws the spear along a telegraphed line, then recalls it — it hits on the way back too).
- **P2 (<50%):** storm intensifies; *Leap slam* (jumps to a player, telegraphed landing ring, shockwave), charged
  ground puddles that pulse damage, Thunder call targets 2 circles per player; faster.
- **Drops:** the **full dragonscale armor set** (field bosses die once per run, so the set is the reward),
  **2 Ender Eyes**, a legendary gun (`roll_perks`).

## Dragonscale armor
Netherite-tier stats, set bonus (4 pieces): immune to lightning, +2 knockback resistance, and your melee/gun kills
call a small harmless-to-you lightning strike on the next enemy within 8 blocks (cooldown 5 s). Equipment assets per
the 26.3 format (see how world's armor sets did it). Not craftable; repaired with netherite ingots.

## Tests
Gametests: arena placement never replaces non-natural blocks; phase machine P1→P2; Thunder call damages but doesn't
ignite or break blocks; set bonus grants lightning immunity; `BossEvents.KILLED` fires with
`CommonIds.STORMPLATE_KNIGHT`.

## Done when
CI green; `/bs boss spawn stormplate_knight` builds its arena and is a dramatic ~5 min fight; the armor looks great
and works; `docs/decisions/boss-stormplate.md`.
