package io.github.tmldude.blocksouls.common.event;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Per-player run events, fired by the game module on the server thread. */
public final class PlayerRunEvents {
	/** The player went down (stays down until revived). */
	public static final Event<Player> DOWNED = player();

	public static final Event<Revived> REVIVED = EventFactory.createArrayBacked(Revived.class, callbacks -> (player, reviver) -> {
		for (Revived callback : callbacks) {
			callback.onRevived(player, reviver);
		}
	});

	public static final Event<BoonGranted> BOON_GRANTED = EventFactory.createArrayBacked(BoonGranted.class,
			callbacks -> (player, boon) -> {
				for (BoonGranted callback : callbacks) {
					callback.onBoonGranted(player, boon);
				}
			});

	private PlayerRunEvents() {
	}

	@FunctionalInterface
	public interface Player {
		void onPlayer(ServerPlayer player);
	}

	@FunctionalInterface
	public interface Revived {
		/** @param reviver null when revived by other means (e.g. an admin command) */
		void onRevived(ServerPlayer player, @Nullable ServerPlayer reviver);
	}

	@FunctionalInterface
	public interface BoonGranted {
		void onBoonGranted(ServerPlayer player, Identifier boon);
	}

	private static Event<Player> player() {
		return EventFactory.createArrayBacked(Player.class, callbacks -> player -> {
			for (Player callback : callbacks) {
				callback.onPlayer(player);
			}
		});
	}
}
