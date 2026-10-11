# Decisions: guns (Stage 1)

Defaults picked while building `guns/` where the brief or `PLAN.md` was silent. Same rules as `DECISIONS.md`.

## Framework
- **Own hitscan, no gun mod.** Client sends an empty `fire` packet per trigger pull (auto weapons: one per tick while
  held); the server checks state, ammo and fire rate, then traces from the eye along the player's own look vector.
  Blocks are clipped with `COLLIDER` shapes (bullets pass through grass/flowers, stop at glass), fluids are ignored.
- **Fire-rate gate in fractional ticks** (`FireGate`): 140 RPM is every 8.57 ticks, and the remainder carries over so the
  average rate is exact. The server accepts a shot up to **1 tick early** (packets bunched into one tick) without
  moving the schedule earlier, so the average can't exceed the gun's RPM. The client runs the same gate to predict
  recoil and muzzle flash; the server stays authoritative.
- **Bullet damage type** `blocksouls_guns:bullet` (scaling `never`), in `#minecraft:bypasses_cooldown` (an auto rifle
  at 600 RPM would otherwise lose most hits to the 10-tick hurt cooldown, and shotgun pellets would collapse into one)
  and `#minecraft:no_knockback` (vanilla knockback per pellet launches mobs). Each weapon applies its own small
  `knockback` instead. `DamageSource(bullet, player, player)`: the player is both direct and causing entity, so
  `getWeaponItem()` is the gun in hand and kill credit / on-kill enchantments work.
- **Not** in `#minecraft:is_projectile`: endermen would dodge every bullet.
- **Precision zone** from the brief: hit y ≥ `eyeY − 0.3·(height/1.8)`, on the 0.3-inflated hitbox. Only living
  entities have one (ender dragon parts don't).
- **Damage falloff** per weapon: full damage to `falloffStart`, linear down to `falloffMinMult` at max range.
  Shotgun 8 → 20 blocks at ×0.3; Hand Cannon 48 → 80 at ×0.6; Sniper none.
- **Spread** is uniform over the cone's disk (`hipSpread` / `adsSpread` degrees). Recoil adds crosshair bloom on the
  client only (the server spread doesn't bloom); kept simple for MVP.
- **Bouncy:** reads world's `blocksouls_world:bouncy` level from the registry; each level = one reflection off a block
  face, ×0.7 damage per bounce (`bounceDamageMult`). 0 when world isn't installed. The ricochet never hits the shooter.
- **Stats sync:** the server sends its weapon stats (JSON) on join, so the client's HUD, crosshair and fire-rate
  prediction match a server with edited `guns.json`. An integrated server re-applies its own config on start.

## Controls and feel
- **ADS is vanilla item use** (`startUsingItem`, use animation `none`), so the server knows who's aiming without
  another packet. Walk speed while aiming comes from the `use_effects` component: **×0.6** (`adsMoveSpeed`) instead of
  vanilla's ×0.2. Zoom: `adsZoom` divides the FOV modifier (Sniper ×4, others 1.15–1.35). Sniper shows the vanilla
  spyglass scope texture plus a fine crosshair (referenced, not copied).
- **Attack clicks are taken at the start of `Minecraft.handleKeybinds`** (mixin) before vanilla turns them into
  hits; `startAttack` / `continueAttack` are cancelled with a gun in hand, so guns never melee or mine. A too-early
  click stays buffered (one) until the gate opens.
- **Recoil:** instant pitch kick (`recoil` degrees, ×0.6 while aiming), 80% recovered over 6 ticks.
- **Tracers** are dust particles along the server's real path (incl. ricochets), skipped for the first 1.5 blocks of
  your own shots. Muzzle flash = small flame + smoke particles.
- **Hit markers:** white body, yellow precision, larger red on kill. One marker per shot (best of its pellets).
- **HUD:** ammo bottom-right (`mag` at 2× scale, then `/ reserve`; `∞` for the Starter Pistol or creative), yellow
  when ≤ 25%, red at 0; reload bar above it. The gun crosshair replaces vanilla's while a gun is held.

## Magazine and reload
- `mag` component absent = full (fresh guns come loaded). Reload is one timed action for every gun (no shell-by-shell
  yet). Creative players and the Starter Pistol don't consume ammo items.
- Reload cancels on slot change, the stack leaving the hand, or going down. Firing is refused mid-reload.
- An empty trigger pull plays a click and auto-reloads; held auto fire on an empty mag does the same.
- `RELOAD_SPEED` +25% speed per level (`reloadSpeedPerLevel`); Outlaw ×1.5 speed; both multiply.

## Rarity and perks
- Our `blocksouls_guns:rarity` component is the source of truth; we also set vanilla's `rarity` (rare → aqua,
  legendary → `epic` light purple) so the name is colored everywhere for free.
- Unrolled guns roll on their first inventory tick (crafted odds 70/25/5). `roll_perks` tiers:
  common 75/22/3, rare 30/55/15, legendary 0/40/60; no tier = crafted odds. Perks are distinct, 0/1/2 by rarity.
- **Explosive Rounds** is not a vanilla explosion: it damages living things within 1.5 blocks of the hit (bullet
  damage type, so it lands right after the hit) and plays particles + sound. It cannot break blocks.
- Perk timers are per player (not per gun) and reset on relog.

## Loot
- Wave tables (`wave_drops/common|rare|legendary`): independent pools for Primary (12/15/18%), Special (3/6/10%) and
  a gun at that tier (1/2/4%). Wave-clear reward: a rare-tier gun at 50% plus 16–32 Primary and 4–10 Special.
- Chests: one pool with a weighted gun (Hand Cannon 30, Auto Rifle 30, Shotgun 20, Sniper 20) at the chest's chance,
  one ammo pool (Primary 8–24 ×3 weight, Special 2–8). Tiers: village weaponsmith, dungeon, mineshaft = common;
  desert/jungle temple, outpost, stronghold corridor/crossing, bastion other/bridge = rare; bastion treasure, ancient
  city, end city = legendary. All in `guns.json` → `loot`.
- Tables replaced by a data pack (`source` not built-in) are left alone.

## Crafting
- Shapes: barrel `GIG` (gunmetal, iron, gunmetal), receiver `SGR` (scrap, gunmetal, redstone), stock ` S / PP`
  (any planks). Hand Cannon `BR / ·I`, Auto Rifle `BRS / ·G·`, Shotgun `BRS / B··`, Sniper `·Y· / BRS / B··`
  (spyglass on top). Primary ×16 `CPC` (copper, gunpowder, copper); Special ×6 `PMP / ·I·`.
- **Recipes that use world's items carry `fabric:load_conditions` → `fabric:all_mods_loaded: [blocksouls_world]`.**
  In 26.3 recipes are a registry: one unknown item id fails the whole registry load (the server won't start), not
  just a log line as the brief assumed. Without `world` those recipes are skipped; guns still come from loot and
  `/bs gun give`. Other modules referencing each other's items in data need the same guard.

## Art
- Item textures: per-gun silhouettes drawn with `gen.py`'s palettes (pistol scrap, rifles steel, shotgun rust), ammo
  bone/ember, parts steel/scrap/wood. Flat `item/generated` models with display transforms that hold the gun level and
  pointing forward; a 3D model is left to `art-items`.
- Sounds: our own sound events, each an `"type": "event"` reference to a re-pitched vanilla event in `sounds.json`.

## Commands
`/bs gun give <gun> [rarity] [perk...]` (rarity without perks rolls that rarity's perk count), `/bs gun ammo
<player> [amount]` (that many of each ammo type, default 64), `/bs gun stats <gun>`.
