# Decisions

Running log of defaults chosen where `PLAN.md` was silent or out of date. Newest at the bottom.
Entries here override `PLAN.md`.

## Phase 1 — skeleton (2026-10-10)

- **Minecraft 26.3** (latest Fabric-supported release; Fabric API 0.162.0+26.3, Loader 0.19.5,
  Loom 1.18-SNAPSHOT, Gradle 9.7.1), per the plan's "pin the latest release" rule.
- **Java 25, not 21.** Minecraft 26.x requires Java 25. Phase 2's Docker images change accordingly
  (a Java 25 tag of `itzg/minecraft-server` and a JDK 25 Gradle build image; verify the tags exist).
- **Mojang names, not Yarn.** Minecraft is unobfuscated since 26.1 and Fabric no longer publishes Yarn for it.
  We use the non-remapping Loom plugin (`net.fabricmc.fabric-loom`) with no mappings.
- **Mod ids / namespaces:** `blocksouls_<module>` per mod, so asset and registry ids never collide across jars.
  Keybinds share one translation namespace (`key.blocksouls.*`) and one controls category.
- **Packages:** `io.github.tmldude.blocksouls.<module>`.
- **`common` ships jar-in-jar** inside each feature mod rather than as a fifth jar, so every feature mod
  installs on its own. `./gradlew build` still produces a standalone common jar; Docker/modpack ship only the four feature jars.
- **Config loader:** Gson into plain classes with default field values. Missing keys keep defaults and the
  file is rewritten to add them; a malformed file is left untouched and defaults are used.
- **License: MIT** (owner asked for it to be public/permissive). `LICENSE` at the root; every
  `fabric.mod.json` declares `"license": "MIT"`. Third-party assets keep their own licenses (`ASSETS.md`).

## Design additions from the owner (2026-10-10)

Folded into `PLAN.md` (B5, Phase C intro + nightly wave, D1 drops + health bar, D2b):
guns/ammo craftable with new ores/items; guns/ammo as rare loot in vanilla structure chests;
boss drops themed to the boss; each night is a server-wide zombie wave that drops guns and ammo;
Elden Ring-style random field bosses; a polished custom boss health bar.

- **Night waves are server-wide** (owner's call), not per-player opt-in. The night counter counts up and
  persists in world data; nights with nobody online don't spawn a wave or advance the counter. "Optional"
  means an admin config toggle.
- The arena-based zombies mode stays alongside night waves (assumption; confirm in Phase C).

## Phase 2 — Docker + modpack (2026-10-11)

- **Base image `itzg/minecraft-server:java25`**, build stage `eclipse-temurin:25-jdk` running the Gradle
  wrapper (so the image builds with the pinned Gradle 9.7.1, not whatever a `gradle:` tag ships).
- **No custom mod-copy script.** The itzg image already syncs `/mods` into `/data/mods` on every start
  and leaves other jars alone, so our jars go in `/mods`. They're staged under **unversioned names**
  (`blocksouls-movement.jar`) so an update overwrites the old jar instead of leaving two versions loaded.
- **Versions come from `gradle.properties`.** The build stage writes them to `/etc/blocksouls.env`; a small
  entrypoint defaults `VERSION` / `FABRIC_LOADER_VERSION` from it before handing off to the itzg start
  script. No `MC_VERSION` to keep in sync in compose.
- **No `docker/server.properties` file.** itzg generates it from env vars (`VIEW_DISTANCE`, `MOTD`, ...), and a
  checked-in file would fight that.
- **Secrets in `docker/.env`** (git-ignored; `.env.example` committed): `WHITELIST`, `OPS`, `RCON_PASSWORD`.
  The backup service talks to the server over RCON, so both share `RCON_PASSWORD`.
- **Image `ghcr.io/tmldude/blocksouls`**, tag from `BLOCKSOULS_TAG` (default `latest`). compose also has a
  `build:` section so `docker compose build` works without a registry.
- **Healthcheck `start_period` 5m** (image default is 2m) because a modded first start downloads mods and
  generates the world.
- **Modpack written by hand** (`pack.toml` + `index.toml`, then `packwiz refresh`) because `packwiz init`
  needs Mojang's version manifest. `modpack/add-mods.sh` holds the third-party mod list.

- **Our jars ship inside the `.mrpack` as overrides**, not as URL references to the GitHub release as the
  plan said. `packwiz modrinth export` downloads URL files to hash them, so a URL reference needs the release
  to exist first and the repo to be public; overrides need neither. Names are unversioned so a pack update
  overwrites them.
- **`release.yml`** refuses a tag that doesn't match `mod_version` in `gradle.properties`, then builds, creates
  the GitHub release (jars + `.mrpack`) and pushes `ghcr.io/tmldude/blocksouls:<tag>` and `:latest`.
- **Offline check** (`tools/offline-check/`): this sandbox can't reach Fabric's maven or Mojang, so code is
  type-checked against decompiled 26.3 vanilla + Fabric API/Loader sources, and pure-logic unit tests run with
  a stub `FabricLoader`. Not a substitute for `./gradlew build`; CI remains the real gate.

## Open risks to resolve in Phase 2

- The plan's gun frameworks (TACZ, Point Blank) have historically been Forge/NeoForge-first. Verify Fabric
  builds for 26.3 exist on Modrinth before depending on one; otherwise use the plan's fallback (own minimal
  hitscan/projectile system in `guns`).
- Verify GeckoLib, Mod Menu, Cloth Config, Lithium, Sodium and Iris all have 26.3 builds.

## Phase A — Movement (2026-10-11)

### A1 jump
- **Client-side only for now.** Vanilla player movement is client-authoritative, so jump physics run on the
  local player. The plan's "server validates the hold count" is deferred to the anti-cheat pass at the end of
  Phase A, since vanilla's own movement checks already bound what a client can do. Each client reads its own
  `movement.json` for now; syncing the server's values is part of that same pass.
- **Defaults** give heights of: tap 1.06 blocks, held 1.37, vanilla 1.25, held 3rd chain jump 2.19.
  Falls use 1.6× gravity but stop accelerating at 3.9 blocks/tick (vanilla terminal velocity) so long falls
  don't trip "moved too quickly".
- **Buffered presses fire on landing even if the key is still held**: the shorter airtime can land before
  vanilla's 10-tick jump cooldown expires, which would otherwise eat the jump.
- **No extra fall gravity / hold boost** while flying, gliding, in liquid, climbing, riding, spectating, or with
  Slow Falling / Levitation.

### A2 dodge roll + stamina
- **Server decides, client moves.** The client checks stamina/ground, sends an empty `roll` packet and
  drives the roll movement (vanilla movement is client-side). The server re-checks, spends stamina and
  runs the 12-tick roll timer; i-frames (ticks 2–8) cancel damage via `ServerLivingEntityEvents.ALLOW_DAMAGE`,
  except damage tagged `bypasses_invulnerability` (void, `/kill`). On a server without the mod the key does
  nothing, so there's never a roll without i-frames.
- **Roll movement:** 0.6 blocks/tick for 7 ticks, easing to 0 by tick 12 (~5.5 blocks). Direction from
  WASD input, or facing if none. Vanilla only kicks above 10 blocks/tick, so no movement-check changes needed.
- **Stamina** is a Fabric attachment synced to its own player only, not saved (full on login). It only syncs
  when it changes, so a player at full stamina sends nothing. Max 100, roll 25, regen 15/s after 1s.
- **HUD:** a thin bar right-aligned above the hunger row, shown only while stamina isn't full; hidden in
  creative/spectator. Turns red under 25%.
- **Deferred to Phase E polish:** the roll's camera tilt and lowered eye height (cosmetic; needs camera
  mixins). Not done yet.

### A3 wall climb
- **Trigger:** airborne + WASD input pushing into a solid block within 0.05 blocks + stamina > 0. Any input
  direction counts, not only forward. **Sneak lets go.** Climbing sets vertical speed to 0.25 but never
  slows a faster rise, so jumping onto a block isn't dampened.
- **Server mirrors the check** from the player's last sent input (`ServerPlayer.getLastClientInput()`) and drains
  5 stamina/s; regen is paused while climbing. No anti-flight changes are needed: vanilla's "flying" kick only
  counts ticks with nothing but air around the player, and a climber is always touching a wall.
- **Ledge grab differs from the plan.** The plan said "snap up when the eye is within 0.6 of the top". A
  teleport-style snap would fight vanilla's server-side "moved wrongly" collision re-check. Instead, once the
  wall's top is ≤ 1.3 blocks above the feet and there's room to stand there, the player gets a pull-up boost
  (0.45 up, 0.2 forward) and climbing pauses 8 ticks so the boost isn't repeated every tick.

## Game design pass (2026-10-11, from the owner)

Recorded as PLAN.md §0b. Owner decisions: ~2-hour run to the final raid; waves 8–10 before it; several waves per
night with a pause at dawn; vanilla mobs keep spawning; Ender Eyes from bosses / teleporting mobs; first-join kit
of a stone pickaxe + an infinite-ammo Starter Pistol that breaks after wave 3; vanilla death (lose everything,
pistol included); endless waves after the finale, no New Game+; admin difficulty preset at world start.

My defaults, to confirm:
- **Day/night cycle ~17 min** (15% shorter than vanilla, from the owner's "10–15% shorter"). With ~7 nights in
  2 hours, several waves per night gets to waves 8–10.
- **The Ender Dragon itself becomes the final raid boss** (from "the final ender dragon raid"). "The Vault" becomes
  an optional side raid.
- **"Better bosses should not despawn"** read as: elite / mini-boss wave mobs survive dawn and keep hunting you;
  ordinary wave zombies burn at dawn.
