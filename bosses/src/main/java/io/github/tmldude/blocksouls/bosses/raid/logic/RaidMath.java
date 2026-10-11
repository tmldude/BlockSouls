package io.github.tmldude.blocksouls.bosses.raid.logic;

/** The final raid's numbers that depend on the group size, the clock and the phase. Pure logic, no Minecraft. */
public final class RaidMath {
	private RaidMath() {
	}

	/** Players the mechanics scale for: at least 1, at most {@code max} (8 by default). */
	public static int scaledPlayers(int players, int max) {
		return Math.clamp(players, 1, Math.max(1, max));
	}

	/** Crystals in a Crystal Order for {@code players} (at least 1 counted): {@code solo} plus some per extra player. */
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

	/** Mobs in the zombie wave: {@code base} plus {@code perPlayer} each, at most {@code max}. */
	public static int waveSize(int players, int base, int perPlayer, int max) {
		return Math.clamp(base + (long) perPlayer * Math.max(1, players), 1, Math.max(1, max));
	}

	/**
	 * Length of the Sovereign's End cast in {@code cycle} (0 = first): longer solo, shorter every cycle, never below
	 * {@code min}.
	 */
	public static int castTicks(int cycle, int players, int base, double soloBonus, double factor, int min) {
		double ticks = base * (players <= 1 ? 1 + soloBonus : 1) * Math.pow(factor, Math.max(0, cycle));
		return Math.max(min, (int) Math.round(ticks));
	}

	/** Soft enrage: on once the fight has run {@code enrageTicks} (0 or less turns it off). */
	public static boolean enraged(int fightTicks, int enrageTicks) {
		return enrageTicks > 0 && fightTicks >= enrageTicks;
	}

	/** Health fraction a phase starts at: phase 0 is full health, later phases start at their threshold. */
	public static double phaseStartFraction(int phase, double[] thresholds) {
		if (phase <= 0 || thresholds.length == 0) {
			return 1.0;
		}
		return thresholds[Math.min(phase, thresholds.length - 1)];
	}

	/**
	 * Where a wipe heals the dragon to: the start of the current phase, or in Grounded the last cleared interlude (a
	 * checkpoint: the wave and the herald stay done).
	 */
	public static double checkpoint(int phase, double[] thresholds, boolean waveDone, double waveThreshold, boolean heraldDone,
			double heraldThreshold) {
		if (phase > 0) {
			return phaseStartFraction(phase, thresholds);
		}
		if (heraldDone) {
			return heraldThreshold;
		}
		return waveDone ? waveThreshold : 1.0;
	}

	/**
	 * Lowest health fraction allowed in Grounded: it can't be pushed past an interlude that hasn't happened yet (the
	 * wave, then the herald). 0 once both are done.
	 */
	public static double healthFloor(boolean waveDone, double waveThreshold, boolean heraldDone, double heraldThreshold) {
		if (!waveDone) {
			return waveThreshold;
		}
		return heraldDone ? 0 : heraldThreshold;
	}

	/** Health after a heal of {@code fraction} of max health, capped at max. */
	public static double heal(double health, double maxHealth, double fraction) {
		return Math.min(maxHealth, health + maxHealth * fraction);
	}
}
