package io.github.tmldude.blocksouls.guns.exotic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Kindling Engine's heat, stored on the gun so it syncs to the HUD for free. Heat decays lazily: the stored value
 * is the heat at {@code tick} (level game time), and {@link #heatAt} applies the decay since. A venting gun can't
 * fire until {@code ventUntil}.
 */
public record KindlingState(double heat, long tick, long ventUntil) {
	public static final KindlingState COLD = new KindlingState(0, 0, 0);
	public static final double MAX = 100;

	public static final Codec<KindlingState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("heat").forGetter(KindlingState::heat),
			Codec.LONG.fieldOf("tick").forGetter(KindlingState::tick),
			Codec.LONG.fieldOf("vent_until").forGetter(KindlingState::ventUntil)).apply(i, KindlingState::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, KindlingState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, KindlingState::heat, ByteBufCodecs.VAR_LONG, KindlingState::tick, ByteBufCodecs.VAR_LONG, KindlingState::ventUntil,
			KindlingState::new);

	/** Heat now (0..100) after decaying since the stored tick. */
	public double heatAt(long now, double decayPerTick) {
		return Math.clamp(heat - Math.max(0, now - tick) * decayPerTick, 0.0, MAX);
	}

	public boolean venting(long now) {
		return now < ventUntil;
	}

	/** Adds heat now; at {@link #MAX} the gun starts venting for {@code ventTicks}. */
	public KindlingState heated(long now, double amount, double decayPerTick, int ventTicks) {
		double next = heatAt(now, decayPerTick) + amount;
		if (next >= MAX) {
			return new KindlingState(MAX, now, now + ventTicks);
		}
		return new KindlingState(next, now, ventUntil);
	}

	/** Fire-rate multiplier for this heat: 1 cold, {@code maxMult} at full heat. */
	public static double rateMult(double heat, double maxMult) {
		return 1.0 + (Math.max(1.0, maxMult) - 1.0) * Math.clamp(heat / MAX, 0.0, 1.0);
	}
}
