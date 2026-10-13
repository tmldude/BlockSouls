package io.github.tmldude.blocksouls.mobs.logic;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.tmldude.blocksouls.mobs.MobsConfig;

/**
 * Whether an elite gets an affix, and which (pure; the random draws are passed in). Works on affix names
 * ({@code Affix#getSerializedName}) so it stays free of Minecraft classes.
 */
public final class AffixRoll {
	private AffixRoll() {
	}

	/**
	 * The chance an elite on this floor gets an affix.
	 *
	 * @param floor     1-based floor (or act) number; 0 or less = Classic
	 * @param escalated the floor carries the {@code elite_affixes} escalation tag
	 */
	public static double chance(int floor, boolean escalated, boolean affixesInClassic, MobsConfig.AffixConfig config) {
		if (floor <= 0) {
			return affixesInClassic ? config.classicChance : 0;
		}
		if (escalated) {
			return config.escalatedChance;
		}
		return floor < config.minFloor ? 0 : config.chance;
	}

	/** The name a uniform draw {@code r} in [0, 1) lands on, by weight; empty when every weight is 0. */
	public static Optional<String> pick(double r, List<String> names, Map<String, Integer> weights) {
		int total = 0;
		for (String name : names) {
			total += Math.max(0, weights.getOrDefault(name, 0));
		}
		if (total <= 0) {
			return Optional.empty();
		}
		double roll = r * total;
		String last = null;
		for (String name : names) {
			int weight = Math.max(0, weights.getOrDefault(name, 0));
			if (weight > 0) {
				last = name;
				roll -= weight;
				if (roll < 0) {
					return Optional.of(name);
				}
			}
		}
		return Optional.ofNullable(last);
	}

	/** Both draws: {@code chanceDraw} against {@link #chance}, then {@code pickDraw} through {@link #pick}. */
	public static Optional<String> roll(double chanceDraw, double pickDraw, int floor, boolean escalated, boolean affixesInClassic,
			List<String> names, MobsConfig.AffixConfig config) {
		if (chanceDraw >= chance(floor, escalated, affixesInClassic, config)) {
			return Optional.empty();
		}
		return pick(pickDraw, names, config.weights);
	}
}
