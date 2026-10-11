package io.github.tmldude.blocksouls.game.run;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import io.github.tmldude.blocksouls.common.run.RunPhase;

/** The {@code /bs} commands as a Java API, for game tests and other debugging (server thread). */
public final class RunDebug {
	private RunDebug() {
	}

	/** Forgets the run entirely (no run started, no mobs, daytime) so the next join auto-starts a fresh one. */
	public static void reset(MinecraftServer server) {
		RunData data = RunData.get(server);
		WaveManager.discardAll(server, data);
		data.reset();
		data.wasNight = false;
		RunSystem.skipTo(server, 1000);
		RunSync.markDirty();
	}

	/** {@code /bs wave start [n]}: starts wave {@code n} (or the next one) right now. */
	public static void startWave(MinecraftServer server, int n) {
		RunData data = RunData.get(server);
		if (data.phase == RunPhase.WAVE) {
			WaveManager.endWave(server, data, false);
		}
		if (n > 0) {
			data.wave = n - 1;
		}
		WaveManager.startWave(server, data);
	}

	/** {@code /bs wave end}: ends the current wave as dawn would. */
	public static void endWave(MinecraftServer server) {
		WaveManager.endWave(server, RunData.get(server), false);
	}

	/** {@code /bs wave clear}: kills the current wave's mobs. */
	public static int clearWave(MinecraftServer server) {
		return WaveManager.killWave(server, RunData.get(server));
	}

	/** {@code /bs time dusk|dawn|night|day}: moves the overworld clock forward to that time of day. */
	public static void skipTo(MinecraftServer server, int timeOfDay) {
		RunSystem.skipTo(server, timeOfDay);
	}

	/** Live wave mob ids of the current wave. */
	public static Set<UUID> waveMobs(MinecraftServer server) {
		return Set.copyOf(RunData.get(server).waveMobs);
	}

	/** Elites still hunting after their wave ended. */
	public static Set<UUID> huntingElites(MinecraftServer server) {
		return Set.copyOf(RunData.get(server).hunters);
	}

	public static int mobsLeft(MinecraftServer server) {
		return WaveManager.mobsLeft(RunData.get(server));
	}

	public static boolean pistolsBroken(MinecraftServer server) {
		return RunData.get(server).pistolsBroken;
	}

	public static Set<UUID> gotKit(MinecraftServer server) {
		return Set.copyOf(RunData.get(server).gotKit);
	}
}
