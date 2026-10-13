package io.github.tmldude.blocksouls.game.player.ping;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;

/**
 * Pings: a keybind marks a spot for the whole party (where you look, or where you lie when down). The server
 * resolves the spot, rate-limits per player and broadcasts to everyone in the dimension; clients draw it on the HUD.
 */
public final class Pings {
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static PlayersConfig.Ping config = new PlayersConfig.Ping();

	private Pings() {
	}

	public static void register(PlayersConfig.Ping pingConfig) {
		config = pingConfig;
		Networking.registerServerbound(PingPayloads.Ping.TYPE, PingPayloads.Ping.CODEC, (payload, context) -> ping(context.player()));
		Networking.registerClientbound(PingPayloads.Pinged.TYPE, PingPayloads.Pinged.CODEC);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> LAST.remove(listener.player.getUUID()));
	}

	/** Places a ping for this player (keybind or {@code /bs ping}). Returns false while on cooldown. */
	public static boolean ping(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < config.cooldownTicks) {
			return false;
		}
		LAST.put(player.getUUID(), now);

		boolean downed = PlayerStatus.isDowned(player);
		Vec3 pos = downed ? player.position() : target(player);
		PingPayloads.Pinged payload = new PingPayloads.Pinged(player.getId(), pos, downed, Math.max(1, config.seconds) * 20);
		for (ServerPlayer other : level.players()) {
			Networking.sendToPlayer(other, payload);
		}
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, downed ? 0.7F : 1.4F);
		return true;
	}

	/** Where the player looks, within range; past the range (or into the sky) the point at max range. */
	private static Vec3 target(ServerPlayer player) {
		HitResult hit = player.pick(config.range, 1.0F, false);
		return hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation();
	}
}
