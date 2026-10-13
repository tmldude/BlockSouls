package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class DayLoopTest {
	private static final List<DirectorConfig.EventSpec> POOL = List.of(new DirectorConfig.EventSpec("blood_altar", 1, 1),
			new DirectorConfig.EventSpec("cursed_chest", 3, 1), new DirectorConfig.EventSpec("challenge_pylon", 1, 3));

	@Test
	void pickEventIsWeightedAndSkipsLockedEvents() {
		// Act 1: altar [0, 0.25), chest [0.25, 1); the pylon isn't unlocked.
		assertEquals("blood_altar", DirectorRules.pickEvent(POOL, 1, 0.1));
		assertEquals("cursed_chest", DirectorRules.pickEvent(POOL, 1, 0.3));
		assertEquals("cursed_chest", DirectorRules.pickEvent(POOL, 1, 0.99));
		// Act 3: altar [0, 0.2), chest [0.2, 0.8), pylon [0.8, 1).
		assertEquals("challenge_pylon", DirectorRules.pickEvent(POOL, 3, 0.9));
	}

	@Test
	void pickEventWithNothingEligibleIsNull() {
		assertNull(DirectorRules.pickEvent(List.of(new DirectorConfig.EventSpec("challenge_pylon", 1, 3)), 1, 0.5));
		assertNull(DirectorRules.pickEvent(List.of(new DirectorConfig.EventSpec("blood_altar", 0, 1)), 1, 0.5));
	}

	@Test
	void everyDefaultEventHasAPositiveWeight() {
		for (DirectorConfig.EventSpec spec : new DirectorConfig().events.pool) {
			assertEquals(true, spec.weight > 0 && spec.minAct >= 1, spec.id);
		}
	}
}
