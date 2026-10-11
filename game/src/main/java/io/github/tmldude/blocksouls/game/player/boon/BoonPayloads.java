package io.github.tmldude.blocksouls.game.player.boon;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Boon packets. The client never sees boon JSON, only what these carry. */
public final class BoonPayloads {
	private BoonPayloads() {
	}

	/** What a card or HUD icon shows. {@code tier} is a {@link BoonTier} ordinal. */
	public record BoonView(Identifier id, int tier, Identifier icon, int level, int maxLevel, boolean combo) {
		public static final StreamCodec<RegistryFriendlyByteBuf, BoonView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, BoonView::id,
				ByteBufCodecs.VAR_INT, BoonView::tier,
				Identifier.STREAM_CODEC, BoonView::icon,
				ByteBufCodecs.VAR_INT, BoonView::level,
				ByteBufCodecs.VAR_INT, BoonView::maxLevel,
				ByteBufCodecs.BOOL, BoonView::combo,
				BoonView::new);

		/** {@code level} is the level shown: the owned level in the HUD, the level after picking on a card. */
		public static BoonView of(Boon boon, int level) {
			return new BoonView(Identifier.parse(boon.id()), boon.tier().ordinal(), Identifier.parse(boon.icon()), level,
					boon.maxLevel(), boon.isCombo());
		}
	}

	/** Server → owner: the boons shown in the HUD row (combo parts left out). */
	public record Owned(List<BoonView> boons) implements CustomPacketPayload {
		public static final Type<Owned> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "boons_owned");
		public static final StreamCodec<RegistryFriendlyByteBuf, Owned> CODEC = StreamCodec.composite(
				BoonView.CODEC.apply(ByteBufCodecs.list()), Owned::boons, Owned::new);

		@Override
		public Type<Owned> type() {
			return TYPE;
		}
	}

	/**
	 * Server → player: open the relic screen. A card with {@code wildcard} set has no boon; picking it shows
	 * {@code wildcardOptions}.
	 */
	public record Offer(int tier, List<Card> cards, List<BoonView> wildcardOptions) implements CustomPacketPayload {
		public static final Type<Offer> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "relic_offer");
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Offer::tier,
				Card.CODEC.apply(ByteBufCodecs.list()), Offer::cards,
				BoonView.CODEC.apply(ByteBufCodecs.list()), Offer::wildcardOptions,
				Offer::new);

		@Override
		public Type<Offer> type() {
			return TYPE;
		}
	}

	public record Card(Optional<BoonView> boon, boolean wildcard) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(BoonView.CODEC), Card::boon,
				ByteBufCodecs.BOOL, Card::wildcard,
				Card::new);
	}

	/** Client → server: picked card {@code card}; for a Wildcard, {@code wildcardPick} is the chosen boon. */
	public record Choose(int card, Optional<Identifier> wildcardPick) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "relic_choose");
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Choose::card,
				ByteBufCodecs.optional(Identifier.STREAM_CODEC), Choose::wildcardPick,
				Choose::new);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}
}
