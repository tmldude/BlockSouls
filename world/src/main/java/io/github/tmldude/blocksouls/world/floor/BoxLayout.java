package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;

/**
 * The room-and-corridor plan of a box floor (floor 4, The Vault), in columns relative to the camp. Pure (no Minecraft
 * classes) so it is unit-tested; the planner reads it to put nodes, crates and the siege points in the open, and the
 * builder generates the same layout again from the same seed to carve it.
 *
 * <p>Order: a room per slot, filler rooms, a spanning tree of corridors from the camp, shortcut corridors sealed by a
 * wall plug, dead-end stubs, the secret room behind a hollow-wall plug, then mob closets (a sealed pocket off a
 * corridor). Every column is either solid or clear to a height above the floor.
 */
public final class BoxLayout {
	/** What a wall plug hides. The builder tags the plug's wall blocks with it. */
	public enum Plug {
		SHORTCUT, CLOSET, HOLLOW
	}

	/** An axis-aligned room, corners inclusive. {@code role} is the slot kind's name in lower case, or {@code filler}. */
	public record Room(String role, int x0, int z0, int x1, int z1, int height) {
		public boolean contains(int x, int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}

		public int cx() {
			return (x0 + x1) / 2;
		}

		public int cz() {
			return (z0 + z1) / 2;
		}
	}

	/** A mob closet: the plug column in the corridor wall and the pocket behind it. */
	public record Closet(int plugX, int plugZ, int pocketX, int pocketZ) {
	}

	/** A column of the corridor's centre line and the axis the corridor runs along there. */
	public record Cell(int x, int z, boolean alongX) {
	}

	public static final int CLOSET_HEIGHT = 3;

	private final int radius;
	private final int half;
	private final FloorTemplate.Box box;
	private final int[] clear;
	private final Map<Long, Plug> plugs = new HashMap<>();
	private final List<Room> rooms = new ArrayList<>();
	private final List<Closet> closets = new ArrayList<>();
	private final List<Cell> corridor = new ArrayList<>();
	private final Random random;

	private BoxLayout(int radius, FloorTemplate.Box box, long seed) {
		this.radius = radius;
		this.box = box;
		this.half = Math.max(0, box.corridorWidth / 2);
		this.clear = new int[(2 * radius + 1) * (2 * radius + 1)];
		this.random = new Random(seed);
	}

	/** The slot kinds that get a room. */
	public static boolean roomKind(FloorSlotKind kind) {
		return kind != FloorSlotKind.NODE && kind != FloorSlotKind.CRATE && kind != FloorSlotKind.SIEGE_RING;
	}

	/** Lays out a box of half-width {@code radius} around the given slots (camp first). Same inputs, same layout. */
	public static BoxLayout generate(int radius, FloorTemplate.Box box, List<FloorPlanner.PlannedSlot> slots, long seed) {
		BoxLayout layout = new BoxLayout(radius, box, seed);
		layout.build(slots);
		return layout;
	}

	// ---------------------------------------------------------------- queries

	public int radius() {
		return radius;
	}

	/** Clear height above the floor at a column (0 = solid; outside the box is solid). */
	public int clear(int x, int z) {
		return inside(x, z) ? clear[index(x, z)] : 0;
	}

	public boolean open(int x, int z) {
		return clear(x, z) > 0;
	}

	/** The plug at a column, or null. */
	public Plug plug(int x, int z) {
		return plugs.get(key(x, z));
	}

	public Map<Long, Plug> plugs() {
		return Collections.unmodifiableMap(plugs);
	}

	public List<Room> rooms() {
		return Collections.unmodifiableList(rooms);
	}

	public List<Room> rooms(String role) {
		return rooms.stream().filter(room -> room.role().equals(role)).toList();
	}

	public List<Closet> closets() {
		return Collections.unmodifiableList(closets);
	}

	/** The corridors' centre lines (the spanning tree and dead ends; shortcuts and the secret tunnel excluded). */
	public List<Cell> corridor() {
		return Collections.unmodifiableList(corridor);
	}

	public static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	public static int keyX(long key) {
		return (int) (key >> 32);
	}

	public static int keyZ(long key) {
		return (int) key;
	}

	private boolean inside(int x, int z) {
		return Math.abs(x) <= radius && Math.abs(z) <= radius;
	}

	private int index(int x, int z) {
		return (x + radius) * (2 * radius + 1) + z + radius;
	}

	// ---------------------------------------------------------------- generation

	private void build(List<FloorPlanner.PlannedSlot> slots) {
		Room secret = null;
		for (FloorPlanner.PlannedSlot slot : slots) {
			if (!roomKind(slot.kind())) {
				continue;
			}
			Room room = roomFor(slot);
			if (slot.kind() == FloorSlotKind.SECRET) {
				secret = room;
			} else {
				rooms.add(room);
				carve(room);
			}
		}
		fillerRooms(secret);
		List<Room> linked = List.copyOf(rooms);
		List<int[]> tree = spanningTree(linked);
		for (int[] edge : tree) {
			corridor.addAll(connect(linked.get(edge[0]), linked.get(edge[1]), box.corridorHeight, secret));
		}
		shortcuts(linked, tree, secret);
		deadEnds(secret);
		if (secret != null) {
			secret(secret);
		}
		carveClosets();
	}

	private Room roomFor(FloorPlanner.PlannedSlot slot) {
		int size = switch (slot.kind()) {
			case CAMP -> Math.max(6, slot.radius() + 3);
			case EXIT_PORTAL -> 2;
			default -> Math.max(3, slot.radius() + 2);
		};
		int height = switch (slot.kind()) {
			case CAMP, MINI_BOSS -> box.hallHeight;
			case EXIT_PORTAL -> Math.max(5, box.roomHeight);
			default -> box.roomHeight;
		};
		String role = slot.kind().name().toLowerCase(java.util.Locale.ROOT);
		return clamp(new Room(role, slot.x() - size, slot.z() - size, slot.x() + size, slot.z() + size, height));
	}

	private Room clamp(Room room) {
		int lo = -radius + 1;
		int hi = radius - 1;
		return new Room(room.role(), Math.max(lo, room.x0()), Math.max(lo, room.z0()), Math.min(hi, room.x1()), Math.min(hi, room.z1()),
				room.height());
	}

	private void carve(Room room) {
		for (int x = room.x0(); x <= room.x1(); x++) {
			for (int z = room.z0(); z <= room.z1(); z++) {
				open(x, z, room.height());
			}
		}
	}

	private void open(int x, int z, int height) {
		if (Math.abs(x) <= radius - 1 && Math.abs(z) <= radius - 1) {
			int i = index(x, z);
			clear[i] = Math.max(clear[i], height);
			plugs.remove(key(x, z));
		}
	}

	private void fillerRooms(Room secret) {
		for (int n = 0; n < box.rooms; n++) {
			for (int attempt = 0; attempt < 60; attempt++) {
				int hx = 3 + random.nextInt(3);
				int hz = 3 + random.nextInt(3);
				int span = radius - 2 - Math.max(hx, hz);
				if (span <= 0) {
					break;
				}
				int x = random.nextInt(2 * span + 1) - span;
				int z = random.nextInt(2 * span + 1) - span;
				Room room = new Room("filler", x - hx, z - hz, x + hx, z + hz, box.roomHeight);
				if (clearOf(room, rooms, 4) && (secret == null || clearOf(room, List.of(secret), 6))) {
					rooms.add(room);
					carve(room);
					break;
				}
			}
		}
	}

	private static boolean clearOf(Room room, List<Room> others, int gap) {
		for (Room other : others) {
			if (room.x0() - gap <= other.x1() && room.x1() + gap >= other.x0() && room.z0() - gap <= other.z1() && room.z1() + gap >= other.z0()) {
				return false;
			}
		}
		return true;
	}

	/** Prim's tree over the room centres (Manhattan), rooted at the camp (index 0). Edges are index pairs. */
	private static List<int[]> spanningTree(List<Room> rooms) {
		List<int[]> edges = new ArrayList<>();
		if (rooms.isEmpty()) {
			return edges;
		}
		boolean[] in = new boolean[rooms.size()];
		in[0] = true;
		for (int added = 1; added < rooms.size(); added++) {
			int bestA = -1;
			int bestB = -1;
			int best = Integer.MAX_VALUE;
			for (int a = 0; a < rooms.size(); a++) {
				if (!in[a]) {
					continue;
				}
				for (int b = 0; b < rooms.size(); b++) {
					if (in[b]) {
						continue;
					}
					int d = distance(rooms.get(a), rooms.get(b));
					if (d < best) {
						best = d;
						bestA = a;
						bestB = b;
					}
				}
			}
			in[bestB] = true;
			edges.add(new int[] {bestA, bestB});
		}
		return edges;
	}

	private static int distance(Room a, Room b) {
		return Math.abs(a.cx() - b.cx()) + Math.abs(a.cz() - b.cz());
	}

	/** The centre line of an L from one room centre to another: x first or z first. */
	private static List<Cell> path(int ax, int az, int bx, int bz, boolean xFirst) {
		List<Cell> cells = new ArrayList<>();
		int x = ax;
		int z = az;
		cells.add(new Cell(x, z, xFirst ? ax != bx : az == bz));
		for (int leg = 0; leg < 2; leg++) {
			boolean alongX = (leg == 0) == xFirst;
			while (alongX ? x != bx : z != bz) {
				if (alongX) {
					x += Integer.signum(bx - x);
				} else {
					z += Integer.signum(bz - z);
				}
				cells.add(new Cell(x, z, alongX));
			}
		}
		return cells;
	}

	private static boolean crosses(List<Cell> path, Room room, int margin) {
		if (room == null) {
			return false;
		}
		for (Cell cell : path) {
			if (cell.x() >= room.x0() - margin && cell.x() <= room.x1() + margin && cell.z() >= room.z0() - margin && cell.z() <= room.z1() + margin) {
				return true;
			}
		}
		return false;
	}

	/** Carves a corridor between two rooms, choosing the L that keeps clear of the secret room when one does. */
	private List<Cell> connect(Room a, Room b, int height, Room secret) {
		boolean xFirst = random.nextBoolean();
		List<Cell> path = path(a.cx(), a.cz(), b.cx(), b.cz(), xFirst);
		if (crosses(path, secret, half + 2)) {
			List<Cell> other = path(a.cx(), a.cz(), b.cx(), b.cz(), !xFirst);
			if (!crosses(other, secret, half + 2)) {
				path = other;
			}
		}
		carvePath(path, height);
		return path;
	}

	private void carvePath(List<Cell> path, int height) {
		for (Cell cell : path) {
			for (int dx = -half; dx <= half; dx++) {
				for (int dz = -half; dz <= half; dz++) {
					open(cell.x() + dx, cell.z() + dz, height);
				}
			}
		}
	}

	/** The corridor's cross-section through a centre cell (perpendicular to its run). */
	private List<long[]> section(Cell cell) {
		List<long[]> cells = new ArrayList<>();
		for (int d = -half; d <= half; d++) {
			cells.add(new long[] {cell.alongX() ? cell.x() : cell.x() + d, cell.alongX() ? cell.z() + d : cell.z()});
		}
		return cells;
	}

	/**
	 * Whether the carve around path cell {@code i} opens only columns that were solid, and the run is straight for
	 * {@code half + 1} cells either side (a corner's square carve would open the columns beside a plug there).
	 */
	private boolean fresh(List<Cell> path, int i, int[] before) {
		Cell cell = path.get(i);
		int span = half + 1;
		if (i < span || i >= path.size() - span) {
			return false;
		}
		for (int j = i - span; j <= i + span; j++) {
			if (path.get(j).alongX() != cell.alongX()) {
				return false;
			}
		}
		return squareWasSolid(cell, before);
	}

	/** Whether the cell's carve square and the wall around it (reach {@code half + 1}) were solid. */
	private boolean squareWasSolid(Cell cell, int[] before) {
		int reach = half + 1;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = cell.x() + dx;
				int z = cell.z() + dz;
				if (Math.abs(x) > radius - 1 || Math.abs(z) > radius - 1 || before[index(x, z)] > 0) {
					return false;
				}
			}
		}
		return true;
	}

	private void plugSection(Cell cell, Plug plug) {
		for (long[] at : section(cell)) {
			int x = (int) at[0];
			int z = (int) at[1];
			if (inside(x, z)) {
				clear[index(x, z)] = 0;
				plugs.put(key(x, z), plug);
			}
		}
	}

	/** Extra corridors between rooms the tree didn't join, each sealed mid-way by a plain wall plug. */
	private void shortcuts(List<Room> linked, List<int[]> tree, Room secret) {
		List<int[]> pairs = new ArrayList<>();
		for (int a = 0; a < linked.size(); a++) {
			for (int b = a + 1; b < linked.size(); b++) {
				int fa = a;
				int fb = b;
				if (tree.stream().noneMatch(e -> e[0] == fa && e[1] == fb || e[0] == fb && e[1] == fa)) {
					pairs.add(new int[] {a, b});
				}
			}
		}
		pairs.sort((p, q) -> Integer.compare(distance(linked.get(p[0]), linked.get(p[1])), distance(linked.get(q[0]), linked.get(q[1]))));
		int made = 0;
		for (int[] pair : pairs) {
			if (made >= box.shortcuts) {
				break;
			}
			int[] before = clear.clone();
			Room a = linked.get(pair[0]);
			Room b = linked.get(pair[1]);
			List<Cell> path = path(a.cx(), a.cz(), b.cx(), b.cz(), random.nextBoolean());
			if (crosses(path, secret, half + 2)) {
				continue;
			}
			List<Cell> fresh = new ArrayList<>();
			for (int i = 0; i < path.size(); i++) {
				if (fresh(path, i, before)) {
					fresh.add(path.get(i));
				}
			}
			if (fresh.size() < 3) {
				continue; // the rooms already touch through other corridors: no wall worth plugging
			}
			carvePath(path, box.corridorHeight);
			plugSection(fresh.get(fresh.size() / 2), Plug.SHORTCUT);
			made++;
		}
	}

	/** Stubs off the corridors that run into solid wall and stop. */
	private void deadEnds(Room secret) {
		if (corridor.isEmpty()) {
			return;
		}
		for (int n = 0; n < box.deadEnds; n++) {
			for (int attempt = 0; attempt < 30; attempt++) {
				Cell from = corridor.get(random.nextInt(corridor.size()));
				int sign = random.nextBoolean() ? 1 : -1;
				int dx = from.alongX() ? 0 : sign;
				int dz = from.alongX() ? sign : 0;
				int length = 6 + random.nextInt(7);
				List<Cell> stub = new ArrayList<>();
				for (int step = half + 1; step <= half + length; step++) {
					stub.add(new Cell(from.x() + dx * step, from.z() + dz * step, !from.alongX()));
				}
				if (solidAround(stub, half + 1, 2) && !crosses(stub, secret, half + 2)) {
					carvePath(stub, box.corridorHeight);
					corridor.addAll(stub);
					break;
				}
			}
		}
	}

	/** Whether every column within {@code reach} across of the cells (and {@code ahead} past the last) is solid inside. */
	private boolean solidAround(List<Cell> cells, int reach, int ahead) {
		Cell last = cells.getLast();
		Cell first = cells.getFirst();
		int sx = Integer.signum(last.x() - first.x());
		int sz = Integer.signum(last.z() - first.z());
		List<Cell> check = new ArrayList<>(cells);
		for (int i = 1; i <= ahead; i++) {
			check.add(new Cell(last.x() + sx * i, last.z() + sz * i, last.alongX()));
		}
		for (Cell cell : check) {
			for (int d = -reach; d <= reach; d++) {
				int x = cell.alongX() ? cell.x() : cell.x() + d;
				int z = cell.alongX() ? cell.z() + d : cell.z();
				if (Math.abs(x) > radius - 2 || Math.abs(z) > radius - 2 || clear[index(x, z)] > 0) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The secret room and its tunnel to the nearest room, sealed by a hollow-wall plug where the tunnel meets the open
	 * vault (the last fresh cross-section before it), so the only way in is through the hollow wall.
	 */
	private void secret(Room secret) {
		Room target = null;
		for (Room room : rooms) {
			if (target == null || distance(room, secret) < distance(target, secret)) {
				target = room;
			}
		}
		int[] before = clear.clone();
		carve(secret);
		if (target == null) {
			return;
		}
		List<Cell> path = path(secret.cx(), secret.cz(), target.cx(), target.cz(), random.nextBoolean());
		Cell plug = null;
		for (int i = 0; i < path.size(); i++) {
			Cell cell = path.get(i);
			if (secret.contains(cell.x(), cell.z())) {
				continue;
			}
			if (!squareWasSolid(cell, before)) {
				break;
			}
			if (fresh(path, i, before)) {
				plug = cell;
			}
		}
		carvePath(path, box.corridorHeight);
		rooms.add(secret);
		if (plug == null) {
			// The tunnel has no solid stretch: wall the secret room's whole rim instead.
			for (int x = secret.x0(); x <= secret.x1(); x++) {
				for (int z = secret.z0(); z <= secret.z1(); z++) {
					if (x == secret.x0() || x == secret.x1() || z == secret.z0() || z == secret.z1()) {
						clear[index(x, z)] = 0;
						plugs.put(key(x, z), Plug.HOLLOW);
					}
				}
			}
			return;
		}
		plugSection(plug, Plug.HOLLOW);
	}

	/** Mob closets: a 3×3 pocket behind a one-column wall plug beside a corridor. */
	private void carveClosets() {
		List<Cell> cells = new ArrayList<>(corridor);
		Collections.shuffle(cells, random);
		for (Cell cell : cells) {
			if (closets.size() >= box.closets) {
				break;
			}
			int sign = random.nextBoolean() ? 1 : -1;
			int dx = cell.alongX() ? 0 : sign;
			int dz = cell.alongX() ? sign : 0;
			int plugX = cell.x() + dx * (half + 1);
			int plugZ = cell.z() + dz * (half + 1);
			int pocketX = plugX + dx * 2;
			int pocketZ = plugZ + dz * 2;
			if (!open(cell.x() + dx * half, cell.z() + dz * half) || !solidBlock(plugX, plugZ, 0) || !solidBlock(pocketX, pocketZ, 2)
					|| plugs.containsKey(key(plugX, plugZ))) {
				continue;
			}
			for (int x = pocketX - 1; x <= pocketX + 1; x++) {
				for (int z = pocketZ - 1; z <= pocketZ + 1; z++) {
					open(x, z, CLOSET_HEIGHT);
				}
			}
			plugs.put(key(plugX, plugZ), Plug.CLOSET);
			closets.add(new Closet(plugX, plugZ, pocketX, pocketZ));
		}
	}

	/** Whether the square of half-size {@code reach} around a column is solid, unplugged and inside the shell. */
	private boolean solidBlock(int cx, int cz, int reach) {
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				if (Math.abs(x) > radius - 2 || Math.abs(z) > radius - 2 || clear[index(x, z)] > 0 || plugs.containsKey(key(x, z))) {
					return false;
				}
			}
		}
		return true;
	}
}
