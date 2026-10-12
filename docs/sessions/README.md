# Worker sessions

Each file here is a brief for one parallel session (PLAN.md §8). A session is started with:

> Read `docs/sessions/<name>.md` and do it.

## Protocol (every session)

1. **Read first:** `CLAUDE.md`, `PLAN.md` §0b (the game), §0c (MVP scope), `DECISIONS.md`, then your brief.
2. **Branch:** `claude/<name>` from the latest `main`. One PR to `main` when your "Done when" list passes.
   Title `<module>: <summary>`. Don't merge it yourself; the orchestrator reviews and merges.
3. **Stay in your lane.** Only edit the paths your brief says you own. Specifically:
   - Never import another feature module's classes. Cross-module calls go through `common` (events, `Runs`,
     `WaveMobs`, `PlayerTraits`, `PlayerStatus`, `CommonIds`, `CommonTags`, `DebugCommands`).
   - **Data files may reference other modules' ids** (recipes, loot tables, tags), because the pack always ships
     every module. But in 26.3 **one unknown item id in a recipe fails the whole recipe registry** (the server won't
     start), so recipes using another module's items need `"fabric:load_conditions": [{"condition":
     "fabric:all_mods_loaded", "values": ["blocksouls_<module>"]}]`. Tag entries use `"required": false`.
   - **26.3 loot table JSON:** pools, entries and tables take one `"modifier"` (a function object keyed by `"type"`,
     or a list of them) and one `"condition"` (use `minecraft:all_of` for several). The old `"functions"` /
     `"conditions"` / `"function"` keys are silently ignored.
   - `common` is frozen. You may *add* to it (a new constant in `CommonIds`, a new event) when you truly need to;
     never change or remove existing signatures. List any additions under "Contract additions" in your PR.
4. **Verify like CI does.** This sandbox can't reach Fabric's maven, so:
   - If `java -version` isn't 25: `sudo apt-get install -y openjdk-25-jdk-headless` (or without sudo as root), and
     use `/usr/lib/jvm/java-25-openjdk-amd64`.
   - `tools/offline-check/setup.sh` once, then `typecheck.sh <your module> common` and `runtests.sh <your module>`
     before every push. For mixins, grep the vanilla source in `~/.cache/blocksouls-offline-check/mc/client`
     for the exact target method and signature.
   - Push, open the PR, then read the CI run (GitHub MCP `actions_list` / `get_job_logs`; or subscribe to your PR's
     activity). CI runs the real Gradle build, unit tests and your game tests (`runGameTest`, a headless server).
     **CI green is the bar**: keep fixing and pushing until it is, then say so in the PR and stop.
   - Push early (a first compiling slice within your first hour) so CI catches build-script or API surprises
     before you've built everything on top of them.
5. **Testing:** unit-test pure logic (no Minecraft classes) in `src/test/java`; test in-world behavior with
   `@GameTest` methods in `src/gametest/java` (copy the setup from `movement/src/gametest`). Use
   `helper.makeMockServerPlayerInLevel()` for players.
6. **Debug commands:** every feature gets `/bs ...` subcommands via `DebugCommands.register(...)` so it can be
   tested without playing for an hour.
7. **Config:** every tunable number is a field in your module's config class, loaded with
   `Config.load("<file>", ...)` into `config/blocksouls/`.
8. **Assets:** placeholder textures from `tools/assets/gen.py` (see `tools/assets/README.md`). Pick palettes
   that fit the theme. Real art replaces them later at the same paths. Add `en_us.json` lang entries for
   everything with a name.
9. **Show, don't tell.** No lore text. Tooltips state mechanics only. Names are original (no Bungie /
   FromSoftware / Activision / Marvel / Nintendo names).
10. **Decide, don't ask.** Pick sensible defaults and log them in `docs/decisions/<name>.md` (one file per
    session so PRs don't conflict; same style as `DECISIONS.md`).
    Ask the user only if something is irreversible and truly ambiguous.
11. **Minecraft 26.3** uses Mojang names (`ServerPlayer`, `Identifier`, `GuiGraphicsExtractor`, `Hud`), Java 25,
    SDL key codes. When unsure of an API, read the source (vanilla in the offline-check cache, Fabric API in
    `~/.cache/blocksouls-offline-check/fabric-src`) instead of guessing.

12. **Party size 1–8.** Anything that scales with player count uses `common`'s `PartyScaling` (see `CLAUDE.md`), and
    its tests cover 1 and 8 players (8 mock players via `helper.makeMockServerPlayerInLevel()`).
13. **Numbers in briefs are starting points.** Keep them unless they play badly; if you change one, log it.
14. **Commits:** conventional commits, small and frequent; never commit secrets; never push to `main`.
15. **Content standards:** anything you add (items, recipes, loot, mobs, bosses, structures, dimensions, sounds,
    icons) follows `docs/CONTENT.md`. Every drop goes into 2+ recipes and every crafted item connects to other
    BlockSouls items; `python3 tools/content/check_recipe_web.py` must pass (CI runs it).

## Stages

| Stage | Sessions (run in parallel) |
|---|---|
| 1 | `game-loop`, `game-players`, `mobs`, `guns`, `world`, `vehicles`, `movement` |
| 2a | `bosses` (framework + Furnace Knight) |
| 2b | `boss-pale-rider`, `boss-ashwyrm`, `boss-stormplate`, `final-raid`, `art-mobs`, `art-items` |
| 3 | orchestrator: integration, balance, distribution, `v0.9` |
| 4 (v0.10) | `audio`, `boon-icons`, `kart-fuel-horn`, `arena-seal`, `world-structures`, `crafting-web` |
| v0.11 (playtest fixes) | `onboarding`, `drops-and-pacing`, `resources`, `config-reload`; then a second wave for the small feel details (feedback item 6) |
| 5–7 (v2) | see `PLAN2.md` (owned by the v2 orchestrator) |
