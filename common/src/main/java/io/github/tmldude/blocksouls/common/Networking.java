package io.github.tmldude.blocksouls.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Typed play-phase packets. Call the {@code register*} methods from a mod's main entrypoint
 * (both sides), then register client receivers via {@code ClientNetworking}.
 */
public final class Networking {
	private Networking() {
	}

	public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String namespace, String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(namespace, path));
	}

	/** Registers a client-to-server payload and its server-side handler. */
	public static <T extends CustomPacketPayload> void registerServerbound(
			CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			ServerPlayNetworking.PlayPayloadHandler<T> handler) {
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type, handler);
	}

	/** Registers a server-to-client payload. The client handler is registered separately in client code. */
	public static <T extends CustomPacketPayload> void registerClientbound(
			CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
	}

	/** Sends to a player if their client can receive it (i.e. has the mod installed). */
	public static boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		if (!ServerPlayNetworking.canSend(player, payload.type())) {
			return false;
		}

		ServerPlayNetworking.send(player, payload);
		return true;
	}
}
