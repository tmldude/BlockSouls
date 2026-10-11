package io.github.tmldude.blocksouls.game.run;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.run.RunPhase;

/** Sends {@link RunSyncPayload} on change, every second while a countdown runs, and on join. */
public final class RunSync {
	private static boolean dirty = true;
	private static RunSyncPayload last;

	private RunSync() {
	}

	public static void markDirty() {
		dirty = true;
	}

	static void tick(MinecraftServer server, RunData data) {
		long now = server.overworld().getGameTime();
		boolean everySecond = now % (20L * Math.max(1, RunSystem.config().syncIntervalSeconds)) == 0;
		if (!dirty && !everySecond) {
			return;
		}
		RunSyncPayload payload = build(server, data);
		if (!payload.equals(last)) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Networking.sendToPlayer(player, payload);
			}
			last = payload;
		}
		dirty = false;
	}

	static void sendTo(ServerPlayer player, MinecraftServer server, RunData data) {
		Networking.sendToPlayer(player, build(server, data));
	}

	static RunSyncPayload build(MinecraftServer server, RunData data) {
		RunConfig config = RunSystem.config();
		long dayTime = server.overworld().getOverworldClockTime();
		float rate = RunSystem.clockRate(server);
		int secondsToNext = switch (data.phase) {
			case WAVE_BREAK -> (int) Math.ceil(Math.max(0, data.breakEndsAt - server.overworld().getGameTime()) / 20.0);
			case DAY -> (int) Math.ceil(WaveMath.secondsUntil(dayTime, config.duskTime, rate));
			default -> -1;
		};
		int mobsLeft = data.phase == RunPhase.WAVE ? WaveManager.mobsLeft(data) : -1;
		return new RunSyncPayload(data.state(), secondsToNext, mobsLeft, List.copyOf(data.nightModifiers), config.duskCueTime, config.duskTime,
				config.dawnTime, (float) config.fogDistance);
	}
}
