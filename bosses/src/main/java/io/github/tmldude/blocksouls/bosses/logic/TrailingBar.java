package io.github.tmldude.blocksouls.bosses.logic;

/**
 * Boss bar fill with a trailing "damage taken" chunk: on a hit the bar drops at once and the chunk between the old
 * and new value stays lit, then drains after a delay. Further hits restart the delay so combos read as one chunk.
 * Values are fractions of max health (0..1).
 */
public final class TrailingBar {
	private final double delaySeconds;
	private final double drainPerSecond;
	private double value;
	private double trail;
	private double hold;

	/**
	 * @param delaySeconds   how long the chunk stays before draining (0.6 s by default)
	 * @param drainPerSecond how much of the bar the chunk drains per second once it starts
	 */
	public TrailingBar(double start, double delaySeconds, double drainPerSecond) {
		this.delaySeconds = delaySeconds;
		this.drainPerSecond = drainPerSecond;
		this.value = clamp(start);
		this.trail = value;
	}

	/** New health fraction from the server. Healing moves both values up at once. */
	public void set(double fraction) {
		fraction = clamp(fraction);
		if (fraction < value) {
			hold = delaySeconds;
		}
		value = fraction;
		if (trail < value) {
			trail = value;
		}
	}

	public void tick(double seconds) {
		if (trail <= value) {
			return;
		}
		if (hold > 0) {
			hold -= seconds;
			if (hold > 0) {
				return;
			}
			// Spend only the part of this tick after the delay ran out.
			seconds = -hold;
			hold = 0;
		}
		trail = Math.max(value, trail - drainPerSecond * seconds);
	}

	public double value() {
		return value;
	}

	/** Top of the trailing chunk ({@code >= value}). */
	public double trail() {
		return trail;
	}

	private static double clamp(double fraction) {
		return Double.isNaN(fraction) ? 0 : Math.clamp(fraction, 0, 1);
	}
}
