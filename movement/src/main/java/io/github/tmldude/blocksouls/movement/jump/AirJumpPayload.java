package io.github.tmldude.blocksouls.movement.jump;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;

/** Client → server: "I air-jumped" ({@code DOUBLE_JUMP}), so others see the puff. The jump itself is client-side. */
public record AirJumpPayload() implements CustomPacketPayload {
	public static final AirJumpPayload INSTANCE = new AirJumpPayload();
	public static final Type<AirJumpPayload> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "air_jump");
	public static final StreamCodec<RegistryFriendlyByteBuf, AirJumpPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<AirJumpPayload> type() {
		return TYPE;
	}
}
