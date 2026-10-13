package io.github.tmldude.blocksouls.mobs.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.ai.AttackChooser;
import io.github.tmldude.blocksouls.mobs.ai.Shockwave;
import io.github.tmldude.blocksouls.mobs.logic.AttackPicker;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * The elite. Leap slam (a ring of green dust marks where it will land, then it jumps and slams there) and thrown
 * debris at range. Always breaks blocks, takes no fall damage, survives dawn (not in {@code burn_in_daylight}).
 */
public class MutantBrute extends WaveMob {
	public static final byte ACTION_LEAP_WINDUP = 1;
	public static final byte ACTION_LEAPING = 2;
	public static final byte ACTION_THROW = 3;
	public static final int THROW_WINDUP_TICKS = 15;
	/** Rad-green, matching its veins. */
	private static final int RING_COLOR = 0x7FD33A;

	private int nextLeap;
	private int nextThrow;
	private @Nullable Vec3 landing;
	/** Leap and throw scored against each other (range, facing, a dodge roll to punish, a target in the air). */
	private final AttackChooser attacks = new AttackChooser(this);
	private final int leap = attacks.add(() -> new AttackPicker.Attack(config().leapMinRange, config().leapMaxRange, false, true, true, 0),
			() -> tickCount >= nextLeap && onGround());
	private final int toss = attacks.add(() -> new AttackPicker.Attack(config().debrisMinRange, getAttributeValue(Attributes.FOLLOW_RANGE), true, false, false, 0),
			() -> tickCount >= nextThrow);

	public MutantBrute(EntityType<? extends MutantBrute> type, Level level) {
		super(type, level);
	}

	private static MobsConfig.MutantBrute config() {
		return BlockSoulsMobs.CONFIG.mutantBrute;
	}

	@Override
	protected void registerAttackGoals() {
		goalSelector.addGoal(2, new LeapSlamGoal());
		goalSelector.addGoal(3, new ThrowDebrisGoal());
		goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
	}

	@Override
	protected boolean alwaysBreaksBlocks() {
		return true;
	}

	@Override
	public double breakSpeed() {
		return config().breakSpeed;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent ambientSound() {
		return MobSounds.MUTANT_BRUTE_AMBIENT;
	}

	@Override
	protected SoundEvent hurtSound() {
		return MobSounds.MUTANT_BRUTE_HURT;
	}

	@Override
	protected SoundEvent deathSound() {
		return MobSounds.MUTANT_BRUTE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	private static void ring(ServerLevel level, Vec3 center, double radius) {
		DustParticleOptions dust = new DustParticleOptions(RING_COLOR, 1.6f);
		int points = 36;
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2 / points;
			level.sendParticles(dust, center.x + Math.cos(angle) * radius, center.y + 0.15, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
		level.sendParticles(dust, center.x, center.y + 0.15, center.z, 6, 0.4, 0, 0.4, 0);
	}

	/** Windup with a landing ring at the target, a ballistic jump, a slam where it lands. */
	private class LeapSlamGoal extends Goal {
		private int airTicks;

		LeapSlamGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			if (target == null || tickCount < nextLeap || !onGround() || !getSensing().hasLineOfSight(target)) {
				return false;
			}
			double dist = distanceTo(target);
			return dist >= config().leapMinRange && dist <= config().leapMaxRange && attacks.chosen(leap);
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_LEAP_WINDUP || action() == ACTION_LEAPING;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			LivingEntity target = getTarget();
			landing = target == null ? position() : target.position();
			getNavigation().stop();
			setAction(ACTION_LEAP_WINDUP, 0);
			playSound(MobSounds.MUTANT_BRUTE_ROAR, 2.0f, 1.0f);
			airTicks = 0;
		}

		@Override
		public void tick() {
			if (!(level() instanceof ServerLevel level) || landing == null) {
				clearAction();
				return;
			}
			getLookControl().setLookAt(landing.x, landing.y, landing.z);
			if (action() == ACTION_LEAP_WINDUP) {
				int t = actionTicks() + 1;
				if (t % 4 == 0) {
					ring(level, landing, config().slamRadius);
				}
				if (t < config().leapWindupTicks) {
					setAction(ACTION_LEAP_WINDUP, t);
					return;
				}
				launch();
				setAction(ACTION_LEAPING, 0);
				return;
			}

			airTicks++;
			setAction(ACTION_LEAPING, airTicks);
			if (airTicks > 3 && onGround() || airTicks > 60) {
				level.playSound(null, getX(), getY(), getZ(), MobSounds.BRUTE_SLAM, SoundSource.HOSTILE, 2.5f, 0.7f);
				Shockwave.floorDust(level, position(), config().slamRadius);
				Shockwave.hit(level, MutantBrute.this, position(), config().slamRadius, getAttributeValue(Attributes.ATTACK_DAMAGE), config().slamKnockback);
				nextLeap = tickCount + config().leapCooldownTicks;
				attacks.used(leap);
				clearAction();
			}
		}

		/** Ballistic velocity towards {@link #landing}, allowing for air drag. */
		private void launch() {
			Vec3 delta = landing.subtract(position());
			double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
			int flightTicks = (int) Math.clamp(horizontal * 1.4, 12, 28);
			// Living entities keep 91% of horizontal speed per tick in the air.
			double carry = (1 - Math.pow(0.91, flightTicks)) / 0.09;
			double vx = delta.x / carry;
			double vz = delta.z / carry;
			double vy = (delta.y + 0.5 * 0.08 * flightTicks * flightTicks) / flightTicks * 1.05;
			setDeltaMovement(vx, Math.min(vy, 2.0), vz);
			syncVelocity = true;
		}

		@Override
		public void stop() {
			if (action() == ACTION_LEAP_WINDUP || action() == ACTION_LEAPING) {
				clearAction();
			}
			if (nextLeap <= tickCount) {
				nextLeap = tickCount + config().leapCooldownTicks;
			}
		}
	}

	/** Rips up a chunk of debris and hurls it at a target at range. */
	private class ThrowDebrisGoal extends Goal {
		ThrowDebrisGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			return target != null && tickCount >= nextThrow && getSensing().hasLineOfSight(target)
					&& distanceTo(target) >= config().debrisMinRange && attacks.chosen(toss);
		}

		@Override
		public boolean canContinueToUse() {
			return action() == ACTION_THROW;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setAction(ACTION_THROW, 0);
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				clearAction();
				return;
			}
			getLookControl().setLookAt(target);
			int t = actionTicks() + 1;
			if (t < THROW_WINDUP_TICKS) {
				setAction(ACTION_THROW, t);
				return;
			}
			Debris debris = new Debris(level(), MutantBrute.this);
			debris.setPos(getX(), getEyeY() + 0.3, getZ());
			double dx = target.getX() - getX();
			double dz = target.getZ() - getZ();
			double dy = target.getY(0.5) - debris.getY();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			debris.shoot(dx, dy + horizontal * 0.12, dz, 1.4f, 2.0f);
			level().addFreshEntity(debris);
			playSound(MobSounds.MUTANT_BRUTE_THROW, 1.5f, 0.8f);
			swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
			nextThrow = tickCount + config().debrisCooldownTicks;
			attacks.used(toss);
			clearAction();
		}

		@Override
		public void stop() {
			if (action() == ACTION_THROW) {
				clearAction();
			}
		}
	}
}
