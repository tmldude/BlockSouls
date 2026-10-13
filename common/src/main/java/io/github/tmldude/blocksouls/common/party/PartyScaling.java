package io.github.tmldude.blocksouls.common.party;

import io.github.tmldude.blocksouls.common.Config;

/**
 * The one place anything scales with player count (BlockSouls is tuned for parties of 1–8). Modules pass in the
 * number of players that matter to them (players in the overworld for waves, players in the fight for bosses) and
 * use these numbers instead of their own per-player formulas. Pure math; tuned in {@code party.json}.
 */
public final class PartyScaling {
	private static PartyConfig config = new PartyConfig();

	private PartyScaling() {
	}

	public static PartyConfig config() {
		return config;
	}

	/** Called once by common's initializer. */
	public static void load() {
		config = Config.load("party", PartyConfig.class, PartyConfig::new);
	}

	/** For tests: swap the config (pass {@code new PartyConfig()} to restore the defaults). */
	public static void setConfig(PartyConfig newConfig) {
		config = newConfig;
	}

	/** Players counted for scaling: at least 1, at most {@code maxParty}. */
	public static int partySize(int players) {
		return Math.max(1, Math.min(players, Math.max(1, config.maxParty)));
	}

	/** {@code 1 + perExtraPlayer · (n − 1)^exponent} for the clamped party size. */
	public static double curve(int players, PartyConfig.Curve curve) {
		int extra = partySize(players) - 1;
		return extra == 0 ? 1.0 : 1.0 + curve.perExtraPlayer * Math.pow(extra, curve.exponent);
	}

	public static double waveSize(int players) {
		return curve(players, config.waveSize);
	}

	public static int aliveCap(int players) {
		return Math.min(config.aliveCapMax, config.aliveCapBase + config.aliveCapPerPlayer * partySize(players));
	}

	public static double elites(int players) {
		return curve(players, config.elites);
	}

	public static double bossHealth(int players) {
		return curve(players, config.bossHealth);
	}

	public static double fieldBossChance(int players) {
		return curve(players, config.fieldBossChance);
	}

	public static double raidCrystals(int players) {
		return curve(players, config.raidCrystals);
	}

	/** Party-shared loot (reward bundles, pity guns). */
	public static double rewards(int players) {
		return curve(players, config.rewards);
	}

	/** Abilities: multiplier on ultimate charge per kill / precision hit. */
	public static double ultimateCharge(int players) {
		return curve(players, config.ultimateCharge);
	}

	/** Abilities: multiplier on healing dart and party overshield amounts. */
	public static double abilityHeal(int players) {
		return curve(players, config.abilityHeal);
	}

	/** v2 floors: island radius multiplier. */
	public static double floorSize(int players) {
		return curve(players, config.floorSize);
	}

	/** v2 floors: resource node count multiplier. */
	public static double floorNodes(int players) {
		return curve(players, config.floorNodes);
	}

	/** v2 floors: crate count multiplier. */
	public static double floorCrates(int players) {
		return curve(players, config.floorCrates);
	}

	/** v2 Director: multiplier on mobs per spawn burst. */
	public static double directorBurst(int players) {
		return curve(players, config.directorBurst);
	}

	/** v2 Director: multiplier on the damage pool that reads as full stress. */
	public static double directorStressPool(int players) {
		return curve(players, config.directorStressPool);
	}

	/** v2 Director: share of a burst that rolls elite (solo is "fewer, scarier"), 0 to 1. */
	public static double directorEliteShare(int players) {
		return Math.max(0, Math.min(1, config.directorSoloEliteShare / curve(players, config.directorEliteFade)));
	}

	// --- floor 6 (Stage 6C, v2-floor6-arena) ---
	/** Final arena: multiplier on the damage rush's window, at least 0.25. */
	public static double rushWindow(int players) {
		return Math.max(0.25, curve(players, config.rushWindow));
	}

	/** Final arena: multiplier on the adds the boss summons in phase 2. */
	public static double arenaAdds(int players) {
		return curve(players, config.arenaAdds);
	}

	/** Final arena: multiplier on the arena's hazard strikes per volley. */
	public static double arenaHazards(int players) {
		return curve(players, config.arenaHazards);
	}
	// --- end floor 6 ---

	public static int raidPlates(int players) {
		int perPlate = Math.max(1, config.playersPerRaidPlate);
		int plates = (partySize(players) + perPlate - 1) / perPlate;
		return Math.max(1, Math.min(plates, Math.max(1, config.raidPlatesMax)));
	}
}
