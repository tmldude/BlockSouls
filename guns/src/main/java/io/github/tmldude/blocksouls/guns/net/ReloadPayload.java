package io.github.tmldude.blocksouls.guns.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/** Client → server: reload the held gun. */
public record ReloadPayload() implements CustomPacketPayload {
	public static final ReloadPayload INSTANCE = new ReloadPayload();
	public static final Type<ReloadPayload> TYPE = Networking.type(BlockSoulsGuns.MOD_ID, "reload");
	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ReloadPayload> type() {
		return TYPE;
	}
}
