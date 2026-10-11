package io.github.tmldude.blocksouls.mobs.ai;

import java.util.EnumSet;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Leaps at a target within range: a quick, low arc that's hard to track. Tunables are read live from config. */
public class PounceGoal extends Goal {
	private final Mob mob;
	private final DoubleSupplier range;
	private final DoubleSupplier up;
	private final DoubleSupplier forward;
	private final IntSupplier cooldown;
	private final @Nullable SoundEvent sound;
	private int nextPounce;

	public PounceGoal(Mob mob, DoubleSupplier range, DoubleSupplier up, DoubleSupplier forward, IntSupplier cooldown, @Nullable SoundEvent sound) {
		this.mob = mob;
		this.range = range;
		this.up = up;
		this.forward = forward;
		this.cooldown = cooldown;
		this.sound = sound;
		setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		if (target == null || !mob.onGround() || mob.tickCount < nextPounce || mob.isVehicle()) {
			return false;
		}
		double distSqr = mob.distanceToSqr(target);
		double r = range.getAsDouble();
		return distSqr > 2.25 && distSqr < r * r && mob.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return !mob.onGround();
	}

	@Override
	public void start() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}
		Vec3 flat = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
		if (flat.lengthSqr() > 1.0E-7) {
			flat = flat.normalize().scale(forward.getAsDouble());
		}
		mob.setDeltaMovement(flat.x, up.getAsDouble(), flat.z);
		mob.getLookControl().setLookAt(target);
		nextPounce = mob.tickCount + cooldown.getAsInt();
		if (sound != null) {
			mob.playSound(sound, 1.0f, 1.0f);
		}
	}
}
