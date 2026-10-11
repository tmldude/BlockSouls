package io.github.tmldude.blocksouls.guns.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/** Server → shooter: a reload started taking {@code ticks}, or ended ({@code 0}: done or cancelled). */
public record ReloadStatePayload(int ticks) implements CustomPacketPayload {
	public static final Type<ReloadStatePayload> TYPE = Networking.type(BlockSoulsGuns.MOD_ID, "reload_state");
	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReloadStatePayload::ticks, ReloadStatePayload::new);

	@Override
	public Type<ReloadStatePayload> type() {
		return TYPE;
	}
}
