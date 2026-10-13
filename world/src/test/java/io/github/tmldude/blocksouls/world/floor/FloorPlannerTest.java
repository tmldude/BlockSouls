package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class FloorPlannerTest {
	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	static FloorTemplate template(String id) {
		FloorTemplate template = new FloorTemplate();
		template.id = id;
		template.nodeMix.put("blocksouls_world:scrap_node", 3);
		template.nodeMix.put("blocksouls_world:gunmetal_node", 1);
		template.slots.add(new FloorTemplate.Slot(FloorSlotKind.CAMP, "", 0, 0, 6));
		template.slots.add(new FloorTemplate.Slot(FloorSlotKind.EXIT_PORTAL, "", 0, -9, 2));
		template.slots.add(new FloorTemplate.Slot(FloorSlotKind.STRUCTURE, "", 30, -18, 7));
		template.slots.add(new FloorTemplate.Slot(FloorSlotKind.SECRET, "", 38, 28, 4));
		template.slots.add(new FloorTemplate.Slot(FloorSlotKind.CAVE, "", 14, 18, 4));
		template.slots.add(new FloorTemplate.Slot(FloorSlotKind.NODE, "blocksouls_world:gunmetal_node", 18, -34, 0));
		template.validate();
		return template;
	}

	@Test
	void soloIslandIsTheTemplateAndEightIsBigger() {
		FloorTemplate template = template("t");
		FloorPlanner.FloorPlan solo = FloorPlanner.plan(template, config, 1, 1, 42);
		FloorPlanner.FloorPlan eight = FloorPlanner.plan(template, config, 1, 8, 42);
		assertEquals(template.radius, solo.radius());
		assertTrue(eight.radius() > solo.radius());
		assertEquals(template.nodes, solo.slots(FloorSlotKind.NODE).size());
		assertTrue(eight.slots(FloorSlotKind.NODE).size() > solo.slots(FloorSlotKind.NODE).size());
		assertEquals(template.crates, solo.slots(FloorSlotKind.CRATE).size());
		assertTrue(eight.slots(FloorSlotKind.CRATE).size() > solo.slots(FloorSlotKind.CRATE).size());
	}

	@Test
	void everySlotIsFilledAndOnTheIsland() {
		for (int players : new int[] {1, 8}) {
			FloorPlanner.FloorPlan plan = FloorPlanner.plan(template("t"), config, 3, players, 7);
			assertEquals(FloorSlotKind.CAMP, plan.slots().getFirst().kind());
			assertEquals(1, plan.slots(FloorSlotKind.EXIT_PORTAL).size());
			assertEquals(config.siegeRingPoints, plan.slots(FloorSlotKind.SIEGE_RING).size());
			assertTrue(config.structures.contains(plan.slots(FloorSlotKind.STRUCTURE).getFirst().tag()));
			assertTrue(config.secrets.contains(plan.slots(FloorSlotKind.SECRET).getFirst().tag()));
			for (FloorPlanner.PlannedSlot slot : plan.slots()) {
				assertTrue(slot.x() * slot.x() + slot.z() * slot.z() <= plan.radius() * plan.radius(), slot.toString());
				if (slot.kind() == FloorSlotKind.NODE) {
					assertFalse(slot.tag().isEmpty());
				}
			}
		}
	}

	@Test
	void planIsDeterministicPerSeed() {
		assertEquals(FloorPlanner.plan(template("t"), config, 2, 4, 99), FloorPlanner.plan(template("t"), config, 2, 4, 99));
	}

	@Test
	void transformRotatesAndMirrors() {
		assertArrayEquals(new int[] {3, 5}, FloorPlanner.transform(3, 5, 1.0, 0, false));
		assertArrayEquals(new int[] {-5, 3}, FloorPlanner.transform(3, 5, 1.0, 1, false));
		assertArrayEquals(new int[] {-3, -5}, FloorPlanner.transform(3, 5, 1.0, 2, false));
		assertArrayEquals(new int[] {5, -3}, FloorPlanner.transform(3, 5, 1.0, 3, false));
		assertArrayEquals(new int[] {-6, 10}, FloorPlanner.transform(3, 5, 2.0, 0, true));
	}

	@Test
	void escalationTagsRampWithTheFloor() {
		assertEquals(List.of("floor_1"), List.copyOf(FloorPlanner.escalationTags(config, 1)));
		assertTrue(FloorPlanner.escalationTags(config, 2).contains("more_elites"));
		assertFalse(FloorPlanner.escalationTags(config, 4).contains("elite_affixes"));
		assertTrue(FloorPlanner.escalationTags(config, 6).containsAll(List.of("more_elites", "stingy_nodes", "mean_mini_boss", "elite_affixes")));
	}

	@Test
	void laterFloorsHaveFewerNodes() {
		assertTrue(FloorPlanner.plan(template("t"), config, 6, 1, 3).slots(FloorSlotKind.NODE).size() < template("t").nodes);
	}

	@Test
	void pickRespectsPoolAndFloorRange() {
		FloorTemplate early = template("a");
		early.maxFloor = 2;
		FloorTemplate late = template("b");
		late.minFloor = 3;
		FloorTemplate other = template("c");
		other.pool = "blocksouls_world:caves";
		List<FloorTemplate> all = List.of(early, late, other);
		assertEquals("a", FloorPlanner.pick(all, "blocksouls_world:surface", 1, 5).id);
		assertEquals("b", FloorPlanner.pick(all, "blocksouls_world:surface", 4, 5).id);
		assertNotNull(FloorPlanner.pick(all, "blocksouls_world:caves", 4, 5));
		assertNull(FloorPlanner.pick(all, "blocksouls_world:nowhere", 1, 5));
	}

	@Test
	void validateRejectsBrokenTemplates() {
		FloorTemplate noPortal = template("t");
		noPortal.slots.removeIf(slot -> slot.kindEnum() == FloorSlotKind.EXIT_PORTAL);
		assertThrows(IllegalArgumentException.class, noPortal::validate);
		FloorTemplate offIsland = template("t");
		offIsland.slots.add(new FloorTemplate.Slot(FloorSlotKind.EVENT, "", 80, 0, 4));
		assertThrows(IllegalArgumentException.class, offIsland::validate);
		FloorTemplate unknown = template("t");
		FloorTemplate.Slot slot = new FloorTemplate.Slot();
		slot.kind = "lava_moat";
		unknown.slots.add(slot);
		assertThrows(IllegalArgumentException.class, unknown::validate);
	}

	@Test
	void shippedSurfaceTemplatesFillEverySlotKind() throws Exception {
		// Gradle runs tests from the module directory; the offline runner from the repo root.
		Path dir = Path.of("src/main/resources/data/blocksouls_world/floor_template");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("world").resolve(dir);
		}
		List<Path> files;
		try (var list = Files.list(dir)) {
			files = list.filter(path -> path.toString().endsWith(".json")).sorted().toList();
		}
		assertTrue(files.size() >= 3, "the Surface pool needs 3+ templates");
		for (Path path : files) {
			try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				FloorTemplate template = FloorTemplate.parse("blocksouls_world:" + path.getFileName(), JsonParser.parseReader(in));
				if (!template.pool.equals("blocksouls_world:surface")) {
					continue; // other floors' pools have their own slot sets and tests
				}
				for (int players : new int[] {1, 8}) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, template.minFloor, players, 11);
					for (FloorSlotKind kind : FloorSlotKind.values()) {
						assertFalse(plan.slots(kind).isEmpty(), path.getFileName() + ": " + kind + " at " + players + " players");
					}
				}
			}
		}
	}
}
