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

import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
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
}
