package io.github.tmldude.blocksouls.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tells each joining client which BlockSouls version the server runs, so a player with an outdated pack is told to
 * update it instead of hitting confusing desyncs.
 */
public final class VersionCheck {
	public record VersionPayload(String version) implements CustomPacketPayload {
		public static final Type<VersionPayload> TYPE = Networking.type("blocksouls_common", "version");
		public static final StreamCodec<RegistryFriendlyByteBuf, VersionPayload> CODEC =
				ByteBufCodecs.STRING_UTF8.map(VersionPayload::new, VersionPayload::version).cast();

		@Override
		public Type<VersionPayload> type() {
			return TYPE;
		}
	}

	private VersionCheck() {
	}

	/** The BlockSouls version this side runs (every BlockSouls mod shares one version). */
	public static String version() {
		return FabricLoader.getInstance().getModContainer("blocksouls_common")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	static void init() {
		Networking.registerClientbound(VersionPayload.TYPE, VersionPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
				Networking.sendToPlayer(listener.player, new VersionPayload(version())));
	}
}
