package io.github.tmldude.blocksouls.common.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworking {
	private ClientNetworking() {
	}

	/** Registers the client handler for a payload registered with {@code Networking.registerClientbound}. */
	public static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
		ClientPlayNetworking.registerGlobalReceiver(type, handler);
	}

	/** Sends to the server if it can receive it (i.e. has the mod installed). */
	public static boolean sendToServer(CustomPacketPayload payload) {
		if (!ClientPlayNetworking.canSend(payload.type())) {
			return false;
		}

		ClientPlayNetworking.send(payload);
		return true;
	}
}
