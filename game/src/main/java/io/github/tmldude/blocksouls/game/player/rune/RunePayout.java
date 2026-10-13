package io.github.tmldude.blocksouls.game.player.rune;

/**
 * Rune souls paid out at run end (the orchestrator's rule, DECISIONS v2): a <em>performance payout</em> — sieges
 * survived (floors in the Floor Loop, nights in Classic) scaled by a threat factor — plus a minority share of the
 * souls left unspent, so playing well is the meta income and hoarding never beats spending at the shop. Pure and
 * per player; not party-dependent, so it doesn't go through {@code PartyScaling}.
 */
public final class RunePayout {
	private RunePayout() {
	}

	/** The performance part: {@code soulsPerSiege × sieges × (1 + threatBonusPerThreat × threat)}, rounded. */
	public static int performance(int siegesSurvived, int threat, RunesConfig config) {
		double base = config.soulsPerSiege * Math.max(0, siegesSurvived);
		double factor = 1.0 + Math.max(0, config.threatBonusPerThreat) * Math.max(0, threat);
		return (int) Math.round(base * factor);
	}

	/** The share of souls left unspent that carries over. */
	public static int carried(int unspentSouls, RunesConfig config) {
		return (int) Math.floor(Math.max(0, unspentSouls) * Math.clamp(config.unspentShare, 0.0, 1.0));
	}

	/** The whole payout: performance + carried, never below {@code minimumPayout} once at least one siege was survived. */
	public static int payout(int siegesSurvived, int threat, int unspentSouls, RunesConfig config) {
		int total = performance(siegesSurvived, threat, config) + carried(unspentSouls, config);
		return siegesSurvived > 0 ? Math.max(config.minimumPayout, total) : total;
	}
}
