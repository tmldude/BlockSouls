# bosses: how to add a boss

The `bosses` module (`blocksouls_bosses`) holds the boss framework, the custom boss bar, lock-on, the field-boss
spawner and the bosses themselves. The **Furnace Knight** (`furnace/`, `client/furnace/`) is the reference boss: copy
its shape. Everything below is what a Stage 2b session (Pale Rider, Ashwyrm, Stormplate Knight, final raid) needs.

## Where your code goes

Work in your own packages and touch shared files only to register:

| What | Path |
|---|---|
| Server: entity, attacks, config, sounds, spawn rule, registration | `bosses/src/main/java/.../bosses/<boss>/` (e.g. `palerider/`, `ashwyrm/`, `stormplate/`, `raid/`) |
| Client: model, animations, renderer registration | `bosses/src/client/java/.../bosses/client/<boss>/` |
| Shared (one line / a few entries each) | `BlockSoulsBosses.onInitialize` and `BlockSoulsBossesClient.onInitializeClient` (the "Bosses: one line each" blocks), `assets/blocksouls_bosses/lang/en_us.json`, `assets/blocksouls_bosses/sounds.json`, `data/blocksouls/tags/entity_type/bosses.json` |
| Data | `data/blocksouls_bosses/loot_table/entities/<boss>.json`, `.../boss_drops/<boss>_*.json` |
| Art | `assets/blocksouls_bosses/textures/entity/<boss>*.png`, `textures/item/<boss>_spawn_egg.png`; generator: add a function to `tools/assets/boss_skins.py` |
| Tests | add `@GameTest` methods to a new class in `bosses/src/gametest/java/.../test/` and list it in `bosses/src/gametest/resources/fabric.mod.json` |
| Decisions | `docs/decisions/boss-<name>.md` |

Don't edit the framework (`api/`, `arena/`, `bar/`, `field/`, `logic/`, `client/render/`, `client/bar/`,
`client/lockon/`) unless you must; if you do, keep existing signatures and say so in your PR.

## The pieces

```
api/BossEntity       base entity: phases, attack state machine, aggro, engage/fog wall/reset, scaling, events
api/BossDefinition   static description of a boss kind: id, health, arena radius, music, phases, extra loot
api/BossPhase        health threshold + attack list + multipliers + optional on-enter hook
api/Attack           one move: windup → active → recovery, hooks receive an AttackRun
api/AttackStats      the tunable numbers of an attack (embed in your config class)
api/AttackRun        one execution: stage clock, target, origin/direction, scratch points, damage helpers
api/Hitboxes         who an area touches (circle, line, arc/cone, box), already filtered to valid victims
api/Telegraphs       forced particle shapes for telegraphs (circle, line, arc, column, and "filling" variants)
api/BossPart         weak-point hitboxes (vanilla dragon-part mechanism), with a damage multiplier
api/Bosses           registers the entity type + spawn egg; Bosses.attributes(...) for base attributes
field/FieldBosses    the field-boss spawner; register a FieldBossRule
client/render/BossModel, BossRenderer, BossRenderState, Anim   model base with keyframes per attack stage
```

## Step by step

Ids for the four bosses already exist in `CommonIds` (`PALE_RIDER`, `ASHWYRM`, `STORMPLATE_KNIGHT`, `FINAL_BOSS`).
The id is also the entity id (`blocksouls_bosses:pale_rider`).

### 1. Config

One file per boss, `config/blocksouls/boss-<name>.json`, loaded in your registration class:

```java
public class PaleRiderConfig {
	public double health = 350;
	public double armor = 6;
	public double speed = 0.3;
	public int arenaRadius = 22;
	public AttackStats lance = new AttackStats(18, 4, 20, 60, 0, 5, 3, 12); // windup, active, recovery, cooldown, minRange, maxRange, weight, damage
	public FieldBossSettings field = new FieldBossSettings(0.35, 20 * 60 * 10, 20 * 60 * 10); // chance/day, cooldown, leave-after
}
```

Ticks are 1/20 s. Damage is at normal difficulty in phase 1; the framework multiplies by the phase's `damage(...)`
and the run's `difficulty.mobDamage`. **Rule (tested):** any hit that can deal more than 30% of a player's health (6)
at its strongest phase needs `windupTicks >= 15` (0.75 s). `BossesGameTests.everyBigHitIsTelegraphed` checks every
registered boss, so you get a failing test, not a balance bug.

### 2. Attacks

Subclass `Attack` and override the hooks you need. Hooks run on the server; the boss advances the stages for you, so
an attack can never get stuck (an attack can only end early, via `run.endActive()`).

```java
final class Lance extends Attack {
	Lance() { super("lance", PaleRiders.CONFIG.lance); }        // the name is also the animation key

	@Override public void start(AttackRun run) { run.sound(PaleRiderSounds.WINDUP, 1.5f, 1f); }

	@Override public void windup(AttackRun run) {               // telegraph; run.progress() goes 0 → 1
		if (run.tick() % 3 == 0) {
			Telegraphs.lineFilling(run.level(), OUTLINE, FILL, run.boss().position(), run.direction(), 6, 0.8, run.progress());
		}
	}

	@Override public void active(AttackRun run) {               // hitboxes
		for (LivingEntity victim : Hitboxes.inLine(run.boss(), run.boss().position(), run.direction(), 6, 0.8, 2)) {
			run.hurt(victim);                                       // once per run per victim, damage scaled for you
		}
	}
}
```

- `run.direction()` faces the target and keeps tracking it for the first `trackFraction()` (70%) of the windup, then
  locks so a late dodge works. Override `trackFraction()` per attack.
- `run.hurt(victim[, baseDamage])` hits once per run; `run.hurtEvery(victim, baseDamage, intervalTicks)` for lingering
  areas (breath, pillars). Both apply difficulty and phase multipliers. Use `run.scaled(base)` for anything custom.
- `run.points()` is a free list for positions picked at `start` (e.g. where pillars will rise).
- `canUse(boss, target)` adds conditions on top of range and cooldown (line of sight, flying, ...).
- Movement during an attack is yours (`boss.setDeltaMovement(...)`, `boss.face(dir)`); the boss stops navigating
  while an attack runs. Clean up in `end(run)`, which always runs, also when the fight resets or the boss dies.
- `Hitboxes.*` only return valid victims: players who aren't downed/spectating, plus the boss's current target.
- Telegraph particles are "forced": they render at any distance and with reduced particles, so they're always
  readable. Draw every 2–3 ticks, not every tick.

### 3. Entity + definition

```java
public class PaleRider extends BossEntity {
	private static BossDefinition definition;

	public PaleRider(EntityType<? extends PaleRider> type, Level level) {
		super(type, level);
		// Optional weak point: name, width, height, damage multiplier, offset (x = left, y = up, z = forward).
		addPart("skull", 0.8f, 0.8f, 1.5, new Vec3(0, 3.1, 0.3));
	}

	static BossDefinition createDefinition() {
		Lance lance = new Lance();
		Volley volley = new Volley();
		definition = BossDefinition.builder(CommonIds.PALE_RIDER)
				.health(PaleRiders.CONFIG.health)
				.arenaRadius(PaleRiders.CONFIG.arenaRadius)
				.music(PaleRiderSounds.MUSIC)
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/pale_rider_guns"))   // optional, see Loot
				.phase(BossPhase.first().attacks(lance, volley))
				.phase(BossPhase.below(0.4).attacks(lance, volley, new Phantoms())
						.damage(1.2).cooldowns(0.75).recovery(0.8).speed(1.2)
						.onEnter(boss -> { /* change the arena, summon adds, ... */ }))
				.build();
		return definition;
	}

	@Override public BossDefinition definition() { return definition; }

	@Override protected SoundEvent getAmbientSound() { return PaleRiderSounds.AMBIENT; }
	// hurt/death/step sounds; staggerSound() for the phase-change roar
}
```

Useful overrides: `preferredDistance()` (how close it walks before stopping, default 3), `fightTick(level, target)`
(every tick between attacks, e.g. for a flying boss's own movement), `onEngage()`, `onReset()`, `onPhaseEnter(i)`,
`canHarm(entity)`, `targetable(player)`. Everything else (aggro, phases, stagger, reset, despawn rules, bar sync,
events) is the base class.

### 4. Registration

```java
public final class PaleRiders {
	public static PaleRiderConfig CONFIG = new PaleRiderConfig();
	public static EntityType<PaleRider> TYPE;

	public static void register() {
		CONFIG = Config.load("boss-pale_rider", PaleRiderConfig.class, PaleRiderConfig::new);
		PaleRiderSounds.init();
		PaleRider.createDefinition();                              // after the config, before the type
		TYPE = Bosses.register(CommonIds.PALE_RIDER, PaleRider::new, new Bosses.Size(1.6f, 3.0f, 2.7f), false,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, 1));
		FieldBosses.register(CommonIds.PALE_RIDER, new PaleRiderSpawnRule());
	}
}
```

Then one line in `BlockSoulsBosses.onInitialize`: `PaleRiders.register();`. `Bosses.register` makes the entity type,
a spawn egg (`<id>_spawn_egg`, needs `items/`, `models/item/`, a texture and a lang entry), and lists the boss for
`/bs boss spawn`. Add the id to `data/blocksouls/tags/entity_type/bosses.json` (guns' boss perks and lock-on use the
tag).

Sounds: `BossSounds.register("entity.pale_rider.ambient")` → `blocksouls_bosses:entity.pale_rider.ambient`; add the
`sounds.json` entry (placeholder: a re-pitched vanilla event, `"type": "event"`) and a subtitle in the lang file.
Music: `BossSounds.register("music.<boss>")`, a `sounds.json` entry without a subtitle; it loops for players in the
fight (client handles it).

### 5. Model and animations (client)

Vanilla Java models (`LayerDefinition`) and vanilla keyframes, no GeckoLib. Extend `BossModel`; build the mesh like
any vanilla model, then return keyframes per attack and stage. `BossModel` bakes and plays them from the synced stage.

```java
public class PaleRiderModel extends BossModel {
	@Override protected void idle(BossRenderState state) { /* walk cycle, breathing; runs every frame first */ }

	@Override protected @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage) {
		if (attack == null) return stage == Stage.STAGGER ? ROAR : null;      // phase-change stagger
		return switch (attack) {
			case "lance" -> switch (stage) {
				case WINDUP -> Anim.of(w).rot("right_arm", 0, 0, 0, 0, w, -120, 0, 0).build();
				case ACTIVE -> ...;
				case RECOVERY -> ...;                                           // ease back to the neutral pose
				default -> null;
			};
			default -> null;
		};
	}
}
```

- `Anim.of(seconds).rot(bone, t, x, y, z, t, x, y, z, ...)` / `.pos(...)` is shorthand for vanilla's
  `AnimationDefinition` (degrees / pixels, offsets on top of the pose). Time 0 = the stage start; derive lengths from
  your `AttackStats` (`windupTicks / 20f`) so tuning the config keeps animations in sync. Recovery animations should
  start from the pose the active one ended in.
- `BossRenderState` has `stage`, `attack`, `stageSeconds`, `phase`, `engaged` plus all vanilla living-entity state.
- Register in a `<Boss>Client.register()` and call it from `BlockSoulsBossesClient`:

```java
ModelLayerRegistry.registerModelLayer(LAYER, PaleRiderModel::createBodyLayer);
EntityRendererRegistry.register(PaleRiders.TYPE, ctx ->
		new BossRenderer<>(ctx, new PaleRiderModel(ctx.bakeLayer(LAYER)), 1.2f, "pale_rider", 1.0f)  // shadow, texture name, scale
				.glow("pale_rider_glow", state -> true)                                             // full-bright layers
				.glow("pale_rider_enraged_glow", state -> state.phase >= 1));
```

Client-side effects (embers, mist) go in your entity's `tick()` under `level().isClientSide()`.

### 6. Arena

Set `arenaRadius` in the definition (or per spawn: `FieldBossRule.Spot(..., arenaRadius)`, `boss.setArenaRadius`).
On engage the framework raises a one-way fog wall ring of that radius around `boss.home()`: only air and replaceable
plants become wall, and it remembers exactly which blocks it placed. Arena changes for a phase go in the phase's
`onEnter` hook; undo them in `onReset()`.

### 7. Loot

`loot_table/entities/<boss>.json` is rolled by vanilla on death; keep it to vanilla items (and your own).
**26.3 loot format:** entries and pools take one `"modifier"` (a single function object; chain several with
`minecraft:sequence`) and one `"condition"`. The old `"functions"` / `"conditions"` lists are silently ignored, so a
`set_count` written that way just drops 1 item.
**Items from other modules go in separate tables** with a load condition, listed via `.extraLoot(...)`:

```json
{ "fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["blocksouls_guns"] }],
  "type": "minecraft:entity", "pools": [ ... ] }
```

Never reference such a table from the main one (`"type": "minecraft:loot_table"`): if the module is missing, the
reference is unbound and **the whole loot registry fails to load**. `boss_drops/legendary_gun` (one random gun, rolled
with `blocksouls_guns:roll_perks` at `"tier": "legendary"`) is shared; any boss can list it.

### 8. Field spawn rule

```java
final class PaleRiderSpawnRule implements FieldBossRule {
	public Schedule schedule() { return Schedule.DAILY; }          // or WATCH: checked every scan, no roll
	public FieldBossSettings settings() { return PaleRiders.CONFIG.field; }
	public BossEntity create(ServerLevel level) { return PaleRiders.TYPE.create(level, EntitySpawnReason.EVENT); }
	public Optional<Spot> find(ServerLevel level, ServerPlayer player, RandomSource random) {
		// overworld, night, plains biome, a standable spot 40-60 blocks from the player ... else Optional.empty()
	}
	// roaming() defaults to true; return false for bosses tied to a place (they don't block roaming bosses)
}
```

The spawner enforces: active run only, killed-this-run never returns, one roaming field boss at a time, per-boss
cooldown after it leaves, ≥ 200 blocks from world spawn (overworld), the arrival cue (far-off horn toward it, a
lightning flash, then a smoke column while nobody engages), and the leave timeout (`leaveAfterTicks` with no player
within 96 blocks). The final raid boss isn't a field boss: don't register a rule; spawn it from your own trigger.

## Contracts the framework keeps for you

- `BossEvents.ENGAGED` when a fight starts and `BossEvents.KILLED` with every participant on death, with your boss
  id.
- Health = base × (1 + 0.5 per extra player in the fight) × `difficulty.mobHealth`; damage × `difficulty.mobDamage`.
  The player count is the most players seen in the fight so far (leaving doesn't weaken the boss).
- Downed players are never targets, can't be hit by `Hitboxes`, and don't keep a fight alive. Everyone dead / downed /
  out of the arena for 10 s → reset: wall down, full heal, walk home.
- Persistent, never despawns mid-fight; damage from outside the arena while engaged is ×0.25 (no sniping through the
  fog); immune to fall, suffocation, cramming and drowning; knockback-immune via `Bosses.attributes`.
- A restart mid-fight resets the fight (the saved wall is removed on load, health restored).

## Debug commands

`/bs boss spawn <id>` (8 blocks in front of you, not engaged), `/bs boss phase <n>`, `/bs boss health <percent>`,
`/bs boss reset`, `/bs boss field roll [id]` (forces a spawn check near you, ignoring the run phase, daily roll,
cooldowns and spawn distance), `/bs boss list`. Phase/health/reset act on the nearest boss within 64 blocks.

## Tests to copy

`bosses/src/gametest/.../BossesGameTests.java`: a scripted phase walk with an invulnerable no-AI pig as forced target
(`boss.forceTarget(pig)`; mock players are creative, so they're never targeted), fog wall up/down, reset, the KILLED
event, weak-point damage through the part mixins, and loot. `boss.attacksFinished()` counts completed attacks, which
is how the softlock test sees progress. Pure logic (`logic/`) is unit-tested in `src/test`.
