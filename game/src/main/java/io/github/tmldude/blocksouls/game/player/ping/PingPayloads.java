package io.github.tmldude.blocksouls.game.player.ping;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Ping packets. */
public final class PingPayloads {
	private PingPayloads() {
	}

	/** Client → server: "ping where I'm looking" (or where I am, when down). */
	public record Ping() implements CustomPacketPayload {
		public static final Ping INSTANCE = new Ping();
		public static final Type<Ping> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "ping");
		public static final StreamCodec<RegistryFriendlyByteBuf, Ping> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Ping> type() {
			return TYPE;
		}
	}

	/** Server → every player in the dimension: a ping by {@code player} (entity id) at {@code pos}. */
	public record Pinged(int player, Vec3 pos, boolean downed, int ticks) implements CustomPacketPayload {
		public static final Type<Pinged> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "pinged");
		public static final StreamCodec<RegistryFriendlyByteBuf, Pinged> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Pinged::player,
				Vec3.STREAM_CODEC, Pinged::pos,
				ByteBufCodecs.BOOL, Pinged::downed,
				ByteBufCodecs.VAR_INT, Pinged::ticks,
				Pinged::new);

		@Override
		public Type<Pinged> type() {
			return TYPE;
		}
	}
}
