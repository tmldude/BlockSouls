package io.github.tmldude.blocksouls.bosses.riftwarden;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The Riftwarden's party-scaled counts (pure, unit-tested at 1 and 8 players). */
public final class RiftMath {
	private RiftMath() {
	}

	/** Phase-2 adds per summon: {@code base} × {@code PartyScaling.arenaAdds}, rounded, between 1 and {@code max}. */
	public static int adds(int base, int players, int max) {
		return scaled(base, PartyScaling.arenaAdds(players), max);
	}

	/** Rift fissures per eruption: {@code base} × {@code PartyScaling.arenaHazards}, rounded, between 1 and {@code max}. */
	public static int fissures(int base, int players, int max) {
		return scaled(base, PartyScaling.arenaHazards(players), max);
	}

	static int scaled(int base, double multiplier, int max) {
		int n = (int) Math.round(Math.max(0, base) * multiplier);
		return Math.max(1, Math.min(Math.max(1, max), n));
	}
}
