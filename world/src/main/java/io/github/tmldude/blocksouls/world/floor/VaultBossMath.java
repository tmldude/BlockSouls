package io.github.tmldude.blocksouls.world.floor;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The Vault's (floor 4) mini-boss numbers, pure so they are unit tested. */
final class VaultBossMath {
	private VaultBossMath() {
	}

	/** A Vault mini-boss's health: its base times {@code PartyScaling.bossHealth} for the party (clamped to 1–8). */
	static double health(double base, int players) {
		return Math.max(1.0, base * PartyScaling.bossHealth(Math.clamp(players, 1, 8)));
	}
}
