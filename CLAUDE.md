# BlockSouls

Modded Minecraft (Fabric): Souls-style bosses, Destiny-style guns, COD-style zombies waves,
web-swinging/wall-climbing, Mario-style jumps. Shipped as a Docker server image + a Modrinth modpack.

- **The plan:** `PLAN.md`. Work top-down, one phase at a time; don't start a phase until the previous
  one's "Done when" passes.
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
./gradlew :zombies:runServer    # dev server with zombies + common loaded
```

Jars land in `<module>/build/libs/blocksouls-<module>-<version>.jar`.

```sh
cd docker && cp .env.example .env && docker compose up -d --build   # local server (see docker/HOSTING.md)
cd modpack && ./add-mods.sh && packwiz modrinth export             # build the .mrpack
```

## Layout and rules

- **One mod per feature**: `movement`, `guns`, `zombies`, `bosses`. Each is its own Fabric mod with id
  `blocksouls_<module>` so a server-only change ships without a client update.
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

## Conventions

- Conventional commits, small PRs per task.
- Unit-test pure logic (wave math, damage calc, physics helpers) with `fabric-loader-junit` in `src/test/`.
- Make tunable numbers config fields, not constants.
