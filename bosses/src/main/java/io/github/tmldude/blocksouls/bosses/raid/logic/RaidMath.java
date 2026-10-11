package io.github.tmldude.blocksouls.bosses.raid.logic;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * The final raid's numbers that depend on the group size, the clock and the phase. Pure logic, no Minecraft. Everything
 * that grows with players goes through common's {@link PartyScaling} (party.json), tuned for parties of 1–8.
 */
public final class RaidMath {
	private RaidMath() {
	}

	/** Players the mechanics scale for: at least 1, at most the party cap (8 by default). */
	public static int scaledPlayers(int players) {
		return PartyScaling.partySize(players);
	}

	/** Crystals in a Crystal Order: {@code solo} × the party's crystal curve, at least 1, at most {@code max}. */
	public static int crystalCount(int players, int solo, int max) {
		return Math.clamp(Math.round(solo * PartyScaling.raidCrystals(players)), 1, Math.max(1, max));
	}

	/** Plates that must be held at the same time: one per two players (1 solo, 4 for a party of 8). */
	public static int requiredPlates(int players) {
		return PartyScaling.raidPlates(players);
	}

	/** Plates placed: one more than required (a choice of where to stand), at least 2. */
	public static int plateSpots(int players) {
		return Math.max(2, requiredPlates(players) + 1);
	}

	/** Mobs in the zombie wave: {@code solo} × the party's wave curve, at least 1, at most {@code max}. */
	public static int waveSize(int players, int solo, int max) {
		return Math.clamp(Math.round(solo * PartyScaling.waveSize(players)), 1, Math.max(1, max));
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
