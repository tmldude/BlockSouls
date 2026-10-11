package io.github.tmldude.blocksouls.mobs.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BreakTimeTest {
	@Test
	void slowestAtWaveOne() {
		assertEquals(8.0, BreakTime.seconds(1, 8, 3, 10, 1), 1e-9);
		assertEquals(160, BreakTime.ticks(1, 8, 3, 10, 1));
	}

	@Test
	void fastestFromWaveTen() {
		assertEquals(3.0, BreakTime.seconds(10, 8, 3, 10, 1), 1e-9);
		assertEquals(3.0, BreakTime.seconds(25, 8, 3, 10, 1), 1e-9);
	}

	@Test
	void linearInBetween() {
		// Wave 5.5 would be halfway; wave 4 is a third of the way.
		assertEquals(8 - 5.0 / 3, BreakTime.seconds(4, 8, 3, 10, 1), 1e-9);
	}

	@Test
	void waveZeroCountsAsWaveOne() {
		assertEquals(8.0, BreakTime.seconds(0, 8, 3, 10, 1), 1e-9);
	}

	@Test
	void speedMultiplierDividesTime() {
		assertEquals(4.0, BreakTime.seconds(1, 8, 3, 10, 2), 1e-9);
		assertEquals(30, BreakTime.ticks(10, 8, 3, 10, 2));
	}

	@Test
	void crackStages() {
		assertEquals(0, BreakTime.crackStage(0, 100));
		assertEquals(5, BreakTime.crackStage(50, 100));
		assertEquals(9, BreakTime.crackStage(100, 100));
	}
}
