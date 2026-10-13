package io.github.tmldude.blocksouls.game.player.classes;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** PlayerClass select packets. */
public final class ClassPayloads {
	private ClassPayloads() {
	}

	/** One playerClass as the select screen shows it. Names and mechanics text come from the lang file by id. */
	public record ClassView(Identifier id, Identifier icon, List<Identifier> capabilities, List<Identifier> synergyTags,
			Optional<Identifier> signatureAbility) {
		public static final StreamCodec<RegistryFriendlyByteBuf, ClassView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, ClassView::id,
				Identifier.STREAM_CODEC, ClassView::icon,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), ClassView::capabilities,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), ClassView::synergyTags,
				ByteBufCodecs.optional(Identifier.STREAM_CODEC), ClassView::signatureAbility,
				ClassView::new);

		public static ClassView of(PlayerClass playerClass) {
			return new ClassView(Identifier.parse(playerClass.id()), Identifier.parse(playerClass.icon()),
					playerClass.capabilities().stream().sorted().map(Identifier::parse).toList(),
					playerClass.synergyTags().keySet().stream().map(Identifier::parse).toList(),
					playerClass.signatureAbility().map(Identifier::parse));
		}
	}

	/** Server → client: open the select screen. {@code current} is the class held now, if any. */
	public record Offer(List<ClassView> classes, Optional<Identifier> current) implements CustomPacketPayload {
		public static final Type<Offer> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "origin_offer");
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
				ClassView.CODEC.apply(ByteBufCodecs.list()), Offer::classes,
				ByteBufCodecs.optional(Identifier.STREAM_CODEC), Offer::current,
				Offer::new);

		@Override
		public Type<Offer> type() {
			return TYPE;
		}
	}

	/** Client → server: the pick. */
	public record Choose(Identifier playerClass) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "origin_choose");
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Choose::playerClass, Choose::new);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}
}
