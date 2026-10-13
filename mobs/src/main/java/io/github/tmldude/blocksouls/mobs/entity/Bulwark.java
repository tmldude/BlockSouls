package io.github.tmldude.blocksouls.mobs.entity;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.logic.FlankMath;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * The shielded mob. A slab of scrap in front soaks most damage from its front arc (a metal clang and sparks say so),
 * and it turns slowly, so the party has to get round it. Shield bash: it rears back for a beat, then shoves whoever's
 * in front.
 */
public class Bulwark extends WaveMob {
	public static final byte ACTION_BASH = 1;

	private int nextBash;
	private float lastYaw = Float.NaN;

	public Bulwark(EntityType<? extends Bulwark> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.Bulwark config() {
		return BlockSoulsMobs.CONFIG.bulwark;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new BashGoal());
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
	}

	/** 0..1 through the bash windup (client and server). */
	public float bashProgress() {
		return action() == ACTION_BASH ? Math.min(1f, actionTicks() / (float) Math.max(1, config().bashWindupTicks)) : 0f;
	}

	/** Whether a hit from {@code from} lands on the shield (its front arc). */
	public boolean shields(Vec3 from) {
		double bearing = FlankMath.bearing(yBodyRot, from.x - getX(), from.z - getZ());
		return FlankMath.inFrontArc(bearing, config().frontArcDegrees);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Vec3 from = source.getSourcePosition();
		if (from != null && !source.is(DamageTypeTags.BYPASSES_SHIELD) && shields(from)) {
			damage *= (float) config().frontDamageFactor;
			level.playSound(null, getX(), getY(), getZ(), MobSounds.BULWARK_CLANG, SoundSource.HOSTILE, 1.2f, 0.9f + getRandom().nextFloat() * 0.2f);
			Vec3 spark = position().add(from.subtract(position()).multiply(1, 0, 1).normalize().scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.CRIT, spark.x, spark.y, spark.z, 8, 0.2, 0.3, 0.2, 0.2);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		// Slow turning: the body (and shield) swings at most turnDegreesPerTick toward where the AI wants it.
		if (!Float.isNaN(lastYaw)) {
			float max = config().turnDegreesPerTick;
			float delta = (float) Math.clamp(FlankMath.wrap(yBodyRot - lastYaw), -max, max);
			yBodyRot = lastYaw + delta;
			setYRot(yBodyRot);
		}
		lastYaw = yBodyRot;
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.BULWARK_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.BULWARK_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.BULWARK_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.3f;
	}

	/** Rears back (the tell), then shoves everything in its front arc within reach. */
	private class BashGoal extends Goal {
		BashGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			double range = config().bashRange;
			return target != null && tickCount >= nextBash && distanceToSqr(target) <= range * range && shields(target.position());
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_BASH;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setAction(ACTION_BASH, 0);
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target != null) {
				getLookControl().setLookAt(target);
			}
			int t = actionTicks() + 1;
			if (t < config().bashWindupTicks) {
				setAction(ACTION_BASH, t);
				return;
			}
			if (level() instanceof ServerLevel level) {
				bash(level);
			}
			nextBash = tickCount + config().bashCooldownTicks;
			clearAction();
		}

		@Override
		public void stop() {
			if (action() == ACTION_BASH) {
				clearAction();
			}
		}
	}

	/** Hits everything in front within reach (half a block of grace): damage and a hard shove. */
	public int bash(ServerLevel level) {
		MobsConfig.Bulwark config = config();
		double reach = config.bashRange + 0.5;
		level.playSound(null, getX(), getY(), getZ(), MobSounds.BULWARK_BASH, SoundSource.HOSTILE, 1.5f, 0.9f);
		swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		int hits = 0;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
				e -> e != this && e.isAlive() && !e.is(CommonTags.WAVE_MOBS) && !e.isSpectator() && e.distanceToSqr(this) <= reach * reach
						&& shields(e.position()))) {
			if (entity.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
				hits++;
			}
			Vec3 away = entity.position().subtract(position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
			entity.push(away.x * config.bashKnockback, 0.3, away.z * config.bashKnockback);
			entity.syncVelocity = true;
		}
		return hits;
	}
}
