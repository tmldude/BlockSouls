package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrailingBarTest {
	private static final double TICK = 0.05;

	@Test
	void hitDropsValueAtOnceAndHoldsTheTrail() {
		TrailingBar bar = new TrailingBar(1, 0.6, 0.5);
		bar.set(0.8);
		assertEquals(0.8, bar.value(), 1e-9);
		assertEquals(1.0, bar.trail(), 1e-9);
		for (int i = 0; i < 11; i++) {
			bar.tick(TICK);
		}
		assertEquals(1.0, bar.trail(), 1e-9, "still holding at 0.55 s");
	}

	@Test
	void trailDrainsAfterTheDelay() {
		TrailingBar bar = new TrailingBar(1, 0.6, 0.5);
		bar.set(0.8);
		bar.tick(0.7);
		// 0.1 s of draining at 0.5/s.
		assertEquals(0.95, bar.trail(), 1e-9);
		bar.tick(1);
		assertEquals(0.8, bar.trail(), 1e-9, "never drains below the value");
	}

	@Test
	void comboRestartsTheDelay() {
		TrailingBar bar = new TrailingBar(1, 0.6, 0.5);
		bar.set(0.9);
		bar.tick(0.5);
		bar.set(0.7);
		bar.tick(0.5);
		assertEquals(1.0, bar.trail(), 1e-9, "second hit restarted the hold");
	}

	@Test
	void healingMovesBothUp() {
		TrailingBar bar = new TrailingBar(0.5, 0.6, 0.5);
		bar.set(1.0);
		assertEquals(1.0, bar.value(), 1e-9);
		assertEquals(1.0, bar.trail(), 1e-9);
	}

	@Test
	void clampsBadInput() {
		TrailingBar bar = new TrailingBar(2, 0.6, 0.5);
		assertEquals(1.0, bar.value(), 1e-9);
		bar.set(Double.NaN);
		assertEquals(0.0, bar.value(), 1e-9);
	}
}
