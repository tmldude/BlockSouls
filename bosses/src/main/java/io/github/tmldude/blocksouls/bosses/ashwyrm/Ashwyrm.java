package io.github.tmldude.blocksouls.bosses.ashwyrm;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.archetype.Burrow;
import io.github.tmldude.blocksouls.bosses.archetype.LingeringPools;
import io.github.tmldude.blocksouls.bosses.archetype.Spiral;
import io.github.tmldude.blocksouls.bosses.api.Stage;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Ashwyrm: a 12-block ash-grey dragon with ember-glowing cracks that lands on mountain peaks.
 * <ul>
 * <li>Phase 1 (100–60%), ground: bite, tail sweep, fire breath, wing buffet.</li>
 * <li>Phase 2 (60–25%), air: circles its home and makes fireball runs; every ~20 s in the air it lands and lies
 * stunned for 6 s, taking double damage.</li>
 * <li>Phase 3 (below 25%), ground, enraged: faster, adds the firestorm (a meteor on every player).</li>
 * </ul>
 * The head is a ×2 weak point. Fire never touches blocks unless {@code griefing} is on.
 */
public class Ashwyrm extends BossEntity {
	/** Phase indices. */
	public static final int GROUND = 0;
	public static final int AIR = 1;
	public static final int ENRAGED = 2;

	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(Ashwyrm.class, EntityDataSerializers.BOOLEAN);
	private static BossDefinition definition;

	private int airTicks;
	private int landings;
	private double circleAngle;
	private int stuckTicks;
	private int hopCooldown;
	private boolean settled;
	private boolean arrivalPending;
	private int arrivingTicks = -1;
	private boolean leaving;
	private int leaveTicks;
	private Vec3 leaveDir = new Vec3(1, 0, 0);

	public Ashwyrm(EntityType<? extends Ashwyrm> type, Level level) {
		super(type, level);
		this.xpReward = config().xp;
		// Offsets match the model at rest (x = left, y = up, z = forward). The head comes first: tests use parts()[0].
		addPart("head", 1.6f, 1.4f, config().headDamageMult, new Vec3(0, 3.2, 5.4));
		addPart("neck", 1.2f, 1.2f, 1.0, new Vec3(0, 2.8, 3.3));
		addPart("tail", 1.4f, 1.1f, 1.0, new Vec3(0, 1.9, -3.6));
		addPart("tail_tip", 1.0f, 0.9f, 1.0, new Vec3(0, 1.5, -5.6));
	}

	public static AshwyrmConfig config() {
		return Ashwyrms.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		AshwyrmConfig c = config();
		AshwyrmAttacks.Bite bite = new AshwyrmAttacks.Bite();
		AshwyrmAttacks.TailSweep tail = new AshwyrmAttacks.TailSweep();
		AshwyrmAttacks.Breath breath = new AshwyrmAttacks.Breath();
		AshwyrmAttacks.WingBuffet buffet = new AshwyrmAttacks.WingBuffet();
		Burrow burrow = new Burrow("burrow", c.burrow, AshwyrmAttacks.THEME, "land");
		Spiral spiral = new Spiral("spiral", c.spiral, AshwyrmAttacks.THEME, "breath");
		LingeringPools pools = new LingeringPools("pools", c.pools, AshwyrmAttacks.THEME, "breath");
		definition = BossDefinition.builder(CommonIds.ASHWYRM)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.music(AshwyrmSounds.MUSIC)
				// A legendary gun, only when guns is installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/legendary_gun"))
				.breakable("tail", c.tailBreakShare, "tail_sweep", c.tailSpeedBonus)
				.phase(BossPhase.first().attacks(bite, tail, breath, buffet, burrow))
				.phase(BossPhase.below(c.phase2Threshold).attacks(new AshwyrmAttacks.FireballRun(), new AshwyrmAttacks.Land(), spiral))
				.phase(BossPhase.below(c.phase3Threshold)
						.attacks(bite, tail, breath, buffet, new AshwyrmAttacks.Firestorm(), burrow, pools)
						.damage(c.phase3Damage)
						.speed(c.phase3Speed)
						.cooldowns(c.phase3Cooldowns)
						.recovery(c.phase3Recovery))
				.build();
		return definition;
	}

	public static BossDefinition boss() {
		return definition;
	}

	@Override
	public BossDefinition definition() {
		return definition;
	}

	// ---------------------------------------------------------------------------------------------------------
	// State

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_FLYING, false);
	}

	/** In the air (both sides): phase 2 flight, arriving, leaving. */
	public boolean isFlying() {
		return entityData.get(DATA_FLYING);
	}

	void setFlying(boolean flying) {
		entityData.set(DATA_FLYING, flying);
		setNoGravity(flying);
		if (flying) {
			airTicks = 0;
		}
	}

	/** Lying stunned after a landing (both sides): takes {@code vulnerableDamageMult}. */
	public boolean isVulnerable() {
		return currentAttack() instanceof AshwyrmAttacks.Land && stage() == Stage.RECOVERY;
	}

	/** Server: time in the air is up, the next attack is a landing. */
	boolean wantsToLand() {
		return isFlying() && airTicks >= config().landIntervalTicks;
	}

	void onLanded() {
		landings++;
		airTicks = 0;
	}

	/** Landings since spawn (tests). */
	public int landings() {
		return landings;
	}

	/** Horizontal unit vector the body faces. */
	Vec3 forward() {
		double yaw = Math.toRadians(getYRot());
		return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
	}

	/** Roughly where the jaws are at rest. */
	Vec3 mouth() {
		return position().add(forward().scale(6.0)).add(0, 2.8, 0);
	}

	/** Flight circle radius: the configured one, but inside the arena. */
	double circleRadius() {
		return Math.max(2, Math.min(config().circleRadius, arenaRadius() - 4));
	}

	/** Cruising height over the ground at (x, z), never below home + flight height. */
	double flightAltitude(double x, double z) {
		double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		return Math.max(home().getY() + config().flightHeight, ground + config().flightHeight * 0.6);
	}

	/** Moves straight toward {@code point} at up to {@code speed} blocks per tick, facing the way it goes. */
	void flyToward(Vec3 point, double speed) {
		Vec3 delta = point.subtract(position());
		double distance = delta.length();
		Vec3 velocity = distance < 1e-3 ? Vec3.ZERO : delta.scale(Math.min(speed, distance) / distance);
		setDeltaMovement(velocity);
		if (velocity.horizontalDistanceSqr() > 1e-4) {
			face(velocity);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Fight

	@Override
	protected double preferredDistance() {
		// In the air phase it never walks.
		return phase() == AIR ? 1000 : 5.0;
	}

	@Override
	protected void fightTick(ServerLevel level, @Nullable LivingEntity target) {
		if (phase() == AIR) {
			if (!isFlying()) {
				takeOff(level);
			}
			circle();
			return;
		}
		if (isFlying()) {
			setFlying(false);
		}
		groundTick(target);
	}

	private void takeOff(ServerLevel level) {
		setFlying(true);
		BlockPos home = home();
		circleAngle = Math.atan2(getZ() - (home.getZ() + 0.5), getX() - (home.getX() + 0.5));
		playSound(AshwyrmSounds.FLAP, 4.0f, 0.6f);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, getX(), getY() + 0.3, getZ(), 40, 3, 0.2, 3, 0.03);
		setDeltaMovement(0, 0.6, 0);
	}

	private void circle() {
		AshwyrmConfig c = config();
		circleAngle += c.circleSpeed;
		BlockPos home = home();
		double radius = circleRadius();
		double x = home.getX() + 0.5 + Math.cos(circleAngle) * radius;
		double z = home.getZ() + 0.5 + Math.sin(circleAngle) * radius;
		flyToward(new Vec3(x, flightAltitude(x, z), z), c.flySpeed);
		if (tickCount % 14 == 0) {
			playSound(AshwyrmSounds.FLAP, 2.5f, 0.8f + random.nextFloat() * 0.1f);
		}
	}

	/** On the ground: if the target is far and the path is blocked (cliffs), hop toward them on its wings. */
	private void groundTick(@Nullable LivingEntity target) {
		AshwyrmConfig c = config();
		if (hopCooldown > 0) {
			hopCooldown--;
		}
		if (target == null) {
			stuckTicks = 0;
			return;
		}
		double distance = distanceTo(target);
		boolean stuck = distance > c.hopMinDistance && (getNavigation().isDone() || horizontalCollision);
		stuckTicks = stuck ? stuckTicks + 1 : 0;
		if (stuckTicks > 30 && hopCooldown == 0 && onGround()) {
			Vec3 to = target.position().subtract(position());
			Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
			double horizontal = Math.min(1.4, distance * 0.07);
			double up = Math.min(1.3, 0.75 + Math.max(0, target.getY() - getY()) * 0.06);
			setDeltaMovement(flat.x * horizontal, up, flat.z * horizontal);
			face(flat);
			playSound(AshwyrmSounds.FLAP, 3.0f, 0.8f);
			hopCooldown = c.hopCooldownTicks;
			stuckTicks = 0;
		}
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (index == ENRAGED) {
			// Crashes out of the sky, burning.
			if (isFlying()) {
				setFlying(false);
			}
			level.sendParticles(ParticleTypes.FLAME, true, true, getX(), getY() + 1.5, getZ(), 80, 2, 1.2, 2, 0.06);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, getX(), getY() + 2, getZ(), 40, 2, 1, 2, 0.04);
	}

	@Override
	protected void onEngage() {
		if (arrivingTicks >= 0 || leaving) {
			arrivingTicks = -1;
			leaving = false;
			setFlying(false);
		}
	}

	@Override
	protected void onReset() {
		setFlying(false);
		airTicks = 0;
		stuckTicks = 0;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isVulnerable()) {
			damage *= (float) config().vulnerableDamageMult;
		}
		return super.hurtServer(level, source, damage);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Arrival and leaving

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		if (reason == EntitySpawnReason.EVENT) {
			// A field spawn: it glides down onto its spot from high above.
			arrivalPending = true;
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (isRemoved()) {
			return;
		}
		if (!settled) {
			settled = true;
			if (!arrivalPending) {
				// Loaded mid-flight (flight state isn't saved): back on the ground.
				setFlying(false);
			}
		}
		if (isFlying() && isEngaged()) {
			airTicks++;
		}
		if (arrivalPending) {
			arrivalPending = false;
			setPos(getX(), getY() + config().arrivalHeight, getZ());
			setFlying(true);
			arrivingTicks = config().arrivalTicks;
		}
		if (arrivingTicks >= 0) {
			arrivalTick(level);
		}
		if (leaving) {
			leaveTick(level);
		}
	}

	private void arrivalTick(ServerLevel level) {
		AshwyrmConfig c = config();
		setDeltaMovement(0, -c.arrivalHeight / Math.max(1, c.arrivalTicks), 0);
		if (tickCount % 8 == 0) {
			playSound(AshwyrmSounds.FLAP, 5.0f, 0.7f);
		}
		arrivingTicks--;
		if (onGround() || getY() <= home().getY() + 0.05 || arrivingTicks < 0) {
			arrivingTicks = -1;
			setFlying(false);
			playSound(AshwyrmSounds.ROAR, 8.0f, 0.7f);
			// The ash column: tall, forced, visible from far off.
			for (int y = 0; y < 24; y += 2) {
				level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, getX(), getY() + y, getZ(), 3, 0.6, 0.5, 0.6, 0.01);
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, getX(), getY() + 0.5, getZ(), 60, 4, 0.3, 4, 0.05);
			level.sendParticles(ParticleTypes.ASH, true, true, getX(), getY() + 2, getZ(), 120, 5, 2, 5, 0.02);
		}
	}

	/** Unengaged too long: flies up and away instead of vanishing. */
	@Override
	public void leave(ServerLevel level) {
		if (leaving) {
			return;
		}
		leaving = true;
		leaveTicks = 0;
		double a = random.nextDouble() * Math.PI * 2;
		leaveDir = new Vec3(Math.cos(a), 0, Math.sin(a));
		setFlying(true);
		playSound(AshwyrmSounds.ROAR, 6.0f, 0.8f);
	}

	private void leaveTick(ServerLevel level) {
		leaveTicks++;
		Vec3 velocity = leaveDir.scale(Math.min(0.9, 0.2 + leaveTicks * 0.015)).add(0, 0.45, 0);
		setDeltaMovement(velocity);
		face(leaveDir);
		if (leaveTicks % 8 == 0) {
			playSound(AshwyrmSounds.FLAP, 5.0f, 0.8f);
		}
		if (leaveTicks >= config().leaveFlightTicks) {
			leaving = false;
			super.leave(level);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Client effects

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			clientEffects();
		}
	}

	/** Embers from the cracks, smoke from the nostrils; more embers when enraged, sparks while stunned. */
	private void clientEffects() {
		Vec3 fwd = forward();
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		int every = phase() >= ENRAGED ? 1 : 3;
		if (tickCount % every == 0) {
			double along = (random.nextDouble() * 2 - 1) * 2.4;
			double across = (random.nextDouble() * 2 - 1) * 1.0;
			Vec3 p = position().add(fwd.scale(along)).add(side.scale(across)).add(0, 1.6 + random.nextDouble() * 1.0, 0);
			level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ASH, p.x, p.y, p.z, 0, 0.03, 0);
		}
		if (tickCount % 7 == 0) {
			Vec3 m = mouth();
			level().addParticle(ParticleTypes.SMOKE, m.x, m.y + 0.3, m.z, fwd.x * 0.05, 0.04, fwd.z * 0.05);
		}
		if (isFlying() && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - 0.5) * 3, getY() + 1.5, getZ() + (random.nextDouble() - 0.5) * 3,
					0, -0.05, 0);
		}
		if (isVulnerable() && tickCount % 3 == 0) {
			Vec3 m = mouth();
			level().addParticle(ParticleTypes.CRIT, m.x + (random.nextDouble() - 0.5), m.y + 0.6, m.z + (random.nextDouble() - 0.5), 0, 0.1, 0);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return AshwyrmSounds.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return AshwyrmSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return AshwyrmSounds.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(AshwyrmSounds.STEP, 1.0f, 0.7f);
	}

	@Override
	protected SoundEvent staggerSound() {
		return AshwyrmSounds.ROAR;
	}

	@Override
	protected float getSoundVolume() {
		return 2.5f;
	}
}
