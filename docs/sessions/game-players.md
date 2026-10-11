# Session: game-players (Stage 1)

**Module:** `game`. **Branch:** `claude/game-players`. **PR title:** `game: downed/revive, boons, HUD`.

You build what happens to players during a run: going down instead of dying, reviving, the run ending when
everyone is down, the roguelike boons, and the run HUD. Read `docs/sessions/README.md` and PLAN.md §0b first.

## You own
- `game/src/main/java/.../game/player/**` (start from the stub `PlayerSystem`; keep `init()`).
- `game/src/client/java/.../game/client/hud/**` (stub `GameHud`), `game/client/player/**` (screens, renderers).
- `game/src/main/resources/data/blocksouls_game/boons/**`, boon/relic textures and lang keys.
- `game/src/test/**` and `game/src/gametest/**` files named `Downed*`/`Boon*`/`Hud*`.
- Shared with **game-loop** (parallel, same module): `BlockSoulsGame*.java`, `fabric.mod.json`, `en_us.json`.

**game-loop** owns `game/run/**` and `game/client/run/**`. You read the run through `Runs.get(server)` (server) and
`ClientRun.get()` (client), listen to `RunEvents`, and call `RunSystem.endRun(server)` when the team wipes. Your
config is `config/blocksouls/game-players.json` (class `game/player/PlayersConfig`).

## Contracts
- `PlayerStatus.setDowned/isDowned` (synced to all clients); fire `PlayerRunEvents.DOWNED` / `REVIVED` /
  `BOON_GRANTED`. Other modules key off the downed flag: guns stop firing, movement disables abilities.
- `PlayerTraits.set/clear` for boons that other modules implement (double jump, roll i-frames, web reach, climb
  speed, stamina regen, flight, reload speed, free roll on kill).

## Downed, not dead
- **Going down:** `ServerLivingEntityEvents.ALLOW_DEATH` for a `ServerPlayer` in an active run (any phase except
  `NOT_STARTED`/`OVER`, not creative/spectator): cancel death, set health to 1, `setDowned(true)`, fire `DOWNED`.
  Damage tagged `bypasses_invulnerability` (void, `/kill`) is a real vanilla death; on that death clear the downed
  flag and boons.
- **While down:** invulnerable (cancel damage in `ALLOW_DAMAGE` except bypasses_invulnerability), forced crawl pose
  (`Pose.SWIMMING`; find how vanilla decides the pose and mixin there), movement speed −85% (attribute modifier),
  can't attack, use items, place/break blocks or open containers (Fabric's attack/use/break callbacks → FAIL),
  dismounted from vehicles. Hostile mobs drop a downed player as target and won't re-target them.
  No bleed-out timer: you stay down until revived.
- **Going down costs your boons** (see below). Gear is kept.
- **Revive:** a teammate holds use on the downed player for **3 s** (config) within 2.5 blocks. Client: while the
  crosshair is on a downed player and use is held, send start/stop payloads; the server checks distance + line of
  sight every tick and cancels if they move away. Both players see a progress bar. Revived: health 6 (config),
  downed off, 3 s of Resistance II, fire `REVIVED(player, reviver)`.
- **Team wipe:** after any player goes down, if every online player in the run (survival/adventure, any
  dimension) is down → `RunSystem.endRun(server)`. Solo: going down ends the run. Players who disconnect while
  down stay down when they rejoin; disconnected players don't count toward a wipe.

## Boons (roguelike run rewards)
- **Relic:** at `RunEvents.DAWN`, every online, non-downed player in the run gets a relic item dropped at their feet
  (tier rolled, see below). Using it opens a 3-card screen; picking one grants the boon and consumes the relic.
  Unused relics keep (they're items). Cards are rolled server-side when the relic is used; the client only
  displays them and sends the chosen index.
- **Tiers:** Common / Rare / Legendary. Relic tier odds by `RunState.wave`: waves 1–3: 85/15/0; 4–6: 65/30/5; 7+:
  45/40/15 (config). A card slot is one boon of the relic's tier the player doesn't have yet (or can stack).
- **Wildcard:** 10% (config) of relics replace one card with a Wildcard that opens a list of every boon of that
  tier to pick from.
- **Lost on going down:** clear all boons (effects, attribute modifiers, traits, granted items stay if they're
  normal items, e.g. a granted Elytra is kept as gear — decide and log).
- **Data-driven:** one JSON per boon in `data/blocksouls_game/boons/` loaded by a server reload listener
  (find the 26.3 Fabric resource-reload API in `fabric-src`). Fields: `tier`, `icon` (item id), `stackable`
  (max level), `requires` (list of boon ids, for combos), and an `effects` list where each entry is one of:
  - `{"type": "effect", "effect": "minecraft:speed", "amplifier": 0}` — permanent while held (reapply if removed,
    e.g. by milk; hide particles);
  - `{"type": "attribute", "attribute": "minecraft:jump_strength", "amount": 0.5, "operation": "add_multiplied_base"}`;
  - `{"type": "trait", "trait": "blocksouls:double_jump", "level": 1}`;
  - `{"type": "item", "item": "minecraft:elytra"}` (granted once).
- **Ship ~20 boons** (names original, tooltips state mechanics only), e.g.:
  - Common: Swift Feet (Speed I), Spring Legs (+50% jump), Iron Skin (+4 max health), Deep Lungs (+20% stamina
    regen), Night Eyes (night vision), Quick Hands (+25% reload speed), Steady Climber (+25% climb speed),
    Lucky (+1 luck).
  - Rare: Strength I, Fire Ward (fire resistance), Featherfall (slow falling), Long Thread (+25% web reach),
    Second Wind (free roll on kill), Hollow Bones (double jump), Slippery (+2 roll i-frame ticks), Haste II.
  - Legendary: Regeneration I, Wings (flight trait), Sky Rider (free Elytra), Juggernaut (+10 max health +
    Resistance I).
  - **Combos (2):** Spring Legs + Featherfall → Moon Boots (Jump Boost II + no fall damage via attribute
    `safe_fall_distance`); Swift Feet + Second Wind → Afterimage (Speed II + roll i-frames). Combo cards only appear
    when the requirements are owned; owning a combo removes its parts from the HUD row but keeps their effects.
- Persist owned boons in a player attachment (persistent; not copied on death).

## HUD (client, `GameHud`, register via `HudElementRegistry`)
- **Top center:** wave number (big numeral), phase as an icon (sun / moon / crossed swords for a wave / hourglass for
  a break), countdown `m:ss` while counting down, mobs-left count during a wave. From `ClientRun.get()`.
- **Boons row:** small icons above the hotbar (left side), tier-colored border, level pips; hover not needed.
- **Downed:** your own screen: dark red vignette, desaturated feel, short mechanics line ("Downed — a teammate can
  revive you"), revive progress bar when someone is reviving you.
- **Teammates down:** an icon + distance over each downed teammate (in-world marker visible through walls), and a
  revive progress bar while you revive someone.
- Hidden when F1 hides the HUD. All toggles in a client config section.

## Debug commands
`down <player>`, `revive <player>`, `boon give <player> <id>`, `boon clear <player>`, `boon list [player]`,
`relic <player> [tier]`.

## Tests
- Unit: tier odds by wave, card rolling (no duplicates, respects `requires`, stacking caps, wildcard chance),
  boon JSON parsing (valid + malformed files).
- Gametests: lethal damage downs a mock player instead of killing; void damage still kills; a downed player takes
  no damage; reviving after 60 ticks restores them; all players down calls the run-over path (assert `Runs.get`
  phase is `OVER` only if game-loop's code is present — otherwise assert your wipe-detection method returns true);
  granting a boon applies its effect/attribute/trait; going down clears them.

## Done when
CI green; in `runClient` against `runServer` with two clients (or one + a mock): go down, get revived, wipe ends the
run; relic at dawn → pick a boon → it works → go down → it's gone; HUD shows wave/timer/boons/downed; `docs/decisions/game-players.md`.

## Out of scope
Wave scheduling, day/night, run summary (game-loop). Movement/gun implementations of traits (those modules).
