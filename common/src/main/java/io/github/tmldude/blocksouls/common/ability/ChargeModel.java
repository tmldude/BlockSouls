package io.github.tmldude.blocksouls.common.ability;

/** How an ability becomes ready again after a cast. */
public enum ChargeModel {
	/** Ready again {@code cooldownTicks} after the cast. */
	COOLDOWN,
	/** Ready when the player's ultimate charge reaches 1.0 (filled by kills and precision hits); a cast spends it. */
	KILL_CHARGE
}
