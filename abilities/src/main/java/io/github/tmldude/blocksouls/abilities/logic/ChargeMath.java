package io.github.tmldude.blocksouls.abilities.logic;

/** Pure ultimate-charge math (0..1 of the bar). Party and synergy multipliers are passed in. */
public final class ChargeMath {
	private ChargeMath() {
	}

	/** {@code 1 + min(stacks · perStack, cap)}: how much more charge a build's matching tag stacks give. */
	public static double stackMultiplier(int stacks, double perStack, double cap) {
		return 1.0 + Math.min(Math.max(0, stacks) * Math.max(0, perStack), Math.max(0, cap));
	}

	/**
	 * Charge for one kill.
	 *
	 * @param partyMult {@code PartyScaling.ultimateCharge(players)}
	 * @param stackMult {@link #stackMultiplier} for the build's {@code on_kill} stacks
	 */
	public static double perKill(double base, boolean elite, double eliteMult, boolean boss, double bossMult, double partyMult, double stackMult) {
		double kind = boss ? bossMult : elite ? eliteMult : 1.0;
		return Math.max(0, base) * kind * partyMult * stackMult;
	}

	/** Charge for one precision hit. */
	public static double perPrecision(double base, double partyMult, double stackMult) {
		return Math.max(0, base) * partyMult * stackMult;
	}

	/** The new charge after adding {@code gained}, clamped to the bar. */
	public static double add(double charge, double gained) {
		return Math.clamp(charge + gained, 0.0, 1.0);
	}

	/** Whether adding {@code gained} fills the bar for the first time. */
	public static boolean becomesReady(double charge, double gained) {
		return charge < 1.0 && add(charge, gained) >= 1.0;
	}
}
