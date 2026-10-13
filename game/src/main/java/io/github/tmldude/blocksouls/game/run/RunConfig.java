package io.github.tmldude.blocksouls.game.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/blocksouls/game-run.json}: every tunable number of the run loop. Pure data (no Minecraft classes) so
 * {@link WaveMath} and its tests can use it directly.
 */
public class RunConfig {
	public static final String FILE = "game-run";
	/** Defaults revision {@link #migrate} brings older files up to. */
	public static final int CURRENT_DEFAULTS = 2;

	/**
	 * Which defaults revision this file was written against. Missing in files from before the field existed (reads as
	 * 0), so {@link #migrate} can update values that were never changed by hand.
	 */
	public int defaultsVersion;

	/**
	 * The run's shape: {@code "classic"} is the v1 open-world 7-night overworld run (the default until v2
	 * ships), {@code "expedition"} the v2 act run (acts advance by portal charge; sieges follow the party
	 * through every dimension on one day/night rhythm), {@code "floors"} the expedition played on the world's
	 * generated floor islands ({@link #floorActs}, one floor per act).
	 */
	public String runShape = "classic";

	/**
	 * The expedition's acts, in order. A dimension that isn't registered (yet) is legal: the act exists,
	 * travel to it just isn't possible until the dimension does (Stage 6 registers acts 3–4).
	 */
	public List<ActSpec> acts = defaultActs();

	/**
	 * The acts of the {@code "floors"} run shape: every act is a floor island in the world's floor dimension
	 * (the world places one per act; acts 2–6 reuse Surface templates with escalation tags until their pools ship).
	 */
	public List<ActSpec> floorActs = defaultFloorActs();

	/** Seconds the player-triggered portal charge takes while its defense siege runs. */
	public int portalChargeSeconds = 180;

	/** Start the run with {@link #defaultDifficulty} the first time a player joins a fresh world. */
	public boolean autoStart = true;
	public String defaultDifficulty = "normal";
	public Map<String, DifficultyPreset> difficulties = defaultDifficulties();
	/** Set by {@code /bs run new [difficulty]}: the next run's difficulty (used once, then cleared). */
	public String nextRunDifficulty = "";

	/** Real minutes for one full day/night cycle (vanilla: 20). */
	public double dayLengthMinutes = 12.0;
	/** Day times (mod 24000). */
	public int duskCueTime = 10000;
	public int duskTime = 13000;
	public int dawnTime = 23000;

	/** Waves per night, by night number (the last value repeats). */
	public List<Integer> wavesPerNight = new ArrayList<>(List.of(2, 2, 2, 3, 3, 3, 3, 4));
	public int endlessExtraWavesPerNight = 1;
	public int waveBreakSeconds = 8;
	/** A new wave only starts if at least this much (real) night is left. */
	public int minNightLeftSeconds = 60;

	/** count = round((baseCount + countPerWave·wave) · party size factor · difficulty.waveSize · modifierFactor). Party scaling
	 * (wave size, mobs alive at once, elites) lives in common's party.json. */
	public double baseCount = 6;
	public double countPerWave = 3;
	public double spawnMinDistance = 24;
	public double spawnMaxDistance = 40;
	public double minPlayerDistance = 12;
	public double followRange = 64;
	/** Max health ×(1 + healthPerWave·(wave−1)); attack damage ×(1 + damagePerWave·(wave−1)). */
	public double healthPerWave = 0.08;
	public double damagePerWave = 0.05;
	/** At most eliteBase + wave / wavesPerExtraElite elites per wave. */
	public int eliteBase = 1;
	public int wavesPerExtraElite = 4;
	/** Each run shifts every wave mob's first wave by a random amount in ±rosterShuffle. */
	public int rosterShuffle = 1;
	/** Share of spawns that break blocks: min(breakerBase + breakerPerWave·(wave−1), breakerMax). */
	public double breakerBase = 0.10;
	public double breakerPerWave = 0.02;
	public double breakerMax = 0.40;

	/** Wave mobs further than this from every player for straySeconds get respawned near a player. */
	public double strayDistance = 96;
	public int straySeconds = 30;
	/** When at most glowRemaining mobs are left for glowSeconds, they glow. */
	public int glowRemaining = 3;
	public int glowSeconds = 45;
	/** Ordinary wave mobs burn this long at dawn before they're removed. */
	public int dawnBurnSeconds = 5;

	public int bloodMoonEvery = 4;
	public double bloodMoonCountBonus = 0.5;
	/** Chance that a non-Blood-Moon night rolls one modifier from modifierPool. */
	public double modifierChance = 0.4;
	public List<String> modifierPool = new ArrayList<>(List.of(RunIds.FOG_ID, RunIds.SPRINTER_ID));
	/** Client fog end distance on fog nights (blocks). */
	public double fogDistance = 24;
	public double sprinterSpeedBonus = 0.3;

	/** Chance that a wave mob killed by a player drops from the wave's tier table. */
	public double dropChance = 0.5;
	/** Tier odds by wave: the last entry whose fromWave ≤ wave applies. Weights, normalized. */
	public List<TierOdds> tierOdds = new ArrayList<>(List.of(
			new TierOdds(1, 1, 0, 0),
			new TierOdds(3, 0.8, 0.2, 0),
			new TierOdds(6, 0.65, 0.3, 0.05)));

	/**
	 * Wave-clear reward bundle: the reward table is rolled round((rewardRollsBase + rewardRollsPerWave·(wave−1)) · party
	 * rewards factor) times, at least once and at most rewardRollsMax. Party scaling lives in common's party.json.
	 */
	public double rewardRollsBase = 1;
	public double rewardRollsPerWave = 0.15;
	public int rewardRollsMax = 8;
	/** Reward chest goes on a free spot within this many blocks of the wave's last kill. */
	public int rewardSearchRadius = 4;

	/**
	 * Pity gun: from this night on, a party carrying nothing but Starter Pistols gets gunPityGuns (× party rewards factor)
	 * rolled guns in the next wave-clear chest, or at its feet at dawn. 0 turns it off.
	 */
	public int gunPityNight = 2;
	public double gunPityGuns = 1;

	/** Every elite a player kills rolls the elite table this many times on top of its tier drop. */
	public int eliteDropRolls = 1;
	/** Elite drops glow (outline through walls) until picked up. */
	public boolean eliteDropsGlow = true;

	/** Starter Pistols break when this wave ends. Late joiners get one only while wave ≤ this. */
	public int starterPistolLastWave = 4;

	/** Seconds between client sync packets while a timer is counting down. */
	public int syncIntervalSeconds = 1;

	/**
	 * Brings a config written against older defaults up to date: every value still at its old default takes the new one,
	 * anything changed by hand is kept. Returns whether anything changed (so the caller saves the file).
	 */
	public static boolean migrate(RunConfig config) {
		if (config.defaultsVersion >= CURRENT_DEFAULTS) {
			return false;
		}
		// 1: the playtest pacing pass (v0.10.1): shorter days, more waves, shorter breaks, more drops.
		if (config.defaultsVersion < 1) {
			if (config.dayLengthMinutes == 17.0) {
				config.dayLengthMinutes = 12.0;
			}
			if (List.of(1, 1, 2, 2, 2, 2, 2, 3).equals(config.wavesPerNight)) {
				config.wavesPerNight = new ArrayList<>(List.of(2, 2, 2, 3, 3, 3, 3, 4));
			}
			if (config.waveBreakSeconds == 20) {
				config.waveBreakSeconds = 8;
			}
			if (config.dropChance == 0.35) {
				config.dropChance = 0.5;
			}
			if (config.starterPistolLastWave == 3) {
				config.starterPistolLastWave = 4;
			}
		}
		// 2: drops-and-pacing (v0.11): earlier dusk cue, rare drops a wave earlier.
		if (config.defaultsVersion < 2) {
			if (config.duskCueTime == 11500) {
				config.duskCueTime = 10000;
			}
			if (sameOdds(config.tierOdds, List.of(new TierOdds(1, 1, 0, 0), new TierOdds(4, 0.8, 0.2, 0), new TierOdds(7, 0.65, 0.3, 0.05)))) {
				config.tierOdds = new RunConfig().tierOdds;
			}
		}
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
	}

	private static boolean sameOdds(List<TierOdds> a, List<TierOdds> b) {
		if (a == null || a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			TierOdds x = a.get(i);
			TierOdds y = b.get(i);
			if (x == null || x.fromWave != y.fromWave || x.common != y.common || x.rare != y.rare || x.legendary != y.legendary) {
				return false;
			}
		}
		return true;
	}

	public static class ActSpec {
		public String id = "";
		public String dimension = "";
		public String name = "";
		/** Floors shape only: the world's floor template pool this act's floor is drawn from. */
		public String pool = "";

		public ActSpec() {
		}

		public ActSpec(String id, String dimension, String name) {
			this.id = id;
			this.dimension = dimension;
			this.name = name;
		}
	}

	public static class DifficultyPreset {
		public double waveSize = 1;
		public double mobHealth = 1;
		public double mobDamage = 1;

		public DifficultyPreset() {
		}

		public DifficultyPreset(double waveSize, double mobHealth, double mobDamage) {
			this.waveSize = waveSize;
			this.mobHealth = mobHealth;
			this.mobDamage = mobDamage;
		}
	}

	public static class TierOdds {
		public int fromWave = 1;
		public double common = 1;
		public double rare = 0;
		public double legendary = 0;

		public TierOdds() {
		}

		public TierOdds(int fromWave, double common, double rare, double legendary) {
			this.fromWave = fromWave;
			this.common = common;
			this.rare = rare;
			this.legendary = legendary;
		}
	}

	private static List<ActSpec> defaultActs() {
		return new ArrayList<>(List.of(
				new ActSpec(RunIds.NAMESPACE + ":overworld", "minecraft:overworld", "Overworld"),
				new ActSpec(RunIds.NAMESPACE + ":infected_nether", "minecraft:the_nether", "Infected Nether"),
				new ActSpec(RunIds.NAMESPACE + ":blightheart", "blocksouls_world:blightheart", "The Blightheart"),
				new ActSpec(RunIds.NAMESPACE + ":skyrift", "blocksouls_world:skyrift", "The Skyrift")));
	}

	private static List<ActSpec> defaultFloorActs() {
		List<ActSpec> floorActs = new ArrayList<>();
		for (int floor = 1; floor <= 6; floor++) {
			ActSpec spec = new ActSpec(RunIds.NAMESPACE + ":floor_" + floor, "blocksouls_world:floors", "Floor " + floor);
			spec.pool = "blocksouls_world:surface";
			// --- floor pools (Stage 6C): one line per floor session ---
			if (floor == 3) {
				spec.pool = "blocksouls_world:skyrift";
			}
			if (floor == 4) {
				spec.pool = "blocksouls_world:vault";
			}
			if (floor == 5) { // v2-floor5-blight: the Blighted Surface
				spec.pool = "blocksouls_world:blight";
			}
			if (floor == 2) { // v2-floor2-infernal
				spec.pool = "blocksouls_world:infernal";
			}
			if (floor == 6) { // v2-floor6-arena: the Final Arena
				spec.pool = "blocksouls_world:arena";
				spec.name = "Final Arena";
			}
			// --- end floor pools ---
			floorActs.add(spec);
		}
		return floorActs;
	}

	private static Map<String, DifficultyPreset> defaultDifficulties() {
		Map<String, DifficultyPreset> map = new LinkedHashMap<>();
		map.put("easy", new DifficultyPreset(0.75, 0.8, 0.75));
		map.put("normal", new DifficultyPreset(1, 1, 1));
		map.put("hard", new DifficultyPreset(1.25, 1.25, 1.2));
		map.put("nightmare", new DifficultyPreset(1.6, 1.6, 1.5));
		return map;
	}
}
