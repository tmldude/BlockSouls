package io.github.tmldude.blocksouls.bosses.palerider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.logic.BossScaling;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Pale Rider: a ghostly horseman fused to a spectral horse (~3.5 blocks), roaming plains at night in pale fog.
 * Phase 1 (mounted): lance charge (two passes), spectral sweep, rearing stomp, wraith call. Phase 2 (below 55%,
 * unhorsed): the horse dissolves; the rider fights on foot with a spear fan, a blink behind its target and a quick
 * slash, floats toward far targets, and every 20 s the phantom horse charges across the arena along a telegraphed lane.
 */
public class PaleRider extends BossEntity {
	/** Pale blue-white telegraph outline. */
	static final ParticleOptions OUTLINE = new DustParticleOptions(0xA9BBD6, 1.6f);
	/** Near-white fill as the windup runs out. */
	static final ParticleOptions FILL = new DustParticleOptions(0xE8F0FF, 1.2f);
	/** Entity tag on the summoned skeleton riders (and their horses). */
	static final String ADD_TAG = "blocksouls_pale_rider_add";

	private static final EntityDimensions ON_FOOT = EntityDimensions.scalable(0.9f, 2.4f).withEyeHeight(2.1f);

	private static BossDefinition definition;

	private int dimensionsPhase;
	private final List<UUID> adds = new ArrayList<>();
	/** Bit i set: the wraith call was used in phase i. */
	private int callsUsed;
	private int ticksInPhase;
	private @Nullable PhantomCharge phantom;

	public PaleRider(EntityType<? extends PaleRider> type, Level level) {
		super(type, level);
		this.xpReward = config().xpReward;
	}

	public static PaleRiderConfig config() {
		return PaleRiders.CONFIG;
	}

	/** Built once, after the config loads. */
	static BossDefinition createDefinition() {
		PaleRiderConfig c = config();
		PaleRiderAttacks.Lance lance = new PaleRiderAttacks.Lance();
		PaleRiderAttacks.Sweep sweep = new PaleRiderAttacks.Sweep();
		PaleRiderAttacks.Stomp stomp = new PaleRiderAttacks.Stomp();
		PaleRiderAttacks.WraithCall call = new PaleRiderAttacks.WraithCall();
		PaleRiderAttacks.Spears spears = new PaleRiderAttacks.Spears();
		PaleRiderAttacks.Blink blink = new PaleRiderAttacks.Blink();
		PaleRiderAttacks.Slash slash = new PaleRiderAttacks.Slash();
		definition = BossDefinition.builder(CommonIds.PALE_RIDER)
				.health(c.health)
				.arenaRadius(c.arenaRadius)
				.music(PaleRiderSounds.MUSIC)
				// A legendary gun, only when the guns module is installed.
				.extraLoot(CommonIds.id(CommonIds.BOSSES, "boss_drops/legendary_gun"))
				.phase(BossPhase.first().attacks(lance, sweep, stomp, call))
				.phase(BossPhase.below(c.phase2Threshold)
						.attacks(spears, blink, slash, call)
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

	/** Phase 2: the horse is gone (both sides). */
	public boolean unhorsed() {
		return phase() >= 1;
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return unhorsed() ? ON_FOOT : super.getDefaultDimensions(pose);
	}

	@Override
	protected double preferredDistance() {
		return unhorsed() ? 2.5 : 4;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Ticking

	@Override
	public void tick() {
		super.tick();
		if (phase() != dimensionsPhase) {
			dimensionsPhase = phase();
			refreshDimensions();
		}
		if (level().isClientSide() && isAlive()) {
			mist();
		}
	}

	/** Pale fog around it; thick while it roams unengaged, a thin trail in the fight. */
	private void mist() {
		double r = unhorsed() ? 0.6 : 1.2;
		int every = isEngaged() ? 3 : 1;
		if (tickCount % every == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			level().addParticle(ParticleTypes.WHITE_ASH, getX() + Math.cos(a) * r, getY() + random.nextDouble() * getBbHeight(),
					getZ() + Math.sin(a) * r, 0, 0.02, 0);
		}
		if (!isEngaged() && tickCount % 2 == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = 1 + random.nextDouble() * 4;
			level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * d, getY() + 0.2, getZ() + Math.sin(a) * d,
					(random.nextDouble() - 0.5) * 0.02, 0.005, (random.nextDouble() - 0.5) * 0.02);
		}
		if (tickCount % 8 == 0) {
			level().addParticle(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.85, getZ(), 0, 0.03, 0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (isRemoved()) {
			return;
		}
		if (!isEngaged()) {
			phantom = null;
			ticksInPhase = 0;
			if (isFieldBoss()) {
				if (level.isBrightOutside()) {
					// Dawn: the rider fades with the night.
					leave(level);
					return;
				}
				int every = config().hoofbeatIntervalTicks;
				if (every > 0 && tickCount % every == 0) {
					// Volume 4 carries ~64 blocks: distant hoofbeats.
					level.playSound(null, getX(), getY(), getZ(), PaleRiderSounds.HOOVES, SoundSource.HOSTILE, 4.0f, 0.7f + random.nextFloat() * 0.1f);
				}
			}
			return;
		}
		if (unhorsed() && !isStaggered()) {
			ticksInPhase++;
			if (phantom == null && PaleRiderMath.phantomChargeDue(ticksInPhase, config().phantomChargeIntervalTicks)) {
				startPhantomCharge(level);
			}
		}
		if (phantom != null && phantom.tick(level)) {
			phantom = null;
		}
	}

	@Override
	protected void fightTick(ServerLevel level, @Nullable LivingEntity target) {
		if (!unhorsed() || target == null || !onGround()) {
			return;
		}
		// On foot it drifts: a short floating glide toward a far target.
		int every = config().floatIntervalTicks;
		if (every > 0 && tickCount % every == 0 && distanceTo(target) > config().floatMinDistance) {
			Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
			setDeltaMovement(dir.x * config().floatSpeed, 0.35, dir.z * config().floatSpeed);
			needsSync = true;
			level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 6, 0.3, 0.1, 0.3, 0.02);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Wraith call: skeleton riders on skeleton horses

	/** Whether the wraith call is still available in the current phase. */
	boolean canCallWraiths() {
		return (callsUsed & (1 << phase())) == 0;
	}

	void summonWraiths(ServerLevel level, @Nullable LivingEntity target) {
		callsUsed |= 1 << phase();
		for (int i = 0; i < config().wraithCallRiders; i++) {
			double a = Math.PI * 2 * i / Math.max(1, config().wraithCallRiders) + random.nextDouble() * 0.5;
			double r = Math.min(3, arenaRadius() - 1.5);
			Vec3 at = position().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
			SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
			Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (horse == null || skeleton == null) {
				continue;
			}
			horse.snapTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
			horse.setTamed(true);
			horse.setAge(0);
			horse.setPersistenceRequired();
			horse.addTag(ADD_TAG);
			horse.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, config().wraithCallSpeedAmplifier, false, false));
			skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			skeleton.snapTo(at.x, at.y, at.z, horse.getYRot(), 0);
			skeleton.setPersistenceRequired();
			skeleton.addTag(ADD_TAG);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				skeleton.setDropChance(slot, 0);
			}
			skeleton.startRiding(horse);
			if (target != null) {
				skeleton.setTarget(target);
			}
			level.addFreshEntityWithPassengers(horse);
			adds.add(horse.getUUID());
			adds.add(skeleton.getUUID());
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 20, 0.5, 0.8, 0.5, 0.03);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 12, 0.6, 0.3, 0.6, 0.02);
		}
	}

	/** Every summoned rider and horse still in the world. */
	public List<Entity> wraiths() {
		List<Entity> alive = new ArrayList<>();
		if (level() instanceof ServerLevel level) {
			for (UUID id : adds) {
				Entity entity = level.getEntity(id);
				if (entity != null && entity.isAlive()) {
					alive.add(entity);
				}
			}
		}
		return alive;
	}

	private void dismissWraiths() {
		if (level() instanceof ServerLevel level) {
			for (Entity entity : wraiths()) {
				level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1, entity.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
				entity.discard();
			}
		}
		adds.clear();
	}

	// ---------------------------------------------------------------------------------------------------------
	// Phantom charge (phase 2): the dissolved horse thunders across the arena on its own

	private void startPhantomCharge(ServerLevel level) {
		LivingEntity target = getTarget();
		Vec3 through = target != null ? target.position() : position();
		double angle = random.nextDouble() * Math.PI * 2;
		Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		double length = Math.max(8, arenaRadius() * 2 - 2);
		Vec3 start = through.subtract(dir.scale(length / 2));
		phantom = new PhantomCharge(start, dir, length);
		level.playSound(null, start.x, start.y, start.z, PaleRiderSounds.HOOVES, SoundSource.HOSTILE, 4.0f, 0.6f);
		playSound(PaleRiderSounds.SCREAM, 2.0f, 1.3f);
	}

	/** Whether a phantom charge is telegraphing or running (tests, debug). */
	public boolean phantomChargeActive() {
		return phantom != null;
	}

	private final class PhantomCharge {
		private final Vec3 start;
		private final Vec3 dir;
		private final double length;
		private final Set<UUID> hit = new HashSet<>();
		private int tick;

		PhantomCharge(Vec3 start, Vec3 dir, double length) {
			this.start = start;
			this.dir = dir;
			this.length = length;
		}

		/** Returns true when done. */
		boolean tick(ServerLevel level) {
			PaleRiderConfig c = config();
			int windup = c.phantomChargeWindupTicks;
			if (tick < windup) {
				if (tick % 3 == 0) {
					Telegraphs.lineFilling(level, OUTLINE, FILL, start, dir, length, c.phantomChargeHalfWidth, (tick + 1) / (float) windup);
				}
			} else {
				int t = tick - windup;
				int ticks = Math.max(1, c.phantomChargeTicks);
				double from = length * t / ticks;
				double to = length * (t + 1) / ticks;
				Vec3 head = start.add(dir.scale(to));
				if (t % 3 == 0) {
					level.playSound(null, head.x, head.y, head.z, PaleRiderSounds.HOOVES, SoundSource.HOSTILE, 2.5f, 0.8f);
				}
				for (double d = from; d <= to; d += 0.75) {
					Vec3 p = start.add(dir.scale(d));
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, p.x, p.y + 0.8, p.z, 4, 0.4, 0.6, 0.4, 0.02);
					level.sendParticles(ParticleTypes.CLOUD, true, true, p.x, p.y + 1.2, p.z, 3, 0.5, 0.7, 0.5, 0.02);
				}
				Vec3 segment = start.add(dir.scale(from));
				double damage = BossScaling.damage(c.phantomChargeDamage, damageMultiplier(), definition().phase(phase()).damageMult());
				for (LivingEntity victim : Hitboxes.inLine(PaleRider.this, segment, dir, to - from, c.phantomChargeHalfWidth, 2.5)) {
					if (hit.add(victim.getUUID()) && victim.hurtServer(level, level.damageSources().mobAttack(PaleRider.this), (float) damage)) {
						victim.knockback(1.0, -dir.x, -dir.z, level.damageSources().mobAttack(PaleRider.this), 0);
						victim.needsSync = true;
					}
				}
				if (t + 1 >= ticks) {
					return true;
				}
			}
			tick++;
			return false;
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Fight hooks

	@Override
	protected void onPhaseEnter(int index) {
		ticksInPhase = 0;
		phantom = null;
		if (index == 1 && level() instanceof ServerLevel level) {
			// The horse dissolves into mist.
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 80, 1.2, 0.8, 1.2, 0.05);
			level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 40, 1.0, 0.8, 1.0, 0.05);
			level.playSound(null, getX(), getY(), getZ(), PaleRiderSounds.STEED_FADE, SoundSource.HOSTILE, 3.0f, 0.6f);
		}
	}

	@Override
	protected void onReset() {
		dismissWraiths();
		callsUsed = 0;
		ticksInPhase = 0;
		phantom = null;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		dismissWraiths();
		phantom = null;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy()) {
			dismissWraiths();
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return PaleRiderSounds.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return PaleRiderSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return PaleRiderSounds.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (!unhorsed()) {
			playSound(PaleRiderSounds.HOOVES, 0.6f, 1.0f);
		}
	}

	@Override
	protected SoundEvent staggerSound() {
		return PaleRiderSounds.SCREAM;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6f;
	}
}
