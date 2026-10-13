package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The inside of a floor's exit portal frame. Unlit, using it starts the act's portal charge ({@code EXIT_PORTAL_USED});
 * lit (the charge completed), walking into it carries the player to the next floor's camp. Unbreakable.
 */
public class FloorPortalBlock extends Block {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public FloorPortalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(LIT) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			WorldFloors.usePortal(serverLevel, pos, state, serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (state.getValue(LIT) && entity instanceof ServerPlayer player && level instanceof ServerLevel serverLevel) {
			WorldFloors.enterPortal(serverLevel, pos, player);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(state.getValue(LIT) ? 1 : 4) == 0) {
			level.addParticle(state.getValue(LIT) ? ParticleTypes.PORTAL : ParticleTypes.ENCHANT, pos.getX() + random.nextDouble(),
					pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.05, 0.0);
		}
	}
}
