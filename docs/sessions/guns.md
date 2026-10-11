# Session: guns (Stage 1)

**Module:** `guns`. **Branch:** `claude/guns`. **PR title:** `guns: framework, 5 guns, ammo, perks`.

You build the shooting: our own server-authoritative hitscan framework (no third-party gun mod), the Starter Pistol
plus four guns, ammo, rarity and perks, precision hits, crafting and loot. Read `docs/sessions/README.md` and PLAN.md
§0b and §4 (B1–B5; this brief overrides it where they differ) first.

## You own
Everything under `guns/`.

## Contracts
- `CommonIds.STARTER_PISTOL` is your item id. The game module hands it out and removes it after wave 3.
- Fill item tags `blocksouls:guns` and `blocksouls:ammo` (`CommonTags`).
- **Loot function** `CommonIds.ROLL_PERKS` (`blocksouls_guns:roll_perks`, field `tier`: `common|rare|legendary`,
  optional): register it; other modules' loot tables use it on gun entries.
- Add gun/ammo pools to the game's wave tables (`CommonIds.WAVE_DROPS_*`, `WAVE_CLEAR_REWARD`) and to vanilla chest
  tables with `LootTableEvents.MODIFY` (weights in config).
- Recipes use world's materials by id: `blocksouls_world:scrap`, `blocksouls_world:gunmetal_ingot` (`CommonIds`).
  They resolve once the whole pack is installed; your isolated tests may log missing ids.
- **Damage source:** bullets deal damage with the shooter as the attacker and the gun as the weapon item (a custom
  damage type `blocksouls_guns:bullet` in data, created with the player as causing entity), so on-kill effects in
  other modules (world's Combustion/Vampiric enchantments, boss kill credit) work on guns.
- **Enchanting:** guns are enchantable (`enchantable` component, value 10). World's wacky enchantments list
  `#blocksouls:guns` as supported items. You implement **Bouncy** for bullets (read the level of the enchantment
  with id `CommonIds.BOUNCY` from the registry): each level = one ricochet off a block surface (reflect, 70% damage).
- `PlayerStatus.isDowned(player)` → can't fire or reload. `PlayerTraits.RELOAD_SPEED` → +25% reload speed per level.
- Vorpal targets: `CommonTags.BOSSES` and `CommonTags.ELITES`.

## Framework
- **Server-authoritative hitscan.** Client sends a `fire` payload (no direction: the server uses the player's own
  rotation); server validates fire rate, ammo and state, raycasts from the eye along the look vector with spread,
  stops at the first block, picks the first entity hit (`ProjectileUtil`-style AABB sweep inflated by 0.3).
  Shotgun = N pellet rays. Range per weapon.
- **Precision hits:** hit point y ≥ target `getEyeY() − 0.3·(height/1.8)` → precision multiplier, yellow hit marker
  and a crit particle. Bosses may later supply weak points; not now.
- **Feel (client):** muzzle flash particles, a fast tracer line, recoil camera kick (pitch up, recover over ~6 ticks),
  hit markers (white; yellow precision; red X on kill) sent from the server, crosshair widening with spread,
  ammo counter HUD bottom-right (`mag / reserve`), reload progress.
- **Controls:** with a gun in the main hand, **attack (left mouse) fires** (hold for automatic) instead of
  attacking/mining, **use (right mouse) aims down sights** (zoom FOV per weapon, slower walk, tighter spread; sniper
  gets a scope overlay), **R reloads** (keybind via `Keybinds`). Find the 26.3 client hooks for cancelling the
  attack and for FOV (mixin into `Minecraft`'s attack handling / `GameRenderer` FOV — grep the vanilla source).
- **Magazine and reload:** current mag stored in an item data component; reload takes ammo items of the gun's type
  from the inventory; auto-reload on an empty trigger pull. Swapping items cancels a reload.
- Weapon stats live in code as records, overridable per weapon in `config/blocksouls/guns.json`.

## Weapons (MVP: Starter Pistol + 4)
| Gun | Ammo | Mode | Damage | RPM | Mag | Reload | Precision | Notes |
|---|---|---|---|---|---|---|---|---|
| **Starter Pistol** | none (infinite) | semi | 4 | 300 | 8 | 1.2 s | ×1.5 | No perks, never drops, not craftable; tooltip "Infinite ammo". |
| **Hand Cannon** | Primary | semi | 9 | 140 | 6 | 1.6 s | ×2.0 | Long range, heavy kick. |
| **Auto Rifle** | Primary | auto | 3.5 | 600 | 30 | 2.0 s | ×1.6 | Bread and butter. |
| **Shotgun** | Special | semi | 3 × 8 pellets | 70 | 5 | 2.4 s (shell by shell ok) | ×1.25 | 20-block range, wide spread. |
| **Sniper** | Special | semi | 22 | 45 | 4 | 2.6 s | ×3.0 | 4× scope, 120-block range, big spread unless aimed. |

Rarity scales damage: common ×1.0, rare ×1.1, legendary ×1.2 (config).

## Ammo
- **Primary** (Hand Cannon, Auto Rifle) and **Special** (Shotgun, Sniper) items, stack to 64 / 32. Recipes:
  Primary ×16 = gunpowder + 2 copper ingots (shaped); Special ×6 = gunpowder ×2 + gunmetal ingot + iron ingot.
- Any wave mob kill also has a small chance to drop Primary (through your wave-table pools).

## Rarity and perks
- Data components: `rarity` (common / rare / legendary) and `perks` (list of perk ids). Tooltip: rarity-colored name,
  perk names + one-line mechanics.
- Guns without a rolled rarity (crafted, `/give`) roll on first inventory tick server-side; crafted guns roll common
  70% / rare 25% / legendary 5% (config). Loot rolls via `roll_perks` with the table's tier.
- Perks (MVP 4): rare guns get 1, legendary 2.
  - **Outlaw:** precision kill → reload 50% faster for 5 s.
  - **Rampage:** kill → +10% damage, stacks ×3, 4 s, refreshed on kill.
  - **Explosive Rounds:** precision hits deal +30% as a small explosion (radius 1.5) that never breaks blocks.
  - **Vorpal:** +20% damage vs bosses and elites.

## Crafting (survival-obtainable)
Gun parts from world's materials: **barrel** (2 gunmetal ingots + iron), **receiver** (gunmetal + scrap + redstone),
**stock** (planks + scrap). Guns: Hand Cannon = barrel + receiver + iron; Auto Rifle = barrel + receiver + stock +
gunmetal; Shotgun = 2 barrels + receiver + stock; Sniper = 2 barrels + receiver + stock + spyglass. Use a shaped
crafting table (no custom workbench in MVP).

## World loot (config weights; rarer structures roll higher tier)
Inject guns (with `roll_perks`) and ammo into vanilla chest loot tables: village/weaponsmith, simple dungeon,
abandoned mineshaft, desert/jungle temples, pillager outpost (common/rare); stronghold, bastion, ancient city, end
city (rare/legendary). Typical: 8–15% chance of a gun per chest, ammo more common.

## Assets
Item textures: `gen.py gun steel|scrap|rust` placeholders per gun, `item` for ammo/parts. Give guns a 3D-ish JSON
item model (a few cuboids) if quick; `art-items` will polish. Sounds: vanilla sound events re-pitched (register our
own ids in `sounds.json`).

## Debug commands
`gun give <gun> [rarity] [perk...]`, `gun ammo <player> [amount]`, `gun stats <gun>`.

## Tests
- Unit: damage calc (rarity, precision, perks, falloff if any), fire-rate gating, spread math, perk stacking timers,
  reload time with `RELOAD_SPEED`.
- Gametests: a mock player firing at a zombie in front of it damages it; precision vs body damage; can't fire while
  downed; Explosive Rounds don't break blocks; reload consumes ammo items; Bouncy ricochets; `roll_perks` produces
  rarity + perk count per tier.

## Done when
CI green; in `runClient`: every gun craftable (with world merged) or via `/bs gun give`, fires, reloads, ADS works,
headshots feel distinct, perks work, ammo HUD shows; guns appear in vanilla chests; `docs/decisions/guns.md`.

## Out of scope
Heavy ammo, rocket launcher, pulse rifle, exotics (growth). Wacky enchantments other than Bouncy-on-bullets (world).
