# Content standards

How BlockSouls content is made, for every session and every version (v1 polish and v2 "Expedition" alike). Read it
before adding an item, mob, boss, structure, dimension, sound or icon. Each section says **where it goes**, **how
it's made** and **how it's checked**. The tools are in `tools/`; CI runs the checks, so a missed step fails the build.

The general rules are in `CLAUDE.md` (module lanes, `common` contracts, original names and art, 1–8 player scaling via
`PartyScaling`, config for every number) and `docs/sessions/README.md` (the session protocol). This file covers the
content itself.

## Every new thing, whatever it is

1. **Original.** Name, look and sound are ours. No trademarked names or third-party assets (`CLAUDE.md`, "Assets").
   Inspired-by is fine ("a Souls-style knight"); a specific franchise character is not.
2. **Generated, not hand-painted.** Art and audio come from scripts in `tools/assets/` so they're reproducible and
   tweakable. Extend a script or add one; commit both the script and its output.
3. **Named and readable.** Every item, block, entity, effect and enchantment has an `en_us.json` entry. Tooltips state
   mechanics only, no lore text (show, don't tell).
4. **Tunable.** Every number lives in the module's config class (`Config.load("<file>", ...)` → `config/blocksouls/`).
   Player-count scaling goes through `PartyScaling` and is tested at 1 and 8 players.
5. **Debuggable.** A `/bs ...` subcommand spawns, gives or triggers it (`DebugCommands.register`).
6. **Tested.** Unit tests for pure logic, a server gametest for in-world behavior, and if it's visible, a line in the
   full-pack client test (below).
7. **Logged.** Defaults you picked go in your `docs/decisions/<session>.md`.

## Items and blocks

- **Where:** the owning module registers it; if another module needs the id, add a constant to `CommonIds` (additive
  only). Lang files live in `src/client/resources/assets/<ns>/lang/en_us.json` for modules with a client source set
  (`mobs` and `bosses` keep theirs in `src/main/resources`).
- **Files:** texture `assets/<ns>/textures/item|block/<id>.png`, item model `models/item/<id>.json`, item definition
  `items/<id>.json` (26.3), blockstate + block model for blocks, loot table `loot_table/blocks/<id>.json` for blocks.
- **Art:** `tools/assets/art.py` (items, blocks, armor, ammo, parts), `guns.py` (3D gun models). Rules and palettes
  are in `tools/assets/STYLE.md` (16×16, top-left light, sel-out outlines, one palette per theme). `gen.py` is only for
  placeholders. Re-run `art.py --sheet docs/art/items-sheet.png` and check the contact sheet.
- **Creative tab:** add it to the module's tab in a sensible order.

## Crafting: every item belongs to the web

The goal: nothing is a dead end, drops always matter, and progression stays tiered (overworld → Nether → End in
Classic, the act chain in v2's Expedition; new tiers slot into existing ones).

- **R1: every drop is an ingredient in at least 2 recipes.** Any BlockSouls item that drops from a mob, boss, chest,
  ore or wave loot must be an ingredient in two or more recipes (different results), so picking it up always means
  something.
- **R2: every crafted item connects.** A recipe uses at least one BlockSouls material, or its result is itself an
  ingredient elsewhere. No recipes that just turn vanilla items into an isolated new item.
- **End items are the exception, with a reason.** Gear (guns, armor, tools), consumables (ammo), unique boss items
  (whistles, trophies) and spawn eggs may be dead ends. List each in `tools/content/recipe_web_exempt.json` under
  `exempt` with a one-line reason.
- **Checked by CI:** `python3 tools/content/check_recipe_web.py` fails on a violation. Run it before pushing.
  `debt` in the exempt file lists known violations being fixed; it must only shrink.
- **Format (26.3):** recipe ingredients are plain id strings or `#tag` strings; results are `{"id": ..., "count": n}`.
  A recipe that uses **another module's** item needs `"fabric:load_conditions": [{"condition": "fabric:all_mods_loaded",
  "values": ["blocksouls_<module>"]}]`, because one unknown id fails the whole recipe registry. Tag entries from
  other modules use `{"id": ..., "required": false}`.
- **Design habits:** new materials get a smelting/compacting path where it makes sense (ore → raw → ingot ↔ block,
  nuggets ↔ ingot), one gear use and one utility use. Boss materials feed boss-tier upgrades (smithing is a good
  fit). Register cross-module recipes in the module that owns the **result**.

## Loot

- **Format (26.3):** pools, entries and tables take one `"modifier"` (a function object keyed by `"type"`, or a list)
  and one `"condition"` (`minecraft:all_of` for several). The old `functions` / `conditions` / `function` keys are
  silently ignored.
- **Injection:** vanilla tables get our items through `world`'s inject tables (`loot_table/inject/*`), not overrides.
- **Guns:** roll gear with `{"type": "blocksouls_guns:roll_perks", "tier": "common|rare|legendary"}`.
- **Bosses:** each field boss drops 2 Ender Eyes plus its own materials (which must satisfy R1).
- Every drop you add must pass the crafting check above.

## Mobs and enemies

- **Where:** `mobs` (wave roster, mutants, dimension mobs). Spawned by other modules through `common`'s `WaveMobs`
  (register a `WaveMobSpec`: id, first wave, weight, elite flag, factory). Nothing outside `mobs` imports mob classes.
- **Behavior rules:** downed players are never targets (`PlayerStatus.isDowned`); ordinary wave mobs burn at dawn,
  elites don't; block breaking only through `#blocksouls:wave_breakable`; counts and elites come from `PartyScaling`.
- **Art and sound, one pipeline:** add the mob to the geometry spec and run, in order, `mob_models.py` →
  `mobs.py` → `mob_preview.py` → `mob_sounds.py`, then `check_mob_assets.py` (CI runs it). Models are original
  cuboid geometry, skins painted on the generated UV layout, sounds synthesized (no samples).
- **Loot:** a loot table per mob (26.3 format); materials obey R1.
- **Tests:** a gametest that spawns it through `WaveMobs` and checks one signature behavior; add it to
  `integration`'s `FullPackGameTests` roster lists and the client test's roster line-up.

## Bosses

Follow `bosses/README.md` ("how to add a boss"): config, attacks, entity + definition, registration (one line in
the shared inits), model and animations, arena, loot, field spawn rule, tests.

- **Readability rule:** anything that can deal more than 30% of a player's health needs at least 15 ticks of windup
  (`BossScaling.readable`, config-locked). Telegraphs never lie.
- **Scaling:** health from `PartyScaling.bossHealth` via the framework; mechanics that need several players use
  `PartyScaling` curves (see the final raid's plates and crystals).
- **Model:** a custom cuboid model per boss (no vanilla bodies), painted by a script in `tools/assets/`.
- **Sound and music:** its own synthesized SFX and a music loop (see Sounds).
- **Client test:** add the boss id to the `bosses` array in `integration`'s `FullPackClientTest` so CI screenshots it.

## Maps, structures and dimensions

- **Structures** are registered structures (structure type + `structure` + `structure_set` + biome tags), so
  `/locate structure` and `/bs world locate` find them. Loot chests use our loot tables; anything that spawns with
  the structure (the kart wreck) is placed by the structure's own code.
- **Features** (ore veins, scrap piles, blight patches) are configured + placed features with biome modifications.
- **Dimensions** (v2): dimension type + dimension JSON + worldgen; portals and per-dimension rules go through
  `common` events so `game` can react (waves, sleep, siege) without importing `world`.
- **Look:** sky/fog colors through 26.3 environment attributes (`EnvironmentAttributes.FOG_COLOR` / `SKY_COLOR`),
  as `world`'s Nether ash fog does.
- **Tests:** a gametest that places the structure and checks its chest loot; the client test can teleport there for
  a screenshot.

## Sounds and music

- **All original, synthesized** by scripts in `tools/assets/` (`mob_sounds.py` and the v0.10 audio scripts):
  numpy synthesis → ffmpeg/libvorbis `.ogg`. No samples, no vanilla sound references for our events.
- Mono for positional SFX, stereo allowed for music (`"stream": true`); 2–3 variants where vanilla uses variants;
  never louder than vanilla thunder.
- Register the event in the module's sounds class and `sounds.json`; the audio checker fails on a missing file or
  an event without an entry.

## Icons and GUI

- Item icons: `art.py` (STYLE.md rules). Boon icons: the boon icon generator (v0.10 `boon-icons`) draws a 16×16
  sprite per boon with a tier frame; new boons get one by adding a row to it. GUI sprites go in
  `textures/gui/sprites` and are drawn with `blitSprite`.
- HUD elements: one renderer per module, config toggles, and they must not overlap vanilla's hotbar/health.

## Tests and CI

- `./gradlew build` (or `tools/offline-check/typecheck.sh` + `runtests.sh` without Fabric's maven) before pushing.
- CI runs: asset checks, the crafting check, unit tests, every module's server gametests, the full-pack gametests
  (`integration`) and the **full-pack client test** (a real client on a dedicated server; screenshots).
- Screenshots of every CI run are pushed to the branch `ci-screenshots/<branch-with-slashes-as-dashes>`; look at
  them when you change anything visible.
- Cross-module ids you add go in `FullPackGameTests.crossModuleIdsResolve`.

## Releases

- Bump `mod_version` in `gradle.properties` (and `modpack/pack.toml`), merge to `main`, then run the `release`
  workflow on `main` (Actions tab or API, `workflow_dispatch`); it tags `v<mod_version>`. It publishes the GitHub
  release (jars, `.mrpack`, server zip), the GHCR server image, and the Modrinth version (environment set to client
  and server).
- On Modrinth, our jars inside the pack need a "My project" attribution per release (MIT, link the repo).
- Store art: `tools/assets/promo.py` (icon + gallery).
