package io.github.tmldude.blocksouls.mobs.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.logic.AttackPicker;
import io.github.tmldude.blocksouls.mobs.logic.FlankMath;

/**
 * A mob's special attacks scored together by {@link AttackPicker}: each attack goal asks {@link #chosen} in
 * {@code canUse} and calls {@link #used} when it fires. The pick is made at most once per tick.
 */
public final class AttackChooser {
	private final Mob mob;
	private final List<Supplier<AttackPicker.Attack>> attacks = new ArrayList<>();
	private final List<BooleanSupplier> ready = new ArrayList<>();
	private int lastUsed = -1;
	private int pickedTick = -1;
	private int picked = -1;

	public AttackChooser(Mob mob) {
		this.mob = mob;
	}

	/** Adds an attack (read fresh from config each pick) with its own readiness (cooldown, footing); returns its index. */
	public int add(Supplier<AttackPicker.Attack> attack, BooleanSupplier isReady) {
		attacks.add(attack);
		ready.add(isReady);
		return attacks.size() - 1;
	}

	/** Whether attack {@code index} is this tick's pick against the mob's target. */
	public boolean chosen(int index) {
		if (pickedTick != mob.tickCount) {
			pickedTick = mob.tickCount;
			picked = pick(mob.getTarget());
		}
		return picked == index;
	}

	/** Records that attack {@code index} fired (it's penalised next pick). */
	public void used(int index) {
		lastUsed = index;
		pickedTick = -1;
	}

	private int pick(@Nullable LivingEntity target) {
		if (target == null) {
			return -1;
		}
		MobsConfig.AttackPicking weights = BlockSoulsMobs.CONFIG.attackPicking;
		boolean[] isReady = new boolean[attacks.size()];
		List<AttackPicker.Attack> list = new ArrayList<>(attacks.size());
		for (int i = 0; i < attacks.size(); i++) {
			isReady[i] = ready.get(i).getAsBoolean();
			list.add(attacks.get(i).get());
		}
		return AttackPicker.pick(list, situation(mob, target, weights), isReady, lastUsed, weights);
	}

	public static AttackPicker.Situation situation(Mob mob, LivingEntity target, MobsConfig.AttackPicking weights) {
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double angle = Math.abs(FlankMath.wrap(FlankMath.yaw(dx, dz) - mob.getYHeadRot()));
		boolean airborne = !target.onGround() && !target.isInWater() && !target.isPassenger();
		return new AttackPicker.Situation(mob.distanceTo(target), angle, RollTracker.rolledWithin(target, weights.rollWindowTicks), airborne);
	}
}
