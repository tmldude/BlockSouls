package io.github.tmldude.blocksouls.game.run.director;

/**
 * The pacing budget (PLAN2 §0): no stretch over ~90 s without a decision or a threat. The Director calls
 * {@link #beat} whenever something happens worth a player's attention (a threat near the party, a choice
 * offered or made, a win); {@link #spent} tells it the ceiling was hit. Pure; time in whole seconds.
 */
public final class PacingBudget {
	private long lastBeat;
	private String lastReason = "start";

	public PacingBudget(long now) {
		lastBeat = now;
	}

	/** Something happened: the clock restarts. */
	public void beat(long now, String reason) {
		if (now >= lastBeat) {
			lastBeat = now;
			lastReason = reason;
		}
	}

	public long quietSeconds(long now) {
		return Math.max(0, now - lastBeat);
	}

	public String lastReason() {
		return lastReason;
	}

	/** The ceiling is hit (the caller acts, logs a violation and then beats). */
	public boolean spent(long now, int ceilingSeconds) {
		return quietSeconds(now) >= Math.max(1, ceilingSeconds);
	}

	/** Seconds left before the ceiling. */
	public long left(long now, int ceilingSeconds) {
		return Math.max(0, ceilingSeconds - quietSeconds(now));
	}

	/**
	 * Intermission tightening: how long a wave break may still run so the budget isn't blown. Returns
	 * {@code breakLeft} untouched when it fits, else at most {@code tightSeconds} (never more than it was).
	 */
	public static int tightenBreak(long quietSeconds, int breakLeft, int ceilingSeconds, int tightSeconds) {
		if (quietSeconds + breakLeft < ceilingSeconds) {
			return breakLeft;
		}
		return Math.max(0, Math.min(breakLeft, tightSeconds));
	}

	/** A lull may last no longer than the lull ceiling, whatever stress asks for. */
	public static int clampLull(int requestedSeconds, int lullCeilingSeconds) {
		return Math.max(0, Math.min(requestedSeconds, lullCeilingSeconds));
	}
}
