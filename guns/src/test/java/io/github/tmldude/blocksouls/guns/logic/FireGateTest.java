package io.github.tmldude.blocksouls.guns.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FireGateTest {
	@Test
	void blocksShotsInsideTheInterval() {
		FireGate gate = new FireGate();
		assertTrue(gate.tryFire(100, 4, 0));
		assertFalse(gate.tryFire(101, 4, 0));
		assertFalse(gate.tryFire(103, 4, 0));
		assertTrue(gate.tryFire(104, 4, 0));
	}

	@Test
	void fractionalRateAveragesOut() {
		FireGate gate = new FireGate();
		double interval = ShotMath.fireIntervalTicks(140);
		int shots = 0;
		// Try every tick for 60 seconds.
		for (long tick = 0; tick < 1200; tick++) {
			if (gate.tryFire(tick, interval, 0)) {
				shots++;
			}
		}
		assertEquals(140, shots, 1);
	}

	@Test
	void toleranceAllowsBunchedPacketsWithoutRaisingTheRate() {
		FireGate gate = new FireGate();
		double interval = 2;
		assertTrue(gate.tryFire(0, interval, 1));
		// Next shot arrives one tick early (two packets bunched): allowed, but the schedule doesn't move earlier.
		assertTrue(gate.tryFire(1, interval, 1));
		assertEquals(4, gate.nextShot(), 1e-9);
		assertFalse(gate.tryFire(2, interval, 1));

		int shots = 0;
		FireGate spam = new FireGate();
		for (long tick = 0; tick < 200; tick++) {
			// Two attempts per tick.
			if (spam.tryFire(tick, interval, 1)) {
				shots++;
			}
			if (spam.tryFire(tick, interval, 1)) {
				shots++;
			}
		}
		assertTrue(shots <= 101, "rate stays capped, got " + shots);
	}

	@Test
	void lateShotRestartsTheSchedule() {
		FireGate gate = new FireGate();
		assertTrue(gate.tryFire(0, 4, 0));
		assertTrue(gate.tryFire(50, 4, 0));
		assertFalse(gate.tryFire(53, 4, 0));
		assertTrue(gate.tryFire(54, 4, 0));
	}
}
