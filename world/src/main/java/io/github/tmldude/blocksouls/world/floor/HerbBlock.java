package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A floor herb (cloudbloom): a glowing plant that pops in one hit, bursting its loot table ({@code nodes/<herb>}) like a
 * node and firing {@code NODE_POPPED}. Unbreakable otherwise, so only a hit harvests it.
 */
public class HerbBlock extends NodeBlock {
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 12.0);

	/** Loot rolls on pop; 0 = the config's {@code nodeBurstRolls}. */
	private final int rolls;

	public HerbBlock(Properties properties) {
		this(properties, 0);
	}

	/** v2-floor5-blight: a herb that rolls its table a fixed number of times (the ghostcap rolls once). */
	public HerbBlock(Properties properties, int rolls) {
		super(properties);
		this.rolls = rolls;
	}

	@Override
	protected int burstRolls() {
		return rolls > 0 ? rolls : super.burstRolls();
	}

	@Override
	protected int hitsToPop() {
		return 1;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE.move(state.getOffset(pos));
	}
}
