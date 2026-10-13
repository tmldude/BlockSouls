# Status (handoff)

Last updated 2026-10-13 (v0.11.0). Read this after `CLAUDE.md` to know where the work stands.

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

## Stage 2b: done
Pale Rider, Ashwyrm, Stormplate Knight and the three-phase final raid (#23), the raid on `PartyScaling` (#26), the
End handoff checked by the client test (#27). A client crash when a boss with hitbox parts spawned was fixed in #23.

## v0.9.0: released (first trial run)
Release workflow can be started by hand on `main` (#28; this sandbox can't push tags). Modrinth publish is on.

## Stage 4 (v0.10.0): done
Original sounds and music for guns, bosses and the kart (#36), custom boon icons (#31), kart fuel and horn (#33),
fog-wall arenas that hold in 3D (#34), wrecks/checkpoints as `/locate`-able structures (#32), content standards +
CI crafting check (#35) and every drop in 2+ recipes (#38). Releases set the Modrinth environment (#30).

## Playtest fixes (v0.10.1, v0.11.0): done
v0.10.1: faster pacing (12-minute days, 2–4 waves a night, 8 s breaks), migrated onto live servers through
`defaultsVersion` (#42). v0.11.0: `/bs config reload` and a tuning map (#44), recipe book, tutorial advancements
and recipe viewer (#45), denser resources (#43), wave drops that feel good with a gun pity timer and faster pacing
(#53).

## Next
- v1 track (this orchestrator): next playtest → feedback pass (hit markers, ammo cues, wave-clear fanfare, death
  recap) → v1.0.0.
- v2 (`PLAN2.md`, the Floor Loop, its own orchestrator and PRs): Stage 6A in progress; classic mode stays the live
  default until v2 ships.

## Decisions that changed late (see DECISIONS.md for all)
- Downed players stay down until revived (no bleed-out); everyone down at once ends the run; going down costs
  boons, gear is kept.
- All 4 field bosses are in the MVP, with custom models (no vanilla bodies).
- Orchestrator: this repo's lead session (Opus); workers per `docs/sessions/`.

## Owner to-dos
- Done: Modrinth project + `MODRINTH_TOKEN` / `MODRINTH_PROJECT_ID` secrets.
- Submit the Modrinth project for review (description, MIT license, icon) so friends can see the unlisted pack.
- Host: Modrinth Servers (8 GB for 6–8 players) or the Hetzner VPS + `docker/HOSTING.md` install script.

## Known gotchas
- Client source sets of feature mods see `common`'s client classes through the `clientImplementation files(...)`
  wiring in the root `build.gradle` (a plain project dependency only exposes `main`).
- Inside a `dependencies {}` block, `project(':x')` is a dependency, not the project.
- Minecraft 26.3 renames: `Gui` → `Hud`, `GuiGraphics` → `GuiGraphicsExtractor`, `ResourceLocation` → `Identifier`;
  key codes are SDL scancodes.
