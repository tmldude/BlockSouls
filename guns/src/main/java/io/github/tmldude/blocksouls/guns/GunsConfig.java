package io.github.tmldude.blocksouls.guns;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/** {@code config/blocksouls/guns.json}. Times are ticks unless named in seconds; chances are 0..1. */
public class GunsConfig {
	/** Per-weapon stats. Each entry replaces that weapon's defaults whole; an invalid entry is ignored (logged). */
	public Map<String, GunStats> weapons = new LinkedHashMap<>(Weapons.defaults());

	public Rarity rarity = new Rarity();
	public Perks perks = new Perks();
	public Shooting shooting = new Shooting();
	public Loot loot = new Loot();

	public static class Rarity {
		public double commonDamageMult = 1.0;
		public double rareDamageMult = 1.1;
		public double legendaryDamageMult = 1.2;
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
	}

	public static class Loot {
		/** Vanilla chest tables that get a gun pool and an ammo pool. */
		public Map<String, ChestLoot> chests = defaultChests();
		/** Weights of each droppable gun in loot pools. */
		public Map<String, Integer> gunWeights = defaultGunWeights();

		/** Wave kill drops (game's {@code wave_drops/<tier>} tables): chance per kill. */
		public double waveCommonPrimaryChance = 0.12;
		public double waveCommonSpecialChance = 0.03;
		public double waveCommonGunChance = 0.01;
		public double waveRarePrimaryChance = 0.15;
		public double waveRareSpecialChance = 0.06;
		public double waveRareGunChance = 0.02;
		public double waveLegendaryPrimaryChance = 0.18;
		public double waveLegendarySpecialChance = 0.10;
		public double waveLegendaryGunChance = 0.04;
		/** Wave-clear reward chest: one gun (rare tier) at this chance, plus ammo. */
		public double waveClearGunChance = 0.5;
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

	private static Map<String, Integer> defaultGunWeights() {
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put(Weapons.HAND_CANNON, 30);
		weights.put(Weapons.AUTO_RIFLE, 30);
		weights.put(Weapons.SHOTGUN, 20);
		weights.put(Weapons.SNIPER_RIFLE, 20);
		return weights;
	}
}
