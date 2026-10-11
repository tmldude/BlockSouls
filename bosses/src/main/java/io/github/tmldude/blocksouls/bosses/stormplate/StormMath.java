package io.github.tmldude.blocksouls.bosses.stormplate;

/** Pure timing math for the Stormplate Knight's moves (unit-tested; no Minecraft classes). */
public final class StormMath {
	private StormMath() {
	}

	/**
	 * How far along its line the thrown spear is after {@code t} active ticks, 0 = in the hand, 1 = at the far end. It
	 * flies out over {@code flight} ticks (fast, then slowing), hangs there until {@code back}, then returns the same way.
	 */
	public static double spearFraction(int t, int flight, int back) {
		flight = Math.max(1, flight);
		if (t <= flight) {
			return ease(Math.max(0, t) / (double) flight);
		}
		if (t <= back) {
			return 1;
		}
		return 1 - ease(Math.min(1, (t - back) / (double) flight));
	}

	private static double ease(double f) {
		return 1 - (1 - f) * (1 - f);
	}

	/** Whether a puddle that's {@code age} ticks old pulses this tick (the last tick of each beat). */
	public static boolean puddlePulses(int age, int pulseTicks) {
		return age % pulseTicks == pulseTicks - 1;
	}

	/**
	 * How far the warning ring has filled in before the next pulse: 0 outside the warning window, rising to 1 on the
	 * pulse tick.
	 */
	public static float puddleWarning(int age, int pulseTicks, int warnTicks) {
		int beat = age % pulseTicks;
		int warnFrom = pulseTicks - Math.max(1, Math.min(warnTicks, pulseTicks));
		if (beat < warnFrom) {
			return 0;
		}
		return (beat - warnFrom + 1) / (float) (pulseTicks - warnFrom);
	}
}
