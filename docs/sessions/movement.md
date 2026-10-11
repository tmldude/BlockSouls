# Session: movement (Stage 1)

**Module:** `movement`. **Branch:** `claude/movement`. **PR title:** `movement: web swing, boon traits, config sync`.

Jump, roll, climb and stamina are done (PLAN.md §3 A1–A3, DECISIONS.md "Phase A"). You add the web swing, make the
movement boons work, sync the server's tuning to clients, and respect the downed state. Read
`docs/sessions/README.md`, PLAN.md §0b and §3, and the existing `movement/` code first; match its style.

## You own
Everything under `movement/`.

## Contracts
- `PlayerStatus.isDowned(player)` (synced to all clients): while down, no jump boost, roll, climb, web swing,
  double jump or flight.
- `PlayerTraits` (synced to the owner): implement every movement trait (levels stack):
  - `DOUBLE_JUMP`: one extra mid-air jump per level (jump key pressed while airborne after leaving the ground;
    vanilla-like jump velocity; small particle puff). Resets on landing.
  - `ROLL_IFRAMES`: +2 i-frame ticks per level (extend the window's end).
  - `FREE_ROLL_ON_KILL`: rolls cost no stamina for 3 s after the player kills a mob (server tracks; tell the
    client via the stamina attachment or a payload).
  - `WEB_REACH`: +25% web range per level.
  - `CLIMB_SPEED`: +25% climb speed per level.
  - `STAMINA_REGEN`: +20% regen per level.
  - `FLIGHT`: double-tap jump in the air to fly (creative-style, slower: 60% of creative speed) for as long as
    stamina lasts, draining 12/s; landing or stamina 0 ends it. Server grants/revokes `mayfly`-style abilities only
    while active, and never lets it persist after the trait is gone (watch out for the floating kick).

## A4 web swing (PLAN.md §3 A4, numbers here win)
- **Input:** a keybind (default `V`, via `Keybinds`; no item needed). Press = shoot a web; hold = swing; release =
  let go. Costs 10 stamina per shot (config); can't shoot with < 10.
- **Anchor:** raycast up to **32 blocks** (×`WEB_REACH`) along the look direction; if it misses, try a few rays
  slightly above the look direction (prefer anchors above the horizon). Must hit a solid block face. No anchor →
  a short "thwip" fizzle.
- **Physics (pure class `Pendulum` with unit tests):** rope length = distance at attach (min 3). Each tick: gravity
  as normal, then if distance > length, remove the outward velocity component and pull the position back to the
  sphere; add a small tangential boost (0.04, config) along the current swing direction when moving downward on
  the arc; air drag 0.99. Holding sneak reels in (−0.3 length/tick, min 3). On release: keep velocity, +0.15 up.
  Cap speed at 2.5 blocks/tick (well under vanilla's 10 b/t "moved too quickly").
- **Authority:** like the jump, the client simulates (vanilla movement is client-side). The client sends the anchor
  in an `attach` payload and a `detach` payload; the server validates the anchor (within range ×1.1 of the server's
  view, line of sight, solid block), tracks the swing state (for fall-damage and anti-kick), and drops it if the
  client is more than rope length + 4 blocks from the anchor.
- **Floating kick:** a swinging player can be in open air for a long time. Vanilla kicks players that are airborne
  with no blocks around for 80 ticks unless flight is allowed. Grep `ServerGamePacketListenerImpl` for the
  above-ground tick counter and reset it while the server sees an active swing or flight (mixin), nothing else.
- **No fall damage** while swinging and for 10 ticks after release (server, `ALLOW_DAMAGE` on fall damage tag) —
  extend to 20 ticks if it feels better; config.
- **Render:** a thin white rope from the player's hand to the anchor (first and third person; other players' ropes
  too — sync the anchor to tracking clients via an attachment synced to all, or a payload). Find the 26.3 world
  render event in Fabric API (`fabric-src`, e.g. a `WorldRenderEvents`/`LevelRenderEvents` successor).

## Server config sync
On join, the server sends its `movement.json` values to the client (payload with the config as JSON or a codec);
the client uses the server's values while connected and its own file in single-player. Server-side checks always
use the server's values.

## Roll visuals (A2 leftovers)
Camera tilt during a roll (roll the camera ±10° in the roll direction, eased) and a lowered eye height (crouch-like)
for the roll's duration. Client-only; skip any part that needs a risky mixin and log it.

## Debug commands
`move trait <player> <trait> <level>` (sets a `PlayerTraits` level for testing), `move stamina <player> <value>`,
`move config reload`.

## Tests
- Unit: `Pendulum` (constraint keeps length, energy roughly conserved without boost, reel-in, speed cap), double jump
  counter, flight stamina drain, trait-scaled numbers.
- Gametests: a mock player with an anchored swing has fall damage cancelled; the swing is dropped when too far from
  the anchor; downed players can't start a roll; traits change roll i-frames and climb speed.

## Done when
CI green; in `runClient`: swing between trees/buildings smoothly with a visible rope, no kicks in 2 minutes of
swinging over open water; every movement trait works when set with `/bs move trait`; another player sees your rope;
`docs/decisions/movement.md`.

## Out of scope
Boon definitions and granting (game-players). Web-shooter item (not needed).
