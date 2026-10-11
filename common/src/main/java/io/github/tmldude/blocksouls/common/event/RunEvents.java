package io.github.tmldude.blocksouls.common.event;

import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import io.github.tmldude.blocksouls.common.run.RunState;

/** Run lifecycle events, fired by the game module on the server thread. */
public final class RunEvents {
	public static final Event<StateChange> RUN_STARTED = stateChange();
	/** Everyone went down at once. {@code state} is the final state (phase OVER). */
	public static final Event<StateChange> RUN_OVER = stateChange();
	/** The final boss died; waves continue forever from here. */
	public static final Event<StateChange> ENDLESS_STARTED = stateChange();

	public static final Event<Night> DUSK = night();
	public static final Event<Night> DAWN = night();

	public static final Event<WaveStarted> WAVE_STARTED = EventFactory.createArrayBacked(WaveStarted.class,
			callbacks -> (server, wave) -> {
				for (WaveStarted callback : callbacks) {
					callback.onWaveStarted(server, wave);
				}
			});

	/** {@code cleared} is false when dawn ended the wave first. */
	public static final Event<WaveEnded> WAVE_ENDED = EventFactory.createArrayBacked(WaveEnded.class,
			callbacks -> (server, wave, cleared) -> {
				for (WaveEnded callback : callbacks) {
					callback.onWaveEnded(server, wave, cleared);
				}
			});

	private RunEvents() {
	}

	@FunctionalInterface
	public interface StateChange {
		void onChange(MinecraftServer server, RunState state);
	}

	@FunctionalInterface
	public interface Night {
		void onNight(MinecraftServer server, int night);
	}

	@FunctionalInterface
	public interface WaveStarted {
		void onWaveStarted(MinecraftServer server, int wave);
	}

	@FunctionalInterface
	public interface WaveEnded {
		void onWaveEnded(MinecraftServer server, int wave, boolean cleared);
	}

	private static Event<StateChange> stateChange() {
		return EventFactory.createArrayBacked(StateChange.class, callbacks -> (server, state) -> {
			for (StateChange callback : callbacks) {
				callback.onChange(server, state);
			}
		});
	}

	private static Event<Night> night() {
		return EventFactory.createArrayBacked(Night.class, callbacks -> (server, night) -> {
			for (Night callback : callbacks) {
				callback.onNight(server, night);
			}
		});
	}
}
