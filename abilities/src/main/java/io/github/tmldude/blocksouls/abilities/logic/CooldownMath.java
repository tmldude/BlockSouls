package io.github.tmldude.blocksouls.abilities.logic;

/** Pure cooldown timing, in ticks of the level's game time. */
public final class CooldownMath {
	private CooldownMath() {
	}

	/** Cooldown after a reduction fraction (0.2 = 20% shorter); never below one tick. */
	public static int withReduction(int baseTicks, double reduction) {
		double kept = 1.0 - Math.clamp(reduction, 0.0, 0.95);
		return Math.max(1, (int) Math.round(Math.max(0, baseTicks) * kept));
	}

	public static long remaining(long readyAt, long now) {
		return Math.max(0, readyAt - now);
	}

	public static boolean ready(long readyAt, long now) {
		return now >= readyAt;
	}

	/** 0 right after the cast → 1 when ready; a zero-length cooldown is always 1. */
	public static float progress(long startedAt, long readyAt, long now) {
		long total = readyAt - startedAt;
		if (total <= 0) {
			return 1.0F;
		}
		return Math.clamp((now - startedAt) / (float) total, 0.0F, 1.0F);
	}

	/** Seconds left, rounded up, for a HUD label. */
	public static int secondsLeft(long readyAt, long now) {
		return (int) Math.ceil(remaining(readyAt, now) / 20.0);
	}
}
