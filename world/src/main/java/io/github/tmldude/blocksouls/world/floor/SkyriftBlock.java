package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Skyrift's interactive blocks (floor 3). {@link Kind#PEDESTAL}: the sky temple's seed pedestal ({@code active} = the
 * seed is on it). {@link Kind#ALTAR}: the camp altar the seed is carried to ({@code active} = the seed took root).
 * {@link Kind#WIND_ALTAR}: the Director's wind altar event ({@code active} = not used yet). Unbreakable, no item.
 */
public class SkyriftBlock extends Block {
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public enum Kind {
		PEDESTAL(true), ALTAR(false), WIND_ALTAR(true);

		final boolean activeByDefault;

		Kind(boolean activeByDefault) {
			this.activeByDefault = activeByDefault;
		}
	}

	private final Kind kind;

	public SkyriftBlock(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(ACTIVE, kind.activeByDefault));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		return switch (kind) {
			case ALTAR -> stack.is(SkySecret.seedItem()) ? SkySecret.plant(serverLevel, pos, state, serverPlayer, stack) : InteractionResult.TRY_WITH_EMPTY_HAND;
			case WIND_ALTAR -> SkyEvents.useWindAltar(serverLevel, pos, state, serverPlayer, stack);
			case PEDESTAL -> InteractionResult.TRY_WITH_EMPTY_HAND;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		switch (kind) {
			case PEDESTAL -> SkySecret.take(serverLevel, pos, state, serverPlayer);
			case ALTAR -> serverPlayer.sendOverlayMessage(Component.translatable(state.getValue(ACTIVE)
					? "message.blocksouls_world.sky_seed.rooted" : "message.blocksouls_world.sky_seed.altar_hint"));
			case WIND_ALTAR -> SkyEvents.useWindAltar(serverLevel, pos, state, serverPlayer, ItemStack.EMPTY);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ACTIVE) || random.nextInt(3) != 0) {
			return;
		}
		level.addParticle(kind == Kind.WIND_ALTAR ? ParticleTypes.CLOUD : ParticleTypes.END_ROD, pos.getX() + 0.2 + random.nextDouble() * 0.6,
				pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03, 0.0);
	}
}
