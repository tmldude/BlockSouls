package io.github.tmldude.blocksouls.guns.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShotMathTest {
	@Test
	void damageAppliesRarityPrecisionAndPerks() {
		assertEquals(9.0, ShotMath.damage(9, 1.0, false, 2.0, 1.0, 1.0, 0, 0.7), 1e-9);
		assertEquals(9.0 * 1.2 * 2.0, ShotMath.damage(9, 1.2, true, 2.0, 1.0, 1.0, 0, 0.7), 1e-9);
		// Rampage x3 (+30%) and Vorpal (+20%) multiply.
		assertEquals(10 * 1.3 * 1.2, ShotMath.damage(10, 1.0, false, 2.0, 1.3 * 1.2, 1.0, 0, 0.7), 1e-9);
	}

	@Test
	void eachBounceKeepsSeventyPercent() {
		assertEquals(10 * 0.7, ShotMath.damage(10, 1, false, 1, 1, 1, 1, 0.7), 1e-9);
		assertEquals(10 * 0.49, ShotMath.damage(10, 1, false, 1, 1, 1, 2, 0.7), 1e-9);
	}

	@Test
	void falloffIsFlatThenLinear() {
		assertEquals(1.0, ShotMath.falloff(5, 8, 20, 0.3));
		assertEquals(1.0, ShotMath.falloff(8, 8, 20, 0.3));
		assertEquals(0.65, ShotMath.falloff(14, 8, 20, 0.3), 1e-9);
		assertEquals(0.3, ShotMath.falloff(20, 8, 20, 0.3), 1e-9);
		assertEquals(0.3, ShotMath.falloff(50, 8, 20, 0.3), 1e-9);
		// start >= range disables it
		assertEquals(1.0, ShotMath.falloff(119, 120, 120, 1.0));
	}

	@Test
	void fireInterval() {
		assertEquals(2.0, ShotMath.fireIntervalTicks(600), 1e-9);
		assertEquals(4.0, ShotMath.fireIntervalTicks(300), 1e-9);
		assertEquals(1200.0 / 140, ShotMath.fireIntervalTicks(140), 1e-9);
	}

	@Test
	void precisionZoneScalesWithHeight() {
		// Zombie: height 1.95, eye 1.74 above feet.
		assertTrue(ShotMath.isPrecision(1.6, 1.74, 1.95));
		assertFalse(ShotMath.isPrecision(1.2, 1.74, 1.95));
		// A tall mob's head zone is proportionally taller.
		assertTrue(ShotMath.isPrecision(2.2, 2.6, 2.9));
		assertFalse(ShotMath.isPrecision(2.0, 2.6, 2.9));
	}

	@Test
	void reloadSpeedTraitAndOutlaw() {
		assertEquals(32, ShotMath.reloadTicks(1.6, 0, 0.25, false, 1.5));
		// +25% per level: 1.6s * 20 / 1.25 = 25.6 -> 26
		assertEquals(26, ShotMath.reloadTicks(1.6, 1, 0.25, false, 1.5));
		assertEquals(16, ShotMath.reloadTicks(1.6, 4, 0.25, false, 1.5));
		// Outlaw: 50% faster
		assertEquals(22, ShotMath.reloadTicks(1.6, 0, 0.25, true, 1.5));
		assertEquals(18, ShotMath.reloadTicks(1.6, 1, 0.25, true, 1.5));
		assertEquals(1, ShotMath.reloadTicks(0.01, 10, 0.25, true, 1.5));
	}
}
