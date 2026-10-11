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
		// Parts take the ids right after the boss's (the client mirrors this in recreateFromPacket).
		part.setId(getId() + parts.length);
		return part;
	}

	public BossPart[] parts() {
		return parts;
	}

	/** Damage that landed on a part: the part's multiplier applies, then it's handled like a hit on the body. */
	public boolean hurtFromPart(ServerLevel level, BossPart part, DamageSource source, float damage) {
		return hurtServer(level, source, (float) (damage * part.damageMultiplier()));
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
		// Late joiners: anyone who walks in joins the fight.
		if (tickCount % 10 == 0) {
			for (ServerPlayer player : level.players()) {
				if (!participants.contains(player.getUUID()) && !player.isSpectator() && player.isAlive() && inArena(player)) {
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
		List<AttackPicker.Option> options = new ArrayList<>(attacks.size());
		int last = -1;
		for (int i = 0; i < attacks.size(); i++) {
			Attack attack = attacks.get(i);
			AttackStats stats = attack.stats();
			int index = definition().indexOf(attack);
			boolean allowed = attack.canUse(this, target);
			options.add(new AttackPicker.Option(allowed ? stats.weight : 0, cooldowns[index], stats.minRange, stats.maxRange));
			if (index == lastAttack) {
				last = i;
			}
		}
		int pick = AttackPicker.pick(options, distanceTo(target), last, framework().repeatPenalty, random::nextLong);
		if (pick >= 0) {
			startAttack(level, attacks.get(pick), target);
		}
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
		lastAttack = index;
		gap = framework().attackGapTicks;
		attacksFinished++;
		run = null;
		attack.end(finished);
		syncStage(Stage.NONE, null);
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
		}
		level.playSound(null, home(), io.github.tmldude.blocksouls.bosses.registry.BossSounds.FOG_WALL_CLOSE, SoundSource.HOSTILE, 2.0f, 0.8f);
		onEngage();
		BossEvents.ENGAGED.invoker().onBoss(level, definition().id(), this, participants());
		FieldBosses.onEngaged(level, this);
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
		entityData.set(DATA_ENGAGED, false);
		participants.clear();
		aggro.clear();
		forcedTarget = null;
		setTarget(null);
		stagger = 0;
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
		if (!bypass && (stagger > 0 || returning)) {
			return false;
		}
		Entity attacker = source.getEntity();
		ServerPlayer player = attacker instanceof ServerPlayer p && !p.isSpectator() ? p : null;
		if (player != null) {
			if (!isEngaged()) {
				engage(player);
			} else {
				join(player);
			}
			if (!inArena(player)) {
				damage *= (float) framework().outsideArenaDamageMult;
			}
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && player != null) {
			aggro.addDamage(player.getUUID(), damage);
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
			List<ServerPlayer> players = participants();
			Collection<ServerPlayer> credited = List.copyOf(players);
			BossEvents.KILLED.invoker().onBoss(level, definition().id(), this, credited);
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
		input.read("BossWall", BlockPos.CODEC.listOf()).ifPresent(list -> {
			wall.load(list);
			wallNeedsClearing = true;
		});
	}
}
