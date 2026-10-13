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
	/** Trees scattered outside the slots. */
	public int trees = 16;
	/** Resource nodes for a solo party (template node slots first, the rest scattered). */
	public int nodes = 12;
	/** Weighted node block ids for node slots without a tag and for scattered nodes. */
	public Map<String, Integer> nodeMix = new LinkedHashMap<>();
	/** Loose crates for a solo party (scattered; the structure, cave, secret and wreck chests come on top). */
	public int crates = 2;
	public List<Slot> slots = new ArrayList<>();

	// ---- Infernal (v2-floor2-infernal): all optional, absent on the Surface templates so they build as before.
	/** Biome painted over the island (sky, fog, particles, ambience); empty keeps the dimension's look. */
	public String biome = "";
	/** Lava channels cutting the island into lobes, with bridges; null for none. */
	public LavaChannels.Spec channels;
	/** Basalt spires scattered outside the slots and channels. */
	public int spires = 0;
	/** Eternal fires lit on netherrack outside the slots. */
	public int fires = 0;

	public static class Terrain {
		public String top = "minecraft:grass_block";
		public String fill = "minecraft:dirt";
		public String base = "minecraft:stone";
		/** Top-block swaps: block id → percent of top blocks replaced with it (Infernal: magma, basalt). */
		public Map<String, Integer> accents = new LinkedHashMap<>();
		/** The camp's ground and fire. */
		public String camp = "minecraft:coarse_dirt";
		public String campfire = "minecraft:campfire";
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
			if ((long) slot.x * slot.x + (long) slot.z * slot.z > (long) radius * radius) {
				throw new IllegalArgumentException(slot.kind + " slot at " + slot.x + "," + slot.z + " is off the island");
			}
		}
		if (camps != 1 || portals != 1) {
			throw new IllegalArgumentException("needs exactly one camp and one exit_portal slot");
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
