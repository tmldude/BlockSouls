package io.github.tmldude.blocksouls.mobs.entity;

import java.util.EnumSet;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * The ranged mob. Keeps its distance and spits a lobbed glob of bile: the throat swells and it stops still for the
 * windup (the tell), then the glob flies in a visible arc, poisoning what it hits. Doesn't melee.
 */
public class Spitter extends WaveMob {
	public static final byte ACTION_SPIT = 1;

	private int nextSpit;

	public Spitter(EntityType<? extends Spitter> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.Spitter config() {
		return BlockSoulsMobs.CONFIG.spitter;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new SpitGoal());
		goalSelector.addGoal(3, new KeepDistanceGoal());
	}

	/** 0..1 through the windup (client and server). */
	public float spitProgress() {
		return action() == ACTION_SPIT ? Math.min(1f, actionTicks() / (float) Math.max(1, config().spitWindupTicks)) : 0f;
	}

	/** Fires a glob at {@code target} now. */
	public SpitGlob spit(LivingEntity target) {
		SpitGlob glob = new SpitGlob(level(), this);
		glob.setPos(getX(), getEyeY() - 0.1, getZ());
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		double dy = target.getY(0.5) - glob.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		glob.shoot(dx, dy + horizontal * 0.2, dz, config().spitSpeed, 1.5f);
		level().addFreshEntity(glob);
		playSound(MobSounds.SPITTER_SPIT, 1.2f, 0.9f + getRandom().nextFloat() * 0.2f);
		return glob;
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.SPITTER_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.SPITTER_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.SPITTER_DEATH;
	}

	private class SpitGoal extends Goal {
		SpitGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			double range = config().spitRange;
			return target != null && tickCount >= nextSpit && getSensing().hasLineOfSight(target) && distanceToSqr(target) <= range * range;
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_SPIT && getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setAction(ACTION_SPIT, 0);
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().setLookAt(target);
			int t = actionTicks() + 1;
			if (t < config().spitWindupTicks) {
				setAction(ACTION_SPIT, t);
				if (t % 3 == 0 && level() instanceof ServerLevel level) {
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SLIME_BALL), getX(), getEyeY() - 0.2, getZ(), 2, 0.15, 0.1, 0.15, 0.02);
				}
				return;
			}
			spit(target);
			nextSpit = tickCount + config().spitCooldownTicks;
			clearAction();
		}

		@Override
		public void stop() {
			if (action() == ACTION_SPIT) {
				clearAction();
			}
		}
	}

	/** Stays around {@code keepAway} blocks from the target: backs off when closer, closes in when far or out of sight. */
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
					Vec3 away = LandRandomPos.getPosAway(Spitter.this, 10, 4, target.position());
					if (away != null) {
						getNavigation().moveTo(away.x, away.y, away.z, 1.2);
					}
				}
			} else if (dist > config().spitRange - 2 || !getSensing().hasLineOfSight(target)) {
				if (tickCount % 10 == 0) {
					getNavigation().moveTo(target, 1.0);
				}
			} else {
				getNavigation().stop();
			}
		}
	}
}
