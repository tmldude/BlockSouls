package io.github.tmldude.blocksouls.bosses.bar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;
import io.github.tmldude.blocksouls.common.Networking;

/**
 * Server → client: one boss's bar. {@code remove} hides it (the other fields are then empty). {@code inFight} is
 * true for players taking part, who also hear the boss music.
 */
public record BossBarPayload(int entityId, boolean remove, Component name, float health, float maxHealth, List<Float> notches,
		int phase, boolean engaged, boolean inFight, Optional<Identifier> music) implements CustomPacketPayload {
	public static final Type<BossBarPayload> TYPE = Networking.type(BlockSoulsBosses.MOD_ID, "boss_bar");
	public static final StreamCodec<RegistryFriendlyByteBuf, BossBarPayload> CODEC = CustomPacketPayload.codec(BossBarPayload::write,
			BossBarPayload::read);

	public static BossBarPayload remove(int entityId) {
		return new BossBarPayload(entityId, true, Component.empty(), 0, 0, List.of(), 0, false, false, Optional.empty());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeBoolean(remove);
		if (remove) {
			return;
		}
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, name);
		buf.writeFloat(health);
		buf.writeFloat(maxHealth);
		buf.writeVarInt(notches.size());
		for (float notch : notches) {
			buf.writeFloat(notch);
		}
		buf.writeVarInt(phase);
		buf.writeBoolean(engaged);
		buf.writeBoolean(inFight);
		buf.writeOptional(music, (b, id) -> b.writeIdentifier(id));
	}

	private static BossBarPayload read(RegistryFriendlyByteBuf buf) {
		int entityId = buf.readVarInt();
		if (buf.readBoolean()) {
			return remove(entityId);
		}
		Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		float health = buf.readFloat();
		float maxHealth = buf.readFloat();
		int count = Math.min(buf.readVarInt(), 16);
		List<Float> notches = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			notches.add(buf.readFloat());
		}
		int phase = buf.readVarInt();
		boolean engaged = buf.readBoolean();
		boolean inFight = buf.readBoolean();
		Optional<Identifier> music = buf.readOptional(b -> b.readIdentifier());
		return new BossBarPayload(entityId, false, name, health, maxHealth, notches, phase, engaged, inFight, music);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
