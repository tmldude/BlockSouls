package io.github.tmldude.blocksouls.vehicles.kart;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;

/** The horn key: the driver's client asks, the server checks the cooldown and honks. */
public final class KartHorn {
	private KartHorn() {
	}

	/** Client to server: "I pressed the horn". Carries nothing; the server knows which kart the sender drives. */
	public record Payload() implements CustomPacketPayload {
		public static final Payload INSTANCE = new Payload();
		public static final Type<Payload> TYPE = Networking.type(BlockSoulsVehicles.MOD_ID, "kart_horn");
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	public static void register() {
		Networking.registerServerbound(Payload.TYPE, Payload.CODEC, (payload, context) -> honk(context.player()));
	}

	/** Server: honks the kart {@code player} drives. Returns whether it honked (false when not driving or on cooldown). */
	public static boolean honk(ServerPlayer player) {
		if (player.getVehicle() instanceof KartEntity kart && kart.getControllingPassenger() == player) {
			return kart.honk(player.level());
		}
		return false;
	}
}
