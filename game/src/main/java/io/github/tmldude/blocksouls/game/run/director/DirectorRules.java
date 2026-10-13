package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jspecify.annotations.Nullable;

/** The Director's pure rules for the day loop and economy (unit-tested without Minecraft). */
final class DirectorRules {
	private DirectorRules() {
	}

	/**
	 * The board for {@code act}: up to {@code slots} valid offers for the act, picked by weight without
	 * replacement from a random seeded by the run and the act (the same board all floor long).
	 */
	static List<DirectorConfig.Offer> stock(List<DirectorConfig.Offer> pool, int act, long seed, int slots) {
		List<DirectorConfig.Offer> candidates = new ArrayList<>();
		for (DirectorConfig.Offer offer : pool) {
			if (offer.valid() && offer.weight > 0 && act >= offer.minAct && act <= offer.maxAct) {
				candidates.add(offer);
			}
		}
		Random random = new Random(seed * 31 + act);
		int size = Math.min(Math.min(slots, 9), candidates.size());
		List<DirectorConfig.Offer> board = new ArrayList<>();
		while (board.size() < size) {
			int total = candidates.stream().mapToInt(o -> o.weight).sum();
			int roll = random.nextInt(total);
			for (DirectorConfig.Offer offer : candidates) {
				roll -= offer.weight;
				if (roll < 0) {
					board.add(offer);
					candidates.remove(offer);
					break;
				}
			}
		}
		return board;
	}

	/** Weighted pick among events unlocked at {@code act}; {@code roll} in [0, 1). Null when none qualify. */
	static @Nullable String pickEvent(List<DirectorConfig.EventSpec> pool, int act, double roll) {
		int total = 0;
		for (DirectorConfig.EventSpec spec : pool) {
			if (spec.id != null && spec.weight > 0 && act >= spec.minAct) {
				total += spec.weight;
			}
		}
		if (total <= 0) {
			return null;
		}
		double target = roll * total;
		for (DirectorConfig.EventSpec spec : pool) {
			if (spec.id != null && spec.weight > 0 && act >= spec.minAct) {
				target -= spec.weight;
				if (target < 0) {
					return spec.id;
				}
			}
		}
		return null;
	}

	/** The circle's radius {@code elapsed} seconds in: shrinks linearly from start to end over the challenge. */
	static double pylonRadius(long elapsed, DirectorConfig.Pylon config) {
		double t = Math.min(1, Math.max(0, elapsed / (double) Math.max(1, config.seconds)));
		return config.startRadius + (config.endRadius - config.startRadius) * t;
	}
}
