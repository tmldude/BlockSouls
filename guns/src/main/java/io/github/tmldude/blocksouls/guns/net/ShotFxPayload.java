package io.github.tmldude.blocksouls.guns.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/**
 * Server → nearby players: someone fired. Each tracer is the bullet's path (start, ricochet points, end), so
 * clients draw tracers and the muzzle flash.
 */
public record ShotFxPayload(int shooterId, List<List<Vec3>> tracers) implements CustomPacketPayload {
	public static final Type<ShotFxPayload> TYPE = Networking.type(BlockSoulsGuns.MOD_ID, "shot_fx");
	public static final StreamCodec<RegistryFriendlyByteBuf, ShotFxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShotFxPayload::shooterId,
			Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(8)).apply(ByteBufCodecs.list(16)), ShotFxPayload::tracers,
			ShotFxPayload::new);

	@Override
	public Type<ShotFxPayload> type() {
		return TYPE;
	}
}
