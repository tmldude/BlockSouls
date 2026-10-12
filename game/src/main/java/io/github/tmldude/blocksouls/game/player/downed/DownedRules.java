package io.github.tmldude.blocksouls.game.player.downed;

/** Pure rules for downed gameplay: what a downed player's shots are worth. */
public final class DownedRules {
	private DownedRules() {
	}

	/** Damage dealt by a downed player, scaled; standing players deal full damage. */
	public static float damage(float amount, boolean attackerDowned, double multiplier) {
		return attackerDowned ? (float) (amount * Math.max(0, multiplier)) : amount;
	}
}
