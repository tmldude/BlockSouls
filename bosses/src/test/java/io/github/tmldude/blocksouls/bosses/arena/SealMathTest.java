package io.github.tmldude.blocksouls.bosses.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.bosses.arena.SealMath.Verdict;

class SealMathTest {
	@Test
	void cylinderIsUnlimitedByDefault() {
		assertTrue(SealMath.inside(3, 0, 0, 4, 0));
		assertTrue(SealMath.inside(3, 300, 0, 4, 0), "unlimited height: far above is still inside");
		assertTrue(SealMath.inside(0, -60, 3, 4, 0));
		assertFalse(SealMath.inside(4, 0, 1, 4, 0));
	}

	@Test
	void sealHeightCapsTheCylinder() {
		assertTrue(SealMath.inside(1, 20, 1, 4, 20));
		assertFalse(SealMath.inside(1, 21, 1, 4, 20));
		assertFalse(SealMath.inside(1, -21, 1, 4, 20));
	}

	@Test
	void participantsComeInButNeverLeave() {
		assertEquals(Verdict.KEEP_IN, SealMath.judge(true, false, true, 1, 64));
		assertEquals(Verdict.ALLOW, SealMath.judge(false, true, true, 1, 64), "a participant who was outside may come in");
		assertEquals(Verdict.ALLOW, SealMath.judge(true, true, true, 1, 64));
	}

	@Test
	void outsidersLeaveButNeverComeIn() {
		assertEquals(Verdict.KEEP_OUT, SealMath.judge(false, true, false, 1, 64));
		assertEquals(Verdict.ALLOW, SealMath.judge(true, false, false, 1, 64));
		assertEquals(Verdict.ALLOW, SealMath.judge(false, false, false, 1, 64));
	}

	@Test
	void serverTeleportsAreLeftAlone() {
		assertEquals(Verdict.ALLOW, SealMath.judge(true, false, true, 200, 64));
		assertEquals(Verdict.ALLOW, SealMath.judge(false, true, false, 200, 64));
	}

	@Test
	void ropeCrossesWhenEndsAreOnDifferentSides() {
		assertTrue(SealMath.crosses(true, false));
		assertTrue(SealMath.crosses(false, true));
		assertFalse(SealMath.crosses(true, true));
	}

	@Test
	void crossingPointIsOnTheCircle() {
		double t = SealMath.crossingFraction(0, 0, 10, 0, 4);
		assertEquals(0.4, t, 1e-9);
		assertEquals(0.6, SealMath.crossingFraction(10, 0, 0, 0, 4), 1e-9);
		assertEquals(0.5, SealMath.crossingFraction(1, 1, 1, 1, 4), 1e-9, "no horizontal move");
		assertEquals(0.5, SealMath.crossingFraction(0, 0, 1, 0, 4), 1e-9, "never reaches the circle");
	}

	@Test
	void pushPointsInOrOut() {
		double[] in = SealMath.push(3, 4, true, 0.5);
		assertEquals(-0.3, in[0], 1e-9);
		assertEquals(-0.4, in[1], 1e-9);
		double[] out = SealMath.push(3, 4, false, 0.5);
		assertEquals(0.3, out[0], 1e-9);
		assertEquals(0.4, out[1], 1e-9);
		double[] center = SealMath.push(0, 0, true, 0.5);
		assertEquals(0, center[0]);
		assertEquals(0, center[1]);
	}
}
