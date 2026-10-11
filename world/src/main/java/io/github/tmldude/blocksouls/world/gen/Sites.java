package io.github.tmldude.blocksouls.world.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Site checks for surface structures. */
public final class Sites {
	private Sites() {
	}

	/**
	 * Ground at {@code origin}'s column if the four corners {@code halfSize} away are within {@code maxStep} blocks of
	 * it, dry and not on leaves or logs; null otherwise.
	 */
	public static BlockPos flatGround(ServerLevelAccessor level, BlockPos origin, int halfSize, int maxStep) {
		BlockPos center = StructurePlacer.groundAt(level, origin.getX(), origin.getZ());
		if (center == null || !solidGround(level.getBlockState(center.below()))) {
			return null;
		}
		int[][] corners = {{-halfSize, -halfSize}, {halfSize, -halfSize}, {-halfSize, halfSize}, {halfSize, halfSize}};
		for (int[] c : corners) {
			BlockPos corner = StructurePlacer.groundAt(level, center.getX() + c[0], center.getZ() + c[1]);
			if (corner == null || Math.abs(corner.getY() - center.getY()) > maxStep) {
				return null;
			}
		}
		return center;
	}

	private static boolean solidGround(BlockState state) {
		return state.isSolid() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
	}
}
