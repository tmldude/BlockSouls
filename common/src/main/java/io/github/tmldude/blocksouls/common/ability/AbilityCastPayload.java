package io.github.tmldude.blocksouls.common.ability;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;

/**
 * Client → server: the player pressed the key for {@code slot}. Common registers the handler, which only fires
 * {@code AbilityEvents.CAST_REQUESTED}; the abilities module validates and casts. Send it with
 * {@code ClientNetworking.sendToServer}.
 */
public record AbilityCastPayload(AbilitySlot slot) implements CustomPacketPayload {
	public static final Type<AbilityCastPayload> TYPE = Networking.type("blocksouls_common", "ability_cast");
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityCastPayload> CODEC =
			AbilitySlot.STREAM_CODEC.map(AbilityCastPayload::new, AbilityCastPayload::slot).cast();

	@Override
	public Type<AbilityCastPayload> type() {
		return TYPE;
	}
}
