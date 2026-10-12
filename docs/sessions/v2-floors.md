# Session: v2-floors (Stage 6A — replaces the Dimensions session)

**Modules:** `world` (floor dimension, templates, placement) + `common` (additions only) + `game` (floor
state hooks only, in `game/run/` via the spine's act machine). **Branch:** `claude/v2-floors`.
**PRs, in order:** `common: floor contracts (floor spec, placement hooks, node/crate events)` →
`world: the floor dimension, Surface template pool, resource nodes` → further floors as separate PRs.

You build the stage the whole v2 run plays on: small island floors in a void dimension. Read
`docs/sessions/README.md` (protocol), **`PLAN2.md` §0–§2 in full** (the Floor Loop), `docs/CONTENT.md`
(structures must be registered structures; generated art; recipe web), DECISIONS.md's v2 entries, and
`docs/decisions/v2-spine.md` (acts, `Acts`/`ActEvents`, portal charge — an act is a floor).

## You own
- A **void floor dimension** (`blocksouls_world:floors`): dimension type + dimension JSON; floors are
  placed at offsets on a grid (one floor per act ordinal per run), glass wall at the island edge, void
  beyond, a death-plane/teleport-back under the island (no void deaths from a misstep: fall below the
  island → teleported to the camp, lose a little).
- The **template system**: a floor template = terrain + trees + placement slots for {camp (center),
  resource nodes, structure, mini-boss arena, secret, event, cave entrance, wreck, siege spawn rings}.
  Templates are structure NBT + a JSON spec; variation per run (which slots are filled with what,
  rotation/mirroring, node mix). Pool per floor number; **escalation tags** so later floors draw meaner
  variants.
- **Resource nodes**: blocks that pop in 2–4 hits with a resource burst (wood from trees stays vanilla;
  scrap piles, gunmetal nodes, floor rares), respawn rules (none within a day; config).
- **Structures** (registered; `/locate` works): one per floor from a pool — ruin, wreck yard, outpost —
  with a chest (gear / enchantment / early boon via `v2-builds`' offer APIs or loot tables).
- **Cave dungeon** entrance + a short underground layout; **secret** placement (cracked wall, hidden drop,
  hint item; one per floor, never required); **event** and **mini-boss** placement *slots* (logic belongs to
  `v2-director`); **wreck** placement with scrap scattered around it (repair interaction stays in
  `vehicles` — coordinate: it should accept scrap items, show its needs on hover).
- Floor look: sky/fog per floor via environment attributes (the spine's vibe switch reads the synthetic
  clock); Floor 1 Surface now; later floors get their looks with their templates.
- `docs/decisions/v2-floors.md`.

## Order of work
1. Contracts + the void dimension + one hand-made **Surface** template with every slot filled; the run's
   act 1 points at it (`game-run.json` acts list → floor specs; `Acts` reports floors). The exit portal
   frame at the camp uses the spine's portal-charge completion.
2. Template variation + the Surface pool (3+ templates), nodes, structure pool, cave, secret, wreck.
3. Floors 2–6 **after** the owner's per-floor design discussion (the orchestrator will brief each);
   until then, acts 2–6 reuse Surface templates with escalation tags so the whole run is walkable.

## Constraints
- `common` additions in their own first PR. Everything data-driven; every number in config;
  `PartyScaling` for floor size / node counts / crate counts, tested at 1 and 8.
- Recipe web: every new resource feeds 2+ trade-board offers or recipes (CONTENT.md R1; coordinate with
  `v2-director`'s trade board and `v2-builds`' recipes). Nodes/crates are end-item sources, not items.
- Lanes: don't touch `game/run/**` beyond act→floor spec plumbing (coordinate with `v2-director`, which
  owns the day loop in those files — if both need the same file, director edits, you message);
  don't touch `bosses/`, `mobs/`, `guns/`, `abilities/`, `movement/`, `game/player/**`, HUD.
  v0.11's `resources` session changed `world` gen defaults — merge main first; your work is new files.
- Classic mode must stay untouched: the floor dimension only generates when a run with
  `runShape: "floors"` asks for it.
- CI green is the bar; a gametest walks a player through a Surface floor (spawn at camp, pop a node,
  open the structure chest, enter the cave, find the secret, repair the wreck with scrap, complete the
  exit portal); the client test screenshots the floor.

## Done when
Surface floor pool playable end to end in `runShape: "floors"`; acts 2–6 walkable on Surface variants;
nodes/structure/cave/secret/wreck slots all placed and tested; floor escalation tags wired; decisions logged.
