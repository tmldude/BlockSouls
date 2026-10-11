package io.github.tmldude.blocksouls.movement.web;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;

/** Web-swing packets. The swing itself is simulated on the client, like all vanilla player movement. */
public final class WebPayloads {
	private WebPayloads() {
	}

	/** Client → server: "I shot a web at {@code anchor}, on the face of {@code block}". */
	public record Attach(Vec3 anchor, BlockPos block) implements CustomPacketPayload {
		public static final Type<Attach> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "web_attach");
		public static final StreamCodec<RegistryFriendlyByteBuf, Attach> CODEC = StreamCodec.composite(
				Vec3.STREAM_CODEC, Attach::anchor,
				BlockPos.STREAM_CODEC, Attach::block,
				Attach::new);

		@Override
		public Type<Attach> type() {
			return TYPE;
		}
	}

	/** Client → server: "I let go". */
	public record Detach() implements CustomPacketPayload {
		public static final Detach INSTANCE = new Detach();
		public static final Type<Detach> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "web_detach");
		public static final StreamCodec<RegistryFriendlyByteBuf, Detach> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Detach> type() {
			return TYPE;
		}
	}

	/** Server → client: "your web is gone" (rejected shot, too far from the anchor, downed). */
	public record Drop() implements CustomPacketPayload {
		public static final Drop INSTANCE = new Drop();
		public static final Type<Drop> TYPE = Networking.type(BlockSoulsMovement.MOD_ID, "web_drop");
		public static final StreamCodec<RegistryFriendlyByteBuf, Drop> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Drop> type() {
			return TYPE;
		}
	}
}
