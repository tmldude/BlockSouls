package io.github.tmldude.blocksouls.guns.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/** Server → client on join: the server's weapon stats (JSON), so HUD, spread and fire-rate prediction match. */
public record StatsSyncPayload(String json) implements CustomPacketPayload {
	public static final Type<StatsSyncPayload> TYPE = Networking.type(BlockSoulsGuns.MOD_ID, "stats_sync");
	public static final StreamCodec<RegistryFriendlyByteBuf, StatsSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, StatsSyncPayload::json, StatsSyncPayload::new);

	@Override
	public Type<StatsSyncPayload> type() {
		return TYPE;
	}
}
