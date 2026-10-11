# Placeholder assets

`gen.py` makes deterministic, in-theme pixel art so no session waits on real art:

```sh
python3 tools/assets/gen.py <kind> <palette> <out.png> [--seed NAME]
# e.g.
python3 tools/assets/gen.py ore rad world/src/main/resources/assets/blocksouls_world/textures/block/uranium_ore.png
python3 tools/assets/gen.py skin blight mobs/src/client/resources/assets/blocksouls_mobs/textures/entity/screamer.png
```

- **kinds:** `block`, `ore`, `ingot`, `item`, `gun`, `helmet`, `chestplate`, `leggings`, `boots` (16×16),
  `skin` (64×64 humanoid layout, vanilla zombie UVs).
- **palettes** (pick by theme): `scrap`, `rust` (scavenged), `blight` (Nether infection), `rad` (radiation),
  `ember` (Furnace Knight), `pale` (Pale Rider), `storm` (Stormplate Knight), `ash`, `bone`, `steel`, `wood`.

Same arguments → same image; the seed defaults to the file name. Commit the generated PNGs. The art sessions
replace them later at the same paths, so keep paths stable.
