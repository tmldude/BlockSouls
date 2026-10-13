package io.github.tmldude.blocksouls.bosses.logic;

/**
 * Fairness invariants (PLAN2 §3h): constants, not config, and not tunable per boss. Config values that touch them
 * are clamped here, so a server file can make a boss slower to react but never faster than the floor.
 */
public final class Fairness {
	/** A hit worth more than this share of a player's max health is "big". */
	public static final double BIG_HIT_SHARE = 0.3;
	/** Big hits need at least this much windup (0.75 s). Telegraphs never lie: the windup always plays in full. */
	public static final int BIG_HIT_MIN_WINDUP = 15;
	/** Reactive moves (sidestep vs. aim) wait at least this long after the cue they react to... */
	public static final int REACTION_FLOOR_MIN = 6;
	/** ...and at most this long (so the reaction still reads as a reaction). */
	public static final int REACTION_FLOOR_MAX = 8;
	/** Reactive moves never come more often than this. */
	public static final int REACTION_MIN_COOLDOWN = 40;
	/** Utility scoring can never push an attack's pick weight above this multiple of its configured weight. */
	public static final double ADAPTATION_CAP = 3.0;
	/** ...nor below this multiple (no attack the boss has becomes impossible to see). */
	public static final double ADAPTATION_FLOOR = 0.25;
	/** Feints only from this phase index on (0 = first), and the follow-up gets at least the big-hit windup. */
	public static final int FEINT_MIN_PHASE = 1;

	private Fairness() {
	}

	public static int reactionTicks(int configured) {
		return Math.clamp(configured, REACTION_FLOOR_MIN, REACTION_FLOOR_MAX);
	}

	public static int reactionCooldown(int configured) {
		return Math.max(REACTION_MIN_COOLDOWN, configured);
	}

	public static double adaptation(double multiplier) {
		return Math.clamp(multiplier, ADAPTATION_FLOOR, ADAPTATION_CAP);
	}

	/** The readability rule with the locked numbers. */
	public static boolean readable(double damage, int windupTicks, double playerMaxHealth) {
		return BossScaling.readable(damage, windupTicks, playerMaxHealth, BIG_HIT_SHARE, BIG_HIT_MIN_WINDUP);
	}
}
