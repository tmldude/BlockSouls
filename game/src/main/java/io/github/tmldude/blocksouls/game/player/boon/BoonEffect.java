package io.github.tmldude.blocksouls.game.player.boon;

/** One entry of a boon's {@code effects} list. Ids are strings here so this stays free of Minecraft classes. */
public sealed interface BoonEffect {
	/** A potion effect held permanently while the boon is owned. Amplifier goes up by one per extra level. */
	record Effect(String effect, int amplifier) implements BoonEffect {
	}

	/** An attribute modifier; {@code amount} is multiplied by the boon's level. */
	record Attribute(String attribute, double amount, String operation) implements BoonEffect {
	}

	/** A trait other modules implement; {@code level} is multiplied by the boon's level. */
	record Trait(String trait, int level) implements BoonEffect {
	}

	/** An item handed over once, when the boon is first granted. */
	record Item(String item, int count) implements BoonEffect {
	}

	/**
	 * A rule the game module implements in code (keystones, curses with behavior, trinkets with behavior); the id
	 * names a {@code BoonRules} entry. The rule sees the owner's level.
	 */
	record Rule(String rule) implements BoonEffect {
	}
}
