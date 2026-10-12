package io.github.tmldude.blocksouls.world;

import java.util.ArrayList;
import java.util.List;

/** {@code config/blocksouls/world.json}. Distances are blocks, times are ticks, chances are 0..1. */
public class WorldConfig {
	/** Defaults revision {@link #migrate} brings older files up to. */
	public static final int CURRENT_DEFAULTS = 1;

	/**
	 * Which defaults revision this file was written against. Missing in files from before the field existed (reads as
	 * 0), so {@link #migrate} can update values that were never changed by hand.
	 */
	public int defaultsVersion;
	public Gen gen = new Gen();
	public Loot loot = new Loot();
	public Enchants enchants = new Enchants();
	public Armor armor = new Armor();
	public AshFog ashFog = new AshFog();

	/**
	 * Brings a config written against older defaults up to date: every value still at its old default takes the new one,
	 * anything changed by hand is kept. Returns whether anything changed (so the caller saves the file).
	 */
	public static boolean migrate(WorldConfig config) {
		if (config.defaultsVersion >= CURRENT_DEFAULTS) {
			return false;
		}
		// 1: the playtest resources pass (v0.11): about twice the ore, scrap and material drops.
		if (config.defaultsVersion < 1) {
			Gen gen = config.gen;
			if (gen.gunmetalVeinsPerChunk == 7) {
				gen.gunmetalVeinsPerChunk = 14;
			}
			if (gen.scrapPileRarityChunks == 24) {
				gen.scrapPileRarityChunks = 12;
			}
			Loot loot = config.loot;
			if (loot.waveCommonChance == 0.25) {
				loot.waveCommonChance = 0.5;
			}
			if (loot.waveRareChance == 0.4) {
				loot.waveRareChance = 0.8;
			}
			if (loot.waveLegendaryChance == 0.6) {
				loot.waveLegendaryChance = 1.0;
			}
			for (String chest : List.of("blocksouls_world:chests/wreck", "blocksouls_world:chests/checkpoint")) {
				if (!loot.materialChests.contains(chest)) {
					loot.materialChests.add(chest);
				}
			}
		}
		config.defaultsVersion = CURRENT_DEFAULTS;
		return true;
	}

	public static class Gen {
		/** Gunmetal veins per chunk (iron's middle band has 10; gunmetal is the gun economy, so it's more common). */
		public int gunmetalVeinsPerChunk = 14;
		/**
		 * Wrecks: one attempt per this many chunks square (12 = one per 12×12 chunks), at least the separation apart.
		 * Read at world load / {@code /reload}. 0 disables them.
		 */
		public int wreckSpacingChunks = 12;
		public int wreckSeparationChunks = 4;
		/** Checkpoints: one attempt per 24×24 chunks. 0 disables them. */
		public int checkpointSpacingChunks = 24;
		public int checkpointSeparationChunks = 8;
		/** Loose scrap piles: on average one per this many overworld chunks. 0 disables them. */
		public int scrapPileRarityChunks = 12;
		/** Extra scrap piles strewn around each wreck / checkpoint, on top of the debris in its layout. 0 for none. */
		public int wreckExtraScrapPiles = 3;
		public int checkpointExtraScrapPiles = 4;
		/** Blight patches per Nether chunk in crimson forests and nether wastes. */
		public int blightPatchesDense = 3;
		/** Blight patches per chunk in the other Nether biomes. */
		public int blightPatchesSparse = 1;
		/** Chance a blight patch embeds a blight heart. */
		public double blightHeartChance = 0.12;
	}

	public static class Loot {
		/** Vanilla chests that get a roll of scrap/gunmetal. */
		public List<String> materialChests = new ArrayList<>(List.of(
				"minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
				"minecraft:chests/village/village_weaponsmith", "minecraft:chests/village/village_toolsmith",
				"minecraft:chests/village/village_armorer", "minecraft:chests/village/village_plains_house",
				"minecraft:chests/village/village_desert_house", "minecraft:chests/village/village_savanna_house",
				"minecraft:chests/village/village_taiga_house", "minecraft:chests/village/village_snowy_house",
				"minecraft:chests/desert_pyramid", "minecraft:chests/jungle_temple", "minecraft:chests/shipwreck_supply",
				"minecraft:chests/pillager_outpost", "minecraft:chests/ruined_portal", "minecraft:chests/igloo_chest",
				"minecraft:chests/bastion_other", "minecraft:chests/nether_bridge",
				"blocksouls_world:chests/wreck", "blocksouls_world:chests/checkpoint"));
		/** Chance per roll that a material chest gets a scrap/gunmetal stack; rolled {@link #materialRolls} times. */
		public double materialChance = 0.6;
		public int materialRolls = 2;
		/** Vanilla chests that can hold a wacky enchanted book. */
		public List<String> bookChests = new ArrayList<>(List.of(
				"minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
				"minecraft:chests/stronghold_library", "minecraft:chests/stronghold_corridor",
				"minecraft:chests/stronghold_crossing", "minecraft:chests/bastion_treasure",
				"minecraft:chests/bastion_other", "minecraft:chests/bastion_bridge", "minecraft:chests/ancient_city",
				"minecraft:chests/end_city_treasure"));
		public double bookChance = 0.25;
		/** Chance a wreck / checkpoint chest also holds a gun (when guns is installed). */
		public double wreckGunChance = 0.2;
		public double checkpointGunChance = 0.5;
		/** Chance per wave kill of an extra materials roll, by tier. */
		public double waveCommonChance = 0.5;
		public double waveRareChance = 0.8;
		public double waveLegendaryChance = 1.0;
		/** Endermen drop this many extra pearls (Looting still adds on top via the vanilla pool). */
		public int endermanExtraPearls = 1;
		public double endermanEyeChance = 0.15;
		/** Added to the eye chance per Looting level. */
		public double endermanEyeLootingBonus = 0.05;
	}

	public static class Enchants {
		/** Bounce speed kept per ricochet. */
		public double bounceDamping = 0.7;
		public float combustionRadius = 2.0F;
		/** Health restored per Vampiric level on a kill (2 = one heart). */
		public float vampiricHealPerLevel = 2.0F;
		public double grappleRange = 30.0;
		/** Launch speed of a grapple pull (blocks/tick), scaled down for short distances. */
		public double grappleSpeed = 1.6;
		public double magnetRadiusLevel1 = 6.0;
		public double magnetRadiusLevel2 = 10.0;
		/** Pull speed toward the player (blocks/tick). */
		public double magnetSpeed = 0.35;
	}

	public static class Armor {
		/** Scrap-plate full set: fraction of explosion damage removed. */
		public float scrapExplosionReduction = 0.3F;
		/** Scrap-plate full set: knockback resistance added (0.1 shows as "+1"). */
		public double scrapKnockbackResistance = 0.1;
		/** Blight-forged full set: damage dealt back to attackers. */
		public float blightThornsDamage = 2.0F;
	}

	public static class AshFog {
		public boolean enabled = true;
		/** Fog end distance in the Nether (vanilla Nether fog ends around 96). */
		public float fogEnd = 48.0F;
		public float fogStart = 4.0F;
		/** Fog color blend toward ash gray (0 = vanilla color, 1 = full ash). */
		public float ashBlend = 0.6F;
		/** Ash particles spawned around the player per client tick. */
		public int particlesPerTick = 3;
	}
}
