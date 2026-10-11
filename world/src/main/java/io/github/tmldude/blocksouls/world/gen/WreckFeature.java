package io.github.tmldude.blocksouls.world.gen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import io.github.tmldude.blocksouls.world.gen.layout.WreckLayout;

/** Roadside wreck. Rarity and biomes come from the placed feature / biome modification. */
public record WreckFeature() implements Feature {
	public static final MapCodec<WreckFeature> CODEC = MapCodec.unit(WreckFeature::new);

	@Override
	public MapCodec<WreckFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		BlockPos ground = Sites.flatGround(level, origin, 2, 2);
		if (ground == null) {
			return false;
		}
		StructurePlacer.Placed placed = StructurePlacer.place(level, random, ground, WreckLayout.generate(random.nextLong()), StructurePlacer.WRECK_LOOT);
		StructureLog.record(level.getLevel(), StructureLog.Kind.WRECK, placed.origin());
		return true;
	}
}
