package io.github.tmldude.blocksouls.world.floor;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Skyrift's mini-boss numbers by party size (pure; party curves from {@code party.json}). */
public final class SkyBossMath {
	private SkyBossMath() {
	}

	/** A Skyrift mini-boss's health: its base times {@code PartyScaling.bossHealth}. */
	public static double health(double base, int players) {
		return Math.max(1.0, base * PartyScaling.bossHealth(players));
	}

	/** Gliders the Matriarch calls per wave: the base pack times {@code PartyScaling.directorBurst}, at least one. */
	public static int gliderPack(int base, int players) {
		return (int) Math.max(1, Math.round(base * PartyScaling.directorBurst(players)));
	}
}
