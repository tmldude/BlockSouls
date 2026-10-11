# BlockSouls art style

One look for every item, block and armor piece: **a scavenged apocalypse** (scrap, rust, taped-up gear, machined
gunmetal) in the overworld, and **a fleshy infection** (the blight) in the Nether. Bosses carry their own accent
palette. All art is original and painted by scripts in this folder, so it is reproducible and tweakable.

| Script | Paints |
|---|---|
| `art.py` | items, blocks, armor icons + equipment layers, ammo, gun parts, wheel/engine, kart, relics; runs `guns.py` |
| `guns.py` | 3D gun item models (cuboid JSON) and their 32×32 material atlases |
| `mobs.py` | mob skins (art-mobs session) |
| `gen.py` | old placeholder generator; only for things nobody has painted yet |

```sh
python3 tools/assets/art.py --sheet docs/art/items-sheet.png   # repaint everything + contact sheet
```

## Rules

- **Sizes.** Items and blocks are 16×16. Gun textures are 32×32 atlases (4×4 tiles of 8×8) because guns are 3D
  models. Animated blocks are vertical strips of 16×16 frames with a `.png.mcmeta`.
- **Light** comes from the **top-left**. Every material is shaded by its silhouette: top/left edges one step lighter,
  bottom/right edges one step darker, the inside at the material's base tone. Cuboid tiles get the same bevel.
- **Outlines (items only):** a 1 px *sel-out*: each outline pixel is the neighbouring colour at ~40% value, never
  pure black. Blocks never get outlines (they tile).
- **Ramps, not hues.** Each material is a ramp (dark → light). Shade by moving along the ramp, never by tinting.
  Noise is ±1 ramp step on 10–45% of pixels (rough materials more, machined metal less).
- **Glow is a separate palette** (`BLIGHT_GLOW`, `TRITIUM`, `VOID`) and is the only thing allowed near-white
  highlights. Emissive block parts go on an overlay texture rendered with `"light_emission": 15`.
- **Read at 1×.** Every icon must be identifiable as a silhouette alone. Check the contact sheet at 100%.
- **Names and designs are original.** No real gun models, no Bungie / FromSoftware / Activision / Marvel /
  Nintendo designs.

## Palettes (`artlib.py` is the source of truth)

| Theme | Ramp | Used for |
|---|---|---|
| Scavenged: scrap | `#1b1c1f #2e3034 #45474c #5f6268 #7d8086 #a0a3a8 #c9cbce` | scrap, scrap-plate armor, engine, kart frame |
| Scavenged: rust | `#261007 #45200e #6b3216 #934a20 #ba6630 #d98b4c #efb47c` | rust patches, wreck, starter pistol slide |
| Scavenged: tape | `#3f3e3a #5d5b55 #7f7c74 #a29e94 #c4c0b4` | duct tape repairs |
| Scavenged: hazard | `#3d2c03 #7a5807 #b8860b #e6b422 #ffd85a` | kart paint, hazard stripes |
| Gunmetal | `#121519 #1f242b #2f363f #434c57 #5c6773 #7d8995 #a6b2bd #d5dde4` | guns, gunmetal ore/ingot/block, blight-forged plates |
| Polymer / rubber | `#0f0f10 … #646066`, `#0d0d0e … #45454a` | gun furniture, grips, tyres |
| Wood | `#271609 #41260f #5e3918 #7d4f24 #9d6834 #bf884b` | stocks, pump, straps |
| Brass | `#33210a #5e3e12 #8f621e #bd8d30 #dfb85a #f6e19a` | cartridges, sights |
| Blight (flesh) | `#17040b #360a1a #5c122c #871d41 #b12f59 #d75280 #f78db0` | blighted netherrack, growth, veins, heart, core |
| Blight glow | `#ff2e63 #ff6b93 #ffb3c9 #fff0f4` | glowing cores, nodes, eyes (emissive only) |
| Nether rock | `#220909 #3b1010 #561716 #701f1d #8b2a26 #a43a33` | the rock under the blight |
| Special (void) | `#160b29 … #ece0ff` | Special ammo |
| Relic tiers | bone / steel-blue / gold + ember | Common / Rare / Legendary relics |
| Bosses | ember `#2a0d03 … #fff0c0`, pale `#1c2230 … #e8f0ff`, storm `#101a2e … #fff1b0` | boss sessions' art |

## Guns

- Modeled **barrel to north (−z)**, top +y, right side +x, grip near the middle of the 16-unit cube.
- Parts are named cuboids: barrel/brake (muzzle face = `bore` tile), receiver, stock, mag, grip, sights. Grips and
  mags may rotate on x (any angle is allowed in 26.3).
- **Display transforms are computed, not hand-tuned** (`guns.displays`): the grip is placed exactly where a
  vanilla sword's hilt sits in each hand pose, the barrel points forward in first and third person, and GUI / item
  frame / ground views are fitted to the model's bounds. Long guns sit diagonally in the GUI like vanilla tools.
- `"gui_light": "front"` so the inventory icon is lit like a flat item.
- The **Starter Pistol** looks cheap: rusty slide, polymer frame, duct tape round the grip, a bent-wire rear sight.
