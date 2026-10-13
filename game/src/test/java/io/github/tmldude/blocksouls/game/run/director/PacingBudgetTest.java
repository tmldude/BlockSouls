package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PacingBudgetTest {
	@Test
	void ceilingIsHitAfterNinetyQuietSeconds() {
		PacingBudget budget = new PacingBudget(100);
		assertFalse(budget.spent(189, 90));
		assertTrue(budget.spent(190, 90));
		budget.beat(150, "threat");
		assertFalse(budget.spent(190, 90));
		assertEquals(40, budget.quietSeconds(190));
		assertEquals("threat", budget.lastReason());
	}

	@Test
	void beatsNeverGoBackInTime() {
		PacingBudget budget = new PacingBudget(100);
		budget.beat(50, "old");
		assertEquals(0, budget.quietSeconds(100));
		assertEquals("start", budget.lastReason());
	}

	@Test
	void breaksTightenNearTheCeiling() {
		assertEquals(8, PacingBudget.tightenBreak(10, 8, 90, 3));
		assertEquals(3, PacingBudget.tightenBreak(85, 8, 90, 3));
		assertEquals(2, PacingBudget.tightenBreak(85, 2, 90, 3), "never lengthens a break");
	}

	@Test
	void lullsAreClamped() {
		assertEquals(20, PacingBudget.clampLull(60, 20));
		assertEquals(12, PacingBudget.clampLull(12, 20));
		assertEquals(0, PacingBudget.clampLull(-5, 20));
	}
}
