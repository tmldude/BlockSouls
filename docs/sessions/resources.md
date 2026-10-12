# Session: resources (v0.11, playtest fixes)

**Lane:** `world`. **Branch:** `claude/resources`. **PR title:** `world: resources easier to find`.

Owner playtest (`docs/playtest/2026-10-12-owner-feedback.md`, item 2): "resources feel far and few between".

## Do
- About **2×** as a starting point:
  - gunmetal veins per chunk;
  - scrap piles (lower `scrapPileRarityChunks`);
  - wave-kill material chances (`waveCommonChance` etc.) and chest material chances.
- **Bias scrap near structures:** extra scrap piles around wrecks and checkpoints (structure pieces or a nearby
  feature), so the first loops find them.
- Keep it config-driven and log old → new in `docs/decisions/resources.md`.

## Tests
- A worldgen gametest counts scrap piles near a placed wreck.
- Unit tests for any spread math.

## Done when
CI green; the crafting check passes.
