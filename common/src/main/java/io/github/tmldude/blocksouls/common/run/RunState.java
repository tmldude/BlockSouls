package io.github.tmldude.blocksouls.common.run;

/**
 * Read-only snapshot of the current run (owned by game). Waves are numbered from 1; {@code wave} is the
 * current or last wave, 0 before the first. {@code night} counts nights that had a wave.
 */
public record RunState(RunPhase phase, int wave, int night, boolean endless, Difficulty difficulty) {
	public static final RunState NOT_STARTED = new RunState(RunPhase.NOT_STARTED, 0, 0, false, Difficulty.NORMAL);

	public boolean isActive() {
		return phase != RunPhase.NOT_STARTED && phase != RunPhase.OVER;
	}

	public boolean isWaveActive() {
		return phase == RunPhase.WAVE;
	}
}
