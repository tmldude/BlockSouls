package io.github.tmldude.blocksouls.game.player.rune;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's meta-progression, kept across runs (and across {@code /bs run new}): rune souls banked and runes
 * unlocked. Plain data for Gson.
 */
public class RuneProfile {
	public int version = 1;
	public int souls;
	public List<String> unlocked = new ArrayList<>();
	public int runsFinished;
	public int soulsEarned;

	public boolean has(String runeId) {
		return unlocked.contains(runeId);
	}

	public Set<String> unlockedSet() {
		return new LinkedHashSet<>(unlocked);
	}

	/** Spends the cost and records the rune; false if it can't be afforded or is already held. */
	public boolean unlock(Rune rune) {
		if (has(rune.id()) || souls < rune.cost()) {
			return false;
		}
		souls -= rune.cost();
		unlocked.add(rune.id());
		return true;
	}

	/** Whether every prerequisite is held. */
	public boolean canReach(Rune rune) {
		return unlocked.containsAll(rune.requires());
	}
}
