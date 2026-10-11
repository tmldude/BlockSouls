# Status (handoff)

Last updated 2026-10-11 (after #24). Read this after `CLAUDE.md` to know where the work stands.

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

## Stage 2a: done
`bosses` framework + Furnace Knight (#16), `art-mobs` (#15), `art-items` (#14). Distribution (#12: install script,
auto-update, server pack, version check), full-pack integration tests (#13: server game tests + a real client on a
dedicated server, screenshots published to `ci-screenshots/<branch>`), 26.3 loot fix (#17), polish (#22).

## Party scaling (#24): done
Built for parties of 1–8. Everything player-dependent goes through `common`'s `PartyScaling`
(`config/blocksouls/party.json`); see `CLAUDE.md` "Party size".

## Now: Stage 2b
`boss-pale-rider`, `boss-ashwyrm`, `boss-stormplate` are ready; `final-raid` (#21) is finishing (raid restructured:
vanilla phase → on-foot phase with a wave and a herald field boss → sealed crystals + plates). They merge together
through `claude/stage2b-integration` (#23). Then route the raid's plate and crystal counts through `PartyScaling`
with tests at 1 and 8 players.

## Next: v0.9.0 (first trial run with friends)
Tag `v0.9.0` → `release.yml` publishes the GHCR image, jars, `.mrpack` and server pack (and Modrinth, if its
secrets are set). The trial run doubles as the first human balance pass. After it: balance to a ~2-hour run,
performance with a full party at late waves, real art and sounds, then the PLAN.md §0c "Growth" column → v1.0.

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
