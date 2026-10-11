# Vehicles (Stage 1, 2026-10-11)

Kart wreck + repaired 4-seat kart. Numbers live in `config/blocksouls/vehicles.json`.

## Driving
- **Client-authoritative like boats.** The driver's client runs the physics and vanilla's vehicle-move packets
  carry the result; the server simulates only when nobody drives (coasting after a hop-out, falling). The client
  feeds the driver's `Input` (W/A/S/D, jump, sprint) to the kart every tick.
- **Physics:** velocity is split along the heading each tick; grip removes 60% of the sideways part (12% while
  drifting, 4% on ice: block friction > 0.8). Accel 0.04, top speed 0.75, braking 0.08, reverse 0.02 up to 0.25,
  coasting ×0.96/tick. Steering 5.5°/tick at full, faded below 0.2 b/t so a parked kart can't spin, flipped in reverse,
  halved in the air. Gravity 0.08, steps up 1 block (`maxUpStep`).
- **Drift = hold sprint while steering** above 0.3 b/t (brief offered sprint or jump; jump is the hop). It tolerates
  a little slowdown and continues through hops. Releasing after ≥ 20 ticks (1 s) adds +0.2 speed, after ≥ 50 ticks
  +0.35; boosted speed decays back to top speed (×0.97/tick). Flame sparks show the tier (driver's view only;
  everyone sees tyre smoke, derived from sideways movement).
- **Hop:** jump = 0.32 up, 12-tick cooldown. **Landing bounce:** landing faster than 0.3 down bounces at 40%.
- **Water:** floats (upward 0.05 when sunk deeper than 0.3) and top speed drops to 0.08; no drifting or hopping.
- **Seats:** first passenger drives (front-left); if the driver leaves, the next rider becomes the driver (vanilla
  passenger order). Riders face forward and can look ±105° (boat rule). Sneak leaves (vanilla).
- **Downed players** can't board (`canAddPassenger`) and a downed first rider doesn't control the kart.

## Health and ramming
- **Ramming is server-side**, measured from the kart's position change between server ticks (the server doesn't
  know the client's velocity). Any non-player living entity touching the kart above 0.35 b/t takes `speed × 20`
  damage plus knockback `speed × 2` and a 0.3 lift; each mob at most once per 10 ticks. Damage is attributed to the
  driver (`playerAttack`), so kills count for them; with no driver it's "fly into wall".
- **Players never take kart damage and can't damage karts** (`playersCanDamage = false`), including their bullets:
  in a co-op game, stray shots into your own ride would just be annoying. Mobs, explosions and other sources do.
  Creative players punching a kart or wreck remove it (like boats).
- **Breaking down:** at 0 HP riders are ejected and the kart becomes a wreck that **keeps its engine and 2 of its
  wheels** (config), so getting it running again costs 2 wheels + 8 scrap, not a full rebuild.
- **Scrap patches a damaged kart** (+10 HP each, right-click). Right-clicking with scrap at full health boards.

## Wreck
- Static: no AI, only falls straight down if placed in the air, never despawns, ignores all damage and explosions.
- **Repair parts** come from the item tag `blocksouls_vehicles:repair_scrap` (contains `blocksouls_world:scrap`,
  optional) so the gametest mod can add a stand-in item; wheels and engines are our items. Right-click with a part
  fits one; with anything else (or a part it already has) it shows the action-bar progress line.
- The model gains parts as you fit them: wheels appear, the engine appears, scrap brings back the bumper and rear
  seats and straightens the bent roll bar. A wreck missing wheels slumps.

## Not in this PR
- **No fuel** (brief: MVP). Gun truck, hover speeder: growth.
- No horn (optional in the brief). Camera stays vanilla.

## Art and sound
- Model is a Java `LayerDefinition` (128×64 box-UV). Textures come from `vehicles/tools/gen_textures.py`, which
  imports `tools/assets/gen.py`'s palettes and paints each UV region by part (kart: scrap body, ember trim, wood
  seats, ash tyres; wreck: rust). Kept in our module instead of extending `gen.py`, to avoid cross-session conflicts.
- Sounds are our own ids in `sounds.json` that point at vanilla sound events (`"type": "event"`): engine = bee loop
  re-pitched by speed (0.5–1.6), hop = slime jump, boost = firework launch, ram = zombie hitting an iron door,
  break = explosion, part fitted = smithing table, repaired = anvil.
