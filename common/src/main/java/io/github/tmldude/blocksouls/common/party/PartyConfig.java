package io.github.tmldude.blocksouls.common.party;

/**
 * How the game scales with the number of players, loaded from {@code config/blocksouls/party.json}. Every curve is
 * {@code 1 + perExtraPlayer · (players − 1)^exponent}: solo is always 1, and an exponent below 1 makes each extra
 * player add a little less than the one before.
 */
public class PartyConfig {
	/** Party sizes above this count as this many (the game is tuned and tested for 1–8). */
	public int maxParty = 8;

	/** Mobs per wave. 2 players ×1.55, 4 ×2.5, 8 ×4.2. */
	public Curve waveSize = new Curve(0.55, 0.9);

	/** Wave mobs alive at once: {@code aliveCapBase + aliveCapPerPlayer · players}, at most {@code aliveCapMax}. */
	public int aliveCapBase = 12;
	public int aliveCapPerPlayer = 5;
	public int aliveCapMax = 60;

	/** Elites allowed per wave. 4 players ×1.7, 8 ×3. */
	public Curve elites = new Curve(0.35, 0.9);

	/** Boss health. 2 players ×1.5, 4 ×2.2, 8 ×3.9. */
	public Curve bossHealth = new Curve(0.5, 0.9);

	/** Field bosses' daily spawn chance. 8 players ×1.7. */
	public Curve fieldBossChance = new Curve(0.1, 1.0);

	/** Final raid: pressure plates that must be held, {@code ceil(players / playersPerRaidPlate)}, 1 to raidPlatesMax. */
	public int playersPerRaidPlate = 2;
	public int raidPlatesMax = 4;
	/** Final raid: crystals in each Crystal Order (× its solo count). 2 players ×1.3, 4 ×1.8, 8 ×2.7. */
	public Curve raidCrystals = new Curve(0.3, 0.9);

	/** Loot shared by the whole party (wave-clear reward bundles, pity guns). 2 players ×1.5, 4 ×2.2, 8 ×3.4. */
	public Curve rewards = new Curve(0.5, 0.8);
	/** v2 floors: island radius (× the template's solo radius). 4 players ×1.2, 8 ×1.4 (area ×2). */
	public Curve floorSize = new Curve(0.07, 0.9);
	/** v2 floors: resource nodes per floor (× the template's solo count). 4 players ×1.7, 8 ×2.3. */
	public Curve floorNodes = new Curve(0.25, 0.85);
	/** v2 floors: crates (structure / cave / secret chests) per floor (× the template's solo count). 8 players ×1.9. */
	public Curve floorCrates = new Curve(0.15, 0.9);

	public static class Curve {
		public double perExtraPlayer;
		public double exponent;

		public Curve() {
		}

		public Curve(double perExtraPlayer, double exponent) {
			this.perExtraPlayer = perExtraPlayer;
			this.exponent = exponent;
		}
	}
}
