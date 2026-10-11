package io.github.tmldude.blocksouls.guns.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/** Client → server: pull the trigger once. No direction: the server uses the player's own rotation. */
public record FirePayload() implements CustomPacketPayload {
	public static final FirePayload INSTANCE = new FirePayload();
	public static final Type<FirePayload> TYPE = Networking.type(BlockSoulsGuns.MOD_ID, "fire");
	public static final StreamCodec<RegistryFriendlyByteBuf, FirePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<FirePayload> type() {
		return TYPE;
	}
}
