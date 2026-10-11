package io.github.tmldude.blocksouls.bosses.raid.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.bosses.logic.PhaseMath;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

class RaidMathTest {
	/** Our dragon's phases: Grounded (raid phase 2), Final Stand (raid phase 3) below 35%. */
	private static final double[] PHASES = {1.0, 0.35};

	@Test
	void playersScaleUpToEight() {
		assertEquals(1, RaidMath.scaledPlayers(0));
		assertEquals(5, RaidMath.scaledPlayers(5));
		assertEquals(8, RaidMath.scaledPlayers(12));
	}

	@Test
	void crystalCountFollowsThePartyCurve() {
		assertEquals(3, RaidMath.crystalCount(1, 3, 8), "solo");
		assertEquals(4, RaidMath.crystalCount(2, 3, 8));
		assertEquals(5, RaidMath.crystalCount(4, 3, 8));
		assertEquals(8, RaidMath.crystalCount(8, 3, 8), "a full party of 8");
		assertEquals(8, RaidMath.crystalCount(20, 3, 8), "bigger parties count as 8");
		assertEquals(3, RaidMath.crystalCount(0, 3, 8), "nobody counted yet plays as solo");
	}

	@Test
	void requiredPlatesIsOnePerTwoPlayers() {
		assertEquals(1, RaidMath.requiredPlates(1), "solo must be possible");
		assertEquals(1, RaidMath.requiredPlates(2));
		assertEquals(2, RaidMath.requiredPlates(4));
		assertEquals(4, RaidMath.requiredPlates(8), "a full party holds 4");
		assertEquals(PartyScaling.raidPlates(8), RaidMath.requiredPlates(8));
	}

	@Test
	void plateSpotsAlwaysHaveASpare() {
		for (int players = 1; players <= 8; players++) {
			int spots = RaidMath.plateSpots(players);
			assertTrue(spots >= 2 && spots <= 5);
			assertTrue(spots > RaidMath.requiredPlates(players), "always a spare plate");
			assertTrue(RaidMath.requiredPlates(players) < players || players == 1, "someone is free to break crystals");
		}
	}

	@Test
	void waveFollowsThePartyCurve() {
		assertEquals(7, RaidMath.waveSize(1, 7, 40), "solo");
		assertEquals(29, RaidMath.waveSize(8, 7, 40), "a full party of 8");
		assertEquals(29, RaidMath.waveSize(20, 7, 40), "bigger parties count as 8");
		assertEquals(20, RaidMath.waveSize(8, 7, 20), "capped");
	}

	@Test
	void castIsLongerSoloAndShortensEachCycle() {
		int solo = RaidMath.castTicks(0, 1, 1200, 0.25, 0.85, 600);
		int duo = RaidMath.castTicks(0, 2, 1200, 0.25, 0.85, 600);
		assertEquals(1500, solo);
		assertEquals(1200, duo);
		assertTrue(RaidMath.castTicks(1, 2, 1200, 0.25, 0.85, 600) < duo);
		assertEquals(600, RaidMath.castTicks(20, 2, 1200, 0.25, 0.85, 600), "never below the minimum");
	}

	@Test
	void enrageStartsAtTwelveMinutes() {
		int twelveMinutes = 20 * 60 * 12;
		assertFalse(RaidMath.enraged(twelveMinutes - 1, twelveMinutes));
		assertTrue(RaidMath.enraged(twelveMinutes, twelveMinutes));
		assertFalse(RaidMath.enraged(1_000_000, 0), "0 turns it off");
	}

	@Test
	void finalStandStartsBelowItsThreshold() {
		assertTrue(PhaseMath.valid(PHASES));
		assertEquals(0, PhaseMath.settle(0.36, PHASES));
		assertEquals(1, PhaseMath.settle(0.34, PHASES));
	}

	@Test
	void wipeHealsToTheLastCheckpoint() {
		assertEquals(1.0, RaidMath.checkpoint(0, PHASES, false, 0.85, false, 0.6));
		assertEquals(0.85, RaidMath.checkpoint(0, PHASES, true, 0.85, false, 0.6), "the wave stays cleared");
		assertEquals(0.6, RaidMath.checkpoint(0, PHASES, true, 0.85, true, 0.6), "the herald stays dead");
		assertEquals(0.35, RaidMath.checkpoint(1, PHASES, true, 0.85, true, 0.6), "phase 3 restarts at its start");
		// Healing to a checkpoint never moves the phase.
		assertEquals(0, PhaseMath.next(0, 0.6, PHASES));
		assertEquals(1, PhaseMath.next(1, 0.35, PHASES));
	}

	@Test
	void interludesCantBeSkipped() {
		assertEquals(0.85, RaidMath.healthFloor(false, 0.85, false, 0.6));
		assertEquals(0.6, RaidMath.healthFloor(true, 0.85, false, 0.6));
		assertEquals(0, RaidMath.healthFloor(true, 0.85, true, 0.6));
	}

	@Test
	void healIsCapped() {
		assertEquals(150, RaidMath.heal(100, 1000, 0.05), 1e-9);
		assertEquals(1000, RaidMath.heal(990, 1000, 0.05), 1e-9);
	}
}
