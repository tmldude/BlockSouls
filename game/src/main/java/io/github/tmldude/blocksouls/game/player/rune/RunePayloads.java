package io.github.tmldude.blocksouls.game.player.rune;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Rune tree packets. */
public final class RunePayloads {
	private RunePayloads() {
	}

	/** One rune as the tree screen shows it. Name and description come from the lang file by id. */
	public record RuneView(Identifier id, int cost, List<Identifier> requires, boolean unlocked, boolean reachable, List<Identifier> capabilities) {
		public static final StreamCodec<RegistryFriendlyByteBuf, RuneView> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, RuneView::id,
				ByteBufCodecs.VAR_INT, RuneView::cost,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), RuneView::requires,
				ByteBufCodecs.BOOL, RuneView::unlocked,
				ByteBufCodecs.BOOL, RuneView::reachable,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), RuneView::capabilities,
				RuneView::new);
	}

	/** Client → server: show me this class's tree. */
	public record Request(Identifier playerClass) implements CustomPacketPayload {
		public static final Type<Request> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "rune_tree_request");
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(Identifier.STREAM_CODEC, Request::playerClass, Request::new);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** Server → client: the tree with the player's state and balance. */
	public record Tree(Identifier playerClass, int souls, List<RuneView> runes) implements CustomPacketPayload {
		public static final Type<Tree> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "rune_tree");
		public static final StreamCodec<RegistryFriendlyByteBuf, Tree> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Tree::playerClass,
				ByteBufCodecs.VAR_INT, Tree::souls,
				RuneView.CODEC.apply(ByteBufCodecs.list()), Tree::runes,
				Tree::new);

		@Override
		public Type<Tree> type() {
			return TYPE;
		}
	}

	/** Client → server: buy this rune. The server answers with a fresh {@link Tree}. */
	public record Unlock(Identifier rune) implements CustomPacketPayload {
		public static final Type<Unlock> TYPE = Networking.type(BlockSoulsGame.MOD_ID, "rune_unlock");
		public static final StreamCodec<RegistryFriendlyByteBuf, Unlock> CODEC = StreamCodec.composite(Identifier.STREAM_CODEC, Unlock::rune, Unlock::new);

		@Override
		public Type<Unlock> type() {
			return TYPE;
		}
	}
}
