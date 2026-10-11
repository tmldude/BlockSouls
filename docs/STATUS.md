# Status (handoff)

Last updated 2026-10-11. Read this after `CLAUDE.md` to know where the work stands.

## Done (on `main`, CI green)
- Build: Gradle multi-project, Minecraft 26.3 / Fabric API 0.162.0+26.3 / Java 25, CI (`build.yml`), release
  (`release.yml`: jars, `.mrpack`, GHCR image).
- Docker server image + compose + backups + `docker/HOSTING.md`; packwiz modpack scaffold.
- `tools/offline-check/`: type-check + unit tests without Fabric's maven (this sandbox can't reach it).
- Movement A1–A3: Mario jump, dodge roll + stamina + HUD, wall climb.
- Design: `PLAN.md` §0b (game), §0c (MVP), §2d (distribution), §8 (parallel sessions); `DECISIONS.md`.

## In progress: Wave 0 (PR #2, branch `claude/wave0-foundation`)
- Done on the branch: module restructure (`game`, `mobs`, `world`, `vehicles`), `common` contracts, game-test
  harness (`src/gametest` + `runGameTest`), placeholder asset generator (`tools/assets/`), world-reset hook in
  the Docker entrypoint, `game` pre-split into `RunSystem` / `PlayerSystem` / `GameHud`, worker protocol
  (`docs/sessions/README.md`).
- CI was red because `.gitignore`'s `run/` rule hid the `common/.../run` package; fixed (rules now `/run/`,
  `/*/run/`). Re-check CI on PR #2.
- **Left to do:** the per-session briefs in `docs/sessions/` for `game-loop`, `game-players`, `mobs`, `guns`,
  `world`, `vehicles`, `movement` (Stage 1), then `bosses`, the three other field bosses, `final-raid`,
  `art-mobs`, `art-items` (Stage 2). Then merge PR #2 and launch Stage 1.

## Decisions that changed late (see DECISIONS.md for all)
- Downed players stay down until revived (no bleed-out); everyone down at once ends the run; going down costs
  boons, gear is kept.
- All 4 field bosses are in the MVP, with custom models (no vanilla bodies).
- Orchestrator: this repo's lead session (Opus); workers per `docs/sessions/`.

## Known gotchas
- Client source sets of feature mods see `common`'s client classes through the `clientImplementation files(...)`
  wiring in the root `build.gradle` (a plain project dependency only exposes `main`).
- Inside a `dependencies {}` block, `project(':x')` is a dependency, not the project.
- Minecraft 26.3 renames: `Gui` → `Hud`, `GuiGraphics` → `GuiGraphicsExtractor`, `ResourceLocation` → `Identifier`;
  key codes are SDL scancodes.
