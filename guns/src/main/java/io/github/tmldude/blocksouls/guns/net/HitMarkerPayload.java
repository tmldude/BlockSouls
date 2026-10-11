package io.github.tmldude.blocksouls.guns.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/** Server → shooter: one of your shots hit. {@code kind}: {@link #BODY}, {@link #PRECISION} or {@link #KILL}. */
public record HitMarkerPayload(int kind) implements CustomPacketPayload {
	public static final int BODY = 0;
	public static final int PRECISION = 1;
	public static final int KILL = 2;

	public static final Type<HitMarkerPayload> TYPE = Networking.type(BlockSoulsGuns.MOD_ID, "hit_marker");
	public static final StreamCodec<RegistryFriendlyByteBuf, HitMarkerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HitMarkerPayload::kind, HitMarkerPayload::new);

	@Override
	public Type<HitMarkerPayload> type() {
		return TYPE;
	}
}
