package io.github.tmldude.blocksouls.game.player.summary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Run summary v2 packets. */
public final class SummaryPayloads {
	private static final int MAX_CARDS = 64;
	private static final int MAX_TAGS = 16;

	private SummaryPayloads() {
	}

	/** The run as a whole: wave and threat reached, nights, acts opened, bosses felled. */
	public record RunLine(int wave, int threat, int nights, int acts, int bosses) {
	}

	/**
	 * One player's card. {@code bestBoonTier} is -1 without a boon; {@code mostDowned} marks the one player with the
	 * most time spent downed.
	 */
	public record Card(String name, Optional<Identifier> playerClass, int bossKills, int revivesGiven, int downedSeconds,
			Optional<Identifier> bestBoon, int bestBoonTier, List<Identifier> build, int runeSouls, boolean mostDowned) {
		public Card {
			build = List.copyOf(build);
		}
	}

	/** Server → every player at run end (or {@code /bs summary show}): the cards to show. */
	public record Cards(RunLine run, List<Card> cards, int columns) implements CustomPacketPayload {
		public static final Type<Cards> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "run_summary");
		public static final StreamCodec<RegistryFriendlyByteBuf, Cards> CODEC = StreamCodec.of(SummaryPayloads::write, SummaryPayloads::read);

		public Cards {
			cards = List.copyOf(cards);
		}

		@Override
		public Type<Cards> type() {
			return TYPE;
		}
	}

	private static void write(RegistryFriendlyByteBuf buf, Cards payload) {
		RunLine run = payload.run();
		buf.writeVarInt(run.wave());
		buf.writeVarInt(run.threat());
		buf.writeVarInt(run.nights());
		buf.writeVarInt(run.acts());
		buf.writeVarInt(run.bosses());
		buf.writeVarInt(payload.columns());
		List<Card> cards = payload.cards().subList(0, Math.min(MAX_CARDS, payload.cards().size()));
		buf.writeVarInt(cards.size());
		for (Card card : cards) {
			buf.writeUtf(card.name(), 64);
			buf.writeOptional(card.playerClass(), (b, id) -> b.writeIdentifier(id));
			buf.writeVarInt(card.bossKills());
			buf.writeVarInt(card.revivesGiven());
			buf.writeVarInt(card.downedSeconds());
			buf.writeOptional(card.bestBoon(), (b, id) -> b.writeIdentifier(id));
			buf.writeVarInt(card.bestBoonTier() + 1);
			List<Identifier> build = card.build().subList(0, Math.min(MAX_TAGS, card.build().size()));
			buf.writeVarInt(build.size());
			build.forEach(buf::writeIdentifier);
			buf.writeVarInt(card.runeSouls());
			buf.writeBoolean(card.mostDowned());
		}
	}

	private static Cards read(RegistryFriendlyByteBuf buf) {
		RunLine run = new RunLine(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		int columns = buf.readVarInt();
		int count = Math.min(MAX_CARDS, buf.readVarInt());
		List<Card> cards = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String name = buf.readUtf(64);
			Optional<Identifier> playerClass = buf.readOptional(b -> b.readIdentifier());
			int bossKills = buf.readVarInt();
			int revives = buf.readVarInt();
			int downed = buf.readVarInt();
			Optional<Identifier> boon = buf.readOptional(b -> b.readIdentifier());
			int tier = buf.readVarInt() - 1;
			int tags = Math.min(MAX_TAGS, buf.readVarInt());
			List<Identifier> build = new ArrayList<>(tags);
			for (int t = 0; t < tags; t++) {
				build.add(buf.readIdentifier());
			}
			int souls = buf.readVarInt();
			boolean mostDowned = buf.readBoolean();
			cards.add(new Card(name, playerClass, bossKills, revives, downed, boon, tier, build, souls, mostDowned));
		}
		return new Cards(run, cards, columns);
	}
}
