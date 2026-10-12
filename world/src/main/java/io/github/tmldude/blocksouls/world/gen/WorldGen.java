package io.github.tmldude.blocksouls.world.gen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Feature types, placement types and the biome modifications that put our placed features in the world, plus the
 * wreck / checkpoint structures (their biomes and spacing are in {@code worldgen/structure} and {@code structure_set}).
 */
public final class WorldGen {
	public static final ResourceKey<PlacedFeature> GUNMETAL_ORE = placed("ore_gunmetal");
	public static final ResourceKey<PlacedFeature> SCRAP_PILES = placed("scrap_piles");
	public static final ResourceKey<PlacedFeature> BLIGHT_DENSE = placed("blight_patch_dense");
	public static final ResourceKey<PlacedFeature> BLIGHT_SPARSE = placed("blight_patch_sparse");

	public static final ResourceKey<Structure> WRECK = structure("wreck");
	public static final ResourceKey<Structure> CHECKPOINT = structure("checkpoint");
	/** Wrecks and checkpoints, for explorer maps / compasses (namespace {@code blocksouls} like CommonTags). */
	public static final TagKey<Structure> RUINS = TagKey.create(Registries.STRUCTURE, CommonIds.id("blocksouls", "ruins"));

	public static final StructureType<RuinStructure> RUIN_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			BlockSoulsWorld.id("ruin"), () -> RuinStructure.CODEC);
	public static final StructurePieceType RUIN_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			BlockSoulsWorld.id("ruin"), (StructurePieceType.ContextlessType) RuinPiece::new);

	private WorldGen() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BlockSoulsWorld.id("scrap_piles"), ScrapPileFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, BlockSoulsWorld.id("blight_patch"), BlightPatchFeature.CODEC);
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, BlockSoulsWorld.id("config_count"), ConfigPlacement.Count.CODEC);
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, BlockSoulsWorld.id("config_rarity"), ConfigPlacement.Rarity.CODEC);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, BlockSoulsWorld.id("config_spread"), ConfigSpreadPlacement.CODEC);

		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, GUNMETAL_ORE);
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

	private static ResourceKey<Structure> structure(String name) {
		return ResourceKey.create(Registries.STRUCTURE, BlockSoulsWorld.id(name));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, BlockSoulsWorld.id(name));
	}
}
