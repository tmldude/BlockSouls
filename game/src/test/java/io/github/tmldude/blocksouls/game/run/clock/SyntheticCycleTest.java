package io.github.tmldude.blocksouls.game.run.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.game.run.RunConfig;
import io.github.tmldude.blocksouls.game.run.WaveMath;

class SyntheticCycleTest {
	@Test
	void startsAlignedToTheGivenDayTime() {
		SyntheticCycle cycle = new SyntheticCycle();
		assertFalse(cycle.started());
		cycle.start(24000 * 3 + 5000);
		assertTrue(cycle.started());
		assertEquals(5000, cycle.timeOfDay(), "aligned to the sun's phase, not its day count");
		// A second start is a no-op (restarts keep the persisted phase).
		cycle.start(11111);
		assertEquals(5000, cycle.timeOfDay());
	}

	@Test
	void doesNotAdvanceBeforeStarting() {
		SyntheticCycle cycle = new SyntheticCycle();
		cycle.advance(1.5F);
		assertEquals(0, cycle.cycleTime());
	}

	@Test
	void advanceCarriesTheFractionLikeTheVanillaClockRate() {
		SyntheticCycle cycle = new SyntheticCycle();
		cycle.start(0);
		// The 17-minute day: rate 20/17 ≈ 1.176 day ticks per game tick.
		float rate = WaveMath.clockRate(17.0);
		int gameTicks = 17 * 60 * 20;
		for (int i = 0; i < gameTicks; i++) {
			cycle.advance(rate);
		}
		// One full real-time day length must land within a tick-rounding error of one full cycle.
		assertTrue(Math.abs(cycle.cycleTime() - WaveMath.DAY_TICKS) <= 1,
				"a 17-minute run of ticks is one full cycle, was " + cycle.cycleTime());
	}

	@Test
	void duskAndDawnEdgesMatchTheSunRules() {
		RunConfig config = new RunConfig();
		SyntheticCycle cycle = new SyntheticCycle();
		cycle.start(0);
		assertFalse(WaveMath.isNight(cycle.cycleTime(), config));
		cycle.skipTo(config.duskTime);
		assertTrue(WaveMath.isNight(cycle.cycleTime(), config), "dusk is night");
		cycle.skipTo(config.dawnTime);
		assertFalse(WaveMath.isNight(cycle.cycleTime(), config), "dawn ends it");
		// The same edge detection the scheduler uses for the sun works on the synthetic phase.
		cycle.skipTo(config.duskTime - 1);
		assertFalse(WaveMath.isNight(cycle.cycleTime(), config));
		cycle.advance(1.0F);
		assertTrue(WaveMath.isNight(cycle.cycleTime(), config));
	}

	@Test
	void skipToMovesForwardOnly() {
		SyntheticCycle cycle = new SyntheticCycle();
		cycle.start(18000);
		cycle.skipTo(1000);
		assertEquals(1000, cycle.timeOfDay());
		assertTrue(cycle.cycleTime() > 18000, "skipped into the next cycle, never backwards");
	}

	@Test
	void persistenceRoundTripsAcrossARestart() {
		SyntheticCycle cycle = new SyntheticCycle();
		cycle.start(12345);
		cycle.advance(1.3F);
		cycle.advance(1.3F);
		SyntheticCycle restarted = new SyntheticCycle();
		restarted.load(cycle.saveTotalTicks(), cycle.saveRemainder(), cycle.started());
		assertEquals(cycle.cycleTime(), restarted.cycleTime());
		assertTrue(restarted.started());
		// Both continue identically.
		cycle.advance(1.3F);
		restarted.advance(1.3F);
		assertEquals(cycle.cycleTime(), restarted.cycleTime());
	}

	@Test
	void loadSanitizesGarbage() {
		SyntheticCycle cycle = new SyntheticCycle();
		cycle.load(-5, Float.NaN, true);
		assertEquals(0, cycle.cycleTime());
		cycle.advance(1.0F);
		assertEquals(1, cycle.cycleTime());
	}
}
