package io.github.tmldude.blocksouls.movement.flight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;

/** Client → server: "I double-tapped jump in the air; let me fly" ({@code FLIGHT} trait). */
public record FlightPayload() implements CustomPacketPayload {
	public static final FlightPayload INSTANCE = new FlightPayload();
	public static final Type<FlightPayload> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "flight");
	public static final StreamCodec<RegistryFriendlyByteBuf, FlightPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<FlightPayload> type() {
		return TYPE;
	}
}
