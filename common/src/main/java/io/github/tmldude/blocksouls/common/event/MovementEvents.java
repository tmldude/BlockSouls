package io.github.tmldude.blocksouls.common.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Movement events, fired by the movement module on the server thread. Roll state itself stays inside movement;
 * modules that react to a roll (guns' reload-cancel) listen here instead of reading it.
 */
public final class MovementEvents {
	/** The player's dodge roll started this tick (after stamina was spent). */
	public static final Event<PlayerRunEvents.Player> ROLL_STARTED = EventFactory.createArrayBacked(PlayerRunEvents.Player.class,
			callbacks -> player -> {
				for (PlayerRunEvents.Player callback : callbacks) {
					callback.onPlayer(player);
				}
			});

	private MovementEvents() {
	}
}
