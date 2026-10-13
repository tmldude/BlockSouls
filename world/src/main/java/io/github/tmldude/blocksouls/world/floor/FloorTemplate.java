package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;

/**
 * A floor template ({@code data/<ns>/floor_template/*.json}): the island's shape and look plus its hand-placed slots.
 * Coordinates are relative to the camp at the template's own radius; the planner scales, rotates and mirrors them per
 * run. Pure data (no Minecraft classes) so the planner tests can build templates directly.
 */
public final class FloorTemplate {
	private static final Gson GSON = new Gson();

	/** Set by the loader from the file name. */
	public transient String id = "";
	/** The pool the template belongs to (e.g. {@code blocksouls_world:surface}). */
	public String pool = "blocksouls_world:surface";
	/** Tags every floor from this template carries (escalation tags are added on top). */
	public List<String> tags = new ArrayList<>();
	/** Floor numbers the template may be drawn for (inclusive): later floors draw meaner variants. */
	public int minFloor = 1;
	public int maxFloor = 99;
	public int weight = 1;

	/** Island radius (blocks) for a solo party; scaled by {@code PartyScaling.floorSize}. */
	public int radius = 56;
	/** Ground level of the island. */
	public int groundY = 80;
	/** Island thickness under the camp (tapers to the edge); caves must fit inside it. */
	public int depth = 16;
	/** Terrain roughness: blocks up/down from {@link #groundY} away from flattened slots. */
	public double hills = 2.5;
	public Terrain terrain = new Terrain();
	/** Extra ground cover and tree style; null (the default) is plain grassland with oak trees. */
	public Decor decor;
	/** Trees scattered outside the slots. */
	public int trees = 16;
	/** Resource nodes for a solo party (template node slots first, the rest scattered). */
	public int nodes = 12;
	/** Weighted node block ids for node slots without a tag and for scattered nodes. */
	public Map<String, Integer> nodeMix = new LinkedHashMap<>();
	/**
	 * The floor's rare nodes (v2-floor5-blight): node block id → count for a solo party, × {@code floorNodes} and the
	 * floor's node factor, scattered on top of {@code nodes}. Empty on floors whose rare sits in {@code nodeMix}.
	 */
	public Map<String, Integer> rareNodes = new LinkedHashMap<>();
	/** The floor's herbs: herb block id → count for a solo party, × {@code floorSize} (2 → 3 at 8 players). */
	public Map<String, Integer> herbs = new LinkedHashMap<>();
	/** Loose crates for a solo party (scattered; the structure, cave, secret and wreck chests come on top). */
	public int crates = 2;
	public List<Slot> slots = new ArrayList<>();

	// ---- Skyrift (floor 3) additions: all optional, the defaults build a Surface floor exactly as before.
	/** Glass wall at the island edge; false leaves the edge open (the drop is the death plane). */
	public boolean edgeWall = true;
	/** Biome painted over the floor's cell (sky, fog, clouds, grass and leaf tint); empty keeps the dimension's. */
	public String biome = "";
	/** Outward wind near the open edge, as a multiple of {@code FloorsConfig.rimWindStrength}; 0 = none. */
	public double rimWind = 0;
	/** Floating shards above and around the island (count 0 = none). */
	public Shards shards = new Shards();
	/** Siege ring points moved onto shards (waves drop in from above). */
	public int elevatedSiegePoints = 0;
	// ---- Infernal (v2-floor2-infernal): all optional (the biome is the Skyrift field above), absent on the Surface templates so they build as before.
	/** Lava channels cutting the island into lobes, with bridges; null for none. */
	public LavaChannels.Spec channels;
	/** Basalt spires scattered outside the slots and channels. */
	public int spires = 0;
	/** Eternal fires lit on netherrack outside the slots. */
	public int fires = 0;

	// ---- Vault (floor 4) block: the box shape. Island templates leave these at their defaults.
	/** {@code "island"} (default: a floating disc) or {@code "box"} (a bounded room-and-corridor dungeon, floor 4). */
	public String shape = SHAPE_ISLAND;
	/** Box only: ceiling height above {@link #groundY} (the bedrock lid). */
	public int height = 24;
	/** Box only: how the rooms and corridors are laid out. */
	public Box box = new Box();

	public static final String SHAPE_ISLAND = "island";
	public static final String SHAPE_BOX = "box";

	public boolean isBox() {
		return SHAPE_BOX.equals(shape);
	}

	/** The box layout's knobs (see {@link BoxLayout}). {@link #radius} is the box's half-width. */
	public static class Box {
		/** Filler rooms on top of the slot rooms (keys, crates, nodes live in them). */
		public int rooms = 7;
		/** Extra corridors that close a loop, each sealed by a wall plug (a hidden shortcut). */
		public int shortcuts = 3;
		/** Corridor stubs that end in a wall. */
		public int deadEnds = 4;
		/** Mob closets: a sealed pocket off a corridor behind a wall that releases a swarm when broken. */
		public int closets = 5;
		/** Share of scattered nodes set into a corridor's wall face (rock behind) instead of standing in a room. */
		public double wallNodeShare = 0.4;
		public int corridorWidth = 3;
		public int corridorHeight = 4;
		public int roomHeight = 6;
		/** Clear height of the camp hall and the mini-boss rooms. */
		public int hallHeight = 9;
		/** Wall variant shares (the rest are plain vault bricks). */
		public double mossyShare = 0.2;
		public double crackedShare = 0.08;
		/** The rare node set into the walls (a block id): wall-face nodes are always this one. Empty = from {@code nodeMix}. */
		public String wallNode = "";
		/** The floor's herb (a {@code HerbBlock} id): {@code herbs} × {@code PartyScaling.floorNodes}, clamped to min..max, in rooms. */
		public String herb = "";
		public int herbs = 0;
		public int herbsMin = 2;
		public int herbsMax = 3;
		/** Vault keys: chests in this many rooms (never the camp, secret or structure room) each hold a key to the vault room. */
		public int keys = 0;
	}
	// ---- end Vault block

	public static class Terrain {
		public String top = "minecraft:grass_block";
		public String fill = "minecraft:dirt";
		public String base = "minecraft:stone";
		/** Top-block swaps: block id → percent of top blocks replaced with it (Infernal: magma, basalt). */
		public Map<String, Integer> accents = new LinkedHashMap<>();
		/** The camp's ground and fire. */
		public String camp = "minecraft:coarse_dirt";
		public String campfire = "minecraft:campfire";

		/**
		 * Optional weighted top blocks replacing {@link #top}: each column picks one by a hash of its position, so the
		 * build's random stream (and every other template's output) is unchanged.
		 */
		public Map<String, Integer> topMix = new LinkedHashMap<>();

		/** The top block id at a column (relative to the centre): {@link #top}, or a pick from {@link #topMix}. */
		public String topAt(int x, int z) {
			if (topMix == null || topMix.isEmpty()) {
				return top;
			}
			int total = 0;
			for (int weight : topMix.values()) {
				total += Math.max(0, weight);
			}
			if (total == 0) {
				return top;
			}
			// 2×2 clumps read as patches rather than per-block noise.
			long hash = (x >> 1) * 0x9E3779B97F4A7C15L ^ (z >> 1) * 0xC2B2AE3D27D4EB4FL;
			hash ^= hash >>> 31;
			hash *= 0xBF58476D1CE4E5B9L;
			hash ^= hash >>> 29;
			int roll = (int) Math.floorMod(hash, (long) total);
			for (Map.Entry<String, Integer> entry : topMix.entrySet()) {
				roll -= Math.max(0, entry.getValue());
				if (roll < 0) {
					return entry.getKey();
				}
			}
			return top;
		}
	}

	/** A themed floor's dressing: tree style, ground patches and scattered plants (all counts for a solo party). */
	public static class Decor {
		/** {@code oak} (the oak feature) or {@code dead} (bare trunks of {@link #deadLog} with a stub branch). */
		public String trees = "oak";
		public String deadLog = "minecraft:dark_oak_log";
		/** Round patches of {@link #patchBlock} in the top layer, with {@link #patchCover} growing on some of it. */
		public int patches;
		public int patchRadius = 3;
		public String patchBlock = "";
		public String patchCover = "";
		public double coverChance = 0.3;
		/** Plants (block id → count) set on open ground outside the slots. */
		public Map<String, Integer> scatter = new LinkedHashMap<>();
	}

	/**
	 * Floating shards: small islands {@code minRise}..{@code maxRise} above the ground, {@code minReach}..{@code maxReach}
	 * of the island radius from the camp (above 1.0 they float past the edge). Their count is scaled by
	 * {@code PartyScaling.floorSize} and clamped to {@code maxCount}.
	 */
	public static class Shards {
		public int count = 0;
		public int maxCount = 8;
		public int minSize = 3;
		public int maxSize = 6;
		public int minRise = 9;
		public int maxRise = 18;
		public double minReach = 0.45;
		public double maxReach = 1.12;
		/** Edge-to-edge gap (blocks) a shard past the rim may leave to the island: a swing, not a leap of faith. */
		public int maxGap = 9;
		/** Share of shards over the island that hang a climbable vine strand toward the ground. */
		public double vineChance = 0.5;
	}

	/** One hand-placed slot. {@code kind} is a {@link FloorSlotKind} name (any case). */
	public static class Slot {
		public String kind = "";
		public String tag = "";
		public int x;
		public int z;
		public int radius;

		public Slot() {
		}

		public Slot(FloorSlotKind kind, String tag, int x, int z, int radius) {
			this.kind = kind.name();
			this.tag = tag;
			this.x = x;
			this.z = z;
			this.radius = radius;
		}

		public FloorSlotKind kindEnum() {
			return FloorSlotKind.valueOf(kind.toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * Parses and checks a template: exactly one camp and one exit portal, known slot kinds, a positive radius.
	 *
	 * @throws IllegalArgumentException when the template is unusable
	 */
	public static FloorTemplate parse(String id, JsonElement json) {
		FloorTemplate template = GSON.fromJson(json, FloorTemplate.class);
		template.id = id;
		template.validate();
		return template;
	}

	public void validate() {
		if (radius < 16) {
			throw new IllegalArgumentException("radius must be at least 16");
		}
		if (shape == null) {
			shape = SHAPE_ISLAND;
		}
		if (!isBox() && !SHAPE_ISLAND.equals(shape)) {
			throw new IllegalArgumentException("unknown shape " + shape);
		}
		if (isBox() && (box == null || height < 12)) {
			throw new IllegalArgumentException("a box needs a box block and a height of at least 12");
		}
		if (isBox() && (box.herbsMin < 0 || box.herbsMax < box.herbsMin)) {
			throw new IllegalArgumentException("box herbs: min must not exceed max");
		}
		if (isBox() && box.keys < 0) {
			throw new IllegalArgumentException("box keys must not be negative");
		}
		int camps = 0;
		int portals = 0;
		for (Slot slot : slots) {
			FloorSlotKind kind;
			try {
				kind = slot.kindEnum();
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("unknown slot kind " + slot.kind);
			}
			if (slot.tag == null) {
				slot.tag = "";
			}
			if (kind == FloorSlotKind.CAMP) {
				camps++;
			} else if (kind == FloorSlotKind.EXIT_PORTAL) {
				portals++;
			}
			if (isBox() ? Math.max(Math.abs(slot.x), Math.abs(slot.z)) > radius - 3
					: (long) slot.x * slot.x + (long) slot.z * slot.z > (long) radius * radius) {
				throw new IllegalArgumentException(slot.kind + " slot at " + slot.x + "," + slot.z + " is off the island");
			}
		}
		if (camps != 1 || portals != 1) {
			throw new IllegalArgumentException("needs exactly one camp and one exit_portal slot");
		}
		if (shards == null) {
			shards = new Shards();
		}
		if (shards.minSize < 1 || shards.maxSize < shards.minSize || shards.maxRise < shards.minRise || shards.maxReach < shards.minReach) {
			throw new IllegalArgumentException("shards: min must not exceed max");
		}
		if (nodeMix == null || nodeMix.isEmpty()) {
			throw new IllegalArgumentException("nodeMix is empty");
		}
		if (biome == null) {
			biome = "";
		}
		if (terrain.accents == null) {
			terrain.accents = new LinkedHashMap<>();
		}
		if (channels != null) {
			channels.validate();
		}
	}

	public boolean allows(int floorNumber) {
		return floorNumber >= minFloor && floorNumber <= maxFloor;
	}
}
