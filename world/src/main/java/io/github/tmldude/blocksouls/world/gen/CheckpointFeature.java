package io.github.tmldude.blocksouls.world.gen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import io.github.tmldude.blocksouls.world.gen.layout.CheckpointLayout;

/** Collapsed checkpoint. Rarity and biomes come from the placed feature / biome modification. */
public record CheckpointFeature() implements Feature {
	public static final MapCodec<CheckpointFeature> CODEC = MapCodec.unit(CheckpointFeature::new);

	@Override
	public MapCodec<CheckpointFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		BlockPos ground = Sites.flatGround(level, origin, 5, 3);
		if (ground == null) {
			return false;
		}
		StructurePlacer.Placed placed = StructurePlacer.place(level, random, ground, CheckpointLayout.generate(random.nextLong()), StructurePlacer.CHECKPOINT_LOOT);
		StructureLog.record(level.getLevel(), StructureLog.Kind.CHECKPOINT, placed.origin());
		return true;
	}
}
