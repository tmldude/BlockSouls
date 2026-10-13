# Decisions

Running log of defaults chosen where `PLAN.md` was silent or out of date. Newest at the bottom.
Entries here override `PLAN.md`. Worker sessions log theirs in `docs/decisions/<session>.md`.

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

## Game design pass 2 (2026-10-11, from the owner)

- **Boons are lost on death** (zombies-perk style). Tiers Common/Rare/Legendary; 3 random cards plus an occasional
  Wildcard pick; include potion effects, stat boosts, movement upgrades, combos and rare wild ones (flight, Elytra).
- **Nether theme:** the source of the infection. Fleshy netherrack, ash fog, overrun piglins, a hidden "dread"
  meter that brings hunters the longer you stay.
- **Art:** procedural placeholders generated in-theme for now.
- **Field bosses** are optional, with mini arenas or open-field roaming, lock-on, never despawn mid-fight. Pool: Furnace
  Knight (MVP), Ashwyrm, Stormplate Knight, Pale Rider (drops a Wraith Steed). Names are original; Elden Ring /
  Dark Souls references stay inspiration only (no "Torrent", no "Dragonslayer").
- **Run variety:** night modifiers, shuffled roster, 1–3 field bosses per run; MVP ships one boss + the final raid.
- **MVP scope** defined in PLAN.md §0c.

## Game design pass 3 (2026-10-11, from the owner)

- **Downed/revive:** 0 health → downed (crawl, ~30 s bleed-out), teammates revive. **Everyone down/dead at once =
  run over.** Bleeding out is a real vanilla death (gear and boons lost).
- **Late joiners** get the Starter Pistol only.
- **Wacky enchantments and armor sets** join boons as "axes of fun".
- **MVP field bosses: 2 of 4.** I picked Furnace Knight (mid-run Nether milestone) + Pale Rider (overworld; the
  Wraith Steed is a big fun payoff). The owner would prefer 4/4; Ashwyrm and Stormplate Knight follow right after the MVP.
- **New run** = fresh world via an admin command + server script (keeps the difficulty setting).
- **HUD** (wave, between-wave timer, boons, downed/revive bars) is MVP.

## Bosses + execution (2026-10-11, from the owner)

- **MVP field bosses stay at 2** (Furnace Knight + Pale Rider), built with **custom models from dedicated design
  sub-sessions**, not re-skinned vanilla bodies.
- **Execution:** full autonomy for PRs, merges and codebase management; questions only when truly blocking.
  This session orchestrates; workers run as parallel sessions per PLAN.md §8.

- **Update:** the MVP now ships **all 4 field bosses fully built** (Furnace Knight, Pale Rider, Ashwyrm, Stormplate
  Knight) with custom models.
- **Distribution (PLAN.md §2d):** Modrinth project auto-published by CI, a "host from your own game" tunnel mod, a
  one-command VPS install script with Watchtower auto-updates, a server-pack zip, and a version check on join.

- **Update (downed):** no bleed-out. Downed players stay down where they fell until revived; everyone down at once
  ends the run. My reading of "boons removed on death like zombies": **going down costs your boons**, gear is kept.
  Void and `/kill` remain vanilla deaths.

## Hosting (2026-10-11, from the owner)

- **Server: Hetzner VPS** (CPX31-class, 8 GB) as the main host, set up by the one-command install script.
  Host-from-your-game (tunnel mod) stays a fallback only if a 26.3 tunnel mod exists.
- **Players: Modrinth project**, auto-published by `release.yml` (needs `MODRINTH_TOKEN` + `MODRINTH_PROJECT_ID`
  repo secrets from the owner). My defaults for what wasn't specified:
  - **Private, permanently** (owner): the Modrinth project stays unlisted (only people with the link), and the
    server is whitelist-only.
  - **Watchtower applies updates automatically in a 05:00 server-time window** only, so nobody is kicked mid-run.
  - **No domain** (owner): friends connect by the server's IP; a domain can be added later.

## Wave 0 close-out (2026-10-11)

- **Session briefs** in `docs/sessions/<name>.md`; workers log decisions in `docs/decisions/<name>.md`.
- **`game` is shared by two parallel sessions.** game-loop owns `game/run/**` + `game/client/run/**`; game-players
  owns `game/player/**` + `game/client/hud/**`. Seams: `RunSystem.endRun(server)` (team wipe → run over) and
  `ClientRun` (client copy of the run state for the HUD). Separate config files `game-run.json` / `game-players.json`.
- **Contract additions:** `CommonIds.SCRAP`, `GUNMETAL_INGOT`, `BLIGHT_CORE` (world materials), `ROLL_PERKS` (guns'
  loot function, usable in any module's loot tables), `CommonTags.WAVE_BREAKABLE` (block tag, filled by mobs).
- **Gun controls:** attack fires, use aims down sights, R reloads. Bullets are attributed to the player + gun so
  on-kill enchantments and boss credit work across modules.
- **No GeckoLib:** custom models use vanilla's Java model + keyframe animation system (no third-party dependency to
  keep in sync with 26.3).
- **Field bosses die once per run** (no farming); the spawner picks from the bosses not yet killed.

## Distribution (2026-10-11, orchestrator)

- **Daily 05:00 update via cron, not Watchtower.** `install.sh` installs `/etc/cron.d/blocksouls` running
  `docker/update.sh`: pull, and only if the image changed, warn players over RCON and restart 60 s later. Same
  behavior as the planned Watchtower window without a third-party container holding the Docker socket.
- **One-command install** (`docker/install.sh`): Docker, clone to `/opt/blocksouls`, `.env` with a generated RCON
  password, `ufw` (22 + 25565), cron, start. Re-runnable.
- **Third-party mods:** Fabric API is required; Mod Menu, Sodium and Lithium are optional and skipped with a warning
  if they lack a 26.3 build. GeckoLib, Cloth Config and Iris are dropped (unused). A `modpack` CI job resolves them on
  every push.
- **Releases** also attach a server-pack zip (our jars + a README) and, once the owner adds `MODRINTH_TOKEN` and
  `MODRINTH_PROJECT_ID`, publish the `.mrpack` as a new Modrinth version (beta while 0.x).
- **Version check on join** (common): the server sends its BlockSouls version; a client on a different version gets a
  chat line telling them to update the pack.

## Party scaling, 1–8 players (2026-10-11, from the owner)

- **Target party size is 1–8 players** (owner). One shared curve in `common` (`PartyScaling`, `party.json`) replaces the
  per-module formulas: `1 + perExtraPlayer · (players − 1)^exponent`, so solo is 1 and each extra player adds a bit less.
  Parties above 8 count as 8.
- **Defaults:** wave size ×1.55 / ×2.5 / ×4.2 at 2 / 4 / 8 players (was `0.6 + 0.4·players`, ×3.8 at 8); mobs alive at
  once `12 + 5·players`, max 60 (was a flat 30); elites per wave ×1.35 / ×1.9 / ×3 (was not scaled); boss health
  ×1.5 / ×2.3 / ×3.9 (was +50% per player, ×4.5 at 8); field bosses' daily chance ×1.7 at 8; final-raid plates
  `ceil(players / 2)`, 1–4 (was at most 2).
- **Rule for all future work** (CLAUDE.md, worker protocol): anything that scales with player count goes through
  `PartyScaling` and is tested at 1 and 8 players.
- **Hosting:** 8 spread-out players means many loaded areas; HOSTING.md recommends the 16 GB VPS tier for 6–8 players.
- **Final raid** (after #21): plates to hold `ceil(players / 2)` (1–4; was min(players, 2)), one spare placed (the End
  arena now has 5 spots); crystals 3 × a `raidCrystals` curve (0.3, 0.9): 3 / 4 / 5 / 8 at 1 / 2 / 4 / 8 (was +1 per
  player); wave 7 × the wave curve (29 at 8; was 4 + 3 per player). `maxScaledPlayers` is gone: party.json's `maxParty`.

## v2 direction — "Expedition" (2026-10-11, from the owner)

Ideation pass for the next version, planned in `PLAN2.md`. v1 ships first.

**Owner decisions:**
- v2 adds: a tag-based synergy engine with keystone boons; an AI Director for sieges; risk-reward world
  objects (altars, cursed chests, pylons, wagers); the day phase as a shop/prep phase with camp
  shopkeepers; optional objectives inside sieges; Origins (run-start classes — scope generous);
  solo Last Stand + co-op cross-player boons and downed gameplay; a major boss expansion (posture/stagger,
  part breaks, new attack/movement archetypes, elite affixes); Tier 1–2 AI (utility attack picking,
  Director, influence maps, habit reads). Learned-model AI (ONNX policies, JEPA-style) is explicitly
  deferred past v2.
- **The run becomes act-structured**: Overworld → Nether → a new dimension → a sky dimension → final raid,
  each act ~3 sieges with prep time, portal-gated, strongholds/Ender-Eye hunt removed, shopkeepers at a
  camp per act.

**Confirmed by the owner (same day):** acts are **open dimensions, not closed maps** (Risk of Rain-style:
explicit stage gate, the party chooses when to leave). Rules in PLAN2.md §2: the portal never pulls;
two-way portals with the siege front following the party; camps as landmarks not walls; portal-charge
defense to leave an act; **Classic mode config flag** keeps the v1 run shape for A/B playtesting.

Owner refinements over the first draft:
- **No fixed siege count per act** ("3 waves is worthless"). An act lasts as long as the party stays;
  the portal charge can be started at any time. Greed vs. safety is the core rule: the threat counter
  (the v1 wave counter, promoted) climbs every night forever, and **every boss ahead — final raid
  included — scales with it at engage time** (`threat.json`, multiplied with `PartyScaling`). Staying =
  stronger build vs. harder bosses; rushing = the reverse; both legitimate.
- **Day/night rhythm in every dimension**, not horn-timer sieges: dimensions without a sun get a
  synthetic cycle — a "vibe switch" (ambient light/fog lerp, soundscape, aggression) on the same
  ~17-minute clock, so v1's dusk-siege/dawn-pause rules apply unchanged everywhere. `SiegeClock` has a
  sun-bound and a synthetic implementation.

Orchestrator notes:
- 26.3's "new biome" is the Dappled Forest (an overworld forest biome, not a dimension), and vanilla has
  no aether — so acts 3–4 are **our own custom dimensions** with original names (no "Aether" per the
  asset-name rule).

**Round 3 (same day, from the owner):**
- **Dimension names confirmed: the Blightheart and the Skyrift.**
- **Movement becomes class identity** (origins): you pick where you start — Mario kit, web/climb,
  aquatic, fire/lava-immune, or glide-flight — instead of everyone having everything. Orchestrator
  guardrail (to confirm by playtest, logged in PLAN2.md §3f): the Souls core (roll + i-frames, stamina,
  downed/revive) and the hold-jump feel stay universal; capability set contract in `common`, gating in
  `movement`; Classic mode grants all (v1 behavior); every origin solo-completable.
- **Boons/relics deepened, "Slay the Spire × Hades"**: boon levels (duplicate pick upgrades), skip-for-
  souls, **curses** priced into greed objects (purgeable at the shopkeeper / a free altar cleanse), and
  **trinkets** — a 3-slot StS-relic-style collection that survives going down (field bosses offer a
  pick-1-of-3). The dawn drop keeps the name "relic"; the carried items are "trinkets".
- **Coordination:** before v2 spine work starts, confirm with the v1 detailing/sound session that its PRs
  are merged or don't overlap `game`/`movement`/`common` (message sent 2026-10-11; awaiting reply).

## v2 Stage 5 kickoff + coordination outcome (2026-10-11, orchestrator)

From the v1 orchestrator session (relayed, not owner-direct): v0.9.0 is released (tag = d37702b; the
friends trial build); Stage 4 / v0.10.0 is in flight (claude/audio, boon-icons, kart-fuel-horn,
arena-seal, world-structures). Agreed split:
- **v2's batches are Stages 5–7** (renamed from PLAN2's "Waves 4–6" — v0.10's batch already uses
  "Stage 4" per docs/sessions/README.md).
- **Stage 5 (spine) starts now from current main**: nothing in Stage 4 touches `game/run`, the wave
  scheduler, or existing `common` contracts. v2 sessions keep off `game/player/boon/**`, the game client
  HUD, `bosses/`, `world/`, `vehicles/`, `mobs/` and `sounds.json` until v0.10.0 merges. **Stage 6 waits
  for v0.10.0** (real collisions). `common` additions go in their own small PR first, as ever.
- **PR ownership:** per the v1 orchestrator, the owner wants v2 PRs opened and merged by the v2 side,
  v0.10's by the v1 side, neither merging the other's. Owner to confirm (relayed second-hand).
- **Classic-by-default rule (orchestrator):** the Stage 5 spine merges with `runShape: "classic"` as the
  default and must be behavior-neutral for the live pack; Expedition becomes the default at `v2.0.0`
  (Stage 7). `integration/FullPackClientTest`'s dusk step is the regression gate.
- Stage 5 brief: `docs/sessions/v2-spine.md` (branch `claude/v2-spine`, two PRs: common contracts, then
  the game spine).

## Content standards adopted for v2 (2026-10-11, orchestrator)

The owner (via the v1 orchestrator; verified against branch `claude/content-standards`, PR #35) set
content standards for both tracks: `docs/CONTENT.md` + a CI-enforced crafting web
(`tools/content/check_recipe_web.py`: R1 every drop feeds 2+ recipes, R2 every crafted item connects,
end items exempt with a reason). Binding once #35 merges. v2 resolutions, logged here as the tie-breaker:
- **Expedition vs. "vanilla-shaped progression":** CONTENT.md's crafting-web goal line says
  "overworld → Nether → stronghold → End". That describes the Classic run shape; in Expedition the act
  portal chain replaces the stronghold step. The crafting rules themselves (R1/R2) apply unchanged to
  all v2 content. Nit reported to the v1 orchestrator for a wording tweak in #35.
- **Ender Eyes:** field bosses keep dropping them (Classic needs them); in Expedition they're crafting
  material only (must keep satisfying R1 — they already feed boss-tier recipes).
- **Souls** (boss currency) will satisfy R1 with 2+ soul-infused recipes rather than an exemption.
- **Trinkets** are end items → `recipe_web_exempt.json` with reasons, like gear.
- **Release coordination** accepted: v1 orchestrator cuts v0.10.x; v2 versions are cut by us only after
  telling them, never both from main at once.

## Playtest verdict + next-build focus (2026-10-12, from the owner)

The owner playtested the v0.9-era build. Feedback triaged in `docs/playtest/2026-10-12-owner-feedback.md`
(discoverability, drops, resources, movement feel → mostly v1-polish lane, routed to the v1 orchestrator).

Direction for the next build:
- **Focus: cooler guns, abilities and boons** — the build/fun content leads. PLAN2 §3j adds **exotics**
  (one rule-breaking gun per archetype), a **gun feel pass**, and a brand-new **`abilities` module**
  (Destiny-style tactical / origin signature / kill-charged ultimate kit, synergy-tagged, contracts via
  `common`). Stage 6 is split 6A (builds & boons, guns & abilities, Director & economy, origins &
  players) / 6B (bosses v2, new bosses, mobs v2, dimensions); 6A launches first, all of 6B still ships.
- **Pacing is now a design goal** ("remarkably terribly slow"): PLAN2 §0 sets the budget — no stretch
  over ~90 s without a decision or threat, core loop (gun in hand, shooting at something that matters)
  within 10 minutes of a fresh run. The Director owns the budget in v2; immediate v1 tuning is in the
  polish batch with the v1 orchestrator.
- **v0.10.1 (v1 orchestrator's PR #42) is a pacing-only patch**: 12-min days, waves/night
  2,2,2,3,3,3,3,4, 8 s breaks, drop chance 0.5, starter pistol to wave 4. It introduces
  **`defaultsVersion` + `RunConfig.migrate` in game-run.json**: shipped-default changes migrate on live
  servers while hand-tuned values stay. **Rule for all v2 work**: any change to a shipped config default
  (game-run.json above all — SiegeClock lives there) uses the same migrate pattern, and the
  `defaultsVersion` field is never dropped.

## Stage 6A launch (2026-10-12, orchestrator)

v0.10.0 is released; Stage 6A starts. Briefs: `docs/sessions/v2-builds.md`, `v2-guns-abilities.md`,
`v2-players.md`, `v2-director.md`.
- **Three sessions launch before the spine PR (#40) merges**: builds, guns-abilities and players depend
  only on the merged #37 contracts (SynergyTags, Capabilities, Threat) and never touch `game/run/**`.
  **v2-director waits for #40**, since it edits the spine's scheduler files. This bends the "previous
  stage merged first" rule only where there is no code dependency; #40's remaining fix is a test-only
  change already in CI.
- Lane split inside 6A: `game/client/hud` is shared file-by-file (builds: build line + trinket row;
  players: origin/summary screens, downed indicator); `common` additions each in their own first PR;
  Souls' two recipe-web recipes split between builds and guns.
- Launch order honors the owner's focus: fun content (builds, guns & abilities) and origins first;
  the Director (pacing) the moment its dependency merges.
- **Stage 5 complete (2026-10-12):** #37 (common contracts) and #40 (spine) merged, both
  behavior-neutral for the live pack (`runShape: "classic"` default). Spine follow-ups after v1's #41/#42
  land: RELOADED wiring for SiegeClock, migrate pattern for the new game-run.json fields. **v2-director
  launches** once v0.11's `drops-and-pacing` is also in main (it edits the same `game/run` files).

## Stage 6A common-contract reconciliation (2026-10-12, orchestrator)

The three 6A sessions opened their `common` contract PRs in parallel (#46 builds, #47 guns-abilities,
#48 players) and independently invented overlapping event classes. Canonical picks, to minimize rework:
- **`PlayerMoveEvents`** (#48: `ROLLED`, `GLIDE_STARTED`) is the one movement-event class; #46's and #47's
  `MovementEvents` are dropped.
- **`CombatEvents`** (#46: `HIT(shooter, target, damage, precision, killed)`, `RELOADED(player, rounds)`) is
  the one combat-event class; #47's `PRECISION_HIT` is `HIT` with `precision == true`.
- `Origins` provider (#47) is the origin-id lookup; the game module installs it when origins land.
- `PlayerStatus.canShoot` (#48) is what guns gate firing on (downed players may keep a pistol in v2).
- `Capabilities` default set becomes **the v1 verbs** (climb, swing, multi-jump), not "everything": GLIDE
  never existed in v1, so all-granted players must not start gliding. `grant()` must materialize that set
  when adding to a default player (bug found in review, fixed in #48).
- Merge order: #48 → #46 → #47 (#47 depends on #46's `CombatEvents`).
- **Lesson for Stage 6B briefs:** when several parallel sessions each add `common` contracts, the
  orchestrator pre-declares shared event/provider names in the briefs, or one session owns all contract
  additions for the stage.

Operational note: all four v2 workers stalled at the same account usage limit (five-hour window) within
~20 minutes of launch, with uncommitted work staged. Workers are now told to commit WIP before long
operations; the orchestrator re-arms a roster check-in just after each reset.

## The Floor Loop — v2 pivots from open-world acts to floors (2026-10-12, from the owner)

After the v0.9 playtest the owner weighed a custom engine (Slay the Spire 2-style). Orchestrator's
recommendation, accepted: **stay on Minecraft as the engine, delete the survival loop** — validate the
roguelike design with friends in weeks; a cost-boxed engine spike (Godot 4) runs alongside only if asked.

Owner's design, now PLAN2 §0–§2: six small island floors in a void dimension (glass edge), each a
5–10-minute day of optional things to do around a **central shopkeeper** (resources as surface nodes and
one shallow cave, a structure, a rotating mini-boss, one secret per floor (Gungeon), one random event
per floor (StS "?"), a cave dungeon, a wreck repaired with scrap lying around it, day mobs), then the
siege, then the exit portal. **No recipe learning**: the trade board shows prices as icons of where
ingredients come from. **The horn** ends the day early for a buff (core risk-reward). Floors escalate
subtly with floor number. **Four classes** (Warden / Striker / Arcanist / Ranger, Destiny mold) with
permanent **rune trees** (meta-progression) replace the ten origins; the universal v1 movement kit stays
on for everyone with a class signature on top. Getting strong is easy and fast; threat makes it matter.

Orchestrator defaults: the open-dimension "expedition" act model is superseded (its spine is reused: act
= floor, portal charge = exit portal, synthetic clock = floor timer); the Dimensions session becomes
**Floors** (6A); the Director takes the day timer, horn, events, mini-boss rotation and loot cadence;
classic mode stays the live default until v2 ships. Per-floor designs (2–6) are discussed with the owner
before their template pools are built; Floor 1 (Surface) and the template system go first.

## Worker sessions run on Opus, not Fable (2026-10-12, from the owner)

Fable sub-sessions consumed the account's five-hour and weekly usage limits within hours of launch. **Rule:
every new worker session is created with the Opus model (`claude-opus-5-5`); Fable is not used for
sub-sessions/sub-agents.** Existing Fable workers finish only the PR they already have open, then stand
down; remaining work in their briefs moves to Opus sessions that pick up from the branch, the brief and the
session's decisions file. Slower per session; the orchestrator keeps the parallel count modest (owner:
"chug on slow opus"). The v1 orchestrator was told the same rule for its v0.11 sessions.

## Pacing patch, v0.10.1 (2026-10-12, from the owner's playtest)

- The owner found the run "remarkably terribly slow". Defaults: day/night 17 → **12 min**, waves per night
  1,1,2,2,2,2,2,3 → **2,2,2,3,3,3,3,4**, wave breaks 20 → **8 s**, wave-kill drop chance 0.35 → **0.5**, Starter
  Pistol lasts to wave 3 → **4** (more waves per night).
- **Existing servers get it too:** `game-run.json` gains `defaultsVersion`; on load, values still at the old default
  move to the new one and hand-tuned values are kept (`RunConfig.migrate`). Use this pattern whenever a default
  changes after release.
- Drop guarantees, a cheaper first gun and resource density follow in v0.11 (`drops-and-pacing`, `resources`).

## v0.11.0 (2026-10-13, orchestrators)

- v0.11.0 ships the abilities kit and synergy builds live under runShape classic (owner priority: guns, abilities, boons).
- It also ships exotic guns and the gun feel pass (#54), and the four classes with rune trees, glide and Last Stand (#55).
  Players without a class keep every v1 movement verb, so classic play is unchanged until someone picks a class.

## Floor rosters in the wave manager (2026-10-13, v2 orchestrator)

- **Each wave spawn resolves its roster from the spawn position** (`FloorRoster`): the placed floor under it gives
  its number and tags to `WaveMobs.eligible(wave, floor, tags)`; outside any floor in a multi-act run the current
  act's number stands in with no tags (the same fallback the Director's affix roll uses); classic runs keep
  `eligible(wave)` untouched. Under `more_elites`, elites also skip the per-run roster shuffle's wave offset.
