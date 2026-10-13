package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Floor 5's Blighted Surface pool (v2-floor5-blight): the shipped templates, their pool and their look. */
class BlightTemplatesTest {
	private static final String POOL = "blocksouls_world:blight";
	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	static List<FloorTemplate> shipped() throws Exception {
		Path dir = Path.of("src/main/resources/data/blocksouls_world/floor_template");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("world").resolve(dir);
		}
		List<FloorTemplate> templates = new ArrayList<>();
		try (var list = Files.list(dir)) {
			for (Path path : list.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					String name = path.getFileName().toString();
					templates.add(FloorTemplate.parse("blocksouls_world:" + name.substring(0, name.length() - 5), JsonParser.parseReader(in)));
				}
			}
		}
		return templates;
	}

	private static List<FloorTemplate> blight() throws Exception {
		return shipped().stream().filter(t -> POOL.equals(t.pool)).toList();
	}

	@Test
	void threeBlightTemplatesForFloorFiveOnly() throws Exception {
		List<FloorTemplate> blight = blight();
		assertEquals(3, blight.size());
		for (FloorTemplate template : blight) {
			assertEquals(5, template.minFloor, template.id);
			assertEquals(5, template.maxFloor, template.id);
			assertTrue(template.tags.containsAll(List.of("blight", "more_elites", "elite_affixes", "mean_mini_boss", "stingy_nodes")), template.id);
			assertEquals("blocksouls_world:blight", template.biome, template.id);
			assertNotNull(template.decor, template.id);
			assertEquals("dead", template.decor.trees, template.id);
			assertFalse(template.terrain.topMix.isEmpty(), template.id);
		}
	}

	@Test
	void poolPickIsBlightOnFloorFiveAndNeverLeaksIntoSurface() throws Exception {
		List<FloorTemplate> all = shipped();
		for (long seed = 0; seed < 40; seed++) {
			FloorTemplate picked = FloorPlanner.pick(all, POOL, 5, seed);
			assertNotNull(picked);
			assertEquals(POOL, picked.pool);
			FloorTemplate surface = FloorPlanner.pick(all, "blocksouls_world:surface", 5, seed);
			assertNotNull(surface);
			assertFalse(surface.tags.contains("blight"), surface.id);
		}
		assertNull(FloorPlanner.pick(all, POOL, 4, 1));
		assertNull(FloorPlanner.pick(all, POOL, 6, 1));
	}

	@Test
	void slotsStayOnTheIslandAtOneAndEightPlayers() throws Exception {
		for (FloorTemplate template : blight()) {
			for (int players : new int[] {1, 8}) {
				FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 5, players, 99);
				for (FloorSlotKind kind : FloorSlotKind.values()) {
					assertFalse(plan.slots(kind).isEmpty(), template.id + ": " + kind + " at " + players);
				}
				for (FloorPlanner.PlannedSlot slot : plan.slots()) {
					assertTrue((long) slot.x() * slot.x() + (long) slot.z() * slot.z() <= (long) plan.radius() * plan.radius(), template.id + " " + slot);
				}
				assertTrue(plan.tags().contains("blight"));
				assertTrue(plan.tags().contains("floor_5"));
				// The broken outpost (the nest-to-be) is the floor's structure.
				assertEquals("outpost", plan.slots(FloorSlotKind.STRUCTURE).getFirst().tag(), template.id);
			}
			assertTrue(FloorPlanner.plan(template, config, 5, 8, 99).radius() > FloorPlanner.plan(template, config, 5, 1, 99).radius());
		}
	}

	@Test
	void fieldBossesHaveRoomAwayFromTheCamp() throws Exception {
		// Roaming bosses spawn 24+ blocks from the camp, inside 85% of the island: the solo island must leave a ring.
		for (FloorTemplate template : blight()) {
			assertTrue(template.radius * 0.85 > 24 + 8, template.id);
		}
	}

	@Test
	void topMixIsDeterministicAndOnlyUsesItsBlocks() throws Exception {
		FloorTemplate template = blight().getFirst();
		Set<String> seen = new HashSet<>();
		for (int x = -40; x <= 40; x++) {
			for (int z = -40; z <= 40; z++) {
				String top = template.terrain.topAt(x, z);
				assertTrue(template.terrain.topMix.containsKey(top), top);
				assertEquals(top, template.terrain.topAt(x, z));
				seen.add(top);
			}
		}
		assertEquals(template.terrain.topMix.keySet(), seen, "every mixed block shows up somewhere");
		FloorTemplate plain = FloorPlannerTest.template("plain");
		assertEquals(plain.terrain.top, plain.terrain.topAt(3, -7));
	}
}
