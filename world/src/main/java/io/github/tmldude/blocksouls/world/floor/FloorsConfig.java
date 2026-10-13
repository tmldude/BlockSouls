package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/blocksouls/floors.json}: the floors run shape's world numbers (PLAN2 §1). Pure data so the planner and its
 * tests use it directly. Distances are blocks.
 */
public class FloorsConfig {
	public static final String FILE = "floors";

	/** Distance between floor centres on the dimension's grid (each placed floor takes the next free cell). */
	public int gridSpacing = 512;
	/** Grid cells per row before the next row starts. */
	public int gridColumns = 16;

	/** Escalation tags added from this floor number on (Gungeon-style ramp: the Director, mobs and loot read them). */
	public Map<String, List<String>> escalationTags = defaultEscalation();
	/** Node count multiplier per floor number (index 0 = floor 1; the last entry repeats): later floors are stingier. */
	public List<Double> nodeFactorByFloor = new ArrayList<>(List.of(1.0, 1.0, 0.92, 0.85, 0.8, 0.75));

	/** Glass wall height above the ground at the island edge. */
	public int wallHeight = 4;
	/** Falling this far below the island's ground sends a player back to the camp. */
	public int deathPlaneDepth = 24;
	/** Health a player loses for falling off (never below 1 health). */
	public float fallPenalty = 4.0F;

	/** Siege spawn ring: points around the camp, at this share of the island radius. */
	public int siegeRingPoints = 8;
	public double siegeRingFraction = 0.8;
	/** Generated nodes and crates keep this far from every other slot's footprint, and nodes this far from each other. */
	public int slotClearance = 3;
	public int nodeSpacing = 4;
	/** Structure kinds a template's empty structure slot picks from. */
	public List<String> structures = new ArrayList<>(List.of("outpost", "wreck_yard"));
	/** Secret kinds a template's empty secret slot picks from. */
	public List<String> secrets = new ArrayList<>(List.of("cracked_wall", "hidden_drop"));

	// ---- Skyrift (floor 3)
	/** Outward push (blocks/tick added every other tick) at the open edge, times the template's {@code rimWind}. */
	public double rimWindStrength = 0.03;
	/** The wind blows within this many blocks of the edge and at most this high above the island's ground. */
	public int rimWindBand = 6;
	public int rimWindHeight = 5;
	/** Share of the wind a sneaking player still feels (brace against it). */
	public double rimWindSneakFactor = 0.25;
	/** {@code /bs floor hazard gust}: the debug gust is this many times the full rim wind, applied once. */
	public double gustFactor = 12.0;

	/** Hits a resource node takes before it pops. */
	public int nodeHits = 3;
	/** Rolls of the node's loot table when it pops (the burst). */
	public int nodeBurstRolls = 3;

	// ---- Infernal (v2-floor2-infernal)
	public Infernal infernal = new Infernal();

	/** Floor 2's hazard: lava is the death plane, and it rises during the portal-charge siege. */
	public static class Infernal {
		/** Seconds after the portal charge starts (the siege) that the lava rises. */
		public int riseSeconds = 90;
		/** Blocks the lava rises (the low ring bridges sit one block over the lava, so 1 sinks them). */
		public int riseBlocks = 1;
		/** Seconds of warning (rumble, lava bursting from the channels) before the rise. */
		public int warnSeconds = 5;
		/** Lava sends a player without fire resistance back to the camp, like falling off. */
		public boolean lavaSendsBack = true;
		/** Seconds of fire resistance from an Ashcap Tonic (wade the lava, shrug off the burning affix). */
		public int tonicSeconds = 90;
		/** Seconds of Scorching from a Scorch Draught. */
		public int scorchSeconds = 120;
		/** Seconds a Scorching player's hit (melee or gun) sets the target burning for. */
		public float scorchIgniteSeconds = 4;
	}

	private static Map<String, List<String>> defaultEscalation() {
		Map<String, List<String>> tags = new LinkedHashMap<>();
		tags.put("2", new ArrayList<>(List.of("more_elites")));
		tags.put("3", new ArrayList<>(List.of("stingy_nodes")));
		tags.put("4", new ArrayList<>(List.of("mean_mini_boss")));
		tags.put("5", new ArrayList<>(List.of("elite_affixes")));
		return tags;
	}

	public double nodeFactor(int floorNumber) {
		return nodeFactorByFloor.isEmpty() ? 1.0 : nodeFactorByFloor.get(Math.min(Math.max(floorNumber, 1), nodeFactorByFloor.size()) - 1);
	}
}
