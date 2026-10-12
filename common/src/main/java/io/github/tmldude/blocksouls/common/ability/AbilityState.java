package io.github.tmldude.blocksouls.common.ability;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A player's ability state: what sits in each slot, each slot's running cooldown and the ultimate charge. Immutable;
 * {@link Abilities} swaps it on the player's attachment. Cooldown times are in the level's game time
 * ({@code Level.getGameTime()}), which both sides share, so a client can draw the sweep without knowing the server
 * tick count.
 *
 * @param equipped       slot → ability id
 * @param cooldowns      slot → running or finished cooldown (absent = never cast)
 * @param ultimateCharge 0..1; the ultimate is ready at 1
 */
public record AbilityState(Map<AbilitySlot, Identifier> equipped, Map<AbilitySlot, Cooldown> cooldowns, double ultimateCharge) {
	public static final AbilityState EMPTY = new AbilityState(Map.of(), Map.of(), 0);

	/** @param startedAt game time of the cast; @param readyAt game time the slot is ready again */
	public record Cooldown(long startedAt, long readyAt) {
		public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("started_at").forGetter(Cooldown::startedAt),
				Codec.LONG.fieldOf("ready_at").forGetter(Cooldown::readyAt)).apply(i, Cooldown::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cooldown> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Cooldown::startedAt, ByteBufCodecs.VAR_LONG, Cooldown::readyAt, Cooldown::new);

		public long total() {
			return Math.max(0, readyAt - startedAt);
		}

		public long remaining(long now) {
			return Math.max(0, readyAt - now);
		}

		public boolean ready(long now) {
			return now >= readyAt;
		}

		/** 0 just cast → 1 ready. */
		public float progress(long now) {
			long total = total();
			return total <= 0 ? 1 : Math.clamp((now - startedAt) / (float) total, 0.0F, 1.0F);
		}
	}

	public static final Codec<AbilityState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(AbilitySlot.CODEC, Identifier.CODEC).optionalFieldOf("equipped", Map.of()).forGetter(AbilityState::equipped),
			Codec.unboundedMap(AbilitySlot.CODEC, Cooldown.CODEC).optionalFieldOf("cooldowns", Map.of()).forGetter(AbilityState::cooldowns),
			Codec.DOUBLE.optionalFieldOf("ultimate_charge", 0.0).forGetter(AbilityState::ultimateCharge)).apply(i, AbilityState::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, AbilitySlot.STREAM_CODEC, Identifier.STREAM_CODEC), AbilityState::equipped,
			ByteBufCodecs.map(HashMap::new, AbilitySlot.STREAM_CODEC, Cooldown.STREAM_CODEC), AbilityState::cooldowns,
			ByteBufCodecs.DOUBLE, AbilityState::ultimateCharge,
			AbilityState::new);

	public AbilityState {
		equipped = Map.copyOf(equipped);
		cooldowns = Map.copyOf(cooldowns);
		ultimateCharge = Math.clamp(ultimateCharge, 0.0, 1.0);
	}

	public AbilityState withEquipped(AbilitySlot slot, Identifier id) {
		Map<AbilitySlot, Identifier> next = new HashMap<>(equipped);
		if (id == null) {
			next.remove(slot);
		} else {
			next.put(slot, id);
		}
		return new AbilityState(next, cooldowns, ultimateCharge);
	}

	public AbilityState withCooldown(AbilitySlot slot, Cooldown cooldown) {
		Map<AbilitySlot, Cooldown> next = new HashMap<>(cooldowns);
		if (cooldown == null) {
			next.remove(slot);
		} else {
			next.put(slot, cooldown);
		}
		return new AbilityState(equipped, next, ultimateCharge);
	}

	public AbilityState withoutCooldowns() {
		return new AbilityState(equipped, Map.of(), ultimateCharge);
	}

	public AbilityState withUltimateCharge(double charge) {
		return new AbilityState(equipped, cooldowns, charge);
	}

	public boolean ultimateReady() {
		return ultimateCharge >= 1.0;
	}

	public boolean isEmpty() {
		return equipped.isEmpty() && cooldowns.isEmpty() && ultimateCharge <= 0;
	}
}
