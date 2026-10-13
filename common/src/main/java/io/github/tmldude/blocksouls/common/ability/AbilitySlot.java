package io.github.tmldude.blocksouls.common.ability;

import java.util.Locale;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The three active-ability slots every player has (v2 abilities). A slot holds at most one ability at a time;
 * which one is in {@link Abilities#equipped}.
 */
public enum AbilitySlot implements StringRepresentable {
	/** Short cooldown, picked at run start or from drops (frag, web bomb, fire wall, healing dart). */
	TACTICAL,
	/** Medium cooldown, set by the player's origin. */
	SIGNATURE,
	/** Charged by kills and precision hits instead of a cooldown. */
	ULTIMATE;

	public static final StringRepresentable.EnumCodec<AbilitySlot> CODEC = StringRepresentable.fromEnum(AbilitySlot::values);
	public static final StreamCodec<ByteBuf, AbilitySlot> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], AbilitySlot::ordinal);

	private final String name = name().toLowerCase(Locale.ROOT);

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Lower-case id used in config and commands; {@code null} for an unknown name. */
	public static AbilitySlot byName(String name) {
		for (AbilitySlot slot : values()) {
			if (slot.name.equals(name)) {
				return slot;
			}
		}
		return null;
	}
}
