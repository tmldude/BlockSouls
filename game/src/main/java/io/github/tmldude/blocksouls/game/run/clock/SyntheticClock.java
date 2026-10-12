package io.github.tmldude.blocksouls.game.run.clock;

import java.util.function.Supplier;

/**
 * {@link SiegeClock} over the run's persisted {@link SyntheticCycle}. The run system advances the cycle
 * (and persists it); this adapts it for the scheduler.
 */
public final class SyntheticClock implements SiegeClock {
	private final SyntheticCycle cycle;
	private final Supplier<Float> rate;
	private final Runnable markDirty;

	public SyntheticClock(SyntheticCycle cycle, Supplier<Float> rate, Runnable markDirty) {
		this.cycle = cycle;
		this.rate = rate;
		this.markDirty = markDirty;
	}

	@Override
	public long cycleTime() {
		return cycle.cycleTime();
	}

	@Override
	public float rate() {
		return rate.get();
	}

	@Override
	public void skipTo(int timeOfDay) {
		cycle.skipTo(timeOfDay);
		markDirty.run();
	}

	@Override
	public boolean synthetic() {
		return true;
	}
}
