package io.github.tmldude.blocksouls.game.run;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.act.Act;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.game.run.clock.SiegeClock;

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
		SiegeClock clock = RunSystem.activeClock(server);
		int secondsToNext = switch (data.phase) {
			case WAVE_BREAK -> (int) Math.ceil(Math.max(0, data.breakEndsAt - server.overworld().getGameTime()) / 20.0);
			case DAY -> (int) Math.ceil(clock.secondsUntil(config.duskTime));
			default -> -1;
		};
		int mobsLeft = data.phase == RunPhase.WAVE ? WaveManager.mobsLeft(data) : -1;
		boolean expedition = RunSystem.isExpedition();
		Act act = ActMachine.current(server);
		int actCount = expedition && !ActMachine.acts().isEmpty() ? ActMachine.acts().size() : 1;
		int chargeSecondsLeft = data.chargingAct > 0
				? (int) Math.ceil(Math.max(0, data.chargeEndsAt - server.overworld().getGameTime()) / 20.0)
				: -1;
		boolean synthetic = expedition && data.synthetic.started();
		return new RunSyncPayload(data.state(), secondsToNext, mobsLeft, List.copyOf(data.nightModifiers), config.duskCueTime, config.duskTime,
				config.dawnTime, (float) config.fogDistance,
				act.ordinal(), act.displayName(), actCount, expedition ? data.highestOpenAct : 1, chargeSecondsLeft,
				synthetic ? data.synthetic.timeOfDay() : -1, synthetic ? WaveMath.clockRate(config.dayLengthMinutes) : 0);
	}
}
