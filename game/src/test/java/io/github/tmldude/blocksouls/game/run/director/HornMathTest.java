package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HornMathTest {
	private final DirectorConfig.Horn horn = new DirectorConfig.Horn();

	@Test
	void earlierHornPaysMore() {
		HornMath.Buff late = HornMath.buff(30, 480, horn);
		HornMath.Buff mid = HornMath.buff(180, 480, horn);
		HornMath.Buff early = HornMath.buff(400, 480, horn);
		assertEquals(1, late.souls());
		assertEquals(0, late.tierShift());
		assertFalse(late.freeRelic());
		assertEquals(9, mid.souls());
		assertEquals(1, mid.tierShift());
		assertFalse(mid.freeRelic());
		assertEquals(20, early.souls());
		assertTrue(early.freeRelic());
	}

	@Test
	void skippedIsClampedToTheDay() {
		HornMath.Buff buff = HornMath.buff(9999, 480, horn);
		assertEquals(1.0, buff.skippedShare(), 1e-9);
		assertEquals(0, HornMath.buff(-5, 480, horn).souls());
		assertEquals(0, HornMath.buff(10, 0, horn).skippedShare(), 1e-9);
	}
}
