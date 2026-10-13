package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Skyrift's sky temple (floor 3): three stacked open tiers (11, 9 and 7 wide, {@link #TIER_HEIGHT} apart) of calcite
 * on quartz pillars, a ladder up a central pillar, and on the roof the top chest (feather armor or a long-range gun)
 * and the glowing seed's pedestal. Built facing the same way every time, so the roof spots are fixed offsets.
 */
public final class SkyTemple {
	/** The structure slot tag that builds it, and the secret slot tag of its seed's camp altar. */
	public static final String TAG = "sky_temple";
	public static final String SEED_SECRET = "sky_seed";
	public static final ResourceKey<LootTable> LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/sky_temple");
	static final int TIERS = 3;
	static final int TIER_HEIGHT = 6;

	private SkyTemple() {
	}

	/** Where you stand on the roof (the air block over its centre). */
	public static BlockPos roof(BlockPos base) {
		return base.above(TIERS * TIER_HEIGHT);
	}

	public static BlockPos pedestal(BlockPos base) {
		return roof(base).offset(1, 0, 1);
	}

	public static BlockPos chest(BlockPos base) {
		return roof(base).offset(-1, 0, -1);
	}

	/** Builds the temple on {@code base} (the air block over the ground at its centre); returns the top chest. */
	static BlockPos build(ServerLevel level, BlockPos base, int flags) {
		BlockState floor = Blocks.CALCITE.defaultBlockState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int tier = 0; tier < TIERS; tier++) {
			int half = 5 - tier;
			int y0 = tier * TIER_HEIGHT;
			for (int x = -half; x <= half; x++) {
				for (int z = -half; z <= half; z++) {
					level.setBlock(base.offset(x, y0 - 1, z), floor, flags);
					for (int y = y0; y < y0 + TIER_HEIGHT - 1; y++) {
						boolean corner = Math.abs(x) == half && Math.abs(z) == half;
						level.setBlock(base.offset(x, y, z), corner ? pillar : air, flags);
					}
				}
			}
			level.setBlock(base.offset(-half + 1, y0 + TIER_HEIGHT - 2, half - 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
					flags);
		}
		int roofY = TIERS * TIER_HEIGHT - 1;
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlock(base.offset(x, roofY, z), Math.abs(x) == 2 && Math.abs(z) == 2 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : floor,
						flags);
				for (int y = roofY + 1; y <= roofY + 3; y++) {
					level.setBlock(base.offset(x, y, z), air, flags);
				}
			}
		}
		// The way up: a ladder on the west face of a central pillar, through every tier's floor and the roof.
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
		for (int y = 0; y <= roofY; y++) {
			level.setBlock(base.offset(1, y, 0), pillar, flags);
			level.setBlock(base.offset(0, y, 0), ladder, flags);
		}
		level.setBlock(roof(base).offset(-2, 0, 2), Blocks.LANTERN.defaultBlockState(), flags);
		level.setBlock(pedestal(base), FloorBlocks.SEED_PEDESTAL.defaultBlockState(), flags);
		BlockPos chest = chest(base);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), flags);
		RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(base.asLong()), chest, LOOT);
		return chest;
	}
}
