package io.github.tmldude.blocksouls.bosses.logic;

/**
 * Bosses ignore the run's wave scaling except for these: health grows with the players in the fight and the
 * difficulty's mob health; damage follows the difficulty's mob damage.
 */
public final class BossScaling {
	private BossScaling() {
	}

	/** {@code (1 + perExtraPlayer·(players − 1)) × difficultyHealth}; fewer than one player counts as one. */
	public static double healthMultiplier(int players, double perExtraPlayer, double difficultyHealth) {
		return (1 + perExtraPlayer * (Math.max(1, players) - 1)) * Math.max(0, difficultyHealth);
	}

	public static double damage(double base, double difficultyDamage, double phaseMultiplier) {
		return Math.max(0, base * difficultyDamage * phaseMultiplier);
	}

	/**
	 * Readable-telegraph rule: anything that can deal more than {@code shareOfHealth} of a player's health
	 * needs at least {@code minWindupTicks} of windup.
	 */
	public static boolean readable(double damage, int windupTicks, double playerMaxHealth, double shareOfHealth, int minWindupTicks) {
		return damage <= playerMaxHealth * shareOfHealth || windupTicks >= minWindupTicks;
	}
}
