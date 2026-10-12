# Crafting web (Stage 4, 2026-10-11)

Closes the three `debt` entries in `tools/content/recipe_web_exempt.json` so every drop feeds 2+ recipes
(`docs/CONTENT.md`, "Crafting"). Data only: no new items, no code outside the integration test.

## Ember Barrel (Ashwyrm, 1 per kill) → 2 recipes
- **Ember Hand Cannon** (`blocksouls_guns:ember_hand_cannon`): `ER / SG` = Ember Barrel, Gun Receiver, Ashwyrm Scale,
  Gunmetal Ingot. Result is a plain `hand_cannon` with components preset: `blocksouls_guns:rarity` = `rare`,
  `blocksouls_guns:perks` = `[explosive_rounds]`, `minecraft:rarity` = `rare`. Because the rarity component is set,
  the gun's first inventory tick doesn't re-roll it: the boss material buys a **guaranteed** Explosive Rounds perk
  instead of the crafted-odds roll. Rare (one perk), not Legendary, so loot Legendaries still have a ceiling above it.
- **Ember Shotgun** (`ember_shotgun`): `ERK / S` = Ember Barrel, Gun Receiver, Gun Stock, Ashwyrm Scale. Same
  components on a `shotgun`. The Ember Barrel stands in for the regular shotgun's two barrels.
- Both live in `guns` (owner of the result) with `fabric:all_mods_loaded` on `blocksouls_bosses` (and `blocksouls_world`
  for the hand cannon's ingot). One Ashwyrm kill gives one Ember Barrel, so it's one ember gun per kill.

## Ashwyrm Scale (Ashwyrm, 4–6 per kill) → 7 recipes
- **Burn-resistant Blight-Forged armor** (`blocksouls_world:blight_forged_<piece>_ashwyrm_scaled`): smithing transform,
  template = Ashwyrm Scale, base = the Blight-Forged piece, addition = Magma Cream. Result is the same piece (so the
  Blight-Forged thorns set bonus and repair still apply; smithing keeps enchantments, trims and damage) with
  `minecraft:attribute_modifiers` replaced by the piece's normal armor / toughness / knockback modifiers (same
  `minecraft:armor.<piece>` ids and values as `WorldArmor.BLIGHT_FORGED`) plus **−20% `burning_time`** per piece
  (`add_multiplied_base`, so a full set burns for 20% as long). It counts on the boss-tier gear the player already
  has, and fits fighting a fire wyrm. One scale per piece: a 4–6 scale kill covers the set.
  - If `WorldArmor.BLIGHT_FORGED` defense/toughness ever change, these four recipes must change with it
    (`ashwyrmScaleSmithsBurnResistantArmor` checks the upgraded piece keeps every base modifier).
  - Re-smithing an upgraded piece is allowed and harmless (it just costs another scale).
- **Ember gun** recipes above (1 scale each).
- **Bulk Special Ammo** (`blocksouls_guns:special_ammo_from_ashwyrm_scale`): `PSP / NNN` = 2 gunpowder, 1 scale,
  3 gunmetal nuggets → 12 Special Ammo (the base recipe gives 6 for an ingot + iron + 2 gunpowder). It's the sink for
  spare scales. Not a separate incendiary ammo type: guns has two ammo slots (`primary` / `special`) and adding a third
  is gun code, outside a data-only session.

## Gunmetal Nugget (wreck / wave loot) → 3 recipes
- Gunmetal Ingot (existing, 9 → 1).
- **Special Ammo from nuggets** (`special_ammo_from_nuggets`): `NNN / P` = 3 nuggets + 1 gunpowder → 2 Special Ammo.
  The cheap early use: nuggets show up long before ore is mined, and 9 nuggets (one ingot's worth) + 3 gunpowder gives
  6, the same yield as the ingot recipe minus its iron.
- The bulk scale recipe above.
- Kart parts were the other idea, but `vehicles/` belongs to another session.

## Audit
- `check_recipe_web.py` passes with an empty `debt`; R2 found nothing else.
- Relics (`blocksouls_game:relic_*`) drop from game code, not a loot table, so the check can't see them as drops.
  They're consumables (spent on a card pick), so they're listed under `exempt` with that reason in case a loot table
  ever names them.
