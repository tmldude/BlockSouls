# Session: arena-seal (Stage 4)

**Module:** `bosses` (framework: `bosses/arena/`, `bosses/api/BossEntity`). **Branch:** `claude/arena-seal`.
**PR title:** `bosses: fog-wall arenas hold in 3D`.

The fog wall is a ring of blocks from `fogWallBelow` (4) to `fogWallAbove` (10) around the boss's home, so a player
can web-swing, pearl, climb, fly (flight trait) or elytra over it, in or out. Read `bosses/README.md` and
`docs/decisions/bosses.md` ("Known limit") first.

## You own
`bosses/src/*/java/.../bosses/arena/**`, the engage/leash parts of `BossEntity`, `BossesConfig`, and bosses tests.
Don't change individual bosses. The final raid has no fog wall (`raisesFogWall() == false`) and must not change.

## Make the seal hold
- Keep the visible wall. Add a **server-side boundary**: while a fog-wall boss is engaged, a participant can't
  leave the arena cylinder and an outsider can't enter it, **at any height** (pushback toward the inside/outside
  plus a little damage-free knockback, with the fog-wall sound and particles where they hit it).
- Cancel ender pearl / chorus fruit teleports across the boundary; stop elytra and creative-style flight at the
  boundary; ropes from the web swing that cross the wall snap (do this from the bosses side: e.g. a block tag on the
  fog wall or a boundary check on the player's movement; don't edit `movement/`).
- A participant who is downed outside (knocked across) still counts; spectators and creative players ignore the
  boundary.
- Config: `sealHeight` (default: unlimited), `pushStrength`.

## Tests
Gametests: an engaged participant teleported/pushed across the ring ends up back inside; an outsider can't enter;
an ender pearl across the ring is cancelled; creative players pass; the final dragon is unaffected; the seal lifts
when the fight resets or the boss dies.

## Done when
CI green; `docs/decisions/arena-seal.md` (update the "Known limit" note in `docs/decisions/bosses.md`).
