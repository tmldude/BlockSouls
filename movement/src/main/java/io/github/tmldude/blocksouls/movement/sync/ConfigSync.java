package io.github.tmldude.blocksouls.movement.sync;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * Sends the server's {@code movement.json} to each client on join (and after a reload), so client-side movement
 * (jump, roll, climb, swing) uses the server's tuning while connected. Server checks always use the server's own.
 * {@code /bs config reload [movement]} copies the file into the live config and re-sends it ({@link ConfigEvents#RELOADED}).
 */
public final class ConfigSync {
	public static final String FILE = "movement";

	private static MinecraftServer server;

	private ConfigSync() {
	}

	/** Server → client: the server's movement config as JSON. */
	public record Payload(String json) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "config");
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(1 << 16), Payload::json,
				Payload::new);

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	public static void register() {
		Networking.registerClientbound(Payload.TYPE, Payload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> send(listener.player));
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
		ConfigEvents.RELOADED.register((name, config) -> {
			if (FILE.equals(name) && config instanceof MovementConfig reloaded) {
				// The tracked instance is the one Config.load returned; the live one is CONFIG.
				if (reloaded != BlockSoulsMovement.CONFIG) {
					BlockSoulsMovement.CONFIG.copyFrom(reloaded);
				}
				if (server != null) {
					sendToAll(server);
				}
			}
		});
	}

	public static void send(ServerPlayer player) {
		Networking.sendToPlayer(player, new Payload(BlockSoulsMovement.CONFIG.toJson()));
	}

	/**
	 * Re-reads {@code movement.json} into the live config and sends it to everyone online (the RELOADED listener
	 * above does both). Returns null on success, otherwise why the old values were kept.
	 */
	public static String reload() {
		return ConfigEvents.reload(FILE);
	}

	public static void sendToAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}
	}
}
