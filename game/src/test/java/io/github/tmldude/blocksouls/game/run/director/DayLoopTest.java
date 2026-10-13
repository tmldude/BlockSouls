package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

	// --- floor events (Stage 6C) ---

	@Test
	void floorTaggedEventsOnlyShowOnTheirFloor() {
		List<DirectorConfig.EventSpec> pool = List.of(new DirectorConfig.EventSpec("blood_altar", 1, 1),
				new DirectorConfig.EventSpec("blocksouls_world:wind_altar", 3, 1, List.of("skyrift"), false));
		// Off Skyrift only the altar qualifies; on Skyrift altar [0, 0.25), wind altar [0.25, 1).
		assertEquals("blood_altar", DirectorRules.pickEvent(pool, 3, Set.of("floor_3"), 0.9));
		assertEquals("blood_altar", DirectorRules.pickEvent(pool, 3, 0.9));
		assertEquals("blocksouls_world:wind_altar", DirectorRules.pickEvent(pool, 3, Set.of("skyrift", "floor_3"), 0.9));
		assertEquals("blood_altar", DirectorRules.pickEvent(pool, 3, Set.of("skyrift"), 0.1));
	}

	@Test
	void aFloorTagPicksItsOwnMiniBossRoster() {
		List<String> roster = List.of("a", "b");
		Map<String, List<String>> byTag = Map.of("skyrift", List.of("roc"), "empty", List.of());
		assertEquals(List.of("roc"), DirectorRules.miniBossRoster(roster, byTag, Set.of("floor_3", "skyrift")));
		assertEquals(roster, DirectorRules.miniBossRoster(roster, byTag, Set.of("infernal")));
		assertEquals(roster, DirectorRules.miniBossRoster(roster, byTag, Set.of("empty")));
		assertEquals(roster, DirectorRules.miniBossRoster(roster, null, Set.of("skyrift")));
	}

	@Test
	void migrationAddsTheFloorEventsToAnOldPool() {
		DirectorConfig old = new DirectorConfig();
		old.defaultsVersion = 1;
		old.events.pool = new java.util.ArrayList<>(List.of(new DirectorConfig.EventSpec("blood_altar", 3, 1)));
		assertEquals(true, DirectorConfig.migrate(old));
		assertEquals(true, old.events.pool.stream().anyMatch(spec -> "blocksouls_world:vault_prison".equals(spec.id)));
		assertEquals(false, old.events.pool.stream().anyMatch(spec -> "cursed_chest".equals(spec.id)), "only floor events are added");
	}
}
