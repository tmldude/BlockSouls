# Assets

**Real art:** `art.py` paints the items, blocks, armor, ammo, gun parts, vehicle and relic textures, and `guns.py`
builds the 3D gun models; the rules and palettes are in [`STYLE.md`](STYLE.md). Re-run after editing either:

```sh
python3 tools/assets/art.py --sheet docs/art/items-sheet.png
```

## Placeholder assets

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
  `ember` (Furnace Knight), `pale` (Pale Rider), `storm` (Stormplate Knight), `ash`, `bone`, `steel`, `wood`,
  `rot` (rotting flesh), `flesh` (raw, mutated).

Same arguments → same image; the seed defaults to the file name. Commit the generated PNGs. The art sessions
replace them later at the same paths, so keep paths stable.

`mobs.py` paints the mobs module's custom models (box-UV layouts, emissive `_glow` layers, spawn eggs):
`python3 tools/assets/mobs.py`. Its box lists mirror the Java `LayerDefinition`s; update both together.
