package io.github.tmldude.blocksouls.game.player.boon;

import java.util.List;
import java.util.random.RandomGenerator;

import io.github.tmldude.blocksouls.game.player.PlayersConfig.TierOddsBracket;

/** Picks a relic's tier from the wave number. */
public final class TierOdds {
	private TierOdds() {
	}

	/** The bracket with the highest {@code fromWave} ≤ {@code wave}; the lowest bracket if none qualifies. */
	public static TierOddsBracket bracket(List<TierOddsBracket> brackets, int wave) {
		TierOddsBracket best = null;
		TierOddsBracket lowest = null;
		for (TierOddsBracket bracket : brackets) {
			if (lowest == null || bracket.fromWave < lowest.fromWave) {
				lowest = bracket;
			}
			if (bracket.fromWave <= wave && (best == null || bracket.fromWave > best.fromWave)) {
				best = bracket;
			}
		}
		return best != null ? best : lowest;
	}

	/** Weights {common, rare, legendary} for the wave (negative weights count as 0). */
	public static double[] weights(List<TierOddsBracket> brackets, int wave) {
		TierOddsBracket bracket = bracket(brackets, wave);
		if (bracket == null) {
			return new double[] {1, 0, 0};
		}
		return new double[] {Math.max(0, bracket.common), Math.max(0, bracket.rare), Math.max(0, bracket.legendary)};
	}

	public static BoonTier roll(List<TierOddsBracket> brackets, int wave, RandomGenerator random) {
		double[] weights = weights(brackets, wave);
		double total = weights[0] + weights[1] + weights[2];
		if (total <= 0) {
			return BoonTier.COMMON;
		}
		double pick = random.nextDouble() * total;
		for (int i = 0; i < weights.length; i++) {
			pick -= weights[i];
			if (pick < 0) {
				return BoonTier.byOrdinal(i);
			}
		}
		return BoonTier.LEGENDARY;
	}
}
