package io.github.tmldude.blocksouls.game.player.downed;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Last Stand packets. */
public final class LastStandPayloads {
	private LastStandPayloads() {
	}

	/** Server → the downed player: the crawl timer and kill tally; {@code ticksLeft < 0} means it ended. */
	public record State(int ticksLeft, int totalTicks, int kills, int killsNeeded) implements CustomPacketPayload {
		public static final Type<State> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "last_stand");
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, State::ticksLeft,
				ByteBufCodecs.VAR_INT, State::totalTicks,
				ByteBufCodecs.VAR_INT, State::kills,
				ByteBufCodecs.VAR_INT, State::killsNeeded,
				State::new);

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}
}
