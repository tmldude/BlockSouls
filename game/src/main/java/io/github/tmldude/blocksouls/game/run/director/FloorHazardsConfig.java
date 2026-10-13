package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Floor "danger scaling" hazards, keyed by floor tag ({@code config/blocksouls/floor_hazards.json}): a denser day-mob
 * spawn, roaming field bosses by day, and zone problems on a timer drawn from a data-driven list. A floor runs the
 * first entry whose tag it carries. Party-size inputs go through {@code PartyScaling} ({@code floorSize} for the
 * storm, {@code directorBurst} for packs). Decisions: {@code docs/decisions/v2-floor5-blight.md}.
 */
public class FloorHazardsConfig {
	public static final String FILE = "floor_hazards";

	public boolean enabled = true;
	public Map<String, TagHazards> byTag = defaults();

	/** One floor tag's hazards. */
	public static class TagHazards {
		/** Replaces {@code director.json}'s {@code dayMobs} on these floors (null = the Director's own). */
		public DirectorConfig.DayMobs dayMobs;
		/** Zone problems: the first comes this long into the day, then one every interval. */
		public int firstProblemSeconds = 60;
		public int problemIntervalSeconds = 100;
		public List<Problem> problems = new ArrayList<>();
		/** Field bosses that walk the island by day (ids the bosses module doesn't know are skipped). */
		public List<String> roamingBosses = new ArrayList<>();
		/** Field bosses alive at once, at most (they come back this long after one dies, by day). */
		public int roamingBossesAlive = 2;
		public int roamingRespawnSeconds = 180;
		public int roamingFirstSeconds = 30;
		/** Field bosses appear at least this far from the camp and from every player. */
		public int roamingMinDistance = 24;
		/** How far a field boss with no target wanders per leg, and its walking speed. */
		public int roamingWander = 18;
		public double roamingSpeed = 0.8;
	}

	/** A zone problem. {@code type}: {@code storm}, {@code surge} or {@code dead_camp}. */
	public static class Problem {
		public String id = "";
		public String type = "";
		public int weight = 1;
		/** How long it lasts (storm, dead camp); a surge is instant. */
		public int seconds = 60;
		/** Storm: radius (solo, × {@code floorSize}), damage per second inside, drift speed (blocks per second). */
		public double radius = 9;
		public float damage = 2;
		public double speed = 0.9;
		/** Surge: pack size (solo, × {@code directorBurst}) and its elite chance. */
		public int pack = 6;
		public double eliteChance = 0.3;

		public Problem() {
		}

		Problem(String id, String type, int weight, int seconds) {
			this.id = id;
			this.type = type;
			this.weight = weight;
			this.seconds = seconds;
		}
	}

	private static Map<String, TagHazards> defaults() {
		Map<String, TagHazards> map = new LinkedHashMap<>();
		// v2-floor5-blight: the Blighted Surface.
		TagHazards blight = new TagHazards();
		blight.dayMobs = new DirectorConfig.DayMobs();
		blight.dayMobs.intervalSeconds = 30;
		blight.dayMobs.pack = 4;
		blight.dayMobs.eliteChance = 0.35;
		blight.dayMobs.maxAlive = 24;
		blight.problems.add(new Problem("blight_storm", "storm", 3, 45));
		blight.problems.add(new Problem("spawn_surge", "surge", 2, 0));
		blight.problems.add(new Problem("dead_camp", "dead_camp", 1, 60));
		blight.roamingBosses.add("blocksouls_bosses:blightheart");
		blight.roamingBosses.add("blocksouls_bosses:pale_rider");
		map.put("blight", blight);
		return map;
	}
}
