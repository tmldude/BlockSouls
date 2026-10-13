package io.github.tmldude.blocksouls.world.floor;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * Debug triggers for the floors' hazards ({@code /bs floor hazard <trigger>}): each floor registers its own (Skyrift's
 * {@code gust} and {@code fall}). A trigger acts on the player who ran it, on the floor they stand on.
 */
public final class FloorHazards {
	/** Fires a hazard at a player; returns a short line for the command's feedback. */
	@FunctionalInterface
	public interface Trigger {
		String fire(ServerPlayer player, PlacedFloor floor);
	}

	private static final Map<String, Trigger> TRIGGERS = new TreeMap<>();

	private FloorHazards() {
	}

	public static void register(String name, Trigger trigger) {
		TRIGGERS.put(name, trigger);
	}

	public static Trigger get(String name) {
		return TRIGGERS.get(name);
	}

	public static Set<String> names() {
		return TRIGGERS.keySet();
	}
}
