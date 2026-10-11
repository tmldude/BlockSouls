# Session: kart-fuel-horn (Stage 4)

**Module:** `vehicles`. **Branch:** `claude/kart-fuel-horn`. **PR title:** `vehicles: kart fuel and horn`.

Read `docs/decisions/vehicles.md` first (it lists "No fuel" and "No horn" as left out of the MVP).

## You own
`vehicles/**` (Java, assets, data, tests). You may add one keybind through `common`'s client `Keybinds` helper and
a payload through `Networking` (adding only; list it under "Contract additions").

## Fuel
- The kart has a fuel tank (config `fuelCapacity`, default 6000 ticks of full throttle). Throttle burns fuel
  (idle burns nothing); at 0 the kart coasts and won't accelerate. Fuel is saved with the kart and survives
  breaking down into a wreck and being repaired.
- **Refuel** by right-clicking the kart with fuel: coal/charcoal (+600), block of coal (+5400), blaze rod (+1200),
  and a new **Fuel Can** (+3000) crafted from scrap + coal (recipe with `fabric:load_conditions` for
  `blocksouls_world` since scrap is theirs). Fuel values are config. A repaired kart starts with 25%.
- **Gauge:** while driving, a small fuel gauge next to the hotbar (client HUD, config toggle), flashing under 15%;
  low-fuel sputter (use a placeholder until the `audio` session's engine sounds land; don't edit their files).
- Debug: `/bs kart fuel <amount>`.

## Horn
- Keybind (default **H**) while driving: horn sound heard ~48 blocks, 1 s cooldown (server-checked). Original
  synthesized sound (reuse `tools/assets/mob_sounds.py` helpers); you own the `kart.horn` event and its file.
- Wave mobs within 6 blocks of the kart flinch (a short knockback away from the kart) on a 10 s cooldown: a panic
  button, not a weapon. Config both.

## Tests
Unit: fuel burn and refuel math, cooldown logic. Gametests: throttle drains fuel and an empty kart doesn't
accelerate; refuel with coal adds the configured amount; fuel survives wreck → repair; the horn is rate-limited
and pushes a nearby wave mob.

## Done when
CI green; `docs/decisions/kart-fuel-horn.md`.
