package io.github.tmldude.blocksouls.bosses.stormplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StormMathTest {
	@Test
	void spearFliesOutHangsAndComesBack() {
		int flight = 9;
		int back = 13;
		assertEquals(0, StormMath.spearFraction(0, flight, back), 1e-9, "starts in the hand");
		assertEquals(1, StormMath.spearFraction(flight, flight, back), 1e-9, "reaches the far end after the flight");
		assertEquals(1, StormMath.spearFraction(back, flight, back), 1e-9, "hangs until it's recalled");
		assertEquals(0, StormMath.spearFraction(back + flight, flight, back), 1e-9, "back in the hand");
		double previous = -1;
		for (int t = 0; t <= flight; t++) {
			double f = StormMath.spearFraction(t, flight, back);
			assertTrue(f > previous, "moves outward every tick");
			previous = f;
		}
		for (int t = back + 1; t <= back + flight; t++) {
			assertTrue(StormMath.spearFraction(t, flight, back) < StormMath.spearFraction(t - 1, flight, back), "moves back every tick");
		}
	}

	@Test
	void spearIsFastOutOfTheHand() {
		assertTrue(StormMath.spearFraction(1, 10, 12) > 0.15, "eases out: the first tick covers more than a linear share");
	}

	@Test
	void puddlePulsesOncePerBeat() {
		int pulses = 0;
		for (int age = 0; age < 500; age++) {
			if (StormMath.puddlePulses(age, 50)) {
				pulses++;
			}
		}
		assertEquals(10, pulses);
		assertFalse(StormMath.puddlePulses(0, 50), "a fresh puddle doesn't pulse at once");
	}

	@Test
	void warningFillsBeforeThePulse() {
		assertEquals(0, StormMath.puddleWarning(10, 50, 15), 1e-6);
		assertTrue(StormMath.puddleWarning(35, 50, 15) > 0, "warning starts 15 ticks before");
		assertEquals(1, StormMath.puddleWarning(49, 50, 15), 1e-6, "full on the pulse tick");
		assertTrue(StormMath.puddleWarning(40, 50, 15) < StormMath.puddleWarning(45, 50, 15), "grows toward the pulse");
	}
}
