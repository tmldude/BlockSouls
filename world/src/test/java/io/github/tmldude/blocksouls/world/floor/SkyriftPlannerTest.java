package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** Floor 3 (Skyrift): the shipped pool, its shards, elevated siege points, the open edge and the rim wind. */
class SkyriftPlannerTest {
	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	static List<FloorTemplate> skyrift() throws Exception {
		Path dir = Path.of("src/main/resources/data/blocksouls_world/floor_template");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("world").resolve(dir);
		}
		List<FloorTemplate> out = new ArrayList<>();
		try (var list = Files.list(dir)) {
			for (Path path : list.filter(p -> p.getFileName().toString().startsWith("skyrift_")).sorted().toList()) {
				try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					String name = path.getFileName().toString().replace(".json", "");
					out.add(FloorTemplate.parse("blocksouls_world:" + name, JsonParser.parseReader(in)));
				}
			}
		}
		return out;
	}

	@Test
	void poolHasTwoToThreeFloorThreeTemplates() throws Exception {
		List<FloorTemplate> pool = skyrift();
		assertTrue(pool.size() >= 2 && pool.size() <= 3, "2–3 Skyrift templates, found " + pool.size());
		for (FloorTemplate template : pool) {
			assertEquals("blocksouls_world:skyrift", template.pool);
			assertEquals(3, template.minFloor);
			assertEquals(3, template.maxFloor);
			assertTrue(template.tags.contains("skyrift"));
			assertFalse(template.edgeWall, template.id + " has no wall: the drop is the hazard");
			assertTrue(template.rimWind > 0, template.id + " has rim wind");
			assertEquals("blocksouls_world:skyrift", template.biome);
		}
		assertEquals(null, FloorPlanner.pick(pool, "blocksouls_world:skyrift", 2, 1));
		assertTrue(FloorPlanner.pick(pool, "blocksouls_world:skyrift", 3, 1) != null);
	}

	@Test
	void shardsAreReachableApartAndScaleWithTheParty() throws Exception {
		for (FloorTemplate template : skyrift()) {
			for (long seed = 1; seed <= 20; seed++) {
				FloorPlanner.FloorPlan solo = FloorPlanner.plan(template, config, 3, 1, seed);
				FloorPlanner.FloorPlan eight = FloorPlanner.plan(template, config, 3, 8, seed);
				assertEquals(FloorPlanner.shardCount(template, 1), solo.shards().size(), template.id + " seed " + seed);
				assertEquals(FloorPlanner.shardCount(template, 8), eight.shards().size(), template.id + " seed " + seed);
				for (FloorPlanner.FloorPlan plan : List.of(solo, eight)) {
					checkShards(template, plan);
				}
			}
			int one = FloorPlanner.shardCount(template, 1);
			int eight = FloorPlanner.shardCount(template, 8);
			assertTrue(one >= 4 && eight <= 8 && eight >= one, template.id + ": 4–8 shards, more for 8 players");
		}
	}

	private static void checkShards(FloorTemplate template, FloorPlanner.FloorPlan plan) {
		FloorTemplate.Shards spec = template.shards;
		List<FloorPlanner.Shard> shards = plan.shards();
		for (int i = 0; i < shards.size(); i++) {
			FloorPlanner.Shard shard = shards.get(i);
			double d = Math.sqrt((double) shard.x() * shard.x() + (double) shard.z() * shard.z());
			assertTrue(d - shard.size() - plan.radius() <= spec.maxGap, "shard past the rim within a swing: " + shard);
			assertTrue(shard.rise() >= spec.minRise && shard.rise() <= spec.maxRise, shard.toString());
			for (FloorPlanner.PlannedSlot slot : plan.slots()) {
				if (slot.kind() == FloorSlotKind.CAMP || slot.kind() == FloorSlotKind.STRUCTURE || slot.kind() == FloorSlotKind.EXIT_PORTAL) {
					double gap = Math.hypot(shard.x() - slot.x(), shard.z() - slot.z());
					assertTrue(gap >= shard.size() + slot.radius() + 3, "shard " + shard + " clear of " + slot);
				}
			}
			for (int j = i + 1; j < shards.size(); j++) {
				FloorPlanner.Shard other = shards.get(j);
				assertTrue(Math.hypot(shard.x() - other.x(), shard.z() - other.z()) >= shard.size() + other.size() + 3, "shards apart");
			}
		}
	}

	@Test
	void someSiegePointsSitOnShardsOverTheIsland() throws Exception {
		for (FloorTemplate template : skyrift()) {
			for (int players : new int[] {1, 8}) {
				for (long seed = 1; seed <= 20; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 3, players, seed);
					List<FloorPlanner.PlannedSlot> ring = plan.slots(FloorSlotKind.SIEGE_RING);
					assertEquals(config.siegeRingPoints, ring.size());
					List<FloorPlanner.PlannedSlot> raised = ring.stream().filter(slot -> slot.rise() > 0).toList();
					assertTrue(!raised.isEmpty() && raised.size() <= template.elevatedSiegePoints,
							template.id + " " + players + "p seed " + seed + ": raised " + raised.size());
					for (FloorPlanner.PlannedSlot point : raised) {
						FloorPlanner.Shard shard = plan.shards().stream().filter(s -> s.x() == point.x() && s.z() == point.z()).findFirst()
								.orElseThrow(() -> new AssertionError("raised point not on a shard: " + point));
						assertEquals(shard.rise(), point.rise());
						assertTrue(FloorPlanner.overIsland(shard, plan.radius()), "raised points drop onto the island: " + shard);
					}
					assertEquals(raised.size(), raised.stream().map(p -> p.x() + "," + p.z()).distinct().count(), "one point per shard");
				}
			}
		}
	}

	@Test
	void everyGroundSlotIsOnTheIsland() throws Exception {
		for (FloorTemplate template : skyrift()) {
			for (int players : new int[] {1, 8}) {
				FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 3, players, 5);
				for (FloorSlotKind kind : FloorSlotKind.values()) {
					assertFalse(plan.slots(kind).isEmpty(), template.id + ": " + kind);
				}
				for (FloorPlanner.PlannedSlot slot : plan.slots()) {
					if (slot.rise() == 0) {
						assertTrue(slot.x() * slot.x() + slot.z() * slot.z() <= plan.radius() * plan.radius(), slot.toString());
					}
				}
			}
		}
	}

	@Test
	void skysteelNodesSitOnFreeShardsAndScaleWithTheParty() throws Exception {
		for (FloorTemplate template : skyrift()) {
			assertEquals("blocksouls_world:skysteel_node", template.shards.node);
			assertTrue(FloorPlanner.shardNodeCount(template, 8) > FloorPlanner.shardNodeCount(template, 1));
			for (int players : new int[] {1, 8}) {
				for (long seed = 1; seed <= 20; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 3, players, seed);
					List<FloorPlanner.PlannedSlot> onShards = plan.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.rise() > 0).toList();
					assertTrue(onShards.size() >= Math.min(2, plan.shards().size() - template.elevatedSiegePoints),
							template.id + " " + players + "p seed " + seed + ": " + onShards.size() + " shard nodes");
					assertTrue(onShards.size() <= FloorPlanner.shardNodeCount(template, players));
					for (FloorPlanner.PlannedSlot node : onShards) {
						assertEquals(template.shards.node, node.tag());
						assertTrue(plan.shards().stream().anyMatch(s -> s.x() == node.x() && s.z() == node.z() && s.rise() == node.rise()),
								"on a shard: " + node);
						assertTrue(plan.slots(FloorSlotKind.SIEGE_RING).stream().noneMatch(p -> p.x() == node.x() && p.z() == node.z()),
								"not on a siege shard: " + node);
					}
					long skysteel = plan.slots(FloorSlotKind.NODE).stream().filter(n -> n.tag().equals(template.shards.node)).count();
					assertTrue(onShards.size() * 2 >= skysteel, "skysteel is mostly on the shards");
				}
			}
		}
	}

	@Test
	void twoToThreeCloudbloomsScaledByTheParty() throws Exception {
		for (FloorTemplate template : skyrift()) {
			assertEquals(Map.of("blocksouls_world:cloudbloom_plant", 2), template.herbs);
			int[] expected = {2, 3};
			int[] parties = {1, 8};
			for (int i = 0; i < 2; i++) {
				int players = parties[i];
				FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 3, players, 9);
				List<FloorPlanner.PlannedSlot> herbs = plan.slots(FloorSlotKind.NODE).stream()
						.filter(slot -> slot.tag().equals("blocksouls_world:cloudbloom_plant")).toList();
				assertEquals(expected[i], herbs.size(), template.id + " " + players + "p");
				assertTrue(herbs.stream().allMatch(h -> h.rise() == 0), "herbs grow on the island");
			}
		}
	}

	@Test
	void templatesWithoutShardsPlanExactlyAsBefore() {
		FloorTemplate plain = FloorPlannerTest.template("t");
		FloorPlanner.FloorPlan plan = FloorPlanner.plan(plain, config, 3, 4, 77);
		assertTrue(plan.shards().isEmpty());
		assertTrue(plan.slots().stream().allMatch(slot -> slot.rise() == 0));
		// Shards draw from their own random: every non-ring slot is unchanged when a template gains them.
		FloorTemplate sky = FloorPlannerTest.template("t");
		sky.shards.count = 6;
		sky.elevatedSiegePoints = 3;
		FloorPlanner.FloorPlan withShards = FloorPlanner.plan(sky, config, 3, 4, 77);
		assertEquals(plan.slots().stream().filter(s -> s.kind() != FloorSlotKind.SIEGE_RING).toList(),
				withShards.slots().stream().filter(s -> s.kind() != FloorSlotKind.SIEGE_RING).toList());
		assertFalse(withShards.shards().isEmpty());
	}

	@Test
	void rimWindBlowsOnlyAtTheEdgeAndNotOnTheShards() {
		double full = config.rimWindStrength;
		assertEquals(0, RimWind.strength(config, 1.0, 10, 54, 1, false));
		assertEquals(0, RimWind.strength(config, 1.0, 54 - config.rimWindBand, 54, 1, false));
		double half = RimWind.strength(config, 1.0, 54 - config.rimWindBand / 2.0, 54, 1, false);
		assertEquals(full / 2, half, 1e-9);
		assertEquals(full, RimWind.strength(config, 1.0, 54, 54, 1, false), 1e-9);
		assertEquals(full * 1.3, RimWind.strength(config, 1.3, 60, 54, 0, false), 1e-9);
		assertEquals(0, RimWind.strength(config, 1.0, 54, 54, config.rimWindHeight + 1, false), "calm up on the shards");
		assertEquals(0, RimWind.strength(config, 0.0, 54, 54, 1, false), "no wind on floors without it");
		assertEquals(full * config.rimWindSneakFactor, RimWind.strength(config, 1.0, 54, 54, 1, true), 1e-9);
	}
}
