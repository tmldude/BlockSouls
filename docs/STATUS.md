# Status (handoff)

Last updated 2026-10-11. Read this after `CLAUDE.md` to know where the work stands.

## Done (on `main`, CI green)
- Build: Gradle multi-project, Minecraft 26.3 / Fabric API 0.162.0+26.3 / Java 25, CI (`build.yml`), release
  (`release.yml`: jars, `.mrpack`, GHCR image).
- Docker server image + compose + backups + `docker/HOSTING.md`; packwiz modpack scaffold.
- `tools/offline-check/`: type-check + unit tests without Fabric's maven (this sandbox can't reach it).
- Movement A1–A3: Mario jump, dodge roll + stamina + HUD, wall climb.
- Design: `PLAN.md` §0b (game), §0c (MVP), §2d (distribution), §8 (parallel sessions); `DECISIONS.md`.

## Wave 0 (PR #2): done
Module restructure (`game`, `mobs`, `world`, `vehicles`), `common` contracts, game-test harness, placeholder asset
generator, world-reset hook, `game` split into `RunSystem` / `PlayerSystem` / `GameHud` / `ClientRun`, worker
protocol + all session briefs in `docs/sessions/`.

## Stage 1: done (PR #10)
game-loop, game-players, mobs, guns, world, vehicles, movement merged; CI green on the combined tree. Each
session's choices are in `docs/decisions/<session>.md`.

## Now: Stage 2
`bosses` (framework + Furnace Knight) plus `art-mobs` and `art-items` in parallel. When `bosses` merges:
`boss-pale-rider`, `boss-ashwyrm`, `boss-stormplate`, `final-raid`. Then Stage 3 (integration, balance, v0.9).

## Decisions that changed late (see DECISIONS.md for all)
- Downed players stay down until revived (no bleed-out); everyone down at once ends the run; going down costs
  boons, gear is kept.
- All 4 field bosses are in the MVP, with custom models (no vanilla bodies).
- Orchestrator: this repo's lead session (Opus); workers per `docs/sessions/`.

## Owner to-dos (needed by the first release, not before)
- Create a Modrinth account + BlockSouls project; add repo secrets `MODRINTH_TOKEN` (a personal access token that
  can create versions) and `MODRINTH_PROJECT_ID`.
- Rent the Hetzner VPS when ready to play; run the install script from `docker/HOSTING.md`.

## Known gotchas
- Client source sets of feature mods see `common`'s client classes through the `clientImplementation files(...)`
  wiring in the root `build.gradle` (a plain project dependency only exposes `main`).
- Inside a `dependencies {}` block, `project(':x')` is a dependency, not the project.
- Minecraft 26.3 renames: `Gui` → `Hud`, `GuiGraphics` → `GuiGraphicsExtractor`, `ResourceLocation` → `Identifier`;
  key codes are SDL scancodes.
