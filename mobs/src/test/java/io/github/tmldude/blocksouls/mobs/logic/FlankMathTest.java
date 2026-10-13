package io.github.tmldude.blocksouls.mobs.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlankMathTest {
	@Test
	void yawMatchesMinecraft() {
		assertEquals(0, FlankMath.yaw(0, 1), 1e-9);
		assertEquals(90, FlankMath.yaw(-1, 0), 1e-9);
		assertEquals(-90, FlankMath.yaw(1, 0), 1e-9);
	}

	@Test
	void bearingIsRelativeToFacing() {
		// Facing +Z; a point at -Z is straight behind.
		assertEquals(180, Math.abs(FlankMath.bearing(0, 0, -1)), 1e-9);
		assertEquals(0, FlankMath.bearing(0, 0, 1), 1e-9);
		// Facing -X (yaw 90); a point at +X is behind.
		assertTrue(FlankMath.behind(FlankMath.bearing(90, 1, 0), 110));
		assertFalse(FlankMath.behind(FlankMath.bearing(90, -1, 0), 110));
	}

	@Test
	void stepsAroundOnItsOwnSide() {
		assertEquals(70, FlankMath.nextBearing(30, 40), 1e-9);
		assertEquals(-70, FlankMath.nextBearing(-30, 40), 1e-9);
		assertEquals(180, FlankMath.nextBearing(170, 40), 1e-9);
	}

	@Test
	void offsetRoundTrips() {
		double[] o = FlankMath.offset(30, 120, 5);
		assertEquals(5, Math.hypot(o[0], o[1]), 1e-9);
		assertEquals(120, FlankMath.bearing(30, o[0], o[1]), 1e-9);
	}

	@Test
	void meanYawOfTheParty() {
		assertEquals(45, FlankMath.meanYaw(new double[] {0, 90}, 7), 1e-9);
		assertEquals(7, FlankMath.meanYaw(new double[] {0, 180}, 7), 1e-9);
		assertEquals(7, FlankMath.meanYaw(new double[0], 7), 1e-9);
	}

	@Test
	void frontArc() {
		assertTrue(FlankMath.inFrontArc(50, 120));
		assertFalse(FlankMath.inFrontArc(70, 120));
		assertFalse(FlankMath.inFrontArc(-170, 120));
	}
}
