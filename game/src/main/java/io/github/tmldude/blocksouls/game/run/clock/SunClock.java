package io.github.tmldude.blocksouls.game.run.clock;

import net.minecraft.server.MinecraftServer;

import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * The sun-bound siege clock: the overworld's world clock, exactly as v1 read it. The run system still owns
 * the clock's rate and empty-server pausing; this only reads it for the scheduler.
 */
public final class SunClock implements SiegeClock {
	private final MinecraftServer server;

	public SunClock(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public long cycleTime() {
		return server.overworld().getOverworldClockTime();
	}

	@Override
	public float rate() {
		return RunSystem.clockRate(server);
	}

	@Override
	public void skipTo(int timeOfDay) {
		RunSystem.skipTo(server, timeOfDay);
	}

	@Override
	public boolean synthetic() {
		return false;
	}
}
