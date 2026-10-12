package io.github.tmldude.blocksouls.game.player.boon;

import java.util.List;
import java.util.Locale;

/**
 * A boon, curse or trinket definition from {@code data/<ns>/boons|curses|trinkets/<path>.json}. All three share one
 * schema and one id space; {@link #kind()} says which folder it came from and how it's owned (boons and curses are
 * lost on going down, trinkets survive it).
 *
 * @param id       {@code <ns>:<path>}
 * @param icon     drawn on cards and in the HUD: an item id or a GUI sprite id ({@link BoonIcons})
 * @param maxLevel how many times it can be picked (1 = not stackable); a duplicate pick upgrades it
 * @param keystone a rule-changing boon (rare/legendary); only one keystone per trigger tag may be owned at once
 * @param tags     synergy tags ({@code blocksouls:fire}, ...), stacked by level into the player's build
 * @param requires boon ids that must all be owned for this to be offered (combos)
 */
public record Boon(String id, Kind kind, BoonTier tier, String icon, int maxLevel, boolean keystone, List<String> tags,
		List<String> requires, List<BoonEffect> effects) {
	public Boon {
		tags = List.copyOf(tags);
		requires = List.copyOf(requires);
		effects = List.copyOf(effects);
	}

	/** A plain boon with no tags (tests, pre-v2 callers). */
	public Boon(String id, BoonTier tier, String icon, int maxLevel, List<String> requires, List<BoonEffect> effects) {
		this(id, Kind.BOON, tier, icon, maxLevel, false, List.of(), requires, effects);
	}

	public boolean isCombo() {
		return !requires.isEmpty();
	}

	public boolean isCurse() {
		return kind == Kind.CURSE;
	}

	public boolean isTrinket() {
		return kind == Kind.TRINKET;
	}

	/** Which data folder a definition lives in, and how it's owned. */
	public enum Kind {
		/** {@code boons/}: offered by relics, lost on going down. */
		BOON,
		/** {@code curses/}: applied by greed objects, purged for souls, lost on going down. */
		CURSE,
		/** {@code trinkets/}: pick-1-of-3 from a Trinket Case, 3 slots, survive going down. */
		TRINKET;

		public String folder() {
			return name().toLowerCase(Locale.ROOT) + "s";
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byOrdinal(int ordinal) {
			Kind[] values = values();
			return values[Math.clamp(ordinal, 0, values.length - 1)];
		}
	}
}
