package io.github.tmldude.blocksouls.game.run.director;

/**
 * What the next spawns should be (PLAN2 §3b): a flanker pack behind a camping party, brutes at a fortified
 * choke (camping and barely scratched), otherwise the wave's own mix. Pure.
 */
public enum Composition {
	MIXED,
	FLANKERS,
	BRUTES;

	/**
	 * @param camping      the party's centre has barely moved for {@code campingSeconds}
	 * @param damageStress the damage signal (0–1): a camping party taking little damage holds a choke
	 */
	public static Composition choose(boolean camping, double damageStress) {
		if (!camping) {
			return MIXED;
		}
		return damageStress < 0.2 ? BRUTES : FLANKERS;
	}

	/** Share of spawns that follow this composition (the rest stay the wave's mix). */
	public double share(DirectorConfig.Levers levers) {
		return switch (this) {
			case MIXED -> 0;
			case FLANKERS -> StressModel.clamp(levers.flankShare);
			case BRUTES -> StressModel.clamp(levers.bruteShare);
		};
	}
}
