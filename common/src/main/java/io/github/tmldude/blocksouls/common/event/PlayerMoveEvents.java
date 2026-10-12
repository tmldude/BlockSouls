package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Per-player movement events, fired by the movement module on the server thread. */
public final class PlayerMoveEvents {
	/** A dodge roll started (the server accepted it and spent the stamina). Origins and boons react here. */
	public static final Event<Player> ROLLED = player();

	/** A glide started (the player left the ground on their wings). */
	public static final Event<Player> GLIDE_STARTED = player();

	private PlayerMoveEvents() {
	}

	@FunctionalInterface
	public interface Player {
		void onPlayer(ServerPlayer player);
	}

	private static Event<Player> player() {
		return EventFactory.createArrayBacked(Player.class, callbacks -> player -> {
			for (Player callback : callbacks) {
				callback.onPlayer(player);
			}
		});
	}
}
