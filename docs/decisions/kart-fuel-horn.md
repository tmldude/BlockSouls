# Kart fuel and horn (Stage 4, 2026-10-11)

Adds what `vehicles.md` left out ("No fuel", "No horn"). Numbers live in `config/blocksouls/vehicles.json` under
`fuel`, `horn` and `hud`.

## Fuel
- **Measured in ticks of throttle.** A full tank (`fuelCapacity`) is 6000 ticks = 5 minutes of holding W or S.
  Idling, coasting and steering burn nothing; `burnPerTick` = 1.
- **The server owns fuel; the driver's client only reads it.** Driving is client-authoritative (like boats), so the
  server can't see the throttle in the physics. It reads the driver's keys from vanilla's input packet
  (`ServerPlayer.getLastClientInput()`) and burns from that, and fuel is synced entity data the driver's client
  checks before accelerating. A hacked client could ignore its empty tank, but it could already ignore every
  other driving rule; this is co-op.
- **Empty tank:** W does nothing (the kart coasts to a stop with normal coast friction) and reverse does nothing,
  but **braking** (S while still rolling forward) still works, since brakes need no engine.
- **Refuel** by right-clicking with a fuel item (one item per click, never past full): coal / charcoal 600, block of
  coal 5400, blaze rod 1200, Fuel Can 3000. The table is a config map of item id → ticks, so packs can add fuels.
  At a full tank the click falls through to boarding (same as scrap at full health).
- **Fuel Can recipe:** `SCS / CSC` (3 scrap + 3 coal or charcoal via `#minecraft:coals`), gated on `blocksouls_world`.
  It's 3000 for 3 coal (1800 loose), so the scrap buys a ~1.7× bonus and a compact stack of 16 to carry.
- **Wreck → repair:** a kart that breaks down stores its fuel in the wreck (saved as `fuel`), and the repaired
  kart gets exactly that back, even 0. A wreck that never ran (world structures, `/bs vehicle spawn wreck`)
  repairs into a kart at 25% (`repairedShare`). We read "a repaired kart starts with 25%" as being about those
  wrecks; otherwise breaking down would refill a tank for free.
- **New / old karts:** a kart spawned directly (command) or loaded from a save made before fuel existed starts full.
- **Gauge:** driver only, a fuel-can icon plus an 18 px vertical bar right of the hotbar (past where the
  attack indicator sits). Amber; under 15% (`lowShare`) it turns red and flashes about twice a second; empty, it
  blinks its outline. `hud.showFuelGauge` turns it off (read client-side from the same config file).
- **Sputter:** below 15% (and on an empty tank) the throttle coughs every 30 ticks: a server-played
  `kart.sputter` sound plus a puff of black exhaust, so the whole party hears it. `kart.sputter` points at vanilla's
  fire extinguish sound as a placeholder for the `audio` session to replace; `kart.refuel` (bucket fill) likewise.
- Debug: `/bs kart fuel <amount>` sets the fuel of the kart you ride or look at; `/bs vehicle stats` shows it.

## Horn
- **Key:** H by default (`key.blocksouls.kart_horn`, via common's `Keybinds`). Only the driver's press is sent
  (`blocksouls_vehicles:kart_horn`, an empty serverbound payload); the server checks they really drive.
- **Rate limit (server):** one honk per 20 ticks (`cooldownTicks`). Presses inside the cooldown are dropped, not
  queued.
- **Range:** played with volume `hearingRange / 16` = 3 on a variable-range event, which vanilla turns into
  48 blocks of reach (the server sends it that far and the client attenuates over that distance).
- **Flinch:** mobs in `#blocksouls:wave_mobs` within 6 blocks (`flinchRadius`) of the kart are knocked back
  0.8 (`flinchStrength`, twice a bare-hand hit) away from the kart and lose their current path. No damage. The
  10 s flinch cooldown (`flinchCooldownTicks`) is only spent when a honk actually pushes something, so honking at
  nothing doesn't waste it. Knockback resistance applies (brutes barely budge), which fits "panic button".
- **Sound:** `kart.horn` is ours: `vehicles/tools/gen_fuel_horn.py` synthesizes it with `tools/assets/mob_sounds.py`'s
  helpers (two detuned pulse tones a major third apart, G4 + B4, through a tinny resonant band, light overdrive).
  The same script paints the placeholder Fuel Can texture from `gen.py`'s rust / ember / scrap palettes.
  The script lives in our module (like `vehicles.md`'s texture script) to stay out of `tools/assets/`.

## Tests
- Unit: burn / effective throttle / refuel / repair-start / low-fuel math (`KartFuelTest`), the cooldown (`CooldownTest`).
- Game tests: throttle drains fuel and idling doesn't (a mock driver with `setLastClientInput`); an empty kart
  doesn't accelerate (calls the kart's `simulate(Input)` directly, since a mock player has no client to run the
  physics); coal refuels by the config amount and a full tank refuses it; fuel survives wreck → repair and a
  never-run wreck repairs to 25%; the horn is driver-only, rate-limited, pushes a nearby wave mob but not a far
  one, and the flinch has its own longer cooldown. The test mod tags vanilla zombies as wave mobs.

## Contract additions
- None in `common`. One new payload (`blocksouls_vehicles:kart_horn`, registered through `Networking`) and one new
  keybind (through `Keybinds`), both in the vehicles module.
