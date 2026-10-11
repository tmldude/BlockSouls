# Decisions: boss-pale-rider (Stage 2b)

Defaults picked by the `boss-pale-rider` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Config: `config/blocksouls/boss-pale_rider.json` (every number below is a field there).

## The fight
- **One entity, two silhouettes.** The rider and horse are one `BossEntity` with one model (`mount` → `horse` +
  `rider`). Phase 2 hides the horse half, shows a robe, and shrinks the hitbox from 1.6×3.5 to 0.9×2.4
  (`getDefaultDimensions` keyed on the synced phase, refreshed on both sides). Drawn at 1.25× scale, translucent
  (`entityTranslucent`, ~80% alpha) with a full-bright eye/lance-tip layer. No weak-point part: its position would
  differ between phases and the brief doesn't ask for one.
- **Arena radius 26** (brief: none given). A 20-block lance run needs room on both sides of the target; the fog wall
  stops a pass early if it gets there (the boss just runs into it).
- **Lance charge** is one attack with a long active stage: pass (16 ticks, 20 blocks) → turn (24 ticks: re-aims at
  its target for the first 60%, then the return lane is drawn locked) → second pass (16 ticks). One hit per pass
  (`hurtEvery` with an interval longer than a pass). The turn's 1.2 s lane telegraph keeps the second pass fair even
  though it isn't the attack's windup.
- **Rearing stomp** is an expanding ring, not a filled circle: only the ring's front edge (≈1 block band) hurts and
  it's 0.8 high, so jumping it works and landing inside it is safe.
- **Wraith call:** 3 vanilla skeletons on tamed skeleton horses (the vanilla skeleton-trap recipe, so their riding AI
  works), Speed II on the horses, no equipment drops. Once per phase (it's in both phases' lists, a bitmask stops
  repeats). They're tagged and tracked; a fight reset, the boss's death or removal dismisses them.
- **Phase 2 attack list:** spear fan (3 lines, 20° apart, 18 long), blink (0.8 s shimmer at the boss *and* at the
  destination 2.2 blocks behind the target, which follows the target for 70% of the shimmer then locks; teleport only
  if the spot is free), a quick slash as close-range filler (brief lists none, but without it the on-foot rider had
  nothing to do at melee range), and the second wraith call.
- **Phantom charge** (phase 2, every 20 s) isn't an `Attack`: it runs beside the attack loop so it overlaps the
  rider's own moves, as the brief describes. A lane through the target's position at a random angle, spanning the
  arena, telegraphed 1.5 s, then a 0.7 s run of soul-fire particles that hits once (10 dmg, scaled like attacks).
  Timer counts only outside the phase-change stagger and restarts on reset/phase change.
- **Floating:** on foot it hops-glides toward targets more than 7 blocks away every 2.5 s (a push with a little
  lift), instead of flying. Keeps pathing vanilla and readable.
- **Numbers:** 360 HP, armor 6, speed 0.34 mounted (×0.8 on foot). Phase 2 below 55%: ×1.15 damage, ×0.85
  recovery, ×0.8 cooldowns. Every hit over 6 has ≥ 15 ticks of windup (tested). XP 120.

## Field spawn
- **DAILY** rule, 30% per day, at night only (`isDarkOutside`), overworld, 40–60 blocks from a player on dry,
  sturdy ground in plains / sunflower plains / meadow / savanna / savanna plateau / windswept savanna (snowy plains
  left out: no "pale fog over snow" read).
- **Arrival:** thick low cloud particles around it while unengaged, plus hoofbeats every 3 s at volume 4 (≈64 blocks)
  on top of the framework's horn/flash/smoke cue.
- **Leaves at dawn** if unengaged (`isBrightOutside`), via the framework's `leave()` so cooldowns apply. Engaged fights
  continue past dawn.

## Wraith Steed
- **Entity:** extends vanilla `AbstractHorse` (`blocksouls_bosses:wraith_steed`) for the ridden-movement code and
  jump-charge HUD. Always "saddled" (`isSaddled` → true) with no saddle item; its type isn't in the saddle/armor
  tags, so the mount screen shows only the player's inventory. No random stats: speed 0.42 (best vanilla 0.3375),
  jump strength 0.9, 30 HP.
- **Double jump:** a jump charge released in mid-air fires one air jump (≥ 75% of a full charge, so a quick tap still
  lifts) in `tickRidden`, which runs on the rider's client (vanilla ridden movement is client-authoritative). A charge
  released mid-air with no air jump left is dropped rather than firing on landing.
- **Fall damage:** none for the steed, and so none for its rider (vanilla only passes fall damage to passengers when
  the horse takes it).
- **Bound:** owner = whoever blew the whistle (vanilla horse owner reference). Only the owner can mount
  (`canAddPassenger`, server-side); no leash, food, breeding, portals; never despawns on distance; drops nothing.
- **Whistle:** summons the steed at your feet **and puts you in the saddle**; used again while it's out (you're on
  foot) it teleports to you and you mount again. One steed per player (newest wins; a stale one fades). In another
  dimension it's replaced by a fresh one. 1 s use cooldown against spam. Killed → 60 s cooldown on the owner's
  whistle (if online). Unridden 5 s → fades (no death, no cooldown).
- **Whistle item:** stack size 1, epic rarity, in Tools & Utilities after the saddle. "Keeps on going down": nothing
  to do, going down keeps gear (`DECISIONS.md`: downed costs boons, not items). No item-level soulbound.
- **Texture:** `wraith_steed.png` is the horse half of `pale_rider.png` (same UV layout, other regions cleared), so
  art for both can be painted once and copied.

## Loot
- `entities/pale_rider.json`: the whistle (always) + 2 Ender Eyes. The legendary gun comes from the shared
  `boss_drops/legendary_gun` table via `extraLoot` (only loads with the guns module).

## Debug
- `/bs boss spawn pale_rider` (framework), `/bs steed` (blow the whistle without the item), `/bs steed whistle`
  (give a whistle and clear its cooldown).

## Placeholders
- Sounds are re-pitched vanilla events (vex, skeleton horse, horse gallop, evoker, trident); music is disc 11 at 0.8
  pitch. Textures from `tools/assets/pale_rider_skins.py` (called from `boss_skins.write_all`), palette `pale` with a
  darker cloak ramp.
