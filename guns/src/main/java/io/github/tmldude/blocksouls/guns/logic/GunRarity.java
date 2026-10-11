package io.github.tmldude.blocksouls.guns.logic;

import java.util.Locale;

/** A gun's rolled rarity. Scales damage (multipliers in config) and decides how many perks it rolls. */
public enum GunRarity {
	COMMON(0),
	RARE(1),
	LEGENDARY(2);

	private final int perkCount;

	GunRarity(int perkCount) {
		this.perkCount = perkCount;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int perkCount() {
		return perkCount;
	}

	/** Unknown ids fall back to common. */
	public static GunRarity byId(String id) {
		for (GunRarity rarity : values()) {
			if (rarity.id().equals(id)) {
				return rarity;
			}
		}
		return COMMON;
	}

	public static boolean isValid(String id) {
		for (GunRarity rarity : values()) {
			if (rarity.id().equals(id)) {
				return true;
			}
		}
		return false;
	}
}
