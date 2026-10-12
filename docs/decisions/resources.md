# Resources decisions

Defaults picked by the `resources` session (v0.11, playtest fixes). Same rules as `DECISIONS.md`. Owner feedback item 2
(`docs/playtest/2026-10-12-owner-feedback.md`): "resources feel far and few between". Supersedes the matching numbers
in `world.md` and `world-structures.md`.

## Old → new (`config/blocksouls/world.json`)

| Field | Old | New | Effect |
|---|---|---|---|
| `gen.gunmetalVeinsPerChunk` | 7 | **14** | 2× gunmetal veins (iron's middle band has 10; gunmetal now beats it, it's the gun economy) |
| `gen.scrapPileRarityChunks` | 24 | **12** | loose scrap clusters in 1 of 12 chunks instead of 1 of 24 |
| `gen.wreckExtraScrapPiles` | — | **3** | new: piles ringed around each wreck (layout debris was 2–4, now 5–7) |
| `gen.checkpointExtraScrapPiles` | — | **4** | new: piles ringed around each checkpoint (was 2–3, now ~6–7) |
| `loot.materialRolls` | (1) | **2** | new: material chests roll the `inject/materials` pool twice, each at `materialChance` (0.6, unchanged) → 1.2 expected stacks, was 0.6 |
| `loot.materialChests` | 18 vanilla chests | **+ `blocksouls_world:chests/wreck`, `chests/checkpoint`** | our own ruin chests also get the material rolls on top of their own tables |
| `loot.waveCommonChance` | 0.25 | **0.5** | |
| `loot.waveRareChance` | 0.4 | **0.8** | |
| `loot.waveLegendaryChance` | 0.6 | **1.0** | capped: legendary kills always roll materials (1.67×) |

- **Chest chance stays 0.6, rolls doubled** instead of raising the chance: 1.2 can't be a probability, and two rolls
  means a chest can have one stack, two or none, which reads as loot rather than a fixed handout.
- **Wave chances are per kill** (not party-scaled): more players kill more mobs, so the party's total already scales;
  `game`'s `dropChance` (raised to 0.5 in v0.10.1) gates whether the tier table rolls at all, so the end-to-end chance a
  common wave kill drops materials is 0.5 × 0.5 = 0.25 (was 0.35 × 0.25 ≈ 0.09).
- Gunmetal vein size (7) and height band (−32..48) are unchanged; doubling the count is the bigger, clearer lever.

## Scrap biased near structures
- **Extra piles are part of the ruin's layout** (`ScrapScatter.add`, applied in `RuinStructure.Kind.layout`), not a
  separate feature: they come from the ruin's own seed, they're placed with the rest of the structure (same ground /
  height checks in `StructurePlacer`), the debug command `/bs world place` shows them too, and the piece's bounding box
  covers them. A nearby-feature approach would need a structure lookup per chunk during decoration.
- **Where:** a square ring just outside the ruin's blocks (Chebyshev distance from one past the furthest block out to
  7), never on a block column, the kart spot or another pile. Max 7 keeps the whole ruin inside its start chunk, as
  `world-structures.md` requires. A checkpoint reaches 5 out, so its ring is 6–7; a wreck's is 3–7.
- **Roads:** there are no road features yet; checkpoints sit "on the road" thematically, so they get the larger count.

## Existing servers
- `world.json` gains `defaultsVersion` (now 1). On load, `WorldConfig.migrate` moves every value still at its old
  default to the new one and keeps hand-tuned values (same pattern as `RunConfig.migrate`, #42). The two ruin chests
  are added to `materialChests` if missing. New fields (`materialRolls`, `*ExtraScrapPiles`) take their defaults from
  the constructor.
- Worldgen changes apply to **newly generated chunks** only; explored land keeps what it had.
