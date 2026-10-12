package io.github.tmldude.blocksouls.common.run;

/**
 * How bosses and sieges scale with the threat counter, loaded from {@code config/blocksouls/threat.json}.
 * Every curve is {@code 1 + perThreat · threat^exponent}, capped at {@code max} (0 = no cap): threat 0 is
 * always ×1, and an exponent below 1 makes each extra threat point add a little less than the one before.
 * These multipliers stack on top of {@code PartyScaling} (and difficulty), never replace it.
 */
public class ThreatConfig {
	/** Boss max health at engage time (field bosses, portal-charge sieges, the final raid). ~×1.6 at threat 20. */
	public Curve bossHealth = new Curve(0.04, 0.9, 3.0);

	/** Boss damage at engage time. ~×1.4 at threat 20. */
	public Curve bossDamage = new Curve(0.03, 0.9, 2.5);

	/**
	 * Extra wave-size lever for v2 features (Director, portal-charge sieges). Neutral by default: the
	 * classic wave formula already grows with the counter ({@code countPerWave}), so a non-1 default
	 * would double-scale ordinary sieges.
	 */
	public Curve waveSize = new Curve(0, 1, 0);

	public static class Curve {
		public double perThreat;
		public double exponent = 1;
		/** Multiplier cap; 0 or less = uncapped. */
		public double max;

		public Curve() {
		}

		public Curve(double perThreat, double exponent, double max) {
			this.perThreat = perThreat;
			this.exponent = exponent;
			this.max = max;
		}
	}
}
