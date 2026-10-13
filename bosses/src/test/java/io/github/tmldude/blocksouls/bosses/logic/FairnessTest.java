package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FairnessTest {
	@Test
	void reactionFloorIsClampedToSixToEightTicks() {
		assertEquals(6, Fairness.reactionTicks(0));
		assertEquals(7, Fairness.reactionTicks(7));
		assertEquals(8, Fairness.reactionTicks(40));
		assertTrue(Fairness.reactionCooldown(0) >= Fairness.REACTION_MIN_COOLDOWN);
	}

	@Test
	void bigHitsNeedALongTelegraph() {
		// > 30% of a 20-health player needs >= 15 ticks of windup.
		assertFalse(Fairness.readable(7, 14, 20));
		assertTrue(Fairness.readable(7, 15, 20));
		assertTrue(Fairness.readable(6, 1, 20), "exactly 30% is not a big hit");
	}

	@Test
	void adaptationNeverRemovesOrDominates() {
		assertEquals(Fairness.ADAPTATION_FLOOR, Fairness.adaptation(0), 1e-9);
		assertEquals(Fairness.ADAPTATION_CAP, Fairness.adaptation(100), 1e-9);
	}
}
