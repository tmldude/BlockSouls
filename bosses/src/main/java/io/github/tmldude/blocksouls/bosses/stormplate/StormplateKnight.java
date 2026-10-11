package io.github.tmldude.blocksouls.bosses.stormplate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Stormplate Knight: a towering knight in layered dragon-scale plate with a lightning spear and a torn cape. It
 * arrives with its own ruin (a ring of broken pillars, {@link StormplateRuin}) under a storm cloud and kneels at the
 * center until someone comes close. Phase 1: thrust, sweep, thunder call, spear throw. Phase 2 (below 50%): the storm
 * intensifies, leap slam joins, thunder leaves charged puddles and calls two circles per player.
 */
public class StormplateKnight extends BossEntity {
	private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(StormplateKnight.class, EntityDataSerializers.BLOCK_POS);
	private static final DustParticleOptions CLOUD_DARK = new DustParticleOptions(0x1B2233, 4.0f);
	private static final DustParticleOptions CLOUD_LIGHT = new DustParticleOptions(0x3A4560, 3.5f);
	private static BossDefinition definition;

	private final StormPuddles puddles = new StormPuddles();
	private boolean ruinPending;
	private boolean ruinBuilt;

	public StormplateKnight(EntityType<? extends StormplateKnight> type, Level level) {
		super(type, level);
	}

	public static StormplateConfig config() {
		return StormplateKnights.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		StormplateConfig c = config();
		StormplateAttacks.Thrust thrust = new StormplateAttacks.Thrust();
		StormplateAttacks.Sweep sweep = new StormplateAttacks.Sweep();
		StormplateAttacks.ThunderCall thunder = new StormplateAttacks.ThunderCall();
		StormplateAttacks.SpearThrow spearThrow = new StormplateAttacks.SpearThrow();
		StormplateAttacks.Leap leap = new StormplateAttacks.Leap();
		definition = BossDefinition.builder(CommonIds.STORMPLATE_KNIGHT)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.music(StormplateSounds.MUSIC)
				// A legendary gun, only when the guns module is installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/legendary_gun"))
				.phase(BossPhase.first().attacks(thrust, sweep, thunder, spearThrow))
				.phase(BossPhase.below(c.phase2Threshold)
						.attacks(thrust, sweep, thunder, spearThrow, leap)
						.damage(c.phase2Damage)
						.recovery(c.phase2Recovery)
						.cooldowns(c.phase2Cooldowns)
						.speed(c.phase2Speed))
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

	StormPuddles puddles() {
		return puddles;
	}

	/** Live charged puddles (tests, debug). */
	public int puddleCount() {
		return puddles.size();
	}

	/** Not engaged yet: it kneels at the center of its ruin (client and server). */
	public boolean kneeling() {
		return !isEngaged() && !isReturning();
	}

	/** The arena center, synced so the client can hang the storm cloud over it. */
	public BlockPos stormCenter() {
		return entityData.get(DATA_HOME);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_HOME, BlockPos.ZERO);
	}

	@Override
	protected double preferredDistance() {
		return 3.0;
	}

	// ---------------------------------------------------------------------------------------------------------
	// The ruin and the storm

	/** Spawned for real (field spawn, command, egg): raise the ruin on the first tick, once. */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		if (!ruinBuilt) {
			ruinPending = true;
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (ruinPending) {
			ruinPending = false;
			ruinBuilt = true;
			StormplateConfig c = config();
			StormplateRuin.build(level, home(), c.ruinRadius, c.ruinPillars, c.ruinFloorShare, c.ruinMaxStep, getRandom());
		}
		if (!stormCenter().equals(home())) {
			entityData.set(DATA_HOME, home());
		}
		if (isEngaged() && phase() >= 1 && !isStaggered()) {
			puddles.tick(level, this);
		}
		flickers(level);
	}

	/** Lightning flickers around the ruin while someone is near enough to see it; twice as often in phase 2. */
	private void flickers(ServerLevel level) {
		int interval = Math.max(20, config().flickerIntervalTicks / (phase() >= 1 ? 2 : 1));
		if (random.nextInt(interval) != 0 || level.getNearestPlayer(getX(), getY(), getZ(), 128, false) == null) {
			return;
		}
		BlockPos home = home();
		double a = random.nextDouble() * Math.PI * 2;
		double r = 6 + random.nextDouble() * Math.max(1, config().ruinRadius - 4);
		Vec3 at = StormplateAttacks.ground(level, new Vec3(home.getX() + 0.5 + Math.cos(a) * r, home.getY(), home.getZ() + 0.5 + Math.sin(a) * r));
		flash(level, at);
		level.playSound(null, at.x, at.y, at.z, StormplateSounds.THUNDER, SoundSource.WEATHER, 2.5f, 0.7f + random.nextFloat() * 0.3f);
	}

	/** A real-looking bolt that does nothing: no damage, no fire, no block changes, no world-wide thunder. */
	static void flash(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			bolt.setSilent(true);
			level.addFreshEntity(bolt);
		}
	}

	@Override
	protected void onPhaseEnter(int index) {
		if (index != 1 || !(level() instanceof ServerLevel level)) {
			return;
		}
		// The storm intensifies: a bolt into the knight itself, and the floor starts to crackle.
		flash(level, position());
		level.playSound(null, getX(), getY(), getZ(), StormplateSounds.THUNDER, SoundSource.HOSTILE, 4.0f, 0.6f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2, getZ(), 80, 1.0, 1.6, 1.0, 0.3);
		BlockPos home = home();
		for (int i = 0; i < config().puddlesOnPhase2; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = 4 + random.nextDouble() * Math.max(1, arenaRadius() - 7);
			puddles.add(StormplateAttacks.ground(level, new Vec3(home.getX() + 0.5 + Math.cos(a) * r, home.getY(), home.getZ() + 0.5 + Math.sin(a) * r)));
		}
	}

	@Override
	protected void onReset() {
		puddles.clear();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		puddles.clear();
	}

	// ---------------------------------------------------------------------------------------------------------
	// Lightning-proof

	@Override
	public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypes.LIGHTNING_BOLT) || super.isInvulnerableTo(level, source);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Client effects

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			sparks();
			cloud();
		}
	}

	/** Sparks crawling over the plate; more in phase 2. */
	private void sparks() {
		int every = phase() >= 1 ? 1 : 3;
		if (tickCount % every != 0) {
			return;
		}
		double a = random.nextDouble() * Math.PI * 2;
		double h = kneeling() ? 2.6 : 3.6;
		level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * 0.7, getY() + 0.4 + random.nextDouble() * h, getZ() + Math.sin(a) * 0.7,
				(random.nextDouble() - 0.5) * 0.1, random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.1);
	}

	/** A low storm cloud hanging over the ruin, darker and busier in phase 2. */
	private void cloud() {
		BlockPos center = stormCenter();
		if (center.equals(BlockPos.ZERO)) {
			return;
		}
		int count = phase() >= 1 ? 4 : 2;
		double radius = config().ruinRadius * 0.85;
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(random.nextDouble()) * radius;
			double x = center.getX() + 0.5 + Math.cos(a) * r;
			double z = center.getZ() + 0.5 + Math.sin(a) * r;
			double y = center.getY() + 16 + random.nextDouble() * 2.5 - r * 0.08;
			level().addAlwaysVisibleParticle(random.nextBoolean() ? CLOUD_DARK : CLOUD_LIGHT, x, y, z, 0, 0, 0);
		}
		if (random.nextInt(phase() >= 1 ? 6 : 14) == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = random.nextDouble() * radius;
			level().addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, center.getX() + Math.cos(a) * r, center.getY() + 15.5,
					center.getZ() + Math.sin(a) * r, 0, -0.3, 0);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return StormplateSounds.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return StormplateSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return StormplateSounds.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(StormplateSounds.STEP, 0.9f, 0.7f);
	}

	@Override
	protected SoundEvent staggerSound() {
		return StormplateSounds.ROAR;
	}

	@Override
	protected float getSoundVolume() {
		return 1.8f;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Save data

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("StormRuinPending", ruinPending);
		output.putBoolean("StormRuinBuilt", ruinBuilt);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		ruinPending = input.getBooleanOr("StormRuinPending", false);
		ruinBuilt = input.getBooleanOr("StormRuinBuilt", false);
	}
}
