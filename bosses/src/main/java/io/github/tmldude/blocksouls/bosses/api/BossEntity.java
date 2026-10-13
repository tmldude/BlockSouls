package io.github.tmldude.blocksouls.bosses.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;
import io.github.tmldude.blocksouls.bosses.BossesConfig;
import io.github.tmldude.blocksouls.bosses.arena.ArenaSeal;
import io.github.tmldude.blocksouls.bosses.arena.FogWall;
import io.github.tmldude.blocksouls.bosses.bar.BossBars;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.bosses.logic.AggroTable;
import io.github.tmldude.blocksouls.bosses.logic.AttackPicker;
import io.github.tmldude.blocksouls.bosses.logic.BossScaling;
import io.github.tmldude.blocksouls.bosses.logic.PhaseMath;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.Runs;

/**
 * Base of every boss: phase machine, attack state machine, aggro, the engage/fog wall/reset cycle, health scaling,
 * the boss bar and the {@code BossEvents}. A boss subclass supplies its {@link BossDefinition} (phases and attacks)
 * and its sounds; everything else is here. See {@code bosses/README.md}.
 *
 * <p><b>Fight cycle.</b> Idle at {@link #home()} → a player hits it or walks within {@link #arenaRadius()} →
 * engaged: the fog wall closes and {@code BossEvents.ENGAGED} fires → attacks until dead ({@code BossEvents.KILLED}
 * with every participant, wall drops) or until nobody is left in the fight for {@code resetAfterTicks} → reset:
 * wall drops, full heal, walk home.
 */
public abstract class BossEntity extends Monster {
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_STAGE = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_ENGAGED = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_POSTURE = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_BROKEN_PARTS = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
	private static final net.minecraft.resources.Identifier SCALING_ID = CommonIds.id(CommonIds.BOSSES, "fight_scaling");

	private BossPart[] parts = new BossPart[0];

	// Client: when the synced stage last changed (drives animation time).
	private int stageStartTick;

	// Server state.
	private int phase;
	private @Nullable AttackRun run;
	private int[] cooldowns;
	private int gap;
	private int lastAttack = -1;
	private int stagger;
	private final Set<UUID> participants = new LinkedHashSet<>();
	private int mostPlayersInFight;
	private final AggroTable<UUID> aggro = new AggroTable<>();
	private @Nullable LivingEntity forcedTarget;
	private @Nullable BlockPos home;
	private int arenaRadiusOverride = -1;
	private final FogWall wall = new FogWall();
	private final ArenaSeal seal = new ArenaSeal(this);
	private boolean wallNeedsClearing;
	private int outOfFightTicks;
	private boolean returning;
	private int returnTicks;
	private boolean fieldBoss;
	private int leaveAfterTicks;
	private int unattendedTicks;
	private double damageMultiplier = 1;
	private boolean scalingApplied;
	private int attacksFinished;
	private boolean fogWallEnabled = true;

	// v2: posture, part breaks, presentation, reactions.
	private double poise;
	private int poiseIdleTicks;
	private int visceral;
	private int postureLockout;
	private int posturesBroken;
	private int hitStop;
	private double[] partDamage = new double[0];
	private final Map<UUID, Integer> lastRolled = new HashMap<>();
	private final Map<UUID, Integer> aimTicks = new HashMap<>();
	private int sidestepCooldown;
	private int sidesteps;
	private int[] lastUsedTick = new int[0];
	private @Nullable Attack queued;
	private final Set<UUID> shieldAdds = new LinkedHashSet<>();
	private int shieldTicks;
	private boolean burrowed;
	private boolean surfaceOnLoad;
	private int lastTelegraphTick = -1;
	private int lastFirstHitTick = -1;
	private @Nullable String lastRunAttack;

	protected BossEntity(EntityType<? extends BossEntity> type, Level level) {
		super(type, level);
		this.cooldowns = new int[0];
		this.xpReward = 50;
		setPersistenceRequired();
	}

	/** The boss's kind: phases, attacks, health, arena. Return a shared static instance. */
	public abstract BossDefinition definition();

	public static BossesConfig framework() {
		return BlockSoulsBosses.CONFIG;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Hooks for subclasses

	/** Server: the fight started (wall is up, event fired). */
	protected void onEngage() {
	}

	/** Server: the fight reset (wall down, healed). */
	protected void onReset() {
	}

	/** Server: entered phase {@code index} (after the definition's own {@link BossPhase#onEnter} hook). */
	protected void onPhaseEnter(int index) {
	}

	/** Server, every tick while engaged and not attacking or staggering, after movement. */
	protected void fightTick(ServerLevel level, @Nullable LivingEntity target) {
	}

	/**
	 * Whether engaging raises the fog wall ring around {@link #home()}. Open-air arenas (the final raid's whole island)
	 * turn it off and define the arena through {@link #inArena} instead. Default: {@link #setFogWallEnabled}.
	 */
	protected boolean raisesFogWall() {
		return fogWallEnabled;
	}

	/** Per instance: no fog wall for this boss (e.g. summoned as an add inside another boss's arena). Not saved. */
	public void setFogWallEnabled(boolean enabled) {
		this.fogWallEnabled = enabled;
	}

	/** Distance the boss tries to close to before it stops walking (it still picks ranged attacks from afar). */
	protected double preferredDistance() {
		return 3.0;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Multipart hitboxes

	/**
	 * Adds a weak-point hitbox. Call from the constructor only.
	 *
	 * @param offset part center relative to the boss's feet in its own frame: x = left, y = up, z = forward
	 */
	protected BossPart addPart(String name, float width, float height, double damageMultiplier, Vec3 offset) {
		BossPart part = new BossPart(this, name, width, height, damageMultiplier, offset);
		parts = Arrays.copyOf(parts, parts.length + 1);
		parts[parts.length - 1] = part;
		// Parts take the ids right after the boss's (the client mirrors this in recreateFromPacket). A client-side boss
		// has no id yet while it's being constructed (getId() would throw and drop the connection); setId and
		// recreateFromPacket number the parts once the spawn packet assigns it, as vanilla's dragon does.
		if (!level().isClientSide()) {
			part.setId(getId() + parts.length);
		}
		return part;
	}

	public BossPart[] parts() {
		return parts;
	}

	/** Damage that landed on a part: the part's multiplier applies, then it's handled like a hit on the body. */
	public boolean hurtFromPart(ServerLevel level, BossPart part, DamageSource source, float damage) {
		float before = getHealth();
		boolean hurt = hurtServer(level, source, (float) (damage * part.damageMultiplier()));
		if (hurt && source.getEntity() instanceof Player) {
			if (part.damageMultiplier() > 1) {
				addPoise(framework().posture.weakPointHitPoise);
			}
			damagePart(level, part.partName(), Math.max(0, before - getHealth()));
		}
		return hurt;
	}

	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket packet) {
		super.recreateFromPacket(packet);
		// Same id scheme as vanilla's dragon: parts follow the boss's id, so attack packets resolve on both sides.
		for (int i = 0; i < parts.length; i++) {
			parts[i].setId(packet.getId() + i + 1);
		}
	}

	@Override
	public void setId(int id) {
		super.setId(id);
		for (int i = 0; i < parts.length; i++) {
			parts[i].setId(id + i + 1);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Synced state

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_PHASE, (byte) 0);
		entityData.define(DATA_STAGE, (byte) Stage.NONE.ordinal());
		entityData.define(DATA_ATTACK, -1);
		entityData.define(DATA_ENGAGED, false);
		entityData.define(DATA_POSTURE, 0f);
		entityData.define(DATA_BROKEN_PARTS, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_STAGE.equals(accessor) || DATA_ATTACK.equals(accessor)) {
			stageStartTick = tickCount;
		}
	}

	/** Current phase index (both sides). */
	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	/** Current stage (both sides). */
	public Stage stage() {
		return Stage.byId(entityData.get(DATA_STAGE));
	}

	/** Current attack (both sides), null between attacks and during a stagger. */
	public @Nullable Attack currentAttack() {
		return definition().attack(entityData.get(DATA_ATTACK));
	}

	public boolean isEngaged() {
		return entityData.get(DATA_ENGAGED);
	}

	/** Client: ticks since the stage last changed (for animations). */
	public int ticksInStage() {
		return tickCount - stageStartTick;
	}

	private void syncStage(Stage stage, @Nullable Attack attack) {
		entityData.set(DATA_STAGE, (byte) stage.ordinal());
		entityData.set(DATA_ATTACK, attack == null ? -1 : definition().indexOf(attack));
	}

	// ---------------------------------------------------------------------------------------------------------
	// Public state

	public BlockPos home() {
		return home != null ? home : blockPosition();
	}

	public void setHome(BlockPos home) {
		this.home = home.immutable();
	}

	public int arenaRadius() {
		return arenaRadiusOverride > 0 ? arenaRadiusOverride : definition().arenaRadius();
	}

	/** Per-instance arena radius (tests, cramped spawn spots). */
	public void setArenaRadius(int radius) {
		this.arenaRadiusOverride = radius;
	}

	public FogWall fogWall() {
		return wall;
	}

	/** The server-side seal that holds the fog wall at any height while engaged (fog-wall bosses only). */
	public ArenaSeal seal() {
		return seal;
	}

	public double damageMultiplier() {
		return damageMultiplier;
	}

	public boolean isFieldBoss() {
		return fieldBoss;
	}

	/** Marks this as a field boss that leaves after {@code leaveAfterTicks} with no player near and no fight. */
	public void makeFieldBoss(int leaveAfterTicks) {
		this.fieldBoss = true;
		this.leaveAfterTicks = leaveAfterTicks;
	}

	public boolean isStaggered() {
		return stagger > 0;
	}

	public boolean isReturning() {
		return returning;
	}

	/** Everyone who joined this fight (online or not). */
	public Set<UUID> participantIds() {
		return participants;
	}

	/** Online participants. */
	public List<ServerPlayer> participants() {
		List<ServerPlayer> players = new ArrayList<>();
		if (level() instanceof ServerLevel level) {
			for (UUID id : participants) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (player != null) {
					players.add(player);
				}
			}
		}
		return players;
	}

	/** Tests and debug: fight this entity regardless of aggro (null to go back to normal targeting). */
	public void forceTarget(@Nullable LivingEntity target) {
		this.forcedTarget = target;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Who counts

	/** Whether attacks may hurt {@code entity}: players that aren't downed/spectating, and the current target. */
	public boolean canHarm(LivingEntity entity) {
		if (entity == this || !entity.isAlive() || entity instanceof BossEntity) {
			return false;
		}
		if (entity instanceof Player player) {
			return !player.isSpectator() && !PlayerStatus.isDowned(player);
		}
		return entity == getTarget();
	}

	/** Whether the player is inside the arena (fog wall radius plus a margin). */
	public boolean inArena(Entity entity) {
		if (entity.level() != level()) {
			return false;
		}
		BlockPos center = home();
		double dx = entity.getX() - (center.getX() + 0.5);
		double dz = entity.getZ() - (center.getZ() + 0.5);
		double r = arenaRadius() + framework().arenaMargin;
		return dx * dx + dz * dz <= r * r && Math.abs(entity.getY() - center.getY()) <= framework().fogWallAbove + 8;
	}

	/** A participant who still counts for the fight: alive, not downed, in the arena. Creative players count. */
	public boolean inFight(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !PlayerStatus.isDowned(player) && inArena(player);
	}

	/** A player the boss may target: in the fight and not in creative. */
	protected boolean targetable(ServerPlayer player) {
		return inFight(player) && !player.isCreative();
	}

	// ---------------------------------------------------------------------------------------------------------
	// Ticking

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		for (BossPart part : parts) {
			part.follow();
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (home == null) {
			home = blockPosition();
		}
		if (cooldowns.length != definition().attacks().size()) {
			cooldowns = new int[definition().attacks().size()];
			lastUsedTick = new int[cooldowns.length];
			Arrays.fill(lastUsedTick, Integer.MIN_VALUE / 2);
		}
		if (partDamage.length != definition().breaks().size()) {
			partDamage = new double[definition().breaks().size()];
		}
		if (surfaceOnLoad) {
			surfaceOnLoad = false;
			setBurrowed(false, Vec3.atBottomCenterOf(home()));
		}
		if (hitStop > 0) {
			// Hit-stop: everything holds for a beat (the bar still updates).
			hitStop--;
			setDeltaMovement(Vec3.ZERO);
			getNavigation().stop();
			if (tickCount % 2 == 0) {
				BossBars.tick(level, this);
			}
			return;
		}
		if (!scalingApplied) {
			// Keeps the health fraction (full for a fresh spawn).
			scalingApplied = true;
			refreshScaling(level);
		}
		if (wallNeedsClearing) {
			// Saved mid-fight: a restart resets the fight.
			wallNeedsClearing = false;
			wall.lower(level);
			setHealth(getMaxHealth());
		}

		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i]--;
			}
		}

		if (isEngaged()) {
			engagedTick(level);
		} else {
			idleTick(level);
		}
		if (tickCount % 2 == 0) {
			BossBars.tick(level, this);
		}
	}

	private void idleTick(ServerLevel level) {
		if (returning) {
			returnTicks++;
			BlockPos target = home();
			if (distanceToSqr(Vec3.atBottomCenterOf(target)) < 2.5 * 2.5) {
				returning = false;
				getNavigation().stop();
			} else if (returnTicks > framework().returnHomeTimeoutTicks) {
				teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
				returning = false;
				getNavigation().stop();
			} else if (returnTicks % 20 == 1) {
				getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
			}
			return;
		}

		if (tickCount % 10 == 0) {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && !player.isCreative() && !PlayerStatus.isDowned(player) && player.isAlive()
						&& distanceInArena(player) <= arenaRadius()) {
					engage(player);
					return;
				}
			}
		}

		if (fieldBoss) {
			int beacon = FieldBosses.config().beaconIntervalTicks;
			if (beacon > 0 && tickCount % beacon == 0) {
				// A smoke column over an unengaged field boss: visible from far off, says "something is over there".
				level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, getX(), getY() + getBbHeight() + 0.5, getZ(), 1, 0.3, 0.2, 0.3,
						0.01);
			}
			boolean someoneNear = !level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(this) < FieldBosses.config().leaveCheckRange
					* FieldBosses.config().leaveCheckRange).isEmpty();
			unattendedTicks = someoneNear ? 0 : unattendedTicks + 1;
			if (unattendedTicks >= leaveAfterTicks) {
				leave(level);
			}
		}
	}

	private double distanceInArena(Entity entity) {
		BlockPos center = home();
		double dx = entity.getX() - (center.getX() + 0.5);
		double dz = entity.getZ() - (center.getZ() + 0.5);
		if (Math.abs(entity.getY() - center.getY()) > framework().fogWallAbove + 4) {
			return Double.MAX_VALUE;
		}
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void engagedTick(ServerLevel level) {
		// Before anything else: whoever crossed the seal since last tick goes back to their side.
		seal.tick(level);

		// Late joiners: anyone who walks in joins the fight (behind a seal, only someone already inside it).
		if (tickCount % 10 == 0) {
			for (ServerPlayer player : level.players()) {
				if (!participants.contains(player.getUUID()) && !player.isSpectator() && player.isAlive() && canJoinFrom(player)) {
					join(player);
				}
			}
		}

		boolean anyoneIn = forcedTarget != null && forcedTarget.isAlive();
		for (ServerPlayer player : participants()) {
			if (inFight(player)) {
				anyoneIn = true;
				break;
			}
		}
		outOfFightTicks = anyoneIn ? 0 : outOfFightTicks + 1;
		if (outOfFightTicks >= framework().resetAfterTicks) {
			resetFight();
			return;
		}

		if (stagger > 0) {
			staggerTick(level);
			return;
		}

		shieldTick(level);
		postureTick();
		if (visceral > 0) {
			visceralTick(level);
			return;
		}
		BossAffixes.tick(level, this);

		if (run == null) {
			int next = PhaseMath.next(phase, getHealth() / getMaxHealth(), definition().thresholds());
			if (next != phase) {
				enterPhase(level, next);
				return;
			}
		}

		if (tickCount % framework().retargetIntervalTicks == 0 || getTarget() == null || !getTarget().isAlive()) {
			retarget();
		}
		LivingEntity target = getTarget();

		if (run != null) {
			attackTick(level);
			return;
		}

		moveTick(target);
		reactTick(level);
		fightTick(level, target);
		if (gap > 0) {
			gap--;
		} else if (target != null) {
			tryStartAttack(level, target);
		}
	}

	private void moveTick(@Nullable LivingEntity target) {
		if (target == null) {
			if (tickCount % 20 == 0 && distanceToSqr(Vec3.atBottomCenterOf(home())) > 9) {
				BlockPos home = home();
				getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
			}
			return;
		}
		getLookControl().setLookAt(target, 30, 30);
		double distance = distanceTo(target);
		if (distance > preferredDistance()) {
			if (tickCount % 5 == 0 || getNavigation().isDone()) {
				getNavigation().moveTo(target, definition().chaseSpeed() * definition().phase(phase).speedMult());
			}
		} else {
			getNavigation().stop();
		}
	}

	private void tryStartAttack(ServerLevel level, LivingEntity target) {
		BossPhase current = definition().phase(phase);
		List<Attack> attacks = current.attacks();
		List<AttackPicker.Scored> options = new ArrayList<>(attacks.size());
		int last = -1;
		for (int i = 0; i < attacks.size(); i++) {
			Attack attack = attacks.get(i);
			AttackStats stats = attack.stats();
			int index = definition().indexOf(attack);
			boolean allowed = !attackLost(attack) && attack.canUse(this, target);
			AttackPicker.Option option = new AttackPicker.Option(allowed ? stats.weight : 0, cooldowns[index], stats.minRange, stats.maxRange);
			options.add(new AttackPicker.Scored(option, attack.traits(), tickCount - lastUsedTick[index]));
			if (index == lastAttack) {
				last = i;
			}
		}
		BossesConfig.Picker p = framework().picker;
		int pick;
		if (p.enabled) {
			AttackPicker.Context context = new AttackPicker.Context(distanceTo(target), angleTo(target), 1.0, justRolled(target),
					!target.onGround() && !target.isInWater());
			AttackPicker.Weights weights = new AttackPicker.Weights(p.distance, p.angle, p.stamina, p.rolled, p.airborne, p.freshnessTicks);
			pick = AttackPicker.pickScored(options, context, weights, last, framework().repeatPenalty, random::nextLong);
		} else {
			pick = AttackPicker.pick(options.stream().map(AttackPicker.Scored::option).toList(), distanceTo(target), last,
					framework().repeatPenalty, random::nextLong);
		}
		if (pick >= 0) {
			startAttack(level, attacks.get(pick), target);
		}
	}

	/** Degrees between where the boss faces and {@code target} (0 = dead ahead, 180 = behind). */
	public double angleTo(Entity target) {
		Vec3 look = AttackRun.flat(Vec3.directionFromRotation(0, getYRot()));
		Vec3 to = AttackRun.flatDirection(position(), target.position(), look);
		return Math.toDegrees(Math.acos(Math.clamp(look.dot(to), -1, 1)));
	}

	private boolean justRolled(LivingEntity target) {
		Integer at = lastRolled.get(target.getUUID());
		return at != null && tickCount - at <= framework().picker.justRolledTicks;
	}

	/** Starts {@code attack} now (debug and tests can call this directly). */
	public void startAttack(ServerLevel level, Attack attack, @Nullable LivingEntity target) {
		cancelAttack();
		getNavigation().stop();
		run = new AttackRun(this, level, attack, definition().phase(phase), target);
		face(run.direction());
		syncStage(Stage.WINDUP, attack);
		attack.start(run);
	}

	private void attackTick(ServerLevel level) {
		AttackRun current = run;
		Attack attack = current.attack();
		switch (current.stage()) {
			case WINDUP -> {
				LivingEntity target = current.target();
				if (target != null && target.isAlive() && current.tick() < attack.stats().windupTicks * attack.trackFraction()) {
					current.track(target);
					face(current.direction());
				}
				attack.windup(current);
			}
			case ACTIVE -> attack.active(current);
			case RECOVERY -> attack.recovery(current);
			default -> {
			}
		}
		if (run != current) {
			// The hook cancelled or replaced the attack (e.g. the boss died).
			return;
		}
		Stage before = current.stage();
		if (!current.advance()) {
			finishAttack(current);
		} else if (current.stage() != before) {
			syncStage(current.stage(), attack);
		}
	}

	private void finishAttack(AttackRun finished) {
		Attack attack = finished.attack();
		int index = definition().indexOf(attack);
		BossPhase current = definition().phase(phase);
		cooldowns[index] = (int) Math.round(attack.stats().cooldownTicks * current.cooldownMult());
		if (index < lastUsedTick.length) {
			lastUsedTick[index] = tickCount;
		}
		lastAttack = index;
		gap = framework().attackGapTicks;
		attacksFinished++;
		run = null;
		recordRun(finished);
		attack.end(finished);
		syncStage(Stage.NONE, null);
		Attack next = queued;
		queued = null;
		if (next != null && level() instanceof ServerLevel level && isEngaged() && !attackLost(next)) {
			startAttack(level, next, finished.target());
		}
	}

	private void recordRun(AttackRun finished) {
		lastRunAttack = finished.attack().name();
		lastTelegraphTick = finished.firstTelegraphTick();
		lastFirstHitTick = finished.firstHitTick();
	}

	/** Name of the last attack that ran to its end (tests). */
	public @Nullable String lastRunAttack() {
		return lastRunAttack;
	}

	/** Run tick of the last finished attack's first telegraph, or -1 (tests: telegraph before hit). */
	public int lastTelegraphTick() {
		return lastTelegraphTick;
	}

	/** Run tick of the last finished attack's first landed hit, or -1. */
	public int lastFirstHitTick() {
		return lastFirstHitTick;
	}

	/** Starts {@code attack} as soon as the current one ends (feints hand over to their real move this way). */
	public void queueAttack(@Nullable Attack attack) {
		queued = attack;
	}

	// --- Shield (summon-and-shield) ---

	/** Shields the boss until every one of {@code adds} is dead (or {@code maxTicks} pass). */
	public void shieldUntilDead(Collection<? extends Entity> adds, int maxTicks) {
		shieldAdds.clear();
		for (Entity add : adds) {
			shieldAdds.add(add.getUUID());
		}
		shieldTicks = shieldAdds.isEmpty() ? 0 : maxTicks;
	}

	public boolean isShielded() {
		return shieldTicks > 0 && !shieldAdds.isEmpty();
	}

	/** Shield adds still alive. */
	public int shieldAddsAlive() {
		return shieldAdds.size();
	}

	private void shieldTick(ServerLevel level) {
		if (shieldTicks <= 0) {
			return;
		}
		shieldAdds.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity add) || !add.isAlive());
		shieldTicks--;
		if (shieldAdds.isEmpty() || shieldTicks <= 0) {
			shieldTicks = 0;
			shieldAdds.clear();
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() / 2, getZ(), 1, 0, 0, 0, 0);
			playSound(SoundEvents.SHIELD_BREAK.value(), 2.0f, 0.6f);
			return;
		}
		if (tickCount % 4 == 0) {
			double r = getBbWidth() * 0.9 + 0.5;
			for (int i = 0; i < 10; i++) {
				double a = random.nextDouble() * Math.PI * 2;
				double y = random.nextDouble() * getBbHeight();
				level.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + y, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	// --- Burrow ---

	/** Underground (burrow attacks): invisible, untouchable, no physics. */
	public boolean isBurrowed() {
		return burrowed;
	}

	/** Hides the boss underground at {@code at}, or brings it back up there. */
	public void setBurrowed(boolean value, Vec3 at) {
		burrowed = value;
		setInvisible(value);
		noPhysics = value;
		setNoGravity(value);
		setDeltaMovement(Vec3.ZERO);
		setPos(at.x, value ? at.y - getBbHeight() - 1.5 : at.y, at.z);
	}

	/** Stops the current attack (calls its {@link Attack#end}). */
	public void cancelAttack() {
		AttackRun current = run;
		if (current != null) {
			run = null;
			current.attack().end(current);
			syncStage(Stage.NONE, null);
		}
	}

	public @Nullable AttackRun attackRun() {
		return run;
	}

	/** Attacks that ran to the end of their recovery since this boss was created (tests use it to spot softlocks). */
	public int attacksFinished() {
		return attacksFinished;
	}

	/** Turns body and head to a horizontal direction. */
	public void face(Vec3 direction) {
		float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90);
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Posture / stagger (PLAN2 §3h)

	/** The boss's name with its field-boss affix prefix, if any ("Burning Ashwyrm"). */
	@Override
	public net.minecraft.network.chat.Component getName() {
		return BossAffixes.name(this, super.getName());
	}

	/** Poise fill, 0..1 (both sides; the bar under the boss bar). */
	public float posture() {
		return entityData.get(DATA_POSTURE);
	}

	/** Server: the visceral window is open. */
	public boolean isPostureBroken() {
		return visceral > 0;
	}

	/** Visceral windows opened since this boss was created (tests). */
	public int posturesBroken() {
		return posturesBroken;
	}

	/** Max poise for this fight (scaled for the party like health). */
	public double maxPoise() {
		double base = definition().poise() > 0 ? definition().poise() : framework().posture.basePoise;
		return io.github.tmldude.blocksouls.bosses.logic.PostureMath.maxPoise(base, Math.max(1, mostPlayersInFight));
	}

	/** Adds poise (precision hits, rolls, debug). Opens the visceral window when it fills. */
	public void addPoise(double amount) {
		BossesConfig.Posture c = framework().posture;
		if (!c.enabled || amount <= 0 || !isEngaged() || visceral > 0 || postureLockout > 0 || stagger > 0 || isDeadOrDying()) {
			return;
		}
		double max = maxPoise();
		poise = io.github.tmldude.blocksouls.bosses.logic.PostureMath.gain(poise, amount, max);
		poiseIdleTicks = 0;
		entityData.set(DATA_POSTURE, (float) (poise / max));
		if (io.github.tmldude.blocksouls.bosses.logic.PostureMath.broken(poise, max) && level() instanceof ServerLevel level) {
			breakPosture(level);
		}
	}

	/** A player's precision hit landed on this boss. */
	public void onPrecisionHit(ServerPlayer player, float damage) {
		BossesConfig.Posture c = framework().posture;
		addPoise(io.github.tmldude.blocksouls.bosses.logic.PostureMath.precisionHit(damage, c.precisionHitPoise, c.precisionDamagePoise));
	}

	/** A participant rolled: poise if it was inside the current attack's hit window and in reach. */
	public void onRolled(ServerPlayer player) {
		lastRolled.put(player.getUUID(), tickCount);
		AttackRun current = run;
		BossesConfig.Posture c = framework().posture;
		if (current == null || !io.github.tmldude.blocksouls.bosses.logic.PostureMath.wellTimedRoll(current.ticksBeforeActive(),
				current.activeTicks(), c.rollLeadTicks)) {
			return;
		}
		double reach = current.attack().stats().maxRange + c.rollRangeSlack;
		if (distanceTo(player) <= reach && current.markRolled(player.getUUID())) {
			addPoise(c.rollPoise);
		}
	}

	/** Opens the visceral window now (debug: {@code /bs boss posture break}). */
	public void breakPosture(ServerLevel level) {
		cancelAttack();
		getNavigation().stop();
		visceral = Math.max(1, framework().posture.visceralTicks);
		posturesBroken++;
		poise = maxPoise();
		entityData.set(DATA_POSTURE, 1f);
		syncStage(Stage.BROKEN, null);
		level.playSound(null, getX(), getY(), getZ(), io.github.tmldude.blocksouls.bosses.registry.BossSounds.POSTURE_BREAK, SoundSource.HOSTILE,
				3.0f, 1.0f);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, true, true, getX(), getY() + getBbHeight() * 0.6, getZ(), 40, 0.6, 0.6, 0.6, 0.5);
		onPostureBroken();
	}

	/** Server: the visceral window just opened. */
	protected void onPostureBroken() {
	}

	private void postureTick() {
		if (postureLockout > 0) {
			postureLockout--;
		}
		if (visceral > 0 || poise <= 0) {
			return;
		}
		poiseIdleTicks++;
		BossesConfig.Posture c = framework().posture;
		double next = io.github.tmldude.blocksouls.bosses.logic.PostureMath.decay(poise, poiseIdleTicks, c.decayDelayTicks, c.decayPerTick);
		if (next != poise) {
			poise = next;
			if (tickCount % 4 == 0 || poise == 0) {
				entityData.set(DATA_POSTURE, (float) (poise / maxPoise()));
			}
		}
	}

	private void visceralTick(ServerLevel level) {
		visceral--;
		setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
		if (visceral % 8 == 0) {
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, true, true, getX(), getY() + getBbHeight() * 0.7, getZ(), 8, 0.5, 0.4, 0.5, 0.2);
		}
		if (visceral <= 0) {
			visceral = 0;
			poise = 0;
			poiseIdleTicks = 0;
			postureLockout = framework().posture.lockoutTicks;
			entityData.set(DATA_POSTURE, 0f);
			gap = framework().attackGapTicks;
			syncStage(Stage.NONE, null);
		}
	}

	private void resetPosture() {
		poise = 0;
		visceral = 0;
		postureLockout = 0;
		poiseIdleTicks = 0;
		hitStop = 0;
		lastRolled.clear();
		aimTicks.clear();
		entityData.set(DATA_POSTURE, 0f);
	}

	/** Freezes the boss for {@code ticks} (hit-stop). */
	public void hitStop(int ticks) {
		hitStop = Math.max(hitStop, ticks);
	}

	public boolean inHitStop() {
		return hitStop > 0;
	}

	/** Called by {@link AttackRun} when one of this boss's attacks damaged {@code victim}. */
	void onAttackLanded(ServerLevel level, LivingEntity victim, float amount) {
		if (amount >= victim.getMaxHealth() * framework().presentation.heavyHitShareOfPlayer) {
			hitStop(framework().presentation.hitStopTicks);
		}
		BossAffixes.onHit(level, this, victim);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Part breaks

	/** Whether the breakable part {@code name} is broken (both sides). */
	public boolean isPartBroken(String name) {
		int index = definition().breakIndex(name);
		return index >= 0 && (entityData.get(DATA_BROKEN_PARTS) & (1 << index)) != 0;
	}

	/** Bit mask of broken parts, by {@link BossDefinition#breaks()} index (both sides). */
	public int brokenParts() {
		return entityData.get(DATA_BROKEN_PARTS);
	}

	/** Whether {@code attack} is gone because the part it needs broke. */
	public boolean attackLost(Attack attack) {
		List<BossDefinition.PartBreak> breaks = definition().breaks();
		int mask = brokenParts();
		for (int i = 0; i < breaks.size(); i++) {
			if ((mask & (1 << i)) != 0 && attack.name().equals(breaks.get(i).lostAttack())) {
				return true;
			}
		}
		return false;
	}

	/** Sustained damage on a breakable part; breaks it at its threshold. */
	public void damagePart(ServerLevel level, String name, double amount) {
		int index = definition().breakIndex(name);
		if (index < 0 || index >= partDamage.length || isPartBroken(name)) {
			return;
		}
		partDamage[index] += amount;
		if (partDamage[index] >= definition().breaks().get(index).shareOfMaxHealth() * getMaxHealth()) {
			breakPart(level, index);
		}
	}

	/** Breaks part {@code index} now (debug: {@code /bs boss break <part>}). */
	public void breakPart(ServerLevel level, int index) {
		List<BossDefinition.PartBreak> breaks = definition().breaks();
		if (index < 0 || index >= breaks.size() || (brokenParts() & (1 << index)) != 0) {
			return;
		}
		BossDefinition.PartBreak broken = breaks.get(index);
		entityData.set(DATA_BROKEN_PARTS, brokenParts() | (1 << index));
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && broken.speedBonus() != 0) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(CommonIds.id(CommonIds.BOSSES, "broken_" + broken.part()), broken.speedBonus(),
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		if (run != null && attackLost(run.attack())) {
			cancelAttack();
			gap = framework().attackGapTicks;
		}
		Vec3 at = position().add(0, getBbHeight() * 0.6, 0);
		for (BossPart part : parts) {
			if (part.partName().equals(broken.part())) {
				at = part.position().add(0, part.getBbHeight() / 2, 0);
			}
		}
		level.playSound(null, at.x, at.y, at.z, io.github.tmldude.blocksouls.bosses.registry.BossSounds.PART_BREAK, SoundSource.HOSTILE, 3.0f, 0.9f);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.05);
		hitStop(framework().presentation.hitStopTicks * 2);
		onPartBroken(broken.part());
	}

	/** Server: a breakable part just broke. */
	protected void onPartBroken(String part) {
	}

	private void restoreParts() {
		List<BossDefinition.PartBreak> breaks = definition().breaks();
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		for (BossDefinition.PartBreak broken : breaks) {
			if (speed != null) {
				speed.removeModifier(CommonIds.id(CommonIds.BOSSES, "broken_" + broken.part()));
			}
		}
		Arrays.fill(partDamage, 0);
		entityData.set(DATA_BROKEN_PARTS, 0);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Reactions (sidestep vs. aim)

	/** Whether this boss sidesteps players who hold their aim on it. Off by default; light-footed bosses opt in. */
	protected boolean sidesteps() {
		return false;
	}

	/** Sidesteps since this boss was created (tests). */
	public int sidestepCount() {
		return sidesteps;
	}

	private void reactTick(ServerLevel level) {
		if (sidestepCooldown > 0) {
			sidestepCooldown--;
		}
		BossesConfig.Reactions c = framework().reactions;
		if (!c.sidestep || !sidesteps() || !onGround()) {
			return;
		}
		int floor = io.github.tmldude.blocksouls.bosses.logic.Fairness.reactionTicks(c.reactionTicks);
		for (ServerPlayer player : participants()) {
			UUID id = player.getUUID();
			if (!inFight(player) || distanceTo(player) > c.range || !aimedAt(player, c.aimConeDegrees)) {
				aimTicks.remove(id);
				continue;
			}
			int held = aimTicks.merge(id, 1, Integer::sum);
			if (held == floor && sidestepCooldown <= 0) {
				// One decision per aim streak, never sooner than the reaction floor.
				if (random.nextDouble() < c.chance) {
					sidestep(level, player, c.speed);
					sidestepCooldown = io.github.tmldude.blocksouls.bosses.logic.Fairness.reactionCooldown(c.cooldownTicks);
				}
			}
		}
	}

	private boolean aimedAt(ServerPlayer player, double coneDegrees) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = position().add(0, getBbHeight() / 2, 0).subtract(eye);
		if (to.lengthSqr() < 1e-4) {
			return false;
		}
		double cos = player.getLookAngle().normalize().dot(to.normalize());
		return cos >= Math.cos(Math.toRadians(coneDegrees));
	}

	/** Hops sideways relative to {@code from}'s line of fire. */
	public void sidestep(ServerLevel level, LivingEntity from, double speed) {
		Vec3 to = AttackRun.flatDirection(from.position(), position(), getLookAngle());
		Vec3 side = new Vec3(-to.z, 0, to.x).scale(random.nextBoolean() ? 1 : -1);
		setDeltaMovement(side.scale(speed).add(0, 0.25, 0));
		needsSync = true;
		sidesteps++;
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 8, 0.4, 0.1, 0.4, 0.02);
		playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Phases

	private void enterPhase(ServerLevel level, int next) {
		cancelAttack();
		phase = next;
		entityData.set(DATA_PHASE, (byte) next);
		stagger = framework().staggerTicks;
		getNavigation().stop();
		syncStage(Stage.STAGGER, null);
		playSound(staggerSound(), 3.0f, 0.7f);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() / 2, getZ(), 2, 0.5, 0.5, 0.5, 0);
		BossPhase entered = definition().phase(next);
		if (entered.onEnter() != null) {
			entered.onEnter().onEnter(this);
		}
		onPhaseEnter(next);
	}

	/** Debug: jump straight to phase {@code index} (plays its stagger). */
	public void forcePhase(int index) {
		if (level() instanceof ServerLevel level) {
			int target = Math.clamp(index, 0, definition().phases().size() - 1);
			if (target == 0) {
				cancelAttack();
				phase = 0;
				entityData.set(DATA_PHASE, (byte) 0);
			} else {
				enterPhase(level, target);
			}
		}
	}

	private void staggerTick(ServerLevel level) {
		stagger--;
		if (stagger % 10 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.8, getZ(), 6, 0.6, 0.4, 0.6, 0.02);
		}
		if (stagger <= 0) {
			stagger = 0;
			gap = framework().attackGapTicks;
			syncStage(Stage.NONE, null);
		}
	}

	/** The roar on a phase change. */
	protected net.minecraft.sounds.SoundEvent staggerSound() {
		return SoundEvents.RAVAGER_ROAR;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Targeting

	private void retarget() {
		if (forcedTarget != null) {
			if (forcedTarget.isAlive()) {
				setTarget(forcedTarget);
				return;
			}
			forcedTarget = null;
		}
		aggro.decay(framework().threatDecay);
		Map<UUID, Double> candidates = new HashMap<>();
		Map<UUID, ServerPlayer> players = new HashMap<>();
		for (ServerPlayer player : participants()) {
			if (targetable(player)) {
				candidates.put(player.getUUID(), (double) distanceTo(player));
				players.put(player.getUUID(), player);
			}
		}
		UUID current = getTarget() instanceof ServerPlayer player ? player.getUUID() : null;
		UUID chosen = aggro.choose(current, candidates, framework().proximityThreat, framework().proximityRange, framework().aggroSwitchMargin);
		setTarget(chosen == null ? null : players.get(chosen));
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target instanceof Player player && PlayerStatus.isDowned(player)) {
			target = null;
		}
		super.setTarget(target);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		if (target == forcedTarget) {
			// Debug/test target: kept even if vanilla wouldn't (e.g. an invulnerable dummy).
			return target.isAlive();
		}
		return !(target instanceof Player player && PlayerStatus.isDowned(player)) && super.canAttack(target);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Engage / reset / death

	/** Starts the fight with {@code player} (no-op if already engaged; then they just join). */
	public void engage(ServerPlayer player) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (isEngaged()) {
			join(player);
			return;
		}
		returning = false;
		participants.clear();
		aggro.clear();
		participants.add(player.getUUID());
		// Count everyone already standing in the arena.
		for (ServerPlayer other : level.players()) {
			if (other != player && !other.isSpectator() && other.isAlive() && inArena(other)) {
				participants.add(other.getUUID());
			}
		}
		entityData.set(DATA_ENGAGED, true);
		outOfFightTicks = 0;
		unattendedTicks = 0;
		gap = framework().attackGapTicks * 2;
		mostPlayersInFight = 0;
		refreshScaling(level);
		if (raisesFogWall()) {
			wall.raise(level, home(), arenaRadius(), framework().fogWallBelow, framework().fogWallAbove);
			seal.activate(level);
		}
		level.playSound(null, home(), io.github.tmldude.blocksouls.bosses.registry.BossSounds.FOG_WALL_CLOSE, SoundSource.HOSTILE, 2.0f, 0.8f);
		io.github.tmldude.blocksouls.bosses.presentation.BossSplash.send(level, this);
		onEngage();
		BossEvents.ENGAGED.invoker().onBoss(level, definition().id(), this, participants());
		FieldBosses.onEngaged(level, this);
	}

	/** Whether {@code player} joins the running fight from where they stand: in the arena, and inside an active seal. */
	private boolean canJoinFrom(ServerPlayer player) {
		return inArena(player) && (!seal.isActive() || seal.isInside(player.position()));
	}

	/** Adds a player to the running fight (late joiner). */
	public void join(ServerPlayer player) {
		if (isEngaged() && participants.add(player.getUUID()) && level() instanceof ServerLevel level) {
			refreshScaling(level);
		}
	}

	/** Back to idle: wall down, full health, walk home. */
	public void resetFight() {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		cancelAttack();
		wall.lower(level);
		seal.release();
		entityData.set(DATA_ENGAGED, false);
		participants.clear();
		aggro.clear();
		forcedTarget = null;
		setTarget(null);
		stagger = 0;
		resetPosture();
		queued = null;
		shieldAdds.clear();
		shieldTicks = 0;
		if (burrowed) {
			setBurrowed(false, Vec3.atBottomCenterOf(home()));
		}
		restoreParts();
		phase = 0;
		entityData.set(DATA_PHASE, (byte) 0);
		syncStage(Stage.NONE, null);
		Arrays.fill(cooldowns, 0);
		lastAttack = -1;
		mostPlayersInFight = 0;
		refreshScaling(level);
		setHealth(getMaxHealth());
		clearFire();
		removeAllEffects();
		returning = true;
		returnTicks = 0;
		onReset();
	}

	/** Recomputes the health/damage multipliers; keeps the current health fraction. */
	private void refreshScaling(ServerLevel level) {
		int inFight = 0;
		if (isEngaged()) {
			for (ServerPlayer player : participants()) {
				if (inFight(player)) {
					inFight++;
				}
			}
		}
		// Players who leave mid-fight don't make the boss weaker.
		mostPlayersInFight = Math.max(mostPlayersInFight, inFight);
		var difficulty = Runs.get(level.getServer()).difficulty();
		double health = BossScaling.healthMultiplier(mostPlayersInFight, difficulty.mobHealth());
		damageMultiplier = difficulty.mobDamage();
		AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		float fraction = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1;
		maxHealth.addOrUpdateTransientModifier(new AttributeModifier(SCALING_ID, health - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		setHealth(Math.max(1, fraction * getMaxHealth()));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass && (stagger > 0 || returning || burrowed)) {
			return false;
		}
		if (!bypass && isShielded()) {
			level.sendParticles(ParticleTypes.ENCHANT, true, true, getX(), getY() + getBbHeight() / 2, getZ(), 12, getBbWidth() / 2,
					getBbHeight() / 3, getBbWidth() / 2, 0.4);
			playSound(SoundEvents.SHIELD_BLOCK.value(), 1.5f, 0.7f);
			return false;
		}
		Entity attacker = source.getEntity();
		ServerPlayer player = attacker instanceof ServerPlayer p && !p.isSpectator() ? p : null;
		if (player != null) {
			if (!isEngaged()) {
				engage(player);
			} else if (!seal.isActive() || seal.isInside(player.position())) {
				// Behind a seal, hits from outside don't make you a participant (who could then walk in).
				join(player);
			}
			if (!inArena(player)) {
				damage *= (float) framework().outsideArenaDamageMult;
			}
		}
		if (visceral > 0) {
			damage *= (float) framework().posture.visceralDamageMult;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && player != null) {
			aggro.addDamage(player.getUUID(), damage);
		}
		if (hurt && damage >= getMaxHealth() * framework().presentation.heavyHitShareOfBoss) {
			hitStop(framework().presentation.hitStopTicks);
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.FALL) || super.isInvulnerableTo(level, source);
	}

	@Override
	public void die(DamageSource source) {
		boolean wasAlive = !isRemoved() && !dead;
		super.die(source);
		if (wasAlive && level() instanceof ServerLevel level) {
			cancelAttack();
			wall.lower(level);
			seal.release();
			List<ServerPlayer> players = participants();
			Collection<ServerPlayer> credited = List.copyOf(players);
			BossEvents.KILLED.invoker().onBoss(level, definition().id(), this, credited);
			BossAffixes.onDeath(level, this);
			FieldBosses.onKilled(level, this);
			BossBars.removeAll(level, this);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (var table : definition().extraLoot()) {
			dropFromLootTable(level, source, killedByPlayer, table);
		}
	}

	/** Field bosses that nobody engaged walk off: a puff of smoke and gone. */
	public void leave(ServerLevel level) {
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1, getZ(), 30, 0.8, 1.2, 0.8, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2f, 0.6f);
		FieldBosses.onLeft(level, this);
		discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		// Unloaded too: a seal must never outlive its boss's ticking.
		seal.release();
		if (reason.shouldDestroy() && level() instanceof ServerLevel level) {
			cancelAttack();
			wall.lower(level);
			BossBars.removeAll(level, this);
			FieldBosses.onRemoved(level, this);
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Never despawn, never get shoved around

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Save data

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("BossHome", BlockPos.CODEC, home());
		output.putInt("BossArenaRadius", arenaRadiusOverride);
		output.putBoolean("BossField", fieldBoss);
		output.putInt("BossLeaveAfter", leaveAfterTicks);
		output.putInt("BossUnattended", unattendedTicks);
		output.putBoolean("BossBurrowed", burrowed);
		if (wall.isUp()) {
			output.store("BossWall", BlockPos.CODEC.listOf(), List.copyOf(wall.positions()));
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.read("BossHome", BlockPos.CODEC).ifPresent(pos -> home = pos);
		arenaRadiusOverride = input.getIntOr("BossArenaRadius", -1);
		fieldBoss = input.getBooleanOr("BossField", false);
		leaveAfterTicks = input.getIntOr("BossLeaveAfter", 0);
		unattendedTicks = input.getIntOr("BossUnattended", 0);
		if (input.getBooleanOr("BossBurrowed", false)) {
			// Saved mid-burrow: surface at home on load (the attack itself isn't saved).
			burrowed = true;
			surfaceOnLoad = true;
		}
		input.read("BossWall", BlockPos.CODEC.listOf()).ifPresent(list -> {
			wall.load(list);
			wallNeedsClearing = true;
		});
	}
}
