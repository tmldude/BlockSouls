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

	public static int raidPlates(int players) {
		int perPlate = Math.max(1, config.playersPerRaidPlate);
		int plates = (partySize(players) + perPlate - 1) / perPlate;
		return Math.max(1, Math.min(plates, Math.max(1, config.raidPlatesMax)));
	}
}
