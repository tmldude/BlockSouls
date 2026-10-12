package io.github.tmldude.blocksouls.common.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import io.github.tmldude.blocksouls.common.Config;

/** Config lifecycle events, fired on the thread that ran the reload (the server thread for {@code /bs config reload}). */
public final class ConfigEvents {
	/**
	 * A config file was re-read and its values copied into the live instance ({@code config} is the same object
	 * {@link Config#load} returned). Listen here to refresh anything cached from config values, or to re-send a
	 * synced config to players. Not fired when the file failed to parse.
	 */
	public static final Event<Reloaded> RELOADED = EventFactory.createArrayBacked(Reloaded.class,
			callbacks -> (name, config) -> {
				for (Reloaded callback : callbacks) {
					callback.onReloaded(name, config);
				}
			});

	private ConfigEvents() {
	}

	/** {@link Config#reload} that fires {@link #RELOADED}. Returns null on success, otherwise why nothing changed. */
	public static String reload(String name) {
		return Config.reload(name, RELOADED.invoker()::onReloaded);
	}

	@FunctionalInterface
	public interface Reloaded {
		void onReloaded(String name, Object config);
	}
}
