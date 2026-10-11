package io.github.tmldude.blocksouls.bosses.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import io.github.tmldude.blocksouls.bosses.registry.BossBlocks;

/**
 * A ring of {@link FogWallBlock}s around an arena center. Only replaces air and replaceable plants (never terrain or
 * builds), remembers exactly what it placed, and only removes its own blocks again.
 */
public final class FogWall {
	private final List<BlockPos> placed = new ArrayList<>();

	public boolean isUp() {
		return !placed.isEmpty();
	}

	public List<BlockPos> positions() {
		return placed;
	}

	/** Raises the ring of {@code radius} around {@code center}, {@code below}..{@code above} blocks vertically. */
	public void raise(ServerLevel level, BlockPos center, int radius, int below, int above) {
		lower(level);
		for (int[] column : ring(radius)) {
			Direction inward = inward(column[0], column[1]);
			BlockState wall = BossBlocks.FOG_WALL.defaultBlockState().setValue(FogWallBlock.FACING, inward);
			for (int dy = -below; dy <= above; dy++) {
				BlockPos pos = center.offset(column[0], dy, column[1]);
				if (!level.isInWorldBounds(pos)) {
					continue;
				}
				BlockState current = level.getBlockState(pos);
				if (current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty())) {
					if (level.setBlock(pos, wall, Block.UPDATE_CLIENTS)) {
						placed.add(pos.immutable());
					}
				}
			}
		}
	}

	/** Removes the blocks this wall placed (anything replaced since is left alone). */
	public void lower(ServerLevel level) {
		for (BlockPos pos : placed) {
			if (level.getBlockState(pos).is(BossBlocks.FOG_WALL)) {
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		placed.clear();
	}

	/** Restores a saved list (the blocks are removed on the boss's next tick, see {@code BossEntity}). */
	public void load(List<BlockPos> positions) {
		placed.clear();
		placed.addAll(positions);
	}

	/** Column offsets on a 1-block-thick circle of {@code radius}, no gaps a player could squeeze through. */
	public static List<int[]> ring(int radius) {
		Set<Long> seen = new LinkedHashSet<>();
		List<int[]> columns = new ArrayList<>();
		int samples = Math.max(16, (int) Math.ceil(2 * Math.PI * radius * 4));
		for (int i = 0; i < samples; i++) {
			double a = 2 * Math.PI * i / samples;
			int x = (int) Math.round(Math.cos(a) * radius);
			int z = (int) Math.round(Math.sin(a) * radius);
			if (seen.add(((long) x << 32) ^ (z & 0xffffffffL))) {
				columns.add(new int[] {x, z});
			}
		}
		return columns;
	}

	/** The horizontal direction pointing from column (x, z) back toward the center. */
	public static Direction inward(int x, int z) {
		if (Math.abs(x) >= Math.abs(z)) {
			return x > 0 ? Direction.WEST : Direction.EAST;
		}
		return z > 0 ? Direction.NORTH : Direction.SOUTH;
	}
}
