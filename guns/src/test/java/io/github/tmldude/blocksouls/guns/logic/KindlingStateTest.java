package io.github.tmldude.blocksouls.guns.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Kindling Engine's heat math (the item component wraps these). */
class KindlingStateTest {
	@Test
	void heatDecaysPerTick() {
		assertEquals(50, HeatMath.decayed(50, 0, 1.2), 1e-9);
		assertEquals(38, HeatMath.decayed(50, 10, 1.2), 1e-9);
		assertEquals(0, HeatMath.decayed(50, 1000, 1.2), 1e-9, "never below cold");
		assertEquals(50, HeatMath.decayed(50, -5, 1.2), 1e-9, "a clock going backwards doesn't heat");
	}

	@Test
	void heatingStacksAndOverheatsAtTheTop() {
		assertEquals(9, HeatMath.heated(0, 9), 1e-9);
		assertEquals(HeatMath.MAX, HeatMath.heated(95, 9), 1e-9);
		assertFalse(HeatMath.overheats(50, 9));
		assertTrue(HeatMath.overheats(95, 9));
		assertTrue(HeatMath.overheats(91, 9), "exactly full counts");
	}

	@Test
	void fireRateRampsWithHeat() {
		assertEquals(1.0, HeatMath.rateMult(0, 1.8), 1e-9);
		assertEquals(1.4, HeatMath.rateMult(50, 1.8), 1e-9);
		assertEquals(1.8, HeatMath.rateMult(100, 1.8), 1e-9);
		assertEquals(1.0, HeatMath.rateMult(100, 0.5), 1e-9, "a multiplier below 1 never slows the gun");
	}
}
