package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

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

	// --- PR 3: the vault room's strongroom and the hidden stair ---
	/** The vault room's loot: one piece of the floor's gear set. */
	static final ResourceKey<LootTable> TREASURE_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/vault_treasure");
	/** Marks where the secret mini-boss wakes: a tile of the chamber floor under its spot, swapped out when it does. */
	static final Block SEAL = Blocks.REINFORCED_DEEPSLATE;

	/**
	 * The vault room (the floor's structure): a 5×5 strongroom of vault walls, roofed, its door a two-high lock that takes
	 * the three vault keys (found in crates in other rooms) on the side facing the camp; the treasure chest inside.
	 */
	void vaultRoom(FloorBuilder builder, BlockPos pos) {
		Direction door = towardCamp(pos);
		BlockState wall = VaultBlocks.VAULT_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
				for (int y = 0; y <= 3; y++) {
					level.setBlock(pos.offset(x, y, z), rim || y == 3 ? wall : air, FLAGS);
				}
			}
		}
		BlockPos doorway = pos.relative(door, 2);
		level.setBlock(doorway, VaultBlocks.VAULT_LOCK.defaultBlockState(), FLAGS);
		level.setBlock(doorway.above(), VaultBlocks.VAULT_LOCK.defaultBlockState(), FLAGS);
		level.setBlock(pos.relative(door.getOpposite()).above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
		builder.chest(pos.relative(door.getOpposite()), TREASURE_LOOT);
	}

	/** The side of a spot that faces the camp hall (along the longer axis). */
	private Direction towardCamp(BlockPos pos) {
		var camps = layout.rooms("camp");
		if (camps.isEmpty()) {
			return Direction.NORTH;
		}
		int dx = centre.getX() + camps.getFirst().cx() - pos.getX();
		int dz = centre.getZ() + camps.getFirst().cz() - pos.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * The secret room behind the hollow wall: a lantern and, in a corner, the hidden stair ({@link VaultSecret}) climbing
	 * through the rock to a chamber over the room, where the seal marks the second mini-boss's spot (it wakes when the
	 * hollow wall bursts). Without headroom for the chamber, a secret chest instead.
	 */
	void hollowSecret(FloorBuilder builder, BlockPos pos) {
		VaultSecret secret = VaultSecret.plan(layout, template.box, template.height);
		if (secret == null) {
			level.setBlock(pos.offset(-1, 0, -1), Blocks.LANTERN.defaultBlockState(), FLAGS);
			builder.chest(pos.offset(1, 0, 1), FloorBuilder.SECRET_LOOT);
			return;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState pillar = VaultBlocks.VAULT_BRICKS.defaultBlockState();
		BoxLayout.Room room = secret.room();
		for (int x = room.x0(); x <= room.x1(); x++) {
			for (int z = room.z0(); z <= room.z1(); z++) {
				if (!secret.inShaft(x, z)) {
					level.setBlock(at(x, secret.floorDy(), z), floor, FLAGS);
				}
				for (int dy = secret.floorDy() + 1; dy <= secret.floorDy() + secret.chamberHeight(); dy++) {
					level.setBlock(at(x, dy, z), air, FLAGS);
				}
			}
		}
		for (int x = secret.shaftX() - 1; x <= secret.shaftX() + 1; x++) {
			for (int z = secret.shaftZ() - 1; z <= secret.shaftZ() + 1; z++) {
				boolean core = x == secret.shaftX() && z == secret.shaftZ();
				for (int dy = 1; dy <= secret.floorDy(); dy++) {
					level.setBlock(at(x, dy, z), core ? pillar : air, FLAGS);
				}
			}
		}
		for (int k = 1; k <= secret.steps(); k++) {
			int[] step = secret.step(k);
			level.setBlock(at(step[0], step[1], step[2]), floor, FLAGS);
		}
		int[] boss = secret.bossSpot();
		level.setBlock(at(boss[0], boss[1] - 1, boss[2]), SEAL.defaultBlockState(), FLAGS);
		level.setBlock(at(boss[0], boss[1], boss[2]).above(secret.chamberHeight() - 1),
				Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
		int lx = pos.getX() - centre.getX() - 1;
		int lz = pos.getZ() - centre.getZ() - 1;
		if (!secret.inShaft(lx, lz)) {
			level.setBlock(pos.offset(-1, 0, -1), Blocks.LANTERN.defaultBlockState(), FLAGS);
		}
	}

	/** A layout column at a height above the ground, in the world. */
	private BlockPos at(int x, int dy, int z) {
		return new BlockPos(centre.getX() + x, groundY + dy, centre.getZ() + z);
	}
	// --- end PR 3 ---

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
