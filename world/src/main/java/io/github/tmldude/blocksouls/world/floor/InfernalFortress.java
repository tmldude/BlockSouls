package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Floor 2's structure and secret (v2-floor2-infernal). The fortress stub is three nether-brick rooms in a row: a gate,
 * the hall with the bastion chest (heat armor or a gun), and a vault sealed by a soulfire door. A water bucket from the
 * floor's one water pocket (a spring on another lobe, so it's carried over a bridge) douses the door; the vault's chest
 * holds an exotic (guns adds it to {@link #VAULT_LOOT} through its {@code exoticTables}).
 */
public final class InfernalFortress {
	/** The structure slot tag that builds the fortress. */
	public static final String TAG = "fortress";
	/** The secret slot tag of the water pocket. */
	public static final String WATER_POCKET = "water_pocket";
	/** The secret slot tag the fortress records at its soulfire door. */
	public static final String SOULFIRE_DOOR = "soulfire_door";
	public static final ResourceKey<LootTable> BASTION_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/infernal_bastion");
	public static final ResourceKey<LootTable> VAULT_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/soulfire_vault");

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** Along the fortress: walls at these offsets, rooms between (gate −7..−4, hall −2..2, vault 4..7). */
	private static final int[] WALLS = {-8, -3, 3, 8};
	private static final int HALF_WIDTH = 3;
	private static final int HEIGHT = 5;

	private InfernalFortress() {
	}

	static void build(FloorBuilder builder, ServerLevel level, BlockPos pos, RandomSource random) {
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction side = along.getClockWise();
		BlockState bricks = Blocks.NETHER_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_NETHER_BRICKS.defaultBlockState();
		BlockState trim = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		BlockState vaultWall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		for (int a = WALLS[0]; a <= WALLS[3]; a++) {
			for (int s = -HALF_WIDTH; s <= HALF_WIDTH; s++) {
				boolean wallA = a == WALLS[0] || a == WALLS[1] || a == WALLS[2] || a == WALLS[3];
				boolean wallS = Math.abs(s) == HALF_WIDTH;
				level.setBlock(at(pos, along, side, a, -1, s), trim, FLAGS);
				// The corners reach past the flattened slot: a footing down to the ground so nothing floats.
				for (int y = -2; y >= -6 && level.getBlockState(at(pos, along, side, a, y, s)).canBeReplaced(); y--) {
					level.setBlock(at(pos, along, side, a, y, s), bricks, FLAGS);
				}
				for (int y = 0; y <= HEIGHT + 2; y++) {
					BlockPos here = at(pos, along, side, a, y, s);
					BlockState state = air;
					if (y < HEIGHT && (wallA || wallS)) {
						// The gate and hall are a ruin: their walls crumble toward the top; the vault stays whole.
						boolean whole = a >= WALLS[2] || y < 3 || random.nextInt(4) != 0;
						state = !whole ? air : a >= WALLS[2] ? vaultWall : random.nextInt(6) == 0 ? cracked : bricks;
					} else if (y == HEIGHT && (a >= WALLS[2] || (wallA || wallS) && random.nextInt(3) == 0)) {
						state = a >= WALLS[2] ? vaultWall : bricks; // the vault's roof; crenellations elsewhere
					}
					level.setBlock(here, state, FLAGS);
				}
			}
		}
		// Doorways: the gate's mouth and the gate-to-hall arch, three high.
		for (int y = 0; y <= 2; y++) {
			level.setBlock(at(pos, along, side, WALLS[0], y, 0), air, FLAGS);
			level.setBlock(at(pos, along, side, WALLS[1], y, 0), air, FLAGS);
		}
		// The sealed soulfire door between the hall and the vault, two high, soul lanterns either side.
		BlockState seal = FloorBlocks.SOULFIRE_SEAL.defaultBlockState();
		level.setBlock(at(pos, along, side, WALLS[2], 0, 0), seal, FLAGS);
		level.setBlock(at(pos, along, side, WALLS[2], 1, 0), seal, FLAGS);
		BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState();
		level.setBlock(at(pos, along, side, WALLS[2] - 1, 0, -2), lantern, FLAGS);
		level.setBlock(at(pos, along, side, WALLS[2] - 1, 0, 2), lantern, FLAGS);
		level.setBlock(at(pos, along, side, 6, HEIGHT - 1, 0), lantern.setValue(LanternBlock.HANGING, true), FLAGS);
		// Set dressing: fences in the gate, a soul campfire in the hall.
		level.setBlock(at(pos, along, side, -6, 0, -2), Blocks.NETHER_BRICK_FENCE.defaultBlockState(), FLAGS);
		level.setBlock(at(pos, along, side, -6, 0, 2), Blocks.NETHER_BRICK_FENCE.defaultBlockState(), FLAGS);
		level.setBlock(at(pos, along, side, -1, 0, -2), Blocks.SOUL_CAMPFIRE.defaultBlockState(), FLAGS);

		builder.chest(at(pos, along, side, 1, 0, 2), BASTION_LOOT);
		builder.chest(at(pos, along, side, 6, 0, 0), VAULT_LOOT);
		builder.record(new FloorSlot(FloorSlotKind.SECRET, SOULFIRE_DOOR, at(pos, along, side, WALLS[2] - 1, 0, 0), 2));
	}

	/** The water pocket: a 3×3 spring (an infinite source) sunk in a clay basin, soul lanterns at two corners. */
	static void waterPocket(ServerLevel level, BlockPos pos) {
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean pool = Math.abs(x) <= 1 && Math.abs(z) <= 1;
				level.setBlock(pos.offset(x, -2, z), Blocks.CLAY.defaultBlockState(), FLAGS);
				level.setBlock(pos.offset(x, -1, z), pool ? Blocks.WATER.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState(), FLAGS);
				for (int y = 0; y <= 2; y++) {
					level.setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
		level.setBlock(pos.offset(2, 0, 2), Blocks.SOUL_LANTERN.defaultBlockState(), FLAGS);
		level.setBlock(pos.offset(-2, 0, -2), Blocks.SOUL_LANTERN.defaultBlockState(), FLAGS);
	}

	private static BlockPos at(BlockPos pos, Direction along, Direction side, int a, int y, int s) {
		return pos.relative(along, a).relative(side, s).above(y);
	}
}
