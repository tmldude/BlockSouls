package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * The Vault's Director events (floor 4), placed through {@link DirectorEvents#PLACE_EVENT} when the Director rolls one
 * of the floor-tagged ids in its pool. {@code vault_prison}: an iron-bar cage holding a golem ally (break it out and it
 * fights for you); the game arms an ambush at the spot. {@code trapped_hall}: a grid of flame grates firing in waves
 * between you and a chest. Nothing here is saved, like the Director's own event state.
 */
public final class VaultEvents {
	public static final Identifier VAULT_PRISON = BlockSoulsWorld.id("vault_prison");
	public static final Identifier TRAPPED_HALL = BlockSoulsWorld.id("trapped_hall");
	static final ResourceKey<LootTable> TRAPPED_HALL_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/vault_trapped_hall");
	/** The trapped hall's grate grid: columns across, rows along (the chest sits past the last row). */
	static final int HALL_WIDTH = 5;
	public static final int HALL_ROWS = 7;
	/** How far behind the row before it each row's cycle starts (ticks): the wave of flame runs down the hall. */
	static final int ROW_DELAY = 10;
	private static final int FLAGS = Block.UPDATE_ALL;

	private VaultEvents() {
	}

	static void init() {
		DirectorEvents.PLACE_EVENT.register((level, event, pos, floor) -> place(level, event, pos));
	}

	/** Builds one of the Vault's events at {@code pos} (the air over the event spot); false for any other id. */
	public static boolean place(ServerLevel level, Identifier event, BlockPos pos) {
		if (event.equals(VAULT_PRISON)) {
			return prison(level, pos);
		}
		if (event.equals(TRAPPED_HALL)) {
			trappedHall(level, pos);
			return true;
		}
		return false;
	}

	/** A 5×5 iron-bar cage (3×3 inside), three high and roofed, around a golem that fights for the players once it is out. */
	static boolean prison(ServerLevel level, BlockPos pos) {
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 0; y <= 3; y++) {
					boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
					level.setBlock(pos.offset(x, y, z), edge ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
		// let each bar join its neighbours (placed with setBlock, they start as posts)
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 3, 2))) {
			BlockState state = level.getBlockState(at);
			if (state.is(Blocks.IRON_BARS)) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockState neighbour = level.getBlockState(at.relative(side));
					state = state.setValue(IronBarsBlock.PROPERTY_BY_DIRECTION.get(side), neighbour.is(Blocks.IRON_BARS));
				}
				level.setBlock(at, state, Block.UPDATE_CLIENTS);
			}
		}
		IronGolem golem = EntityTypes.IRON_GOLEM.spawn(level, pos, EntitySpawnReason.EVENT);
		if (golem == null) {
			return false;
		}
		golem.setPlayerCreated(true); // an ally: it never turns on the players
		golem.setPersistenceRequired();
		return true;
	}

	/** The trapped hall: the floor under {@code pos} becomes a grid of flame grates, a chest past the far row. */
	static void trappedHall(ServerLevel level, BlockPos pos) {
		BlockPos floor = pos.below();
		int half = HALL_WIDTH / 2;
		for (int row = 0; row < HALL_ROWS; row++) {
			for (int x = -half; x <= half; x++) {
				BlockPos at = floor.offset(x, 0, row - HALL_ROWS / 2);
				level.setBlock(at, VaultBlocks.VAULT_FLAME_TRAP.defaultBlockState(), FLAGS);
				VaultFlameTrapBlock.start(level, at, 1 + row * ROW_DELAY);
			}
		}
		BlockPos chest = pos.offset(0, 0, HALL_ROWS / 2 + 1);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), FLAGS);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, TRAPPED_HALL_LOOT);
	}
}
