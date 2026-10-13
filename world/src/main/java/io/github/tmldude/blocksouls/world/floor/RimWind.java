package io.github.tmldude.blocksouls.world.floor;

/**
 * Skyrift's rim wind (pure, unit-tested): how hard the open edge pushes a player outward. Zero inland, rising linearly
 * across the last {@code band} blocks to the edge, full past it; zero above {@code height} over the ground (the shards
 * are calm) and below the ground (already falling).
 */
public final class RimWind {
	private RimWind() {
	}

	/**
	 * @param distance    horizontal distance from the floor's centre
	 * @param radius      the island's radius
	 * @param aboveGround the player's height over the island's ground level
	 */
	public static double strength(FloorsConfig config, double templateWind, double distance, int radius, double aboveGround, boolean sneaking) {
		if (templateWind <= 0 || config.rimWindStrength <= 0 || aboveGround > config.rimWindHeight || aboveGround < -2) {
			return 0;
		}
		int band = Math.max(1, config.rimWindBand);
		double into = distance - (radius - band);
		if (into <= 0) {
			return 0;
		}
		double share = Math.min(1.0, into / band);
		return config.rimWindStrength * templateWind * share * (sneaking ? Math.max(0, config.rimWindSneakFactor) : 1.0);
	}
}
