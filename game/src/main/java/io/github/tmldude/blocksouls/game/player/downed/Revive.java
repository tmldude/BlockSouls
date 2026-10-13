package io.github.tmldude.blocksouls.game.player.downed;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;

/**
 * Reviving: a teammate holds use on a downed player. The client says when it starts and stops; the server
 * re-checks range and line of sight every tick and cancels the attempt if either fails.
 */
public final class Revive {
	private record Attempt(UUID target, int ticks) {
	}

	/** Keyed by reviver. */
	private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
	private static PlayersConfig.Downed config;

	private Revive() {
	}

	public static void register(PlayersConfig.Downed downedConfig) {
		config = downedConfig;

		Networking.registerServerbound(RevivePayloads.Start.TYPE, RevivePayloads.Start.CODEC,
				(payload, context) -> start(context.player(), context.player().level().getEntity(payload.target())));
		Networking.registerServerbound(RevivePayloads.Stop.TYPE, RevivePayloads.Stop.CODEC,
				(payload, context) -> stop(context.player()));
		Networking.registerClientbound(RevivePayloads.Progress.TYPE, RevivePayloads.Progress.CODEC);

		// Holding use on a downed teammate revives them instead of using the held item.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				entity instanceof Player target && PlayerStatus.isDowned(target) && !PlayerStatus.isDowned(player)
						? InteractionResult.SUCCESS : InteractionResult.PASS);

		ServerTickEvents.END_SERVER_TICK.register(Revive::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			cancelAttemptsBy(listener.player);
			cancelAttemptsOn(listener.player);
		});
	}

	public static boolean canRevive(ServerPlayer reviver, Entity entity) {
		return entity instanceof ServerPlayer target
				&& target != reviver
				&& PlayerStatus.isDowned(target)
				&& reviver.isAlive() && !reviver.isSpectator() && !PlayerStatus.isDowned(reviver)
				&& reviver.level() == target.level()
				&& reviver.distanceTo(target) <= config.reviveRange
				&& reviver.hasLineOfSight(target);
	}

	/** Starts (or keeps going) an attempt by {@code reviver} on {@code target}. Returns false if not allowed. */
	public static boolean start(ServerPlayer reviver, Entity target) {
		if (target == null || !canRevive(reviver, target)) {
			return false;
		}
		Attempt current = ATTEMPTS.get(reviver.getUUID());
		if (current == null || !current.target().equals(target.getUUID())) {
			ATTEMPTS.put(reviver.getUUID(), new Attempt(target.getUUID(), 0));
		}
		return true;
	}

	public static void stop(ServerPlayer reviver) {
		Attempt attempt = ATTEMPTS.remove(reviver.getUUID());
		if (attempt != null) {
			ServerPlayer target = reviver.level().getServer().getPlayerList().getPlayer(attempt.target());
			sendEnded(reviver, target);
		}
	}

	/** The reviver went down or left. */
	static void cancelAttemptsBy(ServerPlayer reviver) {
		stop(reviver);
	}

	/** The downed player got up, died or left. */
	static void cancelAttemptsOn(ServerPlayer target) {
		MinecraftServer server = target.level().getServer();
		Iterator<Map.Entry<UUID, Attempt>> it = ATTEMPTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Attempt> entry = it.next();
			if (entry.getValue().target().equals(target.getUUID())) {
				it.remove();
				sendEnded(server.getPlayerList().getPlayer(entry.getKey()), target);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (ATTEMPTS.isEmpty()) {
			return;
		}

		Map<ServerPlayer, ServerPlayer> finished = new HashMap<>();
		Iterator<Map.Entry<UUID, Attempt>> it = ATTEMPTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Attempt> entry = it.next();
			ServerPlayer reviver = server.getPlayerList().getPlayer(entry.getKey());
			ServerPlayer target = server.getPlayerList().getPlayer(entry.getValue().target());
			if (reviver == null || target == null || !canRevive(reviver, target)) {
				it.remove();
				sendEnded(reviver, target);
				continue;
			}

			int ticks = entry.getValue().ticks() + 1;
			if (ticks >= ticksNeeded(reviver)) {
				it.remove();
				finished.put(target, reviver);
			} else {
				entry.setValue(new Attempt(target.getUUID(), ticks));
				send(reviver, target, (float) ticks / ticksNeeded(reviver));
			}
		}

		// Reviving cancels other attempts on the same player, so do it outside the loop.
		finished.forEach((target, reviver) -> {
			sendEnded(reviver, target);
			Downed.revive(target, reviver);
		});
	}

	/** Ticks a reviver must hold: the config time, divided by their class's revive speed (Warden: 2×). */
	public static int ticksNeeded(ServerPlayer reviver) {
		double speed = Math.max(0.01, PlayerClasses.rules(reviver).reviveSpeed());
		return Math.max(1, (int) Math.ceil(config.reviveTicks / speed));
	}

	/** Ticks of progress {@code reviver} has made, or -1 if they aren't reviving anyone. */
	public static int progressTicks(ServerPlayer reviver) {
		Attempt attempt = ATTEMPTS.get(reviver.getUUID());
		return attempt == null ? -1 : attempt.ticks();
	}

	private static void sendEnded(ServerPlayer reviver, ServerPlayer target) {
		if (reviver != null && target != null) {
			send(reviver, target, -1.0F);
		}
	}

	private static void send(ServerPlayer reviver, ServerPlayer target, float progress) {
		RevivePayloads.Progress payload = new RevivePayloads.Progress(target.getId(), reviver.getId(), progress);
		Networking.sendToPlayer(reviver, payload);
		Networking.sendToPlayer(target, payload);
	}
}
