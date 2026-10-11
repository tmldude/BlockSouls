# Session: crafting-web (Stage 4)

**Modules:** recipe and tag data in `guns`, `world`, `bosses` (data only, plus small code only if a recipe needs it).
**Branch:** `claude/crafting-web`. **PR title:** `content: every drop feeds 2+ recipes`.

Read `docs/CONTENT.md` ("Crafting") first. `python3 tools/content/check_recipe_web.py` lists three known gaps under
`debt` in `tools/content/recipe_web_exempt.json`:

- `blocksouls_bosses:ashwyrm_scale` (Ashwyrm drop, 4–6 per kill): in **no** recipe.
- `blocksouls_bosses:ember_barrel` (Ashwyrm drop, rare): in **no** recipe. Its intent (`docs/decisions/boss-ashwyrm.md`)
  was a gun part.
- `blocksouls_world:gunmetal_nugget` (wreck/wave loot): in **one** recipe.

## Do
- Give each at least **two** recipes with different results, fitting the progression (boss materials → boss-tier
  upgrades; nuggets → cheap gunmetal uses). Ideas, pick what plays best: Ashwyrm scale in a fire-resistant upgrade
  (smithing on scrap-plate/blight-forged armor) and in an incendiary ammo type; Ember Barrel as a gun upgrade (e.g. a
  hand cannon or shotgun crafted/smithed with it that carries the Explosive Rounds perk via the result's components,
  if `guns` stores perks as item components) and in a second ember item; gunmetal nuggets into ammo or kart parts.
- Recipes go in the module that owns the **result**, with `fabric:load_conditions` when they use another module's
  item. New items you need get art from `tools/assets/art.py` (STYLE.md) and lang entries.
- Move each fixed item out of `debt`; the check must pass with an empty `debt`.
- Run the check over everything once more and fix any R2 problem you find.

## You own
Recipes, recipe tags and any new small items for this, in `guns`, `world` and `bosses` data. Don't edit
`vehicles/` (the `kart-fuel-horn` session adds its Fuel Can recipe there), boss code, or anything the other Stage 4
sessions own.

## Tests
A full-pack gametest (in `integration`) that each new recipe loads (`RecipeManager.byKey`), like
`crossModuleIdsResolve`. The crafting check passes in CI.

## Done when
CI green; `debt` is empty; `docs/decisions/crafting-web.md`.
