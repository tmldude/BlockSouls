package io.github.tmldude.blocksouls.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.WorldConfig;

/**
 * Placement modifiers whose numbers come from {@code world.json}, so counts and rarities are tunable without a
 * datapack. Used as {@code {"type": "blocksouls_world:config_count", "key": "gunmetal_ore"}}.
 */
public final class ConfigPlacement {
	private ConfigPlacement() {
	}

	/** Repeats the feature {@code count(key)} times. */
	public record Count(String key) implements RepeatingPlacement {
		public static final MapCodec<Count> CODEC = Codec.STRING.fieldOf("key").xmap(Count::new, Count::key);

		@Override
		public int count(RandomSource random, BlockPos origin) {
			return ConfigPlacement.count(BlockSoulsWorld.CONFIG.gen, key);
		}

		@Override
		public MapCodec<Count> codec() {
			return CODEC;
		}
	}

	/** Places on average once every {@code rarity(key)} chunks; never if that's 0. */
	public record Rarity(String key) implements PlacementFilter {
		public static final MapCodec<Rarity> CODEC = Codec.STRING.fieldOf("key").xmap(Rarity::new, Rarity::key);

		@Override
		public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
			int chance = ConfigPlacement.rarity(BlockSoulsWorld.CONFIG.gen, key);
			return chance > 0 && random.nextInt(chance) == 0;
		}

		@Override
		public MapCodec<Rarity> codec() {
			return CODEC;
		}
	}

	public static int count(WorldConfig.Gen gen, String key) {
		return switch (key) {
			case "gunmetal_ore" -> gen.gunmetalVeinsPerChunk;
			case "blight_dense" -> gen.blightPatchesDense;
			case "blight_sparse" -> gen.blightPatchesSparse;
			default -> throw new IllegalArgumentException("Unknown config count " + key);
		};
	}

	public static int rarity(WorldConfig.Gen gen, String key) {
		return switch (key) {
			case "scrap_pile" -> gen.scrapPileRarityChunks;
			default -> throw new IllegalArgumentException("Unknown config rarity " + key);
		};
	}
}
