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
import java.util.List;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The Infernal pool (v2-floor2-infernal): its templates, lava channels and bridges, at 1 and 8 players. */
class InfernalPlannerTest {
	private static final List<String> INFERNAL = List.of("infernal_caldera", "infernal_forks", "infernal_rift");
	/** A cave's stair and hall reach about this far from its slot (FloorBuilder.cave), and must not cut into lava. */
	private static final int CAVE_REACH = 16;
	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	static FloorTemplate load(String name) throws Exception {
		Path dir = Path.of("src/main/resources/data/blocksouls_world/floor_template");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("world").resolve(dir);
		}
		try (var in = Files.newBufferedReader(dir.resolve(name + ".json"), StandardCharsets.UTF_8)) {
			return FloorTemplate.parse("blocksouls_world:" + name, JsonParser.parseReader(in));
		}
	}

	@Test
	void poolHoldsFloorTwoOnly() throws Exception {
		List<FloorTemplate> templates = new ArrayList<>();
		for (String name : INFERNAL) {
			FloorTemplate template = load(name);
			assertEquals("blocksouls_world:infernal", template.pool, name);
			assertTrue(template.tags.contains("infernal"), name);
			assertEquals(2, template.minFloor, name);
			assertEquals(2, template.maxFloor, name);
			assertNotNull(template.channels, name);
			assertEquals("blocksouls_world:infernal", template.biome, name);
			templates.add(template);
		}
		for (long seed = 0; seed < 20; seed++) {
			assertNotNull(FloorPlanner.pick(templates, "blocksouls_world:infernal", 2, seed));
		}
		assertNull(FloorPlanner.pick(templates, "blocksouls_world:infernal", 1, 0));
		assertNull(FloorPlanner.pick(templates, "blocksouls_world:infernal", 3, 0));
	}

	@Test
	void slotsStayOnTheIslandAndOutOfTheLava() throws Exception {
		for (String name : INFERNAL) {
			FloorTemplate template = load(name);
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < 40; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 2, players, seed);
					LavaChannels.Plan channels = FloorPlanner.channels(template, plan.radius(), seed);
					String at = name + " players=" + players + " seed=" + seed;
					for (FloorPlanner.PlannedSlot slot : plan.slots()) {
						double distance = Math.sqrt(slot.x() * slot.x() + slot.z() * slot.z());
						assertTrue(distance + slot.radius() <= plan.radius() - 2, at + ": " + slot + " off the island");
						double lava = channels.distance(slot.x(), slot.z());
						int clearance = switch (slot.kind()) {
							case CAVE -> CAVE_REACH;
							case SIEGE_RING, NODE, CRATE -> config.slotClearance;
							default -> slot.radius() + 1;
						};
						assertTrue(lava > clearance, at + ": " + slot + " is " + lava + " from the lava");
					}
					// The camp and the exit portal sit on the camp lobe, inside the ring.
					for (FloorSlotKind kind : new FloorSlotKind[] {FloorSlotKind.CAMP, FloorSlotKind.EXIT_PORTAL}) {
						FloorPlanner.PlannedSlot slot = plan.slots(kind).getFirst();
						assertTrue(Math.hypot(slot.x(), slot.z()) + slot.radius() < channels.ringRadius() - channels.halfWidth(), at + ": " + kind);
					}
				}
			}
		}
	}

	@Test
	void channelsMakeThreeToFourLobesAllBridged() throws Exception {
		for (String name : INFERNAL) {
			FloorTemplate template = load(name);
			int lobes = 1 + Math.max(1, template.channels.radials.size());
			assertTrue(lobes >= 3 && lobes <= 4, name + " has " + lobes + " lobes");
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < 8; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 2, players, seed);
					LavaChannels.Plan channels = FloorPlanner.channels(template, plan.radius(), seed);
					String at = name + " players=" + players + " seed=" + seed;
					long highRing = 0;
					for (LavaChannels.Bridge bridge : channels.bridges()) {
						assertTrue(channels.inChannel(bridge.x(), bridge.z()), at + ": bridge over no lava " + bridge);
						// The walkway spans the channel: both ends on dry land.
						double reach = bridge.halfLength() + 0.5;
						assertFalse(channels.inChannel(bridge.x() + bridge.ax() * reach, bridge.z() + bridge.az() * reach), at + ": " + bridge);
						assertFalse(channels.inChannel(bridge.x() - bridge.ax() * reach, bridge.z() - bridge.az() * reach), at + ": " + bridge);
						boolean ring = Math.abs(Math.hypot(bridge.x(), bridge.z()) - channels.ringRadius()) < 0.5;
						if (ring && bridge.high()) {
							highRing++;
						}
						if (!ring) {
							assertTrue(bridge.high(), at + ": radial bridges survive the rise");
						}
					}
					// Exactly one way into the camp once the lava has risen: the siege funnel.
					assertEquals(1, highRing, at);
					// Every outer lobe has a ring bridge into the camp before the rise.
					assertEquals(template.channels.ringBridges.size(), channels.bridges().size() - template.channels.radials.size(), at);
					assertTrue(template.channels.ringBridges.size() >= lobes - 1, at + ": a ring bridge per outer lobe");
				}
			}
		}
	}

	@Test
	void surfaceTemplatesHaveNoChannels() throws Exception {
		for (String name : List.of("surface_crossroads", "surface_ridge", "surface_scrapfield")) {
			FloorTemplate template = load(name);
			assertNull(template.channels, name);
			assertNull(FloorPlanner.channels(template, template.radius, 11), name);
			assertEquals(0, template.spires, name);
			assertTrue(template.biome.isEmpty(), name);
		}
	}

	@Test
	void channelsTurnWithTheSlots() {
		LavaChannels.Spec spec = new LavaChannels.Spec();
		spec.radials.add(0.0);
		spec.ringBridges.add(90.0);
		spec.validate();
		for (int rotation = 0; rotation < 4; rotation++) {
			for (boolean mirrored : new boolean[] {false, true}) {
				LavaChannels.Plan plan = LavaChannels.plan(spec, 60, rotation, mirrored);
				int[] onRadial = FloorPlanner.transform(40, 0, 1, rotation, mirrored);
				int[] offRadial = FloorPlanner.transform(0, 40, 1, rotation, mirrored);
				assertTrue(plan.inChannel(onRadial[0], onRadial[1]), "rotation " + rotation + " mirrored " + mirrored);
				assertFalse(plan.inChannel(offRadial[0], offRadial[1]), "rotation " + rotation + " mirrored " + mirrored);
			}
		}
	}
}
