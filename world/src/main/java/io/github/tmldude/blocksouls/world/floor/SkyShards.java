package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Skyrift's (floor 3) sky pieces: the floating shards above and around the island, the glow-berry vine strands that hang
 * from the shards over the island (climbable), and the floor's biome painted over its cell so the sky, fog, clouds and
 * grass take the floor's look. Templates without shards or a biome get none of it.
 */
final class SkyShards {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private SkyShards() {
	}

	/** Builds every planned shard: a tapered blob, the template's top/fill/base, and maybe a vine strand. */
	static void build(ServerLevel level, FloorPlanner.FloorPlan plan, FloorTemplate template, BlockPos centre, RandomSource random,
			IntBinaryOperator groundHeight, BlockState top, BlockState fill, BlockState base) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (FloorPlanner.Shard shard : plan.shards()) {
			int topY = plan.groundY() + shard.rise();
			int size = shard.size();
			// Keep headroom under a low shard: its underside stays clear of the island's highest hills.
			int maxDepth = Math.max(2, shard.rise() - (int) Math.ceil(template.hills) - 3);
			for (int dx = -size; dx <= size; dx++) {
				for (int dz = -size; dz <= size; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz) / size;
					if (d > 1.0) {
						continue;
					}
					int depth = Math.min(maxDepth, 1 + (int) Math.round((size * 0.9 + 1) * (1 - d * d)) + ((dx * 7 + dz * 13) & 1));
					for (int y = topY - depth; y <= topY; y++) {
						BlockState state = y == topY ? top : y >= topY - 1 ? fill : base;
						level.setBlock(pos.set(centre.getX() + shard.x() + dx, y, centre.getZ() + shard.z() + dz), state, FLAGS);
					}
				}
			}
			boolean overIsland = (long) shard.x() * shard.x() + (long) shard.z() * shard.z() < (long) (plan.radius() - 3) * (plan.radius() - 3);
			if (overIsland && random.nextDouble() < template.shards.vineChance) {
				vines(level, centre, shard, topY, groundHeight.applyAsInt(shard.x(), shard.z()), random);
			}
		}
	}

	/** A glow-berry strand from under the shard's centre down to two blocks above the ground (climbable both ways). */
	private static void vines(ServerLevel level, BlockPos centre, FloorPlanner.Shard shard, int topY, int ground, RandomSource random) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(centre.getX() + shard.x(), topY, centre.getZ() + shard.z());
		while (pos.getY() > ground && !level.getBlockState(pos).isAir()) {
			pos.move(0, -1, 0);
		}
		int bottom = ground + 3;
		for (int y = pos.getY(); y >= bottom; y--) {
			Block block = y == bottom ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
			BlockState state = block.defaultBlockState().setValue(CaveVines.BERRIES, random.nextInt(4) == 0);
			level.setBlock(pos.setY(y), state, FLAGS);
		}
	}

	/** Paints the template's biome over the floor's cell: the island plus the shards' reach, every height. */
	static void paintBiome(ServerLevel level, FloorTemplate template, FloorPlanner.FloorPlan plan, BlockPos centre) {
		if (template.biome == null || template.biome.isEmpty()) {
			return;
		}
		Identifier id = Identifier.tryParse(template.biome);
		Holder<Biome> biome = id == null ? null
				: level.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, id)).orElse(null);
		if (biome == null) {
			BlockSoulsWorld.LOGGER.warn("Floor template {} names an unknown biome {}", template.id, template.biome);
			return;
		}
		int reach = (int) Math.ceil(plan.radius() * Math.max(1.0, template.shards.maxReach)) + template.shards.maxSize + 16;
		int minX = centre.getX() - reach;
		int maxX = centre.getX() + reach;
		int minZ = centre.getZ() - reach;
		int maxZ = centre.getZ() + reach;
		List<ChunkAccess> chunks = new ArrayList<>();
		for (int cz = SectionPos.blockToSectionCoord(minZ); cz <= SectionPos.blockToSectionCoord(maxZ); cz++) {
			for (int cx = SectionPos.blockToSectionCoord(minX); cx <= SectionPos.blockToSectionCoord(maxX); cx++) {
				ChunkAccess chunk = level.getChunk(cx, cz);
				chunk.fillBiomesFromNoise((qx, qy, qz) -> {
					int x = QuartPos.toBlock(qx);
					int z = QuartPos.toBlock(qz);
					return x >= minX && x <= maxX && z >= minZ && z <= maxZ ? biome : chunk.getNoiseBiome(qx, qy, qz);
				});
				chunk.markUnsaved();
				chunks.add(chunk);
			}
		}
		level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
	}
}
