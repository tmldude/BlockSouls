package io.github.tmldude.blocksouls.game.player.downed;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Revive packets. */
public final class RevivePayloads {
	private RevivePayloads() {
	}

	/** Client → server: "I'm holding use on this downed player". Re-sent periodically while held. */
	public record Start(int target) implements CustomPacketPayload {
		public static final Type<Start> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "revive_start");
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Start::target, Start::new);

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Client → server: "I let go". */
	public record Stop() implements CustomPacketPayload {
		public static final Stop INSTANCE = new Stop();
		public static final Type<Stop> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "revive_stop");
		public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	/**
	 * Server → reviver and downed player: revive progress in [0, 1], or a negative value when the attempt ended
	 * (finished or cancelled).
	 */
	public record Progress(int downed, int reviver, float progress) implements CustomPacketPayload {
		public static final Type<Progress> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "revive_progress");
		public static final StreamCodec<RegistryFriendlyByteBuf, Progress> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Progress::downed,
				ByteBufCodecs.VAR_INT, Progress::reviver,
				ByteBufCodecs.FLOAT, Progress::progress,
				Progress::new);

		@Override
		public Type<Progress> type() {
			return TYPE;
		}
	}
}
