package io.github.tmldude.blocksouls.movement.roll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;

/** Client → server: "I started a dodge roll this tick". Movement itself is client-side, like vanilla's. */
public record RollPayload() implements CustomPacketPayload {
	public static final RollPayload INSTANCE = new RollPayload();
	public static final Type<RollPayload> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "roll");
	public static final StreamCodec<RegistryFriendlyByteBuf, RollPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<RollPayload> type() {
		return TYPE;
	}
}
