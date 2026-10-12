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

	/**
	 * What a card or HUD icon shows. {@code tier} is a {@link BoonTier} ordinal, {@code kind} a {@link Boon.Kind}
	 * ordinal (boon, curse, trinket).
	 */
	public record BoonView(Identifier id, int tier, Identifier icon, int level, int maxLevel, boolean combo, int kind, boolean keystone) {
		public static final StreamCodec<RegistryFriendlyByteBuf, BoonView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, BoonView::id,
				ByteBufCodecs.VAR_INT, BoonView::tier,
				Identifier.STREAM_CODEC, BoonView::icon,
				ByteBufCodecs.VAR_INT, BoonView::level,
				ByteBufCodecs.VAR_INT, BoonView::maxLevel,
				ByteBufCodecs.BOOL, BoonView::combo,
				ByteBufCodecs.VAR_INT, BoonView::kind,
				ByteBufCodecs.BOOL, BoonView::keystone,
				BoonView::new);

		/** {@code level} is the level shown: the owned level in the HUD, the level after picking on a card. */
		public static BoonView of(Boon boon, int level) {
			return new BoonView(Identifier.parse(boon.id()), boon.tier().ordinal(), Identifier.parse(boon.icon()), level,
					boon.maxLevel(), boon.isCombo(), boon.kind().ordinal(), boon.keystone());
		}

		public boolean isCurse() {
			return kind == Boon.Kind.CURSE.ordinal();
		}

		public boolean isTrinket() {
			return kind == Boon.Kind.TRINKET.ordinal();
		}
	}

	/** Server → owner: everything owned (boons, curses, trinkets; combo parts left out). The client splits by kind. */
	public record Owned(List<BoonView> boons) implements CustomPacketPayload {
		public static final Type<Owned> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "boons_owned");
		public static final StreamCodec<RegistryFriendlyByteBuf, Owned> CODEC = StreamCodec.composite(
				BoonView.CODEC.apply(ByteBufCodecs.list()), Owned::boons, Owned::new);

		@Override
		public Type<Owned> type() {
			return TYPE;
		}
	}

	/** One synergy tag of the build line, with its stack count. */
	public record TagView(Identifier tag, int stacks) {
		public static final StreamCodec<RegistryFriendlyByteBuf, TagView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, TagView::tag,
				ByteBufCodecs.VAR_INT, TagView::stacks,
				TagView::new);
	}

	/** Server → owner: the build line (top tags by stacks, best first). */
	public record BuildLine(List<TagView> tags) implements CustomPacketPayload {
		public static final Type<BuildLine> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "build_line");
		public static final StreamCodec<RegistryFriendlyByteBuf, BuildLine> CODEC = StreamCodec.composite(
				TagView.CODEC.apply(ByteBufCodecs.list()), BuildLine::tags, BuildLine::new);

		@Override
		public Type<BuildLine> type() {
			return TYPE;
		}
	}

	/**
	 * Server → player: open the relic screen. A card with {@code wildcard} set has no boon; picking it shows
	 * {@code wildcardOptions}. A card with {@code upgrade} set levels up a random owned boon. {@code skipSouls} is
	 * what declining pays.
	 */
	public record Offer(int tier, List<Card> cards, List<BoonView> wildcardOptions, int skipSouls) implements CustomPacketPayload {
		public static final Type<Offer> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "relic_offer");
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Offer::tier,
				Card.CODEC.apply(ByteBufCodecs.list()), Offer::cards,
				BoonView.CODEC.apply(ByteBufCodecs.list()), Offer::wildcardOptions,
				ByteBufCodecs.VAR_INT, Offer::skipSouls,
				Offer::new);

		@Override
		public Type<Offer> type() {
			return TYPE;
		}
	}

	public record Card(Optional<BoonView> boon, boolean wildcard, boolean upgrade) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(BoonView.CODEC), Card::boon,
				ByteBufCodecs.BOOL, Card::wildcard,
				ByteBufCodecs.BOOL, Card::upgrade,
				Card::new);
	}

	/**
	 * Client → server: picked card {@code card}; for a Wildcard, {@code wildcardPick} is the chosen boon.
	 * {@link #SKIP} as the card declines the offer for souls.
	 */
	public record Choose(int card, Optional<Identifier> wildcardPick) implements CustomPacketPayload {
		public static final int SKIP = -1;
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

	/** Server → player: open the trinket screen. Picking with full slots replaces {@code replaces}. */
	public record TrinketOffer(List<BoonView> cards, Optional<BoonView> replaces) implements CustomPacketPayload {
		public static final Type<TrinketOffer> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "trinket_offer");
		public static final StreamCodec<RegistryFriendlyByteBuf, TrinketOffer> CODEC = StreamCodec.composite(
				BoonView.CODEC.apply(ByteBufCodecs.list()), TrinketOffer::cards,
				ByteBufCodecs.optional(BoonView.CODEC), TrinketOffer::replaces,
				TrinketOffer::new);

		@Override
		public Type<TrinketOffer> type() {
			return TYPE;
		}
	}

	/** Client → server: picked trinket card {@code card}. */
	public record ChooseTrinket(int card) implements CustomPacketPayload {
		public static final Type<ChooseTrinket> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "trinket_choose");
		public static final StreamCodec<RegistryFriendlyByteBuf, ChooseTrinket> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ChooseTrinket::card, ChooseTrinket::new);

		@Override
		public Type<ChooseTrinket> type() {
			return TYPE;
		}
	}
}
