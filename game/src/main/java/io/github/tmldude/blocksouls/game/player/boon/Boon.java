package io.github.tmldude.blocksouls.game.player.boon;

import java.util.List;

/**
 * A boon definition from {@code data/<ns>/boons/<path>.json}.
 *
 * @param id       {@code <ns>:<path>}
 * @param icon     item id drawn on cards and in the HUD
 * @param maxLevel how many times it can be picked (1 = not stackable)
 * @param requires boon ids that must all be owned for this to be offered (combos)
 */
public record Boon(String id, BoonTier tier, String icon, int maxLevel, List<String> requires, List<BoonEffect> effects) {
	public Boon {
		requires = List.copyOf(requires);
		effects = List.copyOf(effects);
	}

	public boolean isCombo() {
		return !requires.isEmpty();
	}
}
