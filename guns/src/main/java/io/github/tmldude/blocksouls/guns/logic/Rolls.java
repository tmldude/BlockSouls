package io.github.tmldude.blocksouls.guns.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Rarity and perk rolls. */
public final class Rolls {
	private Rolls() {
	}

	/** Picks a rarity from {@code [common, rare, legendary]} weights (any non-negative scale). */
	public static GunRarity rarity(double[] weights, RandomGenerator random) {
		double total = 0;
		for (double weight : weights) {
			total += Math.max(0, weight);
		}
		if (total <= 0) {
			return GunRarity.COMMON;
		}

		double pick = random.nextDouble() * total;
		GunRarity[] rarities = GunRarity.values();
		for (int i = 0; i < rarities.length && i < weights.length; i++) {
			pick -= Math.max(0, weights[i]);
			if (pick < 0) {
				return rarities[i];
			}
		}
		return rarities[Math.min(weights.length, rarities.length) - 1];
	}

	/** {@code count} distinct perks from the pool (fewer if the pool is smaller), in roll order. */
	public static <T> List<T> perks(List<T> pool, int count, RandomGenerator random) {
		List<T> remaining = new ArrayList<>(pool);
		List<T> picked = new ArrayList<>();
		while (picked.size() < count && !remaining.isEmpty()) {
			picked.add(remaining.remove(random.nextInt(remaining.size())));
		}
		return picked;
	}
}
