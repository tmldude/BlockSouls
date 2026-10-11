package io.github.tmldude.blocksouls.bosses.logic;

import java.util.HashMap;
import java.util.Map;

/**
 * Who the boss wants to hit. Threat comes from damage dealt (decaying over time) plus a bonus for being close.
 * The boss only switches away from its current target when someone else beats it by a margin, so it doesn't
 * flip-flop between two players every evaluation.
 *
 * @param <K> player key (a UUID in game)
 */
public final class AggroTable<K> {
	private final Map<K, Double> threat = new HashMap<>();

	public void addDamage(K key, double damage) {
		if (damage > 0) {
			threat.merge(key, damage, Double::sum);
		}
	}

	/** Multiplies all threat by {@code factor} (0..1). */
	public void decay(double factor) {
		threat.replaceAll((key, value) -> value * factor);
		threat.values().removeIf(value -> value < 0.01);
	}

	public double threat(K key) {
		return threat.getOrDefault(key, 0.0);
	}

	public void remove(K key) {
		threat.remove(key);
	}

	public void clear() {
		threat.clear();
	}

	/** Damage threat plus {@code proximityWeight} at distance 0 falling to 0 at {@code proximityRange}. */
	public double score(K key, double distance, double proximityWeight, double proximityRange) {
		double proximity = proximityRange <= 0 ? 0 : Math.max(0, 1 - distance / proximityRange) * proximityWeight;
		return threat(key) + proximity;
	}

	/**
	 * Picks the target among {@code candidates} (key → distance). Keeps {@code current} unless it's gone or
	 * another candidate scores more than {@code (1 + switchMargin)} times its score.
	 *
	 * @return the chosen key, or null if there are no candidates
	 */
	public K choose(K current, Map<K, Double> candidates, double proximityWeight, double proximityRange, double switchMargin) {
		K best = null;
		double bestScore = -1;
		for (Map.Entry<K, Double> entry : candidates.entrySet()) {
			double score = score(entry.getKey(), entry.getValue(), proximityWeight, proximityRange);
			if (score > bestScore) {
				best = entry.getKey();
				bestScore = score;
			}
		}
		if (current != null && candidates.containsKey(current) && best != current) {
			double currentScore = score(current, candidates.get(current), proximityWeight, proximityRange);
			if (bestScore <= currentScore * (1 + switchMargin)) {
				return current;
			}
		}
		return best;
	}
}
