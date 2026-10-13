package io.github.tmldude.blocksouls.game.run.director;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * Per-party stress (PLAN2 §3b): recent damage taken, downs, ammo, DPS trend and boons vs threat, each read
 * as 0 (fine) to 1 (battered) and blended by config weights. Pure; the party-size inputs go through
 * {@code PartyScaling}, so the same hits stress a solo player more than a party of eight.
 */
public final class StressModel {
	private StressModel() {
	}

	/**
	 * What the Director measured over the stress window.
	 *
	 * @param players     party size (counted players)
	 * @param damageTaken health the party lost in the window
	 * @param downs       downs in the window
	 * @param ammo        ammo items the party carries now
	 * @param dpsRecent   damage dealt per second over the newer half of the window
	 * @param dpsEarlier  damage dealt per second over the older half
	 * @param boons       boons the party owns (summed levels)
	 * @param threat      the run's threat (wave counter)
	 */
	public record Inputs(int players, double damageTaken, int downs, int ammo, double dpsRecent, double dpsEarlier, int boons, int threat) {
	}

	/** Each signal 0–1 and the weighted blend. */
	public record Reading(double damage, double downs, double ammo, double dps, double power, double stress) {
	}

	public static Reading score(Inputs in, DirectorConfig.Stress config) {
		int players = PartyScaling.partySize(in.players());
		double damage = clamp(in.damageTaken() / Math.max(1e-6, config.damagePool * PartyScaling.directorStressPool(players)));
		double downs = clamp(in.downs() / Math.max(1e-6, config.downsPerPlayerForFull * players));
		double ammo = clamp(1 - in.ammo() / Math.max(1e-6, (double) config.ammoComfortPerPlayer * players));
		double dps = in.dpsEarlier() <= 0 ? 0 : clamp((in.dpsEarlier() - in.dpsRecent()) / in.dpsEarlier());
		double expected = config.boonsPerThreat * in.threat() * players;
		double power = expected <= 0 ? 0 : clamp((expected - in.boons()) / expected);
		double weights = Math.max(0, config.weightDamage) + Math.max(0, config.weightDowns) + Math.max(0, config.weightAmmo)
				+ Math.max(0, config.weightDps) + Math.max(0, config.weightPower);
		double blend = weights <= 0 ? 0
				: (Math.max(0, config.weightDamage) * damage + Math.max(0, config.weightDowns) * downs + Math.max(0, config.weightAmmo) * ammo
						+ Math.max(0, config.weightDps) * dps + Math.max(0, config.weightPower) * power) / weights;
		// A down is never "fine": a fresh down floors the reading at the downs signal itself.
		return new Reading(damage, downs, ammo, dps, power, clamp(Math.max(blend, downs >= 1 ? downs * 0.8 : 0)));
	}

	/** Moves the smoothed stress toward the raw reading ({@code smoothing} per update, 0–1). */
	public static double smooth(double previous, double raw, double smoothing) {
		double alpha = clamp(smoothing);
		return clamp(previous + (raw - previous) * alpha);
	}

	static double clamp(double value) {
		return Double.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
	}
}
