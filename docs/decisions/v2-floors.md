# v2-floors session decisions

Defaults picked while building Stage 6A's floors (PLAN2.md §0–§2; brief `docs/sessions/v2-floors.md`). Same style
as `DECISIONS.md`: where this conflicts with a plan file, this logs what was actually built.

## Common floor contracts (PR 1)

- **A floor is described twice: the request and the result.** `FloorRequest` (act ordinal, template pool id,
  floor number, run seed, party size) is what the game hands over when an act opens; `PlacedFloor` (template,
  dimension, camp anchor, radius, ground level, seed, tags, filled slots) is the immutable record the world
  returns and every module reads through `Floors`. Nothing else in `common` knows what a template looks like:
  templates, pools and escalation are the world's data.
- **Slots are the cross-module surface.** `FloorSlot(kind, tag, pos, radius)` with `FloorSlotKind` = camp,
  exit portal, node, structure, crate, mini-boss, secret, event, cave, wreck, siege ring. The world places the
  terrain and marks the slot; the logic belongs to the feature's owner (Director: events, mini-boss, camp
  objects; vehicles: the wreck; the wave spawner: siege rings). `tag` carries the kind-specific detail as a
  plain string (node block id, structure kind, loot table, ring index) so the enum never needs a payload type.
- **`Floors` defaults to "no floors"** (empty list, `open` → empty) when no provider is installed, mirroring
  `Acts`/`Runs`: the classic run shape and a server without the world module never see a floor. `open` is
  idempotent per act (asking twice returns the floor already placed), so the game can call it from
  `ACT_STARTED` without bookkeeping.
- **Events** (`FloorEvents`): `FLOOR_OPENED` (before the party is sent, so listeners can fill slots),
  `NODE_POPPED`, `CRATE_OPENED` (first open only), `EXIT_PORTAL_USED` (unlit frame used → the game starts the
  spine's portal charge), `EXIT_PORTAL_LIT` (charge completed → the frame leads on). Node and crate events
  carry the floor (null for a debug-placed node outside any floor) so the Director's loot cadence can count
  per floor without a lookup.
- **Party curves in `party.json`:** `floorSize` (0.07, 0.9: ×1.4 radius at 8 players ≈ twice the area),
  `floorNodes` (0.25, 0.85: ×2.3 at 8), `floorCrates` (0.15, 0.9: ×1.9 at 8). Radius rather than area so the
  walk across a floor stays short for big parties; nodes grow faster than crates because nodes are the
  per-player income and crates the shared jackpots.
- **Ids in `CommonIds`:** the floor dimension `blocksouls_world:floors`, the three node blocks (`scrap_node`,
  `gunmetal_node`, `resin_node`), the Surface rare `amber_resin`, and the `floor_portal` block, so the game and
  Director can reference them without importing `world`.
