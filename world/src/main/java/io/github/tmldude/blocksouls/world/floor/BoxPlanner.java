package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * The planner for box-shaped templates (floor 4, The Vault). Same contract as the island planner (scale for the party,
 * quarter turn and mirror, escalation tags), but nodes, crates and the siege points go where the {@link BoxLayout} is
 * open: nodes stand in rooms or sit set into a corridor's wall face, crates stand in filler rooms, and the
 * siege points are the corridor cells farthest from the camp, so waves come at the camp down the corridors.
 */
public final class BoxPlanner {
	/** Default tags for a box's empty structure and secret slots. */
	public static final String VAULT_STRUCTURE = "vault";
	public static final String HOLLOW_WALL = "hollow_wall";

	private BoxPlanner() {
	}

	/** The seed the layout is generated from (the builder regenerates it from the same plan and seed). */
	public static long layoutSeed(long seed) {
		return seed * 0x9E3779B97F4A7C15L + 0x51ED27L;
	}

	/** The plan's room slots in plan order: the layout's input. */
	public static List<FloorPlanner.PlannedSlot> roomSlots(List<FloorPlanner.PlannedSlot> slots) {
		return slots.stream().filter(slot -> BoxLayout.roomKind(slot.kind())).toList();
	}

	public static BoxLayout layout(FloorTemplate template, FloorPlanner.FloorPlan plan, long seed) {
		return BoxLayout.generate(plan.radius(), template.box, roomSlots(plan.slots()), layoutSeed(seed));
	}

	public static FloorPlanner.FloorPlan plan(FloorTemplate template, FloorsConfig config, int floorNumber, int players, long seed) {
		Random random = new Random(seed);
		double scale = PartyScaling.floorSize(Math.max(1, players));
		int radius = (int) Math.round(template.radius * scale);
		int rotation = random.nextInt(4);
		boolean mirrored = random.nextBoolean();

		List<FloorPlanner.PlannedSlot> slots = new ArrayList<>();
		List<FloorTemplate.Slot> nodeSlots = new ArrayList<>();
		for (FloorTemplate.Slot slot : template.slots) {
			FloorSlotKind kind = slot.kindEnum();
			if (kind == FloorSlotKind.NODE) {
				nodeSlots.add(slot);
				continue;
			}
			if (kind == FloorSlotKind.SIEGE_RING || kind == FloorSlotKind.CRATE) {
				continue;
			}
			int[] at = FloorPlanner.transform(slot.x, slot.z, scale, rotation, mirrored);
			String tag = slot.tag;
			if (tag.isEmpty() && kind == FloorSlotKind.STRUCTURE) {
				tag = VAULT_STRUCTURE;
			} else if (tag.isEmpty() && kind == FloorSlotKind.SECRET) {
				tag = HOLLOW_WALL;
			}
			slots.add(new FloorPlanner.PlannedSlot(kind, tag, at[0], at[1], slot.radius));
		}
		slots.sort((a, b) -> Integer.compare(order(a.kind()), order(b.kind())));
		BoxLayout layout = BoxLayout.generate(radius, template.box, slots, layoutSeed(seed));

		int nodeCount = Math.max(1, (int) Math.round(template.nodes * PartyScaling.floorNodes(Math.max(1, players)) * config.nodeFactor(floorNumber)));
		List<FloorPlanner.PlannedSlot> nodes = new ArrayList<>();
		List<FloorTemplate.Slot> shuffled = new ArrayList<>(nodeSlots);
		Collections.shuffle(shuffled, random);
		for (FloorTemplate.Slot slot : shuffled) {
			if (nodes.size() >= nodeCount) {
				break;
			}
			int[] at = FloorPlanner.transform(slot.x, slot.z, scale, rotation, mirrored);
			String tag = slot.tag.isEmpty() ? FloorPlanner.weighted(template.nodeMix, random) : slot.tag;
			if (!layout.open(at[0], at[1])) {
				continue; // the layout put rock there: a generated spot takes its place
			}
			nodes.add(new FloorPlanner.PlannedSlot(FloorSlotKind.NODE, tag, at[0], at[1], 0));
		}
		int buried = (int) Math.round((nodeCount - nodes.size()) * Math.clamp(template.box.wallNodeShare, 0.0, 1.0));
		List<int[]> wall = wallSpots(layout, random);
		Set<Long> taken = new LinkedHashSet<>();
		nodes.forEach(node -> taken.add(BoxLayout.key(node.x(), node.z())));
		for (int[] spot : wall) {
			if (buried <= 0 || nodes.size() >= nodeCount) {
				break;
			}
			if (spaced(spot, taken, config.nodeSpacing)) {
				nodes.add(new FloorPlanner.PlannedSlot(FloorSlotKind.NODE, FloorPlanner.weighted(template.nodeMix, random), spot[0], spot[1], 0));
				taken.add(BoxLayout.key(spot[0], spot[1]));
				buried--;
			}
		}
		List<int[]> floor = roomSpots(layout, slots, config.slotClearance, random);
		for (int[] spot : floor) {
			if (nodes.size() >= nodeCount) {
				break;
			}
			if (spaced(spot, taken, config.nodeSpacing)) {
				nodes.add(new FloorPlanner.PlannedSlot(FloorSlotKind.NODE, FloorPlanner.weighted(template.nodeMix, random), spot[0], spot[1], 0));
				taken.add(BoxLayout.key(spot[0], spot[1]));
			}
		}
		slots.addAll(nodes);

		int crateCount = (int) Math.round(template.crates * PartyScaling.floorCrates(Math.max(1, players)));
		int crates = 0;
		for (int[] spot : floor) {
			if (crates >= crateCount) {
				break;
			}
			if (spaced(spot, taken, config.nodeSpacing)) {
				slots.add(new FloorPlanner.PlannedSlot(FloorSlotKind.CRATE, "", spot[0], spot[1], 0));
				taken.add(BoxLayout.key(spot[0], spot[1]));
				crates++;
			}
		}

		int index = 0;
		for (int[] point : siegePoints(layout, config.siegeRingPoints)) {
			slots.add(new FloorPlanner.PlannedSlot(FloorSlotKind.SIEGE_RING, Integer.toString(index++), point[0], point[1], 0));
		}

		Set<String> tags = new LinkedHashSet<>(template.tags);
		tags.addAll(FloorPlanner.escalationTags(config, floorNumber));
		return new FloorPlanner.FloorPlan(template.id, radius, template.groundY, rotation, mirrored, tags, List.copyOf(slots));
	}

	private static int order(FloorSlotKind kind) {
		return switch (kind) {
			case CAMP -> 0;
			case EXIT_PORTAL -> 1;
			default -> 2;
		};
	}

	private static boolean spaced(int[] spot, Set<Long> taken, int spacing) {
		for (long key : taken) {
			int dx = spot[0] - BoxLayout.keyX(key);
			int dz = spot[1] - BoxLayout.keyZ(key);
			if (dx * dx + dz * dz < spacing * spacing) {
				return false;
			}
		}
		return true;
	}

	/** Columns in a corridor's side wall face (the node replaces the brick, with rock behind it), shuffled. */
	static List<int[]> wallSpots(BoxLayout layout, Random random) {
		List<int[]> spots = new ArrayList<>();
		for (BoxLayout.Cell cell : layout.corridor()) {
			// the corridor's half-width: walk out from the centre to the first solid column
			int reach = 0;
			int dx = cell.alongX() ? 0 : 1;
			int dz = cell.alongX() ? 1 : 0;
			while (reach < 4 && layout.open(cell.x() + dx * (reach + 1), cell.z() + dz * (reach + 1))) {
				reach++;
			}
			for (int sign : new int[] {-1, 1}) {
				int fx = cell.x() + dx * sign * (reach + 1);
				int fz = cell.z() + dz * sign * (reach + 1);
				int x = cell.x() + dx * sign * (reach + 2);
				int z = cell.z() + dz * sign * (reach + 2);
				int ex = cell.x() + dx * sign * reach;
				int ez = cell.z() + dz * sign * reach;
				if (layout.open(ex, ez) && buriable(layout, fx, fz) && buriable(layout, x, z)) {
					spots.add(new int[] {fx, fz}); // set in the wall face, with rock behind it
				}
			}
		}
		Collections.shuffle(spots, random);
		return spots;
	}

	/** A solid, unplugged column that touches no open column except across the brick in front of it. */
	private static boolean buriable(BoxLayout layout, int x, int z) {
		return Math.abs(x) < layout.radius() - 1 && Math.abs(z) < layout.radius() - 1 && !layout.open(x, z) && layout.plug(x, z) == null;
	}

	/** Open columns inside filler rooms (one in from the walls) clear of every slot, shuffled. */
	static List<int[]> roomSpots(BoxLayout layout, List<FloorPlanner.PlannedSlot> slots, int clearance, Random random) {
		List<int[]> spots = new ArrayList<>();
		for (BoxLayout.Room room : layout.rooms("filler")) {
			for (int x = room.x0() + 1; x <= room.x1() - 1; x++) {
				for (int z = room.z0() + 1; z <= room.z1() - 1; z++) {
					if (clearOfSlots(x, z, slots, clearance)) {
						spots.add(new int[] {x, z});
					}
				}
			}
		}
		Collections.shuffle(spots, random);
		return spots;
	}

	private static boolean clearOfSlots(int x, int z, List<FloorPlanner.PlannedSlot> slots, int clearance) {
		for (FloorPlanner.PlannedSlot slot : slots) {
			long dx = x - slot.x();
			long dz = z - slot.z();
			long reach = slot.radius() + clearance;
			if (dx * dx + dz * dz < reach * reach) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Siege points: corridor cells as far from the camp as the box allows, spread apart, so the waves' paths run the
	 * corridors to the camp hall. Falls back to the farthest cells when the box is too cramped to spread them.
	 */
	static List<int[]> siegePoints(BoxLayout layout, int count) {
		List<int[]> cells = new ArrayList<>();
		for (BoxLayout.Cell cell : layout.corridor()) {
			if (layout.open(cell.x(), cell.z()) && layout.rooms().stream().noneMatch(room -> room.role().equals("camp") && room.contains(cell.x(), cell.z()))) {
				cells.add(new int[] {cell.x(), cell.z()});
			}
		}
		cells.sort((a, b) -> Long.compare(dist(b), dist(a)));
		List<int[]> picked = new ArrayList<>();
		double spacing = layout.radius() * 0.5;
		for (int pass = 0; pass < 3 && picked.size() < count; pass++) {
			double min = spacing;
			for (int[] cell : cells) {
				if (picked.size() >= count) {
					break;
				}
				boolean far = picked.stream().allMatch(p -> Math.hypot(p[0] - cell[0], p[1] - cell[1]) >= min);
				if (far) {
					picked.add(cell);
				}
			}
			spacing /= 2;
		}
		return picked;
	}

	private static long dist(int[] cell) {
		return (long) cell[0] * cell[0] + (long) cell[1] * cell[1];
	}
}
