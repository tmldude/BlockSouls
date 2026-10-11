package io.github.tmldude.blocksouls.world.enchant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WackyMathTest {
	@Test
	void bounceMirrorsTheHitAxisAndDamps() {
		assertArrayEquals(new double[] {0.5, -1.0, 0.25}, WackyMath.reflect(new double[] {1, 2, 0.5}, 1, 0.5), 1e-9);
		assertArrayEquals(new double[] {-1, 0, 0}, WackyMath.reflect(new double[] {1, 0, 0}, 0, 1.0), 1e-9);
	}

	@Test
	void grapplePullsTowardTargetWithLift() {
		double[] v = WackyMath.grapplePull(new double[] {0, 64, 0}, new double[] {20, 64, 0}, 1.6, 30);
		assertEquals(1.6, v[0], 1e-9);
		assertEquals(0.25, v[1], 1e-9);
		assertEquals(0.0, v[2], 1e-9);
	}

	@Test
	void grappleSlowsForShortPullsAndRespectsRange() {
		double[] near = WackyMath.grapplePull(new double[] {0, 0, 0}, new double[] {2, 0, 0}, 1.6, 30);
		assertEquals(1.6 * 0.4, near[0], 1e-9);
		assertNull(WackyMath.grapplePull(new double[] {0, 0, 0}, new double[] {31, 0, 0}, 1.6, 30));
	}

	@Test
	void magnetRadiusByLevel() {
		assertEquals(0, WackyMath.magnetRadius(0, 6, 10));
		assertEquals(6, WackyMath.magnetRadius(1, 6, 10));
		assertEquals(10, WackyMath.magnetRadius(2, 6, 10));
	}

	@Test
	void magnetPullIsCappedAndAimed() {
		double[] v = WackyMath.magnetPull(new double[] {10, 0, 0}, new double[] {0, 0, 0}, 0.35);
		assertArrayEquals(new double[] {-0.35, 0, 0}, v, 1e-9);
		double[] close = WackyMath.magnetPull(new double[] {0.2, 0, 0}, new double[] {0, 0, 0}, 0.35);
		assertArrayEquals(new double[] {0, 0, 0}, close, 1e-9);
	}

	@Test
	void vampiricHealsOneHeartPerLevel() {
		assertEquals(6.0F, WackyMath.vampiricHeal(3, 2.0F));
		assertEquals(0.0F, WackyMath.vampiricHeal(0, 2.0F));
		assertTrue(WackyMath.vampiricHeal(-1, 2.0F) >= 0);
	}
}
