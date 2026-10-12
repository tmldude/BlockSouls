package io.github.tmldude.blocksouls.game.player.origin;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Origin select packets. */
public final class OriginPayloads {
	private OriginPayloads() {
	}

	/** One origin as the select screen shows it. Names and mechanics text come from the lang file by id. */
	public record OriginView(Identifier id, Identifier icon, List<Identifier> capabilities, List<Identifier> synergyTags,
			Optional<Identifier> signatureAbility) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OriginView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, OriginView::id,
				Identifier.STREAM_CODEC, OriginView::icon,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), OriginView::capabilities,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), OriginView::synergyTags,
				ByteBufCodecs.optional(Identifier.STREAM_CODEC), OriginView::signatureAbility,
				OriginView::new);

		public static OriginView of(Origin origin) {
			return new OriginView(Identifier.parse(origin.id()), Identifier.parse(origin.icon()),
					origin.capabilities().stream().sorted().map(Identifier::parse).toList(),
					origin.synergyTags().keySet().stream().map(Identifier::parse).toList(),
					origin.signatureAbility().map(Identifier::parse));
		}
	}

	/** Server → client: open the select screen. {@code current} is the origin held now, if any. */
	public record Offer(List<OriginView> origins, Optional<Identifier> current) implements CustomPacketPayload {
		public static final Type<Offer> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "origin_offer");
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
				OriginView.CODEC.apply(ByteBufCodecs.list()), Offer::origins,
				ByteBufCodecs.optional(Identifier.STREAM_CODEC), Offer::current,
				Offer::new);

		@Override
		public Type<Offer> type() {
			return TYPE;
		}
	}

	/** Client → server: the pick. */
	public record Choose(Identifier origin) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "origin_choose");
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Choose::origin, Choose::new);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}
}
