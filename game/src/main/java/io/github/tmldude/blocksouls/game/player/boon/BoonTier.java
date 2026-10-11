package io.github.tmldude.blocksouls.game.player.boon;

import java.util.Locale;

public enum BoonTier {
	COMMON,
	RARE,
	LEGENDARY;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Null if {@code id} isn't a tier. */
	public static BoonTier byId(String id) {
		for (BoonTier tier : values()) {
			if (tier.id().equals(id)) {
				return tier;
			}
		}
		return null;
	}

	public static BoonTier byOrdinal(int ordinal) {
		BoonTier[] values = values();
		return values[Math.clamp(ordinal, 0, values.length - 1)];
	}
}
