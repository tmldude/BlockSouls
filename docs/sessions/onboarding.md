# Session: onboarding (v0.11, playtest fixes)

**Lanes:** `data/*/advancement/**` in every module, `vehicles/` (hints and tooltips), `modpack/`. **Branch:**
`claude/onboarding`. **PR title:** `content: recipe book, tutorial advancements, recipe viewer`.

The owner's playtest (`docs/playtest/2026-10-12-owner-feedback.md`, item 1): "I don't know how to make anything / the
guns" and "couldn't repair a car". That's the #1 blocker. Read `docs/CONTENT.md` first.

## Do
- **Recipe viewer in the pack:** add EMI to `modpack/` (`add-mods.sh` / a `.pw.toml`) if a Fabric 26.3 build exists
  on Modrinth, else REI, else skip and log why. Client-side, optional on the server. CI's `modpack` job proves it
  resolves.
- **Recipe book:** every BlockSouls recipe gets a vanilla recipe-unlock advancement
  (`data/<ns>/advancement/recipes/<category>/<recipe>.json`, `minecraft:inventory_changed` on its key ingredients plus
  `minecraft:recipe_unlocked`, reward `recipes`), so recipes appear in the crafting book as soon as you hold an
  ingredient. Cross-module ones need the same `fabric:load_conditions` as their recipe. Write a small generator
  (`tools/content/gen_recipe_advancements.py`) so new recipes get one, and a check in
  `tools/content/check_recipe_web.py` (or next to it) that fails when a recipe has no unlock advancement.
- **Tutorial advancements** (show, don't tell; one tab, "BlockSouls"): one short chain per loop, each description
  saying *how*, in mechanics:
  - scrap → gunmetal ingot → first gun part → first gun;
  - find a kart wreck → fit wheels/engine (with their real recipe) → patch with scrap → drive;
  - survive the first night, first boon, first field boss, the final raid.
  
  Put them in the module that owns each trigger item/entity (with load conditions where they reference another module).
  Use vanilla criteria where possible. Add a small `common` event only if a trigger has no vanilla criterion, listed
  under "Contract additions".
- **Kart wreck hints** (`vehicles`): while a player holds a wheel, engine or repair scrap near a wreck, the wreck shows
  a gentle particle cue plus an action-bar line naming what it still needs ("Needs: 2 Wheels, Engine").
  Tooltips on wheel, engine and fuel can say what they're for.

## Tests
- The recipe-advancement check runs in CI.
- Gametests:
  - a player given scrap unlocks the gunmetal recipes;
  - the wreck reports what it needs;
  - the tutorial chain's first step grants.

## Done when
CI green; the client test still passes; `docs/decisions/onboarding.md`.
