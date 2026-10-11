package io.github.tmldude.blocksouls.world.gen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.tmldude.blocksouls.world.content.WorldBlocks;

/** The infection: turns netherrack into blighted netherrack and dresses it with growths, veins and hearts. */
public final class Blight {
	private Blight() {
	}

	/** Result of one infection, for tests and the debug command. */
	public record Result(int converted, int growths, int veins, BlockPos heart) {
	}

	public static boolean infectable(BlockState state) {
		return state.is(Blocks.NETHERRACK) || state.is(BlockTags.NYLIUM);
	}

	/**
	 * Infects a rough ball of {@code radius} around {@code center}. With probability {@code heartChance} one exposed
	 * blighted block becomes a blight heart.
	 */
	public static Result infect(LevelAccessor level, BlockPos center, int radius, RandomSource random, double heartChance) {
		List<BlockPos> converted = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double r2 = (radius + 0.5) * (radius + 0.5);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double d2 = dx * dx + dy * dy + dz * dz;
					// Ragged edge: the outer shell converts only some of the time.
					if (d2 > r2 || d2 > r2 * 0.55 && random.nextInt(3) == 0) {
						continue;
					}
					pos.setWithOffset(center, dx, dy, dz);
					if (level.isOutsideBuildHeight(pos) || !infectable(level.getBlockState(pos))) {
						continue;
					}
					level.setBlock(pos, WorldBlocks.BLIGHTED_NETHERRACK.defaultBlockState(), 2);
					converted.add(pos.immutable());
				}
			}
		}

		int growths = 0;
		int veins = 0;
		List<BlockPos> exposed = new ArrayList<>();
		for (BlockPos block : converted) {
			BlockPos above = block.above();
			if (level.getBlockState(above).isAir()) {
				exposed.add(block);
				if (random.nextInt(4) == 0) {
					level.setBlock(above, WorldBlocks.BLIGHT_GROWTH.defaultBlockState(), 2);
					growths++;
					continue;
				}
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos air = block.relative(side);
				if (!level.getBlockState(air).isAir()) {
					continue;
				}
				exposed.add(block);
				if (random.nextInt(5) == 0) {
					// The vein sits in the air block and clings to the face pointing back at the wall.
					level.setBlock(air, WorldBlocks.BLIGHT_VEINS.defaultBlockState()
							.setValue(MultifaceBlock.getFaceProperty(side.getOpposite()), true), 2);
					veins++;
				}
				break;
			}
		}

		BlockPos heart = null;
		if (!exposed.isEmpty() && random.nextDouble() < heartChance) {
			heart = exposed.get(random.nextInt(exposed.size()));
			level.setBlock(heart, WorldBlocks.BLIGHT_HEART.defaultBlockState(), 2);
		}
		return new Result(converted.size(), growths, veins, heart);
	}
}
