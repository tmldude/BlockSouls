package io.github.tmldude.blocksouls.common.run;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * The threat counter, scaled. The counter itself is the v1 wave counter promoted ({@link RunState#threat()},
 * same persisted value): it climbs every night of the run, in every act, and never pauses. Everything ahead
 * of the party — field bosses, portal-charge sieges, the final raid — reads its multiplier here <em>at
 * engage time</em>, so staying longer means a stronger build against harder bosses and rushing the reverse.
 *
 * <p>Pure math, tuned in {@code threat.json}. Multipliers stack on top of {@link PartyScaling} and
 * difficulty, never replace them: {@code health × Threat.scale(BOSS_HEALTH, t) × PartyScaling.bossHealth(n)}.
 */
public final class Threat {
	private static ThreatConfig config = new ThreatConfig();

	private Threat() {
	}

	public enum Kind {
		BOSS_HEALTH,
		BOSS_DAMAGE,
		WAVE_SIZE
	}

	public static ThreatConfig config() {
		return config;
	}

	/** Called once by common's initializer. */
	public static void load() {
		config = Config.load("threat", ThreatConfig.class, ThreatConfig::new);
	}

	/** For tests: swap the config (pass {@code new ThreatConfig()} to restore the defaults). */
	public static void setConfig(ThreatConfig newConfig) {
		config = newConfig;
	}

	/** The multiplier for {@code kind} at this threat (≥ 1; threat ≤ 0 is always 1). */
	public static double scale(Kind kind, int threat) {
		return curve(switch (kind) {
			case BOSS_HEALTH -> config.bossHealth;
			case BOSS_DAMAGE -> config.bossDamage;
			case WAVE_SIZE -> config.waveSize;
		}, threat);
	}

	public static double bossHealth(int threat) {
		return scale(Kind.BOSS_HEALTH, threat);
	}

	public static double bossDamage(int threat) {
		return scale(Kind.BOSS_DAMAGE, threat);
	}

	public static double waveSize(int threat) {
		return scale(Kind.WAVE_SIZE, threat);
	}

	/** {@code 1 + perThreat · threat^exponent}, capped at {@code max} when max > 0. */
	public static double curve(ThreatConfig.Curve curve, int threat) {
		if (threat <= 0) {
			return 1.0;
		}
		double value = 1.0 + curve.perThreat * Math.pow(threat, curve.exponent);
		return curve.max > 0 ? Math.min(value, curve.max) : value;
	}
}
