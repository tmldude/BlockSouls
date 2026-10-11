package io.github.tmldude.blocksouls.mobs.entity;

import java.util.EnumSet;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * Hangs back and screams on seeing a player: nearby wave mobs get Speed and pile onto that player. Repeats on a
 * cooldown. Doesn't melee.
 */
public class Screamer extends WaveMob {
	public static final byte ACTION_SCREAM = 1;
	public static final int SCREAM_TICKS = 20;

	private int nextScream;

	public Screamer(EntityType<? extends Screamer> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.Screamer config() {
		return BlockSoulsMobs.CONFIG.screamer;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new ScreamGoal());
		goalSelector.addGoal(3, new KeepDistanceGoal());
	}

	/** Screams now at {@code player}: buffs wave mobs around and turns them onto the player. */
	public void scream(ServerLevel level, Player player) {
		MobsConfig.Screamer config = config();
		setAction(ACTION_SCREAM, 0);
		level.playSound(null, getX(), getY(), getZ(), MobSounds.SCREAMER_SCREAM, SoundSource.HOSTILE, 3.0f, 1.0f);
		level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(new DustParticleOptions(0xC2456A, 1.5f), getX(), getEyeY(), getZ(), 40, 1.2, 0.6, 1.2, 0.02);

		double r = config.screamRadius;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r), m -> m != this && m.isAlive()
				&& m.is(CommonTags.WAVE_MOBS) && m.distanceToSqr(this) <= r * r)) {
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, config.speedSeconds * 20, 0), this);
			mob.setTarget(player);
		}
		nextScream = tickCount + config.cooldownSeconds * 20;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && action() == ACTION_SCREAM) {
			int t = actionTicks() + 1;
			if (t >= SCREAM_TICKS) {
				clearAction();
			} else {
				setAction(ACTION_SCREAM, t);
			}
		}
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.SCREAMER_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.SCREAMER_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.SCREAMER_DEATH;
	}

	private class ScreamGoal extends Goal {
		ScreamGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return tickCount >= nextScream && getTarget() instanceof Player player && getSensing().hasLineOfSight(player)
					&& distanceToSqr(player) < config().screamRadius * config().screamRadius;
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_SCREAM;
		}

		@Override
		public void start() {
			getNavigation().stop();
			if (getTarget() instanceof Player player && level() instanceof ServerLevel level) {
				getLookControl().setLookAt(player);
				scream(level, player);
			}
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target != null) {
				getLookControl().setLookAt(target);
			}
		}
	}

	/** Stays around {@code keepAway} blocks from the target: backs off when closer, closes in when far. */
	private class KeepDistanceGoal extends Goal {
		KeepDistanceGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().setLookAt(target);
			double keep = config().keepAway;
			double dist = distanceTo(target);
			if (dist < keep - 1) {
				if (getNavigation().isDone() || tickCount % 10 == 0) {
					Vec3 away = LandRandomPos.getPosAway(Screamer.this, 10, 4, target.position());
					if (away != null) {
						getNavigation().moveTo(away.x, away.y, away.z, 1.2);
					}
				}
			} else if (dist > keep + 4) {
				if (tickCount % 10 == 0) {
					getNavigation().moveTo(target, 1.0);
				}
			} else {
				getNavigation().stop();
			}
		}
	}
}
