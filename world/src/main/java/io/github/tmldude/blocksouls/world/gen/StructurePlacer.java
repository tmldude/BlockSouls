package io.github.tmldude.blocksouls.world.gen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldBlocks;
import io.github.tmldude.blocksouls.world.gen.layout.Cell;
import io.github.tmldude.blocksouls.world.gen.layout.Layout;
import io.github.tmldude.blocksouls.world.gen.layout.Part;

/** Turns a {@link Layout} into blocks, loot chests, scrap piles and (if vehicles is loaded) a kart wreck. */
public final class StructurePlacer {
	public static final ResourceKey<LootTable> WRECK_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/wreck");
	public static final ResourceKey<LootTable> CHECKPOINT_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/checkpoint");
	/** How far down a structure's bottom layer is propped up so it doesn't float on uneven ground. */
	private static final int MAX_FOUNDATION = 4;

	private StructurePlacer() {
	}

	/** What got placed, for tests and the locate log. */
	public record Placed(BlockPos origin, List<BlockPos> chests, List<BlockPos> scrapPiles, Entity kart) {
	}

	/**
	 * Ground level at a column: the first position above the topmost motion-blocking, non-leaf block. Returns null over
	 * water or lava so structures never float on liquids.
	 */
	public static BlockPos groundAt(ServerLevelAccessor level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, y, z);
		BlockState below = level.getBlockState(pos.below());
		if (!below.getFluidState().isEmpty() || y <= level.getMinY()) {
			return null;
		}
		return pos;
	}

	public static Placed place(ServerLevelAccessor level, RandomSource random, BlockPos origin, Layout layout, ResourceKey<LootTable> loot) {
		BlockState ground = level.getBlockState(origin.below());
		BlockState foundation = ground.isAir() || ground.is(BlockTags.LEAVES) ? Blocks.COBBLESTONE.defaultBlockState() : ground;
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		List<BlockPos> chests = new ArrayList<>();

		int minY = layout.cells().stream().mapToInt(Cell::y).min().orElse(0);
		for (Cell cell : layout.cells()) {
			BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
			if (cell.part() == Part.CHEST) {
				level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
				RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
				chests.add(pos);
			} else {
				level.setBlock(pos, state(cell.part(), random), 2);
			}
			if (cell.y() == minY || cell.y() == 0) {
				fillBelow(level, pos.below(), foundation);
			}
		}

		List<BlockPos> piles = new ArrayList<>();
		for (int[] pile : layout.scrapPiles()) {
			BlockPos pos = groundAt(level, origin.getX() + pile[0], origin.getZ() + pile[1]);
			if (pos != null && Math.abs(pos.getY() - origin.getY()) <= 3 && level.getBlockState(pos.below()).isSolid()) {
				level.setBlock(pos, WorldBlocks.SCRAP_PILE.defaultBlockState(), 2);
				piles.add(pos);
			}
		}

		Entity kart = null;
		if (layout.kartSpot() != null) {
			kart = spawnKartWreck(level, origin.offset(layout.kartSpot()[0], 0, layout.kartSpot()[1]), random);
		}
		return new Placed(origin, chests, piles, kart);
	}

	private static void fillBelow(ServerLevelAccessor level, BlockPos start, BlockState foundation) {
		BlockPos.MutableBlockPos pos = start.mutable();
		for (int i = 0; i < MAX_FOUNDATION; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return;
			}
			level.setBlock(pos, foundation, 2);
			pos.move(Direction.DOWN);
		}
	}

	/** Places the vehicles module's kart wreck if that entity type exists; otherwise nothing. */
	public static Entity spawnKartWreck(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(CommonIds.KART_WRECK).orElse(null);
		if (type == null) {
			return null;
		}
		ServerLevel serverLevel = level.getLevel();
		Entity entity = type.create(serverLevel, EntitySpawnReason.STRUCTURE);
		if (entity == null) {
			return null;
		}
		BlockPos ground = groundAt(level, pos.getX(), pos.getZ());
		BlockPos at = ground != null && Math.abs(ground.getY() - pos.getY()) <= 3 ? ground : pos;
		entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		level.addFreshEntity(entity);
		return entity;
	}

	private static BlockState state(Part part, RandomSource random) {
		return switch (part) {
			case HULL -> random.nextInt(5) == 0 ? Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
			case CHASSIS -> random.nextInt(4) == 0 ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
			case GRILLE, FENCE -> Blocks.IRON_BARS.defaultBlockState();
			case GLASS -> Blocks.GLASS_PANE.defaultBlockState();
			case WALL -> Blocks.STONE_BRICKS.defaultBlockState();
			case WALL_CRACKED -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			case WALL_MOSSY -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			case CONCRETE -> Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
			case SANDBAG -> Blocks.WOOL.pick(DyeColor.BROWN).defaultBlockState();
			case SANDBAG_MUD -> Blocks.MUD_BRICKS.defaultBlockState();
			case TOWER_POST -> Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
			case TOWER_FLOOR -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case CHEST -> Blocks.CHEST.defaultBlockState();
			case AIR -> Blocks.AIR.defaultBlockState();
		};
	}
}
