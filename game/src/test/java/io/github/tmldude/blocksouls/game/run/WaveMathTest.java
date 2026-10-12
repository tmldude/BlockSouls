package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaveMathTest {
	private final RunConfig config = new RunConfig();

	@Test
	void waveCountFollowsTheFormula() {
		// Solo: (6 + 3) · 1 = 9
		assertEquals(9, WaveMath.waveCount(1, 1, 1, 1, config));
		// 4 players: (6 + 30) · (1 + 0.55 · 3^0.9 = 2.478) = 89.2
		assertEquals(89, WaveMath.waveCount(10, 4, 1, 1, config));
		// Blood Moon on nightmare, wave 5, 2 players: 21 · 1.55 · 1.6 · 1.5 = 78.12
		assertEquals(78, WaveMath.waveCount(5, 2, 1.6, 1.5, config));
		// A full party of 8 at wave 8: 30 · 4.169 = 125.1
		assertEquals(125, WaveMath.waveCount(8, 8, 1, 1, config));
		// Zero players still counts as one.
		assertEquals(9, WaveMath.waveCount(1, 0, 1, 1, config));
	}

	@Test
	void scalingGrowsPerWave() {
		assertEquals(1.0, WaveMath.healthMultiplier(1, 1, config), 1e-9);
		assertEquals(1.72, WaveMath.healthMultiplier(10, 1, config), 1e-9);
		assertEquals(1.72 * 1.6, WaveMath.healthMultiplier(10, 1.6, config), 1e-9);
		assertEquals(1.0, WaveMath.damageMultiplier(1, 1, config), 1e-9);
		assertEquals(1.45 * 1.25, WaveMath.damageMultiplier(10, 1.25, config), 1e-9);
	}

	@Test
	void breakerShareCreepsUpAndCaps() {
		assertEquals(0.10, WaveMath.breakerShare(1, config), 1e-9);
		assertEquals(0.28, WaveMath.breakerShare(10, config), 1e-9);
		assertEquals(0.40, WaveMath.breakerShare(30, config), 1e-9);
	}

	@Test
	void elitesPerWave() {
		assertEquals(1, WaveMath.maxElites(1, 1, config));
		assertEquals(2, WaveMath.maxElites(4, 1, config));
		assertEquals(3, WaveMath.maxElites(9, 1, config));
		// 8 players: (1 + 8/4) · 3.017 = 9
		assertEquals(9, WaveMath.maxElites(8, 8, config));
	}

	@Test
	void budgetPerNightRepeatsTheLastValue() {
		assertEquals(2, WaveMath.wavesForNight(1, false, config));
		assertEquals(2, WaveMath.wavesForNight(3, false, config));
		assertEquals(4, WaveMath.wavesForNight(8, false, config));
		assertEquals(4, WaveMath.wavesForNight(20, false, config));
		assertEquals(5, WaveMath.wavesForNight(20, true, config));
		assertEquals(3, WaveMath.wavesForNight(1, true, config));
	}

	@Test
	void tierOddsByWave() {
		assertArrayEquals(new double[] {1, 0, 0}, WaveMath.tierOdds(2, config), 1e-9);
		assertArrayEquals(new double[] {0.8, 0.2, 0}, WaveMath.tierOdds(5, config), 1e-9);
		assertArrayEquals(new double[] {0.65, 0.3, 0.05}, WaveMath.tierOdds(12, config), 1e-9);
	}

	@Test
	void pickTierAppliesBloodMoonAndElites() {
		assertEquals(WaveMath.COMMON, WaveMath.pickTier(1, 0.99, 0, false, config));
		assertEquals(WaveMath.RARE, WaveMath.pickTier(1, 0.5, 1, false, config));
		assertEquals(WaveMath.RARE, WaveMath.pickTier(1, 0.1, 0, true, config));
		assertEquals(WaveMath.RARE, WaveMath.pickTier(5, 0.85, 0, false, config));
		assertEquals(WaveMath.LEGENDARY, WaveMath.pickTier(5, 0.85, 1, false, config));
		assertEquals(WaveMath.LEGENDARY, WaveMath.pickTier(7, 0.97, 0, false, config));
		assertEquals(WaveMath.LEGENDARY, WaveMath.pickTier(7, 0.97, 1, true, config));
	}

	@Test
	void dayLengthRate() {
		// 17-minute days: ≈1.176 day-time ticks per game tick, so 24000 day ticks take 17 · 60 · 20 game ticks.
		float rate = WaveMath.clockRate(17.0);
		assertEquals(20.0 / 17.0, rate, 1e-6);
		assertEquals(17 * 60 * 20, 24000 / rate, 1.0);
		assertEquals(1.0F, WaveMath.clockRate(20.0), 1e-6);
		assertEquals(1.0F, WaveMath.clockRate(0), 1e-6);
	}

	@Test
	void dayLengthAccumulatorAdvancesUniformly() {
		// Mirrors vanilla's clock accumulator: whole ticks advance, the fraction carries over.
		float rate = WaveMath.clockRate(17.0);
		float partial = 0;
		long total = 0;
		int gameTicks = 17 * 60 * 20;
		for (int i = 0; i < gameTicks; i++) {
			partial += rate;
			int whole = (int) Math.floor(partial);
			partial -= whole;
			total += whole;
			// Never more than 2 day ticks in one game tick: the day speeds up evenly.
			assertTrue(whole == 1 || whole == 2);
		}
		assertEquals(24000, total, 2);
	}

	@Test
	void nightWindowAndTimers() {
		assertFalse(WaveMath.isNight(12999, config));
		assertTrue(WaveMath.isNight(13000, config));
		assertTrue(WaveMath.isNight(22999, config));
		assertFalse(WaveMath.isNight(23000, config));
		assertTrue(WaveMath.isNight(24000L * 5 + 15000, config));
		assertEquals(10000, WaveMath.ticksUntil(13000, 23000));
		assertEquals(14000, WaveMath.ticksUntil(23000, 13000));
		assertEquals(0, WaveMath.ticksUntil(13000, 13000));
		// 1200 day ticks at rate 1 = 60 s.
		assertEquals(60.0, WaveMath.secondsUntil(21800, 23000, 1.0F), 1e-9);
	}
}
