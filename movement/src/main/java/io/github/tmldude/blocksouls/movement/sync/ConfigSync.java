package io.github.tmldude.blocksouls.movement.sync;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * Sends the server's {@code movement.json} to each client on join (and after a reload), so client-side movement
 * (jump, roll, climb, swing) uses the server's tuning while connected. Server checks always use the server's own.
 */
public final class ConfigSync {
	public static final String FILE = "movement";

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
	}

	public static void send(ServerPlayer player) {
		Networking.sendToPlayer(player, new Payload(BlockSoulsMovement.CONFIG.toJson()));
	}

	/** Re-reads {@code movement.json} into the live config and sends it to everyone online. */
	public static void reload(MinecraftServer server) {
		BlockSoulsMovement.CONFIG.copyFrom(Config.load(FILE, MovementConfig.class, MovementConfig::new));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}
	}
}
