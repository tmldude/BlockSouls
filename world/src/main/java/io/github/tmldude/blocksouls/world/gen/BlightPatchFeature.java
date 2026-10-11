package io.github.tmldude.blocksouls.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** A patch of infection around a netherrack surface near the origin. */
public record BlightPatchFeature(int radius) implements Feature {
	public static final MapCodec<BlightPatchFeature> CODEC = Codec.intRange(1, 6).fieldOf("radius")
			.xmap(BlightPatchFeature::new, BlightPatchFeature::radius);

	@Override
	public MapCodec<BlightPatchFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		// Find an infectable block with air next to it, scanning down from the origin.
		BlockPos.MutableBlockPos pos = origin.mutable();
		for (int i = 0; i < 24 && pos.getY() > level.getMinY(); i++, pos.move(0, -1, 0)) {
			if (Blight.infectable(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
				int r = Math.max(1, radius - 1 + random.nextInt(3));
				return Blight.infect(level, pos, r, random, BlockSoulsWorld.CONFIG.gen.blightHeartChance).converted() > 0;
			}
		}
		return false;
	}
}
