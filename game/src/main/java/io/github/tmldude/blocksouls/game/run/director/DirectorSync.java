package io.github.tmldude.blocksouls.game.run.director;

import java.util.Locale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;

/** Sends {@link DirectorSyncPayload} when it changes (checked once a second) and on join. */
final class DirectorSync {
	private static boolean dirty = true;
	private static DirectorSyncPayload last = DirectorSyncPayload.EMPTY;

	private DirectorSync() {
	}

	static void init() {
		Networking.registerClientbound(DirectorSyncPayload.TYPE, DirectorSyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> Networking.sendToPlayer(listener.player, build(server)));
	}

	static void markDirty() {
		dirty = true;
	}

	/** Called once a second by the Director (and on run end, to clear the HUD). */
	static void flush(MinecraftServer server) {
		if (!dirty) {
			return;
		}
		dirty = false;
		DirectorSyncPayload payload = build(server);
		if (payload.equals(last)) {
			return;
		}
		last = payload;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Networking.sendToPlayer(player, payload);
		}
	}

	static DirectorSyncPayload build(MinecraftServer server) {
		if (!Director.active(server)) {
			return DirectorSyncPayload.EMPTY;
		}
		DirectorData data = DirectorData.get(server);
		String objective = Objectives.current();
		return new DirectorSyncPayload(data.dayRunning() ? data.daySecondsLeft() : -1, data.daySeconds(), (float) Director.stress(),
				Director.mood().name().toLowerCase(Locale.ROOT), objective == null ? "" : objective, Objectives.progress());
	}
}
