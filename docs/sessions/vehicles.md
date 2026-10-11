# Session: vehicles (Stage 1)

**Module:** `vehicles`. **Branch:** `claude/vehicles`. **PR title:** `vehicles: kart wreck and 4-seat kart`.

You build the first vehicle: a broken-down kart wreck that players repair into a bouncy, drifty 4-seat kart. Read
`docs/sessions/README.md` and PLAN.md §0b first.

## You own
Everything under `vehicles/`.

## Contracts
- Entity id `CommonIds.KART_WRECK` (`blocksouls_vehicles:kart_wreck`): world's structures place it next to wrecks.
- Item ids `blocksouls_vehicles:wheel` and `blocksouls_vehicles:engine` are referenced by world's loot tables — use
  exactly these.
- Recipes use world's `blocksouls_world:scrap` / `gunmetal_ingot` (`CommonIds`).
- Downed players (`PlayerStatus.isDowned`) can't drive or board (the game also dismounts them).

## Kart wreck
- A static, non-moving entity (no AI, no gravity issues, never despawns), wrecked-looking (rusty, a missing wheel,
  smoke particles occasionally). Survives explosions (it's a wreck).
- **Repair:** right-click with parts. Needs **4 wheels, 1 engine, 8 scrap** (config); each right-click consumes one
  part, shows progress (particles + a small action-bar line `Wheels 2/4 · Engine 0/1 · Scrap 5/8` — mechanics only),
  and the model gains the parts visually if easy. When complete: sound, smoke puff, it becomes a **Kart**.
- `wheel` = 2 scrap + iron ingot + 2 dried kelp blocks (the "rubber"), shaped; `engine` = gunmetal ingot
  ×2 + piston + redstone + scrap ×2. Both also appear in world's wreck chests.

## Kart
- **4 seats:** driver + 3 passengers (offset positions). Right-click to board; sneak to leave.
- **Driving:** client-authoritative like boats (the controlling passenger drives; server syncs). Throttle W/S, steer
  A/D, max speed 0.75 blocks/tick on ground (config), acceleration ~0.04/tick, braking stronger, reverse slower.
  Steps up 1-block ledges, falls with gravity, slides on ice, slow in water (doesn't sink, but stalls).
- **Fun:** **hop** on jump (small vertical impulse, short cooldown), **drift** by holding sprint (or the jump key
  while turning — pick one) while steering: rear slides out, small speed boost when released after ≥1 s. Boosty
  bounce on landing. Camera can stay vanilla.
- **Ramming:** hitting a mob above 0.35 blocks/tick deals `speed × 20` damage and knockback. Players are never
  hurt by karts (friendly). Wave mobs bouncing off a kart should feel good.
- **Health:** 40 (config). Takes damage from mobs, explosions, guns. At 0 HP it smokes, ejects riders and turns
  back into a kart wreck (not destroyed). Repair a damaged kart with scrap (+10 HP each).
- **No fuel** in MVP (log it).
- Persists in the world; never despawns. Pick-up into an item: no (it's a car).

## Art
- Custom model in Java (`LayerDefinition`/`ModelPart`): low chassis, roll bar, 4 wheels that spin with speed and
  steer, seats; the wreck reuses it with parts missing/tilted. Texture via PIL in `rust`/`scrap` palettes (extend
  `tools/assets/gen.py` with a generic box-UV kind if that helps other sessions too — coordinate via the PR).
- Sounds: engine loop pitch by speed (vanilla sound re-pitched, own `sounds.json` ids), horn on a key? optional.

## Debug commands
`vehicle spawn kart|wreck`, `vehicle repair` (repairs the wreck/kart you look at), `vehicle stats`.

## Tests
- Unit: steering/drift/accel math as pure functions (speed after N ticks, drift boost, ram damage).
- Gametests: repairing a wreck with all parts produces a kart; partial parts don't; kart at 0 HP becomes a wreck and
  ejects passengers; 4 passengers can ride; ramming a zombie at speed damages it.

## Done when
CI green; in `runClient`: find/spawn a wreck, repair it, drive with 3 friends (or test passengers), drift, hop,
ram zombies, break it back into a wreck; `docs/decisions/vehicles.md`.

## Out of scope
Gun truck, hover speeder, fuel (growth).
