package io.github.tmldude.blocksouls.mobs.logic;

import java.util.List;

import io.github.tmldude.blocksouls.mobs.MobsConfig;

/**
 * Utility scoring for a mob's special attacks (pure). Each ready attack scores from the situation: how well the
 * distance suits it, whether the target is in front, whether the target just dodge-rolled or is in the air, and
 * whether this attack was the last one used. The best score over the threshold wins; nothing over it means "keep
 * walking / melee".
 */
public final class AttackPicker {
	private AttackPicker() {
	}

	/**
	 * One special attack.
	 *
	 * @param ranged     hits at range (a thrown or shouted attack): likes airborne targets
	 * @param ground     lands on the ground around it (slams): dislikes airborne targets
	 * @param punish     good against a target recovering from a dodge roll
	 * @param bonus      a mob-specific extra score (the Screamer's nearby allies)
	 */
	public record Attack(double minRange, double maxRange, boolean ranged, boolean ground, boolean punish, double bonus) {
		public Attack withBonus(double extra) {
			return new Attack(minRange, maxRange, ranged, ground, punish, extra);
		}
	}

	/**
	 * @param distance        to the target, blocks
	 * @param angleDegrees    between where the mob faces and the target, 0..180
	 * @param targetJustRolled the target dodge-rolled within the roll window
	 * @param targetAirborne  the target is off the ground
	 */
	public record Situation(double distance, double angleDegrees, boolean targetJustRolled, boolean targetAirborne) {
	}

	/** The attack's score, or negative infinity when it can't be used (on cooldown, out of range). */
	public static double score(Attack attack, Situation situation, boolean ready, boolean usedLast, MobsConfig.AttackPicking weights) {
		if (!ready || situation.distance() < attack.minRange() || situation.distance() > attack.maxRange()) {
			return Double.NEGATIVE_INFINITY;
		}
		double mid = (attack.minRange() + attack.maxRange()) / 2;
		double half = Math.max(0.5, (attack.maxRange() - attack.minRange()) / 2);
		// 1 at the middle of its range, 0.5 at the edges.
		double distance = 1 - 0.5 * Math.min(1, Math.abs(situation.distance() - mid) / half);
		double angle = 1 - Math.clamp(situation.angleDegrees(), 0, 180) / 180;
		double score = weights.distanceWeight * distance + weights.angleWeight * angle - weights.angleWeight;
		if (situation.targetJustRolled() && attack.punish()) {
			score += weights.justRolledBonus;
		}
		if (situation.targetAirborne()) {
			if (attack.ranged()) {
				score += weights.airborneWeight;
			}
			if (attack.ground()) {
				score -= weights.airborneWeight;
			}
		}
		if (usedLast) {
			score -= weights.repeatPenalty;
		}
		return score + attack.bonus();
	}

	/**
	 * The index of the best attack scoring over the threshold, or -1.
	 *
	 * @param ready    per attack, off cooldown
	 * @param lastUsed index of the attack used last (-1 = none); ignored with a single attack
	 */
	public static int pick(List<Attack> attacks, Situation situation, boolean[] ready, int lastUsed, MobsConfig.AttackPicking weights) {
		int best = -1;
		double bestScore = weights.threshold;
		// The repeat penalty is about variety: a mob with one special attack isn't penalised for using it again.
		boolean varied = attacks.size() > 1;
		for (int i = 0; i < attacks.size(); i++) {
			double score = score(attacks.get(i), situation, ready[i], varied && i == lastUsed, weights);
			if (score > bestScore) {
				best = i;
				bestScore = score;
			}
		}
		return best;
	}
}
