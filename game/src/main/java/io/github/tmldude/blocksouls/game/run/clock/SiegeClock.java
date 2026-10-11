package io.github.tmldude.blocksouls.game.run.clock;

import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.WaveMath;

/**
 * What time of day it is for the siege scheduler: one interface over "when is dusk, when is dawn", so wave
 * chaining, dawn rules and relic drops are one code path in every dimension. Two implementations: the
 * {@linkplain SunClock sun-bound} overworld clock (exactly the v1 behavior) and the
 * {@linkplain SyntheticCycle synthetic} cycle for dimensions without a sun.
 */
public interface SiegeClock {
	/** Day-time ticks (the same scale as {@code getOverworldClockTime}; {@code mod 24000} is the time of day). */
	long cycleTime();

	/** Day-time ticks per game tick this clock runs at right now. */
	float rate();

	/** Moves forward to the next occurrence of {@code timeOfDay} (debug/tests). */
	void skipTo(int timeOfDay);

	/** True for the synthetic cycle (no sun to render; the client gets the phase synced instead). */
	boolean synthetic();

	default int timeOfDay() {
		return WaveMath.timeOfDay(cycleTime());
	}

	default boolean isNight(RunConfig config) {
		return WaveMath.isNight(cycleTime(), config);
	}

	/** Real seconds until the next occurrence of {@code timeOfDay} at the current rate. */
	default double secondsUntil(int timeOfDay) {
		return WaveMath.secondsUntil(cycleTime(), timeOfDay, rate());
	}
}
