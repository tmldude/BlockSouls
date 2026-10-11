package io.github.tmldude.blocksouls.mobs.logic;

/** How long a wave mob takes to break one block. Pure math so it can be unit-tested. */
public final class BreakTime {
	private BreakTime() {
	}

	/**
	 * Seconds to break one block: {@code slowest} at wave 1, falling linearly to {@code fastest} at
	 * {@code fastestWave} and staying there, divided by the mob's break-speed multiplier.
	 */
	public static double seconds(int wave, double slowest, double fastest, int fastestWave, double speedMultiplier) {
		double t = fastestWave <= 1 ? 1 : Math.clamp((wave - 1) / (double) (fastestWave - 1), 0.0, 1.0);
		double base = slowest + (fastest - slowest) * t;
		return base / Math.max(0.01, speedMultiplier);
	}

	public static int ticks(int wave, double slowest, double fastest, int fastestWave, double speedMultiplier) {
		return Math.max(1, (int) Math.round(seconds(wave, slowest, fastest, fastestWave, speedMultiplier) * 20));
	}

	/** Crack stage 0..9 for {@code destroyBlockProgress}. */
	public static int crackStage(int progressTicks, int totalTicks) {
		return Math.clamp((int) (10.0 * progressTicks / Math.max(1, totalTicks)), 0, 9);
	}
}
