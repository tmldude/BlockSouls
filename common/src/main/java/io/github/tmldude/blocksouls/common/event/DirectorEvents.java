package io.github.tmldude.blocksouls.common.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** The AI Director's day loop (game, server thread). Other modules react with set dressing (sounds, sky, lines). */
public final class DirectorEvents {
	/** A floor's day started: {@code seconds} until the siege comes unless the horn ends it early. */
	public static final Event<DayStarted> DAY_STARTED = EventFactory.createArrayBacked(DayStarted.class, callbacks -> (server, seconds) -> {
		for (DayStarted callback : callbacks) {
			callback.onDayStarted(server, seconds);
		}
	});

	/** A player blew the camp horn with {@code secondsSkipped} of day left ({@code overclock}: the sneak-use). */
	public static final Event<HornBlown> HORN_BLOWN = EventFactory.createArrayBacked(HornBlown.class, callbacks -> (player, secondsSkipped, overclock) -> {
		for (HornBlown callback : callbacks) {
			callback.onHornBlown(player, secondsSkipped, overclock);
		}
	});

	/** A siege objective started (the id is a game objective id, e.g. {@code blocksouls_game:supply_drop}). */
	public static final Event<Objective> OBJECTIVE_STARTED = EventFactory.createArrayBacked(Objective.class, callbacks -> (server, objective, success) -> {
		for (Objective callback : callbacks) {
			callback.onObjective(server, objective, success);
		}
	});

	/** A siege objective ended; {@code success} pays the extra relic. */
	public static final Event<Objective> OBJECTIVE_ENDED = EventFactory.createArrayBacked(Objective.class, callbacks -> (server, objective, success) -> {
		for (Objective callback : callbacks) {
			callback.onObjective(server, objective, success);
		}
	});

	private DirectorEvents() {
	}

	@FunctionalInterface
	public interface DayStarted {
		void onDayStarted(MinecraftServer server, int seconds);
	}

	@FunctionalInterface
	public interface HornBlown {
		void onHornBlown(ServerPlayer player, int secondsSkipped, boolean overclock);
	}

	@FunctionalInterface
	public interface Objective {
		/** {@code success} is always false for {@code OBJECTIVE_STARTED}. */
		void onObjective(MinecraftServer server, Identifier objective, boolean success);
	}
}
