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
- **License:** not chosen yet; `fabric.mod.json` has no `license` field. Needs a human decision before the
  first public release.

## Open risks to resolve in Phase 2

- The plan's gun frameworks (TACZ, Point Blank) have historically been Forge/NeoForge-first. Verify Fabric
  builds for 26.3 exist on Modrinth before depending on one; otherwise use the plan's fallback (own minimal
  hitscan/projectile system in `guns`).
- Verify GeckoLib, Mod Menu, Cloth Config, Lithium, Sodium and Iris all have 26.3 builds.
