package io.github.tmldude.blocksouls.bosses.palerider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.bosses.palerider.PaleRiderMath.LanceStep;

class PaleRiderMathTest {
	@Test
	void lanceStepsRunInOrder() {
		assertEquals(LanceStep.FIRST_PASS, PaleRiderMath.lanceStep(0, 16, 24));
		assertEquals(LanceStep.FIRST_PASS, PaleRiderMath.lanceStep(15, 16, 24));
		assertEquals(LanceStep.TURN, PaleRiderMath.lanceStep(16, 16, 24));
		assertEquals(LanceStep.TURN, PaleRiderMath.lanceStep(39, 16, 24));
		assertEquals(LanceStep.SECOND_PASS, PaleRiderMath.lanceStep(40, 16, 24));
		assertEquals(LanceStep.SECOND_PASS, PaleRiderMath.lanceStep(55, 16, 24));
		assertEquals(LanceStep.DONE, PaleRiderMath.lanceStep(56, 16, 24));
	}

	@Test
	void defaultLanceActiveStageFitsBothPasses() {
		PaleRiderConfig config = new PaleRiderConfig();
		assertEquals(PaleRiderMath.lanceActiveTicks(config.lancePassTicks, config.lanceTurnTicks), config.lance.activeTicks);
	}

	@Test
	void everyHitIsTelegraphedForAtLeastThreeQuartersOfASecond() {
		PaleRiderConfig config = new PaleRiderConfig();
		// The return pass of the lance and the phantom charge are telegraphed outside an attack's windup.
		assertTrue(config.lanceTurnTicks >= 15, "lance return lane");
		assertTrue(config.phantomChargeWindupTicks >= 15, "phantom charge lane");
		assertTrue(config.blink.windupTicks >= 15, "blink shimmer");
	}

	@Test
	void fanIsCenteredOnTheAim() {
		double[][] fan = PaleRiderMath.fan(1, 0, 3, 20);
		assertEquals(3, fan.length);
		assertArrayEquals(new double[] {1, 0}, fan[1], 1e-9);
		assertEquals(Math.cos(Math.toRadians(-20)), fan[0][0], 1e-9);
		assertEquals(Math.sin(Math.toRadians(-20)), fan[0][1], 1e-9);
		assertEquals(Math.sin(Math.toRadians(20)), fan[2][1], 1e-9);
		for (double[] dir : fan) {
			assertEquals(1, Math.hypot(dir[0], dir[1]), 1e-9);
		}
	}

	@Test
	void evenFanStraddlesTheAim() {
		double[][] fan = PaleRiderMath.fan(0, 1, 2, 30);
		assertEquals(-fan[0][0], fan[1][0], 1e-9);
		assertEquals(fan[0][1], fan[1][1], 1e-9);
	}

	@Test
	void behindIsOnTheFarSideOfTheTarget() {
		assertArrayEquals(new double[] {12, 0}, PaleRiderMath.behind(0, 0, 10, 0, 2), 1e-9);
		assertArrayEquals(new double[] {-3, -5}, PaleRiderMath.behind(-3, 5, -3, -3, 2), 1e-9);
		// Standing on the target: still a valid spot.
		double[] same = PaleRiderMath.behind(1, 1, 1, 1, 2);
		assertEquals(2, Math.hypot(same[0] - 1, same[1] - 1), 1e-9);
	}

	@Test
	void phantomChargeComesEveryInterval() {
		assertFalse(PaleRiderMath.phantomChargeDue(0, 400));
		assertTrue(PaleRiderMath.phantomChargeDue(400, 400));
		assertFalse(PaleRiderMath.phantomChargeDue(401, 400));
		assertTrue(PaleRiderMath.phantomChargeDue(800, 400));
		assertFalse(PaleRiderMath.phantomChargeDue(400, 0));
	}
}
