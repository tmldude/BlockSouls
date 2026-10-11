package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhaseMathTest {
	private static final double[] TWO = {1.0, 0.5};
	private static final double[] THREE = {1.0, 0.6, 0.25};

	@Test
	void staysInFirstPhaseAboveThreshold() {
		assertEquals(0, PhaseMath.next(0, 1.0, TWO));
		assertEquals(0, PhaseMath.next(0, 0.5, TWO), "exactly at the threshold is still P1 (P2 is below 50%)");
	}

	@Test
	void entersNextPhaseBelowThreshold() {
		assertEquals(1, PhaseMath.next(0, 0.49, TWO));
		assertEquals(1, PhaseMath.next(1, 0.1, TWO), "no phase after the last");
	}

	@Test
	void advancesOnePhaseAtATime() {
		assertEquals(1, PhaseMath.next(0, 0.1, THREE), "skipping P2 still plays P2's transition first");
		assertEquals(2, PhaseMath.next(1, 0.1, THREE));
		assertEquals(2, PhaseMath.settle(0.1, THREE));
		assertEquals(1, PhaseMath.settle(0.3, THREE));
	}

	@Test
	void neverGoesBack() {
		assertEquals(1, PhaseMath.next(1, 0.9, TWO), "healing doesn't undo a phase");
	}

	@Test
	void validatesThresholds() {
		assertTrue(PhaseMath.valid(THREE));
		assertFalse(PhaseMath.valid(new double[] {1.0, 0.5, 0.6}));
		assertFalse(PhaseMath.valid(new double[] {1.0, 0}));
		assertFalse(PhaseMath.valid(new double[0]));
	}
}
