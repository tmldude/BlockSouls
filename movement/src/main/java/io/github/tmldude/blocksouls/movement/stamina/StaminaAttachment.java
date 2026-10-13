package io.github.tmldude.blocksouls.movement.stamina;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.tmldude.blocksouls.common.player.Stamina;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;
import io.github.tmldude.blocksouls.movement.MovementConfig;

/** Server-authoritative stamina on each player, synced to that player only. Not saved: full on login. */
public final class StaminaAttachment {
	private static final StreamCodec<ByteBuf, StaminaState> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, StaminaState::value,
			ByteBufCodecs.VAR_INT, StaminaState::regenDelay,
			StaminaState::new);

	private static AttachmentType<StaminaState> type;
	private static MovementConfig.Stamina config;

	private StaminaAttachment() {
	}

	public static void register(MovementConfig.Stamina staminaConfig) {
		config = staminaConfig;
		type = AttachmentRegistry.create(
				Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "stamina"),
				builder -> builder.syncWith(CODEC, AttachmentSyncPredicate.targetOnly()));
	}

	/** The player's stamina; full if nothing has been stored or synced yet. */
	public static StaminaState get(Player player) {
		StaminaState state = ((AttachmentTarget) player).getAttached(type);
		return state != null ? state : StaminaState.full(config);
	}

	public static void set(Player player, StaminaState state) {
		((AttachmentTarget) player).setAttached(type, state);
	}

	/**
	 * Installs {@code common}'s {@link Stamina} provider: other modules add or drain stamina through it (a headshot
	 * refund, an ability cost), clamped to [0, the player's max]; a drain restarts the regen delay like any spend.
	 */
	public static void installProvider() {
		Stamina.setProvider((player, amount) -> {
			StaminaState current = get(player);
			double max = MovementTraits.staminaMax(player);
			float next = (float) Math.clamp(current.value() + amount, 0.0, max);
			if (next == current.value()) {
				return false;
			}
			set(player, amount < 0 ? new StaminaState(next, config.regenDelayTicks) : new StaminaState(next, current.regenDelay()));
			return true;
		});
	}
}
