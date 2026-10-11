# Decisions: mobs (Stage 1)

Defaults picked by the `mobs` session. Same rules as `DECISIONS.md` (the root file wins on conflict).

## Entities
- **Our own base classes, not vanilla `Zombie`.** `HostileMob extends Monster` (downed players are never valid
  targets: `canAttack`, `setTarget` and a per-tick check all refuse them) and `WaveMob extends HostileMob` (wave number,
  block-breaker flag, a synced "action" that renderers animate). Extending `Zombie` would drag in drowning
  conversion, babies, reinforcements and door breaking that fight the wave design.
- **Daylight burning comes from the vanilla `minecraft:burn_in_daylight` entity tag** (26.3's `Mob#aiStep` burns
  exactly those). It lists the five ordinary wave mobs; the Mutant Brute is left out, so it survives dawn. The six wave
  mobs and the Infected Piglin are also in `minecraft:undead` (Smite, inverted healing).
- **Wave mobs never pick up items**, so the game's wave drop tables stay the only reward.
- **Factory:** creates the mob at the block center with a random yaw, applies `WaveContext.wave`/`breaksBlocks`, and
  calls `finalizeSpawn(..., EntitySpawnReason.EVENT, ...)`. It doesn't touch `nightModifiers`, health or damage; the
  game applies those (brief: "don't rescale health yourself").
- **Hitboxes:** Shambler 0.6×1.95, Crawler 0.9×0.8, Screamer 0.5×2.0, Bloater 1.2×1.7, Brute 1.3×2.6, Mutant Brute 1.5×3.0,
  Infected Piglin 0.6×1.95, Ash Hound 0.7×0.75. Brute 60% and Mutant 80% knockback resistance.
- **Stats not in the brief** (all in `mobs.json`): Screamer 2 damage (it never melees; it keeps 8 blocks away),
  Bloater 0 melee damage (its attack is the burst), speeds Screamer 0.25 / Bloater 0.18 / Brute 0.2 / Mutant 0.24 /
  Infected Piglin 0.3 / Ash Hound 0.38, armor Shambler 2 / Brute 6 / Mutant 8 / Infected Piglin 2, Ash Hound 4 damage,
  Infected Piglin 4 + its gold sword.
- **Crawler leap:** within 4 blocks, 0.4 up / 0.9 forward, 2 s cooldown. Ash Hounds use the same pounce (5 blocks).
- **Screamer:** screams at its target the first time it sees them and every 15 s after; wave mobs (by the
  `#blocksouls:wave_mobs` tag) within 24 blocks get Speed I for 8 s and switch to that target. Its targeting goal is the
  normal "nearest player", so "on first seeing a player" means once it has a target in line of sight.
- **Bloater:** starts swelling at 2.5 blocks; deflates if the target gets more than 5 blocks away before the halfway
  point, after which it's committed. Burst: 6 damage falling to 3 at the 3-block edge, 0.4 knockback, poison cloud
  (radius 2.5, 6 s, Poison I 5 s). No `Explosion` is created, so terrain is never touched. Wave mobs aren't hurt by it.
- **Brute slam:** at 3.5 blocks, 1 s windup (both fists overhead), 4-block shockwave dealing its attack damage (halving
  at the edge) with knockback 1.2, 5 s cooldown.
- **Mutant Brute:** leap slam when the target is 6–16 blocks away: a 1.25 s windup with a rad-green dust ring where it
  will land, a ballistic jump, a slam on landing (radius 4, attack damage, knockback 1.5), 8 s cooldown; no fall damage.
  Throws a debris chunk (6 damage, its own `blocksouls_mobs:debris` projectile, never breaks blocks) at targets ≥ 5 blocks
  away every 3.5 s. Melee otherwise.
- **Shockwaves and bursts don't hurt other wave mobs** (no friendly fire inside a horde).

## Block breaking
- **"Stuck" = not getting closer.** A breaker counts as stuck once its distance to the target hasn't improved by
  0.5 blocks for `stuckTicks` (2 s). That covers both a blocked path and a mob pressed against a wall, and isn't fooled
  by idle wandering.
- **"The block in its way"** is found by ray-casting from the mob toward the target at feet, body and (for tall mobs)
  head height, out to 1.5 blocks past its hitbox. Only `#blocksouls:wave_breakable` blocks with a finite hardness count.
- **Break time** is linear from 8 s at wave 1 to 3 s at wave 10 and stays there, divided by the mob's break speed
  (Brute and Mutant ×2). Crack stages are shown with `destroyBlockProgress`; blocks drop as items (`destroyBlock(pos,
  true, mob)`). `mob_griefing` off stops it.
- **`#blocksouls:wave_breakable`** = planks, wooden slabs/stairs/fences, all fence gates, all doors and trapdoors (iron
  ones too: players build them), wool, glass, tinted glass, stained glass and panes, and the cobblestone, mossy
  cobblestone, stone brick (all variants) and brick families with their slabs/stairs/walls. Listed by vanilla tags and
  ids, no convention tags.

## Nether
- **Infected Piglins replace a share by spawn weight.** In each Nether biome that spawns piglins or zombified piglins,
  every such entry gives `replaceShare` (30%) of its weight to an Infected Piglin entry with the same group size; the
  rest stays. Biomes without piglins (soul sand valley, basalt deltas, warped forest) get a flat weight of 10, groups of
  1–3. Data-level (Fabric biome modifications), no mixins. Fire-immune.
- **Dread** is a saved player attachment (`blocksouls_mobs:dread`, survives death) holding only the meter. Pack timers
  live in memory, so a restart counts as a fresh visit.
- **"Leaving resets the pack timer, not the meter":** the first pack comes the moment dread crosses 240; then one every
  120 s in the Nether, each pack one hound bigger (3–5 base, capped at 9). Leaving resets the timer and the growth.
  Coming back already above 240 means the next pack after a full 120 s.
- **Howls** start at 120 dread: on average every 25 s, a far-off howl (re-pitched wolf whines) from a random direction,
  sent only to that player and louder as dread grows. **Ash particles** (white ash, then ash past 60%) thicken around
  the player from 60 dread, again only for them. Nothing in any HUD.
- **Packs** spawn 20–30 blocks away on solid ground (searched ±8 blocks vertically, 20 tries; no spot = no pack that
  time), already targeting the player. Ash Hounds have a 64-block follow range, despawn like normal monsters and are
  fire-immune. Creative/spectator players don't build dread.

## Art and sound
- **Models are our own Java `LayerDefinition`s**, no vanilla bodies: Shambler (hunched, long dragging arm), Crawler
  (low torso, four splayed limbs), Screamer (gaunt, hinged jaw), Bloater (swelling sack body), Brute (huge shoulders,
  knuckle arms, tiny head), Mutant Brute (Brute rig + spines and growths, drawn 1.35×, emissive veins), Infected Piglin
  (piglin head on a humanoid rig with fleshy growths, so it holds its sword), Ash Hound (lean, emissive eyes).
  Telegraphs are animated from the synced action: swell, slam windup, leap crouch, throw, jaw open.
- **Placeholder skins** come from `tools/assets/mobs.py`, which paints every face of each model's UV boxes (palettes:
  rot for Shambler/Bloater, ash for Crawler/Ash Hound, bone for Screamer, new `flesh` for the Brute, rad for the
  Mutant, blight for the Infected Piglin). Added palettes `rot` and `flesh` to `gen.py`.
- **Spawn eggs** have their own 16×16 textures (26.x eggs aren't tinted) and sit after the zombified piglin egg in
  the Spawn Eggs tab.
- **Sounds:** our own `SoundEvent` ids (`blocksouls_mobs:entity.<mob>.<sound>`, `ambient.dread.howl`) whose
  `sounds.json` entries point at re-pitched vanilla *events* (`"type": "event"`), so real sounds drop in at the same ids.

## Debug commands
- `/bs spawn <mob> [count] [wave] [breaker]` takes short names (`shambler` … `ash_hound`); wave mobs go through their
  registered factory with a fake `WaveContext` (normal difficulty, no night modifiers), the Nether mobs are spawned
  directly. `/bs dread get|set <player> [value]`, `/bs mobs list`.

## Tests
- Mock game-test players are always in creative, which vanilla refuses as a mob target, so tests aim mobs at a
  no-AI pig and check downed handling through `canAttack`.
- "Elites don't burn, Shamblers do" is checked through the `burn_in_daylight` tag (the exact vanilla mechanism);
  a real sunlit test would depend on the game module's day length.
