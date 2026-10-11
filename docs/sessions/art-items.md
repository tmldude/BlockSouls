# Session: art-items (Stage 2b)

**Modules:** `guns`, `world`, `vehicles`, `game` (assets only). **Branch:** `claude/art-items`.
**PR title:** `art: items, blocks, guns, armor`.

Replace the generated placeholder textures for items and blocks with directed, cohesive art, and give guns real
3D item models. Read `docs/sessions/README.md`, PLAN.md §0b (scavenged-apocalypse look, infected Nether) first.

## You own
`*/src/main/resources/assets/**/textures/{item,block}/**`, item/block model JSONs, equipment textures, gun item
models, the vehicle texture, relic/boon icons, and `tools/assets/` additions. **No Java changes** except asset
registration bugs you find (list them in the PR). Bosses and mobs are other sessions' art.

## Do
- **A coherent style guide first** (`tools/assets/STYLE.md`): palettes per theme (scrap/rust for scavenged, blight
  for the Nether infection, gunmetal for guns, ember/pale/storm for bosses), outline rules, light direction, 16×16
  for items/blocks (32×32 only for guns if it reads better).
- **Guns:** 3D JSON item models (cuboids: barrel, receiver, stock, mag, sight) with first/third-person display
  transforms that hold like guns; rarity tint not needed (tooltips carry rarity). Starter Pistol looks cheap and
  taped-together.
- **Blocks:** gunmetal ores (stone + deepslate), scrap pile, blighted netherrack (fleshy, animated `.mcmeta` pulse),
  blight growth, blight heart (emissive).
- **Items:** scrap, gunmetal raw/ingot/nugget, blight core, ammo (Primary/Special), gun parts, wheel, engine, relic
  (per tier), boon icons (if the game uses item icons, keep ids).
- **Armor:** scrap-plate and blight-forged item icons + equipment layer textures.
- Hand-directed pixel art via PIL scripts (original only). Paths stay stable; no missing textures.

## Done when
CI green; a screenshot sheet (creative inventory tabs or a contact sheet PNG rendered from the textures) in the PR;
`docs/decisions/art-items.md`.
