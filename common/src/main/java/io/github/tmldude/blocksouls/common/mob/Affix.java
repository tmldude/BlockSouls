package io.github.tmldude.blocksouls.common.mob;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * An elite or field-boss affix (PLAN2 §3h): a name prefix, a palette shift and exactly one modifier. Which
 * entity carries which is in {@link Affixes}; what each one does is the mobs module's (bosses applies the
 * same contract to field bosses).
 */
public enum Affix implements StringRepresentable {
	/** Leaves a short fire trail and ignites on hit. */
	BURNING("Burning"),
	/** Slows on hit and takes less projectile damage. */
	FROSTED("Frosted"),
	/** Explodes on death (no block damage). */
	VOLATILE("Volatile"),
	/** Pulls dropped items and players' projectiles slightly off course. */
	MAGNETIC("Magnetic");

	public static final StringRepresentable.EnumCodec<Affix> CODEC = StringRepresentable.fromEnum(Affix::values);
	public static final StreamCodec<ByteBuf, Affix> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Affix::ordinal);

	private final String name = name().toLowerCase(Locale.ROOT);
	private final String prefix;

	Affix(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** English nameplate prefix ("Burning" in "Burning Brute"). */
	public String prefix() {
		return prefix;
	}

	/** Translation key for the prefix: {@code affix.blocksouls_common.<name>}. */
	public String translationKey() {
		return "affix.blocksouls_common." + name;
	}

	/** Lower-case id used in config and commands; {@code null} for an unknown name. */
	public static @Nullable Affix byName(String name) {
		for (Affix affix : values()) {
			if (affix.name.equals(name)) {
				return affix;
			}
		}
		return null;
	}
}
