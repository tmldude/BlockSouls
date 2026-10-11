package io.github.tmldude.blocksouls.bosses.raid.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.bosses.logic.PhaseMath;

class RaidMathTest {
	private static final double[] PHASES = {1.0, 0.7, 0.3};

	@Test
	void crystalCountGrowsWithPlayersUpToTheCap() {
		assertEquals(5, RaidMath.crystalCount(1, 5, 1, 8));
		assertEquals(6, RaidMath.crystalCount(2, 5, 1, 8));
		assertEquals(8, RaidMath.crystalCount(4, 5, 1, 8));
		assertEquals(8, RaidMath.crystalCount(10, 5, 1, 8));
		assertEquals(5, RaidMath.crystalCount(0, 5, 1, 8), "nobody counted yet plays as solo");
	}

	@Test
	void requiredPlatesIsPlayersCappedAtTwo() {
		assertEquals(1, RaidMath.requiredPlates(1), "solo must be possible");
		assertEquals(2, RaidMath.requiredPlates(2));
		assertEquals(2, RaidMath.requiredPlates(4));
		assertEquals(1, RaidMath.requiredPlates(0));
	}

	@Test
	void plateSpotsAreTwoToFour() {
		assertEquals(2, RaidMath.plateSpots(1));
		assertEquals(3, RaidMath.plateSpots(2));
		assertEquals(4, RaidMath.plateSpots(3));
		assertEquals(4, RaidMath.plateSpots(4));
		for (int players = 1; players <= 4; players++) {
			assertTrue(RaidMath.plateSpots(players) > RaidMath.requiredPlates(players), "always a spare plate");
		}
	}

	@Test
	void enrageStartsAtTwelveMinutes() {
		int twelveMinutes = 20 * 60 * 12;
		assertFalse(RaidMath.enraged(twelveMinutes - 1, twelveMinutes));
		assertTrue(RaidMath.enraged(twelveMinutes, twelveMinutes));
		assertFalse(RaidMath.enraged(1_000_000, 0), "0 turns it off");
	}

	@Test
	void phaseThresholdsSplitTheFightIntoThree() {
		assertTrue(PhaseMath.valid(PHASES));
		assertEquals(0, PhaseMath.settle(0.71, PHASES));
		assertEquals(1, PhaseMath.settle(0.69, PHASES));
		assertEquals(1, PhaseMath.settle(0.31, PHASES));
		assertEquals(2, PhaseMath.settle(0.29, PHASES));
	}

	@Test
	void wipeHealsToTheStartOfTheCurrentPhase() {
		assertEquals(1.0, RaidMath.phaseStartFraction(0, PHASES));
		assertEquals(0.7, RaidMath.phaseStartFraction(1, PHASES));
		assertEquals(0.3, RaidMath.phaseStartFraction(2, PHASES));
		// Healing to the phase start never moves the phase back or forward.
		for (int phase = 0; phase < PHASES.length; phase++) {
			assertEquals(phase, PhaseMath.next(phase, RaidMath.phaseStartFraction(phase, PHASES), PHASES));
		}
	}

	@Test
	void shieldKeepsTheDragonInPhaseOne() {
		double floor = RaidMath.healthFloor(true, PHASES);
		assertTrue(floor > 0.7 && floor < 0.71);
		assertEquals(0, PhaseMath.next(0, floor, PHASES));
		assertEquals(0, RaidMath.healthFloor(false, PHASES));
	}

	@Test
	void healIsCapped() {
		assertEquals(150, RaidMath.heal(100, 1000, 0.05), 1e-9);
		assertEquals(1000, RaidMath.heal(990, 1000, 0.05), 1e-9);
	}

	@Test
	void plateHoldFillsDrainsAndCompletes() {
		PlateHold hold = new PlateHold();
		for (int i = 0; i < 59; i++) {
			assertFalse(hold.tick(1, 1, 60));
		}
		assertTrue(hold.tick(1, 1, 60), "60 ticks held completes");
		assertEquals(0, hold.ticks(), "resets after completing");

		for (int i = 0; i < 30; i++) {
			hold.tick(1, 2, 60);
		}
		assertEquals(0, hold.ticks(), "not enough plates held: no progress");
		for (int i = 0; i < 40; i++) {
			hold.tick(2, 2, 60);
		}
		for (int i = 0; i < 10; i++) {
			hold.tick(1, 2, 60);
		}
		assertEquals(30, hold.ticks(), "stepping off drains");
		assertEquals(0.5f, hold.progress(60), 1e-6);
	}
}
