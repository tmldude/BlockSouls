package io.github.tmldude.blocksouls.mobs.entity;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.ai.Shockwave;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/** Huge and slow. Ground slam: a one-second windup, then a shockwave that knocks everyone back. Always breaks blocks. */
public class Brute extends WaveMob {
	public static final byte ACTION_SLAM = 1;

	private int nextSlam;

	public Brute(EntityType<? extends Brute> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.Brute config() {
		return BlockSoulsMobs.CONFIG.brute;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new SlamGoal());
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
	}

	@Override
	protected boolean alwaysBreaksBlocks() {
		return true;
	}

	@Override
	public double breakSpeed() {
		return config().breakSpeed;
	}

	/** 0..1 through the windup (client and server). */
	public float slamProgress() {
		return action() == ACTION_SLAM ? Math.min(1f, actionTicks() / (float) config().slamWindupTicks) : 0f;
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.BRUTE_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.BRUTE_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.BRUTE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.5f;
	}

	private class SlamGoal extends Goal {
		SlamGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			double range = config().slamRange;
			return target != null && tickCount >= nextSlam && onGround() && distanceToSqr(target) < range * range;
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_SLAM;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setAction(ACTION_SLAM, 0);
			playSound(MobSounds.BRUTE_WINDUP, 1.5f, 0.8f);
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target != null) {
				getLookControl().setLookAt(target);
			}
			int t = actionTicks() + 1;
			if (t < config().slamWindupTicks) {
				setAction(ACTION_SLAM, t);
				return;
			}
			if (level() instanceof ServerLevel level) {
				level.playSound(null, getX(), getY(), getZ(), MobSounds.BRUTE_SLAM, SoundSource.HOSTILE, 2.0f, 0.9f);
				Shockwave.floorDust(level, position(), config().slamRadius);
				Shockwave.hit(level, Brute.this, position(), config().slamRadius, getAttributeValue(Attributes.ATTACK_DAMAGE), config().slamKnockback);
			}
			nextSlam = tickCount + config().slamCooldownTicks;
			clearAction();
		}

		@Override
		public void stop() {
			if (action() == ACTION_SLAM) {
				clearAction();
			}
		}
	}
}
