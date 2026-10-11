# Movement session (Stage 1) decisions

Defaults picked while adding the web swing, movement traits, config sync and roll visuals. Same rules as
`DECISIONS.md`: entries here override `PLAN.md`; the root file wins on conflict.

## A4 web swing
- **Numbers from the brief**: 32-block reach (×`WEB_REACH`), 10 stamina per shot, rope min 3, reel 0.3/tick,
  swing boost 0.04, drag 0.99, release +0.15 up, speed cap 2.5 b/t, 10 no-fall ticks after release. All in
  `movement.json` → `web`.
- **Fallback rays:** if the look ray misses or only hits something below the rope point, 3 more rays at +6°, +12°,
  +18° pitch are tried and the first hit above the player wins; failing that, a below-the-player hit is still used
  (a slack rope; harmless). Misses and shots without stamina play a low "fizzle" (cobweb break at low pitch).
- **Sounds are vanilla placeholders** (cobweb break, pitched up for the shot) until real art.
- **Rope point** is 1.2 blocks above the feet (about hand height) on both sides; the renderer draws from the hand
  position vanilla's fishing line uses.
- **Physics integrates on actual movement.** Each client tick, last tick's real displacement is the velocity
  (so collisions and air steering count), the `Pendulum` step produces next tick's velocity, and vanilla moves the
  player by it (so walls still stop you). The constraint removes outward velocity, projects next tick's position
  onto the rope's sphere, and gives back the speed the chord-vs-arc projection loses (energy drift ≈1% per half
  swing without boost/drag). The boost only applies while the rope is taut and the player is moving down.
- **On the ground** the swing stops simulating and the rope pays out as you walk (server mirrors this), so you can
  walk under an anchor; holding sneak on the ground still reels in (pulls you up).
- **The swing ends** on release, entering liquid, starting to fly/glide, mounting, dying or going down.
- **Anti-kick needs no mixin.** 26.3's `ServerGamePacketListenerImpl` has a public `resetFlyingTicks()`; the server
  calls it every tick for players with an active swing or flight. Nothing else is exempted.
- **Server drops** the swing (and tells the client) when the rope point is more than rope length + 4 from the
  anchor; the server mirrors reel-in from the player's last sent input. A rejected shot also tells the client to let
  go, so the client never swings without the server backing it (same rule as the roll).
- **Server validation:** anchor within reach ×1.1 of the server's eye position, on a block with a collision shape
  (anchor within its bounds ±0.01), and a ray from the eye to just past the anchor stops on that block (or within
  1 block of the anchor, for latency).
- **Fall damage** is cancelled through `ALLOW_DAMAGE` on `is_fall` damage while swinging and for 10 ticks after.
  The server also resets fall distance every swinging tick, so a long swing doesn't bank a lethal fall.
- **Rope sync:** the anchor is an attachment synced to all clients (`blocksouls_movement:web_anchor`); the local
  player draws its own rope from its local state, so the rope appears before the server confirms.
- **Render** uses Fabric's `LevelRenderEvents.COLLECT_SUBMITS` (26.3's successor to `WorldRenderEvents`) with
  `RenderTypes.lines()`.

## Traits
- **DOUBLE_JUMP:** one air jump per level at vanilla jump strength (`JUMP_STRENGTH` + Jump Boost), counted from
  presses after leaving the ground (the press that launched a ground jump and coyote-time presses don't count);
  resets on landing. The client shows its own puff right away; the server counts air jumps (bounded by the trait)
  and sends the puff + a soft sound to everyone else.
- **ROLL_IFRAMES:** +2 i-frame ticks per level at the window's end. When that runs past the roll's 12 ticks, the
  server keeps the roll timer alive until the i-frames end; a new roll may still start once the movement ends.
- **FREE_ROLL_ON_KILL:** killing any `Mob` makes rolls free for 60 ticks (not scaled by level). The end time is a
  separate attachment synced to the owner (game time), not part of the stamina attachment, so the countdown never
  causes extra sync traffic.
- **WEB_REACH / CLIMB_SPEED / STAMINA_REGEN:** +25% / +25% / +20% per level, linear.
- **FLIGHT:** double-tap jump in the air (two air presses within 7 ticks; with `DOUBLE_JUMP` the first press is
  still an air jump). The server grants `mayfly` + `flying` at 60% of creative speed (0.03) and drains 12 stamina/s
  (regen paused). It ends on landing, stamina 0, trait gone, going down, or toggling flight off with vanilla's own
  double tap; then abilities go back to the game mode's defaults and flying speed to 0.05. Creative/spectator
  players are never touched. A persistent marker revokes a flight interrupted by logout/crash on the next join.
  Running out of stamina mid-air means a real fall (vanilla only skips fall damage while `mayfly` is set).
- Trait scaling percentages are config fields (`movement.json` → `traits`).

## Downed
- While `PlayerStatus.isDowned`: no jump feel (tap/hold/fall gravity), roll, climb, swing (an active one is
  dropped), air jumps or flight (an active one ends), on both client and server.

## Config sync
- On join the server sends its `movement.json` as JSON (one payload); the client copies the values into its live
  config object (features hold references to its sections, so values are copied in place, not replaced). On
  disconnect the client reloads its own file. Single-player's integrated server shares the same object and file, so
  sync is a no-op there. `/bs move config reload` re-reads the file and re-sends it to everyone.

## Roll visuals (A2 leftovers)
- **Camera tilt:** up to ±10° (config `roll.cameraTiltDegrees`) toward the sideways part of the roll direction, sine
  eased over the roll; pure forward/back rolls don't tilt. Injected at the end of `GameRenderer.bobHurt` (vanilla's
  hurt tilt), so it also tilts the held item like hurt does. The tilt's sign was reasoned, not seen in-game: flip it
  in `RollHandler.cameraTilt` if it leans the wrong way.
- **Eye height:** the camera eases to crouch eye height during a roll (`Camera.tick`'s `getEyeHeight()` call,
  wrapped with MixinExtras); the hitbox is unchanged. `roll.lowerEyeHeight` turns it off.
- Neither needed a risky mixin, so nothing was skipped.

## Debug commands
- `/bs move trait <player> <trait> <level>` (traits: `double_jump`, `roll_iframes`, `free_roll_on_kill`,
  `web_reach`, `climb_speed`, `stamina_regen`, `flight`; level 0 removes), `/bs move stamina <player> <value>`,
  `/bs move config reload`.

## Not verified here
- This sandbox can't run `runClient` (no Mojang/Fabric maven access), so feel (swing smoothness, tilt direction,
  rope placement in first person) still needs a human playtest. CI covers compile, unit tests and game tests.
