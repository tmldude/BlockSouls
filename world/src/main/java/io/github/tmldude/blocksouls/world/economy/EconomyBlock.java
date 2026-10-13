package io.github.tmldude.blocksouls.world.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.event.EconomyEvents;

/**
 * A camp or event object of the run economy (blood altar, cursed chest, challenge pylon, camp horn). The world only
 * owns the block: using it fires {@link EconomyEvents#CAMP_OBJECT_USED} (sneak-use is the alternate use) and the
 * game decides what happens, removing single-use objects itself. Unbreakable in survival and drops nothing.
 */
public class EconomyBlock extends Block {
	private final CampObject object;

	public EconomyBlock(CampObject object, Properties properties) {
		super(properties);
		this.object = object;
	}

	public CampObject object() {
		return object;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer serverPlayer
				&& !EconomyEvents.CAMP_OBJECT_USED.invoker().onUsed(serverPlayer, object, pos, player.isShiftKeyDown())) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.blocksouls_world.economy.nothing"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		ParticleOptions particle = switch (object) {
			case BLOOD_ALTAR -> ParticleTypes.CRIMSON_SPORE;
			case CURSED_CHEST -> ParticleTypes.SOUL;
			case CHALLENGE_PYLON -> ParticleTypes.FLAME;
			default -> null;
		};
		if (particle == null || random.nextInt(3) != 0) {
			return;
		}
		level.addParticle(particle, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
				0.0, object == CampObject.CHALLENGE_PYLON ? 0.03 : 0.01, 0.0);
	}
}
