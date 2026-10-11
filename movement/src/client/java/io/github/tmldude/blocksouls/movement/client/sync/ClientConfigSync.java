package io.github.tmldude.blocksouls.movement.client.sync;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.sync.ConfigSync;

/**
 * Uses the server's movement config while connected and goes back to this client's own file on disconnect. In
 * single-player the integrated server sends the same file's values, so nothing changes.
 */
public final class ClientConfigSync {
	private ClientConfigSync() {
	}

	public static void register() {
		ClientNetworking.receive(ConfigSync.Payload.TYPE, (payload, context) -> {
			MovementConfig server = MovementConfig.fromJson(payload.json());
			if (server == null) {
				BlockSoulsMovement.LOGGER.warn("Server sent a movement config that doesn't parse; keeping local values");
				return;
			}
			BlockSoulsMovement.CONFIG.copyFrom(server);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
				BlockSoulsMovement.CONFIG.copyFrom(Config.load(ConfigSync.FILE, MovementConfig.class, MovementConfig::new)));
	}
}
