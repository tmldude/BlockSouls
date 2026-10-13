# Session: v2-boards (Stage 6C, wave A)

**Modules:** `game` (`run/director/Shop*`, `DirectorConfig.Shop`) + `common` (additions only) + `world` (the
`Shopkeeper` entity only if a hook is missing) + `tools/content/check_recipe_web.py`. **Branch:** `claude/v2-boards`.
**PRs, in order:** `common: board recipe contract` (small, first) → `game: forge + brew boards, crafting tables off`.

Read `docs/sessions/README.md` (protocol), `docs/design/floors.md` (the owner's decisions D1 and D3 are your spec),
DECISIONS.md "Floors 2–6 first draft", `docs/decisions/v2-director.md` (the trade board you extend:
`game/run/director/Shop.java`, offers in `DirectorConfig.Shop`), `docs/decisions/crafting-web.md` and `docs/CONTENT.md`
(the recipe rules you must keep green).

## You own
- **Board recipes.** Every BlockSouls recipe becomes craftable only at the shopkeeper. Preferred shape: a recipe
  type the player's crafting grids never match (e.g. `blocksouls_common:forge` / `blocksouls_common:brew`
  serializers that wrap shaped/shapeless ingredients and a result), with existing recipe JSON migrated
  mechanically (a script in `tools/content/`, run in CI or committed output; your call, log it). Gear recipes
  (armor, gun parts) go to the forge board, consumables (food, tonics, ammo if any) to the brew board. The checker
  `check_recipe_web.py` must read the new types so R1–R3 still hold; keep `python3 tools/content/check_recipe_web.py`
  green.
- **Crafting tables off** in floors runs (`runShape == "floors"`): using a crafting table shows a short message and
  opens nothing; the 2×2 grid stays vanilla-only by construction. Classic runs unchanged.
- **The shopkeeper's three boards:** trade (the existing offer pool), **forge**, **brew**. Switch between them on the
  board's bottom row. A forge/brew entry shows the result, its ingredients as "where to get it" icons (reuse
  `DirectorConfig.Shop.sources`; extend it with the floor rares/herbs as floor sessions add them: they will add
  entries in their own PRs, so make `sources` datapack-mergeable or reloadable), and crafts on click when the
  player holds the ingredients. Entries are filtered by floor (`minFloor`/`maxFloor`) and optional floor tags
  (`floorTags`, matched against `PlacedFloor.tags()` via `Floors.at`), so a floor's rare shows only on that floor.
- **Forge services:** keep perk reroll; add **enchant** (pay the floor's metal + souls → one random enchantment on
  the held gear piece, from a per-floor list in config) and **upgrade** (two gear pieces of the same tier → one of
  the next, the §3d forge ritual for armor). All prices config.
- **Contract additions in `common`:** whatever the floor sessions need to register board entries from data without
  importing `game` (ids in `CommonIds`, a `CommonTags` item tag per board if you use tags). List them in the PR.
- **Debug:** `/bs shop open <trade|forge|brew>`, `/bs shop give-ingredients <recipe>`.
- **Tests:** unit tests for entry filtering (floor, tags, 1 and 8 players if anything scales) and recipe matching;
  a gametest that opens the brew board and crafts one consumable; the recipe-web check in CI.
- `docs/decisions/v2-boards.md`.

## Not yours
Floor content (templates, nodes, herbs, mobs): the floor sessions. The horn, events, mini-boss: the Director.
Don't change the trade board's existing offers beyond moving them under the "trade" tab.

## Done when
Crafting tables do nothing in a floors run; every BlockSouls recipe is listed on the forge or brew board and crafts
there; the recipe-web check and CI are green; floor sessions can add board entries and sources from data in their
own module without touching `game` Java. Say so in the PR and stop.
