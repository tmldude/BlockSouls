package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carves a box floor (floor 4, The Vault) from its {@link BoxLayout}: a bedrock shell (floor, lid and the four sides at
 * the bound), vault walls everywhere the layout is solid (plain, mossy and cracked, plugs tagged with what they hide),
 * the template's floor block under open columns, and air up to each column's clear height. Dark: only the camp hall is
 * lit.
 */
final class VaultBuilder {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final ServerLevel level;
	private final FloorTemplate template;
	private final BoxLayout layout;
	private final BlockPos centre;
	private final int groundY;
	private final long seed;
	private final BlockState floor;

	VaultBuilder(ServerLevel level, FloorTemplate template, BoxLayout layout, BlockPos centre, int groundY, long seed, BlockState floor) {
		this.level = level;
		this.template = template;
		this.layout = layout;
		this.centre = centre;
		this.groundY = groundY;
		this.seed = seed;
		this.floor = floor;
	}

	BoxLayout layout() {
		return layout;
	}

	void terrain() {
		int r = layout.radius();
		int top = groundY + template.height;
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				boolean side = Math.abs(x) == r + 1 || Math.abs(z) == r + 1;
				int clear = layout.clear(x, z);
				VaultWallBlock.Content content = VaultWallBlock.Content.of(layout.plug(x, z));
				int plugTop = template.box.corridorHeight;
				for (int y = groundY - 1; y <= top; y++) {
					pos.set(centre.getX() + x, y, centre.getZ() + z);
					int dy = y - groundY;
					BlockState state;
					if (side || y == groundY - 1 || y == top) {
						state = bedrock;
					} else if (dy == 0) {
						state = clear > 0 ? floor : wall(x, y, z, VaultWallBlock.Content.NONE);
					} else if (dy <= clear) {
						state = air;
					} else {
						state = wall(x, y, z, dy <= plugTop ? content : VaultWallBlock.Content.NONE);
					}
					level.setBlock(pos, state, FLAGS);
				}
			}
		}
		lightCamp();
	}

	/** A wall block: the variant comes from a hash of the position; shortcut plugs are always cracked (the only tell). */
	private BlockState wall(int x, int y, int z, VaultWallBlock.Content content) {
		Block block = VaultBlocks.VAULT_BRICKS;
		if (content == VaultWallBlock.Content.SHORTCUT) {
			block = VaultBlocks.CRACKED_VAULT_BRICKS;
		} else {
			double roll = hash(x, y, z);
			if (roll < template.box.mossyShare) {
				block = VaultBlocks.MOSSY_VAULT_BRICKS;
			} else if (roll < template.box.mossyShare + template.box.crackedShare) {
				block = VaultBlocks.CRACKED_VAULT_BRICKS;
			}
		}
		return block.defaultBlockState().setValue(VaultWallBlock.CONTENT, content);
	}

	private double hash(int x, int y, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** The vault room (the floor's structure). Its lock and keys come with the structure slice; for now, a chest. */
	void vaultRoom(FloorBuilder builder, BlockPos pos) {
		builder.chest(pos, FloorBuilder.CRATE_LOOT);
	}

	/** The secret room behind the hollow wall: a lantern and the secret chest. */
	void hollowSecret(FloorBuilder builder, BlockPos pos) {
		level.setBlock(pos.offset(-1, 0, -1), Blocks.LANTERN.defaultBlockState(), FLAGS);
		builder.chest(pos.offset(1, 0, 1), FloorBuilder.SECRET_LOOT);
	}

	/** Hanging lanterns in the camp hall's corners: the only light the vault starts with. */
	private void lightCamp() {
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (BoxLayout.Room room : layout.rooms("camp")) {
			int y = groundY + room.height();
			for (int[] corner : new int[][] {{room.x0() + 1, room.z0() + 1}, {room.x0() + 1, room.z1() - 1}, {room.x1() - 1, room.z0() + 1},
					{room.x1() - 1, room.z1() - 1}}) {
				level.setBlock(centre.offset(corner[0], y - centre.getY(), corner[1]), lantern, FLAGS);
			}
		}
	}
}
