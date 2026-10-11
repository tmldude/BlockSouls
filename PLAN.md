# BLOCKSOULS — Modded Minecraft Build Plan

Minecraft (Java, Fabric) with Dark Souls boss fights / Destiny-style raids, Destiny-style guns, COD Zombies wave mode, Spider-Man web-swinging and wall-climbing, and Mario-style jump feel. Shipped as a Docker server image + a Modrinth modpack for friends.

This file is written for Claude Code. Work top-down, one phase at a time. Each phase ends with a checkable "Done when". Do not start a later phase until the earlier one passes.

> **Note:** some ground rules below (Java 21, Yarn mappings) predate Minecraft 26.x. `DECISIONS.md` records what changed and takes precedence.

---

## 0. Ground rules

- **Loader:** Fabric. **MC version:** pin the latest Fabric-supported release at project start (check https://fabricmc.net/develop and the Fabric API Modrinth page). Record it in `gradle.properties` and never bump it mid-phase.
- **Language:** Java 21. Kotlin is not used.
- **Mappings:** Yarn (default for Fabric Loom).
- **Assets:** All textures, models, sounds, names are **original**. No Bungie / FromSoftware / Activision / Marvel / Nintendo assets or trademarked names. Use inspired-by names (e.g. "Hand Cannon", "Pulse Rifle" are generic; "Gjallarhorn" is not).
- **Repo layout:** one Gradle multi-project, one mod per feature so server-only mods can ship without a client update.
- **Commit style:** conventional commits, small PRs per task. Run `./gradlew build` before every commit.
- **Testing:** Fabric's `fabric-loader-junit` for unit tests of pure logic (wave math, damage calc, physics helpers). Gametest framework (`fabric-gametest-api`) for in-world checks where cheap.
- **Ask the human** only when a decision is irreversible or taste-dependent (names, art direction, balance numbers). Otherwise choose a sensible default and note it in `DECISIONS.md`.

## 0b. The game (design target — every phase serves this)

A friends' server plays like a normal Minecraft run to the Ender Dragon, but with a night-wave clock that
pushes difficulty up, and a Souls-style raid as the finale.

**Run length:** ~2 hours from a fresh world to the final raid. Everything below is tuned for that and lives in config.

**Day/night:** the full cycle is ~17 minutes (15% shorter than vanilla's 20), so a 2-hour run has ~7 nights.

**Waves:**
- One server-wide wave counter that only ever counts up. Waves run only at night, only when someone is online.
- **Several waves per night.** The first wave starts at dusk. When a wave is cleared there's a short break, and
  the next wave starts if enough night is left. Dawn is the "pause": no new waves start, the current one ends,
  and ordinary wave zombies burn in the sun. **Elite / mini-boss wave mobs don't despawn at dawn**; they keep
  hunting you into the day until killed.
- Target: **waves 8–10 reached by the final raid**, scaling for real (more mobs, tougher mobs, new mob types
  unlocked by wave number, mutants late). Wave kills drop guns, ammo and materials, better as waves climb.
- **Vanilla mobs still spawn** normally alongside the waves.

**Progression stays vanilla-shaped:** overworld → Nether → stronghold → The End. New ores, items, mobs and
crafting slot into the existing tiers instead of replacing them. To fit 2 hours, Ender Eyes also come from
bosses, and teleporting mobs drop Ender Pearls more readily.

**Start:** players present when the run starts get a **stone pickaxe** and a **Starter Pistol** with infinite ammo.
The pistol **breaks at the end of wave 3** (with a suitably silly sound and message). **Late joiners get only the
Starter Pistol**; friends can hand them gear.

**Death: downed, not dead.** At 0 health you go **down** instead of dying: you crawl, can't fight, and bleed out
over ~30 s. A teammate holding use on you for a few seconds **revives** you. If you bleed out you die for real:
vanilla rules, you drop everything, boons are lost, and the Starter Pistol isn't re-issued. You respawn at your
bed/spawn after the current wave ends (immediately if no wave is running).
- **Run over = everyone down or dead at the same time.** That ends the run with a run summary (waves survived,
  bosses killed, best boon, time) and the admin starts a new run.
- Solo play: going down with nobody to revive you means bleeding out.
- This rewards sticking together and playing carefully.

**Axes of fun:** boons, **wacky enchantments**, cool armor sets, new ores, new crafting, movement, cars, web
swinging and field bosses. Each new item should land on at least one axis.
- **Wacky enchantments:** found on loot and boss drops and applied at a normal enchanting table. Examples:
  Bouncy (arrows and bullets ricochet), Magnetized (pull nearby drops), Combustion (kills explode),
  Grapple (hits pull you toward the target), Moonwalk (armor: low gravity), Vampiric (heal on kill).
- **Armor sets with set bonuses:** a scrap-plate set (early), a blight-forged set from the Nether, a dragonscale set
  from the Stormplate Knight, and a hazmat set against radiation (growth).

**Finale:** the **Ender Dragon is reworked into the final Souls-style raid boss** in The End (phases, telegraphed
attacks, mechanics for 2–4 players). Killing it switches the world to **endless mode**: waves keep coming
every night and keep scaling, COD-style, with no reset.

**Difficulty:** admins pick a difficulty preset when starting a world (multiplies wave size, mob health and damage).

**Wave pressure rules:**
- **Block breaking:** ~1 in 10 wave zombies can slowly break player-placed blocks (never terrain); the share
  and speed creep up with the wave number. Brutes and Mutants always can.
- **No sleeping during an active wave.** Beds still set spawn, and sleeping works once the night's waves are over.
- **Other dimensions don't get waves, but they're no escape:** the wave counter still advances on nights nobody
  is in the overworld (you just miss that night's loot). The Nether has its own pressure; see "Nether" below.

**Run rewards (roguelike boons):** at dawn after surviving a night, a relic drops at each player's feet; using it
offers **3 random boons**. Occasionally one card is a **Wildcard** that lets you pick any boon of that tier instead.
- **Tiers: Common / Rare / Legendary**, with odds that improve as waves climb.
- **Lost on death**, like zombies perks; the run's gear and boons are what you're protecting.
- **Kinds:**
  - **Effects:** any vanilla potion effect as a permanent run buff (speed, strength, jump boost, night vision,
    fire resistance, ...).
  - **Stat boosts:** +50% jump height, sprint speed, max health, stamina pool/regen, reload speed, loot luck.
  - **Movement upgrades** that build on our movement: longer web reach, double jump, faster climbing,
    i-frames on more roll ticks, a free roll after a kill.
  - **Combos:** owning two related boons unlocks a stronger third, e.g. Spring Legs + Featherfall → Moon Boots.
  - **Wild ones:** rare and legendary picks like short flight, a free Elytra, or "every night starts with a lightning strike on your best enemy".
- All boons are data-driven (JSON), so adding more is content work, not code.

**Run variety:** no two runs play the same.
- **Night modifiers** are rolled per night, shown through the world rather than text: fog nights, sprinter
  nights, a Blood Moon every 4th night, swarm nights with more but weaker mobs.
- The **wave roster's order** shuffles within tiers each run.
- **Field bosses:** 1–3 appear over a run, picked from a pool (MVP ships 1).

**Vehicles: repairable wrecks, made fun.** Broken-down vehicles in the world get fixed with parts:
- a 4-seat kart-style buggy (bouncy, drifty);
- a gun-truck variant with a passenger-manned turret on the back;
- a late-game hover speeder (needs a radioactive core).

**Framing: show, don't tell.** No lore dumps or quest text. The world explains itself: wrecks, ruins, rad
craters, an infected Nether, sky and sound cues before dusk, item names that hint. Tooltips state mechanics,
never story.

## 0c. MVP: the first playable run

The first goal is one complete, fun run end to end, not every idea. Everything else is "growth" after friends have played it.

| Area | MVP | Growth (later) |
|---|---|---|
| Game loop | Downed/revive, team-wipe = run over + run summary, run start/new-run admin command, wave/boon HUD, short day/night, multi-wave nights + dawn pause, dusk cues, sleep + dimension rules, starter kit + breaking pistol, difficulty presets, endless mode | More night modifiers |
| Run variety | Blood Moon + 2 night modifiers, shuffled roster | Modifier pool, run seeds |
| Boons | ~20 boons over 3 tiers incl. Wildcard, lost on death, 2 combos | Big data-driven pool |
| Enchantments & armor | 6 wacky enchantments, scrap-plate + blight-forged armor sets | More enchantments, dragonscale + hazmat sets |
| Movement | Jump, roll, climb (done), basic web swing | Boon-driven upgrades |
| Guns | Own framework, Starter Pistol + 4 guns, ammo, rarity, 4 perks, headshots | Full 6 + exotics |
| Mobs | 5 wave types + Mutant Brute elite + 2 Nether mobs; block breaking | Full roster, Stalker, Spreader |
| World | Scrap, 1 new ore, 2 wreck/ruin structures, loot injection, Ender Eye sources, infected Nether look | Radiation zones, more structures |
| Vehicles | 4-seat kart from a repaired wreck | Gun truck, hover speeder |
| Bosses | Framework, boss bar, lock-on, **all 4 field bosses** (Furnace Knight, Pale Rider, Ashwyrm, Stormplate Knight) with custom models, **final dragon raid** | Side raid, more bosses |
| Art | Procedural, in-theme placeholders for everything | Real models, sounds |

## 1. Repo skeleton

```
blocksouls/
  settings.gradle              # includes all subprojects
  build.gradle                 # shared Loom config
  gradle.properties            # minecraft_version, loader_version, fabric_version, mod_version
  common/                      # shared utils, networking helpers, config
  movement/                    # jump feel, dodge roll, web swing, wall climb  (client+server)
  guns/                        # weapon framework, ammo, perks              (client+server)
  zombies/                     # wave mode, round manager, barricades, points (server-heavy)
  bosses/                      # boss entities, phases, boss bars, raid encounters (client+server)
  docker/
    Dockerfile
    compose.yml
    server.properties
  modpack/
    pack.toml / modrinth.index.json   # built by packwiz
  .github/workflows/
    build.yml                  # gradle build + test on PR
    release.yml                # on tag: build jars, build+push image to GHCR, build .mrpack, attach to release
  CLAUDE.md                    # pointer to this file + commands
  DECISIONS.md                 # running log of defaults chosen
  PLAN.md                      # this file
```

**Tasks**
1. `gradle init` multi-project with Fabric Loom on every subproject. Use the Fabric example mod as the template for `fabric.mod.json`, mixins JSON, and entrypoints.
2. `common` exports: a `Networking` helper (typed packets via Fabric's `PayloadTypeRegistry`), a `Config` loader (JSON in `config/blocksouls/`), and a `Keybinds` registry for the client.
3. `CLAUDE.md` with: build/run commands, the version pin, the asset rule, the "one mod per feature" rule.
4. CI `build.yml`: checkout, JDK 21, `./gradlew build test`, upload jars as artifacts.

**Done when:** `./gradlew build` produces four mod jars; `./gradlew :movement:runClient` opens a client with the mod loaded; CI green on main.

## 2. Docker server + modpack (do this BEFORE features — friends can join an empty server)

### 2a. Docker
- Base image `itzg/minecraft-server:java21`.
- Multi-stage `Dockerfile`: stage 1 `gradle:8-jdk21` runs `./gradlew build`; stage 2 copies `*/build/libs/*.jar` (exclude `-sources`) into `/mods-baked/`. Entrypoint script copies `/mods-baked/*` into `/data/mods/` on start so updates to the image replace baked mods but leave third-party ones alone.
- `compose.yml` env:
  ```
  EULA=TRUE
  TYPE=FABRIC
  VERSION=${MC_VERSION}
  MEMORY=6G
  ONLINE_MODE=TRUE
  MODRINTH_PROJECTS=fabric-api,<third-party mods from 2b>
  MODRINTH_DOWNLOAD_DEPENDENCIES=required
  ENABLE_WHITELIST=TRUE
  WHITELIST=<usernames>
  VIEW_DISTANCE=10
  ```
  Volume `./data:/data`, port `25565:25565`. Add `restart: unless-stopped`.
- Healthcheck via the image's built-in `mc-health`.
- Backups: second service `itzg/mc-backup` with `BACKUP_INTERVAL=2h`, `PRUNE_BACKUPS_DAYS=7`.

### 2b. Modpack
- Use **packwiz** in `modpack/`. `packwiz init` with the pinned MC + Fabric versions.
- Add Fabric API and the third-party mods via `packwiz modrinth add`. Initial third-party list (verify each exists for the pinned version; swap for equivalents if not):
  - Fabric API
  - Gun framework: Timeless and Classics Zero (TACZ) **or** Point Blank — pick the one with the better Fabric support for the pinned version; record in DECISIONS.md.
  - Mod Menu, Cloth Config (config UI)
  - GeckoLib (animated entities for bosses)
  - Lithium (server perf)
  - Sodium + Iris (client perf, client-only)
- Our four mods are added as local files that `release.yml` uploads to the GitHub release and references by URL.
- `packwiz modrinth export` → `.mrpack`.
- Write `modpack/README.md`: "Install Modrinth App → Add pack from file → drag this .mrpack → join `<ip>`."

### 2c. Hosting
- Hetzner CPX31 (or equivalent, 8GB RAM) running Docker. `docker compose up -d`. Document in `docker/HOSTING.md`: provisioning, firewall (only 22 + 25565), updating (`docker compose pull && docker compose up -d`).

**Done when:** `docker compose up` locally runs a joinable Fabric server with Fabric API + the empty feature mods; the `.mrpack` imports cleanly into Modrinth App; a friend can connect.

### 2d. Distribution: make it super easy

**Players: one click, auto-updates.**
- The modpack is published as a **Modrinth project**. `release.yml` uploads every release automatically (needs a
  `MODRINTH_TOKEN` repo secret and the project id).
- Players install the Modrinth App, search "BlockSouls", click Install, then Play. Updates show up in the app.
- A GitHub release `.mrpack` stays as the fallback (drag into Modrinth App or Prism).
- **Version check on join:** a client whose BlockSouls version doesn't match the server's is turned away with one
  plain line: "Update BlockSouls in the Modrinth App".

**Hosting: three ways, easiest first.**
1. **Host from your own game:** the pack bundles a tunnel mod (e4mc or equivalent, if it has a 26.3 build). Open
   to LAN → it prints a public address → friends join it. No server, no port forwarding. The whole game loop
   runs in the integrated server.
2. **Always-on server with one command:** `curl -fsSL <raw github url>/docker/install.sh | sh` on any fresh Linux
   VPS. It installs Docker, writes `.env` interactively (whitelist, ops), starts the stack and opens the firewall.
   **Watchtower** in compose pulls each new release image automatically, so updating is just tagging a release.
3. **Rented Minecraft hosts:** a server-pack zip on each release (mods + config) for hosts that take uploads.

**New run:** `/bs run new` (admin) restarts the server onto a fresh world, keeping the difficulty setting.

## 3. Phase A — Movement (mod: `movement`)

This is the feel layer. Build in the order below; each one is tunable from `config/blocksouls/movement.json`.

### A1. Mario jump
- Mixin into `LivingEntity#jump` / `getJumpVelocity` for players only.
- Variable height: client tracks ticks jump key is held (cap ~8); each held tick adds `holdBoost` upward velocity. Server receives a tiny packet with the hold count for validation (clamp).
- Faster fall: when `velocity.y < 0` and airborne, multiply gravity by `fallGravityMult` (default 1.6).
- Short coyote time (3 ticks) and jump buffering (5 ticks).
- Optional triple-jump chain: three jumps within 0.5s of landing each give +15%/+30% height. Config-gated, default on.

### A2. Dodge roll (Souls)
- Keybind (default: Left Alt). On press while grounded: apply horizontal impulse in input direction (or facing if no input), set `rolling` for 12 ticks, grant **i-frames for ticks 2–8** by cancelling `LivingEntity#damage` for the player during that window (server-authoritative; client sends roll packet, server starts the timer).
- Stamina: lightweight stamina bar (client HUD), 100 max, roll costs 25, regen 15/s after 1s delay. Stored as a player attachment (Fabric Attachment API).
- Animation: lower eye height and tilt camera during roll (client-only).

### A3. Wall climb
- Each tick while airborne and pushing into a solid block face (check `world.getBlockCollisions` for the player's horizontal AABB expanded 0.05 toward facing): set `velocity.y = climbSpeed` (0.25), drain stamina 5/s.
- Ledge grab: if the block above the collided block is air and player's eye is within 0.6 of its top, snap up and over.
- Server mirrors the check for anti-cheat; client predicts.

### A4. Web swing
- Keybind (default: right-click while holding the "Web Shooter" item, or a bare keybind — config). On press: raycast up to 40 blocks in look direction, prefer blocks above the horizon; on hit, store anchor point and rope length = distance.
- Each tick while held: pendulum constraint. If `distance(player, anchor) > ropeLength`, project velocity onto the tangent plane and pull position back to the sphere. Add small forward boost along the swing arc (gravity does the rest). On release: keep velocity, add `releaseBoost` of +0.15 up.
- Render: a thin line from hand to anchor (client, `WorldRenderEvents.AFTER_ENTITIES`).
- Server authority: server runs the same constraint from the client-reported anchor and corrects if the client drifts > 2 blocks.
- Fall damage: cancel while swinging or within 10 ticks of releasing.

**Done when:** all four feel good in `runClient` (human playtests; tune defaults), no server kick for "moved too fast" (adjust `max-tick-time`/vanilla anti-cheat flag in config or override the check for our movement states), unit tests for the pendulum math and stamina regen.

## 4. Phase B — Guns (mod: `guns`)

Use the chosen framework for ballistics/recoil/reload; our mod supplies weapons, ammo, and the perk/rarity layer.

### B1. Weapon set (v1: six weapons)
| Archetype | Slot | Notes |
|---|---|---|
| Hand Cannon | Kinetic | 3-shot burst-capable, high precision dmg |
| Auto Rifle | Kinetic | bread and butter |
| Pulse Rifle | Energy | burst |
| Shotgun | Special | close range |
| Sniper | Special | 2× scope, precision ×3 |
| Rocket Launcher | Heavy | splash, scarce ammo |

Each weapon = one JSON definition in the framework's format + a GeckoLib or JSON model + original textures (16-32px, chunky to fit Minecraft).

### B2. Ammo economy
- Three ammo types (Primary / Special / Heavy) as items. Primary drops from any kill, Special 1-in-4, Heavy from bosses/zombie round ends. Max carry capacities in config.

### B3. Rarity + perks
- Item components: `rarity` (common/rare/legendary/exotic) and `perks` (list of ids). On craft/drop, roll 1–2 perks from the weapon's pool.
- Perk pool v1 (8): Outlaw (faster reload after precision kill), Rampage (stacking dmg on kill), Kill Clip, Explosive Rounds, Dragonfly (AoE on precision kill), Vorpal (+boss dmg), Lightweight (+move speed), Snapshot (faster ADS).
- Tooltip shows rarity color + perk names/descriptions.
- Exotic (one per archetype, v2) = unique perk with its own behavior class.

### B4. Precision hits
- Headshot detection: compare hit vector y to `entity.getEyeY() - 0.3`. Multiply damage and emit yellow damage particle.

### B5. Crafting materials + world loot
- Every gun and every ammo type is craftable in survival. Add whatever new ores/items the recipes need (e.g. a gunmetal ore that generates underground, smelts to ingots, plus parts like barrels/receivers/firing pins). Recipe tiers should map to rarity: commons from early-game materials, legendaries need boss/zombie drops.
- Guns and ammo appear as **rare chest loot** across vanilla structures (villages, dungeons, mineshafts, strongholds, desert/jungle temples, outposts, ancient cities, etc.), injected via Fabric's `LootTableEvents.MODIFY`. Rarer structures roll higher rarity. Weights live in config.

**Done when:** six weapons craftable (recipes in data), every gun and ammo type obtainable in survival via crafting, guns/ammo show up in vanilla structure chests, all perks fire correctly in a gametest, ammo drops, headshots register.

## 5. Phase C — Zombies wave mode (mod: `zombies`, server-heavy)

**Night waves are the core game loop; see §0b for the design.** Admins can switch them off in config. The arena mode below stays available as a side mode for built maps.

### C1. Arena + round manager
- A `ZombiesArena` is a world-saved data object: bounding box, spawn points (list), barricade positions, start trigger block, current round, players in, points per player.
- Admin command `/zombies arena create <name>`, `/zombies arena addspawn`, `/zombies arena setbounds`, `/zombies start`, `/zombies stop`. Wand item to click corners.
- Round N: zombie count = `min(24 + 6N, 60)` alive-cap 24; zombie health = `20 + 10N` (cap 400); speed tier increases every 5 rounds. 10s intermission between rounds, "Round N" title + sound.
- Power-ups (10% drop on kill): Insta-Kill (30s), Double Points (30s), Max Ammo, Nuke. Floating item entity with glow + despawn 15s.

### C2. Horde zombie
- Custom entity `HordeZombie extends ZombieEntity`: no sunlight burn, no door-breaking (uses barricades), targets nearest player in arena, pathfinds to nearest barricade if no path. Rounds ≥10 spawn 1 in 8 as `Sprinter` variant.

### C3. Barricades + buy system
- Barricade block: 5 "planks" state; zombies break one plank/2s when adjacent; players rebuild with right-click (10 points each).
- Wall-buy blocks: right-click with enough points → give weapon/ammo. Points: 10/hit, 60/kill, 100/headshot kill.
- Door blocks (buyable, open an area), Mystery Box block (random weapon for 950 points, weighted by rarity).
- Points displayed in a sidebar scoreboard.

### C4. Perk machines (v2)
- Juggernog (+hp), Speed Cola (reload), Stamin-Up (stamina regen). Lose on death. Max 4.

- **Night waves (§0b):** at dusk, if anyone is online, wave N starts with a "Wave N" title for every overworld player; Horde Zombies spawn around each of them in survival terrain using the round scaling. Cleared waves chain into the next after a short break while night lasts; dawn ends the current wave. Kills roll gun/ammo/material drops (rarity weighted by wave number); clearing a wave gives a reward chest.
- **Starting kit + Starter Pistol** (first join only) and the pistol breaking at the end of wave 3.
- **Shorter day/night cycle** (§0b).

**Done when:** waves trigger server-wide at dusk with players online, chain within a night and stop at dawn; the wave counter persists across restarts and skips empty nights; elites survive dawn; wave kills drop guns/ammo; the Starter Pistol breaks after wave 3; `/zombies start` in a built arena runs rounds to at least 20 with 4 players without server TPS < 18; downed/revive loop works; unit tests for round scaling and point math.

## 6. Phase D — Bosses & raids (mod: `bosses`)

### D1. Boss framework
- `BossEntity` base: phases list, each phase = health threshold + attack pattern list + optional arena change hook. `AttackPattern` = windup ticks (telegraph: particles + sound), active ticks (hitbox/damage), recovery ticks. Attacks are chosen by weighted RNG with cooldowns. Boss bar via `ServerBossBar`.
- Lock-on: client keybind (default: middle mouse) locks camera to nearest boss; soft camera lerp.
- Boss arena: fog wall block (passable only outward until boss dies), boss music via custom sound event.
- Death: drops Heavy ammo, a guaranteed Legendary weapon, and a "Soul" currency item. Drops should fit the boss (e.g. Ashwyrm drops fire-themed gun parts/materials used in high-tier recipes).
- **Boss health bar:** custom-rendered (not the vanilla boss bar look): boss name, wide styled bar, a trailing "damage taken" chunk that drains after a delay, phase-threshold notches, and a subtle shake/flash on phase change. Vanilla `ServerBossBar` stays as the server-side source of truth; the client draws our own HUD from synced data.

### D2. Boss 1 — the Dragon ("Ashwyrm")
- GeckoLib model, ~12 blocks long. Phases:
  - P1 (100–60%): ground. Attacks: bite (short windup), tail sweep (360°, jump-dodge), fire breath (cone, 3s, roll through).
  - P2 (60–25%): takes flight, strafes with fireballs that leave fire blocks; periodically lands → short vulnerability window with 2× damage.
  - P3 (<25%): ground, enraged (faster), adds "firestorm" — meteors on random players, 1.5s telegraph circles.
- Weak point: head hitbox child entity takes 2× dmg.

### D2b. Random boss events (field bosses)
- Like Elden Ring's field bosses / invasions: bosses occasionally appear in the overworld near players (e.g. a dragon landing on a mountain, a giant knight at a ruin at night), announced with a sound + title + map marker. Spawn chance, cooldowns and biome rules in config; never inside claimed/arena areas or near spawn by default.
- Uses the D1 framework so every boss can be a field boss. Fog wall goes up only when a player engages.
- **Optional**: you can walk away. They hold a **mini arena** (a ruin, a bastion, a crater) or roam open fields,
  and support **lock-on**. Field bosses never despawn mid-fight.
- **Boss pool** (original names, inspired-by only):

  | Boss | Where | Drop |
  |---|---|---|
  | **Furnace Knight** | Guards a Nether bastion; mid-run milestone | Blaze rods, an Ender Eye, a blight core |
  | **Ashwyrm** | A dragon that lands on mountains | Fire-themed gun parts |
  | **Stormplate Knight** | A towering knight in dragon-scale armor, lightning spear, ruined arena | Dragonscale armor set |
  | **The Pale Rider** | A ghostly horseman roaming plains at night | **Wraith Steed whistle**: a summonable spectral horse with a double jump |

### D3. Final raid — the reworked Ender Dragon (§0b)
- The vanilla Ender Dragon fight becomes the finale: multi-phase, telegraphed attacks, mechanics for 2–4 players
  (the plate / relic-carrier / crystal-order ideas from "The Vault" below fit here). Uses the D1 framework and boss bar.
- Its death flips the world to endless mode (zombies mod listens for it).

### D3b. Raid encounter — "The Vault" (v2, optional side raid)
- Multi-room gauntlet with mechanics requiring coordination: plate-standing to open doors (needs 2 players), "relic carrier" buff item (cleanses a debuff on others), DPS phase on a mini-boss gated by destroying 3 crystals in order shown by a symbol display. Ends in a boss (reuse framework, new model).
- Checkpoint system: wipe → respawn at last checkpoint.

**Done when:** random boss events spawn per config and despawn cleanly if ignored; the custom boss health bar renders correctly for 1–4 players; Ashwyrm is beatable by 2–4 players in ~5 minutes with v1 guns and movement; no phase softlocks (gametest walks the phase machine with scripted health).

## 7. Phase E — Polish & release

- HUD: stamina bar, ammo counter, lock-on reticle, round counter — all in one `HudRenderer` with config toggles.
- Sounds: original SFX (synth or freesound CC0 — record licenses in `ASSETS.md`).
- Balance pass config file with every number used above; expose via Cloth Config.
- `release.yml`: on `v*` tag → build, test, GHCR push `ghcr.io/<user>/blocksouls:<tag>` and `:latest`, packwiz export, GitHub release with `.mrpack` + jars.
- Server update doc: tag → wait for CI → `docker compose pull && up -d` → tell friends to update pack in Modrinth App.

## 8. Session plan (parallel workstreams)

Goal: get to a playable v1 by running sessions **in parallel**, each in its own worktree and branch, each owning
one module so they rarely touch the same files. Sections 3–7 still describe the features; this section says
who builds what, in what order. Phase letters (A–E) no longer gate each other; the waves below do.

### Module layout (set up in Wave 0)

| Module | Owns |
|---|---|
| `common` | **Contracts every module codes against**: the run's game state (wave number, phase, endless flag, difficulty), game events (wave start/end, dawn, boon picked, boss killed), mob-spawn-by-id API, shared tags and damage types, config + networking helpers, debug-command registry |
| `movement` | Jump, roll, climb, web swing, stamina (A1–A4) |
| `guns` | Weapon framework, all guns incl. Starter Pistol, ammo, perks, precision hits |
| `game` (was `zombies`) | Day/night length, wave scheduler, multi-wave nights, dawn pause, sleep rule, dimension rule, starting kit, boons, difficulty presets, endless mode, wave HUD, arena side mode |
| `mobs` | Wave roster, mutants, Nether mobs, block-breaking AI; spawned by id through `common` |
| `world` | Scrap, ores, worldgen, wrecks/ruins/structures, radiation zones, loot tables, Ender Eye sources |
| `vehicles` | Kart, gun truck, speeder; wreck repair |
| `bosses` | Boss framework, boss bar, lock-on, field bosses, final dragon raid |

Rules for parallel work:
- `common`'s contracts are written in Wave 0 and **frozen**. Changing one is its own small PR, merged before
  anyone depends on the change.
- Modules talk only through `common`: events, and spawning or looking things up by id. No module imports another feature module.
- Every session ships **placeholder assets** from the shared generator (`tools/assets/`), so nobody waits on art.
  The Graphics session replaces them later.
- Every feature gets debug commands (`/bs wave start 7`, `/bs spawn mobs:brute`, `/bs time dusk`, ...) and at least
  one server gametest, so CI can check it without a human.

### Wave 0: foundation (one session, sequential; everything depends on it)
1. **Working build:** network access or CI green; real `./gradlew build` + runClient/runServer.
2. Module layout above; `common` contracts with stub implementations; debug-command registry; gametest
   harness in CI (server gametests headless, client gametests under xvfb for smoke screenshots).
3. Placeholder asset generator; verify GeckoLib (and any third-party mod we still want) has a 26.3 build.

### Wave 1: features (parallel, one worktree each)
| Session | Builds |
|---|---|
| **Game loop & waves** (dedicated) | Everything in `game` |
| Mobs | Wave roster (8 types), mutants, Nether mobs, block breaking |
| Guns | Own hitscan/projectile framework, 6 weapons + Starter Pistol, ammo, perks, precision |
| World & items | Scrap, ores, wrecks/ruins, radiation, loot, Ender Eye sources, Nether changes |
| Vehicles | Kart, gun truck, speeder, repair |
| Movement | A4 web swing + roll visuals |

### Wave 2: bosses & look (parallel)
| Session | Builds |
|---|---|
| Boss framework + field bosses | D1, D2, D2b, custom boss bar |
| **Final raid** (dedicated) | Reworked Ender Dragon, endless-mode trigger |
| Graphics & audio | Textures, models, sounds, particles, blood-moon / dusk sky cues, HUD styling |

### Wave 3: intersections (sequential)
Wire it all together: mob roster into waves, drops into recipes into progression gates, boons tuned,
balance to a ~2-hour run, performance with 4 players at wave 10, full-run gametest with scripted waves,
distribution (§2d: Modrinth publish, install script, Watchtower, server pack, version check) →
`v1.0.0`.

Each wave ends with its branches merged to `main` and CI green, before the next wave starts.

## 9. Known risks

- Third-party gun framework may lag the pinned MC version → fall back to writing a minimal hitscan/projectile system in `guns` (reserve a session).
- Vanilla "moved too quickly" checks fight custom movement → set `MAX_MOVE_SPEED` override or flag movement states so our server mixin skips the check for them.
- GeckoLib learning curve for the dragon → prototype with a scaled vanilla Ender Dragon model first, swap later.
- Swing physics is the only part with real tuning risk; budget playtest time.
- **Art is the biggest gap for "show, don't tell".** Code sessions can generate decent procedural pixel textures and
  simple block/item models, but animated mob and boss models and good sound design need either a human with
  Blockbench or CC0 asset packs (licenses in `ASSETS.md`). Plan the Graphics session around that.
- **"One-shot to playable" needs automated verification.** Parallel sessions only converge if each can build and run
  gametests itself, hence Wave 0's working build and gametest harness. Fun still needs human playtests.
- **Balance of a 2-hour run** can only be tuned by playing it; everything tunable stays in config.
