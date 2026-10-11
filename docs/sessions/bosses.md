# Session: bosses (Stage 2a)

**Module:** `bosses`. **Branch:** `claude/bosses`. **PR title:** `bosses: framework, boss bar, lock-on, Furnace Knight`.

You build the boss framework every boss uses (field bosses and the final raid), the custom boss health bar,
lock-on, the field-boss spawner, and the first boss: the **Furnace Knight**. Stage 2b sessions build the other three
field bosses and the final raid **on your framework in parallel**, so its API must be clear and documented. Read
`docs/sessions/README.md`, PLAN.md §0b and §6 (D1, D2b) first; this brief wins where they differ.

## You own
Everything under `bosses/` for now. When you're done, write `bosses/README.md`: how to add a boss (entity, phases,
attacks, arena, model, loot, spawner entry), so 2b sessions can each work in their own package
(`bosses/<boss>/`, `bosses/raid/`) and touch shared files only to register.

## Contracts
- Boss ids in `CommonIds`: `FURNACE_KNIGHT`, `PALE_RIDER`, `ASHWYRM`, `STORMPLATE_KNIGHT`, `FINAL_BOSS`. Fill the
  entity tag `blocksouls:bosses` (`CommonTags.BOSSES`).
- Fire `BossEvents.ENGAGED` when a fight starts and `BossEvents.KILLED` (with every participant) on death.
- Loot tables may use `blocksouls_guns:roll_perks` (`CommonIds.ROLL_PERKS`) with `"tier": "legendary"` on gun
  entries, and items by id from other modules (`blocksouls_world:blight_core`, guns' gun ids).
- Downed players (`PlayerStatus.isDowned`) aren't targets and don't count as "in the fight" for resets.
- Bosses don't rescale with `Runs` difficulty except: health ×(1 + 0.5·(players in fight − 1)) and
  ×`difficulty.mobHealth` (read via `Runs.get(server)`), damage ×`difficulty.mobDamage`.

## Framework (D1)
- **`BossEntity`** base (extends a vanilla `Monster`/`PathfinderMob`): a `BossDefinition` (id, name, base health,
  phases, music, arena radius) and a **phase machine**: each phase = health threshold + a weighted attack list +
  optional on-enter hook (arena change, adds, buff). Phase transitions play a stagger (short invulnerable roar).
- **`Attack`** = windup ticks (telegraph: particles, sound, a ground decal/circle or line drawn with particles) →
  active ticks (hitboxes/damage) → recovery ticks (punish window). Weighted random pick with per-attack cooldowns and
  range requirements (melee/mid/ranged). Telegraphs must be readable: every attack that can deal >30% of a player's
  health has ≥ 0.75 s of windup.
- **Targeting:** among non-downed engaged players; switches on aggro rules (damage dealt, proximity), not every tick.
- **Engage / arena:** a boss is engaged when a player hits it or comes within its arena radius. Then a **fog wall**
  (a ring of translucent wall blocks or a barrier entity ring — pick what renders well) closes: passable outward
  never, inward always (late friends can join), removed when the boss dies or the fight resets. **Reset:** all
  engaged players dead/downed/left the arena for 10 s → the boss walks home, heals to full, the wall drops.
- **Never despawn mid-fight**; persistence always on. Field bosses that nobody engages leave after their timeout
  (below).
- **Boss health bar (custom HUD, client):** synced from the server (keep a vanilla `ServerBossEvent` only as the
  source of truth if useful, but don't render vanilla's bar for our bosses). Wide styled bar at top center, boss
  name, a trailing "damage taken" chunk that drains after 0.6 s, phase notches at thresholds, subtle shake + flash
  on phase change, stacked for up to 2 bosses. Shown to engaged players + anyone within 48 blocks.
- **Lock-on (client):** middle mouse (keybind via `Keybinds`) toggles lock on the nearest boss or elite in view
  within 32 blocks; camera yaw/pitch lerp toward the target's chest each frame (soft, 0.25), a reticle on the target,
  breaks on line-of-sight loss for 1 s, distance > 40, or key press. Scroll or the key again cycles targets.
- **Multipart hitboxes** (weak points like a dragon head): provide an optional helper on top of vanilla's part-entity
  mechanism (see `EnderDragon` parts) with a damage multiplier per part.
- **Models:** vanilla Java models (`LayerDefinition`/`ModelPart`) with keyframe animations (vanilla's
  `AnimationDefinition`/`KeyframeAnimations`, or whatever 26.3 calls them — check the source), **no GeckoLib**.
  Provide a small base renderer that plays an animation per attack phase (windup/active/recovery) so bosses only
  supply keyframes. Custom models only; no reskinned vanilla bodies.
- **Music:** a boss music sound event per boss (placeholder: a vanilla music disc track or music event re-pitched).

## Field-boss spawner (D2b)
- Per-run pool of the four field bosses. Each kind has a spawn rule (where/when), a chance per in-game day check
  (config), a cooldown, and a "leave after N minutes unengaged" timeout (default 10 min of no player within 96
  blocks). At most one roaming field boss at a time; never within 200 blocks of world spawn by default; never during
  run phase `NOT_STARTED`/`OVER`. Killed bosses don't respawn in the same run (track in SavedData).
- Announce through the world, not text: a distant sound cue + a visible sign (smoke column, lightning, a beam) near
  the boss; its name appears only on the boss bar when you're close.
- Register spawn rules through a small API so 2b sessions add theirs (`FieldBosses.register(id, rule)`).

## Furnace Knight (first boss; proves the framework)
- **Where:** guards the first Nether **bastion** players approach (spawns when a player comes within 64 blocks of a
  bastion's center while no Furnace Knight has been killed this run; stands at the bastion's entrance). Find how to
  detect the bastion structure (`ServerLevel.structureManager()`).
- **Look:** a 3.2-block armored knight whose helmet is a furnace grate glowing from inside, a huge cleaver-sword;
  embers drift off it. Palette `ember` + dark steel.
- **Stats:** 400 HP (scaled as above), armor 10, knockback-immune, fire-immune.
- **P1 (100–50%):** *Overhead slam* (1 s windup, line shockwave 8 blocks of fire particles, 14 dmg), *Sweeping cut*
  (0.8 s windup, 270° arc, 10 dmg, jump or roll), *Furnace breath* (1.2 s windup, visor opens, 5-block cone of fire for
  2 s, sets on fire), *Charge* (1 s windup, dashes 12 blocks in a line, 12 dmg).
- **P2 (<50%):** roar stagger; blade ignites (+25% damage, faster recovery), adds *Fire pillars* (3–5 telegraphed
  circles under players for 1.5 s, then 10 dmg pillars). Shorter cooldowns.
- **Drops:** 4 blaze rods, **2 Ender Eyes**, 1 `blocksouls_world:blight_core`, one legendary gun (guns' ids +
  `roll_perks`), XP. Configurable.

## Debug commands
`boss spawn <id>` (in front of you, engaged=false), `boss phase <n>`, `boss health <percent>`, `boss reset`,
`boss field roll` (force a field-boss spawn check near you), `boss list`.

## Tests
- Unit: phase selection by health, weighted attack pick with cooldowns, health scaling by players/difficulty,
  aggro target choice, boss bar trailing-damage math.
- Gametests: the phase machine walks P1 → P2 with scripted health and never softlocks (no attack stuck); engaging
  closes the fog wall and killing removes it; reset heals and drops the wall; `BossEvents.KILLED` fires with the
  participants; the Furnace Knight drops its loot.

## Done when
CI green; `/bs boss spawn furnace_knight` gives a fair, readable ~3–5 minute fight for 1–2 players with Stage 1 guns
and movement; custom boss bar and lock-on work; `bosses/README.md` explains how to add a boss;
`docs/decisions/bosses.md`.

## Out of scope
Pale Rider, Ashwyrm, Stormplate Knight, final raid (Stage 2b sessions).
