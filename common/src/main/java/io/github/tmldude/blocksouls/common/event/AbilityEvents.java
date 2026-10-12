package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;

/** Ability events (v2 abilities), all on the server thread. */
public final class AbilityEvents {
	/**
	 * The player asked to cast the slot (keybind or command). Fired by common; the abilities module listens, checks
	 * readiness and casts. Other listeners must not assume the cast went off — wait for {@link #CAST}.
	 */
	public static final Event<CastRequested> CAST_REQUESTED = EventFactory.createArrayBacked(CastRequested.class,
			callbacks -> (player, slot) -> {
				for (CastRequested callback : callbacks) {
					callback.onCastRequested(player, slot);
				}
			});

	/** An ability went off (fired by the abilities module after its effect, before the cooldown starts). */
	public static final Event<Cast> CAST = EventFactory.createArrayBacked(Cast.class, callbacks -> (player, ability) -> {
		for (Cast callback : callbacks) {
			callback.onCast(player, ability);
		}
	});

	/** The player's ultimate charge just reached full. */
	public static final Event<PlayerRunEvents.Player> ULTIMATE_READY = EventFactory.createArrayBacked(PlayerRunEvents.Player.class,
			callbacks -> player -> {
				for (PlayerRunEvents.Player callback : callbacks) {
					callback.onPlayer(player);
				}
			});

	private AbilityEvents() {
	}

	@FunctionalInterface
	public interface CastRequested {
		void onCastRequested(ServerPlayer player, AbilitySlot slot);
	}

	@FunctionalInterface
	public interface Cast {
		void onCast(ServerPlayer player, AbilityDefinition ability);
	}
}
