package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Floor 6: the final arena template plans one fighting ring, clear of the camp and on the island, at 1 and 8 players. */
class ArenaPlannerTest {
	// Mirrors CommonIds (which needs Minecraft on the classpath; unit tests run without it).
	private static final String POOL = "blocksouls_world:arena";
	private static final String TIMELESS = "timeless";
	private static final String ARENA_SLOT = "final_arena";

	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	static FloorTemplate arena() throws Exception {
		Path path = Path.of("src/main/resources/data/blocksouls_world/floor_template/arena_final.json");
		if (!Files.exists(path)) {
			path = Path.of("world").resolve(path);
		}
		try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return FloorTemplate.parse("blocksouls_world:arena_final", JsonParser.parseReader(in));
		}
	}

	@Test
	void templateIsTheArenaPool() throws Exception {
		FloorTemplate template = arena();
		assertEquals(POOL, template.pool);
		assertTrue(template.tags.contains(TIMELESS), "no day on the final floor");
		assertNotNull(FloorPlanner.pick(List.of(template), POOL, 6, 1));
	}

	@Test
	void oneRingClearOfTheCampAtOneAndEight() throws Exception {
		FloorTemplate template = arena();
		for (int players : new int[] {1, 8}) {
			for (long seed = 1; seed <= 10; seed++) {
				FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 6, players, seed);
				List<FloorPlanner.PlannedSlot> rings = plan.slots().stream()
						.filter(s -> s.kind() == FloorSlotKind.STRUCTURE && ARENA_SLOT.equals(s.tag())).toList();
				assertEquals(1, rings.size(), "one arena ring");
				FloorPlanner.PlannedSlot ring = rings.getFirst();
				double fromCamp = Math.hypot(ring.x(), ring.z());
				assertTrue(fromCamp - ring.radius() >= 6, "camp sits outside the ring (" + players + " players)");
				assertTrue(fromCamp + ring.radius() + 1 <= plan.radius(), "ring and wall fit on the island (" + players + " players)");
				assertTrue(plan.tags().contains(TIMELESS));
			}
		}
	}
}
