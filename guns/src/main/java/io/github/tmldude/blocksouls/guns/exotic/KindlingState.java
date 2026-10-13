package io.github.tmldude.blocksouls.guns.exotic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import io.github.tmldude.blocksouls.guns.logic.HeatMath;

/**
 * Kindling Engine's heat, stored on the gun so it syncs to the HUD for free. Heat decays lazily: the stored value
 * is the heat at {@code tick} (level game time), and {@link #heatAt} applies the decay since. A venting gun can't
 * fire until {@code ventUntil}.
 */
public record KindlingState(double heat, long tick, long ventUntil) {
	public static final KindlingState COLD = new KindlingState(0, 0, 0);
	public static final double MAX = HeatMath.MAX;

	public static final Codec<KindlingState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("heat").forGetter(KindlingState::heat),
			Codec.LONG.fieldOf("tick").forGetter(KindlingState::tick),
			Codec.LONG.fieldOf("vent_until").forGetter(KindlingState::ventUntil)).apply(i, KindlingState::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, KindlingState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, KindlingState::heat, ByteBufCodecs.VAR_LONG, KindlingState::tick, ByteBufCodecs.VAR_LONG, KindlingState::ventUntil,
			KindlingState::new);

	/** Heat now (0..100) after decaying since the stored tick. */
	public double heatAt(long now, double decayPerTick) {
		return HeatMath.decayed(heat, now - tick, decayPerTick);
	}

	public boolean venting(long now) {
		return now < ventUntil;
	}

	/** Adds heat now; at {@link #MAX} the gun starts venting for {@code ventTicks}. */
	public KindlingState heated(long now, double amount, double decayPerTick, int ventTicks) {
		double current = heatAt(now, decayPerTick);
		if (HeatMath.overheats(current, amount)) {
			return new KindlingState(MAX, now, now + ventTicks);
		}
		return new KindlingState(HeatMath.heated(current, amount), now, ventUntil);
	}

	/** Fire-rate multiplier for this heat: 1 cold, {@code maxMult} at full heat. */
	public static double rateMult(double heat, double maxMult) {
		return HeatMath.rateMult(heat, maxMult);
	}
}
