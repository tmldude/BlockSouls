package io.github.tmldude.blocksouls.game.run;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Wave rewards and their guarantees as pure functions (no Minecraft classes), so they can be unit-tested. */
public final class RewardMath {
	private RewardMath() {
	}

	/** Times the wave-clear reward table is rolled into the chest: grows with the wave and, through {@link PartyScaling#rewards}, the party. */
	public static int bundleRolls(int wave, int players, RunConfig config) {
		double raw = (config.rewardRollsBase + config.rewardRollsPerWave * Math.max(0, wave - 1)) * PartyScaling.rewards(players);
		return Math.clamp(Math.round(raw), 1, Math.max(1, config.rewardRollsMax));
	}

	/** Whether the pity gun is owed now: the pity night has come, the party has no gun but Starter Pistols, and it wasn't given yet. */
	public static boolean gunPityDue(int night, boolean partyHasGun, boolean alreadyGiven, RunConfig config) {
		return config.gunPityNight > 0 && config.gunPityGuns > 0 && night >= config.gunPityNight && !partyHasGun && !alreadyGiven;
	}

	/** Guns the pity hands out: one solo, more for bigger parties (shared loot scales through {@link PartyScaling#rewards}). */
	public static int pityGuns(int players, RunConfig config) {
		if (config.gunPityGuns <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(config.gunPityGuns * PartyScaling.rewards(players)));
	}
}
