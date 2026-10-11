package io.github.tmldude.blocksouls.bosses.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BossScalingTest {
	@Test
	void healthFollowsThePartyCurve() {
		// Common's party curve: 1 + 0.5 · (players − 1)^0.9.
		assertEquals(1.0, BossScaling.healthMultiplier(1, 1.0), 1e-9);
		assertEquals(1.5, BossScaling.healthMultiplier(2, 1.0), 1e-9);
		assertEquals(1 + 0.5 * Math.pow(3, 0.9), BossScaling.healthMultiplier(4, 1.0), 1e-9);
		assertEquals(1 + 0.5 * Math.pow(7, 0.9), BossScaling.healthMultiplier(8, 1.0), 1e-9);
		// Parties above 8 count as 8.
		assertEquals(BossScaling.healthMultiplier(8, 1.0), BossScaling.healthMultiplier(12, 1.0), 1e-9);
	}

	@Test
	void noPlayersCountsAsOne() {
		assertEquals(1.0, BossScaling.healthMultiplier(0, 1.0), 1e-9);
	}

	@Test
	void difficultyMultipliesHealth() {
		assertEquals(3.0, BossScaling.healthMultiplier(2, 2.0), 1e-9);
		assertEquals(400 * 1.5 * 1.25, 400 * BossScaling.healthMultiplier(2, 1.25), 1e-9);
	}

	@Test
	void damageFollowsDifficultyAndPhase() {
		assertEquals(14.0, BossScaling.damage(14, 1.0, 1.0), 1e-9);
		assertEquals(17.5, BossScaling.damage(14, 1.0, 1.25), 1e-9);
		assertEquals(21.0, BossScaling.damage(14, 1.5, 1.0), 1e-9);
	}

	@Test
	void bigHitsNeedLongWindups() {
		// 30% of 20 health = 6.
		assertTrue(BossScaling.readable(6, 0, 20, 0.3, 15));
		assertFalse(BossScaling.readable(14, 10, 20, 0.3, 15));
		assertTrue(BossScaling.readable(14, 15, 20, 0.3, 15));
	}
}
