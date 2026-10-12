package io.github.tmldude.blocksouls.game.player.boon;

import java.util.Collection;
import java.util.Map;

/**
 * Pure rules for what a relic may offer and how likely each card is. No Minecraft classes, so the weighting is
 * unit-testable; the synergy vocabulary's trigger tags are recognized by their {@code on_} path prefix here (the
 * catalog cross-checks the real category at load time).
 */
public final class OfferRules {
	private OfferRules() {
	}

	/** How offers lean toward the build; see {@code BuildConfig.Offers}. */
	public record Weighting(double tagWeightPerStack, double tagWeightMax, int keystoneMinTagStacks, boolean oneKeystonePerTrigger) {
		public static final Weighting NONE = new Weighting(0, 0, 0, true);
	}

	/** Whether a synergy tag id is a trigger tag ({@code blocksouls:on_kill}, ...). */
	public static boolean isTrigger(String tag) {
		return tag.substring(tag.indexOf(':') + 1).startsWith("on_");
	}

	/** Stacks the player owns across the boon's tags. */
	public static int ownedStacks(Boon boon, Map<String, Integer> ownedTags) {
		int stacks = 0;
		for (String tag : boon.tags()) {
			stacks += ownedTags.getOrDefault(tag, 0);
		}
		return stacks;
	}

	/** {@code 1 + perStack × owned stacks of its tags}, capped at {@code 1 + max}. Never below 1. */
	public static double weight(Boon boon, Map<String, Integer> ownedTags, Weighting weighting) {
		double bonus = weighting.tagWeightPerStack() * ownedStacks(boon, ownedTags);
		if (weighting.tagWeightMax() >= 0) {
			bonus = Math.min(bonus, weighting.tagWeightMax());
		}
		return 1.0 + Math.max(0, bonus);
	}

	/**
	 * A keystone is blocked when the player owns another keystone sharing one of its trigger tags (one keystone per
	 * trigger), or hasn't invested in any of its tags yet ({@code keystoneMinTagStacks}).
	 */
	public static boolean keystoneAllowed(Boon boon, Map<String, Integer> owned, Map<String, Integer> ownedTags,
			Collection<Boon> all, Weighting weighting) {
		if (!boon.keystone()) {
			return true;
		}
		if (weighting.keystoneMinTagStacks() > 0 && ownedStacks(boon, ownedTags) < weighting.keystoneMinTagStacks()) {
			return false;
		}
		if (!weighting.oneKeystonePerTrigger()) {
			return true;
		}
		for (Boon other : all) {
			if (other.keystone() && !other.id().equals(boon.id()) && owned.getOrDefault(other.id(), 0) > 0 && sharesTrigger(boon, other)) {
				return false;
			}
		}
		return true;
	}

	static boolean sharesTrigger(Boon a, Boon b) {
		for (String tag : a.tags()) {
			if (isTrigger(tag) && b.tags().contains(tag)) {
				return true;
			}
		}
		return false;
	}
}
