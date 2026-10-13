package io.github.tmldude.blocksouls.bosses.logic;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * Posture (poise) arithmetic, pure so it's unit-tested. Poise fills from precision hits and well-timed rolls; full
 * poise breaks the boss's posture (the visceral window). Poise decays after a quiet spell, and is scaled for the
 * party with the same curve as boss health, so an eight-player party (eight times the precision hits) breaks a boss
 * about as often as a solo player does.
 */
public final class PostureMath {
	private PostureMath() {
	}

	/** Max poise for a fight with {@code players} in it (fewer than one counts as one). */
	public static double maxPoise(double basePoise, int players) {
		return Math.max(1, basePoise * PartyScaling.bossHealth(players));
	}

	/** Poise after gaining {@code amount}, capped at {@code max}. */
	public static double gain(double poise, double amount, double max) {
		return Math.clamp(poise + Math.max(0, amount), 0, max);
	}

	/** Whether this much poise breaks posture. */
	public static boolean broken(double poise, double max) {
		return poise >= max - 1e-6;
	}

	/** One tick of decay: nothing until {@code idleTicks} reaches {@code delay}, then {@code perTick} a tick. */
	public static double decay(double poise, int idleTicks, int delay, double perTick) {
		return idleTicks < delay ? poise : Math.max(0, poise - Math.max(0, perTick));
	}

	/** Poise a precision hit adds: a flat part plus a share of its damage. */
	public static double precisionHit(double damage, double flat, double perDamage) {
		return Math.max(0, flat + Math.max(0, damage) * perDamage);
	}

	/**
	 * Whether a roll {@code ticksBeforeActive} ticks before the attack's hitboxes go live (negative = during the active
	 * part) is well-timed: inside the active part, or at most {@code lead} ticks before it.
	 */
	public static boolean wellTimedRoll(int ticksBeforeActive, int activeTicks, int lead) {
		return ticksBeforeActive <= lead && ticksBeforeActive > -activeTicks;
	}
}
