package io.github.tmldude.blocksouls.bosses.archetype;

import net.minecraft.world.entity.LivingEntity;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.logic.Fairness;

/**
 * Feint: plays the start of one move's windup (its animation and telegraph), stops, and goes straight into a
 * different move, which then gets its own full windup. Late phases only ({@link Fairness#FEINT_MIN_PHASE}), and only
 * into a follow-up whose windup meets the big-hit floor, so the real hit is always readable.
 */
public class Feint extends Attack {
	private final Attack bait;
	private final Attack real;

	public Feint(String name, ArchetypeParams.Feint params, Attack bait, Attack real) {
		super(name, params.stats);
		this.bait = bait;
		this.real = real;
	}

	public Attack bait() {
		return bait;
	}

	public Attack follow() {
		return real;
	}

	@Override
	public String animation() {
		return bait.animation();
	}

	@Override
	public double trackFraction() {
		return bait.trackFraction();
	}

	@Override
	public boolean canUse(BossEntity boss, LivingEntity target) {
		return boss.phase() >= Fairness.FEINT_MIN_PHASE && real.stats().windupTicks >= Fairness.BIG_HIT_MIN_WINDUP
				&& !boss.attackLost(real) && real.canUse(boss, target);
	}

	@Override
	public void start(AttackRun run) {
		bait.start(run);
	}

	@Override
	public void windup(AttackRun run) {
		bait.windup(run);
		run.telegraphed();
	}

	@Override
	public void active(AttackRun run) {
		if (run.first()) {
			run.boss().queueAttack(real);
		}
	}
}
