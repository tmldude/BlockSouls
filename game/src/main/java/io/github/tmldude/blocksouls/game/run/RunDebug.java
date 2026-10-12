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

	// ---------------------------------------------------------------- v2 expedition spine

	/** Reparses the act list after a test swapped {@code RunSystem.config()} fields. */
	public static void reloadActs(MinecraftServer server) {
		ActMachine.load(RunSystem.config());
	}

	/** {@code /bs act charge}: starts the portal charge. Returns null on success, else why not. */
	public static String startCharge(MinecraftServer server) {
		return ActMachine.startCharge(server, RunData.get(server));
	}

	/** {@code /bs act advance}: opens the next act and makes it current. Returns null on success, else why not. */
	public static String advanceAct(MinecraftServer server) {
		return ActMachine.advance(server, RunData.get(server));
	}

	/** The party's current act ordinal (1-based). */
	public static int currentAct(MinecraftServer server) {
		return RunData.get(server).actOrdinal;
	}

	public static int highestOpenAct(MinecraftServer server) {
		return RunData.get(server).highestOpenAct;
	}

	public static boolean charging(MinecraftServer server) {
		return RunData.get(server).chargingAct > 0;
	}

	/** {@code /bs threat set}: sets the threat (wave) counter. */
	public static void setThreat(MinecraftServer server, int threat) {
		RunData data = RunData.get(server);
		data.wave = Math.max(0, threat);
		data.setDirty();
		RunSync.markDirty();
	}

	/** Moves the run's synthetic cycle forward to {@code timeOfDay} (expedition dimensions without a sun). */
	public static void skipSyntheticTo(MinecraftServer server, int timeOfDay) {
		RunData data = RunData.get(server);
		data.synthetic.skipTo(timeOfDay);
		data.setDirty();
	}

	/** Forces an act-machine tick now (it normally runs once a second). */
	public static void tickActs(MinecraftServer server) {
		ActMachine.tick(server, RunData.get(server));
	}

	/** One line of act-machine state for test failures and debugging: shape, acts, and who is where. */
	public static String actDebug(MinecraftServer server) {
		RunData data = RunData.get(server);
		StringBuilder sb = new StringBuilder("shape=").append(RunSystem.config().runShape)
				.append(" act=").append(data.actOrdinal)
				.append(" open=").append(data.highestOpenAct)
				.append(" charging=").append(data.chargingAct)
				.append(" active=").append(RunSystem.activeLevel(server).dimension().identifier())
				.append(" acts=").append(ActMachine.acts().stream().map(a -> a.dimension().toString()).toList())
				.append(" players=[");
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			sb.append(player.getScoreboardName()).append('@').append(player.level().dimension().identifier());
			if (!player.isAlive()) {
				sb.append(",dead");
			}
			if (player.isSpectator()) {
				sb.append(",spectator");
			}
			if (io.github.tmldude.blocksouls.common.player.PlayerStatus.isDowned(player)) {
				sb.append(",downed");
			}
			sb.append(' ');
		}
		return sb.append(']').toString();
	}
}
