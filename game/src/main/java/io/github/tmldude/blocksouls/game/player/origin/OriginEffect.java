package io.github.tmldude.blocksouls.game.player.origin;

/** One entry of an origin's {@code passives} or {@code drawbacks}. Ids are strings so this stays free of Minecraft classes. */
public sealed interface OriginEffect {
	/** When a conditional effect holds. {@code ALWAYS} effects are permanent while the origin is held. */
	enum When {
		ALWAYS,
		IN_WATER,
		IN_RAIN,
		IN_LAVA,
		ON_FIRE;

		public static When byId(String id) {
			for (When when : values()) {
				if (when.name().toLowerCase(java.util.Locale.ROOT).equals(id)) {
					return when;
				}
			}
			return null;
		}
	}

	/** A potion effect: permanent for {@code ALWAYS}, else held for as long as {@code when} is true. */
	record Effect(String effect, int amplifier, When when) implements OriginEffect {
	}

	/** An attribute modifier held while the origin is held. */
	record Attribute(String attribute, double amount, String operation) implements OriginEffect {
	}

	/** A trait level added on top of whatever boons grant. */
	record Trait(String trait, int level) implements OriginEffect {
	}
}
