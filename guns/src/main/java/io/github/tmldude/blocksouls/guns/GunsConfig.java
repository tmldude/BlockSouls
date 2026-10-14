package io.github.tmldude.blocksouls.guns;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/** {@code config/blocksouls/guns.json}. Times are ticks unless named in seconds; chances are 0..1. */
public class GunsConfig {
	/** Defaults revision {@link #migrate} brings older files up to. */
	public static final int CURRENT_DEFAULTS = 2;

	/** Which defaults revision this file was written against (0 = before the field existed). */
	public int defaultsVersion;

	/** Per-weapon stats. Each entry replaces that weapon's defaults whole; an invalid entry is ignored (logged). */
	public Map<String, GunStats> weapons = new LinkedHashMap<>(Weapons.defaults());

	public Rarity rarity = new Rarity();
	public Perks perks = new Perks();
	public Shooting shooting = new Shooting();
	public Loot loot = new Loot();
	public Exotics exotics = new Exotics();
	public Drops drops = new Drops();

	/**
	 * Brings a guns.json written against older defaults up to date: values still at an old default take the new one,
	 * anything changed by hand is kept. Returns whether anything changed (so the caller saves the file).
	 */
	public static boolean migrate(GunsConfig config) {
		if (config.defaultsVersion >= CURRENT_DEFAULTS) {
			return false;
		}
		// 1: drops-and-pacing (v0.11): about twice the ammo from waves.
		if (config.defaultsVersion < 1 && config.loot != null) {
			Loot loot = config.loot;
			loot.waveCommonPrimaryChance = bump(loot.waveCommonPrimaryChance, 0.12, 0.25);
			loot.waveCommonSpecialChance = bump(loot.waveCommonSpecialChance, 0.03, 0.06);
			loot.waveRarePrimaryChance = bump(loot.waveRarePrimaryChance, 0.15, 0.30);
			loot.waveRareSpecialChance = bump(loot.waveRareSpecialChance, 0.06, 0.12);
			loot.waveLegendaryPrimaryChance = bump(loot.waveLegendaryPrimaryChance, 0.18, 0.35);
			loot.waveLegendarySpecialChance = bump(loot.waveLegendarySpecialChance, 0.10, 0.20);
		}
		// 2: floor secrets (Stage 6C) join the exotic tables; a table the file already lists keeps its chance.
		if (config.defaultsVersion < 2 && config.loot != null && config.loot.exoticTables != null) {
			defaultExoticTables().forEach(config.loot.exoticTables::putIfAbsent);
		}
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
	}

	private static double bump(double value, double oldDefault, double newDefault) {
		return value == oldDefault ? newDefault : value;
	}

	public static class Rarity {
		public double commonDamageMult = 1.0;
		public double rareDamageMult = 1.1;
		public double legendaryDamageMult = 1.2;
		public double exoticDamageMult = 1.2;
		/** Odds [common, rare, legendary] for guns with no rolled rarity (crafted, {@code /give}). */
		public double[] craftedOdds = {70, 25, 5};
		/** Odds for {@code roll_perks} with {@code "tier": "common"}. */
		public double[] commonTierOdds = {75, 22, 3};
		public double[] rareTierOdds = {30, 55, 15};
		public double[] legendaryTierOdds = {0, 40, 60};
	}

	public static class Perks {
		public int outlawDurationTicks = 100;
		/** Reload speed multiplier while Outlaw is active (1.5 = 50% faster). */
		public double outlawReloadSpeed = 1.5;
		public double rampageBonusPerStack = 0.10;
		public int rampageMaxStacks = 3;
		public int rampageDurationTicks = 80;
		/** Extra damage dealt as a small explosion on precision hits, as a share of the hit's damage. */
		public double explosiveShare = 0.30;
		public double explosiveRadius = 1.5;
		public double vorpalMult = 1.20;
	}

	public static class Shooting {
		/** A shot may arrive this many ticks early (packets bunched into one tick) without raising the fire rate. */
		public double fireRateTolerance = 1.0;
		/** Reload speed bonus per {@code blocksouls:reload_speed} trait level. */
		public double reloadSpeedPerLevel = 0.25;
		/** Damage kept per ricochet (Bouncy). */
		public double bounceDamageMult = 0.7;
		/** Entity hitboxes are inflated by this much for the hitscan sweep. */
		public double hitboxMargin = 0.3;
		/** Walk speed multiplier while aiming down sights (vanilla item use is 0.2). */
		public double adsMoveSpeed = 0.6;
		/** A dodge roll finishes a reload that is at least this far along (0..1); 1 disables reload-cancel. */
		public double reloadCancelThreshold = 0.8;
	}

	public static class Loot {
		/** Vanilla chest tables that get a gun pool and an ammo pool. */
		public Map<String, ChestLoot> chests = defaultChests();
		/** Weights of each droppable gun in loot pools. */
		public Map<String, Integer> gunWeights = defaultGunWeights();

		/** Wave kill drops (game's {@code wave_drops/<tier>} tables): chance per kill. */
		public double waveCommonPrimaryChance = 0.25;
		public double waveCommonSpecialChance = 0.06;
		public double waveCommonGunChance = 0.01;
		public double waveRarePrimaryChance = 0.30;
		public double waveRareSpecialChance = 0.12;
		public double waveRareGunChance = 0.02;
		public double waveLegendaryPrimaryChance = 0.35;
		public double waveLegendarySpecialChance = 0.20;
		public double waveLegendaryGunChance = 0.04;
		/** Rounds per wave-kill ammo drop. */
		public int wavePrimaryMin = 8;
		public int wavePrimaryMax = 20;
		public int waveSpecialMin = 3;
		public int waveSpecialMax = 6;
		/** Wave-clear reward chest (per reward roll): one gun (rare tier) at this chance, plus ammo. */
		public double waveClearGunChance = 0.5;
		public int waveClearPrimaryMin = 24;
		public int waveClearPrimaryMax = 48;
		public int waveClearSpecialMin = 6;
		public int waveClearSpecialMax = 12;
		/** Elite kills (game's {@code wave_drops/elite} table, per roll): a gun at this chance and tier, plus ammo. */
		public double eliteGunChance = 0.35;
		public String eliteGunTier = "rare";
		public int elitePrimaryMin = 16;
		public int elitePrimaryMax = 32;
		/** The night-2 pity gun's {@code roll_perks} tier. */
		public String pityGunTier = "rare";
		/** Max stack sizes (applied at startup). Vanilla allows up to 99. */
		public int primaryAmmoStack = 99;
		public int specialAmmoStack = 64;

		/** Weights of each exotic when a {@code roll_perks} entry with {@code "tier": "exotic"} picks one. */
		public Map<String, Integer> exoticWeights = defaultExoticWeights();
		/** Loot tables that get an extra exotic pool, with the chance per roll. Boss drops and the legendary wave tier. */
		public Map<String, Double> exoticTables = defaultExoticTables();
	}

	/** Each exotic's own numbers (base stats are in {@code weapons}). */
	public static class Exotics {
		/** Stray Verdict: the last round in the mag ricochets to a second target within this range, at this damage share. */
		public double strayVerdictRange = 10;
		public double strayVerdictDamageMult = 0.8;
		/** Lantern Eye: precision kills mark hostiles within the radius; marked targets take extra damage from any gun. */
		public double lanternEyeMarkRadius = 8;
		public int lanternEyeMarkTicks = 160;
		public double lanternEyeMarkedDamageMult = 1.25;
		/** Backdraft: each shot launches the shooter backward with this speed (blocks/tick) and a little lift. */
		public double backdraftLaunch = 0.9;
		public double backdraftLift = 0.25;
		/** Kindling Engine: each hit adds heat; heat decays per tick; at 100 the gun vents for a while. Fire rate ramps with heat. */
		public double kindlingHeatPerHit = 9;
		public double kindlingHeatDecayPerTick = 1.2;
		public int kindlingVentTicks = 50;
		/** Fire-rate multiplier at full heat (1.8 = 80% faster). */
		public double kindlingMaxRateMult = 1.8;
		/** Heat above which hits set the target on fire, and for how long. */
		public double kindlingIgniteHeat = 60;
		public int kindlingIgniteTicks = 60;
		/** Tether Lance: the rocket bursts at the first thing it hits; nearby hostiles take blast damage and the two nearest are tethered. */
		public double tetherBlastRadius = 3.5;
		public double tetherBlastDamage = 12;
		public int tetherTicks = 60;
		public double tetherPull = 0.18;
	}

	/** Rare and legendary guns on the ground: a colored particle beam, glow outline, rarity-colored name and a sound sting. */
	public static class Drops {
		public boolean beam = true;
		/** Ticks between beam particle columns, and the column's height in blocks. */
		public int beamIntervalTicks = 8;
		public double beamHeight = 6;
		public boolean glow = true;
		public boolean nameplate = true;
		public boolean sting = true;
	}

	/**
	 * @param tier       {@code common}, {@code rare} or {@code legendary}: the {@code roll_perks} tier
	 * @param gunChance  chance per chest of one gun
	 * @param ammoChance chance per chest of an ammo stack
	 */
	public record ChestLoot(String tier, double gunChance, double ammoChance) {
	}

	private static Map<String, ChestLoot> defaultChests() {
		Map<String, ChestLoot> chests = new LinkedHashMap<>();
		chests.put("minecraft:chests/village/village_weaponsmith", new ChestLoot("common", 0.15, 0.40));
		chests.put("minecraft:chests/simple_dungeon", new ChestLoot("common", 0.12, 0.40));
		chests.put("minecraft:chests/abandoned_mineshaft", new ChestLoot("common", 0.08, 0.35));
		chests.put("minecraft:chests/desert_pyramid", new ChestLoot("rare", 0.12, 0.35));
		chests.put("minecraft:chests/jungle_temple", new ChestLoot("rare", 0.12, 0.35));
		chests.put("minecraft:chests/pillager_outpost", new ChestLoot("rare", 0.15, 0.45));
		chests.put("minecraft:chests/stronghold_corridor", new ChestLoot("rare", 0.10, 0.35));
		chests.put("minecraft:chests/stronghold_crossing", new ChestLoot("rare", 0.10, 0.35));
		chests.put("minecraft:chests/bastion_treasure", new ChestLoot("legendary", 0.15, 0.40));
		chests.put("minecraft:chests/bastion_other", new ChestLoot("rare", 0.10, 0.35));
		chests.put("minecraft:chests/bastion_bridge", new ChestLoot("rare", 0.10, 0.35));
		chests.put("minecraft:chests/ancient_city", new ChestLoot("legendary", 0.12, 0.40));
		chests.put("minecraft:chests/end_city_treasure", new ChestLoot("legendary", 0.15, 0.40));
		return chests;
	}

	private static Map<String, Integer> defaultExoticWeights() {
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put(Weapons.STRAY_VERDICT, 1);
		weights.put(Weapons.LANTERN_EYE, 1);
		weights.put(Weapons.BACKDRAFT, 1);
		weights.put(Weapons.KINDLING_ENGINE, 1);
		weights.put(Weapons.TETHER_LANCE, 1);
		return weights;
	}

	private static Map<String, Double> defaultExoticTables() {
		Map<String, Double> tables = new LinkedHashMap<>();
		tables.put("blocksouls_bosses:boss_drops/legendary_gun", 0.2);
		tables.put("blocksouls_bosses:boss_drops/final_dragon_guns", 1.0);
		tables.put("blocksouls_game:wave_drops/legendary", 0.01);
		// --- floor secrets (Stage 6C): one line per floor session ---
		tables.put("blocksouls_world:chests/soulfire_vault", 1.0); // v2-floor2-infernal: behind the soulfire door
		// --- end floor secrets ---
		return tables;
	}

	private static Map<String, Integer> defaultGunWeights() {
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put(Weapons.HAND_CANNON, 30);
		weights.put(Weapons.AUTO_RIFLE, 30);
		weights.put(Weapons.SHOTGUN, 20);
		weights.put(Weapons.SNIPER_RIFLE, 20);
		return weights;
	}
}
