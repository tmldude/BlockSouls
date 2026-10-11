package io.github.tmldude.blocksouls.world.gen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** Feature types, placement types and the biome modifications that put our placed features in the world. */
public final class WorldGen {
	public static final ResourceKey<PlacedFeature> GUNMETAL_ORE = placed("ore_gunmetal");
	public static final ResourceKey<PlacedFeature> WRECK = placed("wreck");
	public static final ResourceKey<PlacedFeature> CHECKPOINT = placed("checkpoint");
	public static final ResourceKey<PlacedFeature> SCRAP_PILES = placed("scrap_piles");
	public static final ResourceKey<PlacedFeature> BLIGHT_DENSE = placed("blight_patch_dense");
	public static final ResourceKey<PlacedFeature> BLIGHT_SPARSE = placed("blight_patch_sparse");

	private WorldGen() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BlockSoulsWorld.id("wreck"), WreckFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BlockSoulsWorld.id("checkpoint"), CheckpointFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BlockSoulsWorld.id("scrap_piles"), ScrapPileFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BlockSoulsWorld.id("blight_patch"), BlightPatchFeature.CODEC);
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, BlockSoulsWorld.id("config_count"), ConfigPlacement.Count.CODEC);
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, BlockSoulsWorld.id("config_rarity"), ConfigPlacement.Rarity.CODEC);

		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, GUNMETAL_ORE);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SAVANNA,
				Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.DESERT, Biomes.TAIGA, Biomes.SNOWY_TAIGA,
				Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.MEADOW),
				GenerationStep.Decoration.SURFACE_STRUCTURES, WRECK);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
						.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
						.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate())
						.and(BiomeSelectors.tag(BiomeTags.IS_BEACH).negate()),
				GenerationStep.Decoration.SURFACE_STRUCTURES, CHECKPOINT);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
						.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
						.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
				GenerationStep.Decoration.VEGETAL_DECORATION, SCRAP_PILES);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.CRIMSON_FOREST, Biomes.NETHER_WASTES),
				GenerationStep.Decoration.UNDERGROUND_DECORATION, BLIGHT_DENSE);
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether()
						.and(BiomeSelectors.includeByKey(Biomes.CRIMSON_FOREST, Biomes.NETHER_WASTES).negate()),
				GenerationStep.Decoration.UNDERGROUND_DECORATION, BLIGHT_SPARSE);
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, BlockSoulsWorld.id(name));
	}
}
