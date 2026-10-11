# Session: boon-icons (Stage 4)

**Module:** `game` (boons). **Branch:** `claude/boon-icons`. **PR title:** `game: custom boon icons`.

Boons show a vanilla item as their icon (`"icon": "minecraft:blaze_powder"` in `data/*/boons/*.json`, drawn by
`BoonStyle` from `BuiltInRegistries.ITEM`). Give every boon its own original icon.

## You own
`game/src/main/resources/data/*/boons/**`, `game/src/main/resources/assets/blocksouls_game/textures/gui/boon/**`,
the boon classes in `game/src/main/java/.../player/boon/` and `game/src/client/java/.../client/hud/` (BoonStyle,
BoonRowHud, the boon pick screen/cards), and a generator in `tools/assets/` (e.g. `boon_icons.py`).

## What to make
- One **16×16 (drawn 2× in the HUD) original icon per boon** (22 today) plus the **Wildcard** card back, generated
  by a script so new boons get one easily. Tier reads at a glance: a frame or palette per tier (common / rare /
  legendary). The two combos (`afterimage`, `moon_boots`) get a visible "combo" mark.
- `icon` accepts either an item id (old behavior, keeps third-party/datapack boons working) or a sprite id
  (`blocksouls_game:boon/<name>`); `BoonCatalog` validates both. Sprites go in a GUI atlas (26.3: `textures/gui/sprites`
  + `guiSprite` / `GuiGraphicsExtractor.blitSprite`; check the vanilla source for the exact call).
- Update every boon JSON to its new sprite. Tooltips unchanged.

## Tests
Unit: the parser accepts both icon kinds and rejects a missing sprite. Gametest: every registered boon's icon
resolves. Screenshot: add a boon-row screenshot to `integration`'s client test (`/bs boon give` a few boons first;
this one shared-file edit is allowed, list it in the PR).

## Done when
CI green; the client test screenshot shows the new icons; `docs/decisions/boon-icons.md`.
