package io.github.tmldude.blocksouls.common.run;

public enum RunPhase {
	/** No run has started on this world yet. */
	NOT_STARTED,
	DAY,
	/** Night, a wave is being fought. */
	WAVE,
	/** Night, short break between waves. */
	WAVE_BREAK,
	/** Night, but no more waves start (not enough night left, or nobody in the overworld). */
	NIGHT_QUIET,
	/** Everyone went down at once; the run is over until an admin starts a new one. */
	OVER
}
