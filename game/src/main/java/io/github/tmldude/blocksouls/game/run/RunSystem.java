package io.github.tmldude.blocksouls.game.run;

import net.minecraft.server.MinecraftServer;

/**
 * Owned by the "game-loop" session (docs/sessions/game-loop.md): run lifecycle, day/night, waves, wave drops,
 * starter kit, sleep and dimension rules, difficulty, endless mode. Installs the {@code Runs} provider.
 */
public final class RunSystem {
	private RunSystem() {
	}

	public static void init() {
	}

	/**
	 * Ends the active run: phase becomes {@code OVER}, waves stop, {@code RunEvents.RUN_OVER} fires and every player
	 * sees the run summary. Called by the player system when everyone is down at once. No-op if no run is active.
	 */
	public static void endRun(MinecraftServer server) {
	}
}
