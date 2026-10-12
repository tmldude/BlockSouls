package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Movement events, fired by the movement module on the server thread, for build rules keyed on movement. */
public final class MovementEvents {
	/** A player started a dodge roll. The roll's direction is the player's current delta movement. */
	public static final Event<Rolled> ROLLED = EventFactory.createArrayBacked(Rolled.class, callbacks -> player -> {
		for (Rolled callback : callbacks) {
			callback.onRolled(player);
		}
	});

	private MovementEvents() {
	}

	@FunctionalInterface
	public interface Rolled {
		void onRolled(ServerPlayer player);
	}
}
