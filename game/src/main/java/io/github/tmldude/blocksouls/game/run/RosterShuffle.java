package io.github.tmldude.blocksouls.game.run;

import java.util.Random;

/** Per-run shuffle of the wave roster: each mob's first wave moves by a seeded offset (pure). */
public final class RosterShuffle {
	private RosterShuffle() {
	}

	/** Offset in [−spread, spread] for one mob id, stable for a given run seed. */
	public static int offset(String id, long runSeed, int spread) {
		if (spread <= 0) {
			return 0;
		}
		Random random = new Random(mix(runSeed, id.hashCode()));
		return random.nextInt(2 * spread + 1) - spread;
	}

	/** Mixes a run seed with a salt (SplitMix64 finalizer) so nearby seeds give unrelated rolls. */
	static long mix(long seed, long salt) {
		long z = seed + 0x9E3779B97F4A7C15L * (salt + 1);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** A mob's first wave this run (never below 1). */
	public static int shiftedMinWave(int minWave, int offset) {
		return Math.max(1, minWave + offset);
	}
}
