package io.github.tmldude.blocksouls.world.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
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

/** Floor 4 (The Vault): the box templates, their layout and plan. */
class VaultPlannerTest {
	private static final int SEEDS = 40;
	private final FloorsConfig config = new FloorsConfig();

	@BeforeEach
	void defaults() {
		PartyScaling.setConfig(new PartyConfig());
	}

	static List<FloorTemplate> vaultTemplates() throws Exception {
		Path dir = Path.of("src/main/resources/data/blocksouls_world/floor_template");
		if (!Files.isDirectory(dir)) {
			dir = Path.of("world").resolve(dir);
		}
		List<FloorTemplate> templates = new ArrayList<>();
		try (var list = Files.list(dir)) {
			for (Path path : list.filter(p -> p.getFileName().toString().startsWith("vault_")).sorted().toList()) {
				try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					String name = path.getFileName().toString().replace(".json", "");
					templates.add(FloorTemplate.parse("blocksouls_world:" + name, JsonParser.parseReader(in)));
				}
			}
		}
		return templates;
	}

	@Test
	void thePoolHasTwoToThreeBoxTemplatesForFloorFour() throws Exception {
		List<FloorTemplate> templates = vaultTemplates();
		assertTrue(templates.size() >= 2 && templates.size() <= 3, "2–3 vault templates, found " + templates.size());
		for (FloorTemplate template : templates) {
			assertTrue(template.isBox(), template.id);
			assertEquals("blocksouls_world:vault", template.pool);
			assertTrue(template.tags.contains("vault"));
			assertEquals(4, template.minFloor);
			assertEquals(4, template.maxFloor);
		}
		for (long seed = 0; seed < 10; seed++) {
			FloorTemplate picked = FloorPlanner.pick(templates, "blocksouls_world:vault", 4, seed);
			assertNotNull(picked);
			assertTrue(picked.isBox());
		}
		assertEquals(null, FloorPlanner.pick(templates, "blocksouls_world:vault", 3, 1));
	}

	@Test
	void anIslandTemplateStillParsesAsAnIsland() {
		FloorTemplate island = FloorPlannerTest.template("t");
		assertFalse(island.isBox());
		FloorTemplate bad = FloorPlannerTest.template("t");
		bad.shape = "donut";
		assertThrows(IllegalArgumentException.class, bad::validate);
		FloorTemplate box = FloorPlannerTest.template("b");
		box.shape = FloorTemplate.SHAPE_BOX;
		box.radius = 40;
		box.slots.add(new FloorTemplate.Slot(FloorSlotKind.EVENT, "", 39, 0, 2));
		assertThrows(IllegalArgumentException.class, box::validate, "a box slot must sit inside the walls");
	}

	@Test
	void everySlotIsInsideTheBoxAndWhereItBelongs() throws Exception {
		for (FloorTemplate template : vaultTemplates()) {
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < SEEDS; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, players, seed);
					BoxLayout layout = BoxPlanner.layout(template, plan, seed);
					String at = template.id + " p" + players + " seed " + seed;
					assertEquals(FloorSlotKind.CAMP, plan.slots().getFirst().kind(), at);
					assertEquals(1, plan.slots(FloorSlotKind.EXIT_PORTAL).size(), at);
					assertEquals(BoxPlanner.VAULT_STRUCTURE, plan.slots(FloorSlotKind.STRUCTURE).getFirst().tag(), at);
					assertEquals(BoxPlanner.HOLLOW_WALL, plan.slots(FloorSlotKind.SECRET).getFirst().tag(), at);
					assertTrue(plan.tags().contains("vault") && plan.tags().contains("floor_4"), at);
					for (FloorPlanner.PlannedSlot slot : plan.slots()) {
						assertTrue(Math.abs(slot.x()) < plan.radius() && Math.abs(slot.z()) < plan.radius(), at + " " + slot);
						switch (slot.kind()) {
							case CRATE, SIEGE_RING, CAMP, EXIT_PORTAL, MINI_BOSS, EVENT, STRUCTURE, SECRET ->
									assertTrue(layout.open(slot.x(), slot.z()), at + " " + slot + " stands in the open");
							case NODE -> assertTrue(layout.open(slot.x(), slot.z()) || inWallFace(layout, slot.x(), slot.z()),
									at + " " + slot + " stands in a room or is set into a wall face");
							default -> {
							}
						}
					}
					assertEquals(config.siegeRingPoints, plan.slots(FloorSlotKind.SIEGE_RING).size(), at);
					assertTrue(layout.clear(plan.slots(FloorSlotKind.MINI_BOSS).getFirst().x(), plan.slots(FloorSlotKind.MINI_BOSS).getFirst().z())
							>= template.box.hallHeight, at + " the mini-boss room is a tall hall");
				}
			}
		}
	}

	@Test
	void everyRoomIsReachableAndTheSecretOnlyThroughTheHollowWall() throws Exception {
		for (FloorTemplate template : vaultTemplates()) {
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < SEEDS; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, players, seed);
					BoxLayout layout = BoxPlanner.layout(template, plan, seed);
					String at = template.id + " p" + players + " seed " + seed;
					Set<Long> walk = reach(layout, Set.of());
					for (FloorPlanner.PlannedSlot slot : plan.slots()) {
						if (slot.kind() != FloorSlotKind.SECRET && slot.kind() != FloorSlotKind.NODE) {
							assertTrue(walk.contains(BoxLayout.key(slot.x(), slot.z())), at + " " + slot + " is reachable from the camp");
						}
					}
					FloorPlanner.PlannedSlot secret = plan.slots(FloorSlotKind.SECRET).getFirst();
					assertFalse(walk.contains(BoxLayout.key(secret.x(), secret.z())), at + " the secret is sealed");
					assertTrue(reach(layout, Set.of(BoxLayout.Plug.HOLLOW)).contains(BoxLayout.key(secret.x(), secret.z())),
							at + " breaking the hollow wall opens the secret");
					assertTrue(layout.plugs().containsValue(BoxLayout.Plug.HOLLOW), at);
					for (BoxLayout.Closet closet : layout.closets()) {
						assertFalse(walk.contains(BoxLayout.key(closet.pocketX(), closet.pocketZ())), at + " a closet is sealed");
						assertTrue(reach(layout, Set.of(BoxLayout.Plug.CLOSET)).contains(BoxLayout.key(closet.pocketX(), closet.pocketZ())), at);
					}
					assertTrue(!layout.closets().isEmpty(), at + " the hazard has closets");
				}
			}
		}
	}

	@Test
	void shortcutsAndDeadEndsShowUp() throws Exception {
		int shortcuts = 0;
		int runs = 0;
		for (FloorTemplate template : vaultTemplates()) {
			for (long seed = 0; seed < SEEDS; seed++) {
				FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, 1, seed);
				BoxLayout layout = BoxPlanner.layout(template, plan, seed);
				shortcuts += layout.plugs().containsValue(BoxLayout.Plug.SHORTCUT) ? 1 : 0;
				runs++;
			}
		}
		assertTrue(shortcuts > runs * 3 / 4, "most vaults hide a shortcut: " + shortcuts + "/" + runs);
	}

	@Test
	void eightPlayersGetABiggerVaultMoreNodesAndBiggerClosets() throws Exception {
		FloorTemplate template = vaultTemplates().getFirst();
		FloorPlanner.FloorPlan solo = FloorPlanner.plan(template, config, 4, 1, 5);
		FloorPlanner.FloorPlan eight = FloorPlanner.plan(template, config, 4, 8, 5);
		assertEquals(template.radius, solo.radius());
		assertTrue(eight.radius() > solo.radius());
		assertTrue(eight.slots(FloorSlotKind.NODE).size() > solo.slots(FloorSlotKind.NODE).size());
		assertTrue(eight.slots(FloorSlotKind.CRATE).size() >= solo.slots(FloorSlotKind.CRATE).size());
		VaultConfig vault = new VaultConfig();
		assertEquals(vault.closetSwarm, vault.swarm(PartyScaling.directorBurst(1)));
		assertTrue(vault.swarm(PartyScaling.directorBurst(8)) > vault.swarm(PartyScaling.directorBurst(1)));
	}

	@Test
	void deepIronSitsInTheWallsAndCaveMossInTheRoomsAtOneAndEightPlayers() throws Exception {
		for (FloorTemplate template : vaultTemplates()) {
			assertEquals(2, BoxPlanner.herbCount(template, 1), template.id);
			assertTrue(BoxPlanner.herbCount(template, 8) >= BoxPlanner.herbCount(template, 1) && BoxPlanner.herbCount(template, 8) <= 3, template.id);
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < SEEDS; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, players, seed);
					BoxLayout layout = BoxPlanner.layout(template, plan, seed);
					String at = template.id + " p" + players + " seed " + seed;
					int herbs = 0;
					int deepIron = 0;
					for (FloorPlanner.PlannedSlot node : plan.slots(FloorSlotKind.NODE)) {
						if (node.tag().equals(template.box.herb)) {
							herbs++;
							assertTrue(layout.open(node.x(), node.z()), at + " herb " + node + " stands in a room");
						} else if (node.tag().equals(template.box.wallNode)) {
							deepIron++;
							assertTrue(!layout.open(node.x(), node.z()) && inWallFace(layout, node.x(), node.z()), at + " deep iron " + node + " is in a wall face");
						} else {
							assertTrue(layout.open(node.x(), node.z()), at + " " + node);
						}
					}
					assertEquals(BoxPlanner.herbCount(template, players), herbs, at + " herbs");
					assertTrue(deepIron > 0, at + " the walls hide deep iron");
				}
			}
		}
	}

	// ---- PR 3: the vault room's keys and the secret stair

	@Test
	void threeKeysSitInThreeOtherRoomsAndNothingIsPlannedInTheStrongroomAtOneAndEightPlayers() throws Exception {
		for (FloorTemplate template : vaultTemplates()) {
			assertEquals(3, template.box.keys, template.id);
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < SEEDS; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, players, seed);
					BoxLayout layout = BoxPlanner.layout(template, plan, seed);
					String at = template.id + " p" + players + " seed " + seed;
					Set<Long> walk = reach(layout, Set.of());
					Set<BoxLayout.Room> keyRooms = new HashSet<>();
					for (FloorPlanner.PlannedSlot key : plan.slots(FloorSlotKind.CRATE)) {
						if (!key.tag().equals(BoxPlanner.VAULT_KEY)) {
							continue;
						}
						assertTrue(walk.contains(BoxLayout.key(key.x(), key.z())), at + " key " + key + " is reachable without breaking anything");
						BoxLayout.Room room = layout.rooms().stream().filter(r -> r.contains(key.x(), key.z())).findFirst().orElse(null);
						assertNotNull(room, at + " key " + key + " stands in a room");
						assertTrue(Set.of("filler", "event", "mini_boss").contains(room.role()), at + " key in a " + room.role() + " room");
						assertTrue(keyRooms.add(room), at + " one key per room");
					}
					assertEquals(template.box.keys, keyRooms.size(), at + " keys");
					for (FloorPlanner.PlannedSlot slot : plan.slots()) {
						if (slot.kind() == FloorSlotKind.NODE || slot.kind() == FloorSlotKind.CRATE) {
							assertFalse(BoxPlanner.inStrongroom(slot.x(), slot.z(), plan.slots()), at + " " + slot + " is in the strongroom");
						}
					}
				}
			}
		}
	}

	@Test
	void theHiddenStairClimbsFromTheSecretRoomToAChamberAboveEveryCeilingAtOneAndEightPlayers() throws Exception {
		for (FloorTemplate template : vaultTemplates()) {
			for (int players : new int[] {1, 8}) {
				for (long seed = 0; seed < SEEDS; seed++) {
					FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, players, seed);
					BoxLayout layout = BoxPlanner.layout(template, plan, seed);
					String at = template.id + " p" + players + " seed " + seed;
					VaultSecret stair = VaultSecret.plan(layout, template.box, template.height);
					assertNotNull(stair, at);
					BoxLayout.Room room = stair.room();
					for (BoxLayout.Room other : layout.rooms()) {
						assertTrue(stair.floorDy() > other.height() + 1, at + " the chamber floor is over " + other.role() + "'s ceiling");
					}
					assertTrue(stair.chamberHeight() >= VaultSecret.MIN_CHAMBER && stair.floorDy() + stair.chamberHeight() < template.height,
							at + " the chamber fits under the lid");
					for (int x = stair.shaftX() - 1; x <= stair.shaftX() + 1; x++) {
						for (int z = stair.shaftZ() - 1; z <= stair.shaftZ() + 1; z++) {
							assertTrue(x > room.x0() && x < room.x1() && z > room.z0() && z < room.z1() && layout.open(x, z), at + " shaft in the room");
						}
					}
					int[] first = stair.step(1);
					Set<Long> opened = reach(layout, Set.of(BoxLayout.Plug.HOLLOW));
					boolean approach = false;
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						approach |= !stair.inShaft(first[0] + d[0], first[2] + d[1]) && opened.contains(BoxLayout.key(first[0] + d[0], first[2] + d[1]));
					}
					assertTrue(approach, at + " the first step is reached from the room floor once the hollow wall is down");
					for (int k = 1; k <= stair.steps(); k++) {
						int[] step = stair.step(k);
						assertEquals(k, step[1]);
						assertTrue(stair.inShaft(step[0], step[2]) && (step[0] != stair.shaftX() || step[2] != stair.shaftZ()), at + " step " + k);
						if (k > 1) {
							int[] last = stair.step(k - 1);
							assertEquals(1, Math.abs(step[0] - last[0]) + Math.abs(step[2] - last[2]), at + " step " + k + " is one across");
						}
						if (k + 8 <= stair.steps()) {
							int[] above = stair.step(k + 8);
							assertTrue(above[0] == step[0] && above[2] == step[2], at + " the step over step " + k + " is eight up");
						}
					}
					assertEquals(stair.floorDy(), stair.step(stair.steps())[1], at + " the last step is flush with the chamber floor");
					int[] boss = stair.bossSpot();
					assertTrue(room.contains(boss[0], boss[2]) && !stair.inShaft(boss[0], boss[2]) && boss[1] == stair.floorDy() + 1, at);
				}
			}
		}
	}

	@Test
	void theLayoutIsTheSameForTheSamePlanAndSeed() throws Exception {
		FloorTemplate template = vaultTemplates().getFirst();
		FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, 4, 3, 77);
		assertEquals(plan, FloorPlanner.plan(template, config, 4, 3, 77));
		BoxLayout a = BoxPlanner.layout(template, plan, 77);
		BoxLayout b = BoxPlanner.layout(template, plan, 77);
		for (int x = -plan.radius(); x <= plan.radius(); x++) {
			for (int z = -plan.radius(); z <= plan.radius(); z++) {
				assertEquals(a.clear(x, z), b.clear(x, z));
				assertEquals(a.plug(x, z), b.plug(x, z));
			}
		}
	}

	private static boolean inWallFace(BoxLayout layout, int x, int z) {
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (layout.open(x + d[0], z + d[1]) && !layout.open(x - d[0], z - d[1])) {
				return true;
			}
		}
		return false;
	}

	/** Columns reachable from the camp (0, 0) through open columns, plus plugs of the given kinds treated as broken. */
	private static Set<Long> reach(BoxLayout layout, Set<BoxLayout.Plug> broken) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {0, 0});
		seen.add(BoxLayout.key(0, 0));
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int x = at[0] + d[0];
				int z = at[1] + d[1];
				BoxLayout.Plug plug = layout.plug(x, z);
				boolean passable = layout.clear(x, z) >= 2 || plug != null && broken.contains(plug);
				if (passable && seen.add(BoxLayout.key(x, z))) {
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen;
	}
}
