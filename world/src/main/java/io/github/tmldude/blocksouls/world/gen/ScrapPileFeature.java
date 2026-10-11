package io.github.tmldude.blocksouls.world.gen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import io.github.tmldude.blocksouls.world.content.WorldBlocks;

/** A loose cluster of 1–3 scrap piles on dry, solid ground (roadside junk). */
public record ScrapPileFeature() implements Feature {
	public static final MapCodec<ScrapPileFeature> CODEC = MapCodec.unit(ScrapPileFeature::new);

	@Override
	public MapCodec<ScrapPileFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		int count = 1 + random.nextInt(3);
		boolean any = false;
		for (int i = 0; i < count; i++) {
			BlockPos pos = StructurePlacer.groundAt(level, origin.getX() + random.nextInt(5) - 2, origin.getZ() + random.nextInt(5) - 2);
			if (pos != null && level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
				level.setBlock(pos, WorldBlocks.SCRAP_PILE.defaultBlockState(), 2);
				any = true;
			}
		}
		return any;
	}
}
