# Session: mobs (Stage 1)

**Module:** `mobs`. **Branch:** `claude/mobs`. **PR title:** `mobs: wave roster, Nether mobs, block breaking`.

You build the things that hunt players: the wave roster, the Mutant Brute elite, two Nether mobs, the hidden Nether
"dread" pressure, and slow block breaking. Read `docs/sessions/README.md` and PLAN.md §0b first.

## You own
Everything under `mobs/`.

## Contracts
- Register each wave mob with `WaveMobs.register(new WaveMobSpec(id, minWave, weight, elite, factory))` at init.
  The **factory** creates the entity at the position (don't add it to the level), calls `finalizeSpawn` with an
  event spawn reason, and reads `WaveContext` (`breaksBlocks`, `nightModifiers`, `wave`). The game module adds it,
  applies health/damage scaling and night modifiers, targets players and tracks it. Don't rescale health yourself.
- Entity type ids are fixed in `CommonIds` (`SHAMBLER`, `CRAWLER`, `SCREAMER`, `BLOATER`, `BRUTE`, `MUTANT_BRUTE`,
  `INFECTED_PIGLIN`, `ASH_HOUND`). Fill tags `blocksouls:wave_mobs` (the 6 wave types), `blocksouls:elites`
  (Mutant Brute) and the block tag `blocksouls:wave_breakable` (`CommonTags`).
- Loot: give each mob a vanilla-style loot table (rotten flesh, bones, a little gunpowder/iron nuggets); the game's
  wave drop tables are separate.
- Wave mobs don't spawn naturally. They must not despawn while their wave runs (game sets persistence; don't fight
  it). They burn in daylight like zombies **except** elites (the game also discards ordinary ones at dawn).
- Downed players (`PlayerStatus.isDowned`) are never valid targets.

## Roster (vanilla-tier numbers before wave scaling; all in config)
| Mob | minWave / weight | Body | Behavior |
|---|---|---|---|
| **Shambler** | 1 / 10 | humanoid, 1.95 tall | 20 HP, 3 dmg, slow (0.21), groans. The bread and butter. |
| **Crawler** | 2 / 5 | low, 0.8 tall, 0.9 wide | 12 HP, 2 dmg, fast (0.33), leaps at targets within 4 blocks. Hard to hit. |
| **Screamer** | 3 / 2 | thin humanoid | 14 HP; on first seeing a player it screams (sound + particles): wave mobs within 24 blocks get Speed I for 8 s and target that player; repeats every 15 s. Keeps 8 blocks away. |
| **Bloater** | 4 / 2 | wide, slow | 30 HP, swells up near players (1.5 s telegraph: inflates, hisses) then bursts: 6 dmg in 3 blocks + a lingering poison cloud. Also bursts on death. **No terrain damage.** |
| **Brute** | 5 / 2 | 2.6 tall, broad | 60 HP, 8 dmg, slow; ground slam (1 s windup, 4-block shockwave, knockback). Always breaks blocks, 2× speed. |
| **Mutant Brute** (elite) | 7 / 1 | 3 tall, glowing rad-green veins | 150 HP, 12 dmg; leap slam (telegraphed landing ring), throws chunks of debris. Always breaks blocks. Survives dawn. |

Nether (natural spawns, not wave mobs):
- **Infected Piglin:** a piglin overrun by the infection: always hostile, 24 HP, gold sword, fleshy growths. Spawns
  in all Nether biomes (biome modification), replacing a share of zombified piglins/piglins (config ~30%).
- **Ash Hound:** fast, low wolf-like hunter, 16 HP, packs of 3–5. Only spawned by dread (below).

## Dread (hidden Nether pressure)
Per player, time spent in the Nether accumulates dread (player attachment, not shown in any HUD). Rises 1/s, decays
2/s outside the Nether. Thresholds (config): 120 → distant howls; 240 → an Ash Hound pack spawns 20–30 blocks away
hunting that player; every 120 after that, another pack (+1 hound). Show it only through sound and ash particles
intensifying. Leaving resets the pack timer, not the meter.

## Block breaking
A goal for mobs whose `breaksBlocks` is true (and always for Brute/Mutant Brute): when the path to the target is
blocked or the mob is stuck for 2 s, it breaks the `#blocksouls:wave_breakable` block in its way (the one at its
feet/head level toward the target), showing crack progress (`destroyBlockProgress`) over **8 s** at wave 1 down to
**3 s** at wave 10+ (config). Fill the tag with player-buildable blocks: planks, cobblestone, stone bricks, bricks,
glass/panes, doors, trapdoors, fences, fence gates, wool, and slabs/stairs/walls of those. Never natural terrain
(logs, stone, dirt, grass, sand, gravel, deepslate, netherrack). Respect the `mob_griefing`
game rule. Blocks drop as items.

## Models and art
- Custom models in Java (`LayerDefinition` / `ModelPart`, vanilla animation helpers), **not** reused vanilla zombie
  bodies for Crawler, Bloater, Brute, Mutant Brute, Ash Hound; Shambler/Screamer/Infected Piglin may start from the
  humanoid/piglin layout with their own proportions. Renderers in `src/client`.
- Textures from `tools/assets/gen.py skin <palette>` (humanoid UVs) as a start; for non-humanoid layouts extend
  `gen.py` with a generic box-UV skin kind or write per-mob textures with PIL. Palettes: rot-green/`rad` for mutants,
  `blight` for Nether. Silhouettes must read at a distance: Crawler low, Bloater round, Brute huge.
  The `art-mobs` session polishes later; keep texture paths stable.
- Sounds: vanilla sound events (zombie, ravager, ghast, ...) re-pitched for now; register our own `SoundEvent` ids
  pointing at those via `sounds.json` so real sounds can drop in later.
- Spawn eggs for every mob (creative + testing).

## Debug commands
`spawn <mob> [count] [wave] [breaker]` (uses the registered factory with a fake `WaveContext`), `dread get|set
<player> <value>`, `mobs list`.

## Tests
- Unit: dread accumulation/thresholds, break-time formula.
- Gametests: every factory returns a valid mob of the right type; a breaker Shambler breaks a planks wall between it
  and a target within the expected time but not a stone wall; Bloater burst damages but doesn't break blocks;
  Screamer buffs a nearby Shambler; elites don't burn in daylight, Shamblers do.

## Done when
CI green; all 8 mobs spawn via `/bs spawn`, look distinct, behave as above; waves (when game is merged) use them;
`docs/decisions/mobs.md`.

## Out of scope
Wave scheduling/scaling (game-loop), bosses (bosses), final art (art-mobs).
