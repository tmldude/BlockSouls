# BlockSouls

Modded Minecraft (Fabric): Souls-style bosses, Destiny-style guns, COD-style zombies waves,
web-swinging/wall-climbing, Mario-style jumps. Shipped as a Docker server image + a Modrinth modpack.

- **The plan:** `PLAN.md`. §0b is the game design every feature serves. §8 splits the work into waves of
  parallel sessions, each owning one module; don't start a wave until the previous one is merged with CI green.
- **The next version:** `PLAN2.md` ("Expedition": act-structured run, synergy builds, AI Director, origins,
  bosses v2). v1 ships first; v2 work starts at its Wave 4.
- **Decisions:** `DECISIONS.md` logs every default chosen. Where it conflicts with `PLAN.md`, `DECISIONS.md` wins.
  Add an entry whenever you pick a default the plan doesn't specify.

## Version pin

Minecraft **26.3**, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18-SNAPSHOT, Gradle 9.7.1, **Java 25**.
Pinned in `gradle.properties`. Never bump mid-phase.

Minecraft 26.x is unobfuscated: code uses Mojang's official names (`ServerPlayer`, `Identifier`,
`CustomPacketPayload`), not Yarn. There is no `mappings` line and no remapping. Key codes are SDL
scancodes (`org.lwjgl.sdl.SDLScancode`), not GLFW.

## Commands

```sh
./gradlew build                 # build + test all mods; run before every commit
./gradlew test                  # unit tests only
./gradlew :movement:runClient   # dev client with movement + common loaded
./gradlew :game:runServer       # dev server with game + common loaded
```

Jars land in `<module>/build/libs/blocksouls-<module>-<version>.jar`.

```sh
cd docker && cp .env.example .env && docker compose up -d --build   # local server (see docker/HOSTING.md)
cd modpack && ./add-mods.sh && packwiz modrinth export             # build the .mrpack
```

**No access to maven.fabricmc.net / Mojang?** (some sandboxes) Use `tools/offline-check/`: it type-checks
against decompiled 26.3 vanilla + Fabric sources and runs unit tests. Run `setup.sh` once, then
`typecheck.sh` and `runtests.sh` before every commit. For mixins, also grep the vanilla sources in
`~/.cache/blocksouls-offline-check/mc/client` to confirm the target method name and signature.

## Layout and rules

- **One mod per feature**: `movement`, `guns`, `game`, `mobs`, `world`, `vehicles`, `bosses` (who owns what:
  PLAN.md §8). Each is its own Fabric mod with id `blocksouls_<module>` so a server-only change ships without a
  client update. Feature modules never import each other; they talk through `common` (see "Contracts" below).
- `common` is a library mod (`blocksouls_common`) nested jar-in-jar into each feature mod. It holds
  `Networking` (typed payloads), `Config` (JSON in `config/blocksouls/`), and client-side `Keybinds` /
  `ClientNetworking`.
- Shared Loom config lives in the root `build.gradle` `subprojects {}` block; per-module build files only
  add module-specific dependencies.
- Client-only code goes in `src/client/` (split source sets); never reference client classes from `src/main/`.
- Packages: `io.github.tmldude.blocksouls.<module>`.

## Assets

All textures, models, sounds and names are **original**. No Bungie / FromSoftware / Activision / Marvel /
Nintendo assets or trademarked names. Use generic or inspired-by names ("Hand Cannon" yes, "Gjallarhorn" no).
Record third-party asset licenses in `ASSETS.md`.

## Party size: 1 to 8 players

BlockSouls is built for parties of **1–8 players**. Anything that depends on how many players there are (wave sizes,
mobs alive at once, elites, boss health, mechanics that need several players, event frequency, loot shared per party)
goes through `common`'s `PartyScaling` (curves in `config/blocksouls/party.json`), never a module's own per-player
formula. Solo must stay completable, and anything that scales needs a test at 1 and at 8 players.

## Conventions

- Conventional commits, small PRs per task.
- Unit-test pure logic (wave math, damage calc, physics helpers) with `fabric-loader-junit` in `src/test/`.
- Make tunable numbers config fields, not constants.
