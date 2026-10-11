package io.github.tmldude.blocksouls.mobs.entity;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.ai.Shockwave;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * Waddles up to players, swells (a 1.5 s hissing telegraph) and bursts: damage around it plus a lingering poison
 * cloud. Also bursts when killed. Never damages terrain.
 */
public class Bloater extends WaveMob {
	public static final byte ACTION_SWELL = 1;

	private boolean burst;

	public Bloater(EntityType<? extends Bloater> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.Bloater config() {
		return BlockSoulsMobs.CONFIG.bloater;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new SwellGoal());
		// Walks into range; its "attack" is the burst, so the melee goal only moves it (damage is 0).
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		return false;
	}

	/** 0..1, how far into the swell it is (client and server). */
	public float swellProgress() {
		return action() == ACTION_SWELL ? Math.min(1f, actionTicks() / (float) config().swellTicks) : 0f;
	}

	/** Bursts now: damage, poison cloud, gone. Safe to call more than once. */
	public void burst(ServerLevel level) {
		if (burst) {
			return;
		}
		burst = true;
		MobsConfig.Bloater config = config();
		level.playSound(null, getX(), getY(), getZ(), MobSounds.BLOATER_BURST, SoundSource.HOSTILE, 2.0f, 1.0f);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 3, 0.5, 0.5, 0.5, 0);
		level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.8, getZ(), 40, 1.0, 0.6, 1.0, 0.2);
		Shockwave.hit(level, this, position().add(0, 0.5, 0), config.burstRadius, config.burstDamage, 0.4);

		AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
		cloud.setOwner(this);
		cloud.setRadius(config.cloudRadius);
		cloud.setRadiusOnUse(-0.1f);
		cloud.setWaitTime(5);
		cloud.setDuration(config.cloudSeconds * 20);
		cloud.setRadiusPerTick(-cloud.getRadius() / (float) cloud.getDuration() / 2);
		cloud.addEffect(new MobEffectInstance(MobEffects.POISON, config.poisonSeconds * 20, 0));
		level.addFreshEntity(cloud);

		if (isAlive()) {
			kill(level);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (!burst && level() instanceof ServerLevel level) {
			burst(level);
		}
		super.die(source);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && action() == ACTION_SWELL && actionTicks() % 10 == 0) {
			playSound(MobSounds.BLOATER_HISS, 1.0f, 0.8f + 0.4f * swellProgress());
		}
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.BLOATER_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.BLOATER_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.BLOATER_DEATH;
	}

	/** Swells while a target is close; deflates if it gets away; bursts when fully swollen. */
	private class SwellGoal extends Goal {
		SwellGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		private boolean targetClose(double range) {
			LivingEntity target = getTarget();
			return target != null && distanceToSqr(target) < range * range;
		}

		@Override
		public boolean canUse() {
			return targetClose(config().triggerRange) || action() == ACTION_SWELL;
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_SWELL;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			getNavigation().stop();
			if (action() != ACTION_SWELL) {
				setAction(ACTION_SWELL, 0);
			}
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target != null) {
				getLookControl().setLookAt(target);
			}
			int t = actionTicks();
			// Once committed past the halfway point there's no backing out.
			if (t < config().swellTicks / 2 && !targetClose(config().triggerRange * 2)) {
				t -= 2;
			} else {
				t++;
			}
			if (t <= 0) {
				clearAction();
			} else if (t >= config().swellTicks && level() instanceof ServerLevel level) {
				burst(level);
			} else {
				setAction(ACTION_SWELL, t);
			}
		}
	}
}
