package io.github.tmldude.blocksouls.bosses.raid.logic;

/** The final raid's numbers that depend on the group size, the clock and the phase. Pure logic, no Minecraft. */
public final class RaidMath {
	private RaidMath() {
	}

	/** Crystals in the Crystal Order for {@code players} (at least 1 counted): {@code solo} plus some per extra player. */
	public static int crystalCount(int players, int solo, int perExtraPlayer, int max) {
		int extra = Math.max(0, players - 1);
		return Math.clamp(solo + (long) perExtraPlayer * extra, 1, Math.max(1, max));
	}

	/** Plates that must be held at the same time: one per player, at most two (so a solo player can do it). */
	public static int requiredPlates(int players) {
		return Math.clamp(players, 1, 2);
	}

	/** Plates placed: one more than the players (choice of where to stand), between 2 and 4. */
	public static int plateSpots(int players) {
		return Math.clamp(players + 1, 2, 4);
	}

	/** Soft enrage: on once the fight has run {@code enrageTicks} (0 or less turns it off). */
	public static boolean enraged(int fightTicks, int enrageTicks) {
		return enrageTicks > 0 && fightTicks >= enrageTicks;
	}

	/**
	 * Health fraction a phase starts at: phase 0 is full health, later phases start at their threshold. A wipe heals
	 * the dragon back to this.
	 */
	public static double phaseStartFraction(int phase, double[] thresholds) {
		if (phase <= 0 || thresholds.length == 0) {
			return 1.0;
		}
		return thresholds[Math.min(phase, thresholds.length - 1)];
	}

	/**
	 * Lowest health fraction allowed right now: while the crystal shield is up the dragon can't be pushed out of the
	 * first phase (it stops a hair above the second phase's threshold); otherwise no floor.
	 */
	public static double healthFloor(boolean shielded, double[] thresholds) {
		if (!shielded || thresholds.length < 2) {
			return 0;
		}
		return Math.min(1.0, thresholds[1] + 0.005);
	}

	/** Health after a heal of {@code fraction} of max health, capped at max. */
	public static double heal(double health, double maxHealth, double fraction) {
		return Math.min(maxHealth, health + maxHealth * fraction);
	}
}
