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

	/** A curse was put on the player (greed objects, debug). */
	public static final Event<Granted> CURSE_APPLIED = granted();
	/** A curse was purged from the player (shopkeeper, altar cleanse). Not fired when going down wipes them. */
	public static final Event<Granted> CURSE_PURGED = granted();
	/** The player took a trinket from an offer. */
	public static final Event<Granted> TRINKET_GRANTED = granted();

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

	@FunctionalInterface
	public interface Granted {
		void onGranted(ServerPlayer player, Identifier id);
	}

	private static Event<Granted> granted() {
		return EventFactory.createArrayBacked(Granted.class, callbacks -> (player, id) -> {
			for (Granted callback : callbacks) {
				callback.onGranted(player, id);
			}
		});
	}

	private static Event<Player> player() {
		return EventFactory.createArrayBacked(Player.class, callbacks -> player -> {
			for (Player callback : callbacks) {
				callback.onPlayer(player);
			}
		});
	}
}
