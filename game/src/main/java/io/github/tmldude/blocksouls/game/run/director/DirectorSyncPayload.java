package io.github.tmldude.blocksouls.game.run.director;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Networking;

/**
 * Server → client: the Director's state for the HUD ({@code ClientRun.director()}).
 *
 * @param daySecondsLeft seconds left on the day timer, -1 when no day is running
 * @param daySeconds     the running day's full length (0 when none)
 * @param stress         the party's smoothed stress, 0–1
 * @param mood           the pacer's mood id ({@code build}, {@code lull}, {@code spike})
 * @param objective      the running siege objective's id, or empty
 * @param progress       the objective's progress, 0–1
 */
public record DirectorSyncPayload(int daySecondsLeft, int daySeconds, float stress, String mood, String objective, float progress)
		implements CustomPacketPayload {
	public static final Type<DirectorSyncPayload> TYPE = Networking.type(CommonIds.GAME, "director_sync");
	public static final StreamCodec<RegistryFriendlyByteBuf, DirectorSyncPayload> CODEC = StreamCodec.ofMember(DirectorSyncPayload::write,
			DirectorSyncPayload::read);
	public static final DirectorSyncPayload EMPTY = new DirectorSyncPayload(-1, 0, 0, "build", "", 0);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(daySecondsLeft);
		buf.writeVarInt(daySeconds);
		buf.writeFloat(stress);
		buf.writeUtf(mood);
		buf.writeUtf(objective);
		buf.writeFloat(progress);
	}

	private static DirectorSyncPayload read(FriendlyByteBuf buf) {
		return new DirectorSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readUtf(), buf.readUtf(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
