package io.github.tmldude.blocksouls.guns.weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default stats per weapon id, and the active set (defaults with config overrides applied). */
public final class Weapons {
	public static final String STARTER_PISTOL = "starter_pistol";
	public static final String HAND_CANNON = "hand_cannon";
	public static final String AUTO_RIFLE = "auto_rifle";
	public static final String SHOTGUN = "shotgun";
	public static final String SNIPER_RIFLE = "sniper_rifle";

	private static final Map<String, GunStats> DEFAULTS = new LinkedHashMap<>();
	private static Map<String, GunStats> active = Map.of();

	static {
		//                                       ammo       mode    dmg  pel  rpm  mag  reload prec  range hip  ads  zoom kick falloff min  kb    scope perks
		DEFAULTS.put(STARTER_PISTOL, new GunStats("none", "semi", 4.0, 1, 300, 8, 1.2, 1.5, 48, 1.5, 0.4, 1.25, 2.0, 24, 0.6, 0.15, false, false));
		DEFAULTS.put(HAND_CANNON, new GunStats("primary", "semi", 9.0, 1, 140, 6, 1.6, 2.0, 80, 1.2, 0.25, 1.35, 6.0, 48, 0.6, 0.35, false, true));
		DEFAULTS.put(AUTO_RIFLE, new GunStats("primary", "auto", 3.5, 1, 600, 30, 2.0, 1.6, 60, 2.5, 0.8, 1.3, 1.2, 32, 0.6, 0.05, false, true));
		DEFAULTS.put(SHOTGUN, new GunStats("special", "semi", 3.0, 8, 70, 5, 2.4, 1.25, 20, 6.0, 4.5, 1.15, 7.0, 8, 0.3, 0.12, false, true));
		DEFAULTS.put(SNIPER_RIFLE, new GunStats("special", "semi", 22.0, 1, 45, 4, 2.6, 3.0, 120, 6.0, 0.0, 4.0, 8.0, 120, 1.0, 0.4, true, true));
		active = Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULTS));
	}

	private Weapons() {
	}

	public static Map<String, GunStats> defaults() {
		return Collections.unmodifiableMap(DEFAULTS);
	}

	/** Active stats; falls back to the default for an id with no valid override. */
	public static GunStats get(String id) {
		GunStats stats = active.get(id);
		return stats != null ? stats : DEFAULTS.get(id);
	}

	public static Map<String, GunStats> active() {
		return active;
	}

	/**
	 * Applies overrides on top of the defaults. Invalid or unknown entries are skipped and returned so the caller
	 * can log them.
	 */
	public static List<String> apply(Map<String, GunStats> overrides) {
		Map<String, GunStats> merged = new LinkedHashMap<>(DEFAULTS);
		List<String> rejected = new ArrayList<>();
		if (overrides != null) {
			overrides.forEach((id, stats) -> {
				if (DEFAULTS.containsKey(id) && stats != null && stats.isValid()) {
					merged.put(id, stats);
				} else {
					rejected.add(id);
				}
			});
		}
		active = Collections.unmodifiableMap(merged);
		return rejected;
	}
}
