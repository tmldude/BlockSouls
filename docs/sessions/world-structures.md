# Session: world-structures (Stage 4)

**Module:** `world`. **Branch:** `claude/world-structures`. **PR title:** `world: wrecks and checkpoints as real structures`.

Wrecks and checkpoint ruins are code-placed features (`world/gen/WreckFeature`, `CheckpointFeature`,
`StructurePlacer`), so vanilla `/locate` can't find them and `/bs world locate` only knows explored chunks
(`StructureLog`). Read `docs/decisions/world.md` ("Structures") first.

## You own
`world/**`.

## Do
- Turn both into **registered structures** (a `StructureType` + `structure` + `structure_set` + biome tags, or
  jigsaw/template pools if that fits better), keeping their look, loot, biomes and roughly today's density.
- `/locate structure blocksouls_world:wreck` (and `checkpoint`) works; `/bs world locate` uses the real structure
  search (nearest, explored or not) and drops `StructureLog` if nothing else needs it.
- Keep what hangs off them: the **kart wreck entity** next to world-gen wrecks (`StructurePlacer` spawns
  `CommonIds.KART_WRECK`), loot tables, scrap.
- Optional if time allows: a structure tag (`#blocksouls:ruins`) so an explorer-map or compass could target them later.

## Tests
Gametests: both structures generate in a test world (place via the structure's own placement in a gametest) with
their loot chests and the kart wreck; `/locate` finds one (`ChunkGenerator.findNearestMapStructure`).

## Done when
CI green; the full-pack tests still pass; `docs/decisions/world-structures.md`.
