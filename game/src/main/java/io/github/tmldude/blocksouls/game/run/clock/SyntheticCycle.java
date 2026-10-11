package io.github.tmldude.blocksouls.game.run.clock;

import io.github.tmldude.blocksouls.game.run.WaveMath;

/**
 * The run's one synthetic day/night cycle for dimensions without a sun (Nether, End, acts 3–4): no fake
 * sun, just a persisted ~17-minute phase the sieges and the client "vibe switch" run off. Pure accumulator
 * logic (mirrors vanilla's clock-rate fractional accumulator) so it can be unit-tested; persistence lives
 * in {@code RunData}.
 */
public final class SyntheticCycle {
	private long totalTicks;
	private float remainder;
	private boolean started;

	/** Day-time ticks so far (same scale as the overworld clock's time; {@code mod 24000} is the time of day). */
	public long cycleTime() {
		return totalTicks;
	}

	public int timeOfDay() {
		return WaveMath.timeOfDay(totalTicks);
	}

	public boolean started() {
		return started;
	}

	/**
	 * Starts the cycle aligned to this day time (the overworld's, so moving between dimensions keeps one
	 * rhythm). Does nothing once started.
	 */
	public void start(long alignToDayTime) {
		if (started) {
			return;
		}
		totalTicks = Math.floorMod(alignToDayTime, (long) WaveMath.DAY_TICKS);
		remainder = 0;
		started = true;
	}

	/** Advances one game tick at this clock rate (day-time ticks per game tick), carrying the fraction. */
	public void advance(float rate) {
		if (!started) {
			return;
		}
		remainder += Math.max(0, rate);
		int whole = (int) remainder;
		totalTicks += whole;
		remainder -= whole;
	}

	/** Moves forward to the next occurrence of {@code timeOfDay} (0 if already there). */
	public void skipTo(int timeOfDay) {
		totalTicks += WaveMath.ticksUntil(totalTicks, timeOfDay);
		remainder = 0;
	}

	public void reset() {
		totalTicks = 0;
		remainder = 0;
		started = false;
	}

	// ---------------------------------------------------------------- persistence (RunData's NBT)

	public long saveTotalTicks() {
		return totalTicks;
	}

	public float saveRemainder() {
		return remainder;
	}

	public void load(long savedTotalTicks, float savedRemainder, boolean savedStarted) {
		totalTicks = Math.max(0, savedTotalTicks);
		remainder = Float.isFinite(savedRemainder) ? Math.max(0, Math.min(savedRemainder, 1)) : 0;
		started = savedStarted;
	}
}
