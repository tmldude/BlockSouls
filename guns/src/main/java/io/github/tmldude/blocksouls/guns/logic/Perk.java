package io.github.tmldude.blocksouls.guns.logic;

import java.util.Locale;
import java.util.Optional;

/** Gun perks. Stored on the item by id ({@code blocksouls_guns:<id>}); numbers live in config. */
public enum Perk {
	/** Precision kill: reload faster for a few seconds. */
	OUTLAW,
	/** Kill: stacking damage bonus that refreshes on each kill. */
	RAMPAGE,
	/** Precision hits deal bonus damage in a small explosion that never breaks blocks. */
	EXPLOSIVE_ROUNDS,
	/** Bonus damage against bosses and elites. */
	VORPAL;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<Perk> byId(String id) {
		for (Perk perk : values()) {
			if (perk.id().equals(id)) {
				return Optional.of(perk);
			}
		}
		return Optional.empty();
	}
}
