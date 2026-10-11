# Art: items, blocks, guns, armor decisions

Defaults picked by the `art-items` session (Stage 2b). Same rules as `DECISIONS.md`. Style rules and palettes:
`tools/assets/STYLE.md`. Contact sheet: `docs/art/items-sheet.png` (+ `items-sheet-guns.png`).

## Pipeline
- **Scripts, not hand-saved PNGs.** `tools/assets/art.py` paints every owned texture from ASCII sprites (auto-shaded
  per material, sel-out outline) and small procedural painters (blocks, tyres, box-UV layouts); `guns.py` writes the
  gun models and atlases. Deterministic: re-running produces identical files. Shared helpers/palettes: `artlib.py`.
- **The per-module placeholder painters are removed** (`world/tools/gen_textures.py`, `vehicles/tools/gen_textures.py`):
  re-running them would overwrite the art. `world/tools/gen_data.py` now emits the two-layer blight heart model
  so re-running it keeps the emissive overlay. (`docs/decisions/vehicles.md` still mentions the old kart painter;
  the kart is now painted by `art.py`.)

## Guns
- **3D cuboid item models**, 9–19 elements each, one 32×32 texture per gun at the old path
  (`blocksouls_guns:item/<gun>`), split into 8×8 material tiles (bevelled swatches: plate, rust, wood grain, tape,
  checkered grip, vents, flutes, bore, lens, glow). Faces sample a whole tile; no UV unwrap, so parts can be moved
  without repainting.
- **Orientation:** barrel to −z. In first person the camera looks down −z, so rotation ≈ 0 keeps the barrel
  forward (4° yaw toward the crosshair). Third person uses rotation `[90, 0, 0]`, derived from `ItemInHandLayer`'s
  `Rx(-90)·Ry(180)` so the barrel points where the body faces with the top up.
- **Grip placement is computed:** each gun's grip point lands where vanilla `item/handheld` puts a sword's hilt in
  that pose, so guns sit in the hand like vanilla tools. Scales: pistols 0.85–0.9, rifles 0.65–0.8.
- **GUI:** profile with the muzzle right, top tipped 14° toward the viewer; long guns tilted 30–35° on screen so they
  fill the slot. `gui_light: front`. Item frame = same view seen from the back (vanilla's `fixed` 180° convention).
- **No rarity tint** (tooltips carry rarity, per the brief).
- **Starter Pistol** reads cheap: rusted slide, polymer frame, duct tape on the grip, bent brass wire rear sight,
  orange front sight.
- Gun parts and ammo stay flat 16×16 `item/generated` icons.

## Blocks
- **Gunmetal ores:** original stone / deepslate bases with steel-blue lumps and a cyan-white glint (reads apart
  from iron and coal).
- **Blighted netherrack:** netherrack crust with lumpy flesh growths (random-walk patches, tileable); 8-frame loop
  where the veins swell and glow (existing `.mcmeta`, frametime 3, interpolated).
- **Blight heart (emissive):** two coplanar cubes, flesh shell + a beating core overlay with `"light_emission": 15`.
  The overlay has transparent pixels, so 26.3 puts its quads in the cutout layer automatically (per-quad layer from
  sprite transparency, `BakedQuad`); no Java render-layer registration needed. 8-frame "lub-dub" beat, frametime 2.
  The base texture is now static (its old `.mcmeta` removed).
- Scrap pile, blight growth (cross), blight veins (glow-lichen overlay) keep their models; only textures change.

## Items and armor
- Boons keep **vanilla item ids as icons** (`boons/*.json` `icon`): adding our own icon items would need Java in
  `game`; out of lane. Relics got per-tier art (bone / steel-blue / gold) on one shared reliquary silhouette.
- **Armor:** scrap-plate = overlapping riveted scrap plates with rust and wood straps, hazard-painted chest plate;
  blight-forged = dark gunmetal plates with flesh between them, bone trim, glowing eyes and an exposed core.
  Equipment layers follow the vanilla 64×32 humanoid UVs; the helmet has a see-through visor slit.
- **Kart:** hazard-yellow body with checker-plate deck, black seats, rubber tyres with scrap hubs; the wreck is
  the same layout rusted and grimed.

## Not done / for later
- Not verified in a running client (sandbox has no Mojang/Fabric access); the display transforms are derived from
  vanilla's math (`ItemTransform`, `FirstPersonHandsAndItemsRenderer`, `ItemInHandLayer`) and checked with a
  software render of the GUI pose. **Integration: eyeball guns in first/third person once.**
- No `ASSETS.md` entries: nothing third-party was used.
