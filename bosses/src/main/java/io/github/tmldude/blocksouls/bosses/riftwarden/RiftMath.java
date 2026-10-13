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

	/**
	 * The damage rush's window in ticks: {@code seconds} × {@code PartyScaling.rushWindow} × {@code shrink}^(rush − 1),
	 * never under {@code minSeconds}.
	 *
	 * @param rush 1-based: the first rush gets the full window, each later one is shorter
	 */
	public static int rushTicks(double seconds, int players, int rush, double shrink, double minSeconds) {
		double window = seconds * PartyScaling.rushWindow(players) * Math.pow(Math.max(0, shrink), Math.max(0, rush - 1));
		return (int) Math.round(20 * Math.max(minSeconds, window));
	}

	/** Whether {@code dealt} damage since the rush began reaches {@code fraction} of max health (the kill). */
	public static boolean rushWon(double dealt, double maxHealth, double fraction) {
		return dealt >= maxHealth * fraction;
	}

	/** Whether the rush that just failed was the last timed one ({@code rush} is 1-based); after it the boss is unbound. */
	public static boolean lastRush(int rush, int maxRushes) {
		return rush >= Math.max(1, maxRushes);
	}

	static int scaled(int base, double multiplier, int max) {
		int n = (int) Math.round(Math.max(0, base) * multiplier);
		return Math.max(1, Math.min(Math.max(1, max), n));
	}
}
