# Decisions: arena-seal (Stage 4)

Defaults picked by the `arena-seal` session. Same rules as `DECISIONS.md` (the root file wins on conflict).
Code: `bosses/.../arena/ArenaSeal.java` (server), `SealMath.java` (pure rules, unit-tested), `arena/mixin/`.

## The seal
- **A cylinder, not more blocks.** The visible fog wall stays as it was (4 below to 10 above home). On top of it,
  while a fog-wall boss is engaged, the server enforces a vertical cylinder: radius = the wall ring
  (`arenaRadius`), centered on the home block, height `sealHeight` up and down (default **0 = unlimited**). No ceiling
  blocks: a dome would need thousands of blocks and still miss pearls.
- **Who is held:** participants who are *inside* can't leave; everyone else can't enter. A participant who is outside
  (e.g. they started the fight by hitting the boss from outside, or respawned after dying) may walk in, and is held
  from then on. A non-participant who is somehow inside may leave.
- **Late joiners:** behind a seal, only someone already inside the cylinder joins (before: anyone in radius + 1.5).
  Hitting the boss from outside no longer makes you a participant mid-fight (it would have let you walk in); it still
  does when it *starts* the fight. Damage from outside stays ×0.25.
- **How it holds:** every boss tick, each player's position is compared with last tick's. A crossing the wrong way
  puts them back at last tick's position (their side), with a damage-free push of `pushStrength` (0.6 blocks/tick)
  away from the line plus a small hop, elytra gliding and creative-style flight (the flight trait) switched off,
  dismounted from vehicles, fall distance reset. The fog wall sound (pitched up) and ash/cloud particles play where
  the line was crossed. Returning to the previous position, rather than clamping onto the circle, can't put anyone
  inside terrain.
- **Server teleports are left alone:** a move longer than `sealMaxCatchDistance` (64 blocks) in one tick is a
  command or a run transition, never a player's own movement (pearls are cancelled before they move anyone, chorus
  is 8 blocks). Such a participant stays a participant and may walk back in.
- **Ignored:** spectators and creative players, both ways (admins and debugging). Dead players drop their track; a
  respawned player starts fresh wherever they spawn (never pulled back into the arena).
- **Downed players:** a participant knocked across while downed is put back like anyone else and still counts as a
  participant (KILLED credit, can re-enter after a revive). Teammates can reach them because they never leave.
- **The final raid** has no fog wall (`raisesFogWall() == false`) and so no seal; adds spawned with
  `setFogWallEnabled(false)` don't seal either.
- **Lifts** on reset, death and removal (including chunk unload, so a seal never outlives its boss's ticking).

## Teleports
- **Ender pearls:** a pearl whose landing would carry its thrower across a seal the wrong way fizzles (removed, no
  teleport, no pearl damage) with the fog wall hit effect. Mixin on `ThrownEnderpearl.onHit` (HEAD).
- **Chorus fruit** (any `randomTeleport`): `LivingEntity.canRandomlyTeleportTo` returns false for spots across the
  seal, so vanilla's 16 tries pick a spot on the player's own side (or none). No effect per rejected try (16 sounds).

## Web ropes
- **Snap from the bosses side, without importing `movement`:** the movement module keeps the swing in two Fabric
  attachments, `blocksouls_movement:web_anchor` (synced) and `blocksouls_movement:web_rope_length` (server). The seal
  looks them up by id (`AttachmentRegistryImpl.get`, Fabric's internal registry; same idea as data files referencing
  other modules' ids) and, when the anchor is across the line in a direction the player may not go, sets the rope
  length to -1000. Movement's own server tick then finds the player "too far" from the anchor, drops the swing and
  tells the client (`web_drop`), exactly like a rope stretched past its limit. Cobweb-break sound plus the fog wall
  hit effect, once per snap. Without the movement module the lookup returns null and nothing happens.
- While the player stands on the ground, movement pays rope out instead of dropping it; the seal re-snaps every tick,
  so the swing drops the moment they leave the ground. Allowed directions (a participant outside swinging in, anyone
  swinging inside the arena or anchoring on the wall itself) are never snapped.

## Config (`config/blocksouls/bosses.json`)
- `sealHeight` 0 (unlimited), `pushStrength` 0.6, `sealMaxCatchDistance` 64.
