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
		assertEquals(true, old.events.pool.stream().anyMatch(spec -> "blocksouls_world:wind_altar".equals(spec.id)));
		assertEquals(false, old.events.pool.stream().anyMatch(spec -> "cursed_chest".equals(spec.id)), "only floor events are added");
	}

	// v2-floor5-blight: the Blighted Surface's own events and mini-boss roster, on main's floor-tag API.
	@Test
	void blightRollsOnlyItsOwnEvents() {
		DirectorConfig.Events events = new DirectorConfig().events;
		Set<String> blight = Set.of("blight", "more_elites");
		boolean own = events.ownEventsOnlyFloorTags.stream().anyMatch(blight::contains);
		assertEquals(true, own);
		for (double roll = 0; roll < 1; roll += 0.05) {
			String id = DirectorRules.pickEvent(events.pool, 5, blight, own, roll);
			assertEquals(true, "blood_altar".equals(id) || "challenge_pylon".equals(id), id);
		}
		// Other floors never roll the blight copies: same split as the generic pool.
		List<DirectorConfig.EventSpec> generic = events.pool.stream().filter(spec -> spec.floorTags.isEmpty()).toList();
		for (double roll = 0; roll < 1; roll += 0.05) {
			assertEquals(DirectorRules.pickEvent(generic, 3, roll), DirectorRules.pickEvent(events.pool, 3, Set.of("surface"), false, roll));
		}
		// Without an own-only tag a floor's events join the generic pool (the Vault's way).
		assertEquals(true, DirectorRules.pickEvent(events.pool, 5, Set.of("blight"), false, 0.0) != null);
	}

	@Test
	void blightMiniBossRosterHasBlightheartPaleRiderAndAnEarlierBoss() {
		DirectorConfig.Events events = new DirectorConfig().events;
		for (long seed = 0; seed < 6; seed++) {
			List<String> tagged = DirectorRules.miniBossRoster(events.miniBosses, events.miniBossesByFloorTag, Set.of("blight"));
			List<String> roster = DirectorRules.withEarlier(tagged, events.miniBosses, 5, events.miniBossFromAct, seed);
			assertEquals("blocksouls_bosses:blightheart", roster.get(0));
			assertEquals("blocksouls_bosses:pale_rider", roster.get(1));
			assertEquals(true, roster.size() == 2 || events.miniBosses.contains(roster.get(2)), roster.toString());
			assertEquals(false, roster.contains(DirectorRules.EARLIER));
		}
		List<String> surface = DirectorRules.miniBossRoster(events.miniBosses, events.miniBossesByFloorTag, Set.of("surface"));
		assertEquals(events.miniBosses, DirectorRules.withEarlier(surface, events.miniBosses, 5, 2, 7));
		// Act 2 (the first mini-boss act) has no earlier floor to borrow from.
		assertEquals(List.of("a"), DirectorRules.withEarlier(List.of("a", DirectorRules.EARLIER), events.miniBosses, 2, 2, 7));
	}

	@Test
	void migrationAddsTheBlightCopiesOfGenericEvents() {
		DirectorConfig old = new DirectorConfig();
		old.defaultsVersion = 2;
		old.events.pool = new java.util.ArrayList<>(List.of(new DirectorConfig.EventSpec("blood_altar", 3, 1)));
		assertEquals(true, DirectorConfig.migrate(old));
		assertEquals(true, old.events.pool.stream().anyMatch(spec -> "blood_altar".equals(spec.id) && spec.floorTags.equals(List.of("blight"))));
		assertEquals(1, old.events.pool.stream().filter(spec -> "blood_altar".equals(spec.id) && spec.floorTags.isEmpty()).count());
	}
}
