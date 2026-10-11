# Decisions: boon-icons

Defaults picked while giving every boon its own icon (`docs/sessions/boon-icons.md`).

## Art

- **Generated, not hand-painted:** `tools/assets/boon_icons.py` reads every `data/*/boons/*.json`, paints a 16×16
  sprite per boon plus the Wildcard card back, and rewrites each JSON's `"icon"` to `<ns>:boon/<name>`. Contact
  sheet: `docs/art/boon-icons-sheet.png`. A boon without a glyph in the script gets a generic rune and a warning,
  so adding a boon never ships a missing sprite.
- **Tier = frame + background:** common is scrap steel on charcoal, rare is steel blue on navy, legendary is gold
  on dark brown with bright corner studs. The Wildcard back is the void (purple) palette with a "?". All three read
  apart at 1× even in the HUD row, which also keeps its tier-colored outline.
- **Combo mark:** a magenta (`#E36BFF`, the HUD's combo color) diamond over the top-right corner of the frame.
- **Glyphs are 12×12** in the 14×14 interior, auto-shaded and sel-out outlined per `STYLE.md`. Glow highlights
  (arrows, iris) use the `TRITIUM` glow palette; wind/motion lines are a flat pale blue.
- **Size:** 16×16 sprites, drawn at 16 px in the HUD row (the 18 px slot left of the hotbar; 2× would push the
  row into the hotbar and chat) and at 2× (32 px) on the relic cards. The brief's "drawn 2×" is taken as the cards.

## Code

- **Sprite vs item:** an icon whose path starts with `boon/` is a GUI sprite (`textures/gui/sprites/boon/*.png`, in
  vanilla's `gui` atlas, drawn with `GuiGraphicsExtractor.blitSprite`); anything else is an item id drawn with
  `fakeItem` as before, so third-party and datapack boons keep working. `BoonIcons` holds the rule.
- **Sprites live in `src/main/resources`**, not `src/client`, so the server (and gametests) can check they exist.
  `BoonCatalog.spriteExists` looks the PNG up in every loaded mod's files. The server can't see client resource
  packs, so a datapack boon that wants a custom sprite needs a mod to ship it (or uses an item icon).
- **Missing sprite = boon skipped** at load (`BoonParser` throws, the catalog logs and skips it), same as any other
  malformed boon. An unknown *item* icon stays a warning (an isolated module run may lack the item).
- `BoonParser.parse(id, json)` without a sprite checker still accepts any sprite id; the catalog and the shipped-boon
  unit test always pass one.

## HUD row layout (review fix)

- The boon row used a fixed 6 per row and ran off the left edge on narrow GUIs (854×480 at GUI scale 2 started at
  x = −29). `BoonRowLayout` now fits as many 20 px columns (max 6) as there are between the offhand slot and a 2 px
  left margin, and wraps upward. If the rows would climb above y = 40 (below the run info), it adds columns with a
  tighter, overlapping step instead, so every icon stays on screen at any GUI scale. Unit-tested from 320×240 to
  1920×1080 with 0–24 boons.
- No other boon row exists: the `game` HUD's only top element is the centered run info.
