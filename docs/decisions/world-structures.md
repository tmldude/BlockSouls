# World structures decisions

Defaults picked by the `world-structures` session (Stage 4). Same rules as `DECISIONS.md`. Supersedes the
"Structures" section of `world.md` where they differ.

## Registered structures
- **One structure type, two structures.** `blocksouls_world:ruin` (`RuinStructure`, field `"kind": "wreck" |
  "checkpoint"`) with one custom piece (`RuinPiece`, id `blocksouls_world:ruin`) rather than jigsaw/template pools: the
  look comes from the seeded layout generators (`WreckLayout`, `CheckpointLayout`), which randomise every instance
  (smashed windows, dents, roof holes, rotation). NBT templates would freeze them into a few variants.
- **The piece regenerates the layout from a stored seed** and `StructurePlacer` builds only what's inside the chunk
  being generated (blocks, foundation, chests, scrap piles, kart wreck by column). The start sits at the centre of its
  chunk and the footprint is ≤ 7 blocks out, so in practice the whole ruin is in one chunk.
- **Site check moved to the generator's noise** (`getBaseHeight`), so it also runs for chunks that don't exist yet,
  which is what lets `/locate` search unexplored land. Same thresholds as before: corners 2 (wreck) / 5 (checkpoint)
  blocks out within 2 / 3 blocks of the centre's height, and dry (`WORLD_SURFACE_WG == OCEAN_FLOOR_WG` at all five
  columns). The leaves/logs check is gone: structures generate before trees. Step `surface_structures`, no terrain
  adaptation (the placer still props the bottom layer up to 4 blocks).
- **Biomes** are tags `#blocksouls_world:has_structure/wreck` (same list as before) and `has_structure/checkpoint`
  (every overworld surface biome except oceans, rivers and beaches, listed explicitly since tags can't subtract; cave
  biomes left out since the biome is sampled at the surface).
- **`#blocksouls:ruins`** structure tag holds both, for a future explorer map / compass (`WorldGen.RUINS`). Not added
  to `common`'s `CommonTags` since nothing outside `world` uses it yet.

## Density
- **Spacing is still config.** Structure sets use `blocksouls_world:config_spread` (`ConfigSpreadPlacement`, a
  `RandomSpreadStructurePlacement` subclass so `/locate`'s grid search works) reading `world.json`:
  `wreckSpacingChunks` 12 / `wreckSeparationChunks` 4, `checkpointSpacingChunks` 24 / `checkpointSeparationChunks` 8.
  One attempt per 12×12 / 24×24 chunks equals the old 1-in-144 / 1-in-576 rarity rolls. Read when the datapack loads
  (world load / `/reload`), not live. Spacing 0 disables (frequency 0). Separation is clamped below spacing.
- **Old keys** `wreckRarityChunks` / `checkpointRarityChunks` are removed; an old `world.json` keeps loading (unknown
  keys are ignored) and gets the new defaults.
- Fixed salts (1730429161 wreck, 905125277 checkpoint) keep the two grids independent.

## Locate
- **`/bs world locate wreck|checkpoint`** runs `ChunkGenerator.findNearestMapStructure` (vanilla `/locate`'s search,
  100 chunks), so it finds the nearest one explored or not. `/locate structure blocksouls_world:wreck` and
  `#blocksouls:ruins` work too. **`StructureLog` is gone** (nothing else used it); its saved data in
  old worlds is simply never read.
- `/bs world place wreck|checkpoint` still stamps a layout at your feet (no structure start, so `/locate` won't list it).

## Tests
- The gametest level is flat with structures off, so `findNearestMapStructure` can't run there. The gametests build a
  vanilla overworld noise generator (seed 0), take the structure's placement from `ChunkGeneratorStructureState` (what
  `findNearestMapStructure` uses), walk its spread grid running the structure's own generation until a start is valid
  (the same check `/locate` does for unexplored chunks), then move that start's piece into the test area and place it
  chunk by chunk with `StructureStart.placeInChunk`. They assert loot chests that roll loot, scrap piles, and the kart
  wreck iff `blocksouls_vehicles:kart_wreck` is registered.
