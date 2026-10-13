package io.github.tmldude.blocksouls.game.player.classes;

/** One entry of a class's {@code passives} or {@code drawbacks}. Ids are strings so this stays free of Minecraft classes. */
public sealed interface ClassEffect {
	/** When a conditional effect holds. {@code ALWAYS} effects are permanent while the class is held. */
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
	record Effect(String effect, int amplifier, When when) implements ClassEffect {
	}

	/** An attribute modifier held while the class is held. */
	record Attribute(String attribute, double amount, String operation) implements ClassEffect {
	}

	/** A trait level added on top of whatever boons grant. */
	record Trait(String trait, int level) implements ClassEffect {
	}
}
