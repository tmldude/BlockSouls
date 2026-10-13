package io.github.tmldude.blocksouls.bosses.logic;

import java.util.List;
import java.util.random.RandomGenerator;

/** Weighted random attack choice among the attacks that are off cooldown and in range. */
public final class AttackPicker {
	private AttackPicker() {
	}

	/**
	 * @param weight       relative pick weight ({@code <= 0} never picked)
	 * @param cooldownLeft ticks until it can be used again ({@code > 0} = not ready)
	 * @param minRange     closest distance to the target it can be used at
	 * @param maxRange     farthest distance it can be used at
	 */
	public record Option(double weight, int cooldownLeft, double minRange, double maxRange) {
		public boolean usable(double distance) {
			return weight > 0 && cooldownLeft <= 0 && distance >= minRange && distance <= maxRange;
		}
	}

	/**
	 * Picks an index into {@code options}, or -1 when none is usable at this distance.
	 *
	 * @param last          index of the previous attack (-1 for none); its weight is multiplied by
	 *                      {@code repeatPenalty} so the same move rarely comes twice in a row
	 * @param repeatPenalty 0..1
	 */
	public static int pick(List<Option> options, double distance, int last, double repeatPenalty, RandomGenerator random) {
		double total = 0;
		for (int i = 0; i < options.size(); i++) {
			total += effectiveWeight(options, i, distance, last, repeatPenalty);
		}
		if (total <= 0) {
			return -1;
		}
		double roll = random.nextDouble() * total;
		int fallback = -1;
		for (int i = 0; i < options.size(); i++) {
			double weight = effectiveWeight(options, i, distance, last, repeatPenalty);
			if (weight <= 0) {
				continue;
			}
			fallback = i;
			roll -= weight;
			if (roll < 0) {
				return i;
			}
		}
		// Rounding left a sliver; take the last usable one.
		return fallback;
	}

	private static double effectiveWeight(List<Option> options, int i, double distance, int last, double repeatPenalty) {
		Option option = options.get(i);
		if (!option.usable(distance)) {
			return 0;
		}
		return i == last ? option.weight() * Math.clamp(repeatPenalty, 0, 1) : option.weight();
	}

	/** True when at least one option could be used at some distance once cooldowns run out. */
	public static boolean anyInRange(List<Option> options, double distance) {
		for (Option option : options) {
			if (option.weight() > 0 && distance >= option.minRange() && distance <= option.maxRange()) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Utility scoring (PLAN2 §3k Tier 1)

	/**
	 * What an attack is good at, for utility scoring. Each value is roughly -1..1; 0 = indifferent.
	 *
	 * @param frontal  + prefers a target in front (thrusts, breath), - prefers one behind or beside (tail swipes, spins)
	 * @param antiAir  + good against an airborne target (rising pillars, homing orbs), - useless against one (ground waves)
	 * @param punish   + good right after the target rolled (the roll is spent; delayed hits, wide AoEs)
	 * @param pressure + good when the target is low on stamina (chains, multi-hits)
	 */
	public record Traits(double frontal, double antiAir, double punish, double pressure) {
		public static final Traits NONE = new Traits(0, 0, 0, 0);
	}

	/**
	 * The situation the boss is choosing in.
	 *
	 * @param angleDegrees    angle between the boss's facing and the target, 0..180
	 * @param targetStamina   0..1 (1 when unknown)
	 * @param justRolled      the target rolled in the last few ticks
	 * @param airborne        the target is off the ground
	 */
	public record Context(double distance, double angleDegrees, double targetStamina, boolean justRolled, boolean airborne) {
	}

	/** Scoring weights (from {@code bosses.json} {@code picker}). */
	public record Weights(double distance, double angle, double stamina, double rolled, double airborne, double freshnessTicks) {
	}

	/**
	 * An option plus what utility scoring needs.
	 *
	 * @param sinceUsed ticks since this attack last ended ({@code Integer.MAX_VALUE} for never)
	 */
	public record Scored(Option option, Traits traits, int sinceUsed) {
	}

	/**
	 * Utility multiplier for one usable option (1 = neutral), clamped to {@link Fairness#adaptation}: scoring adapts
	 * the mix, it never removes an attack from it.
	 */
	public static double utility(Scored scored, Context context, Weights weights) {
		Option option = scored.option();
		Traits traits = scored.traits();
		double m = 1;
		// Distance: middle of the attack's band scores best.
		double band = Math.max(0.5, option.maxRange() - option.minRange());
		double mid = (option.minRange() + option.maxRange()) / 2;
		double fit = 1 - Math.min(1, Math.abs(context.distance() - mid) / (band / 2));
		m *= 1 + weights.distance() * (fit - 0.5);
		// Angle: cos 1 in front, -1 behind.
		double facing = Math.cos(Math.toRadians(Math.clamp(context.angleDegrees(), 0, 180)));
		m *= 1 + weights.angle() * traits.frontal() * facing;
		m *= 1 + weights.stamina() * traits.pressure() * (1 - Math.clamp(context.targetStamina(), 0, 1));
		if (context.justRolled()) {
			m *= 1 + weights.rolled() * traits.punish();
		}
		if (context.airborne()) {
			m *= 1 + weights.airborne() * traits.antiAir();
		}
		// Freshness: something used a moment ago is less appealing than something not seen in a while.
		if (weights.freshnessTicks() > 0) {
			double fresh = Math.min(1, scored.sinceUsed() / weights.freshnessTicks());
			m *= 0.6 + 0.4 * fresh;
		}
		return Fairness.adaptation(Math.max(0, m));
	}

	/** Like {@link #pick}, with every usable option's weight multiplied by its {@link #utility}. */
	public static int pickScored(List<Scored> options, Context context, Weights weights, int last, double repeatPenalty,
			RandomGenerator random) {
		double[] w = new double[options.size()];
		double total = 0;
		for (int i = 0; i < options.size(); i++) {
			Scored scored = options.get(i);
			if (!scored.option().usable(context.distance())) {
				continue;
			}
			double base = i == last ? scored.option().weight() * Math.clamp(repeatPenalty, 0, 1) : scored.option().weight();
			w[i] = base * utility(scored, context, weights);
			total += w[i];
		}
		if (total <= 0) {
			return -1;
		}
		double roll = random.nextDouble() * total;
		int fallback = -1;
		for (int i = 0; i < w.length; i++) {
			if (w[i] <= 0) {
				continue;
			}
			fallback = i;
			roll -= w[i];
			if (roll < 0) {
				return i;
			}
		}
		return fallback;
	}
}
