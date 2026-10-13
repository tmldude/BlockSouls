package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class RewardMathTest {
	private final RunConfig config = new RunConfig();

	@AfterEach
	void restoreParty() {
		PartyScaling.setConfig(new PartyConfig());
	}

	@Test
	void soloGetsOneBundleEarlyAndMoreLater() {
		assertEquals(1, RewardMath.bundleRolls(1, 1, config));
		assertEquals(2, RewardMath.bundleRolls(8, 1, config), "1 + 0.15·7 = 2.05");
		assertTrue(RewardMath.bundleRolls(20, 1, config) > RewardMath.bundleRolls(8, 1, config), "escalates with the wave");
	}

	@Test
	void eightPlayersGetMoreBundlesButCapped() {
		int solo = RewardMath.bundleRolls(1, 1, config);
		int eight = RewardMath.bundleRolls(1, 8, config);
		assertEquals(3, eight, "party rewards ×3.37 at 8 players");
		assertTrue(eight > solo);
		assertEquals(config.rewardRollsMax, RewardMath.bundleRolls(40, 8, config), "never past rewardRollsMax");
		assertEquals(1, RewardMath.bundleRolls(1, 0, config), "nobody present counts as solo");
	}

	@Test
	void pityGunIsOwedFromNightTwoOnlyWhenUnarmed() {
		assertFalse(RewardMath.gunPityDue(1, false, false, config), "night 1 is too early");
		assertTrue(RewardMath.gunPityDue(2, false, false, config), "night 2, nothing but pistols");
		assertTrue(RewardMath.gunPityDue(3, false, false, config), "a skipped night 2 still owes it");
		assertFalse(RewardMath.gunPityDue(2, true, false, config), "an armed party needs no pity");
		assertFalse(RewardMath.gunPityDue(2, false, true, config), "only once per run");
	}

	@Test
	void pityGunCountScalesWithTheParty() {
		assertEquals(1, RewardMath.pityGuns(1, config), "solo: one gun");
		assertEquals(3, RewardMath.pityGuns(8, config), "8 players: round(3.37)");
		assertTrue(RewardMath.gunPityDue(2, false, false, config));
	}

	@Test
	void pityCanBeTurnedOff() {
		config.gunPityGuns = 0;
		assertFalse(RewardMath.gunPityDue(2, false, false, config));
		assertEquals(0, RewardMath.pityGuns(8, config));
		config.gunPityGuns = 1;
		config.gunPityNight = 0;
		assertFalse(RewardMath.gunPityDue(5, false, false, config));
	}
}
