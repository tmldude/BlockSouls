package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Floor 6, the final arena: a flat ring of polished blackstone with a low wall, pillars round the edge and a 3×3
 * crying-obsidian trigger plate at the centre (the {@code final_arena} structure slot's position). Bosses spawns the
 * final boss when the party steps on the plate; the camp sits just outside the ring.
 */
final class ArenaFloor {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** Air cleared over the ring so nothing the island grew blocks the fight. */
	private static final int HEADROOM = 16;

	private ArenaFloor() {
	}

	/** Builds the arena centred on {@code centre} (the first air block over the ground). */
	static void build(ServerLevel level, BlockPos centre, int radius) {
		int r = Math.max(8, radius);
		BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState inlay = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.sqrt((double) x * x + (double) z * z);
				if (d > r + 1) {
					continue;
				}
				// Two ground layers so the ring stays solid however thin the island is here.
				BlockState ground = Math.abs(d - r * 0.5) < 0.5 || Math.abs(d - r + 1.5) < 0.5 ? inlay : floor;
				level.setBlock(pos.set(centre.getX() + x, centre.getY() - 1, centre.getZ() + z), ground, FLAGS);
				level.setBlock(pos.set(centre.getX() + x, centre.getY() - 2, centre.getZ() + z), Blocks.DEEPSLATE.defaultBlockState(), FLAGS);
				for (int y = 0; y < HEADROOM; y++) {
					level.setBlock(pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z), air, FLAGS);
				}
				if (d > r) {
					level.setBlock(pos.set(centre.getX() + x, centre.getY(), centre.getZ() + z), wall, FLAGS);
				}
			}
		}
		pillars(level, centre, r);
		plate(level, centre);
	}

	/** Eight pillars just inside the wall, soul lanterns on top: cover, and the ring's silhouette. */
	private static void pillars(ServerLevel level, BlockPos centre, int r) {
		BlockState stone = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		double reach = r - 3;
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4 + Math.PI / 8;
			BlockPos base = centre.offset((int) Math.round(Math.cos(angle) * reach), 0, (int) Math.round(Math.sin(angle) * reach));
			int height = i % 2 == 0 ? 6 : 4;
			for (int y = 0; y < height; y++) {
				level.setBlock(base.above(y), stone, FLAGS);
			}
			level.setBlock(base.above(height), Blocks.SOUL_LANTERN.defaultBlockState(), FLAGS);
		}
	}

	/** The 3×3 trigger plate, set into the ground at the centre. */
	private static void plate(ServerLevel level, BlockPos centre) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(centre.offset(x, -1, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), FLAGS);
			}
		}
	}
}
