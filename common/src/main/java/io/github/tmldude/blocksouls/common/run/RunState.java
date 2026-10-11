package io.github.tmldude.blocksouls.common.run;

/**
 * Read-only snapshot of the current run (owned by game). Waves are numbered from 1; {@code wave} is the
 * current or last wave, 0 before the first. {@code night} counts nights that had a wave.
 */
public record RunState(RunPhase phase, int wave, int night, boolean endless, Difficulty difficulty) {
	public static final RunState NOT_STARTED = new RunState(RunPhase.NOT_STARTED, 0, 0, false, Difficulty.NORMAL);

	/**
	 * The threat counter (v2 name for the wave counter — same persisted value): it climbs every night of
	 * the run and never pauses, and everything ahead of the party scales with it at engage time through
	 * {@link Threat#scale}. {@link #wave()} remains as the v1 accessor.
	 */
	public int threat() {
		return wave;
	}

	public boolean isActive() {
		return phase != RunPhase.NOT_STARTED && phase != RunPhase.OVER;
	}

	public boolean isWaveActive() {
		return phase == RunPhase.WAVE;
	}
}
