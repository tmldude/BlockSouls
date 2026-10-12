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
	public static final int CURRENT_DEFAULTS = 1;

	/**
	 * Which defaults revision this file was written against. Missing in files from before the field existed (reads as
	 * 0), so {@link #migrate} can update values that were never changed by hand.
	 */
	public int defaultsVersion;

	/** Start the run with {@link #defaultDifficulty} the first time a player joins a fresh world. */
	public boolean autoStart = true;
	public String defaultDifficulty = "normal";
	public Map<String, DifficultyPreset> difficulties = defaultDifficulties();
	/** Set by {@code /bs run new [difficulty]}: the next run's difficulty (used once, then cleared). */
	public String nextRunDifficulty = "";

	/** Real minutes for one full day/night cycle (vanilla: 20). */
	public double dayLengthMinutes = 12.0;
	/** Day times (mod 24000). */
	public int duskCueTime = 11500;
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
			new TierOdds(4, 0.8, 0.2, 0),
			new TierOdds(7, 0.65, 0.3, 0.05)));

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
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
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

	private static Map<String, DifficultyPreset> defaultDifficulties() {
		Map<String, DifficultyPreset> map = new LinkedHashMap<>();
		map.put("easy", new DifficultyPreset(0.75, 0.8, 0.75));
		map.put("normal", new DifficultyPreset(1, 1, 1));
		map.put("hard", new DifficultyPreset(1.25, 1.25, 1.2));
		map.put("nightmare", new DifficultyPreset(1.6, 1.6, 1.5));
		return map;
	}
}
