package io.github.tmldude.blocksouls.bosses.presentation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;
import io.github.tmldude.blocksouls.common.Networking;

/** Server → client: show the boss's name-splash for {@code ticks} and play the sting. */
public record BossSplashPayload(int entityId, Component name, int ticks) implements CustomPacketPayload {
	public static final Type<BossSplashPayload> TYPE = Networking.type(BlockSoulsBosses.MOD_ID, "boss_splash");
	public static final StreamCodec<RegistryFriendlyByteBuf, BossSplashPayload> CODEC = CustomPacketPayload.codec(BossSplashPayload::write,
			BossSplashPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, name);
		buf.writeVarInt(ticks);
	}

	private static BossSplashPayload read(RegistryFriendlyByteBuf buf) {
		return new BossSplashPayload(buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
